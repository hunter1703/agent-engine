"""Stages for one Helm chart's lifecycle: deploy, uninstall — plus the two small setup
stages (namespace, ingress controller) chart deploys depend on."""

from __future__ import annotations

import asyncio
from abc import abstractmethod
from dataclasses import dataclass, field
from pathlib import Path

from deployae import helm, kube, localcerts
from deployae.charts import LOCAL_CERTS_DIR, Chart
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
    rendered: str | None = field(default=None, init=False)

    async def execute(self) -> None:
        if self.lint_first:
            await asyncio.to_thread(helm.lint, self.chart, self.ctx)

        rendered = await asyncio.to_thread(
            helm.upgrade_install,
            self.chart,
            self.ctx,
            dry_run=self.dry_run,
            atomic=self.atomic,
            timeout=self.timeout,
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
class EnsureEnvSecretStage(Stage):
    """Writes the one namespace Secret that app-base mounts into every app pod. Must
    run after the namespace exists and before those charts' DeployChartStage, so the
    Secret is present by the time pods start."""

    namespace: str
    env_file: Path | None = None

    async def run(self) -> None:
        secret_name = await asyncio.to_thread(
            kube.ensure_env_secret, self.namespace, self.env_file
        )
        print(f"Applied Secret {secret_name} in namespace {self.namespace}")


@dataclass(eq=False, kw_only=True)
class EnsureIngressControllerStage(Stage):
    async def run(self) -> None:
        await asyncio.to_thread(helm.ensure_ingress_controller)


@dataclass(eq=False, kw_only=True)
class EnsureLocalTlsCertStage(Stage):
    """Generates (or reuses) a mkcert cert for a chart's ingress hosts and creates/
    updates the matching Secret, so the chart's own DeployChartStage never needs to run
    with a Secret its Ingress references not existing yet. `enabled` should be set to
    False for any chart/tier whose resolved ingress config doesn't need one (see
    Chart.needs_local_tls_cert) — mkcert's CA has no meaning outside the machine that
    trusts it, and a tier with its own clusterIssuer already gets a real cert elsewhere."""

    chart: Chart
    tier: str
    namespace_override: str | None = None

    async def run(self) -> None:
        hosts = self.chart.ingress_tls_hosts(self.tier)
        cert_file, key_file = await asyncio.to_thread(
            localcerts.ensure_cert_files, hosts, LOCAL_CERTS_DIR
        )
        await asyncio.to_thread(
            kube.ensure_tls_secret,
            self.chart.tls_secret_name(self.tier),
            self.chart.namespace(self.namespace_override),
            cert_file,
            key_file,
        )
