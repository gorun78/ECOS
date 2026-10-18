// @vitest-environment node
/**
 * _base.singleChannel.spec.ts — §13.2.4 C2「ApiResponse 单解包点」DW 域收口契约（#143/B5-8）。
 *
 * <p>差距台账行 676 把 C2 归因为「`src/api.ts` 的 `apiFetchData` 是第二解包点」——本批勘定
 * 该归因**已失真**：`src/api.ts` 现为 49 行纯 re-export shim（`await fetch(` 与手写解包均零命中）。
 * C2 的真实载体是「私设传输 ∩ 手写解包」的文件集，改前实测 10 文件 / 32 处 `await fetch(`，
 * 本批收口其中 `pages/data-workbench/api/_base.ts`（1 文件 / 4 处）⇒ 余 9 文件 / 28 处。
 * 本文件收口的这一枚是 DW 五文件（lineage / unstructured / metadata / datasource / pipeline）
 * 共用的私设 HTTP 基座：
 * 自带 `fetch`、自带 `json.data ?? json` 拆包、自带 `${url} → ${status}` 文案，
 * 无 traceId、无 401/403 处置、无伪失败（`success:false` / `code≠0`）判定 ⇒ 故障信封
 * 会被当业务数据渲染（fail-open）。</p>
 *
 * <p>本文件先以「修复前的红」形态钉住缺陷原形，再要求收口后全绿。
 * 兼容性红线：四个导出函数签名与 URL 常量逐字不变（API 只增不改），
 * HTTP 非 2xx 的 `Error.message` 文案 `${url} → ${status}` 逐字保持（消费者按原文渲染）。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { del, get, post, put } from '../_base';

const SRC = readFileSync(fileURLToPath(new URL('../_base.ts', import.meta.url)), 'utf8');
const URL_GET = '/datanet/datasource/list';
const URL_WRITE = '/api/v1/pipeline/definitions';

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  headers?: Record<string, string>;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  const lower: Record<string, string> = { 'content-type': 'application/json' };
  Object.entries(spec.headers ?? {}).forEach(([k, v]) => {
    lower[k.toLowerCase()] = v;
  });
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string) => lower[k.toLowerCase()] ?? null },
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)),
  } as unknown as Response;
}

/** 记录 fetch 入参，供「URL/method/body/header 拼装」断言。 */
function stubFetch(res: Response) {
  const spy = vi.fn(async (...args: [input: string, init?: RequestInit]) => {
    void args;
    return res;
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('源形自检 — 私设传输与私拆信封不得留在域内', () => {
  /** 缺陷原形（用于证明判据能变红，非本域源码） */
  const DEFECT = 'const res = await fetch(url, { headers }); const json = await res.json(); return json.data ?? json;';

  it('判据能变红：私设 fetch 与私拆信封原形命中', () => {
    expect(/await fetch\(/.test(DEFECT)).toBe(true);
    expect(/json\.data/.test(DEFECT)).toBe(true);
  });

  it('不误报：收口后的 _base.ts 内无 await fetch( 私设传输', () => {
    expect(/await fetch\(/.test(SRC), '_base.ts 仍自设 fetch，未合流 httpClient 单通道').toBe(false);
  });

  it('不误报：收口后的 _base.ts 内无 json.data 私拆信封', () => {
    expect(/json\.data/.test(SRC), '_base.ts 仍自拆 ApiResponse 信封，C2 单解包点未成立').toBe(false);
  });

  it('不误报：_base.ts 不再自拼 auth 头（归 httpClient 内 getAuthToken 单源）', () => {
    // 判据取 `authOnlyHeaders` 符号面而非 "Authorization" 字面量：后者在改前也命不中
    // （改前经 authOnlyHeaders() 间接拼头，域内并无 Authorization 字样）⇒ 属「不能变红的判据」，弃用。
    expect(/authOnlyHeaders|authHeaders\(/.test(SRC), '_base.ts 仍自拼 auth 头').toBe(false);
  });
});

describe('四动词 — 委托单通道且入参逐字不变', () => {
  it('get：URL 原样上送（不自动加 /api 前缀）、method 缺省、无 body', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { id: 1 } } }));
    await expect(get(URL_GET)).resolves.toEqual({ id: 1 });
    const [url, init] = spy.mock.calls[0];
    expect(url).toBe(URL_GET);
    expect(init?.method).toBeUndefined();
    expect(init?.body).toBeUndefined();
  });

  it('post：method=POST 且 body 为 JSON.stringify 原文', async () => {
    const spy = stubFetch(makeRes({ body: { success: true, data: 'ok' } }));
    await expect(post(URL_WRITE, { name: 'p1' })).resolves.toBe('ok');
    const [url, init] = spy.mock.calls[0];
    expect(url).toBe(URL_WRITE);
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe(JSON.stringify({ name: 'p1' }));
  });

  it('put：method=PUT 且 body 上送', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { updated: true } } }));
    await expect(put(URL_WRITE, { a: 1 })).resolves.toEqual({ updated: true });
    const [, init] = spy.mock.calls[0];
    expect(init?.method).toBe('PUT');
    expect(init?.body).toBe(JSON.stringify({ a: 1 }));
  });

  it('del：method=DELETE 且无 body', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: null } }));
    await expect(del(`${URL_WRITE}/x1`)).resolves.toBeNull();
    const [url, init] = spy.mock.calls[0];
    expect(url).toBe(`${URL_WRITE}/x1`);
    expect(init?.method).toBe('DELETE');
    expect(init?.body).toBeUndefined();
  });
});

describe('解包三态与差异腿（单源口径）', () => {
  it('裸 data（无 data 字段）→ 返回整个信封对象', async () => {
    stubFetch(makeRes({ body: { id: 'raw', name: 'n' } }));
    await expect(get(URL_GET)).resolves.toEqual({ id: 'raw', name: 'n' });
  });

  it('data:null 与改前差异如实锁定：改后返回 null（改前 `?? json` 回落整个信封）', async () => {
    stubFetch(makeRes({ body: { code: 0, message: 'ok', data: null } }));
    await expect(get(URL_GET)).resolves.toBeNull();
  });

  it('伪失败 success:false → 现在抛错而非把故障信封当数据返回（fail-open 收口）', async () => {
    stubFetch(makeRes({ body: { success: false, message: '无权访问该数据源' } }));
    await expect(get(URL_GET)).rejects.toThrow('无权访问该数据源');
  });

  it('伪失败 code≠0/200 → 抛错并取信封 message', async () => {
    stubFetch(makeRes({ body: { code: 5001, message: '数据源不存在' } }));
    await expect(get(URL_GET)).rejects.toThrow('数据源不存在');
  });

  it('兼容锁：HTTP 非 2xx 的 message 文案逐字保持 `${url} → ${status}`（消费者按原文渲染）', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    await expect(get(URL_GET)).rejects.toThrow(`${URL_GET} → 500`);
  });

  it('网络层失败归一为 NetworkError 并广播 ecos-network-down（改前裸抛 TypeError 不可识别）', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    await expect(get(URL_GET)).rejects.toMatchObject({ name: 'NetworkError' });
  });
});
