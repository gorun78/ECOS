#!/usr/bin/env bash
# ECOS CLI 安装脚本（Linux / macOS / Git Bash）
#
# 用法：
#   bash tools/ecos-cli/dist/install.sh
#   bash tools/ecos-cli/dist/install.sh --jar ./ecos-cli.jar --home "$HOME/.ecos-cli"
#
# 行为：
#   1) 校验 JDK ≥ 17
#   2) 复制 fat jar 到 ~/.ecos-cli/lib/
#   3) 生成 ~/.ecos-cli/bin/ecos 包装器
#   4) 提示把 bin 目录加入 PATH（**不自动改 PATH**）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
JAR=""
HOME_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar) JAR="${2:-}"; shift 2 ;;
    --home) HOME_DIR="${2:-}"; shift 2 ;;
    -h|--help) sed -n '1,20p' "$0"; exit 0 ;;
    *) echo "[ecos] 未知参数: $1" >&2; exit 64 ;;
  esac
done

[[ -z "$JAR" ]] && JAR="$REPO_ROOT/tools/ecos-cli/target/ecos-cli.jar"
[[ -z "$HOME_DIR" ]] && HOME_DIR="$HOME/.ecos-cli"

if [[ ! -f "$JAR" ]]; then
  echo "[ecos] 找不到 CLI jar：$JAR" >&2
  echo "[ecos] 先构建：mvn -o -f tools/ecos-cli/pom.xml package" >&2
  exit 1
fi

resolve_java() {
  local candidates=()
  [[ -n "${ECOS_JAVA:-}" ]] && candidates+=("$ECOS_JAVA")
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME/bin/java")
  if [[ -d "$HOME/env" ]]; then
    while IFS= read -r d; do candidates+=("$d/bin/java"); done < <(find "$HOME/env" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null)
  fi
  candidates+=("$(command -v java || true)")
  for c in "${candidates[@]}"; do
    [[ -z "$c" ]] && continue
    [[ "$c" != "java" && ! -x "$c" ]] && continue
    if "$c" -version 2>&1 | head -1 | grep -qE 'version "(1[7-9]|[2-9][0-9])'; then
      echo "$c"; return 0
    fi
  done
  return 1
}

JAVA_BIN="$(resolve_java)" || {
  echo "[ecos] 未找到 JDK 17+。请设置 ECOS_JAVA 或 JAVA_HOME 后重试。" >&2
  exit 1
}
echo "[1/4] JDK 检测通过：$JAVA_BIN"

mkdir -p "$HOME_DIR/lib" "$HOME_DIR/bin"
TARGET="$HOME_DIR/lib/ecos-cli.jar"
cp -f "$JAR" "$TARGET"
echo "[2/4] 已安装 jar：$TARGET"

WRAPPER="$HOME_DIR/bin/ecos"
cat > "$WRAPPER" <<EOF
#!/usr/bin/env bash
set -euo pipefail
JAR="$TARGET"
JAVA_BIN="$JAVA_BIN"
if [[ ! -f "\$JAR" ]]; then
  echo "[ecos] 找不到 \$JAR，请重新运行 install.sh" >&2
  exit 1
fi
exec "\$JAVA_BIN" -Dfile.encoding=UTF-8 -jar "\$JAR" "\$@"
EOF
chmod +x "$WRAPPER"
echo "[3/4] 已生成包装器：$WRAPPER"

echo "[4/4] 完成。请手动把以下目录加入 PATH（本脚本不会自动改 PATH）："
echo "      $HOME_DIR/bin"
echo ""
echo "验证："
echo "      ecos --version"
echo "      ecos doctor --base-url http://localhost:8080"
