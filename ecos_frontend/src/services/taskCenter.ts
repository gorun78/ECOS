/**
 * Task Center API — 与 TaskController 真实后端端点对齐
 * 后端: /api/v1/task/*
 * PMO-72 W4: 数据源 = td_runtime_task* 5 表 (PG sys_man.public) +
 *           runtime-task JdbcTaskPersistenceService 持久化 (cron/next_run_at/last_run_at/last_status)
 */

import { apiFetchData, doFetch } from "../api";
import { rawAuthedFetch } from "./httpClient";

const BASE = "/api/v1/task";
/** rawAuthedFetch 自带 API_BASE 前缀单源，此处只给「前缀之后」的路径，避免拼成 /api/api/… */
const AUTHED_PROBE_BASE = "/v1/task";

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
// 解包点单源 (#143/B5-10c-1)：信封拆合交 httpClient，域内只保留「失败形状 T|null」
// 这层契约（消费者 AsyncTaskCenterView 用 !! 与 Array.isArray 判据）。
async function jget<T>(url: string): Promise<T | null> {
  return apiFetchData<T>(url, { cache: "no-store" }).catch((): T | null => null);
}

async function jpost<T>(url: string, body?: unknown): Promise<T | null> {
  return apiFetchData<T>(url, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  }).catch((): T | null => null);
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

// ── Task Center (S5-1.5) — 自 src/api.ts 迁入，签名与路径不变 (H6-T1) ──
const TASK_BASE = "/api/v1/task";

/** GET /api/v1/task/list — 任务列表 */
export async function apiTaskList(params?: {
  status?: string;
  type?: string;
  page?: number;
  size?: number;
}): Promise<any> {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.type) qs.set("type", params.type);
  if (params?.page != null) qs.set("page", String(params.page));
  if (params?.size != null) qs.set("size", String(params.size));
  const q = qs.toString();
  return apiFetchData(`${TASK_BASE}/list${q ? "?" + q : ""}`);
}

/** POST /api/v1/task/submit — 提交任务 */
export async function apiTaskSubmit(body: {
  taskName: string;
  taskType: string;
  config: Record<string, any>;
  runner: string;
  priority?: string;
  maxRetries?: number;
}): Promise<any> {
  return apiFetchData(`${TASK_BASE}/submit`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/task/{id}/status — 查询任务状态 */
export async function apiTaskStatus(id: string): Promise<any> {
  return apiFetchData(`${TASK_BASE}/${encodeURIComponent(id)}/status`);
}

/** POST /api/v1/task/{id}/cancel — 取消任务 */
export async function apiTaskCancel(id: string): Promise<any> {
  return apiFetchData(`${TASK_BASE}/${encodeURIComponent(id)}/cancel`, {
    method: "POST",
  });
}

/** GET /api/v1/task/stats — 任务实时统计 */
export async function apiTaskStats(): Promise<TaskStats> {
  return apiFetchData(`${TASK_BASE}/stats`);
}

// ── 裸 fetch 收口 (H6-T2)：TaskPanel / TaskCenter 原语义（整包 JSON）迁移 ──
// #143/B5-10c-1 二次收口：这两条腿的返回值是**整包信封**（消费者 useTaskPanel 逐字判
// d.code === 0 / d.code !== 0 并读 d.message、d.total），故合流到整包通道 doFetch 而非
// apiFetchData——合包与否由腿部外显形状决定，整包语义不构成第二解包点。

/** 整包 JSON GET（保留 code/data 原判断语义，失败抛错由调用方 catch） */
async function rawGetJson(url: string): Promise<any> {
  return doFetch(url);
}

/** 整包 JSON POST（单源恒带 Content-Type，原 withContentType 分支失去意义后移除） */
async function rawPostJson(url: string, body?: unknown): Promise<any> {
  return doFetch(url, {
    method: "POST",
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export function taskPanelTypesJson(): Promise<any> {
  return rawGetJson(`${BASE}/types`);
}

export function taskPanelStatsJson(): Promise<any> {
  return rawGetJson(`${BASE}/stats`);
}

export function taskPanelListJson(params: URLSearchParams): Promise<any> {
  return rawGetJson(`${BASE}/list?${params.toString()}`);
}

export function taskPanelDetailJson(taskId: string): Promise<any> {
  return rawGetJson(`${BASE}/${taskId}`);
}

export function taskPanelActionJson(taskId: string, action: string): Promise<any> {
  return rawPostJson(`${BASE}/${taskId}/${action}`);
}

export function taskPanelBatchJson(taskIds: string[], action: string): Promise<any> {
  return rawPostJson(`${BASE}/batch`, { taskIds, action });
}

/** GET /api/v1/task/doris/health — 返回 {ok, json}，仅 ok 时解析 */
export async function taskDorisHealth(): Promise<{ ok: boolean; json?: any }> {
  const r = await rawAuthedFetch(`${AUTHED_PROBE_BASE}/doris/health`);
  return { ok: r.ok, json: r.ok ? await r.json() : undefined };
}

/** POST /api/v1/task/{id}/execute — 仅返回是否成功（原 TaskCenter 语义） */
export async function taskExecuteOk(taskId: string): Promise<boolean> {
  const r = await rawAuthedFetch(`${AUTHED_PROBE_BASE}/${taskId}/execute`, { method: "POST" });
  return r.ok;
}
