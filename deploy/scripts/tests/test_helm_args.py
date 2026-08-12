"""Tests deployae.helm's argument-list construction — the correctness-critical bit
a shell word-split couldn't guarantee, since --set values here are real list elements."""

from __future__ import annotations

from deployae.charts import Chart
from deployae.helm import DeployContext, value_flags


def test_app_chart_value_flags_include_env_and_image_tag() -> None:
    flags = value_flags(
        Chart("catalog"), DeployContext(tier="local", environment="local", image_tag="abc123")
    )
    assert "global.env=local" in flags
    assert "global.imageTag=abc123" in flags
    assert not any(flag.startswith("namespace=") for flag in flags)


def test_infra_chart_value_flags_include_namespace_set_not_env() -> None:
    flags = value_flags(Chart("mongodb"), DeployContext(tier="local", environment="local"))
    assert "namespace=infra" in flags
    assert not any(flag.startswith("global.env=") for flag in flags)


def test_global_properties_value_flags_include_both_env_and_namespace() -> None:
    # global-properties isn't one of the five app charts, so — like the infra charts —
    # its namespace is still script-overridable via --set, on top of its env identity.
    flags = value_flags(Chart("global-properties"), DeployContext(environment="local"))
    assert "env=local" in flags
    assert any(flag.startswith("namespace=") for flag in flags)


def test_value_flags_includes_tier_overlay_file() -> None:
    flags = value_flags(Chart("catalog"), DeployContext(tier="local"))
    overlay_index = flags.index("-f")
    assert flags[overlay_index + 1].endswith("catalog/tiers/local/values.yaml")


def test_value_flags_preserves_values_containing_spaces() -> None:
    flags = value_flags(
        Chart("catalog"), DeployContext(tier="local", set_arguments=["some.key=value with spaces"])
    )
    assert "some.key=value with spaces" in flags


def test_value_flags_includes_rollout_revision_as_set_string() -> None:
    flags = value_flags(Chart("catalog"), DeployContext(tier="local", rollout_revision="12345"))
    assert "--set-string" in flags
    assert "global.rolloutRevision=12345" in flags


def test_value_flags_omits_rollout_revision_for_infra_charts() -> None:
    flags = value_flags(Chart("mongodb"), DeployContext(tier="local", rollout_revision="12345"))
    assert "--set-string" not in flags
