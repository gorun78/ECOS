/**
 * Data Workbench — 数据管道 (Pipeline) CRUD/执行 + 同步任务
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { get, post, PIPELINE_DEFS, INTEGRATION } from './_base';
import { authOnlyHeaders as authHeaders } from '../../../services/auth';
import { mapPipelineDef, mapSyncTask, normalizeBackendPipelineNode } from './mappers';
import type { DataPipeline, DataSyncTask } from '../types';

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
