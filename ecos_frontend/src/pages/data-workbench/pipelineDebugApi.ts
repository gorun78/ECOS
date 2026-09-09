/**
 * pipelineDebugApi — 断点调试后端 API 客户端。
 *
 * 后端契约 (T1 + T3):
 *   POST   /api/v1/pipeline/debug/sessions                       — 创建调试会话
 *   GET    /api/v1/pipeline/debug/sessions/{id}                  — 会话状态 (含 steps/log/hitRecords/snapshot)
 *   POST   /api/v1/pipeline/debug/sessions/{id}/step             — 单步推进
 *   POST   /api/v1/pipeline/debug/sessions/{id}/continue         — 继续到下一断点/结束
 *   POST   /api/v1/pipeline/debug/sessions/{id}/stop             — 停止会话
 *   POST   /api/v1/pipeline/debug/sessions/{id}/reset            — 重置会话
 *   DELETE /api/v1/pipeline/debug/sessions/{id}                  — 删除会话
 *   GET    /api/v1/pipeline/debug/sessions/executions?definitionId&pageSize — 执行历史
 *   GET    /api/v1/pipeline/debug/sessions/{id}/steps            — 当前步骤快照
 *   GET    /api/v1/pipeline/debug/sessions/{id}/preview/{nodeId}— 节点 schema+sample
 *   GET    /api/v1/pipeline/debug/executions/{executionId}/logs  — 节点级执行日志
 *
 * 返回值统一 ApiResponse<T>: { code, success, message, data }。
 *
 * @license Apache-2.0
 */

const DBG_BASE = '/api/v1/pipeline/debug/sessions';
const LOG_BASE = '/api/v1/pipeline/debug/executions';

function authHeaders(): Record<string, string> {
  const token =
    typeof localStorage !== 'undefined'
      ? localStorage.getItem('token') || localStorage.getItem('accessToken') || ''
      : '';
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 步骤级快照 (debug 会话下保留 rows/sample/schema)。 */
export interface NodeStepSnapshot {
  nodeId: string;
  type?: string;
  status: string; // QUEUED/AWAITING/RUNNING/SUCCEEDED/FAILED
  rowsProcessed?: number;
  elapsedMs?: number;
  finishedAt?: string;
  columnsIn?: string[];
  columnsOut?: string[];
  sampleRows?: Record<string, unknown>[];
  snapshot?: Record<string, unknown>;
  errorMsg?: string;
}

/** 断点规格 (调试会话配置)。 */
export interface BreakpointSpec {
  nodeId: string;
  condition?: string;
  enabled: boolean;
}

/** 兼容 — 旧名 Breakpoint 字段对齐 BreakpointSpec (nodeId/enabled/condition)。 */
export type Breakpoint = BreakpointSpec & { id: string };

/** 命中记录 (调试会话命中断点时的变量快照)。 */
export interface HitRecord {
  nodeId: string;
  condition?: string;
  at: string;
  snapshot?: Record<string, unknown>;
}

/** 调试会话 VO (后端 PipelineDebugSessionVO 投影)。 */
export interface DebugSession {
  sessionId: string;
  state:
    | 'created'
    | 'queued'
    | 'running'
    | 'awaiting'
    | 'broken'
    | 'paused'
    | 'completed'
    | 'failed'
    | 'stopped';
  definitionId?: string;
  executionId?: string;
  pipelineName?: string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
  totalNodes: number;
  completedNodes: number;
  currentNodeId?: string;
  currentStepStatus?: string;
  rowsProcessed: number;
  variableSnapshot?: Record<string, unknown>;
  breakpoints: BreakpointSpec[];
  steps: NodeStepSnapshot[];
  hitRecords: HitRecord[];
  error?: string;
}

/** 节点级日志行。 */
export interface DebugLogLine {
  seq: number;
  nodeId?: string;
  level: 'INFO' | 'WARNING' | 'ERROR';
  message: string;
  atMs: number;
}

/** 执行记录 (与 PipelineController#listExecutions 同源)。 */
export interface ExecutionRecord {
  id: string;
  definitionId: string;
  status: string;
  startedAt?: number;
  completedAt?: number;
  errorMessage?: string;
  rowsProcessed?: number;
}

async function call<T>(method: 'GET' | 'POST' | 'DELETE', path: string, body?: unknown): Promise<T | null> {
  try {
    const res = await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      throw new Error(`debug ${method} ${path} → ${res.status}`);
    }
    const json = await res.json();
    return (json?.data ?? json) as T;
  } catch (e) {
    console.warn('[pipeline-debug] api call failed:', e);
    return null;
  }
}

/**
 * 创建调试会话。
 * @param payload 调试请求 (definitionId 或 definition.nodes + breakpoints)
 */
export function startDebugSession(
  payload: {
    definitionId?: string;
    definition?: {
      name?: string;
      nodes: { nodeId: string; type: string; config?: Record<string, unknown>; dependsOn?: string[] }[];
      edges?: { id?: string; source: string; target: string }[];
    };
    breakpoints?: { nodeId: string; condition?: string }[];
  },
): Promise<DebugSession | null> {
  return call<DebugSession>('POST', DBG_BASE, payload);
}

/** 查询会话。 */
export function getDebugSession(id: string): Promise<DebugSession | null> {
  return call<DebugSession>('GET', `${DBG_BASE}/${encodeURIComponent(id)}`);
}

/** 单步推进。 */
export function stepDebugSession(id: string): Promise<DebugSession | null> {
  return call<DebugSession>('POST', `${DBG_BASE}/${encodeURIComponent(id)}/step`, {});
}

/** 继续到下一断点。 */
export function continueDebugSession(id: string): Promise<DebugSession | null> {
  return call<DebugSession>('POST', `${DBG_BASE}/${encodeURIComponent(id)}/continue`, {});
}

/** 停止会话。 */
export function stopDebugSession(id: string): Promise<DebugSession | null> {
  return call<DebugSession>('POST', `${DBG_BASE}/${encodeURIComponent(id)}/stop`, {});
}

/** 重置会话 (保留断点)。 */
export function resetDebugSession(id: string): Promise<DebugSession | null> {
  return call<DebugSession>('POST', `${DBG_BASE}/${encodeURIComponent(id)}/reset`, {});
}

/** 删除会话。 */
export function deleteDebugSession(id: string): Promise<null> {
  return call<null>('DELETE', `${DBG_BASE}/${encodeURIComponent(id)}`);
}

/** 列出执行历史 (分页)。 */
export function listDebugExecutions(
  definitionId: string,
  page = 1,
  pageSize = 20,
): Promise<ExecutionRecord[] | null> {
  return call<ExecutionRecord[]>('GET', `${DBG_BASE}/executions?definitionId=${encodeURIComponent(definitionId)}&page=${page}&pageSize=${pageSize}`);
}

/** 节点数据预览 (columnsIn/Out + sampleRows 前 100 行)。 */
export function getNodePreview(
  sessionId: string,
  nodeId: string,
): Promise<{ columnsIn?: string[]; columnsOut?: string[]; sampleRows?: Record<string, unknown>[] } | null> {
  return call<unknown>('GET', `${DBG_BASE}/${encodeURIComponent(sessionId)}/preview/${encodeURIComponent(nodeId)}`) as Promise<{ columnsIn?: string[]; columnsOut?: string[]; sampleRows?: Record<string, unknown>[] } | null>;
}

/** 取节点级日志 (SSE 兜底, 按 executionId 拉一次全量, 前端做增量)。 */
export function getExecutionLog(executionId: string): Promise<DebugLogLine[] | null> {
  return call<DebugLogLine[]>('GET', `${LOG_BASE}/${encodeURIComponent(executionId)}/logs`);
}
