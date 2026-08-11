"""Post-uninstall cleanup stages: PVCs, namespaces, and LocalStack's non-Helm-tracked
resources — each depends on the uninstall(s) it needs to have finished first."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass

from deployae import kube
from deployae.stages.base import Stage


@dataclass(eq=False, kw_only=True)
class DeletePvcsStage(Stage):
    namespace: str

    async def run(self) -> None:
        await asyncio.to_thread(kube.delete_pvcs, self.namespace)
        print(f"Deleted PVCs from namespace {self.namespace}")


@dataclass(eq=False, kw_only=True)
class DeleteNamespaceStage(Stage):
    namespace: str

    async def run(self) -> None:
        await asyncio.to_thread(kube.delete_namespace, self.namespace)
        print(f"Deleted namespace {self.namespace}")


@dataclass(eq=False, kw_only=True)
class RemoveLocalstackResourcesStage(Stage):
    namespace: str

    async def run(self) -> None:
        await asyncio.to_thread(
            kube.delete_by_label, self.namespace, "app.kubernetes.io/name=localstack"
        )
        print(f"Removed localstack resources from namespace {self.namespace}")
