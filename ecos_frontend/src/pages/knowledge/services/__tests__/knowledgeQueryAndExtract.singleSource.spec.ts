// @vitest-environment node
/**
 * knowledgeQueryApi / extract 单源用例（#143/B5-11d，台账 §13.2.4 C2′ 注⑫）
 *
 * <p>**本域缺陷本体（改前）**：`services/knowledgeQueryApi.ts` 五条裸 `fetch(`
 * ——1 条带 `authHeaders()`、4 条**故意不带身份**；`pages/knowledge/services/extract.ts`
 * 三条裸 `fetch(`——分片上传 + 两份**逐字重复**的导出日志腿（只差一句错误文案），
 * Bearer 由域内 `getAuthToken()` 手拼。八条腿都不在 httpClient 内 ⇒ C2′ 违例。</p>
 *
 * <p>**本批方案**：带身份的走 `rawAuthedUrlFetch`，匿名的走**本批新增**的具名匿名通道
 * `rawAnonUrlFetch`。不匿名折成带身份，理由见该通道 JSDoc——加 Bearer 会让网关按身份与
 * 准入等级裁决，应答面改变，属语义变更、不属「传输合流」授权范围；留裸 `fetch` 则判据出洞、
 * 且「这次请求不带身份」这个决定散落在域文件里不可核对。</p>
 *
 * <p>**D-B5-36（新登记，2026-10-19 源级取证）**：4 条匿名腿的目标端点在
 * `AnonymousEndpointRegistry`（permitAll 单源，实测 12 条登记 = 9 ANONYMOUS + 3 CLEARANCE_EXEMPT，
 * permitAll 展开 10 条）里**零条**命中——`knowledge/query`、`knowledge/rag`、
 * `ontology/compiler/compile` 不在任一条目内；`security/audit-logs` 亦不命中：
 * CLEARANCE_EXEMPT 登记的是 `/api/v1/security`（dualPath），而 `matches()` 逐段精确比对，
 * 3 段 pattern 不吃 4 段 path ⇒ 连准入豁免都不适用。
 * 唯一安全链 `sysman-impl/security/SecurityConfig.java:40-41` 的兜底是
 * `anyRequest().authenticated()` ⇒ **经网关/Express BFF（生产链路）这四条腿恒 401**；
 * vite dev 把 `/api/v1/knowledge`、`/api/v1/ontology` 直连 18086/18083 引擎、
 * 而引擎侧无 SecurityFilterChain（实测全仓仅 sysman-impl 与 datanet 两处声明）⇒ 仅 dev 可过。
 * 表现：Copilot 抽屉的知识检索/审计面在生产形态下静默空返回
 * （`fetchSecurityAuditLogs` 只把 `ok:false` 透出、不抛错 ⇒ 无错误态）。
 * **未起服 curl、未 live 复验**，本批不宣布这四条腿可用；处置（改带身份 = 语义变更 /
 * 补登记 permitAll = 后端契约变更）需裁决。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError } from '../../../../services/httpClient';
import {
  compileOntologyRaw,
  fetchSecurityAuditLogs,
  knowledgeQueryAnonymous,
  knowledgeQueryWithAuth,
  ragQueryRaw,
} from '../../../../services/knowledgeQueryApi';
import {
  exportExtractLog,
  fetchExportLogBody,
  uploadDocumentChunked,
} from '../extract';

const SRC_QUERY = readFileSync(
  fileURLToPath(new URL('../../../../services/knowledgeQueryApi.ts', import.meta.url)),
  'utf8',
);
const SRC_EXTRACT = readFileSync(fileURLToPath(new URL('../extract.ts', import.meta.url)), 'utf8');
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 与 `api-base-single-source` / `privateTransportRatchet` 同口径：注释行不参与判据。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

function makeRes(status: number, opts: { json?: unknown; blob?: string; contentType?: string } = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string): string | null => (k.toLowerCase() === 'content-type' ? opts.contentType ?? 'application/json' : null) },
    json: () => Promise.resolve(opts.json ?? {}),
    blob: () => Promise.resolve(new Blob([opts.blob ?? 'log-bytes'], { type: opts.contentType ?? 'text/plain' })),
  } as unknown as Response;
}

function stubFetch(res: Response | ((url: string, init?: RequestInit) => Response)) {
  const spy = vi.fn(async (input: string, init?: RequestInit) =>
    typeof res === 'function' ? res(input, init) : res,
  );
  vi.stubGlobal('fetch', spy);
  return spy;
}

function callsOf(spy: ReturnType<typeof stubFetch>) {
  return spy.mock.calls.map((c) => ({
    url: String(c[0]),
    init: (c[1] ?? {}) as RequestInit,
    headers: { ...((c[1]?.headers ?? {}) as Record<string, string>) },
  }));
}

function storageStub(): Storage {
  const m = new Map<string, string>();
  return {
    getItem: (k: string): string | null => m.get(k) ?? null,
    setItem: (k: string, v: string): void => void m.set(k, v),
    removeItem: (k: string): void => void m.delete(k),
    clear: (): void => m.clear(),
    key: (_index: number): string | null => null,
    get length() {
      return m.size;
    },
  } as unknown as Storage;
}

function stubSession(token: string | null) {
  const ls = storageStub();
  if (token !== null) ls.setItem('token', token);
  const dispatched: string[] = [];
  const win = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((ev: { type: string }): boolean => {
      dispatched.push(ev.type);
      return true;
    }),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', storageStub());
  vi.stubGlobal('window', win);
  return { ls, dispatched, win };
}

async function expectError(promise: Promise<unknown>): Promise<Error> {
  return promise.then((): never => {
    throw new Error('期望抛错，实际成功返回');
  }, (e: unknown): Error => e as Error);
}

beforeEach(() => {
  stubSession('tk-11d');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形 — 两文件私设传输归零（C2′ 棘轮第四批）', () => {
  it('knowledgeQueryApi.ts：零裸 fetch、零手拼 Bearer，1 条带身份 + 4 条走具名匿名通道', () => {
    const code = codeOnly(SRC_QUERY);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, /rawAuthedUrlFetch\(/)).toBe(1);
    expect(countMatches(code, /rawAnonUrlFetch\(/)).toBe(4);
  });

  it('extract.ts：零裸 fetch、零手拼 Bearer，三条腿共用车内一份导出日志请求体', () => {
    const code = codeOnly(SRC_EXTRACT);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, /rawAuthedUrlFetch\(/)).toBe(2);
    // 两条对外导出函数不再各自复制请求体 ⇒ `export-log` 字面量在本文件只出现一次
    expect(countMatches(code, /extract\/structured\/export-log/)).toBe(1);
  });

  it('两枚棘轮豁免位同批剪除；基线声明计数由基线实体自证（不钉死数字）', () => {
    expect(SRC_RATCHET).not.toContain("'services/knowledgeQueryApi.ts'");
    expect(SRC_RATCHET).not.toContain("'pages/knowledge/services/extract.ts'");

    const entryRe = /^\s*'([^']+)':\s*(\d+),$/gm;
    const entries = [...SRC_RATCHET.matchAll(entryRe)];
    expect(entries.length).toBeGreaterThan(0);
    const declaredCount = Number(SRC_RATCHET.match(/expect\(entries\.length\)\.toBe\((\d+)\)/)?.[1]);
    const declaredSum = Number(SRC_RATCHET.match(/n \+ c, 0\)\)\.toBe\((\d+)\)/)?.[1]);
    expect(declaredCount).toBe(entries.length);
    expect(declaredSum).toBe(entries.reduce((n, m) => n + Number(m[2]), 0));
  });
});

describe('knowledgeQueryApi — 身份面逐腿保持（1 带 4 不带）', () => {
  it('knowledgeQueryWithAuth：URL/方法/体不变，带 Bearer + Content-Type，非 2xx 也照旧返回整包 JSON', async () => {
    const spy = stubFetch(makeRes(500, { json: { success: false, message: '引擎不可用' } }));
    const out = await knowledgeQueryWithAuth({ q: '四季营收' });
    const [call] = callsOf(spy);
    expect(call.url).toBe('/api/v1/knowledge/query');
    expect(call.init.method).toBe('POST');
    expect(call.init.body).toBe(JSON.stringify({ q: '四季营收' }));
    expect(call.headers.Authorization).toBe('Bearer tk-11d');
    expect(call.headers['Content-Type']).toBe('application/json');
    // 改前无 ok 判定 ⇒ 错误信封原样交给调用方（AIPKnowledgeView 自己读 success）
    expect(out).toEqual({ success: false, message: '引擎不可用' });
  });

  it('四条匿名腿：会话里有 token 也**不得**出现 Authorization（匿名面被改 = 语义变更，本批禁止）', async () => {
    const spy = stubFetch(makeRes(200, { json: { data: {} } }));
    await knowledgeQueryAnonymous({ q: 'a' });
    await fetchSecurityAuditLogs();
    await ragQueryRaw({ query: 'q' });
    await compileOntologyRaw({ ontologyId: 'o1' });
    const list = callsOf(spy);
    expect(list).toHaveLength(4);
    for (const c of list) expect(c.headers.Authorization).toBeUndefined();
    expect(list[0].url).toBe('/api/v1/knowledge/query');
    expect(list[0].headers['Content-Type']).toBe('application/json');
    expect(list[1].url).toBe('/api/v1/security/audit-logs');
    expect(list[1].init.method ?? 'GET').toBe('GET');
    expect(list[1].headers['Content-Type']).toBeUndefined();
    expect(list[2].url).toBe('/api/v1/knowledge/rag');
    expect(list[3].url).toBe('/api/v1/ontology/compiler/compile');
  });

  it('ragQueryRaw / compileOntologyRaw：文案口径 = errorMessage ?? `HTTP ${status}`', async () => {
    stubFetch(makeRes(401));
    await expect(ragQueryRaw({ query: 'q' })).rejects.toThrow('HTTP 401');
    await expect(compileOntologyRaw({ ontologyId: 'o' })).rejects.toThrow('HTTP 401');

    stubFetch(makeRes(403));
    await expect(ragQueryRaw({ query: 'q' }, 'RAG 检索失败')).rejects.toThrow('RAG 检索失败');
    await expect(compileOntologyRaw({ ontologyId: 'o' }, '本体编译失败')).rejects.toThrow('本体编译失败');
  });

  it('fetchSecurityAuditLogs：ok:false 不解析响应体（改前口径），ok:true 才透出 json', async () => {
    let jsonCalls = 0;
    const res401 = makeRes(401);
    const originJson = res401.json.bind(res401);
    res401.json = (): Promise<unknown> => {
      jsonCalls += 1;
      return originJson();
    };
    stubFetch(res401);
    await expect(fetchSecurityAuditLogs()).resolves.toEqual({ ok: false, json: undefined });
    expect(jsonCalls).toBe(0);

    stubFetch(makeRes(200, { json: { data: [{ id: 'log-1' }] } }));
    const ok = await fetchSecurityAuditLogs();
    expect(ok.ok).toBe(true);
    expect(ok.json).toEqual({ data: [{ id: 'log-1' }] });
  });

  it('匿名腿网络失败同样归一 NetworkError + ecos-network-down（单源第 1 条行为差）', async () => {
    const { dispatched } = stubSession('tk-11d');
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const err = await expectError(ragQueryRaw({ query: 'q' }));
    expect(err).toBeInstanceOf(NetworkError);
    expect(dispatched).toContain('ecos-network-down');
  });

  it('匿名通道 401/403 无会话副作用：不清 token、不跳 #/login（裸通道取舍）', async () => {
    const { ls, win } = stubSession('tk-11d');
    stubFetch(makeRes(401));
    // 改前这四条腿对 401 就没有任何动作（连状态都不看，`fetchSecurityAuditLogs` 只透出 ok）
    // ⇒ 单源的会话副作用必须**不**作用到匿名腿上，否则匿名面被换成「跳登录」。
    await knowledgeQueryAnonymous({ q: 'a' });
    await ragQueryRaw({ query: 'q' }).catch((): undefined => undefined);
    expect(ls.getItem('token')).toBe('tk-11d');
    expect(win.location.hash).toBe('');
  });
});

describe('extract 三条腿 — 上传/导出形态原样', () => {
  /** 12MB 文件按 5MB 切片 = 3 片（末片 2MB） */
  function fakeFile(size = 12 * 1024 * 1024): File {
    return {
      size,
      name: 'up.pdf',
      slice: (start: number, end: number) => new Blob(['x'.repeat(end - start)]),
    } as unknown as File;
  }

  it('分片上传：三片三次请求，带 Bearer 且**不带** Content-Type（multipart boundary 由浏览器自造）', async () => {
    const spy = stubFetch(makeRes(200, { json: { success: true } }));
    const ratios: number[] = [];
    const out = await uploadDocumentChunked(fakeFile(), (r) => ratios.push(r));
    const list = callsOf(spy);
    expect(list).toHaveLength(3);
    for (const c of list) {
      expect(c.url).toBe('/api/v1/knowledge/extract/upload');
      expect(c.init.method).toBe('POST');
      expect(c.headers.Authorization).toBe('Bearer tk-11d');
      expect(c.headers['Content-Type']).toBeUndefined();
      expect(c.init.body).toBeInstanceOf(FormData);
      expect((c.init.body as FormData).get('totalChunks')).toBe('3');
    }
    expect(list[2].init.body).toBeInstanceOf(FormData);
    expect((list[2].init.body as FormData).get('chunkIndex')).toBe('2');
    expect(ratios).toEqual([1 / 3, 2 / 3, 1]);
    expect(out.taskId).toBe(out.fileId);
    expect(out.fileId).toMatch(/^doc-\d+-/);
  });

  it('某片 HTTP 非 2xx ⇒ 抛 `chunk i/n failed: status` 并停止后续分片（改前口径）', async () => {
    const spy = stubFetch((_url, init) => {
      const idx = Number((init?.body as FormData).get('chunkIndex'));
      return makeRes(idx === 1 ? 413 : 200, { json: { success: true } });
    });
    const err = await expectError(uploadDocumentChunked(fakeFile(), () => undefined));
    expect(err.message).toBe('chunk 1/3 failed: 413');
    expect(spy.mock.calls).toHaveLength(2);
  });

  it('两份导出函数共用一条请求腿：URL/方法/体一致，blob 原样返回，文案各自保留', async () => {
    const spy = stubFetch(makeRes(200, { blob: 'LOG', contentType: 'text/plain' }));
    const b1 = await fetchExportLogBody('job-1');
    const b2 = await exportExtractLog('job-1');
    expect(b1).toBeInstanceOf(Blob);
    expect(b2).toBeInstanceOf(Blob);
    expect(await b2.text()).toBe('LOG');
    const list = callsOf(spy);
    expect(list).toHaveLength(2);
    for (const c of list) {
      expect(c.url).toBe('/api/v1/knowledge/extract/structured/export-log');
      expect(c.init.method).toBe('POST');
      expect(c.init.body).toBe(JSON.stringify({ jobId: 'job-1' }));
      expect(c.headers.Authorization).toBe('Bearer tk-11d');
      expect(c.headers['Content-Type']).toBe('application/json');
    }

    stubFetch(makeRes(500));
    await expect(fetchExportLogBody('job-1')).rejects.toThrow('export-log HTTP 500');
    await expect(exportExtractLog('job-1')).rejects.toThrow('extract log export failed: 500');
  });

  it('带身份腿的 headers 为逐键覆盖：调用方自定义头与 Bearer 并存（D-B5-34 整组替换缺陷不适用）', async () => {
    const spy = stubFetch(makeRes(200, { json: { ok: true } }));
    await knowledgeQueryWithAuth({ q: 'a' });
    const h = callsOf(spy)[0].headers;
    expect(h.Authorization).toBe('Bearer tk-11d');
    expect(h['Content-Type']).toBe('application/json');
  });
});
