"""Thin subprocess wrapper around the `helm` binary. Argument lists are built as real
Python lists, so a value containing a space is passed through intact rather than being
mis-split across flags."""

from __future__ import annotations

import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from deployae.charts import Chart

DEFAULT_TIMEOUT = "10m"


class HelmError(RuntimeError):
    """A `helm` invocation exited non-zero."""


@dataclass(frozen=True)
class DeployContext:
    """Cross-cutting flags shared by apply/lint/template: which tier/environment/
    namespace to target, plus any caller-supplied extra values files or --set overrides."""

    tier: str | None = None
    environment: str | None = None
    namespace: str | None = None
    extra_values_files: list[Path] = field(default_factory=list)
    set_arguments: list[str] = field(default_factory=list)
    image_tag: str | None = None
    rollout_revision: str | None = None


def _run(args: list[str]) -> None:
    result = subprocess.run(["helm", *args])
    if result.returncode != 0:
        raise HelmError(f"helm {' '.join(args)} exited {result.returncode}")


def _output(args: list[str]) -> str:
    result = subprocess.run(["helm", *args], capture_output=True, text=True)
    if result.returncode != 0:
        raise HelmError(f"helm {' '.join(args)} exited {result.returncode}:\n{result.stderr}")
    return result.stdout


def ensure_dependencies(chart: Chart) -> None:
    """`helm dependency build` — required after any change to a shared chart (app-base/
    infra-base) before re-rendering its consumers."""
    subprocess.run(
        ["helm", "dependency", "build", str(chart.path)], check=True, capture_output=True
    )


def value_flags(chart: Chart, ctx: DeployContext) -> list[str]:
    """-f/--set flags for one chart: namespace/environment overrides, image tag +
    rollout revision (app charts only), the tier/environment overlay file, then any
    caller-supplied extra -f files and --set overrides."""
    flags: list[str] = []

    namespace_set = chart.namespace_set(chart.namespace(ctx.namespace))
    if namespace_set:
        flags += ["--set", namespace_set]

    tier_set = chart.tier_set(ctx.tier)
    if tier_set:
        flags += ["--set", tier_set]

    env_set = chart.env_set(ctx.environment)
    if env_set:
        flags += ["--set", env_set]

    if chart.is_app_chart:
        if ctx.image_tag:
            flags += ["--set", f"global.imageTag={ctx.image_tag}"]
        if ctx.rollout_revision:
            flags += ["--set-string", f"global.rolloutRevision={ctx.rollout_revision}"]

    overlay = chart.values_overlay_file(chart.effective_tier(ctx.tier, ctx.environment))
    if overlay:
        flags += ["-f", str(overlay)]
    for values_file in ctx.extra_values_files:
        flags += ["-f", str(values_file)]
    for set_arg in ctx.set_arguments:
        flags += ["--set", set_arg]

    return flags


def lint(chart: Chart, ctx: DeployContext) -> None:
    args = ["lint", str(chart.path), "--namespace", chart.namespace(ctx.namespace)]
    args += value_flags(chart, ctx)
    _run(args)


def template(chart: Chart, ctx: DeployContext) -> str:
    release_name = chart.release_name(chart.effective_tier(ctx.tier, ctx.environment))
    args = [
        "template",
        release_name,
        str(chart.path),
        "--namespace",
        chart.namespace(ctx.namespace),
    ]
    args += value_flags(chart, ctx)
    return _output(args)


def upgrade_install(
    chart: Chart,
    ctx: DeployContext,
    *,
    dry_run: bool = False,
    atomic: bool = True,
    timeout: str = DEFAULT_TIMEOUT,
    extra_set: list[str] | None = None,
) -> str | None:
    """Deploys (or, with dry_run, just renders) one chart. Returns the rendered
    manifest text when dry_run is set, otherwise None."""
    release_name = chart.release_name(chart.effective_tier(ctx.tier, ctx.environment))
    chart_ns = chart.namespace(ctx.namespace)
    if dry_run:
        args = ["template", release_name, str(chart.path), "--namespace", chart_ns]
    else:
        args = [
            "upgrade",
            "--install",
            release_name,
            str(chart.path),
            "--namespace",
            chart_ns,
            "--create-namespace",
            "--timeout",
            timeout,
        ]
        if atomic:
            args.append("--atomic")

    args += value_flags(chart, ctx)
    for set_arg in extra_set or []:
        args += ["--set", set_arg]

    if dry_run:
        return _output(args)
    _run(args)
    return None


def uninstall(chart: Chart, tier: str | None, environment: str | None, namespace: str) -> bool:
    """Returns True if a release was actually removed. Uninstalling a release that was
    never installed isn't an error worth surfacing, so a non-zero exit is swallowed."""
    release_name = chart.release_name(chart.effective_tier(tier, environment))
    result = subprocess.run(
        ["helm", "uninstall", release_name, "--namespace", namespace],
        capture_output=True,
    )
    return result.returncode == 0


def ensure_ingress_controller() -> None:
    """Idempotent: `helm upgrade --install` is a no-op when nothing changed, so this is
    safe to call on every deploy. Needed for any chart's ingress.yaml to route traffic."""
    subprocess.run(
        ["helm", "repo", "add", "ingress-nginx", "https://kubernetes.github.io/ingress-nginx"],
        capture_output=True,
    )
    subprocess.run(["helm", "repo", "update", "ingress-nginx"], check=True, capture_output=True)
    _run(
        [
            "upgrade",
            "--install",
            "ingress-nginx",
            "ingress-nginx/ingress-nginx",
            "--namespace",
            "ingress-nginx",
            "--create-namespace",
        ]
    )
