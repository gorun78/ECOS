// @vitest-environment node
/**
 * systemDictApi / ontologyExportApi 单源 + 分态用例（#143/B5-11f，台账 §13.2.4 C2′ 注）
 *
 * <p>**本域缺陷本体**（改前）：两文件各一条私设传输腿，都是「域内自拼 `/api` 字面 URL +
 * 自拼 `Authorization: Bearer` + 裸 `await fetch(`」：`services/systemDictApi.ts` 的
 * `fetchSystemDictItems`（GET `/api/v1/system/dict/{type}`，且不判 `res.ok` 直接
 * `res.json()`），`services/ontology/ontologyExportApi.ts` 的 `downloadExport`
 * （GET `/api/v1/ontology/export/{id}/download`，`res.blob()` + `content-disposition`
 * 取名）；后者的 `EXPORT_BASE` 还是硬编码 `/api/v1/...`。</p>
 *
 * <p>**为什么合流到 `rawAuthedUrlFetch`**：这两腿要的是「带身份、但不预读响应体、不做
 * 401/403 会话副作用」的传输面，正是该通道的定义。`apiFetchData`/`apiFetchEnvelope` 都会
 * 先把响应体读成 text/JSON，blob 腿一旦被读掉就拿不到流；而域内继续自拼则同时留 C2′
 * （传输散落）与 api-base（基址非单源）两个洞。两腿合流后 URL 取值逐字不变。</p>
 *
 * <p>**Bearer 归一守卫消失的等价性取证**（本批唯一的「删代码」点）：原 systemDictApi 腿写
 * `token.startsWith('Bearer ') ? token : Bearer 拼值`，而单源与 `services/auth#authHeaders`
 * 一律无条件拼 `Bearer ${token}`。是否等价取决于「会话里存的值会不会本来带前缀」，故本 spec
 * 用「src 全量写入点扫描」（A4）把它钉成可复核事实，而不是凭读码印象宣布等价。</p>
 *
 * <p>**证据边界**：本批为源级 + 配置级 + 单测级证据；后端未起、未 curl、未 live 复验，
 * 不宣布这两条腿的真实应答面已验证。</p>
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError } from '../httpClient';
import { fetchSystemDictItems } from '../systemDictApi';
import { downloadExport } from '../ontology/ontologyExportApi';

// D-B5-27（`gateTraversalTimeout` 元门禁判据）：本 spec 的 A4 要遍历 src 全树，
// 必须用**文件级** setConfig 声明超时——per-test 第三参只护写它的那条用例。
vi.setConfig({ testTimeout: 30_000 });

const SRC_DICT = readFileSync(fileURLToPath(new URL('../systemDictApi.ts', import.meta.url)), 'utf8');
const SRC_EXPORT = readFileSync(
  fileURLToPath(new URL('../ontology/ontologyExportApi.ts', import.meta.url)),
  'utf8',
);
const SRC_LOGIN = readFileSync(
  fileURLToPath(new URL('../../pages/Login.tsx', import.meta.url)),
  'utf8',
);
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);
const SRC_APIBASE = readFileSync(
  fileURLToPath(new URL('../../__tests__/api-base-single-source.test.ts', import.meta.url)),
  'utf8',
);
const SRC_ROOT = fileURLToPath(new URL('../..', import.meta.url));

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization\s*[:=]/;
const AUTHED_URL_CHANNEL = /rawAuthedUrlFetch\s*\(/;
const HARDCODED_API = /["'`]\/api\b/;

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

function walk(dir: string, out: string[]): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(full) && !/\.d\.ts$/.test(full)) out.push(full);
  }
  return out;
}

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  headers?: Record<string, string>;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? (spec.body === undefined ? '' : JSON.stringify(spec.body));
  const map = new Map<string, string>(
    Object.entries({ 'content-type': 'application/json', ...(spec.headers ?? {}) }).map(([k, v]) => [
      k.toLowerCase(),
      v,
    ]),
  );
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (k: string): string | null => map.get(k.toLowerCase()) ?? null },
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)),
    blob: () => Promise.resolve({ size: 42, kind: 'blob' }),
  } as unknown as Response;
}

/**
 * 桩别判据用 `typeof === 'function'` 而不是 `instanceof Response`：`makeRes` 返回的是
 * 手造应答对象（node 环境下并非 `Response` 实例），若用 instanceof 分派会把成功应答
 * 误投进抛错支 ⇒ 用例集体塌成 NetworkError（本 spec 首跑即栽在此，属假红的根因）。
 */
function stubFetch(resOrThrower: Response | (() => Error)) {
  const thrower = typeof resOrThrower === 'function' ? resOrThrower : null;
  const res = thrower === null ? resOrThrower : null;
  const spy = vi.fn(async (_input: string, _init?: RequestInit): Promise<Response> => {
    if (thrower) throw thrower();
    return res as Response;
  });
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

let session = { ls: storageStub(), dispatched: [] as Array<{ type: string; detail?: unknown }>, win: { location: { hash: '' } } };

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
  session = { ls, dispatched, win };
}

async function expectError(promise: Promise<unknown>): Promise<unknown> {
  return promise.then((): never => {
    throw new Error('期望抛错，实际成功返回');
  }, (e: unknown): unknown => e);
}

beforeEach(() => {
  stubSession('tk-11f');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('A 源形 — 两文件私设传输与硬编码基址归零（C2′ 棘轮第六批）', () => {
  it('A1 systemDictApi.ts：零裸 fetch、零手拼 Authorization，一腿走 rawAuthedUrlFetch', () => {
    const code = codeOnly(SRC_DICT);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, AUTHED_URL_CHANNEL)).toBe(1);
  });

  it('A2 ontologyExportApi.ts：零裸 fetch、零手拼 Authorization，blob 腿走 rawAuthedUrlFetch', () => {
    const code = codeOnly(SRC_EXPORT);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, AUTHED_URL_CHANNEL)).toBe(1);
  });

  it('A3 两文件零 "/api" 字面量，基址一律 API_BASE 派生（api-base 单源）', () => {
    expect(countMatches(codeOnly(SRC_DICT), HARDCODED_API)).toBe(0);
    expect(countMatches(codeOnly(SRC_EXPORT), HARDCODED_API)).toBe(0);
    expect(SRC_DICT).toContain('`${API_BASE}/v1/system/dict/${dictType}`');
    expect(SRC_EXPORT).toContain('const EXPORT_BASE = `${API_BASE}/v1/ontology/export`;');
  });

  // D-B5-27 同型防线：遍历 src 全树的用例必须自带超时，否则并行 worker 下冷读会被 5s 默认值掐死（假红）
  it('A4 Bearer 守卫等价性取证：src 生产代码零处把带前缀值写入 storage，Login 只写原值', () => {
    const offenders: string[] = [];
    for (const file of walk(SRC_ROOT, [])) {
      const rel = relative(SRC_ROOT, file).replace(/\\/g, '/');
      if (rel.includes('__tests__') || /\.test\.tsx?$/.test(rel) || /\.spec\.tsx?$/.test(rel)) continue;
      readFileSync(file, 'utf8')
        .split('\n')
        .forEach((line, i) => {
          const t = line.trim();
          // 与棘轮同口径剥注释：本文件头的等价性说明里就引用了 `setItem(..., 'Bearer ...')` 原文
          if (t.startsWith('//') || t.startsWith('/*') || t.startsWith('*')) return;
          if (/setItem\([^,]*,\s*[`'"]Bearer\s/.test(line)) offenders.push(`${rel}:${i + 1}`);
        });
    }
    expect(offenders).toEqual([]);
    expect(SRC_LOGIN).toContain('localStorage.setItem("token", data.accessToken)');
  }, 30_000);

  it('A5 两份门禁基线同批剪除本批两文件，且棘轮声明值 = 当前实测口径 11/12', () => {
    for (const src of [SRC_RATCHET, SRC_APIBASE]) {
      expect(src).not.toContain("'services/systemDictApi.ts'");
      expect(src).not.toContain("'services/ontology/ontologyExportApi.ts'");
    }
    const declaredCount = Number(/expect\(entries\.length\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1] ?? -1);
    const declaredSum = Number(
      /expect\(entries\.reduce\(\(n, \[, c\]\) => n \+ c, 0\)\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1] ?? -1,
    );
    // 14/15 → 11/12：#143/B5-11g 剪除页面域三腿后的实测口径。
    // 本断言故意读门禁源码而非复制常量——棘轮声明若与实际收口脱节，这里会变红。
    expect(declaredCount).toBe(11);
    expect(declaredSum).toBe(12);
  });
});

describe('B fetchSystemDictItems 行为面（合流零语义变化）', () => {
  it('B1 URL 逐字不变，头 = 单源拼的 Bearer + 调用方 Content-Type', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));
    expect(await fetchSystemDictItems('gender')).toEqual([]);
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/system/dict/gender');
    expect(init.method ?? 'GET').toBe('GET');
    expect(headersOf(init)).toEqual({
      Authorization: 'Bearer tk-11f',
      'Content-Type': 'application/json',
    });
  });

  it('B2 信封 code!==0 或缺 data 时折叠成空数组（既有降级形状，不抛错）', async () => {
    stubFetch(makeRes({ body: { code: 1, message: 'boom' } }));
    expect(await fetchSystemDictItems('a')).toEqual([]);
    stubFetch(makeRes({ body: { code: 0 } }));
    expect(await fetchSystemDictItems('b')).toEqual([]);
  });

  it('B3 逐字段默认值原样保留（dictLabelEn 回退、extValue 缺省 undefined、sortOrder 回退 0）', async () => {
    stubFetch(
      makeRes({
        body: {
          code: 0,
          data: [
            { dictCode: 'M', dictLabel: '男' },
            { dictCode: 'F', dictLabel: '女', dictLabelEn: 'Female', extValue: 'x', sortOrder: 7 },
          ],
        },
      }),
    );
    expect(await fetchSystemDictItems('sex')).toEqual([
      { dictCode: 'M', dictLabel: '男', dictLabelEn: '男', extValue: undefined, sortOrder: 0 },
      { dictCode: 'F', dictLabel: '女', dictLabelEn: 'Female', extValue: 'x', sortOrder: 7 },
    ]);
  });

  it('B4 不判 res.ok：404 仍照 body 解析（改前形状，不得借合流偷偷加状态门）', async () => {
    const spy = stubFetch(
      makeRes({ status: 404, body: { code: 0, data: [{ dictCode: 'X', dictLabel: 'x' }] } }),
    );
    const items = await fetchSystemDictItems('missing');
    expect(items.map((d) => d.dictCode)).toEqual(['X']);
    expect(lastCall(spy).url).toBe('/api/v1/system/dict/missing');
  });

  it('B5 200 回 HTML 时由 res.json() 抛错，不塌成空数组（无 try/catch 吞解析错）', async () => {
    stubFetch(makeRes({ raw: '<html>login</html>', headers: { 'content-type': 'text/html' } }));
    const e = await expectError(fetchSystemDictItems('c'));
    expect(e).toBeInstanceOf(SyntaxError);
  });

  it('B6 无登录态时单源不拼 Authorization，其余形状不变', async () => {
    session.ls.removeItem('token');
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));
    await fetchSystemDictItems('d');
    expect(headersOf(lastCall(spy).init)).toEqual({ 'Content-Type': 'application/json' });
  });

  it('B7 网络故障归一为 NetworkError 并派发 ecos-network-down（合流新增的唯一共享面）', async () => {
    stubFetch(() => new TypeError('Failed to fetch'));
    const e = await expectError(fetchSystemDictItems('e'));
    expect(e).toBeInstanceOf(NetworkError);
    expect(session.dispatched.map((d) => d.type)).toEqual(['ecos-network-down']);
    expect(session.ls.getItem('token')).toBe('tk-11f');
    expect(session.win.location.hash).toBe('');
  });
});

describe('C downloadExport 行为面（blob 腿合流，零语义变化）', () => {
  const created: string[] = [];
  const revoked: string[] = [];
  const clicks: string[] = [];
  const urlProps: Array<['createObjectURL' | 'revokeObjectURL', PropertyDescriptor | undefined]> = [];

  beforeEach(() => {
    created.length = 0;
    revoked.length = 0;
    clicks.length = 0;
    for (const key of ['createObjectURL', 'revokeObjectURL'] as const) {
      urlProps.push([key, Object.getOwnPropertyDescriptor(URL, key)]);
    }
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: (blob: unknown): string => {
        const u = `blob:mock/${String((blob as { size?: number })?.size ?? 0)}`;
        created.push(u);
        return u;
      },
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: (u: string): void => {
        revoked.push(u);
      },
    });
    vi.stubGlobal('document', {
      createElement: (tag: string) => ({
        tag,
        href: '',
        download: '',
        click(): void {
          clicks.push(tag === 'a' ? this.download : '');
        },
      }),
      body: { appendChild: vi.fn(), removeChild: vi.fn() },
    });
  });

  afterEach(() => {
    for (const [key, desc] of urlProps.splice(0).reverse()) {
      if (desc) Object.defineProperty(URL, key, desc);
      else Reflect.deleteProperty(URL, key);
    }
  });

  it('C1 URL 逐字不变，只带 Authorization（改前无 Content-Type，合流后仍无）', async () => {
    const spy = stubFetch(
      makeRes({ headers: { 'content-disposition': 'attachment; filename="onto.json"' } }),
    );
    await downloadExport('exp-9');
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/ontology/export/exp-9/download');
    expect(headersOf(init)).toEqual({ Authorization: 'Bearer tk-11f' });
  });

  it('C2 blob 落地链路保留：文件名取自 content-disposition，锚点点击后撤销 objectURL', async () => {
    stubFetch(makeRes({ headers: { 'content-disposition': 'attachment; filename="export.csv"' } }));
    await downloadExport('exp-7');
    expect(clicks).toEqual(['export.csv']);
    expect(created).toEqual(['blob:mock/42']);
    expect(revoked).toEqual(created);
  });

  it('C3 非 2xx 抛 `Download failed: HTTP ${status}`，不动 token、不跳登录（无会话副作用）', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    const e = await expectError(downloadExport('exp-5'));
    expect(e).toBeInstanceOf(Error);
    expect((e as Error).message).toBe('Download failed: HTTP 500');
    expect(session.ls.getItem('token')).toBe('tk-11f');
    expect(session.win.location.hash).toBe('');
    expect(created).toHaveLength(0);
  });

  it('C4 网络故障同样归一 NetworkError + ecos-network-down，且不进 blob 落地链路', async () => {
    stubFetch(() => new TypeError('Failed to fetch'));
    const e = await expectError(downloadExport('exp-6'));
    expect(e).toBeInstanceOf(NetworkError);
    expect(session.dispatched.map((d) => d.type)).toEqual(['ecos-network-down']);
    expect(created).toHaveLength(0);
    expect(revoked).toHaveLength(0);
  });
});
