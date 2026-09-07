"""Ensures MongoDB indexes for a deployed service by calling its internal endpoint.

Index creation is a deploy-time step rather than a service startup hook: with several replicas
a startup hook has every pod building the same indexes at once, and a long build delays
readiness. Running it here also means a failure surfaces as a failed deploy rather than a pod
that quietly came up without its indexes.
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass

import httpx

from deployae import kube
from deployae.charts import Chart
from deployae.stages.base import Stage

DEFAULT_SERVICE_PORT = 8080
ENSURE_PATH = "/internal/mongo/ensure-index"


@dataclass(eq=False, kw_only=True)
class EnsureIndexesStage(Stage):
    chart_name: str
    tier: str
    namespace_override: str | None
    service_port: int = DEFAULT_SERVICE_PORT

    async def run(self) -> None:
        await asyncio.to_thread(self._ensure)

    def _ensure(self) -> None:
        """No readiness poll here: this stage's own depends_on already waits on the target
        chart's DeployChartStage, which blocks on `kubectl rollout status` — itself gated on
        the same /q/health/ready check via the Deployment's readinessProbe — so the service is
        already known-ready by the time this runs."""
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
