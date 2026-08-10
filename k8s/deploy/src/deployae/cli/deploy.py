"""`deployae deploy` — deploys the full stack as a dependency graph of concurrent stages
(build images, infra services, config seeding, app services), rather than a flat list.

Each stage is a coroutine; a stage's dependencies are `asyncio.Event`s it awaits before
starting, so downstream stages start the instant their prerequisites are actually ready —
no polling. One stage failing sets a shared abort flag: stages already running finish
(there's no value in killing an in-flight `helm upgrade`), but anything still waiting on
a dependency unblocks immediately and skips its own action.
"""

from __future__ import annotations

import argparse
import asyncio
import signal
import subprocess
import time
from collections.abc import Awaitable, Callable
from dataclasses import dataclass, replace

from deployae import helm, kube, output
from deployae.charts import K8S_DIR, Chart
from deployae.cli.build import build_images
from deployae.cli.common import add_deploy_flags, build_context
from deployae.cli.init import init_postgres_schema, init_qdrant_collection
from deployae.seeding import catalog as catalog_seeding
from deployae.seeding import infra as infra_seeding

REPO_ROOT = K8S_DIR.parent
APP_COMPONENTS = ("agent", "catalog", "rest", "knowledge", "connectors")
INFRA_COMPONENTS = ("mongodb", "postgres", "localstack", "qdrant")
DEFAULT_LOCAL_PORT = 8080


@dataclass
class Stage:
    name: str
    depends_on: tuple[str, ...]
    action: Callable[[], Awaitable[None]]


async def run_stages(stages: list[Stage]) -> None:
    events = {stage.name: asyncio.Event() for stage in stages}
    abort = asyncio.Event()
    errors: list[tuple[str, BaseException]] = []

    async def run_one(stage: Stage) -> None:
        try:
            for dep in stage.depends_on:
                await events[dep].wait()
            if not abort.is_set():
                await stage.action()
        except Exception as error:
            errors.append((stage.name, error))
            abort.set()
        finally:
            events[stage.name].set()

    await asyncio.gather(*(run_one(stage) for stage in stages))

    if errors:
        for name, error in errors:
            output.error(f"{name}: {error}")
        raise SystemExit(1)


def add_arguments(parser: argparse.ArgumentParser) -> None:
    add_deploy_flags(parser)
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


def run(args: argparse.Namespace) -> None:
    ctx = build_context(args)
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
    ctx = replace(ctx, rollout_revision=None if dry_run else str(int(time.time())))
    interrupted = asyncio.Event()
    _install_shutdown_handler(interrupted, ctx)

    stages = _build_stages(
        ctx, atomic=atomic, skip_infra=skip_infra, dry_run=dry_run, timeout=timeout
    )
    deploy_task = asyncio.ensure_future(run_stages(stages))
    await asyncio.wait(
        {deploy_task, asyncio.ensure_future(interrupted.wait())},
        return_when=asyncio.FIRST_COMPLETED,
    )
    if interrupted.is_set():
        return
    await deploy_task

    output.phase("Deployment complete — application is ready")
    if ctx.tier == "local" and not dry_run:
        await _local_port_forward(Chart("rest").namespace(ctx.namespace), local_port)


def _install_shutdown_handler(interrupted: asyncio.Event, ctx: helm.DeployContext) -> None:
    def handle_signal() -> None:
        output.warn("Shutdown signal received. Deleting workloads (preserving volumes)...")
        kube.delete_workloads(Chart("agent").namespace(ctx.namespace))
        kube.delete_workloads(Chart("mongodb").namespace(ctx.namespace))
        output.info("Workloads deleted. PVCs preserved for next deployment.")
        interrupted.set()

    loop = asyncio.get_event_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, handle_signal)


async def _local_port_forward(namespace: str, local_port: int) -> None:
    output.step(f"Starting port-forward rest:8080 → localhost:{local_port}")
    output.info(f"REST API available at http://localhost:{local_port}")
    output.note("Press Ctrl+C to stop.")
    while True:
        process = await asyncio.create_subprocess_exec(
            "kubectl", "port-forward", "-n", namespace, "svc/rest", f"{local_port}:8080"
        )
        await process.wait()
        output.warn("Port-forward dropped, restarting...")
        await asyncio.sleep(2)


def _build_stages(
    ctx: helm.DeployContext, *, atomic: bool, skip_infra: bool, dry_run: bool, timeout: str
) -> list[Stage]:
    stages: list[Stage] = [Stage("builds-done", (), _build_action(ctx, dry_run))]

    for name in INFRA_COMPONENTS:
        stages.append(
            Stage(
                f"{name}-ready",
                (),
                _infra_deploy_action(name, ctx, atomic, timeout, skip_infra, dry_run),
            )
        )

    stages.append(
        Stage("infra-seeded", ("mongodb-ready",), _seed_infra_action(ctx, skip_infra, dry_run))
    )
    stages.append(
        Stage(
            "postgres-schema-ready",
            ("postgres-ready",),
            _init_postgres_action(ctx, skip_infra, dry_run),
        )
    )
    stages.append(
        Stage(
            "qdrant-collections-ready",
            ("qdrant-ready",),
            _init_qdrant_action(ctx, skip_infra, dry_run),
        )
    )
    stages.append(
        Stage(
            "localstack-buckets-ready",
            ("localstack-ready",),
            _localstack_buckets_action(ctx, skip_infra, dry_run),
        )
    )

    stages.append(
        Stage(
            "global-properties-ready",
            (),
            _app_deploy_action("global-properties", ctx, atomic, timeout, dry_run),
        )
    )
    stages.append(
        Stage(
            "catalog-ready",
            ("global-properties-ready", "builds-done"),
            _app_deploy_action("catalog", ctx, atomic, timeout, dry_run),
        )
    )
    stages.append(
        Stage(
            "rest-ready",
            ("global-properties-ready", "builds-done"),
            _app_deploy_action("rest", ctx, atomic, timeout, dry_run),
        )
    )
    stages.append(
        Stage(
            "knowledge-ready",
            ("global-properties-ready", "builds-done", "qdrant-collections-ready"),
            _app_deploy_action("knowledge", ctx, atomic, timeout, dry_run),
        )
    )
    stages.append(
        Stage(
            "connectors-ready",
            ("global-properties-ready", "builds-done"),
            _app_deploy_action("connectors", ctx, atomic, timeout, dry_run),
        )
    )
    stages.append(
        Stage(
            "agent-ready",
            ("global-properties-ready", "catalog-ready", "localstack-buckets-ready", "builds-done"),
            _app_deploy_action("agent", ctx, atomic, timeout, dry_run),
        )
    )

    stages.append(
        Stage(
            "catalog-seeded",
            ("mongodb-ready", "catalog-ready", "rest-ready"),
            _seed_catalog_action(ctx, dry_run),
        )
    )
    return stages


def _build_action(ctx: helm.DeployContext, dry_run: bool) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if dry_run:
            return
        output.phase("Building Docker images")
        await asyncio.to_thread(build_images, list(APP_COMPONENTS), tag=ctx.image_tag or "dev")

    return action


def _infra_deploy_action(
    name: str, ctx: helm.DeployContext, atomic: bool, timeout: str, skip_infra: bool, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if skip_infra or dry_run:
            return
        chart = Chart(name)
        output.phase(f"Deploying {name}")
        await asyncio.to_thread(helm.ensure_dependencies, chart)
        await asyncio.to_thread(helm.upgrade_install, chart, ctx, atomic=atomic, timeout=timeout)
        kind = chart.workload_kind()
        if kind:
            await asyncio.to_thread(
                kube.rollout_status, chart.namespace(ctx.namespace), kind, name, timeout
            )

    return action


def _app_deploy_action(
    name: str, ctx: helm.DeployContext, atomic: bool, timeout: str, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        chart = Chart(name)
        output.phase(f"Deploying {name}")
        await asyncio.to_thread(helm.ensure_dependencies, chart)
        extra_set = (
            None if dry_run else await asyncio.to_thread(_connectors_env_secret_set, chart, ctx)
        )
        rendered = await asyncio.to_thread(
            helm.upgrade_install,
            chart,
            ctx,
            dry_run=dry_run,
            atomic=atomic,
            timeout=timeout,
            extra_set=extra_set,
        )
        if dry_run:
            print(rendered)
            return
        kind = chart.workload_kind()
        if kind:
            await asyncio.to_thread(
                kube.rollout_status,
                chart.namespace(ctx.namespace),
                kind,
                chart.resource_name(ctx.tier),
                timeout,
            )

    return action


def _connectors_env_secret_set(chart: Chart, ctx: helm.DeployContext) -> list[str] | None:
    if chart.name != "connectors":
        return None
    release_name = chart.release_name(chart.effective_tier(ctx.tier, ctx.environment))
    secret_name = kube.ensure_env_secret(
        release_name, chart.namespace(ctx.namespace), REPO_ROOT / ".env"
    )
    return [f"app-base.secrets.envSecretName={secret_name}"] if secret_name else None


def _seed_infra_action(
    ctx: helm.DeployContext, skip_infra: bool, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if skip_infra or dry_run:
            return
        output.phase("Seeding infrastructure configuration")
        await asyncio.to_thread(
            infra_seeding.run, ctx.tier, ctx.namespace, infra_seeding.SeedInfraOptions()
        )

    return action


def _init_postgres_action(
    ctx: helm.DeployContext, skip_infra: bool, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if skip_infra or dry_run:
            return
        output.phase("Initializing PostgreSQL schema")
        await asyncio.to_thread(init_postgres_schema, ctx.namespace)

    return action


def _init_qdrant_action(
    ctx: helm.DeployContext, skip_infra: bool, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if skip_infra or dry_run:
            return
        output.phase("Initializing Qdrant collections")
        await asyncio.to_thread(init_qdrant_collection, ctx.namespace)

    return action


def _localstack_buckets_action(
    ctx: helm.DeployContext, skip_infra: bool, dry_run: bool
) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if skip_infra or dry_run:
            return
        output.phase("Ensuring LocalStack S3 buckets")
        await asyncio.to_thread(
            _ensure_localstack_buckets, Chart("localstack").namespace(ctx.namespace)
        )

    return action


def _ensure_localstack_buckets(
    namespace: str, buckets: tuple[str, ...] = ("agent-assets",)
) -> None:
    pod = subprocess.run(
        [
            "kubectl",
            "get",
            "pods",
            "--namespace",
            namespace,
            "-l",
            "app.kubernetes.io/name=localstack",
            "-o",
            "jsonpath={.items[0].metadata.name}",
        ],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.strip()
    for bucket in buckets:
        subprocess.run(
            [
                "kubectl",
                "exec",
                "--namespace",
                namespace,
                pod,
                "--",
                "sh",
                "-c",
                f"awslocal s3api head-bucket --bucket '{bucket}' 2>/dev/null || awslocal s3 mb 's3://{bucket}'",
            ],
            check=True,
        )
        output.info(f"Bucket ready: {bucket}")


def _seed_catalog_action(ctx: helm.DeployContext, dry_run: bool) -> Callable[[], Awaitable[None]]:
    async def action() -> None:
        if dry_run:
            return
        output.phase("Seeding application catalog")
        await asyncio.to_thread(catalog_seeding.run, ctx.tier, ctx.namespace)

    return action
