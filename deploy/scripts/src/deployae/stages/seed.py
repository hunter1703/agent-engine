from __future__ import annotations

import asyncio
import os
import uuid
import glob
import json
import logging
from dataclasses import dataclass
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

def expand_json_vars(file_path: str) -> str:
    with open(file_path, "r") as f:
        content = f.read()
    return os.path.expandvars(content)

async def create_secret_from_dir(namespace: str, secret_name: str, dir_path: str):
    import tempfile
    
    # We resolve the variables and write to a temporary directory
    if not os.path.exists(dir_path):
        return
        
    with tempfile.TemporaryDirectory() as tmp_dir:
        for file in glob.glob(os.path.join(dir_path, "*.json")):
            resolved = expand_json_vars(file)
            filename = os.path.basename(file)
            with open(os.path.join(tmp_dir, filename), "w") as f:
                f.write(resolved)
                
        # Dry-run create and apply
        cmd = f"kubectl create secret generic {secret_name} -n {namespace} --from-file={tmp_dir}/ --dry-run=client -o yaml | kubectl apply -f -"
        await run_cmd(cmd)

@dataclass(eq=False, kw_only=True)
class SeedInfraConfigStage(Stage):
    environment: str
    tier: str
    namespace_override: str | None = None
    external_mongodb_uri: str | None = None
    
    async def run(self) -> None:
        run_id = f"seed-infra-{self.tier}-{uuid.uuid4().hex[:6]}"
        namespace = "agent-engine"
        
        output.info(f"Seeding infra config using Job {run_id}")
        
        # 1. ConfigMap for seed_infra.py
        cmd = f"kubectl create configmap {run_id}-script -n {namespace} --from-file=seed_infra.py=deploy/scripts/ci/seed_infra.py --dry-run=client -o yaml | kubectl apply -f -"
        await run_cmd(cmd)
        
        # 2. Extract values from SQL.json and GP
        sql_path = f"deploy/configs/{self.environment}/infra/SQL.json"
        gp_path = f"deploy/k8s/global-properties/envs/{self.environment}/values.yaml"
        
        sql_content = expand_json_vars(sql_path)
        sql_json = json.loads(sql_content)[0]
        jdbc_url = sql_json.get("jdbcUrl")
        jdbc_user = sql_json.get("jdbcUser")
        jdbc_password = sql_json.get("jdbcPassword")
        
        postgres_conninfo = f"postgresql://{jdbc_user}:{jdbc_password}@{jdbc_url.replace('jdbc:postgresql://', '')}"
        
        # For MongoDB, we just parse it from the yaml using basic string matching since yaml parsing might require extra deps (though pyyaml is available)
        mongo_uri = None
        with open(gp_path) as f:
            for line in f:
                if "infra.mongodb.uri:" in line:
                    mongo_uri = line.split("infra.mongodb.uri:")[1].strip()
                    break
        if mongo_uri:
            mongo_uri = os.path.expandvars(mongo_uri)
            
        # 3. Secret for configs
        import tempfile
        with tempfile.TemporaryDirectory() as tmp_dir:
            with open(os.path.join(tmp_dir, "SQL.json"), "w") as f:
                f.write(sql_content)
            
            enc_path = f"deploy/configs/{self.environment}/infra/ENCRYPTION.json"
            if os.path.exists(enc_path):
                with open(os.path.join(tmp_dir, "ENCRYPTION.json"), "w") as f:
                    f.write(expand_json_vars(enc_path))
                    
            cmd = f"kubectl create secret generic {run_id}-config -n {namespace} --from-file={tmp_dir}/ "
            if mongo_uri:
                cmd += f"--from-literal=MONGO_ATLAS_URI='{mongo_uri}' "
            cmd += f"--from-literal=POSTGRES_CONNINFO='{postgres_conninfo}' "
            cmd += "--dry-run=client -o yaml | kubectl apply -f -"
            await run_cmd(cmd)
            
        # 4. Apply Job
        job_yaml = f"""apiVersion: batch/v1
kind: Job
metadata:
  name: {run_id}
  namespace: {namespace}
spec:
  ttlSecondsAfterFinished: 30
  backoffLimit: 1
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: seed
          image: python:3.12-slim
          command: ["sh", "-c", "pip install --quiet pymongo 'psycopg[binary]' && python3 /scripts/seed_infra.py"]
          env:
            - name: MONGO_ATLAS_URI
              valueFrom:
                secretKeyRef: {{name: {run_id}-config, key: MONGO_ATLAS_URI}}
            - name: POSTGRES_CONNINFO
              valueFrom:
                secretKeyRef: {{name: {run_id}-config, key: POSTGRES_CONNINFO}}
          volumeMounts:
            - {{name: script, mountPath: /scripts}}
            - {{name: config, mountPath: /config}}
      volumes:
        - {{name: script, configMap: {{name: {run_id}-script}}}}
        - {{name: config, secret: {{secretName: {run_id}-config}}}}
"""
        
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
                output.error(f"Infra seed failed:\n{logs}")
                raise e
        finally:
            os.remove(job_file)
            

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
        cmd = f"kubectl create configmap {run_id}-script -n {namespace} --from-file=seed_app.py=deploy/scripts/ci/seed_app.py --dry-run=client -o yaml | kubectl apply -f -"
        await run_cmd(cmd)
        
        # 2. Create secrets for models, agents, connectors
        await create_secret_from_dir(namespace, f"{run_id}-connectors", f"deploy/configs/{self.environment}/connectors")
        await create_secret_from_dir(namespace, f"{run_id}-models", f"deploy/configs/{self.environment}/models")
        await create_secret_from_dir(namespace, f"{run_id}-agents", f"deploy/configs/{self.environment}/agents")
        
        # 3. Apply Job
        job_yaml = f"""apiVersion: batch/v1
kind: Job
metadata:
  name: {run_id}
  namespace: {namespace}
spec:
  ttlSecondsAfterFinished: 30
  backoffLimit: 1
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

