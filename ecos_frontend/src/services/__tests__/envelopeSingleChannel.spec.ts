// @vitest-environment node
/**
 * C2「ApiResponse 单解包点」最后两文件合流 —— cognitiveClient / scenarioBlueprintClient
 * （台账 §13.2.4 C2，#143/B5-10c-2）。
 *
 * <p>**缺陷本体**：两条域通道各自保留一份私设 `await fetch(` ＋ 自带解包腿
 * （`cognitiveClient#callJsonInternal` 与 `scenarioBlueprintClient#callBlueprint`），
 * 是 httpClient 之外的第五、第六个解包点。二者不并入 `apiFetchData` 的原因是真实的：
 * 它们各自带一张**按 HTTP status 与后端原始 `errorCode` 分类**的错误矩阵
 * （认知 7 态：404/400/503→SCHEMA_NOT_READY、501→STUB…；蓝图 409 迁移冲突族优先
 * `errorCode`、且 `code:201` 必须放行），而 `apiFetchData` 在解包前就把非 2xx 与
 * 信封失败一律折叠成抛错、且不透出响应体原文与 `errorCode` ⇒ 照搬会摧毁分类。</p>
 *
 * <p>**本批方案**：httpClient 增 additive 原语 `apiFetchEnvelope`（解包 + 取号 +
 * 整包/原文/信封字段一次取齐，除网络层失败外不抛错），两域客户端改读该通道，
 * 分类规则原样留在域内。同批收口 **D-B5-31**：蓝图取号腿只认 `X-Trace-Id`，
 * 而网关 `RequestContextFilter` 对每个响应写的是 `X-Request-Id` ⇒ 真机 traceId 恒缺。</p>
 *
 * <p>**必须锁住的三条等价性**：①错误矩阵逐条同码同态（含认知不容 `code:201`、
 * 蓝图容 `code:201` 且不查 `success` 这一组分态）；②解包形状同值（`data` 优先、
 * 否则整包、JSON 标量不塌成 null、非 JSON ⇒ null）；③文案兜底口径同值
 * （信封 `message` → 响应原文 → `HTTP <status>`）。</p>
 *
 * <p>**差异腿（合流必然带来的单源副作用，非等价迁移，需 live 复验）**：401 自此
 * 清会话并跳 `#/login`（但**仍按 401 分类**，不被折叠成网络态）、403 自此广播
 * `ecos-no-access`、网络故障自此广播 `ecos-network-down` 且抛出物为单源
 * NetworkError 口径、down 之后的成功腿自此广播 `ecos-network-up`。</p>
 *
 * <p>**兼容性红线**（不随本批变更，故逐条锁死）：URL 与 method/body 形态逐字不变、
 * 调用方自定义头**逐键覆盖**而非整组替换、无 token 时不发 `Authorization`、
 * 公开签名与错误类型（`CognitiveApiError`／`BlueprintApiError`）不变。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiFetchEnvelope } from '../httpClient';
import { callJson, callJsonWithTrace, CognitiveApiError } from '../cognitiveClient';
import {
  BlueprintApiError,
  blueprintActionUrl,
  blueprintCollectionUrl,
  createBlueprint,
  listBlueprints,
  transitionBlueprint,
} from '../scenarioBlueprintClient';

const SRC = {
  cognitive: readFileSync(fileURLToPath(new URL('../cognitiveClient.ts', import.meta.url)), 'utf8'),
  blueprint: readFileSync(fileURLToPath(new URL('../scenarioBlueprintClient.ts', import.meta.url)), 'utf8'),
  http: readFileSync(fileURLToPath(new URL('../httpClient.ts', import.meta.url)), 'utf8'),
};

const PRIVATE_TRANSPORT = /await fetch\(/;
/** C2 台账判据的字面形状（`data ??`） */
const HAND_UNPACK = /data\s*\?\?/;
/** 两域客户端改前的实际解包形状（三元式），与 HAND_UNPACK 并列锁 */
const TERNARY_UNPACK = /\.data\s*!==\s*undefined/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;

/** 缺陷原形（证明判据能变红，非本域源码） */
const DEFECT =
  'const res = await fetch(url, { headers }); const json = await res.json(); '
  + 'const o = json; return o.data !== undefined ? o.data : json; // Authorization: `Bearer ${t}`';

/** 收口形状（证明判据不误报） */
const CLEAN =
  "import { apiFetchEnvelope } from '../httpClient';\nconst out = await apiFetchEnvelope(url, init);\nreturn out.data;\n";

/** 计数用：`String.match(非全局正则)` 只返回首个命中，恒得 0/1 ⇒ 计数门必须强制带 g */
function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 按函数名切出腿体（两腿签名都以 `Promise<unknown>` 收尾，据此跳过带默认值的参数表） */
function sliceFn(src: string, name: string): string {
  const head = src.indexOf(`function ${name}(`);
  if (head < 0) throw new Error(`源中未找到函数 ${name}`);
  const tail = src.indexOf('Promise<unknown>', head);
  const bodyStart = src.indexOf('{', tail);
  let depth = 0;
  for (let i = bodyStart; i < src.length; i += 1) {
    const ch = src[i];
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return src.slice(bodyStart, i + 1);
    }
  }
  return src.slice(bodyStart);
}

interface ResSpec {
  status?: number;
  /** 响应体（对象/数组/标量）；与 raw 二选一 */
  body?: unknown;
  /** 原文响应体（非 JSON 场景） */
  raw?: string;
  /** 响应头（键按 fetch Headers 语义大小写不敏感查） */
  headers?: Record<string, string>;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? (spec.body === undefined ? '' : JSON.stringify(spec.body));
  const map = new Map<string, string>();
  Object.entries(spec.headers ?? {}).forEach(([k, v]) => map.set(k.toLowerCase(), v));
  if (!map.has('content-type')) map.set('content-type', 'application/json');
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (k: string): string | null => map.get(k.toLowerCase()) ?? null,
    },
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)),
  } as unknown as Response;
}

function headersOf(init: RequestInit): Record<string, string> {
  return (init.headers ?? {}) as Record<string, string>;
}

function stubFetch(res: Response | (() => Response)) {
  const spy = vi.fn(async (...args: [input: string, init?: RequestInit]) => {
    void args;
    return typeof res === 'function' ? res() : res;
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

function stubNetworkDown(message = 'Failed to fetch') {
  const spy = vi.fn(async (...args: [input: string, init?: RequestInit]) => {
    void args;
    throw new TypeError(message);
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

function lastCall(spy: ReturnType<typeof stubFetch>) {
  const call = spy.mock.calls[spy.mock.calls.length - 1];
  return { url: call[0], init: call[1] ?? {} };
}

function lastHeaders(spy: ReturnType<typeof stubFetch>) {
  return headersOf(lastCall(spy).init);
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

/**
 * 会话面桩：`getAuthToken()` 走 localStorage['token']；`handleAuthExpired()` 需要
 * `window.location.hash` + localStorage；三个网络/权限事件需要 `window.dispatchEvent`。
 * dispatched 记录事件 type ⇒ 差异腿可断言「单源副作用真的发生」。
 */
function stubSession(token: string | null) {
  const ls = storageStub();
  const ss = storageStub();
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
  vi.stubGlobal('sessionStorage', ss);
  vi.stubGlobal('window', win);
  return { win, ls, dispatched };
}

const URL_COG = '/api/v1/cognitive/minds/m1';
const SC = 'sc001';
const BP = 'bp1';

beforeEach(() => {
  stubSession('tk-1');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形自检 — C2 三面判据（含判据自身可红可绿的元校验）', () => {
  it('缺陷原形同时命中三面判据（判据非空转）', () => {
    expect(PRIVATE_TRANSPORT.test(DEFECT)).toBe(true);
    expect(HAND_UNPACK.test(DEFECT) || TERNARY_UNPACK.test(DEFECT)).toBe(true);
    expect(HAND_AUTH_HEADER.test(DEFECT)).toBe(true);
  });

  it('收口形状三面皆不命中（判据不误报）', () => {
    expect(PRIVATE_TRANSPORT.test(CLEAN)).toBe(false);
    expect(HAND_UNPACK.test(CLEAN)).toBe(false);
    expect(TERNARY_UNPACK.test(CLEAN)).toBe(false);
    expect(HAND_AUTH_HEADER.test(CLEAN)).toBe(false);
  });

  it('cognitiveClient.ts 全文件零私设传输', () => {
    expect(countMatches(SRC.cognitive, PRIVATE_TRANSPORT)).toBe(0);
  });

  it('callJsonInternal 腿体内零手写解包、零私拼 auth 头', () => {
    const leg = sliceFn(SRC.cognitive, 'callJsonInternal');
    expect(TERNARY_UNPACK.test(leg)).toBe(false);
    expect(HAND_UNPACK.test(leg)).toBe(false);
    expect(HAND_AUTH_HEADER.test(leg)).toBe(false);
  });

  it('callJsonInternal 腿体确实走 apiFetchEnvelope（不是换个姿势私设）', () => {
    expect(sliceFn(SRC.cognitive, 'callJsonInternal')).toContain('apiFetchEnvelope(');
  });

  it('scenarioBlueprintClient.ts 全文件零私设传输', () => {
    expect(countMatches(SRC.blueprint, PRIVATE_TRANSPORT)).toBe(0);
  });

  it('callBlueprint 腿体内零手写解包、零私拼 auth 头', () => {
    const leg = sliceFn(SRC.blueprint, 'callBlueprint');
    expect(TERNARY_UNPACK.test(leg)).toBe(false);
    expect(HAND_UNPACK.test(leg)).toBe(false);
    expect(HAND_AUTH_HEADER.test(leg)).toBe(false);
  });

  it('callBlueprint 腿体确实走 apiFetchEnvelope', () => {
    expect(sliceFn(SRC.blueprint, 'callBlueprint')).toContain('apiFetchEnvelope(');
  });

  it('解包面全部收在 httpClient 内（本门数得的两处即 apiFetchData 与 apiFetchEnvelope）', () => {
    expect(countMatches(SRC.http, TERNARY_UNPACK)).toBe(2);
    expect(typeof apiFetchEnvelope).toBe('function');
  });
});

describe('认知通道错误矩阵等价（改前逐条同码同态）', () => {
  it('404 + 信封 message ⇒ NOT_FOUND/404/原文案', async () => {
    stubFetch(makeRes({ status: 404, body: { message: '场景不存在' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect(e).toBeInstanceOf(CognitiveApiError);
    expect((e as CognitiveApiError).code).toBe('NOT_FOUND');
    expect((e as CognitiveApiError).status).toBe(404);
    expect((e as CognitiveApiError).message).toBe('场景不存在');
  });

  it('404 非 JSON 体 ⇒ message 落响应原文', async () => {
    stubFetch(makeRes({ status: 404, raw: '<html>not found</html>', headers: { 'content-type': 'text/html' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).message).toBe('<html>not found</html>');
  });

  it('404 空体 ⇒ `HTTP 404` 兜底', async () => {
    stubFetch(makeRes({ status: 404, raw: '' }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).message).toBe('HTTP 404');
  });

  it('400 ⇒ BAD_REQUEST', async () => {
    stubFetch(makeRes({ status: 400, body: { message: 'mind 不属场景' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('BAD_REQUEST');
    expect((e as CognitiveApiError).message).toBe('mind 不属场景');
  });

  it('503 + `schema not ready` ⇒ SCHEMA_NOT_READY', async () => {
    stubFetch(makeRes({ status: 503, body: { message: 'SCHEMA_NOT_READY: mind layer missing' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('SCHEMA_NOT_READY');
    expect((e as CognitiveApiError).status).toBe(503);
  });

  it('503 其他文案 ⇒ ENGINE_DISABLED（同码不同态不得合并）', async () => {
    stubFetch(makeRes({ status: 503, body: { message: 'cognitive engine disabled' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('ENGINE_DISABLED');
  });

  it('501 ⇒ STUB 且 stub=true（UI 据此渲染占位而非错误）', async () => {
    stubFetch(makeRes({ status: 501, body: { message: 'not implemented' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('STUB');
    expect((e as CognitiveApiError).stub).toBe(true);
  });

  it('其余非 2xx（500）⇒ HTTP_ERROR 并带 status', async () => {
    stubFetch(makeRes({ status: 500, raw: '' }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('HTTP_ERROR');
    expect((e as CognitiveApiError).status).toBe(500);
  });

  it('200 + success:false ⇒ BUSINESS_ERROR + 信封 message', async () => {
    stubFetch(makeRes({ status: 200, body: { success: false, message: '业务校验失败' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('BUSINESS_ERROR');
    expect((e as CognitiveApiError).message).toBe('业务校验失败');
  });

  it('200 + code:500 且无 message ⇒ `Error 500`', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 500 } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('BUSINESS_ERROR');
    expect((e as CognitiveApiError).message).toBe('Error 500');
  });

  it('200 + code:201 仍判业务错误（认知侧不容 201，与蓝图侧分态）', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 201, data: { id: 'x' } } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('BUSINESS_ERROR');
  });

  it('200 信封 {code:0,data:[…]} ⇒ 取 data 本体', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 0, data: [{ mindId: 'm1' }] } }));
    expect(await callJson(URL_COG)).toEqual([{ mindId: 'm1' }]);
  });

  it('200 裸载荷（无 data 字段）⇒ 整包回落，与改前 `o.data !== undefined ? o.data : body` 同值', async () => {
    stubFetch(makeRes({ status: 200, body: { rows: [{ a: 1 }] } }));
    expect(await callJson(URL_COG)).toEqual({ rows: [{ a: 1 }] });
  });

  it('200 JSON 标量 ⇒ 原样返回（不得塌成 null）', async () => {
    stubFetch(makeRes({ status: 200, raw: '5' }));
    expect(await callJson(URL_COG)).toBe(5);
  });

  it('200 非 JSON ⇒ null（改前 try/catch 保持 null 同值）', async () => {
    stubFetch(makeRes({ status: 200, raw: 'plain text', headers: { 'content-type': 'text/plain' } }));
    expect(await callJson(URL_COG)).toBeNull();
  });

  it('200 空体 ⇒ null 且不报业务错误', async () => {
    stubFetch(makeRes({ status: 200, raw: '' }));
    expect(await callJson(URL_COG)).toBeNull();
  });

  it('URL 与 method/body 逐字透传（合流不改请求形状）', async () => {
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0, data: null } }));
    await callJson(URL_COG, { method: 'POST', body: JSON.stringify({ a: 1 }) });
    expect(lastCall(spy).url).toBe(URL_COG);
    expect(lastCall(spy).init.method).toBe('POST');
    expect(lastCall(spy).init.body).toBe(JSON.stringify({ a: 1 }));
  });
});

describe('蓝图通道错误矩阵等价（按 status + 后端原始 errorCode 分类）', () => {
  it('409 + 已知 errorCode ⇒ 直接取该码并透出 rawErrorCode', async () => {
    stubFetch(makeRes({ status: 409, body: { errorCode: 'ILLEGAL_TRANSITION', message: '不可从 RELEASED 迁移' } }));
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect(e).toBeInstanceOf(BlueprintApiError);
    expect((e as BlueprintApiError).code).toBe('ILLEGAL_TRANSITION');
    expect((e as BlueprintApiError).rawErrorCode).toBe('ILLEGAL_TRANSITION');
    expect((e as BlueprintApiError).status).toBe(409);
    expect((e as BlueprintApiError).message).toBe('不可从 RELEASED 迁移');
  });

  it('409 无 errorCode ⇒ BLUEPRINT_STATE_CONFLICT（按 HTTP 归一）', async () => {
    stubFetch(makeRes({ status: 409, body: { message: '并发冲突' } }));
    const e = await transitionBlueprint(SC, BP, 'READY_FOR_APPROVAL').catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('BLUEPRINT_STATE_CONFLICT');
    expect((e as BlueprintApiError).rawErrorCode).toBeUndefined();
  });

  it('409 未识别 errorCode ⇒ 保留原始串到 rawErrorCode，禁吞成通用码', async () => {
    stubFetch(makeRes({ status: 409, body: { errorCode: 'FUTURE_CODE', message: 'x' } }));
    const e = await transitionBlueprint(SC, BP, 'ACTIVE').catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('BLUEPRINT_STATE_CONFLICT');
    expect((e as BlueprintApiError).rawErrorCode).toBe('FUTURE_CODE');
  });

  it('404 + errorCode BLUEPRINT_NOT_FOUND ⇒ 名码优先于 HTTP 归一', async () => {
    stubFetch(makeRes({ status: 404, body: { errorCode: 'BLUEPRINT_NOT_FOUND', message: '版本不存在' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('BLUEPRINT_NOT_FOUND');
  });

  it('400 ⇒ BAD_REQUEST；空体时 message 落 `HTTP 400`', async () => {
    stubFetch(makeRes({ status: 400, raw: '' }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('BAD_REQUEST');
    expect((e as BlueprintApiError).message).toBe('HTTP 400');
  });

  it('503 两态：`schema not ready` ⇒ SCHEMA_NOT_READY，其余 ⇒ ENGINE_DISABLED', async () => {
    stubFetch(makeRes({ status: 503, body: { message: 'schema not ready' } }));
    const a = await listBlueprints(SC).catch((x: unknown) => x);
    expect((a as BlueprintApiError).code).toBe('SCHEMA_NOT_READY');
    stubFetch(makeRes({ status: 503, body: { message: 'workspace service down' } }));
    const b = await listBlueprints(SC).catch((x: unknown) => x);
    expect((b as BlueprintApiError).code).toBe('ENGINE_DISABLED');
  });

  it('其余非 2xx（500）⇒ HTTP_ERROR 并带 status', async () => {
    stubFetch(makeRes({ status: 500, body: { message: 'boom' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('HTTP_ERROR');
    expect((e as BlueprintApiError).status).toBe(500);
    expect((e as BlueprintApiError).message).toBe('boom');
  });

  it('201 建稿 + code:201 ⇒ 放行（蓝图特有容差，禁与认知侧对齐成业务错误）', async () => {
    stubFetch(makeRes({ status: 201, body: { code: 201, data: { id: BP, status: 'DRAFT' } } }));
    expect(await createBlueprint(SC, { scenarioId: SC, goalStatement: '季度经营复盘' }))
      .toEqual({ id: BP, status: 'DRAFT' });
  });

  it('200 + code:400 ⇒ BUSINESS_ERROR', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 400, message: '内容非法' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('BUSINESS_ERROR');
    expect((e as BlueprintApiError).message).toBe('内容非法');
  });

  it('200 + success:false 但无 code ⇒ 不判业务错误（蓝图不查 success，逐字保持）', async () => {
    stubFetch(makeRes({ status: 200, body: { success: false, data: [{ id: BP }] } }));
    expect(await listBlueprints(SC)).toEqual([{ id: BP }]);
  });

  it('200 信封 {code:0,data:[…]} ⇒ asArray 取其数组', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 0, data: [{ id: 'b1' }, { id: 'b2' }] } }));
    expect(await listBlueprints(SC)).toEqual([{ id: 'b1' }, { id: 'b2' }]);
  });

  it('200 裸数组体 ⇒ 整包回落仍成数组（解包形状与改前同值）', async () => {
    stubFetch(makeRes({ status: 200, body: [{ id: 'b1' }] }));
    expect(await listBlueprints(SC)).toEqual([{ id: 'b1' }]);
  });

  it('动作腿 URL 逐字：`{API_BASE}/v1/business/scenarios/{sc}/blueprints/{id}/transition`', async () => {
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0, data: { id: BP } } }));
    await transitionBlueprint(SC, BP, 'RELEASED', '上线原因');
    expect(lastCall(spy).url).toBe(`${blueprintCollectionUrl(SC)}/${BP}/transition`);
    expect(lastCall(spy).init.method).toBe('POST');
    expect(lastCall(spy).init.body).toBe(JSON.stringify({ target: 'RELEASED', reason: '上线原因' }));
  });
});

describe('traceId 取号单源（含 D-B5-31 收口）', () => {
  it('认知：成功腿经 callJsonWithTrace 透出响应头 X-Request-Id', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 0, data: { ok: 1 } }, headers: { 'X-Request-Id': 'tr-1' } }));
    const r = await callJsonWithTrace(URL_COG);
    expect(r.traceId).toBe('tr-1');
    expect(r.data).toEqual({ ok: 1 });
  });

  it('认知：错误腿同样带 traceId（矩阵各腿共用同一次取号）', async () => {
    stubFetch(makeRes({ status: 404, body: { message: 'x' }, headers: { 'X-Request-Id': 'tr-2' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).traceId).toBe('tr-2');
  });

  it('蓝图：网关每响应写的是 X-Request-Id ⇒ traceId 必须取到（D-B5-31 改前恒 undefined）', async () => {
    stubFetch(makeRes({ status: 409, body: { message: '冲突' }, headers: { 'X-Request-Id': 'tr-3' } }));
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect((e as BlueprintApiError).traceId).toBe('tr-3');
  });

  it('蓝图：顺位由单源裁决（X-Request-Id 优先于历史口径 X-Trace-Id）', async () => {
    stubFetch(makeRes({ status: 409, body: { message: 'x' }, headers: { 'X-Request-Id': 'tr-new', 'X-Trace-Id': 'tr-old' } }));
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect((e as BlueprintApiError).traceId).toBe('tr-new');
  });

  it('蓝图：仅历史头 X-Trace-Id 时仍兼容取到', async () => {
    stubFetch(makeRes({ status: 404, body: { message: 'x' }, headers: { 'X-Trace-Id': 'tr-old' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).traceId).toBe('tr-old');
  });

  it('蓝图：无响应头时回落信封 traceId', async () => {
    stubFetch(makeRes({ status: 404, body: { message: 'x', traceId: 'tr-env' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).traceId).toBe('tr-env');
  });

  it('蓝图：两头与信封皆无 ⇒ undefined（禁伪造号）', async () => {
    stubFetch(makeRes({ status: 404, body: { message: 'x' } }));
    const e = await listBlueprints(SC).catch((x: unknown) => x);
    expect((e as BlueprintApiError).traceId).toBeUndefined();
  });
});

describe('差异腿 — 合流后单源副作用（改前形状见文件头注）', () => {
  it('认知 401 ⇒ 清会话并跳 #/login，但分类仍是 HTTP_ERROR/401（不折叠成网络态）', async () => {
    const { win, ls } = stubSession('tk-1');
    stubFetch(makeRes({ status: 401, body: { message: 'unauthorized' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('HTTP_ERROR');
    expect((e as CognitiveApiError).status).toBe(401);
    expect(win.location.hash).toBe('#/login');
    expect(ls.getItem('token')).toBeNull();
  });

  it('蓝图 401 ⇒ 同样退会话，分类仍是 HTTP_ERROR/401', async () => {
    const { win } = stubSession('tk-1');
    stubFetch(makeRes({ status: 401, body: { message: 'unauthorized' } }));
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('HTTP_ERROR');
    expect((e as BlueprintApiError).status).toBe(401);
    expect(win.location.hash).toBe('#/login');
  });

  it('认知 403 ⇒ 广播 ecos-no-access 且不清会话，落 HTTP_ERROR/403', async () => {
    const { win, ls, dispatched } = stubSession('tk-1');
    stubFetch(makeRes({ status: 403, body: { message: 'forbidden' } }));
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('HTTP_ERROR');
    expect((e as CognitiveApiError).status).toBe(403);
    expect(dispatched).toContain('ecos-no-access');
    expect(ls.getItem('token')).toBe('tk-1');
    expect(win.location.hash).toBe('');
  });

  it('蓝图 403 ⇒ 广播 ecos-no-access，落 HTTP_ERROR/403', async () => {
    const { dispatched } = stubSession('tk-1');
    stubFetch(makeRes({ status: 403, body: { message: 'forbidden' } }));
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('HTTP_ERROR');
    expect(dispatched).toContain('ecos-no-access');
  });

  it('网络失败 ⇒ 广播 ecos-network-down（改前只有裸 TypeError、无横幅）', async () => {
    const { dispatched } = stubSession('tk-1');
    stubNetworkDown();
    const e = await callJson(URL_COG).catch((x: unknown) => x);
    expect((e as CognitiveApiError).code).toBe('NETWORK');
    expect((e as CognitiveApiError).status).toBe(0);
    expect(dispatched).toContain('ecos-network-down');
  });

  it('网络失败 ⇒ 抛出物取单源 NetworkError 口径（`fetch <url> unreachable`），域内仍归一 NETWORK', async () => {
    stubSession('tk-1');
    stubNetworkDown();
    const e = await transitionBlueprint(SC, BP, 'RELEASED').catch((x: unknown) => x);
    expect((e as BlueprintApiError).code).toBe('NETWORK');
    expect((e as BlueprintApiError).message).toContain(`fetch ${blueprintActionUrl(SC, BP, '/transition')}`);
  });

  it('down 之后的成功腿 ⇒ 广播 ecos-network-up 复位横幅（单源口径，改前无）', async () => {
    const { dispatched } = stubSession('tk-1');
    stubNetworkDown();
    await callJson(URL_COG).catch((): null => null);
    stubFetch(makeRes({ status: 200, body: { code: 0, data: [] } }));
    await callJson(URL_COG);
    expect(dispatched).toContain('ecos-network-down');
    expect(dispatched).toContain('ecos-network-up');
  });

  it('恒带 Content-Type: application/json ＋ 有 token 才带 Bearer（等价红线，非差异）', async () => {
    stubSession('tk-1');
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0, data: null } }));
    await callJson(URL_COG);
    const h = lastHeaders(spy);
    expect(h['Content-Type']).toBe('application/json');
    expect(h.Authorization).toBe('Bearer tk-1');
  });

  it('无 token ⇒ 不发 Authorization 头（两客户端改前即有 `if (token)` 守卫，逐字保持）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0, data: null } }));
    await listBlueprints(SC);
    expect('Authorization' in lastHeaders(spy)).toBe(false);
  });

  it('调用方自定义头逐键覆盖，不整组替换掉 Content-Type/Bearer（改前 merged 形状）', async () => {
    stubSession('tk-1');
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0, data: null } }));
    await callJson(URL_COG, { method: 'PUT', headers: { 'X-ECOS-DOMAIN': 'd1' } });
    const h = lastHeaders(spy);
    expect(h['X-ECOS-DOMAIN']).toBe('d1');
    expect(h['Content-Type']).toBe('application/json');
    expect(h.Authorization).toBe('Bearer tk-1');
  });
});
