import { expect, test } from '@playwright/test';
import { loginAsAdmin, call } from '../../helpers/ecos';

/**
 * B7·Copilot — API 级（对应矩阵域11）。
 *
 * 反假绿纪律：
 *  - Copilot 的核心是「真调 agent-loop」，不是「回一段固定文案」：
 *    成功响应必须带可追溯字段（agentId/模型/工具调用痕迹之一），且不得为空字符串
 *  - 无密钥 / 引擎不可用时必须显式降级（4xx/5xx + 明确原因），不得伪造成「已回答」
 *  - Agent 清单必须与后端引擎真实对齐（engine 字段指向真实引擎名）
 */

let token = '';

test.beforeAll(async ({ request }) => {
  token = await loginAsAdmin(request);
});

test.describe('Copilot Agent 与工具面', () => {
  test('V-COP-001 Agent 清单真实且 engine 字段指向真实引擎', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent/copilot/agents', { token });
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
    const list = json?.data as any[];
    expect(Array.isArray(list), 'agents 应为数组').toBe(true);
    expect(list.length, 'Copilot 应有可选 Agent').toBeGreaterThan(0);
    const KNOWN = /data-engine|ontology-engine|kb-engine|ai-engine|workspace|security-engine/;
    list.forEach((a) => {
      expect(a.id, 'Agent 应有 id').toBeTruthy();
      expect(a.name, `Agent ${a.id} 应有 name`).toBeTruthy();
      expect(String(a.engine ?? ''), `Agent ${a.id} 的 engine 应指向真实引擎，实得 ${a.engine}`).toMatch(KNOWN);
    });
  });

  test('V-COP-002 工具白名单真实且非空', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent/copilot/tools', { token });
    expect(status).toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data;
    const tools = Array.isArray(d) ? d : (d as any)?.tools ?? (d as any)?.items;
    expect(Array.isArray(tools), 'tools 应为数组').toBe(true);
    expect(tools.length, '工具白名单应有条目').toBeGreaterThan(0);
  });

  test('V-COP-003 快捷问题非空且为可读文本（agentId 必填）', async ({ request }) => {
    // agentId 是 @RequestParam 必填；缺失必须 400，不得回落到通用问题集
    const missing = await call(request, 'GET', '/api/v1/agent/copilot/quick-questions', { token });
    expect(missing.status, `缺 agentId 应 400，实得 ${missing.status}`).toBe(400);

    const { status, json } = await call(request, 'GET', '/api/v1/agent/copilot/quick-questions?agentId=agent-data', { token });
    expect(status, 'agent-data 快捷问题应 200').toBe(200);
    expect(json?.code).toBe(0);
    const qs = json?.data as any[];
    expect(Array.isArray(qs), '快捷问题应为数组').toBe(true);
    expect(qs.length, '快捷问题应有条目').toBeGreaterThan(0);
    qs.forEach((q: any) => {
      const text = typeof q === 'string' ? q : q?.text ?? q?.question ?? q?.label;
      expect(String(text ?? '').trim().length, `快捷问题文本不应为空：${JSON.stringify(q)}`).toBeGreaterThan(0);
      expect(q?.agentId ?? 'agent-data', '快捷问题应归属到请求的 agent').toBe('agent-data');
    });
  });

  test('V-COP-003b 未注册 Agent 的快捷问题必须拒绝（D18 回归常驻）', async ({ request }) => {
    const { status, json } = await call(request, 'GET', '/api/v1/agent/copilot/quick-questions?agentId=__NO_SUCH_AGENT__', { token });
    expect(status, `未注册 Agent 应 400，实得 ${status}`).toBe(400);
    expect(String(json?.message ?? ''), '拒绝必须列出可选值来源').toContain('agents');
  });

  test('V-COP-004 chat 必须 POST（GET 应 405）', async ({ request }) => {
    const { status } = await call(request, 'GET', '/api/v1/agent/copilot/chat', { token });
    expect(status, `GET chat 应 405，实得 ${status}`).toBe(405);
  });
});

test.describe('Copilot 对话（核心：真调 vs 伪造）', () => {
  test('V-COP-010 未知 Agent 必须明确拒绝（D18 回归常驻）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/agent/copilot/chat', {
      token,
      data: { agentId: '__NO_SUCH_AGENT__', message: 'hi' },
    });
    // D18：原实现把未知 agentId 交给旧通道，返回 answer="Task completed: hi" 的伪造结果 + 硬编码 thoughtChain
    expect(status, `未知 Agent 必须 400，实得 ${status}`).toBe(400);
    expect(json?.code, '未知 Agent 不得返回 code=0').not.toBe(0);
    expect(String(json?.message ?? ''), '拒绝必须指出可用 Agent 的清单位置').toContain('agents');
    expect((json?.data as any)?.answer, '拒绝时不得携带任何"答案"文本').toBeFalsy();
  });

  test('V-COP-011 空消息必须拒绝（不得凭空生成答案）', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/agent/copilot/chat', {
      token,
      data: { agentId: 'agent-data', message: '   ' },
    });
    const d = json?.data as any;
    const rejected = status >= 400 || json?.code !== 0 || d?.degraded === true;
    expect(rejected, `空消息应被拒或标注降级，实得 HTTP=${status} code=${json?.code}`).toBe(true);
  });

  test('V-COP-012 真实提问：成功须可追溯，被护栏拦截须如实标注', async ({ request }) => {
    const { status, json } = await call(request, 'POST', '/api/v1/agent/copilot/chat', {
      token,
      data: { agentId: 'agent-data', message: 'how many data sources are there' },
    });
    expect(status, 'chat 应 200（业务结果在 data.success 里）').toBe(200);
    expect(json?.code).toBe(0);
    const d = json?.data as any;
    expect(d, 'chat 必须返回结果体').toBeTruthy();
    // 可追溯性：主通道应暴露 agent/engine/工具白名单/traceId 之一
    const traceable = Boolean(d?.agentId) || Boolean(d?.engine) || Boolean(d?.traceId)
      || Boolean(d?.sessionId) || Array.isArray(d?.tools) || Array.isArray(d?.toolCalls);
    expect(traceable, `响应应可追溯，实得 keys=${Object.keys(d ?? {}).join(',')}`).toBe(true);

    if (d?.success === true) {
      const answer = String(d?.answer ?? d?.content ?? d?.reply ?? '');
      expect(answer.trim().length, '成功时必须返回非空答案（空串=假成功）').toBeGreaterThan(0);
    } else {
      // 降级/拦截路径：必须显式标注原因（真实字段是 errorCode/errorClass + traceId），
      // 且不得同时给出"看起来像答案"的文本
      const reason = String(d?.error ?? d?.errorCode ?? d?.errorClass ?? d?.degradedReason ?? d?.violations ?? '');
      expect(reason.trim().length, `未成功时必须给出原因，实得 data.success=${d?.success} keys=${Object.keys(d ?? {}).join(',')}`).toBeGreaterThan(0);
      expect(String(d?.traceId ?? '').trim().length, '未成功时必须带 traceId 便于排障').toBeGreaterThan(0);
      expect(String(d?.answer ?? '').trim().length, '拦截时不得伪造答案文本').toBe(0);
      console.log(`[V-COP-012] agent-loop 未成功: ${d?.errorCode}/${d?.errorClass} trace=${d?.traceId}`);
    }
  });

  test('V-COP-013 会话 ID 语义：缺省会话必须新生成，跨请求可复用', async ({ request }) => {
    const first = await call(request, 'POST', '/api/v1/agent/copilot/chat', {
      token,
      data: { agentId: 'agent-data', message: 'ping 1' },
    });
    const s1 = (first.json?.data as any)?.sessionId;
    expect(s1, '未传 sessionId 时必须生成一个').toBeTruthy();
    const second = await call(request, 'POST', '/api/v1/agent/copilot/chat', {
      token,
      data: { agentId: 'agent-data', message: 'ping 2', sessionId: s1 },
    });
    const s2 = (second.json?.data as any)?.sessionId;
    // 显式传入的会话 id 会被沿用（多轮对话所需语义）
    expect(s2, '传入既有 sessionId 时应沿用以保持多轮会话').toBe(s1);
  });
});
