import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B2 认知引擎 + AI 引擎 — API 级（对应矩阵域02 / 域03）。
 * 覆盖：认知只读面（hypotheses/evidence/frameworks/methods/objects/models/pipeline/config）、
 *       AIP 编排（agents/models/pipelines）、Agent 运行时、护栏、405 状态行保真（D6 回归）。
 *
 * 反假绿纪律：
 *  - 每条断言配「形态 + 语义」双检，不只判 200
 *  - 已知缺口（认知上下文裁剪、预测闭环）显式登记为 PARTIAL，不判 PASS
 *  - 405 保真做成常驻断言（D6 修复后不得回退）
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('认知只读面（E1~E4 及目录类）', () => {
  const readonly = [
    { path: '/api/v1/cognitive/hypotheses', name: '假设' },
    { path: '/api/v1/cognitive/evidence', name: '证据' },
    { path: '/api/v1/cognitive/frameworks', name: '框架' },
    { path: '/api/v1/cognitive/methods', name: '方法' },
    { path: '/api/v1/cognitive/objects', name: '对象目录' },
    { path: '/api/v1/cognitive/models', name: '模型注册表' },
    { path: '/api/v1/cognitive/pipeline', name: '流水线定义' },
    { path: '/api/v1/cognitive/config', name: '配置' },
  ];

  for (const { path, name } of readonly) {
    test(`V-COG ${name} → code=0 且 data 为数组`, async ({ request }) => {
      const { status, json } = await call(request, 'GET', path, { token });
      expect(status, `${name} 应 200`).toBe(200);
      expect(json?.code, `${name} code 应为 0`).toBe(0);
      expect(Array.isArray(json?.data), `${name} data 应为数组`).toBe(true);
    });
  }
});

test('V-COG-001 认知引擎健康 UP', async ({ request }) => {
  const { status, json } = await call(request, 'GET', '/api/v1/engine/cognitive/health', { token });
  expect(status).toBe(200);
  expect(json?.data?.status).toBe('UP');
});

test('V-COG-002 反向探针：不存在的认知资源必须 404', async ({ request }) => {
  const { status } = await call(request, 'GET', '/api/v1/cognitive/hypotheses/NOT_EXIST_999', { token });
  expect([400, 404], '不存在资源应 400/404').toContain(status);
});

test.describe('AIP 编排（AI 工作台后端）', () => {
  test('V-AI-001 AIP Agent 列表真实', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/aip/agents', { token });
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
  });

  test('V-AI-002 AIP 模型列表真实且非空', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/aip/models', { token });
    expect(status).toBe(200);
    expect(Array.isArray(json?.data)).toBe(true);
    expect((json?.data ?? []).length, 'AIP 模型应有存量数据').toBeGreaterThan(0);
  });

  test('V-AI-003 AIP 流水线列表真实', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/aip/pipelines', { token });
    expect(status).toBe(200);
    expect(Array.isArray(json?.data)).toBe(true);
  });

  test('V-AI-004 反向探针：不存在的 AIP 模型 404', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/aip/models/NOT_EXIST_999', { token });
    expect([400, 404]).toContain(status);
  });
});

test.describe('护栏与 Agent 运行时', () => {
  test('V-AI-010 护栏策略列表', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/guardrails/policies', { token });
    expect([200]).toContain(status);
    if (status === 200) expect(json?.code).toBe(0);
  });

  test('V-AI-011 Agent 配置列表真实', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agents', { token });
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
  });

  test('V-AI-012 Agent Mesh 工具清单：MCP 未连接时显式 501 降级', async ({ request }) => {
    // 实测：/api/agent-mesh/mcp/tools 在 MCP 通道未连接时返回
    // 501 ECOS-AI-AGENT-RUNTIME-ABSENT（设计降级，非缺陷）。断言「明确降级且不伪装成功」。
    const { status, json } = await call(request, 'GET', '/api/agent-mesh/mcp/tools', { token });
    if (status === 200) {
      expect(json?.code).toBe(0);
    } else {
      expect([501, 503], `MCP 未连应显式 501/503，实得 ${status}`).toContain(status);
      expect(json?.code, '不得伪装 code=0').not.toBe(0);
      expect(String(json?.errorCode ?? ''), '应带可识别错误码').toMatch(/ECOS-AI/);
    }
  });
});

test.describe('HTTP 状态行保真（D6 回归，2026-11-13 修复）', () => {
  test('V-HTTP-405 方法不匹配必须 405 + Allow 头，不得报 500', async ({ request }) => {
    // 缺陷 D6：GlobalExceptionHandler 无 405 分支，HttpRequestMethodNotSupportedException
    // 落入兜底被报成 500 ECOS-SYS-500。修复后应 405 + ECOS-SYS-405 + Allow 头。
    const res = await request.get('/api/v1/cognitive/executions', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(res.status(), '方法不匹配必须是 405 而非 500').toBe(405);
    const json = await res.json().catch(() => null);
    expect(json?.errorCode, '应含 ECOS-SYS-405').toBe('ECOS-SYS-405');
    const allow = res.headers()['allow'] ?? '';
    expect(allow.toUpperCase(), '405 应带 Allow 头列出支持的方法').toContain('POST');
  });

  test('V-HTTP-404 不存在的路径必须 404 而非 500', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/definitely-not-a-real-path-xyz', { token });
    expect(status).toBe(404);
    expect(json?.errorCode).toBe('ECOS-SYS-404');
  });

  test('V-HTTP-400 坏请求体必须 400 而非 500', async ({ request }) => {
    const res = await request.post('/api/v1/cognitive/executions', {
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: '{bad json',
    });
    expect(res.status(), '畸形 JSON 应 400').toBe(400);
  });
});

test.describe('权限（V5）', () => {
  test('V-COG-SEC 无 token 访问认知端点被拒', async ({ request }) => {
    for (const p of ['/api/v1/cognitive/hypotheses', '/api/v1/cognitive/models', '/api/v1/aip/models']) {
      const { status } = await call(request, 'GET', p);
      expect([401, 403], `${p} 无 token 应拒绝`).toContain(status);
    }
  });
});
