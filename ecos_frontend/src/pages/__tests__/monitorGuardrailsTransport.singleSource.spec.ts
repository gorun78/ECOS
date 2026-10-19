// @vitest-environment node
/**
 * EngineMonitor / MonitoringCenter / Guardrails.helpers 单源收口取证（#143/B5-11g，
 * 台账 §13.2.4 C2′ 注）
 *
 * <p>**本域缺陷本体**（改前）：三处各自持有一条私设传输腿——
 * `pages/EngineMonitor.tsx#apiFetch`、`pages/MonitoringCenter.tsx#apiFetch`、
 * `pages/Guardrails/helpers.ts#apiCall`，形状都是「域内裸 `await fetch(` +
 * 自拼 `authHeaders()`」。其中前两处还把 `/api/v1/engine/…` 写成字面前缀（api-base 单源违例）。</p>
 *
 * <p>**为什么合流到 `rawAuthedUrlFetch` 而不是 `apiFetch`/`apiFetchData`**：三处域内各自
 * 定义了**不同的**应答形状——EngineMonitor 用 `Error('HTTP_401')` + `NoAccessError`，
 * MonitoringCenter 用 `{kind:'ok'|'denied'|'offline'|'error'}` 四态分类，
 * apiCall 用中文文案 + 直接返回 JSON。单源的 `apiFetch` 语义是 401→`handleAuthExpired()`、
 * 403→`notifyNoAccess`+`ApiRequestError`、且无 content-type 容差。把域内三套语义折进单源
 * 就是**语义变更**，不属本批「只收传输」的授权范围 ⇒ 走「带身份、不预读响应体、零会话副作用」
 * 的裸响应通道，只把 `fetch(` 本身搬进单源。</p>
 *
 * <p>**头集合逐键不变是硬约束**：`authHeaders()` = `Content-Type: application/json` +
 * （有 token 才拼的）`Authorization`；`rawAuthedUrlFetch` 的底头**只有 Authorization**
 * （通道注释明写「刻意不设 Content-Type」）。故三腿必须显式带回 `Content-Type`，
 * 否则 `apiCall` 的 POST/PUT 腿（`pages/GuardrailsView.tsx` 实有 2 POST + 2 PUT + 1 DELETE）
 * 会因缺声明类型而承担 415 风险。B7/B9 把这条钉成行为断言。</p>
 *
 * <p>**D-B5-34 形态差**：改前 `{ headers: authHeaders(), ...options }` 是**整组替换**
 * （调用方传 `headers` 会把 Authorization 整个丢掉 ⇒ 请求变成无身份）；合流后单源
 * `{ ...baseHeaders, ...initHeaders }` 是**逐键合并**（Authorization 保留）。
 * A6 用扫描取证「`GuardrailsView.tsx` 的 `apiCall` 调用点零处传 `headers`」⇒ 本应用内
 * 两者取值相同，该等价性是**被测出来的事实**，不是假设；调用点计数取扫描派生值，不写死。</p>
 *
 * <p>**证据边界**：前两处 helper 是**页面文件私有**（未 `export`），本 spec 与 #143/B5-11a
 * 处理 `components/AlertPanel.tsx` 的方式一致，对其做**源形取证**（A2~A5），不做行为取证；
 * `apiCall` 是导出函数，做完整行为取证（B 组）。后端未起、未 curl、未 live 复验，
 * 不宣布这三条腿的真实应答面已验证。401 腿的手删单键（EngineMonitor `localStorage.removeItem`
 * 而 MonitoringCenter 已用 `clearAuthToken()`）登记在 #108 队列，本批不夹带修复、
 * 也不把该形态断言为正确。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NetworkError } from '../../services/httpClient';
import { apiCall } from '../Guardrails/helpers';

// D-B5-27（`gateTraversalTimeout` 元门禁判据）：A6 要扫 src 全树的调用点，
// 必须用**文件级** setConfig——per-test 第三参只护写它的那条用例。
vi.setConfig({ testTimeout: 30_000 });

const read = (p: string): string => readFileSync(fileURLToPath(new URL(p, import.meta.url)), 'utf8');

const SRC_MONITOR = read('../../pages/EngineMonitor.tsx');
const SRC_CENTER = read('../../pages/MonitoringCenter.tsx');
const SRC_HELPERS = read('../Guardrails/helpers.ts');
const SRC_VIEW = read('../../pages/GuardrailsView.tsx');
const SRC_RATCHET = read('../../__tests__/privateTransportRatchet.test.ts');
const SRC_APIBASE = read('../../__tests__/api-base-single-source.test.ts');

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization\s*[:=]/;
const AUTHED_URL_CHANNEL = /rawAuthedUrlFetch\s*\(/;
const HARDCODED_API = /["'`]\/api\b/;
const CONTENT_TYPE = /'Content-Type':\s*'application\/json'/;

function countInCode(src: string, re: RegExp): number {
  const code = src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (code.match(g) ?? []).length;
}

// ────────────────────────────────────────────────────────────
// 桩件（与 #143/B5-11f spec 同形：手造应答 + typeof 判据分派，防把成功应答误投抛错支）
// ────────────────────────────────────────────────────────────

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
  } as unknown as Response;
}

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

interface FakeWindow {
  location: { hash: string };
  localStorage: Storage;
  dispatchEvent: (ev: { type: string; detail?: unknown }) => boolean;
  addEventListener: () => void;
  removeEventListener: () => void;
}

let session = {
  ls: storageStub(),
  dispatched: [] as Array<{ type: string; detail?: unknown }>,
  win: { location: { hash: '' } } as unknown as FakeWindow,
};

function stubSession(token: string | null) {
  const ls = storageStub();
  if (token !== null) ls.setItem('token', token);
  const dispatched: Array<{ type: string; detail?: unknown }> = [];
  const win: FakeWindow = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((): boolean => true),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  (win.dispatchEvent as ReturnType<typeof vi.fn>).mockImplementation((ev: { type: string; detail?: unknown }) => {
    dispatched.push({ type: ev.type, detail: ev.detail });
    return true;
  });
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', storageStub());
  vi.stubGlobal('window', win);
  session = { ls, dispatched, win };
}

async function expectError(promise: Promise<unknown>): Promise<unknown> {
  return promise.then((): never => {
    throw new Error('EXPECTED_REJECT');
  }, (e: unknown) => e);
}

beforeEach(() => {
  stubSession('tk-11g');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ────────────────────────────────────────────────────────────
describe('A 源形 — 三文件私设传输与硬编码基址归零（C2′ 棘轮 + api-base 单源）', () => {
  it('A1 判据自检：缺陷原形能变红、收口形状不误报（防恒 0 假绿）', () => {
    const DEFECT = [
      "const res = await fetch('/api/v1/engine/data/health', { headers: { Authorization: `Bearer ${t}` } });",
      'this.sock = new WebSocket(WS_URL);',
    ].join('\n');
    const CLEAN = [
      "import { API_BASE, rawAuthedUrlFetch } from '../services/httpClient';",
      "const res = await rawAuthedUrlFetch(url, { headers: { 'Content-Type': 'application/json' } });",
      'const data = await apiFetch(`${API_BASE}/v1/engine/${engine}/health`);',
      "// 历史注释：原裸 fetch('/api/v1/x') 已合流单源",
    ].join('\n');
    expect(countInCode(DEFECT, PRIVATE_TRANSPORT)).toBe(2);
    expect(countInCode(DEFECT, HAND_AUTH_HEADER)).toBe(1);
    expect(countInCode(DEFECT, HARDCODED_API)).toBe(1);
    expect(countInCode(CLEAN, PRIVATE_TRANSPORT)).toBe(0);
    expect(countInCode(CLEAN, HARDCODED_API)).toBe(0);
    expect(countInCode(CLEAN, CONTENT_TYPE)).toBe(1);
  });

  it('A2 三文件零私设传输（裸 fetch / WebSocket / XHR / axios，注释剔除）', () => {
    for (const [name, src] of [
      ['pages/EngineMonitor.tsx', SRC_MONITOR],
      ['pages/MonitoringCenter.tsx', SRC_CENTER],
      ['pages/Guardrails/helpers.ts', SRC_HELPERS],
    ] as const) {
      expect(countInCode(src, PRIVATE_TRANSPORT), `${name} 仍有私设传输`).toBe(0);
    }
  });

  it('A3 三文件零手写 Authorization（Bearer 拼接只在单源）', () => {
    for (const [name, src] of [
      ['pages/EngineMonitor.tsx', SRC_MONITOR],
      ['pages/MonitoringCenter.tsx', SRC_CENTER],
      ['pages/Guardrails/helpers.ts', SRC_HELPERS],
    ] as const) {
      expect(countInCode(src, HAND_AUTH_HEADER), `${name} 仍自拼 Authorization`).toBe(0);
    }
  });

  it('A4 三文件各恰一处单源通道，且都显式带回 Content-Type（头集合逐键不变）', () => {
    for (const [name, src] of [
      ['pages/EngineMonitor.tsx', SRC_MONITOR],
      ['pages/MonitoringCenter.tsx', SRC_CENTER],
      ['pages/Guardrails/helpers.ts', SRC_HELPERS],
    ] as const) {
      expect(countInCode(src, AUTHED_URL_CHANNEL), `${name} 的 rawAuthedUrlFetch 调用数异常`).toBe(1);
      expect(countInCode(src, CONTENT_TYPE), `${name} 未显式带 Content-Type`).toBeGreaterThanOrEqual(1);
    }
  });

  it('A5 基址单源：两页面零 /api 字面量且改由 ${API_BASE} 派生；helpers 本就无基址字面量', () => {
    expect(countInCode(SRC_MONITOR, HARDCODED_API)).toBe(0);
    expect(countInCode(SRC_CENTER, HARDCODED_API)).toBe(0);
    expect(countInCode(SRC_HELPERS, HARDCODED_API)).toBe(0);
    // EngineMonitor 4 处（health/config/status 三点 + ENGINE_META.data.configPath）、
    // MonitoringCenter 6 处（ENGINES 六枚 apiBase）都由 API_BASE 派生。
    expect(countInCode(SRC_MONITOR, /\$\{API_BASE\}/g)).toBe(4);
    expect(countInCode(SRC_CENTER, /\$\{API_BASE\}/g)).toBe(6);
    expect(SRC_MONITOR).toContain("from '../services/httpClient'");
    expect(SRC_CENTER).toContain("from '../services/httpClient'");
  });

  it('A6 D-B5-34 等价性取证：apiCall 调用点全部在 GuardrailsView，且零处传 headers', () => {
    const callCount = countInCode(SRC_VIEW, /\bapiCall\s*(?:<[^(]*>)?\s*\(/g);
    // 不写死计数：只要求「确有调用点」+「零处传 headers」两条事实同时成立。
    expect(callCount).toBeGreaterThan(0);
    expect(countInCode(SRC_VIEW, /\bheaders\s*:/g), '出现传 headers 的调用点 ⇒ 整组替换语义需重新评估').toBe(0);
    // 反向自检：若某调用点开始传 headers，本判据必须能变红。
    expect(countInCode('await apiCall(u, { headers: { X: 1 } });', /\bheaders\s*:/g)).toBe(1);
  });

  it('A7 两份门禁基线同批剪除三文件，且棘轮声明值 = 当前实测口径 11/12', () => {
    for (const gate of [SRC_RATCHET, SRC_APIBASE]) {
      expect(gate).not.toContain("'pages/EngineMonitor.tsx'");
      expect(gate).not.toContain("'pages/MonitoringCenter.tsx'");
    }
    expect(SRC_RATCHET).not.toContain("'pages/Guardrails/helpers.ts'");
    const declaredCount = Number(/expect\(entries\.length\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1] ?? -1);
    const declaredSum = Number(
      /expect\(entries\.reduce\(\(n, \[, c\]\) => n \+ c, 0\)\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1] ?? -1,
    );
    // 读门禁源码而非复制常量：棘轮声明若与实际收口脱节，这里会变红。
    expect(declaredCount).toBe(11);
    expect(declaredSum).toBe(12);
  });
});

// ────────────────────────────────────────────────────────────
describe('B apiCall 行为面（唯一导出的 helper，逐面取证零语义变化）', () => {
  it('B1 200 + {data} 信封 → 解包 data；URL 原样透传（单源不再拼前缀）', async () => {
    const spy = stubFetch(makeRes({ body: { data: [{ id: 'p1' }], code: 0 } }));
    expect(await apiCall('/api/v1/guardrails/policies')).toEqual([{ id: 'p1' }]);
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/guardrails/policies');
    expect(init.method ?? 'GET').toBe('GET');
    expect(headersOf(init)).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer tk-11g' });
  });

  it('B2 非信封应答原样返回（不因合流偷偷加 code 折叠）', async () => {
    stubFetch(makeRes({ body: { list: [1, 2], total: 2 } }));
    expect(await apiCall('/x')).toEqual({ list: [1, 2], total: 2 });
  });

  it('B3 content-type 非 JSON → 返回 null（域内容差面保留）', async () => {
    stubFetch(makeRes({ raw: '<html>ok</html>', headers: { 'content-type': 'text/html' } }));
    expect(await apiCall('/x')).toBeNull();
  });

  it('B4 非 2xx（非 401/403）抛 body 文本，空 body 退化为 HTTP {status}', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    expect((await expectError(apiCall('/x'))) instanceof Error).toBe(true);
    expect((await expectError(apiCall('/x')) as Error).message).toBe('boom');
    stubFetch(makeRes({ status: 503 }));
    expect((await expectError(apiCall('/x')) as Error).message).toBe('HTTP 503');
  });

  it('B5 401：清 token + 跳 #/login + 中文文案（域内会话副作用面，未折进单源）', async () => {
    stubFetch(makeRes({ status: 401, raw: 'expired' }));
    const e = await expectError(apiCall('/x'));
    expect((e as Error).message).toBe('登录已过期，请重新登录');
    expect(session.ls.getItem('token')).toBeNull();
    expect(session.win.location.hash).toBe('#/login');
  });

  it('B6 403：文案含服务端文本，且不清 token、不跳登录（有效会话只报无权限）', async () => {
    stubFetch(makeRes({ status: 403, raw: 'policy read-only' }));
    const e = await expectError(apiCall('/x'));
    expect((e as Error).message).toContain('policy read-only');
    expect(session.ls.getItem('token')).toBe('tk-11g');
    expect(session.win.location.hash).toBe('');
  });

  it('B7 POST：method/body 保留且 Content-Type 未被丢（改后若不显式补头即在此变红）', async () => {
    const spy = stubFetch(makeRes({ body: { data: { id: 'new' } } }));
    const out = await apiCall('/api/v1/guardrails/policies', {
      method: 'POST',
      body: JSON.stringify({ name: 'n1' }),
    });
    expect(out).toEqual({ id: 'new' });
    const { init } = lastCall(spy);
    expect(init.method).toBe('POST');
    expect(init.body).toBe(JSON.stringify({ name: 'n1' }));
    expect(headersOf(init)).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer tk-11g' });
  });

  it('B8 逐键合并（D-B5-34 新语义）：调用方传 headers 时 Authorization 仍在，同名键由调用方覆盖', async () => {
    const spy = stubFetch(makeRes({ body: { data: 'ok' } }));
    await apiCall('/x', { headers: { 'Content-Type': 'text/plain', 'X-Trace': 't1' } });
    expect(headersOf(lastCall(spy).init)).toEqual({
      Authorization: 'Bearer tk-11g',
      'Content-Type': 'text/plain',
      'X-Trace': 't1',
    });
  });

  it('B9 无登录态：只带 Content-Type，不拼 Authorization', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { data: null } }));
    await apiCall('/x');
    expect(headersOf(lastCall(spy).init)).toEqual({ 'Content-Type': 'application/json' });
  });

  it('B10 网络故障归一 NetworkError + ecos-network-down（合流新增的唯一共享面）', async () => {
    stubFetch(() => new TypeError('Failed to fetch'));
    const e = await expectError(apiCall('/x'));
    expect(e).toBeInstanceOf(NetworkError);
    expect(session.dispatched.map((d) => d.type)).toContain('ecos-network-down');
    expect(session.ls.getItem('token')).toBe('tk-11g');
  });
});
