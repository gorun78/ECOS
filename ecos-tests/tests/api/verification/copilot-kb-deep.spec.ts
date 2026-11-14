import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B9·Copilot + B4·知识引擎 — 端点补测（矩阵域11 的 V-COP-016~035、域06 的 V-KB-00x~08x）。
 *
 * 只覆盖**能经 HTTP 判定的**条目；纯 UI 交互项（输入框/抽屉/主题）另在各分册的
 * 「未覆盖清单」里按 §9.3 登记，不在此伪造断言。
 *
 * 反假绿纪律：
 *  - 未知 agentId / 空消息必须 4xx，不得 5xx，不得返回编造回复
 *  - 降级不伪造：LLM 不可用时必须显式错误，响应体里不得出现"成功"信封
 *  - 凭据不回显：chat/工具响应的任何位置不得出现 token 原文
 */

let token = '';
test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

const COP = '/api/v1/agent/copilot';

test.describe('一、Copilot 端点面（V-COP-016~035）', () => {
  test('V-COP-020 agents 目录真实（Copilot 可选 Agent）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', `${COP}/agents`, { token });
    expect(status, `agents 应 200，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    const rows = Array.isArray(json?.data) ? json.data : (json?.data?.items ?? []);
    expect(Array.isArray(rows), 'agents 应返回数组').toBe(true);
    // eslint-disable-next-line no-console
    console.log(`[V-COP-020] agents=${rows.length}`);
  });

  test('V-COP-015 工具白名单可见（tools 端点真实且声明所属 Agent）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', `${COP}/tools`, { token });
    expect(status, `tools 应 200，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    const rows = Array.isArray(json?.data) ? json.data : (json?.data?.items ?? []);
    expect(Array.isArray(rows), 'tools 应返回数组').toBe(true);
    // eslint-disable-next-line no-console
    console.log(`[V-COP-015] tools=${rows.length}`);
  });

  test('V-COP-006 快捷问题来自后端（quick-questions 按 agentId 真实非空）', async ({ request }) => {
    // 契约：`GET /quick-questions` 必填 agentId；目录外 agentId 明确 400（D18 禁回落占位串）。
    const agentsRes = await call(request, 'GET', `${COP}/agents`, { token });
    const agents = Array.isArray(agentsRes.json?.data) ? agentsRes.json!.data as any[] : [];
    expect(agents.length, '目录内智能体不应为空').toBeGreaterThan(0);
    const agentId = String(agents[0].agentId ?? agents[0].id ?? agents[0].key);
    const { status, json } = await call(
      request, 'GET', `${COP}/quick-questions?agentId=${encodeURIComponent(agentId)}`, { token });
    expect(status, `quick-questions 应 200，实得 ${status}`).toBe(200);
    expect(json?.code).toBe(0);
    const rows = Array.isArray(json?.data) ? json.data : (json?.data?.items ?? []);
    expect(rows.length, '快捷问题不应为空（前端不得自造）').toBeGreaterThan(0);

    // 反向腿：目录外 agentId 必须 400，不得回落占位
    const bad = await call(request, 'GET', `${COP}/quick-questions?agentId=VERIFY_NOT_EXIST_999`, { token });
    expect(bad.status, '目录外 agentId 应 400').toBe(400);
    // eslint-disable-next-line no-console
    console.log(`[V-COP-006] agentId=${agentId} quickQuestions=${rows.length} unknown→${bad.status}`);
  });

  test('V-COP-016 未知 agentId → 明确业务错误（不得 500）', async ({ request }) => {
    const res = await request.fetch(`${COP}/chat`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      data: { agentId: 'VERIFY_NOT_EXIST_999', message: 'hello' },
    });
    const status = res.status();
    expect(status, `未知 agentId 应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx（不存在 ≠ 服务故障）').toBeLessThan(500);
    // eslint-disable-next-line no-console
    console.log(`[V-COP-016] unknown agent → ${status}`);
  });

  test('V-COP-017 空消息必须被拒（不得当作有效提问）', async ({ request }) => {
    const res = await request.fetch(`${COP}/chat`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      data: { message: '' },
    });
    const status = res.status();
    const json = await res.json().catch(() => null);
    expect(status, `空消息应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
    expect(json?.code, '失败信封 code 不得为 0').not.toBe(0);
    // eslint-disable-next-line no-console
    console.log(`[V-COP-017] empty message → ${status} code=${json?.code}`);
  });

  test('V-COP-030 无 token 访问 Copilot 面必须被拒', async ({ request }) => {
    for (const p of ['/agents', '/tools', '/quick-questions']) {
      const res = await request.fetch(`${COP}${p}`, { headers: { Accept: 'application/json' } });
      expect([401, 403], `${p} 无 token 应 401/403，实得 ${res.status()}`).toContain(res.status());
      const json = await res.json().catch(() => null);
      expect(json?.data ?? null, `${p} 拒绝响应不得携带业务 data`).toBeNull();
    }
  });

  test('V-COP-033 凭据不回显：Copilot 读面响应不得含 token 原文', async ({ request }) => {
    for (const p of ['/agents', '/tools', '/quick-questions']) {
      const res = await request.fetch(`${COP}${p}`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      });
      const text = await res.text();
      expect(text.includes(token), `${p} 响应不得回显调用方 token`).toBe(false);
      expect(text, `${p} 不得回显私钥`).not.toMatch(/"privateKey"\s*:/);
      expect(text, `${p} 不得回显对称密钥`).not.toMatch(/"secret"\s*:\s*"[^"]{8,}"/);
    }
  });

  test('V-COP-018 降级不伪造：LLM 不可用时 chat 必须显式失败而非编造回复', async ({ request }) => {
    const res = await request.fetch(`${COP}/chat`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', Accept: 'application/json' },
      data: { message: 'VERIFY: 请回复 OK', agentId: 'default' },
    });
    const status = res.status();
    const json = await res.json().catch(() => null);
    if (status >= 400) {
      expect(json?.code, `失败时 code 不得为 0，实得 ${json?.code}`).not.toBe(0);
      // eslint-disable-next-line no-console
      console.log(`[V-COP-018] chat → ${status} code=${json?.code}（显式失败，未伪造）`);
    } else {
      // 200 必须有真实模型产物痕迹（model/usage/traceId 之一），不能是本地拼接的假回复
      const hasProvenance = json?.data?.model || json?.data?.usage || json?.data?.traceId || json?.data?.messageId;
      expect(hasProvenance, '200 必须带模型溯源字段（禁本地编造回复）').toBeTruthy();
      // eslint-disable-next-line no-console
      console.log(`[V-COP-018] chat → 200 带溯源字段，未伪造`);
    }
  });
});

test.describe('二、知识引擎端点面（V-KB-004~083）', () => {
  const kbReads = [
    { id: 'V-KB-003', path: '/api/v1/knowledge-bases', name: '知识库列表' },
    { id: 'V-KB-020', path: '/api/v1/knowledge/ecos-graph', name: 'ECOS 图谱' },
    { id: 'V-KB-021', path: '/api/v1/knowledge/graph', name: '图谱查询' },
    { id: 'V-KB-030', path: '/api/v1/knowledge/extract/files', name: '抽取文件' },
    { id: 'V-KB-002', path: '/api/v1/knowledge/settings', name: '知识设置' },
    { id: 'V-KB-070', path: '/api/v1/knowledge/rules', name: '专家规则' },
    { id: 'V-KB-001', path: '/api/v1/engine/knowledge/health', name: '知识引擎健康' },
  ];

  for (const { id, path, name } of kbReads) {
    test(`${id} ${name} 可达且 code=0`, async ({ request }) => {
      const { status, json } = await call(request, 'GET', path, { token });
      expect(status, `${name} 应 200，实得 ${status}`).toBe(200);
      expect(json?.code, `${name} code 应为 0`).toBe(0);
      // eslint-disable-next-line no-console
      console.log(`[${id}] ${path} → 200`);
    });
  }

  test('V-KB-080 无 token 访问知识面必须被拒', async ({ request }) => {
    const res = await request.fetch('/api/v1/knowledge-bases', { headers: { Accept: 'application/json' } });
    expect([401, 403], `无 token 应 401/403，实得 ${res.status()}`).toContain(res.status());
  });

  test('V-KB-081 不存在的知识库 → 4xx 而非 5xx', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/knowledge-bases/VERIFY_NOT_EXIST_999', { token });
    expect(status, `应 4xx，实得 ${status}`).toBeGreaterThanOrEqual(400);
    expect(status, '不得 5xx').toBeLessThan(500);
  });

  test('V-KB-060 认知供给面方法语义（POST 写面，GET 应 405）', async ({ request }) => {
    const res = await request.fetch('/api/v1/knowledge/cognition/supply', {
      method: 'GET',
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    });
    expect(res.status(), 'GET 写面应 405（方法语义明确）').toBe(405);
  });
});
