import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B5/B6·控制面板 — 剩余后端面补测（矩阵域08，2026-11-13 域06 闭合轮的延续）。
 *
 * 覆盖四组「表面是 UI Tab、实则有可验后端面」的条目：
 *  - V-CTRL-SEC-002 检测 Tab 的四个管理器后端（RLS / CLS / 脱敏 / ABAC）
 *  - V-CTRL-SEC-005 审计查询（真实行 + 分页）
 *  - V-CTRL-MON-003 数字孪生 / V-CTRL-MON-005 引擎配置族根
 *  - V-CTRL-GOV-002 引擎配置读面 / V-CTRL-GOV-003 诊断面
 *  - V-CTRL-D-003 / D-004 任务与告警（DB 对账留 L4 批）
 *
 * 反假绿纪律：
 *  - 脱敏的**真实前缀是 `/api/v1/security/mask`**（不是 `/masking/rules`——那个 404）；
 *    本文件对两个前缀都断言，防止将来路径漂移后只剩一条「看着绿」的腿。
 *  - 引擎配置只判**有实际配置键**的面；security/knowledge/cognitive/ai 四引擎
 *    实测**无 settings 端点**（404），故本文件不为它们编造断言，而是显式登记该事实。
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

async function get(request: any, path: string) {
  return call(request, 'GET', path, { token });
}

test.describe('V-CTRL-SEC-002 检测 Tab：四管理器后端', () => {
  test('RLS 策略面返回真实行（含 filter 表达式）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/security/rls/policies');
    expect(status, 'RLS 策略面应 200').toBe(200);
    expect(json?.code).toBe(0);
    const rows = json!.data as any[];
    expect(Array.isArray(rows), 'RLS 应为数组').toBe(true);
    // 实测有种子行（rls_forecast_run_admin）；空集则说明策略表读不到，不算通过
    expect(rows.length, 'RLS 策略必须有种子行（空集说明读不到 policy 表）').toBeGreaterThan(0);
    const first = rows[0];
    expect(first.policy_name ?? first.policyName, 'RLS 行必须带策略名').toBeTruthy();
    expect(first.table_name ?? first.tableName, 'RLS 行必须带表名').toBeTruthy();
  });

  test('CLS 列级安全策略面可达（允许空集，但形态必须正确）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/security/cls/policies');
    expect(status, 'CLS 策略面应 200').toBe(200);
    expect(json?.code).toBe(0);
    expect(Array.isArray(json!.data), 'CLS 应为数组').toBe(true);
  });

  test('脱敏：demo 返回真实脱敏样本（双前缀 `/mask` 与 `/masking` 等价）', async ({ request }) => {
    for (const path of ['/api/v1/security/mask/demo', '/api/v1/security/masking/demo']) {
      const { status, json } = await get(request, path);
      expect(status, `${path} 应 200`).toBe(200);
      const samples = json!.data?.samples as any[];
      expect(Array.isArray(samples) && samples.length > 0,
        `${path} 必须返回脱敏样本（空集说明规则未加载）`).toBeTruthy();
      const emailSample = samples.find((s) => s.rule === 'email');
      expect(emailSample, '样本必须含 email 规则（脱敏规则已加载的证据）').toBeTruthy();
      expect(String(emailSample.masked), '脱敏结果必须与原文不同（真脱敏而非回声）')
        .not.toBe(String(emailSample.input));
      expect(String(emailSample.masked), '脱敏后必须保留掩码字符').toContain('*');
    }
  });

  test('ABAC 策略面分页形态正确', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/security/abac/policies');
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
    const page = json!.data;
    expect(Array.isArray(page.data), 'ABAC 分页 data 应为数组').toBe(true);
    expect(Number(page.total), 'total 必须为数值').toBeGreaterThanOrEqual(0);
  });

  test('CLS 列查询：缺 tableName 必须 400（禁无界全量）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/security/cls/columns');
    expect(status, '缺必填参数必须 400').toBe(400);
    expect(String(json?.message ?? ''), '拒绝原因须点名 tableName').toContain('tableName');
  });
});

test.describe('V-CTRL-SEC-005 审计查询：真实行 + 分页', () => {
  test('审计日志返回真实行且带事件字段', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/security/audit/logs?page=1&pageSize=5');
    expect(status, '审计日志应 200').toBe(200);
    expect(json?.code).toBe(0);
    const page = json!.data;
    const rows = (page.data ?? page.rows ?? page.items ?? []) as any[];
    expect(Array.isArray(rows), '审计行应为数组').toBe(true);
    expect(rows.length, '审计表必须有行（登录/权限事件会写入）').toBeGreaterThan(0);
    const first = rows[0];
    expect(first.eventId ?? first.event_id, '审计行必须带 eventId').toBeTruthy();
    expect(first.eventType ?? first.event_type, '审计行必须带 eventType').toBeTruthy();
    expect(first.timestamp, '审计行必须带时间戳').toBeTruthy();
  });

  test('分页参数生效：pageSize 变化必须反映在返回行数上', async ({ request }) => {
    const one = await get(request, '/api/v1/security/audit/logs?page=1&pageSize=1');
    const five = await get(request, '/api/v1/security/audit/logs?page=1&pageSize=5');
    const rowsOf = (j: any) => (j?.data?.data ?? j?.data?.rows ?? j?.data?.items ?? []) as any[];
    expect(rowsOf(one.json).length, 'pageSize=1 时必须只返回 1 行').toBeLessThanOrEqual(1);
    expect(rowsOf(five.json).length, 'pageSize=5 时不得少于 1 行').toBeGreaterThanOrEqual(1);
  });
});

test.describe('V-CTRL-MON-005 / GOV-002 引擎配置读面', () => {
  test('data 引擎配置族根返回真实分组（settings / groups / defaults 三腿）', async ({ request }) => {
    const settings = await get(request, '/api/v1/engine/data/settings');
    expect(settings.status, 'data settings 应 200').toBe(200);
    const keys = Object.keys(settings.json!.data ?? {});
    expect(keys.length, 'data settings 必须有分组（空对象说明读不到配置）').toBeGreaterThan(0);
    expect(keys, '必须含 pipeline 分组（实测 7 组：pipeline/quality/lineage/general/execution/lake/storage）')
      .toContain('pipeline');

    const groups = await get(request, '/api/v1/engine/data/settings/groups');
    expect(groups.status, 'settings/groups 应 200').toBe(200);
    expect(Array.isArray(groups.json!.data?.segments), 'groups 必须返回 segments 数组').toBe(true);
    expect((groups.json!.data.segments as any[]).length).toBeGreaterThan(0);

    const defaults = await get(request, '/api/v1/engine/data/settings/defaults');
    expect(defaults.status, 'settings/defaults 应 200').toBe(200);
    expect(Object.keys(defaults.json!.data ?? {}).length, 'defaults 必须非空').toBeGreaterThan(0);
  });

  test('ontology 引擎配置读面返回真实配置行', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/engine/ontology/settings');
    expect(status, 'ontology settings 应 200').toBe(200);
    const rows = json!.data as any[];
    expect(Array.isArray(rows) && rows.length > 0,
      'ontology 配置必须有行（如 ontology.graph.backend）').toBeTruthy();
    expect(rows[0].configKey ?? rows[0].config_key, '配置行必须带 configKey').toBeTruthy();
  });

  test('登记事实：security/knowledge/cognitive/ai 四引擎无 settings 端点（404 是当前真实形态）', async ({ request }) => {
    // 这条不是"功能通过"，而是**钉住已知形态**：矩阵 GOV-002 写的是"各引擎配置读写一致"，
    // 但实测只有 data + ontology 两个引擎有 settings 面。若将来补上了，本用例会红，
    // 强制把该矩阵条目改判并补真实断言——避免"四个引擎都一致"被默认当真。
    for (const engine of ['security', 'knowledge', 'cognitive', 'ai']) {
      const { status } = await get(request, `/api/v1/engine/${engine}/settings`);
      expect(status, `${engine} settings 当前实测 404；若已实现请更新本条与矩阵 GOV-002`).toBe(404);
    }
  });
});

test.describe('V-CTRL-MON-003 / GOV-003 孪生与诊断', () => {
  test('数字孪生健康面返回设备数与组件状态', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/twins/health');
    expect(status, 'twins/health 应 200').toBe(200);
    const d = json!.data;
    expect(typeof d.device_count, 'device_count 必须为数值').toBe('number');
    expect(d.twin_version, 'twin_version 必须存在（版本可追溯）').toBeTruthy();
    expect(d.mqtt, 'mqtt 组件状态必填').toBeTruthy();
    expect(typeof d.mqtt.status, 'mqtt.status 必须为字符串').toBe('string');
  });

  test('网关诊断面列出全部制品与端口', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/system/gateway-diagnostics');
    expect(status, 'gateway-diagnostics 应 200').toBe(200);
    const artifacts = json!.data?.artifacts as any[];
    expect(Array.isArray(artifacts) && artifacts.length > 0, '诊断必须列制品').toBeTruthy();
    const gw = artifacts.find((a) => a.name === 'gateway');
    expect(gw, '诊断清单必须含 gateway 自身').toBeTruthy();
    expect(gw.port, 'gateway 端口必须是 8080').toBe(8080);
    expect(gw.mode, 'gateway 必须是 monolith 形态（本部署事实）').toBe('monolith');
  });
});

test.describe('V-CTRL-D-003 / D-004 任务与告警（API 面；库对账留 L4）', () => {
  test('任务列表返回真实行且字段齐全', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/task/list?page=1&pageSize=5');
    expect(status, 'task/list 应 200').toBe(200);
    const page = json!.data;
    const items = (page.items ?? page.data ?? []) as any[];
    expect(Array.isArray(items), '任务行应为数组').toBe(true);
    expect(items.length, '任务表必须有行（实测有 pipeline-debug-session-cleanup）').toBeGreaterThan(0);
    expect(items[0].taskId ?? items[0].task_id, '任务行必须带 taskId').toBeTruthy();
    expect(items[0].taskName ?? items[0].task_name, '任务行必须带 taskName').toBeTruthy();
  });

  test('告警列表分页形态正确（空集允许，但必须自报 total）', async ({ request }) => {
    const { status, json } = await get(request, '/api/v1/monitor/alerts?page=1&pageSize=5');
    expect(status, 'monitor/alerts 应 200').toBe(200);
    const page = json!.data;
    const rows = (page.rows ?? page.data ?? []) as any[];
    expect(Array.isArray(rows), '告警行应为数组').toBe(true);
    expect(Number(page.total), 'total 必须为数值（空集时 total 应为 0 而非缺失）')
      .toBeGreaterThanOrEqual(0);
    if (Number(page.total) === 0) {
      expect(rows.length, 'total=0 时 rows 必须为空（不得自相矛盾）').toBe(0);
    }
  });
});
