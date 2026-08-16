"""Ensures MongoDB indexes for a deployed service by calling its internal endpoint.

Index creation is a deploy-time step rather than a service startup hook: with several replicas
a startup hook has every pod building the same indexes at once, and a long build delays
readiness. Running it here also means a failure surfaces as a failed deploy rather than a pod
that quietly came up without its indexes.
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass

import httpx

from deployae import kube
from deployae.charts import Chart
from deployae.stages.base import Stage

DEFAULT_SERVICE_PORT = 8080
ENSURE_PATH = "/internal/mongo/ensure-index"
READY_PATH = "/q/health/ready"


@dataclass(eq=False, kw_only=True)
class EnsureIndexesStage(Stage):
    chart_name: str
    tier: str
    namespace_override: str | None
    service_port: int = DEFAULT_SERVICE_PORT
    ready_timeout: float = 60.0

    async def run(self) -> None:
        await asyncio.to_thread(self._ensure)

    def _ensure(self) -> None:
        chart = Chart(self.chart_name)
        namespace = chart.namespace(self.namespace_override)
        service = chart.resource_name(self.tier)

        if not kube.service_exists(namespace, service):
            print(
                f"Skipping index creation because service '{service}' is not present "
                f"in namespace '{namespace}'."
            )
            return

        with (
            kube.port_forward(namespace, service, self.service_port) as local_port,
            httpx.Client(base_url=f"http://127.0.0.1:{local_port}", timeout=120) as client,
        ):
            _wait_ready(client, service, self.ready_timeout)
            response = client.post(ENSURE_PATH)
            if not response.is_success:
                raise RuntimeError(
                    f"Failed to ensure indexes for {service} "
                    f"(HTTP {response.status_code}): {response.text}"
                )
            for result in response.json():
                if result.get("error"):
                    raise RuntimeError(
                        f"Failed to ensure indexes for collection "
                        f"{result['collection']}: {result['error']}"
                    )
                print(f"Ensured indexes on {result['collection']}: {result['indexes']}")


def _wait_ready(client: httpx.Client, service: str, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            if client.get(READY_PATH, timeout=2).is_success:
                return
        except httpx.HTTPError as error:
            last_error = error
        time.sleep(1)
    raise RuntimeError(f"{service} never became ready at {READY_PATH}") from last_error
