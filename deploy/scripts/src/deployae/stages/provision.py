"""Provisions the environment and each of its customers through the internal service.

The environment call sets up what all customers share; then one call per entry in the
environment's customers.json provisions that customer: its client configs and the databases,
indexes, event store, vector collections and buckets behind them. Every step is safe to run again.
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import httpx

from deployae.charts import CONFIGS_DIR
from deployae.stages.internal import InternalEndpointStage
from deployae.stages.seed import expand_json_vars

ENVIRONMENT_PATH = "/internal/provision/environment"
CUSTOMERS_PATH = "/internal/provision/customer"


@dataclass(eq=False, kw_only=True)
class ProvisionStage(InternalEndpointStage):
    environment: str

    def _execute(self, client: httpx.Client, service: str) -> None:
        self._provision("environment", client.post(ENVIRONMENT_PATH), service)
        for customer in self._customers():
            self._provision(
                f"customer {customer['id']}", client.post(CUSTOMERS_PATH, json=customer), service
            )

    def _customers(self) -> list[dict]:
        path = CONFIGS_DIR / self.environment / "customers.json"
        return json.loads(expand_json_vars(path)) if path.is_file() else []

    def _provision(self, target: str, response: httpx.Response, service: str) -> None:
        steps = self._succeeded(response, service).json()["steps"]
        failed = [step for step in steps if step.get("error")]
        for step in steps:
            print(f"Provisioned {target}: {step['name']}" + (f" FAILED: {step['error']}" if step.get("error") else ""))
        if failed:
            raise RuntimeError(
                f"Provisioning {target} failed: "
                + "; ".join(f"{step['name']}: {step['error']}" for step in failed)
            )
