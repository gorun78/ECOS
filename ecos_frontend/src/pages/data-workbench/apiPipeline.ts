/**
 * Data Workbench — 管道 + 血缘 + 同步任务域（W66 拆分）。
 * 管道 definition CRUD/执行、血缘拓扑/影响度/重建、管道同步任务。
 * @license Apache-2.0
 */
import type { DataPipeline, DataSyncTask, PipelineNode } from './types';
import { get, post, authHeaders } from './httpClient';

// ─── API 端点常量 ──────────────────────────────────────────
const PIPELINE_DEFS = '/api/v1/pipeline/definitions'; // PipelineController
const LINEAGE_TOPOL = '/api/v1/engine/data/lineage/topology';
const INTEGRATION  = '/api/integration/metadata';   // syncTasks 聚合接口

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

/** Read a node field tolerating backend field-name variants (first non-empty wins). */
function readNodeField(n: Record<string, unknown>, keys: string[]): unknown {
  for (const k of keys) {
    const v = n[k];
    if (v !== undefined && v !== null && v !== '') return v;
  }
  return undefined;
}

// Wave 3 (lower): backend node payload is not canonicalized (id may be
// id/nodeId, type may be type/nodeType, position may be positionX/x) —
// normalize both for the canvas rendering.
function normalizeBackendPipelineNode(raw: Record<string, unknown>): PipelineNode {
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

/**
 * Wave 3 (lower): fetch one pipeline definition by id — full graph detail.
 * GET /api/v1/pipeline/definitions/{id} returns the definition JSONB
 * (with nodes/edges); the list API only returns summaries. Editor must use
 * this to render the canvas (list/detail pairing contract).
 *
 * PMO-52 T2: tolerate top-level `{id,name,nodes,edges}` OR
 * nested ApiResponse wrapper `{code, success, data:{id,name,nodes,edges}}`.
 */
export async function getPipelineDefinition(id: string): Promise<DataPipeline | null> {
  try {
    // Use full fetch (not the local `get` helper) so the test can stub the
    // full envelope via `vi.stubGlobal('fetch', ...)` and we get identical
    // tolerance for both flat and wrapped shapes in one place.
    const res = await fetch(`${PIPELINE_DEFS}/${encodeURIComponent(id)}`, {
      headers: { ...authHeaders() },
    });
    if (!res.ok) throw new Error(`${res.status}`);
    const json = (await res.json()) as Record<string, unknown>;
    const raw: unknown = json.data ?? json;
    if (!raw || typeof raw !== 'object') return null;
    const flat = raw as Record<string, unknown>;
    // Nested ApiResponse<T> envelope: unwrap .data when present
    const inner = flat.data && typeof flat.data === 'object'
      ? (flat.data as Record<string, unknown>)
      : flat;
    const rawNodes = Array.isArray(inner.nodes) ? (inner.nodes as Record<string, unknown>[]) : [];
    const detail = mapPipelineDef(inner);
    return { ...detail, nodes: rawNodes.map(normalizeBackendPipelineNode) };
  } catch (e) {
    console.warn('[data-workbench] getPipelineDefinition failed:', e);
    return null;
  }
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

/** Data Lineage — 查询持久化拓扑（全局表级/字段级血缘全景图） */
/** 后端 DataLineageService.getTopology 返回 Map（nodes/edges/total_nodes/total_edges/from_db） */
export interface LineageTopologyNode {
  id: string;
  type: string;
  label: string;
  table?: string;
  pipeline_task_id?: string;
  pipeline_task_name?: string;
}
export interface LineageTopologyEdge {
  id: string;
  source: string;
  target: string;
  transform?: string;
  pipeline_task_id?: string;
  pipeline_task_name?: string;
}
export interface LineageTopology {
  nodes: LineageTopologyNode[];
  edges: LineageTopologyEdge[];
  total_nodes: number;
  total_edges: number;
  from_db?: boolean;
}
export async function fetchLineageTopology(): Promise<LineageTopology> {
  try {
    const data = await get<Partial<LineageTopology>>(LINEAGE_TOPOL);
    return {
      nodes: Array.isArray(data?.nodes) ? data.nodes : [],
      edges: Array.isArray(data?.edges) ? data.edges : [],
      total_nodes: Number(data?.total_nodes) || 0,
      total_edges: Number(data?.total_edges) || 0,
      from_db: data?.from_db,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchLineageTopology failed:', e);
    return { nodes: [], edges: [], total_nodes: 0, total_edges: 0, from_db: false };
  }
}

/** Data Lineage — 影响度分析：从 startNode 做双向 N 层 BFS */
/** 后端 DataLineageService.computeImpact 返回 Map，downstream/upstream 每项含 id/hop/label/riskScore */
interface LineageImpactNodeRaw {
  id?: string;
  hop?: number;
  label?: string;
  riskScore?: number;
}
interface LineageImpactRaw {
  startNode?: string;
  canonicalStartNode?: string;
  matched?: boolean;
  depth?: number;
  severity?: string;
  totalRisk?: number;
  branchCount?: number;
  downstream?: LineageImpactNodeRaw[];
  upstream?: LineageImpactNodeRaw[];
}
export async function fetchLineageImpact(
  startNode: string,
  depth = 3
): Promise<{
  startNode: string;
  canonicalStartNode: string;
  matched: boolean;
  depth: number;
  severity: 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  totalRisk: number;
  downstream: { id: string; hop: number; label: string; riskScore: number }[];
  upstream: { id: string; hop: number; label: string; riskScore: number }[];
  branchCount: number;
} | null> {
  if (!startNode || !startNode.trim()) return null;
  try {
    const params = new URLSearchParams();
    params.set('startNode', startNode);
    params.set('depth', String(depth));
    const url = '/api/v1/engine/data/lineage/impact?' + params.toString();
    const res = await fetch(url, { headers: { ...authHeaders() } });
    if (!res.ok) return null;
    const json = await res.json();
    const data: LineageImpactRaw = json?.data ?? json;
    if (!data) return null;
    const toArr = (v: unknown): LineageImpactNodeRaw[] => (Array.isArray(v) ? (v as LineageImpactNodeRaw[]) : []);
    return {
      startNode: data.startNode as string,
      canonicalStartNode: (data.canonicalStartNode as string) || data.startNode || '',
      matched: Boolean(data.matched),
      depth: Number(data.depth) || 1,
      severity: (data.severity as string) || 'NONE',
      totalRisk: Number(data.totalRisk) || 0,
      downstream: toArr(data.downstream).map((n) => ({
        id: n.id as string, hop: Number(n.hop) || 1,
        label: n.label as string, riskScore: Number(n.riskScore) || 0,
      })),
      upstream: toArr(data.upstream).map((n) => ({
        id: n.id as string, hop: Number(n.hop) || 1,
        label: n.label as string, riskScore: Number(n.riskScore) || 0,
      })),
      branchCount: Number(data.branchCount) || 0,
    };
  } catch (e) {
    console.warn('[data-workbench] fetchLineageImpact failed:', e);
    return null;
  }
}

/** Data Lineage — 重建并持久化血缘（同步全量解析 pipeline_task） */
export async function rebuildLineage(limit = 0): Promise<{
  total_nodes: number; total_edges: number;
  tasks_scanned: number; tasks_parsed: number; sql_failed: number;
  definitions_scanned?: number;
}> {
  try {
    const res = await fetch(`${LINEAGE_TOPOL}/rebuild?limit=${limit}`, {
      method: 'POST', headers: { ...authHeaders() },
    });
    if (!res.ok) throw new Error(`${LINEAGE_TOPOL}/rebuild → ${res.status}`);
    const json = await res.json();
    const data = json?.data ?? json;
    return {
      total_nodes: Number(data?.total_nodes) || 0,
      total_edges: Number(data?.total_edges) || 0,
      tasks_scanned: Number(data?.tasks_scanned) || 0,
      tasks_parsed: Number(data?.tasks_parsed) || 0,
      sql_failed: Number(data?.sql_failed) || 0,
      ...(data?.definitions_scanned != null
        ? { definitions_scanned: Number(data.definitions_scanned) }
        : {}),
    };
  } catch (e) {
    console.warn('[data-workbench] rebuildLineage failed:', e);
    return { total_nodes: 0, total_edges: 0, tasks_scanned: 0, tasks_parsed: 0, sql_failed: 0 };
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

/** 触发同步任务执行 → PipelineTaskController POST /api/v1/engine/data/pipeline/tasks/{id}/run */
export async function triggerSyncRun(taskId: string): Promise<{ status?: string; runId?: string } | null> {
  try {
    return await post<{ status?: string; runId?: string }>(`/api/v1/engine/data/pipeline/tasks/${taskId}/run`, {});
  } catch (e) {
    console.warn('[data-workbench] triggerSyncRun failed:', e);
    return null;
  }
}