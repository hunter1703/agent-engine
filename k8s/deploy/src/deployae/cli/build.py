"""`deployae build` — builds (and optionally pushes) the Docker images for the app
services. `build_images()` is a plain function, called directly by `apply`/`deploy` as
well as this command's own `run()` — no subprocess re-invocation of the CLI itself."""

from __future__ import annotations

import argparse
import subprocess
from concurrent.futures import ThreadPoolExecutor

from deployae.charts import APP_CHART_NAMES, K8S_DIR
from deployae.cli.common import default_image_tag

REPO_ROOT = K8S_DIR.parent

_GRADLE_TASKS = {
    "agent": ":agent:core:quarkusBuild",
    "catalog": ":catalog:core:quarkusBuild",
    "rest": ":interfaces:rest:quarkusBuild",
    "knowledge": ":knowledge:core:quarkusBuild",
    "connectors": ":connectors:core:quarkusBuild",
}

_DOCKER_MODULES = {
    "agent": "agent/core",
    "catalog": "catalog/core",
    "rest": "interfaces/rest",
    "knowledge": "knowledge/core",
    "connectors": "connectors/core",
}


def _image_name(component: str, tag: str, registry_prefix: str | None) -> str:
    prefix = f"{registry_prefix}/" if registry_prefix else ""
    return f"{prefix}agent-engine/{component}:{tag}"


def _build_component_image(
    component: str, tag: str, registry_prefix: str | None, push: bool
) -> None:
    image = _image_name(component, tag, registry_prefix)
    subprocess.run(
        [
            "docker",
            "build",
            "--build-arg",
            f"SERVICE_MODULE={_DOCKER_MODULES[component]}",
            "-t",
            image,
            "-f",
            str(REPO_ROOT / "docker" / "Dockerfile"),
            str(REPO_ROOT),
        ],
        check=True,
    )
    if push:
        subprocess.run(["docker", "push", image], check=True)
    print(f"Built {image}")


def build_images(
    components: list[str], *, tag: str, registry_prefix: str | None = None, push: bool = False
) -> None:
    """Runs the Quarkus build for every component first (a real prerequisite the
    Dockerfile depends on), then builds each Docker image in parallel."""
    if not components:
        return
    tasks = [_GRADLE_TASKS[component] for component in components]
    subprocess.run(["./gradlew", *tasks, "-x", "test", "--parallel"], cwd=REPO_ROOT, check=True)

    with ThreadPoolExecutor(max_workers=len(components)) as pool:
        futures = [
            pool.submit(_build_component_image, component, tag, registry_prefix, push)
            for component in components
        ]
        for future in futures:
            future.result()


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "components",
        nargs="*",
        choices=list(APP_CHART_NAMES),
        default=[],
        help="Components to build (default: all five)",
    )
    parser.add_argument("--tag", default=default_image_tag(), help="Image tag to build")
    parser.add_argument("--registry-prefix", help="Optional registry/repository prefix")
    parser.add_argument("--push", action="store_true", help="Push built images after build")


def run(args: argparse.Namespace) -> None:
    components = args.components or list(APP_CHART_NAMES)
    build_images(components, tag=args.tag, registry_prefix=args.registry_prefix, push=args.push)
