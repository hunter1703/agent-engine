"""Stages for one Helm chart's lifecycle: deploy, uninstall — plus the two small setup
stages (namespace, ingress controller) chart deploys depend on."""

from __future__ import annotations

import asyncio
from abc import abstractmethod
from dataclasses import dataclass, field
from pathlib import Path

from deployae import helm, kube
from deployae.charts import Chart
from deployae.stages.base import Stage


@dataclass(eq=False, kw_only=True)
class HelmStage(Stage):
    """Base for any stage operating on one local Helm chart: always refreshes the
    chart's vendored subchart dependencies before running its specific helm operation.
    Subclasses implement `execute()`, not `run()`, so this refresh never has to be
    repeated per subclass."""

    chart: Chart
    ctx: helm.DeployContext

    async def run(self) -> None:
        await asyncio.to_thread(helm.ensure_dependencies, self.chart)
        await self.execute()

    @abstractmethod
    async def execute(self) -> None: ...

    def header(self) -> str:
        release_name = self.chart.release_name(
            self.chart.effective_tier(self.ctx.tier, self.ctx.environment)
        )
        return f"---\n# {self.chart.name} ({release_name})"


@dataclass(eq=False, kw_only=True)
class DeployChartStage(HelmStage):
    atomic: bool = True
    timeout: str = helm.DEFAULT_TIMEOUT
    dry_run: bool = False
    lint_first: bool = False
    needs_env_secret: bool = False
    env_file: Path | None = None
    rendered: str | None = field(default=None, init=False)

    async def execute(self) -> None:
        if self.lint_first:
            await asyncio.to_thread(helm.lint, self.chart, self.ctx)

        extra_set = None
        if self.needs_env_secret and not self.dry_run:
            extra_set = await asyncio.to_thread(self._env_secret_set)

        rendered = await asyncio.to_thread(
            helm.upgrade_install,
            self.chart,
            self.ctx,
            dry_run=self.dry_run,
            atomic=self.atomic,
            timeout=self.timeout,
            extra_set=extra_set,
        )
        if self.dry_run:
            self.rendered = rendered
            return

        namespace = self.chart.namespace(self.ctx.namespace)
        print(f"Deployed {self.chart.name} to namespace {namespace}")
        kind = self.chart.workload_kind()
        if kind:
            await asyncio.to_thread(
                kube.rollout_status,
                namespace,
                kind,
                self.chart.resource_name(self.ctx.tier),
                self.timeout,
            )

    def _env_secret_set(self) -> list[str] | None:
        """Creates/updates the .env Secret for charts needing runtime API keys injected
        as environment variables. Deferred to execute() (not computed at construction
        time) because it needs the chart's namespace to already exist — guaranteed by
        this stage's own `depends_on` an EnsureNamespaceStage, resolved by the time
        `run()` reaches here, not before the graph even starts."""
        assert self.env_file is not None
        release_name = self.chart.release_name(
            self.chart.effective_tier(self.ctx.tier, self.ctx.environment)
        )
        secret_name = kube.ensure_env_secret(
            release_name, self.chart.namespace(self.ctx.namespace), self.env_file
        )
        return [f"app-base.secrets.envSecretName={secret_name}"] if secret_name else None


@dataclass(eq=False, kw_only=True)
class UninstallChartStage(Stage):
    chart: Chart
    tier: str | None
    environment: str | None
    namespace: str

    async def run(self) -> None:
        removed = await asyncio.to_thread(
            helm.uninstall, self.chart, self.tier, self.environment, self.namespace
        )
        if removed:
            print(f"Removed {self.chart.name} from namespace {self.namespace}")


@dataclass(eq=False, kw_only=True)
class EnsureNamespaceStage(Stage):
    namespace: str

    async def run(self) -> None:
        await asyncio.to_thread(kube.create_namespace, self.namespace)


@dataclass(eq=False, kw_only=True)
class EnsureIngressControllerStage(Stage):
    async def run(self) -> None:
        await asyncio.to_thread(helm.ensure_ingress_controller)
