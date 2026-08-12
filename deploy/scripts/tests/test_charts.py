"""Exercises Chart resolution against the repo's real k8s/ chart tree — these methods
are pure functions of that tree, so testing against the real thing (rather than a
fixture copy) is what actually protects against regressions in it."""

from __future__ import annotations

import pytest

from deployae.charts import Chart, TierRequiredError, resolve_charts


def test_app_chart_namespace_falls_back_to_parent() -> None:
    assert Chart("catalog").namespace() == "agent-engine"


def test_infra_chart_namespace_falls_back_to_parent() -> None:
    assert Chart("mongodb").namespace() == "infra"


def test_namespace_override_wins() -> None:
    assert Chart("catalog").namespace("custom-ns") == "custom-ns"


def test_app_chart_release_name_requires_tier() -> None:
    with pytest.raises(TierRequiredError):
        Chart("catalog").release_name(None)


def test_app_chart_release_name() -> None:
    assert Chart("catalog").release_name("local") == "agent-engine-catalog-local"


def test_global_properties_release_name_requires_environment() -> None:
    with pytest.raises(TierRequiredError):
        Chart("global-properties").release_name(None)


def test_global_properties_release_name_keyed_by_environment() -> None:
    assert Chart("global-properties").release_name("local") == "agent-engine-global-properties-local"


def test_infra_chart_release_name_includes_tier() -> None:
    assert Chart("mongodb").release_name("local") == "agent-engine-mongodb-local"


def test_infra_chart_release_name_without_tier() -> None:
    assert Chart("mongodb").release_name(None) == "agent-engine-mongodb"


def test_effective_tier_routes_environment_for_global_properties() -> None:
    assert Chart("global-properties").effective_tier("local", "staging") == "staging"


def test_effective_tier_routes_tier_for_everyone_else() -> None:
    assert Chart("catalog").effective_tier("local", "staging") == "local"


def test_namespace_set_is_none_for_app_charts() -> None:
    assert Chart("catalog").namespace_set("agent-engine") is None


def test_namespace_set_for_infra_charts() -> None:
    assert Chart("mongodb").namespace_set("infra") == "namespace=infra"


def test_env_set_for_app_charts() -> None:
    assert Chart("catalog").env_set("local") == "global.env=local"


def test_env_set_for_global_properties() -> None:
    assert Chart("global-properties").env_set("local") == "env=local"


def test_env_set_for_infra_charts_is_none() -> None:
    assert Chart("mongodb").env_set("local") is None


def test_env_set_without_environment_is_none() -> None:
    assert Chart("catalog").env_set(None) is None


def test_values_overlay_file_uses_envs_dir_for_global_properties() -> None:
    overlay = Chart("global-properties").values_overlay_file("local")
    assert overlay is not None
    assert overlay.parent.parent.name == "envs"


def test_values_overlay_file_uses_tiers_dir_for_others() -> None:
    overlay = Chart("mongodb").values_overlay_file("local")
    assert overlay is not None
    assert overlay.parent.parent.name == "tiers"


def test_values_overlay_file_missing_tier_returns_none() -> None:
    assert Chart("catalog").values_overlay_file(None) is None


def test_values_overlay_file_nonexistent_tier_returns_none() -> None:
    assert Chart("catalog").values_overlay_file("nonexistent-tier-xyz") is None


def test_resource_name_app_chart() -> None:
    assert Chart("catalog").resource_name("local") == "catalog-local"


def test_resource_name_infra_chart_includes_tier() -> None:
    assert Chart("mongodb").resource_name("local") == "mongodb-local"


def test_resource_name_infra_chart_without_tier() -> None:
    assert Chart("mongodb").resource_name(None) == "mongodb"


def test_resource_name_app_chart_requires_tier() -> None:
    with pytest.raises(TierRequiredError):
        Chart("catalog").resource_name(None)


def test_workload_kind_statefulset_app_chart() -> None:
    assert Chart("agent").workload_kind() == "statefulset"


def test_workload_kind_deployment_app_chart() -> None:
    assert Chart("catalog").workload_kind() == "deployment"


def test_workload_kind_global_properties_is_none() -> None:
    assert Chart("global-properties").workload_kind() is None


def test_workload_kind_infra_statefulset() -> None:
    assert Chart("mongodb").workload_kind() == "statefulset"


def test_workload_kind_infra_deployment() -> None:
    assert Chart("qdrant").workload_kind() == "deployment"


def test_resolve_charts_defaults_to_default_charts() -> None:
    names = [chart.name for chart in resolve_charts([])]
    assert names == ["global-properties", "agent", "catalog", "rest", "knowledge", "connectors"]


def test_resolve_charts_dedupes_preserving_order() -> None:
    names = [chart.name for chart in resolve_charts(["catalog", "rest", "catalog"])]
    assert names == ["catalog", "rest"]


def test_resolve_charts_rejects_unknown_name() -> None:
    with pytest.raises(ValueError, match="Unsupported chart"):
        resolve_charts(["bogus"])
