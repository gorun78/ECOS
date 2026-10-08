/**
 * dataClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  DataAsset,
  DatasetHistoryRecord,
  DataSource,
  DataResource,
  DataField,
} from "../types";
import { MOCK_DATA_ASSETS } from "../mockData";
import { apiFetchData, doFetch } from "../services/httpClient";

// ── Datasets ────────────────────────────────────────────

/**
 * PMO-43 T1 — tag mock-fallback data with `error: 'MOCK'` so UI and tests can
 * distinguish provable real backend data from placeholder enrichment rows.
 * Each entry is shallow-copied (shallow is sufficient because the status items
 * in the raw mock are primitives/leaf arrays; a shallow copy is O(1) per asset
 * and avoids accidentally mutating the mock singletons).
 */
const MOCK_DATA_ASSETS_TAGGED: DataAsset[] = MOCK_DATA_ASSETS.map((a) => ({
  ...a,
  error: 'MOCK',
}));

export async function fetchDatasets(): Promise<DataAsset[]> {
  try {
    // Get first active datasource and its resources
    const dsResp = await doFetch("/api/v1/datanet/datasource");
    const sources = dsResp?.data ?? [];
    if (!Array.isArray(sources) || sources.length === 0) {
      console.warn("fetchDatasets: no datasources registered, using mock");
      return MOCK_DATA_ASSETS_TAGGED;
    }
    const active = sources.find((s: any) => s.status === "ACTIVE");
    if (!active) {
      console.warn("fetchDatasets: no active datasource, using mock");
      return MOCK_DATA_ASSETS_TAGGED;
    }
    // Try to get resources from this datasource
    const resResp = await doFetch(`/api/v1/datanet/metadata/resources/${active.datasourceId}`);
    const resources = resResp?.data ?? [];
    const items = Array.isArray(resources) ? resources : [];
    if (items.length === 0) {
      console.warn("fetchDatasets: no resources collected, using mock");
      return MOCK_DATA_ASSETS_TAGGED;
    }
    return items.map((r: any, i: number) => ({
      id: r.tableName || r.name || `ds_${i}`,
      name: r.tableName || r.name || `dataset_${i}`,
      description: r.comment || r.description || "",
      type: "table" as const,
      owner: active.createBy || "data-team",
      domain: r.schema || "public",
      tags: [] as string[],
      status: "Healthy" as const,
      qualityScore: 85 + (i % 15),
      rows: r.rowCount || 0,
      columns: r.columnCount || 0,
      storageSize: r.dataSize || "—",
      updatedAt: r.updatedAt || new Date().toISOString().slice(0, 10),
      schema: [] as { name: string; type: string; nullable?: boolean; primaryKey?: boolean; qualityScore?: number; description?: string }[],
      qualityRules: [] as any[],
      history: [] as DatasetHistoryRecord[],
      permissions: { owner: [active.createBy || "data-team"], editor: [] as string[], viewer: [] as string[] },
    })) as unknown as DataAsset[];
  } catch (e) {
    console.warn("fetchDatasets: backend unavailable, using mock", e);
    return MOCK_DATA_ASSETS_TAGGED;
  }
}

export async function fetchDataset(id: string): Promise<DataAsset | null> {
  try {
    // 使用批量端点一次拿到所有资源（含数据源名）
    const all = await fetchAllResources();
    const found = all.find((r: any) =>
      r.resourceId === id || r.resourceName === id || r.tableName === id
    );
    if (found) {
      // 获取字段详情
      let schema: any[] = [];
      try {
        const fieldsResp = await doFetch(`${DATANET_META}/fields/${found.resourceId || id}`);
        const fields: any[] = Array.isArray(fieldsResp?.data) ? fieldsResp.data :
                              Array.isArray(fieldsResp?.data?.data) ? fieldsResp.data.data : [];
        schema = fields.map((f: any) => ({
          name: f.fieldName || f.columnName || f.name || "?",
          type: f.dataType || f.fieldType || f.type || "VARCHAR",
          nullable: f.nullable !== undefined ? f.nullable : true,
          primaryKey: f.primaryKey || false,
          qualityScore: 100,
          description: f.comment || f.remarks || "",
        }));
      } catch (e) { /* schema optional */ }

      return {
        id: found.resourceId || id,
        name: found.resourceName || id,
        description: found.sourcePath || found.datasourceName || "",
        type: found.resourceType === "VIEW" ? "view" : "dataset",
        owner: found.datasourceName || "data-team",
        domain: found.sourcePath?.split(".")[0] || "default",
        tags: [found.resourceType || "TABLE", found.datasourceType || "JDBC"],
        status: "Healthy" as const,
        qualityScore: 85,
        rows: 0,
        columns: schema.length || found.fieldCount || 0,
        storageSize: "—",
        updatedAt: "—",
        schema,
        qualityRules: [] as any[],
        history: [] as DatasetHistoryRecord[],
        permissions: { owner: [found.datasourceName || "data-team"], editor: [] as string[], viewer: [] as string[] },
      };
    }
  } catch (e) {
    console.warn("fetchDataset: backend unavailable", e);
  }
  // Fallback: try mock data, then construct minimal asset
  const mockAsset = MOCK_DATA_ASSETS.find(a => a.id === id || a.name === id);
  if (mockAsset) return { ...mockAsset, error: 'MOCK' };
  // Construct minimal asset so the page isn't blank — tag it as a placeholder
  return {
    id,
    name: id,
    description: `Dataset: ${id}`,
    type: "dataset",
    owner: "data-team",
    domain: "default",
    tags: [],
    status: "Healthy" as const,
    qualityScore: 80,
    rows: 0,
    columns: 0,
    storageSize: "—",
    updatedAt: new Date().toISOString().slice(0, 10),
    schema: [],
    qualityRules: [],
    history: [],
    permissions: { owner: ["data-team"], editor: [] as string[], viewer: [] as string[] },
    error: 'MOCK',
  };
}


// ── Pipeline Builder ──────────────────────────────────────────
const PIPELINE_BASE = "/api/v1/pipeline/definitions";

export interface PipelineDefinition {
  id?: string;
  name: string;
  description?: string;
  nodes: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges: { from: string; to: string; label?: string }[];
  createdAt?: string;
  updatedAt?: string;
  status?: string;
}

export interface PipelineExecution {
  id: string;
  pipelineId: string;
  status: "pending" | "running" | "success" | "failed";
  startedAt?: string;
  finishedAt?: string;
  logs?: string[];
  error?: string;
}

/** GET /api/pipeline/definitions — 列表 */
export async function fetchPipelines(pageSize = 50): Promise<{ data: PipelineDefinition[]; total: number }> {
  return apiFetchData(`${PIPELINE_BASE}?pageSize=${pageSize}`);
}

/** GET /api/pipeline/definitions/{id} — 详情 */
export async function fetchPipeline(id: string): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`);
}

/** POST /api/pipeline/definitions — 创建 */
export async function createPipeline(body: {
  name: string;
  description?: string;
  nodes?: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges?: { from: string; to: string; label?: string }[];
}): Promise<PipelineDefinition> {
  return apiFetchData(PIPELINE_BASE, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** PUT /api/pipeline/definitions/{id} — 更新 */
export async function updatePipeline(id: string, body: Partial<PipelineDefinition>): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** DELETE /api/pipeline/definitions/{id} — 删除 */
export async function deletePipeline(id: string): Promise<void> {
  await apiFetchData(`${PIPELINE_BASE}/${id}`, { method: "DELETE" });
}

/** POST /api/pipeline/definitions/{id}/execute — 执行 */
export async function executePipeline(id: string): Promise<PipelineExecution> {
  return apiFetchData(`${PIPELINE_BASE}/${id}/execute`, { method: "POST" });
}

/** GET /api/pipeline/executions/{id} — 执行状态 */
export async function getExecution(executionId: string): Promise<PipelineExecution> {
  return apiFetchData(`/api/v1/pipeline/executions/${executionId}`);
}

// ── Data Quality Dashboard ────────────────────────────────────
const DQ_BASE = "/api/v1/dq";

export async function fetchDqRules(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/rules`);
  const arr = resp?.data?.data;  // ApiResponse<{data:[...],total}>
  if (Array.isArray(arr)) return arr;
  return resp?.data || resp || [];
}

export async function fetchDqIssues(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/issues`);
  const arr = resp?.data?.data;  // ApiResponse<{data:[...],total}>
  if (Array.isArray(arr)) return arr;
  return resp?.data || resp || [];
}

export async function fetchDqDashboard(): Promise<any> {
  const resp = await doFetch(`${DQ_BASE}/dashboard`);
  return resp?.data || resp || null;
}

export async function fetchDqAll(): Promise<[any, any, any]> {
  const [r, i, d] = await Promise.allSettled([fetchDqRules(), fetchDqIssues(), fetchDqDashboard()]);
  return [
    r.status === "fulfilled" ? r.value : [],
    i.status === "fulfilled" ? i.value : [],
    d.status === "fulfilled" ? d.value : null,
  ];
}

export async function createDqItem(type: string, body: any): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}`, { method: "POST", body: JSON.stringify(body) });
}

export async function updateDqItem(type: string, id: string, body: any): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export async function deleteDqItem(type: string, id: string): Promise<any> {
  return doFetch(`${DQ_BASE}/${type}/${id}`, { method: "DELETE" });
}

export async function runDqCheck(): Promise<any> {
  return doFetch(`${DQ_BASE}/check`, { method: "POST" });
}

export async function resolveDqIssue(issueId: string, body: any): Promise<any> {
  // Backend uses PUT /api/dq/issues/{id} with status in body
  return doFetch(`${DQ_BASE}/issues/${issueId}`, { method: "PUT", body: JSON.stringify(body) });
}


// ── Datanet: 物理表注册 & 元数据采集 ───────────────────────────
const DATANET_DS = "/api/v1/datanet/datasource";
const DATANET_META = "/api/v1/datanet/metadata";
const DATANET_CATALOG = "/api/v1/datanet/catalog";

/** GET /datanet/datasource — 获取所有数据源列表 */
export async function fetchDataSources(): Promise<DataSource[]> {
  const resp = await doFetch(DATANET_DS);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataSource[];
  return [];
}

/** POST /datanet/datasource — 创建数据源 */
export async function createDataSource(body: Record<string, any>): Promise<DataSource> {
  const resp = await doFetch(DATANET_DS, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as DataSource;
}

/** DELETE /datanet/datasource/{id} — 删除数据源 */
export async function deleteDataSource(id: string): Promise<void> {
  await doFetch(`${DATANET_DS}/${id}`, { method: "DELETE" });
}

/** POST /datanet/datasource/test — 前置连接测试（不保存） */
export async function testRawConnection(body: { datasourceType: string; connectionConfig: string }): Promise<{ success: boolean; message: string }> {
  const resp = await doFetch(`${DATANET_DS}/test`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as { success: boolean; message: string };
}

/** POST /datanet/datasource/{id}/test — 测试数据源连接 */
export async function testDataSourceConnection(id: string): Promise<{ success: boolean }> {
  const resp = await doFetch(`${DATANET_DS}/${id}/test`, { method: "POST" });
  return (resp?.data ?? resp) as { success: boolean };
}

/** POST /datanet/metadata/collect/{datasourceId} — 触发元数据采集 */
export async function collectMetadata(datasourceId: string): Promise<{ resourcesCollected: number; elapsedMs: number }> {
  const resp = await doFetch(`${DATANET_META}/collect/${datasourceId}`, { method: "POST" });
  return (resp?.data ?? resp) as { resourcesCollected: number; elapsedMs: number };
}

/** GET /datanet/metadata/resources/{datasourceId} — 获取数据源的资源（表/视图）列表 */
export async function fetchResources(datasourceId: string): Promise<DataResource[]> {
  const resp = await doFetch(`${DATANET_META}/resources/${datasourceId}`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataResource[];
  return [];
}

/** GET /datanet/metadata/resources — 批量获取所有资源（含数据源信息，一次请求替代N+1） */
export interface BulkResource {
  resourceId: string;
  resourceName: string;
  resourceType: string;
  sourcePath?: string;
  fieldCount?: number;
  datasourceId: string;
  datasourceName: string;
  datasourceType: string;
}
export async function fetchAllResources(): Promise<BulkResource[]> {
  const resp = await doFetch(`${DATANET_META}/resources/all`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as BulkResource[];
  return [];
}

/** GET /datanet/metadata/fields/{resourceId} — 获取资源的字段列表 */
export async function fetchFields(resourceId: string): Promise<DataField[]> {
  const resp = await doFetch(`${DATANET_META}/fields/${resourceId}`);
  const arr = resp?.data?.data ?? resp?.data;
  if (Array.isArray(arr)) return arr as DataField[];
  return [];
}

/** GET /datanet/metadata/preview/{resourceId} — 预览数据行 + 列元数据
 *  后端返回: { rows: Record<string,any>[], columns: {name,label,type}[], rowCount: number } */
export interface DataPreview {
  rows: Record<string, any>[];
  columns: { name: string; label?: string; type: string }[];
  rowCount: number;
}

export async function fetchPreview(resourceId: string, limit = 50): Promise<DataPreview> {
  const resp = await doFetch(`${DATANET_META}/preview/${resourceId}?limit=${limit}`);
  const d = resp?.data;
  if (!d) return { rows: [], columns: [], rowCount: 0 };
  return {
    rows: Array.isArray(d.rows) ? d.rows : [],
    columns: Array.isArray(d.columns)
      ? (d.columns as Record<string, unknown>[]).map(c => ({
          name: (c.name as string) || (c.columnName as string) || '',
          label: (c.label as string) || undefined,
          type: (c.type as string) || (c.dataType as string) || '',
        }))
      : [],
    rowCount: typeof d.rowCount === 'number' ? d.rowCount : 0,
  };
}


// ── Data Catalog (table list for RLS/CLS table reference) ────────────────────────

export interface CatalogTable {
  catalogId: string;
  resourceId: string;
  resourceName: string;
  resourceType: string;
  orgName?: string;
  description?: string;
}

/** GET /datanet/catalog/search — 从数据目录获取表/视图列表 */
export async function fetchCatalogTables(keyword?: string): Promise<CatalogTable[]> {
  try {
    const sp = new URLSearchParams();
    sp.set('page', '1');
    sp.set('pageSize', '200');
    if (keyword) sp.set('keyword', keyword);
    const data = await apiFetchData<any[]>(`/datanet/catalog/search?${sp.toString()}`);
    return Array.isArray(data) ? data.map(d => ({
      catalogId: d.catalogId || d.catalog_id || '',
      resourceId: d.resourceId || d.resource_id || '',
      resourceName: d.resourceName || d.resource_name || '',
      resourceType: d.resourceType || d.resource_type || 'TABLE',
      orgName: d.orgName || d.org_name,
      description: d.description,
    })) : [];
  } catch (e) {
    console.warn('fetchCatalogTables: backend unavailable', e);
    return [];
  }
}


// ── Data Masking Demo (P1-5) ─────────────────────────────

export interface DataMaskingDemoData {
  comparisons?: Array<{
    original?: string;
    raw?: string;
    masked?: string;
    type?: string;
    rule?: string;
  }>;
  [key: string]: any;
}

export interface DataMaskingApplyResult {
  original?: string;
  masked?: string;
  detections?: Array<{
    type?: string;
    rule?: string;
    count?: number;
  }>;
  [key: string]: any;
}

/** GET /api/v1/data-masking/demo — shows masked/unmasked comparison cards */
export async function fetchDataMaskingDemo(): Promise<DataMaskingDemoData> {
  try {
    return await apiFetchData<DataMaskingDemoData>("/v1/data-masking/demo");
  } catch (e) {
    console.warn("fetchDataMaskingDemo: backend unavailable", e);
    return { comparisons: [] };
  }
}

/** POST /api/v1/data-masking/apply — input textarea + rules selector → show result */
export async function applyDataMasking(body: {
  text: string;
  rules: string[];
}): Promise<DataMaskingApplyResult> {
  return apiFetchData<DataMaskingApplyResult>("/v1/data-masking/apply", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

