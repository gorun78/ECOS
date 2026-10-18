/**
 * extract.ts — 知识抽取（接入 → 预演 → 真执行 → 报告）契约（B.9 / K-55 拆分）。
 *
 * 覆盖 Tab：`extract`
 * base 单源：KNOWLEDGE_BASE = /api/v1/knowledge（`/extract/*`）+ lineage / integration
 *
 * K-55 纠正：
 *   - `fetchExtractLogs` / `fetchExportLogBody` / `uploadDocumentChunked` /
 *     `uploadEvalSeed` 不再直接 `localStorage.getItem('token')`，
 *     auth 头统一走 `services/auth.ts`（`getAuthToken` / `authOnlyHeaders`）。
 *   - `fetchOntologyTree` / `fetchScheduledExtracts` / `fetchExtractJobs` 走
 *     `kbFetchData` → 5xx / ECOS-KB-* 抛 `KbApiError`（Tab 错误态分治）。
 *
 * ImportQueue（`kb_import_queue`）：属 K-57 4 组 localStorage-as-DB 组之一，
 * 移动端折扣要求「改为 sessionStorage 只保 UI 偏好」，数据→服务端持久化
 * TODO: 后续 Wave 由服务端 export/import 队列落地；此处切换 storage 面以
 *        减小浏览器本地数据面（页刷新后队列可恢复但不再跨会话）。
 */
import { apiFetchData } from '../../../api';
import { ApiRequestError } from '../../../services/httpClient';
import { getAuthToken } from '../../../services/auth';
import { kbFetchData } from './kbFetch';
import type {
  ImportQueueItem,
  StructuredExtractReport,
  StructuredExtractJob,
} from '../typesAndConstants';

const KB_V1 = '/api/v1/knowledge';

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
    entities?: EntityBrief[];
    domainBreakdown?: Record<string, EntityBrief[]>;
  }>;
}

export type OntologyTreeNode = OntologyTreeVo;

/**
 * 定时抽取任务定义（TB-1 批次 / PMO-73 W3 后）。
 *
 * 字段语义（铁律 §1.6-2：调度单事实源 = td_runtime_task_plan）：
 * - `id`         — 旧 fallback 镜像行 id（ecos_knowledge.kb_scheduled_extract.id）；主流程不消费
 * - `scheduleId` — 真实调度 ID == td_runtime_task_plan.task_id（前端只读显示，不区分旧/新来源）
 * - `mode/period/timeOfDay/ontologyIds` — 业务语义字段
 * - `enabled`    — 取自 td_runtime_task_plan.task_status ∈ {RUNNING, PAUSED}
 */
export interface ScheduledExtractVo {
  id: number;
  scheduleId: string;
  name: string;
  ontologyIds: string[];
  mode: string;
  period: string;
  timeOfDay?: string;
  enabled: boolean;
  lastRunAt?: string;
  lastStatus?: string;
  createdAt: string;
}

export interface ScheduledExtractCreateReq {
  name: string;
  ontologyIds: string[];
  mode: string;
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay: string;
}

export interface ExtractLogEntry {
  ts: string;
  level: 'INFO' | 'WARN' | 'ERROR';
  message: string;
}

// ── PMO-54 — Document extraction (chunked upload) ────────────────────────────

/**
 * PDF / md / docx 等分片上传（5MB/chunk）。
 * auth 头改走 auth.ts（K-55）；行为不变 —— BFF 端点路径 / handler 不动。
 */
export async function uploadDocumentChunked(
  file: File,
  onProgress: (fraction: number) => void,
): Promise<{ fileId: string; taskId: string }> {
  const CHUNK_SIZE = 5 * 1024 * 1024;
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE);
  const fileId = `doc-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const token = getAuthToken();
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
export async function fetchUploadEnabled(): Promise<{ allowed: boolean; hint: string }> {
  try {
    return await apiFetchData<{ allowed: boolean; hint: string }>(`${KB_V1}/extract/upload-enabled`);
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

/** 抽取作业列表（水位表倒序分页）— K-55 前端侧限上限 50 */
export async function fetchStructuredJobs(pageNum: number, pageSize: number): Promise<StructuredExtractJob[]> {
  const clampedSize = Math.min(Math.max(1, pageSize), 50);
  try {
    const data = await apiFetchData<StructuredExtractJob[]>(
      `${KB_V1}/extract/structured/jobs?pageNum=${pageNum}&pageSize=${clampedSize}`,
    );
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/**
 * K-55 严格版：抽取作业列表拉取失败抛 KbApiError / Error，
 * 供 OverviewPage「抽取任务」KPI 区分真实 0 与失败降级。
 */
export async function kbFetchStructuredJobs(pageNum: number, pageSize: number): Promise<StructuredExtractJob[]> {
  const clampedSize = Math.min(Math.max(1, pageSize), 50);
  const data = await kbFetchData<StructuredExtractJob[]>(
    `${KB_V1}/extract/structured/jobs?pageNum=${pageNum}&pageSize=${clampedSize}`,
  );
  return Array.isArray(data) ? data : [];
}

export async function fetchStructuredJobDetail(jobId: string): Promise<{ jobId: string; status: string; detail: string }> {
  return await apiFetchData<{ jobId: string; status: string; detail: string }>(
    `${KB_V1}/extract/structured/jobs/${encodeURIComponent(jobId)}`,
  );
}

/** 待审核文件列表（PMO-56 待补） */
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

// ── 抽取作业信息（Wave 3 补充 2） ──────────────────────────────────────────────
// StructuredExtractReport / StructuredExtractJob 类型自 typesAndConstants 单一来源
// （与本文件 fetchTriggerStructuredExtract / fetchStructuredJobs 返回一致）。

export interface StructuredExtractTriggerResp {
  taskId?: string;
  jobId?: string;
  status?: string;
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

// ── 抽取任务轮询（DocumentUploadTab 状态机）───────────────────────────────────
// 传输合流 #143/B5-10c-3：该腿原写在页面组件的 setInterval 回调里——私设 `await fetch`
// ＋手拼 Bearer＋自行拆信封，是 httpClient 之外的第四个解包点（台账 §13.2.4 C2）。
// 腿体下沉本域服务＝解包交 apiFetchData 单源，组件只消费判别结果；同时让该腿可在
// node 环境被直接测试（组件文件在 Node 20.18.1 下无法安全 import，见 vite.config W198 注）。

/** 后端 TaskStatus → 页面阶段 token（未知状态无映射 ⇒ 调用方保持当前阶段） */
export type ExtractPhaseToken = 'queued' | 'parsing' | 'extracting' | 'reviewing' | 'done' | 'failed';

// 用 Map 而非对象字面量：状态串来自后端，`obj['constructor']` 这类键会取到原型成员。
const EXTRACT_STATUS_PHASE = new Map<string, ExtractPhaseToken>([
  ['UPLOADED', 'queued'],
  ['PARSING', 'parsing'],
  ['EXTRACTING', 'extracting'],
  ['PENDING_REVIEW', 'reviewing'],
  ['COMPLETED', 'done'],
  ['FAILED', 'failed'],
]);

export type ExtractTaskPayload = Record<string, any>;

export type ExtractTaskPoll =
  | { source: 'backend'; task: ExtractTaskPayload }
  | { source: 'unavailable'; error: unknown };

function normalizeTaskPayload(v: unknown): ExtractTaskPayload {
  const o = (v && typeof v === 'object' ? v : {}) as Record<string, unknown>;
  return { ...o, status: String(o.status ?? '') };
}

/**
 * 轮询一次抽取任务。两条出腿互斥：
 * `backend` = 后端应答了（含错误信封，载荷按空对象归一 ⇒ 映射为「不作为」）；
 * `unavailable` = 传输/HTTP 不可用，由调用方决定是否降级。
 */
export async function pollExtractTask(taskId: string): Promise<ExtractTaskPoll> {
  try {
    const data = await apiFetchData<ExtractTaskPayload>(`${KB_V1}/extract/tasks/${taskId}`);
    return { source: 'backend', task: normalizeTaskPayload(data) };
  } catch (e) {
    // `httpStatus` 缺失 ⇒ 抛出物只可能来自「HTTP 200 + 信封 success:false / code 非 0·200」这一条腿，
    // 即后端应答了、只是应答失败（`ExtractionController.getTask` 的 notFound 就是这个形状）。
    // 改前此处拆到的是错误信封本身 ⇒ 无 status ⇒ 保持当前阶段；若折进 unavailable，
    // 一条真实应答就会被本地伪造推进接管，假推进面反而扩大。
    if (e instanceof ApiRequestError && e.httpStatus === undefined) {
      return { source: 'backend', task: normalizeTaskPayload({}) };
    }
    return { source: 'unavailable', error: e };
  }
}

export function mapExtractTaskPhase(task: ExtractTaskPayload | null | undefined): ExtractPhaseToken | null {
  return EXTRACT_STATUS_PHASE.get(String(task?.status ?? '')) ?? null;
}

/** 审核面板入参取形：sourceText 兜空串，results 优先、reviewData 次之、皆无则 null */
export function extractTaskReviewPayload(task: ExtractTaskPayload): { sourceText: string; data: any } {
  return { sourceText: task.sourceText || '', data: task.results || task.reviewData || null };
}

// 结构化抽取作业（status/jobs 端点，兼容 taskId 关联）
export interface ExtractJobInfo {
  jobId: string;
  trigger: 'MANUAL' | 'SCHEDULED';
  mode: 'FULL' | 'INCREMENTAL';
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  progress?: number;
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

/** 拉取最近抽取作业列表（K-55 limit 上限 50，不再无界） */
export async function fetchExtractJobs(limit = 50): Promise<ExtractJobInfo[]> {
  const clamped = Math.min(Math.max(1, limit), 50);
  try {
    const d = await apiFetchData<{ jobs?: ExtractJobInfo[] } & ExtractJobInfo>(
      `${KB_V1}/extract/structured/jobs?limit=${clamped}`,
    );
    if (!d) return [];
    if (Array.isArray((d as { jobs?: unknown }).jobs)) return (d as { jobs?: ExtractJobInfo[] }).jobs || [];
    if (d.jobId) return [d as ExtractJobInfo];
    return [];
  } catch {
    return [];
  }
}

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
  scheduleId: string;
  name: string;
  ontologyIds: string[];
  mode: 'FULL' | 'INCREMENTAL';
  period: 'DAILY' | 'WEEKLY' | 'MONTHLY';
  timeOfDay?: string;
  enabled: number;
  created_at?: string;
  next_run_at?: string;
  last_run_at?: string;
  last_status?: string;
}

export async function createScheduledExtract(
  req: ScheduledExtractCreateReq,
): Promise<{ id: number; scheduleId: string; nextRunAt: string }> {
  const data = await apiFetchData<{ id: number; scheduleId: string; nextRunAt: string }>(
    `${KB_V1}/extract/scheduled`,
    { method: 'POST', body: JSON.stringify(req) },
  );
  return data || { id: 0, scheduleId: '', nextRunAt: new Date().toISOString() };
}

export async function updateScheduledExtract(
  id: number,
  req: ScheduledExtractCreateReq,
): Promise<void> {
  await apiFetchData<unknown>(`${KB_V1}/extract/scheduled/${id}`, {
    method: 'PUT',
    body: JSON.stringify(req),
  });
}

export async function deleteScheduledExtract(id: number): Promise<void> {
  await apiFetchData<unknown>(`${KB_V1}/extract/scheduled/${id}`, { method: 'DELETE' });
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

/** 拉取本体三级树（domain → ontology → entityCodes）；走 KbApiError → Tab 显式错误/空分治 */
export async function kbFetchOntologyTree(): Promise<OntologyTreeVo[]> {
  const data = await kbFetchData<OntologyTreeVo[]>(`${KB_V1}/extract/ontology-tree`);
  return Array.isArray(data) ? data : [];
}

/** 静默版（OntologyTreePicker 老 surface），保留：KbApiError 由调用方按需 catch */
export async function fetchOntologyTree(): Promise<OntologyTreeVo[]> {
  try {
    const data = await apiFetchData<OntologyTreeVo[]>(`${KB_V1}/extract/ontology-tree`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 获取定时抽取任务列表（走 KbApiError 供 Tab 错误态） */
export async function kbFetchScheduledExtracts(): Promise<ScheduledExtractVo[]> {
  const data = await kbFetchData<ScheduledExtractVo[]>(`${KB_V1}/extract/scheduled`);
  return Array.isArray(data) ? data : [];
}

export async function fetchScheduledExtracts(): Promise<ScheduledExtractVo[]> {
  try {
    const data = await apiFetchData<ScheduledExtractVo[]>(`${KB_V1}/extract/scheduled`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** KbApiError 版：抽取日志（结构化抽取 → 按 jobId 反查） */
export async function kbFetchExtractLogs(jobId: string): Promise<ExtractLogEntry[]> {
  const data = await kbFetchData<ExtractLogEntry[]>(
    `${KB_V1}/extract/structured/logs/${encodeURIComponent(jobId)}`,
  );
  return Array.isArray(data) ? data : [];
}

export async function fetchExtractLogs(jobId: string): Promise<ExtractLogEntry[]> {
  try {
    const data = await apiFetchData<ExtractLogEntry[]>(
      `${KB_V1}/extract/structured/logs/${encodeURIComponent(jobId)}`,
    );
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 抽取日志导出 Blob（auth 头走 auth.ts；K-55 头部单源） */
export async function fetchExportLogBody(jobId: string): Promise<Blob> {
  const token = getAuthToken();
  const res = await fetch(`${KB_V1}/extract/structured/export-log`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ jobId }),
  });
  if (!res.ok) throw new Error(`export-log HTTP ${res.status}`);
  return res.blob();
}

/** 导出抽取日志（alias；page 层需 a.click() 触发下载） */
export async function exportExtractLog(jobId: string): Promise<Blob> {
  const token = getAuthToken();
  const res = await fetch(`${KB_V1}/extract/structured/export-log`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ jobId }),
  });
  if (!res.ok) throw new Error(`extract log export failed: ${res.status}`);
  return await res.blob();
}

// ── Lineage / integration metadata（extract 数据源可见性） ───────────────────────

export async function fetchLineageImpact(startNode: string) {
  try {
    return await apiFetchData(`/api/v1/lineage/impact?startNode=${encodeURIComponent(startNode)}`);
  } catch {
    return null;
  }
}

export async function parseLineage(format: string, payload: string) {
  try {
    return await apiFetchData('/api/v1/lineage/parse', {
      method: 'POST',
      body: JSON.stringify({ format, payload }),
    });
  } catch {
    return null;
  }
}

export async function fetchIntegrationMetadata(): Promise<{
  simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean };
} | null> {
  try {
    return await apiFetchData<{
      simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean };
    }>('/api/v1/integration/metadata');
  } catch {
    return null;
  }
}

export async function fetchIntegrationLogs() {
  try {
    return await apiFetchData('/api/v1/integration/logs');
  } catch {
    return [];
  }
}

export async function toggleSimulationDrift(type: string, enabled: boolean) {
  try {
    return await apiFetchData('/api/v1/integration/metadata/drift', {
      method: 'POST',
      body: JSON.stringify({ type, enabled }),
    });
  } catch {
    return null;
  }
}

/** DataWorkbench ETL sources —— tab/DatasyncTab 主数据源之一 */
export interface DataWorkbenchSource {
  dsId: string;
  name: string;
  type: string;
  status: 'connected' | 'disconnected' | 'flaky';
  records?: string;
  pipelines?: Array<{ pipelineId: string; name: string }>;
}

interface DataWorkbenchSourceRaw {
  id?: string | number;
  dsId?: string;
  name?: string;
  tableName?: string;
  sourceType?: string;
  type?: string;
  status?: string;
  syncStatus?: string;
  records?: string;
  recordsOrFields?: string;
  pipelines?: DataWorkbenchSource['pipelines'];
}

interface DataWorkbenchSourcesResponse {
  data?: DataWorkbenchSourceRaw[];
  sources?: DataWorkbenchSourceRaw[];
}

export async function fetchDataWorkbenchSources(): Promise<DataWorkbenchSource[]> {
  try {
    const data = await apiFetchData<DataWorkbenchSourceRaw[] | DataWorkbenchSourcesResponse>('/api/v1/integration/metadata');
    const items = Array.isArray(data) ? data : data?.data || data?.sources || [];
    return items.map((s) => ({
      dsId: String(s.id ?? s.dsId ?? s.name ?? ''),
      name: String(s.name ?? s.tableName ?? s.id ?? ''),
      type: String(s.sourceType ?? s.type ?? 'integration'),
      status: (s.status ?? (s.syncStatus === 'synced' ? 'connected' : 'disconnected')) as DataWorkbenchSource['status'],
      records: s.records ?? s.recordsOrFields,
      pipelines: Array.isArray(s.pipelines) ? s.pipelines : undefined,
    })).filter(s => s.dsId);
  } catch {
    return [];
  }
}

interface MetadataDriftRaw {
  schemaDelta?: Array<Record<string, unknown>>;
  fields?: Array<Record<string, unknown>>;
  rows?: Array<Record<string, unknown>>;
  samples?: Array<Record<string, unknown>>;
  lineage?: { nodes: Array<Record<string, unknown>>; links: Array<Record<string, unknown>> };
}

export async function fetchMetadataDrift(sample?: boolean): Promise<{
  schemaDelta: Array<Record<string, unknown>>;
  rows?: Array<Record<string, unknown>>;
  lineage?: { nodes: Array<Record<string, unknown>>; links: Array<Record<string, unknown>> };
}> {
  try {
    const q = sample ? '?sample=true' : '';
    const data = await apiFetchData<MetadataDriftRaw | null>(`/api/v1/integration/metadata/drift${q}`);
    return {
      schemaDelta: data?.schemaDelta || data?.fields || [],
      rows: data?.rows || data?.samples,
      lineage: data?.lineage,
    };
  } catch {
    return { schemaDelta: [] };
  }
}

// ── PMO-54 — Import queue（K-55 组 1：localStorage → sessionStorage 折扣） ────
//
// 移动端折扣 B.9 / K-57：这一组把 `import queue` 当作本地数据 (KB) 用，
// 属「数据持久化」性质（不是 UI 偏好）。按 B.9 约束改为 sessionStorage，
// 会话结束不清空 UI 序列（避免污染其它会话的浏览器 profile），
// 服务端持久化方向 TODO：走 `ITaskManagementService` 建立 KB_IMPORT_TASK 队列
//   + 每行 `kb_import_queue_item`（W111 首 wave 交付），届时前端改读 REST。
const IMPORT_QUEUE_STORAGE_KEY = 'kb_import_queue';
const IMPORT_QUEUE_STORAGE: Storage | undefined =
  typeof sessionStorage !== 'undefined' ? sessionStorage : undefined;

function queueStorage(): Storage | null {
  return IMPORT_QUEUE_STORAGE ?? null;
}

export function fetchImportQueue(): ImportQueueItem[] {
  try {
    const store = queueStorage();
    if (!store) return [];
    const raw = store.getItem(IMPORT_QUEUE_STORAGE_KEY);
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
  try {
    queueStorage()?.setItem(IMPORT_QUEUE_STORAGE_KEY, JSON.stringify(list.slice(0, 50)));
  } catch {
    /* sessionStorage 面不可用（如 storage 关闭）— 静默保留 in-memory */
  }
  return entry;
}

export function retryImport(id: string) {
  const list = fetchImportQueue().map((item) =>
    item.id === id ? { ...item, status: 'queued' as const, progress: 0, errorMsg: undefined } : item,
  );
  try {
    queueStorage()?.setItem(IMPORT_QUEUE_STORAGE_KEY, JSON.stringify(list));
  } catch {
    /* noop */
  }
}
