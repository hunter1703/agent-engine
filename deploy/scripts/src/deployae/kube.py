"""Thin subprocess wrapper around `kubectl`, plus a reusable port-forward primitive.

`port_forward()` is the one connectivity primitive every seeding/init module shares:
forward a Service port to localhost, then talk to it with a native Python client
(pymongo/psycopg/httpx) instead of shelling into a pod or spinning up a throwaway one.
"""

from __future__ import annotations

import contextlib
import os
import re
import socket
import subprocess
import time
from collections.abc import Iterator
from pathlib import Path

ENV_SECRET_NAME = "agent-engine-secrets"
_SECRET_KEY_PATTERN = re.compile(r"^[-._a-zA-Z][-._a-zA-Z0-9]*$")


class KubectlError(RuntimeError):
    """A `kubectl` invocation exited non-zero."""


def _run(args: list[str], *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(["kubectl", *args], text=True, capture_output=capture)
    if result.returncode != 0:
        stderr = result.stderr if capture else ""
        raise KubectlError(f"kubectl {' '.join(args)} exited {result.returncode}: {stderr}")
    return result


def _apply_stdin(manifest_yaml: str) -> None:
    subprocess.run(
        ["kubectl", "apply", "-f", "-"],
        input=manifest_yaml,
        text=True,
        check=True,
        capture_output=True,
    )


def create_namespace(namespace: str) -> None:
    dry_run = subprocess.run(
        ["kubectl", "create", "namespace", namespace, "--dry-run=client", "-o", "yaml"],
        capture_output=True,
        text=True,
        check=True,
    )
    _apply_stdin(dry_run.stdout)


def delete_namespace(namespace: str) -> None:
    _run(["delete", "namespace", namespace, "--ignore-not-found"], capture=True)


def delete_pvcs_by_instance(namespace: str, instance: str) -> None:
    _run(["delete", "pvc", "-n", namespace, "-l", f"app.kubernetes.io/instance={instance}", "--ignore-not-found"], capture=True)


def delete_by_label(namespace: str, label_selector: str) -> None:
    _run(
        ["delete", "all", "-l", label_selector, "--namespace", namespace, "--ignore-not-found"],
        capture=True,
    )


def service_exists(namespace: str, name: str) -> bool:
    result = subprocess.run(
        ["kubectl", "get", "svc", name, "--namespace", namespace], capture_output=True
    )
    return result.returncode == 0


def rollout_status(namespace: str, kind: str, name: str, timeout: str) -> None:
    _run(["rollout", "status", f"{kind}/{name}", "--namespace", namespace, "--timeout", timeout])


def ensure_env_secret(namespace: str, env_file: Path | None) -> str:
    """Creates/updates the single namespace Secret every app pod mounts via envFrom.

    Contents are the full `--env` file when one is given (local deploys). `INFRA_MONGODB_URI`
    from the process environment is included as well, so CI can inject the Atlas URI without
    a file. The applied Secret is exactly this set of keys — a deploy with no secrets
    replaces any previous contents, so a leftover Atlas URI cannot override a later local
    ConfigMap URI.
    """
    import base64

    import yaml
    from dotenv import dotenv_values

    entries: dict[str, str] = {}
    if env_file is not None:
        for key, value in dotenv_values(env_file).items():
            if key and _SECRET_KEY_PATTERN.match(key) and value is not None:
                entries[key] = str(value)

    mongo_uri = os.environ.get("INFRA_MONGODB_URI")
    if mongo_uri:
        entries.setdefault("INFRA_MONGODB_URI", mongo_uri)

    data = {
        key: base64.b64encode(value.encode()).decode() for key, value in entries.items()
    }
    secret_manifest = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {
            "name": ENV_SECRET_NAME,
            "namespace": namespace,
        },
        "type": "Opaque",
        "data": data,
    }
    _apply_stdin(yaml.dump(secret_manifest))
    return ENV_SECRET_NAME


def ensure_tls_secret(secret_name: str, namespace: str, cert_file: Path, key_file: Path) -> None:
    """Creates/updates a `kubernetes.io/tls` Secret from an existing cert+key pair.
    Idempotent, like `ensure_env_secret` — safe to call on every deploy."""
    dry_run = subprocess.run(
        [
            "kubectl",
            "create",
            "secret",
            "tls",
            secret_name,
            f"--cert={cert_file}",
            f"--key={key_file}",
            "--namespace",
            namespace,
            "--dry-run=client",
            "-o",
            "yaml",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    _apply_stdin(dry_run.stdout)


def _free_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def _wait_until_reachable(
    process: subprocess.Popen[str], local_port: int, service: str, timeout: float
) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            stderr = process.stderr.read() if process.stderr else ""
            raise KubectlError(f"port-forward to {service} exited early: {stderr}")
        try:
            with socket.create_connection(("127.0.0.1", local_port), timeout=0.5):
                return
        except OSError:
            time.sleep(0.2)
    raise KubectlError(f"Timed out waiting for port-forward to {service}:{local_port}")


@contextlib.contextmanager
def port_forward(
    namespace: str, service: str, remote_port: int, *, ready_timeout: float = 30.0
) -> Iterator[int]:
    """Forwards a Service port to a free local port, waits until it actually accepts
    connections, yields the local port, and always tears the forward down on exit."""
    local_port = _free_local_port()
    process = subprocess.Popen(
        [
            "kubectl",
            "port-forward",
            "--namespace",
            namespace,
            f"service/{service}",
            f"{local_port}:{remote_port}",
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
    )
    try:
        _wait_until_reachable(process, local_port, service, ready_timeout)
        yield local_port
    finally:
        process.terminate()
        with contextlib.suppress(subprocess.TimeoutExpired):
            process.wait(timeout=5)
