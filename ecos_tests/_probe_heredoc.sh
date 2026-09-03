#!/usr/bin/env bash
# Test: 在不污染 PATH 下用 head 命令
test_heredoc_cmd() {
  local OUT_DIR="/tmp/x"
  mkdir -p "$OUT_DIR"
  python3 - > "$OUT_DIR/out.tsv" <<'PLEOF'
print("hello")
PLEOF
  local result=$(head -c 10 "$OUT_DIR/out.tsv")
  echo "head_result: $result"
  local wc=$(wc -l "$OUT_DIR/out.tsv")
  echo "wc: $wc"
}
test_heredoc_cmd
echo "---"
test_heredoc_cmd
