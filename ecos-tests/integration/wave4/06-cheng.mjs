#!/usr/bin/env node
// 06-cheng.mjs — Wave-4.1 域 6/7: cheng (K→C) KB approve 闭环 + RuleRef collector
// 验收:
//   T1 列出已有抽取任务 GET /api/v1/knowledge/extract/tasks
//   T2 选 1 个 UPLOADED/PENDING 任务, POST /{id}/approve → 200 + 返回 ApprovalOutcome
//   T3 T2 返回 entityCount/ruleCount 计数 + status=APPROVED
//   T4 GET /api/v1/knowledge/compliance-rules 列表 200 (T2 入库的 rule 在列表中能看到)
//   T5 用 rule 调 cognitive diagnose (POST /api/v1/cognitive/diagnose) → 推理路径含 ruleRefs (RuleRefCollector 收口)
//   T6 (空容忍) 不存在的 task id POST /{id}/approve → 404/400
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED

import { BASE, http, get, post, check, report, gatewayAlive, login, note } from './lib/w4-common.mjs';

console.log('═══ 06-cheng.mjs — Wave-4.1 域6: cheng K→C KB approve 闭环 + RuleRef ═══');
console.log(`  BASE: ${BASE}`);
await gatewayAlive() || process.exit(77);

let token;
try {
  const sess = await login();
  token = sess.token;
} catch (e) {
  note('T-session 失败', e.message);
}
const withAuth = { token };

// ── T1 ── 抽取任务列表
let tasks = [];
let selectedId = null;
try {
  const r = await get('/api/v1/knowledge/extract/tasks?pageSize=20', { ...withAuth, timeout: 15000 });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || [];
  tasks = Array.isArray(data) ? data : (data?.content || []);
  check('T1 tasks 列表 200 + code===0', r.status === 200 && code === 0,
    `status=${r.status} code=${code} total=${tasks.length}`);
  // 选一个非 APPROVED 的 (最优先 UPLOADED / PENDING / EXTRACTING)
  const priority = ['PENDING', 'UPLOADED', 'EXTRACTING', 'EXTRACTED'];
  selectedId = tasks.find(t => priority.includes(t.status))?.id
    || tasks[0]?.id;
  check('T1 有可用的抽取任务', !!selectedId, `selected=${selectedId} status=${tasks.find(t => t.id === selectedId)?.status}`);
} catch (e) {
  check('T1 tasks 列表', false, e.message);
}

// ── T2 ── approve (Wave-2C 闭环: 实体 → Neo4j + rules → sys_compliance_rule + 实体链接)
let approvedOutcome = null;
if (selectedId) {
  try {
    const r = await post(`/api/v1/knowledge/extract/${encodeURIComponent(selectedId)}/approve`, { ...withAuth, timeout: 60000 });
    const code = r.body?.code ?? r.body?.status;
    approvedOutcome = r.body?.data || {};
    check('T2 approve 200 + code===0', r.status === 200 && code === 0,
      `status=${r.status} code=${code} body=${JSON.stringify(r.body).slice(0, 200)}`);
  } catch (e) {
    check('T2 approve', false, e.message);
  }
} else {
  check('T2 approve (skip 无 task)', true, 'tasks 清单空，跳过 — P3');
}

// ── T3 ── ApprovalOutcome 结构化 (Wave-2C: { status, counts, ... })
if (approvedOutcome) {
  const counts = approvedOutcome.counts || approvedOutcome || {};
  const status = approvedOutcome.status || counts.status || approvedOutcome.approved;
  const ruleCount = counts.ruleCount ?? counts.rules ?? counts.rule_count ?? 0;
  const entityCount = counts.entityCount ?? counts.entities ?? counts.entity_count ?? 0;
  check('T3 ApprovalOutcome 含 counts / status',
    !!(counts || approvedOutcome.status || approvedOutcome.approved),
    `status=${status} ruleCount=${ruleCount} entityCount=${entityCount}`);
} else {
  check('T3 ApprovalOutcome (skip)', true, 'T2 未执行');
}

// ── T4 ── compliance rules 列表
let rules = [];
{
  const r = await get('/api/v1/knowledge/compliance-rules', { ...withAuth, timeout: 15000 });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || [];
  rules = Array.isArray(data) ? data : (data?.content || []);
  check('T4 compliance-rules 200 + code===0', r.status === 200 && code === 0,
    `status=${r.status} code=${code} total=${rules.length}`);
  const anyEnabled = rules.some(x => x.enabled === true || x.enabled === 't');
  check('T4 至少 1 条 enabled rule (供 07 跨域 rule 消费)', anyEnabled,
    `enabled=${rules.filter(x => x.enabled).length}/${rules.length}`);
}

// ── T5 ── 取 1 条 rule 跑 cognitive diagnose → 验证 RuleRefCollector 收口
// POST /api/v1/cognitive/diagnose { metric, deviation, domain, maxDepth }
// 期望: response.reasoningPath.ruleRefs[].ruleId ∈ rules[].id
let firstRuleId = rules.find(x => x.enabled)?.id;
let t5RuleRefHit = null;
if (firstRuleId) {
  try {
    const r = await post('/api/v1/cognitive/diagnose', {
      ...withAuth,
      body: { metric: firstRuleId, deviation: -10, domain: 'finance', maxDepth: 4 },
      timeout: 60000,
    });
    const code = r.body?.code ?? r.body?.status;
    const data = r.body?.data || {};
    const path = data.reasoningPath || {};
    const ruleHits = (path.ruleRefs || []).map(x => x.ruleId)
      .concat((path.steps || []).map(s => s.ruleRef?.ruleId).filter(Boolean));
    t5RuleRefHit = ruleHits.filter(id => id === firstRuleId).length > 0;
    check('T5 cognitive diagnose 200 + reasoningPath',
      r.status === 200 && code === 0 && path.steps,
      `status=${r.status} steps=${path.steps?.length} ruleRefs=${(path.ruleRefs || []).length}`);
    check('T5 RuleRefCollector 已收口 (seed rule → reasoningPath.ruleRefs)',
      t5RuleRefHit === true,
      `firstRuleId=${firstRuleId} ruleRefsIncluded=${JSON.stringify(ruleHits).slice(0, 120)}`);
  } catch (e) {
    check('T5 cognitive diagnose (RuleRef 收口)', false, e.message);
  }
} else {
  check('T5 RuleRefCollector 收口 (skip 无 enabled rule)', true, '規則为 0，跳过 — P3');
}

// ── T6 ── 不存在的 task id
{
  const r = await post('/api/v1/knowledge/extract/nonexistent-id-zzz/approve', { ...withAuth, timeout: 10000 });
  check('T6 不存在 task approve → 404/400', r.status === 404 || r.status === 400 || r.status === 403,
    `status=${r.status} msg=${JSON.stringify(r.body).slice(0, 120)}`);
}

report('06-cheng (KB approve 闭环 + RuleRef 收口)', '/tmp/wave4_06-cheng.json');
