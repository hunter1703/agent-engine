"""Saves the tier's infra configs through the internal service.

Each file in deploy/configs/<env>/infra is a list of infra configs, saved through the infra-config
endpoint, which first prepares whatever a config points at (a SQL server's database, for one).
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
ENCRYPTION_FILE = "ENCRYPTION.json"


@dataclass(eq=False, kw_only=True)
class SeedInfraConfigStage(InternalEndpointStage):
    environment: str

    def _execute(self, client: httpx.Client, service: str) -> None:
        for path in self._config_files():
            configs = json.loads(expand_json_vars(path))
            self._succeeded(client.post(SAVE_PATH, json=configs), service)
            print(f"Saved {path.name}")

    def _config_files(self):
        """Encryption first: configs saved after it have their secret fields encrypted."""
        files = sorted((CONFIGS_DIR / self.environment / "infra").glob("*.json"))
        return sorted(files, key=lambda path: path.name != ENCRYPTION_FILE)
