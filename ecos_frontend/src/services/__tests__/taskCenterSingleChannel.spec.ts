// @vitest-environment node
/**
 * taskCenterSingleChannel.spec.ts — §13.2.4 C2「ApiResponse 单解包点」第四批首子批
 * （#143/B5-10c-1）收口契约：`services/taskCenter.ts` 的 6 条私设传输腿。
 *
 * <p>本域是 C2 交集判据（生产文件里「私设 `await fetch(`」∩「手写 `data ??` 解包」）
 * 的最后一族「多腿同文件」载体：`jget`/`jpost`（4+6 个调用点）、`rawGetJson`/`rawPostJson`
 * （6 个 taskPanel*Json 包装）、`taskDorisHealth`/`taskExecuteOk`（两枚裸探测）。
 * 与 #143/B5-10b 的分界＝本域错误文案**不到用户眼前**：`jget`/`jpost` 的失败外显形状是
 * `null`（消费者用 `!!` 真值判据 + `Array.isArray(list.items)`），故收口只需保形状，
 * 不需要 `_base` 那两枚文案还原原语。</p>
 *
 * <p>收口方案＝**按腿部外显形状分派单源通道**（不是一律 `apiFetchData`）：
 * ①`jget`/`jpost` 的返回值是「信封里的 data 本体」⇒ 走 `apiFetchData`（解包点单源）；
 * ②`rawGetJson`/`rawPostJson` 的返回值是「整包信封」，消费者 `useTaskPanel.ts:88/96/110/126/171/192/212`
 * 逐字判 `d.code === 0`／`d.code !== 0` 并读 `d.message`／`d.total` ⇒ 走 `doFetch`
 * （C2 的「单」指解包实现单源，整包语义不拆信封＝不违反单解包点）；
 * ③`taskDorisHealth`/`taskExecuteOk` 把 `res.ok` **本身**当业务答案且必须零 401/403 副作用
 * ⇒ 走 `rawAuthedFetch`（探测通道）。三者都是 httpClient 单源，域内不得再有 `await fetch(`、
 * `data ??` 手写解包或 `authOnlyHeaders()` 私拼 auth 头。</p>
 *
 * <p>差异腿（改前形状逐字锁在注里，属登记的收口差异而非回归）：
 A 非 2xx／伪失败：改前 `!res.ok ⇒ return null` 且 HTTP 200 的 `{code:-1}` 走 `data ?? null`，
 改后由 `apiFetchData` 抛错、域内归一为 `null` ⇒ 外显同值，但单源在抛错前已完成 401 清会话／
 403 广播（改前 401 是静默 null，会话不退）。
 B 网络层失败：改前 `await fetch` reject 直接冒泡，改后归一 `NetworkError` 并被域内收成 `null`
 ⇒ `AsyncTaskCenterView.tsx:110` 的 `if (!list)` 上屏 offlineBanner（与改前 try/catch 分支同值）；
 `runOp` 的 `!!(await map[action](id))`（该文件 :164 无 catch）由改前「整链 reject、无任何 toast」
 变为「报 actionFailed」，方向与「无假成功」红线一致。
 C 信封缺 `data` 键：改前 `null`，改后 `apiFetchData` 回落整个信封 ⇒ 真后端 `ApiResponse`
 恒带 `data`（`TaskController.java:170-181` 成功/失败两侧都构造 data 或 code=-1），故不改判据；
 本批按形状如实锁定。
 D `Content-Type`／`Authorization`：改前 `raw*Json` 裸 fetch 两皆无（`taskPanelBatchJson` 手补
 Content-Type），改后 `doFetch` 恒带两枚；`rawAuthedFetch` 只补 Authorization 不补 Content-Type。
 E 非 2xx 的整包腿：改前 `r.json()` 解析故障体后交消费者判 `code`，改后 `doFetch` 抛错；
 `useTaskPanel.ts` 的 7 个调用点全部在 try/catch 内 ⇒ 外显等价（toast 文案由「未知错误」变状态码）。</p>
 *
 * <p>兼容性红线：URL 拼装逐字（含 `list?` 空 query 的尾问号、`fetchTaskStatus` 的
 * `encodeURIComponent` 与 `taskPanelDetailJson`/`taskExecuteOk` **不**编码的不对称）、
 * body 形态（5 个 body-less POST 不得补 body）、`{ok,json}`／`boolean` 探测形状、
 * `T | null` 返回类型、公开签名一律不变。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  fetchTaskList,
  fetchTaskStatus,
  fetchTaskStats,
  fetchTaskTypes,
  executeTask,
  cancelTask,
  pauseTask,
  resumeTask,
  archiveTask,
  batchTask,
  taskPanelTypesJson,
  taskPanelStatsJson,
  taskPanelListJson,
  taskPanelDetailJson,
  taskPanelActionJson,
  taskPanelBatchJson,
  taskDorisHealth,
  taskExecuteOk,
} from '../taskCenter';

const DOMAIN_FILE = {
  label: 'services/taskCenter.ts',
  src: readFileSync(fileURLToPath(new URL('../taskCenter.ts', import.meta.url)), 'utf8'),
};

const PRIVATE_TRANSPORT = /await fetch\(/;
const HAND_UNPACK = /data\s*\?\?/;
const PRIVATE_AUTH_HEADER = /authOnlyHeaders|authHeaders\(/;

/** 缺陷原形（证明判据能变红，非本域源码） */
const DEFECT =
  'const res = await fetch(url, { headers: authOnlyHeaders() }); const json = await res.json(); return json?.data ?? null;';

/** 收口形状（证明判据不误报） */
const CLEAN =
  "import { apiFetchData, doFetch } from '../api';\nreturn apiFetchData<T>(url, { cache: 'no-store' }).catch(() => null);\n";

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  /** doFetch 只在响应 content-type 含 application/json 时解析，默认给 JSON */
  contentType?: string | null;
  /** true ⇒ json() 抛错（后端非 JSON 响应体） */
  brokenJson?: boolean;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  const ct = spec.contentType === undefined ? 'application/json' : spec.contentType;
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: {
      get: (k: string): string | null => (k.toLowerCase() === 'content-type' ? ct : null),
    },
    text: () => Promise.resolve(text),
    json: () =>
      spec.brokenJson
        ? Promise.reject(new SyntaxError('Unexpected token < in JSON'))
        : Promise.resolve(JSON.parse(text)),
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
 * 会话面桩：`getAuthToken()` 走 localStorage['token']，`handleAuthExpired()` 需要
 * window.location.hash + window.localStorage。stub 后 401 腿可断言「单源副作用真的发生」。
 */
function stubSession(token: string | null) {
  const ls = storageStub();
  const ss = storageStub();
  if (token !== null) ls.setItem('token', token);
  const win = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: vi.fn(() => true),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', ss);
  vi.stubGlobal('window', win);
  return win;
}

/** console.warn 在合流后是「域内吞错」的唯一痕迹，静音以保持输出可读 */
function muteWarn() {
  vi.stubGlobal('console', { ...console, warn: vi.fn() });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('源形自检 — 私设传输/私拆信封/私拼 auth 头不得留在域内', () => {
  it('判据能变红：缺陷原形三条全命中', () => {
    expect(PRIVATE_TRANSPORT.test(DEFECT)).toBe(true);
    expect(HAND_UNPACK.test(DEFECT)).toBe(true);
    expect(PRIVATE_AUTH_HEADER.test(DEFECT)).toBe(true);
  });

  it('services/taskCenter.ts 无 await fetch( 私设传输', () => {
    expect(PRIVATE_TRANSPORT.test(DOMAIN_FILE.src)).toBe(false);
  });

  it('services/taskCenter.ts 无 data ?? 手写解包', () => {
    expect(HAND_UNPACK.test(DOMAIN_FILE.src)).toBe(false);
  });

  it('services/taskCenter.ts 无私拼 auth 头', () => {
    expect(PRIVATE_AUTH_HEADER.test(DOMAIN_FILE.src)).toBe(false);
  });

  it('收口形状不误报：委托单源通道的源形三条判据全不命中', () => {
    expect(PRIVATE_TRANSPORT.test(CLEAN)).toBe(false);
    expect(HAND_UNPACK.test(CLEAN)).toBe(false);
    expect(PRIVATE_AUTH_HEADER.test(CLEAN)).toBe(false);
  });
});

describe('jget 腿 — 解包点单源（apiFetchData）+ 失败形状 T|null', () => {
  it('fetchTaskList 逐字 URL、GET 无 method/body、cache no-store 透传', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { items: [], total: 0 } } }));
    const r = await fetchTaskList({ offset: 0, limit: 100, status: 'RUNNING' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/task/list?offset=0&limit=100&status=RUNNING');
    expect(init.method).toBeUndefined();
    expect(init.body).toBeUndefined();
    expect(init.cache).toBe('no-store');
    expect(r).toEqual({ items: [], total: 0 });
  });

  it('fetchTaskList 空参保留 list? 尾问号（逐字兼容）', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { items: [] } } }));
    await fetchTaskList();
    expect(lastCall(spy).url).toBe('/api/v1/task/list?');
  });

  it('fetchTaskStatus 对 taskId 做 encodeURIComponent', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'RUNNING' } } }));
    const r = await fetchTaskStatus('task 1/x');
    expect(lastCall(spy).url).toBe('/api/v1/task/task%201%2Fx/status');
    expect(r).toEqual({ status: 'RUNNING' });
  });

  it('fetchTaskStats / fetchTaskTypes 逐字 URL 并解包 data 本体', async () => {
    muteWarn();
    const sSpy = stubFetch(makeRes({ body: { code: 0, data: { total: 7 } } }));
    expect(await fetchTaskStats()).toEqual({ total: 7 });
    expect(lastCall(sSpy).url).toBe('/api/v1/task/stats');

    const tSpy = stubFetch(makeRes({ body: { code: 0, data: { categories: [] } } }));
    expect(await fetchTaskTypes()).toEqual({ categories: [] });
    expect(lastCall(tSpy).url).toBe('/api/v1/task/types');
  });

  it('解包语义锁：{code,data} 信封只回 data，整包键不外泄', async () => {
    muteWarn();
    stubFetch(makeRes({ body: { code: 0, message: 'ok', data: { items: [{ taskId: 't1' }] } } }));
    const r = await fetchTaskList();
    expect(r).not.toHaveProperty('code');
    expect(r).not.toHaveProperty('message');
    expect(r).toEqual({ items: [{ taskId: 't1' }] });
  });

  it('data 为 null ⇒ null（不回落整封）', async () => {
    muteWarn();
    stubFetch(makeRes({ body: { code: 0, data: null } }));
    expect(await fetchTaskStats()).toBeNull();
  });

  it('差异腿 A：非 2xx 归一为 null（改前 !res.ok ⇒ null，单源改为抛错后域内收形）', async () => {
    muteWarn();
    stubSession('T');
    const spy = stubFetch(makeRes({ status: 500, raw: 'Internal Server Error' }));
    expect(await fetchTaskStats()).toBeNull();
    expect(lastCall(spy).url).toBe('/api/v1/task/stats');
  });

  it('差异腿 A：伪失败 {code:-1}（HTTP 200）归一为 null，不得假成功', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ body: { code: -1, message: '任务不存在' } }));
    expect(await fetchTaskStats()).toBeNull();
  });

  it('伪失败 {success:false}（HTTP 200）归一为 null', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ body: { success: false, message: 'denied' } }));
    expect(await fetchTaskList()).toBeNull();
  });

  it('差异腿 B：网络层失败由冒泡改判为 null，失败可上屏', async () => {
    muteWarn();
    stubSession('T');
    stubNetworkDown();
    expect(await fetchTaskList({ offset: 0, limit: 100 })).toBeNull();
  });

  it('差异腿 A+：401 单源副作用真的发生（改前静默 null 且会话不退）', async () => {
    muteWarn();
    const win = stubSession('T');
    stubFetch(makeRes({ status: 401, raw: '{"message":"expired"}' }));
    expect(await fetchTaskStats()).toBeNull();
    expect(win.location.hash).toBe('#/login');
  });

  it('差异腿 D：GET 自此带单源 Content-Type + Bearer（改前只有 Authorization）', async () => {
    muteWarn();
    stubSession('TK');
    const spy = stubFetch(makeRes({ body: { code: 0, data: { total: 1 } } }));
    await fetchTaskStats();
    const h = lastHeaders(spy);
    expect(h['Content-Type']).toBe('application/json');
    expect(h.Authorization).toBe('Bearer TK');
  });

  it('无 token 时不伪造 Authorization（单源自身口径）', async () => {
    muteWarn();
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { code: 0, data: { total: 1 } } }));
    await fetchTaskStats();
    expect(lastHeaders(spy).Authorization).toBeUndefined();
  });
});

describe('jpost 腿 — body-less POST 形状不得被补 body', () => {
  const bodyLess: ReadonlyArray<[string, (id: string) => Promise<unknown>, string]> = [
    ['executeTask', (id) => executeTask(id), '/api/v1/task/t%201/execute'],
    ['cancelTask', (id) => cancelTask(id), '/api/v1/task/t%201/cancel'],
    ['pauseTask', (id) => pauseTask(id), '/api/v1/task/t%201/pause'],
    ['resumeTask', (id) => resumeTask(id), '/api/v1/task/t%201/resume'],
    ['archiveTask', (id) => archiveTask(id), '/api/v1/task/t%201/archive'],
  ];

  it.each(bodyLess)('%s：POST 且 body 保持 undefined', async (_label, fn, url) => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { message: 'ok' } } }));
    const r = await fn('t 1');
    const call = lastCall(spy);
    expect(call.url).toBe(url);
    expect(call.init.method).toBe('POST');
    expect(call.init.body).toBeUndefined();
    expect(r).toEqual({ message: 'ok' });
  });

  it('batchTask：body 逐字 {"taskIds":[…],"action":"…"}', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { successCount: 2, errors: [] } } }));
    const r = await batchTask(['a', 'b'], 'cancel');
    expect(lastCall(spy).url).toBe('/api/v1/task/batch');
    expect(lastCall(spy).init.body).toBe(JSON.stringify({ taskIds: ['a', 'b'], action: 'cancel' }));
    expect(r).toEqual({ successCount: 2, errors: [] });
  });

  it('executeTask 解包 data 本体（taskId/result 不外泄整封）', async () => {
    muteWarn();
    stubFetch(makeRes({ body: { code: 0, data: { taskId: 't1', result: 'ASYNC_EXECUTING' } } }));
    expect(await executeTask('t1')).toEqual({ taskId: 't1', result: 'ASYNC_EXECUTING' });
  });

  it('差异腿 A：非 2xx ⇒ null（AsyncTaskCenterView.tsx:164 的 !! 判据据此报失败而非假成功）', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ status: 502, raw: 'Bad Gateway' }));
    expect(await cancelTask('t1')).toBeNull();
    expect(await batchTask(['t1'], 'pause')).toBeNull();
  });

  it('伪失败 {code:-1} ⇒ null（后端 TaskManagementException 走 HTTP 200 + code=-1）', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ body: { code: -1, message: 'task not found' } }));
    expect(await executeTask('t1')).toBeNull();
  });
});

describe('raw 整包腿 — 走 doFetch 后仍返回信封，不拆 data', () => {
  it('taskPanelTypesJson 返回整包 {code,data:[…]}（消费者判 d.code===0 && Array.isArray(d.data))', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: [{ key: 'cron' }] } }));
    const d = await taskPanelTypesJson();
    expect(d).toEqual({ code: 0, data: [{ key: 'cron' }] });
    expect(d.code).toBe(0);
    expect(Array.isArray(d.data)).toBe(true);
    expect(lastCall(spy).url).toBe('/api/v1/task/types');
    expect(lastCall(spy).init.method).toBeUndefined();
  });

  it('taskPanelStatsJson 逐字 URL 且整包', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { running: 3 } } }));
    expect(await taskPanelStatsJson()).toEqual({ code: 0, data: { running: 3 } });
    expect(lastCall(spy).url).toBe('/api/v1/task/stats');
  });

  it('taskPanelListJson 保留 total 键 + 逐字 query', async () => {
    muteWarn();
    const qs = new URLSearchParams();
    qs.set('page', '2');
    qs.set('size', '20');
    const spy = stubFetch(makeRes({ body: { code: 0, data: [{ taskId: 't1' }], total: 41 } }));
    const d = await taskPanelListJson(qs);
    expect(lastCall(spy).url).toBe('/api/v1/task/list?page=2&size=20');
    expect(d.total).toBe(41);
    expect(d.data).toEqual([{ taskId: 't1' }]);
  });

  it('taskPanelDetailJson 对 taskId 不编码（改前逐字不对称，必须保持）', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'DONE' } } }));
    await taskPanelDetailJson('a b/c');
    expect(lastCall(spy).url).toBe('/api/v1/task/a b/c');
  });

  it('taskPanelActionJson：URL 逐字 + body-less POST', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { message: 'ok' } } }));
    const d = await taskPanelActionJson('t1', 'cancel');
    expect(lastCall(spy).url).toBe('/api/v1/task/t1/cancel');
    expect(lastCall(spy).init.method).toBe('POST');
    expect(lastCall(spy).init.body).toBeUndefined();
    expect(d.code).toBe(0);
  });

  it('taskPanelBatchJson：body 逐字（withContentType 形参在单源下失去意义，公开签名不变）', async () => {
    muteWarn();
    const spy = stubFetch(makeRes({ body: { code: 0, data: { successCount: 1, errors: [] } } }));
    await taskPanelBatchJson(['x', 'y'], 'archive');
    expect(lastCall(spy).url).toBe('/api/v1/task/batch');
    expect(lastCall(spy).init.body).toBe(JSON.stringify({ taskIds: ['x', 'y'], action: 'archive' }));
  });

  it('差异腿 E：非 2xx 由「返回故障整包」改为抛错（7 个调用点全在 try/catch 内 ⇒ 外显等价）', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ status: 503, raw: '{"code":-1,"message":"schema not ready"}' }));
    await expect(taskPanelStatsJson()).rejects.toThrow();
    await expect(taskPanelActionJson('t1', 'pause')).rejects.toThrow();
  });

  it('差异腿 D：整包腿自此带 Authorization + Content-Type（改前裸 fetch 两者皆无）', async () => {
    muteWarn();
    stubSession('TZ');
    const spy = stubFetch(makeRes({ body: { code: 0, data: [] } }));
    await taskPanelTypesJson();
    const h = lastHeaders(spy);
    expect(h.Authorization).toBe('Bearer TZ');
    expect(h['Content-Type']).toBe('application/json');
  });

  it('差异腿 D：taskPanelActionJson 自此恒带 Content-Type（改前 withContentType=false）', async () => {
    muteWarn();
    stubSession('TZ');
    const spy = stubFetch(makeRes({ body: { code: 0, data: {} } }));
    await taskPanelActionJson('t1', 'resume');
    expect(lastHeaders(spy)['Content-Type']).toBe('application/json');
  });

  it('非 JSON 响应体：单源返回 null 而非抛解析错（消费者读 d.code 落 TypeError 由 try/catch 吸收）', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ contentType: 'text/html', raw: '<html>500</html>' }));
    expect(await taskPanelStatsJson()).toBeNull();
  });
});

describe('探测腿 — rawAuthedFetch 保持 {ok,json}/boolean 形状且零 401/403 副作用', () => {
  it('taskDorisHealth 拼接结果逐字（防 API_BASE 双前缀成 /api/api/v1/…）', async () => {
    muteWarn();
    stubSession('T');
    const spy = stubFetch(makeRes({ body: { status: 'UP' } }));
    const r = await taskDorisHealth();
    expect(lastCall(spy).url).toBe('/api/v1/task/doris/health');
    expect(r).toEqual({ ok: true, json: { status: 'UP' } });
  });

  it('taskDorisHealth 非 2xx ⇒ {ok:false, json:undefined} 且不抛、不解析', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ status: 500, brokenJson: true }));
    expect(await taskDorisHealth()).toEqual({ ok: false });
  });

  it('taskDorisHealth 401 不跳登录（探测通道：未登录/无权限本身就是业务答案）', async () => {
    muteWarn();
    const win = stubSession('T');
    stubFetch(makeRes({ status: 401, raw: '{"message":"expired"}' }));
    expect(await taskDorisHealth()).toEqual({ ok: false });
    expect(win.location.hash).toBe('');
  });

  it('taskExecuteOk：URL 对 taskId 不编码 + POST 无 body ⇒ 返回 res.ok', async () => {
    muteWarn();
    stubSession('T');
    const spy = stubFetch(makeRes({ status: 200, body: { code: 0 } }));
    expect(await taskExecuteOk('t 1')).toBe(true);
    const call = lastCall(spy);
    expect(call.url).toBe('/api/v1/task/t 1/execute');
    expect(call.init.method).toBe('POST');
    expect(call.init.body).toBeUndefined();
  });

  it('taskExecuteOk 非 2xx ⇒ false（不抛）', async () => {
    muteWarn();
    stubSession('T');
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    expect(await taskExecuteOk('t1')).toBe(false);
  });

  it('taskExecuteOk 401 ⇒ false 且不跳登录', async () => {
    muteWarn();
    const win = stubSession('T');
    stubFetch(makeRes({ status: 401, raw: '{"message":"expired"}' }));
    expect(await taskExecuteOk('t1')).toBe(false);
    expect(win.location.hash).toBe('');
  });

  it('差异腿 D：探测腿自此带 Authorization，但不补 Content-Type（改前形状）', async () => {
    muteWarn();
    stubSession('TP');
    const spy = stubFetch(makeRes({ body: { status: 'UP' } }));
    await taskDorisHealth();
    const h = lastHeaders(spy);
    expect(h.Authorization).toBe('Bearer TP');
    expect(h['Content-Type']).toBeUndefined();
  });

  it('网络层失败仍冒泡为 NetworkError（单源归一，类型变而形状不变）', async () => {
    muteWarn();
    stubSession('T');
    stubNetworkDown();
    await expect(taskDorisHealth()).rejects.toThrow(/Network error|Failed to fetch/);
  });
});
