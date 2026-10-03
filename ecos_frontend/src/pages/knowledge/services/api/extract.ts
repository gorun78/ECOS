// B.9 / K-55 — Tab「extract 知识抽取」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { KB_V1 } from './base';
import type { ImportQueueItem, ExtractUploadGate, StructuredExtractReport, StructuredExtractJob } from '../../typesAndConstants';
import { fetchEngineConfig } from './govern';

// ── Wave 3 C1 — T8 抽取 · 定时 · 日志 · 本体树 类型 ───────────────────────────

/** 本体对象类型（实体）简表 */
export interface EntityBrief {
  code: string;
  name: string;
  entityType: string;
  domainId?: string;
  sortOrder?: number;
}

/** 本体树节点（域 → 本体 → 对象类型） */
export interface OntologyTreeVo {
  domain: string;
  ontologies: Array<{
    id: string;
    name: string;
    entityCodes: string[];
    /** 对象类型列表（entityType 徽章渲染源） */
    entities?: EntityBrief[];
    /** 按业务域分组的对象类型（domainId → entities） */
    domainBreakdown?: Record<string, EntityBrief[]>;
  }>;
}

/**
 * 定时抽取任务定义（TB-1 批次 / PMO-73 W3 后）
 *
 * 字段语义（铁律 §1.6-2：调度单事实源 = td_runtime_task_plan）：
 * - `id`         — 旧 fallback 镜像行 id（ecos_knowledge.kb_scheduled_extract.id）；主流程不消费
 * - `scheduleId` — 真实调度 ID == td_runtime_task_plan.task_id（前端只读显示，不区分旧/新来源）
 * - `mode/period/timeOfDay/ontologyIds` — 业务语义字段（同名旧 V141 + TB-1 路径一致）
 * - `enabled`    — 取自 td_runtime_task_plan.task_status ∈ {RUNNING, PAUSED}
 * - `lastRunAt/lastStatus/createdAt` — 来自 td_runtime_task_plan 计划元信息
 */
export interface ScheduledExtractVo {
  id: number;
  /** 真实调度 ID == td_runtime_task_plan.task_id（前端只读显示，不区分旧/新来源） */
  scheduleId: string;
  name: string;
  ontologyIds: string[];
  mode: string;
  period: string;
  timeOfDay?: string;
  /** task_status 不在 PAUSED 即 enabled=true */
  enabled: boolean;
  lastRunAt?: string;
  lastStatus?: string;
  createdAt: string;
}

/** 定时抽取任务创建 / 修改入参 */
export interface ScheduledExtractCreateReq {
  name: string;
  ontologyIds: string[];
  mode: string;
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay: string;
}

/** 抽取日志条目 */
export interface ExtractLogEntry {
  ts: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
}

// ── PMO-54 — Ingest queue ─────────────────────────────────────────────────────

const IMPORT_QUEUE_STORAGE_KEY = 'kb_import_queue';

export function fetchImportQueue(): ImportQueueItem[] {
  try {
    const raw = localStorage.getItem(IMPORT_QUEUE_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as ImportQueueItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function addImportToQueue(item: Omit<ImportQueueItem, 'id' | 'status' | 'enrollmentAt'>): ImportQueueItem {
  const list = fetchImportQueue();
  const now = new Date().toISOString();
  const entry: ImportQueueItem = {
    id: `imp-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    dsId: item.dsId,
    pipelineId: item.pipelineId,
    label: item.label,
    status: 'queued',
    progress: 0,
    enrollmentAt: now,
    errorMsg: item.errorMsg,
  };
  list.unshift(entry);
  localStorage.setItem(IMPORT_QUEUE_STORAGE_KEY, JSON.stringify(list.slice(0, 50)));
  return entry;
}

export function retryImport(id: string) {
  const list = fetchImportQueue().map(item =>
    item.id === id ? { ...item, status: 'queued' as const, progress: 0, errorMsg: undefined } : item
  );
  localStorage.setItem(IMPORT_QUEUE_STORAGE_KEY, JSON.stringify(list));
}

// ── PMO-54 — Document extraction (chunked upload) ──────────────────────────

export async function uploadDocumentChunked(
  file: File,
  onProgress: (fraction: number) => void,
): Promise<{ fileId: string; taskId: string }> {
  const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB per chunk
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  const fileId = `doc-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};
  for (let i = 0; i < totalChunks; i++) {
    const start = i * CHUNK_SIZE;
    const end = Math.min(start + CHUNK_SIZE, file.size);
    const blob = file.slice(start, end);
    const fd = new FormData();
    fd.append('file', blob);
    fd.append('fileId', fileId);
    fd.append('chunkIndex', String(i));
    fd.append('totalChunks', String(totalChunks));
    fd.append('name', file.name);
    const res = await fetch(`${KB_V1}/extract/upload`, { method: 'POST', headers, body: fd });
    if (!res.ok) throw new Error(`chunk ${i}/${totalChunks} failed: ${res.status}`);
    onProgress((i + 1) / totalChunks);
  }
  return { fileId, taskId: fileId };
}

export async function fetchExtractCandidates(fileId: string): Promise<{ candidates: Record<string, unknown>[] } | null> {
  try {
    const data = await apiFetchData<{ candidates?: Record<string, unknown>[] } | null>(`${KB_V1}/extract/candidates/${encodeURIComponent(fileId)}`);
    if (!data) return null;
    return { candidates: (data.candidates || []) as Record<string, unknown>[] };
  } catch {
    return null;
  }
}

// ── K1 结构化（映射驱动）实例抽取 ─────────────────────────────────────────────

/** 临时文件上传门禁（引擎配置 extract.allow_direct_upload） */
export async function fetchUploadEnabled(): Promise<ExtractUploadGate> {
  try {
    return await apiFetchData<ExtractUploadGate>(`${KB_V1}/extract/upload-enabled`);
  } catch {
    // 后端不可用时按默认禁用，避免绕过门禁
    return { allowed: false, hint: 'backend unavailable' };
  }
}

/** 触发结构化抽取（dryRun=true 只统计不落库） */
export async function fetchTriggerStructuredExtract(params: {
  ontologyId?: string;
  mode: 'FULL' | 'INCREMENTAL';
  dryRun?: boolean;
}): Promise<StructuredExtractReport> {
  return await apiFetchData<StructuredExtractReport>(`${KB_V1}/extract/structured`, {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

/** 抽取作业列表（水位表倒序分页） */
export async function fetchStructuredJobs(pageNum: number, pageSize: number): Promise<StructuredExtractJob[]> {
  try {
    const data = await apiFetchData<StructuredExtractJob[]>(`${KB_V1}/extract/structured/jobs?pageNum=${pageNum}&pageSize=${pageSize}`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 抽取作业详情（jobId KBK1S- 前缀反查 kg_sync_log） */
export async function fetchStructuredJobDetail(jobId: string): Promise<{ jobId: string; status: string; detail: string }> {
  return await apiFetchData<{ jobId: string; status: string; detail: string }>(
    `${KB_V1}/extract/structured/jobs/${encodeURIComponent(jobId)}`
  );
}

/** K1 抽取引擎配置（scope=extract，复用 fetchEngineConfig） */
export async function fetchExtractEngineConfig(): Promise<{ config: Record<string, unknown>; version: number; updatedAt: string }> {
  return fetchEngineConfig('extract');
}

// 待审核文件列表（PMO-56 待补）
export async function fetchExtractCandidateFiles(): Promise<{
  fileId: string;
  fileName: string;
  status: string;
  candidateCount: number;
  checksum?: string;
  createdAt?: string;
  error?: string;
}[]> {
  return await apiFetchData<{
    fileId: string;
    fileName: string;
    status: string;
    candidateCount: number;
    checksum?: string;
    createdAt?: string;
    error?: string;
  }[]>(`${KB_V1}/extract/files`);
}

/** 拉取本体三级树（domain → ontology → entityCodes）；空树或失败时兜底返回 [] 不抛 */
export async function fetchOntologyTree(): Promise<OntologyTreeVo[]> {
  try {
    const data = await apiFetchData<OntologyTreeVo[]>(`${KB_V1}/extract/ontology-tree`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/**
 * 获取定时抽取任务列表
 *
 * 后端 (ScheduledExtractController GET /extract/scheduled) 主源 =
 * td_runtime_task_plan（task_type='KB_IMPORT_SCHEDULED'，V152 W4 持久化），
 * fallback 旧表 kb_scheduled_extract 旧行合并；id 为主源 mirror 行 id（前端不消费，仅回退 UI 显示）。
 */
export async function fetchScheduledExtracts(): Promise<ScheduledExtractVo[]> {
  try {
    const data = await apiFetchData<ScheduledExtractVo[]>(`${KB_V1}/extract/scheduled`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 创建定时抽取任务 */
export async function createScheduledExtract(
  req: ScheduledExtractCreateReq,
): Promise<{ id: number; scheduleId: string; nextRunAt: string }> {
  const data = await apiFetchData<{ id: number; scheduleId: string; nextRunAt: string }>(
    `${KB_V1}/extract/scheduled`,
    { method: 'POST', body: JSON.stringify(req) },
  );
  return data || { id: 0, scheduleId: '', nextRunAt: new Date().toISOString() };
}

/** 更新定时抽取任务 */
export async function updateScheduledExtract(
  id: number,
  req: ScheduledExtractCreateReq,
): Promise<void> {
  await apiFetchData<unknown>(`${KB_V1}/extract/scheduled/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  });
}

/** 删除定时抽取任务 */
export async function deleteScheduledExtract(id: number): Promise<void> {
  await apiFetchData<unknown>(`${KB_V1}/extract/scheduled/${id}`, { method: 'DELETE' });
}

/** 获取抽取作业日志（结构化抽取 → 按 jobId 反查日志） */
export async function fetchExtractLogs(jobId: string): Promise<ExtractLogEntry[]> {
  try {
    const data = await apiFetchData<ExtractLogEntry[]>(`${KB_V1}/extract/structured/logs/${encodeURIComponent(jobId)}`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 获取抽取日志导出 Blob（POST body 触发下载，供 T10 组件 window.open / a.download 触发） */
export async function fetchExportLogBody(jobId: string): Promise<Blob> {
  const token = localStorage.getItem('token') || '';
  const res = await fetch(`${KB_V1}/extract/structured/export-log`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ jobId }),
  });
  if (!res.ok) throw new Error(`export-log HTTP ${res.status}`);
  return res.blob();
}

/**
 * 触发结构化抽取。
 *
 * 后端（StructuredExtractController POST /extract/structured）两种响应形态：
 * - dryRun=true  —— 同步返回 EntityInstanceExtractionReportVO
 *   （{ ontologyId, ontologyCount, entityCount, nodeCreated, nodeUpdated,
 *     nodeSkipped, edgeCreated, durationMs, issues[], ... }，无可轮询 taskId）；
 * - dryRun=false —— 异步返回 KbImportTriggerVO（{ taskId, jobId }）。
 */
export interface StructuredExtractDryRunReport {
  ontologyId?: string;
  durationMs?: number;
  ontologyCount?: number;
  entityCount?: number;
  nodeCreated?: number;
  nodeUpdated?: number;
  nodeSkipped?: number;
  edgeCreated?: number;
  invalidMappings?: number;
  mode?: string;
  dryRun?: boolean;
}

export interface StructuredExtractTriggerResp extends StructuredExtractDryRunReport {
  taskId?: string;
  jobId?: string;
  status?: string;
}

export async function triggerStructuredExtract(req: {
  dryRun: boolean;
  mode: string;
}): Promise<StructuredExtractTriggerResp> {
  const data = await apiFetchData<StructuredExtractTriggerResp>(
    `${KB_V1}/extract/structured`,
    { method: 'POST', body: JSON.stringify(req) },
  );
  return data || { taskId: '', jobId: '', status: 'PENDING' };
}

/** 查询结构化抽取任务状态 */
export async function fetchExtractStatus(taskId: string): Promise<{
  taskId: string;
  status: string;
  progress: number;
  statusMessage?: string;
}> {
  const data = await apiFetchData<{ taskId: string; status: string; progress: number; statusMessage?: string }>(
    `${KB_V1}/extract/status/${encodeURIComponent(taskId)}`,
  );
  return data || { taskId, status: 'UNKNOWN', progress: 0 };
}

// ── Wave 3 C1（2 号）：本体树 + 定时抽取管理 + 日志流（snake_case 后端形态） ─────

/** 同 OntologyTreeVo，语义命名对齐任务契约 */
export type OntologyTreeNode = OntologyTreeVo;

/** 结构化抽取作业信息（status/jobs 端点，兼容 taskId 关联） */
export interface ExtractJobInfo {
  jobId: string;
  trigger: 'MANUAL' | 'SCHEDULED';
  mode: 'FULL' | 'INCREMENTAL';
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  progress?: number;             // 0~100
  statusMessage?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  rowsRead?: number;
  rowsWritten?: number;
  mismatches?: number;
  error?: string;
  advice?: string;
  taskId?: string;
}

/** 拉取最近抽取作业列表（limit 上限 50） */
export async function fetchExtractJobs(limit = 50): Promise<ExtractJobInfo[]> {
  try {
    const d = await apiFetchData<{ jobs?: ExtractJobInfo[] } & ExtractJobInfo>(
      `${KB_V1}/extract/structured/jobs?limit=${limit}`,
    );
    if (!d) return [];
    // 兼容两种返回形态：{jobs:[...]} 或直接数组形态
    if (Array.isArray((d as { jobs?: unknown }).jobs)) return (d as { jobs?: ExtractJobInfo[] }).jobs || [];
    if (d.jobId) return [d as ExtractJobInfo];
    return [];
  } catch {
    return [];
  }
}

/** 单个抽取作业状态（按 taskId 查询） */
export async function fetchExtractJobStatus(taskId: string): Promise<ExtractJobInfo | null> {
  try {
    return await apiFetchData<ExtractJobInfo>(
      `${KB_V1}/extract/structured/status/${encodeURIComponent(taskId)}`,
    );
  } catch {
    return null;
  }
}

/** 定时抽取任务行（后端 snake_case 形态；TB-1 后 scheduleId = td_runtime_task_plan.task_id） */
export interface ScheduledExtractRow {
  id: number;
  /** 真实调度 ID == td_runtime_task_plan.task_id（前端不区分旧/新来源，仅显示） */
  scheduleId: string;
  name: string;
  ontologyIds: string[];
  mode: 'FULL' | 'INCREMENTAL';
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay?: string;             // "HH:mm"
  enabled: number;                // 0/1
  created_at?: string;
  next_run_at?: string;
  last_run_at?: string;
  last_status?: string;
}

/** 创建定时抽取任务（POST /extract/scheduled） */
export async function createScheduledExtractRow(body: {
  name: string;
  ontologyIds: string[];
  mode: 'FULL' | 'INCREMENTAL';
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay: string;
}): Promise<{ id: number; scheduleId: string; nextRunAt: string }> {
  return await apiFetchData<{ id: number; scheduleId: string; nextRunAt: string }>(
    `${KB_V1}/extract/scheduled`,
    { method: 'POST', body: JSON.stringify(body) },
  );
}

/** 更新定时抽取任务（PUT /extract/scheduled/{id}） */
export async function updateScheduledExtractRow(id: number, body: {
  name: string;
  ontologyIds: string[];
  mode: 'FULL' | 'INCREMENTAL';
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay: string;
  enabled?: boolean;
}): Promise<{ id: number; scheduleId: string; nextRunAt: string }> {
  return await apiFetchData<{ id: number; scheduleId: string; nextRunAt: string }>(
    `${KB_V1}/extract/scheduled/${id}`,
    { method: 'PUT', body: JSON.stringify(body) },
  );
}

/** 导出抽取日志（POST /export-log → Blob，供 a.download 触发下载） */
export async function exportExtractLog(jobId: string): Promise<Blob> {
  const token = localStorage.getItem('token') || '';
  const res = await fetch(`${KB_V1}/extract/structured/export-log`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ jobId }),
  });
  if (!res.ok) throw new Error(`extract log export failed: ${res.status}`);
  return await res.blob();
}
