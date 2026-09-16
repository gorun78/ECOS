#!/usr/bin/env bash
# check-dangling-changes.sh — ECOS「交付前内容变更审计」（2026-09-16 建立）
#
# ── 为什么需要它 ──────────────────────────────────────────────
# release/v2.1-alpha 曾发生"血缘地图改动全部丢失"事故：
# 完整实现只活在一个被 `git stash drop` 掉的悬空 commit（024fdda）里，
# 从未进入任何分支。当时 `git status` 是干净的、`git log` 也毫无异常，
# 直到用户发现功能缺失才被追溯出来——说明"看起来干净"不足以保证交付完整。
#
# 本脚本把「交付前必须确认：没有改动只活在 stash / 悬空对象里」固化为可重复动作。
#
# ─ 用法 ──────────────────────────────────────────────────────
#   bash ecos_backend/scripts/check-dangling-changes.sh [基线分支，默认 release/v2.1-alpha]
#
# 退出码：0 = 无风险；1 = 存在未落地改动（须人工确认后才能交付）

set -uo pipefail

BASE_BRANCH="${1:-release/v2.1-alpha}"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"

if [ -z "$REPO_ROOT" ]; then
  echo "✗ 不在 git 仓库内，无法审计"
  exit 1
fi

cd "$REPO_ROOT" || exit 1
RISK=0

echo "══════════════════════════════════════════════════════════"
echo " 交付前内容变更审计   base=$BASE_BRANCH"
echo " 仓库: $REPO_ROOT"
echo "══════════════════════════════════════════════════════════"

# ── 检查 1：working tree 未提交变更 ────────────────────────────
# Git提交规范「提交凭证」条：commit hash 即 DONE 凭证，working tree 未提交一律不算交付。
echo
echo "【1/3】working tree 未提交变更"
WT="$(git status --porcelain)"
if [ -n "$WT" ]; then
  echo "   存在未提交变更（不计入交付，需 clean commit 后才可用 git log --grep 溯源）："
  echo "$WT" | sed 's/^/    /'
  RISK=1
else
  echo "  ✓ 干净"
fi

# ── 检查 2：存活的 stash ──────────────────────────────────────
echo
echo "【2/3】存活 stash（改动未落地到任何分支）"
STASHES="$(git stash list)"
if [ -n "$STASHES" ]; then
  echo "  ⚠ 存在未落地的 stash，其内容不属于任何分支："
  echo "$STASHES" | sed 's/^/    /'
  echo "    → 处置：git stash apply 后 clean commit；确认已入库再 git stash drop"
  RISK=1
else
  echo "  ✓ 无 stash"
fi

# ─ 检查 3：悬空对象（被 drop 但未 gc 的历史）──────────────────
# 注意：`git rev-parse "<ref>:<path>"` 对不存在的路径会原样回显参数而不报错，
# 必须用 `--verify --quiet`，否则会把"路径不存在"误判为"路径有内容"。
echo
echo "【3/3】悬空对象中含 base 分支所缺内容的文件"
DANGLING="$(git fsck --dangling --no-progress 2>/dev/null | awk '/dangling commit/ {print $3}')"
if [ -z "$DANGLING" ]; then
  echo "  ✓ 无悬空 commit"
else
  FOUND=0
  for d in $DANGLING; do
    # numstat 第 2 列 = 该 dangling commit 有、而 base 没有的行数
    HITS="$(git diff --numstat "$d" "$BASE_BRANCH" -- . 2>/dev/null | awk '$2+0 > 0 {print "    " $2 " 行独有  " $3}')"
    if [ -n "$HITS" ]; then
      FOUND=1
      echo "  ⚠ dangling $d （$(git log -1 --format=%s "$d" 2>/dev/null)）含 $BASE_BRANCH 所缺内容："
      echo "$HITS" | head -30
      HITS_N="$(echo "$HITS" | wc -l | tr -d ' ')"
      if [ "$HITS_N" -gt 30 ]; then
        echo "    …（共 $HITS_N 项，此处仅列前 30；完整清单：git diff --numstat $d $BASE_BRANCH）"
      fi
    fi
  done
  if [ "$FOUND" -eq 0 ]; then
    echo "  ✓ 悬空 commit 均未含 base 所缺内容"
  else
    echo "    → 处置：确认这些内容是否应交付；应交付则 git checkout <dangling> -- <file> 回放后再 clean commit"
    RISK=1
  fi
fi

echo
echo "══════════════════════════════════════════════════════════"
if [ "$RISK" -eq 0 ]; then
  echo " 结论：无未落地改动，可交付"
else
  echo " 结论：存在未落地改动风险 —— 请先按上方处置建议收口，再交付"
fi
echo "══════════════════════════════════════════════════════════"
exit "$RISK"