#!/bin/bash
# 看 Wave3Demo 依赖 + 可用 Bean
cd /home/guorongxiao/ECOS/ecos_backend
echo "=== Wave3Demo 类签名 + 依赖 ==="
grep -nE 'class |@Service|@Component|@Autowired|@Resource|private final|constructor|public Wave3Demo' \
  engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/Wave3Demo.java | head -40

echo
echo "=== Wave3Demo run() 公共方法签名 ==="
grep -nE 'public .*\(' \
  engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/Wave3Demo.java | head -20

echo
echo "=== 是否 @Component/@Service ==="
grep -nE '@Component|@Service' engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/Wave3Demo.java || echo "NOT a spring bean (pure class)"

echo
echo "=== 可用的 DocumentParser 相关 Bean ==="
grep -rn 'class DocumentParserService' engine/kb-engine/ 2>/dev/null
grep -rn '@Component\|@Service' engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/DocumentParserService.java 2>/dev/null | head -3

echo
echo "=== MinerUParser 是否在 kb-engine ==="
find engine/kb-engine -name "MinerU*Parser*.java" -o -name "*MinerU*.java" 2>/dev/null | head -5

echo
echo "=== TextChunker (cognitive) 是否存在 ==="
find engine/cognitive-engine -name "*TextChunker*.java" -o -name "*Chunker*.java" 2>/dev/null | head -5

echo
echo "=== 配置级 mermaid/causal 解析器 ==="
find engine/cognitive-engine -name "*Mermaid*.java" -o -name "*Causal*.java" 2>/dev/null | head -5

echo DONE