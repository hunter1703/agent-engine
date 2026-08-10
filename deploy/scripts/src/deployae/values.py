"""YAML helpers for reading chart values files and Chart.yaml dependency metadata."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml


def load_yaml(path: Path) -> dict[str, Any]:
    """Load a YAML file into a dict. A missing file is treated as empty, matching how
    Helm treats an absent values overlay."""
    if not path.is_file():
        return {}
    with path.open() as handle:
        return yaml.safe_load(handle) or {}


def parent_chart_name(chart_yaml_path: Path) -> str | None:
    """Return the first Helm dependency name declared in a Chart.yaml, if any."""
    dependencies = load_yaml(chart_yaml_path).get("dependencies") or []
    return dependencies[0]["name"] if dependencies else None
