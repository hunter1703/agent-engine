"""Seeds configs/models/*.json and configs/agents/*.json into REST's catalog via
/v1/model/upsert and /v1/agent/upsert, over a temporary port-forward. Agent files are
topologically ordered so every subAgentId is seeded before the agent that references it."""

from __future__ import annotations

import json
import time
from graphlib import TopologicalSorter
from pathlib import Path

import httpx

from deployae import kube
from deployae.charts import DEPLOY_DIR, K8S_DIR, Chart

DEFAULT_REST_PORT = 8080
READY_PATH = "/q/health/ready"


def pick_config_files(base_dir: Path, overlay_dir: Path) -> list[Path]:
    """A same-named file in overlay_dir replaces the base_dir version; overlay-only
    files are appended after, in base-then-overlay-only order."""
    chosen: dict[str, Path] = {}
    for path in sorted(base_dir.glob("*.json")):
        overlay_path = overlay_dir / path.name
        chosen[path.name] = overlay_path if overlay_path.is_file() else path
    if overlay_dir.is_dir():
        for path in sorted(overlay_dir.glob("*.json")):
            chosen.setdefault(path.name, path)
    return list(chosen.values())


def topological_agent_order(agent_files: list[Path]) -> list[Path]:
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


def _upsert(client: httpx.Client, path: Path, endpoint: str) -> None:
    response = client.post(
        endpoint, content=path.read_bytes(), headers={"Content-Type": "application/json"}
    )
    if not response.is_success:
        raise RuntimeError(
            f"Failed to seed {path.name} (HTTP {response.status_code}): {response.text}"
        )
    print(f"Seeded {path.name}")


def run(
    tier: str,
    namespace_override: str | None,
    *,
    rest_port: int = DEFAULT_REST_PORT,
    ready_timeout: float = 60.0,
) -> None:
    rest = Chart("rest")
    rest_ns = rest.namespace(namespace_override)
    rest_name = rest.resource_name(tier)

    if not kube.service_exists(rest_ns, rest_name):
        print(
            f"Skipping catalog sync because service '{rest_name}' is not present "
            f"in namespace '{rest_ns}'."
        )
        return

    model_files = pick_config_files(
        DEPLOY_DIR / "configs" / "models", K8S_DIR / "tiers" / tier / "catalog" / "models"
    )
    agent_files = topological_agent_order(
        pick_config_files(
            DEPLOY_DIR / "configs" / "agents", K8S_DIR / "tiers" / tier / "catalog" / "agents"
        )
    )

    with (
        kube.port_forward(rest_ns, rest_name, rest_port) as local_port,
        httpx.Client(base_url=f"http://127.0.0.1:{local_port}", timeout=30) as client,
    ):
        _wait_ready(client, ready_timeout)
        for path in model_files:
            _upsert(client, path, "/v1/model/upsert")
        for path in agent_files:
            _upsert(client, path, "/v1/agent/upsert")
