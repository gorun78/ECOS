#!/bin/bash
# L3 research sandbox isolation verification (2026-11-25)
#
# Runs REAL attack cases against the running sandbox container and asserts
# the container-level defenses actually hold. No mocks.
#
# Usage:  bash verify-isolation.sh
# Prereq: docker compose up -d research-sandbox  (container healthy)
#
# Exit code: 0 = all checks passed, 1 = at least one check failed.

set -u
BASE="${SANDBOX_BASE:-http://127.0.0.1:18099}"
CONTAINER="${SANDBOX_CONTAINER:-ecos-research-sandbox}"
PASS=0
FAIL=0

ok()   { echo "  [PASS] $1"; PASS=$((PASS+1)); }
bad()  { echo "  [FAIL] $1"; FAIL=$((FAIL+1)); }

# run_code <json_payload> -> prints response body
run_code() {
  curl -s -m 60 -X POST "$BASE/research/execute" \
    -H "Content-Type: application/json" -d "$1"
}

echo "=== A. Container is healthy ==="
status=$(docker inspect "$CONTAINER" --format '{{.State.Health.Status}}' 2>/dev/null)
[ "$status" = "healthy" ] && ok "container healthy" || bad "container not healthy (got: $status)"

echo "=== B. Legitimate computation works ==="
resp=$(run_code '{"code":"public class M{public static void main(String[] a){int s=0;for(int i=1;i<=100;i++)s+=i;System.out.println(\"METRIC \"+s);}}"}')
echo "$resp" | grep -q '"measuredMetric":5050' && ok "compute 1..100 = 5050" || bad "compute failed: $(echo "$resp" | head -c 150)"

echo "=== C. Network API rejected by static guard ==="
resp=$(run_code '{"code":"public class N{public static void main(String[] x)throws Exception{new java.net.Socket(\"1.1.1.1\",80);}}"}')
echo "$resp" | grep -q '"code":403' && ok "Socket blocked" || bad "Socket not blocked"

echo "=== D. Process spawn rejected ==="
resp=$(run_code '{"code":"public class P{public static void main(String[] x)throws Exception{Runtime.getRuntime().exec(\"id\");}}"}')
echo "$resp" | grep -q '"code":403' && ok "Runtime.exec blocked" || bad "Runtime.exec not blocked"

echo "=== E. Write to read-only root is denied by container ==="
# 注意：本项验证的是**运行时** --read-only，不是镜像里的 USER。
# 判据用 docker inspect 的 HostConfig.ReadonlyRootfs（K 段重复但角度不同：
# E 看「写入是否真被拒」，K 看「配置位是否真开了」）。
resp=$(run_code '{"code":"import java.nio.file.*;public class W{public static void main(String[] x)throws Exception{try{Files.writeString(Path.of(\"/app/pwn\"),\"x\");System.out.println(\"WROTE_ROOT\");}catch(Exception e){System.out.println(\"BLOCKED\");}}"}')
ro_cfg=$(docker inspect "$CONTAINER" --format '{{.HostConfig.ReadonlyRootfs}}' 2>/dev/null | tr -d '\r')
if echo "$resp" | grep -q 'BLOCKED' && [ "$ro_cfg" = "true" ]; then
  ok "read-only root enforced (write denied AND cfg on)"
elif echo "$resp" | grep -q 'WROTE_ROOT'; then
  bad "root was writable! (ReadonlyRootfs=$ro_cfg)"
else
  bad "write denied but ReadonlyRootfs=$ro_cfg（配置未开，只是路径权限偶然挡住）"
fi

echo "=== F. Outbound network actually unreachable (container level) ==="
out=$(docker exec "$CONTAINER" sh -c "curl -s -m 4 -o /dev/null -w '%{http_code}' http://example.com 2>&1 || true" 2>/dev/null)
case "$out" in
  000*) ok "no outbound network (got $out)" ;;
  *)    bad "outbound reachable? got: $out" ;;
esac

echo "=== G. Infinite loop killed by timeout ==="
resp=$(run_code '{"code":"public class L{public static void main(String[] x){while(true){}}}","timeoutMs":3000}')
echo "$resp" | grep -q '"timedOut":true' && ok "timeout enforced" || bad "timeout not enforced"

echo "=== H. Memory bomb contained by -Xmx ==="
resp=$(run_code '{"code":"public class O{public static void main(String[] x){java.util.List<long[]> l=new java.util.ArrayList<>();while(true)l.add(new long[1000000]);}}","timeoutMs":8000}')
echo "$resp" | grep -q 'OutOfMemoryError' && ok "OOM contained" || bad "OOM not contained"

echo "=== I. Output flood truncated ==="
resp=$(run_code '{"code":"public class F{public static void main(String[] x){for(int i=0;i<200000;i++)System.out.println(\"f\"+i);}}","timeoutMs":8000}')
echo "$resp" | grep -q '"truncated":true' && ok "output truncated" || bad "output not truncated"

echo "=== J. Container survived all attacks ==="
status=$(docker inspect "$CONTAINER" --format '{{.State.Health.Status}}' 2>/dev/null)
[ "$status" = "healthy" ] && ok "still healthy after attacks" || bad "degraded after attacks: $status"

echo "=== K. Non-root / caps / readonly rootfs ==="
uid=$(docker exec "$CONTAINER" sh -c 'id -u' 2>/dev/null | tr -d '\r')
[ "$uid" = "10001" ] && ok "runs as uid 10001" || bad "uid=$uid"
cap=$(docker exec "$CONTAINER" sh -c 'grep CapEff /proc/self/status' 2>/dev/null | tr -d '\r')
echo "$cap" | grep -q '0000000000000000' && ok "caps dropped" || bad "caps present: $cap"
ro=$(docker inspect "$CONTAINER" --format '{{.HostConfig.ReadonlyRootfs}}' 2>/dev/null)
[ "$ro" = "true" ] && ok "rootfs read-only" || bad "rootfs not read-only"

echo "=== L. No docker socket, no host mounts ==="
docker exec "$CONTAINER" sh -c 'test ! -e /var/run/docker.sock' 2>/dev/null \
  && ok "no docker socket" || bad "docker socket present"
mounts=$(docker inspect "$CONTAINER" --format '{{range .Mounts}}{{.Source}} {{end}}' 2>/dev/null | tr -d '\r')
[ -z "$mounts" ] && ok "no host mounts" || bad "host mounts: $mounts"

echo
echo "=============================================="
echo "  PASS=$PASS  FAIL=$FAIL"
echo "=============================================="
[ "$FAIL" -eq 0 ] || exit 1
