// @vitest-environment node
/**
 * alertsApi 单源 + 分态用例（#143/B5-11a，台账 §13.2.4 C2′ / 缺陷 D-B5-32）
 *
 * <p>**本域缺陷本体**（改前）：`services/alertsApi.ts` 自带 6 处私设 `await fetch(`
 * ＋ 手写 `data ??` 解包，且只判 `res.ok`。而 gateway `AlertController` 的
 * 详情/ack 腿返回的是 `ApiResponse.notFound(...)` = **HTTP 200 包壳、信封 code=404**
 * （`common-api/ApiResponse` 契约：0 成功 / 400 参数错 / 404 不存在 / -1 系统错）
 * ⇒ 改前面板把「后端自声明未承流」读成成功，渲染成空列表＝假真理。
 * 同批 `components/AlertPanel.tsx` 另有 1 处私设 `new WebSocket`，而后端
 * `/topic/alerts` 零发布者、`/ws` 只开 SockJS ⇒ 该腿恒不能握手。</p>
 *
 * <p>**本批方案**：传输委托 `apiFetchEnvelope`（Bearer/解包/取号/401·403 副作用单源），
 * 分类矩阵留域内；WebSocket 与 liveAlerts 渲染删除，改为显式「实时推送未接线」横幅。</p>
 *
 * <p>**逐腿改前改后响应形状取证（源级，2026-10-19）**：
 * `GET /api/alerts/rules`、`GET /api/alerts/history`、`POST /api/alerts/test` 三族
 * 后端**零映射**（改后 = 404 真实传输态 → `ENDPOINT_NOT_WIRED`）；
 * `GET /api/alerts/{id}`、`POST /api/alerts/{id}/ack` 有映射但自回 notFound
 * （改后 = 200 + code:404 包壳 → 亦 `ENDPOINT_NOT_WIRED`）；
 * 改前这两族形状分别为「空数组 + 静默成功」。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  AlertApiError,
  alertAck,
  alertCreateRule,
  alertDeleteRule,
  alertTest,
  fetchAlertHistory,
  fetchAlertRules,
} from '../alertsApi';

const SRC_API = readFileSync(fileURLToPath(new URL('../alertsApi.ts', import.meta.url)), 'utf8');
const SRC_PANEL = readFileSync(fileURLToPath(new URL('../../components/AlertPanel.tsx', import.meta.url)), 'utf8');

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_UNPACK = /data\s*\?\?/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 与 `api-base-single-source` 同口径：注释行不参与硬编码判据（本文件头注释大量引用后端真实路径）。 */
function countMatchesInCode(src: string, re: RegExp): number {
  const code = src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
  return countMatches(code, re);
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
  const map = new Map<string, string>();
  Object.entries(spec.headers ?? {}).forEach(([k, v]) => map.set(k.toLowerCase(), v));
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
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', storageStub());
  vi.stubGlobal('window', {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((ev: { type: string }): boolean => {
      dispatched.push(ev.type);
      return true;
    }),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
  return { ls, dispatched };
}

async function expectAlertError(promise: Promise<unknown>): Promise<AlertApiError> {
  const err = await promise.then((): null => null, (e: unknown): unknown => e);
  expect(err).toBeInstanceOf(AlertApiError);
  return err as AlertApiError;
}

beforeEach(() => {
  stubSession('tk-1');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形自检 — alertsApi 与 AlertPanel 零私设传输', () => {
  it('判据可红：缺陷原形命中三面', () => {
    const DEFECT =
      'const res = await fetch("/api/alerts/rules"); const j = await res.json(); '
      + 'return j.data ?? []; // Authorization: `Bearer ${t}`\n'
      + 'this.sock = new WebSocket(WS_URL);';
    expect(PRIVATE_TRANSPORT.test(DEFECT)).toBe(true);
    expect(HAND_UNPACK.test(DEFECT)).toBe(true);
    expect(HAND_AUTH_HEADER.test(DEFECT)).toBe(true);
  });

  it('alertsApi.ts 全文件零私设传输 / 零手写解包 / 零私拼 auth 头', () => {
    expect(countMatchesInCode(SRC_API, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatchesInCode(SRC_API, HAND_UNPACK)).toBe(0);
    expect(countMatchesInCode(SRC_API, HAND_AUTH_HEADER)).toBe(0);
  });

  it('alertsApi.ts 走单源：import API_BASE + apiFetchEnvelope，前缀非硬编码', () => {
    expect(SRC_API).toMatch(/import\s*\{[^}]*API_BASE[^}]*\}\s*from\s*'\.\/httpClient'/);
    expect(SRC_API).toMatch(/apiFetchEnvelope/);
    expect(countMatchesInCode(SRC_API, /["'`]\/api\b/)).toBe(0);
  });

  it('AlertPanel.tsx 零私设传输（WS 腿已删，改显式未接线横幅）', () => {
    expect(countMatchesInCode(SRC_PANEL, PRIVATE_TRANSPORT)).toBe(0);
    expect(SRC_PANEL).not.toMatch(/stomp|liveAlerts|connectWs/);
  });
});

describe('分态矩阵 — HTTP 态与 200 包壳态都不得折叠成成功', () => {
  it('传输真实 404（rules/history/test 三族零映射）→ ENDPOINT_NOT_WIRED', async () => {
    stubFetch(makeRes({ status: 404, raw: 'Not Found' }));
    const e = await expectAlertError(fetchAlertRules());
    expect(e.code).toBe('ENDPOINT_NOT_WIRED');
    expect(e.status).toBe(404);
    expect(e.message).toBe('Not Found');
  });

  it('200 包壳 code:404（AlertController 详情/ack 自回 notFound）→ 同归 ENDPOINT_NOT_WIRED', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 404, message: 'alert endpoint not wired', data: null } }));
    const e = await expectAlertError(alertAck(7));
    expect(e.code).toBe('ENDPOINT_NOT_WIRED');
    expect(e.status).toBe(200);
    expect(e.businessCode).toBe(404);
    expect(e.message).toBe('alert endpoint not wired');
  });

  it('200 包壳 code:400 / code:403 → BAD_REQUEST / FORBIDDEN（不折叠成 BUSINESS_ERROR）', async () => {
    stubFetch(makeRes({ body: { code: 400, message: 'metric required' } }));
    expect((await expectAlertError(alertTest({ metric: '' }))).code).toBe('BAD_REQUEST');

    stubFetch(makeRes({ body: { code: 403, message: 'platform:alert:ack denied' } }));
    expect((await expectAlertError(alertAck(7))).code).toBe('FORBIDDEN');
  });

  it('success:false 无 code 亦判失败（改前 fail-open 的正是这一面）', async () => {
    stubFetch(makeRes({ body: { success: false, message: 'engine down' } }));
    const e = await expectAlertError(alertCreateRule({ name: 'n' }));
    expect(e.code).toBe('BUSINESS_ERROR');
    expect(e.businessCode).toBeUndefined();
    expect(e.message).toBe('engine down');
  });

  it('传输 400/403/500 各自归位；403 单源广播 ecos-no-access', async () => {
    const s = stubSession('tk-1');
    stubFetch(makeRes({ status: 400, raw: 'bad' }));
    expect((await expectAlertError(alertDeleteRule(3))).code).toBe('BAD_REQUEST');

    stubFetch(makeRes({ status: 403, raw: 'nope' }));
    expect((await expectAlertError(fetchAlertHistory(5))).code).toBe('FORBIDDEN');
    expect(s.dispatched).toContain('ecos-no-access');

    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    const e = await expectAlertError(fetchAlertRules());
    expect(e.code).toBe('HTTP_ERROR');
    expect(e.status).toBe(500);
    expect(e.message).toBe('boom');
  });

  it('网络层失败 → NETWORK / status 0，且单源广播 ecos-network-down', async () => {
    const s = stubSession('tk-1');
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const e = await expectAlertError(fetchAlertRules());
    expect(e.code).toBe('NETWORK');
    expect(e.status).toBe(0);
    expect(s.dispatched).toContain('ecos-network-down');
  });

  it('成功信封 code:0 解包 data，数组面原样透出（面板 rowsOf 依赖此形状）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, message: 'ok', data: [{ id: 1 }] } }));
    await expect(fetchAlertRules()).resolves.toEqual([{ id: 1 }]);
    expect(lastCall(spy).url).toBe('/api/alerts/rules');
  });

  it('成功信封 code:200 放行（与单源 apiFetchData 同码同态：0 与 200 皆成功口径）', async () => {
    stubFetch(makeRes({ body: { code: 200, message: 'ok', data: { id: 9 } } }));
    await expect(alertCreateRule({ name: 'r' })).resolves.toEqual({ id: 9 });
  });

  it('非 0/200 的正数 code（如 201）一律判失败 —— 口径与 apiFetchData 逐字一致，不放水', async () => {
    stubFetch(makeRes({ body: { code: 201, message: 'created', data: { id: 9 } } }));
    const e = await expectAlertError(alertCreateRule({ name: 'r' }));
    expect(e.code).toBe('BUSINESS_ERROR');
    expect(e.businessCode).toBe(201);
  });
});

describe('兼容面锁死 — URL/method/body 与鉴权单源', () => {
  it('URL 逐字不变：rules / history?limit 编码 / test / {id}/ack', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));

    await fetchAlertRules();
    expect(lastCall(spy).url).toBe('/api/alerts/rules');

    await fetchAlertHistory(20);
    expect(lastCall(spy).url).toBe('/api/alerts/history?limit=20');

    await fetchAlertHistory('a b' as unknown as number);
    expect(lastCall(spy).url).toBe('/api/alerts/history?limit=a%20b');

    await alertTest({ metric: 'm' });
    expect(lastCall(spy)).toEqual({
      url: '/api/alerts/test',
      init: expect.objectContaining({ method: 'POST', body: JSON.stringify({ metric: 'm' }) }),
    });

    await alertAck('7');
    expect(lastCall(spy).url).toBe('/api/alerts/7/ack');

    await alertDeleteRule(3);
    expect(lastCall(spy).url).toBe('/api/alerts/rules/3');
  });

  it('ack 用 POST（改前 PUT ⇒ 405 被吞）且 ack body 不伪造操作人', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: null } }));
    await alertAck(11);
    const { init } = lastCall(spy);
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });

  it('Bearer 由单源拼接：有 token 发 Authorization，无 token 不发', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));
    await fetchAlertRules();
    expect((lastCall(spy).init.headers as Record<string, string>).Authorization).toBe('Bearer tk-1');

    stubSession(null);
    await fetchAlertRules();
    const headers = lastCall(spy).init.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
    expect(headers['Content-Type']).toBe('application/json');
  });

  it('traceId 透出：响应头 X-Request-Id 单源取号进错误对象', async () => {
    stubFetch(makeRes({ status: 404, raw: 'Not Found', headers: { 'X-Request-Id': 'req-abc' } }));
    const e = await expectAlertError(fetchAlertHistory(1));
    expect(e.traceId).toBe('req-abc');
  });
});
