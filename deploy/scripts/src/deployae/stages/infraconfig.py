"""Saves the tier's infra configs through the internal service.

Each file in deploy/configs/<env>/infra is a list of infra configs. A server config is sent to its
server's endpoint, which prepares the server for customers to use before saving it; the remaining
configs, such as the default models, are saved together through the generic infra-config endpoint.
Re-running is safe: a config of the same type and key is replaced.
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import httpx

from deployae.charts import CONFIGS_DIR
from deployae.stages.internal import InternalEndpointStage
from deployae.stages.seed import expand_json_vars

SAVE_PATH = "/internal/infra-config"
SERVER_PATHS = {
    "ENCRYPTION.json": "/internal/server/encryption",
    "MONGO.json": "/internal/server/mongo",
    "SQL.json": "/internal/server/sql",
    "VECTOR.json": "/internal/server/vector",
    "CLOUDSTORAGE.json": "/internal/server/cloudstorage",
    "MICROSERVICE.json": "/internal/server/microservice",
}
ENCRYPTION_FILE = "ENCRYPTION.json"


@dataclass(eq=False, kw_only=True)
class SeedInfraConfigStage(InternalEndpointStage):
    environment: str

    def _execute(self, client: httpx.Client, service: str) -> None:
        for path in self._config_files():
            configs = json.loads(expand_json_vars(path))
            server_path = SERVER_PATHS.get(path.name)
            if server_path is None:
                self._succeeded(client.post(SAVE_PATH, json=configs), service)
            else:
                for config in configs:
                    self._succeeded(client.put(server_path, json=config), service)
            print(f"Saved {path.name}")

    def _config_files(self):
        """Encryption first: configs saved after it have their secret fields encrypted."""
        files = sorted((CONFIGS_DIR / self.environment / "infra").glob("*.json"))
        return sorted(files, key=lambda path: path.name != ENCRYPTION_FILE)
