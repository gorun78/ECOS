/**
 * Data Workbench — 数据源 + 元数据采集域（W66 拆分）。
 * 数据源 CRUD / 元数据采集·版本·预览·字段 / 资源列表 / 版本归档。
 * @license Apache-2.0
 */
import type { DataConnection, TableInfo } from './types';
import { get, post, put, del, authHeaders } from './httpClient';
import { toApiType } from './apiCommon';

// ─── API 端点常量 ──────────────────────────────────────────
const DATANET_DS = '/datanet/datasource';           // DataSourceController

/** metadataConfig 原始 JSONB 承载体（后端字段形态不定，消费端按键窄化） */
type MetadataConfigMap = Record<string, unknown>;
/** DataConnection.strategy 的窄化形态（trigger/countMethod 为受控字面量联合） */
type MetadataSyncStrategy = NonNullable<DataConnection['strategy']>;

/** DataSourceEntity → DataConnection */
function mapDsToConn(e: Record<string, unknown>): DataConnection {
  const configRaw = (e.connectionConfig as string) || '{}';
  let cfg: Record<string, unknown> = {};
  try { cfg = JSON.parse(configRaw); } catch { /* keep empty */ }

  const host = extractHost(cfg);

  return {
    id: (e.datasourceId as string) || '',
    name: (e.datasourceName as string) || '',
    type: mapDsType((e.datasourceType as string) || ''),
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
function extractHost(cfg: Record<string, unknown>): string {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  if (!jdbcUrl) return (cfg.host as string) || '';
  // jdbc:mysql://172.18.0.44:13306/db → 172.18.0.44
  const m = jdbcUrl.match(/\/\/([^:/]+)/);
  return m ? m[1] : jdbcUrl;
}

/** 从 jdbcUrl 提取数据库名 */
function extractDb(cfg: Record<string, unknown>): string | undefined {
  const jdbcUrl = (cfg.jdbcUrl as string) || (cfg.url as string) || '';
  // jdbc:postgresql://localhost:5432/sys_man → sys_man
  const m = jdbcUrl.match(/\/[^/]+\/([^?]+)/);
  if (m) return m[1];
  // jdbc:mysql://host:port/db?params → db
  const m2 = jdbcUrl.match(/\/([^/?]+)(\?|$)/);
  return m2 ? m2[1] : undefined;
}

function mapDsType(t: string): DataConnection['type'] {
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
  if (lower.includes('sftp')) return 'sftp';
  if (lower.includes('sap')) return 'sap';
  if (lower.includes('rest') || lower.includes('http') || lower.includes('api')) return 'rest_api';
  if (lower.includes('kafka')) return 'kafka';
  if (lower.includes('mongo')) return 'mongodb';
  if (lower.includes('fs') || lower.includes('file')) return 'fs';
  return 'postgresql';
}

function mapDsStatus(s: string): DataConnection['status'] {
  const lower = s.toLowerCase();
  if (lower === 'active' || lower === 'online') return 'connected';
  if (lower === 'inactive' || lower === 'offline') return 'disconnected';
  if (lower === 'error' || lower === 'failed') return 'error';
  if (lower === 'testing') return 'testing';
  return 'disconnected';
}

/** 数据源连接列表 */
export async function fetchDataConnections(): Promise<DataConnection[]> {
  try {
    const data = await get<unknown[]>(DATANET_DS);
    if (!Array.isArray(data)) return [];
    return data.map(mapDsToConn);
  } catch (e) {
    console.warn('[data-workbench] fetchDataConnections failed:', e);
    return [];
  }
}

/** ConnectionConfig builder — 前端表单 → connectionConfig JSON string
 *  PMO-48-T5: 扩展 oracle/mssql/dm/kingbase/gaussdb/minio/fs 分支 */
function buildConnectionConfig(c: {
  type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  password?: string;
  extra?: Record<string, string | number | boolean>;
}): string {
  const extra = c.extra ?? {};
  const cfg: Record<string, unknown> = {};
  if (c.username) cfg.username = c.username;
  if (c.password) cfg.password = c.password;

  // ── JDBC 类 (postgresql/mysql/doris/clickhouse/oracle/mssql/dm/kingbase/gaussdb) ──
  const jdbcTypes = ['postgresql', 'mysql', 'doris', 'clickhouse', 'oracle', 'mssql', 'dm', 'kingbase', 'gaussdb'];
  if (jdbcTypes.includes(c.type)) {
    // 各数据库驱动映射
    const driverMap: Record<string, string> = {
      postgresql: 'postgresql', mysql: 'mysql', doris: 'mysql',
      clickhouse: 'clickhouse',
      oracle: 'oracle', mssql: 'sqlserver', dm: 'dm',
      kingbase: 'postgresql', gaussdb: 'postgresql',
    };
    const driver = driverMap[c.type] || 'postgresql';
    const db = c.database || c.schema || '';
    const defaultPortMap: Record<string, number> = {
      postgresql: 5432, mysql: 3306, doris: 9030, clickhouse: 8123,
      oracle: 1521, mssql: 1433, dm: 5236, kingbase: 54321, gaussdb: 5432,
    };
    const portNum = c.port || defaultPortMap[c.type] || 5432;

    if (c.type === 'doris') {
      // Doris: FE 地址 + HTTP 端口 (MySQL 协议端口)
      const feHost = (extra.feHost as string) || c.host || 'localhost';
      const feHttpPort = Number(extra.feHttpPort) || 8030;
      cfg.jdbcUrl = `jdbc:mysql://${feHost}:${c.port || 9030}/${db}`;
      cfg.feHost = feHost;
      cfg.feHttpPort = feHttpPort;
      if (extra.warehouse) cfg.warehouse = String(extra.warehouse);
    } else if (c.type === 'oracle') {
      const dbType = (extra.dbType as string) || 'SERVICE_NAME';
      if (dbType === 'EZCONNECT') {
        cfg.jdbcUrl = `jdbc:oracle:thin:@//${c.host || 'localhost'}:${portNum}/${db}`;
      } else if (dbType === 'SID') {
        cfg.jdbcUrl = `jdbc:oracle:thin:@${c.host || 'localhost'}:${portNum}:${db}`;
      } else {
        // SERVICE_NAME (默认)
        cfg.jdbcUrl = `jdbc:oracle:thin:@${c.host || 'localhost'}:${portNum}/${db}`;
      }
      cfg.dbType = dbType;
    } else if (c.type === 'mssql') {
      cfg.jdbcUrl = `jdbc:sqlserver://${c.host || 'localhost'}:${portNum};databaseName=${db};encrypt=false;trustServerCertificate=true`;
    } else if (c.type === 'dm') {
      cfg.jdbcUrl = `jdbc:dm://${c.host || 'localhost'}:${portNum}${db ? '/' + db : ''}`;
    } else if (c.type === 'oracle') {
      cfg.jdbcUrl = cfg.jdbcUrl || `jdbc:oracle:thin:@${c.host}:${portNum}/${db}`;
    } else {
      cfg.jdbcUrl = `jdbc:${driver}://${c.host || 'localhost'}:${portNum}/${db}`;
    }
    if (c.schema && c.type !== 'doris') cfg.schema = c.schema;
    if (extra.ssl === true) cfg.ssl = true;
  } else if (c.type === 's3') {
    cfg.endpointUrl = (extra.endpointUrl as string) || c.endpointUrl || c.host;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    cfg.accessKey = (extra.accessKey as string) || '';
    cfg.secretKey = (extra.secretKey as string) || '';
    cfg.region = (extra.region as string) || '';
  } else if (c.type === 'oss') {
    cfg.endpointUrl = c.endpointUrl || (extra.endpointUrl as string) || c.host;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    cfg.accessKey = (extra.accessKey as string) || '';
    cfg.secretKey = (extra.secretKey as string) || '';
    cfg.region = (extra.region as string) || '';
  } else if (c.type === 'minio') {
    // PMO-48-T5: MinIO 三阶梯 (listenPort + roleArn 判定 STS)
    cfg.endpointUrl = (extra.endpointUrl as string) || c.endpointUrl || `http://${c.host || 'localhost'}:${c.port || 9000}`;
    cfg.bucket = c.bucket || (extra.bucket as string) || '';
    const listenPort = Number(extra.listenPort) || c.port || 9000;
    cfg.listenPort = listenPort;
    const roleArn = (extra.roleArn as string) || '';
    if (roleArn.startsWith('arn:aws:iam')) {
      // STS 模式
      cfg.roleArn = roleArn;
    } else {
      // AccessKey 模式
      cfg.accessKey = (extra.accessKey as string) || '';
      cfg.secretKey = (extra.secretKey as string) || '';
    }
    if (extra.pathPrefix) cfg.pathPrefix = String(extra.pathPrefix);
  } else if (c.type === 'csv') {
    cfg.filePath = (extra.filePath as string) || '';
    cfg.delimiter = (extra.delimiter as string) || ',';
    cfg.encoding = (extra.encoding as string) || 'UTF-8';
    cfg.hasHeader = extra.hasHeader === true;
    cfg.rowLimit = (extra.rowLimit as number) || 0;
  } else if (c.type === 'fs') {
    // PMO-48-T5: 本地文件系统
    cfg.rootPath = (extra.rootPath as string) || c.host || '';
    cfg.pattern = (extra.pattern as string) || '';
    cfg.recursive = extra.recursive === true;
  } else if (c.type === 'sftp' || c.type === 'sap') {
    cfg.host = c.host;
    cfg.port = c.port;
    if (extra.key) cfg.key = String(extra.key);
    if (extra.keyDecrypt) cfg.keyDecrypt = String(extra.keyDecrypt);
    if (extra.algo) cfg.algo = String(extra.algo);
    if (c.type === 'sap') {
      cfg.system = (extra.system as string) || '';
      cfg.client = (extra.client as string) || '';
      if (extra.appServer) cfg.appServer = String(extra.appServer);
      if (extra.instance) cfg.instance = String(extra.instance);
      if (extra.sysNum) cfg.sysNum = String(extra.sysNum);
      if (extra.funcModule) cfg.funcModule = String(extra.funcModule);
      if (extra.charset) cfg.charset = String(extra.charset);
    }
  } else if (c.type === 'rest_api') {
    cfg.endpointUrl = c.endpointUrl || c.host;
    if (extra) {
      if (extra.baseURLEnc) cfg.baseUrl = String(extra.baseURLEnc);
      if (extra.apiSubPath) cfg.apiSubPath = String(extra.apiSubPath);
      if (extra.apiMethod) cfg.apiMethod = String(extra.apiMethod);
      if (extra.authType) cfg.authType = String(extra.authType);
      if (extra.apiKey) cfg.apiKey = String(extra.apiKey);
      if (extra.clientCreds) cfg.clientCreds = String(extra.clientCreds);
      if (extra.credEnc) cfg.credEnc = String(extra.credEnc);
      if (extra.usernameEnc) cfg.usernameEnc = String(extra.usernameEnc);
      if (extra.passwordEnc) cfg.passwordEnc = String(extra.passwordEnc);
      if (extra.timeoutMs) cfg.timeoutMs = Number(extra.timeoutMs);
    }
  } else if (c.type === 'kafka') {
    cfg.host = (extra.bootstrapServers as string) || c.host;
    cfg.port = Number(extra.bootstrapPort) || 9092;
    cfg.topic = (extra.topic as string) || '';
    cfg.protocol = (extra.protocol as string) || 'PLAINTEXT';
    if (c.database) cfg.database = c.database;
  } else if (c.type === 'mongodb') {
    const db = c.database || '';
    cfg.jdbcUrl = `mongodb://${c.host || 'localhost'}:${c.port || 27017}/${db}${c.schema ? '/' + c.schema : ''}`;
    cfg.authSource = (extra.authSource as string) || 'admin';
    if (extra.username) {
      cfg.username = String(extra.username);
    }
    if (extra.password) {
      cfg.password = String(extra.password);
    }
  } else {
    // Fallback
    cfg.host = c.host;
    cfg.port = c.port;
    if (c.database) cfg.database = c.database;
    if (c.schema) cfg.schema = c.schema;
  }

  return JSON.stringify(cfg);
}

/** PMO-37: 策略 → metadataConfig JSON string (td_datasource.metadata_config JSONB) */
function toMetadataConfigJson(strategy: { trigger?: string; countMethod?: string; scheduleCron?: string }): string {
  const cfg: Record<string, unknown> = {
    trigger: strategy.trigger || 'MANUAL',
    countMethod: strategy.countMethod || 'OFF',
  };
  if (strategy.scheduleCron) cfg.scheduleCron = strategy.scheduleCron;
  return JSON.stringify(cfg);
}

/** 创建数据源 → DataSourceController POST /datanet/datasource */
export async function createDataSource(payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  description?: string; tags?: string; password?: string;
  extra?: Record<string, string | number | boolean>;
  strategy?: { trigger?: string; countMethod?: string; scheduleCron?: string };
}): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
      ...(payload.strategy ? { metadataConfig: toMetadataConfigJson(payload.strategy) } : {}),
    };
    const entity = await post<Record<string, unknown>>(DATANET_DS, dto);
    return mapDsToConn(entity);
  } catch (e) {
    console.warn('[data-workbench] createDataSource failed:', e);
    return null;
  }
}

/** 测试数据源连接 → POST /datanet/datasource/{id}/test */
export async function testDataSource(id: string): Promise<{ success: boolean; datasourceId: string } | null> {
  try {
    return await post<{ success: boolean; datasourceId: string }>(`${DATANET_DS}/${id}/test`, {});
  } catch (e) {
    console.warn('[data-workbench] testDataSource failed:', e);
    return null;
  }
}

/** 更新数据源 → PUT /datanet/datasource/{id} */
export async function updateDataSource(id: string, payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  description?: string; tags?: string; password?: string;
  extra?: Record<string, string | number | boolean>;
  strategy?: { trigger?: string; countMethod?: string; scheduleCron?: string };
}): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
      ...(payload.strategy ? { metadataConfig: toMetadataConfigJson(payload.strategy) } : {}),
    };
    const entity = await put<Record<string, unknown>>(`${DATANET_DS}/${id}`, dto);
    return mapDsToConn(entity);
  } catch (e) {
    console.warn('[data-workbench] updateDataSource failed:', e);
    return null;
  }
}

/** 删除数据源 → DELETE /datanet/datasource/{id} */
export async function deleteDataSource(id: string): Promise<boolean> {
  try {
    await del<void>(`${DATANET_DS}/${id}`);
    return true;
  } catch (e) {
    console.warn('[data-workbench] deleteDataSource failed:', e);
    return false;
  }
}

/** 立即触发元数据采集 → POST /api/v1/datanet/metadata/collect-async/{id} (PMO-37) */
export async function triggerMetadataCollect(datasourceId: string): Promise<{ taskId?: string; message?: string } | null> {
  try {
    const res = await fetch(`/api/v1/datanet/metadata/collect-async/${encodeURIComponent(datasourceId)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return (json.data ?? json) as { taskId?: string; message?: string } | null;
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
    const raw = await get<Record<string, unknown>>(`/api/v1/datanet/metadata/collect-status/${encodeURIComponent(taskId)}`);
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

/** 立即触发采集（同步 UI 模式）— POST /api/v1/datanet/metadata/collect-sync/{id}
 *  后端立即提交并后台执行（与本机异步行为一致），任务中心正常追踪；
 *  前端则应基于返回的 taskId 立即进入实时轮询 /collect-status 来渲染进度面板。
 *  与 collect-async 的差异：返回 mode:'sync-ui' 并鼓舞调用方在 success/failed 后关闭面板并 toast。
 */
export async function triggerCollectSync(datasourceId: string): Promise<{ taskId?: string; mode?: string } | null> {
  try {
    const res = await fetch(`/api/v1/datanet/metadata/collect-sync/${encodeURIComponent(datasourceId)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    return (json.data ?? json) as { taskId?: string; mode?: string } | null;
  } catch (e) {
    console.warn('[data-workbench] triggerCollectSync failed:', e);
    return null;
  }
}

/** 查询当前数据源的活跃采集任务 → GET /api/v1/task/list?taskType=METADATA_COLLECT
 *  过滤出 parameters 中 datasourceId 匹配的任务，用于"在任务中心查看"功能 */
/** 后端 TaskController.toTaskMap 返回 { taskId, taskName, taskType, category, description, priority, createTime, createdBy, parameters }；
 *  status/progress/startedAt 由 status 映射补齐，列表项未必带，前端用默认值兜底 */
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
    const res = await fetch(`/api/v1/task/list?taskType=METADATA_COLLECT&offset=0&limit=20`, {
      headers: { ...authHeaders() },
    });
    if (!res.ok) return [];
    const json = await res.json();
    const list = (json?.data?.data || json?.data || []) as TaskListItemRaw[];
    if (!Array.isArray(list)) return [];
    return list
      .filter((t) => {
        // 任务 parameters 中应包含 datasourceId
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

/** 获取采集差异记录 → GET /api/v1/datanet/metadata/collect-diff/{id}
 *  返回最近 N 次采集的 diff 摘要（含 gitCommit / diffSummary / diffMarkdown） */
/** 后端 MetadataController.collectDiff 返回 { code, message, data:{ diffs:[item] } }，
 *  item 键: collectedAt/taskId/diffSummary/diffMarkdown/gitCommit/tablesTotal/tablesOk/tablesFailed */
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
    const res = await fetch(`/api/v1/datanet/metadata/collect-diff/${encodeURIComponent(datasourceId)}?limit=${limit}`, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) return [];
    const json = await res.json();
    const list = (json?.data?.diffs || []) as CollectDiffItemRaw[];
    if (!Array.isArray(list)) return [];
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

/** 获取数据源的历史版本列表 → GET /api/v1/datanet/metadata/version-history/{id}
 *  失败返回 null（调用方据此展示错误提示而非空列表，避免与"无历史版本"混淆） */
export async function fetchVersionHistory(datasourceId: string): Promise<{
  versions: MetadataVersion[];
  current: { exists: boolean; collectedAt?: string; tableCount?: number };
} | null> {
  try {
    const res = await fetch(`/api/v1/datanet/metadata/version-history/${encodeURIComponent(datasourceId)}`, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) return null;
    const json = await res.json();
    if (json?.code !== 0) return null;
    return {
      versions: Array.isArray(json?.data?.versions) ? json.data.versions : [],
      current: json?.data?.current || { exists: false },
    };
  } catch (e) {
    console.warn('[data-workbench] fetchVersionHistory failed:', e);
    return null;
  }
}

/** 历史版本与当前版本结构化比较 → GET /api/v1/datanet/metadata/version-diff/{id}?version={ts}
 *  业务错误（版本不存在等）以 message 返回，网络错误返回 null */
export async function fetchVersionDiff(datasourceId: string, version: string): Promise<VersionDiffResult | { error: string } | null> {
  try {
    const res = await fetch(`/api/v1/datanet/metadata/version-diff/${encodeURIComponent(datasourceId)}?version=${encodeURIComponent(version)}`, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) return { error: `HTTP ${res.status}` };
    const json = await res.json();
    if (json?.code !== 0) {
      return { error: json?.message || 'version diff failed' };
    }
    const d = json?.data || {};
    return {
      rows: Array.isArray(d.rows) ? d.rows : [],
      currentCollectedAt: d.currentCollectedAt || '',
      versionCollectedAt: d.versionCollectedAt || '',
      summary: d.summary,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchVersionDiff failed:', e);
    return null;
  }
}

/** 表字段预览 → GET /api/v1/datanet/metadata/preview/{resourceId}?limit=n
 *  后端返回 {rows: List<Map<列名,值>>, columns: 列数量(int)}；
 *  此处从首行数据推导列定义（name + 按值类型推断 type），供表卡片懒加载展开显示全量列 */
export async function fetchPreview(resourceId: string, limit = 50): Promise<{
  columns: { name: string; type: string; label?: string }[];
  rows: Record<string, unknown>[];
} | null> {
  try {
    const res = await fetch(`/api/v1/datanet/metadata/preview/${encodeURIComponent(resourceId)}?limit=${limit}`, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    const data = json?.data ?? json;
    const rows: Record<string, unknown>[] = Array.isArray(data?.rows) ? data.rows : [];
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

/** 表字段元数据 → GET /api/v1/datanet/metadata/fields/{resourceId}
 *  从 td_data_field 查数据字段清单（name/type/length/nullable/pk），用于表详情抽屉列表 */
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
    const res = await fetch(`/api/v1/datanet/metadata/fields/${encodeURIComponent(resourceId)}`, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    const arr = json?.data?.data ?? json?.data ?? json;
    if (!Array.isArray(arr)) return [];
    return arr as DataFieldMeta[];
  } catch (e) {
    console.warn('[data-workbench] fetchFields failed:', e);
    return [];
  }
}

/** 保存元数据策略配置 → PUT /api/v1/datanet/metadata/strategy/{id} (P0-3) */
export async function saveMetadataStrategy(datasourceId: string, strategy: string, countMethod: string, scheduleCron?: string): Promise<boolean> {
  try {
    // 后端字段名: strategy (对应 MetadataStrategyConfig.strategy 独立键)
    const body: Record<string, unknown> = { strategy, countMethod };
    if (scheduleCron) body.scheduleCron = scheduleCron;
    const res = await fetch(`/api/v1/datanet/metadata/strategy/${encodeURIComponent(datasourceId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    // 后端返回 {code:0} 表示成功（success true 或 code 0 均可）
    return json?.success === true || json?.code === 0;
  } catch (e) {
    console.warn('[data-workbench] saveMetadataStrategy failed:', e);
    return false;
  }
}

/** 测试未保存的数据源连接 → POST /datanet/datasource/test (raw DTO, no id needed) */
export async function testDataSourceRaw(payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; password?: string; schema?: string;
  bucket?: string; endpointUrl?: string; role?: string;
  extra?: Record<string, string | number | boolean>;
}): Promise<{ success: boolean; message?: string } | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: toApiType(payload.type),
      connectionConfig: buildConnectionConfig(payload),
    };
    const result = await post<{ success: boolean; message?: string }>(`${DATANET_DS}/test`, dto);
    return result;
  } catch (e) {
    console.warn('[data-workbench] testDataSourceRaw failed:', e);
    return null;
  }
}

/** 获取数据源的物理表/目录列表 → GET /api/v1/datanet/metadata/resources/{datasourceId}
 *  禁缓存：Vite BFF 给 API 响应加 ETag，采集后重拉会拿到 304 空数组缓存，
 *  加 cache: 'no-store' + _=timestamp 强制后端重新查询。
 */
export async function fetchDataSourceResources(datasourceId: string): Promise<TableInfo[]> {
  try {
    // 禁缓存：加 _=Date.now() + cache:'no-store' 双保险
    const url = `/api/v1/datanet/metadata/resources/${encodeURIComponent(datasourceId)}?_=${Date.now()}`;
    const res = await fetch(url, {
      headers: { ...authHeaders() },
      cache: 'no-store',
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    const raw: unknown[] = (json?.data ?? json);
    if (!Array.isArray(raw)) return [];
    return raw.map((r: Record<string, unknown>) => ({
      name: (r.resourceName as string) || (r.tableName as string) || (r.name as string) || '',
      rowCount: normalizeRowCount(r.recordCount, r.rowCount, r.rows),
      columns: Array.isArray(r.columns)
        ? (r.columns as Record<string, unknown>[]).map(c => ({
            name: (c.name as string) || (c.columnName as string) || '',
            type: (c.type as string) || (c.dataType as string) || '',
          }))
        : [],
      // T3-2: 保留 resourceId 供表详情抽屉调用 preview/{resourceId}
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

// B8: 后端 recordCount 可能为 -1 (countMethod=OFF) / null / 0 / 正数。
// 归一: -1/null/NaN → null(UI 显示"未知"), 其它数值保留。
function normalizeRowCount(rc?: unknown, rowCount?: unknown, rows?: unknown): number | null {
  const v = rc != null ? rc : rowCount != null ? rowCount : rows;
  if (v == null || (typeof v === 'number' && Number.isNaN(v))) return null;
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  if (Number.isNaN(n) || n < 0) return null;
  return n;
}