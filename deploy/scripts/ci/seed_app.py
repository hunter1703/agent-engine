import glob
import json
import os
import urllib.request
import urllib.error
from graphlib import TopologicalSorter
import time

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
    seed_domain = os.environ.get("SEED_DOMAIN", "localhost")
    seed_username = os.environ.get("SEED_USERNAME", "admin")
    seed_password = os.environ.get("SEED_PASSWORD", "password")

    origin = f"http://{seed_domain}:3000" if seed_domain == "localhost" else f"https://{seed_domain}"

    # Login
    login_data = {"type": "password", "username": seed_username, "password": seed_password}
    req = urllib.request.Request(
        f"{rest_url}/v1/auth/login", data=json.dumps(login_data).encode("utf-8"), headers={"Content-Type": "application/json", "Origin": origin}, method="POST"
    )
    session_cookie = None
    try:
        with urllib.request.urlopen(req, timeout=30) as f:
            cookie_header = f.getheader("Set-Cookie")
            if cookie_header:
                for part in cookie_header.split(";"):
                    if part.strip().startswith("session="):
                        session_cookie = part.strip()
                        break
    except Exception as e:
        print(f"Warning: Failed to login, proceeding without session cookie: {e}")

    def post_json(url, data, retries=3):
        headers = {"Content-Type": "application/json", "Origin": origin}
        if session_cookie:
            headers["Cookie"] = session_cookie
        req = urllib.request.Request(
            url, data=json.dumps(data).encode("utf-8"), headers=headers, method="POST"
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

    # Models
    for path in sorted(glob.glob("/config/models/*.json")):
        with open(path) as f:
            docs = json.load(f)
        if not isinstance(docs, list):
            docs = [docs]
        for doc in docs:
            post_json(f"{rest_url}/v1/model/upsert?skipVersion=true", doc)
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
            post_json(f"{rest_url}/v1/agent/upsert?skipVersion=true", doc)
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
