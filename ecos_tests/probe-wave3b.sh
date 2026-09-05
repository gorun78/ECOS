#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
echo "=== 所有 Wave3* 文件 ==="
find engine -name "Wave3*" 2>/dev/null

echo
echo "=== Wave3DemoController 源码 (前 80 行) ==="
find engine/cognitive-engine -name "Wave3DemoController.java" | head -1 | xargs cat 2>/dev/null | head -80

echo
echo "=== 05 T5 报错 = 'reasoningPath 缺失', 看 Wave3Demo.run() 返回结构 ==="
grep -rln 'reasoningPath\|class Wave3' engine/cognitive-engine/ 2>/dev/null | head -10

echo
echo "=== 是否有 Wave3DemoOrchestrator/Wave3Service ==="
find engine/cognitive-engine -name "Wave3*.java" 2>/dev/null

echo
echo "=== 错误体样本: 05 T2 看实际为啥 400 ==="
# 直接 curl 拿响应体
curl -s -X POST http://localhost:8080/api/v1/cognitive/demo/wave3 \
  -H "Content-Type: application/json" \
  -d '{"sourceDocument":"test doc","domain":"finance"}' | head -c 800
echo
echo "DONE"