"""Shared plumbing for deploy stages that call an endpoint on a deployed service's internal API."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass

import httpx

from deployae import kube
from deployae.charts import Chart
from deployae.stages.base import Stage

DEFAULT_SERVICE_PORT = 8080


@dataclass(eq=False, kw_only=True)
class InternalEndpointStage(Stage):
    chart_name: str
    tier: str
    namespace_override: str | None
    service_port: int = DEFAULT_SERVICE_PORT

    async def run(self) -> None:
        await asyncio.to_thread(self._run)

    def _run(self) -> None:
        """No readiness poll here: this stage's own depends_on already waits on the target
        chart's DeployChartStage, which blocks on `kubectl rollout status` — itself gated on
        the same /q/health/ready check via the Deployment's readinessProbe — so the service is
        already known-ready by the time this runs."""
        chart = Chart(self.chart_name)
        namespace = chart.namespace(self.namespace_override)
        service = chart.resource_name(self.tier)

        if not kube.service_exists(namespace, service):
            raise RuntimeError(
                f"{self.name} needs service '{service}' in namespace '{namespace}', "
                f"but it is not deployed"
            )

        with (
            kube.port_forward(namespace, service, self.service_port) as local_port,
            httpx.Client(base_url=f"http://127.0.0.1:{local_port}", timeout=120) as client,
        ):
            self._execute(client, service)

    def _execute(self, client: httpx.Client, service: str) -> None:
        self._handle(self._succeeded(self._call(client), service))

    def _succeeded(self, response: httpx.Response, service: str) -> httpx.Response:
        if not response.is_success:
            raise RuntimeError(
                f"{self.name} failed for {service} (HTTP {response.status_code}): "
                f"{response.text}"
            )
        return response

    def _call(self, client: httpx.Client) -> httpx.Response:
        raise NotImplementedError

    def _handle(self, response: httpx.Response) -> None:
        raise NotImplementedError
