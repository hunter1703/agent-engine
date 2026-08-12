"""`deployae cleanup` — removes Helm releases (and optionally PVCs/namespaces) for one or
more charts. Uninstalls run in parallel (they're independent releases); PVC and
namespace deletion each depend on the uninstall(s) for their own namespace finishing
first."""

from __future__ import annotations

import argparse
import asyncio

from deployae.charts import ALL_CHART_NAMES, APP_CHART_NAMES, CHARTS_BY_NAME, Chart
from deployae.stages import (
    DeleteNamespaceStage,
    DeletePvcsStage,
    RemoveLocalstackResourcesStage,
    Stage,
    UninstallChartStage,
    run_graph,
)


def add_arguments(parser: argparse.ArgumentParser) -> None:
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--app", action="store_true", help="Remove only app services")
    group.add_argument("--app-infra", action="store_true", help="Remove app + infra services")
    group.add_argument("--all", action="store_true", help="Remove everything including persistence (PVCs, Namespaces, Ingress)")

    parser.add_argument("-t", "--tier", help="Tier to remove for the app/infra charts")
    parser.add_argument("-n", "--namespace", help="Override every selected chart's namespace")


def run(args: argparse.Namespace) -> None:
    if args.all:
        print("Nuclear cleanup initiated: deleting namespaces agent-engine, infra, ingress-nginx...")
        # For --all, we don't need to specify tier or run helm uninstalls individually,
        # deleting the namespaces will cascade and delete everything in them (including PVCs and Helm releases).
        asyncio.run(run_graph([
            DeleteNamespaceStage(name="delete-ns-agent-engine", namespace="agent-engine"),
            DeleteNamespaceStage(name="delete-ns-infra", namespace="infra"),
            DeleteNamespaceStage(name="delete-ns-ingress", namespace="ingress-nginx"),
        ]))
        return

    if not args.tier:
        print("Error: -t/--tier is required for --app and --app-infra")
        import sys
        sys.exit(1)

    if args.app:
        chart_names = APP_CHART_NAMES
    else: # args.app_infra
        chart_names = (*APP_CHART_NAMES, *INFRA_CHART_NAMES)

    charts = [CHARTS_BY_NAME[n] for n in chart_names]

    asyncio.run(
        cleanup_charts(
            charts,
            tier=args.tier,
            namespace_override=args.namespace,
        )
    )


async def cleanup_charts(
    charts: list[Chart],
    *,
    tier: str,
    namespace_override: str | None,
) -> None:
    namespace_by_chart = {chart.name: chart.namespace(namespace_override) for chart in charts}
    namespaces = sorted(set(namespace_by_chart.values()))

    uninstall_stages = [
        UninstallChartStage(
            name=f"uninstall-{chart.name}",
            chart=chart,
            tier=tier,
            environment=None,
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



    await run_graph(stages)
