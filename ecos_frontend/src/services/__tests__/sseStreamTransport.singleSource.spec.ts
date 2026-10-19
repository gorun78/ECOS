/**
 * SSE 流式传输腿单源（#143/B5-13，台账 §13.2.4 C2′ ㉓）
 *
 * <p>**本域缺陷本体**（改前）：三条流式腿各留一份私设传输 + 域内身份拼接——
 * `services/agentLoopStream.ts`(POST `/api/v1/agent-loop/chat`)、
 * `services/wagentRunStream.ts`(GET `/api/v1/wagent/runs/{id}/events`)、
 * `pages/aiworkbench/api.ts#oagPlaygroundStream`(POST `/api/v1/oag/chat/stream`)
 * 都是 `authHeaders()` 自拼 Bearer + 裸 `await fetch(`。前两条走的是 SSE 承流面，
 * 必须拿到未被消费的 `Response.body` ⇒ 不能折进 `apiFetchData`/`apiFetchEnvelope`。</p>
 *
 * <p>**为什么另开 `rawStreamUrlFetch` 而不是复用 `rawAuthedUrlFetch`**（本批的裁决点）：
 * 非流式裸通道带了 PMO-43 网络态归一（`markNetworkDown` + `notifyNetworkDown` +
 * `toNetworkError`）。对「按 AbortSignal 主动停」的流式调用方，这套归一做了两件越权的事：
 * ① 错误对象被改写后 `isAbortError` 可能不再认得用户中止 ⇒ 流结局四分类里的 `aborted`
 * 塌成 `transport`；② 一次流被取消/自然断开被广播成**全局掉线**（跨组件副作用）。
 * 两者都超出「传输合流 = 零语义变化」的授权面 ⇒ 流式腿需要一条只拼头、不改写错误、
 * 不广播网络态的具名通道。全仓 grep `rawStreamUrlFetch` 即流式传输面清单。</p>
 *
 * <p>**判别性取证**（第 5 条用例的存在理由）：第 4 条断言「流式腿不广播 ecos-network-down」
 * 若单看，可能只是因为 node 环境下压根没有 `window` 而空转。故同批用**同一颗 AbortError**
 * 走非流式通道做一次对照，读出该通道确实广播了一次——两条合起来才构成
 * 「通道差异是事实」而非「断言恒真」。</p>
 *
 * <p>**证据边界**：源级 + 单测级证据（fetch 全程被桩替换）。后端未起、未 curl、未 live 复验，
 * 不宣布三条腿的真实 SSE 应答面已验证。</p>
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi, afterEach } from 'vitest';
// D-B5-27 元门禁判据：本 spec 要遍历 src 全树，超时必须**文件级**声明。
vi.setConfig({ testTimeout: 30_000 });
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

import { authHeaders } from '../auth';
import { rawAuthedUrlFetch, rawStreamUrlFetch } from '../httpClient';
import { AGENT_LOOP_CHAT_REL_PATH, streamAgentLoopChat } from '../agentLoopStream';
import { streamWagentRunEvents, wagentRunEventsStreamPath } from '../wagentRunStream';
import { oagPlaygroundStream } from '../../pages/aiworkbench/api';

const SRC_ROOT = fileURLToPath(new URL('../..', import.meta.url));

const LEGS = {
  'services/agentLoopStream.ts': readFileSync(
    fileURLToPath(new URL('../agentLoopStream.ts', import.meta.url)),
    'utf8',
  ),
  'services/wagentRunStream.ts': readFileSync(
    fileURLToPath(new URL('../wagentRunStream.ts', import.meta.url)),
    'utf8',
  ),
  'pages/aiworkbench/api.ts': readFileSync(
    fileURLToPath(new URL('../../pages/aiworkbench/api.ts', import.meta.url)),
    'utf8',
  ),
};

const SRC_HTTP = readFileSync(fileURLToPath(new URL('../httpClient.ts', import.meta.url)), 'utf8');
const SRC_RATCHET = readFileSync(
  fileURLToPath(new URL('../../__tests__/privateTransportRatchet.test.ts', import.meta.url)),
  'utf8',
);

const SSE_ACCEPT = 'text/event-stream';
const PRIVATE_TRANSPORT = /(?:[^A-Za-z0-9_.$-]fetch\s*\(|new\s+WebSocket\s*\(|XMLHttpRequest|\baxios\b)/;
const IN_DOMAIN_IDENTITY = /(?:authHeaders|getAuthToken)\s*\(/;
const AUTH_HEADERS_IMPORT = /import\s*\{[^}]*\bauthHeaders\b[^}]*\}\s*from/;
const STREAM_CHANNEL_CALL = /rawStreamUrlFetch\s*\(/g;
/** `.test()` 用非 global 副本：带 g 的正则有 lastIndex 状态，跨文件复用会漏判（第 2 个文件永远 false）。 */
const STREAM_CHANNEL_TEST = /rawStreamUrlFetch\s*\(/;

/** 与 `privateTransportRatchet` 同口径：注释行不参与判据（注释里写「已收口」不糊绿，写缺陷原形不弄红）。 */
function codeOnly(src: string): string {
  return src
    .split('\n')
    .filter((line) => {
      const t = line.trim();
      return !(t.startsWith('//') || t.startsWith('*') || t.startsWith('/*') || t.startsWith('///'));
    })
    .join('\n');
}

function countMatches(src: string, re: RegExp): number {
  const g = new RegExp(re.source, re.flags.includes('g') ? re.flags : `${re.flags}g`);
  return (src.match(g) ?? []).length;
}

/** 单腿判据（纯函数：真实树与合成违例共用同一份，杜绝两套规则漂移）。 */
function legVerdict(path: string, src: string): string[] {
  const code = codeOnly(src);
  const v: string[] = [];
  if (PRIVATE_TRANSPORT.test(code)) v.push(`${path}: 仍有私设传输腿（裸 fetch / WebSocket / XHR / axios）`);
  if (IN_DOMAIN_IDENTITY.test(code)) v.push(`${path}: 域内仍在拼身份（authHeaders()/getAuthToken() 调用面）`);
  if (AUTH_HEADERS_IMPORT.test(code)) v.push(`${path}: 仍在引入 authHeaders（身份拼接已归单源）`);
  const calls = countMatches(code, STREAM_CHANNEL_CALL);
  if (calls !== 1) v.push(`${path}: 具名流式通道调用数 = ${calls}（应为 1）`);
  return v;
}

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  headers?: Record<string, string>;
}

/**
 * `body` 恒为 null：本 spec 只验传输面（URL/method/头/信号）与失败分态，
 * 承流解析由 `sseChannelSingleSource` / 帧序契约两门属主，不在此重复。
 */
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
    body: null,
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
  const init = (call[1] ?? {}) as RequestInit;
  return {
    url: String(call[0]),
    init,
    headers: { ...((init.headers ?? {}) as Record<string, string>) },
    json: (init.body === undefined ? {} : JSON.parse(String(init.body))) as Record<string, unknown>,
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

/** 事件记录面：`window` 必须存在，否则 PMO-43 广播在 `typeof window === 'undefined'` 处就短路了（断言会假真）。 */
let dispatched: Array<{ type: string; detail?: unknown }> = [];

function stubSession(token: string | null) {
  const ls = storageStub();
  if (token !== null) ls.setItem('token', token);
  dispatched = [];
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
}

const abortError = (): Error => Object.assign(new Error('The user aborted a request.'), { name: 'AbortError' });

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('SSE 流式三腿传输合流（#143/B5-13）', () => {
  it('agentLoop 腿：URL/method/body/signal 逐面不变，头集合与改前 authHeaders()+Accept 逐键相等', async () => {
    stubSession('tok-loop');
    const ctrl = new AbortController();
    const spy = stubFetch(makeRes({ status: 200, headers: { 'content-type': SSE_ACCEPT } }));

    await streamAgentLoopChat({
      agentId: 'a-1',
      message: '你好',
      thread: 't/half',
      lastEventId: '7',
      abortSignal: ctrl.signal,
    });

    const call = lastCall(spy);
    expect(call.url).toBe(`/api${AGENT_LOOP_CHAT_REL_PATH}?thread=${encodeURIComponent('t/half')}`);
    expect(call.init.method).toBe('POST');
    expect(call.json).toEqual({ agentId: 'a-1', message: '你好' });
    expect(call.init.signal).toBe(ctrl.signal);
    // 逐键对照**活的** authHeaders()（同一桩会话）：新增键/漏键都会在这里现形，而不是靠人读注释。
    expect(call.headers).toEqual({ ...authHeaders(), Accept: SSE_ACCEPT, 'Last-Event-ID': '7' });
  });

  it('wagent run 腿：GET 路径仍出自单源 path 助手，头集合与改前逐键相等（不新增、不删除键）', async () => {
    stubSession('tok-run');
    const ctrl = new AbortController();
    const spy = stubFetch(makeRes({ status: 200, headers: { 'content-type': SSE_ACCEPT } }));

    await streamWagentRunEvents({ runId: 'run-9', abortSignal: ctrl.signal });

    const call = lastCall(spy);
    expect(call.url).toBe(`/api${wagentRunEventsStreamPath('run-9')}`);
    expect(call.init.method).toBe('GET');
    expect(call.init.signal).toBe(ctrl.signal);
    expect(call.headers).toEqual({ ...authHeaders(), Accept: SSE_ACCEPT });
    expect(call.headers['Last-Event-ID']).toBeUndefined();
  });

  it('OAG 腿：头集合恰等于改前 authHeaders()（本腿历史上就没有 Accept，传输合流不许顺手加）', async () => {
    stubSession('tok-oag');
    const ctrl = new AbortController();
    const spy = stubFetch(makeRes({ status: 500, raw: 'gateway exploded' }));

    await expect(
      oagPlaygroundStream({
        message: 'm',
        model: 'deepseek-chat',
        temperature: 0.2,
        maxTokens: 128,
        sessionId: 's-1',
        abortSignal: ctrl.signal,
        handlers: {} as never,
      }),
    ).rejects.toThrow('OAG stream failed: 500');

    const call = lastCall(spy);
    expect(call.url).toBe('/api/v1/oag/chat/stream');
    expect(call.init.method).toBe('POST');
    expect(call.json).toEqual({
      message: 'm',
      model: 'deepseek-chat',
      temperature: 0.2,
      maxTokens: 128,
      sessionId: 's-1',
    });
    expect(call.init.signal).toBe(ctrl.signal);
    expect(call.headers).toEqual(authHeaders());
    expect(call.headers.Accept).toBeUndefined();
  });

  it('无 token 会话：流式腿不出现 Authorization 键（拼身份的条件仍只活在单源里）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ status: 200, headers: { 'content-type': SSE_ACCEPT } }));

    await streamAgentLoopChat({ agentId: 'a-2', message: 'x' });

    const call = lastCall(spy);
    expect('Authorization' in call.headers).toBe(false);
    expect(call.headers).toEqual({ 'Content-Type': 'application/json', Accept: SSE_ACCEPT });
  });

  it('AbortError 原样抛给调用方：aborted 不被归一化成 transport，且一次取消不广播全局掉线', async () => {
    stubSession('tok-abort');
    stubFetch(abortError);

    const loop = await streamAgentLoopChat({ agentId: 'a-3', message: 'y' });
    expect(loop.failure?.kind).toBe('aborted');

    const run = await streamWagentRunEvents({ runId: 'run-3' });
    expect(run.failure?.kind).toBe('aborted');

    // OAG 腿无四分类容器，异常直传给调用方：抛出的必须还是那颗 AbortError，而非 NetworkError。
    await expect(
      oagPlaygroundStream({ message: 'z', abortSignal: new AbortController().signal, handlers: {} as never }),
    ).rejects.toMatchObject({ name: 'AbortError' });

    expect(dispatched.filter((e) => e.type === 'ecos-network-down')).toEqual([]);
  });

  it('判别性对照：同一颗 AbortError 走非流式通道必然广播 ecos-network-down（证明上一条不是恒真空断言）', async () => {
    stubSession('tok-ctl');
    stubFetch(abortError);

    await expect(rawAuthedUrlFetch('/v1/probe')).rejects.toBeInstanceOf(Error);
    const downs = dispatched.filter((e) => e.type === 'ecos-network-down');
    expect(downs).toHaveLength(1);

    // 同批钉住具名流式通道的正身：一次 AbortError 既不抛网络态、也不留任何广播。
    dispatched = [];
    stubFetch(abortError);
    await expect(rawStreamUrlFetch('/api/v1/any/stream')).rejects.toMatchObject({ name: 'AbortError' });
    expect(dispatched).toEqual([]);
  });

  it('非 2xx 分类面未被合流改变：信封 message/traceId 照旧提取，status 透出不塌成 null', async () => {
    stubSession('tok-503');
    stubFetch(
      makeRes({
        status: 503,
        headers: { 'content-type': 'application/json' },
        body: { message: 'SCHEMA_NOT_READY：图谱未就绪', traceId: 'T-503' },
      }),
    );

    const loop = await streamAgentLoopChat({ agentId: 'a-4', message: 'q' });
    expect(loop.failure).toEqual({
      kind: 'transport',
      status: 503,
      message: 'SCHEMA_NOT_READY：图谱未就绪',
      traceId: 'T-503',
    });

    const run = await streamWagentRunEvents({ runId: 'run-4' });
    // 判别式取窄面而非逐键读：`WagentStreamFailure` 是四态 union，contract 态没有 status/traceId 键
    expect(run.failure).toMatchObject({ kind: 'transport', status: 503, traceId: 'T-503' });
    // 5xx 是服务端答案而非「网络不可达」⇒ 不该有掉线广播（合流前后同口径）
    expect(dispatched.filter((e) => e.type === 'ecos-network-down')).toEqual([]);
  });

  it('三条腿代码面：零私设传输、零域内拼身份、各自恰一次具名流式通道', () => {
    const violations = Object.entries(LEGS).flatMap(([path, src]) => legVerdict(path, src));
    expect(violations).toEqual([]);
  });

  it('判据反向自检：合成违例必须各判出该判的红（否则上面两条是自证清白）', () => {
    const old = [
      'import { authHeaders } from "./auth";',
      'const res = await fetch(`${API_BASE}${path}`, { headers: authHeaders() });',
    ].join('\n');
    const bad = legVerdict('services/demo/old.ts', old);
    expect(bad).toHaveLength(4);
    expect(bad.join('\n')).toContain('私设传输腿');
    expect(bad.join('\n')).toContain('域内仍在拼身份');
    expect(bad.join('\n')).toContain('仍在引入 authHeaders');
    expect(bad.join('\n')).toContain('调用数 = 0');

    // 双调用点也违例：合流是「一条腿一次通道」，不许在域内再包一层重试通道。
    const twice = legVerdict(
      'services/demo/twice.ts',
      'const a = await rawStreamUrlFetch(u1);\nconst b = await rawStreamUrlFetch(u2);',
    );
    expect(twice.join('\n')).toContain('调用数 = 2');

    // 注释既糊不住门、也不把闸弄红
    const prose = legVerdict(
      'services/demo/prose.ts',
      [
        '// 历史上这里 await fetch(url, { headers: authHeaders() })，现已合流 rawStreamUrlFetch(',
        'const res = await rawStreamUrlFetch(url, { method: "GET" });',
      ].join('\n'),
    );
    expect(prose).toEqual([]);
  });

  it('rawStreamUrlFetch 通道体不引用 PMO-43 归一化三件套（流式腿的边界是通道本体，不只是调用方约定）', () => {
    const start = SRC_HTTP.indexOf('export async function rawStreamUrlFetch');
    expect(start).toBeGreaterThan(-1);
    const end = SRC_HTTP.indexOf('\n}', start);
    expect(end).toBeGreaterThan(start);
    const body = SRC_HTTP.slice(start, end);
    for (const forbidden of ['markNetworkDown', 'notifyNetworkDown', 'toNetworkError']) {
      expect(body.includes(forbidden)).toBe(false);
    }
    // 但拼身份仍出自单源的 getAuthToken —— 通道只是不归一化，不是不鉴权
    expect(body.includes('getAuthToken')).toBe(true);
  });

  it('流式传输面清单可核对：全仓 rawStreamUrlFetch 调用点恰为三条腿 + 通道定义', () => {
    const files: string[] = [];
    const walk = (dir: string): void => {
      for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) {
          if (entry === '__tests__' || entry === '__snapshots__') continue;
          walk(full);
          continue;
        }
        if (!/\.tsx?$/.test(entry) || /\.(test|spec)\.tsx?$/.test(entry) || /\.d\.ts$/.test(entry)) continue;
        if (STREAM_CHANNEL_TEST.test(codeOnly(readFileSync(full, 'utf8')))) {
          files.push(relative(SRC_ROOT, full).split(sep).join('/'));
        }
      }
    };
    walk(SRC_ROOT);
    expect([...files].sort()).toEqual(
      ['pages/aiworkbench/api.ts', 'services/agentLoopStream.ts', 'services/httpClient.ts', 'services/wagentRunStream.ts'],
    );
  });

  it('棘轮基线已剪本批三项，且元断言数与自身条目实数同号（防「剪了代码没剪基线」的反向漂移）', () => {
    const start = SRC_RATCHET.indexOf('const BASELINE');
    const block = SRC_RATCHET.slice(start, SRC_RATCHET.indexOf('});', start));
    const entries = [...block.matchAll(/'([^']+)':\s*(\d+)/g)].map((m) => [m[1], Number(m[2])] as const);
    for (const leg of Object.keys(LEGS)) {
      expect(entries.some(([rel]) => rel === leg)).toBe(false);
    }
    const declaredCount = /entries\.length\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1];
    const declaredSum = /n \+ c, 0\)\)\.toBe\((\d+)\)/.exec(SRC_RATCHET)?.[1];
    expect(Number(declaredCount)).toBe(entries.length);
    expect(Number(declaredSum)).toBe(entries.reduce((n, [, c]) => n + c, 0));
  });
});
