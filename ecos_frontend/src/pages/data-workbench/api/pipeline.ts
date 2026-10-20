/**
 * Data Workbench — 数据管道 (Pipeline) CRUD/执行 + 同步任务
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license Apache-2.0
 */
import { del, get, post, postEmpty, put, PIPELINE_DEFS, INTEGRATION } from './_base';
import { mapPipelineDef, mapSyncTask, normalizeBackendPipelineNode } from './mappers';
import { API_BASE } from '../../../services/httpClient';
// #145/B6-15：`/v1/engine/data/pipeline/runs` 族根属主在 `api/dataClient`，本行只取值不持体。
import { ENGINE_DATA_PIPELINE_RUNS_BASE } from '../../../api/dataClient';
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

/** 同步任务列表 — 从 CeosCompatController 聚合接口获取（失败向上抛出，由调用方呈现错误态+重试，禁吞错回退空数组） */
export async function fetchDataSyncTasks(): Promise<DataSyncTask[]> {
  const data = await get<{ syncTasks?: unknown[] }>(INTEGRATION);
  if (!data?.syncTasks || !Array.isArray(data.syncTasks)) return [];
  return data.syncTasks.map(mapSyncTask);
}

/** Fetch sync tasks from PipelineTaskController (task_type=SYNC)（失败上抛，禁吞错回退空数组） */
export async function fetchSyncTasksFromPipeline(): Promise<DataSyncTask[]> {
  const data = await get<unknown[]>(`${API_BASE}/v1/engine/data/pipeline/tasks?taskType=SYNC`);
  if (!Array.isArray(data)) return [];
  return data.map(mapSyncTask);
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
    const result = await post<Record<string, unknown>>(`${API_BASE}/v1/engine/data/pipeline/tasks`, body);
    return mapSyncTask(result);
  } catch (e) {
    console.warn('[data-workbench] createSyncTask failed:', e);
    return null;
  }
}

/** 触发同步任务执行 → PipelineTaskController POST /api/v1/engine/data/pipeline/tasks/{id}/run */
export async function triggerSyncRun(taskId: string): Promise<{ status?: string; runId?: string } | null> {
  try {
    return await post<{ status?: string; runId?: string }>(`${API_BASE}/v1/engine/data/pipeline/tasks/${taskId}/run`, {});
  } catch (e) {
    console.warn('[data-workbench] triggerSyncRun failed:', e);
    return null;
  }
}

/**
 * 查询一次 pipeline run 的终态状态 — 用于 triggerSync 轮询。
 * GET /api/v1/engine/data/pipeline/runs/{runId}（与 PipelineExecutionMonitor 同源端点）。
 * 失败返回 null（调用方必须按失败处理，不得当成成功）。
 */
export async function fetchPipelineRunStatus(
  runId: string
): Promise<{ status?: string; errorMsg?: string } | null> {
  try {
    const data = await get<{ status?: string; errorMsg?: string; data?: { status?: string; errorMsg?: string } }>(
      `${ENGINE_DATA_PIPELINE_RUNS_BASE}/${encodeURIComponent(runId)}`
    );
    return data?.data && typeof data.data === 'object' ? data.data : data;
  } catch (e) {
    console.warn('[data-workbench] fetchPipelineRunStatus failed:', e);
    return null;
  }
}

/** Pipeline 定义列表（失败向上抛出，由调用方呈现错误态+重试，禁吞错回退空数组） */
export async function fetchDataPipelines(): Promise<DataPipeline[]> {
  const data = await get<unknown[]>(PIPELINE_DEFS);
  if (!Array.isArray(data)) return [];
  return data.map(mapPipelineDef);
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
    const raw = await get<Record<string, unknown>>(`${PIPELINE_DEFS}/${encodeURIComponent(id)}`);
    if (!raw || typeof raw !== 'object') return null;
    // Nested ApiResponse<T> envelope: unwrap .data when present
    const inner = raw.data && typeof raw.data === 'object'
      ? (raw.data as Record<string, unknown>)
      : raw;
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
    const created = await post<Record<string, unknown>>(PIPELINE_DEFS, {
      name: payload.name,
      description: payload.description || '',
      nodes: payload.nodes || [],
      edges: payload.edges || [],
    });
    return mapPipelineDef(created ?? {});
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
    const updated = await put<Record<string, unknown>>(`${PIPELINE_DEFS}/${id}`, body);
    return mapPipelineDef(updated ?? {});
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
    await del<unknown>(`${PIPELINE_DEFS}/${id}`);
    return true;
  } catch (e) {
    console.warn('[data-workbench] deletePipeline failed:', e);
    return false;
  }
}

/** Pipeline CRUD — 执行 */
export async function executePipeline(id: string): Promise<{ executionId?: string; status?: string } | null> {
  try {
    return await postEmpty<{ executionId?: string; status?: string }>(`${PIPELINE_DEFS}/${id}/execute`) ?? null;
  } catch (e) {
    console.warn('[data-workbench] executePipeline failed:', e);
    return null;
  }
}
