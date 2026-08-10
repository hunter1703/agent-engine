"""`deployae init postgres` / `deployae init qdrant` — one-time schema/collection setup
for the Pekko Postgres journal and the Qdrant KnowledgeChunk collection, reached over a
temporary port-forward."""

from __future__ import annotations

import argparse

import httpx
import psycopg

from deployae import kube
from deployae.charts import Chart

DEFAULT_POSTGRES_PORT = 5432
DEFAULT_QDRANT_PORT = 6333
DEFAULT_VECTOR_SIZE = 768

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


def add_arguments(parser: argparse.ArgumentParser) -> None:
    subparsers = parser.add_subparsers(dest="target", required=True)

    postgres_parser = subparsers.add_parser(
        "postgres", help="Create the Pekko Persistence JDBC schema"
    )
    postgres_parser.add_argument("-n", "--namespace", help="Namespace postgres lives in")
    postgres_parser.add_argument("--service-name", default="postgres")
    postgres_parser.add_argument("--port", type=int, default=DEFAULT_POSTGRES_PORT)
    postgres_parser.add_argument("--user", default="postgres")
    postgres_parser.add_argument("--database", default="agent_engine_events")
    postgres_parser.set_defaults(handler=run_postgres)

    qdrant_parser = subparsers.add_parser("qdrant", help="Create the KnowledgeChunk collection")
    qdrant_parser.add_argument("-n", "--namespace", help="Namespace qdrant lives in")
    qdrant_parser.add_argument("--service-name", default="qdrant")
    qdrant_parser.add_argument("--port", type=int, default=DEFAULT_QDRANT_PORT)
    qdrant_parser.add_argument("--vector-size", type=int, default=DEFAULT_VECTOR_SIZE)
    qdrant_parser.set_defaults(handler=run_qdrant)


def run(args: argparse.Namespace) -> None:
    args.handler(args)


def run_postgres(args: argparse.Namespace) -> None:
    init_postgres_schema(args.namespace, args.service_name, args.port, args.user, args.database)


def run_qdrant(args: argparse.Namespace) -> None:
    init_qdrant_collection(args.namespace, args.service_name, args.port, args.vector_size)


def init_postgres_schema(
    namespace_override: str | None,
    service_name: str = "postgres",
    port: int = DEFAULT_POSTGRES_PORT,
    user: str = "postgres",
    database: str = "agent_engine_events",
) -> None:
    namespace = Chart("postgres").namespace(namespace_override)
    with (
        kube.port_forward(namespace, service_name, port) as local_port,
        psycopg.connect(
            host="127.0.0.1", port=local_port, user=user, dbname=database, autocommit=True
        ) as conn,
    ):
        conn.execute(_POSTGRES_SCHEMA)
    print("PostgreSQL Pekko schema initialized")


def init_qdrant_collection(
    namespace_override: str | None,
    service_name: str = "qdrant",
    port: int = DEFAULT_QDRANT_PORT,
    vector_size: int = DEFAULT_VECTOR_SIZE,
) -> None:
    namespace = Chart("qdrant").namespace(namespace_override)
    with (
        kube.port_forward(namespace, service_name, port) as local_port,
        httpx.Client(base_url=f"http://127.0.0.1:{local_port}") as client,
    ):
        existing = client.get("/collections/KnowledgeChunk")
        if existing.status_code == 200 and "result" in existing.json():
            print("KnowledgeChunk collection already exists")
            return
        response = client.put(
            "/collections/KnowledgeChunk",
            json={"vectors": {"size": vector_size, "distance": "Cosine"}},
        )
        if not response.is_success or response.json().get("status") != "ok":
            raise RuntimeError(f"Failed to create KnowledgeChunk collection: {response.text}")
        print("KnowledgeChunk collection created")
    print("Qdrant collections initialized successfully")
