#!/bin/bash
# soak-72h.sh — Wave-4.2 T-03 主入口
# 用法:
#   bash soak-72h.sh prepare   # 准备: 基线 heap + metrics 起
#   bash soak-72h.sh run       # 真实跑: k6 run 2h 段 (默认 2h 演示)
#   bash soak-72h.sh seg2      # 拆段 2 (累计 2-24h 段)
#   bash soak-72h.sh seg3      # 拆段 3 (最终 24-72h 段)
#   bash soak-72h.sh report    # 出段报告
#   bash soak-72h.sh teardown  # 停: k6 + metrics
set +e
DIR=$(cd "$(dirname "$0")" && pwd)
SOAK_DIR=/home/guorongxiao/ecos-soak
EXECDIR=$DIR
mkdir -p $SOAK_DIR

JPID=$(ps -ef | grep -E 'GatewayApplication' | grep -v grep | awk '{print $2}' | head -n1)
node_bin=/home/guorongxiao/.hermes/node/bin/node

report() {
  local seg=$1
  local out=$2
  local baseline=$3
  local csv=$4
  {
    echo "# 72h Soak Segment Report: $seg"
    echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "GW PID: $JPID ($([[ -n $JPID ]] && echo Running || echo DEAD))"
    echo ""
    echo "## Heap / GC 趋势 (前 3 行 + 后 3 行)"
    echo "```"
    head -n 4 "$csv" 2>/dev/null
    echo "..."
    tail -n 4 "$csv" 2>/dev/null
    echo "```"
    echo ""
    echo "## 与基线对照"
    echo "  基线 (start): $(cat $baseline 2>/dev/null)"
    local last_line=$(tail -n 1 "$csv" 2>/dev/null)
    local seg_net=$(( ${last_line##*,} - ${baseline##*,} ))  # RSS Δ (KB)
    local used_heap=$(echo "$last_line" | awk -F, '{print $2}')
    local total_heap=$(echo "$last_line" | awk -F, '{print $3}')
    echo "  最后 (end): heapUsed=${used_heap}MB heapTotal=${total_heap}MB RSS=$(echo "$last_line" | awk -F, '{print $9}')KB"
    echo ""
    echo "  Net RSS Δ (KB) = $seg_net = $((seg_net / 1024)) MB  (G2 门槛 < 100 MB 即 < 102400 KB)"
    echo "  判定: $([ $seg_net -lt 102400 ] && echo "PASS <100MB" || echo "FAIL >=100MB delta")"
    echo ""
    echo "## 健康 check (health endpoint)"
    curl -s -o /dev/null -w "  health=%{http_code}\n" --max-time 3 http://localhost:8080/actuator/health
  } >> "$out"
}

prepare() {
  echo "[1/4] 检查 GW 健康"
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://localhost:8080/actuator/health 2>/dev/null)
  if [ "$CODE" != "200" ]; then
    echo "  [FATAL] GW 不健康, 退出"
    exit 1
  fi
  echo "  GW OK (200)"

  echo "[2/4] 采 0h 基线 heap (active) to $SOAK_DIR/baseline.csv"
  $DIR/soak-metrics.sh $SOAK_DIR/baseline.csv 1   # 跑 1 次 (300s 后就会写新行, 我们截首行即可)
  pkill -f "soak-metrics.sh.*baseline.csv" 2>/dev/null
  sleep 5
  cp $SOAK_DIR/baseline.csv $SOAK_DIR/baseline-0h.csv 2>/dev/null

  echo "[3/4] 起 metrics sampler (5min) → $SOAK_DIR/metrics-main.csv"
  nohup bash $DIR/soak-metrics.sh $SOAK_DIR/metrics-main.csv 300 > $SOAK_DIR/metrics.log 2>&1 &
  echo "  metrics pid=$!"

  echo "[4/4] k6 准备 (检查)"
  which k6 2>/dev/null || {
    echo "  k6 not found, trying npm i -g ..."
    npm i -g k6-cli 2>/dev/null || true
    k6 --version || { echo "  [WARN] k6 install skip, 用 python3 / uv 另采"; return 0; }
  }
  echo "  k6 ready"
  echo ""
  echo "  all clean, baseline captured at $(date '+%H:%M')"
  echo "  next: bash $0 run (跑 2h 段)"
}

run() {
  echo "[run] k6 (default 2h, 50 VU × 25 UC × 5 roles)"
  export ECOS_BASE=${ECOS_BASE:-http://localhost:8080}
  cd $DIR || exit 9
  K6_DUR=${ECOS_DURATION:-2h}
  K6_VUS=${ECOS_VUS:-50}
  if command -v k6; then
    k6 run -e VUS=$K6_VUS -e DURATION=$K6_DUR k6-72h.js 2>&1 | tee $SOAK_DIR/k6-run.log
  else
    # 本地无 k6: 用 node 简易 fallback 5 角色 × 5 UC 并发 5 min 段
    echo "  [fallback] k6 未装, node fetch 5 min 段"
    SND_SEED=${ECOS_SEED_DURATION:-2h}
    node -e "
      const ROLES = { admin: ['admin','admin123'], tenantA: ['tenant-a','tenant-a-123'], analyst: ['analyst-hz','Analyst@2026'], viewer: ['viewer','viewer1234'], auditor: ['auditor-hz','Audit@2026'] };
      const EPS = [
        ['admin','POST','/api/v1/auth/login',null],
        ['admin','GET','/api/v1/iam/tenants',null],
        ['admin','GET','/api/v1/iam/users?limit=20',null],
        ['admin','GET','/api/v1/iam/roles',null],
        ['admin','GET','/api/v1/iam/permissions?limit=20',null],
        ['admin','GET','/api/v1/sysman/indicators',null],
        ['admin','GET','/api/v1/audit/rules?limit=20',null],
        ['admin','GET','/api/v1/cooperators?limit=20',null],
        ['admin','GET','/api/v1/service-catalog?limit=20',null],
        ['tenantA','POST','/api/v1/auth/login',null],
        ['tenantA','GET','/api/v1/iam/tenants',null],
        ['tenantA','GET','/api/v1/iam/users?limit=20',null],
        ['tenantA','GET','/api/v1/iam/roles',null],
        ['tenantA','GET','/api/v1/data/datasets?limit=20',null],
        ['tenantA','GET','/api/v1/kb/compliance-rules',null],
        ['tenantA','GET','/api/v1/knowledge/graph/nodes?limit=20',null],
        ['analyst','POST','/api/v1/auth/login',null],
        ['analyst','GET','/api/v1/data/pipelines?limit=20',null],
        ['analyst','GET','/api/v1/data/jobs?limit=20',null],
        ['analyst','POST','/api/v1/kb/rag/search',{query:'sales analysis',topK:5}],
        ['analyst','GET','/api/v1/knowledge/graph/nodes/search?query=sales&limit=10',null],
        ['analyst','GET','/api/v1/knowledge/entities?limit=20',null],
        ['viewer','POST','/api/v1/auth/login',null],
        ['viewer','POST','/api/v1/kb/rag/search',{query:'finance',topK:3}],
        ['viewer','GET','/api/v1/kb/spaces?limit=20',null],
        ['viewer','GET','/api/v1/kb/articles?limit=20',null],
        ['auditor','POST','/api/v1/auth/login',null],
        ['auditor','GET','/api/v1/audit/rules?limit=50',null],
        ['auditor','GET','/api/v1/audit/evaluations?limit=20',null],
        ['auditor','GET','/api/v1/audit/events?limit=20',null],
        ['auditor','POST','/api/v1/cognitive/causal/reason',{metric:'sales',domain:'finance',maxDepth:3}],
      ];
      const VUS = parseInt(process.env.ECOS_VUS || '50');
      const DURATION_MIN = process.env.ECOS_DURATION_MIN || 5; // min
      const END = Date.now() + DURATION_MIN * 60 * 1000;
      let pass = 0, fail = 0;
      async function worker(vu) {
        const role = Object.keys(ROLES)[vu % 5];
        let token = '';
        try {
          const lr = await fetch('http://localhost:8080/api/v1/auth/login', {
            method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ username: ROLES[role][0], password: ROLES[role][1] })
          });
          const lj = await lr.json();
          token = (lj.data && (lj.data.accessToken || lj.data.token)) || lj.token || '';
          if (!token) { fail++; console.error('no token role', role); return; }
          pass++;
        } catch (e) { fail++; console.error('login fail', role, e.message); return; }
        const eps = EPS.filter(e => e[0] === role);
        let i = 0;
        while (Date.now() < END) {
          const ep = eps[i++ % eps.length];
          const method = ep[1];
          const path = ep[2];
          const body = ep[3];
          const t0 = performance.now();
          try {
            const res = await fetch('http://localhost:8080' + path, {
              method,
              headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token,
                'X-Tenant-Id': 'tenant-a',
                'X-User-Id': ROLES[role][0],
              },
              body: body ? JSON.stringify(body) : undefined,
            });
            const txt = await res.text();
            const dur = performance.now() - t0;
            if (res.status >= 200 && res.status < 400) pass++; else fail++;
            if (fail < 5) {
              // 记录失败样本
              console.error('[sample FAIL]', method, path, 'status=' + res.status, 'ms=' + dur.toFixed(1));
            }
          } catch (e) {
            fail++;
          }
          await new Promise(r => setTimeout(r, 50));  // 50ms → ~20 req/s per VU × 50 = QPS ~1000
        }
      }
      const t0 = performance.now();
      const p = [];
      for (let vu = 0; vu < VUS; vu++) p.push(worker(vu));
      await Promise.all(p);
      console.log('Soak fallback:', {
        duration_ms: performance.now() - t0,
        pass, fail, total: pass + fail
      });
    " 2>&1 | tee $SOAK_DIR/k6-run.log
  fi
  echo "  seg run done"
}

seg2() { echo "[seg2 placeholder] 2-24h 段 — 连续跑 22h 后再出"; bash "$0" run; }
seg3() { echo "[seg3 placeholder] 24-72h 段 — 连续跑 48h 后再出"; bash "$0" run; }

teardown() {
  pkill -f "soak-metrics.sh" 2>/dev/null
  echo "  metrics stopped"
  echo "  (k6 进程随 shell 退出自动结束)"
}

case "$1" in
  prepare)  prepare ;;
  run)      run ;;
  seg2)     seg2 ;;
  seg3)     seg3 ;;
  report)
    SEG=${2:-0-2h}
    OUT=$3
    bash report $SEG $OUT $SOAK_DIR/baseline-0h.csv $SOAK_DIR/metrics-main.csv
    ;;
  teardown) teardown ;;
  *)
    echo "用法: bash soak-72h.sh [prepare|run|seg2|seg3|report|teardown]"
    exit 1
    ;;
esac
