from __future__ import annotations

import json
from pathlib import Path

from deployae.seeding.catalog import pick_config_files, topological_agent_order


def test_pick_config_files_overlay_replaces_same_named_file(tmp_path: Path) -> None:
    base_dir, overlay_dir = tmp_path / "base", tmp_path / "overlay"
    base_dir.mkdir()
    overlay_dir.mkdir()
    (base_dir / "a.json").write_text("{}")
    (overlay_dir / "a.json").write_text("{}")
    (base_dir / "b.json").write_text("{}")

    files = pick_config_files(base_dir, overlay_dir)
    assert [f.parent.name for f in files if f.name == "a.json"] == ["overlay"]
    assert [f.parent.name for f in files if f.name == "b.json"] == ["base"]


def test_pick_config_files_overlay_only_file_appended(tmp_path: Path) -> None:
    base_dir, overlay_dir = tmp_path / "base", tmp_path / "overlay"
    base_dir.mkdir()
    overlay_dir.mkdir()
    (base_dir / "a.json").write_text("{}")
    (overlay_dir / "c.json").write_text("{}")

    assert [f.name for f in pick_config_files(base_dir, overlay_dir)] == ["a.json", "c.json"]


def test_pick_config_files_no_overlay_dir_uses_base_only(tmp_path: Path) -> None:
    base_dir = tmp_path / "base"
    base_dir.mkdir()
    (base_dir / "a.json").write_text("{}")

    files = pick_config_files(base_dir, tmp_path / "does-not-exist")
    assert [f.name for f in files] == ["a.json"]


def test_topological_agent_order_parents_after_children(tmp_path: Path) -> None:
    (tmp_path / "leaf1.json").write_text(json.dumps({"id": "leaf1"}))
    (tmp_path / "leaf2.json").write_text(json.dumps({"id": "leaf2"}))
    (tmp_path / "parent.json").write_text(
        json.dumps({"id": "parent", "subAgentIds": ["leaf1", "leaf2"]})
    )

    files = [tmp_path / "parent.json", tmp_path / "leaf1.json", tmp_path / "leaf2.json"]
    order_ids = [json.loads(f.read_text())["id"] for f in topological_agent_order(files)]

    assert order_ids.index("parent") > order_ids.index("leaf1")
    assert order_ids.index("parent") > order_ids.index("leaf2")


def test_topological_agent_order_handles_unknown_dependency(tmp_path: Path) -> None:
    (tmp_path / "a.json").write_text(json.dumps({"id": "a", "subAgentIds": ["not-in-this-batch"]}))
    ordered = topological_agent_order([tmp_path / "a.json"])
    assert [f.name for f in ordered] == ["a.json"]


def test_topological_agent_order_no_dependencies(tmp_path: Path) -> None:
    (tmp_path / "a.json").write_text(json.dumps({"id": "a"}))
    (tmp_path / "b.json").write_text(json.dumps({"id": "b"}))
    ordered = topological_agent_order([tmp_path / "a.json", tmp_path / "b.json"])
    assert {f.name for f in ordered} == {"a.json", "b.json"}


def test_topological_agent_order_detects_duplicate_id_collision(tmp_path: Path) -> None:
    """A same id declared twice collapses to one entry — this is exactly the class of
    bug found in the real configs/agents/ directory (two files declaring the same id),
    where one file's content silently overwrites the other's seeded document."""
    (tmp_path / "first.json").write_text(json.dumps({"id": "shared"}))
    (tmp_path / "second.json").write_text(json.dumps({"id": "shared"}))
    ordered = topological_agent_order([tmp_path / "first.json", tmp_path / "second.json"])
    assert len(ordered) == 1
