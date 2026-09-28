#!/usr/bin/env python3
"""自动提交辅助脚本：分支白名单校验 + 变更范围收集 + commit message 骨架生成。

对齐 .hermes/rules/Git提交规范.md。
退出码约定：0=成功；2=主干分支拒绝直推；3=存在敏感/打包产物需人工剔除。
"""
import argparse
import json
import subprocess
import sys

ALLOWED_BRANCH_PREFIXES = ("feature/", "user/", "bugfix/", "hotfix/")
PROTECTED_BRANCHES = {"main", "master", "develop", "test"}
SENSITIVE_PATTERNS = (".env", ".key", "credentials", ".pem", "target/", "node_modules/", "dist/")


def run(cmd, cwd=None):
    r = subprocess.run(cmd, shell=True, cwd=cwd, capture_output=True, text=True)
    return r.returncode, r.stdout.strip(), r.stderr.strip()


def current_branch(repo):
    rc, out, _ = run("git rev-parse --abbrev-ref HEAD", cwd=repo)
    return out if rc == 0 else None


def check_branch(branch):
    """返回 (allowed, reason)。主干/未知分支拒绝。"""
    if not branch:
        return False, "无法解析当前分支"
    if branch in PROTECTED_BRANCHES:
        return False, f"分支 {branch} 为主干，禁止直推，必须走 MR/PR"
    if not branch.startswith(ALLOWED_BRANCH_PREFIXES):
        return False, f"分支 {branch} 不在白名单（{ALLOWED_BRANCH_PREFIXES}）"
    return True, "ok"


def collect_changes(repo):
    _, status, _ = run("git status --short", cwd=repo)
    _, stat, _ = run("git diff --stat", cwd=repo)
    lines = [l for l in status.splitlines() if l.strip()]
    return lines, stat


def detect_sensitive(changed_lines):
    hits = []
    for line in changed_lines:
        # git status --short 输出格式：`XY path`，路径在第 4 个字符起
        path = line[3:].strip()
        for pat in SENSITIVE_PATTERNS:
            if pat in path:
                hits.append((path, pat))
    return hits


def build_message(type_, scope, summary):
    if not summary:
        summary = "<动宾结构描述>"
    return f"{type_}({scope}): {summary}"


def main():
    p = argparse.ArgumentParser(description="自动提交辅助")
    p.add_argument("--repo", default=".", help="git 仓库目录")
    p.add_argument("--check-branch", action="store_true", help="仅校验分支白名单")
    p.add_argument("--type", default="feat",
                   choices=["feat", "fix", "refactor", "perf", "style", "docs", "test", "chore"])
    p.add_argument("--scope", default="模块", help="提交模块")
    p.add_argument("--summary", default="", help="动宾结构标题")
    args = p.parse_args()

    repo = args.repo.rstrip("/")
    branch = current_branch(repo)

    if args.check_branch:
        allowed, reason = check_branch(branch)
        print(json.dumps({"branch": branch, "allowed": allowed, "reason": reason}, ensure_ascii=False))
        sys.exit(0 if allowed else 2)

    allowed, reason = check_branch(branch)
    if not allowed:
        print(json.dumps({"branch": branch, "allowed": False, "reason": reason}, ensure_ascii=False))
        sys.exit(2)

    changed_lines, diff_stat = collect_changes(repo)
    sensitive = detect_sensitive(changed_lines)

    if sensitive:
        print(json.dumps(
            {"branch": branch, "allowed": True, "sensitive_files": sensitive},
            ensure_ascii=False,
        ))
        sys.exit(3)

    message = build_message(args.type, args.scope, args.summary)
    print(json.dumps(
        {
            "branch": branch,
            "allowed": True,
            "changed_files": changed_lines,
            "diff_stat": diff_stat,
            "commit_message": message,
        },
        ensure_ascii=False,
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    sys.exit(main())
