"""One-time infra bootstrap stages: Qdrant collection, LocalStack buckets."""

from __future__ import annotations

import asyncio
import json
import subprocess
import time
from dataclasses import dataclass

import httpx

from deployae import kube
from deployae.charts import CONFIGS_DIR, Chart
from deployae.stages.base import Stage
from deployae.stages.seed import expand_json_vars

DEFAULT_QDRANT_PORT = 6333
DEFAULT_VECTOR_SIZE = 768
DEFAULT_LOCALSTACK_BUCKETS = ("agent-assets",)


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
class InitQdrantCollectionStage(_BootstrapStage):
    # True for a tier with no self-hosted qdrant chart (e.g. socialmedia, backed by Qdrant
    # Cloud) - the environment's own VECTOR.json is read directly for connection details and
    # connected to straight from wherever deployae runs, instead of port-forwarding to a
    # self-hosted chart's in-cluster-only Service, which wouldn't resolve or exist here.
    external: bool = False
    environment: str | None = None
    port: int = DEFAULT_QDRANT_PORT
    vector_size: int = DEFAULT_VECTOR_SIZE

    async def run(self) -> None:
        await asyncio.to_thread(self._init)

    def _init(self) -> None:
        if self.external:
            with httpx.Client(timeout=30.0, **self._external_client_kwargs()) as client:
                self._ensure_collection(client)
            return

        namespace, service_name = self._resolve_target("qdrant", self.namespace_override, self.tier)
        with (
            kube.port_forward(namespace, service_name, self.port) as local_port,
            httpx.Client(base_url=f"http://127.0.0.1:{local_port}", timeout=30.0) as client,
        ):
            self._ensure_collection(client)

    def _external_client_kwargs(self) -> dict:
        vector_config_path = CONFIGS_DIR / self.environment / "infra" / "VECTOR.json"
        config = json.loads(expand_json_vars(vector_config_path))[0]
        scheme = "https" if config.get("tls") else "http"
        base_url = f"{scheme}://{config['host']}:{config.get('httpPort', DEFAULT_QDRANT_PORT)}"
        headers = {"api-key": config["apiKey"]} if config.get("apiKey") else {}
        return {"base_url": base_url, "headers": headers}

    def _ensure_collection(self, client: httpx.Client) -> None:
        for attempt in (1, 2):
            try:
                existing = client.get("/collections/KnowledgeChunk")
                break
            except (httpx.RequestError, httpx.TimeoutException):
                if attempt == 2:
                    raise
                time.sleep(2)

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
        # app.kubernetes.io/instance is the tier-suffixed label (e.g. "localstack-local");
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
