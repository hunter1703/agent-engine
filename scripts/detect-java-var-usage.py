#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import List

JAVA_FILE = re.compile(r".*\.java$")
SKIP_DIRS = {"build", ".gradle", ".idea", "out", ".worktrees"}

# Detects `var` in common declaration forms:
# - var x = ...;
# - for (var x : items)
# - for (var i = 0; ...)
# - try (var resource = ...)
VAR_RE = re.compile(r"\bvar\b\s+[A-Za-z_$][\w$]*\s*(?:=|:|;|,)" )


def should_skip(path: Path) -> bool:
    parts = set(path.parts)
    return any(part in parts for part in SKIP_DIRS)


def find_java_files(root: Path, main_only: bool) -> List[Path]:
    files: List[Path] = []
    for dirpath, _, filenames in os.walk(root):
        p = Path(dirpath)
        if should_skip(p):
            continue
        for name in filenames:
            if not JAVA_FILE.match(name):
                continue
            full = p / name
            if main_only and f"src{os.sep}main{os.sep}java" not in str(full):
                continue
            files.append(full)
    return files


def strip_inline_comment(line: str) -> str:
    idx = line.find("//")
    return line if idx < 0 else line[:idx]


def detect_file(path: Path) -> List[str]:
    findings: List[str] = []
    with path.open("r", encoding="utf-8") as f:
        for line_no, raw in enumerate(f, start=1):
            line = strip_inline_comment(raw)
            if VAR_RE.search(line):
                findings.append(f"{path}:{line_no}:{raw.strip()}")
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="Detect Java `var` usage.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--main-only", action="store_true", help="Only scan src/main/java")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    files = find_java_files(root, main_only=args.main_only)

    findings: List[str] = []
    for path in files:
        findings.extend(detect_file(path))

    if findings:
        for finding in findings:
            print(finding)
        print(f"\nFound {len(findings)} `var` usage(s).")
        return 1

    print("No Java `var` usage detected.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
