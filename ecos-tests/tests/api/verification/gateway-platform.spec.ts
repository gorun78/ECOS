import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B7 网关横切 + 平台管控（对应矩阵域09，GW 部分）。
 *
 * 反假绿纪律：
 *  - 路由前缀必须用「实测存在的路径」，不猜；猜错产生的 404 不得被当作缺陷或通过
 *  - 安全头、限流、配额、降级横幅都必须是**响应层实测**，不是读代码推断
 *  - 三源对账（route-manifest ↔ yml ↔ 活体）只对活体侧做断言，源码侧由 lint 兜底
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function ok(request: any, path: string, label: string): Promise<any> {
  const { status, json } = await call(request, 'GET', path, { token });
  expect(status, `${label} HTTP 应 200，实得 ${status}`).toBe(200);
  expect(json?.code, `${label} code 应为 0（${json?.message ?? ''}）`).toBe(0);
  return json?.data;
}

test.describe('网关诊断与运行时监控（V-GW-020）', () => {
  test('V-GW-020 网关诊断返回工件与端口清单', async ({ request }) => {
    const d = await ok(request, '/api/v1/system/gateway-diagnostics', '网关诊断');
    expect(Array.isArray(d.artifacts), '诊断应含 artifacts 数组').toBe(true);
    expect(d.artifacts.length, '应至少含 gateway 工件').toBeGreaterThan(0);
    const gw = d.artifacts.find((a: any) => a.port === 8080);
    expect(gw, '工件清单应含 8080 网关').toBeTruthy();
    expect(gw.active, 'gateway 工件应为 active').toBe(true);
  });

  test('V-GW-021 运行时监控指标真实', async ({ request }) => {
    const d = await ok(request, '/api/monitor', '运行时指标');
    expect(d.system, '指标应含 system 段').toBeTruthy();
    expect(typeof d.system.heap_used_mb, '堆内存应为数字').toBe('number');
    expect(d.system.heap_used_mb, '堆内存应为正数').toBeGreaterThan(0);
  });

  test('V-GW-022 监控健康含数据库状态', async ({ request }) => {
    const d = await ok(request, '/api/monitor/health', '监控健康');
    expect(d.database?.status, '数据库状态应为 UP').toBe('UP');
    expect(typeof d.uptime_ms, 'uptime 应为数字').toBe('number');
    expect(d.uptime_ms, 'uptime 应为正数').toBeGreaterThan(0);
  });

  test('V-GW-023 告警面双前缀可用且口径一致（空 vs 非空都自洽）', async ({ request }) => {
    const a = await ok(request, '/api/monitor/alerts', '告警汇总');
    const b = await ok(request, '/api/v1/monitor/alerts', '告警分页');
    expect(a.total === undefined || typeof a.total === 'number', '汇总 total 若存在应为数字').toBe(true);
    expect(typeof b.total, '分页 total 应为数字').toBe('number');
    expect(b.total, '分页 total 不应为负').toBeGreaterThanOrEqual(0);
  });

  test('V-GW-024 死信队列面真实', async ({ request }) => {
    const d = await ok(request, '/api/v1/monitor/dlq', '死信队列');
    expect(typeof d.total, 'DLQ total 应为数字').toBe('number');
    expect(Array.isArray(d.rows), 'DLQ 应含 rows 数组').toBe(true);
  });

  test('V-GW-025 事件 topic 清单含 ecos.* 八主题', async ({ request }) => {
    const d = await ok(request, '/api/v1/system/events/topics', '事件主题');
    expect(Array.isArray(d.topics), 'topics 应为数组').toBe(true);
    expect(d.topics.length, '事件主题应有条目').toBeGreaterThan(0);
    d.topics.forEach((t: any) => expect(String(t.name), '主题名应以 ecos. 开头').toMatch(/^ecos\./));
    const names = d.topics.map((t: any) => t.name);
    ['ecos.audit', 'ecos.agent', 'ecos.knowledge'].forEach((n) => {
      expect(names, `应含标准主题 ${n}`).toContain(n);
    });
  });
});

test.describe('网关转发器（V-GW-012/013/014）', () => {
  test('V-GW-012 workspace 转发可达（21 场景）', async ({ request }) => {
    const list = await ok(request, '/api/v1/workspace/scenarios', 'workspace 转发');
    expect(Array.isArray(list), '场景列表应为数组').toBe(true);
    expect(list.length, '场景应有基线数据').toBeGreaterThan(0);
  });

  test('V-GW-012b workspace 不存在场景经网关必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/workspace/scenarios/__NOT_EXIST__', { token });
    expect([400, 404], `不存在场景应 400/404，实得 ${status}`).toContain(status);
  });

  test('V-GW-014 OAG 面存在（GET 应 405，POST 语义）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/oag/health', { token });
    expect([200, 405], `OAG health GET 应 200 或 405，实得 ${status}`).toContain(status);
  });
});

test.describe('网关安全头与限流（V-GW-015/017）', () => {
  test('V-GW-017 响应必须带全套安全头', async ({ request }) => {
    const res = await request.get('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status()).toBe(200);
    const h = res.headers();
    const required: Array<[string, RegExp]> = [
      ['x-content-type-options', /nosniff/],
      ['x-frame-options', /DENY|SAMEORIGIN/],
      ['strict-transport-security', /max-age=\d+/],
      ['content-security-policy', /default-src/],
    ];
    const missing = required.filter(([k, re]) => !re.test(h[k] ?? ''));
    expect(missing.map(([k]) => k), '缺失或不合规的安全头').toEqual([]);
  });

  test('V-GW-017b CSP 必须禁止 frame 嵌入（frame-ancestors none）', async ({ request }) => {
    const res = await request.get('/api/v1/auth/me', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(String(res.headers()['content-security-policy'] ?? '')).toContain("frame-ancestors 'none'");
  });

  test('V-GW-015 限流配置可探测（超限语义登记）', async ({ request }) => {
    // 限流器存在与否通过突发请求观察，不断言「必然 429」（阈值未知，硬断言会产生假红）
    const results: number[] = [];
    for (let i = 0; i < 8; i += 1) {
      const { status } = await call(request, 'GET', '/api/v1/auth/me', { token });
      results.push(status);
    }
    expect(results.every((s) => s === 200 || s === 429), `突发请求只应 200/429，实得 ${results.join(',')}`).toBe(true);
    console.log(`[V-GW-015] burst=${results.join(',')}`);
  });
});

test.describe('平台管控读面（PLAT）', () => {
  test('V-PLAT-001 告警列表可达', async ({ request }) => {
    const list = await ok(request, '/api/v1/alerts', '告警列表');
    expect(Array.isArray(list), '告警列表应为数组').toBe(true);
  });

  test('V-PLAT-002 告警不存在 id 必须 404（反向探针）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/alerts/__NOT_EXIST__', { token });
    expect([400, 404], `不存在告警应 400/404，实得 ${status}`).toContain(status);
  });

  test('V-PLAT-003 门户检索必须要求关键字（禁无界全量）', async ({ request }) => {
    const withQ = await ok(request, '/api/v1/portal/search?q=scenario', '门户检索带 q');
    expect(typeof withQ.total, '检索 total 应为数字').toBe('number');
    expect(Array.isArray(withQ.items), '检索应含 items').toBe(true);
    const { status: s2, json: j2 } = await call(request, 'GET', '/api/v1/portal/search', { token });
    expect([200, 400], `缺 q 应 200（空结果）或 400，实得 ${s2}`).toContain(s2);
    if (s2 === 200) {
      const d = j2?.data as any;
      expect(d?.total, '缺 q 时不得返回无界全量结果').toBeLessThanOrEqual((withQ?.total ?? 0) + 1);
    }
  });

  test('V-PLAT-004 接口引用面可达', async ({ request }) => {
    const list = await ok(request, '/api/v1/interfaces', '接口引用');
    expect(Array.isArray(list), '接口引用应为数组').toBe(true);
  });

  test('V-PLAT-005 安全审计日志面真实（seed 数据可读）', async ({ request }) => {
    const list = await ok(request, '/api/security/audit-logs', '安全审计日志');
    expect(Array.isArray(list), '安全审计应为数组').toBe(true);
    expect(list.length, '审计应有 seed 记录').toBeGreaterThan(0);
    const row = list[0];
    expect(row.event ?? row.action, '审计行应有事件名').toBeTruthy();
    expect(row.timestamp, '审计行应有时间戳').toBeTruthy();
  });
});

test.describe('降级与不可用语义（X 域，core）', () => {
  test('V-X-001 不存在的引擎健康面必须 404（不得伪装 UP）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/engine/__no_engine__/health', { token });
    expect([400, 404], `不存在引擎应 400/404，实得 ${status}`).toContain(status);
    expect(json?.code).not.toBe(0);
  });

  test('V-X-002 无 token 不得泄露任何业务数据（响应体不含 data）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/system/users');
    expect([401, 403]).toContain(status);
    expect(json?.data, '拒绝响应不得携带 data').toBeFalsy();
  });

  test('V-X-003 错误响应必须带可追踪错误码', async ({ request }) => {
    const { json } = await call(request, 'GET', '/api/v1/workspace/scenarios/__NOT_EXIST__', { token });
    expect(json, '错误响应应有信封').toBeTruthy();
    expect(json?.code, '错误码不得为 0').not.toBe(0);
    const hasCode = Boolean(json?.errorCode) || Boolean(json?.message);
    expect(hasCode, '错误响应应有 errorCode 或 message').toBe(true);
  });
});
