#!/usr/bin/env python3
"""One entry point for the Pinterest board recommendations.

    modelae download URL   downloads the boards at URL with gallery-dl, using cookies.txt, and
                            writes them to training.zip, replacing it once the download completes
    modelae training       starts the model server and indexes the images in training.zip
    modelae start          starts the model server and the proxy that answers the Pinterest app's
                            board picker (mitmdump running mitm.py, so no dashboard opens)

The downloaded boards are kept in a temporary folder only until they are in training.zip.
"""

from __future__ import annotations

import argparse
import os
import resource
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from contextlib import contextmanager
from pathlib import Path

import ingest_knowledge
import training_data

HERE = Path(__file__).parent
COOKIES_PATH = HERE / "cookies.txt"
MODEL_SERVER_HEALTH_URL = "http://127.0.0.1:8081/health"
MODEL_SERVER_START_SECONDS = 300
DEFAULT_ENGINE_URL = "https://agents.com"
OPEN_FILES_LIMIT = 10240


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    download_parser = commands.add_parser("download", help="download the boards into training.zip")
    download_parser.add_argument(
        "url",
        nargs="?",
        default=os.environ.get("PINTEREST_URL"),
        help="the Pinterest profile or board to download (default: $PINTEREST_URL)",
    )

    training_parser = commands.add_parser("training", help="index training.zip")
    _add_engine_arguments(training_parser)
    training_parser.add_argument("--workers", type=int, default=4)
    training_parser.add_argument(
        "--reindex", action="store_true", help="index images that are already indexed again"
    )

    start_parser = commands.add_parser("start", help="start the model server and the proxy")
    _add_engine_arguments(start_parser)
    start_parser.add_argument("--port", type=int, default=9000, help="the proxy's port")

    args = parser.parse_args()
    _raise_open_files_limit()
    # Exiting on SIGTERM, as on Ctrl-C, stops the processes started here instead of leaving them.
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(1))
    if args.command == "download":
        return download(args)
    if args.command == "training":
        return training(args)
    return start(args)


def download(args: argparse.Namespace) -> int:
    if not args.url:
        print("Give the Pinterest URL to download, or set PINTEREST_URL", file=sys.stderr)
        return 1
    if shutil.which("gallery-dl") is None:
        print("gallery-dl is not installed", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory(prefix="boards-") as temporary:
        record = Path(temporary) / training_data.DOWNLOAD_RECORD
        if training_data.ARCHIVE_PATH.is_file():
            with training_data.TrainingArchive() as archive:
                if archive.has(training_data.DOWNLOAD_RECORD):
                    record.write_bytes(archive.read(training_data.DOWNLOAD_RECORD))
        exit_code = 1
        try:
            exit_code = subprocess.run(
                [
                    "gallery-dl", "--cookies", str(COOKIES_PATH), "--destination", temporary,
                    "--download-archive", str(record), args.url,
                ]
            ).returncode
        finally:
            # Whatever was downloaded is kept, even when the download is stopped.
            count = training_data.write_archive(Path(temporary))
            print(f"Added {count} files to {training_data.ARCHIVE_PATH}")
    if exit_code != 0:
        print(f"gallery-dl stopped with exit code {exit_code}; what it downloaded is kept, "
              "run the download again to continue", file=sys.stderr)
    return exit_code


def training(args: argparse.Namespace) -> int:
    ingest_arguments = ["--rest-url", args.engine_url, "--workers", str(args.workers)]
    if args.insecure:
        ingest_arguments.append("--insecure")
    if not args.reindex:
        ingest_arguments.append("--skip-existing")
    with model_server():
        ingest_knowledge.main(ingest_arguments)
    return 0


def start(args: argparse.Namespace) -> int:
    environment = {**os.environ, "AGENT_ENGINE_URL": args.engine_url}
    if args.insecure:
        environment["AGENT_ENGINE_INSECURE"] = "true"
    mitmdump = Path(sys.executable).parent / "mitmdump"
    with model_server():
        return subprocess.run(
            [
                str(mitmdump),
                "--listen-host", "0.0.0.0",
                "--listen-port", str(args.port),
                "-s", str(HERE / "mitm.py"),
            ],
            env=environment,
        ).returncode


def _raise_open_files_limit() -> None:
    """Raises this process's limit on open files, which the model server and the proxy inherit.
    macOS gives a terminal 256, which a proxy carrying a phone's connections can exceed."""
    soft, hard = resource.getrlimit(resource.RLIMIT_NOFILE)
    wanted = OPEN_FILES_LIMIT if hard == resource.RLIM_INFINITY else min(OPEN_FILES_LIMIT, hard)
    if soft < wanted:
        resource.setrlimit(resource.RLIMIT_NOFILE, (wanted, hard))


def _add_engine_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--engine-url",
        default=os.environ.get("AGENT_ENGINE_URL", DEFAULT_ENGINE_URL),
        help="the Agent Engine's URL (default: $AGENT_ENGINE_URL or %(default)s)",
    )
    parser.add_argument(
        "--insecure",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="skip TLS verification of the Agent Engine, whose local certificate is self-signed",
    )


@contextmanager
def model_server():
    """Runs the model server for the duration of the block, unless one is already running."""
    if _model_server_is_healthy():
        yield
        return
    process = subprocess.Popen([sys.executable, "-m", "model.server"], cwd=HERE)
    try:
        deadline = time.time() + MODEL_SERVER_START_SECONDS
        while not _model_server_is_healthy():
            if process.poll() is not None or time.time() > deadline:
                raise RuntimeError("The model server did not start")
            time.sleep(1)
        yield
    finally:
        process.terminate()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()


def _model_server_is_healthy() -> bool:
    try:
        with urllib.request.urlopen(MODEL_SERVER_HEALTH_URL, timeout=2):
            return True
    except (urllib.error.URLError, OSError):
        return False


if __name__ == "__main__":
    sys.exit(main())
