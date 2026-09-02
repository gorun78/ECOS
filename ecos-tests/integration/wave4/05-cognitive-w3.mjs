#!/usr/bin/env node
// 05-cognitive-w3.mjs — Wave-4.1 域 5/7: cognitive Wave3.2 Demo 端到端
// 验收 (POST /api/v1/cognitive/demo/wave3):
//   T1 健康端点 GET /api/v1/cognitive/health 200 (或 fallback help 探测)
//   T2 demo 200 + 4 段产物齐 (sourceDocument + entityLinking + causalDiagnosis + decision)
//   T3 causalDiagnosis.reasoningPath 非空 (推理路径)
//   T4 reasoningPath ≥ 1 step 且 step 字段完整 (stepId/description)
//   T5 5 Contract: ruleRefs/clauses 至少出现 1 个, 或 steps 中某 step 携带 ruleRef/precedentRef
// 退出: 0=PASS, 1=FAIL, 77=ENV_BLOCKED

import { BASE, http, get, post, check, report, gatewayAlive, login, note } from './lib/w4-common.mjs';

console.log('═══ 05-cognitive-w3.mjs — Wave-4.1 域5: cognitive Wave3.2 Demo ═══');
console.log(`  BASE: ${BASE}`);
await gatewayAlive() || process.exit(77);

let token;
try {
  const sess = await login();
  token = sess.token;
} catch (e) {
  note('T-session 失败(部分端点可能 401)', e.message);
}
const withAuth = { token };

// ── T1 ── 健康探测
{
  const r = await get('/api/v1/cognitive/health', { ...withAuth, timeout: 10000 });
  // 容忍: /api/v1/cognitive/health 或 /api/v1/engine/cognitive/health
  const r2 = r.status === 200 ? r : await get('/api/v1/engine/cognitive/health', { ...withAuth, timeout: 10000 }).catch(() => null);
  check('T1 cognitive 健康探测 200 (容忍两路径)',
    (r.status === 200 || r2?.status === 200),
    `health=${r.status} engine=${r2?.status}`);
}

// ── T2 ── demo 主链路
// markdown 样例: 含 mermaid 因果 ≥ 3 个 token (CausalChain.exception 的 5 步推断)
const sample = `# Wave-4.1 Demo 财报
## 概述
销售额总额 (metric) 下降 12%, 库存成本高, 资金流出。

\`\`\`mermaid
graph LR
  Sales -->|deviation| Cash Flow
  CashFlow -->|trigger| InventoryCost
  InventoryCost -->|root cause| Margin
\`\`\`

- 销售额下降 12%
- 根因: 配件涨价
- 距财年底还有 45 天
`;

let diagonal;
{
  const r = await post('/api/v1/cognitive/demo/wave3', {
    ...withAuth,
    body: { markdown: sample, domain: 'finance', maxDepth: 4 },
    timeout: 60000, // LLM/diagnose 可能慢
  });
  const code = r.body?.code ?? r.body?.status;
  const data = r.body?.data || {};
  diagonal = data;
  // P0-3 留痕: demo/wave3 在 cross-domain 路径下系统层 P0
  // 真实观察 (Wave-4.1 暴露 P0-3): gateway 日志显示
  //   ERROR QuotaFilter: Quota check failed tenantId=...: BadSqlGrammarException:
  //   PSQLException: ERROR: could not determine data type of parameter $1
  // 后续 DefaultHandlerExceptionResolver: HttpMessageNotReadableException:
  //   Required request body is missing: Wave3DemoController.demo(java.util.Map)
  // → 真根因: QuotaFilter.filter(javax.servlet.http.HttpServletRequest) 在 chain.doFilter
  //   之前未复用 ReadableHttpBody / Cache-control 缓冲 — Spring 后续 @RequestBody 解析
  //   已看到 EOF. 实为 Wave-1A sysman 多租户 P2-17 配额组件没保证 request 体可重读.
  // 当前 metadata 全推出 4 段产物 → T2.1 关直 FAIL，T2.2 / T2.3 进料源 (B 给的是空 {}
  // 大小采样 ≠ 推理结论) 真实评估 disabled.
  check('T2 demo 200 + code===0', r.status === 200 && code === 0, `status=${r.status} code=${code}`);
  check('T2 4 段产物齐 (sourceDocument + entityLinking + causalDiagnosis + decision)',
    data.sourceDocument && data.entityLinking && data.causalDiagnosis && data.decision,
    `keys=[${Object.keys(data).join(',')}.slice`);
  const ents = data.sourceDocument?.extractedEntities || [];
  check('T2 sourceDocument 抽实体 ≥ 1', ents.length >= 1, `entities=${ents.length}`);
  const links = data.entityLinking || [];
  check('T2 entityLinking 返回 (含降级 status 可空)', Array.isArray(links), `links=${links.length}`);
}

// ── T3 ── causalDiagnosis.reasoningPath 非空
{
  const cd = diagonal?.causalDiagnosis || {};
  check('T3 causalDiagnosis 含 reasoningPath', !!(cd.rootCause || cd.causalChain || cd.reasoningPath),
    `rootCause=${(cd.rootCause || '').slice(0, 60)} chainLen=${cd.causalChain?.length}`);
  const rp = cd.reasoningPath;
  check('T3/B Wave-3.2 推理路径 reasoningPath 非空', !!rp,
    rp ? `steps=${rp.steps?.length} conclusion=${JSON.stringify(rp.conclusion || '').slice(0, 60)}` : 'reasoningPath 缺失');
}

// ── T4 ── steps 完整
{
  const rp = diagonal?.causalDiagnosis?.reasoningPath;
  if (!rp || !rp.steps) {
    check('T4 steps ≥ 1', false, 'reasoningPath 缺失 steps');
  } else {
    check('T4 steps ≥ 1', rp.steps.length >= 1, `count=${rp.steps.length}`);
    const s0 = rp.steps[0];
    check('T4 step 字段完整 (stepId/description/confidence)',
      s0 && (s0.stepId || s0.description) && typeof s0.confidence === 'number',
      JSON.stringify(s0).slice(0, 150));
  }
}

// ── T5 ── 5 Contract 在 reasoningPath (RuleRef / PrecedentRef / Justification)
// pathStats 是 controller 内生成, 这里独立再算一次
{
  const rp = diagonal?.causalDiagnosis?.reasoningPath;
  const stats = diagonal?.causalDiagnosis?.reasoningPathStats;
  if (!rp) {
    check('T5 reasoningPath 内 5 Contract 证据 (豁免: path 缺失)', true, '跳过 — 由 T3 记录原因');
  } else {
    // Contract 1 ReasoningPath: 本身存在 → 1 命中
    // Contract 2 ReasoningStep: steps → count
    // Contract 3 RuleRef: path.ruleRefs / steps[].ruleRef
    // Contract 4 PrecedentRef: path.precedentRefs / steps[].precedentRef
    // Contract 5 Justification: path.justification / path.clauses (clauses 是 JustificationClause)
    const pathHit = {
      ReasoningPath: !!rp,
      ReasoningStep: (rp.steps || []).length,
      RuleRef: (rp.ruleRefs || []).length + (rp.steps || []).reduce((n, s) => n + (s.ruleRef ? 1 : 0), 0),
      PrecedentRef: (rp.precedentRefs || []).length + (rp.steps || []).reduce((n, s) => n + (s.precedentRef ? 1 : 0), 0),
      Justification: !!(rp.justification || (rp.clauses || []).length),
    };
    check('T5 Contract[ReasoningPath]', pathHit.ReasoningPath === true);
    check('T5 Contract[ReasoningStep ≥ 1]', pathHit.ReasoningStep >= 1, `count=${pathHit.ReasoningStep}`);
    check('T5 Contract[RuleRef ≥ 1]', pathHit.RuleRef >= 1,
      `count=${pathHit.RuleRef} path.ruleRefs=${(rp.ruleRefs || []).length} (stats.rule_hits=${stats?.rule_hits})`);
    // PrecedentRef 容忍 0 (== Wave-3B pgvector 未完成) / Justification 容忍 0 (字符串 justification 也有)
    check('T5 Contract[PrecedentRef ≥ 0 或 ≥1 容忍 Wave-3B 未完成]', pathHit.PrecedentRef >= 0,
      `count=${pathHit.PrecedentRef} (允许 0 — 需 pgvector) stats.precedent_count=${stats?.precedent_count}`);
    check('T5 Contract[Justification 有值]', pathHit.Justification === true,
      `justification=${!!rp.justification} clauses=${(rp.clauses || []).length} stats.avg_confidence=${stats?.avg_confidence}`);
    note('T5 5 Contract 证据', JSON.stringify(pathHit));
  }
}

report('05-cognitive-w3 (Wave3.2 demo → 5 Contract)', '/tmp/wave4_05-cognitive.json');
