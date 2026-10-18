// @vitest-environment node
/**
 * dwTransportSingleChannel.spec.ts — §13.2.4 C2「ApiResponse 单解包点」DW 域传输合流第一批
 * 收口契约（#143/B5-10）。
 *
 * <p>范围＝#143/B5-8 收口 `_base.ts` 之后的域内直接 `fetch` 载体中「错误腿全部被吞、
 * 无用户可见文案依赖」的三个文件：`api/pipeline.ts`（5 处）、`api/lineage.ts`（2 处）、
 * `pipelineDebugApi.ts`（1 处 `call` 包装器，实际覆盖 9 个调用点）＝8 处。
 * 错误文案对用户可见的 `api/metadata.ts`（10 处）与 `api/datasource.ts` fetchSchemaPreview
 * 留待 B5-10b 逐腿锁版。</p>
 *
 * <p>判据三条（只认代码面）：域内不得残留 ①私设传输 `await fetch(`、②手写解包 `data ??`、
 * ③私拼 auth 头符号 `authOnlyHeaders|authHeaders(`。配「缺陷原形能变红 + 收口形状不误报」双自检。</p>
 *
 * <p>兼容性红线：URL 拼装、body 形态（含 body-less POST）、返回形状逐字保持；
 * 差异腿（`data` 字段缺失时单源回落整个信封、`success:false` 由静默转抛错）在下方如实锁定，
 * 属登记的收口差异而非回归。</p>
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { fetchLineageImpact, rebuildLineage } from '../lineage';
import { getPipelineDefinition, createPipeline, deletePipeline, executePipeline } from '../pipeline';
import { startDebugSession, getDebugSession, deleteDebugSession } from '../../pipelineDebugApi';

const DOMAIN_FILES: { readonly label: string; readonly src: string }[] = [
  { label: 'api/pipeline.ts', src: readFileSync(fileURLToPath(new URL('../pipeline.ts', import.meta.url)), 'utf8') },
  { label: 'api/lineage.ts', src: readFileSync(fileURLToPath(new URL('../lineage.ts', import.meta.url)), 'utf8') },
  { label: 'pipelineDebugApi.ts', src: readFileSync(fileURLToPath(new URL('../../pipelineDebugApi.ts', import.meta.url)), 'utf8') },
];

const PRIVATE_TRANSPORT = /await fetch\(/;
const HAND_UNPACK = /data\s*\?\?/;
const PRIVATE_AUTH_HEADER = /authOnlyHeaders|authHeaders\(/;

/** 缺陷原形（用于证明判据能变红，非本域源码） */
const DEFECT = 'const h = { ...authHeaders() }; const res = await fetch(url, { headers: h }); const json = await res.json(); return json.data ?? json;';

interface ResSpec {
  status?: number;
  body?: unknown;
  raw?: string;
}

function makeRes(spec: ResSpec): Response {
  const status = spec.status ?? 200;
  const text = spec.raw ?? JSON.stringify(spec.body ?? {});
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (): string | null => null },
    text: () => Promise.resolve(text),
    json: () => Promise.resolve(JSON.parse(text)),
  } as unknown as Response;
}

function stubFetch(res: Response) {
  const spy = vi.fn(async (...args: [input: string, init?: RequestInit]) => {
    void args;
    return res;
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

/** 单通道最终落到 httpClient 的那一次 fetch 入参。 */
function lastCall(spy: ReturnType<typeof stubFetch>) {
  const call = spy.mock.calls[spy.mock.calls.length - 1];
  return { url: call[0], init: call[1] ?? {} };
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

  it.each(DOMAIN_FILES.map((f) => [f.label, f.src]))('%s 不再自拼 auth 头', (_label, src) => {
    expect(PRIVATE_AUTH_HEADER.test(src as string)).toBe(false);
  });
});

describe('lineage — 影响度与重建（GET 带 query / body-less POST）', () => {
  it('fetchLineageImpact：URL 与 query 逐字上送、无 method、单源解包后仍按结构映射', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { startNode: 't1', matched: true, depth: 2, severity: 'HIGH', downstream: [{ id: 'd1', hop: 1 }] } } }));
    const r = await fetchLineageImpact('t1', 2);
    expect(r).toMatchObject({ startNode: 't1', matched: true, depth: 2, severity: 'HIGH', downstream: [{ id: 'd1', hop: 1 }], upstream: [] });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/engine/data/lineage/impact?startNode=t1&depth=2');
    expect(init.method).toBeUndefined();
    expect(init.body).toBeUndefined();
  });

  it('fetchLineageImpact：HTTP 非 2xx 仍返回 null（吞错口径不变）', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    await expect(fetchLineageImpact('t1')).resolves.toBeNull();
  });

  it('rebuildLineage：POST 不上送 body（改前即无 body，合流后不得补 {}）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { total_nodes: 7, total_edges: 3, tasks_scanned: 5, tasks_parsed: 4, sql_failed: 1, definitions_scanned: 2 } } }));
    const r = await rebuildLineage(10);
    expect(r).toEqual({ total_nodes: 7, total_edges: 3, tasks_scanned: 5, tasks_parsed: 4, sql_failed: 1, definitions_scanned: 2 });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/engine/data/lineage/topology/rebuild?limit=10');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });

  it('rebuildLineage：伪失败 success:false 不再被当成功计数（fail-open 收口）', async () => {
    stubFetch(makeRes({ body: { success: false, message: '重建被拒绝' } }));
    await expect(rebuildLineage()).resolves.toEqual({ total_nodes: 0, total_edges: 0, tasks_scanned: 0, tasks_parsed: 0, sql_failed: 0 });
  });
});

describe('pipeline — 详情双形态容忍 / 创建 body 原文 / 删除布尔语义', () => {
  it('getPipelineDefinition：裸对象形态（无 data 字段）仍可渲染', async () => {
    stubFetch(makeRes({ body: { id: 'p1', name: 'n', nodes: [{ nodeId: 'a', type: 'SOURCE' }] } }));
    await expect(getPipelineDefinition('p1')).resolves.toMatchObject({ id: 'p1' });
  });

  it('getPipelineDefinition：嵌套信封两层 data 容忍保持', async () => {
    stubFetch(makeRes({ body: { code: 0, data: { data: { id: 'p2', name: 'm', nodes: [] } } } }));
    await expect(getPipelineDefinition('p2')).resolves.toMatchObject({ id: 'p2' });
  });

  it('createPipeline：URL/method/body 逐字上送', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { id: 'p3', name: 'n1' } } }));
    await expect(createPipeline({ name: 'n1', nodes: [], edges: [] })).resolves.toMatchObject({ id: 'p3' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/pipeline/definitions');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(JSON.stringify({ name: 'n1', description: '', nodes: [], edges: [] }));
  });

  it('executePipeline：body-less POST 且取 data 载荷', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { executionId: 'e1', status: 'RUNNING' } } }));
    await expect(executePipeline('p1')).resolves.toEqual({ executionId: 'e1', status: 'RUNNING' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/pipeline/definitions/p1/execute');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
  });

  it('deletePipeline：HTTP 非 2xx → false（与改前 res.ok 同结论）', async () => {
    stubFetch(makeRes({ status: 500, raw: 'boom' }));
    await expect(deletePipeline('p1')).resolves.toBe(false);
  });

  it('deletePipeline：差异腿如实锁定 —— 改前 HTTP 200 + success:false 返回 true（fail-open），改后 false', async () => {
    stubFetch(makeRes({ body: { success: false, message: '无权删除' } }));
    await expect(deletePipeline('p1')).resolves.toBe(false);
  });
});

describe('pipelineDebugApi — call 包装器合流后三动词形态不变', () => {
  it('POST 创建会话：body 为 payload 的 JSON 原文', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { id: 's1', status: 'READY' } } }));
    await expect(startDebugSession({ definitionId: 'd1' })).resolves.toMatchObject({ id: 's1' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/pipeline/debug/sessions');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(JSON.stringify({ definitionId: 'd1' }));
  });

  it('GET 查询会话：无 method、无 body、URL 编码逐字', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: { id: 's/1' } } }));
    await expect(getDebugSession('s/1')).resolves.toMatchObject({ id: 's/1' });
    const { url, init } = lastCall(spy);
    expect(url).toBe('/api/v1/pipeline/debug/sessions/s%2F1');
    expect(init.method).toBeUndefined();
    expect(init.body).toBeUndefined();
  });

  it('DELETE 删除会话：无 body 且返回 null（改前 204 走 json() 抛错亦为 null）', async () => {
    const spy = stubFetch(makeRes({ body: { code: 0, data: null } }));
    await expect(deleteDebugSession('s1')).resolves.toBeNull();
    const { init } = lastCall(spy);
    expect(init.method).toBe('DELETE');
    expect(init.body).toBeUndefined();
  });

  it('吞错口径保持：HTTP 非 2xx → null 而非上抛', async () => {
    stubFetch(makeRes({ status: 404, raw: 'not found' }));
    await expect(getDebugSession('missing')).resolves.toBeNull();
  });
});
