"""Argument-parsing helpers shared by the apply/lint/template/deploy subcommands."""

from __future__ import annotations

import argparse
import subprocess
from collections.abc import Callable, Iterable
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import TypeVar

from deployae import output
from deployae.charts import ALL_CHART_NAMES, K8S_DIR, Chart
from deployae.helm import DeployContext

REPO_ROOT = K8S_DIR.parent
T = TypeVar("T")


def add_chart_selector(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "charts",
        nargs="*",
        choices=list(ALL_CHART_NAMES),
        default=[],
        help="Charts to target (default: global-properties + the five app charts)",
    )


def add_deploy_flags(parser: argparse.ArgumentParser, *, include_image_tag: bool = True) -> None:
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
    if include_image_tag:
        parser.add_argument(
            "--image-tag",
            default=default_image_tag(),
            help="Override the app image tag for app charts",
        )


def build_context(args: argparse.Namespace) -> DeployContext:
    return DeployContext(
        tier=args.tier,
        environment=args.environment,
        namespace=args.namespace,
        extra_values_files=args.values_files,
        set_arguments=args.set_arguments,
        image_tag=getattr(args, "image_tag", None),
    )


def run_on_each_chart(charts: Iterable[Chart], fn: Callable[[Chart], None]) -> None:
    """Runs fn(chart) for every chart concurrently — these are all blocking subprocess
    calls (helm/kubectl/docker), so threads are the natural fit, not asyncio. Collects
    every failure before raising, so one chart's error doesn't hide another's."""
    charts = list(charts)
    with ThreadPoolExecutor(max_workers=len(charts) or 1) as pool:
        futures = {pool.submit(fn, chart): chart for chart in charts}
        failures: list[tuple[str, Exception]] = []
        for future in as_completed(futures):
            try:
                future.result()
            except Exception as error:
                failures.append((futures[future].name, error))
        if failures:
            for name, error in failures:
                output.error(f"{name}: {error}")
            raise SystemExit(1)


def default_image_tag() -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "--short", "HEAD"], capture_output=True, text=True, cwd=REPO_ROOT
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except FileNotFoundError:
        pass
    return "dev"
