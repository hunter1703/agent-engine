"""`deployae apply` — deploys (or, with --dry-run, renders) one or more charts.

`apply_charts()` is the shared engine: `deployae apply` calls it standalone, and
`deployae deploy`'s dependency-graph orchestrator calls it per-stage.
"""

from __future__ import annotations

import argparse
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace

from deployae import helm, kube
from deployae.charts import K8S_DIR, Chart, resolve_charts
from deployae.cli.build import build_images
from deployae.cli.common import (
    add_chart_selector,
    add_deploy_flags,
    build_context,
    run_on_each_chart,
)

REPO_ROOT = K8S_DIR.parent


def add_arguments(parser: argparse.ArgumentParser) -> None:
    add_chart_selector(parser)
    add_deploy_flags(parser)
    parser.add_argument(
        "--no-atomic", action="store_true", help="Disable atomic rollback on Helm failure"
    )
    parser.add_argument("--lint", action="store_true", help="Run helm lint before deploying")
    parser.add_argument(
        "--dry-run", action="store_true", help="Render release changes without applying them"
    )
    parser.add_argument(
        "--timeout", default=helm.DEFAULT_TIMEOUT, help="Helm timeout (default: 10m)"
    )


def run(args: argparse.Namespace) -> None:
    apply_charts(
        resolve_charts(args.charts),
        build_context(args),
        dry_run=args.dry_run,
        atomic=not args.no_atomic,
        lint_first=args.lint,
        timeout=args.timeout,
        build_first=True,
    )


def apply_charts(
    charts: list[Chart],
    ctx: helm.DeployContext,
    *,
    dry_run: bool,
    atomic: bool,
    lint_first: bool,
    timeout: str,
    build_first: bool,
) -> None:
    if build_first and not dry_run:
        components = [chart.name for chart in charts if chart.is_app_chart]
        build_images(components, tag=ctx.image_tag or "dev")

    ctx = replace(ctx, rollout_revision=None if dry_run else str(int(time.time())))

    if not dry_run:
        for namespace in {chart.namespace(ctx.namespace) for chart in charts}:
            kube.create_namespace(namespace)
        if any(chart.name == "rest" for chart in charts):
            helm.ensure_ingress_controller()

    if dry_run:
        # Sequential, not parallel: each chart prints a full multi-KB manifest, and
        # interleaving several of those from concurrent threads would produce
        # unreadable, potentially-garbled output. There's nothing here slow enough
        # (no cluster round-trip) to make parallelizing worth that tradeoff.
        for chart in charts:
            helm.ensure_dependencies(chart)
            if lint_first:
                helm.lint(chart, ctx)
            print(helm.upgrade_install(chart, ctx, dry_run=True, timeout=timeout))
        return

    _deploy_parallel(charts, ctx, atomic=atomic, lint_first=lint_first, timeout=timeout)
    _wait_for_rollouts(charts, ctx, timeout)


def _deploy_parallel(
    charts: list[Chart], ctx: helm.DeployContext, *, atomic: bool, lint_first: bool, timeout: str
) -> None:
    def deploy_one(chart: Chart) -> None:
        helm.ensure_dependencies(chart)
        if lint_first:
            helm.lint(chart, ctx)
        helm.upgrade_install(
            chart,
            ctx,
            atomic=atomic,
            timeout=timeout,
            extra_set=_connectors_env_secret_set(chart, ctx),
        )
        print(f"Deployed {chart.name} to namespace {chart.namespace(ctx.namespace)}")

    run_on_each_chart(charts, deploy_one)


def _connectors_env_secret_set(chart: Chart, ctx: helm.DeployContext) -> list[str] | None:
    if chart.name != "connectors":
        return None
    release_name = chart.release_name(chart.effective_tier(ctx.tier, ctx.environment))
    secret_name = kube.ensure_env_secret(
        release_name, chart.namespace(ctx.namespace), REPO_ROOT / ".env"
    )
    return [f"app-base.secrets.envSecretName={secret_name}"] if secret_name else None


def _wait_for_rollouts(charts: list[Chart], ctx: helm.DeployContext, timeout: str) -> None:
    with ThreadPoolExecutor(max_workers=len(charts) or 1) as pool:
        futures = []
        for chart in charts:
            kind = chart.workload_kind()
            if not kind:
                continue
            futures.append(
                pool.submit(
                    kube.rollout_status,
                    chart.namespace(ctx.namespace),
                    kind,
                    chart.resource_name(ctx.tier),
                    timeout,
                )
            )
        for future in futures:
            future.result()
