"""Ensures MongoDB indexes for a deployed service by calling its internal endpoint.

Index creation is a deploy-time step rather than a service startup hook: with several replicas
a startup hook has every pod building the same indexes at once, and a long build delays
readiness. Running it here also means a failure surfaces as a failed deploy rather than a pod
that quietly came up without its indexes.
"""

from __future__ import annotations

from dataclasses import dataclass

import httpx

from deployae.stages.internal import InternalEndpointStage

ENSURE_PATH = "/internal/mongo/ensure-index"


@dataclass(eq=False, kw_only=True)
class EnsureIndexesStage(InternalEndpointStage):
    def _call(self, client: httpx.Client) -> httpx.Response:
        return client.post(ENSURE_PATH)

    def _handle(self, response: httpx.Response) -> None:
        for result in response.json():
            if result.get("error"):
                raise RuntimeError(
                    f"Failed to ensure indexes for collection "
                    f"{result['collection']}: {result['error']}"
                )
            print(f"Ensured indexes on {result['collection']}: {result['indexes']}")
