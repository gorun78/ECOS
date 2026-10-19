// @vitest-environment node
/**
 * caseLibraryApi / copilotApi / datanetMetadataApi 单源 + 分态用例（#143/B5-11e，台账 §13.2.4 C2′ 注）
 *
 * <p>**本域缺陷本体**（改前）：三文件共 8 条私设传输腿，全部是裸 `await fetch(` +
 * 域内自拼 URL：`services/caseLibraryApi.ts` 四腿（基址私设 `API_BASE = "/cases"`，
 * 与单源 `API_BASE = "/api"` 同名不同值 ⇒ 读者极易误判它走网关 `/api` 族）、
 * `services/copilotApi.ts` 两腿（字面 `/api/v1/agent/copilot/…`）、
 * `services/datanetMetadataApi.ts` 两腿（字面 `/api/v1/datanet/metadata/…`）。
 * 八腿**改前都不带 `Authorization`**（三文件头注释各自写明「无鉴权头」）。</p>
 *
 * <p>**为什么匿名腿也要收**：C2′ 收的是「传输封装散落」，不是「所有请求必须带身份」。
 * 留裸 `fetch` 等于在判据上打洞；折进带 Bearer 的通道则会把这八条腿从「匿名面」搬到
 * 「identity + clearance 裁决面」，那是语义变化、不在传输批授权内。故八腿统一改走
 * #143/B5-11d 订立的具名匿名通道 `rawAnonUrlFetch`——「这次不带身份」成为可 grep 的显式决定。</p>
 *
 * <p>**八腿可达性取证（源级 + 配置级两面；本批未起服 curl、未 live 复验，不宣布功能可用）**：
 * ① `copilotApi` 两腿的后端映射**实存**（ai-engine-impl `CopilotController`
 * @ `/api/v1/agent/copilot`，`@GetMapping("/quick-questions")` + `@PostMapping("/chat")`）；
 * ② `datanetMetadataApi` 两腿的映射**实存**（data-engine-impl `MetadataController`
 * 双路径 `{"/api/datanet/metadata","/api/v1/datanet/metadata"}`，其下
 * `@GetMapping("/resources/all")` + `@GetMapping("/preview/{resourceId}")`）；
 * ①② 两条前缀都不在 `AnonymousEndpointRegistry` 的 ANONYMOUS 面 ⇒ 按 sysman-impl
 * `SecurityConfig` 的 `anyRequest().authenticated()` 走网关必 401 —— 与 **D-B5-36**
 * 同族（映射实存、只缺身份面），本批把该登记的匿名腿清单从 4 条扩到 8 条。
 * ③ `caseLibraryApi` 四腿则属另一族：全仓 `ecos_backend` 的 `*.java`/`*.yml` 搜
 * `/cases` 与 `*Case*Controller` **零命中**，且 `config/route-manifest.ts` 的 `/cases`
 * 条目是 `viteFallback` 而 BFF 无对应显式承流（同文件构建期校验 ③b 明写这一历史事实，
 * 并禁止再增）⇒ 生产链路只会被 SPA 兜底吞掉。登记为 **D-B5-37**（死腿，D-B5-33 同族）。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError } from '../httpClient';
import {
  fetchCaseDetail,
  fetchCaseLibrary,
  recordCase,
  searchCaseLibrary,
} from '../caseLibraryApi';
import {
  copilotChat,
  fetchCopilotQuickQuestions,
} from '../copilotApi';
import {
  fetchDatanetPreview,
  fetchDatanetResourcesAll,
} from '../datanetMetadataApi';

const SRC_CASES = readFileSync(fileURLToPath(new URL('../caseLibraryApi.ts', import.meta.url)), 'utf8');
const SRC_COPILOT = readFileSync(fileURLToPath(new URL('../copilotApi.ts', import.meta.url)), 'utf8');
const SRC_DATANET = readFileSync(fileURLToPath(new URL('../datanetMetadataApi.ts', import.meta.url)), 'utf8');
const SRC_KQAPI = readFileSync(fileURLToPath(new URL('../knowledgeQueryApi.ts', import.meta.url)), 'utf8');
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);
const SRC_APIBASE = readFileSync(
  fileURLToPath(new URL('../../__tests__/api-base-single-source.test.ts', import.meta.url)),
  'utf8',
);

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization:\s*`?Bearer/;
const HARDCODED_API = /["'`]\/api\b/;
const ANON_CHANNEL = /rawAnonUrlFetch\s*\(/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 与 `privateTransportRatchet` / `api-base-single-source` 同口径：注释行不参与判据。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? (spec.body === undefined ? '' : JSON.stringify(spec.body));
  const map = new Map<string, string>();
  if (!map.has('content-type')) map.set('content-type', 'application/json');
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string): string | null => map.get(k.toLowerCase()) ?? null },
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)),
  } as unknown as Response;
}

function stubFetch(res: Response) {
  const spy = vi.fn(async (_input: string, _init?: RequestInit) => res);
  vi.stubGlobal('fetch', spy);
  return spy;
}

function lastCall(spy: ReturnType<typeof stubFetch>) {
  const call = spy.mock.calls[spy.mock.calls.length - 1];
  return { url: String(call[0]), init: (call[1] ?? {}) as RequestInit };
}

function headersOf(init: RequestInit): Record<string, string> {
  return { ...((init.headers ?? {}) as Record<string, string>) };
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
  const dispatched: Array<{ type: string; detail?: unknown }> = [];
  const win = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((ev: { type: string; detail?: unknown }): boolean => {
      dispatched.push({ type: ev.type, detail: ev.detail });
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

async function expectError(promise: Promise<unknown>): Promise<unknown> {
  return promise.then((): never => {
    throw new Error('期望抛错，实际成功返回');
  }, (e: unknown): unknown => e);
}

beforeEach(() => {
  stubSession('tk-11e');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形 — 三文件私设传输归零（C2′ 棘轮第五批）', () => {
  it('caseLibraryApi.ts：零裸 fetch、零 Bearer 面，四腿全部走具名匿名通道', () => {
    const code = codeOnly(SRC_CASES);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, ANON_CHANNEL)).toBe(4);
  });

  it('copilotApi.ts / datanetMetadataApi.ts：各两腿走具名匿名通道，零裸 fetch', () => {
    for (const src of [SRC_COPILOT, SRC_DATANET]) {
      const code = codeOnly(src);
      expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
      expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
      expect(countMatches(code, ANON_CHANNEL)).toBe(2);
    }
  });

  it('三文件 + knowledgeQueryApi 的 "/api" 字面量归零：基址一律 API_BASE 派生', () => {
    for (const src of [SRC_COPILOT, SRC_DATANET, SRC_KQAPI]) {
      expect(countMatches(codeOnly(src), HARDCODED_API)).toBe(0);
      expect(codeOnly(src)).toContain('API_BASE');
    }
    // caseLibrary 基址是 /cases（不属 /api 族），必须**不**拼 API_BASE，否则换掉承流路径
    const cases = codeOnly(SRC_CASES);
    expect(cases).not.toContain('API_BASE');
    expect(cases).toContain("const CASES_BASE = '/cases'");
  });

  it('两枚棘轮/基线豁免位与 api-base 基线同批剪除；棘轮声明计数由基线实体自证（不钉死数字）', () => {
    for (const rel of [
      "'services/caseLibraryApi.ts'",
      "'services/copilotApi.ts'",
      "'services/datanetMetadataApi.ts'",
    ]) {
      expect(SRC_RATCHET).not.toContain(rel);
      expect(SRC_APIBASE).not.toContain(rel);
    }
    expect(SRC_APIBASE).not.toContain("'services/knowledgeQueryApi.ts'");
    // extract.ts 仍有 lineage/integration 两族字面量 ⇒ 不许冒充已收口
    expect(SRC_APIBASE).toContain("'pages/knowledge/services/extract.ts'");

    const entryRe = /^\s*'([^']+)':\s*(\d+),$/gm;
    const entries = [...SRC_RATCHET.matchAll(entryRe)];
    // 原此处 `toBeGreaterThan(0)` 是把「基线仍非空」当本批不变量——#143/B5-15 存量清零后
    // 该假设失效（终态就是空集）。自证面改由下面两条承担：声明数字必须等于实体条目数与和，
    // 空集时两边同为 0，仍然检得出「剪代码不剪声明」这类漂移。
    const declaredCount = Number(SRC_RATCHET.match(/expect\(entries\.length\)\.toBe\((\d+)\)/)?.[1]);
    const declaredSum = Number(SRC_RATCHET.match(/n \+ c, 0\)\)\.toBe\((\d+)\)/)?.[1]);
    expect(declaredCount).toBe(entries.length);
    expect(declaredSum).toBe(entries.reduce((n, m) => n + Number(m[2]), 0));
  });
});

describe('caseLibraryApi 四腿 — URL 逐字 + 匿名面 + 无 ok 判定原样', () => {
  it('四条 URL 逐字（/cases 族，改前改后同串）且都不带 Authorization', async () => {
    const spy = stubFetch(makeRes({ body: { data: [] } }));
    await fetchCaseLibrary();
    expect(lastCall(spy).url).toBe('/cases');
    await searchCaseLibrary('库存 周转', 7);
    expect(lastCall(spy).url).toBe('/cases/search?q=%E5%BA%93%E5%AD%98%20%E5%91%A8%E8%BD%AC&k=7');
    await fetchCaseDetail(42);
    expect(lastCall(spy).url).toBe('/cases/42');
    await recordCase({ title: 't', tags: ['a'] });
    const rec = lastCall(spy);
    expect(rec.url).toBe('/cases/record');
    expect(rec.init.method).toBe('POST');
    expect(rec.init.body).toBe(JSON.stringify({ title: 't', tags: ['a'] }));
    expect(headersOf(rec.init)['Content-Type']).toBe('application/json');
    for (const call of spy.mock.calls) {
      expect(headersOf((call[1] ?? {}) as RequestInit).Authorization).toBeUndefined();
    }
  });

  it('非 2xx 仍返回整包 JSON（改前就没有 ok 判定，合流不得顺手加）', async () => {
    stubFetch(makeRes({ status: 404, body: { error: 'case not found' } }));
    await expect(fetchCaseDetail(9)).resolves.toEqual({ error: 'case not found' });
  });

  it('响应体非 JSON（生产链路被 SPA 兜底吞掉时的 HTML）⇒ fail-loud 抛 SyntaxError，不塌成 null', async () => {
    stubFetch(makeRes({ status: 200, raw: '<!doctype html><html><body>index</body></html>' }));
    const err = await expectError(fetchCaseLibrary());
    expect(err).toBeInstanceOf(Error);
  });
});

describe('copilotApi 两腿 — {ok,json} 形态原样（非 2xx 不抛错）', () => {
  it('两条 URL 逐字（API_BASE 派生后与改前同串），GET 腿不带 Content-Type', async () => {
    const spy = stubFetch(makeRes({ body: { questions: ['q1'] } }));
    await fetchCopilotQuickQuestions('ge');
    expect(lastCall(spy).url).toBe('/api/v1/agent/copilot/quick-questions?agentId=agent-ge');
    expect((lastCall(spy).init.method ?? 'GET')).toBe('GET');
    await copilotChat({ agentId: 'agent-ge', message: '你好', sessionId: 's-1' });
    const chat = lastCall(spy);
    expect(chat.url).toBe('/api/v1/agent/copilot/chat');
    expect(chat.init.method).toBe('POST');
    expect(chat.init.body).toBe(JSON.stringify({ agentId: 'agent-ge', message: '你好', sessionId: 's-1' }));
    for (const call of spy.mock.calls) {
      expect(headersOf((call[1] ?? {}) as RequestInit).Authorization).toBeUndefined();
    }
  });

  it('ok:false ⇒ 不调用 json()（只透出 ok，分态留给 CopilotPanel）；ok:true ⇒ 解析整包', async () => {
    let jsonCalls = 0;
    const fail = makeRes({ status: 500, body: {} });
    fail.json = (): Promise<unknown> => {
      jsonCalls += 1;
      return Promise.resolve({});
    };
    stubFetch(fail);
    const res = await fetchCopilotQuickQuestions('zhi');
    expect(res).toEqual({ ok: false, json: undefined });
    expect(jsonCalls).toBe(0);

    stubFetch(makeRes({ status: 201, body: { reply: 'ok' } }));
    await expect(copilotChat({ agentId: 'a', message: 'm', sessionId: 's' })).resolves.toEqual({
      ok: true,
      json: { reply: 'ok' },
    });
  });
});

describe('datanetMetadataApi 两腿 — 非 2xx 抛 `HTTP ${status}` 文案原样', () => {
  it('两条 URL 逐字；limit 缺省 50', async () => {
    const spy = stubFetch(makeRes({ body: { resources: [] } }));
    await fetchDatanetResourcesAll();
    expect(lastCall(spy).url).toBe('/api/v1/datanet/metadata/resources/all');
    await fetchDatanetPreview('ds-1');
    expect(lastCall(spy).url).toBe('/api/v1/datanet/metadata/preview/ds-1?limit=50');
    await fetchDatanetPreview('ds-1', 10);
    expect(lastCall(spy).url).toBe('/api/v1/datanet/metadata/preview/ds-1?limit=10');
    expect(headersOf(lastCall(spy).init).Authorization).toBeUndefined();
  });

  it('401/500 ⇒ 抛 Error(`HTTP <status>`)，且不因单源而清会话或跳登录', async () => {
    const { ls, win } = stubSession('tk-11e');
    stubFetch(makeRes({ status: 500, raw: '' }));
    const err500 = (await expectError(fetchDatanetResourcesAll())) as Error & { cause?: unknown };
    expect(err500).toBeInstanceOf(Error);
    expect(err500.message).toBe('HTTP 500');

    stubFetch(makeRes({ status: 401, raw: '' }));
    const err401 = (await expectError(fetchDatanetPreview('ds-2'))) as Error;
    expect(err401.message).toBe('HTTP 401');
    expect(ls.getItem('token')).toBe('tk-11e');
    expect(win.location.hash).toBe('');
  });

  it('ok 但响应体非 JSON ⇒ resp.json() 抛错（改前同形，不折成 null 读成「后端说没数据」）', async () => {
    stubFetch(makeRes({ status: 200, raw: '<html>502 Bad Gateway</html>' }));
    const err = await expectError(fetchDatanetResourcesAll());
    expect(err).toBeInstanceOf(Error);
  });
});

describe('匿名通道共同面 — 网络归一与「不继承会话副作用」', () => {
  it('八腿任一网络层失败 ⇒ 归一 NetworkError + ecos-network-down（改前抛裸 TypeError 且无广播）', async () => {
    const { dispatched } = stubSession('tk-11e');
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    // 逐条 await（用 thunk 而非先建 8 个 Promise）：一次性并发会让先失败的腿
    // 在被 await 之前就落进 unhandledRejection，测出来的红与本判据无关。
    const legs: Array<() => Promise<unknown>> = [
      () => fetchCaseLibrary(),
      () => searchCaseLibrary('q'),
      () => fetchCaseDetail(1),
      () => recordCase({ title: 'x' }),
      () => fetchCopilotQuickQuestions('ge'),
      () => copilotChat({ agentId: 'a', message: 'm', sessionId: 's' }),
      () => fetchDatanetResourcesAll(),
      () => fetchDatanetPreview('ds'),
    ];
    for (const leg of legs) {
      const err = await expectError(leg());
      expect(err).toBeInstanceOf(NetworkError);
    }
    expect(dispatched.map((d) => d.type)).toContain('ecos-network-down');
  });

  it('八腿对 401 都不做会话动作（清 token / 跳 #/login 属带身份通道，匿名腿不得继承）', async () => {
    const { ls, win } = stubSession('tk-11e');
    stubFetch(makeRes({ status: 401, raw: '' }));
    await fetchCaseLibrary().catch((): undefined => undefined);
    await fetchCopilotQuickQuestions('ge').catch((): undefined => undefined);
    await fetchDatanetResourcesAll().catch((): undefined => undefined);
    expect(ls.getItem('token')).toBe('tk-11e');
    expect(win.location.hash).toBe('');
  });
});
