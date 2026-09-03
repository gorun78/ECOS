#!/bin/bash
# 重启 GW + 跑 V108 + 跑 815 端点回归
export PATH=/home/guorongxiao/.hermes/node/bin:$PATH
exec 1> /tmp/ecos_final_verify.log 2>&1

echo "=== 1. GW 清端口 ==="
JPID=$(lsof -ti:8080 2>/dev/null)
[ -n "$JPID" ] && kill -9 $JPID 2>/dev/null && sleep 3
echo "old GW killed: $JPID"

echo "=== 2. 应用 V108 迁移 (docker psql) ==="
# V108 是幂等 (IF NOT EXISTS), 手动 psql 执行
docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 \
  < /home/guorongxiao/ECOS/ecos_backend/gateway/src/main/resources/db/migration/V108__wave6_t25_missing_tables.sql 2>&1 | tail -n 15
echo "  V108 exit=$?"

echo "=== 3. 重编译 (kb/data/cognitive/buszhi 有改) ==="
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl gateway -am -DskipTests=true -Dmaven.test.skip=true -Djacoco.skip=true -Djacoco.perModuleCheck.skip=true -q 2>&1 | tail -n 20'
echo "  BUILD exit=$?"

echo "=== 4. 启动 GW (后台) ==="
nohup env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  /home/guorongxiao/.local/jdk/jdk-17.0.19+10/bin/java \
  -jar /home/guorongxiao/ECOS/ecos_backend/gateway/target/gateway-*.jar \
  > /tmp/ecos_gw_final.log 2>&1 &
GW_PID=$!
echo "  GW starting pid=$GW_PID"
for i in $(seq 1 40); do
  sleep 3
  HC=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://127.0.0.1:8080/actuator/health 2>/dev/null)
  [ "$HC" = "200" ] && { echo "  GW UP after $((i*3))s (pid=$GW_PID)"; break; }
done
echo "  $curl health=$HC"

echo "=== 5. 跑 815 端点回归 ==="
[ -f /home/guorongxiao/ECOS/ecos_tests/curl_all_regress.sh ] && bash /home/guorongxiao/ECOS/ecos_tests/curl_all_regress.sh 2>&1 | tail -n 30
# 或 .py
[ -f /home/guorongxiao/ECOS/ecos_tests/curl_all_regress.py ] && python3 /home/guorongxiao/ECOS/ecos_tests/curl_all_regress.py 2>&1 | tail -n 30

echo "=== 6. 5xx 计数 ==="
grep -cE "^5[0-9]{2}" /tmp/curl_all.log 2>/dev/null || echo "(no count, 看 detail)"
grep -oE "[0-9]{3}" /tmp/curl_all_detail.tsv 2>/dev/null | sort | uniq -c | sort -rn | head -n 8
echo "=== END ==="
