from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from deployae.stages.seed import (
    SeedInfraConfigStage,
    _topological_agent_order,
    _upsert_infra_config,
)


def _infra_stage(environment: str = "local") -> SeedInfraConfigStage:
    return SeedInfraConfigStage(
        name="seed-infra-config", tier=None, environment=environment, namespace_override=None
    )


def test_environment_configs_reads_every_json_file_in_the_env_dir(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("deployae.charts.CONFIGS_DIR", tmp_path)
    infra_dir = tmp_path / "local" / "infra"
    infra_dir.mkdir(parents=True)
    (infra_dir / "SQL.json").write_text(json.dumps([{"id": "SQL:default"}]))
    (infra_dir / "VECTOR.json").write_text(json.dumps([{"id": "QDRANT:default"}]))

    ids = {doc["id"] for doc in _infra_stage()._environment_configs()}
    assert ids == {"SQL:default", "QDRANT:default"}


def test_environment_configs_ignores_non_json_files(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("deployae.charts.CONFIGS_DIR", tmp_path)
    infra_dir = tmp_path / "local" / "infra"
    infra_dir.mkdir(parents=True)
    (infra_dir / "SQL.json").write_text(json.dumps([{"id": "SQL:default"}]))
    (infra_dir / "README.md").write_text("not a config")

    docs = _infra_stage()._environment_configs()
    assert [doc["id"] for doc in docs] == ["SQL:default"]


def test_environment_configs_missing_environment_returns_empty(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr("deployae.charts.CONFIGS_DIR", tmp_path)
    assert _infra_stage("staging")._environment_configs() == []


class _FakeCollection:
    def __init__(self) -> None:
        self.docs: dict[str, Any] = {}

    def find_one(self, query: dict[str, Any]) -> Any:
        return self.docs.get(query["_id"])

    def replace_one(self, query: dict[str, Any], payload: dict[str, Any], upsert: bool) -> None:
        self.docs[query["_id"]] = payload


def test_upsert_preserves_created_time_across_updates() -> None:
    collection = _FakeCollection()
    _upsert_infra_config(collection, {"id": "x", "type": "sql", "jdbcUrl": "a"})
    first_created = collection.docs["x"]["createdTime"]

    _upsert_infra_config(collection, {"id": "x", "type": "sql", "jdbcUrl": "b"})
    assert collection.docs["x"]["createdTime"] == first_created
    assert collection.docs["x"]["jdbcUrl"] == "b"


def test_upsert_requires_id() -> None:
    with pytest.raises(ValueError, match="missing id"):
        _upsert_infra_config(_FakeCollection(), {"type": "sql"})


def test_topological_agent_order_parents_after_children(tmp_path: Path) -> None:
    (tmp_path / "leaf1.json").write_text(json.dumps({"id": "leaf1"}))
    (tmp_path / "leaf2.json").write_text(json.dumps({"id": "leaf2"}))
    (tmp_path / "parent.json").write_text(
        json.dumps({"id": "parent", "subAgentIds": ["leaf1", "leaf2"]})
    )

    files = [tmp_path / "parent.json", tmp_path / "leaf1.json", tmp_path / "leaf2.json"]
    order_ids = [json.loads(f.read_text())["id"] for f in _topological_agent_order(files)]

    assert order_ids.index("parent") > order_ids.index("leaf1")
    assert order_ids.index("parent") > order_ids.index("leaf2")


def test_topological_agent_order_handles_unknown_dependency(tmp_path: Path) -> None:
    (tmp_path / "a.json").write_text(json.dumps({"id": "a", "subAgentIds": ["not-in-this-batch"]}))
    ordered = _topological_agent_order([tmp_path / "a.json"])
    assert [f.name for f in ordered] == ["a.json"]


def test_topological_agent_order_no_dependencies(tmp_path: Path) -> None:
    (tmp_path / "a.json").write_text(json.dumps({"id": "a"}))
    (tmp_path / "b.json").write_text(json.dumps({"id": "b"}))
    ordered = _topological_agent_order([tmp_path / "a.json", tmp_path / "b.json"])
    assert {f.name for f in ordered} == {"a.json", "b.json"}


def test_topological_agent_order_detects_duplicate_id_collision(tmp_path: Path) -> None:
    """A same id declared twice collapses to one entry — this is exactly the class of
    bug found in the real configs/agents/ directory (two files declaring the same id),
    where one file's content silently overwrites the other's seeded document."""
    (tmp_path / "first.json").write_text(json.dumps({"id": "shared"}))
    (tmp_path / "second.json").write_text(json.dumps({"id": "shared"}))
    ordered = _topological_agent_order([tmp_path / "first.json", tmp_path / "second.json"])
    assert len(ordered) == 1
