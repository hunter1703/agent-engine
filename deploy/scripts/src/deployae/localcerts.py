"""Local-only TLS cert generation via `mkcert`, for terminating HTTPS at the local
ingress the way a real deployment terminates TLS at its edge. Never used outside the
`local` tier — mkcert's CA is trusted only on the machine that created it, so it has no
meaning for a shared/staging/prod environment, which would get a real cert instead
(e.g. via cert-manager)."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


class MkcertNotFoundError(RuntimeError):
    """`mkcert` isn't on PATH. Install it (`brew install mkcert` on macOS) and run
    `mkcert -install` once to trust its local CA in your system/browser keychain."""


def ensure_cert_files(hosts: list[str], cert_dir: Path) -> tuple[Path, Path]:
    """Returns (cert_file, key_file) for `hosts[0]` under `cert_dir`, generating them
    with `mkcert` if they don't already exist. Idempotent: an existing pair is reused
    as-is, so this never clobbers a cert someone's browser has already been made to
    trust. `hosts[0]` is also always included as a SAN alongside the rest of `hosts`."""
    if not hosts:
        raise ValueError("ensure_cert_files() needs at least one host")

    primary = hosts[0]
    cert_dir.mkdir(parents=True, exist_ok=True)
    cert_file = cert_dir / f"{primary}.pem"
    key_file = cert_dir / f"{primary}-key.pem"
    if cert_file.is_file() and key_file.is_file():
        return cert_file, key_file

    if shutil.which("mkcert") is None:
        raise MkcertNotFoundError(
            "mkcert is required to generate a local TLS cert for "
            f"{primary} but isn't on PATH. Install it (e.g. `brew install mkcert`), "
            "run `mkcert -install` once to trust its CA, then retry."
        )

    subprocess.run(
        [
            "mkcert",
            "-cert-file",
            str(cert_file),
            "-key-file",
            str(key_file),
            *hosts,
        ],
        check=True,
    )
    return cert_file, key_file
