// @vitest-environment node
/**
 * extractPollSingleChannel.spec.ts — §13.2.4 C2「ApiResponse 单解包点」第四批末子批
 * （#143/B5-10c-3）收口契约：`DocumentUploadTab.tsx` 轮询腿（C2 交集判据的最后一处）。
 *
 * <p>缺陷本体：该腿原先写在页面组件的 `setInterval` 回调里——私设 `await fetch(`
 * ＋ 手拼 `Authorization: Bearer ${getAuthToken()}` ＋ 手写 `json?.data ?? json` 拆信封，
 * 是 httpClient 之外的第四个解包点（台账 §13.2.4 C2 链 9/28 → 6/20 → 4/9 → 3/3 → 本批收口）。</p>
 *
 * <p>收口方案＝**腿体下沉域服务 + 走 apiFetchData 单源**：把轮询那一腿从组件里移到
 * `services/extract.ts`（同域 `fetchExtractStatus` 等已用 `apiFetchData`，非新增范式），
 * 组件只消费 `{source:'backend'|'unavailable'}` 判别结果。下沉的理由是可测性——组件文件
 * 在 node 环境（本机 Node 20.18.1 下 jsdom 依赖链 ERR_REQUIRE_ESM，见 vite.config §W198 注）
 * 无法安全 import，留在组件内则该腿永远只能靠人眼核对。</p>
 *
 * <p>三条必须锁住的等价性（任何一条改绿方式若不成立即为假绿）：
 ①解包等价：`{code:0,data:{…}}` ⇒ data 本体；裸 data ⇒ 整包回落；`data:null`／标量 data ⇒
 归一空对象（改前 `json?.data ?? json` 落错误信封 ⇒ 同样无 status）。
 ②**伪失败不作为**：HTTP 200 但信封 `success:false`／`code` 非 0·200（后端 `ApiResponse.notFound`
 即此形状，`ExtractionController.java:86-95`）时，改前拆到错误信封 ⇒ 无 status ⇒ 保持当前阶段；
 收口后 `apiFetchData` 会抛错，若直接折叠进 `unavailable` 就把「后端应答了」升级成本地伪造
 阶段推进 ⇒ 必须靠 `ApiRequestError.httpStatus === undefined` 辨出这条腿，仍返回 `backend`。
 ③不可用等价：非 2xx／网络失败／响应体非 JSON ⇒ 改前抛错进 catch，收口后仍 `unavailable`。</p>
 *
 * <p>差异腿（改前形状逐字锁在注里，属登记的收口差异而非回归）：
 A `Content-Type`：改前只发 Authorization，改后 `apiFetchData` 恒带 `application/json`。
 B 无 token：改前发字面量 `Bearer `（实测桩值，getAuthToken() 空串插值），改后单源在不取值时不发该头。
 C 401：改前 `!res.ok ⇒ throw 'status 401'` 静默降级、会话保留；改后单源先 `handleAuthExpired()`
 ⇒ 清会话 + `window.location.hash='#/login'`，抛出物再归一为 `unavailable`（外显阶段同值）。
 D 403：改前无任何广播；改后 `notifyNoAccess()` 发 `ecos-no-access`，且 `NoAccessError` 不是
 `ApiRequestError` ⇒ 落 `unavailable`（不清 token，口径与单源一致）。
 E 网络层：改前裸 `TypeError` 冒泡；改后归一 `NetworkError` + `markNetworkDown()` +
 `ecos-network-down` 事件（全局横幅自此可见）。
 F 非 2xx 抛出物形状：改前 `Error('status 500')`，改后 `ApiRequestError`（带 `httpStatus=500`）；
 组件只按 `source` 分派，不读 message ⇒ 外显等价。</p>
 *
 * <p>兼容性红线：URL 逐字（`/api/v1/knowledge/extract/tasks/${taskId}`，**不**引入
 * `encodeURIComponent`——同文件 `fetchExtractStatus` 编码而本腿改前不编码，本批不夹带口径变更）、
 * 6 条状态映射与未知状态「保持当前阶段」的不作为语义、审核取形 `sourceText || ''`／
 * `results || reviewData || null` 的 falsy 口径、`Phase` 八值、轮询周期与本地推进链
 * （其本身是 D-B5-30 已登记的假真理面，本批不处置）全部不变。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { ApiRequestError, NetworkError, NO_ACCESS_EVENT } from '../../../../services/httpClient';
import {
  extractTaskReviewPayload,
  mapExtractTaskPhase,
  pollExtractTask,
  type ExtractPhaseToken,
} from '../extract';

const SERVICE_FILE = {
  label: 'services/extract.ts',
  src: readFileSync(fileURLToPath(new URL('../extract.ts', import.meta.url)), 'utf8'),
};
const PAGE_FILE = {
  label: 'tabs/DocumentUploadTab.tsx',
  src: readFileSync(fileURLToPath(new URL('../../tabs/DocumentUploadTab.tsx', import.meta.url)), 'utf8'),
};

/** §13.2.4 C2 交集判据三面：私设传输 / 手写解包 / 私拼 auth 头 */
const PRIVATE_TRANSPORT = /await fetch\(/;
const HAND_UNPACK = /data\s*\?\?/;
const HAND_AUTH_HEADER = /Authorization:\s*`Bearer \$\{/;

/** 缺陷原形（证明判据能变红，非本域源码） */
const DEFECT =
  "const res = await fetch(url, { headers: { Authorization: `Bearer ${getAuthToken()}` } });" +
  'const json = await res.json(); return json?.data ?? json;';

/** 收口形状（证明判据不误报） */
const CLEAN =
  "import { apiFetchData } from '../../../api';\nreturn apiFetchData<T>(url).then((data) => normalize(data));\n";

/** 切出 `export async function NAME(` 到首个行首 `}` 的函数体，用于按腿判定而非按文件 */
function sliceFn(src: string, name: string): string {
  const start = src.indexOf(`export async function ${name}(`);
  if (start < 0) throw new Error(`function ${name} not found in source`);
  const end = src.indexOf('\n}', start);
  return src.slice(start, end < 0 ? src.length : end);
}

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  contentType?: string | null;
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
  return (lastCall(spy).init.headers ?? {}) as Record<string, string>;
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
 * 会话面桩：`getAuthToken()` 读 localStorage['token']，`handleAuthExpired()` 需要
 * `window.localStorage` + `window.location.hash`，`notifyNetworkDown()`／`notifyNoAccess()`
 * 需要 `window.dispatchEvent`。stub 后差异腿 C/D/E 可断言「单源副作用真的发生」。
 */
function stubSession(token: string | null) {
  const ls = storageStub();
  const ss = storageStub();
  if (token !== null) ls.setItem('token', token);
  const dispatched: string[] = [];
  const win = {
    location: { hash: '' },
    localStorage: ls,
    dispatchEvent: (ev: { type?: string }) => {
      if (ev && typeof ev.type === 'string') dispatched.push(ev.type);
      return true;
    },
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  };
  vi.stubGlobal('localStorage', ls);
  vi.stubGlobal('sessionStorage', ss);
  vi.stubGlobal('window', win);
  return { win, ls, dispatched };
}

const TASK_URL = '/api/v1/knowledge/extract/tasks/f-1';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ═══════════════════════════════════════════════════════════════════════════
describe('源形自检 — C2 三面判据（含判据自身可红可绿的元校验）', () => {
  it('缺陷原形同时命中三面判据（判据非空转）', () => {
    expect(PRIVATE_TRANSPORT.test(DEFECT)).toBe(true);
    expect(HAND_UNPACK.test(DEFECT)).toBe(true);
    expect(HAND_AUTH_HEADER.test(DEFECT)).toBe(true);
  });

  it('收口形状三面皆不命中（判据不误报）', () => {
    expect(PRIVATE_TRANSPORT.test(CLEAN)).toBe(false);
    expect(HAND_UNPACK.test(CLEAN)).toBe(false);
    expect(HAND_AUTH_HEADER.test(CLEAN)).toBe(false);
  });

  it('pollExtractTask 腿体内零私设传输、零手写解包、零私拼 auth 头', () => {
    const body = sliceFn(SERVICE_FILE.src, 'pollExtractTask');
    expect(body).not.toMatch(PRIVATE_TRANSPORT);
    expect(body).not.toMatch(HAND_UNPACK);
    expect(body).not.toMatch(HAND_AUTH_HEADER);
  });

  it('pollExtractTask 腿体确实走 apiFetchData 单源（不是换个姿势私设）', () => {
    expect(sliceFn(SERVICE_FILE.src, 'pollExtractTask')).toMatch(/apiFetchData\s*[<(]/);
  });

  it('DocumentUploadTab.tsx 全文件不再含 C2 三面违例', () => {
    expect(PAGE_FILE.src).not.toMatch(PRIVATE_TRANSPORT);
    expect(PAGE_FILE.src).not.toMatch(HAND_UNPACK);
    expect(PAGE_FILE.src).not.toMatch(HAND_AUTH_HEADER);
    expect(PAGE_FILE.src).not.toMatch(/from '.*services\/auth'/);
  });

  it('轮询腿已从组件迁出，组件只消费域服务的判别结果', () => {
    expect(PAGE_FILE.src).toMatch(/pollExtractTask/);
    expect(PAGE_FILE.src).toMatch(/mapExtractTaskPhase/);
    expect(PAGE_FILE.src).toMatch(/extractTaskReviewPayload/);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('等价腿 — URL 与解包形状（改前后同值）', () => {
  it('URL 逐字：基址、路径、taskId 不编码', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    const r = await pollExtractTask('f-1');
    expect(lastCall(spy).url).toBe(TASK_URL);
    expect(r.source).toBe('backend');
  });

  it('信封 {code:0,data:{…}} ⇒ 取 data 本体', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { status: 'EXTRACTING' } } }));
    const r = await pollExtractTask('f-1');
    expect(r).toEqual({ source: 'backend', task: { status: 'EXTRACTING' } });
  });

  it('裸载荷（无信封）⇒ 整包回落，与改前 `json?.data ?? json` 同值', async () => {
    stubFetch(makeRes({ body: { status: 'COMPLETED' } }));
    const r = await pollExtractTask('f-1');
    expect(r).toEqual({ source: 'backend', task: { status: 'COMPLETED' } });
  });

  it('{code:0,data:null} ⇒ 空载荷归一，status 为空串（改前落错误信封同样无 status）', async () => {
    stubFetch(makeRes({ body: { code: 0, message: 'ok', data: null } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
    expect(mapExtractTaskPhase(r.source === 'backend' ? r.task : null)).toBeNull();
  });

  it('data 为标量 ⇒ 归一空对象，不抛', async () => {
    stubFetch(makeRes({ body: { code: 0, data: 'oops' } }));
    const r = await pollExtractTask('f-1');
    expect(r).toEqual({ source: 'backend', task: { status: '' } });
  });

  it('载荷业务字段原样透传（审核取形依赖 sourceText/results/reviewData）', async () => {
    const results = { entities: [{ code: 'E1' }] };
    stubFetch(makeRes({ body: { code: 0, data: { status: 'PENDING_REVIEW', sourceText: '原文', results } } }));
    const r = await pollExtractTask('f-1');
    expect(r.source === 'backend' && r.task.sourceText).toBe('原文');
    expect(r.source === 'backend' && r.task.results).toEqual(results);
  });

  it('status 非字符串（数字）⇒ String() 归一后无映射 ⇒ 不作为', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { status: 3 } } }));
    const r = await pollExtractTask('f-1');
    expect(r.source === 'backend' && mapExtractTaskPhase(r.task)).toBeNull();
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('等价腿 — HTTP 200 伪失败必须留在 backend 腿（反「扩大假推进面」）', () => {
  it('信封 success:false ⇒ backend 腿 + 空 status（保持当前阶段，不触发本地推进）', async () => {
    stubSession('t0');
    stubFetch(makeRes({ body: { success: false, code: 404, message: '任务不存在: f-1' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
    expect(r.source === 'backend' && mapExtractTaskPhase(r.task)).toBeNull();
  });

  it('信封 code 非 0·200 ⇒ 同上（后端 ApiResponse.notFound/badRequest 真实形状）', async () => {
    stubFetch(makeRes({ body: { code: 404, message: '任务不存在', data: null, traceId: 'tr-1' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
  });

  it('信封 code:500 系统错误也留在 backend 腿——错误文案不到用户眼前，但不得伪推进阶段', async () => {
    stubFetch(makeRes({ body: { code: 500, message: '查询任务失败' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
  });

  it('伪失败腿不清会话、不跳登录（与不可用腿分态）', async () => {
    const { win, ls, dispatched } = stubSession('t0');
    stubFetch(makeRes({ body: { success: false, code: 404, message: '任务不存在' } }));
    await pollExtractTask('f-1');
    expect(ls.getItem('token')).toBe('t0');
    expect(win.location.hash).toBe('');
    expect(dispatched).toEqual([]);
  });

  it('后端应答了 ⇒ 不发 ecos-network-down（改前无此事件，收口后也不得因错误信封而发）', async () => {
    const { dispatched } = stubSession('t0');
    stubFetch(makeRes({ body: { code: -1, message: 'db down' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
    expect(dispatched).toEqual([]);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('等价腿 — 不可用形状（非 2xx／非 JSON／网络失败）', () => {
  it('HTTP 500 ⇒ unavailable（改前 throw "status 500" 进 catch，同值）', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom', contentType: 'text/plain' }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
  });

  it('HTTP 200 但响应体非 JSON ⇒ unavailable（改前 res.json() reject 同值）', async () => {
    stubFetch(makeRes({ brokenJson: true, raw: '<html>502</html>' }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
  });

  it('HTTP 200 且 content-type 非 JSON 但体为合法 JSON ⇒ 仍解析（改前 res.json() 同值；apiFetchData 不看 content-type）', async () => {
    stubFetch(makeRes({ raw: '{"code":0,"data":{"status":"PARSING"}}', contentType: 'text/html' }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
    expect(r.source === 'backend' && mapExtractTaskPhase(r.task)).toBe('parsing');
  });

  it('网络层失败 ⇒ unavailable（外显同值：都进降级腿）', async () => {
    stubNetworkDown();
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
  });

  it('不可用腿透传抛出物（组件不读 message，只按 source 分派）', async () => {
    stubFetch(makeRes({ status: 503, raw: 'unavailable', contentType: 'text/plain' }));
    const r = await pollExtractTask('f-1');
    expect(r.source === 'unavailable' && r.error).toBeInstanceOf(Error);
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('差异腿 — 收口后单源副作用（改前形状见文件头注）', () => {
  it('A 恒带 Content-Type: application/json', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    await pollExtractTask('f-1');
    expect(lastHeaders(spy)['Content-Type']).toBe('application/json');
  });

  it('B 有 token 时经 getAuthToken 单源带 Bearer', async () => {
    stubSession('tok-123');
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    await pollExtractTask('f-1');
    expect(lastHeaders(spy).Authorization).toBe('Bearer tok-123');
  });

  it('B 无 token 时不发出空的 Authorization 头（实测改前发字面量 `Bearer `）', async () => {
    stubSession(null);
    const spy = stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    await pollExtractTask('f-1');
    expect(lastHeaders(spy).Authorization).toBeUndefined();
  });

  it('C 401 ⇒ 单源清会话 + 跳 #/login，且仍归一为 unavailable', async () => {
    const { win, ls } = stubSession('tok-123');
    stubFetch(makeRes({ status: 401, body: { code: 401, message: 'unauthorized' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
    expect(ls.getItem('token')).toBeNull();
    expect(win.location.hash).toBe('#/login');
  });

  it('D 403 ⇒ 广播 ecos-no-access、不清会话，落 unavailable（NoAccessError 非伪失败）', async () => {
    const { win, ls, dispatched } = stubSession('tok-123');
    stubFetch(makeRes({ status: 403, body: { code: 403, message: 'forbidden' } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
    expect(dispatched).toContain(NO_ACCESS_EVENT);
    expect(ls.getItem('token')).toBe('tok-123');
    expect(win.location.hash).toBe('');
  });

  it('E 网络失败 ⇒ 归一 NetworkError 并广播 ecos-network-down（改前只有裸 TypeError、无横幅）', async () => {
    const { dispatched } = stubSession('tok-123');
    stubNetworkDown();
    const r = await pollExtractTask('f-1');
    expect(r.source === 'unavailable' && r.error).toBeInstanceOf(NetworkError);
    expect(dispatched).toContain('ecos-network-down');
  });

  it('F 非 2xx 抛出物带 httpStatus，故不会被误判为「后端应答了」', async () => {
    stubSession('tok-123');
    stubFetch(makeRes({ status: 500, body: { code: 0, data: { status: 'PARSING' } } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('unavailable');
    expect(r.source === 'unavailable' && r.error).toBeInstanceOf(ApiRequestError);
    expect(r.source === 'unavailable' && (r.error as ApiRequestError).httpStatus).toBe(500);
  });

  it('成功腿此后会广播 ecos-network-up 复位横幅（单源口径，改前无）', async () => {
    const { dispatched } = stubSession('tok-123');
    stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    const r = await pollExtractTask('f-1');
    expect(r.source).toBe('backend');
    // notifyNetworkUp 仅在近期 down 过才发；先造一次 down 再成功
    stubNetworkDown();
    await pollExtractTask('f-1');
    stubFetch(makeRes({ body: { code: 0, data: { status: 'PARSING' } } }));
    await pollExtractTask('f-1');
    expect(dispatched).toContain('ecos-network-down');
    expect(dispatched).toContain('ecos-network-up');
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('状态映射表 — 6 条已知状态与「未知即不作为」', () => {
  it.each([
    ['UPLOADED', 'queued'],
    ['PARSING', 'parsing'],
    ['EXTRACTING', 'extracting'],
    ['PENDING_REVIEW', 'reviewing'],
    ['COMPLETED', 'done'],
    ['FAILED', 'failed'],
  ] as const)('%s ⇒ %s', (status, phase) => {
    expect(mapExtractTaskPhase({ status })).toBe(phase as ExtractPhaseToken);
  });

  it('未知状态 ⇒ null（组件据此保持当前阶段）', () => {
    expect(mapExtractTaskPhase({ status: 'RUNNING' })).toBeNull();
  });

  it('空/缺字段/非对象载荷 ⇒ null', () => {
    expect(mapExtractTaskPhase({ status: '' })).toBeNull();
    expect(mapExtractTaskPhase({})).toBeNull();
    expect(mapExtractTaskPhase(null)).toBeNull();
    expect(mapExtractTaskPhase(undefined)).toBeNull();
  });

  it('原型键名不得被当成状态命中（Map 而非对象字面量）', () => {
    for (const k of ['constructor', '__proto__', 'toString', 'hasOwnProperty']) {
      expect(mapExtractTaskPhase({ status: k })).toBeNull();
    }
  });
});

// ═══════════════════════════════════════════════════════════════════════════
describe('审核载荷取形 — 逐字保持改前 falsy 口径', () => {
  it('sourceText 缺失/空串 ⇒ 空串', () => {
    expect(extractTaskReviewPayload({ status: 'PENDING_REVIEW' }).sourceText).toBe('');
    expect(extractTaskReviewPayload({ sourceText: '' }).sourceText).toBe('');
  });

  it('sourceText 为 0 等 falsy ⇒ 空串（`||` 口径，非 String()）', () => {
    expect(extractTaskReviewPayload({ sourceText: 0 }).sourceText).toBe('');
  });

  it('results 优先于 reviewData', () => {
    const r = extractTaskReviewPayload({ results: { entities: [1] }, reviewData: { entities: [2] } });
    expect(r.data).toEqual({ entities: [1] });
  });

  it('results 缺失 ⇒ 回落 reviewData', () => {
    expect(extractTaskReviewPayload({ reviewData: { relations: [] } }).data).toEqual({ relations: [] });
  });

  it('二者皆缺 ⇒ null（面板侧自有兜底，不在此造空对象）', () => {
    expect(extractTaskReviewPayload({ status: 'PENDING_REVIEW' }).data).toBeNull();
  });

  it('results 为空数组 ⇒ 保留空数组（[] 为 truthy，改前同值）', () => {
    expect(extractTaskReviewPayload({ results: [], reviewData: { x: 1 } }).data).toEqual([]);
  });
});
