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
    instances: list[str]

    async def run(self) -> None:
        for instance in self.instances:
            await asyncio.to_thread(kube.delete_pvcs_by_instance, self.namespace, instance)
        print(f"Deleted PVCs from namespace {self.namespace} for instances: {', '.join(self.instances)}")


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


@dataclass(eq=False, kw_only=True)
class CleanDockerCacheStage(Stage):
    async def run(self) -> None:
        await asyncio.to_thread(self._clean)

    def _clean(self) -> None:
        import subprocess

        try:
            print("Cleaning Docker build cache, unused images, and stopped containers...")
            subprocess.run(["docker", "builder", "prune", "-a", "-f"], check=True)
            # -a (not just dangling/untagged) is what actually reclaims space here: every
            # deploy tags its images with the git SHA, so a plain `image prune` never touches
            # a previous deploy's now-unreferenced images and they accumulate across deploys
            # until the node runs out of ephemeral storage and the kubelet starts evicting pods.
            subprocess.run(["docker", "image", "prune", "-a", "-f"], check=True)
            subprocess.run(["docker", "container", "prune", "-f"], check=True)
            print("Docker build cache, unused images, and stopped containers cleaned")
        except (subprocess.SubprocessError, FileNotFoundError) as e:
            print(f"Warning: Failed to clean Docker cache: {e}")
