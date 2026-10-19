// @vitest-environment node
/**
 * 整域 REST 基座传输单源 + 分态用例（#143/B5-15，台账 §13.2.4 C2′ 注余项② 的收口批）
 *
 * <p>**本域缺陷本体**（改前）：`services/capabilityClient.ts#callCapability` 与
 * `services/scenarioActionClient.ts#rawCall` 各自写了一份完整传输封装——
 * `{Content-Type: application/json}` + `getAuthToken()` 拼 Bearer + 调用方头逐键合并 +
 * 裸 `fetch(url, {...init, headers: merged})`。两文件都只有一条传输腿，但它是**整域基座的地基**
 * （域内全部契约方法都从它走），所以留着裸 `fetch` 与留着 20 条散腿在判据上是同一件事：
 * Bearer 拼接、头合并规则、网络失败面都不在单源可核对面上。</p>
 *
 * <p>**为什么必须新增第四条裸响应通道 `rawAuthedJsonUrlFetch`**（本批裁决点，
 * 三选一的判据全部来自「传输合流的授权面只有零语义变化」）：
 * ① `rawAuthedUrlFetch` ✗ —— 它带 PMO-43 网络态归一（`NetworkError` + `ecos-network-down`
 * 广播），会把两基座自持的 `code:'NETWORK', status:0` 分态折叠掉；且底头刻意不含
 * `Content-Type`，两基座的 POST/PUT JSON 体丢类型即有 415 风险。
 * ② `apiFetchEnvelope` ✗ —— 它照做平台会话副作用：401 → `handleAuthExpired()`
 * （清 token + 跳 `#/login`）、403 → `notifyNoAccess()`。两基座改前都没有，
 * 「打开能力目录盘就被踢回登录页」是行为变更。
 * ③ `rawAuthedJsonUrlFetch` ✓ —— 带身份 + JSON 默认头 + 逐键合并 + 零副作用零归一，
 * 与 `rawStreamUrlFetch` 共用 `sendUrlFetch` 内核。本 spec 就是把 ①② 的「不会误伤」
 * 钉成断言（网络失败仍是域内错误、401/403 既不清会话也不广播），而不是留在注释里。</p>
 *
 * <p>**取号顺位注记**（零变化保留，不属本批修复面）：两基座只读 `X-Trace-Id` 响应头，
 * 不像单源 `pickTraceId` 那样先看 `X-Request-Id`。改前就是这样，合流批不动它；
 * 「域内顺位 vs 平台顺位」是否统一，与 401/403 副作用同属一个待裁决项（见台账）。</p>
 *
 * <p>**证据边界**：源级 + 单测级证据（`fetch` 全程桩）。后端未起、未 curl、未 live 复验，
 * 不宣布 `/api/v1/ai/capabilities` 与 `/api/v1/workspace/**` 的真实应答面已验证。</p>
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  NetworkError,
  rawAuthedJsonUrlFetch,
} from '../../services/httpClient';
import {
  CapabilityApiError,
  capabilityCollectionUrl,
  listCapabilities,
} from '../../services/capabilityClient';
import {
  ScenarioActionError,
  approveScenarioAction,
  createScenarioAction,
  listScenarioActions,
  scenarioActionsUrl,
} from '../../services/scenarioActionClient';

// 本 spec 的「全仓通道调用点清单」要遍历 src 全树 ⇒ 文件级超时（D-B5-27 元门禁口径）。
vi.setConfig({ testTimeout: 30_000 });

const SRC_CAP = readFileSync(
  fileURLToPath(new URL('../../services/capabilityClient.ts', import.meta.url)),
  'utf8',
);
const SRC_ACTION = readFileSync(
  fileURLToPath(new URL('../../services/scenarioActionClient.ts', import.meta.url)),
  'utf8',
);
const SRC_HTTP = readFileSync(
  fileURLToPath(new URL('../../services/httpClient.ts', import.meta.url)),
  'utf8',
);
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);
const SRC_ROOT = fileURLToPath(new URL('../..', import.meta.url));

// ── 静态判据（与棘轮门同口径）──────────────────────────

const PRIVATE_TRANSPORT =
  /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/g;
const HAND_AUTH_HEADER = /Authorization\s*[:=]/g;
const IN_DOMAIN_IDENTITY = /(?:authOnlyHeaders|authHeaders|getAuthToken)\s*\(/g;
const CHANNEL_CALL = /rawAuthedJsonUrlFetch\s*\(/g;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 剥行注释：注释里的历史形态不参与判据（与 `privateTransportRatchet#isComment` 同规则）。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

/** 取通道/内核函数体切片（到下一个顶层 `export ` 或 `/**` 文档块为止）。 */
function sliceOfChannel(src: string, fnName: string): string {
  const marker = `export async function ${fnName}`;
  const start = src.indexOf(marker);
  if (start < 0) throw new Error(`未找到通道 ${fnName}`);
  const rest = src.slice(start + marker.length);
  const next = rest.search(/\n(?:export |\/\*\*)/);
  return next < 0 ? rest : rest.slice(0, next);
}

function walk(dir: string, out: string[]): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === 'node_modules' || entry.startsWith('.')) continue;
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(full) && !/\.d\.ts$/.test(full)) out.push(full);
  }
  return out;
}

/** 生产文件（排除测试面与单源正主）——与棘轮门的扫描面一致，避免两门口径漂移。 */
function isTestPath(rel: string): boolean {
  return (
    rel.includes('__tests__') ||
    rel.endsWith('.test.ts') || rel.endsWith('.test.tsx') ||
    rel.endsWith('.spec.ts') || rel.endsWith('.spec.tsx') ||
    rel.startsWith('test/')
  );
}

// ── 应答桩（忠实面：text/json/headers 三件都按浏览器语义给）──

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
    // 非法 JSON 必须是 rejected promise，而不是在 json() 调用点同步抛（B5-14 的桩保真教训）。
    json: (): Promise<unknown> => {
      try {
        return Promise.resolve(JSON.parse(text));
      } catch (e) {
        return Promise.reject(e instanceof Error ? e : new Error(String(e)));
      }
    },
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
  const init = (call[1] ?? {}) as RequestInit;
  return {
    url: String(call[0]),
    method: init.method,
    body: init.body as string | undefined,
    headers: { ...(init.headers ?? {}) } as Record<string, string>,
  };
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

let dispatched: Array<{ type: string; detail?: unknown }> = [];
let winStub: { location: { hash: string } } = { location: { hash: '' } };

function stubSession(token: string | null) {
  const ls = storageStub();
  if (token !== null) ls.setItem('token', token);
  ls.setItem('username', 'admin');
  dispatched = [];
  winStub = {
    location: { hash: '' },
  };
  const win = {
    ...winStub,
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
  return ls;
}

function capabilityRow(id: string) {
  return {
    capabilityId: id,
    family: 'embedding',
    contractVersion: 'v1',
    evidenceRequired: true,
    automationLevel: 1,
    costUnit: 'TOKEN',
    toolset: 'core',
  };
}

beforeEach(() => {
  stubSession('tok-cap');
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// ── 静态判据：两基座 + 单源通道本体 ────────────────────

describe('两条整域基座的传输面静态判据（#143/B5-15 合流 rawAuthedJsonUrlFetch）', () => {
  for (const [label, src] of [
    ['capabilityClient.ts', SRC_CAP],
    ['scenarioActionClient.ts', SRC_ACTION],
  ] as const) {
    it(`${label}：零裸传输 / 零手写身份头 / 零域内取号，通道调用恰一处`, () => {
      const code = codeOnly(src);
      expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
      expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
      expect(countMatches(code, IN_DOMAIN_IDENTITY)).toBe(0);
      expect(countMatches(code, CHANNEL_CALL)).toBe(1);
      expect(code).toContain("from './httpClient'");
    });
  }

  it('棘轮基线已把两文件剪除，且元门禁同步钉在 0 文件 / 0 命中行', () => {
    expect(SRC_RATCHET).not.toContain("'services/capabilityClient.ts':");
    expect(SRC_RATCHET).not.toContain("'services/scenarioActionClient.ts':");
    expect(SRC_RATCHET).toContain('expect(entries.length).toBe(0)');
    expect(SRC_RATCHET).toContain('expect(entries.reduce((n, [, c]) => n + c, 0)).toBe(0)');
  });

  it('全仓「自持分态整域基座」通道调用点清单可核对：恰这两个生产文件', () => {
    const files = walk(SRC_ROOT, [])
      .map((f) => relative(SRC_ROOT, f).split('\\').join('/'))
      .filter((rel) => !isTestPath(rel) && rel !== 'services/httpClient.ts')
      .filter((rel) => countMatches(codeOnly(readFileSync(join(SRC_ROOT, rel), 'utf8')), CHANNEL_CALL) > 0)
      .sort();
    expect(files).toEqual(['services/capabilityClient.ts', 'services/scenarioActionClient.ts']);
  });
});

describe('rawAuthedJsonUrlFetch 通道本体：只做拼头+发请求，零副作用零归一', () => {
  const body = sliceOfChannel(SRC_HTTP, 'rawAuthedJsonUrlFetch');

  it('底头 = Content-Type: application/json + getAuthToken() 拼 Bearer（身份仍在单源）', () => {
    expect(body).toContain("'Content-Type': 'application/json'");
    expect(body).toContain('getAuthToken()');
    expect(body).toContain('`Bearer ${token}`');
    expect(body).toContain('sendUrlFetch(url, headers, options)');
  });

  it('通道体不得引用 PMO-43 归一三件套，也不得引用会话副作用面', () => {
    for (const forbidden of [
      'markNetworkDown',
      'notifyNetworkDown',
      'notifyNetworkUp',
      'toNetworkError',
      'handleAuthExpired',
      'checkAuthExpired',
      'notifyNoAccess',
      'clearAuthSession',
    ]) {
      expect(body, `通道本体出现了 ${forbidden} ⇒ 会给自持分态的基座带来语义变更`).not.toContain(forbidden);
    }
  });

  it('与 rawStreamUrlFetch 的唯一差 = JSON 默认头（两条都不归一化，口径不漂移）', () => {
    const stream = sliceOfChannel(SRC_HTTP, 'rawStreamUrlFetch');
    expect(stream).not.toContain("'Content-Type'");
    expect(stream).toContain('sendUrlFetch(url, headers, options)');
  });

  it('调用方 headers 逐键合并（不是整组替换）：自定义头与身份/类型头共存', async () => {
    const spy = stubFetch(makeRes({ body: { code: 200, data: [] } }));
    await rawAuthedJsonUrlFetch('/api/v1/any', { headers: { 'X-Scene-Id': 'sc-01' } });
    expect(lastCall(spy).headers).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer tok-cap',
      'X-Scene-Id': 'sc-01',
    });
  });

  it('调用方显式同名头以其为准（合并方向 = 覆盖底头，改前就是这个形状）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 200, data: [] } }));
    await rawAuthedJsonUrlFetch('/api/v1/any', { headers: { 'Content-Type': 'application/json; charset=utf-8' } });
    expect(lastCall(spy).headers['Content-Type']).toBe('application/json; charset=utf-8');
  });

  it('无 token ⇒ 只发 Content-Type，不发空 Authorization（禁伪装已登录）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { code: 200, data: [] } }));
    await rawAuthedJsonUrlFetch('/api/v1/any');
    expect(lastCall(spy).headers).toEqual({ 'Content-Type': 'application/json' });
  });

  it('网络失败原样上抛，不折叠 NetworkError、不广播掉线（域内自持分态的边界）', async () => {
    stubFetch(() => new Error('ECONNREFUSED'));
    await expect(rawAuthedJsonUrlFetch('/api/v1/any')).rejects.toThrow('ECONNREFUSED');
    expect(dispatched).toEqual([]);
  });
});

// ── 能力目录腿：实际请求字节面 + 错误矩阵零变化 ────────

describe('capabilityClient#listCapabilities（GET /api/v1/ai/capabilities）', () => {
  it('URL 仍由域内导出函数单源构造，GET 也带 Content-Type（改前既有形状，合流后不变）', async () => {
    const spy = stubFetch(
      makeRes({ body: { code: 200, data: [capabilityRow('TEXT_EMBEDDING')] } }),
    );
    const rows = await listCapabilities();
    expect(rows.map((r) => r.capabilityId)).toEqual(['TEXT_EMBEDDING']);
    const call = lastCall(spy);
    expect(call.url).toBe(capabilityCollectionUrl());
    expect(call.url).toBe('/api/v1/ai/capabilities');
    expect(call.method).toBeUndefined();
    expect(call.headers).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer tok-cap',
    });
  });

  it('网络失败 ⇒ CapabilityApiError(code=NETWORK, status=0)，不是 NetworkError、无广播', async () => {
    stubFetch(() => new Error('Failed to fetch'));
    const e = await listCapabilities().catch((err) => err);
    expect(e).toBeInstanceOf(CapabilityApiError);
    expect(e).not.toBeInstanceOf(NetworkError);
    expect((e as CapabilityApiError).code).toBe('NETWORK');
    expect((e as CapabilityApiError).status).toBe(0);
    expect((e as CapabilityApiError).message).toBe('Failed to fetch');
    expect(dispatched).toEqual([]);
  });

  it('401 不清会话也不跳登录（本域无该副作用，合流批不许顺手加）', async () => {
    const ls = stubSession('tok-expired');
    stubFetch(makeRes({ status: 401, body: { code: 401, message: 'unauthorized' } }));
    const e = await listCapabilities().catch((err) => err);
    expect((e as CapabilityApiError).code).toBe('UNKNOWN');
    expect((e as CapabilityApiError).status).toBe(401);
    expect(ls.getItem('token')).toBe('tok-expired');
    expect(winStub.location.hash).toBe('');
    expect(dispatched).toEqual([]);
  });

  it('403 不发 ecos-no-access 广播（同上，会话/权限副作用留在调用方之外）', async () => {
    stubFetch(makeRes({ status: 403, body: { code: 403, message: 'denied' } }));
    const e = await listCapabilities().catch((err) => err);
    expect((e as CapabilityApiError).status).toBe(403);
    expect(dispatched).toEqual([]);
  });

  it('状态码矩阵零变化：404/400/503(schema)/503(engine)', async () => {
    const cases: Array<[number, string, string]> = [
      [404, 'gone', 'NOT_FOUND'],
      [400, 'bad', 'BAD_REQUEST'],
      [503, 'SCHEMA_NOT_READY: index building', 'SCHEMA_NOT_READY'],
      [503, 'engine disabled', 'ENGINE_DISABLED'],
    ];
    for (const [status, message, code] of cases) {
      stubFetch(makeRes({ status, body: { code: status, message } }));
      const e = await listCapabilities().catch((err) => err);
      expect((e as CapabilityApiError).code, `HTTP ${status} ${message}`).toBe(code);
      expect((e as CapabilityApiError).status).toBe(status);
    }
  });

  it('200 + 信封 code 非成功 ⇒ BUSINESS_ERROR（禁把伪成功当目录）', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 500, message: '内部失败' } }));
    const e = await listCapabilities().catch((err) => err);
    expect((e as CapabilityApiError).code).toBe('BUSINESS_ERROR');
    expect((e as CapabilityApiError).message).toBe('内部失败');
  });

  it('取号顺位仍属域内：X-Trace-Id 头优先，只有 X-Request-Id 时回落到信封 traceId', async () => {
    stubFetch(
      makeRes({
        status: 500,
        body: { code: 500, message: 'boom', traceId: 'trace-env' },
        headers: { 'X-Request-Id': 'trace-req-only' },
      }),
    );
    const e1 = await listCapabilities().catch((err) => err);
    expect((e1 as CapabilityApiError).traceId).toBe('trace-env');

    stubFetch(
      makeRes({
        status: 500,
        body: { code: 500, message: 'boom', traceId: 'trace-env' },
        headers: { 'X-Trace-Id': 'trace-hdr' },
      }),
    );
    const e2 = await listCapabilities().catch((err) => err);
    expect((e2 as CapabilityApiError).traceId).toBe('trace-hdr');
  });

  it('非 JSON 错误体 ⇒ 原文当 message（不塌成空串伪装成功）', async () => {
    stubFetch(makeRes({ status: 502, raw: '<html>gate</html>' }));
    const e = await listCapabilities().catch((err) => err);
    expect((e as CapabilityApiError).message).toBe('<html>gate</html>');
    expect((e as CapabilityApiError).status).toBe(502);
  });
});

// ── 场景动作腿：POST 载荷面 + 结构化错误回传 ───────────

describe('scenarioActionClient 三条契约方法（workspace /actions · /approve）', () => {
  const payload = {
    ownerUserId: 'u-owner',
    dueDate: '2026-12-31',
    expectedImpact: '1234567.89',
    controlMetric: 'M-OPEX-RATIO',
  };

  it('POST 创建：URL/方法/JSON 体/头集合逐字保持，金额仍以字符串透传', async () => {
    const spy = stubFetch(makeRes({ body: { code: 200, data: { actionId: 'a-7' } } }));
    const res = await createScenarioAction('sc1', payload);
    expect(res.actionId).toBe('a-7');
    const call = lastCall(spy);
    expect(call.url).toBe(scenarioActionsUrl('sc1'));
    expect(call.method).toBe('POST');
    expect(JSON.parse(String(call.body))).toEqual(payload);
    expect(call.headers).toEqual({
      'Content-Type': 'application/json',
      Authorization: 'Bearer tok-cap',
    });
  });

  it('400 逐字段回传仍是 ACTION_FIELDS_MISSING + missingFields（结构化面未被归一摧毁）', async () => {
    stubFetch(
      makeRes({
        status: 400,
        body: {
          code: 400,
          errorCode: 'ACTION_FIELDS_MISSING',
          message: 'missing',
          data: { missingFields: ['owner', 'NOT_A_FIELD'] },
        },
        headers: { 'X-Trace-Id': 'trace-act' },
      }),
    );
    const e = await createScenarioAction('sc1', payload).catch((err) => err);
    expect(e).toBeInstanceOf(ScenarioActionError);
    expect((e as ScenarioActionError).code).toBe('ACTION_FIELDS_MISSING');
    expect((e as ScenarioActionError).missingFields).toEqual(['owner']);
    expect((e as ScenarioActionError).traceId).toBe('trace-act');
  });

  it('503/409/403 三条域内分态保持不变', async () => {
    stubFetch(makeRes({ status: 503, body: { errorCode: 'COGNITIVE_UNAVAILABLE', message: 'down' } }));
    expect((await listScenarioActions('sc1').catch((e) => e) as ScenarioActionError).code).toBe(
      'COGNITIVE_UNAVAILABLE',
    );

    stubFetch(makeRes({ status: 409, body: { errorCode: 'BASELINE_RUN_MODE_MISMATCH', message: 'x' } }));
    expect((await approveScenarioAction('a1').catch((e) => e) as ScenarioActionError).code).toBe(
      'BASELINE_RUN_MODE_MISMATCH',
    );

    const ls = stubSession('tok-keep');
    stubFetch(makeRes({ status: 403, body: { message: 'denied' } }));
    const e403 = (await listScenarioActions('sc1').catch((e) => e)) as ScenarioActionError;
    expect(e403.code).toBe('FORBIDDEN');
    expect(ls.getItem('token')).toBe('tok-keep');
    expect(winStub.location.hash).toBe('');
    expect(dispatched).toEqual([]);
  });

  it('网络失败 ⇒ ScenarioActionError(code=NETWORK, status=0) 且原样保留错误文案', async () => {
    stubFetch(() => new Error('ECONNREFUSED'));
    const e = await listScenarioActions('sc1').catch((err) => err);
    expect(e).not.toBeInstanceOf(NetworkError);
    expect((e as ScenarioActionError).code).toBe('NETWORK');
    expect((e as ScenarioActionError).status).toBe(0);
    expect((e as ScenarioActionError).message).toBe('ECONNREFUSED');
    expect(dispatched).toEqual([]);
  });
});

// ── 判据能变红（防恒 0 假绿）───────────────────────────

describe('静态判据能变红', () => {
  it('改前形态（裸 fetch + 域内取号 + 手写 Bearer）被同一组判据抓住', () => {
    const OLD = [
      "import { getAuthToken } from './auth';",
      'const token = getAuthToken();',
      "if (token) headers.Authorization = `Bearer ${token}`;",
      'const res = await fetch(url, { ...init, headers: merged });',
    ].join('\n');
    expect(countMatches(OLD, PRIVATE_TRANSPORT)).toBe(1);
    expect(countMatches(OLD, HAND_AUTH_HEADER)).toBe(1);
    expect(countMatches(OLD, IN_DOMAIN_IDENTITY)).toBe(1);
    expect(countMatches(OLD, CHANNEL_CALL)).toBe(0);
  });

  it('合流形态只有通道调用一处命中，注释里的历史 fetch 不算数', () => {
    const NEW = [
      '// 历史上这里 fetch(url, { headers: { Authorization: `Bearer ${token}` } })，已合流单源',
      'const res = await rawAuthedJsonUrlFetch(url, init);',
      'if (!res.ok) throw new CapabilityApiError({ code: "NOT_FOUND", status: res.status });',
    ].join('\n');
    expect(countMatches(codeOnly(NEW), PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(codeOnly(NEW), HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(codeOnly(NEW), IN_DOMAIN_IDENTITY)).toBe(0);
    expect(countMatches(codeOnly(NEW), CHANNEL_CALL)).toBe(1);
  });
});
