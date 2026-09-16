"""`deployae buildInfraSetupImage` — builds the infra-setup container image for local or cloud."""

from __future__ import annotations

import argparse
import subprocess
import sys

from deployae.charts import DEPLOY_DIR, REPO_ROOT

ALIASES = ["build-infra-setup", "build-infra-setup-image"]


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--image-registry",
        "--registry",
        dest="image_registry",
        help="Registry prefix to build and push to (e.g. ghcr.io/<owner>). "
        "If omitted, builds into the local Docker daemon.",
    )
    parser.add_argument(
        "--platforms",
        default="linux/arm64",
        help="Target platforms when pushing to a registry (default: linux/arm64)",
    )


def run(args: argparse.Namespace) -> None:
    dockerfile_path = DEPLOY_DIR / "docker" / "infra-setup" / "Dockerfile"
    if not dockerfile_path.is_file():
        print(f"Error: Dockerfile not found at {dockerfile_path}", file=sys.stderr)
        sys.exit(1)

    if args.image_registry:
        image = f"{args.image_registry}/agent-engine/infra-setup:latest"
        print(f"Building and pushing {image} for platforms [{args.platforms}]...")
        cmd = [
            "docker",
            "buildx",
            "build",
            "--platform",
            args.platforms,
            "-t",
            image,
            "-f",
            str(dockerfile_path),
            "--push",
            str(REPO_ROOT),
        ]
        subprocess.run(cmd, check=True)
        print(f"Successfully built and pushed {image}")
    else:
        image = "agent-engine/infra-setup:latest"
        print(f"Building {image} for platform [linux/arm64] into local Docker daemon...")
        cmd = [
            "docker",
            "build",
            "--platform",
            "linux/arm64",
            "-t",
            image,
            "-f",
            str(dockerfile_path),
            str(REPO_ROOT),
        ]
        subprocess.run(cmd, check=True)
        print(f"Successfully built {image} for local Docker")
