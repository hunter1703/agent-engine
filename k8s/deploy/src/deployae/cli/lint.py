"""`deployae lint` — helm lint across one or more charts."""

from __future__ import annotations

import argparse

from deployae import helm
from deployae.charts import Chart, resolve_charts
from deployae.cli.common import (
    add_chart_selector,
    add_deploy_flags,
    build_context,
    run_on_each_chart,
)


def add_arguments(parser: argparse.ArgumentParser) -> None:
    add_chart_selector(parser)
    add_deploy_flags(parser)


def run(args: argparse.Namespace) -> None:
    ctx = build_context(args)

    def lint_one(chart: Chart) -> None:
        helm.ensure_dependencies(chart)
        helm.lint(chart, ctx)
        print(f"Linted {chart.name}")

    run_on_each_chart(resolve_charts(args.charts), lint_one)
