"""Build stages: one Gradle build feeding N parallel Docker image builds."""

from __future__ import annotations

import asyncio
import subprocess
from dataclasses import dataclass

from deployae.charts import DEPLOY_DIR, REPO_ROOT
from deployae.stages.base import Stage

GRADLE_TASKS = {
    "agent": ":agent:core:quarkusBuild",
    "catalog": ":catalog:core:quarkusBuild",
    "rest": ":interfaces:rest:quarkusBuild",
    "knowledge": ":knowledge:core:quarkusBuild",
    "connectors": ":connectors:core:quarkusBuild",
}

DOCKER_MODULES = {
    "agent": "agent/core",
    "catalog": "catalog/core",
    "rest": "interfaces/rest",
    "knowledge": "knowledge/core",
    "connectors": "connectors/core",
}


@dataclass(eq=False, kw_only=True)
class BuildGradleStage(Stage):
    components: tuple[str, ...]

    async def run(self) -> None:
        await asyncio.to_thread(self._build)

    def _build(self) -> None:
        """Builds every component's Quarkus artifacts in one invocation — a real
        prerequisite the Dockerfile depends on, and cheaper than one gradle process per
        component since `--parallel` already parallelizes across modules internally."""
        tasks = [GRADLE_TASKS[component] for component in self.components]
        subprocess.run(["./gradlew", *tasks, "-x", "test", "--parallel"], cwd=REPO_ROOT, check=True)


@dataclass(eq=False, kw_only=True)
class BuildDockerImageStage(Stage):
    component: str
    tag: str
    registry_prefix: str | None = None
    push: bool = False

    async def run(self) -> None:
        await asyncio.to_thread(self._build)

    @property
    def image(self) -> str:
        prefix = f"{self.registry_prefix}/" if self.registry_prefix else ""
        return f"{prefix}agent-engine/{self.component}:{self.tag}"

    def _build(self) -> None:
        subprocess.run(
            [
                "docker",
                "build",
                "--build-arg",
                f"SERVICE_MODULE={DOCKER_MODULES[self.component]}",
                "-t",
                self.image,
                "-f",
                str(DEPLOY_DIR / "docker" / "Dockerfile"),
                str(REPO_ROOT),
            ],
            check=True,
        )
        if self.push:
            subprocess.run(["docker", "push", self.image], check=True)
        print(f"Built {self.image}")
