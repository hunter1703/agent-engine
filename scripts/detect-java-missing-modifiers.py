#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List

JAVA_FILE = re.compile(r".*\.java$")

TYPE_DECL_RE = re.compile(
    r"^\s*(?:@[\w$.]+(?:\([^)]*\))?\s*)*"
    r"(?:(public|protected|private)\s+)?"
    r"(?:(?:abstract|final|static|sealed|non-sealed|strictfp)\s+)*"
    r"(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)"
)

FIELD_RE = re.compile(
    r"^\s*(?:@[\w$.]+(?:\([^)]*\))?\s*)*"
    r"(?:(public|protected|private)\s+)?"
    r"(?P<mods>(?:(?:static|final|transient|volatile|synchronized|native|strictfp)\s+)*)"
    r"[A-Za-z_$][\w$<>, ?\[\].@]*\s+"
    r"([A-Za-z_$][\w$]*)\s*(?:=|;)"
)

METHOD_RE = re.compile(
    r"^\s*(?:@[\w$.]+(?:\([^)]*\))?\s*)*"
    r"(?:(public|protected|private)\s+)?"
    r"(?P<mods>(?:(?:static|final|synchronized|native|strictfp|default)\s+)*)"
    r"(?:<[^>]+>\s*)?"
    r"[A-Za-z_$][\w$<>, ?\[\].@]*\s+"
    r"([A-Za-z_$][\w$]*)\s*\([^;{}]*\)\s*(?:throws\s+[^{};]+)?\s*(\{|;)"
)

CTOR_RE_TEMPLATE = (
    r"^\s*(?:@[\w$.]+(?:\([^)]*\))?\s*)*"
    r"(?:(public|protected|private)\s+)?"
    r"(?P<mods>(?:(?:static|final|synchronized|native|strictfp)\s+)*)"
    r"__TYPE_NAME__\s*\([^;{}]*\)\s*(?:throws\s+[^{};]+)?\s*\{"
)

SKIP_PREFIXES = (
    "return ", "throw ", "if ", "for ", "while ", "switch ", "case ", "default:", "new ", "try ", "catch ", "do ",
)


@dataclass
class TypeCtx:
    kind: str
    name: str
    depth: int


def strip_inline_comments(line: str) -> str:
    idx = line.find("//")
    return line if idx < 0 else line[:idx]


def should_skip_path(path: Path) -> bool:
    parts = set(path.parts)
    return any(p in parts for p in ("build", ".gradle", ".idea", "out", ".worktrees"))


def find_java_files(root: Path) -> List[Path]:
    files: List[Path] = []
    for dirpath, dirnames, filenames in os.walk(root):
        p = Path(dirpath)
        if should_skip_path(p):
            continue
        for file_name in filenames:
            if JAVA_FILE.match(file_name):
                files.append(p / file_name)
    return files


def detect_file(path: Path, include_static: bool) -> List[str]:
    findings: List[str] = []
    stack: List[TypeCtx] = []
    depth = 0

    with path.open("r", encoding="utf-8") as f:
        lines = f.readlines()

    for i, raw in enumerate(lines, start=1):
        line = strip_inline_comments(raw).rstrip("\n")
        stripped = line.strip()

        td = TYPE_DECL_RE.match(line)
        if td:
            kind = td.group(2)
            name = td.group(3)
            stack.append(TypeCtx(kind=kind, name=name, depth=depth))

        ctx = stack[-1] if stack else None

        if ctx and depth == ctx.depth + 1 and stripped and not stripped.startswith("@"):
            if ctx.kind != "interface":
                if stripped.startswith("static {"):
                    pass
                elif stripped.startswith("{"):
                    pass
                elif ctx.kind == "enum" and re.match(
                    r"^\s*[A-Z][A-Z0-9_]*(\s*\([^)]*\))?\s*(,\s*[A-Z][A-Z0-9_]*(\s*\([^)]*\))?\s*)*;?\s*$",
                    stripped,
                ):
                    pass
                elif stripped.startswith(tuple(SKIP_PREFIXES)):
                    pass
                else:
                    ctor_re = re.compile(CTOR_RE_TEMPLATE.replace("__TYPE_NAME__", re.escape(ctx.name)))
                    ctor_match = ctor_re.match(line)
                    if ctor_match:
                        access = ctor_match.group(1)
                        mods = (ctor_match.group("mods") or "").strip()
                        if access is None and not (ctx.kind == "enum"):
                            findings.append(f"{path}:{i}:constructor:{raw.strip()}")
                        if access is None and mods:
                            findings.append(f"{path}:{i}:constructor:{raw.strip()}")
                    else:
                        m = METHOD_RE.match(line)
                        if m:
                            access = m.group(1)
                            mods = (m.group("mods") or "").split()
                            if access is None:
                                if include_static or "static" not in mods:
                                    findings.append(f"{path}:{i}:method:{raw.strip()}")
                        else:
                            fmatch = FIELD_RE.match(line)
                            if fmatch:
                                access = fmatch.group(1)
                                mods = (fmatch.group("mods") or "").split()
                                if access is None:
                                    if include_static or "static" not in mods:
                                        findings.append(f"{path}:{i}:field:{raw.strip()}")

        opens = line.count("{")
        closes = line.count("}")
        depth += opens - closes
        while stack and depth <= stack[-1].depth:
            stack.pop()

    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description="Detect package-private members/constructors in Java concrete types.")
    parser.add_argument("--root", default=".", help="Repository root")
    parser.add_argument("--include-static", action="store_true", help="Also flag static fields/methods")
    parser.add_argument("--main-only", action="store_true", help="Only scan src/main/java")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    files = find_java_files(root)
    if args.main_only:
        files = [p for p in files if f"src{os.sep}main{os.sep}java" in str(p)]

    findings: List[str] = []
    for path in files:
        findings.extend(detect_file(path, include_static=args.include_static))

    if findings:
        for finding in findings:
            print(finding)
        print(f"\nFound {len(findings)} missing-modifier issues.")
        return 1

    print("No genuine missing-modifier issues found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
