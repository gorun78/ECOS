/**
 * Data Workbench — backend API layer
 * 对接 databridge-v2 真实后端，含字段映射适配。
 * @license Apache-2.0
 */
import type { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck } from './types';

// ─── API 端点常量 ──────────────────────────────────────────
const DATANET_DS = '/datanet/datasource';           // DataSourceController
const INTEGRATION  = '/api/integration/metadata';   // IntegrationMetadataController (connections + syncTasks)
const PIPELINE_DEFS = '/api/v1/pipeline/definitions'; // PipelineController
const DQ_RULES      = '/api/v1/ecos/dq/rules';         // DqController (camelCase 字段)
const LINEAGE_NODES = '/api/v1/engine/data/lineage/nodes';
const LINEAGE_EDGES = '/api/v1/engine/data/lineage/edges';

// ─── Auth helper ────────────────────────────────────────
function authHeaders(): Record<string, string> {
  const token = typeof localStorage !== 'undefined' ? (localStorage.getItem('token') || localStorage.getItem('accessToken') || '') : '';
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function get<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { ...authHeaders() } });
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
    taskType: (t.taskType as DataSyncTask['taskType']) || 'SYNC',
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

/** Pipeline save payload (PMO-3J T3) — matches backend PipelineController createDefinition. */
export interface PipelineSavePayload {
  name: string;
  description?: string;
  nodes?: Array<{
    id: string;
    nodeId: string;
    type: string; // P2-01 enumeration value
    config: Record<string, unknown>;
    positionX: number;
    positionY: number;
  }>;
  edges?: Array<{ from: string; to: string }>;
  status?: string;
}

/** Pipeline CRUD — 创建 (supports full { name, nodes, edges } payload per PMO-3J T3) */
export async function createPipeline(payload: PipelineSavePayload): Promise<DataPipeline | null> {
  try {
    const res = await fetch(PIPELINE_DEFS, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({
        name: payload.name,
        description: payload.description || '',
        nodes: payload.nodes || [],
        edges: payload.edges || [],
      }),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return mapPipelineDef(json.data ?? {});
  } catch (e) {
    console.warn('[data-workbench] createPipeline failed:', e);
    return null;
  }
}

/** Pipeline CRUD — 更新 (supports full { name, nodes, edges } payload per PMO-3J T3) */
export async function updatePipeline(
  id: string,
  payload: Partial<PipelineSavePayload>
): Promise<DataPipeline | null> {
  try {
    const body: Record<string, unknown> = {};
    if (payload.name !== undefined) body.name = payload.name;
    if (payload.description !== undefined) body.description = payload.description;
    if (payload.status !== undefined) body.status = payload.status;
    if (payload.nodes !== undefined) body.nodes = payload.nodes;
    if (payload.edges !== undefined) body.edges = payload.edges;
    const res = await fetch(`${PIPELINE_DEFS}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = await res.json();
    return mapPipelineDef(json.data ?? {});
  } catch (e) {
    console.warn('[data-workbench] updatePipeline failed:', e);
    return null;
  }
}

/**
 * Save a pipeline definition's full graph (nodes + edges) — PMO-3J T3.
 * Used when an existing pipeline's DAG is edited and must persist
 * `ecos_pipeline_node` rows. Falls back to updatePipeline if the dedicated
 * definition endpoint is unavailable.
 */
export async function savePipelineDefinition(
  id: string,
  payload: PipelineSavePayload
): Promise<DataPipeline | null> {
  return updatePipeline(id, payload);
}

/** Pipeline CRUD — 删除（软删除 → ARCHIVED） */
export async function deletePipeline(id: string): Promise<boolean> {
  try {
    const res = await fetch(`${PIPELINE_DEFS}/${id}`, { method: 'DELETE', headers: { ...authHeaders() } });
    return res.ok;
  } catch (e) {
    console.warn('[data-workbench] deletePipeline failed:', e);
    return false;
  }
}

/** Pipeline CRUD — 执行 */
export async function executePipeline(id: string): Promise<{ executionId?: string; status?: string } | null> {
  try {
    const res = await fetch(`${PIPELINE_DEFS}/${id}/execute`, { method: 'POST', headers: { ...authHeaders() } });
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

/** Data Lineage — fetch nodes */
export async function fetchLineageNodes(): Promise<unknown[]> {
  try {
    const data = await get<unknown[]>(LINEAGE_NODES);
    return Array.isArray(data) ? data : [];
  } catch (e) {
    console.warn('[data-workbench] fetchLineageNodes failed:', e);
    return [];
  }
}

/** Data Lineage — fetch edges */
export async function fetchLineageEdges(): Promise<unknown[]> {
  try {
    const data = await get<unknown[]>(LINEAGE_EDGES);
    return Array.isArray(data) ? data : [];
  } catch (e) {
    console.warn('[data-workbench] fetchLineageEdges failed:', e);
    return [];
  }
}

/** Data Lineage — trigger build from pipeline execution records */
export async function buildLineage(): Promise<boolean> {
  try {
    const res = await fetch('/api/v1/engine/data/lineage/build', { method: 'POST', headers: { ...authHeaders() } });
    return res.ok;
  } catch (e) {
    console.warn('[data-workbench] buildLineage failed:', e);
    return false;
  }
}

/** Fetch sync tasks from PipelineTaskController (task_type=SYNC) */
export async function fetchSyncTasksFromPipeline(): Promise<DataSyncTask[]> {
  try {
    const data = await get<unknown[]>('/api/v1/engine/data/pipeline/tasks?taskType=SYNC');
    if (!Array.isArray(data)) return [];
    return data.map(mapSyncTask);
  } catch (e) {
    console.warn('[data-workbench] fetchSyncTasksFromPipeline failed:', e);
    return [];
  }
}

// ─── 写入路径（POST）─────────────────────────────────────

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

async function put<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

async function del<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'DELETE', headers: { ...authHeaders() } });
  if (!res.ok) throw new Error(`${url} → ${res.status}`);
  const json = await res.json();
  return (json.data ?? json) as T;
}

/** ConnectionConfig builder — 前端表单 → connectionConfig JSON string */
function buildConnectionConfig(c: {
  type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  password?: string;
}): string {
  const cfg: Record<string, unknown> = { username: c.username };
  if (c.password) cfg.password = c.password;
  const isJdbc = ['postgresql', 'mysql', 'doris'].includes(c.type);
  if (isJdbc) {
    const driver = c.type === 'mysql' ? 'mysql' : c.type === 'doris' ? 'mysql' : 'postgresql';
    const db = c.database || c.schema || '';
    cfg.jdbcUrl = `jdbc:${driver}://${c.host || 'localhost'}:${c.port || 5432}/${db}`;
  } else if (c.type === 's3' || c.type === 'oss') {
    cfg.endpointUrl = c.endpointUrl || c.host;
    cfg.bucket = c.bucket;
    cfg.role = c.role;
  } else {
    cfg.host = c.host;
    cfg.port = c.port;
    if (c.database) cfg.database = c.database;
  }
  return JSON.stringify(cfg);
}

/** 创建数据源 → DataSourceController POST /datanet/datasource */
export async function createDataSource(payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; schema?: string; bucket?: string; endpointUrl?: string; role?: string;
  description?: string; tags?: string; password?: string;
}): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: payload.type.toUpperCase(),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
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
}): Promise<DataConnection | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: payload.type.toUpperCase(),
      connectionConfig: buildConnectionConfig(payload),
      description: payload.description || '',
      tags: payload.tags || '',
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

/** 测试未保存的数据源连接 → POST /datanet/datasource/test (raw DTO, no id needed) */
export async function testDataSourceRaw(payload: {
  name: string; type: string; host: string; port: number; username: string;
  database?: string; password?: string;
}): Promise<{ success: boolean; message?: string } | null> {
  try {
    const dto = {
      datasourceName: payload.name,
      datasourceType: payload.type.toUpperCase(),
      connectionConfig: buildConnectionConfig(payload),
    };
    const result = await post<{ success: boolean; message?: string }>(`${DATANET_DS}/test`, dto);
    return result;
  } catch (e) {
    console.warn('[data-workbench] testDataSourceRaw failed:', e);
    return null;
  }
}

/** 创建同步任务 → PipelineTaskController POST /api/v1/engine/data/pipeline/tasks (taskType=SYNC) */
export async function createSyncTask(payload: {
  name: string; sourceConnectionId: string; sourceTable?: string;
  targetTable?: string; syncMode?: string; schedule?: string; description?: string;
}): Promise<DataSyncTask | null> {
  try {
    const body = {
      name: payload.name,
      task_type: 'SYNC',
      status: 'DRAFT',
      description: payload.description || '',
      cron_expression: payload.schedule || '',
      config_json: JSON.stringify({
        sourceConnectionId: payload.sourceConnectionId,
        sourceTable: payload.sourceTable || '',
        targetTable: payload.targetTable || '',
        syncMode: payload.syncMode || 'snapshot',
      }),
    };
    const result = await post<Record<string, unknown>>('/api/v1/engine/data/pipeline/tasks', body);
    return mapSyncTask(result);
  } catch (e) {
    console.warn('[data-workbench] createSyncTask failed:', e);
    return null;
  }
}

/** 创建健康检查规则 → DqController POST /api/v1/ecos/dq/rules */
export async function createHealthCheck(payload: {
  name: string; ruleType: string; severity?: string;
  targetEntity?: string; ruleExpression?: string; description?: string;
}): Promise<DataHealthCheck | null> {
  try {
    const body = {
      name: payload.name,
      ruleType: payload.ruleType,
      severity: payload.severity || 'MEDIUM',
      targetEntity: payload.targetEntity || '',
      ruleExpression: payload.ruleExpression || '',
      description: payload.description || '',
      enabled: true,
    };
    const rule = await post<Record<string, unknown>>(DQ_RULES, body);
    return mapDqRule(rule);
  } catch (e) {
    console.warn('[data-workbench] createHealthCheck failed:', e);
    return null;
  }
}

/** 触发同步任务执行 → PipelineTaskController POST /api/v1/engine/data/pipeline/tasks/{id}/run */
export async function triggerSyncRun(taskId: string): Promise<{ status?: string; runId?: string } | null> {
  try {
    return await post<{ status?: string; runId?: string }>(`/api/v1/engine/data/pipeline/tasks/${taskId}/run`, {});
  } catch (e) {
    console.warn('[data-workbench] triggerSyncRun failed:', e);
    return null;
  }
}

/** 执行健康检查 → DqController POST /api/v1/ecos/dq/check */
export async function runHealthCheck(): Promise<Record<string, unknown> | null> {
  try {
    return await post<Record<string, unknown>>('/api/v1/ecos/dq/check', {});
  } catch (e) {
    console.warn('[data-workbench] runHealthCheck failed:', e);
    return null;
  }
}
