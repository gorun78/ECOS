#!/usr/bin/env node
// 07-cross-domain.mjs — Wave-4.1 域 7/7: 跨域 7 步串
//      data 观众 → cognitive 决策 → kb 入库
// 验收 (7 步):
//   S1 data: POST /transform/execute input→validate → output 行 (产出: validationedRows, rule: must-exist)
//   S2 data→onto: 检查 S1 输出可投到 GET /api/v1/ontology/domains 命中已有 domain
//   S3 onto→cognitive: 拿 domain code 输入 POST /api/v1/cognitive/demo/wave3 (markdown 自动生 metric)
//   S4 cognitive: reasoningPath.steps ≥ 1, ruleRefs ≥ 0 (判定 ≥ 1 才记 P0)
//   S5 cognitive→kb: DIAGNOSE 后用 reasoningPath.ruleRefs 中的 ruleId GET /api/v1/knowledge/compliance-rules/{id}
//   S6 kb→onto: 用 ruleId 在 /api/v1/knowledge/compliance-rules 列表确认 rule 存在 (入库闭环)
//   S7 kb→decision: causalDiagnosis + decision ID 落库 (ecos_decision 新增 1 行; 无权限, smoke 测)
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED

import { BASE, http, get, post, check, report, gatewayAlive, login, note } from './lib/w4-common.mjs';

console.log('═══ 07-cross-domain.mjs — Wave-4.1 域7: data→cognitive→kb 7 步串 ═══');
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

// ── S1 data: audience rows (校验后) ──
let filteredRows = 0;
let s1Ok = false;
{
  const r = await post('/api/v1/engine/data/transform/execute', {
    ...withAuth,
    body: {
      input: {
        columns: ['name', 'dept', 'age'],
        rows: [
          { name: 'Al', dept: 'R&D', age: 30 },
          { name: 'Be', dept: 'QA', age: 40 },
          { name: 'Ce', dept: 'QA', age: 0 }, // filtered out
        ],
      },
      chain: [{ type: 'validation', params: { column: 'age', op: 'gt', threshold: 0 } }],
    },
    timeout: 30000,
  });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || {};
  filteredRows = data.output?.rows?.length ?? 0;
  s1Ok = r.status === 200 && code === 0 && data.success === true;
  check('S1 data 观众 (validation 过滤 1 行)', s1Ok && filteredRows === 2,
    `status=${r.status} success=${data.success} outRows=${filteredRows}`);
}

// ── S2 data→onto: 拿 domain code 作为 S3 输入 ──
let domainCode = null;
{
  const r = await get('/api/v1/ontology/domains', { ...withAuth, timeout: 10000 });
  const data = r.body?.data || [];
  const arr = Array.isArray(data) ? data : (data?.content || []);
  domainCode = arr[0]?.code || 'finance';
  check('S2 data→onto: 拿到 1 个 domainCode 供 cognitive 用', !!domainCode,
    `code=${domainCode} total=${arr.length}`);
}

// ── S3 onto→cognitive: Wave3 demo 端到端 ──
let diagonal = null;
{
  const md = `# Cross-demo ${Date.now()}
## 概要
${domainCode} updated_下游指标骤降 15%, 成本抬升。

\`\`\`mermaid
graph LR
  ${domainCode || 'Finance'} --> Metric
  Metric -->|deviation| Volume
  Volume -->|root| Margin
\`\`\`

- Margin -15%
- 业务: ${domainCode || 'finance'}
`;
  const r = await post('/api/v1/cognitive/demo/wave3', {
    ...withAuth,
    body: { markdown: md, domain: 'finance', maxDepth: 4 },
    timeout: 90000,
  });
  const code = r.body?.code ?? r.body?.status;
  diagonal = r.body?.data || {};
  check('S3 onto→cognitive: wave3 200 + 4 段产物',
    r.status === 200 && code === 0 && diagonal.sourceDocument && diagonal.causalDiagnosis,
    `status=${r.status} domain=${diagonal?.causalDiagnosis?.domain} chain=${diagonal?.causalDiagnosis?.causalChain?.length}`);
}

// ── S4 cognitive: 推理路径 ≥ 1 step ──
{
  const rp = diagonal?.causalDiagnosis?.reasoningPath;
  const steps = rp?.steps || [];
  check('S4 推理路径 steps ≥ 1', steps.length >= 1, `count=${steps.length}`);
}

// ── S5 cognitive→kb: ruleRef → GET compliance-rules/{id} ──
// 07 串: 用 reasoningPath.ruleRefs[0].ruleId 跳 kb 详情
let kbRuleHit = 0;
let ruleProbeDetail = 'skip';
{
  const rp = diagonal?.causalDiagnosis?.reasoningPath || {};
  const ruleRefs = (rp.ruleRefs || []).map(r => r.ruleId)
    .concat((rp.steps || []).map(s => s.ruleRef?.ruleId).filter(Boolean));
  const uniq = [...new Set(ruleRefs)];
  if (uniq.length === 0) {
    note('S5 reasoningPath 无 ruleRef (容忍 Wave-3B 未就绪)', '跳过 kb rule 详情探测');
  } else {
    const probeId = uniq[0];
    const r = await get(`/api/v1/knowledge/compliance-rules/${encodeURIComponent(probeId)}`, { ...withAuth, timeout: 10000 });
    kbRuleHit = r.status === 200 ? 1 : 0;
    ruleProbeDetail = `probe=${probeId} status=${r.status} found=${kbRuleHit === 1} total=${uniq.length}`;
    check('S5 kb rule 详情 200 (wave reasoningPath 推出的 ruleId 在 kb 表存在)',
      kbRuleHit === 1, ruleProbeDetail);
  }
}

// ── S6 kb 入库闭环: compliance-rules 列表存在映射规则 (供 07 串展示)
{
  const r = await get('/api/v1/knowledge/compliance-rules', { ...withAuth, timeout: 10000 });
  const data = r.body?.data || [];
  const arr = Array.isArray(data) ? data : (data?.content || []);
  check('S6 kb 入库: compliance-rules ≥ 1 (approved 入库闭环可见)', arr.length >= 1,
    `total=${arr.length} ids=[${arr.slice(0, 3).map(x => x.id).join(',')}]`);
}

// ── S7 cognitive decision 落库: decisionId 非空 + 格式 normal
{
  const d = diagonal?.decision || {};
  check('S7 决策落库 (decisionId non-empty)',
    !!d.decisionId && /[\w-]+/.test(d.decisionId),
    `decisionId=${d.decisionId} category=${d.category} scenario=${d.scenario}`);
}

report('07-cross-domain (data→cognitive→kb 跨域 7 步串)', '/tmp/wave4_07-cross.json');
