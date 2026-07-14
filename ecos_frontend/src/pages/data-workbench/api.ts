/**
 * Data Workbench — backend API layer
 * 对接 databridge-v2 真实后端，含字段映射适配。
 * @license Apache-2.0
 */
import type { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck } from './types';

// ─── API 端点常量 ──────────────────────────────────────────
const DATANET_DS = '/datanet/datasource';           // DataSourceController
const INTEGRATION  = '/api/integration/metadata';   // CeosCompatController (connections + syncTasks)
const PIPELINE_DEFS = '/api/v1/pipeline/definitions'; // PipelineController
const DQ_RULES      = '/api/v1/ecos/dq/rules';         // DqController (camelCase 字段)

async function get<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  // ApiResponse<T> 包裹: { code, message, data }
  return (json.data ?? json) as T;
}

// ─── 字段映射适配器 ────────────────────────────────────────

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
  if (lower.includes('s3') || lower.includes('oss')) return 's3';
  if (lower.includes('sftp')) return 'sftp';
  if (lower.includes('sap')) return 'sap';
  if (lower.includes('rest') || lower.includes('http') || lower.includes('api')) return 'rest_api';
  if (lower.includes('kafka')) return 'kafka';
  if (lower.includes('mongo')) return 'mongodb';
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

/** CeosCompatController syncTask → DataSyncTask */
function mapSyncTask(t: Record<string, unknown>): DataSyncTask {
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
    durationMs: t.durationMs as number,
    description: (t.description as string) || '',
    errorMessage: t.errorMessage as string,
  };
}

function mapSyncStatus(s: string): DataSyncTask['status'] {
  switch (s.toLowerCase()) {
    case 'success': case 'completed': return 'success';
    case 'running': case 'active': return 'running';
    case 'failed': case 'error': return 'failed';
    default: return 'paused';
  }
}

/** PipelineDefinition → DataPipeline */
function mapPipelineDef(d: Record<string, unknown>): DataPipeline {
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

function mapPipelineStatus(s: string): DataPipeline['status'] {
  switch (s.toLowerCase()) {
    case 'active': case 'published': return 'active';
    case 'running': return 'running';
    case 'success': return 'success';
    case 'error': case 'failed': return 'error';
    default: return 'draft';
  }
}

/** DQ Rule → DataHealthCheck */
function mapDqRule(r: Record<string, unknown>): DataHealthCheck {
  const ruleType = (r.ruleType as string) || (r.rule_type as string) || '';
  return {
    id: (r.id as string) || '',
    name: (r.name as string) || (r.code as string) || '',
    status: (r.enabled as boolean) ? 'ok' : 'pending',
    checkType: mapDqCheckType(ruleType),
    targetTable: (r.targetEntity as string) || (r.target_entity as string),
    datasetId: (r.targetEntity as string),
    threshold: (r.ruleExpression as string) || (r.rule_expression as string),
    lastChecked: (r.updatedAt as string) || (r.updated_at as string),
    message: (r.description as string) || '',
    config: {
      severity: r.severity as string,
      params: r.params,
      ruleExpression: r.ruleExpression || r.rule_expression,
    },
  };
}

function mapDqStatus(_s: string): DataHealthCheck['status'] {
  return 'ok'; // DqController uses enabled boolean, simplified
}

function mapDqCheckType(t: string): DataHealthCheck['checkType'] {
  const lower = t.toLowerCase();
  if (lower.includes('null')) return 'null_check';
  if (lower.includes('range')) return 'range_check';
  if (lower.includes('unique')) return 'uniqueness';
  if (lower.includes('fresh')) return 'freshness';
  if (lower.includes('row_count') || lower.includes('count')) return 'row_count';
  if (lower.includes('schema')) return 'schema_check';
  return 'custom_sql';
}

// ─── 公开 API 函数 ─────────────────────────────────────────

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

/** 同步任务列表 — 从 CeosCompatController 聚合接口获取 */
export async function fetchDataSyncTasks(): Promise<DataSyncTask[]> {
  try {
    const data = await get<{ syncTasks?: unknown[] }>(INTEGRATION);
    if (!data?.syncTasks || !Array.isArray(data.syncTasks)) return [];
    return data.syncTasks.map(mapSyncTask);
  } catch (e) {
    console.warn('[data-workbench] fetchDataSyncTasks failed:', e);
    return [];
  }
}

/** Pipeline 定义列表 */
export async function fetchDataPipelines(): Promise<DataPipeline[]> {
  try {
    const data = await get<unknown[]>(PIPELINE_DEFS);
    if (!Array.isArray(data)) return [];
    return data.map(mapPipelineDef);
  } catch (e) {
    console.warn('[data-workbench] fetchDataPipelines failed:', e);
    return [];
  }
}

/** Pipeline CRUD — 创建 */
export async function createPipeline(name: string, description?: string): Promise<DataPipeline | null> {
  try {
    const res = await fetch(PIPELINE_DEFS, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, description: description || '' }),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return mapPipelineDef(json.data ?? {});
  } catch (e) {
    console.warn('[data-workbench] createPipeline failed:', e);
    return null;
  }
}

/** Pipeline CRUD — 更新 */
export async function updatePipeline(id: string, data: { name?: string; description?: string; status?: string }): Promise<DataPipeline | null> {
  try {
    const res = await fetch(`${PIPELINE_DEFS}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return mapPipelineDef(json.data ?? {});
  } catch (e) {
    console.warn('[data-workbench] updatePipeline failed:', e);
    return null;
  }
}

/** Pipeline CRUD — 删除（软删除 → ARCHIVED） */
export async function deletePipeline(id: string): Promise<boolean> {
  try {
    const res = await fetch(`${PIPELINE_DEFS}/${id}`, { method: 'DELETE' });
    return res.ok;
  } catch (e) {
    console.warn('[data-workbench] deletePipeline failed:', e);
    return false;
  }
}

/** Pipeline CRUD — 执行 */
export async function executePipeline(id: string): Promise<{ executionId?: string; status?: string } | null> {
  try {
    const res = await fetch(`${PIPELINE_DEFS}/${id}/execute`, { method: 'POST' });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return json.data as Record<string, unknown> ?? null;
  } catch (e) {
    console.warn('[data-workbench] executePipeline failed:', e);
    return null;
  }
}

/** 数据质量健康检查 — 从 DQ 规则转换（双包装解包） */
export async function fetchDataHealthChecks(): Promise<DataHealthCheck[]> {
  try {
    // DqController 返回: {code:0, data:{data:[...], total:N}}
    const wrapper = await get<{ data?: unknown[]; total?: number }>(DQ_RULES);
    const data = wrapper?.data;
    if (!data || !Array.isArray(data)) return [];
    return data.map(mapDqRule);
  } catch (e) {
    console.warn('[data-workbench] fetchDataHealthChecks failed:', e);
    return [];
  }
}
