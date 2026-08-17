"""Seeding stages: MongoDB infra config, and REST's model/agent catalog. Config content
under deploy/configs/<environment>/ is the source of truth — these stages read files
and insert as-is, with zero override/computation."""

from __future__ import annotations

import asyncio
import json
import time
from dataclasses import dataclass
from graphlib import TopologicalSorter
from pathlib import Path
from typing import Any, Literal

import httpx
from pymongo import MongoClient

from deployae import kube
from deployae.charts import Chart, env_config_dir
from deployae.stages.base import Stage

DEFAULT_MONGODB_PORT = 27017
DEFAULT_REST_PORT = 8080
READY_PATH = "/q/health/ready"

Kind = Literal["models", "agents"]
_ENDPOINT_BY_KIND: dict[Kind, str] = {
    "models": "/v1/model/upsert",
    "agents": "/v1/agent/upsert",
}


@dataclass(eq=False, kw_only=True)
class SeedInfraConfigStage(Stage):
    tier: str | None
    environment: str
    namespace_override: str | None
    mongodb_port: int = DEFAULT_MONGODB_PORT

    async def run(self) -> None:
        await asyncio.to_thread(self._seed)

    def _seed(self) -> None:
        """`tier` locates the live MongoDB Service to write into (mongodb-<tier>, or plain
        `mongodb` if it wasn't deployed with a tier); `environment` picks which config files
        get seeded. These are unrelated axes — MongoDB is a shared per-environment resource,
        the config files describe that same environment's canonical topology."""
        mongodb = Chart("mongodb")
        mongodb_ns = mongodb.namespace(self.namespace_override)
        mongodb_name = mongodb.resource_name(self.tier)
        docs = self._environment_configs()

        with kube.port_forward(mongodb_ns, mongodb_name, self.mongodb_port) as local_port:
            client: MongoClient[dict[str, Any]] = MongoClient(f"mongodb://127.0.0.1:{local_port}")
            try:
                collection = client["INFRA"]["InfraConfig"]
                for doc in docs:
                    _upsert_infra_config(collection, doc)
            finally:
                client.close()

    def _environment_configs(self) -> list[dict[str, Any]]:
        docs: list[dict[str, Any]] = []
        for path in sorted(env_config_dir(self.environment, "infra").glob("*.json")):
            docs.extend(json.loads(path.read_text()))
        return docs


def _upsert_infra_config(collection: Any, doc: dict[str, Any]) -> None:
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


@dataclass(eq=False, kw_only=True)
class SeedRestCatalogStage(Stage):
    kind: Kind
    tier: str
    environment: str
    namespace_override: str | None
    rest_port: int = DEFAULT_REST_PORT
    ready_timeout: float = 60.0

    async def run(self) -> None:
        await asyncio.to_thread(self._seed)

    def _seed(self) -> None:
        """Seeds just one kind (models or agents) into one REST instance. `tier` picks which
        REST instance (rest-<tier>); `environment` picks which config files."""
        rest = Chart("rest")
        rest_ns = rest.namespace(self.namespace_override)
        rest_name = rest.resource_name(self.tier)

        if not kube.service_exists(rest_ns, rest_name):
            print(
                f"Skipping {self.kind} sync because service '{rest_name}' is not present "
                f"in namespace '{rest_ns}'."
            )
            return

        files = self._files_for_kind()
        endpoint = _ENDPOINT_BY_KIND[self.kind]

        with (
            kube.port_forward(rest_ns, rest_name, self.rest_port) as local_port,
            httpx.Client(base_url=f"http://127.0.0.1:{local_port}", timeout=30) as client,
        ):
            _wait_ready(client, self.ready_timeout)
            for path in files:
                _upsert_catalog_entry(client, path, endpoint)

    def _files_for_kind(self) -> list[Path]:
        files = sorted(env_config_dir(self.environment, self.kind).glob("*.json"))
        return _topological_agent_order(files) if self.kind == "agents" else files


def _topological_agent_order(agent_files: list[Path]) -> list[Path]:
    """Orders agent config files so every agent's subAgentIds are seeded before the
    agent itself. A dependency not present among agent_files (e.g. already seeded in a
    prior run) is simply not a constraint graphlib needs to know about."""
    path_by_id: dict[str, Path] = {}
    deps_by_id: dict[str, set[str]] = {}
    for path in agent_files:
        doc = json.loads(path.read_text())
        agent_id = doc["id"]
        path_by_id[agent_id] = path
        deps_by_id[agent_id] = set(doc.get("subAgentIds") or [])

    known_ids = set(path_by_id)
    filtered_deps = {
        agent_id: {dep for dep in deps if dep in known_ids} for agent_id, deps in deps_by_id.items()
    }
    return [path_by_id[agent_id] for agent_id in TopologicalSorter(filtered_deps).static_order()]


def _wait_ready(client: httpx.Client, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            if client.get(READY_PATH, timeout=2).is_success:
                return
        except httpx.HTTPError as error:
            last_error = error
        time.sleep(1)
    raise RuntimeError(f"REST never became ready at {READY_PATH}") from last_error


def _upsert_catalog_entry(client: httpx.Client, path: Path, endpoint: str) -> None:
    response = client.post(
        endpoint, content=path.read_bytes(), headers={"Content-Type": "application/json"}
    )
    if not response.is_success:
        raise RuntimeError(
            f"Failed to seed {path.name} (HTTP {response.status_code}): {response.text}"
        )
    print(f"Seeded {path.name}")
