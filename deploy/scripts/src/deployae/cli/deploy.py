"""`deployae deploy` — deploys the full stack as a dependency graph of concurrent
stages: build images, infra services, config seeding, app services. Every stage type is
one of the classes in `deployae.stages`; this module's only job is to wire up which
stage depends on which and hand the resulting graph to `run_graph()`.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import signal
import subprocess
import time
from dataclasses import replace
from pathlib import Path

from deployae import helm, output
from deployae.charts import REPO_ROOT, Chart
from deployae.stages import (
    BuildDockerImageStage,
    BuildGradleStage,
    DeployChartStage,
    EnsureIndexesStage,
    EnsureIngressControllerStage,
    EnsureLocalstackBucketsStage,
    EnsureNamespaceStage,
    InitPostgresSchemaStage,
    InitQdrantCollectionStage,
    SeedInfraConfigStage,
    SeedRestCatalogStage,
    Stage,
    run_graph,
)

APP_COMPONENTS = ("agent", "catalog", "rest", "knowledge", "connectors", "scheduler", "internal")
INFRA_COMPONENTS = ("mongodb", "postgres", "localstack", "qdrant")
ENV_SECRET_CHARTS = ("connectors", "agent", "knowledge")
DEFAULT_LOCAL_PORT = 8080


def _default_image_tag() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=REPO_ROOT
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except FileNotFoundError:
        pass
    return "dev"


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "-t",
        "--tier",
        help="Tier overlay under k8s/<chart>/tiers (required for values a chart marks `required`)",
    )
    parser.add_argument(
        "-e",
        "--environment",
        help="Environment: selects global-properties' own overlay, and tells app charts "
        "which global-properties ConfigMap to mount",
    )
    parser.add_argument("-n", "--namespace", help="Override every selected chart's namespace")
    parser.add_argument(
        "-f",
        "--values",
        action="append",
        default=[],
        dest="values_files",
        type=Path,
        help="Additional Helm values file (repeatable)",
    )
    parser.add_argument(
        "--set",
        action="append",
        default=[],
        dest="set_arguments",
        help="Additional Helm --set override (repeatable)",
    )
    parser.add_argument(
        "--image-tag",
        default=_default_image_tag(),
        help="Override the app image tag for app charts",
    )
    parser.add_argument(
        "--no-atomic", action="store_true", help="Disable atomic rollback on Helm failure"
    )
    parser.add_argument(
        "--skip-infra",
        action="store_true",
        help="Skip infra deploy and seeding — use when MongoDB/Postgres/etc. are already running",
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="Render app chart changes without applying them"
    )
    parser.add_argument(
        "--timeout", default=helm.DEFAULT_TIMEOUT, help="Helm timeout (default: 10m)"
    )
    parser.add_argument("--local-port", type=int, default=DEFAULT_LOCAL_PORT)


def _build_context(args: argparse.Namespace) -> helm.DeployContext:
    return helm.DeployContext(
        tier=args.tier,
        environment=args.environment,
        namespace=args.namespace,
        extra_values_files=args.values_files,
        set_arguments=args.set_arguments,
        image_tag=args.image_tag,
    )


def run(args: argparse.Namespace) -> None:
    ctx = _build_context(args)
    asyncio.run(
        _deploy(
            ctx,
            atomic=not args.no_atomic,
            skip_infra=args.skip_infra,
            dry_run=args.dry_run,
            timeout=args.timeout,
            local_port=args.local_port,
        )
    )


async def _deploy(
    ctx: helm.DeployContext,
    *,
    atomic: bool,
    skip_infra: bool,
    dry_run: bool,
    timeout: str,
    local_port: int,
) -> None:
    start_time = time.time()
    ctx = replace(ctx, rollout_revision=None if dry_run else str(int(time.time())))
    interrupted = asyncio.Event()
    _install_shutdown_handler(interrupted)

    stages = build_stages(
        ctx, atomic=atomic, skip_infra=skip_infra, dry_run=dry_run, timeout=timeout
    )
    deploy_task = asyncio.ensure_future(run_graph(stages))
    await asyncio.wait(
        {deploy_task, asyncio.ensure_future(interrupted.wait())},
        return_when=asyncio.FIRST_COMPLETED,
    )
    if interrupted.is_set():
        # Cancelling here (rather than leaving deploy_task for asyncio.run()'s own
        # implicit cleanup) is what stops a stage still waiting on its dependencies —
        # without it, the signal handler stays registered with nothing consuming its
        # events, so every subsequent Ctrl+C just re-fires it and reprints the warning
        # while the deploy keeps running underneath. A stage already inside a blocking
        # `asyncio.to_thread` helm/kubectl subprocess call can't be interrupted this way
        # regardless — cancellation only takes effect once that call returns.
        deploy_task.cancel()
        with contextlib.suppress(asyncio.CancelledError):
            await deploy_task
        return
    await deploy_task

    if dry_run:
        # Infra chart stages are disabled under dry-run (their `.rendered` stays None),
        # so this naturally prints only the app charts that were actually rendered.
        for stage in stages:
            if isinstance(stage, DeployChartStage) and stage.rendered is not None:
                print(stage.header())
                print(stage.rendered)

    elapsed = time.time() - start_time
    output.phase(f"Deployment complete in {int(elapsed // 60)}m {int(elapsed % 60)}s — application is ready")
    if not dry_run:
        rest = Chart("rest")
        await _port_forward_rest(
            rest.namespace(ctx.namespace), rest.resource_name(ctx.tier), local_port, interrupted
        )


def _install_shutdown_handler(interrupted: asyncio.Event) -> None:
    def handle_signal() -> None:
        output.warn("Shutdown signal received. Stopping (deployed workloads are left running).")
        interrupted.set()

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, handle_signal)


async def _port_forward_rest(
    namespace: str, service_name: str, local_port: int, interrupted: asyncio.Event
) -> None:
    output.step(f"Starting port-forward {service_name}:8080 → localhost:{local_port}")
    output.info(f"REST API available at http://localhost:{local_port}")
    output.note("Press Ctrl+C to stop.")
    while not interrupted.is_set():
        process = await asyncio.create_subprocess_exec(
            "kubectl", "port-forward", "-n", namespace, f"svc/{service_name}", f"{local_port}:8080"
        )
        wait_task = asyncio.ensure_future(process.wait())
        interrupted_task = asyncio.ensure_future(interrupted.wait())
        await asyncio.wait({wait_task, interrupted_task}, return_when=asyncio.FIRST_COMPLETED)
        if interrupted.is_set():
            wait_task.cancel()
            process.terminate()
            with contextlib.suppress(TimeoutError):
                await asyncio.wait_for(process.wait(), timeout=5)
            break
        interrupted_task.cancel()
        output.warn("Port-forward dropped, restarting...")
        await asyncio.sleep(2)


def _namespace_stages_for(
    charts: list[Chart], ctx: helm.DeployContext
) -> dict[str, EnsureNamespaceStage]:
    """One EnsureNamespaceStage per unique namespace the given charts deploy into."""
    return {
        namespace: EnsureNamespaceStage(name=f"ensure-namespace-{namespace}", namespace=namespace)
        for namespace in {chart.namespace(ctx.namespace) for chart in charts}
    }


def _chart_prerequisites(
    chart: Chart,
    ctx: helm.DeployContext,
    namespace_stages: dict[str, EnsureNamespaceStage],
    ingress_stage: EnsureIngressControllerStage,
) -> tuple[Stage, ...]:
    """The setup stages one chart's DeployChartStage should depend on: its namespace,
    plus the ingress controller if this is `rest`."""
    prerequisites: tuple[Stage, ...] = (namespace_stages[chart.namespace(ctx.namespace)],)
    if chart.name == "rest":
        prerequisites = (*prerequisites, ingress_stage)
    return prerequisites


def build_stages(
    ctx: helm.DeployContext, *, atomic: bool, skip_infra: bool, dry_run: bool, timeout: str
) -> list[Stage]:
    """Builds the full stage graph.

    The graph's *shape* never changes with skip_infra/dry_run — every stage always
    exists, so dependency references are always valid objects. What changes is each
    stage's `enabled` flag: a disabled stage's dependents still unblock the instant it
    "completes" (run_graph treats a disabled stage as an immediate no-op), so turning a
    stage off never stalls anything downstream.
    """
    all_charts = [Chart(name) for name in (*INFRA_COMPONENTS, "global-properties", *APP_COMPONENTS)]
    namespace_stages = _namespace_stages_for(all_charts, ctx)
    ingress_stage = EnsureIngressControllerStage(name="ensure-ingress-controller")
    stages: list[Stage] = [*namespace_stages.values(), ingress_stage]

    # --- Build: one gradle build feeding one docker-image stage per component ---
    gradle_stage = BuildGradleStage(
        name="build-gradle", components=APP_COMPONENTS, enabled=not dry_run
    )
    stages.append(gradle_stage)
    image_stage_by_component = {
        component: BuildDockerImageStage(
            name=f"build-image-{component}",
            depends_on=(gradle_stage,),
            component=component,
            tag=ctx.image_tag or "dev",
            enabled=not dry_run,
        )
        for component in APP_COMPONENTS
    }
    stages.extend(image_stage_by_component.values())

    # --- Infra charts ---
    infra_enabled = not (skip_infra or dry_run)
    infra_deploy_by_name = {
        name: DeployChartStage(
            name=f"deploy-{name}",
            depends_on=_chart_prerequisites(Chart(name), ctx, namespace_stages, ingress_stage),
            chart=Chart(name),
            ctx=ctx,
            atomic=atomic,
            timeout=timeout,
            enabled=infra_enabled,
        )
        for name in INFRA_COMPONENTS
    }
    stages.extend(infra_deploy_by_name.values())

    seed_infra_config_stage = SeedInfraConfigStage(
        name="seed-infra-config",
        depends_on=(infra_deploy_by_name["mongodb"],),
        tier=ctx.tier,
        environment=ctx.environment,
        namespace_override=ctx.namespace,
        enabled=infra_enabled,
    )
    stages.append(seed_infra_config_stage)
    stages.append(
        InitPostgresSchemaStage(
            name="init-postgres-schema",
            depends_on=(infra_deploy_by_name["postgres"],),
            namespace_override=ctx.namespace,
            tier=ctx.tier,
            enabled=infra_enabled,
        )
    )
    qdrant_collections_stage = InitQdrantCollectionStage(
        name="init-qdrant-collections",
        depends_on=(infra_deploy_by_name["qdrant"],),
        namespace_override=ctx.namespace,
        tier=ctx.tier,
        enabled=infra_enabled,
    )
    stages.append(qdrant_collections_stage)
    localstack_buckets_stage = EnsureLocalstackBucketsStage(
        name="ensure-localstack-buckets",
        depends_on=(infra_deploy_by_name["localstack"],),
        namespace_override=ctx.namespace,
        tier=ctx.tier,
        enabled=infra_enabled,
    )
    stages.append(localstack_buckets_stage)

    # --- App charts ---
    def deploy_app_chart(name: str, *extra_deps: Stage) -> DeployChartStage:
        chart = Chart(name)
        # Every app service reads Mongo-backed infra config at startup somewhere (encryption,
        # microservice client wiring, Pekko cluster config, vector DB, cloud storage, ...) via
        # InfraConfigService.findById(), which returns null — not an error — for a document
        # that hasn't been seeded yet. Without this dependency, app charts and seed-infra-config
        # race, and whichever finishes startup first decides whether that config exists.
        # global-properties is the one exception: it doesn't read infra config, so keeping it
        # off this dependency keeps it off the critical path.
        infra_config_dep = () if name == "global-properties" else (seed_infra_config_stage,)
        depends_on = (
            *_chart_prerequisites(chart, ctx, namespace_stages, ingress_stage),
            *infra_config_dep,
            *extra_deps,
        )
        stage = DeployChartStage(
            name=f"deploy-{name}",
            depends_on=depends_on,
            chart=chart,
            ctx=ctx,
            atomic=atomic,
            timeout=timeout,
            dry_run=dry_run,
            needs_env_secret=name in ENV_SECRET_CHARTS,
            env_file=REPO_ROOT / ".env",
        )
        stages.append(stage)
        return stage

    global_properties_stage = deploy_app_chart("global-properties")
    catalog_stage = deploy_app_chart(
        "catalog", global_properties_stage, image_stage_by_component["catalog"]
    )
    rest_stage = deploy_app_chart("rest", global_properties_stage, image_stage_by_component["rest"])
    deploy_app_chart(
        "knowledge",
        global_properties_stage,
        image_stage_by_component["knowledge"],
    )
    deploy_app_chart("connectors", global_properties_stage, image_stage_by_component["connectors"])
    deploy_app_chart("scheduler", global_properties_stage, image_stage_by_component["scheduler"])
    internal_stage = deploy_app_chart(
        "internal", global_properties_stage, image_stage_by_component["internal"]
    )
    deploy_app_chart(
        "agent",
        global_properties_stage,
        image_stage_by_component["agent"],
    )

    # --- Index creation: internal links every repository, so one call covers all collections ---
    stages.append(
        EnsureIndexesStage(
            name="ensure-indexes",
            depends_on=(infra_deploy_by_name["mongodb"], internal_stage),
            chart_name="internal",
            tier=ctx.tier,
            namespace_override=ctx.namespace,
            enabled=not dry_run,
        )
    )

    # --- Catalog seeding: models, then agents ---
    seed_models_stage = SeedRestCatalogStage(
        name="seed-models",
        depends_on=(infra_deploy_by_name["mongodb"], catalog_stage, rest_stage),
        kind="models",
        tier=ctx.tier,
        environment=ctx.environment,
        namespace_override=ctx.namespace,
        enabled=not dry_run,
    )
    stages.append(seed_models_stage)
    stages.append(
        SeedRestCatalogStage(
            name="seed-agents",
            depends_on=(seed_models_stage,),
            kind="agents",
            tier=ctx.tier,
            environment=ctx.environment,
            namespace_override=ctx.namespace,
            enabled=not dry_run,
        )
    )

    return stages
