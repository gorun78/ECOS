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
 * <p>#142/B4-3e-47 起，预览腿的出参从 `{fields,tableNames} | {ok:false,error}` 改为
 * 按属主七键契约的判别联合，页面按 `kind` 出分态文案（§4.7.2「不可用 ≠ 无权限 ≠ 空结果」）；
 * 文案还原口径不变，仍是 `_base` 单点。</p>
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
// #142/B4-3e-47：预览腿的属主族根（面值同时由 `dataWorkbenchApiBaseSingleSource` 门钉住）
import { DATANET_DATASOURCE_BASE } from '../../../../api/dataClient';

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

describe('fetchSchemaPreview — 属主通道 + 七键契约 + §4.7.2 分态 (#142/B4-3e-47)', () => {
  const payload = { name: '订单库', type: 'mysql' as const, host: 'h', port: 3306, username: 'u', password: 'p', database: 'db' };
  /** 属主端点面值（族根取 `api/dataClient` 的导出常量，本门按面值钉住路径段） */
  const OWNER_URL = `${DATANET_DATASOURCE_BASE}/preview-schema`;

  it('POST 打属主端点，上送 DataSourceDTO 三枚属主键（扁平第二形态已退役）', async () => {
    const spy = stubFetch(makeRes({
      body: { code: 0, data: { type: 'MYSQL', tableNames: ['T1'], fields: [{ name: 'id', type: 'int', required: true, comment: null }], fieldsObject: 'T1' } },
    }));
    const r = await fetchSchemaPreview(payload);
    expect(r).toEqual({
      ok: true, type: 'MYSQL', tableNames: ['T1'],
      fields: [{ name: 'id', type: 'int', required: true, comment: null }],
      fieldsObject: 'T1', fieldsDegraded: false, fieldsReason: null, note: null,
    });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/datanet/datasource/preview-schema');
    expect(OWNER_URL).toBe(url);
    expect(init.method).toBe('POST');
    const body = JSON.parse(String(init.body)) as Record<string, unknown>;
    expect(Object.keys(body).sort()).toEqual(['connectionConfig', 'datasourceName', 'datasourceType']);
    expect(body.datasourceType).toBe('MYSQL');
    expect(body.datasourceName).toBe('订单库');
    // 连接配置只准 `buildConnectionConfig` 一种拼法：JDBC 腿的键面＝username/password/jdbcUrl
    expect(JSON.parse(String(body.connectionConfig))).toEqual({ username: 'u', password: 'p', jdbcUrl: 'jdbc:mysql://h:3306/db' });
  });

  it('名称缺省时走占位名，不送空串（后端把空名当入参不合法的那条腿不由前端制造）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { type: 'MYSQL', tableNames: [], fields: [] } } }));
    await fetchSchemaPreview({ ...payload, name: '  ' });
    expect(JSON.parse(String(lastCall(spy).init.body)).datasourceName).toBe('schema-preview');
  });

  it('列面的 null 属性逐列保留＝未知，禁折成空串/false 冒充「已知无」', async () => {
    // 两列各压一条腿：① 后端显式给 null；② 后端整键不给（缺键与给 null 同义＝未知）
    // 变异取证：把 `f.name ?? null` 折成 `?? ''`、`f.type` 折成 `''`、`req` 折成 `false` 都必须在此变红
    stubFetch(makeRes({
      body: {
        code: 0,
        data: {
          type: 'MYSQL', tableNames: ['T1'], fieldsObject: 'T1',
          fields: [{ name: 'c', type: null, required: null, comment: null }, {}],
        },
      },
    }));
    const r = await fetchSchemaPreview(payload);
    expect(r.ok).toBe(true);
    expect((r as { fields: unknown[] }).fields).toEqual([
      { name: 'c', type: null, required: null, comment: null },
      { name: null, type: null, required: null, comment: null },
    ]);
  });

  it('列面降级＝部分可用显式声明：对象面不透出为零，reason 原文上屏', async () => {
    stubFetch(makeRes({
      body: { code: 0, data: { type: 'REST_API', tableNames: ['r1'], fieldsObject: 'r1', fields: [], fieldsDegraded: true, fieldsReason: 'no declarative columns' } },
    }));
    const r = await fetchSchemaPreview(payload);
    expect(r).toMatchObject({ ok: true, tableNames: ['r1'], fields: [], fieldsDegraded: true, fieldsReason: 'no declarative columns' });
  });

  it('对象存储／本地文件占位面：note 与「对象面为空」分态，不写 fieldsDegraded', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { type: 'MINIO', tableNames: [], fields: [], note: 'enum pending' } } }));
    const r = await fetchSchemaPreview(payload);
    expect(r).toMatchObject({ ok: true, tableNames: [], fields: [], fieldsDegraded: false, note: 'enum pending' });
  });

  it('状态行分态：400/403/404/500/503 各归一类，未列举状态不猜语义落 unknown 且带原号', async () => {
    // 401 不在此列：单源对 401 先走 `handleAuthExpired`（清会话＋跳 #/login），
    // 在 node 环境里那条腿根本到不了错误分类，属会话面而非本腿契约面。
    const CASES: readonly [number, string][] = [
      [400, 'invalid_input'], [403, 'denied'], [404, 'not_found'],
      [500, 'server_error'], [502, 'unavailable'], [503, 'unavailable'], [504, 'unavailable'],
      [429, 'unknown'],
    ];
    for (const [status, kind] of CASES) {
      stubFetch(makeRes({ status, raw: `{"code":${status},"message":"m${status}"}` }));
      const r = await fetchSchemaPreview(payload);
      expect(r, `HTTP ${status}`).toMatchObject({ ok: false, kind, error: `m${status}`, status });
    }
  });

  it('伪失败（HTTP 200 + success:false）判 failed_envelope，绝不折成空面成功', async () => {
    stubFetch(makeRes({ body: { code: 0, success: false, message: 'preview rejected' } }));
    const r = await fetchSchemaPreview(payload);
    expect(r).toMatchObject({ ok: false, kind: 'failed_envelope', error: 'preview rejected' });
    expect(r).not.toMatchObject({ fields: [] });
  });

  it('差异腿：网络层失败判 network（后端无应答），与 failed_envelope 分态', async () => {
    stubNetworkDown();
    const r = await fetchSchemaPreview(payload);
    expect(r).toMatchObject({ ok: false, kind: 'network' });
    expect((r as { error: string }).error).toMatch(/^Network error: fetch /);
  });

  it('键面违约整单判 invalid_response（缺 tableNames／fields 非数组／列项非对象）', async () => {
    const BAD: readonly unknown[] = [
      { type: 'MYSQL', fields: [] },
      { type: 'MYSQL', tableNames: ['T1'], fields: 'oops' },
      { type: 'MYSQL', tableNames: ['T1'], fields: ['not-an-object'] },
      { type: 'MYSQL', tableNames: ['T1'], fields: [], fieldsDegraded: 'true' },
      null,
    ];
    for (const data of BAD) {
      stubFetch(makeRes({ body: { code: 0, data } }));
      const r = await fetchSchemaPreview(payload);
      expect(r, JSON.stringify(data)).toMatchObject({ ok: false, kind: 'invalid_response' });
    }
  });

  it('判据能变红：幻影路径与扁平第二形态已从域源码消失，且原形仍被同一判据命中', () => {
    const src = readFileSync(fileURLToPath(new URL('../datasource.ts', import.meta.url)), 'utf8');
    const PHANTOM = /\/v1\/ecos\/data\/datasource\/preview-schema/;
    const FLAT_BODY = /body\.feHttpPort\s*=|body\.bootstrapServers\s*=/;
    expect(PHANTOM.test(src), 'datasource.ts 仍在打幻影预览路径').toBe(false);
    expect(FLAT_BODY.test(src), 'datasource.ts 仍私设第二份扁平连接配置形态').toBe(false);
    const DEFECT = "post('/api/v1/ecos/data/datasource/preview-schema', body); body.feHttpPort = 1;";
    expect(PHANTOM.test(DEFECT)).toBe(true);
    expect(FLAT_BODY.test(DEFECT)).toBe(true);
  });
});
