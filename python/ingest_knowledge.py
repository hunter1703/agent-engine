#!/usr/bin/env python3
"""Batch Knowledge Ingestion Script for Agent Engine.

Indexes the images of the boards in training.zip, each with the name of its board as its tag.
The archive is read in memory, one image at a time, and never extracted to disk. Images are
uploaded and indexed concurrently, `--workers` at a time, and are not described to text unless
`--describe` is given. With `--skip-existing`, images already indexed are left alone.

Usage:
    python ingest_knowledge.py --skip-existing
    python ingest_knowledge.py --rest-url https://agents.com --insecure --workers 8
    python ingest_knowledge.py --ca-bundle "$(mkcert -CAROOT)/rootCA.pem"
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import ssl
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any, Dict, List, Optional

from training_data import TrainingArchive


class AgentEngineClient:
    """Standard-library HTTP client for Agent Engine REST API using systemSession."""

    def __init__(
        self,
        base_url: str = "http://localhost:8080",
        domain: str = "localhost",
        session_cookie: str = "systemSession",
        insecure: bool = False,
        ca_bundle: Optional[str] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.domain = domain
        self.session_cookie = session_cookie
        self.origin = (
            f"http://{self.domain}:3000"
            if self.domain == "localhost"
            else f"https://{self.domain}"
        )

        handlers: list[Any] = []
        if insecure:
            ctx = ssl.create_default_context()
            ctx.check_hostname = False
            ctx.verify_mode = ssl.CERT_NONE
            handlers.append(urllib.request.HTTPSHandler(context=ctx))
        elif ca_bundle:
            ctx = ssl.create_default_context(cafile=ca_bundle)
            handlers.append(urllib.request.HTTPSHandler(context=ctx))

        self.opener = urllib.request.build_opener(*handlers)

    def upload_file(self, name: str, content: bytes) -> Dict[str, Any]:
        """Upload an image to /v1/storage/upload."""
        mime_type, _ = mimetypes.guess_type(name)
        if not mime_type:
            mime_type = "image/jpeg"

        encoded_name = urllib.request.quote(name)
        url = f"{self.base_url}/v1/storage/upload?name={encoded_name}"

        req = urllib.request.Request(
            url,
            data=content,
            headers={
                "Content-Type": mime_type,
                "Content-Length": str(len(content)),
                "Origin": self.origin,
                "Cookie": f"session={self.session_cookie}",
            },
            method="POST",
        )
        try:
            with self.opener.open(req, timeout=60) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            err = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Upload failed: HTTP {e.code} - {err}") from e

    def is_indexed(self, knowledge_id: str) -> bool:
        """Whether knowledge `knowledge_id` exists and its indexing completed."""
        req = urllib.request.Request(
            f"{self.base_url}/v1/knowledge/{knowledge_id}",
            headers={"Origin": self.origin, "Cookie": f"session={self.session_cookie}"},
            method="GET",
        )
        try:
            with self.opener.open(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8")).get("indexingStatus") == "COMPLETED"
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return False
            raise

    def create_knowledge(
        self,
        file_details: Dict[str, Any],
        title: Optional[str] = None,
        description: Optional[str] = None,
        tags: Optional[List[str]] = None,
        knowledge_id: Optional[str] = None,
        wait_for_completion: bool = True,
        describe_media: bool = True,
        embedders: Optional[List[str]] = None,
    ) -> Dict[str, Any]:
        """Index an uploaded file via /v1/knowledge."""
        url = f"{self.base_url}/v1/knowledge"
        payload: Dict[str, Any] = {
            "fileDetails": file_details,
            "waitForCompletion": wait_for_completion,
            "settings": {
                "describeMedia": describe_media,
                "modalityVsEmbeddingModelIds": {"IMAGE": embedders or []},
            },
        }
        if title:
            payload["title"] = title
        if description:
            payload["description"] = description
        if tags:
            payload["tags"] = tags
        if knowledge_id:
            payload["knowledgeId"] = knowledge_id

        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "Content-Type": "application/json",
                "Origin": self.origin,
                "Cookie": f"session={self.session_cookie}",
            },
            method="POST",
        )
        try:
            with self.opener.open(req, timeout=120) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            err = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Index failed: HTTP {e.code} - {err}") from e


def index_image(
    client: AgentEngineClient,
    archive: TrainingArchive,
    entry: str,
    board: str,
    describe_media: bool,
    embedders: List[str],
    skip_existing: bool = False,
) -> bool:
    """Uploads the image `entry` of the archive and indexes it with its board as the tag.

    Returns False, doing nothing, when `skip_existing` and it is already indexed."""
    knowledge_id = archive.knowledge_id(entry)
    if skip_existing and client.is_indexed(knowledge_id):
        return False
    name = entry.rsplit("/", 1)[-1]
    file_details = client.upload_file(name, archive.read(entry))
    client.create_knowledge(
        file_details=file_details,
        title=f"{name.rsplit('.', 1)[0]} - {board}",
        tags=[board],
        knowledge_id=knowledge_id,
        wait_for_completion=True,
        describe_media=describe_media,
        embedders=embedders,
    )
    return True


def ingest(
    archive: TrainingArchive,
    client: AgentEngineClient,
    dry_run: bool = False,
    workers: int = 4,
    describe_media: bool = False,
    embedders: Optional[List[str]] = None,
    skip_existing: bool = False,
) -> Dict[str, int]:
    """Indexes the images of every board in `archive`. Returns the number of boards, and of
    images indexed, skipped and failed."""
    board_vs_images = archive.board_vs_images()
    total = sum(len(images) for images in board_vs_images.values())
    counts = {"boards": len(board_vs_images), "indexed": 0, "skipped": 0, "failed": 0}
    print(f"\n{counts['boards']} boards, {total} images")
    if dry_run:
        counts["indexed"] = total
        return counts

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [
            pool.submit(
                index_image, client, archive, entry, board, describe_media, embedders or [],
                skip_existing,
            )
            for board, images in board_vs_images.items()
            for entry in images
        ]
        for done, future in enumerate(as_completed(futures), 1):
            try:
                counts["indexed" if future.result() else "skipped"] += 1
            except Exception as e:
                counts["failed"] += 1
                print(f"   ✗ {e}", flush=True)
            if done % 100 == 0 or done == total:
                print(f"   {done}/{total} done", flush=True)
    return counts


def main(argv: Optional[List[str]] = None) -> None:
    parser = argparse.ArgumentParser(
        description="Index the images of the boards in training.zip into Agent Engine."
    )
    parser.add_argument(
        "--rest-url",
        default=os.environ.get("REST_URL", "http://localhost:8080"),
        help="Agent Engine REST URL (default: http://localhost:8080)",
    )
    parser.add_argument(
        "--domain",
        default=os.environ.get("SEED_DOMAIN", "localhost"),
        help="Auth domain (default: localhost)",
    )
    parser.add_argument(
        "--session-cookie",
        default=os.environ.get("AGENT_SESSION_COOKIE", "systemSession"),
        help="Session cookie (default: systemSession)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Perform a dry run without uploading or indexing",
    )
    parser.add_argument(
        "--insecure",
        action="store_true",
        help="Disable SSL certificate verification",
    )
    parser.add_argument(
        "--ca-bundle",
        default=os.environ.get("AGENT_ENGINE_CA_BUNDLE"),
        help="CA certificate file to verify the server with, e.g. mkcert's rootCA.pem",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=4,
        help="Number of images uploaded and indexed concurrently (default: 4)",
    )
    parser.add_argument(
        "--embedders",
        default="siglip-image-embed,arcface-image-embed",
        help="Comma-separated ids of the embedding models whose vectors of each image are indexed "
        "(default: %(default)s)",
    )
    parser.add_argument(
        "--skip-existing",
        action="store_true",
        help="Skip images that are already indexed, so only new ones are uploaded",
    )
    parser.add_argument(
        "--describe",
        action="store_true",
        help="Describe each image to text while indexing (off by default)",
    )

    args = parser.parse_args(argv)

    client = AgentEngineClient(
        base_url=args.rest_url,
        domain=args.domain,
        session_cookie=args.session_cookie,
        insecure=args.insecure,
        ca_bundle=args.ca_bundle,
    )

    with TrainingArchive() as archive:
        counts = ingest(
            archive=archive,
            client=client,
            dry_run=args.dry_run,
            workers=max(1, args.workers),
            describe_media=args.describe,
            embedders=[e.strip() for e in args.embedders.split(",") if e.strip()],
            skip_existing=args.skip_existing,
        )

    print("\n" + "=" * 50)
    print("Ingestion Summary:")
    print(f"  Boards:         {counts['boards']}")
    print(f"  Images indexed: {counts['indexed']}")
    print(f"  Images skipped: {counts['skipped']}")
    print(f"  Images failed:  {counts['failed']}")
    print("=" * 50)


if __name__ == "__main__":
    main()
