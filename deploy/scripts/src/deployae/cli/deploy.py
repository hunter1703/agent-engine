"""`deployae deploy` — deploys the full stack as a dependency graph of concurrent
stages: build images, infra services, config seeding, app services. Every stage type is
one of the classes in `deployae.stages`; this module's only job is to wire up which
stage depends on which and hand the resulting graph to `run_graph()`.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import os
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
    EnsureEnvSecretStage,
    EnsureIngressControllerStage,
    EnsureLocalTlsCertStage,
    EnsureNamespaceStage,
    ProvisionStage,
    SeedInfraConfigStage,
    SeedAppConfigStage,
    Stage,
    UninstallChartStage,
    run_graph,
)

APP_COMPONENTS = ("agent", "catalog", "rest", "knowledge", "connectors", "scheduler", "internal")
INFRA_COMPONENTS = ("mongodb", "postgres", "localstack", "qdrant")


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
        "--env",
        help="Path to a .env file: loaded into the process for seed-time ${VAR} expansion, "
        "and copied wholesale into the namespace Secret that every app pod mounts",
    )
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
        "--image-registry",
        help="Registry prefix to build/push/deploy images through (e.g. ghcr.io/<owner>) "
        "instead of assuming the deploying machine's own Docker daemon is what the cluster "
        "pulls from — required for a cluster that isn't on the same host as the build",
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
    parser.add_argument(
        "--cleanup-internal",
        action="store_true",
        help="Uninstall the 'internal' release once index creation has finished — it's only "
        "needed transiently, to create indexes at deploy time, not as a standing service",
    )


def _build_context(args: argparse.Namespace) -> helm.DeployContext:
    return helm.DeployContext(
        tier=args.tier,
        environment=args.environment,
        namespace=args.namespace,
        extra_values_files=args.values_files,
        set_arguments=args.set_arguments,
        image_tag=args.image_tag,
        image_registry=args.image_registry,
        # A secret, not a CLI flag, so it never shows up in argv/ps output or shell history —
        # set by the caller's environment (a CI job's `env:` block, e.g.) when the target tier
        # has no self-hosted mongodb chart to seed via port-forward instead.
        mongodb_uri=os.environ.get("INFRA_MONGODB_URI"),
    )


def run(args: argparse.Namespace) -> None:
    env_file = getattr(args, "env", None)
    env_path: Path | None = None
    if env_file:
        env_path = Path(os.path.expanduser(env_file))
        if not env_path.is_file():
            raise ValueError(f"env file {env_path} not found")
        from dotenv import load_dotenv

        load_dotenv(env_path, override=True)

    ctx = _build_context(args)
    asyncio.run(
        _deploy(
            ctx,
            atomic=not args.no_atomic,
            skip_infra=args.skip_infra,
            dry_run=args.dry_run,
            timeout=args.timeout,
            cleanup_internal=args.cleanup_internal,
            env_file=env_path,
        )
    )


async def _deploy(
    ctx: helm.DeployContext,
    *,
    atomic: bool,
    skip_infra: bool,
    dry_run: bool,
    timeout: str,
    cleanup_internal: bool = False,
    env_file: Path | None = None,
) -> None:
    start_time = time.time()
    ctx = replace(ctx, rollout_revision=None if dry_run else str(int(time.time())))
    interrupted = asyncio.Event()
    _install_shutdown_handler(interrupted)

    stages = build_stages(
        ctx,
        atomic=atomic,
        skip_infra=skip_infra,
        dry_run=dry_run,
        timeout=timeout,
        cleanup_internal=cleanup_internal,
        env_file=env_file,
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


def _install_shutdown_handler(interrupted: asyncio.Event) -> None:
    def handle_signal() -> None:
        output.warn("Shutdown signal received. Stopping (deployed workloads are left running).")
        interrupted.set()

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, handle_signal)


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
    env_secret_stages: dict[str, EnsureEnvSecretStage],
    ingress_stage: EnsureIngressControllerStage,
    tls_cert_stages: dict[str, EnsureLocalTlsCertStage],
) -> tuple[Stage, ...]:
    """The setup stages one chart's DeployChartStage should depend on: its namespace,
    the env secret, the ingress controller if this is `rest`, plus its own local TLS cert/Secret stage
    if it declares TLS hosts (so its Ingress never briefly references a Secret that
    doesn't exist yet)."""
    ns = chart.namespace(ctx.namespace)
    prerequisites: tuple[Stage, ...] = (
        namespace_stages[ns],
    )
    if ns in env_secret_stages:
        prerequisites = (*prerequisites, env_secret_stages[ns])
    if chart.name == "rest":
        prerequisites = (*prerequisites, ingress_stage)
    tls_stage = tls_cert_stages.get(chart.name)
    if tls_stage:
        prerequisites = (*prerequisites, tls_stage)
    return prerequisites


def build_stages(
    ctx: helm.DeployContext,
    *,
    atomic: bool,
    skip_infra: bool,
    dry_run: bool,
    timeout: str,
    cleanup_internal: bool = False,
    env_file: Path | None = None,
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
    app_charts = [Chart(name) for name in (*APP_COMPONENTS, "global-properties")]
    app_namespaces = {chart.namespace(ctx.namespace) for chart in app_charts}
    env_secret_stages = {
        ns: EnsureEnvSecretStage(
            name=f"ensure-env-secret-{ns}",
            namespace=ns,
            env_file=env_file if not dry_run else None,
            depends_on=(namespace_stages[ns],),
            enabled=not dry_run,
        )
        for ns in app_namespaces
    }
    ingress_stage = EnsureIngressControllerStage(name="ensure-ingress-controller")
    stages: list[Stage] = [*namespace_stages.values(), *env_secret_stages.values(), ingress_stage]

    # mkcert's CA is only ever trusted on the machine that generated it, so this only makes
    # sense when the tier's own resolved ingress config has TLS enabled with no clusterIssuer
    # to provision a real one via cert-manager (see Chart.needs_local_tls_cert) — driven by
    # what that tier's values actually declare, not by the tier's name.
    tls_cert_stages = {
        component: EnsureLocalTlsCertStage(
            name=f"ensure-local-tls-cert-{component}",
            depends_on=(namespace_stages[Chart(component).namespace(ctx.namespace)],),
            chart=Chart(component),
            tier=ctx.tier or "",
            namespace_override=ctx.namespace,
            enabled=Chart(component).needs_local_tls_cert(ctx.tier)
            and not dry_run
            and Chart(component).is_enabled_for_tier(ctx.tier),
        )
        for component in APP_COMPONENTS
        if Chart(component).ingress_tls_hosts(ctx.tier)
    }
    stages.extend(tls_cert_stages.values())

    # --- Build: one gradle build feeding one docker-image stage per component ---
    # A tier assembles its own subset of app charts by which tiers/<tier>/values.yaml files
    # exist (see Chart.is_enabled_for_tier) — building/pushing a component no chart for this
    # tier will ever deploy is pure waste (CI minutes, registry storage), so the gradle task
    # list itself is trimmed to what's actually enabled. image_stage_by_component still holds
    # an entry per component regardless (each deploy_app_chart() call below references its own
    # component unconditionally), just disabled for anything not enabled for this tier.
    enabled_components = tuple(
        component for component in APP_COMPONENTS if Chart(component).is_enabled_for_tier(ctx.tier)
    )
    gradle_stage = BuildGradleStage(
        name="build-gradle", components=enabled_components, enabled=not dry_run
    )
    stages.append(gradle_stage)
    image_stage_by_component = {
        component: BuildDockerImageStage(
            name=f"build-image-{component}",
            depends_on=(gradle_stage,),
            component=component,
            tag=ctx.image_tag or "dev",
            registry_prefix=ctx.image_registry,
            push=bool(ctx.image_registry),
            enabled=not dry_run and component in enabled_components,
        )
        for component in APP_COMPONENTS
    }
    stages.extend(image_stage_by_component.values())

    # --- Infra charts ---
    infra_enabled = not (skip_infra or dry_run)
    infra_chart_enabled = {
        name: infra_enabled and Chart(name).is_enabled_for_tier(ctx.tier)
        for name in INFRA_COMPONENTS
    }
    infra_deploy_by_name = {
        name: DeployChartStage(
            name=f"deploy-{name}",
            depends_on=_chart_prerequisites(
                Chart(name), ctx, namespace_stages, env_secret_stages, ingress_stage, tls_cert_stages
            ),
            chart=Chart(name),
            ctx=ctx,
            atomic=atomic,
            timeout=timeout,
            enabled=infra_chart_enabled[name],
        )
        for name in INFRA_COMPONENTS
    }
    stages.extend(infra_deploy_by_name.values())

    # --- App charts ---
    def deploy_app_chart(name: str, *extra_deps: Stage) -> DeployChartStage:
        chart = Chart(name)
        # Every app service reads Mongo-backed infra config at startup somewhere (encryption,
        # microservice client wiring, Pekko cluster config, vector DB, cloud storage, ...) via
        # InfraConfigService.findById(), which returns null — not an error — for a document
        # that hasn't been saved yet. Without this dependency, app charts and the infra config
        # save race, and whichever finishes startup first decides whether that config exists.
        # global-properties doesn't read infra config, and internal is what saves it, so
        # neither can wait on it.
        infra_config_dep = (
            () if name in ("global-properties", "internal") else (infra_config_stage,)
        )
        depends_on = (
            *_chart_prerequisites(chart, ctx, namespace_stages, env_secret_stages, ingress_stage, tls_cert_stages),
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
            # A tier assembles its own subset of services by which charts it has a
            # tiers/<tier>/values.yaml for — see Chart.is_enabled_for_tier. A chart with
            # no overlay for this tier is skipped entirely, not deployed with defaults.
            enabled=chart.is_enabled_for_tier(ctx.tier),
        )
        stages.append(stage)
        return stage

    global_properties_stage = deploy_app_chart("global-properties")
    mongodb_deps = tuple(
        infra_deploy_by_name[name] for name in ("mongodb",) if infra_chart_enabled[name]
    )
    internal_stage = deploy_app_chart(
        "internal", global_properties_stage, image_stage_by_component["internal"], *mongodb_deps
    )
    infra_config_stage = SeedInfraConfigStage(
        name="seed-infra-config",
        depends_on=(
            *mongodb_deps,
            *(infra_deploy_by_name[name] for name in ("postgres",) if infra_chart_enabled[name]),
            internal_stage,
        ),
        chart_name="internal",
        tier=ctx.tier,
        namespace_override=ctx.namespace,
        environment=ctx.environment,
        enabled=(infra_chart_enabled["mongodb"] or bool(ctx.mongodb_uri)) and not dry_run,
    )
    stages.append(infra_config_stage)
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
    # The environment and every customer are provisioned through internal: client configs, and the
    # databases, event store tables, indexes, vector collections and buckets behind them. The
    # services that host Pekko actors read and write the event store from startup, so they wait
    # for it.
    provision_stage = ProvisionStage(
        name="provision",
        depends_on=(
            *(
                infra_deploy_by_name[name]
                for name in ("mongodb", "postgres", "qdrant", "localstack")
                if infra_chart_enabled[name]
            ),
            infra_config_stage,
        ),
        environment=ctx.environment,
        chart_name="internal",
        tier=ctx.tier,
        namespace_override=ctx.namespace,
        enabled=not dry_run,
    )
    stages.append(provision_stage)
    deploy_app_chart(
        "scheduler",
        global_properties_stage,
        image_stage_by_component["scheduler"],
        provision_stage,
    )
    deploy_app_chart(
        "agent",
        global_properties_stage,
        image_stage_by_component["agent"],
        provision_stage,
    )

    # --- Optional: internal is only needed transiently, to provision — not as a standing
    # service. --cleanup-internal tears it down once that's done. ---
    stages.append(
        UninstallChartStage(
            name="cleanup-internal",
            depends_on=(provision_stage,),
            chart=Chart("internal"),
            tier=ctx.tier,
            environment=ctx.environment,
            namespace=Chart("internal").namespace(ctx.namespace),
            enabled=cleanup_internal and not dry_run,
        )
    )

    # --- App Config Seeding (replaces old rest seeding) ---
    seed_app = SeedAppConfigStage(
        name="seed-app-config",
        depends_on=tuple(stages.copy()),  # run after everything else
        environment=ctx.environment,
        tier=ctx.tier,
        enabled=not dry_run
    )
    stages.append(seed_app)

    return stages
