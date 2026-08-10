"""`deployae status` — shows Helm releases, pods, and services for every namespace the
known charts deploy into."""

from __future__ import annotations

import argparse
import subprocess

from deployae.charts import ALL_CHARTS


def add_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("-n", "--namespace", help="Override every chart's namespace")


def run(args: argparse.Namespace) -> None:
    for namespace in sorted({chart.namespace(args.namespace) for chart in ALL_CHARTS}):
        print(f"=== Namespace: {namespace} ===")
        print("Helm releases:")
        subprocess.run(["helm", "ls", "--namespace", namespace])
        print("\nPods:")
        subprocess.run(["kubectl", "get", "pods", "--namespace", namespace])
        print("\nServices:")
        subprocess.run(["kubectl", "get", "svc", "--namespace", namespace])
        print()
