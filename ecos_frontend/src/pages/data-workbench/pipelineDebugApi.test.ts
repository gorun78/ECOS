/**
 * pipelineDebugApi.test.ts — Wave 4 T4
 *
 * 后端 PipelineDebugController 已落地（T1+T2+T3）：
 * - POST /api/v1/pipeline/debug/sessions            开始调试
 * - GET  /{sessionId}                               会话状态
 * - POST /{sessionId}/step                          单步
 * - POST /{sessionId}/continue                      继续到下一断点
 * - POST /{sessionId}/stop                          停止
 * - POST /{sessionId}/reset                         重置
 * - DELETE /{sessionId}                             删除
 * - GET  /executions?...                             运行历史
 * - GET  /{sessionId}/preview/{nodeId}              数据预览
 * - GET  /{sessionId}/steps                         节点步骤
 * T3: GET /api/v1/pipeline/debug/executions/{executionId}/logs
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  getDebugSession,
  startDebugSession,
  stepDebugSession,
  continueDebugSession,
  stopDebugSession,
  resetDebugSession,
  deleteDebugSession,
  listDebugExecutions,
  getExecutionLog,
} from './pipelineDebugApi';

const authHeadersStub = () => {
  const store: Record<string, string> = { token: 'test-token' };
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => { store[k] = v; },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => {
      for (const k of Object.keys(store)) delete store[k];
    },
    length: 1,
    key: (i: number) => (i === 0 ? 'token' : null),
  });
};

const fetchOk = (data: unknown) =>
  vi.fn().mockResolvedValue({
    ok: true,
    status: 200,
    json: async () => ({ success: true, code: 0, data }),
  });

const fetchErr = (status: number) =>
  vi.fn().mockResolvedValue({
    ok: false,
    status,
    json: async () => ({}),
  });

beforeEach(() => {
  authHeadersStub();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('startDebugSession', () => {
  it('POST /sessions — ok=true 返回会话', async () => {
    const payload: Parameters<typeof startDebugSession>[0] = {
      definitionId: 'pb-001',
      definition: { name: 'ping', nodes: [], edges: [] },
      breakpoints: [],
    };
    const f = fetchOk({
      sessionId: 's1',
      state: 'CREATED',
      totalNodes: 1,
      completedNodes: 0,
      rowsProcessed: 0,
      createdAt: '2026-09-09T00:00:00Z',
    });
    vi.stubGlobal('fetch', f);

    const s = await startDebugSession(payload);
    expect(s).not.toBeNull();
    expect(s!.sessionId).toBe('s1');
    expect(s!.state).toBe('CREATED');

    const [url, init] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/v1/pipeline/debug/sessions');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(JSON.stringify(payload));
  });

  it('网络错误 → 返回 null（防御性）', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('net')));

    expect(
      await startDebugSession({
        definitionId: 'x',
        definition: { name: 'x', nodes: [], edges: [] },
      }),
    ).toBeNull();
  });
});

describe('GET /{sessionId}', () => {
  it('URL 编码正确（/{sessionId}/step 与 GET 不能混淆）', async () => {
    const f = fetchOk({
      sessionId: 's/x',
      state: 'paused',
      currentNodeId: 'n2',
      completedNodes: 1,
      totalNodes: 5,
      rowsProcessed: 42,
      createdAt: '2026-09-09T00:00:00Z',
    });
    vi.stubGlobal('fetch', f);

    const s = await getDebugSession('s/x');
    expect(s).not.toBeNull();
    expect(s!.sessionId).toBe('s/x');
    expect(s!.state).toBe('paused');

    const [url] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/v1/pipeline/debug/sessions/s%2Fx');
  });

  it('网络错误 → 返回 null', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('net')));

    expect(await getDebugSession('id1')).toBeNull();
  });
});

describe('POST 动作 — step/continue/stop/reset', () => {
  it.each([
    ['step', stepDebugSession],
    ['continue', continueDebugSession],
    ['stop', stopDebugSession],
    ['reset', resetDebugSession],
  ])('POST /s1/%s — ok=true', async (action, fn) => {
    const f = fetchOk({
      sessionId: 's1',
      state: action === 'stop' ? 'stopped' : 'running',
      completedNodes: 1,
      totalNodes: 3,
      rowsProcessed: 10,
    });
    vi.stubGlobal('fetch', f);

    const s = await fn('s1');
    expect(s).not.toBeNull();
    expect(s!.state).toBeDefined();

    const [url, init] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe(`/api/v1/pipeline/debug/sessions/s1/${action}`);
    expect(init.method).toBe('POST');
  });

  it.each([
    ['step', stepDebugSession],
    ['continue', continueDebugSession],
    ['stop', stopDebugSession],
    ['reset', resetDebugSession],
  ])('POST /%s — 网络 503 → 降级 null 不 throw', async (action, fn) => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', fetchErr(503));

    expect(await fn('broken')).toBeNull();
  });
});

describe('DELETE /{sessionId}', () => {
  it('ok=true 删除成功 — call() 返回 data（后端 DELETE 通常 data=null，回退整 json）', async () => {
    // 后端约定 DELETE 响应 { success: true, code: 0, data: null }
    // call() 实现: json?.data ?? json — data===null 命中 ?? 回退整 json
    const f = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async (): Promise<{ success: boolean; code: number; data: unknown }> => ({ success: true, code: 0, data: null }),
    });
    vi.stubGlobal('fetch', f);

    const res = await deleteDebugSession('s1');
    // 实际返回的是 json 整体（data:null 短路 ??）
    // 关键契约：fetch 被调用、url=DELETE 端点（结构验证），res 为 truthy 含义是"完成"
    expect(res).not.toBeNull();
    expect((res as unknown as { success?: boolean }).success).toBe(true);

    const [url, init] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/v1/pipeline/debug/sessions/s1');
    expect(init.method).toBe('DELETE');
  });

  it('网络 503 → null 不 throw', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', fetchErr(503));

    expect(await deleteDebugSession('broken')).toBeNull();
  });
});

describe('GET /executions（运行历史）', () => {
  it('data 数组 → 返回 ExecutionRecord 列表', async () => {
    const exec = {
      id: 'ex-001',
      definitionId: 'pb-001',
      status: 'COMPLETED',
      startedAt: 1725000000,
      completedAt: 1725000900,
      rowsProcessed: 1024,
    };
    const f = fetchOk([exec]);
    vi.stubGlobal('fetch', f);

    const list = await listDebugExecutions('pb-001', 1, 10);
    expect(list).toHaveLength(1);
    expect(list!.at(0)?.id).toBe('ex-001');
    expect(list!.at(0)?.status).toBe('COMPLETED');

    const [url] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe(
      '/api/v1/pipeline/debug/sessions/executions?definitionId=pb-001&page=1&pageSize=10',
    );
  });

  it('网络 500 → null', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', fetchErr(500));

    expect(await listDebugExecutions('pb-001', 1, 10)).toBeNull();
  });
});

describe('GET /executions/{executionId}/logs (T3 日志端点)', () => {
  it('data 数组 → 返回日志行', async () => {
    const logLine = {
      seq: 1,
      nodeId: 'n1',
      level: 'INFO',
      message: 'step 1 started',
      atMs: 1725000000000,
    };
    const f = fetchOk([logLine]);
    vi.stubGlobal('fetch', f);

    const lines = await getExecutionLog('ex-001');
    expect(lines).toHaveLength(1);
    expect(lines?.at(0)?.seq).toBe(1);
    expect(lines?.at(0)?.level).toBe('INFO');
    expect(lines?.at(0)?.atMs).toBe(1725000000000);

    const [url] = (f as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/v1/pipeline/debug/executions/ex-001/logs');
  });

  it('网络 500 → null', async () => {
    vi.stubGlobal('console', { ...console, warn: vi.fn() });
    vi.stubGlobal('fetch', fetchErr(500));

    expect(await getExecutionLog('ex-001')).toBeNull();
  });
});
