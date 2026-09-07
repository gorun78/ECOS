/**
 * Task Center API — 与 TaskController 真实后端端点对齐
 * 后端: /api/v1/task/*
 */

const BASE = "/api/v1/task";

// ── 类型 ──────────────────────────────────────────────
export type RealTaskStatus =
  | "PENDING" | "PARSING" | "PARSED" | "RUNNING" | "PAUSED"
  | "SUCCEEDED" | "FAILED" | "CANCELLED" | "TIMEOUT";

export type TaskCategory = "pipeline" | "agent" | "realtime" | "management";

export interface TaskSummary {
  taskId: string;
  taskName: string;
  taskType: string;
  category: TaskCategory | null;
  description?: string;
  priority?: number;
  createTime?: string;
  createdBy?: string;
  parameters?: Record<string, unknown>;
}

export interface TaskStatusInfo {
  taskId: string;
  status: RealTaskStatus | "UNKNOWN";
  statusMessage?: string;
  progress: number;
  startedAt?: string;
  completedAt?: string;
}

export interface TaskFull extends TaskSummary {
  status?: RealTaskStatus | "UNKNOWN";
  statusMessage?: string;
  progress?: number;
  startedAt?: string;
  completedAt?: string;
}

export interface TaskSingleFull {
  task: TaskSummary;
  status: TaskStatusInfo | null;
}

export interface TaskListResult {
  items: TaskSummary[];
  total: number;
  offset: number;
  limit: number;
}

export interface TaskStats {
  total: number;
  running: number;
  pending: number;
  succeeded: number;
  failed: number;
  cancelled: number;
}

export interface TaskTypesPayload {
  categories: { key: string; label: string; types: string[] }[];
}

// ── 内部工具 ──────────────────────────────────────────
function authHeaders(): Record<string, string> {
  try {
    const token = sessionStorage.getItem("jwt") || localStorage.getItem("jwt") || "";
    return token ? { Authorization: `Bearer ${token}` } : {};
  } catch { return {}; }
}

async function jget<T>(url: string): Promise<T | null> {
  const res = await fetch(url, { headers: authHeaders(), cache: "no-store" });
  if (!res.ok) return null;
  const json = await res.json();
  return (json?.data ?? null as T);
}

async function jpost<T>(url: string, body?: unknown): Promise<T | null> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) return null;
  const json = await res.json();
  return (json?.data ?? null as T);
}

// ── 列表与统计 ────────────────────────────────────────
export async function fetchTaskList(params: {
  offset?: number;
  limit?: number;
  taskType?: string;
  status?: string;
  createdBy?: string;
} = {}): Promise<TaskListResult | null> {
  const qs = new URLSearchParams();
  if (params.offset != null) qs.set("offset", String(params.offset));
  if (params.limit != null) qs.set("limit", String(params.limit));
  if (params.taskType) qs.set("taskType", params.taskType);
  if (params.status) qs.set("status", params.status);
  if (params.createdBy) qs.set("createdBy", params.createdBy);
  return jget<TaskListResult>(`${BASE}/list?${qs.toString()}`);
}

export async function fetchTaskStatus(taskId: string): Promise<TaskStatusInfo | null> {
  return jget<TaskStatusInfo>(`${BASE}/${encodeURIComponent(taskId)}/status`);
}

export async function fetchTaskStats(): Promise<TaskStats | null> {
  return jget<TaskStats>(`${BASE}/stats`);
}

export async function fetchTaskTypes(): Promise<TaskTypesPayload | null> {
  return jget<TaskTypesPayload>(`${BASE}/types`);
}

// ── 单任务操作 ────────────────────────────────────────
export async function executeTask(taskId: string): Promise<{ taskId: string; result?: string } | null> {
  return jpost<{ taskId: string; result?: string }>(`${BASE}/${encodeURIComponent(taskId)}/execute`);
}

export async function cancelTask(taskId: string): Promise<{ message: string } | null> {
  return jpost<{ message: string }>(`${BASE}/${encodeURIComponent(taskId)}/cancel`);
}

export async function pauseTask(taskId: string): Promise<{ message: string } | null> {
  return jpost<{ message: string }>(`${BASE}/${encodeURIComponent(taskId)}/pause`);
}

export async function resumeTask(taskId: string): Promise<{ message: string } | null> {
  return jpost<{ message: string }>(`${BASE}/${encodeURIComponent(taskId)}/resume`);
}

export async function archiveTask(taskId: string): Promise<{ message: string } | null> {
  return jpost<{ message: string }>(`${BASE}/${encodeURIComponent(taskId)}/archive`);
}

// ── 批量操作 ──────────────────────────────────────────
export type BatchAction = "cancel" | "pause" | "resume" | "archive";

export async function batchTask(taskIds: string[], action: BatchAction): Promise<{ successCount: number; errors: string[] } | null> {
  return jpost<{ successCount: number; errors: string[] }>(`${BASE}/batch`, { taskIds, action });
}

// ── 任务分类辅助 ──────────────────────────────────────
export const TYPE_TO_CATEGORY: Record<string, TaskCategory> = {
  DORIS_SQL: "pipeline", ETL: "pipeline", DATA_SYNC: "pipeline", DATA_INGEST: "pipeline", PIPELINE: "pipeline",
  AGENT: "agent", AI_AGENT: "agent", LLM_TASK: "agent", KG_SYNC: "agent",
  METADATA_COLLECT: "pipeline",
  REALTIME: "realtime", STREAMING: "realtime", MONITOR: "realtime", ALERT: "realtime", TELEMETRY: "realtime",
  DATA_QUALITY: "management", REPORT: "management", MAINTENANCE: "management", BACKUP: "management",
  CONFIG: "management", ADMIN: "management",
};

export function getTypeCategory(taskType?: string): TaskCategory | null {
  if (!taskType) return null;
  return TYPE_TO_CATEGORY[taskType.toUpperCase()] ?? null;
}

export const CATEGORY_LABELS: Record<TaskCategory, string> = {
  pipeline: "管道",
  agent: "Agent",
  realtime: "实时",
  management: "管理类",
};
