"""Status stage: fetches one namespace's Helm releases, pods, and services. Fetching is
concurrent across namespaces; each stage buffers its own text into `report` rather than
printing directly, so the caller can print every namespace's block in a fixed order with
no risk of two blocks interleaving."""

from __future__ import annotations

import asyncio
import subprocess
from dataclasses import dataclass, field

from deployae.stages.base import Stage


@dataclass(eq=False, kw_only=True)
class StatusNamespaceStage(Stage):
    namespace: str
    report: str = field(default="", init=False)

    async def run(self) -> None:
        self.report = await asyncio.to_thread(self._collect)

    def _collect(self) -> str:
        sections = [
            ("Helm releases:", ["helm", "ls", "--namespace", self.namespace]),
            ("Pods:", ["kubectl", "get", "pods", "--namespace", self.namespace]),
            ("Services:", ["kubectl", "get", "svc", "--namespace", self.namespace]),
        ]
        lines = [f"=== Namespace: {self.namespace} ==="]
        for heading, args in sections:
            lines.append(heading)
            result = subprocess.run(args, capture_output=True, text=True)
            lines.append((result.stdout or result.stderr).rstrip())
        return "\n".join(lines)
