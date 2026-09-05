#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
echo "=== CausalDetector 类路径 + traverseKgChain 实现 ==="
F=$(find engine/cognitive-engine -name "CausalDetector*.java" 2>/dev/null | grep -v target | head -1)
echo "$F"
grep -nE 'traverseKgChain|Mapper|Dao|\.query|\.find|\.search|SELECT|sql' "$F" 2>/dev/null | head -30

echo
echo "=== RuleCausalService / RuleMapper.findByDomain SQL ==="
RM=$(find engine -name "ComplianceRuleMapper.java" 2>/dev/null | grep -v target | head -1)
echo "mapper file: $RM"
grep -nE 'findByDomain|findAll|@Select|@Param|SELECT|WHERE' "$RM" 2>/dev/null | head -20

echo
echo "=== 500 堆栈完整 (Caused by 段, L1060-L1160) ==="
sed -n '1060,1160p' /tmp/gw-w3-ctr.log 2>/dev/null | grep -vE 'at org.apache|at java.base|at com.zaxxer|at com.google|at io.opentelemetry' | head -50

echo "DONE"