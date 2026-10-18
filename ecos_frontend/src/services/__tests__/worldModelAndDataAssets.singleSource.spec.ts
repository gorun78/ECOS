// @vitest-environment node
/**
 * #143/B5-11b 单源 + 分态用例：`services/worldModelGraphApi.ts` ＋ `services/dataAssetsApi.ts`
 * （台账 §13.2.4 C2′ 收口注族；新登 D-B5-33 世界模型承流矩阵 / D-B5-34 单源 headers 整组替换）
 *
 * <p>**改前缺陷本体（实测取证，2026-10-19）**：
 * ① `worldModelGraphApi.ts` 自带 2 处私设 `await fetch(`，且头注释明写「无鉴权头」——
 * 而 gateway `SecurityConfig` 取数自 `AnonymousEndpointRegistry.permitAllPatterns()`
 * （runtime/common-api，实测登记 12 条 = 9 ANONYMOUS + 3 CLEARANCE_EXEMPT、permitAll 展开 10 条，
 * 其中零条 world-model／`/api/v1/ecos` 登记；此处原记「46 条」系 #143/B5-11b 误测，#143/B5-11d 勘误，
 * 「零条命中 ⇒ 改前恒 401」的结论不受影响）
 * ＋ `anyRequest().authenticated()` ⇒ **五腿改前恒 401**，不是此前记载的「两腿错挂前缀」
 * （该归因已在台账勘定）。前缀错位真实存在但为 **4/5**：sysman
 * `WorldModelGraphController` @ `/api/v1/ecos/world-model-graph` 只有
 * `/goals`(/tree/`{id}`)、`/links`(/`{id}`)、`/causal-graph` 七枚映射 ⇒
 * 本文件所调 `/state`、GET `/scenarios`、POST `/scenarios`、POST `/strategy/recommend`
 * 在本前缀下零映射；cognitive `WorldModelController` @ `/api/v1/world-model`
 * 有 `/state`、POST `/scenarios`、`/strategy/recommend`、`/causal-graph` 四枚
 * **显式 501 stub**（`data.stub=true`）。改后补上 Bearer ⇒ `/causal-graph` 转真可达，
 * 余四腿由恒 401 转为显式 404 未承流（不伪造数据、不改 URL 定档，见 R-52①）。</p>
 *
 * <p>② `dataAssetsApi.ts` 的 `{ headers: {...}, ...init }` 展开序在 `init.headers`
 * 存在时整组替换掉 Content-Type + Authorization（实测当前 6 个调用点均未传 headers
 * ⇒ **潜在态、零现网触发**，不夸大为已发生）；改后委托 `apiFetchData`，并显式拒绝
 * 传 headers 的调用（同形缺陷在单源 `{ headers, ...options }` 里依然存在 = D-B5-34）。</p>
 *
 * <p>③ **解包形状变更（调用方同批改，本文件钉死）**：`apiFetchData` 返回信封 `data`
 * 内层，故 `DataAssetsDashboard` 六处 `resp.data` 必须去掉，否则 `resp.data` 恒
 * undefined ⇒ 空列表假象；世界模型两消费面（`queries/useEcosApi.ts`、
 * `stores/cognitive/useCognitiveStore.ts`）写的是 `json.data || json`，解包后对象
 * 无 `data` 键 ⇒ 回落取整包，形状兼容，本文件用真实信封样本证之。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  fetchCausalGraphJson,
  fetchScenariosJson,
  fetchWorldStateJson,
  postSimulationJson,
  postStrategyRecommendJson,
} from '../worldModelGraphApi';
import { dataAssetsRequest } from '../dataAssetsApi';

const SRC_WMG = readFileSync(fileURLToPath(new URL('../worldModelGraphApi.ts', import.meta.url)), 'utf8');
const SRC_DAA = readFileSync(fileURLToPath(new URL('../dataAssetsApi.ts', import.meta.url)), 'utf8');

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_UNPACK = /data\s*\?\?/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;
const HARDCODED_API = /["'`]\/api\b/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 与 `api-base-single-source`／棘轮门同口径：注释行不参与判据。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

function makeRes(spec: { status?: number; body?: unknown; headers?: Record<string, string> }): Response {
  const status = spec.status ?? 200;
  const text = spec.body === undefined ? '' : JSON.stringify(spec.body);
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
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', storageStub());
  vi.stubGlobal('window', {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn((): boolean => true),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  });
}

const WMG = '/api/v1/ecos/world-model-graph';
const CAUSAL_ENVELOPE: {
  code: number;
  success: boolean;
  message: string;
  data: { nodes: { id: string; label: string }[]; edges: unknown[]; nodeCount: number; edgeCount: number };
} = {
  code: 0,
  success: true,
  message: 'ok',
  data: { nodes: [{ id: 'g1', label: '总体目标' }], edges: [], nodeCount: 1, edgeCount: 0 },
};

beforeEach(() => stubSession('tk-11b'));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形自检 — 两文件零私设传输（C2′ 棘轮剪除后的钉）', () => {
  it('worldModelGraphApi.ts 零私设传输 / 零手写解包 / 零私拼 auth 头 / 零 /api 字面', () => {
    const code = codeOnly(SRC_WMG);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_UNPACK)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(countMatches(code, HARDCODED_API)).toBe(0);
    expect(SRC_WMG).toContain('from "./httpClient"');
  });

  it('dataAssetsApi.ts 零私设传输、零 authOnlyHeaders 手拼、零 /api 字面', () => {
    const code = codeOnly(SRC_DAA);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HARDCODED_API)).toBe(0);
    expect(SRC_DAA).not.toMatch(/authOnlyHeaders/);
  });

  it('棘轮基线不得留下这两枚陈旧豁免位', () => {
    const ratchet = readFileSync(
      fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
      'utf8',
    );
    expect(ratchet).not.toContain("'services/worldModelGraphApi.ts'");
    expect(ratchet).not.toContain("'services/dataAssetsApi.ts'");
    // 计数不在此钉死（钉住数字 = 每批都要回来改一行，且改漏了就变红）；
    // 「声明数与基线实体一致」由 #143/B5-11c 的棘轮自校验用例从基线对象本身推导。
  });
});

describe('worldModelGraphApi 分态 — 五腿 URL 逐字、Bearer 补齐、解包一层', () => {
  it('GET /state：URL 逐字不变且补上 Bearer（改前不带鉴权头 ⇒ 恒 401）', async () => {
    const spy = stubFetch(makeRes({ body: CAUSAL_ENVELOPE }));
    await fetchWorldStateJson();
    const { url, init } = lastCall(spy);
    expect(url).toBe(`${WMG}/state`);
    expect((init.headers as Record<string, string>)?.Authorization).toBe('Bearer tk-11b');
  });

  it('GET /causal-graph：解到信封 data 内层，消费面 `json.data || json` 回落取整包（形状兼容）', async () => {
    stubFetch(makeRes({ body: CAUSAL_ENVELOPE }));
    const json = await fetchCausalGraphJson('Failed to fetch causal graph');
    expect(json).toEqual(CAUSAL_ENVELOPE.data);
    expect(json.data).toBeUndefined();
    expect(json.data || json).toEqual(CAUSAL_ENVELOPE.data);
    expect(json.nodes).toHaveLength(1);
  });

  it('GET /scenarios：404 真实未承流转为显式抛错，不再静默成空数据', async () => {
    // 单源 `apiFetchData` 的 message 口径 = `text || HTTP ${status}`：
    // body 非空时透出 body 原文，body 空时才回落 `HTTP 404`。两种形态都必须抛错。
    stubFetch(makeRes({ status: 404, body: 'not found' }));
    const withBody = await fetchScenariosJson().then((): null => null, (e: unknown): unknown => e);
    expect(withBody).toBeInstanceOf(Error);
    expect((withBody as Error).message).toContain('not found');

    stubFetch(makeRes({ status: 404 }));
    const emptyBody = await fetchScenariosJson().then((): null => null, (e: unknown): unknown => e);
    expect((emptyBody as Error).message).toContain('HTTP 404');
  });

  it('POST /scenarios：body 序列化 + method 正确，且传 body 不丢 Authorization（D-B5-34 防线）', async () => {
    const spy = stubFetch(makeRes({ body: CAUSAL_ENVELOPE }));
    await postSimulationJson({ scenarioId: 's1' });
    const { url, init } = lastCall(spy);
    expect(url).toBe(`${WMG}/scenarios`);
    expect(init.method).toBe('POST');
    expect(init.body).toBe(JSON.stringify({ scenarioId: 's1' }));
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer tk-11b');
    expect(headers['Content-Type']).toBe('application/json');
  });

  it('POST /strategy/recommend：goal 逐字 encode 且无 body 腿仍带鉴权头', async () => {
    const spy = stubFetch(makeRes({ body: CAUSAL_ENVELOPE }));
    await postStrategyRecommendJson('提升 复购率 & 达成');
    const { url, init } = lastCall(spy);
    expect(url).toBe(`${WMG}/strategy/recommend?goal=${encodeURIComponent('提升 复购率 & 达成')}`);
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer tk-11b');
  });

  it('errorMessage 语义保持：传入则包裹为业务文案、cause 保留单源原错；缺省则透出原错', async () => {
    stubFetch(makeRes({ status: 500, body: 'boom' }));
    const wrapped = await fetchWorldStateJson('Simulation failed').then(
      (): null => null,
      (e: unknown): unknown => e,
    );
    expect((wrapped as Error).message).toBe('Simulation failed');
    expect((wrapped as Error).cause).toBeInstanceOf(Error);

    const raw = await fetchWorldStateJson().then((): null => null, (e: unknown): unknown => e);
    expect((raw as Error).message).not.toBe('Simulation failed');
  });

  it('无 token 时不带 Authorization 头（不伪造会话）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: CAUSAL_ENVELOPE }));
    await fetchWorldStateJson();
    const headers = lastCall(spy).init.headers as Record<string, string>;
    expect(headers.Authorization).toBeUndefined();
  });

  it('200 包壳业务错（code 非 0/200）不得当成功返回', async () => {
    stubFetch(makeRes({ body: { code: 500, success: false, message: '构建因果图失败', data: null } }));
    const err = await fetchCausalGraphJson().then((): null => null, (e: unknown): unknown => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toContain('构建因果图失败');
  });
});

describe('dataAssetsApi 分态 — 委托单源 + 解包形状变更 + 拒绝对抗整组替换', () => {
  it('GET 列表：返回信封 data 内层数组（调用方须去掉 .data）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, success: true, data: ['L1', 'L2'] } }));
    const resp = await dataAssetsRequest<string[]>('/api/v1/datanet/levels');
    expect(lastCall(spy).url).toBe('/api/v1/datanet/levels');
    expect(resp).toEqual(['L1', 'L2']);
    expect((resp as unknown as { data?: unknown }).data).toBeUndefined();
  });

  it('非信封裸数组：整包透出（不塌 null）', async () => {
    stubFetch(makeRes({ body: [1, 2, 3] }));
    await expect(dataAssetsRequest<number[]>('/api/v1/datanet/assets')).resolves.toEqual([1, 2, 3]);
  });

  it('POST 带 body：Bearer 与 Content-Type 同时在场，method/body 原样透传', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { ok: true } } }));
    const out = await dataAssetsRequest('/api/v1/datanet/assets/a1/security-tag', {
      method: 'POST',
      body: JSON.stringify({ confirmed: true }),
    });
    expect(out).toEqual({ ok: true });
    const { init } = lastCall(spy);
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer tk-11b');
    expect(headers['Content-Type']).toBe('application/json');
    expect(init.method).toBe('POST');
  });

  it('传 headers 显式拒绝对抗（单源整组替换缺陷 D-B5-34 不可被静默触发）', async () => {
    stubFetch(makeRes({ body: { code: 0, data: [] } }));
    await expect(
      dataAssetsRequest('/api/v1/datanet/levels', { headers: { 'X-Tenant': 't1' } }),
    ).rejects.toThrow(/D-B5-34/);
  });

  it('401：单源会话副作用生效（退登/清 token），域内不再吞错成空列表', async () => {
    stubFetch(makeRes({ status: 401, body: '' }));
    const err = await dataAssetsRequest('/api/v1/datanet/levels').then(
      (): null => null,
      (e: unknown): unknown => e,
    );
    expect(err).toBeInstanceOf(Error);
  });

  it('网络层失败转 NetworkError（不再 TypeError 裸抛）', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const err = await dataAssetsRequest('/api/v1/datanet/levels').then(
      (): null => null,
      (e: unknown): unknown => e,
    );
    expect((err as Error).message).toMatch(/Network error|Failed to fetch/);
  });
});
