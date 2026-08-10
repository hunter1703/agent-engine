"""Chart registry: metadata and name/namespace/overlay resolution for every Helm chart
under k8s/."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from deployae.values import load_yaml, parent_chart_name

K8S_DIR = Path(__file__).resolve().parents[3]

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
        carries Helm's own `agent-engine-` release-tracking prefix."""
        if self.is_app_chart:
            if not tier:
                raise TierRequiredError(f"TIER must be set (-t/--tier) for '{self.name}'")
            return f"{self.name}-{tier}"
        return self.name

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
