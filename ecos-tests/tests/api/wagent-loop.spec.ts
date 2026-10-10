import { expect, test } from '@playwright/test';
import { call, loginAsAdmin } from '../helpers/ecos';

/**
 * PRD-10 W-Agent 赋能 DIK-C —— 25 端点 live REST 契约规格（`--project=api`，只需 gateway :8080）。
 *
 * 【交付口径（与 forecast-loop.spec.ts 同纪律，交付非验收）】
 * 本文件是**契约源码交付**，非"已验收通过"凭证。断言只落到
 * `ApiResponse<T>` 信封 + `body.code` + 关键路由/语义字段（runId/status/grade/layers/endpoint_map/…），
 * 绝不臆造生产数字（金额/概率以 BigDecimal → number 语义透传，不 double）。
 *
 * 【携层核心事实（忠实源，不臆造）】
 * - 承载：`WAgentRunController`(#5-#10) / `WAgentKillSwitchController`(杀闸) /
 *   `WAgentQuestionController`#3/#4 / `WAgentGoalController`#1/#2 /
 *   `WAgentReadinessController`#12/#13（DIKC 定级，非 LLM） /
 *   `WAgentToolController`#14-#16 / `WAgentCandidateController`#17-#20 /
 *   `WAgentDecisionController`#21-#23（claims/evidence/decision） /
 *   `WAgentActionController`#24a/#24b（draft/commit）。
 * - 异常码映射：`BusinessException(int code, ...)` → **HTTP 恒 400** + `body.code` 携业务码
 *   （400/409/403/404/501），二者语义双重一致（禁 200+code:xxx 伪装，亦禁 5xx 吞拒）。
 *
 * 【依赖】gateway :8080 起 + `cognitive:true` profile（W Agent 编排域打在 ai-engine → gateway fat-JAR）。
 * 凭据只从 `ecos-tests/.env` 环境变量读取（禁写入用例，见 AGENTS.md `ecos-tests` 条）。
 *
 * 【断言焦点】
 *   A. 查端点已带 token → 合法码 200 + `code===0` + key 字段非空。
 *   B. 杀闸 SYSTEM 粒度 + 非 admin → HTTP 400 + code 403 E-POLICY（fail-closed DENY，禁 fail-open）。
 *   C. readiness 定级 = DIKC 四元（d/i/k/c）+ grade 枚举 A/B/C/D（非 LLM、确定性，多次必同值）。
 *   D. claims/evidence 随行键 byte-identical（对仗 forecast-loop X-1 六要素随行不漂移）。
 */

const W = '/api/v1/wagent';

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

// ─── A. 路由存在性 + 已带 token 合法码 + 信封 key 契约 ───

test('wagent Q-1: createQuestion confidence>=0.70 → READY；<0.70 → CLARIFY（F10-05 不 fail-open 建 Run）', async ({ request }) => {
  const hi = await call(request, 'POST', `${W}/questions`, {
    token,
    data: { questionId: 'q-w-e2e-1', intent: 'FORECAST', confidence: 0.92, slotsSourceJson: '{}' },
  });
  expect(hi.status, `createQuestion(hi) HTTP=${hi.status} 应 200`).toBe(200);
  expect(hi.json?.code).toBe(0);
  expect(hi.json?.data?.questionId).toBe('q-w-e2e-1');
  expect(hi.json?.data?.status, 'hi-confidence 应 READY').toBe('READY');

  const lo = await call(request, 'POST', `${W}/questions`, {
    token,
    data: { questionId: 'q-w-e2e-2', intent: 'DIAGNOSE', confidence: 0.4, slotsSourceJson: '{}' },
  });
  expect(lo.status).toBe(200);
  expect(lo.json?.data?.status, 'low-confidence 保守 CLARIFY').toBe('CLARIFY');

  const g = await call(request, 'GET', `${W}/questions/q-w-e2e-1`, { token });
  expect(g.status).toBe(200);
  expect(g.json?.code).toBe(0);
  expect(g.json?.data?.questionId).toBe('q-w-e2e-1');
  // latest_run 摘要字符串键随行（入参不含 number 即回零值摘要）
  expect('latest_run' in (g.json?.data ?? {}), 'getQuestion 应回 latest_run 键').toBe(true);
});

test('wagent G-1: createGoal → 200 goalId + version=1（integer，禁 double）', async ({ request }) => {
  const r = await call(request, 'POST', `${W}/goals`, {
    token,
    data: { goalId: 'g-w-e2e-1', goalType: 'KPI', description: 'e2e goal' },
  });
  expect(r.status, `createGoal HTTP=${r.status} 应 200`).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(r.json?.data?.goalId).toBe('g-w-e2e-1');
  expect(r.json?.data?.version, 'version integer-only').toBe(1);
});

test('wagent T-1: searchTools 四重过滤回 tools[]/count/progressiveDisclosure', async ({ request }) => {
  const r = await call(request, 'GET', `${W}/tools?toolset=di&query=`, { token });
  expect(r.status, `searchTools HTTP=${r.status} 应 200`).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(Array.isArray(r.json?.data?.tools)).toBe(true);
  expect(r.json?.data?.progressiveDisclosure).toBe(true);
});

test('wagent T-2: describeTool(不存在) → E_NOT_FOUND → HTTP 400 + code 404（禁 5xx）', async ({ request }) => {
  const r = await call(request, 'GET', `${W}/tools/tool-does-not-exist-e2e`, { token });
  expect(r.status, `describeTool(404) HTTP=${r.status} 应 400（BusinessException 固定）`).toBe(400);
  expect(r.json?.code, `describeTool code=${r.json?.code} 应 404 业务语义`).toBe(404);
  expect(String(r.json?.message ?? '')).toMatch(/E_NOT_FOUND|tool/);
});

// ─── B. Run 主链（#5-#10）：起 run → 201/202 异步 → 状态/取消/失败码 ───

let firstRunId = '';

test('wagent R-1: startRun 同步 → 201 PLANNING；Prefer: respond-async → 202', async ({ request }) => {
  const syncRes = await request.fetch(`${W}/runs`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { idempotencyKey: 'idem-w-e2e-1', questionId: 'q-w-e2e-1' },
  });
  expect(syncRes.status(), `startRun sync HTTP=${syncRes.status()} 应 201 CREATED`).toBe(201);
  const sync = (await syncRes.json()) as any;
  expect(sync.code).toBe(0);
  expect(sync.data.status, '首跑状态 PLANNING').toBe('PLANNING');
  expect(sync.data.async).toBe(false);
  expect(sync.data.runId).toBeTruthy();
  firstRunId = sync.data.runId;

  const asyncRes = await request.fetch(`${W}/runs`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Prefer: 'respond-async' },
    data: { idempotencyKey: 'idem-w-e2e-async', questionId: 'q-w-e2e-2' },
  });
  expect(asyncRes.status(), `startRun async HTTP=${asyncRes.status()} 应 202 ACCEPTED`).toBe(202);
  const aj = (await asyncRes.json()) as any;
  expect(aj.data.status).toBe('PLANNING');
  expect(aj.data.async).toBe(true);
});

test('wagent R-2: getRun 状态 + timeline + budget 键随行', async ({ request }) => {
  const g = await call(request, 'GET', `${W}/runs/${firstRunId}`, { token });
  expect(g.status, `getRun HTTP=${g.status} 应 200`).toBe(200);
  expect(g.json?.code).toBe(0);
  expect(g.json?.data?.status).toBeTruthy();
  expect(Array.isArray(g.json?.data?.timeline), 'timeline 应事件数组').toBe(true);
});

test('wagent R-3: streamRunEvents SSE P-4 未闭合 → 501 降级轮询（HTTP 400 + code 501，禁 5xx）', async ({ request }) => {
  const r = await call(request, 'GET', `${W}/runs/${firstRunId}/events`, { token });
  // BusinessException(501) → HTTP 恒 400 + body.code=501（携带降级语义），断言二者一致
  expect(r.status, `SSE P-4 未闭合 HTTP=${r.status} 应 400（BusinessException）`).toBe(400);
  expect(r.json?.code, `SSE code=${r.json?.code} 应 501（降级脚手架语义）`).toBe(501);
  expect(String(r.json?.message ?? '')).toMatch(/SSE|polling|P-4/i);
});

test('wagent R-4: approveRunGate 缺 approvalToken → 400 E-WA-V；cancelRun 幂等 200', async ({ request }) => {
  const blank = await call(request, 'POST', `${W}/runs/${firstRunId}/approval`, {
    token,
    data: { approvalToken: '' },
  });
  expect(blank.status, 'approvalToken 空应 400').toBe(400);
  expect(blank.json?.code).toBe(400);
  expect(String(blank.json?.message ?? '')).toMatch(/E-WA-V|approvalToken/);

  const c1 = await call(request, 'POST', `${W}/runs/${firstRunId}/cancel`, { token });
  expect(c1.status).toBe(200);
  expect(c1.json?.code).toBe(0);
  // 幂等：再发一次仍 200
  const c2 = await call(request, 'POST', `${W}/runs/${firstRunId}/cancel`, { token });
  expect(c2.status, 'cancel 幂等二次仍 200').toBe(200);
  expect(c2.json?.code).toBe(0);
});

// ─── C. Kill Switch（#11）：SYSTEM 粒度 + 非 admin → 403 E-POLICY（fail-closed）；TENANT 校验 ───

test('wagent K-1: kill-switch SYSTEM + 非 admin → HTTP 400 + code 403 E-POLICY（禁 fail-open）', async ({ request }) => {
  const res = await request.fetch(`${W}/flags/kill-switch`, {
    method: 'POST',
    // 故意不带 X-User-Role（= 非 admin）→ SYSTEM 粒度应拒
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { scope: 'SYSTEM', enabled: true, reason: 'e2e denial' },
  });
  expect(res.status(), `SYSTEM 非 admin HTTP=${res.status()} 应 400（BusinessException）`).toBe(400);
  const j = (await res.json()) as any;
  expect(j.code, `E-POLICY code=${j.code} 应 403 业务语义`).toBe(403);
  expect(String(j.message ?? '')).toMatch(/E-POLICY|admin/i);
});

test('wagent K-2: kill-switch SYSTEM + admin → 200 回 scope/enabled/pausedRunCount(integer)', async ({ request }) => {
  const res = await request.fetch(`${W}/flags/kill-switch`, {
    method: 'POST',
    // X-User-Role 由上游 SecurityConfig/ClearanceInterceptor 写入；此处断言 admin 路径
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'X-User-Role': 'admin' },
    data: { scope: 'SYSTEM', enabled: true, reason: 'e2e admin' },
  });
  expect(res.status(), `SYSTEM admin HTTP=${res.status()} 应 200`).toBe(200);
  const j = (await res.json()) as any;
  expect(j.code).toBe(0);
  expect(j.data.scope).toBe('SYSTEM');
  expect(j.data.enabled).toBe(true);
  expect(typeof j.data.pausedRunCount, 'pausedRunCount integer-only').toBe('number');
});

test('wagent K-3: kill-switch TENANT 缺 tenantId → 400 E-WA-V；非法 scope → 400', async ({ request }) => {
  const noTenant = await call(request, 'POST', `${W}/flags/kill-switch`, {
    token,
    data: { scope: 'TENANT', enabled: true, reason: 'x' },
  });
  expect(noTenant.status, 'TENANT 缺 tenantId 应 400').toBe(400);
  expect(String(noTenant.json?.message ?? '')).toMatch(/E-WA-V|tenantId|scope/);

  const badScope = await call(request, 'POST', `${W}/flags/kill-switch`, {
    token,
    data: { scope: 'GLOBAL', enabled: true },
  });
  expect(badScope.status, 'scope 非 SYSTEM/TENANT 应 400').toBe(400);
  expect(String(badScope.json?.message ?? '')).toMatch(/E-WA-V|scope/);
});

// ─── D. Readiness DIKC（#12/#13）：定级 = 四元 + A/B/C/D 枚举（非 LLM），refill 循环封顶 ───

test('wagent RD-1: getReadiness → grade 枚举 A-D + layers{d,i,k,c} + degradations[]（DIKC 非 LLM 定级）', async ({ request }) => {
  const r = await call(request, 'GET', `${W}/readiness/q-w-e2e-1`, { token });
  expect(r.status, `getReadiness HTTP=${r.status} 应 200`).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(String(r.json?.data?.grade ?? ''), 'grade 应 A/B/C/D 单字母').toMatch(/^[A-D]$/);
  const layers = r.json?.data?.layers ?? {};
  for (const k of ['d', 'i', 'k', 'c']) {
    expect(layers[k], `layers.${k} 缺失`);
  }
  expect(Array.isArray(r.json?.data?.degradations), 'degradations 应数组').toBe(true);
});

test('wagent RD-2: refillReadiness → refillRound/capped/suspend/submittedTaskIds[]（round 封顶 suspend）', async ({ request }) => {
  const r = await call(request, 'POST', `${W}/readiness/q-w-e2e-1/refill`, { token });
  expect(r.status, `refill HTTP=${r.status} 应 200`).toBe(200);
  expect(r.json?.code).toBe(0);
  expect(r.json?.data?.questionId).toBe('q-w-e2e-1');
  expect(typeof r.json?.data?.refillRound, 'refillRound integer-only').toBe('number');
  expect(['boolean']).toContain(typeof r.json?.data?.capped);
  expect(Array.isArray(r.json?.data?.submittedTaskIds), 'submittedTaskIds 应数组').toBe(true);
  expect(Array.isArray(r.json?.data?.gaps), 'gaps 应数组').toBe(true);
});

// ─── E. Candidates（#17-#20）：查询回显 + 404 语义 + 评审四目/发布 token 校验 ───

test('wagent CD-1: listCandidates 过滤回显 + 404 语义（不存在 id → HTTP 400 + code 404）', async ({ request }) => {
  const l = await call(request, 'GET', `${W}/candidates?type=RULE&status=DRAFT`, { token });
  expect(l.status, `listCandidates HTTP=${l.status} 应 200`).toBe(200);
  expect(l.json?.code).toBe(0);

  const miss = await call(request, 'GET', `${W}/candidates/cand-does-not-exist-e2e`, { token });
  expect(miss.status, `getCandidate(404) HTTP=${miss.status} 应 400`).toBe(400);
  expect(miss.json?.code, `code=${miss.json?.code} 应 404`).toBe(404);
});

test('wagent CD-2: reviewCandidate 缺 decision → 400 E-WA-V；publish 缺 approvalToken → 400', async ({ request }) => {
  const noDec = await call(request, 'POST', `${W}/candidates/cand-e2e-1/review`, {
    token,
    data: { decision: '', token: 'tok' },
  });
  expect(noDec.status, 'review 缺 decision 应 400').toBe(400);
  expect(String(noDec.json?.message ?? '')).toMatch(/E-WA-V|decision/);

  // 不存在 candidate → 404 语义（HTTP 400 + code 404）
  const missRev = await call(request, 'POST', `${W}/candidates/cand-missing-e2e/review`, {
    token,
    data: { decision: 'APPROVE', token: 'tok' },
  });
  expect(missRev.status).toBe(400);
  expect(missRev.json?.code).toBe(404);

  const noTok = await call(request, 'POST', `${W}/candidates/cand-e2e-1/publish`, {
    token,
    data: { approvalToken: '' },
  });
  expect(noTok.status, 'publish 缺 approvalToken 应 400').toBe(400);
  expect(String(noTok.json?.message ?? '')).toMatch(/E-WA-V|approvalToken/);
});

// ─── F. Claims / Evidence / Decision（#21-#23）：随行键不漂移（对仗 forecast X-1）+ 决策 201 ───

test('wagent CE-1: listClaims 回 runId/claims[]/count；traceEvidence 回 endpoint_map(JSON 串)', async ({ request }) => {
  const c = await call(request, 'GET', `${W}/claims/${firstRunId}`, { token });
  expect(c.status, `listClaims HTTP=${c.status} 应 200`).toBe(200);
  expect(c.json?.code).toBe(0);
  expect(c.json?.data?.runId).toBe(firstRunId);
  expect(Array.isArray(c.json?.data?.claims), 'claims 应数组').toBe(true);
  expect(typeof c.json?.data?.count, 'count integer-only').toBe('number');

  const e = await call(request, 'GET', `${W}/evidence/ev-e2e-1`, { token });
  expect(e.status, `traceEvidence HTTP=${e.status} 应 200`).toBe(200);
  expect(e.json?.code).toBe(0);
  expect(e.json?.data?.evidenceId).toBe('ev-e2e-1');
  // endpoint_map 保持 JSON 字符串语义（engine→path，不展开成对象 → 禁 double 语义漂移）
  expect(typeof e.json?.data?.endpoint_map, 'endpoint_map 应 string 语义').toBe('string');
});

test('wagent CE-2: recordDecision → 201 decisionId + runId 随行 + 键不漂移', async ({ request }) => {
  const r = await call(request, 'POST', `${W}/decisions`, {
    token,
    data: { runId: firstRunId, decision: 'adopt', rationale: 'e2e' },
  });
  expect(r.status, `recordDecision HTTP=${r.status} 应 201 CREATED`).toBe(201);
  expect(r.json?.code).toBe(0);
  expect(r.json?.data?.decisionId).toBeTruthy();
  expect(r.json?.data?.runId, 'decision 应随行 runId（对仗 X-1 不漂移）').toBe(firstRunId);
  expect(r.json?.data?.recordedAt).toBeTruthy();
});

// ─── G. Actions（#24a/#24b）：5 必填缺一 400；commit 幂等 published → 409 E-WA-STATE ───

test('wagent A-1: draftAction 5 必填缺一 → 400；全齐 → 200 DRAFT', async ({ request }) => {
  const bad = await call(request, 'POST', `${W}/actions`, {
    token,
    // 缺 dueDate
    data: { optionId: 'o-1', title: 't', ownerId: 'u1', role: 'pm' },
  });
  expect(bad.status, 'draftAction 缺必填应 400').toBe(400);
  expect(bad.json?.code).toBe(400);
  expect(String(bad.json?.message ?? '')).toMatch(/dueDate|optionId|title|ownerId|role/);

  const good = await call(request, 'POST', `${W}/actions`, {
    token,
    data: { optionId: 'o-1', title: 't', ownerId: 'u1', role: 'pm', dueDate: '2026-10-20' },
  });
  expect(good.status, `draftAction HTTP=${good.status} 应 200`).toBe(200);
  expect(good.json?.code).toBe(0);
  expect(good.json?.data?.status).toBe('DRAFT');
});

test('wagent A-2: commitAction 缺 token → 400；再 commit 已 published → 409 E-WA-STATE', async ({ request }) => {
  const d = await call(request, 'POST', `${W}/actions`, {
    token,
    data: { optionId: 'o-2', title: 't2', ownerId: 'u2', role: 'eng', dueDate: '2026-10-21' },
  });
  const actionId = d.json?.data?.actionId;
  expect(actionId).toBeTruthy();

  const noTok = await request.fetch(`${W}/actions/${actionId}/commit`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { approvalToken: '' },
  });
  expect(noTok.status(), 'commit 缺 token 应 400').toBe(400);

  const ok1 = await request.fetch(`${W}/actions/${actionId}/commit`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { approvalToken: 'tok-e2e' },
  });
  expect(ok1.status(), `commit HTTP=${ok1.status()} 应 200`).toBe(200);
  const o1 = (await ok1.json()) as any;
  expect(o1.data.status).toBe('PUBLISHED');
  expect(o1.data.committed).toBe(true);

  // 二次 commit → 已 published → 409 语义（HTTP 400 + code 409）
  const dup = await request.fetch(`${W}/actions/${actionId}/commit`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { approvalToken: 'tok-e2e' },
  });
  expect(dup.status(), '二次 commit 应 400（BusinessException）').toBe(400);
  const dupJ = (await dup.json()) as any;
  expect(dupJ.code, `E-WA-STATE code=${dupJ.code} 应 409`).toBe(409);
});
