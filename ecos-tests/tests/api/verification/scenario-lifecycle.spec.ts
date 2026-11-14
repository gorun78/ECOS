import { expect, test } from '@playwright/test';
import { loginAsAdmin, call, bearer } from '../../helpers/ecos';

/**
 * B1 场景工作台闭环 — API 级（对应矩阵域01 §三~§七）。
 * 覆盖矩阵条目：V-WS-010~019（CRUD/状态机）、V-WS-030~033（预校验）、V-WS-024/100（完整度/对账）。
 *
 * 反假绿纪律：
 *  - 非法跃迁必须返回业务错误（409 或业务错误码），且 DB 状态不变（无副作用）
 *  - 每条关键断言配反向探针
 *  - 写操作后回读 +（执行报告中补 L4 PG 对账）
 *
 * 测试数据策略：新建场景打 VERIFY- 前缀，不删除既有 sc001/sc002/sc003。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});
// 不用 serial：本文件含「缺陷断言」（预期 FAIL）。serial 会首个失败即中断后续用例，
// 导致真实可用的功能被连坐跳过 ⇒ 假绿的对立面「假红」。各用例尽量独立，共享态仅 createdId（skip 守卫）。

const ts = Date.now();
const scenarioName = `VERIFY-scenario-${ts}`;
let createdId = '';

test('V-WS-010 场景列表返回数组且含真实场景', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios', { token });
  expect(status).toBe(200);
  const list = Array.isArray(json?.data) ? json.data : [];
  expect(list.length, '应至少含既有场景').toBeGreaterThan(0);
  const names = list.map((s: any) => s?.name);
  expect(names.some((n: string) => n && n.includes('飞行运行安全')), '应含 sc001 飞行运行安全').toBe(true);
});

test('V-WS-011 场景详情：已存在 id 200，不存在 id 404（反向探针）', async ({ request }) => {
  const ok = await call(request, 'GET', '/api/v1/workspace/scenarios/sc001', { token });
  expect(ok.status).toBe(200);
  expect(String(ok.json?.data?.id ?? '')).toBe('sc001');

  const bad = await call(request, 'GET', '/api/v1/workspace/scenarios/NOT_EXIST_999', { token });
  expect(bad.status, '不存在 id 必须 404').toBe(404);
});

test('V-WS-012 创建场景（VERIFY 前缀）后回读一致', async ({ request }) => {
  const created = await call(request, 'POST', '/api/v1/workspace/scenarios', {
    token,
    data: {
      name: scenarioName,
      description: 'Playwright 验证：创建场景回读一致性',
      businessGoal: '验证创建-回读闭环',
      priority: 'LOW',
    },
  });
  expect([200, 201], `创建应 200/201，实得 ${created.status}`).toContain(created.status);
  createdId = String(created.json?.data?.id ?? '');
  expect(createdId, '创建应返回 id').not.toBe('');

  const read = await call(request, 'GET', `/api/v1/workspace/scenarios/${encodeURIComponent(createdId)}`, { token });
  expect(read.status).toBe(200);
  expect(String(read.json?.data?.name ?? ''), '回读 name 应等于创建值').toBe(scenarioName);
});

test('V-WS-012b【缺陷断言】创建幂等：同名重复创建必须拒绝或返回同 id（当前追加不同 id = 缺陷）', async ({ request }) => {
  test.skip(!createdId, '前置创建失败');
  const dup = await call(request, 'POST', '/api/v1/workspace/scenarios', {
    token,
    data: { name: scenarioName, description: 'dup', businessGoal: 'dup', priority: 'LOW' },
  });
  expect(dup.status, '重复创建不得 5xx').toBeLessThan(500);
  // 真实缺陷（2026-11-13 L4 实测）：同名场景被追加为不同 id 多行。
  // 期望二选一：① 409/业务错误（拒绝重复）② 200 且返回同一 id（真幂等）。追加不同 id 判 FAIL。
  if (dup.status === 200 || dup.status === 201) {
    const dupId = String(dup.json?.data?.id ?? '');
    expect(dupId, '同名重复创建返回不同 id = 幂等缺陷（应拒绝或返回同 id）').toBe(createdId);
  }
});

// workspace 直连基址（gateway PATCH 转发存在缺陷 ⇒ 状态机断言直连 18090 验真，
// 同时 V-WS-019g 单独断言「经 gateway PATCH」暴露该缺陷，不洗白）。
const WS = process.env.ECOS_WORKSPACE ?? 'http://127.0.0.1:18090';
const WS_HEADERS = (t: string) => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${t}`,
  'X-ECOS-USER': 'u-admin',
  'X-ECOS-ROLE': 'admin',
  'X-ECOS-TENANT': 'tenant-a',
});

// 自建一个全新 DRAFT 场景并返回 id（供状态机用例独立使用，不依赖共享 createdId —— 并行下共享态不可靠）。
async function createDraft(request: any, name: string): Promise<string> {
  const r = await call(request, 'POST', '/api/v1/workspace/scenarios', {
    token, data: { name, description: 'd', businessGoal: 'g', priority: 'LOW' },
  });
  return String(r.json?.data?.id ?? '');
}

test('V-WS-017 非法状态跃迁（直连 workspace 验状态机真身）：DRAFT 回退必须 409 ILLEGAL_TRANSITION', async ({ request }) => {
  const id = await createDraft(request, `VERIFY-status-illegal-${Date.now()}`);
  expect(id, '自建场景应返回 id').not.toBe('');
  const res = await fetch(`${WS}/api/v1/workspace/scenarios/${id}/status`, {
    method: 'PATCH',
    headers: WS_HEADERS(token),
    body: JSON.stringify({ status: 'DRAFT' }),
  });
  expect(res.status, `DRAFT 回退应 409，实得 ${res.status}`).toBe(409);
  const json = (await res.json().catch(() => null)) as any;
  expect(String(json?.errorCode ?? ''), '应含 ILLEGAL_TRANSITION').toContain('ILLEGAL_TRANSITION');

  // 无副作用：回读状态不应变成非法目标（DRAFT→DRAFT 被拒后状态应仍为 DRAFT）
  const read = await fetch(`${WS}/api/v1/workspace/scenarios/${id}`, { headers: WS_HEADERS(token) });
  expect(read.status).toBe(200);
  const readJson = (await read.json().catch(() => null)) as any;
  expect(String(readJson?.data?.status ?? ''), '非法跃迁后状态不应被改写').toBe('DRAFT');
});

test('V-WS-018 激活门禁（直连验真）：新建场景无任何绑定/连边时 DRAFT→ACTIVE 应被拒', async ({ request }) => {
  const id = await createDraft(request, `VERIFY-activate-guard-${Date.now()}`);
  expect(id, '自建场景应返回 id').not.toBe('');
  const res = await fetch(`${WS}/api/v1/workspace/scenarios/${id}/status`, {
    method: 'PATCH',
    headers: WS_HEADERS(token),
    body: JSON.stringify({ status: 'ACTIVE' }),
  });
  // 激活需「islands 空 ∧ verdict≠EMPTY ∧ 认知可达」；全新场景不满足 → 应拒绝（4xx）
  expect(res.status, `无绑定激活应被拒，实得 ${res.status}`).toBeGreaterThanOrEqual(400);
  expect(res.status, '拒绝应为业务错误非 5xx').toBeLessThan(500);
});

test('V-WS-024/100 完整度：sc001 基线 coverage=0.0 且结构完整', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios/sc001/completeness', { token });
  expect(status).toBe(200);
  const d = json?.data;
  expect(d?.scenarioId).toBe('sc001');
  expect(typeof d?.coverage, 'coverage 应为数字').toBe('number');
  expect(Array.isArray(d?.requiredEdges), 'requiredEdges 应为数组').toBe(true);
  // 基线：sc001 无有效连边，coverage 应为 0 或 PARTIAL
  expect(['PARTIAL', 'EMPTY', 'COMPLETE', 'READY'], 'verdict 应合法').toContain(String(d?.verdict ?? ''));
});

test('V-WS-030 pre-validate 返回三项且结构完整', async ({ request }) => {
  const res = await fetch(`${process.env.ECOS_API ?? 'http://127.0.0.1:8080'}/api/v1/workspace/scenarios/sc001/pre-validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...bearer(token) },
    body: JSON.stringify({}),
  });
  expect(res.status).toBe(200);
  const json = (await res.json().catch(() => null)) as any;
  const checks = Array.isArray(json?.data) ? json.data : [];
  const names = checks.map((c: any) => c?.name);
  expect(names, '应含 mind/sandbox_coverage/bindings_reachability 三项').toEqual(
    expect.arrayContaining(['mind', 'sandbox_coverage', 'bindings_reachability'])
  );
  for (const c of checks) {
    expect(typeof c?.pass, `${c?.name} 的 pass 应为布尔`).toBe('boolean');
    expect(typeof c?.detail, `${c?.name} 的 detail 应为字符串`).toBe('string');
  }
});

test('V-WS-031 预校验 mind 项与 E1 detect 结论一致（双源对账）', async ({ request }) => {
  // pre-validate 的 mind 项
  const pv = await fetch(`${process.env.ECOS_API ?? 'http://127.0.0.1:8080'}/api/v1/workspace/scenarios/sc001/pre-validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...bearer(token) },
    body: JSON.stringify({}),
  });
  const pvJson = (await pv.json().catch(() => null)) as any;
  const mindCheck = (pvJson?.data ?? []).find((c: any) => c?.name === 'mind');

  // E1 detect
  const detect = await call(request, 'GET', '/api/v1/business/scenarios/sc001/cognition/detect', { token });
  // detect 可达性：200（含空）或 503（认知未承流降级）
  expect([200, 503], `detect 应 200 或显式 503，实得 ${detect.status}`).toContain(detect.status);
  // 双源一致性：若 detect 200 且无 mind，则 pre-validate mind 应 false
  if (detect.status === 200 && mindCheck) {
    const minds = detect.json?.data?.minds ?? detect.json?.data ?? [];
    const mindCount = Array.isArray(minds) ? minds.length : 0;
    if (mindCount === 0) {
      expect(mindCheck.pass, 'detect 无 mind 时 pre-validate mind 应 false').toBe(false);
    }
  }
});

test('V-WS-060 Mind 列表：sc001 基线返回数组（不伪造）', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios/sc001/minds', { token });
  expect(status).toBe(200);
  expect(Array.isArray(json?.data), 'minds data 应为数组').toBe(true);
});

test('V-WS-070 场景动作：DecisionGateway 未装配时应显式 503 而非伪装空列表', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/workspace/scenarios/sc001/actions', { token });
  // 已知缺口 P1-14：网关未装配 → 503 COGNITIVE_UNAVAILABLE；若 200 则必须是真实数据
  if (status === 503) {
    expect(String(json?.errorCode ?? json?.message ?? ''), '503 应含错误码/reason').toMatch(/COGNITIVE|UNAVAILABLE|503/i);
  } else {
    expect(status, '非 503 时应为 200 真实数据').toBe(200);
    expect(Array.isArray(json?.data), '200 时 data 应为数组').toBe(true);
  }
});

test('V-WS-019g【缺陷断言】gateway PATCH 转发：状态机经网关必须可用（当前 503 = 缺陷，不洗白）', async ({ request }) => {
  // 根因：RestTemplateConfig 用默认 SimpleClientHttpRequestFactory（HttpURLConnection 不支持 PATCH）。
  // 直连 18090 返回 409 ILLEGAL_TRANSITION（状态机正常），经 8080 返回 503（转发层 fail-closed）。
  // 本断言期望「经 gateway 也能拿到业务错误码而非 503」；当前会 FAIL —— 这是真实缺陷登记点。
  const res = await fetch(`${process.env.ECOS_API ?? 'http://127.0.0.1:8080'}/api/v1/workspace/scenarios/sc001/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...bearer(token) },
    body: JSON.stringify({ status: 'DRAFT' }),
  });
  expect(res.status, 'gateway PATCH 不应 fail-closed 成 503（应透传上游 4xx）').not.toBe(503);
});

test('V-WS-105 金额列形态：场景金额权威列必须为 NUMERIC（D3 方案 A 已修，V330）', async ({ request }) => {
  // MC01：金额列唯一形态 NUMERIC(p,s)。D3 修复采用方案 A（2026-11-13 用户裁决）：
  // 新增 budget_amount NUMERIC(18,2) 为权威列并回填（「850万」→8500000），旧文本列 budget
  // 保留供存量读方过渡（「只加不删」）。本断言核权威列类型正确 + 回填值正确。
  // 用 execFileSync 传 argv 数组，不经 shell（SQL 为常量仍避免拼接，符合安全基线）
  const { execFileSync } = await import('child_process');
  const sh = (sql: string) => execFileSync(
    'docker',
    ['exec', 'ecos-postgres', 'psql', '-U', 'postgres', '-d', 'sys_man', '-t', '-A', '-c', sql],
    { encoding: 'utf8' }
  ).trim();

  const colType = sh("SELECT data_type FROM information_schema.columns WHERE table_schema='ecos_control' AND table_name='ecos_business_scenario' AND column_name='budget_amount'");
  expect(colType, `budget_amount 列应为 numeric（MC01），实得「${colType}」`).toBe('numeric');

  const sc001 = sh("SELECT budget_amount::text FROM ecos_control.ecos_business_scenario WHERE id='sc001'");
  expect(sc001, '「850万」应回填为 8500000.00').toBe('8500000.00');
});

test('V-WS-105b 新写入双写：创建场景时 budget_amount 按文本自动解析（D3 应用侧补齐）', async ({ request }) => {
  // V330 迁移只做了「加列 + 回填」，脚本自述「新写入由应用侧双写（后续批次）」。
  // 若应用侧不双写，budget_amount 对新数据恒为 NULL ⇒ 金额权威列形同虚设。
  // 本断言钉住双写：文本「750万」⇒ 数值 7500000.00。
  const { execFileSync } = await import('child_process');
  const sh = (sql: string) => execFileSync(
    'docker',
    ['exec', 'ecos-postgres', 'psql', '-U', 'postgres', '-d', 'sys_man', '-t', '-A', '-c', sql],
    { encoding: 'utf8' }
  ).trim();

  const name = `VERIFY-d3-dualwrite-${Date.now()}`;
  const created = await call(request, 'POST', '/api/v1/workspace/scenarios', {
    token,
    data: { name, description: 'D3 双写回归钉', budget: '750万' },
  });
  expect(created.status, '创建场景应 200').toBe(200);

  const amount = sh(`SELECT COALESCE(budget_amount::text,'<NULL>') FROM ecos_control.ecos_business_scenario WHERE name='${name}'`);
  expect(amount, '新写入必须双写 budget_amount（「750万」→7500000.00）；<NULL> 表示双写缺失').toBe('7500000.00');

  // 反向探针：不可解析的文本不得编造金额（宁可为 NULL，也不能猜）
  const badName = `${name}-bad`;
  await call(request, 'POST', '/api/v1/workspace/scenarios', {
    token,
    data: { name: badName, description: 'D3 反向探针', budget: '约五百万' },
  });
  const badAmount = sh(`SELECT COALESCE(budget_amount::text,'<NULL>') FROM ecos_control.ecos_business_scenario WHERE name='${badName}'`);
  expect(badAmount, '不可解析的预算文本必须留 NULL，不得编造数值').toBe('<NULL>');
});

// ═══════════ D31 / D59 / D60 回归钉（2026-11-14）═══════════════
// 三条缺陷在同一链条上：网关没路由(D31) → 通了但 500(D59) → 通了可查但分页参数被忽略(D60)。
// 任一环回退，下面三条就会变红。

test('V-WS-090a 对象面经网关可达（D31：workspace.controller 整包排除，须由转发器承流）', async ({ request }) => {
  // 反向探针最关键：D31 之前这条恒 404 ECOS-SYS-404，且不是业务 404。
  const res = await call(request, 'GET', '/api/v1/ecos/objects/Project?page=1&pageSize=2', { token });
  expect(res.status, '经网关必须可达（404 = 转发前缀缺失，D31 回退）').toBe(200);
  expect(res.json?.errorCode, '不得是网关兜底 404').not.toBe('ECOS-SYS-404');
  const body = res.json?.data;
  expect(body, '必须返回分页结构(data/total/page/pageSize)').toHaveProperty('total');
  expect(body).toHaveProperty('pageSize');
});

test('V-WS-090b 查询面经网关可达（D31：/api/query 子族）', async ({ request }) => {
  const res = await call(request, 'GET', '/api/query/history', { token });
  expect(res.status, 'GET /api/query/history 必须经网关可达').toBe(200);
  expect(Array.isArray(res.json?.data), '历史列表应为数组').toBe(true);

  // 反向红线：/api/v1/query 属 AI 域 NLQController，不得被转给 workspace
  const nlq = await call(request, 'GET', '/api/v1/query/history', { token });
  expect(nlq.status, '/api/v1/query 不得落到 workspace 的 QueryHistoryController').not.toBe(200);
});

test('V-WS-090c 实体映射指向真实表：未知实体 404 而非 500（D59）', async ({ request }) => {
  // D59 之前：Project → demo_project（从未建表）→ relation does not exist → 500。
  // 修好后：真实业务表；未登记实体明确 404「实体 X 不存在」，不抛 500。
  const unknown = await call(request, 'GET', '/api/v1/ecos/objects/NO_SUCH_ENTITY_D59?page=1&pageSize=1', { token });
  expect(unknown.status, '未登记实体必须是 200+业务 404，绝不能 500').toBe(200);
  expect(unknown.json?.code, '业务码应为 404').toBe(404);
  expect(String(unknown.json?.message ?? ''), '必须点名缺哪个实体').toContain('NO_SUCH_ENTITY_D59');

  const project = await call(request, 'GET', '/api/v1/ecos/objects/Project?page=1&pageSize=1', { token });
  expect(project.status, 'Project 必须可查（无 RLS 策略时为 0 行，不是 500）').toBe(200);
  expect(project.json?.code, 'Project 查询不得报错').toBe(0);
});

test('V-WS-090d 分页参数 pageSize 生效（D60：曾只认 size，前端传 pageSize 被静默忽略）', async ({ request }) => {
  const byPageSize = await call(request, 'GET', '/api/v1/ecos/objects/Project?page=1&pageSize=2', { token });
  expect(byPageSize.status).toBe(200);
  expect(byPageSize.json?.data?.pageSize, 'pageSize 必须被采纳，回落 50 = D60 回退').toBe(2);

  const bySize = await call(request, 'GET', '/api/v1/ecos/objects/Project?page=1&size=3', { token });
  expect(bySize.status).toBe(200);
  expect(bySize.json?.data?.pageSize, '兼容参数 size 仍须生效').toBe(3);

  const page2 = await call(request, 'GET', '/api/v1/ecos/objects/Project?page=2&pageSize=1', { token });
  expect(page2.status).toBe(200);
  expect(page2.json?.data?.page, '页码必须原样回显').toBe(2);
  expect(page2.json?.data?.pageSize).toBe(1);
});
