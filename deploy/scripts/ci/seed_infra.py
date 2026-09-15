"""Runs inside a one-off Kubernetes Job (see .github/workflows/deploy.yml), not on the CI
runner — deliberately, so its outbound connections to Atlas/Neon originate from a node whose
IP is already in Atlas's Network Access list, instead of a GitHub-hosted runner's ephemeral IP
(which would otherwise force opening Atlas to 0.0.0.0/0).

Reads InfraConfig documents from /config/*.json (mounted from a Secret containing the tier's
deploy/configs/<env>/infra/*.json files) and upserts them into MongoDB Atlas — mirrors
deployae's own SeedInfraConfigStage upsert semantics exactly. Then applies the same event-
sourcing schema deployae's InitPostgresSchemaStage uses, against Neon.
"""

from __future__ import annotations

import glob
import json
import os
import time

import psycopg
from pymongo import MongoClient

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


def seed_mongo() -> None:
    client = MongoClient(os.environ["MONGO_ATLAS_URI"])
    try:
        collection = client["INFRA"]["InfraConfig"]
        for path in sorted(glob.glob("/config/*.json")):
            docs = json.loads(open(path).read())
            for doc in docs:
                doc_id = doc.get("id") or doc.get("_id")
                if not doc_id:
                    raise ValueError(f"Config missing id in {path}: {doc}")
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
                print(f"Upserted {doc_id} ({payload.get('type')})")
        
        encryption_key = os.environ.get("ENCRYPTION_KEY")
        if encryption_key:
            doc_id = "ENCRYPTION#ENCRYPTION#default"
            existing = collection.find_one({"_id": doc_id})
            now_ms = int(time.time() * 1000)
            payload = {
                "_id": doc_id,
                "_t": "com.agentengine.util.mongodb.infra.EncryptionInfraConfig",
                "category": "ENCRYPTION",
                "type": "ENCRYPTION",
                "configId": "default",
                "key": encryption_key,
                "createdTime": (existing["createdTime"] if existing and isinstance(existing.get("createdTime"), int) else now_ms),
                "updatedTime": now_ms
            }
            collection.replace_one({"_id": doc_id}, payload, upsert=True)
            print(f"Upserted {doc_id} (ENCRYPTION)")
    finally:
        client.close()


def init_postgres_schema() -> None:
    with psycopg.connect(os.environ["POSTGRES_CONNINFO"], autocommit=True) as conn:
        conn.execute(_POSTGRES_SCHEMA)
    print("PostgreSQL Pekko schema initialized")


if __name__ == "__main__":
    seed_mongo()
    init_postgres_schema()
