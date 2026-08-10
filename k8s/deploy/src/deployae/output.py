"""Small ANSI-colored console output helpers for phase/step banners and status lines."""

from __future__ import annotations

_BLUE = "\033[0;34m"
_CYAN = "\033[0;36m"
_GREEN = "\033[0;32m"
_YELLOW = "\033[0;33m"
_RED = "\033[0;31m"
_BOLD = "\033[1m"
_RESET = "\033[0m"

_PHASE_RULE = "━" * 50
_STEP_RULE = "━" * 48


def phase(message: str) -> None:
    print(f"\n{_BOLD}{_BLUE}{_PHASE_RULE}{_RESET}")
    print(f"{_BOLD}{_BLUE}  {message}{_RESET}")
    print(f"{_BOLD}{_BLUE}{_PHASE_RULE}{_RESET}")


def step(message: str) -> None:
    print(f"{_CYAN}  {_STEP_RULE}{_RESET}")
    print(f"    {_GREEN}→ {message}{_RESET}")
    print(f"{_CYAN}  {_STEP_RULE}{_RESET}")


def info(message: str) -> None:
    print(f"    {_GREEN}✓{_RESET} {message}")


def warn(message: str) -> None:
    print(f"    {_YELLOW}⚠ {message}{_RESET}")


def note(message: str) -> None:
    print(f"    {_YELLOW}ℹ{_RESET} {message}")


def error(message: str) -> None:
    print(f"{_BOLD}{_RED}✗ {message}{_RESET}")
