#!/bin/bash
cd /home/guorongxiao/ECOS/ecos_backend
F=engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CausalReasonerServiceImpl.java
echo "=== L100-L130 ==="
sed -n '100,130p' "$F"
echo
echo "=== 该类用到的 DAO 依赖 (grep final) ==="
grep -nE 'private final|@Autowired|Mapper|Dao|Repository' "$F" | head -20
echo
echo "=== L110 引用的方法/变量 上下文 (L90-L120) ==="
sed -n '90,120p' "$F"
echo "DONE"