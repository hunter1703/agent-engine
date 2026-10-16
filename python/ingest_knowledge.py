#!/usr/bin/env python3
"""Batch Knowledge Ingestion Script for Agent Engine.

Walks a directory tree, finds directories with an `info.json` file, and indexes
all direct image files in that folder with the `tags` defined in `info.json`.
With `--folder-as-tag`, a directory with images and no `info.json` is indexed with
its own name as the tag. With `--tag-level N` it is indexed with the name of its ancestor N
levels below the given folder instead (1 is the folders directly under it), so every image
nested beneath that folder shares its tag. With `--skip-existing`, images already indexed are
left alone.
Images are uploaded and indexed concurrently, `--workers` at a time, and are not
described to text unless `--describe` is given.
Uses standard library only (no external pip dependencies needed).

Usage:
    python ingest_knowledge.py /path/to/dataset
    python ingest_knowledge.py /path/to/dataset --rest-url http://localhost:8080 --workers 8
    python ingest_knowledge.py ~/pinterest-boards --folder-as-tag --skip-existing
    python ingest_knowledge.py ~/pinterest-boards/pinterest/me --tag-level 1 --skip-existing
    python ingest_knowledge.py /path/to/dataset --ca-bundle "$(mkcert -CAROOT)/rootCA.pem"
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import mimetypes
import os
import ssl
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif", ".tiff", ".tif"}


def is_image_file(path: Path) -> bool:
    """Check if the given file has an image extension."""
    return path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS


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

    def upload_file(self, file_path: Path) -> Dict[str, Any]:
        """Upload an image to /v1/storage/upload."""
        mime_type, _ = mimetypes.guess_type(str(file_path))
        if not mime_type:
            mime_type = "image/jpeg"

        file_size = file_path.stat().st_size
        filename = file_path.name
        encoded_name = urllib.request.quote(filename)

        url = f"{self.base_url}/v1/storage/upload?name={encoded_name}"

        with open(file_path, "rb") as f:
            data = f.read()

        req = urllib.request.Request(
            url,
            data=data,
            headers={
                "Content-Type": mime_type,
                "Content-Length": str(file_size),
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


def knowledge_id_of(image: Path, root_dir: Path) -> str:
    """A stable id for `image`, so that indexing it again replaces its knowledge."""
    relative_path = image.relative_to(root_dir).as_posix()
    return hashlib.sha256(relative_path.encode("utf-8")).hexdigest()


def index_image(
    client: AgentEngineClient,
    image: Path,
    knowledge_id: str,
    tags: List[str],
    describe_media: bool,
    embedders: List[str],
    skip_existing: bool = False,
) -> bool:
    """Uploads one image and indexes it as the knowledge `knowledge_id`, carrying `tags`.

    Returns False, doing nothing, when `skip_existing` and it is already indexed."""
    if skip_existing and client.is_indexed(knowledge_id):
        return False
    file_details = client.upload_file(image)
    client.create_knowledge(
        file_details=file_details,
        title=f"{image.stem} - {tags[0]}",
        tags=tags,
        knowledge_id=knowledge_id,
        wait_for_completion=True,
        describe_media=describe_media,
        embedders=embedders,
    )
    return True


def process_directory(
    root_dir: Path,
    client: AgentEngineClient,
    dry_run: bool = False,
    workers: int = 4,
    describe_media: bool = False,
    embedders: Optional[List[str]] = None,
    folder_as_tag: bool = False,
    skip_existing: bool = False,
    tag_level: int = 0,
) -> Tuple[int, int, int, int]:
    """Recursively walk root_dir and index images where info.json is present.

    Returns:
        (total_dirs, info_dirs, indexed_images, skipped_images)
    """
    total_dirs = 0
    info_dirs = 0
    indexed_images = 0
    skipped_images = 0
    pool = ThreadPoolExecutor(max_workers=workers)
    futures = {}

    print(f"\nScanning directory: {root_dir.resolve()}\n")

    for current_dir_str, _, filenames in os.walk(root_dir):
        total_dirs += 1
        current_dir = Path(current_dir_str)
        info_file = current_dir / "info.json"

        # Check for images directly in this directory
        image_files = [
            current_dir / f for f in filenames if is_image_file(current_dir / f)
        ]

        tags: List[str] = []
        if info_file.exists():
            try:
                with open(info_file, "r", encoding="utf-8") as f:
                    info_data = json.load(f)
            except Exception as e:
                print(f"[ERROR] Could not parse {info_file}: {e}")
                continue

            raw_tags = info_data.get("tags")
            if isinstance(raw_tags, list):
                tags = [str(t).strip() for t in raw_tags if str(t).strip()]
            elif isinstance(raw_tags, str) and raw_tags.strip():
                tags = [raw_tags.strip()]
            elif "tag" in info_data and str(info_data["tag"]).strip():
                tags = [str(info_data["tag"]).strip()]
            elif "text" in info_data and str(info_data["text"]).strip():
                tags = [str(info_data["text"]).strip()]
            elif "description" in info_data and str(info_data["description"]).strip():
                tags = [str(info_data["description"]).strip()]

            if not tags:
                print(f"[WARN] {info_file} has no tags. Using folder name.")
                tags = [current_dir.name]
        elif (folder_as_tag or tag_level) and image_files:
            folders = current_dir.relative_to(root_dir).parts
            if tag_level and len(folders) < tag_level:
                skipped_images += len(image_files)
                print(f"[SKIP] {len(image_files)} image(s) above tag level {tag_level}")
                continue
            tags = [folders[tag_level - 1] if tag_level else current_dir.name]
        else:
            if image_files:
                skipped_images += len(image_files)
                rel_path = current_dir.relative_to(root_dir)
                display_path = "." if str(rel_path) == "." else str(rel_path)
                print(
                    f"[SKIP] {display_path}: "
                    f"{len(image_files)} image(s) skipped (no info.json)"
                )
            continue

        info_dirs += 1
        rel_path = current_dir.relative_to(root_dir)
        display_path = "." if str(rel_path) == "." else str(rel_path)
        print(
            f"\n📁 Indexing folder: {display_path}"
            f"\n   Tags: {tags}"
            f"\n   Images to index: {len(image_files)}"
        )

        if dry_run:
            for img in image_files:
                print(f"   [DRY-RUN] Would index: {img.name}")
            indexed_images += len(image_files)
            continue

        for img in image_files:
            futures[
                pool.submit(
                    index_image,
                    client,
                    img,
                    knowledge_id_of(img, root_dir),
                    tags,
                    describe_media,
                    embedders or [],
                    skip_existing,
                )
            ] = img

    # One pool serves every directory, so folders with few images do not limit the concurrency.
    for future in as_completed(futures):
        img = futures[future]
        try:
            if future.result():
                print(f"   ✓ {img}", flush=True)
                indexed_images += 1
            else:
                print(f"   = {img} (already indexed)", flush=True)
                skipped_images += 1
        except Exception as e:
            print(f"   ✗ {img}: {e}", flush=True)
    pool.shutdown()

    return total_dirs, info_dirs, indexed_images, skipped_images


def main():
    parser = argparse.ArgumentParser(
        description="Recursively walk a folder and index images containing info.json into Agent Engine."
    )
    parser.add_argument(
        "folder",
        type=str,
        help="Root folder to walk through",
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
        "--folder-as-tag",
        action="store_true",
        help="Index a folder of images that has no info.json with the folder's name as its tag",
    )
    parser.add_argument(
        "--tag-level",
        type=int,
        default=0,
        help="Index the images of a folder that has no info.json with the name of its ancestor this "
        "many levels below the given folder (1 = the folders directly under it); implies "
        "--folder-as-tag",
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

    args = parser.parse_args()

    root_dir = Path(args.folder)
    if not root_dir.exists() or not root_dir.is_dir():
        print(f"Error: Directory '{args.folder}' does not exist or is not a directory.")
        sys.exit(1)

    client = AgentEngineClient(
        base_url=args.rest_url,
        domain=args.domain,
        session_cookie=args.session_cookie,
        insecure=args.insecure,
        ca_bundle=args.ca_bundle,
    )

    total_dirs, info_dirs, indexed, skipped = process_directory(
        root_dir=root_dir,
        client=client,
        dry_run=args.dry_run,
        workers=max(1, args.workers),
        describe_media=args.describe,
        embedders=[e.strip() for e in args.embedders.split(",") if e.strip()],
        folder_as_tag=args.folder_as_tag,
        skip_existing=args.skip_existing,
        tag_level=args.tag_level,
    )

    print("\n" + "=" * 50)
    print("Ingestion Summary:")
    print(f"  Total directories checked: {total_dirs}")
    print(f"  Directories with info.json: {info_dirs}")
    print(f"  Images indexed:            {indexed}")
    print(f"  Images skipped:            {skipped}")
    print("=" * 50)


if __name__ == "__main__":
    main()
