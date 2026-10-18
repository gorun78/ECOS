// @vitest-environment node
/**
 * kbFetch / agentMeshApi 单源 + 分态用例（#143/B5-11c，台账 §13.2.4 C2′ 注⑪）
 *
 * <p>**本域缺陷本体**（改前）：`pages/knowledge/services/kbFetch.ts` 两处裸 `fetch(`
 * —— `kbFetchData` 的 `await fetch(` 与 `kbSseRequest` 的 `return fetch(`
 * （后者正是 C2′ 判据订立时举证的「非 await 形态原形样本」），Bearer 由域内 `buildHeaders`
 * 手拼；`services/agentMeshApi.ts` 两条通道腿（getJson/postJson）裸 `fetch(url)`
 * 且文件头注释明写「无鉴权头」。</p>
 *
 * <p>**agentMesh 六腿恒 401 取证（D-B5-35，2026-10-19 源级）**：gateway 侧唯一安全链是
 * `sysman-impl/security/SecurityConfig.java:40-41` =
 * `.requestMatchers(AnonymousEndpointRegistry.permitAllPatterns()).permitAll()`
 * ＋ `.anyRequest().authenticated()`；实测 `AnonymousEndpointRegistry` 登记的匿名面
 * 只有 auth/engine health/health/security/audit/knowledge extract 六族，**零条**
 * `agent-mesh` / `agent-runtime` ⇒ 不带 `Authorization` 的六腿改前必 401，
 * 被 `useAgentStore` 的 `catch → set({error})` + `agents: []` 初值折叠成「空网格」假象。
 * 与世界模型四死腿（D-B5-33）的差别是：这六腿的后端映射**实存**——
 * ai-engine-impl `AgentMeshController` @ `/api/agent-mesh`（GET `/agents`、GET `/missions`、
 * POST `/missions`、POST `/missions/{id}/execute`），前端 `/api/v1/agent-mesh/…` 前缀由
 * gateway `VersionPrefixRewriteFilter.java:41` 正向条目改写；agent-service
 * `AgentRuntimeController` @ `/api/v1/agent-runtime`（GET `/telemetry/{agentId}`、POST `/plans`），
 * 且 gateway `pom.xml:56` 已含 `agent-service` ⇒ 经 gateway 可达。
 * **补 Bearer 即转真实可达腿，但本批只到「源码 + 配置」两面取证，未起服 curl、未 live 复验，
 * 不宣布功能已可用。**</p>
 *
 * <p>**本批方案**：`kbFetchData` 委托第四条通道 `apiFetchEnvelope`（拿到 status/原文/traceId
 * 才能在域内做 KbApiError 分态；`apiFetchData` 会在解包前抛错并摧毁分类）；
 * `kbSseRequest` 委托新增的裸通道 `rawAuthedUrlFetch`（返回**未消费**的原始 Response，
 * 帧序解析留给 `services/sseParser` 单源）；`agentMeshApi` 与 B5-11b 的 `worldModelGraphApi`
 * 同形，委托 `apiFetchData`。</p>
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  ApiRequestError,
  NetworkError,
  NO_ACCESS_EVENT,
} from '../../../../services/httpClient';
import {
  KbApiError,
  kbFetchData,
  kbSseRequest,
} from '../kbFetch';
import {
  createMeshMissionJson,
  createRuntimePlanJson,
  executeMeshMissionJson,
  fetchAgentTelemetryJson,
  fetchMeshAgentsJson,
  fetchMeshMissionsJson,
} from '../../../../services/agentMeshApi';

const SRC_KBFETCH = readFileSync(fileURLToPath(new URL('../kbFetch.ts', import.meta.url)), 'utf8');
const SRC_MESH = readFileSync(fileURLToPath(new URL('../../../../services/agentMeshApi.ts', import.meta.url)), 'utf8');
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);
const SRC_APIBASE = readFileSync(
  fileURLToPath(new URL('../../../../__tests__/api-base-single-source.test.ts', import.meta.url)),
  'utf8',
);

const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;
const HARDCODED_API = /["'`]\/api\b/;

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 与 `api-base-single-source` / `privateTransportRatchet` 同口径：注释行不参与硬编码判据。 */
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
  stubSession('tk-11c');
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('源形 — 两文件私设传输归零（C2′ 棘轮第三批）', () => {
  it('kbFetch.ts：零裸 fetch、零手拼 Bearer，两条腿分别委托 envelope 通道与裸 URL 通道', () => {
    const code = codeOnly(SRC_KBFETCH);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HAND_AUTH_HEADER)).toBe(0);
    expect(code).toContain('apiFetchEnvelope(');
    expect(code).toContain('rawAuthedUrlFetch(');
  });

  it('agentMeshApi.ts：零裸 fetch、零硬编码 /api 前缀，基址由 API_BASE 派生', () => {
    const code = codeOnly(SRC_MESH);
    expect(countMatches(code, PRIVATE_TRANSPORT)).toBe(0);
    expect(countMatches(code, HARDCODED_API)).toBe(0);
    expect(code).toContain('apiFetchData(');
  });

  it('两枚棘轮豁免位与 api-base 基线项同批剪除；棘轮声明计数由基线实体自证（不钉死数字）', () => {
    expect(SRC_RATCHET).not.toContain("'pages/knowledge/services/kbFetch.ts'");
    expect(SRC_RATCHET).not.toContain("'services/agentMeshApi.ts'");
    expect(SRC_APIBASE).not.toContain("'services/agentMeshApi.ts'");

    const entryRe = /^\s*'([^']+)':\s*(\d+),$/gm;
    const entries = [...SRC_RATCHET.matchAll(entryRe)];
    expect(entries.length).toBeGreaterThan(0);
    const declaredCount = Number(SRC_RATCHET.match(/expect\(entries\.length\)\.toBe\((\d+)\)/)?.[1]);
    const declaredSum = Number(SRC_RATCHET.match(/n \+ c, 0\)\)\.toBe\((\d+)\)/)?.[1]);
    // matchAll 结果：[0]=整行匹配 [1]=路径 [2]=允许命中数
    expect(declaredCount).toBe(entries.length);
    expect(declaredSum).toBe(entries.reduce((n, m) => n + Number(m[2]), 0));
  });
});

describe('kbFetchData 分态 — KbApiError 矩阵在单源之上原样保留', () => {
  it('5xx + ECOS-KB 码 ⇒ KbApiError(status, code)，traceId 由单源透出', async () => {
    stubFetch(
      makeRes({
        status: 503,
        body: { code: 'ECOS-KB-050', message: '导航表不存在' },
        headers: { 'X-Request-Id': 'tr-050' },
      }),
    );
    const err = await expectError(kbFetchData('/api/v1/knowledge/nav/products')) as KbApiError;
    expect(err).toBeInstanceOf(KbApiError);
    expect(err.status).toBe(503);
    expect(err.code).toBe('ECOS-KB-050');
    expect(err.traceId).toBe('tr-050');
    expect(err.message).toContain('导航表不存在');
  });

  it('403 且业务码只落 errorCode 面（kb-engine 实测形态）⇒ 仍 KbApiError + 单源广播 ecos-no-access', async () => {
    const { dispatched } = stubSession('tk-11c');
    stubFetch(
      makeRes({
        status: 403,
        body: { errorCode: 'ECOS-KB-062', message: '非知识管理员', data: null },
      }),
    );
    const err = await expectError(kbFetchData('/api/v1/knowledge/profiles/p1/publish', { method: 'POST' })) as KbApiError;
    expect(err).toBeInstanceOf(KbApiError);
    expect(err.code).toBe('ECOS-KB-062');
    expect(err.status).toBe(403);
    expect(dispatched.map((d) => d.type)).toContain(NO_ACCESS_EVENT);
  });

  it('404 无业务码 ⇒ 普通 Error 带 status（不是 KbApiError），message 取响应原文/兜 HTTP 码两面', async () => {
    stubFetch(makeRes({ status: 404, raw: '{"detail":"asset gone"}' }));
    const withBody = (await expectError(kbFetchData('/api/v1/knowledge/assets/none'))) as Error & { status?: number };
    expect(withBody).toBeInstanceOf(Error);
    expect(withBody).not.toBeInstanceOf(KbApiError);
    expect(withBody.status).toBe(404);
    expect(withBody.message).toContain('asset gone');

    stubFetch(makeRes({ status: 404, raw: '' }));
    const empty = (await expectError(kbFetchData('/api/v1/knowledge/assets/none'))) as Error & { status?: number };
    expect(empty).not.toBeInstanceOf(KbApiError);
    expect(empty.status).toBe(404);
    expect(empty.message).toBe('HTTP 404');
  });

  it('HTTP 200 包壳 + 字符串业务码 ⇒ 不得读成成功（D-B5-32 同族反假真理）', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 'ECOS-KB-071', message: '状态冲突' } }));
    const err = await expectError(kbFetchData('/api/v1/knowledge/assumptions/a1/submit', { method: 'POST' })) as KbApiError;
    expect(err).toBeInstanceOf(KbApiError);
    expect(err.status).toBe(500);
    expect(err.code).toBe('ECOS-KB-071');
  });

  it('success:false 且 message 内嵌业务码 ⇒ KbApiError(500, code)', async () => {
    stubFetch(makeRes({ status: 200, body: { success: false, message: 'ECOS-KB-072 已下线' } }));
    const err = await expectError(kbFetchData('/api/v1/knowledge/assets/a1/transition', { method: 'POST' })) as KbApiError;
    expect(err).toBeInstanceOf(KbApiError);
    expect(err.code).toBe('ECOS-KB-072');
  });

  it('解包口径与 apiFetchData 一致：信封内层数组、裸数组、code:0 信封三形都不塌', async () => {
    stubFetch(makeRes({ body: { success: true, data: [{ id: 'a1' }] } }));
    await expect(kbFetchData<{ id: string }[]>('/api/v1/knowledge/graph')).resolves.toEqual([{ id: 'a1' }]);

    stubFetch(makeRes({ body: [{ id: 'b1' }, { id: 'b2' }] }));
    await expect(kbFetchData<{ id: string }[]>('/api/v1/knowledge/graph')).resolves.toHaveLength(2);

    stubFetch(makeRes({ body: { code: 0, data: { nodes: [], edges: [] } } }));
    await expect(kbFetchData<{ nodes: unknown[] }>('/api/v1/knowledge/graph')).resolves.toHaveProperty('nodes');
  });

  it('HTTP 200 但响应体非 JSON（网关 HTML）⇒ fail-loud，不塌成 null 读成「后端说没数据」', async () => {
    stubFetch(makeRes({ status: 200, raw: '<html>502 Bad Gateway</html>' }));
    const err = await expectError(kbFetchData('/api/v1/knowledge/stats')) as Error;
    expect(err).toBeInstanceOf(Error);
    expect(err.message).toContain('非 JSON');
  });

  it('401 ⇒ 单源会话副作用（清三 key + 跳 #/login）先执行，域内仍按 status=401 分类', async () => {
    const { ls, win } = stubSession('tk-11c');
    const spy = stubFetch(makeRes({ status: 401, raw: '' }));
    const err = (await expectError(kbFetchData('/api/v1/knowledge/nav/products'))) as Error & { status?: number };
    expect(err.status).toBe(401);
    expect(ls.getItem('token')).toBeNull();
    expect(win.location.hash).toBe('#/login');
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('网络层失败 ⇒ 归一 NetworkError + ecos-network-down（改前原样抛 TypeError，isErrorResponse 两者同判 network）', async () => {
    const { dispatched } = stubSession('tk-11c');
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const err = await expectError(kbFetchData('/api/v1/knowledge/stats')) as Error;
    expect(err).toBeInstanceOf(NetworkError);
    expect(dispatched.map((d) => d.type)).toContain('ecos-network-down');
  });

  it('调用方自定义 headers 为逐键覆盖：Bearer 与 Content-Type 不丢（apiFetchData 的整组替换缺陷 D-B5-34 不适用于本通道）', async () => {
    const spy = stubFetch(makeRes({ body: { success: true, data: [] } }));
    await kbFetchData<{ id: string }[]>('/api/v1/knowledge/nav/categories', { headers: { Accept: 'text/csv' } });
    const h = headersOf(lastCall(spy).init);
    expect(h.Authorization).toBe('Bearer tk-11c');
    expect(h['Content-Type']).toBe('application/json');
    expect(h.Accept).toBe('text/csv');
  });
});

describe('kbSseRequest — 裸通道仍交回未消费的流', () => {
  it('返回原始 Response：单源不得提前 text()/json() 读掉 body（否则帧解析器拿不到 reader）', async () => {
    let textCalls = 0;
    let jsonCalls = 0;
    const streamRes = {
      ok: true,
      status: 200,
      headers: { get: (): string | null => 'text/event-stream' },
      text: () => {
        textCalls += 1;
        return Promise.resolve('data: x\n\n');
      },
      json: () => {
        jsonCalls += 1;
        return Promise.resolve({});
      },
      body: {
        getReader: () => ({
          read: async (): Promise<{ done: boolean; value: Uint8Array | undefined }> => ({
            done: true,
            value: undefined,
          }),
        }),
      },
    } as unknown as Response;
    stubFetch(streamRes);
    const res = await kbSseRequest('/api/v1/knowledge/rag?query=%E4%B8%AD&stream=true');
    expect(res).toBe(streamRes);
    expect(res.body?.getReader).toBeTypeOf('function');
    expect(textCalls).toBe(0);
    expect(jsonCalls).toBe(0);
  });

  it('URL 逐字不变 + Bearer 在场；GET 流式不再带 Content-Type', async () => {
    const spy = stubFetch(makeRes({ status: 200, raw: 'data: {}\n\n' }));
    await kbSseRequest('/api/v1/knowledge/rag?query=a%20b&stream=true');
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/knowledge/rag?query=a%20b&stream=true');
    expect(init.method ?? 'GET').toBe('GET');
    expect(headersOf(init).Authorization).toBe('Bearer tk-11c');
    expect(headersOf(init)['Content-Type']).toBeUndefined();
  });

  it('SSE 腿网络失败同样归一 NetworkError（govern.ts 的 catch→POST 兜底通道因此仍可触发）', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }));
    const err = await expectError(kbSseRequest('/api/v1/knowledge/rag?query=x&stream=true')) as Error;
    expect(err).toBeInstanceOf(NetworkError);
  });
});

describe('agentMeshApi 分态 — 六腿补 Bearer（D-B5-35），URL 逐字不变', () => {
  it('四条 mesh 腿 URL 逐字（/api/v1/agent-mesh/…）且带 Bearer（改前不带 ⇒ 恒 401）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));
    await fetchMeshAgentsJson();
    expect(lastCall(spy).url).toBe('/api/v1/agent-mesh/agents');
    await fetchMeshMissionsJson();
    expect(lastCall(spy).url).toBe('/api/v1/agent-mesh/missions');
    await createMeshMissionJson({ name: 'm1' });
    expect(lastCall(spy).url).toBe('/api/v1/agent-mesh/missions');
    expect(lastCall(spy).init.method).toBe('POST');
    expect(lastCall(spy).init.body).toBe(JSON.stringify({ name: 'm1' }));
    await executeMeshMissionJson('m-9');
    expect(lastCall(spy).url).toBe('/api/v1/agent-mesh/missions/m-9/execute');
    expect(lastCall(spy).init.method).toBe('POST');
    for (const call of spy.mock.calls) {
      expect(headersOf((call[1] ?? {}) as RequestInit).Authorization).toBe('Bearer tk-11c');
    }
  });

  it('两条 runtime 腿 URL 逐字（/api/v1/agent-runtime/…）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { count: 3 } } }));
    await fetchAgentTelemetryJson();
    expect(lastCall(spy).url).toBe('/api/v1/agent-runtime/telemetry/default');
    await createRuntimePlanJson({ id: 'g1' });
    expect(lastCall(spy).url).toBe('/api/v1/agent-runtime/plans');
    expect(lastCall(spy).init.method).toBe('POST');
    expect(headersOf(lastCall(spy).init).Authorization).toBe('Bearer tk-11c');
  });

  it('POST 无 body 腿（execute）不丢 Bearer，且请求体不被伪造空串', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { ok: true } } }));
    await executeMeshMissionJson('m-1');
    const { init } = lastCall(spy);
    expect(headersOf(init).Authorization).toBe('Bearer tk-11c');
    expect(init.body).toBeUndefined();
    expect(headersOf(init)['Content-Type']).toBe('application/json');
  });

  it('errorMessage 存在时包裹单源错误并保留 cause（消费面文案口径不变）', async () => {
    stubFetch(makeRes({ status: 404, raw: 'not found' }));
    const err = await expectError(fetchMeshAgentsJson('Failed to fetch agents')) as Error & { cause?: unknown };
    expect(err.message).toBe('Failed to fetch agents');
    expect(err.cause).toBeInstanceOf(ApiRequestError);
    expect((err.cause as ApiRequestError).httpStatus).toBe(404);
  });

  it('HTTP 200 包壳 code:404 ⇒ 抛出（改前只判 res.ok，把「后端自声明未承流」读成成功）', async () => {
    stubFetch(makeRes({ status: 200, body: { code: 404, message: 'mission 不存在' } }));
    const err = await expectError(executeMeshMissionJson('ghost')) as Error;
    expect(err).toBeInstanceOf(ApiRequestError);
    expect(err.message).toBe('mission 不存在');
  });

  it('消费面 `json.data || json` 在单源解包后不塌：内层数组与内层对象两形都取到真值', async () => {
    stubFetch(makeRes({ body: { code: 0, data: [{ id: 'agent-1' }] } }));
    const agentsEnvelope = await fetchMeshAgentsJson();
    expect(agentsEnvelope.data || agentsEnvelope).toEqual([{ id: 'agent-1' }]);

    stubFetch(makeRes({ body: { code: 0, data: { agentId: 'default', total: 7 } } }));
    const telemetry = await fetchAgentTelemetryJson();
    expect(telemetry.data || telemetry).toEqual({ agentId: 'default', total: 7 });
  });
});
