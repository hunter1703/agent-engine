from __future__ import annotations

from dataclasses import dataclass

import pytest

from deployae.stages.base import Stage, run_graph


@dataclass(eq=False, kw_only=True)
class _RecordingStage(Stage):
    log: list[str]
    fail: bool = False

    async def run(self) -> None:
        if self.fail:
            raise RuntimeError(f"{self.name} failed")
        self.log.append(self.name)


@dataclass(eq=False, kw_only=True)
class _WaitAndRecordStage(Stage):
    """Records the order dependencies actually resolved in, proving a dependent doesn't
    start until every one of its dependencies has finished."""

    log: list[str]

    async def run(self) -> None:
        for dependency in self.depends_on:
            assert dependency.name in self.log, (
                f"{self.name} ran before its dependency {dependency.name}"
            )
        self.log.append(self.name)


async def test_independent_stages_all_run() -> None:
    log: list[str] = []
    stages = [_RecordingStage(name=f"stage-{i}", log=log) for i in range(5)]
    await run_graph(stages)
    assert set(log) == {stage.name for stage in stages}


async def test_dependent_stage_waits_for_its_dependency() -> None:
    log: list[str] = []
    first = _WaitAndRecordStage(name="first", log=log)
    second = _WaitAndRecordStage(name="second", depends_on=(first,), log=log)
    await run_graph([second, first])  # deliberately out of order in the input list
    assert log == ["first", "second"]


async def test_diamond_dependency_resolves_correctly() -> None:
    log: list[str] = []
    root = _WaitAndRecordStage(name="root", log=log)
    left = _WaitAndRecordStage(name="left", depends_on=(root,), log=log)
    right = _WaitAndRecordStage(name="right", depends_on=(root,), log=log)
    tip = _WaitAndRecordStage(name="tip", depends_on=(left, right), log=log)
    await run_graph([tip, right, left, root])
    assert log[0] == "root"
    assert log[-1] == "tip"
    assert set(log[1:3]) == {"left", "right"}


async def test_disabled_stage_does_not_run_but_unblocks_dependents() -> None:
    log: list[str] = []
    disabled = _RecordingStage(name="disabled", log=log, enabled=False)
    dependent = _RecordingStage(name="dependent", depends_on=(disabled,), log=log)
    await run_graph([disabled, dependent])
    assert log == ["dependent"]


async def test_failed_stage_raises_system_exit() -> None:
    log: list[str] = []
    failing = _RecordingStage(name="failing", log=log, fail=True)
    with pytest.raises(SystemExit):
        await run_graph([failing])


async def test_dependent_of_failed_stage_never_runs() -> None:
    log: list[str] = []
    failing = _RecordingStage(name="failing", log=log, fail=True)
    dependent = _RecordingStage(name="dependent", depends_on=(failing,), log=log)
    with pytest.raises(SystemExit):
        await run_graph([failing, dependent])
    assert log == []
