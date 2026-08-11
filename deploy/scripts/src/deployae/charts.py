"""Chart registry: metadata and name/namespace/overlay resolution for every Helm chart
under k8s/."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from deployae.values import load_yaml, parent_chart_name


def _find_deploy_dir() -> Path:
    """Locates this checkout's deploy/ directory, checked in order:

    1. DEPLOYAE_DEPLOY_DIR, if set — an explicit escape hatch, always wins.
    2. Walk up from the current working directory looking for a deploy/k8s
       directory, the same way `git`/`npm` locate a project root by walking up
       looking for .git/package.json. This is what makes `deployae` work when
       installed globally via `uv tool install` and invoked from anywhere inside
       the repo — __file__ then lives in an isolated site-packages tree that has
       nothing to do with any particular checkout, so it can't be used to find one.
    3. The parent-counting trick, for the one case cwd-walking can't cover: running
       straight from the deploy/scripts source tree (e.g. `uv run`) from a working
       directory outside the repo entirely.
    """
    if "DEPLOYAE_DEPLOY_DIR" in os.environ:
        return Path(os.environ["DEPLOYAE_DEPLOY_DIR"]).resolve()

    for candidate in (Path.cwd(), *Path.cwd().resolve().parents):
        deploy_dir = candidate / "deploy"
        if (deploy_dir / "k8s").is_dir():
            return deploy_dir

    # deploy/scripts/src/deployae/charts.py
    #   parents[0]=deployae/ parents[1]=src/ parents[2]=scripts/ parents[3]=deploy/
    from_source = Path(__file__).resolve().parents[3]
    if (from_source / "k8s").is_dir():
        return from_source

    raise RuntimeError(
        "Could not locate the deploy/ directory. Run deployae from inside the "
        "agent-engine repo, or set DEPLOYAE_DEPLOY_DIR explicitly."
    )


DEPLOY_DIR = _find_deploy_dir()
REPO_ROOT = (
    Path(os.environ["DEPLOYAE_REPO_ROOT"])
    if "DEPLOYAE_REPO_ROOT" in os.environ
    else DEPLOY_DIR.parent
)
K8S_DIR = DEPLOY_DIR / "k8s"
CONFIGS_DIR = DEPLOY_DIR / "configs"

APP_CHART_NAMES = ("agent", "catalog", "rest", "knowledge", "connectors")
INFRA_CHART_NAMES = ("mongodb", "postgres", "localstack", "qdrant")
ALL_CHART_NAMES = ("global-properties", *APP_CHART_NAMES, *INFRA_CHART_NAMES)
DEFAULT_CHART_NAMES = ("global-properties", *APP_CHART_NAMES)


class TierRequiredError(RuntimeError):
    """Raised when an operation needs a tier that wasn't supplied."""


@dataclass(frozen=True)
class Chart:
    name: str

    @property
    def path(self) -> Path:
        return K8S_DIR / self.name

    @property
    def is_app_chart(self) -> bool:
        return self.name in APP_CHART_NAMES

    @property
    def uses_environment_overlay(self) -> bool:
        """global-properties has exactly one release per environment, not per app-tier —
        its overlay lives under envs/, every other chart's under tiers/."""
        return self.name == "global-properties"

    def namespace(self, override: str | None = None) -> str:
        """Precedence: explicit override > this chart's own values.yaml > its parent
        chart's (app-base/infra-base) values.yaml. Read live, not hardcoded, so the
        chart files stay the single source of truth."""
        if override:
            return override
        own = load_yaml(self.path / "values.yaml").get("namespace")
        if own:
            return own
        parent = parent_chart_name(self.path / "Chart.yaml")
        if parent:
            return load_yaml(K8S_DIR / parent / "values.yaml").get("namespace", "")
        return ""

    def namespace_set(self, namespace: str) -> str | None:
        """--set override for the chart's `namespace` value. App charts always deploy to
        whatever namespace their own chart values declare — nothing gets injected for them."""
        return None if self.is_app_chart else f"namespace={namespace}"

    def tier_set(self, tier: str | None) -> str | None:
        """--set override carrying the tier into infra charts so {{ .Values.tier }} is
        populated and the instance helper produces a tier-suffixed resource name."""
        if self.is_app_chart or self.name == "global-properties" or not tier:
            return None
        return f"tier={tier}"

    def release_name(self, tier_or_env: str | None) -> str:
        """`tier_or_env` is whatever `effective_tier()` resolved for this chart: the
        tier for app charts, or the environment for global-properties.

        global-properties' namespace is fixed (no per-environment suffix — see
        `namespace_set`), which means two environments' ConfigMaps can legitimately
        coexist in the same namespace (that's exactly why the ConfigMap itself is named
        `global-properties-<env>-configmap`, not just `global-properties-configmap`). A
        release name that didn't vary by environment would make Helm treat deploying a
        second environment as *upgrading* the first one's release — and delete its
        ConfigMap as a resource no longer in the new render. So global-properties needs
        environment as its release identity for exactly the same reason app charts need
        tier as theirs.
        """
        if self.name == "global-properties":
            if not tier_or_env:
                raise TierRequiredError(
                    "ENVIRONMENT must be set (-e/--environment) to deploy or reference "
                    "'global-properties'"
                )
            return f"agent-engine-global-properties-{tier_or_env}"
        if self.is_app_chart:
            if not tier_or_env:
                raise TierRequiredError(
                    f"TIER must be set (-t/--tier) to deploy or reference '{self.name}'"
                )
            return f"agent-engine-{self.name}-{tier_or_env}"
        if tier_or_env:
            return f"agent-engine-{self.name}-{tier_or_env}"
        return f"agent-engine-{self.name}"

    def env_set(self, environment: str | None) -> str | None:
        """--set override carrying the selected environment into the chart's own
        values. `environment` is a general axis, not something global-properties owns —
        it's the reverse: global-properties is simply the first chart whose *release
        identity* is keyed by it (via `effective_tier`/`release_name`), and app charts
        are the first consumers that need to know its *value* (to pick which
        environment's global-properties ConfigMap to mount). Any future chart that needs
        to know which environment it's running in would read the same value."""
        if not environment:
            return None
        if self.name == "global-properties":
            return f"env={environment}"
        return f"global.env={environment}" if self.is_app_chart else None

    def effective_tier(self, tier: str | None, environment: str | None) -> str | None:
        """The value this chart's identity (release name, values overlay file) is
        actually keyed by: environment for global-properties, tier for everyone else."""
        return environment if self.uses_environment_overlay else tier

    def values_overlay_file(self, tier_or_env: str | None) -> Path | None:
        if not tier_or_env:
            return None
        subdir = "envs" if self.uses_environment_overlay else "tiers"
        candidate = self.path / subdir / tier_or_env / "values.yaml"
        return candidate if candidate.is_file() else None

    def resource_name(self, tier: str | None) -> str:
        """The literal Deployment/StatefulSet name — distinct from release_name, which
        carries Helm's own `agent-engine-` release-tracking prefix. Mirrors
        release_name()'s tier handling: required for app charts, optional (falls back
        to the plain chart name) for infra charts, matching the infra-base `instance`
        Helm helper's own `{{- if .Values.tier -}}` fallback."""
        if self.name == "global-properties":
            return self.name
        if self.is_app_chart:
            if not tier:
                raise TierRequiredError(f"TIER must be set (-t/--tier) for '{self.name}'")
            return f"{self.name}-{tier}"
        return f"{self.name}-{tier}" if tier else self.name

    def workload_kind(self) -> str | None:
        """Which kubectl rollout status to wait on. None for global-properties (a
        ConfigMap, nothing to roll out). App charts declare this themselves via their own
        values.yaml `type:` field; infra charts have a fixed, non-configurable shape."""
        if self.name == "global-properties":
            return None
        if self.is_app_chart:
            own_app_base = load_yaml(self.path / "values.yaml").get("app-base", {})
            return own_app_base.get("type", "deployment")
        return "statefulset" if self.name in ("mongodb", "postgres") else "deployment"


ALL_CHARTS = [Chart(name) for name in ALL_CHART_NAMES]
DEFAULT_CHARTS = [Chart(name) for name in DEFAULT_CHART_NAMES]
CHARTS_BY_NAME = {chart.name: chart for chart in ALL_CHARTS}


def resolve_charts(requested: list[str]) -> list[Chart]:
    """Validate and de-duplicate requested chart names, defaulting to DEFAULT_CHARTS
    when none were requested."""
    if not requested:
        return list(DEFAULT_CHARTS)
    unknown = [name for name in requested if name not in CHARTS_BY_NAME]
    if unknown:
        valid = ", ".join(ALL_CHART_NAMES)
        raise ValueError(f"Unsupported chart(s): {', '.join(unknown)}. Valid charts: {valid}")
    seen: dict[str, Chart] = {}
    for name in requested:
        seen.setdefault(name, CHARTS_BY_NAME[name])
    return list(seen.values())


def env_config_dir(environment: str, subdir: str) -> Path:
    """Path to one environment's seed-data directory, e.g. env_config_dir("prod", "infra")
    -> deploy/configs/env/prod/infra. Seed data is inserted as-is, one file per config
    type — no base/overlay merge, since each environment has exactly one copy."""
    return CONFIGS_DIR / "env" / environment / subdir
