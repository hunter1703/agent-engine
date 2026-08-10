from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pytest

from deployae.seeding.infra import InfraSeedContext, _merge_json_dir, apply_overrides, upsert


def _ctx(**overrides: Any) -> InfraSeedContext:
    defaults: dict[str, Any] = {
        "catalog_host": "catalog.agent-engine.svc.cluster.local",
        "catalog_grpc_port": 9000,
        "agent_host": "agent.agent-engine.svc.cluster.local",
        "agent_grpc_port": 9001,
        "agent_seed_nodes": [
            "pekko://agent@agent-0.agent-internal.agent-engine.svc.cluster.local:2552"
        ],
        "agent_cluster_name": "agent",
        "pekko_snapshot_threshold": 100,
        "sql_jdbc_url": "jdbc:postgresql://postgres.infra.svc.cluster.local:5432/agent_engine_events",
        "knowledge_host": "knowledge.agent-engine.svc.cluster.local",
        "knowledge_grpc_port": 9002,
        "localstack_endpoint": "http://localstack.infra.svc.cluster.local:4566",
        "qdrant_host": "qdrant.infra.svc.cluster.local",
    }
    defaults.update(overrides)
    return InfraSeedContext(**defaults)


def test_apply_overrides_pekko() -> None:
    doc = {"type": "PEKKO", "clusterName": "old", "snapshotThreshold": 1, "seedNodes": []}
    ctx = _ctx()
    result = apply_overrides(doc, ctx)
    assert result["seedNodes"] == ctx.agent_seed_nodes
    assert result["clusterName"] == "agent"
    assert result["snapshotThreshold"] == 100


def test_apply_overrides_sql() -> None:
    result = apply_overrides({"type": "sql", "jdbcUrl": "old"}, _ctx())
    assert result["jdbcUrl"] == _ctx().sql_jdbc_url


def test_apply_overrides_microservice_catalog() -> None:
    doc = {"type": "microservice", "serverId": "catalog", "host": "old", "port": 1}
    result = apply_overrides(doc, _ctx())
    assert result["host"] == "catalog.agent-engine.svc.cluster.local"
    assert result["port"] == 9000


def test_apply_overrides_microservice_unknown_server_id_untouched() -> None:
    doc = {"type": "microservice", "serverId": "unknown", "host": "old", "port": 1}
    assert apply_overrides(doc, _ctx()) == doc


def test_apply_overrides_default_models_only_overrides_provided_ids() -> None:
    doc = {
        "type": "default_models",
        "titleModelId": "old-title",
        "compactionModelId": "old-compaction",
    }
    result = apply_overrides(doc, _ctx(title_model_id="new-title"))
    assert result["titleModelId"] == "new-title"
    assert result["compactionModelId"] == "old-compaction"


def test_apply_overrides_cloudstorage() -> None:
    result = apply_overrides({"type": "cloudstorage", "endpointUrl": "old"}, _ctx())
    assert result["endpointUrl"] == "http://localstack.infra.svc.cluster.local:4566"


def test_apply_overrides_qdrant() -> None:
    result = apply_overrides({"type": "qdrant", "host": "old"}, _ctx())
    assert result["host"] == "qdrant.infra.svc.cluster.local"


def test_apply_overrides_unknown_type_passthrough() -> None:
    doc = {"type": "something-else", "value": 42}
    assert apply_overrides(doc, _ctx()) == doc


def test_merge_json_dir_overlay_replaces_same_named_file(tmp_path: Path) -> None:
    base_dir, overlay_dir = tmp_path / "base", tmp_path / "overlay"
    base_dir.mkdir()
    overlay_dir.mkdir()
    (base_dir / "a.json").write_text(json.dumps([{"id": "a", "from": "base"}]))
    (overlay_dir / "a.json").write_text(json.dumps([{"id": "a", "from": "overlay"}]))
    (base_dir / "b.json").write_text(json.dumps([{"id": "b", "from": "base"}]))

    by_id = {doc["id"]: doc for doc in _merge_json_dir(base_dir, overlay_dir)}
    assert by_id["a"]["from"] == "overlay"
    assert by_id["b"]["from"] == "base"


def test_merge_json_dir_overlay_only_file_is_included(tmp_path: Path) -> None:
    base_dir, overlay_dir = tmp_path / "base", tmp_path / "overlay"
    base_dir.mkdir()
    overlay_dir.mkdir()
    (base_dir / "a.json").write_text(json.dumps([{"id": "a"}]))
    (overlay_dir / "c.json").write_text(json.dumps([{"id": "c"}]))

    ids = {doc["id"] for doc in _merge_json_dir(base_dir, overlay_dir)}
    assert ids == {"a", "c"}


def test_merge_json_dir_missing_overlay_dir_uses_base_only(tmp_path: Path) -> None:
    base_dir = tmp_path / "base"
    base_dir.mkdir()
    (base_dir / "a.json").write_text(json.dumps([{"id": "a"}]))

    docs = _merge_json_dir(base_dir, tmp_path / "does-not-exist")
    assert [doc["id"] for doc in docs] == ["a"]


class _FakeCollection:
    def __init__(self) -> None:
        self.docs: dict[str, Any] = {}

    def find_one(self, query: dict[str, Any]) -> Any:
        return self.docs.get(query["_id"])

    def replace_one(self, query: dict[str, Any], payload: dict[str, Any], upsert: bool) -> None:
        self.docs[query["_id"]] = payload


def test_upsert_preserves_created_time_across_updates() -> None:
    collection = _FakeCollection()
    upsert(collection, {"id": "x", "type": "sql", "jdbcUrl": "a"})
    first_created = collection.docs["x"]["createdTime"]

    upsert(collection, {"id": "x", "type": "sql", "jdbcUrl": "b"})
    assert collection.docs["x"]["createdTime"] == first_created
    assert collection.docs["x"]["jdbcUrl"] == "b"


def test_upsert_requires_id() -> None:
    with pytest.raises(ValueError, match="missing id"):
        upsert(_FakeCollection(), {"type": "sql"})
