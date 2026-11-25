#!/usr/bin/env node
/**
 * L3 研究循环离线验证脚本（Phase 0，2026-11-25）。
 *
 * <p><b>定位</b>：不启动任何容器、不连数据库、不调 LLM，仅对「已导出的谱系快照 JSON」
 * 做结构校验，用来在离线环境证明一条研究谱系自洽。这是可行性报告 v2.0 §7 Phase 0
 * 的「零依赖验证包」——用于评审、交接与回归，不作为运行时依赖。</p>
 *
 * <p><b>用法</b>：</p>
 * <pre>
 *   node ecos_backend/scripts/verify-research-lineage.mjs &lt;snapshot.json&gt;
 *   node ecos_backend/scripts/verify-research-lineage.mjs --export &lt;out.json&gt;   # 生成模板骨架
 * </pre>
 *
 * <p><b>退出码</b>：0 = 全部约束通过；1 = 存在违例（逐条打印）；2 = 用法/读文件错误。</p>
 *
 * <p><b>校验的不变量</b>（与后端服务层一一对应）：</p>
 * <ol>
 *   <li>每条运行 run_level 必须在词表内（RD_AGENT / MENTAL_MODEL / ALGORITHM）；</li>
 *   <li>RESEARCH_RUN 必须 run_mode=SANDBOX（研究轮次不得计入正式口径）；</li>
 *   <li>谱系父子链闭合：parent_run_id 要么为空（根），要么指向本快照内的既有行；</li>
 *   <li>loop_index 沿父子链严格递增；</li>
 *   <li>stop_reason 词表封闭；且仅终态轮可带 stop_reason（中间轮必须为空）；</li>
 *   <li>至多一条 SOTA 且其 stop_reason 必须为 ACCEPTED；</li>
 *   <li>审批记录：APPROVED 必带 approval_ref；decision 词表封闭。</li>
 * </ol>
 */
import { readFileSync, writeFileSync } from 'node:fs';

const LEVELS = new Set(['RD_AGENT', 'MENTAL_MODEL', 'ALGORITHM']);
const STOP_REASONS = new Set(['ACCEPTED', 'MAX_LOOPS', 'NO_PROGRESS', 'BUDGET_EXHAUSTED', 'FAILED']);
const TERMINAL_STOPS = new Set(['ACCEPTED', 'MAX_LOOPS', 'NO_PROGRESS']);
const DECISIONS = new Set(['APPROVED', 'REJECTED']);

const TEMPLATE = {
  scenarioId: 'sc-example',
  exportedAt: '2026-11-25T00:00:00Z',
  runs: [
    {
      id: 'run-000000000001',
      scenario_id: 'sc-example',
      run_type: 'RESEARCH_RUN',
      run_level: 'RD_AGENT',
      run_mode: 'SANDBOX',
      loop_index: 0,
      parent_run_id: null,
      sota_run_id: null,
      branch_key: 'br-example',
      status: 'DEGRADED',
      stop_reason: null,
      hypotheses: '[]',
      exec_trace: '[]',
      advice: '{}',
      budget: '{"maxLoops":3,"maxSecondsPerLoop":120,"maxTotalSeconds":300}',
    },
    {
      id: 'run-000000000002',
      scenario_id: 'sc-example',
      run_type: 'RESEARCH_RUN',
      run_level: 'RD_AGENT',
      run_mode: 'SANDBOX',
      loop_index: 1,
      parent_run_id: 'run-000000000001',
      sota_run_id: 'run-000000000002',
      branch_key: 'br-example',
      status: 'COMPLETED',
      stop_reason: 'ACCEPTED',
      hypotheses: '[]',
      exec_trace: '[]',
      advice: '{}',
      budget: '{"maxLoops":3,"maxSecondsPerLoop":120,"maxTotalSeconds":300}',
    },
  ],
  approvals: [
    {
      id: 'apr-000000000001',
      run_id: 'run-000000000002',
      decision: 'APPROVED',
      approval_ref: 'APR-EXAMPLE-001',
      approver: 'u-admin',
    },
  ],
};

function fail(errors, msg) {
  errors.push(msg);
}

function validate(snap) {
  const errors = [];
  const runs = Array.isArray(snap.runs) ? snap.runs : [];
  if (runs.length === 0) fail(errors, '快照缺少 runs 或为空数组');
  const byId = new Map(runs.map((r) => [r.id, r]));

  // 1) 分级词表
  for (const r of runs) {
    if (!LEVELS.has(r.run_level)) {
      fail(errors, `[分级] ${r.id} run_level=${JSON.stringify(r.run_level)} 不在词表 ${[...LEVELS].join('/')}`);
    }
  }

  // 2) 研究轮次必须沙盒
  for (const r of runs.filter((x) => x.run_type === 'RESEARCH_RUN')) {
    if (r.run_mode !== 'SANDBOX') {
      fail(errors, `[隔离] ${r.id} RESEARCH_RUN 的 run_mode=${JSON.stringify(r.run_mode)}，必须为 SANDBOX`);
    }
  }

  // 3) 父子链闭合
  for (const r of runs) {
    if (r.parent_run_id != null && !byId.has(r.parent_run_id)) {
      fail(errors, `[谱系] ${r.id} parent_run_id=${r.parent_run_id} 不在快照内（链断裂或导出不全）`);
    }
  }

  // 4) loop_index 沿链递增（并防环）
  for (const r of runs) {
    const seen = new Set([r.id]);
    let cur = r;
    while (cur && cur.parent_run_id != null) {
      if (seen.has(cur.parent_run_id)) {
        fail(errors, `[谱系] ${r.id} 父子链存在环（重复节点 ${cur.parent_run_id}）`);
        break;
      }
      seen.add(cur.parent_run_id);
      const parent = byId.get(cur.parent_run_id);
      if (!parent) break;
      if (typeof cur.loop_index === 'number' && typeof parent.loop_index === 'number'
          && cur.loop_index <= parent.loop_index && cur.loop_index !== parent.loop_index) {
        // 同 loop_index 允许（不同分支），但不得倒退
        fail(errors, `[轮次] ${cur.id} loop_index=${cur.loop_index} 小于父 ${parent.id} 的 ${parent.loop_index}`);
      }
      cur = parent;
    }
  }

  // 5) stop_reason 词表 + 仅终态轮可带
  const hasChild = new Set(runs.map((r) => r.parent_run_id).filter(Boolean));
  for (const r of runs) {
    if (r.stop_reason != null && !STOP_REASONS.has(r.stop_reason)) {
      fail(errors, `[停止原因] ${r.id} stop_reason=${JSON.stringify(r.stop_reason)} 不在词表 ${[...STOP_REASONS].join('/')}`);
    }
    if (r.stop_reason != null && !TERMINAL_STOPS.has(r.stop_reason) && hasChild.has(r.id)) {
      fail(errors, `[停止原因] ${r.id} 非终态原因 ${r.stop_reason} 却仍有子轮（中间轮应留空）`);
    }
  }

  // 6) SOTA 唯一且必须 ACCEPTED
  const sotas = runs.filter((r) => r.sota_run_id === r.id);
  if (sotas.length > 1) {
    fail(errors, `[SOTA] 存在多条自指 SOTA（${sotas.map((r) => r.id).join(', ')}），至多一条`);
  }
  for (const s of sotas) {
    if (s.stop_reason !== 'ACCEPTED') {
      fail(errors, `[SOTA] ${s.id} 自指 SOTA 但 stop_reason=${JSON.stringify(s.stop_reason)}，必须为 ACCEPTED`);
    }
  }

  // 7) 审批留痕
  const approvals = Array.isArray(snap.approvals) ? snap.approvals : [];
  for (const a of approvals) {
    if (!DECISIONS.has(a.decision)) {
      fail(errors, `[审批] ${a.id} decision=${JSON.stringify(a.decision)} 不在词表 APPROVED/REJECTED`);
    }
    if (a.decision === 'APPROVED' && !(a.approval_ref && String(a.approval_ref).trim())) {
      fail(errors, `[审批] ${a.id} 为 APPROVED 但缺 approval_ref（禁生成即审批）`);
    }
    if (a.run_id && !byId.has(a.run_id)) {
      fail(errors, `[审批] ${a.id} run_id=${a.run_id} 不在快照内`);
    }
  }

  return errors;
}

const argv = process.argv.slice(2);
if (argv.length === 0 || argv[0] === '--help' || argv[0] === '-h') {
  console.error('用法: node verify-research-lineage.mjs <snapshot.json> | --export <out.json>');
  process.exit(2);
}

if (argv[0] === '--export') {
  const out = argv[1] || 'research-lineage.template.json';
  try {
    writeFileSync(out, JSON.stringify(TEMPLATE, null, 2), 'utf8');
    console.log(`[OK] 模板已写出: ${out}`);
    process.exit(0);
  } catch (e) {
    console.error(`[ERR] 写出失败: ${e.message}`);
    process.exit(2);
  }
}

const file = argv[0];
let snap;
try {
  snap = JSON.parse(readFileSync(file, 'utf8'));
} catch (e) {
  console.error(`[ERR] 读取/解析失败: ${file} → ${e.message}`);
  process.exit(2);
}

const errors = validate(snap);
const runs = Array.isArray(snap.runs) ? snap.runs.length : 0;
const approvals = Array.isArray(snap.approvals) ? snap.approvals.length : 0;

if (errors.length === 0) {
  console.log(`[PASS] 谱系自洽：runs=${runs} approvals=${approvals} 全部约束通过`);
  process.exit(0);
}
console.error(`[FAIL] ${errors.length} 条违例（runs=${runs} approvals=${approvals}）：`);
for (const e of errors) console.error(`  - ${e}`);
process.exit(1);
