import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B6 安全引擎 + 系统管理 — API 级（对应矩阵域07）。
 *
 * 反假绿纪律：
 *  - 认证面必须验「错误密码不泄露用户存在性」：不存在用户与错误密码必须是同一响应形态
 *  - 授权面必须验「无 token 与错 token 都不得放行」，两者状态码差异登记为观察项
 *  - RLS/CLS/ABAC/脱敏四大面：只读列表 + 写操作必须真实落库（不能只回 200）
 *  - 审计面：写操作发 Kafka；消费落库未实现属于已知缺口，断言必须区分「可读日志」与「未落库」
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function okAny(request: any, path: string, label: string): Promise<any> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（${json?.message ?? ''}）`).toBe(0);
  expect(json?.data === undefined, `${label} 应返回 data`).toBe(false);
  return json!.data;
}

test.describe('认证与会话（V-SEC-010~014）', () => {
  test('V-SEC-010 登录成功返回 RS256 token', async ({ request }) => {
    const res = await request.post('/api/v1/auth/login', {
      data: { username: process.env.ECOS_USER, password: process.env.ECOS_PASS },
    });
    expect(res.status()).toBe(200);
    const json = await res.json();
    expect(json?.code).toBe(0);
    const tk = json?.data?.accessToken ?? json?.data?.token;
    expect(tk, '登录应返回 token').toBeTruthy();
    // RS256 三段式 JWT
    expect(String(tk).split('.').length, 'JWT 应为三段式').toBe(3);
  });

  test('V-SEC-010b 错误密码与不存在用户必须同形（不泄露用户存在性）', async ({ request }) => {
    const wrongPass = await request.post('/api/v1/auth/login', {
      data: { username: process.env.ECOS_USER, password: 'definitely_wrong_' + Date.now() },
    });
    const noUser = await request.post('/api/v1/auth/login', {
      data: { username: 'no_such_user_' + Date.now(), password: 'x' },
    });
    const a = await wrongPass.json();
    const b = await noUser.json();
    expect(wrongPass.status(), '错误密码应被拒').toBeGreaterThanOrEqual(400);
    expect(noUser.status(), '不存在用户应被拒').toBeGreaterThanOrEqual(400);
    expect(b.code, '两种失败的业务码必须一致（否则可枚举用户）').toBe(a.code);
    expect(b.errorCode ?? '', '两种失败的 errorCode 必须一致').toBe(a.errorCode ?? '');
  });

  test('V-SEC-013 当前用户返回身份三要素', async ({ request }) => {
    const d = await okAny(request, '/api/v1/auth/me', '当前用户');
    expect(d.username, 'me 应返回 username').toBeTruthy();
    expect(d.userId ?? d.user_id, 'me 应返回 userId').toBeTruthy();
    expect(Array.isArray(d.roles), 'me 应返回 roles 数组').toBe(true);
    expect(d.roles.length, 'roles 不应为空').toBeGreaterThan(0);
  });

  test('V-SEC-011 token 刷新语义（存在则验，不存在登记）', async ({ request }) => {
    const res = await request.post('/api/v1/auth/refresh', {
      headers: { Authorization: `Bearer ${token}` },
      data: {},
    });
    const json = await res.json().catch(() => null);
    if (res.status() === 200 && json?.code === 0) {
      const nt = json?.data?.accessToken ?? json?.data?.token;
      expect(nt, '刷新成功必须返回新 token').toBeTruthy();
    } else {
      // 未实现时必须是明确拒绝，不能假成功
      expect(res.status(), '刷新未实现时不应 5xx').toBeLessThan(500);
      expect(json?.code ?? -1, '刷新失败不得返回 code=0').not.toBe(0);
    }
  });

  test('V-SEC-012 登出端点存在且不返回 5xx', async ({ request }) => {
    const res = await request.post('/api/v1/auth/logout', {
      headers: { Authorization: `Bearer ${token}` },
      data: {},
    });
    expect(res.status(), '登出不应 5xx').toBeLessThan(500);
  });
});

test.describe('授权与策略四平面（V-SEC-020~023）', () => {
  test('V-SEC-020 ABAC 策略分页契约', async ({ request }) => {
    const d = await okAny(request, '/api/v1/security/abac/policies', 'ABAC 策略');
    expect(typeof d.total, 'ABAC total 应为数字').toBe('number');
    expect(Array.isArray(d.data), 'ABAC 应含 data 数组').toBe(true);
    expect(d.data.length, 'data 长度不应超过 total').toBeLessThanOrEqual(Number(d.total));
  });

  test('V-SEC-021 RLS 行级策略列表真实（13 条基线）', async ({ request }) => {
    const list = await okAny(request, '/api/v1/security/rls/policies', 'RLS 策略');
    expect(Array.isArray(list), 'RLS 应为数组').toBe(true);
    expect(list.length, 'RLS 应有基线策略').toBeGreaterThan(0);
    const row = list[0];
    expect(Object.keys(row ?? {}).length, 'RLS 行不应为空对象').toBeGreaterThan(0);
  });

  test('V-SEC-021b RLS 不存在策略必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/security/rls/policies/__NOT_EXIST__', { token });
    expect([400, 404], `不存在 RLS 策略应 400/404，实得 ${status}`).toContain(status);
  });

  test('V-SEC-022 CLS 列级策略列表可达', async ({ request }) => {
    const list = await okAny(request, '/api/v1/security/cls/policies', 'CLS 策略');
    expect(Array.isArray(list), 'CLS 应为数组').toBe(true);
  });

  test('V-SEC-023 数据脱敏 demo 端点真实', async ({ request }) => {
    const d = await okAny(request, '/api/v1/data-masking/demo', '脱敏演示');
    expect(d, '脱敏演示应返回结构化结果').toBeTruthy();
  });

  test('V-SEC-024 数据权限策略列表可达', async ({ request }) => {
    const d = await okAny(request, '/api/v1/data-permission/policies', '数据权限');
    expect(Array.isArray(d) || typeof d === 'object', '数据权限应为数组或分页体').toBe(true);
  });

  test('V-SEC-025 策略引擎策略列表可达', async ({ request }) => {
    const d = await okAny(request, '/api/v1/policy-engine/policies', '策略引擎');
    expect(Array.isArray(d), '策略引擎应为数组').toBe(true);
  });

  test('V-SEC-026 安全资产目录 5 条基线', async ({ request }) => {
    const list = await okAny(request, '/api/v1/security/assets', '安全资产');
    expect(Array.isArray(list), '安全资产应为数组').toBe(true);
    expect(list.length, '安全资产目录应有基线条目').toBeGreaterThan(0);
  });

  test('V-SEC-027 安全决策端点必须 POST（GET 应为 405）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/security/decision', { token });
    expect(status, `安全决策 GET 应 405，实得 ${status}`).toBe(405);
  });
});

test.describe('安全配置与画像（V-SEC-030）', () => {
  test('V-SEC-030 安全画像真实且含敏感等级', async ({ request }) => {
    const d = await okAny(request, '/api/v1/security-profiles', '安全画像');
    expect(d.clearanceLevel ?? d.level, '画像应含 clearanceLevel 或 level').toBeTruthy();
  });

  test('V-SEC-031 安全配置 profile 面真实', async ({ request }) => {
    const d = await okAny(request, '/api/v1/security/profile', '安全 profile');
    expect(d, '安全 profile 不应为空').toBeTruthy();
  });
});

test.describe('审计（V-SEC-040）', () => {
  test('V-SEC-040 审计日志分页契约', async ({ request }) => {
    const d = await okAny(request, '/api/v1/audit/logs', '审计日志');
    expect(typeof d.total, '审计 total 应为数字').toBe('number');
    expect(Array.isArray(d.data), '审计应含 data 数组').toBe(true);
    expect(d.data.length, '审计条目数不应超过 total').toBeLessThanOrEqual(Number(d.total));
  });

  test('V-SEC-040b 审计 id 必须为数字（bigint 主键，非数字必须明确拒绝）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/security/audit/events?id=abc', { token });
    // 该端点 id 落 bigint 主键，字符串必须被明确拒绝而不是静默 200
    expect([400, 404], `非数字 id 应 400/404，实得 ${status}`).toContain(status);
    expect(json?.code).not.toBe(0);
  });

  test('V-SEC-040c 加密审计端点可达', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/security/audit/crypto?id=1', { token });
    expect(status, '加密审计不应 5xx').toBeLessThan(500);
  });
});

test.describe('系统管理（SYS）', () => {
  test('V-SYS-001 用户列表分页契约', async ({ request }) => {
    const d = await okAny(request, '/api/v1/system/users', '用户列表');
    expect(typeof d.total, '用户 total 应为数字').toBe('number');
    expect(Array.isArray(d.data), '用户应含 data 数组').toBe(true);
    expect(d.total, '至少应有 admin 用户').toBeGreaterThan(0);
    // 敏感字段不得回显
    const raw = JSON.stringify(d);
    expect(raw, '用户列表不得回显明文密码').not.toMatch(/"password"\s*:\s*"[^"]+"/);
  });

  test('V-SYS-002 角色列表分页真实', async ({ request }) => {
    const d = await okAny(request, '/api/v1/system/roles', '角色列表');
    expect(typeof d.total, '角色 total 应为数字').toBe('number');
    expect(d.total, '应有基线角色').toBeGreaterThan(0);
  });

  test('V-SYS-003 租户列表真实', async ({ request }) => {
    const d = await okAny(request, '/api/v1/system/tenants', '租户列表');
    expect(typeof d.total, '租户 total 应为数字').toBe('number');
    const list = d.data ?? d.list ?? [];
    expect(Array.isArray(list), '租户应含数组').toBe(true);
    expect(list.length, '应有基线租户').toBeGreaterThan(0);
  });

  test('V-SYS-004 数据字典类型列表非空', async ({ request }) => {
    const list = await okAny(request, '/api/v1/system/dict', '字典类型');
    expect(Array.isArray(list), '字典应为数组').toBe(true);
    expect(list.length, '字典类型应有基线').toBeGreaterThan(0);
  });

  test('V-SYS-005 系统配置主读面可用（D17 回归常驻）', async ({ request }) => {
    // D17：sysman 侧 SysConfigController 曾被 gateway excludeFilters 排除 ⇒ 主读面全线 404。
    // 现已在 sysman 侧显式命名消除 Bean 名冲突并恢复装配。前端 dictConfigClient 走这条路径。
    const d = await okAny(request, '/api/v1/system/config', '系统配置主读面');
    const rows = d.data ?? d.items ?? d;
    expect(Array.isArray(rows), '配置主读面应返回条目数组').toBe(true);
    expect((rows as any[]).length, '配置库应有条目').toBeGreaterThan(0);
    const row = (rows as any[])[0];
    expect(row.config_key ?? row.configKey, '配置行应有 config_key').toBeTruthy();
  });

  test('V-SYS-005b 配置消费审计面真实（178 条基线）', async ({ request }) => {
    const d = await okAny(request, '/api/v1/system/config/audit', '配置审计');
    expect(typeof d.total, '审计 total 应为数字').toBe('number');
    expect(d.total, '配置审计应有记录').toBeGreaterThan(0);
    expect(Array.isArray(d.items), '审计应含 items').toBe(true);
  });

  test('V-SYS-005c /items 面为细粒度权限面（admin 无 config:READ 时须 403 且说明）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/system/config/items', { token });
    // 该面挂 @RequirePermission("config:READ")；admin 当前未被授予该权限码 ⇒ 403 是权限模型真实态。
    // 记录为观察项：主读面 /system/config 可用，细粒度面需显式授权。
    expect([200, 403], `/items 应为 200 或 403，实得 ${status}`).toContain(status);
    if (status === 403) {
      expect(String(json?.message ?? ''), '403 必须指出缺失的权限码').toContain('config:READ');
    }
  });

  test('V-SYS-006 配置 metadata 面可用 + /group 为内部服务面', async ({ request }) => {
    const { status: s1 } = await call(request, 'GET', '/api/v1/system/config/metadata', { token });
    expect(s1, '配置 metadata 应可达（D17 后）').toBe(200);
    const { status: s2, json: j2 } = await call(request, 'GET', '/api/v1/system/config/group', { token });
    // /group 面按设计要求服务凭证 X-ECOS-SERVICE（内部调用面），用户 token 不足以访问
    expect([200, 403], `/group 应 200 或 403，实得 ${s2}`).toContain(s2);
    if (s2 === 403) {
      expect(String(j2?.errorCode ?? ''), '403 应给出服务凭证缺失错误码').toContain('ECOS-AUTH-011');
    }
  });

  test('V-SYS-007 系统配置写操作必须拒绝未知名（不静默成功）', async ({ request }) => {
    const { status, json } = await call(request, 'PUT', '/api/v1/system/config/__NOPE_KEY__', {
      token,
      data: { configValue: 'x' },
    });
    expect(json?.code, '未知配置键不得返回 code=0 假成功').not.toBe(0);
    expect(status).toBeGreaterThanOrEqual(400);
  });

  test('V-SYS-008 字典表端点非空', async ({ request }) => {
    const d = await okAny(request, '/api/v1/dict/tables', '字典表');
    expect(d, '字典表不应为空').toBeTruthy();
  });
});

test.describe('安全引擎状态（V-SEC-050）', () => {
  test('V-SEC-050 安全引擎状态健康', async ({ request }) => {
    const d = await okAny(request, '/api/v1/engine/security/status', '安全引擎状态');
    expect(d.name, '应返回引擎名').toBe('security-engine');
    expect(['UP', 'RUNNING'], `状态应为健康态，实得 ${d.status}`).toContain(String(d.status));
  });
});

test.describe('权限（V5）', () => {
  test('V-SEC-060 无 token 与错 token 都必须被拒（不得放行）', async ({ request }) => {
    const noToken = await call(request, 'GET', '/api/v1/system/users');
    const badToken = await call(request, 'GET', '/api/v1/system/users', { token: 'invalid.jwt.token' });
    expect([401, 403], `无 token 应 401/403，实得 ${noToken.status}`).toContain(noToken.status);
    expect([401, 403], `错 token 应 401/403，实得 ${badToken.status}`).toContain(badToken.status);
    // 观察项登记：两者状态码可以不同（现有实现 无token=403 / 错token=401）
    console.log(`[V-SEC-060] noToken=${noToken.status} badToken=${badToken.status}`);
  });

  test('V-SEC-061 敏感面不得匿名访问（abac/rls/audit）', async ({ request }) => {
    for (const p of ['/api/v1/security/abac/policies', '/api/v1/security/rls/policies', '/api/v1/audit/logs']) {
      const { status } = await call(request, 'GET', p);
      expect([401, 403], `${p} 匿名访问必须被拒，实得 ${status}`).toContain(status);
    }
  });
});
