#!/bin/bash
# 读取各域 JSON 结果 (runner 写的) + 汇总
echo "=== per-domain json reports ==="
for f in /tmp/wave4_01-sysman.json /tmp/wave4_02-data.json /tmp/wave4_03-onto-search.json /tmp/wave4_04-onto-crud.json /tmp/wave4_05-cognitive.json /tmp/wave4_06-cheng.json /tmp/wave4_07-cross-domain.json; do
  echo "----- $f -----"
  if [ -f "$f" ]; then
    cat "$f" | head -c 4000
    echo ""
  else
    echo "   (missing — checking alternative names)"; ls -la /tmp/wave4_*.json 2>/dev/null | grep -i "$(basename $f | sed 's/\.json//')" 2>/dev/null
  fi
  echo ""
done
