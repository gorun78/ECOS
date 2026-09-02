#!/usr/bin/env python3
"""Resolve TERMINAL_CWD from one Hermes profile .env without exposing secrets."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path


def parse_env_value(line: str) -> tuple[str, str] | None:
    stripped = line.strip()
    if not stripped or stripped.startswith("#"):
        return None
    if stripped.startswith("export "):
        stripped = stripped[7:].lstrip()
    if "=" not in stripped:
        return None
    key, value = stripped.split("=", 1)
    key = key.strip()
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        value = value[1:-1]
    return key, value


def resolve_terminal_cwd(env_file: Path) -> Path:
    env_file = env_file.expanduser().resolve()
    if not env_file.is_file():
        raise RuntimeError(f"Hermes profile .env 不存在：{env_file}")

    try:
        text = env_file.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as exc:
        raise RuntimeError(f"Hermes profile .env 必须使用 UTF-8：{env_file}") from exc

    terminal_cwd: str | None = None
    for line in text.splitlines():
        parsed = parse_env_value(line)
        if parsed and parsed[0] == "TERMINAL_CWD":
            terminal_cwd = parsed[1]

    if not terminal_cwd:
        raise RuntimeError(f"Hermes profile .env 缺少 TERMINAL_CWD：{env_file}")

    expanded = os.path.expandvars(os.path.expanduser(terminal_cwd))
    target = Path(expanded)
    if not target.is_absolute():
        target = env_file.parent / target
    target = target.resolve()
    if not target.is_dir():
        raise RuntimeError(f"TERMINAL_CWD 不是已存在目录：{target}")
    return target


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="解析 Hermes profile 的 TERMINAL_CWD")
    parser.add_argument("--env-file", type=Path, required=True, help="profile .env 路径")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        print(resolve_terminal_cwd(args.env_file))
    except RuntimeError as exc:
        print(f"错误：{exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
