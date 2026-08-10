"""`deployae cleanup` — removes Helm releases (and optionally PVCs/namespaces) for one or
more charts."""

from __future__ import annotations

import argparse

from deployae import helm, kube
from deployae.charts import ALL_CHART_NAMES, ALL_CHARTS, Chart, resolve_charts

# App charts (which depend on global-properties and the infra charts) come out first,
# shared/infra charts last.
_REMOVAL_ORDER = (
    "rest",
    "catalog",
    "agent",
    "knowledge",
    "connectors",
    "global-properties",
    "mongodb",
    "postgres",
    "localstack",
    "qdrant",
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
    selected = {chart.name for chart in charts}

    namespaces: set[str] = set()
    for name in _REMOVAL_ORDER:
        if name not in selected:
            continue
        chart = Chart(name)
        namespace = chart.namespace(args.namespace)
        namespaces.add(namespace)
        if helm.uninstall(chart, args.tier, args.environment, namespace):
            print(f"Removed {name} from namespace {namespace}")

    print(f"Removed selected releases from namespaces: {', '.join(sorted(namespaces))}")

    if "localstack" in selected:
        localstack_ns = Chart("localstack").namespace(args.namespace)
        kube.delete_by_label(localstack_ns, "app.kubernetes.io/name=localstack")
        print(f"Removed localstack resources from namespace {localstack_ns}")

    if not args.keep_volumes:
        print("Deleting PVCs (data volumes)...")
        for namespace in namespaces:
            kube.delete_pvcs(namespace)
        print(f"Deleted all PVCs from namespaces: {', '.join(sorted(namespaces))}")

    if args.delete_namespace:
        for namespace in namespaces:
            kube.delete_namespace(namespace)
        print(f"Deleted namespaces: {', '.join(sorted(namespaces))}")
