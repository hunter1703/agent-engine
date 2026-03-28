#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Iterable, List, Set

JAVA_FILE = re.compile(r".*\.java$")

LAMBDA_SINGLE_RE = re.compile(r"\b([A-Za-z_$][\w$]*)\s*->")
LAMBDA_MULTI_RE = re.compile(r"\(([^)]*)\)\s*->")
LOCAL_VAR_RE = re.compile(
    r"^\s*(?:final\s+)?(?:var|[A-Za-z_$][\w$<>, ?\[\].@]*)\s+([A-Za-z_$][\w$]*)\s*(?==|;|,)"
)
FOR_EACH_RE = re.compile(r"for\s*\([^:;]+\s+([A-Za-z_$][\w$]*)\s*:")
CATCH_RE = re.compile(r"catch\s*\([^)]*\s+([A-Za-z_$][\w$]*)\s*\)")
PATTERN_INSTANCEOF_RE = re.compile(r"instanceof\s+[A-Za-z_$][\w$<>, ?\[\].@]*\s+([A-Za-z_$][\w$]*)")
PATTERN_CASE_RE = re.compile(r"case\s+[A-Za-z_$][\w$.<>?, ]*\s+([A-Za-z_$][\w$]*)\s*->")

DEFAULT_DENYLIST = {
    "cmd", "cfg", "conf", "obj", "val", "tmp", "ctx", "req", "res", "resp", "msg", "evt", "el", "it", "map", "lst"
}
DEFAULT_ALLOWED_SHORT = {
    "i", "j", "k", "x", "y", "z", "id", "ui", "db", "io", "ip", "os", "vm", "url", "uri", "sql", "api", "cpu", "gpu"
}

SKIP_DIRS = {"build", ".gradle", ".idea", "out", ".worktrees"}
DISALLOWED_LOCAL_TYPES = {"return", "throw", "yield"}


def should_skip_path(path: Path) -> bool:
    parts = set(path.parts)
    return any(part in parts for part in SKIP_DIRS)


def find_java_files(root: Path, main_only: bool) -> List[Path]:
    files: List[Path] = []
    for dirpath, _, filenames in os.walk(root):
        p = Path(dirpath)
        if should_skip_path(p):
            continue
        for name in filenames:
            if JAVA_FILE.match(name):
                full = p / name
                if main_only and f"src{os.sep}main{os.sep}java" not in str(full):
                    continue
                files.append(full)
    return files


def split_params(text: str) -> Iterable[str]:
    for raw in text.split(","):
        token = raw.strip()
        if not token:
            continue
        pieces = token.split()
        if pieces:
            yield pieces[-1]


def is_needlessly_abbrev(name: str, denylist: Set[str], allow_short: Set[str]) -> bool:
    if name == "_":
        return False
    lowered = name.lower()
    if lowered in denylist:
        return True
    if len(name) == 1 and lowered not in allow_short:
        return True
    if len(name) == 2 and lowered not in allow_short and lowered[0] == lowered[1]:
        return True
    return False


def strip_comments(line: str) -> str:
    idx = line.find("//")
    return line if idx < 0 else line[:idx]


def detect_file(path: Path, denylist: Set[str], allow_short: Set[str]) -> List[str]:
    findings: List[str] = []
    in_block_comment = False
    with path.open("r", encoding="utf-8") as f:
        for line_no, raw in enumerate(f, start=1):
            raw_line = raw.rstrip("\n")
            stripped_raw = raw_line.strip()
            if in_block_comment:
                if "*/" in raw_line:
                    in_block_comment = False
                continue
            if stripped_raw.startswith("/*") or stripped_raw.startswith("/**"):
                if "*/" not in stripped_raw:
                    in_block_comment = True
                continue
            if stripped_raw.startswith("*"):
                continue

            line = strip_comments(raw)
            stripped = line.strip()
            if not stripped:
                continue
            if re.match(
                r"^(?:public|protected|private)?\s*(?:abstract|final|sealed|non-sealed|static|strictfp\s+)*\s*(class|interface|enum|record)\b",
                stripped,
            ):
                continue
            if re.match(r"^(public|protected|private)?\s*[\w<>, ?\[\].@]+\s+\w+\s*\([^)]*\)\s*\{?$", stripped):
                continue

            for m in LAMBDA_SINGLE_RE.finditer(line):
                name = m.group(1)
                if is_needlessly_abbrev(name, denylist, allow_short):
                    findings.append(f"{path}:{line_no}:lambda-param:{name}:{raw.strip()}")

            for m in LAMBDA_MULTI_RE.finditer(line):
                params = m.group(1)
                if not params.strip() or "(" in params or ")" in params:
                    continue
                for name in split_params(params):
                    if is_needlessly_abbrev(name, denylist, allow_short):
                        findings.append(f"{path}:{line_no}:lambda-param:{name}:{raw.strip()}")

            for regex, kind in (
                (LOCAL_VAR_RE, "local-var"),
                (FOR_EACH_RE, "for-each-var"),
                (CATCH_RE, "catch-var"),
                (PATTERN_INSTANCEOF_RE, "pattern-var"),
                (PATTERN_CASE_RE, "case-pattern-var"),
            ):
                m = regex.search(line)
                if m:
                    name = m.group(1)
                    if kind == "local-var":
                        prefix = line[: m.start(1)].strip().split()
                        if prefix and prefix[-1] in DISALLOWED_LOCAL_TYPES:
                            continue
                    if is_needlessly_abbrev(name, denylist, allow_short):
                        findings.append(f"{path}:{line_no}:{kind}:{name}:{raw.strip()}")
    return findings


def parse_csv_set(value: str) -> Set[str]:
    if not value:
        return set()
    return {item.strip().lower() for item in value.split(",") if item.strip()}


def main() -> int:
    parser = argparse.ArgumentParser(description="Detect needlessly abbreviated Java variable names.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--main-only", action="store_true", help="Only scan src/main/java")
    parser.add_argument("--deny", default="", help="Comma-separated extra banned names")
    parser.add_argument("--allow-short", default="", help="Comma-separated extra allowed short names")
    args = parser.parse_args()

    denylist = set(DEFAULT_DENYLIST)
    denylist.update(parse_csv_set(args.deny))

    allow_short = set(DEFAULT_ALLOWED_SHORT)
    allow_short.update(parse_csv_set(args.allow_short))

    root = Path(args.root).resolve()
    files = find_java_files(root, main_only=args.main_only)

    findings: List[str] = []
    for path in files:
        findings.extend(detect_file(path, denylist, allow_short))

    if findings:
        for finding in findings:
            print(finding)
        print(f"\nFound {len(findings)} abbreviated-name issues.")
        return 1

    print("No abbreviated variable names detected.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
