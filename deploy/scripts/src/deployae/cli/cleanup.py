"""`deployae cleanup` — removes Helm releases (and optionally PVCs/namespaces) for one or
more charts. Uninstalls run in parallel (they're independent releases); PVC and
namespace deletion each depend on the uninstall(s) for their own namespace finishing
first."""

from __future__ import annotations

import argparse
import asyncio

from deployae.charts import ALL_CHART_NAMES, ALL_CHARTS, Chart, resolve_charts
from deployae.stages import (
    DeleteNamespaceStage,
    DeletePvcsStage,
    RemoveLocalstackResourcesStage,
    Stage,
    UninstallChartStage,
    run_graph,
)


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "charts",
        nargs="*",
        choices=list(ALL_CHART_NAMES),
        default=[],
        help="Charts to remove (default: every chart, including infra)",
    )
    parser.add_argument("-t", "--tier", help="Tier to remove for the five app charts")
    parser.add_argument("-e", "--environment", help="Environment to remove for global-properties")
    parser.add_argument("-n", "--namespace", help="Override every selected chart's namespace")
    parser.add_argument(
        "--delete-namespace",
        action="store_true",
        help="Delete every namespace touched by the selected charts, after removal",
    )
    parser.add_argument(
        "--keep-volumes",
        action="store_true",
        help="Preserve PVCs (MongoDB and Postgres data volumes)",
    )


def run(args: argparse.Namespace) -> None:
    charts = resolve_charts(args.charts) if args.charts else list(ALL_CHARTS)
    asyncio.run(
        cleanup_charts(
            charts,
            tier=args.tier,
            environment=args.environment,
            namespace_override=args.namespace,
            delete_namespace=args.delete_namespace,
            keep_volumes=args.keep_volumes,
        )
    )


async def cleanup_charts(
    charts: list[Chart],
    *,
    tier: str | None,
    environment: str | None,
    namespace_override: str | None,
    delete_namespace: bool,
    keep_volumes: bool,
) -> None:
    namespace_by_chart = {chart.name: chart.namespace(namespace_override) for chart in charts}
    namespaces = sorted(set(namespace_by_chart.values()))

    uninstall_stages = [
        UninstallChartStage(
            name=f"uninstall-{chart.name}",
            chart=chart,
            tier=tier,
            environment=environment,
            namespace=namespace_by_chart[chart.name],
        )
        for chart in charts
    ]
    uninstalls_by_namespace: dict[str, list[UninstallChartStage]] = {
        namespace: [] for namespace in namespaces
    }
    for stage in uninstall_stages:
        uninstalls_by_namespace[namespace_by_chart[stage.chart.name]].append(stage)

    stages: list[Stage] = list(uninstall_stages)

    localstack_uninstall = next((s for s in uninstall_stages if s.chart.name == "localstack"), None)
    if localstack_uninstall:
        stages.append(
            RemoveLocalstackResourcesStage(
                name="remove-localstack-resources",
                depends_on=(localstack_uninstall,),
                namespace=namespace_by_chart["localstack"],
            )
        )

    pvc_stage_by_namespace: dict[str, DeletePvcsStage] = {}
    if not keep_volumes:
        for namespace in namespaces:
            stage = DeletePvcsStage(
                name=f"delete-pvcs-{namespace}",
                depends_on=tuple(uninstalls_by_namespace[namespace]),
                namespace=namespace,
            )
            pvc_stage_by_namespace[namespace] = stage
            stages.append(stage)

    if delete_namespace:
        for namespace in namespaces:
            depends_on = (
                (pvc_stage_by_namespace[namespace],)
                if namespace in pvc_stage_by_namespace
                else tuple(uninstalls_by_namespace[namespace])
            )
            stages.append(
                DeleteNamespaceStage(
                    name=f"delete-namespace-{namespace}", depends_on=depends_on, namespace=namespace
                )
            )

    await run_graph(stages)
