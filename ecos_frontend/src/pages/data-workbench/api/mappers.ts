/**
 * Data Workbench — 字段映射适配器 (SourceEntity → 前端 VO)
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { CONNECTION_TYPES } from '../types';
import type { ConnType, DataConnection, DataSyncTask, DataPipeline, PipelineNode, DisplayConnType } from '../types';
import { toApiType } from './_base';
import type { DataSourceTypeCatalogEntry } from './datasource';

type MetadataConfigMap = Record<string, unknown>;
/** DataConnection.strategy 的窄化形态（trigger/countMethod 为受控字面量联合） */
type MetadataSyncStrategy = NonNullable<DataConnection['strategy']>;

/** DataSourceEntity → DataConnection */
export function mapDsToConn(e: Record<string, unknown>): DataConnection {
  const configRaw = (e.connectionConfig as string) || '{}';
  let cfg: Record<string, unknown> = {};
  try { cfg = JSON.parse(configRaw); } catch { /* keep empty */ }

  const host = extractHost(cfg);
  // #142/B4-3e-64（D-B4-93）：保留后端原文，供显示面解析不到时出「未知类型（原值 X）」
  const rawType = (e.datasourceType as string) || '';

  return {
    id: (e.datasourceId as string) || '',
    name: (e.datasourceName as string) || '',
    type: mapDsType(rawType),
    typeRaw: rawType,
    status: mapDsStatus((e.status as string) || 'disconnected'),
    config: {
      host,
      port: (cfg.port as number) || 0,
      database: (cfg.database as string) || (cfg.db as string) || extractDb(cfg),
      username: (cfg.username as string) || (cfg.user as string),
      schema: cfg.schema as string,
      bucket: cfg.bucket as string,
      endpointUrl: cfg.endpointUrl as string || cfg.endpoint as string,
      role: cfg.role as string,
      lastTested: e.lastTestTime as string,
    },
    lastTested: e.lastTestTime as string,
    description: (e.description as string) || '',
    category: (e.tags as string) || '',
    tablesAvailable: [],
    // PMO-37: 解析 metadataConfig (JSONB string or object)
    ...(() => {
      try {
        const raw = (e.metadataConfig as MetadataConfigMap) || (e.metadataConfig as string);
        let mc: MetadataConfigMap = {};
        if (typeof raw === 'string' && raw.length > 1) { try { mc = JSON.parse(raw); } catch { mc = {}; } }
        else if (raw && typeof raw === 'object') mc = raw;
        let strategy: MetadataSyncStrategy | undefined;
        if (mc && (mc.trigger || mc.countMethod)) {
          strategy = {
            trigger: (mc.trigger as MetadataSyncStrategy['trigger']) || 'MANUAL',
            countMethod: (mc.countMethod as MetadataSyncStrategy['countMethod']) || (mc.count_method as MetadataSyncStrategy['countMethod']) || 'OFF',
            scheduleCron: mc.scheduleCron as string,
          };
        }
        return { metadataConfig: mc, ...(strategy ? { strategy } : {}) };
      } catch { return {}; }
    })(),
  };
}

/** 从 jdbcUrl 提取主机名 */
export function extractHost(cfg: Record<string, unknown>): string {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  if (!jdbcUrl) return (cfg.host as string) || '';
  // jdbc:mysql://172.18.0.44:13306/db → 172.18.0.44
  const m = jdbcUrl.match(/\/\/([^:/]+)/);
  return m ? m[1] : jdbcUrl;
}

/** 从 jdbcUrl 提取数据库名 */
export function extractDb(cfg: Record<string, unknown>): string | undefined {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  // jdbc:postgresql://localhost:5432/sys_man → sys_man
  const m = jdbcUrl.match(/\/[^/]+\/([^?]+)/);
  if (m) return m[1];
  // jdbc:mysql://host:port/db?params → db
  const m2 = jdbcUrl.match(/\/([^/?]+)(\?|$)/);
  return m2 ? m2[1] : undefined;
}

/**
 * 后端类型名 → 前端显示类型（历史行标签通道）。
 *
 * <p>#142/B4-3e-64（差距 <b>D-B4-93</b>）：改前本函数在 17 条 `includes` 分支之后
 * <b>无条件 `return 'postgresql'`</b>，词表外名与空串（`datasourceType` 为 null 时上游传 `''`）
 * 一律显示成 PostgreSQL；`csv` 一枚更是没有任何分支，直接被折成「PostgreSQL」。
 * 现两处改动：①补 `csv` 显式腿；②末腿改 `return 'unknown'`——「解析不到」是一个可读结论，
 * 不是一枚具体类型。页面用 `DataConnection.typeRaw` 出「未知类型（原值 X）」。</p>
 *
 * <p>中间的 `includes` 关键词链保持原样：它承担历史库存量拼写（`GAUSSDB`/`Dameng`/
 * `Microsoft SQL Server` 等）的宽松匹配，且已被跨面对账门逐枚钉住
 * 「承流面规范名 ⇄ 显示分支 ⇄ 回环同名」三面一致。收紧这条链属显示面与词表的进一步合一，
 * 见台账余项。</p>
 */
export function mapDsType(t: string): DisplayConnType {
  const lower = t.toLowerCase();
  if (lower.includes('postgres')) return 'postgresql';
  if (lower.includes('mysql')) return 'mysql';
  if (lower.includes('doris')) return 'doris';
  if (lower.includes('clickhouse')) return 'clickhouse';
  if (lower.includes('oracle')) return 'oracle';
  if (lower.includes('sqlserver') || lower.includes('mssql')) return 'mssql';
  if (lower.includes('dm') || lower.includes('dameng')) return 'dm';
  if (lower.includes('kingbase')) return 'kingbase';
  if (lower.includes('gauss')) return 'gaussdb';
  if (lower.includes('minio')) return 'minio';
  if (lower.includes('s3') || lower.includes('oss')) return 's3';
  if (lower.includes('csv')) return 'csv';
  if (lower.includes('sftp')) return 'sftp';
  if (lower.includes('sap')) return 'sap';
  if (lower.includes('rest') || lower.includes('http') || lower.includes('api')) return 'rest_api';
  if (lower.includes('kafka')) return 'kafka';
  if (lower.includes('mongo')) return 'mongodb';
  if (lower.includes('fs') || lower.includes('file')) return 'fs';
  return 'unknown';
}

export function mapDsStatus(s: string): DataConnection['status'] {
  const lower = s.toLowerCase();
  if (lower === 'active' || lower === 'online') return 'connected';
  if (lower === 'inactive' || lower === 'offline') return 'disconnected';
  if (lower === 'error' || lower === 'failed') return 'error';
  if (lower === 'testing') return 'testing';
  return 'disconnected';
}

/** CeosCompatController syncTask → DataSyncTask */
export function mapSyncTask(t: Record<string, unknown>): DataSyncTask {
  return {
    id: (t.id as string) || (t.taskId as string) || '',
    name: (t.name as string) || (t.taskName as string) || '',
    sourceConnectionId: (t.sourceConnectionId as string) || '',
    sourceTable: (t.sourceTable as string),
    targetDatasetId: (t.targetDatasetId as string),
    status: mapSyncStatus((t.status as string) || 'paused'),
    schedule: (t.schedule as string) || (t.cronExpression as string),
    lastRunTime: (t.lastRun as string) || (t.lastRunTime as string),
    recordsSynced: (t.recordsSynced as number) || (t.rowsSynced as number) || 0,
    syncMode: (t.syncMode as DataSyncTask['syncMode']) || 'snapshot',
    taskType: (t.taskType as DataSyncTask['taskType']) || 'SYNC',
    durationMs: t.durationMs as number,
    description: (t.description as string) || '',
    errorMessage: t.errorMessage as string,
  };
}

export function mapSyncStatus(s: string): DataSyncTask['status'] {
  switch (s.toLowerCase()) {
    case 'success': case 'completed': return 'success';
    case 'running': case 'active': return 'running';
    case 'failed': case 'error': return 'failed';
    default: return 'paused';
  }
}

/** PipelineDefinition → DataPipeline */
export function mapPipelineDef(d: Record<string, unknown>): DataPipeline {
  return {
    id: (d.id as string) || '',
    name: (d.name as string) || '',
    status: mapPipelineStatus((d.status as string) || 'draft'),
    lastExecuted: (d.updatedAt as string),
    description: (d.description as string) || '',
    nodes: (d.nodes as DataPipeline['nodes']) || [],
    expressionsCount: 0,
  };
}

export function mapPipelineStatus(s: string): DataPipeline['status'] {
  switch (s.toLowerCase()) {
    case 'active': case 'published': return 'active';
    case 'running': return 'running';
    case 'success': return 'success';
    case 'error': case 'failed': return 'error';
    default: return 'draft';
  }
}

/** Read a node field tolerating backend field-name variants (first non-empty wins). */
export function readNodeField(n: Record<string, unknown>, keys: string[]): unknown {
  for (const k of keys) {
    const v = n[k];
    if (v !== undefined && v !== null && v !== '') return v;
  }
  return undefined;
}

// Wave 3 (lower): backend node payload is not canonicalized (id may be
// id/nodeId, type may be type/nodeType, position may be positionX/x) —
// normalize both for the canvas rendering.
export function normalizeBackendPipelineNode(raw: Record<string, unknown>): PipelineNode {
  const pos = Number(readNodeField(raw, ['positionX', 'x', 'left'])) || 0;
  const pos2 = Number(readNodeField(raw, ['positionY', 'y', 'top'])) || 0;
  return {
    id: String(readNodeField(raw, ['id', 'nodeId']) || ''),
    name: readNodeField(raw, ['name', 'label', 'nodeName']) as string | undefined,
    type: String(readNodeField(raw, ['type', 'nodeType']) || ''),
    config: (raw.config ?? {}) as PipelineNode['config'],
    left: String(pos),
    right: String(pos2),
    ...(() => {
      const dependsOn = raw.dependsOn;
      return Array.isArray(dependsOn) ? { inputs: dependsOn.map(String) } : {};
    })(),
  };
}

// B8: 后端 recordCount 可能为 -1 (countMethod=OFF) / null / 0 / 正数。
// 归一: -1/null/NaN → null(UI 显示"未知"), 其它数值保留。
export function normalizeRowCount(rc?: unknown, rowCount?: unknown, rows?: unknown): number | null {
  const v = rc != null ? rc : rowCount != null ? rowCount : rows;
  if (v == null || (typeof v === 'number' && Number.isNaN(v))) return null;
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  if (Number.isNaN(n) || n < 0) return null;
  return n;
}

// ────────────────────────────────────────────────────────────
// #142/B4-3e-64（D-B4-78 前端侧腿）：属主类型目录 → 前端下拉选项的唯一派生通道
//
// <p>派生方向是「后端目录 → 前端显示名」，反查表由前端既有两枚面拼出：
// `CONNECTION_TYPES`（显示名面）× `toApiType`（前端名 → 后端名的单源归一腿，
// 现只有 `fs → FILESYSTEM` 一条）。因此本文件<b>没有第三份类型字面量表</b>——
// 新增一枚后端类型时前端只需要补它的显示名，跨面对账门（gateway
// `DatasourceTypeVocabularySingleSourceTest`）会逐枚判定「属主名有无显示归属」。</p>
//
// <p>解析不到的一律进 `unresolvedCanonical` 并让页面显式提示，禁静默丢枚：
// 静默丢的后果正是本族缺陷「前端看不到而后端能登」。</p>
// ────────────────────────────────────────────────────────────

/** 显示名反查表：键 = `toApiType(前端名)` 的小写形态（属主名大小写不敏感比对） */
const DISPLAY_TYPE_BY_API_NAME: ReadonlyMap<string, ConnType> = new Map(
  CONNECTION_TYPES.map((o) => [toApiType(o.value).toLowerCase(), o.value]),
);

/** 一条属主目录项解析成的前端显示类型；解析不到 = `unknown`（禁回落具体类型，同 `mapDsType` 口径） */
export function resolveConnTypeFromApi(entry: DataSourceTypeCatalogEntry): DisplayConnType {
  for (const name of [entry.canonical, ...entry.aliases]) {
    if (typeof name !== 'string' || name.trim() === '') continue;
    const hit = DISPLAY_TYPE_BY_API_NAME.get(name.trim().toLowerCase());
    if (hit) return hit;
  }
  return 'unknown';
}

/** 下拉派生结果：`options` 按 `CONNECTION_TYPES` 声明序；`unresolvedCanonical` 须由页面显式提示 */
export interface SupportedOptionSet {
  options: (typeof CONNECTION_TYPES)[number][];
  unresolvedCanonical: string[];
}

/**
 * 用属主目录裁剪显示选项面。两枚独立入参（目录 + 选项面）为测试夹具而设，
 * 生产调用走默认值 = 真实 `CONNECTION_TYPES`。
 */
export function deriveSupportedOptions(
  entries: DataSourceTypeCatalogEntry[],
  optionFace: (typeof CONNECTION_TYPES)[number][] = CONNECTION_TYPES,
): SupportedOptionSet {
  const picked = new Set<DisplayConnType>();
  const unresolvedCanonical: string[] = [];
  for (const entry of entries) {
    const hit = resolveConnTypeFromApi(entry);
    if (hit === 'unknown') unresolvedCanonical.push(entry.canonical);
    else picked.add(hit);
  }
  const options = optionFace.filter((o) => picked.has(o.value));
  return { options, unresolvedCanonical };
}
