"""The generic orchestration engine: a `Stage` is one unit of build/deploy work with
references to the other Stage instances it depends on; `run_graph()` executes a list of
them concurrently, starting each one the instant its own dependencies are done.

This is the *only* concurrency mechanism in deployae — every command (deploy, cleanup)
constructs a list of Stage objects and hands it to `run_graph()`, rather than each
command inventing its own thread pool or ad hoc async loop.
"""

from __future__ import annotations

import asyncio
from abc import ABC, abstractmethod
from dataclasses import dataclass, field

from deployae import output


@dataclass(eq=False, kw_only=True)
class Stage(ABC):
    """`eq=False` keeps the default identity-based `__hash__`/`__eq__` — two Stage
    instances are never "equal" just because their fields match, since each represents a
    distinct node in the dependency graph. `depends_on` holds direct references to other
    Stage objects, not names, so the graph is a real object graph, not a string lookup."""

    name: str
    depends_on: tuple[Stage, ...] = field(default_factory=tuple)
    enabled: bool = True

    @abstractmethod
    async def run(self) -> None:
        """Do this stage's actual work. Only called when `enabled` is True and no
        dependency (transitively) failed."""


async def run_graph(stages: list[Stage]) -> None:
    """Runs every stage concurrently. A stage waits on its own `depends_on` before
    starting; once all stages it depends on have finished (successfully, skipped, or
    failed), it either runs or — if anything upstream failed — skips itself, still
    unblocking whatever depends on *it* so the abort propagates promptly instead of
    deadlocking. A stage already running when another one fails is left to finish; there
    is no value in killing an in-flight `helm upgrade` partway through.
    """
    events = {stage: asyncio.Event() for stage in stages}
    aborted = asyncio.Event()
    errors: list[tuple[str, BaseException]] = []

    async def run_one(stage: Stage) -> None:
        try:
            for dependency in stage.depends_on:
                await events[dependency].wait()
            if stage.enabled and not aborted.is_set():
                await stage.run()
        except Exception as error:
            errors.append((stage.name, error))
            aborted.set()
        finally:
            events[stage].set()

    await asyncio.gather(*(run_one(stage) for stage in stages))

    if errors:
        for name, error in errors:
            output.error(f"{name}: {error}")
        raise SystemExit(1)
