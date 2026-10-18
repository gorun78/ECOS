// @vitest-environment node
/**
 * httpClient.traceId.spec.ts — 传输层追踪号单源契约（#143/B5-6）。
 *
 * <p>差距台账 篇13-15 的「httpClient 不带 traceId」腿：改前 `apiFetch`/`apiFetchData`/
 * `doFetch` 三条错误腿只抛 `message`＋`status`，页面要为拿追踪号各自拆信封（实测
 * glossary 与 cognitiveClient 各写了一份 `pickTraceId`，改一处口径另一处就漂移）。
 * 收口后追踪号在传输层一次取出并随 `ApiRequestError`/`NoAccessError` 抛出。</p>
 *
 * <p>判据口径：响应头 `X-Request-Id` → `X-Trace-Id` → `X-ECOS-Trace-Id` 优先，其次信封
 * `traceId`；两者都拿不到时保持 `undefined`——「拿不到号」与「后端没给号」都不得渲染成
 * 一个假号。兼容性红线：`message` 文案与 `e.status` 判别面与改前逐字一致
 * （`isErrorResponse` 与各处 `err.message` 消费者零改动）。</p>
 *
 * <p>全程 stub fetch，不依赖 jsdom（本机 PATH Node 20.18.1 下 jsdom 链报
 * ERR_REQUIRE_ESM，见 vite.config.ts 注记）。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  API_BASE,
  ApiRequestError,
  NoAccessError,
  apiFetch,
  apiFetchData,
  doFetch,
  pickTraceId,
  readApiError,
} from '../httpClient';

interface ResSpec {
  status?: number;
  headers?: Record<string, string>;
  /** JSON 响应体；与 raw 互斥 */
  body?: unknown;
  /** 非 JSON 响应体（网关 HTML/纯文本回显） */
  raw?: string;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const lower: Record<string, string> = {};
  Object.entries(spec.headers ?? {}).forEach(([k, v]) => {
    lower[k.toLowerCase()] = v;
  });
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => lower[k.toLowerCase()] ?? null },
    json: () => Promise.resolve(JSON.parse(text)),
    text: () => Promise.resolve(text),
  } as unknown as Response;
}

function stubFetch(...responses: Response[]) {
  const calls: { url: string; init?: RequestInit }[] = [];
  let i = 0;
  const spy = vi.fn(async (input: unknown, init?: RequestInit) => {
    calls.push({ url: String(input), init });
    return responses[Math.min(i++, responses.length - 1)];
  });
  vi.stubGlobal('fetch', spy);
  return { spy, calls };
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('pickTraceId — 取号口径单源', () => {
  it('响应头优先于信封，且三个头名按 X-Request-Id → X-Trace-Id → X-ECOS-Trace-Id 顺序', () => {
    const both = makeRes({
      status: 500,
      headers: { 'X-Request-Id': 'hdr-req', 'X-Trace-Id': 'hdr-trace' },
      body: { traceId: 'env-1' },
    });
    expect(pickTraceId(both, { traceId: 'env-1' })).toBe('hdr-req');
    const onlySecond = makeRes({ status: 500, headers: { 'X-Trace-Id': 'hdr-trace' } });
    expect(pickTraceId(onlySecond)).toBe('hdr-trace');
    const onlyThird = makeRes({ status: 500, headers: { 'X-ECOS-Trace-Id': 'hdr-ecos' } });
    expect(pickTraceId(onlyThird)).toBe('hdr-ecos');
  });

  it('无号时返回 undefined：信封 null/空串/缺字段都不算一个号（禁把「拿不到」当「没有号」）', () => {
    const noHeader = makeRes({ status: 500 });
    expect(pickTraceId(noHeader, { traceId: null })).toBeUndefined();
    expect(pickTraceId(noHeader, { traceId: '' })).toBeUndefined();
    expect(pickTraceId(noHeader, {})).toBeUndefined();
    expect(pickTraceId(noHeader, null)).toBeUndefined();
    expect(pickTraceId(noHeader)).toBeUndefined();
  });
});

describe('apiFetch — 错误腿携带 traceId', () => {
  it('非 2xx 抛 ApiRequestError，透出响应头号与 status/httpStatus 双写', async () => {
    const { calls } = stubFetch(
      makeRes({ status: 500, headers: { 'X-Request-Id': 'tr-500' } }),
    );
    const err = await apiFetch('/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(calls[0].url).toBe(`${API_BASE}/v1/things`);
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).traceId).toBe('tr-500');
    expect((err as ApiRequestError).httpStatus).toBe(500);
    expect((err as ApiRequestError).status).toBe(500);
    // 文案与改前逐字一致（`API ${path} returned ${status}`）
    expect((err as Error).message).toBe('API /v1/things returned 500');
  });

  it('403 抛 NoAccessError 且带 traceId，既有 status=403 判别面不变', async () => {
    stubFetch(makeRes({ status: 403, headers: { 'X-Trace-Id': 'tr-403' } }));
    const err = await apiFetch('/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(NoAccessError);
    expect((err as NoAccessError).traceId).toBe('tr-403');
    expect((err as NoAccessError).status).toBe(403);
  });

  it('成功回 2xx 时不透出 traceId（只在错误腿取号，成功面零行为变化）', async () => {
    stubFetch(makeRes({ status: 200, headers: { 'X-Request-Id': 'tr-ok' }, body: { ok: true } }));
    await expect(apiFetch('/v1/things')).resolves.toEqual({ ok: true });
  });
});

describe('apiFetchData — 错误腿携带 traceId', () => {
  it('非 2xx 从失败信封兜底取号，message 保持响应体原文', async () => {
    const body = { code: 500, message: 'boom', traceId: 'env-500' };
    stubFetch(makeRes({ status: 500, body }));
    const err = await apiFetchData('/api/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).traceId).toBe('env-500');
    expect((err as ApiRequestError).httpStatus).toBe(500);
    expect((err as Error).message).toBe(JSON.stringify(body));
  });

  it('非 JSON 失败体（网关 HTML）解析不出号时 traceId 为 undefined，不抛解析异常', async () => {
    stubFetch(makeRes({ status: 502, raw: '<html>502 Bad Gateway</html>' }));
    const err = await apiFetchData('/api/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).traceId).toBeUndefined();
    expect((err as Error).message).toBe('<html>502 Bad Gateway</html>');
  });

  it('Response 面缺 headers 时取号不得反噬错误本身（缺陷原形自检：裸取 headers 会抛 TypeError）', async () => {
    const body = '{"code":404,"message":"资源不存在","traceId":"tc-404"}';
    const noHeadersRes = {
      ok: false,
      status: 404,
      text: () => Promise.resolve(body),
      json: () => Promise.resolve(JSON.parse(body)),
    } as unknown as Response;
    stubFetch(noHeadersRes);
    const err = await apiFetchData('/api/v1/dq/rules').then(
      (): null => null,
      (e: unknown) => e,
    );
    // 必须是带 HTTP 语义的 ApiRequestError，而不是取号崩溃产生的 TypeError——
    // 下游 data-quality 回落遥测按 /404/ 匹配 message 判定 status，崩溃会让归因变成 undefined
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).httpStatus).toBe(404);
    expect((err as Error).message).toBe(body);
    // 无响应头 ⇒ 退到信封兜底取号
    expect((err as ApiRequestError).traceId).toBe('tc-404');
  });

  it('HTTP 200 伪失败（code 非 0/200）同样带信封号', async () => {
    stubFetch(
      makeRes({ status: 200, body: { code: 4001, message: '参数非法', traceId: 'env-biz' } }),
    );
    const err = await apiFetchData('/api/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as Error).message).toBe('参数非法');
    expect((err as ApiRequestError).traceId).toBe('env-biz');
    // 伪失败腿没有 HTTP 语义 ⇒ status 不补假值
    expect((err as ApiRequestError).httpStatus).toBeUndefined();
  });

  it('success===false 伪失败也归入 ApiRequestError', async () => {
    stubFetch(makeRes({ status: 200, body: { success: false, message: 'nope' } }));
    const err = await apiFetchData('/api/v1/things').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as Error).message).toBe('nope');
    expect((err as ApiRequestError).traceId).toBeUndefined();
  });
});

describe('doFetch — 探测腿同样单源取号', () => {
  it('非 2xx 抛 ApiRequestError 并透出响应头号', async () => {
    stubFetch(makeRes({ status: 503, headers: { 'X-Request-Id': 'tr-probe' } }));
    const err = await doFetch('/api/v1/probe').then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).traceId).toBe('tr-probe');
    expect((err as Error).message).toBe('503');
  });
});

describe('readApiError — 页面侧取摘要单源', () => {
  it('ApiRequestError / NoAccessError 都能取到 traceId 与 status', () => {
    expect(
      readApiError(new ApiRequestError('m1', { traceId: 't1', httpStatus: 500 })),
    ).toEqual({ message: 'm1', traceId: 't1', status: 500 });
    const noAccess = new NoAccessError('m2', { traceId: 't2' });
    expect(readApiError(noAccess)).toEqual({ message: 'm2', traceId: 't2', status: 403 });
  });

  it('非本层错误（网络层/TypeError）不编造 traceId', () => {
    expect(readApiError(new TypeError('Failed to fetch'))).toEqual({
      message: 'Failed to fetch',
    });
    expect(readApiError({ status: 418, message: 'legacy' })).toEqual({
      message: 'legacy',
      status: 418,
    });
  });
});
