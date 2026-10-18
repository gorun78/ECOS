// @vitest-environment node
/**
 * cognitiveClient.traceSingleSource.spec.ts — D-B5-26 取号腿合流契约（#143/B5-7）。
 *
 * <p>差距台账 v1.0.36 行⑨登记的 **D-B5-26**：`services/cognitiveClient.ts` 自留第二份
 * traceId 提取腿（错误腿只看 `X-Trace-Id`、成功腿只看 `X-Request-Id`/`X-Trace-Id`，
 * 两者顺位不同且都缺第三号 `X-ECOS-Trace-Id`），与 #143/B5-6 刚落地的
 * `httpClient#pickTraceId` 单源口径分裂 ⇒ 改一处口径另一处必然漂移。</p>
 *
 * <p>本文件先以「修复前的红」形态钉住缺陷原形（六项中改前实跑四项红），
 * 再要求收口后全绿。兼容性红线：`CognitiveApiError` 的 `code`/`status`/`message`
 * 三面与改前逐字一致（七态矩阵消费者与 `isCognitiveApiError` 零改动），
 * 本批只动「号从哪取」。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { callJson, callJsonWithTrace, CognitiveApiError } from '../cognitiveClient';

const SRC_DIR = fileURLToPath(new URL('..', import.meta.url));
const ENDPOINT = 'http://bff.test/api/v1/cognitive/detect';

interface ResSpec {
  status?: number;
  /** 不提供则桩面完全没有 headers 面（用于锁「取号不得反噬传输错误」） */
  headers?: Record<string, string>;
  body?: unknown;
  raw?: string;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  const stub: Record<string, unknown> = {
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(text),
  };
  if (spec.headers) {
    const lower: Record<string, string> = {};
    Object.entries(spec.headers).forEach(([k, v]) => {
      lower[k.toLowerCase()] = v;
    });
    stub.headers = { get: (k: string) => lower[k.toLowerCase()] ?? null };
  }
  return stub as unknown as Response;
}

function stubFetch(res: Response) {
  const spy = vi.fn(async () => res);
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('错误腿 — 取号一律走单源口径', () => {
  it('X-Request-Id（网关实际回写的头号）能被错误腿取到', async () => {
    stubFetch(makeRes({ status: 500, headers: { 'X-Request-Id': 'req-1' } }));
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(CognitiveApiError);
    expect((err as CognitiveApiError).code).toBe('HTTP_ERROR');
    expect((err as CognitiveApiError).status).toBe(500);
    expect((err as CognitiveApiError).traceId).toBe('req-1');
  });

  it('X-ECOS-Trace-Id 第三顺位同样可取（单源三号面不得在域内缩水）', async () => {
    stubFetch(makeRes({ status: 404, headers: { 'X-ECOS-Trace-Id': 'ecos-1' } }));
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect((err as CognitiveApiError).code).toBe('NOT_FOUND');
    expect((err as CognitiveApiError).traceId).toBe('ecos-1');
  });

  it('兼容锁：只有历史口径 X-Trace-Id 时行为不变', async () => {
    stubFetch(makeRes({ status: 400, headers: { 'X-Trace-Id': 'legacy-1' } }));
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect((err as CognitiveApiError).code).toBe('BAD_REQUEST');
    expect((err as CognitiveApiError).traceId).toBe('legacy-1');
  });

  it('顺位冲突时以单源顺序（X-Request-Id 优先）裁决，禁域内自定顺位', async () => {
    stubFetch(
      makeRes({
        status: 503,
        headers: { 'X-Request-Id': 'req-win', 'X-Trace-Id': 'trace-lose' },
      }),
    );
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect((err as CognitiveApiError).code).toBe('ENGINE_DISABLED');
    expect((err as CognitiveApiError).traceId).toBe('req-win');
  });

  it('桩面无 headers 面时取号不得反噬传输错误本身（锁 501 STUB 腿）', async () => {
    stubFetch(makeRes({ status: 501, body: { message: 'not implemented' } }));
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(CognitiveApiError);
    expect((err as CognitiveApiError).code).toBe('STUB');
    expect((err as CognitiveApiError).stub).toBe(true);
    expect((err as Error).message).toBe('not implemented');
    expect((err as CognitiveApiError).traceId).toBeUndefined();
  });

  it('HTTP 200 伪失败（code≠0/200）同样透出信封号', async () => {
    stubFetch(
      makeRes({ status: 200, body: { code: 4009, message: 'mind 不属场景', traceId: 'biz-9' } }),
    );
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect((err as CognitiveApiError).code).toBe('BUSINESS_ERROR');
    expect((err as Error).message).toBe('mind 不属场景');
    expect((err as CognitiveApiError).traceId).toBe('biz-9');
  });

  it('success===false 伪失败带响应头号', async () => {
    stubFetch(
      makeRes({
        status: 200,
        headers: { 'X-Request-Id': 'req-biz' },
        body: { success: false, message: 'nope' },
      }),
    );
    const err = await callJson(ENDPOINT).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect((err as CognitiveApiError).code).toBe('BUSINESS_ERROR');
    expect((err as CognitiveApiError).traceId).toBe('req-biz');
  });
});

describe('成功腿 callJsonWithTrace — 与错误腿同口径', () => {
  it('X-ECOS-Trace-Id 在成功态也可取', async () => {
    stubFetch(
      makeRes({ status: 200, headers: { 'X-ECOS-Trace-Id': 'ecos-ok' }, body: { data: [1] } }),
    );
    const r = await callJsonWithTrace(ENDPOINT);
    expect(r.data).toEqual([1]);
    expect(r.traceId).toBe('ecos-ok');
  });

  it('callJson 成功面仍不透出号（既有对外行为零变化，号只在 callJsonWithTrace 可见）', async () => {
    stubFetch(makeRes({ status: 200, headers: { 'X-Request-Id': 'req-x' }, body: [7] }));
    await expect(callJson(ENDPOINT)).resolves.toEqual([7]);
  });
});

describe('message 文案兼容红线（逐字不变，本组改前即绿＝非回归锁）', () => {
  it('信封 message 优先；无 message 回落响应体原文；空体回落 `HTTP ${status}`', async () => {
    stubFetch(makeRes({ status: 400, headers: { 'X-Trace-Id': 'm-1' }, body: { message: '参数非法' } }));
    await expect(callJson(ENDPOINT)).rejects.toThrow('参数非法');

    stubFetch(makeRes({ status: 400, headers: { 'X-Trace-Id': 'm-2' }, raw: 'plain boom' }));
    await expect(callJson(ENDPOINT)).rejects.toThrow('plain boom');

    stubFetch(makeRes({ status: 400, headers: { 'X-Trace-Id': 'm-3' }, raw: '' }));
    await expect(callJson(ENDPOINT)).rejects.toThrow('HTTP 400');
  });
});

describe('源形自检 — 域内自留取号腿不得回归', () => {
  /** 缺陷原形：域内直接对响应头自取三号（顺位与单源漂移即由此产生） */
  const DEFECT = 'const headerTrace = res.headers.get("X-Request-Id") ?? res.headers.get("X-Trace-Id");';
  const SELF_OWNED_LEG = /res\.headers\.get\(\s*["'`]X-/;

  it('判据能变红：自留取号腿原形命中', () => {
    expect(SELF_OWNED_LEG.test(DEFECT)).toBe(true);
  });

  it('不误报：收口后的 cognitiveClient.ts 不得再自取响应头号', () => {
    const src = readFileSync(`${SRC_DIR}/cognitiveClient.ts`, 'utf8');
    expect(SELF_OWNED_LEG.test(src), 'cognitiveClient.ts 仍自留取号腿，未合流 pickTraceId 单源').toBe(false);
  });
});
