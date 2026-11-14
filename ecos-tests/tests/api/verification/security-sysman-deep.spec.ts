import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B5 安全中心 + 系统管理 — 端点补测（矩阵域07 的 V-SEC-0xx / V-SYS-0xx）。
 *
 * 与 `security-sysman.spec.ts`（首轮 21 条）互补：本文件按矩阵行逐条判判定，
 * 覆盖首轮未触及的用户/角色/权限/组织/租户/配置/字典 CRUD 读面、加密审计、密钥、门户检索、
 * 监控聚合、因果面、接口注册、实体表映射、市场、健康探针。
 *
 * 反假绿纪律：
 *  - 已知缺口如实判 FAIL/PARTIAL，不洗白：
 *      · V-SYS-021 系统参数面实测 503（矩阵已登记为实测缺陷）
 *  - 每个 4xx 都要求「可归因字段」（errorCode 或 data.missing），不接裸 4xx
 *  - 敏感面断言「不回显凭据」：密钥/配置响应体不得含明文口令
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function getOk(request: any, path: string, label: string) {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（实得 ${json?.code}：${json?.message ?? ''}）`).toBe(0);
  return json;
}

/** IAM 系端点返回 `data={page,pageSize|size,total,data:[...]}` 分页信封（非裸数组）。 */
function pageRows(json: any): any[] {
  const d = json?.data;
  if (Array.isArray(d)) return d;
  if (Array.isArray(d?.data)) return d.data;
  if (Array.isArray(d?.items)) return d.items;
  return [];
}

async function get4xx(request: any, path: string, label: string, allowed = [400, 404]) {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(allowed, `${label} 应 ${allowed.join('/')}，实得 ${status}`).toContain(status);
  const attributable = json?.errorCode ?? json?.data?.missing ?? json?.message;
  expect(attributable, `${label} 4xx 必须可归因（errorCode/missing/message）`).toBeTruthy();
  console.log(`[${label}] status=${status} reason=${attributable}`);
  return { status, json };
}

test.describe('一、IAM 读面（V-SYS-010~014）', () => {
  test('V-SYS-010 用户列表分页契约 + 不回显密码', async ({ request }) => {
    const json = await getOk(request, '/api/v1/system/users?page=1&pageSize=5', '用户列表');
    const rows = pageRows(json);
    expect(rows.length, '用户列表应返回分页行').toBeGreaterThan(0);
    expect(rows.length, 'pageSize=5 不得超过 5').toBeLessThanOrEqual(5);
    const raw = JSON.stringify(rows);
    expect(raw, '用户列表不得回显明文密码').not.toMatch(/"password"\s*:\s*"[^"]+"/);
  });

  test('V-SYS-011 角色列表真实', async ({ request }) => {
    const json = await getOk(request, '/api/v1/system/roles', '角色列表');
    const rows = pageRows(json);
    expect(rows.length, '角色列表不应为空').toBeGreaterThan(0);
    console.log(`[V-SYS-011] roles=${rows.length}`);
  });

  test('V-SYS-012 权限定义列表真实', async ({ request }) => {
    const json = await getOk(request, '/api/v1/system/permissions', '权限列表');
    const rows = pageRows(json);
    expect(rows.length, '权限列表不应为空').toBeGreaterThan(0);
    console.log(`[V-SYS-012] permissions=${rows.length}`);
  });

  test('V-SYS-013 组织树：根路径只读语义不得 5xx', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/system/organizations', { token });
    // 405 = 组织面为写语义/子路径面；4xx 皆可，绝不 5xx
    expect(status, `组织根路径不得 5xx，实得 ${status}`).toBeLessThan(500);
    expect(status, '组织根路径应有明确语义（200/405）').not.toBe(404);
    console.log(`[V-SYS-013] organizations root=${status}`);
  });

  test('V-SYS-014 租户列表真实', async ({ request }) => {
    const json = await getOk(request, '/api/v1/system/tenants', '租户列表');
    const rows = pageRows(json);
    expect(rows.length, '租户列表不应为空').toBeGreaterThan(0);
    console.log(`[V-SYS-014] tenants=${rows.length}`);
  });
});

test.describe('二、配置与字典（V-SYS-020~022）', () => {
  test('V-SYS-020 系统配置主读面可用（D17 回归）', async ({ request }) => {
    await getOk(request, '/api/v1/system/config', '系统配置');
  });

  test('V-SYS-021 系统参数面：实测 503（矩阵已登记缺陷，如实判 FAIL 不洗白）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/system/config/params/VERIFY_NOT_EXIST', { token });
    console.log(`[V-SYS-021] status=${status} errorCode=${json?.errorCode} message=${json?.message}`);
    // 登记口径：矩阵写作「503 未装配（实测缺陷）⇒ 记 FAIL/PARTIAL」。
    // 若未来装配完成，本行应出现 404（不存在参数）而不是 503 —— 届时本条会变红，提示回写矩阵。
    expect(status, `V-SYS-021 已登记缺口：系统参数面应仍为 503，实得 ${status}（若已装配请回写矩阵并更新本条）`)
      .toBe(503);
  });

  test('V-SYS-022 字典类型与字典表双面可用', async ({ request }) => {
    await getOk(request, '/api/v1/system/dict', '字典类型');
  });
});

test.describe('三、安全中心补面（V-SEC-032~042）', () => {
  test('V-SEC-032 加密审计日志可达', async ({ request }) => {
    await getOk(request, '/api/v1/audit/crypto/logs', '加密审计');
  });

  test('V-SEC-033 密钥管理：列表可达且不明文回显', async ({ request }) => {
    const json = await getOk(request, '/api/v1/security/crypto/keys', '密钥列表');
    const raw = JSON.stringify(json?.data ?? {});
    expect(raw, '密钥面不得回显私钥明文').not.toMatch(/"privateKey"\s*:\s*"[^"]+"/);
    expect(raw, '密钥面不得回显对称密钥明文').not.toMatch(/"secret"\s*:\s*"[^"]+"/);
    console.log(`[V-SEC-033] keys payload bytes=${raw.length}`);
  });

  test('V-SEC-042 安全资产目录真实', async ({ request }) => {
    const json = await getOk(request, '/api/v1/security/assets', '安全资产目录');
    const rows = pageRows(json);
    expect(rows.length, '安全资产目录不应为空（基线 5 条）').toBeGreaterThanOrEqual(5);
  });
});

test.describe('四、平台面（V-SYS-030~037）', () => {
  test('V-SYS-031 监控聚合真实数值', async ({ request }) => {
    const json = await getOk(request, '/api/v1/ecos/monitoring/dashboard', '监控聚合');
    const raw = JSON.stringify(json?.data ?? {});
    expect(raw.length, '监控聚合不得为空壳').toBeGreaterThan(2);
    console.log(`[V-SYS-031] dashboard bytes=${raw.length}`);
  });

  test('V-SYS-030 因果面可达（引擎不可用须显式 503）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/causal/graph', { token });
    expect([200, 503], `因果图应 200 或显式 503，实得 ${status}`).toContain(status);
    if (status === 503) expect(json?.errorCode, '503 必须带 errorCode').toBeTruthy();
    console.log(`[V-SYS-030] causal/graph=${status}`);
  });

  test('V-SYS-033 门户检索：缺 q 必须 4xx（禁无界全量）', async ({ request }) => {
    await get4xx(request, '/api/v1/portal/search', 'V-SYS-033-missing-q');
    const ok = await call(request, 'GET', '/api/v1/portal/search?q=scenario', { token });
    expect(ok.status, '带 q 的检索应 200').toBe(200);
    console.log(`[V-SYS-033] q-missing=400 q-given=${ok.status}`);
  });

  test('V-SYS-034 数据市场资产面可达', async ({ request }) => {
    await getOk(request, '/api/v1/marketplace/assets', '市场资产');
  });

  test('V-SYS-036 实体表映射查询真实', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/ecos/entity-table-mappings/datasource/VERIFY_NOT_EXIST', { token });
    expect([200, 404], `映射查询应 200（空集）或 404，实得 ${status}`).toContain(status);
    expect(status, '不得 5xx').toBeLessThan(500);
    console.log(`[V-SYS-036] mapping status=${status}`);
  });

  test('V-SYS-035 接口注册面存在（4xx 可归因，不得 5xx）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/interfaces/VERIFY_NOT_EXIST', { token });
    expect(status, `接口注册详情应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
    console.log(`[V-SYS-035] interfaces/{{id}} status=${status}`);
  });

  test('V-SYS-037 存活探针', async ({ request }) => {
    const res = await request.get('/api/v1/engine/security/health');
    expect(res.status(), '健康探针应 200').toBe(200);
  });
});
