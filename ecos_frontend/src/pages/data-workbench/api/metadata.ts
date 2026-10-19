/**
 * Data Workbench — 数据目录 / 元数据采集 (采集/状态/diff/版本/预览/字段)
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { get, postEmpty, put, asArray, httpStatusText } from './_base';
import { normalizeRowCount } from './mappers';
import { API_BASE } from '../../../services/httpClient';
import type { TableInfo } from '../types';

/** 立即触发元数据采集 → POST /api/v1/datanet/metadata/collect-async/{id} (PMO-37) */
export async function triggerMetadataCollect(datasourceId: string): Promise<{ taskId?: string; message?: string } | null> {
  try {
    const result = await postEmpty<{ taskId?: string; message?: string } | null>(`${API_BASE}/v1/datanet/metadata/collect-async/${encodeURIComponent(datasourceId)}`);
    return result ?? null;
  } catch (e) {
    console.warn('[data-workbench] triggerMetadataCollect failed:', e);
    return null;
  }
}

/** 查询元数据采集任务状态 → GET /api/v1/datanet/metadata/collect-status/{taskId}
 *  后端 result 字段是 JSON 字符串（如 {"tablesTotal":0,"tablesOk":0}），
 *  这里解析后展平到返回类型里，方便 UI 显示采集结果。
 */
export async function fetchCollectStatus(taskId: string): Promise<{
  status?: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | string;
  progress?: number;
  message?: string;
  collectedTables?: number;
  totalTables?: number;
  tablesOk?: number;
  tablesFailed?: number;
  errorMessage?: string;
  elapsedMs?: number;
  /** 运行时 statusMessage (TaskStatus 持续刷新; 与 message 不同 — 后者是"抛出"的 msg) */
  statusMessage?: string;
  /** 运行时 processed vs total records (live progress) */
  processedRecords?: number;
  totalRecords?: number;
} | null> {
  try {
    const raw = await get<Record<string, unknown>>(`${API_BASE}/v1/datanet/metadata/collect-status/${encodeURIComponent(taskId)}`);
    if (!raw) return null;
    // 后端 result 是 JSON 字符串，解析后展平
    let result: Record<string, unknown> = {};
    const rawResult = raw.result;
    if (typeof rawResult === 'string') {
      try { result = JSON.parse(rawResult); } catch { result = {}; }
    } else if (rawResult && typeof rawResult === 'object') {
      result = rawResult as Record<string, unknown>;
    }
    return {
      status: raw.status as string,
      progress: (raw.progress as number) ?? 0,
      message: raw.message as string | undefined,
      // runtime TaskStatus 字段（同步/定时采集都会持续刷新）
      statusMessage: raw.statusMessage as string | undefined,
      processedRecords: raw.processedRecords as number | undefined,
      totalRecords: raw.totalRecords as number | undefined,
      errorMessage: raw.errorMessage as string | undefined,
      totalTables: (result.tablesTotal as number) ?? 0,
      collectedTables: (result.tablesOk as number) ?? 0,
      tablesOk: (result.tablesOk as number) ?? 0,
      tablesFailed: (result.tablesFailed as number) ?? 0,
      elapsedMs: (result.elapsedMs as number) ?? 0,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchCollectStatus failed:', e);
    return null;
  }
}

/** 立即触发采集（同步 UI 模式）— POST /api/v1/datanet/metadata/collect-sync/{id} */
export async function triggerCollectSync(datasourceId: string): Promise<{ taskId?: string; mode?: string } | null> {
  try {
    const result = await postEmpty<{ taskId?: string; mode?: string } | null>(`${API_BASE}/v1/datanet/metadata/collect-sync/${encodeURIComponent(datasourceId)}`);
    return result ?? null;
  } catch (e) {
    console.warn('[data-workbench] triggerCollectSync failed:', e);
    return null;
  }
}

/** 查询当前数据源的活跃采集任务 → GET /api/v1/task/list?taskType=METADATA_COLLECT */
interface TaskListItemRaw {
  taskId?: string;
  id?: string;
  status?: string;
  progress?: number;
  startedAt?: string;
  createdAt?: string;
  parameters?: { datasourceId?: string; datasource_id?: string } & Record<string, unknown>;
}
export async function fetchActiveCollectTasks(datasourceId: string): Promise<{ taskId: string; status: string; progress: number; startTime?: string }[]> {
  try {
    const raw = await get<unknown>(`${API_BASE}/v1/task/list?taskType=METADATA_COLLECT&offset=0&limit=20`);
    const list = asArray<TaskListItemRaw>(raw);
    return list
      .filter((t) => {
        const params = t?.parameters || {};
        const dsId = params.datasourceId || params.datasource_id || '';
        return dsId === datasourceId;
      })
      .map((t) => ({
        taskId: t.taskId || t.id || '',
        status: t.status || 'RUNNING',
        progress: t.progress ?? 0,
        startTime: t.startedAt || t.createdAt,
      }));
  } catch (e) {
    console.warn('[data-workbench] fetchActiveCollectTasks failed:', e);
    return [];
  }
}

/** 获取采集差异记录 → GET /api/v1/datanet/metadata/collect-diff/{id} */
interface CollectDiffItemRaw {
  collectedAt?: string;
  taskId?: string;
  diffSummary?: string;
  diffMarkdown?: string;
  gitCommit?: string;
  tablesTotal?: number;
  tablesOk?: number;
  tablesFailed?: number;
}
export async function fetchCollectDiff(datasourceId: string, limit = 5): Promise<{
  collectedAt: string;
  taskId?: string;
  diffSummary?: string;
  diffMarkdown?: string;
  gitCommit?: string;
  tablesTotal?: number;
}[]> {
  try {
    const raw = await get<{ diffs?: CollectDiffItemRaw[] }>(`${API_BASE}/v1/datanet/metadata/collect-diff/${encodeURIComponent(datasourceId)}?limit=${limit}`, { cache: 'no-store' });
    const rawDiffs = raw?.diffs;
    const list = Array.isArray(rawDiffs) ? rawDiffs : [];
    return list.map((d) => ({
      collectedAt: d.collectedAt,
      taskId: d.taskId,
      diffSummary: d.diffSummary,
      diffMarkdown: d.diffMarkdown,
      gitCommit: d.gitCommit,
      tablesTotal: d.tablesTotal,
    }));
  } catch (e) {
    console.warn('[data-workbench] fetchCollectDiff failed:', e);
    return [];
  }
}

// ── Git 版本档案 (history/) ────────────────────────────────

/** 历史版本条目（Git 元数据存档 history/ 目录） */
export interface MetadataVersion {
  versionId: string;      // yyyyMMdd_HHmmss
  collectedAt?: string;   // 快照采集时间
  tableCount?: number;    // 该版本表数量
}

/** 历史版本比较差异行（对应差异表格 7 列） */
export interface VersionDiffRow {
  changeType: 'ADDED' | 'DELETED' | 'MODIFIED';
  tableName: string;
  field: string;
  currentValue: string;
  previousValue: string;
  changeTime: string;
  commitMessage: string;
}

/** 版本比较结果 */
export interface VersionDiffResult {
  rows: VersionDiffRow[];
  currentCollectedAt: string;
  versionCollectedAt: string;
  summary?: string;
}

/** 获取数据源的历史版本列表 → GET /api/v1/datanet/metadata/version-history/{id} */
export async function fetchVersionHistory(datasourceId: string): Promise<{
  versions: MetadataVersion[];
  current: { exists: boolean; collectedAt?: string; tableCount?: number };
} | null> {
  try {
    const raw = await get<{ versions?: MetadataVersion[]; current?: { exists: boolean; collectedAt?: string; tableCount?: number } }>(
      `${API_BASE}/v1/datanet/metadata/version-history/${encodeURIComponent(datasourceId)}`,
      { cache: 'no-store' },
    );
    const rawVersions = raw?.versions;
    return {
      versions: Array.isArray(rawVersions) ? rawVersions : [],
      current: raw?.current || { exists: false },
    };
  } catch (e) {
    console.warn('[data-workbench] fetchVersionHistory failed:', e);
    return null;
  }
}

/** 历史版本与当前版本结构化比较 → GET /api/v1/datanet/metadata/version-diff/{id}?version={ts} */
export async function fetchVersionDiff(datasourceId: string, version: string): Promise<VersionDiffResult | { error: string } | null> {
  try {
    const raw = await get<Record<string, unknown>>(
      `${API_BASE}/v1/datanet/metadata/version-diff/${encodeURIComponent(datasourceId)}?version=${encodeURIComponent(version)}`,
      { cache: 'no-store' },
    );
    const d = raw ?? {};
    return {
      rows: Array.isArray(d.rows) ? d.rows as VersionDiffRow[] : [],
      currentCollectedAt: (d.currentCollectedAt as string) || '',
      versionCollectedAt: (d.versionCollectedAt as string) || '',
      summary: d.summary as string | undefined,
    };
  } catch (e) {
    // 返回的 error 文案会直接呈现给用户：非 2xx/伪失败取单源还原；网络层失败（httpStatusText
    // 返回 null）留在 null 腿，禁把「请求没到达服务端」的内部文案当后端文案渲染。
    const text = httpStatusText(e, 'version diff failed');
    if (text !== null) return { error: text };
    console.warn('[data-workbench] fetchVersionDiff failed:', e);
    return null;
  }
}

// ── 资源/字段/策略 ─────────────────────────────────────────

/** 表字段预览 → GET /api/v1/datanet/metadata/preview/{resourceId}?limit=n */
export async function fetchPreview(resourceId: string, limit = 50): Promise<{
  columns: { name: string; type: string; label?: string }[];
  rows: Record<string, unknown>[];
} | null> {
  try {
    const raw = await get<{ rows?: Record<string, unknown>[] }>(`${API_BASE}/v1/datanet/metadata/preview/${encodeURIComponent(resourceId)}?limit=${limit}`, { cache: 'no-store' });
    const rawRows = raw?.rows;
    const rows: Record<string, unknown>[] = Array.isArray(rawRows) ? rawRows : [];
    const inferType = (v: unknown): string =>
      typeof v === 'number' ? 'NUMBER' : typeof v === 'boolean' ? 'BOOL' : 'STRING';
    const columns = rows.length > 0
      ? Object.keys(rows[0]).map(name => ({ name, type: inferType(rows[0][name]) }))
      : [];
    return { columns, rows };
  } catch (e) {
    console.warn('[data-workbench] fetchPreview failed:', e);
    return null;
  }
}

/** 表字段元数据 → GET /api/v1/datanet/metadata/fields/{resourceId} */
export interface DataFieldMeta {
  fieldId: string;
  fieldName: string;
  fieldAlias?: string;
  dataType: string;
  dataLength?: number | null;
  dataPrecision?: number | null;
  nullable?: boolean;
  primaryKey?: boolean;
  defaultValue?: string | null;
  description?: string | null;
  ordinalPosition?: number | null;
  resourceId?: string;
}
export async function fetchFields(resourceId: string): Promise<DataFieldMeta[]> {
  try {
    const raw = await get<unknown>(`${API_BASE}/v1/datanet/metadata/fields/${encodeURIComponent(resourceId)}`, { cache: 'no-store' });
    return asArray<DataFieldMeta>(raw);
  } catch (e) {
    console.warn('[data-workbench] fetchFields failed:', e);
    return [];
  }
}

/** 保存元数据策略配置 → PUT /api/v1/datanet/metadata/strategy/{id} (P0-3) */
export async function saveMetadataStrategy(datasourceId: string, strategy: string, countMethod: string, scheduleCron?: string): Promise<boolean> {
  try {
    const body: Record<string, unknown> = { strategy, countMethod };
    if (scheduleCron) body.scheduleCron = scheduleCron;
    await put(`${API_BASE}/v1/datanet/metadata/strategy/${encodeURIComponent(datasourceId)}`, body);
    return true;
  } catch (e) {
    console.warn('[data-workbench] saveMetadataStrategy failed:', e);
    return false;
  }
}

/** 获取数据源的物理表/目录列表 → GET /api/v1/datanet/metadata/resources/{datasourceId} */
export async function fetchDataSourceResources(datasourceId: string): Promise<TableInfo[]> {
  try {
    const url = `${API_BASE}/v1/datanet/metadata/resources/${encodeURIComponent(datasourceId)}?_=${Date.now()}`;
    const raw = await get<unknown>(url, { cache: 'no-store' });
    return asArray<Record<string, unknown>>(raw).map((r: Record<string, unknown>) => ({
      name: (r.resourceName as string) || (r.tableName as string) || (r.name as string) || '',
      rowCount: normalizeRowCount(r.recordCount, r.rowCount, r.rows),
      columns: Array.isArray(r.columns)
        ? (r.columns as Record<string, unknown>[]).map(c => ({
            name: (c.name as string) || (c.columnName as string) || '',
            type: (c.type as string) || (c.dataType as string) || '',
          }))
        : [],
      resourceId: (r.resourceId as string) || undefined,
      sourcePath: (r.sourcePath as string) || undefined,
      description: (r.description as string) || undefined,
      fieldCount: (r.fieldCount as number | undefined) ?? undefined,
    }));
  } catch (e) {
    console.warn('[data-workbench] fetchDataSourceResources failed:', e);
    return [];
  }
}
