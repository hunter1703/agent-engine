"""One-time infra bootstrap stages: Postgres schema, Qdrant collection, LocalStack buckets."""

from __future__ import annotations

import asyncio
import subprocess
from dataclasses import dataclass

import httpx
import psycopg

from deployae import kube
from deployae.charts import Chart
from deployae.stages.base import Stage

DEFAULT_POSTGRES_PORT = 5432
DEFAULT_QDRANT_PORT = 6333
DEFAULT_VECTOR_SIZE = 768
DEFAULT_LOCALSTACK_BUCKETS = ("agent-assets",)

_POSTGRES_SCHEMA = """
CREATE TABLE IF NOT EXISTS event_journal (
    ordering        BIGSERIAL,
    persistence_id  VARCHAR(255) NOT NULL,
    sequence_number BIGINT       NOT NULL,
    deleted         BOOLEAN      DEFAULT FALSE NOT NULL,
    writer          VARCHAR(255) NOT NULL,
    write_timestamp BIGINT       NOT NULL,
    adapter_manifest VARCHAR(255),
    event_ser_id    INTEGER      NOT NULL,
    event_ser_manifest VARCHAR(255) NOT NULL,
    event_payload   BYTEA        NOT NULL,
    meta_ser_id     INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload    BYTEA,
    PRIMARY KEY (persistence_id, sequence_number)
);
CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal (ordering);

CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id  VARCHAR(255) NOT NULL,
    sequence_number BIGINT       NOT NULL,
    created         BIGINT       NOT NULL,
    snapshot_ser_id INTEGER      NOT NULL,
    snapshot_ser_manifest VARCHAR(255) NOT NULL,
    snapshot_payload BYTEA       NOT NULL,
    meta_ser_id     INTEGER,
    meta_ser_manifest VARCHAR(255),
    meta_payload    BYTEA,
    PRIMARY KEY (persistence_id, sequence_number)
);
"""


@dataclass(eq=False, kw_only=True)
class _BootstrapStage(Stage):
    """Shared resolution for one-time infra bootstrap stages: each targets one infra
    chart's live namespace and tier-suffixed resource name."""

    namespace_override: str | None
    tier: str | None = None

    @staticmethod
    def _resolve_target(
        chart_name: str, namespace_override: str | None, tier: str | None
    ) -> tuple[str, str]:
        chart = Chart(chart_name)
        return chart.namespace(namespace_override), chart.resource_name(tier)


@dataclass(eq=False, kw_only=True)
class InitPostgresSchemaStage(_BootstrapStage):
    port: int = DEFAULT_POSTGRES_PORT
    user: str = "postgres"
    database: str = "agent_engine_events"

    async def run(self) -> None:
        await asyncio.to_thread(self._init)

    def _init(self) -> None:
        namespace, service_name = self._resolve_target(
            "postgres", self.namespace_override, self.tier
        )
        with (
            kube.port_forward(namespace, service_name, self.port) as local_port,
            psycopg.connect(
                host="127.0.0.1",
                port=local_port,
                user=self.user,
                dbname=self.database,
                autocommit=True,
            ) as conn,
        ):
            conn.execute(_POSTGRES_SCHEMA)
        print("PostgreSQL Pekko schema initialized")


@dataclass(eq=False, kw_only=True)
class InitQdrantCollectionStage(_BootstrapStage):
    port: int = DEFAULT_QDRANT_PORT
    vector_size: int = DEFAULT_VECTOR_SIZE

    async def run(self) -> None:
        await asyncio.to_thread(self._init)

    def _init(self) -> None:
        namespace, service_name = self._resolve_target("qdrant", self.namespace_override, self.tier)
        with (
            kube.port_forward(namespace, service_name, self.port) as local_port,
            httpx.Client(base_url=f"http://127.0.0.1:{local_port}") as client,
        ):
            existing = client.get("/collections/KnowledgeChunk")
            if existing.status_code == 200 and "result" in existing.json():
                print("KnowledgeChunk collection already exists")
                return
            response = client.put(
                "/collections/KnowledgeChunk",
                json={"vectors": {"size": self.vector_size, "distance": "Cosine"}},
            )
            if not response.is_success or response.json().get("status") != "ok":
                raise RuntimeError(f"Failed to create KnowledgeChunk collection: {response.text}")
            print("KnowledgeChunk collection created")


@dataclass(eq=False, kw_only=True)
class EnsureLocalstackBucketsStage(_BootstrapStage):
    buckets: tuple[str, ...] = DEFAULT_LOCALSTACK_BUCKETS

    async def run(self) -> None:
        await asyncio.to_thread(self._ensure)

    def _ensure(self) -> None:
        # app.kubernetes.io/instance is the tier-suffixed label (e.g. "localstack-prod");
        # app.kubernetes.io/name stays the plain untiered chart name regardless of tier.
        namespace, instance = self._resolve_target("localstack", self.namespace_override, self.tier)
        pod = subprocess.run(
            [
                "kubectl",
                "get",
                "pods",
                "--namespace",
                namespace,
                "-l",
                f"app.kubernetes.io/instance={instance}",
                "-o",
                "jsonpath={.items[0].metadata.name}",
            ],
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
        for bucket in self.buckets:
            subprocess.run(
                [
                    "kubectl",
                    "exec",
                    "--namespace",
                    namespace,
                    pod,
                    "--",
                    "sh",
                    "-c",
                    f"awslocal s3api head-bucket --bucket '{bucket}' 2>/dev/null || awslocal s3 mb 's3://{bucket}'",
                ],
                check=True,
            )
            print(f"Bucket ready: {bucket}")
