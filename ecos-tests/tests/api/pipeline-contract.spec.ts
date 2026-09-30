import { expect, test } from '@playwright/test';
import { ApiResponse, call, creds, loginAsAdmin } from '../helpers/ecos';

/**
 * 迁移自 ecos-tests/data-workbench-pipeline-smoke.mjs 的 API 面（断言 A–I 逐条对应，语义未削弱）。
 *
 * 前置：
 *   1. gateway :8080 已启动；
 *   2. E 步骤要求 security-engine(:18081) + OPA 策略就位 —— OPA 无策略时后端返回
 *      `SECURITY_ENGINE_UNAVAILABLE`，按 `pipeline-ABAC-semantic-note.md` 既有口径**判失败**（不放宽为
 *      "DENY 即通过"），因为 fail-closed 只是兜底，策略缺位本身是要暴露的环境/实现缺口。
 *   3. 凭据只从 env 读取（见 .env.example），用例内不写死账号口令。
 */

// 节点类型全集 = 前端基础 5 + PMO-36 新增 4 + 数据采集/近源读取/文档解析 3（B6-1/B6-2 + 近源库）
// 单源基准 = PipelineNodeTypesCatalog.java:19~28（SUPPORTED 集）与
//            PipelineControllerTest.java:187 `assertEquals(12, ...SUPPORTED.size())`（铁律 §4.8.2 目录↔枚举同源契约）
const NODE_TYPE_COUNT = 12;
const EXPECTED_NODE_TYPES = [
  'SOURCE_JDBC', 'SOURCE_CSV', 'SOURCE_REST', 'SOURCE_CDC', 'SOURCE_MINIO',
  'TRANSFORM_SQL', 'TRANSFORM_UDF', 'TRANSFORM_DOC_PARSE', 'JOIN', 'SINK',
  'SINK_MINIO', 'OUTPUT_OBJECT',
];
// 前端画布必须可用的基础 5 类（禁用即属实现缺口）
const ALWAYS_ENABLED_TYPES = ['SOURCE_JDBC', 'SOURCE_CSV', 'SOURCE_REST', 'TRANSFORM_SQL', 'OUTPUT_OBJECT'];

const TERMINAL_STATUSES = ['COMPLETED', 'SUCCESS', 'FAILED', 'CANCELLED', 'WRONG', 'DONE'];

function smokeDefinition() {
  return {
    name: `e2e-${Date.now()}`,
    description: 'Playwright 工程用例：source + transform + sink',
    status: 'ACTIVE',
    nodes: [
      { id: 'n-src', nodeId: 'n-src', type: 'SOURCE_CSV', config: { filePath: '/tmp/sample.csv', interfaceType: 'csv', header: true } },
      { id: 'n-tr', nodeId: 'n-tr', type: 'TRANSFORM_SQL', config: { sql: 'SELECT 1 AS one' } },
      { id: 'n-out', nodeId: 'n-out', type: 'OUTPUT_OBJECT', config: { targetTable: 'e2e_target', mode: 'APPEND', rows: [{ one: 1 }] } },
    ],
    edges: [
      { from: 'n-src', to: 'n-tr' },
      { from: 'n-tr', to: 'n-out' },
    ],
  };
}

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test('A 登录签发 bearer token', async ({ request }) => {
  const res = await request.post('/api/v1/auth/login', { data: creds() });
  expect(res.status()).toBe(200);
  const json = (await res.json()) as ApiResponse;
  expect(String(json?.data?.token ?? json?.data?.accessToken ?? json?.token ?? ''), '登录未返回 token').not.toBe('');
});

test('B node-types 返回 12 类全集，且目录↔枚举同源、禁用项带 i18n 原因键', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/pipeline/node-types', { token });
  expect(status).toBe(200);
  expect(json?.success).toBe(true);
  const items = json?.data?.items ?? [];
  expect(items.length, `node-types 数量=${items.length}`).toBe(NODE_TYPE_COUNT);

  const types = items.map((i: { type: string }) => i.type).sort();
  expect(types, '节点类型集与 catalog 单源不一致').toEqual([...EXPECTED_NODE_TYPES].sort());

  for (const t of ALWAYS_ENABLED_TYPES) {
    const item = items.find((i: { type: string }) => i.type === t);
    expect(item?.enabled, `基础类型 ${t} 不可用`).toBe(true);
  }

  // 禁用项必须给出 i18n 原因键（前端规范 §六：禁硬编码文案），例如 standard 档的 SOURCE_CDC
  for (const item of items as { type: string; enabled: boolean; disabledReasonKey?: string }[]) {
    if (item.enabled === false) {
      expect(String(item.disabledReasonKey ?? ''), `${item.type} 禁用但未给 disabledReasonKey`).not.toBe('');
    }
  }
});

test('C–H 管道定义生命周期（创建→详情→执行→轮询→列表→逻辑删除→已删不可见）', async ({ request }) => {
  let definitionId = '';
  let taskId = '';

  await test.step('C POST /pipeline/definitions → 200 + id', async () => {
    const { status, json } = await call(request, 'POST', '/api/v1/pipeline/definitions', { token, data: smokeDefinition() });
    expect(status).toBe(200);
    expect(json?.success).toBe(true);
    definitionId = String(json?.data?.id ?? '');
    expect(definitionId, '创建未返回 id').not.toBe('');
  });

  await test.step('D GET /definitions/{id} → 节点全量回显（3 节点）', async () => {
    const { status, json } = await call(request, 'GET', `/api/v1/pipeline/definitions/${definitionId}`, { token });
    expect(status).toBe(200);
    expect(json?.success).toBe(true);
    const nodes = json?.data?.nodes ?? [];
    expect(nodes.length, '详情必须回显全部节点（列表/详情配对）').toBe(3);
    expect(nodes.map((n: { type: string }) => n.type)).toEqual(['SOURCE_CSV', 'TRANSFORM_SQL', 'OUTPUT_OBJECT']);
  });

  await test.step('E POST /definitions/{id}/execute → 放行或按策略 DENY', async () => {
    const res = await call(request, 'POST', `/api/v1/pipeline/definitions/${definitionId}/execute`, { token });
    const msg = String(res.json?.message ?? '');
    if (res.json?.code === 403) {
      // ABAC：OPA 策略命中 DENY 属预期裁决；security-engine 不可用 = fail-closed 环境问题，判失败
      expect(msg, 'ABAC 拒绝原因非策略 DENY').toMatch(/DENY_BY_POLICY|OPA.*deny/i);
      expect(msg).not.toMatch(/SECURITY_ENGINE_UNAVAILABLE/i);
    } else {
      expect(res.status).toBe(200);
      expect(res.json?.success).toBe(true);
      taskId = String(res.json?.data?.taskId ?? '');
    }
  });

  await test.step('E2 GET /tasks/{taskId}/status → 收敛到终态（≤8s）', async () => {
    test.skip(!taskId, 'E 阶段按策略 DENY，无 taskId 可轮询');
    let finalStatus = '';
    for (let i = 0; i < 8 && !TERMINAL_STATUSES.includes(finalStatus); i++) {
      const res = await call(request, 'GET', `/api/v1/pipeline/tasks/${taskId}/status`, { token });
      finalStatus = String(res.json?.data?.status ?? '');
      if (!TERMINAL_STATUSES.includes(finalStatus)) await new Promise((r) => setTimeout(r, 1000));
    }
    expect(TERMINAL_STATUSES, `任务未收敛，末态=${finalStatus || 'unknown'}`).toContain(finalStatus);
  });

  await test.step('F GET /definitions/{id}/executions → 200 + items[]', async () => {
    const { status, json } = await call(request, 'GET', `/api/v1/pipeline/definitions/${definitionId}/executions?page=1&pageSize=10`, { token });
    expect(status).toBe(200);
    expect(json?.success).toBe(true);
    expect(Array.isArray(json?.data?.items)).toBe(true);
  });

  await test.step('G DELETE /definitions/{id} → 200（逻辑删除归档）', async () => {
    const { status, json } = await call(request, 'DELETE', `/api/v1/pipeline/definitions/${definitionId}`, { token });
    expect(status).toBe(200);
    expect(json?.success).toBe(true);
  });

  await test.step('H 归档后 GET /definitions/{id}/executions → 不可见', async () => {
    const { json } = await call(request, 'GET', `/api/v1/pipeline/definitions/${definitionId}/executions?page=1&pageSize=10`, { token });
    const hidden = json?.code === 404 || json?.success === false || !json?.data;
    expect(hidden, `归档定义仍可见: ${JSON.stringify(json?.data).slice(0, 120)}`).toBe(true);
  });
});

test('I POST /pipeline/debug/sessions → sessionId（含断点）', async ({ request }) => {  const body = {
    definitionId: null,
    definition: {
      name: `e2e-debug-${Date.now()}`,
      nodes: [
        { nodeId: 'd-a', type: 'SOURCE_JDBC', config: { sql: 'SELECT 1', datasourceId: 'ws-ds' } },
        { nodeId: 'd-b', type: 'TRANSFORM_SQL', config: { sql: 'SELECT 2' } },
      ],
    },
    breakpoints: [{ nodeId: 'd-a' }],
  };
  const { status, json } = await call(request, 'POST', '/api/v1/pipeline/debug/sessions', { token, data: body });
  expect(status).toBe(200);
  expect(String(json?.data?.sessionId ?? ''), '调试会话未返回 sessionId').not.toBe('');
});
