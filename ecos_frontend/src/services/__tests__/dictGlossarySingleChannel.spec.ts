// @vitest-environment node
/**
 * dictGlossarySingleChannel.spec.ts — 字典/术语域「API 单通道 + 基址单源」契约
 * （#143/B5-6，前端开发规范 §十 + 详设-08 §九 W206/C188）。
 *
 * <p>改前形态（缺陷原形，本文件末尾有能变红的自检）：</p>
 * <ul>
 *   <li>`services/dict.ts` 私设 `const API_BASE = '/api/v1/dict'` 并自带 fetch + 拆信封腿，
 *       且<b>请求不带 Authorization</b> —— 后端 auth 通道下这类调用只可能 401，属真实缺陷
 *       而非风格问题；</li>
 *   <li>`services/glossary.ts` 私设基址 + 自留一份 `pickTraceId`/拆信封，与 cognitiveClient
 *       那份各自漂移。</li>
 * </ul>
 *
 * <p>收口后两域只经 `httpClient#apiFetchData` 单通道：基址由 `API_BASE` 派生、token 由
 * `auth#getAuthToken` 单源注入、追踪号由传输层单源抛出。glossary 侧保留
 * `GlossaryApiError` 这一既有判别面（回映射不改 message 口径）。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { API_BASE, ApiRequestError } from '../httpClient';
import { getDictTables } from '../dict';
import {
  GlossaryApiError,
  listGlossaryTerms,
  readGlossaryError,
  updateGlossaryTerm,
} from '../glossary';

const SRC_DIR = fileURLToPath(new URL('..', import.meta.url));

function makeRes(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  const lower: Record<string, string> = {};
  Object.entries(headers).forEach(([k, v]) => {
    lower[k.toLowerCase()] = v;
  });
  const text = JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => lower[k.toLowerCase()] ?? null },
    json: () => Promise.resolve(JSON.parse(text)),
    text: () => Promise.resolve(text),
  } as unknown as Response;
}

/** 记录 (url, init) 的 fetch stub；token 走 localStorage 单源注入 */
function stubFetchWithAuth(res: Response, token = 'tok-78') {
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => (k === 'token' ? token : null),
    setItem: (): void => undefined,
    removeItem: (): void => undefined,
  });
  const calls: { url: string; init?: RequestInit }[] = [];
  const spy = vi.fn(async (input: unknown, init?: RequestInit) => {
    calls.push({ url: String(input), init });
    return res;
  });
  vi.stubGlobal('fetch', spy);
  return calls;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('dict.ts — 传输改走 httpClient 单通道', () => {
  it('基址由 API_BASE 派生，且请求带 Bearer token（改前此处不带 Authorization）', async () => {
    const calls = stubFetchWithAuth(makeRes(200, { code: 0, data: [] }));
    const result = await getDictTables({ schema: 'ecos_dw' });
    expect(calls[0].url).toBe(`${API_BASE}/v1/dict/tables?schema=ecos_dw`);
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer tok-78');
    expect(result).toEqual({ items: [], total: 0 });
  });

  it('传输错误由 httpClient 抛出 ApiRequestError（域内不再自拆信封）', async () => {
    stubFetchWithAuth(
      makeRes(500, { code: 500, message: 'dict boom', traceId: 'dict-tr' }, { 'X-Request-Id': 'dict-tr' }),
    );
    const err = await getDictTables().then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiRequestError);
    expect((err as ApiRequestError).traceId).toBe('dict-tr');
  });
});

describe('glossary.ts — 单通道 + GlossaryApiError 判别面保留', () => {
  it('列表请求 URL 由 API_BASE 派生并带 token', async () => {
    const calls = stubFetchWithAuth(makeRes(200, { code: 0, data: [] }));
    const items = await listGlossaryTerms({ domain: 'FIN' });
    expect(calls[0].url).toBe(`${API_BASE}/v1/ontology/glossary/terms?domain=FIN`);
    expect((calls[0].init?.headers as Record<string, string>).Authorization).toBe('Bearer tok-78');
    expect(items).toEqual([]);
  });

  it('HTTP 500 + 响应头号 → GlossaryApiError 透出 traceId/httpStatus，文案口径不变', async () => {
    stubFetchWithAuth(
      makeRes(500, { code: 500, message: '术语服务异常' }, { 'X-Request-Id': 'gl-tr' }),
    );
    const err = await listGlossaryTerms().then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(GlossaryApiError);
    expect((err as GlossaryApiError).traceId).toBe('gl-tr');
    expect((err as GlossaryApiError).httpStatus).toBe(500);
    // message = 响应体原文（改后传输层给 body 原文），页面侧 readGlossaryError 口径可用
    expect(readGlossaryError(err).traceId).toBe('gl-tr');
  });

  it('HTTP 200 伪失败（code≠0/200）→ 后端 message 优先，仍归 GlossaryApiError', async () => {
    stubFetchWithAuth(makeRes(200, { code: 4009, message: '词条编码重复', traceId: 'gl-biz' }));
    const err = await updateGlossaryTerm(1, {}).then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(GlossaryApiError);
    expect((err as Error).message).toBe('词条编码重复');
    expect((err as GlossaryApiError).traceId).toBe('gl-biz');
  });

  it('非本层错误原样透出（网络层归 NetworkError，不包装成 GlossaryApiError、不编造号）', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const err = await listGlossaryTerms().then(
      (): null => null,
      (e: unknown) => e,
    );
    expect(err).not.toBeInstanceOf(GlossaryApiError);
    expect((err as Error).message).toContain('unreachable');
    expect(readGlossaryError(err).traceId).toBeUndefined();
  });
});

describe('源形自检 — 私有基址/自留传输腿不得回归', () => {
  /** 缺陷原形：私设字面基址 + 裸 fetch */
  const DEFECT = [
    `const API_BASE = '${API_BASE}/v1/dict';`,
    '  const res = await fetch(`${API_BASE}${path}`, { headers, ...options });',
  ].join('\n');
  const PRIVATE_BASE_RE = /^\s*(?:export\s+)?const\s+[A-Z_]*API_BASE[A-Z_]*\s*=\s*['"`]/m;
  const BARE_FETCH_RE = /^\s*(?:const\s+\w+\s*=\s*)?(?:await\s+)?fetch\(/m;

  it('判据能变红：缺陷原形两处都命中', () => {
    expect(PRIVATE_BASE_RE.test(DEFECT)).toBe(true);
    expect(BARE_FETCH_RE.test(DEFECT)).toBe(true);
  });

  it('不误报：收口后的 dict.ts / glossary.ts 两处都不命中', () => {
    for (const rel of ['dict.ts', 'glossary.ts']) {
      const src = readFileSync(`${SRC_DIR}/${rel}`, 'utf8');
      expect(PRIVATE_BASE_RE.test(src), `${rel} 不得再私设 API_BASE 常量`).toBe(false);
      expect(BARE_FETCH_RE.test(src), `${rel} 不得再自留裸 fetch 传输腿`).toBe(false);
    }
  });
});
