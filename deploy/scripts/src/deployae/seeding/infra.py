"""Seeds configs/infra/*.json (merged with an optional tier overlay) into MongoDB's
INFRA.InfraConfig collection, over a temporary port-forward."""

from __future__ import annotations

import json
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pymongo import MongoClient

from deployae import kube
from deployae.charts import DEPLOY_DIR, K8S_DIR, Chart

DEFAULT_MONGODB_PORT = 27017


@dataclass(frozen=True)
class SeedInfraOptions:
    """CLI-tunable knobs for this seeding run."""

    agent_seed_node_count: int = 3
    agent_cluster_name: str = "agent"
    pekko_snapshot_threshold: int = 100
    title_model_id: str | None = None
    compaction_model_id: str | None = None
    evaluator_model_id: str | None = None
    embedding_model_id: str | None = None
    mongodb_port: int = DEFAULT_MONGODB_PORT
    postgres_port: int = 5432
    postgres_database: str = "agent_engine_events"


@dataclass(frozen=True)
class InfraSeedContext:
    """Live cluster facts the seeded config documents need to reference, resolved once
    up front so `apply_overrides` stays a pure, independently testable function."""

    catalog_host: str
    catalog_grpc_port: int
    agent_host: str
    agent_grpc_port: int
    agent_seed_nodes: list[str]
    agent_cluster_name: str
    pekko_snapshot_threshold: int
    sql_jdbc_url: str
    knowledge_host: str
    knowledge_grpc_port: int
    localstack_endpoint: str
    qdrant_host: str
    title_model_id: str | None = None
    compaction_model_id: str | None = None
    evaluator_model_id: str | None = None
    embedding_model_id: str | None = None


def _merge_json_dir(base_dir: Path, overlay_dir: Path) -> list[dict[str, Any]]:
    """Every base_dir/*.json array, with a same-named file under overlay_dir replacing
    the base version; overlay-only files are merged in too."""
    chosen: dict[str, Path] = {path.name: path for path in sorted(base_dir.glob("*.json"))}
    if overlay_dir.is_dir():
        for path in sorted(overlay_dir.glob("*.json")):
            chosen[path.name] = path
    docs: list[dict[str, Any]] = []
    for path in chosen.values():
        docs.extend(json.loads(path.read_text()))
    return docs


def merged_configs(tier: str) -> list[dict[str, Any]]:
    base_dir = DEPLOY_DIR / "configs" / "infra"
    overlay_dir = K8S_DIR / "tiers" / tier / "configs" / "infra"
    return _merge_json_dir(base_dir, overlay_dir)


def apply_overrides(config: dict[str, Any], ctx: InfraSeedContext) -> dict[str, Any]:
    """Overlays deployment-specific fields onto one base config doc, by type."""
    next_config = dict(config)
    config_type = next_config.get("type")

    if config_type == "default_models":
        for field_name, value in (
            ("titleModelId", ctx.title_model_id),
            ("compactionModelId", ctx.compaction_model_id),
            ("evaluatorModelId", ctx.evaluator_model_id),
            ("embeddingModelId", ctx.embedding_model_id),
        ):
            if value:
                next_config[field_name] = value
        return next_config

    if config_type == "PEKKO":
        next_config["seedNodes"] = ctx.agent_seed_nodes
        next_config["clusterName"] = ctx.agent_cluster_name or next_config.get("clusterName")
        next_config["snapshotThreshold"] = ctx.pekko_snapshot_threshold
        return next_config

    if config_type == "sql":
        next_config["jdbcUrl"] = ctx.sql_jdbc_url or next_config.get("jdbcUrl")
        return next_config

    if config_type == "cloudstorage":
        next_config["endpointUrl"] = ctx.localstack_endpoint
        return next_config

    if config_type == "qdrant":
        next_config["host"] = ctx.qdrant_host
        return next_config

    if config_type == "microservice":
        server_targets = {
            "catalog": (ctx.catalog_host, ctx.catalog_grpc_port),
            "agent": (ctx.agent_host, ctx.agent_grpc_port),
            "knowledge": (ctx.knowledge_host, ctx.knowledge_grpc_port),
        }
        target = server_targets.get(next_config.get("serverId"))
        if target:
            next_config["host"], next_config["port"] = target
        return next_config

    return next_config


def build_context(
    tier: str, namespace_override: str | None, options: SeedInfraOptions
) -> InfraSeedContext:
    catalog, agent, knowledge = Chart("catalog"), Chart("agent"), Chart("knowledge")
    localstack, qdrant, postgres = Chart("localstack"), Chart("qdrant"), Chart("postgres")

    catalog_ns = catalog.namespace(namespace_override)
    agent_ns = agent.namespace(namespace_override)
    knowledge_ns = knowledge.namespace(namespace_override)
    localstack_ns = localstack.namespace(namespace_override)
    qdrant_ns = qdrant.namespace(namespace_override)
    postgres_ns = postgres.namespace(namespace_override)

    catalog_name = catalog.resource_name(tier)
    agent_name = agent.resource_name(tier)
    agent_headless = f"{agent_name}-internal"
    knowledge_name = knowledge.resource_name(tier)
    localstack_name = localstack.resource_name(tier)
    qdrant_name = qdrant.resource_name(tier)
    postgres_name = postgres.resource_name(tier)

    catalog_grpc_port = kube.service_port(catalog_ns, catalog_name, "grpc", 9000)
    agent_grpc_port = kube.service_port(agent_ns, agent_name, "grpc", 9000)
    knowledge_grpc_port = kube.service_port(knowledge_ns, knowledge_name, "grpc", 9000)
    agent_pekko_port = kube.service_port(agent_ns, agent_headless, "pekko", 2552)
    agent_replicas = kube.statefulset_replicas(agent_ns, agent_name, 3)

    seed_node_count = min(options.agent_seed_node_count, agent_replicas)
    agent_seed_nodes = [
        f"pekko://{options.agent_cluster_name}@{agent_name}-{index}.{agent_headless}."
        f"{agent_ns}.svc.cluster.local:{agent_pekko_port}"
        for index in range(seed_node_count)
    ]

    sql_jdbc_url = (
        f"jdbc:postgresql://{postgres_name}.{postgres_ns}"
        f".svc.cluster.local:{options.postgres_port}/{options.postgres_database}"
    )

    return InfraSeedContext(
        catalog_host=f"{catalog_name}.{catalog_ns}.svc.cluster.local",
        catalog_grpc_port=catalog_grpc_port,
        agent_host=f"{agent_name}.{agent_ns}.svc.cluster.local",
        agent_grpc_port=agent_grpc_port,
        agent_seed_nodes=agent_seed_nodes,
        agent_cluster_name=options.agent_cluster_name,
        pekko_snapshot_threshold=options.pekko_snapshot_threshold,
        sql_jdbc_url=sql_jdbc_url,
        knowledge_host=f"{knowledge_name}.{knowledge_ns}.svc.cluster.local",
        knowledge_grpc_port=knowledge_grpc_port,
        localstack_endpoint=(f"http://{localstack_name}.{localstack_ns}.svc.cluster.local:4566"),
        qdrant_host=f"{qdrant_name}.{qdrant_ns}.svc.cluster.local",
        title_model_id=options.title_model_id,
        compaction_model_id=options.compaction_model_id,
        evaluator_model_id=options.evaluator_model_id,
        embedding_model_id=options.embedding_model_id,
    )


def upsert(collection: Any, doc: dict[str, Any]) -> None:
    doc_id = doc.get("id") or doc.get("_id")
    if not doc_id:
        raise ValueError(f"Config is missing id: {doc}")
    existing = collection.find_one({"_id": doc_id})
    now_ms = int(time.time() * 1000)
    payload = {**doc, "_id": doc_id}
    payload["createdTime"] = (
        existing["createdTime"]
        if existing and isinstance(existing.get("createdTime"), int)
        else now_ms
    )
    payload["updatedTime"] = now_ms
    payload.pop("id", None)
    collection.replace_one({"_id": doc_id}, payload, upsert=True)
    print(f"Upserted infra config {doc_id} ({payload.get('type')})")


def run(tier: str, namespace_override: str | None, options: SeedInfraOptions) -> None:
    mongodb = Chart("mongodb")
    mongodb_ns = mongodb.namespace(namespace_override)
    mongodb_name = mongodb.resource_name(tier)
    ctx = build_context(tier, namespace_override, options)
    docs = [apply_overrides(config, ctx) for config in merged_configs(tier)]

    with kube.port_forward(mongodb_ns, mongodb_name, options.mongodb_port) as local_port:
        client: MongoClient[dict[str, Any]] = MongoClient(f"mongodb://127.0.0.1:{local_port}")
        try:
            collection = client["INFRA"]["InfraConfig"]
            for doc in docs:
                upsert(collection, doc)
        finally:
            client.close()
