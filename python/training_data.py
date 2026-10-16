"""The training data: Pinterest boards kept in an encrypted archive, training.zip.

The archive is read in memory, one image at a time; nothing is ever extracted to disk. It holds
the folders gallery-dl downloads, `pinterest/<user>/<board>/<image>`, where an image may sit in
sections nested below its board.
"""

from __future__ import annotations

import hashlib
import os
import threading
from pathlib import Path, PurePosixPath

import pyzipper

ARCHIVE_PATH = Path(__file__).parent / "training.zip"
PASSWORD = os.environ.get("TRAINING_ZIP_PASSWORD", "justletmein").encode("utf-8")
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif", ".tiff", ".tif"}


def write_archive(source_dir: Path, archive_path: Path = ARCHIVE_PATH) -> int:
    """Writes every file under `source_dir` into an AES-256 encrypted archive at `archive_path`,
    replacing it, and returns the number of files. Images are already compressed, so they are
    stored as they are."""
    temporary_path = archive_path.with_name(archive_path.name + ".part")
    count = 0
    with pyzipper.AESZipFile(
        temporary_path, "w", compression=pyzipper.ZIP_STORED, encryption=pyzipper.WZ_AES
    ) as archive:
        archive.setpassword(PASSWORD)
        for path in sorted(source_dir.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(source_dir).as_posix())
                count += 1
    temporary_path.replace(archive_path)
    return count


class TrainingArchive:
    """Reads the images of the boards in an archive written by `write_archive`."""

    def __init__(self, archive_path: Path = ARCHIVE_PATH):
        if not archive_path.is_file():
            raise FileNotFoundError(f"{archive_path} not found; create it with `run.py download`")
        self._archive = pyzipper.AESZipFile(archive_path)
        self._archive.setpassword(PASSWORD)
        self._lock = threading.Lock()
        images = [
            PurePosixPath(name)
            for name in self._archive.namelist()
            if not name.endswith("/") and PurePosixPath(name).suffix.lower() in IMAGE_EXTENSIONS
        ]
        self._boards_root = _boards_root(images)
        self._board_vs_images: dict[str, list[str]] = {}
        for image in images:
            board = image.relative_to(self._boards_root).parts[0]
            self._board_vs_images.setdefault(board, []).append(image.as_posix())

    def __enter__(self) -> TrainingArchive:
        return self

    def __exit__(self, *_) -> None:
        self._archive.close()

    def board_vs_images(self) -> dict[str, list[str]]:
        """The entries of the images of each board, nested sections included."""
        return self._board_vs_images

    def read(self, entry: str) -> bytes:
        """The content of the image `entry`, decrypted in memory."""
        with self._lock:
            return self._archive.read(entry)

    def knowledge_id(self, entry: str) -> str:
        """A stable id for the image `entry`, from its path below the folder holding the boards."""
        relative_path = PurePosixPath(entry).relative_to(self._boards_root).as_posix()
        return hashlib.sha256(relative_path.encode("utf-8")).hexdigest()


def _boards_root(images: list[PurePosixPath]) -> PurePosixPath:
    """The folder holding the boards: the first below the archive's root, going down through
    folders that hold nothing but one folder, that holds more than one folder or any image."""
    root = PurePosixPath()
    while True:
        below = {image.relative_to(root).parts[0] for image in images}
        directly_in = any(len(image.relative_to(root).parts) == 1 for image in images)
        if directly_in or len(below) != 1:
            return root
        root = root / next(iter(below))
