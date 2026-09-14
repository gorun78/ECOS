/**
 * Task Center API — 与 TaskController 真实后端端点对齐
 * 后端: /api/v1/task/*
 */

const BASE = "/api/v1/task";

// ── 类型 ──────────────────────────────────────────────
export type RealTaskStatus =
  | "PENDING" | "PARSING" | "PARSED" | "RUNNING" | "PAUSED"
  | "SUCCEEDED" | "FAILED" | "CANCELLED" | "TIMEOUT";

export type TaskCategory = "pipeline" | "agent" | "realtime" | "management" | "metadata" | "cron";

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

// ── 任务分类辅助：从 taskType 字符串到 category key（前端 label 从 i18n 取，避免写死）──
export type TaskCategoryKey = "pipeline" | "agent" | "realtime" | "management" | "metadata" | "cron";

export interface TaskCategoryDef {
  key: TaskCategoryKey;
  types: string[];        // 匹配该分类的 taskType 字符串列表（忽略大小写）
}

export const TASK_CATEGORIES: TaskCategoryDef[] = [
  { key: "metadata",   types: ["METADATA_COLLECT", "SCHEMA_PREVIEW"] },
  { key: "pipeline",   types: ["DORIS_SQL", "ETL", "DATA_SYNC", "DATA_INGEST", "PIPELINE", "PIPELINE_RUN"] },
  { key: "agent",      types: ["AGENT", "AI_AGENT", "LLM_TASK", "KG_SYNC", "CRON", "AGENT_CRON"] },
  { key: "realtime",   types: ["REALTIME", "STREAMING", "MONITOR", "ALERT", "TELEMETRY", "HEALTH_CHECK"] },
  { key: "management", types: ["DATA_QUALITY", "REPORT", "MAINTENANCE", "BACKUP", "CONFIG", "ADMIN", "DQ_CHECK"] },
  { key: "cron",       types: ["CRON", "SCHEDULED"] },
];

/** 单次（taskType 优先归类 metadata，避免与已有 pipeline 字集群冲突） */
export function categorize(taskType?: string): TaskCategoryKey | null {
  if (!taskType) return null;
  const t = taskType.toUpperCase();
  // metadata 优先 — 同步/定时采集都是"元数据"类
  if (t === "METADATA_COLLECT") return "metadata";
  for (const c of TASK_CATEGORIES) {
    if (c.key === "metadata") continue; // 已处理
    if (c.types.some(x => x.toUpperCase() === t)) return c.key;
  }
  return null;
}

/** 兼容层：保留旧 TYPE_TO_CATEGORY + getTypeCategory，防止其他模块 import 失败 */
const LEGACY_LEGACY_TYPES: Record<string, TaskCategory> = {
  DORIS_SQL: "pipeline", ETL: "pipeline", DATA_SYNC: "pipeline", DATA_INGEST: "pipeline", PIPELINE: "pipeline",
  AGENT: "agent", AI_AGENT: "agent", LLM_TASK: "agent", KG_SYNC: "agent",
  METADATA_COLLECT: "pipeline",
  REALTIME: "realtime", STREAMING: "realtime", MONITOR: "realtime", ALERT: "realtime", TELEMETRY: "realtime",
  DATA_QUALITY: "management", REPORT: "management", MAINTENANCE: "management", BACKUP: "management",
  CONFIG: "management", ADMIN: "management",
};
export const TYPE_TO_CATEGORY = LEGACY_LEGACY_TYPES;
export function getTypeCategory(taskType?: string): TaskCategory | null {
  if (!taskType) return null;
  return LEGACY_LEGACY_TYPES[taskType.toUpperCase()] ?? null;
}

// 兼容层：CATEGORY_LABELS 已硬编码中文 — 仅供旧调用方读，新增请使用 i18n t('taskPanel.category.' + key)
export const CATEGORY_LABELS: Record<TaskCategory, string> = {
  pipeline: "管道",
  agent: "Agent",
  realtime: "实时",
  management: "管理类",
  metadata: "元数据",
  cron: "定时任务",
};
