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
    # Only safe to prune unused agent-engine images before this deploy's own builds have run: at
    # that point every candidate is genuinely stale, left over from an earlier deploy whose pods
    # have already been rolled past it. Running the same prune again right after this deploy's own
    # builds finish is a race against every deploy_app_chart stage that's concurrently trying to
    # get its own freshly built image pulled by a pod — "not yet referenced by any container"
    # looks like "unused" until that pod is actually scheduled, so a component whose Helm
    # install/pod-schedule hasn't caught up yet would lose its just-built image out from under it.
    # Post-build cleanup must stick to the build cache and stopped containers, never images,
    # however tempting it is to reuse this same stage for both.
    prune_unused_images: bool = True

    async def run(self) -> None:
        await asyncio.to_thread(self._clean)

    def _clean(self) -> None:
        import subprocess

        try:
            print("Cleaning Docker build cache and stopped containers...")
            subprocess.run(["docker", "builder", "prune", "-a", "-f"], check=True)
            if self.prune_unused_images:
                print("Cleaning unused agent-engine Docker images...")
                self._prune_agent_engine_images()
            subprocess.run(["docker", "container", "prune", "-f"], check=True)
            print("Docker build cache and stopped containers cleaned")
        except (subprocess.SubprocessError, FileNotFoundError) as e:
            print(f"Warning: Failed to clean Docker cache: {e}")

    def _prune_agent_engine_images(self) -> None:
        """Removes only this project's own images (`[registry/]agent-engine/<component>:<tag>`),
        never third-party images the host or other pods happen to share, like the seed Job's
        `python:3.12-slim` — those must stay cached across deploys instead of being re-pulled
        from Docker Hub every time. Plain `rmi` (not `-f`) skips any image a container still
        references, matching `docker image prune`'s "only unused" guarantee for the images this
        does target."""
        import subprocess

        result = subprocess.run(
            ["docker", "images", "--filter", "reference=*agent-engine/*", "-q"],
            check=True,
            capture_output=True,
            text=True,
        )
        image_ids = sorted({line.strip() for line in result.stdout.splitlines() if line.strip()})
        if image_ids:
            subprocess.run(["docker", "rmi", *image_ids], check=False)
