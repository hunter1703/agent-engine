from __future__ import annotations

import asyncio
import os
import uuid
import glob
import json
import logging
from pathlib import Path
from dataclasses import dataclass
from deployae.charts import REPO_ROOT, CONFIGS_DIR
from deployae.stages.base import Stage
from deployae import output

# Helper to run shell commands asynchronously
async def run_cmd(cmd: str, env: dict = None) -> str:
    # Use the current environment but overwrite with passed env
    merged_env = os.environ.copy()
    if env:
        merged_env.update(env)
    process = await asyncio.create_subprocess_shell(
        cmd,
        env=merged_env,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE
    )
    stdout, stderr = await process.communicate()
    if process.returncode != 0:
        raise RuntimeError(f"Command failed: {cmd}\n{stderr.decode()}")
    return stdout.decode()

def expand_json_vars(file_path: str | Path) -> str:
    with open(file_path, "r") as f:
        content = f.read()
    return os.path.expandvars(content)

async def create_secret_from_dir(namespace: str, secret_name: str, dir_path: str | Path):
    import tempfile
    
    dir_path = Path(dir_path)
    # We resolve the variables and write to a temporary directory
    if not dir_path.exists():
        return
        
    with tempfile.TemporaryDirectory() as tmp_dir:
        for file in dir_path.glob("*.json"):
            resolved = expand_json_vars(file)
            with open(os.path.join(tmp_dir, file.name), "w") as f:
                f.write(resolved)
                
        # Dry-run create and apply
        cmd = f"kubectl create secret generic {secret_name} -n {namespace} --from-file={tmp_dir}/ --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f -"
        await run_cmd(cmd)

@dataclass(eq=False, kw_only=True)
class SeedAppConfigStage(Stage):
    environment: str
    tier: str
    namespace_override: str | None = None
    external_mongodb_uri: str | None = None
    
    async def run(self) -> None:
        run_id = f"seed-app-{self.tier}-{uuid.uuid4().hex[:6]}"
        namespace = "agent-engine"
        rest_service_name = f"rest-{self.tier}"
        
        output.info(f"Seeding app config using Job {run_id}")
        
        # 1. ConfigMap for seed_app.py
        seed_app_py = REPO_ROOT / "deploy" / "scripts" / "ci" / "seed_app.py"
        cmd = f"kubectl create configmap {run_id}-script -n {namespace} --from-file=seed_app.py={seed_app_py} --dry-run=client -o yaml | kubectl apply --server-side --force-conflicts -f -"
        await run_cmd(cmd)
        
        # 2. Create secrets for models, agents, connectors
        await create_secret_from_dir(namespace, f"{run_id}-connectors", CONFIGS_DIR / self.environment / "connectors")
        await create_secret_from_dir(namespace, f"{run_id}-models", CONFIGS_DIR / self.environment / "models")
        await create_secret_from_dir(namespace, f"{run_id}-agents", CONFIGS_DIR / self.environment / "agents")
        
        # 3. Apply Job
        job_yaml = f"""apiVersion: batch/v1
kind: Job
metadata:
  name: {run_id}
  namespace: {namespace}
spec:
  ttlSecondsAfterFinished: 600
  backoffLimit: 3
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: seed
          image: python:3.12-slim
          command: ["python3", "/scripts/seed_app.py"]
          env:
            - name: REST_URL
              value: "http://{rest_service_name}:8080"
          envFrom:
            - secretRef:
                name: agent-engine-secrets
                optional: true
          volumeMounts:
            - {{name: script, mountPath: /scripts}}
            - {{name: connectors, mountPath: /config/connectors}}
            - {{name: models, mountPath: /config/models}}
            - {{name: agents, mountPath: /config/agents}}
      volumes:
        - {{name: script, configMap: {{name: {run_id}-script}}}}
        - {{name: connectors, secret: {{secretName: {run_id}-connectors, optional: true}}}}
        - {{name: models, secret: {{secretName: {run_id}-models, optional: true}}}}
        - {{name: agents, secret: {{secretName: {run_id}-agents, optional: true}}}}
"""

        import tempfile
        with tempfile.NamedTemporaryFile("w", delete=False) as f:
            f.write(job_yaml)
            job_file = f.name
            
        try:
            await run_cmd(f"kubectl apply -f {job_file}")
            
            # Wait for job completion
            try:
                await run_cmd(f"kubectl wait --for=condition=complete --timeout=180s job/{run_id} -n {namespace}")
            except Exception as e:
                logs = await run_cmd(f"kubectl logs job/{run_id} -n {namespace} --tail=200")
                output.error(f"App seed failed:\n{logs}")
                raise e
        finally:
            os.remove(job_file)


