// @vitest-environment node
/**
 * metadataDatasourceSingleChannel.spec.ts — §13.2.4 C2「ApiResponse 单解包点」
 * DW 域传输合流第三批（错误文案对用户可见的一组）收口契约（#143/B5-10b）。
 *
 * <p>范围＝#143/B5-8 收口 `_base.ts` 之后 C2 交集载体里「非 2xx／伪失败的文案会走到用户
 * 眼前」的两个文件：`api/metadata.ts`（10 处私设传输）、`api/datasource.ts`
 * 的 `fetchSchemaPreview`（1 处）。二者合流后域内不得再有 `await fetch(`、手写解包
 * 「信封 data 缺失即回落整个信封」的形态，也不得私拼 auth 头。</p>
 *
 * <p>本批与 #143/B5-10 的分界＝错误腿是否用户可见：`fetchVersionDiff` 的
 * `{error: string}` 被 `HistoryVersionCompareModal.tsx:108` 直接 `setDiffError(r.error)`
 * 渲染，`fetchSchemaPreview` 的 `{ok:false,error}` 被 `Modals.tsx:354` 直接
 * `setTestResult({ok:false,msg:result.error})` 渲染 ⇒ 文案必须经单源还原口径取得，
 * 禁在域内重拆信封。还原口径由 `_base` 单点提供（`httpStatusText`／`envelopeErrorText`）。</p>
 *
 * <p>兼容性红线：URL 拼装（含 `?_=<ts>` 缓存破坏串与 `cache:'no-store'`）、body 形态
 * （含两个 body-less POST 采集端点）、成功面返回形状逐字保持。差异腿如实锁定并在用例内
 * 注明改前形状，属登记的收口差异而非回归。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  triggerMetadataCollect,
  fetchCollectStatus,
  triggerCollectSync,
  fetchActiveCollectTasks,
  fetchCollectDiff,
  fetchVersionHistory,
  fetchVersionDiff,
  fetchPreview,
  fetchFields,
  saveMetadataStrategy,
  fetchDataSourceResources,
} from '../metadata';
import { fetchSchemaPreview } from '../datasource';

const DOMAIN_FILES: { readonly label: string; readonly src: string }[] = [
  { label: 'api/metadata.ts', src: readFileSync(fileURLToPath(new URL('../metadata.ts', import.meta.url)), 'utf8') },
  { label: 'api/datasource.ts', src: readFileSync(fileURLToPath(new URL('../datasource.ts', import.meta.url)), 'utf8') },
];

const PRIVATE_TRANSPORT = /await fetch\(/;
const HAND_UNPACK = /data\s*\?\?/;
const PRIVATE_AUTH_HEADER = /authOnlyHeaders|authHeaders\(/;

/** 缺陷原形（证明判据能变红，非本域源码） */
const DEFECT = 'const res = await fetch(url, { headers: authHeaders() }); const json = await res.json(); const rows = json?.data?.data ?? json?.data ?? json;';

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
  /** true ⇒ json() 抛错（后端非 JSON 错误体） */
  brokenJson?: boolean;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (): string | null => null },
    text: () => Promise.resolve(text),
    json: () => (spec.brokenJson ? Promise.reject(new Error('not json')) : Promise.resolve(JSON.parse(text))),
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

  it.each(DOMAIN_FILES.map((f) => [f.label, f.src]))('%s 无 await fetch( 私设传输', (_label, src) => {
    expect(PRIVATE_TRANSPORT.test(src as string)).toBe(false);
  });

  it.each(DOMAIN_FILES.map((f) => [f.label, f.src]))('%s 无 data ?? 手写解包', (_label, src) => {
    expect(HAND_UNPACK.test(src as string)).toBe(false);
  });

  it.each(DOMAIN_FILES.map((f) => [f.label, f.src]))('%s 无私拼 auth 头', (_label, src) => {
    expect(PRIVATE_AUTH_HEADER.test(src as string)).toBe(false);
  });

  it('收口形状不误报：委托 _base 四动词的源形三条判据全不命中', () => {
    const CLEAN = "import { get, post, postEmpty, put } from './_base';\nconst rows = await get<Rec[]>(url, { cache: 'no-store' });\nreturn rows ?? [];\n";
    expect(PRIVATE_TRANSPORT.test(CLEAN)).toBe(false);
    expect(HAND_UNPACK.test(CLEAN)).toBe(false);
    expect(PRIVATE_AUTH_HEADER.test(CLEAN)).toBe(false);
  });
});

describe('采集触发两腿 — body-less POST 形状不得被补 body', () => {
  it('triggerMetadataCollect: POST collect-async/{id} 无 body，成功透出 taskId', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, success: true, data: { taskId: 't1', message: '已排队' } } }));
    const r = await triggerMetadataCollect('ds 1');
    expect(r).toEqual({ taskId: 't1', message: '已排队' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/metadata/collect-async/ds%201');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });

  it('triggerMetadataCollect: 非 2xx → null（错误腿仍被域内吞，文案不入 UI）', async () => {
    muteWarn();
    stubFetch(makeRes({ status: 500, body: { code: 500, message: '炸了' } }));
    expect(await triggerMetadataCollect('ds1')).toBeNull();
  });

  it('triggerCollectSync: POST collect-sync/{id} 无 body', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { taskId: 't9', mode: 'sync' } } }));
    expect(await triggerCollectSync('ds1')).toEqual({ taskId: 't9', mode: 'sync' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/metadata/collect-sync/ds1');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });
});

describe('采集状态与任务列表 — 多层 data 形状在单源下的取法', () => {
  it('fetchCollectStatus: 后端 result 为 JSON 字符串时展平（既有口径不变）', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { status: 'SUCCEEDED', progress: 100, result: '{"tablesTotal":3,"tablesOk":3,"elapsedMs":12}' } } }));
    const r = await fetchCollectStatus('t1');
    expect(r).toMatchObject({ status: 'SUCCEEDED', totalTables: 3, tablesOk: 3, elapsedMs: 12 });
  });

  it('fetchActiveCollectTasks: 双层 {data:{data:[…]}} 与单层 {data:[…]} 都能取到，并按 datasourceId 过滤', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { data: [
      { taskId: 't1', status: 'RUNNING', parameters: { datasourceId: 'ds1' } },
      { id: 't2', status: 'PENDING', parameters: { datasource_id: 'other' } },
    ] } } }));
    const r = await fetchActiveCollectTasks('ds1');
    expect(r).toEqual([{ taskId: 't1', status: 'RUNNING', progress: 0, startTime: undefined }]);

    muteWarn();
    stubFetch(makeRes({ body: { code: 0, data: [{ taskId: 't3', status: 'RUNNING', parameters: { datasourceId: 'ds2' } }] } }));
    expect(await fetchActiveCollectTasks('ds2')).toHaveLength(1);
  });

  it('fetchActiveCollectTasks: 非 2xx → [] 且不抛（改前 return []，合流后由 catch 取得同一形状）', async () => {
    muteWarn();
    stubFetch(makeRes({ status: 503, body: { code: 503, message: 'unavailable' } }));
    expect(await fetchActiveCollectTasks('ds1')).toEqual([]);
  });

  it('fetchCollectDiff: GET no-store，取 diffs 数组', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { diffs: [{ collectedAt: 'c1', tablesTotal: 5 }] } } }));
    const r = await fetchCollectDiff('ds1', 3);
    expect(r).toEqual([{ collectedAt: 'c1', taskId: undefined, diffSummary: undefined, diffMarkdown: undefined, gitCommit: undefined, tablesTotal: 5 }]);
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/metadata/collect-diff/ds1?limit=3');
    expect(init.cache).toBe('no-store');
    expect(init.method).toBeUndefined();
  });
});

describe('Git 版本档案 — 用户可见错误文案必须经单源还原', () => {
  it('fetchVersionHistory: 正常信封透出 versions/current', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { versions: [{ versionId: 'v1', tableCount: 2 }], current: { exists: true, collectedAt: 'c0' } } } }));
    const r = await fetchVersionHistory('ds1');
    expect(r).toMatchObject({ versions: [{ versionId: 'v1', tableCount: 2 }] });
    expect(lastCall(spy).init.cache).toBe('no-store');
  });

  it('差异腿：伪失败 code≠0 由「域内 return null」转「单源抛错→catch→null」，形状不变', async () => {
    muteWarn();
    stubFetch(makeRes({ body: { code: 500, message: '后端炸了' } }));
    expect(await fetchVersionHistory('ds1')).toBeNull();
  });

  it('差异腿：后端未回 code 字段时，改前「code!==0 即 null」按失败处理，合流后由单源口径视为成功面', async () => {
    // 改前域内自判 `json?.code !== 0` ⇒ 连「后端没给 code」都算故障；
    // 合流后伪失败判定归单源（只在 code 存在且 ≠0/200 或 success:false 时抛错）⇒ 裸 payload 走成功面。
    stubFetch(makeRes({ body: { versions: [{ versionId: 'v2', tableCount: 1 }], current: { exists: false } } }));
    expect(await fetchVersionHistory('ds1')).toMatchObject({ versions: [{ versionId: 'v2' }] });
  });

  it('fetchVersionDiff: 非 2xx → {error:`HTTP ${status}`}（改前逐字形状，经 isErrorResponse 的 status 还原）', async () => {
    stubFetch(makeRes({ status: 500, raw: '{"message":"后端炸了"}' }));
    expect(await fetchVersionDiff('ds1', 'v1')).toEqual({ error: 'HTTP 500' });
  });

  it('fetchVersionDiff: 403 → {error:`HTTP 403`}（NoAccessError 带 status=403，还原口径同 HTTP 腿）', async () => {
    stubFetch(makeRes({ status: 403, raw: '{"message":"无权"}' }));
    expect(await fetchVersionDiff('ds1', 'v1')).toEqual({ error: 'HTTP 403' });
  });

  it('fetchVersionDiff: 伪失败取信封 message 原文（用户可见，非 HTTP n 占位）', async () => {
    stubFetch(makeRes({ body: { code: 404, message: '版本不存在' } }));
    expect(await fetchVersionDiff('ds1', 'v9')).toEqual({ error: '版本不存在' });
  });

  it('差异腿：伪失败无 message 时兜底文案由域内「version diff failed」转单源「Error ${code}」（携带码位，信息量不减）', async () => {
    stubFetch(makeRes({ body: { code: 500 } }));
    expect(await fetchVersionDiff('ds1', 'v9')).toEqual({ error: 'Error 500' });
  });

  it('fetchVersionDiff: 网络层失败仍 null（不当后端文案透出，与改前 catch TypeError 同形状）', async () => {
    // 消费者 HistoryVersionCompareModal.tsx:105 把 null 渲染成 i18n 通用失败态，
    // 而 {error} 渲染的是后端原文 ⇒ 网络层必须留在 null 腿，禁把内部文案推到 UI。
    stubNetworkDown();
    expect(await fetchVersionDiff('ds1', 'v1')).toBeNull();
  });

  it('fetchVersionDiff: 成功面 rows/时间戳形状不变，非数组 rows 回落 []', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { rows: [{ changeType: 'ADDED', tableName: 'T1' }], currentCollectedAt: 'c1', versionCollectedAt: 'c0', summary: 's' } } }));
    expect(await fetchVersionDiff('ds1', 'v1')).toEqual({
      rows: [{ changeType: 'ADDED', tableName: 'T1' }],
      currentCollectedAt: 'c1',
      versionCollectedAt: 'c0',
      summary: 's',
    });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/metadata/version-diff/ds1?version=v1');
    expect(init.cache).toBe('no-store');
  });
});

describe('资源/字段/策略 — 信封缺失与多层形状的收口口径', () => {
  it('fetchPreview: data.rows 存在时由首行推断列类型', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { rows: [{ a: 1, b: 'x', c: true }] } } }));
    const r = await fetchPreview('res1', 10);
    expect(r?.columns).toEqual([
      { name: 'a', type: 'NUMBER' },
      { name: 'b', type: 'STRING' },
      { name: 'c', type: 'BOOL' },
    ]);
    expect(r?.rows).toHaveLength(1);
    expect(lastCall(spy).url).toBe('/api/v1/datanet/metadata/preview/res1?limit=10');
  });

  it('fetchPreview: 顶层无 data 字段时单源回落整个信封（与改前 ?? 形状同结果）', async () => {
    stubFetch(makeRes({ body: { rows: [{ z: 2 }] } }));
    expect(await fetchPreview('res1')).toMatchObject({ columns: [{ name: 'z', type: 'NUMBER' }] });
  });

  it('fetchPreview: 非 2xx → null（throw new Error(`HTTP n`) 被域内 catch 吞，改前同形状）', async () => {
    muteWarn();
    stubFetch(makeRes({ status: 502, raw: 'bad gateway' }));
    expect(await fetchPreview('res1')).toBeNull();
  });

  it('fetchFields: 三层形状 {data:{data:[…]}} → {data:[…]} → 裸数组 都取到', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { data: [{ fieldId: 'f1' }] } } }));
    expect(await fetchFields('res1')).toEqual([{ fieldId: 'f1' }]);
    muteWarn();
    stubFetch(makeRes({ body: { code: 0, data: [{ fieldId: 'f2' }] } }));
    expect(await fetchFields('res1')).toEqual([{ fieldId: 'f2' }]);
    stubFetch(makeRes({ body: [{ fieldId: 'f3' }] }));
    expect(await fetchFields('res1')).toEqual([{ fieldId: 'f3' }]);
  });

  it('fetchFields: 非 2xx → []；伪失败也 → []（改前 fail-open 把故障信封当数据，改后由单源抛错）', async () => {
    muteWarn();
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    expect(await fetchFields('res1')).toEqual([]);
    stubFetch(makeRes({ body: { code: 500, message: '后端炸了' } }));
    expect(await fetchFields('res1')).toEqual([]);
  });

  it('saveMetadataStrategy: PUT 上送三键 body，成功面 true', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, success: true } }));
    expect(await saveMetadataStrategy('ds1', 'cron', 'select', '0 0 * * *')).toBe(true);
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/metadata/strategy/ds1');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual({ strategy: 'cron', countMethod: 'select', scheduleCron: '0 0 * * *' });
  });

  it('saveMetadataStrategy: 不传 scheduleCron 时 body 不含该键（条件键形状不变）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, success: true } }));
    await saveMetadataStrategy('ds1', 'manual', 'count');
    expect(JSON.parse(String(lastCall(spy).init.body))).toEqual({ strategy: 'manual', countMethod: 'count' });
  });

  it('差异腿：saveMetadataStrategy 的 true 判据由「读信封 success/code」转「单源未抛错即成功」', async () => {
    muteWarn();
    // 改前：{success:false} → false；{code≠0} → false。合流后由单源抛错→catch→false，形状一致
    stubFetch(makeRes({ body: { code: 0, success: false, message: 'no' } }));
    expect(await saveMetadataStrategy('ds1', 'cron', 'select')).toBe(false);
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    expect(await saveMetadataStrategy('ds1', 'cron', 'select')).toBe(false);
    // 改前：success 与 code 矛盾（success:true + code:500）时返回 true（读的是 success 字段）
    // 合流后：单源先判 code≠0 抛错 ⇒ false，即矛盾信封按失败处理（fail-closed）
    stubFetch(makeRes({ body: { code: 500, success: true } }));
    expect(await saveMetadataStrategy('ds1', 'cron', 'select')).toBe(false);
  });

  it('fetchDataSourceResources: 缓存破坏串 + no-store + 逐行映射形状', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: [{ resourceName: 'T1', recordCount: 10, columns: [{ name: 'c1', dataType: 'VARCHAR' }] }] } }));
    const r = await fetchDataSourceResources('ds 1');
    expect(r).toHaveLength(1);
    expect(r[0]).toMatchObject({ name: 'T1', rowCount: 10, columns: [{ name: 'c1', type: 'VARCHAR' }] });
    expect(lastCall(spy).url).toMatch(/^\/api\/v1\/datanet\/metadata\/resources\/ds%201\?_=\d+$/);
    expect(lastCall(spy).init.cache).toBe('no-store');
  });

  it('fetchDataSourceResources: 非数组 payload → []，非 2xx → []', async () => {
    muteWarn();
    stubFetch(makeRes({ body: { code: 0, data: { oops: true } } }));
    expect(await fetchDataSourceResources('ds1')).toEqual([]);
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    expect(await fetchDataSourceResources('ds1')).toEqual([]);
  });
});

describe('fetchSchemaPreview — 非 2xx 文案优先取后端 message/error，取不到才回落 HTTP n', () => {
  const payload = { type: 'mysql' as const, host: 'h', port: 3306, username: 'u', password: 'p', database: 'db' };

  it('POST preview-schema 上送 buildPreviewSchemaPayload 的结果，成功面透出 fields/tableNames', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { fields: [{ name: 'id' }], tableNames: ['T1'] } } }));
    const r = await fetchSchemaPreview(payload);
    expect(r).toEqual({ fields: [{ name: 'id' }], tableNames: ['T1'] });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/ecos/data/datasource/preview-schema');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toMatchObject({ type: 'MYSQL', host: 'h', port: 3306, username: 'u', database: 'db' });
  });

  it('非 2xx + JSON 信封带 message → 用户看到后端原文（不是整段 body 文本）', async () => {
    stubFetch(makeRes({ status: 400, raw: '{"code":400,"message":"连接被拒绝"}' }));
    expect(await fetchSchemaPreview(payload)).toEqual({ ok: false, error: '连接被拒绝' });
  });

  it('非 2xx + JSON 只有 error 字段 → 取 error', async () => {
    stubFetch(makeRes({ status: 400, raw: '{"error":"bad type"}' }));
    expect(await fetchSchemaPreview(payload)).toEqual({ ok: false, error: 'bad type' });
  });

  it('非 2xx + body 非 JSON → 回落 `HTTP ${status}`（改前 try/catch 后同形状）', async () => {
    stubFetch(makeRes({ status: 502, raw: 'bad gateway', brokenJson: true }));
    expect(await fetchSchemaPreview(payload)).toEqual({ ok: false, error: 'HTTP 502' });
  });

  it('差异腿：网络层失败由裸 TypeError 文案转单源 NetworkError 文案，仍呈 {ok:false,error}', async () => {
    // 改前：fetch 抛 TypeError('Failed to fetch') → catch 分支 error='Failed to fetch'
    // 改后：单源归一 NetworkError（并广播 ecos-network-down）→ error 为单源文案
    stubNetworkDown();
    const r = await fetchSchemaPreview(payload);
    expect(r).toMatchObject({ ok: false });
    expect((r as { error: string }).error).toMatch(/^Network error: fetch /);
  });

  it('伪失败（HTTP 200 + success:false）不再被当成功渲染', async () => {
    stubFetch(makeRes({ body: { code: 0, success: false, message: '预览失败' } }));
    // 改前：json.data ?? json ⇒ data 缺失回落整封 ⇒ fields/tableNames 均非数组 ⇒ 返回 {fields:[],tableNames:[]}（假成功）
    expect(await fetchSchemaPreview(payload)).toEqual({ ok: false, error: '预览失败' });
  });
});
