import glob
import json
import os
import urllib.request
import urllib.error
from graphlib import TopologicalSorter

import time

def post_json(url, data, retries=3):
    req = urllib.request.Request(
        url, data=json.dumps(data).encode("utf-8"), headers={"Content-Type": "application/json"}, method="POST"
    )
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=30) as f:
                return f.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"Failed to post to {url}: HTTP {e.code} {e.read().decode('utf-8', errors='replace')}")
        except Exception:
            if attempt == retries - 1:
                raise
            time.sleep(2)

def topological_agent_order(agent_files):
    path_by_id = {}
    deps_by_id = {}
    for path in agent_files:
        with open(path) as f:
            docs = json.load(f)
            if not isinstance(docs, list):
                docs = [docs]
            for doc in docs:
                agent_id = doc["id"]
                path_by_id[agent_id] = path
                deps_by_id[agent_id] = set(doc.get("subAgentIds") or [])

    known_ids = set(path_by_id)
    filtered_deps = {
        agent_id: {dep for dep in deps if dep in known_ids} for agent_id, deps in deps_by_id.items()
    }
    # TopologicalSorter returns stable output, but we use set comprehensions which are unordered.
    # It's fine for our purposes. We yield the paths in order.
    # Note: Multiple agents might be in the same file, so we deduplicate paths.
    ordered_paths = []
    seen = set()
    for agent_id in TopologicalSorter(filtered_deps).static_order():
        path = path_by_id[agent_id]
        if path not in seen:
            ordered_paths.append(path)
            seen.add(path)
    return ordered_paths

def seed():
    rest_url = os.environ.get("REST_URL", "http://rest:8080")
    
    # Models
    for path in sorted(glob.glob("/config/models/*.json")):
        with open(path) as f:
            docs = json.load(f)
        if not isinstance(docs, list):
            docs = [docs]
        for doc in docs:
            post_json(f"{rest_url}/v1/model/upsert", doc)
            print(f"Seeded model {doc.get('id')}", flush=True)

    # Agents
    agent_files = sorted(glob.glob("/config/agents/*.json"))
    if agent_files:
        agent_files = topological_agent_order(agent_files)
    for path in agent_files:
        with open(path) as f:
            docs = json.load(f)
        if not isinstance(docs, list):
            docs = [docs]
        for doc in docs:
            post_json(f"{rest_url}/v1/agent/upsert", doc)
            print(f"Seeded agent {doc.get('id')}", flush=True)

    # Connections
    for path in sorted(glob.glob("/config/connectors/*.json")):
        with open(path) as f:
            docs = json.load(f)
        if not isinstance(docs, list):
            docs = [docs]
        for doc in docs:
            post_json(f"{rest_url}/v1/connection/", doc)
            print(f"Seeded connection {doc.get('id') or doc.get('appName')}", flush=True)

if __name__ == "__main__":
    seed()

