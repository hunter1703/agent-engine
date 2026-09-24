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
    # Only safe to prune *all* unused images (-a, not just dangling) before this deploy's own
    # builds have run: at that point every candidate is genuinely stale, left over from an
    # earlier deploy whose pods have already been rolled past it. Running the same -a prune
    # again right after this deploy's own builds finish is a race against every deploy_app_chart
    # stage that's concurrently trying to get its own freshly built image pulled by a pod —
    # `docker image prune -a` treats "not yet referenced by any container" as unused, so a
    # component whose Helm install/pod-schedule hasn't caught up yet loses its just-built image
    # out from under it. Post-build cleanup must stick to the build cache and stopped
    # containers, never images, however tempting it is to reuse this same stage for both.
    prune_unused_images: bool = True

    async def run(self) -> None:
        await asyncio.to_thread(self._clean)

    def _clean(self) -> None:
        import subprocess

        try:
            print("Cleaning Docker build cache and stopped containers...")
            subprocess.run(["docker", "builder", "prune", "-a", "-f"], check=True)
            if self.prune_unused_images:
                print("Cleaning unused Docker images...")
                subprocess.run(["docker", "image", "prune", "-a", "-f"], check=True)
            subprocess.run(["docker", "container", "prune", "-f"], check=True)
            print("Docker build cache and stopped containers cleaned")
        except (subprocess.SubprocessError, FileNotFoundError) as e:
            print(f"Warning: Failed to clean Docker cache: {e}")
