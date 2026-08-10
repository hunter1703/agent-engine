"""`deployae template` — renders manifests for one or more charts to stdout, in order."""

from __future__ import annotations

import argparse

from deployae import helm
from deployae.charts import resolve_charts
from deployae.cli.common import add_chart_selector, add_deploy_flags, build_context


def add_arguments(parser: argparse.ArgumentParser) -> None:
    add_chart_selector(parser)
    add_deploy_flags(parser)


def run(args: argparse.Namespace) -> None:
    ctx = build_context(args)
    for chart in resolve_charts(args.charts):
        helm.ensure_dependencies(chart)
        release_name = chart.release_name(chart.effective_tier(ctx.tier, ctx.environment))
        print(f"---\n# {chart.name} ({release_name})")
        print(helm.template(chart, ctx))
