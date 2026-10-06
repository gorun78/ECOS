/**
 * E4.1 域拆分 · Datanet (物理表/元数据) + Monitoring Dashboard + Datasets
 * 原 api.ts L893-1103 (Datanet/Monitoring) + L69-201 (Datasets)
 */
import { doFetch, apiFetchData } from "./services/httpClient";
import type { DataSource, DataResource, DataField, DataAsset, DatasetHistoryRecord } from "./types";
import { MOCK_DATA_ASSETS } from "./mockData";

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

// ── Datasets ────────────────────────────────────────────
// 原 api.ts L69-201。fetchDatasets/fetchDataset 依赖上方的
// fetchAllResources 与 DATANET_META，故随 Datanet 域一并落位本文件。

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
    const dsResp = await doFetch(DATANET_DS);
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
    const resResp = await doFetch(`${DATANET_META}/resources/${active.datasourceId}`);
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

// ── Monitoring Dashboard ───────────────────────────────
// 对接 MonitorController (/api/monitor)
const MONITOR_BASE = "/api/v1/monitor";

export interface MonitoringKpi {
  label: string;
  value: string;
  desc: string;
  icon: string;
  color: string;
}

export interface MonitoringChartPoint {
  time: string;
  [key: string]: number | string;
}

export interface MonitoringProcess {
  name: string;
  type: string;
  status: string;
  uptime: string;
  items: string;
}

export interface MonitoringAlert {
  time: string;
  module: string;
  level: string;
  message: string;
}

export interface MonitoringDashboard {
  systemMetrics: MonitoringKpi[];
  chartData: MonitoringChartPoint[];
  processes: MonitoringProcess[];
  alerts: MonitoringAlert[];
}

export async function fetchMonitoringDashboard(): Promise<MonitoringDashboard> {
  try {
    const raw = await apiFetchData<any>(MONITOR_BASE);
    const sys = raw?.system || {};
    const recentAlerts: any[] = raw?.recent_alerts || [];

    // Map backend response → MonitoringDashboard
    const systemMetrics: MonitoringKpi[] = [
      { label: "CPU Cores", value: String(sys.cpu_cores || "N/A"), desc: "Available", icon: "Cpu", color: "text-blue-400" },
      { label: "CPU Load", value: String(sys.cpu_load || "N/A"), desc: "System load", icon: "Cpu", color: "text-cyan-400" },
      { label: "Heap Used", value: (sys.heap_used_mb || 0) + " MB", desc: "JVM heap", icon: "Server", color: "text-emerald-400" },
      { label: "Heap Max", value: (sys.heap_max_mb || 0) + " MB", desc: "Max heap", icon: "Server", color: "text-violet-400" },
      { label: "Active Alerts", value: String(raw.active_alerts ?? 0), desc: "Open alerts", icon: "AlertTriangle", color: "text-red-400" },
      { label: "DQ Issues", value: String(raw.open_dq_issues ?? 0), desc: "Open DQ issues", icon: "Database", color: "text-amber-400" },
    ];

    const alerts: MonitoringAlert[] = recentAlerts.map((a: any) => ({
      time: a.created_at || "",
      module: a.rule_name || "System",
      level: a.level || "INFO",
      message: a.message || "",
    }));

    // Map backend processes (or fallback mock)
    const backendProcesses: any[] = raw?.processes || [];
    const processes: MonitoringProcess[] = backendProcesses.length > 0
      ? backendProcesses.map((p: any) => ({
          name: p.name || "Unknown",
          type: p.type || "process",
          status: p.status || "UNKNOWN",
          uptime: p.uptime || "N/A",
          items: String(p.items ?? ""),
        }))
      : [
          { name: "ECOS Gateway", type: "Java/Spring", status: "RUNNING", uptime: "N/A", items: "Port 8080" },
          { name: "PostgreSQL", type: "Database", status: "RUNNING", uptime: "N/A", items: "Port 5432" },
        ];

    // Map backend chartData (or fallback empty)
    const chartData: MonitoringChartPoint[] = (raw?.chartData || []).map((c: any) => ({
      time: c.time || "",
      cpu: c.cpu,
      memory: c.memory,
    }));

    return {
      systemMetrics,
      chartData,
      processes,
      alerts,
    };
  } catch {
    return { systemMetrics: [], chartData: [], processes: [], alerts: [] };
  }
}

export async function runSystemDiagnostics(): Promise<{ database: { status: string }; uptime_ms: number; version: string; status: string }> {
  const health = await apiFetchData<any>(`${MONITOR_BASE}/health`);
  return {
    database: { status: health?.database?.status || "UNKNOWN" },
    uptime_ms: health?.uptime_ms || 0,
    version: health?.version || "N/A",
    status: health?.status || "DOWN",
  };
}
