"""Build stages: one Gradle build feeding N parallel Docker image builds."""

from __future__ import annotations

import asyncio
import hashlib
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path

from deployae.charts import DEPLOY_DIR, REPO_ROOT
from deployae.stages.base import Stage

LIBS_CATALOG = REPO_ROOT / "gradle" / "libs.versions.toml"
DEPS_BASE_CONTEXT = DEPLOY_DIR / "docker" / ".deps-base-context"

GRADLE_TASKS = {
    "agent": ":agent:core:quarkusBuild",
    "catalog": ":catalog:core:quarkusBuild",
    "rest": ":interfaces:rest:quarkusBuild",
    "knowledge": ":knowledge:core:quarkusBuild",
    "connectors": ":connectors:core:quarkusBuild",
    "scheduler": ":scheduler:core:quarkusBuild",
    "tenancy": ":tenancy:core:quarkusBuild",
    "internal": ":internal:quarkusBuild",
}

DOCKER_MODULES = {
    "agent": "agent/core",
    "catalog": "catalog/core",
    "rest": "interfaces/rest",
    "knowledge": "knowledge/core",
    "connectors": "connectors/core",
    "scheduler": "scheduler/core",
    "tenancy": "tenancy/core",
    "internal": "internal",
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
class EnsureDepsBaseImageStage(Stage):
    """Builds (or reuses) the shared dependency-jar base image every service's own image is
    built FROM (see Dockerfile.base) — the union of every component's
    build/quarkus-app/lib/{boot,main}, which is what actually dominates each service's image
    size and, unlike each service's own classes, is almost entirely identical content shared
    across them.

    Tagged by a hash of gradle/libs.versions.toml, not by commit: the tag itself *is* the cache
    key, so there is no stored-commit bookkeeping to go stale or fail to resolve — an unchanged
    catalog always recomputes the same tag, which is either already present (skip) or not (build
    once, under that tag, for every future deploy to reuse)."""

    components: tuple[str, ...]
    registry_prefix: str | None = None
    push: bool = False

    async def run(self) -> None:
        await asyncio.to_thread(self._ensure)

    @property
    def tag(self) -> str:
        digest = hashlib.sha256(LIBS_CATALOG.read_bytes()).hexdigest()
        return digest[:12]

    @property
    def image(self) -> str:
        prefix = f"{self.registry_prefix}/" if self.registry_prefix else ""
        return f"{prefix}agent-engine/deps-base:{self.tag}"

    def _ensure(self) -> None:
        if self._exists_locally():
            print(f"Reusing {self.image} (already built — libs.versions.toml unchanged)")
            return
        if self.registry_prefix and self._pull():
            print(f"Reusing {self.image} (pulled from registry — libs.versions.toml unchanged)")
            return
        print(f"Building {self.image} (missing locally and in the registry)...")
        self._build()
        if self.push:
            subprocess.run(["docker", "push", self.image], check=True)

    def _exists_locally(self) -> bool:
        return (
            subprocess.run(
                ["docker", "image", "inspect", self.image], capture_output=True
            ).returncode
            == 0
        )

    def _pull(self) -> bool:
        return subprocess.run(["docker", "pull", self.image], capture_output=True).returncode == 0

    def _build(self) -> None:
        context = self._assemble_lib_union()
        subprocess.run(
            [
                "docker",
                "build",
                "--platform",
                "linux/arm64",
                "-t",
                self.image,
                "-f",
                str(DEPLOY_DIR / "docker" / "Dockerfile.base"),
                str(context),
            ],
            check=True,
        )

    def _assemble_lib_union(self) -> Path:
        """Collects each component's lib/{boot,main} jars into one build context, keeping
        whichever copy of a given filename is seen first — safe because two components on the
        same dependency catalog that both carry e.g. jackson-databind-2.x.jar have the exact
        same bytes under that name."""
        lib_dir = DEPS_BASE_CONTEXT / "lib"
        if DEPS_BASE_CONTEXT.exists():
            shutil.rmtree(DEPS_BASE_CONTEXT)
        for subdir in ("boot", "main"):
            (lib_dir / subdir).mkdir(parents=True, exist_ok=True)
        for component in self.components:
            module_lib = REPO_ROOT / DOCKER_MODULES[component] / "build" / "quarkus-app" / "lib"
            for subdir in ("boot", "main"):
                src = module_lib / subdir
                if not src.is_dir():
                    continue
                for jar in src.iterdir():
                    dest = lib_dir / subdir / jar.name
                    if not dest.exists():
                        shutil.copy2(jar, dest)
        return DEPS_BASE_CONTEXT


@dataclass(eq=False, kw_only=True)
class BuildDockerImageStage(Stage):
    component: str
    tag: str
    deps_base_image: str
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
                "--platform",
                "linux/arm64",
                "--build-arg",
                f"SERVICE_MODULE={DOCKER_MODULES[self.component]}",
                "--build-arg",
                f"DEPS_BASE_IMAGE={self.deps_base_image}",
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
