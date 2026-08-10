from __future__ import annotations

from pathlib import Path

from deployae.values import load_yaml, parent_chart_name


def test_load_yaml_missing_file_returns_empty(tmp_path: Path) -> None:
    assert load_yaml(tmp_path / "does-not-exist.yaml") == {}


def test_load_yaml_reads_real_content(tmp_path: Path) -> None:
    path = tmp_path / "values.yaml"
    path.write_text("namespace: agent-engine\nreplicas: 3\n")
    assert load_yaml(path) == {"namespace": "agent-engine", "replicas": 3}


def test_load_yaml_empty_file_returns_empty(tmp_path: Path) -> None:
    path = tmp_path / "values.yaml"
    path.write_text("")
    assert load_yaml(path) == {}


def test_parent_chart_name_returns_first_dependency(tmp_path: Path) -> None:
    path = tmp_path / "Chart.yaml"
    path.write_text(
        "apiVersion: v2\nname: x\ndependencies:\n  - name: app-base\n    version: 0.1.0\n"
    )
    assert parent_chart_name(path) == "app-base"


def test_parent_chart_name_no_dependencies(tmp_path: Path) -> None:
    path = tmp_path / "Chart.yaml"
    path.write_text("apiVersion: v2\nname: x\n")
    assert parent_chart_name(path) is None


def test_parent_chart_name_missing_file(tmp_path: Path) -> None:
    assert parent_chart_name(tmp_path / "missing.yaml") is None
