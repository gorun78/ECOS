#!/usr/bin/env bash
# 构建 ecos-cli 并把 git sha 注入清单（--version 显示 "<版本> (<sha>)"）。
#
# 用法：bash tools/ecos-cli/dist/build.sh [额外 mvn 参数...]
# 说明：pom 里 build.id 默认 local（零插件、离线可构建）；本脚本只是把 sha 显式传进去，
#       CI 走同口径（-Dbuild.id=<sha>），保证「--version 含 sha」在本地与流水线一致。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SHA="$(git -C "$REPO_ROOT" rev-parse --short=8 HEAD 2>/dev/null || echo local)"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null | head -1)" ]]; then
  SHA="${SHA}-dirty"
fi

MVN="${MVN:-mvn}"
echo "[ecos-cli] build.id=$SHA"
exec "$MVN" -f "$REPO_ROOT/tools/ecos-cli/pom.xml" package -DskipTests -Dbuild.id="$SHA" "$@"
