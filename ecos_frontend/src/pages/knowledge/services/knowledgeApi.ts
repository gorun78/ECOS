import { apiFetchData } from '../../../api';
import type {
  KnowledgeSettings,
  RagRequest,
  RagResult,
  SyncStatus,
  SyncLog,
  MetadataAsset,
  RuleRepository,
  RuleVersion,
  GlossaryTerm,
  GraphBuildJob,
  GraphBuildPreview,
  ImportQueueItem,
  EvalSeedQuery,
  EvalReport,
  LifecycleAsset,
  LifecycleAuditEntry,
  LifecycleState,
  EngineConfigScope,
  EngineConfig,
  ExtractUploadGate,
  StructuredExtractReport,
  StructuredExtractJob,
} from '../typesAndConstants';

const KNOWLEDGE_BASE = '/api/knowledge';
const GRAPH_BASE = '/api/knowledge';
const GLOSSARY_BASE = '/api/v1/ontology/glossary';
const CATALOG_BASE = '/api/catalog';
const COGNITIVE_BASE = '/api/v1/cognitive';
const RULES_BASE = '/api/v1/knowledge/compliance-rules';

// ── PMO-54 helpers ───────────────────────────────────────────────────────────

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
    /** 对象类型列表（entityType 徽章渲染源） */
    entities?: EntityBrief[];
    /** 按业务域分组的对象类型（domainId → entities） */
    domainBreakdown?: Record<string, EntityBrief[]>;
  }>;
}

/** 定时抽取任务定义 */
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

/** SSE-capable RAG query — falls back to POST /rag when backend hasn't wired SSE */
export async function runRAGQuerySSE(query: string, onToken: (token: string) => void): Promise<{ answerGenerated: boolean }> {
  // Try: GET /api/v1/knowledge/rag?query=...&stream=true
  const token = localStorage.getItem('token') || '';
  try {
    const url = `${KB_V1}/rag?query=${encodeURIComponent(query)}&stream=true`;
    const res = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok || !res.body) throw new Error(`SSE ${res.status}`);
    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let answerGenerated = true;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const chunk = decoder.decode(value, { stream: true });
      // SSE-format `data: xxx` lines; strip newline and parse
      const lines = chunk.split('\n');
      for (const line of lines) {
        const m = line.match(/^data:\s*(.+)$/);
        if (m) onToken(m[1].trim());
      }
    }
    return { answerGenerated };
  } catch (e) {
    // SSE not available — fall back to POST; report answer not generated
    console.info('SSE fallback to POST:', (e as Error).message);
    const result = await runRAGQuery({ query });
    onToken(result.answer || '');
    return { answerGenerated: Boolean(result.answerGenerated) };
  }
}

export async function fetchGraph(domain?: string) {
  const q = domain ? `?domain=${encodeURIComponent(domain)}` : '';
  return apiFetchData<{ nodes: unknown[]; edges: unknown[] }>(`${GRAPH_BASE}/graph${q}`);
}

export async function fetchNode(id: string) {
  return apiFetchData(`${GRAPH_BASE}/nodes/${encodeURIComponent(id)}`);
}

export async function searchKnowledge(query: string) {
  return apiFetchData(`${GRAPH_BASE}/search?q=${encodeURIComponent(query)}`);
}

export async function findPath(source: string, target: string) {
  return apiFetchData(`${GRAPH_BASE}/path?s=${encodeURIComponent(source)}&t=${encodeURIComponent(target)}`);
}

// ── PMO-26 T1: Graph full-text search & path ──
export async function graphSearch(query: string) {
  return apiFetchData(`/api/v1/knowledge/graph/search?q=${encodeURIComponent(query)}`);
}

export async function graphPath(source: string, target: string) {
  return apiFetchData('/api/v1/knowledge/graph/path', {
    method: 'POST',
    body: JSON.stringify({ source, target }),
  });
}

export async function fetchNeighbors(id: string, depth = 1) {
  return apiFetchData(`${GRAPH_BASE}/neighbors/${encodeURIComponent(id)}?d=${depth}`);
}

export async function createNode(data: Record<string, unknown>) {
  return apiFetchData(`${GRAPH_BASE}/nodes`, { method: 'POST', body: JSON.stringify(data) });
}

export async function createEdge(data: Record<string, unknown>) {
  return apiFetchData(`${GRAPH_BASE}/edges`, { method: 'POST', body: JSON.stringify(data) });
}

export async function getDataSource() {
  return apiFetchData(`${GRAPH_BASE}/source`);
}

export async function fetchSyncStatuses(): Promise<SyncStatus[]> {
  try {
    const data = await apiFetchData<Record<string, unknown>>(`${KNOWLEDGE_BASE}/sync/status`);
    return (data?.objectTypes as SyncStatus[]) || [];
  } catch {
    return [];
  }
}

export async function triggerFullSync() {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/trigger`, { method: 'POST' });
}

export async function triggerObjectSync(objectType: string) {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/object/${encodeURIComponent(objectType)}`, { method: 'POST' });
}

export async function fetchSyncLogs(): Promise<SyncLog[]> {
  try {
    const data = await apiFetchData<SyncLog[]>(`${KNOWLEDGE_BASE}/sync/logs`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function fetchOntologyMappings() {
  try {
    return await apiFetchData('/api/v1/ontology/mappings');
  } catch {
    return { mappings: [] };
  }
}

export async function saveOntologyMappings(mappings: unknown) {
  try {
    return await apiFetchData('/api/v1/ontology/mappings', {
      method: 'POST',
      body: JSON.stringify(mappings),
    });
  } catch {
    return null;
  }
}

export async function exportOntology() {
  try {
    return await apiFetchData('/api/v1/ontology/export');
  } catch {
    return '';
  }
}

export async function fetchIndexStatus() {
  try {
    return await apiFetchData(`${KNOWLEDGE_BASE}/index-status`);
  } catch {
    return { nodeCount: 0, relationshipCount: 0 };
  }
}

export async function syncVectors(config: Record<string, unknown>) {
  try {
    return await apiFetchData('/api/v1/knowledge/sync', {
      method: 'POST',
      body: JSON.stringify(config),
    });
  } catch {
    return { status: 'queued' };
  }
}

export async function runRAGQuery(req: RagRequest): Promise<RagResult> {
  try {
    const data = await apiFetchData<RagResult>('/api/v1/knowledge/rag', {
      method: 'POST',
      body: JSON.stringify(req),
    });
    return data || { answer: '', sources: [], tokensUsed: 0 };
  } catch {
    return { answer: '', sources: [], tokensUsed: 0 };
  }
}

export async function runKnowledgeQuery(query: string) {
  try {
    return await apiFetchData('/api/v1/knowledge/query', {
      method: 'POST',
      body: JSON.stringify({ query }),
    });
  } catch {
    return [];
  }
}

export async function getSettings(): Promise<KnowledgeSettings> {
  try {
    const data = await apiFetchData<KnowledgeSettings>(`${KNOWLEDGE_BASE}/settings`);
    return data || {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  } catch {
    return {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  }
}

export async function updateSettings(data: Partial<KnowledgeSettings>) {
  return apiFetchData(`${KNOWLEDGE_BASE}/settings`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function fetchGlossaryTerms(params?: { domain?: string; status?: string }): Promise<GlossaryTerm[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  const query = qs.toString();
  const url = query ? `${GLOSSARY_BASE}/terms?${query}` : `${GLOSSARY_BASE}/terms`;
  try {
    const data = await apiFetchData<GlossaryTerm[]>(url);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function createGlossaryTerm(data: { name: string; definition: string; domain?: string }) {
  return apiFetchData(`${GLOSSARY_BASE}/terms`, { method: 'POST', body: JSON.stringify(data) });
}

export async function updateGlossaryTerm(id: string, data: Record<string, unknown>) {
  return apiFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export async function deleteGlossaryTerm(id: string) {
  return apiFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'DELETE' });
}

export async function classifyAsset(assetId: string) {
  return apiFetchData(`${CATALOG_BASE}/assets/${encodeURIComponent(assetId)}/auto-classify`, { method: 'POST' });
}

export async function fetchCognitiveConfig() {
  return apiFetchData(`${COGNITIVE_BASE}/config`);
}

export async function updateCognitiveConfig(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData(`${COGNITIVE_BASE}/config`, {
    method: 'PUT',
    body: JSON.stringify(updates),
  });
}

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

export async function fetchIntegrationMetadata(): Promise<{ simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean } } | null> {
  try {
    return await apiFetchData<{ simulationState?: { isSchemaDriftActive?: boolean; isSlaBreachActive?: boolean } }>('/api/v1/integration/metadata');
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

// ── Rule Repository ─────────────────────────────────────

export async function fetchRules(params?: { domain?: string; status?: string; keyword?: string }): Promise<RuleRepository[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  if (params?.keyword) qs.set('keyword', params.keyword);
  const query = qs.toString();
  const url = query ? `${RULES_BASE}?${query}` : RULES_BASE;
  try {
    const data = await apiFetchData<RuleRepository[]>(url);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function createRule(data: Partial<RuleRepository>) {
  return apiFetchData<RuleRepository>(RULES_BASE, { method: 'POST', body: JSON.stringify(data) });
}

export async function updateRule(id: string, data: Partial<RuleRepository>) {
  return apiFetchData<RuleRepository>(`${RULES_BASE}/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export async function deleteRule(id: string) {
  return apiFetchData(`${RULES_BASE}/${id}`, { method: 'DELETE' });
}

export async function fetchRuleVersions(ruleId: string): Promise<RuleVersion[]> {
  try {
    const data = await apiFetchData<RuleVersion[]>(`${RULES_BASE}/${ruleId}/versions`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

// ── PMO-54 — Overview / Dashboard ─────────────────────────────────────────────

// Wave 0 — 向量库概要新增可选字段（后端只加不改：embeddingDim = embedding_vec 维数，docCount = 覆盖文档数）
export interface GraphStats {
  graphNodeCount: number;
  graphEdgeCount: number;
  embeddingCount: number;
  ruleCount: number;
  articleCount?: number;
  docCount?: number;
  complianceRuleCount?: number;
  lastUpdatedAt?: string;
  embeddingDim?: number;
}

export async function fetchGraphStats(): Promise<GraphStats> {
  try {
    const data = await apiFetchData<any>('/api/v1/knowledge/stats');
    return {
      graphNodeCount: data?.graphNodeCount ?? 0,
      graphEdgeCount: data?.graphEdgeCount ?? 0,
      embeddingCount: data?.embeddingCount ?? 0,
      ruleCount: data?.ruleCount ?? data?.complianceRuleCount ?? 0,
      articleCount: data?.articleCount,
      docCount: data?.docCount,
      complianceRuleCount: data?.complianceRuleCount,
      lastUpdatedAt: data?.lastUpdatedAt,
      embeddingDim: data?.embeddingDim,
    };
  } catch {
    return { graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0 };
  }
}

/** Wave 0 数据同步 — 读本体工作台「实体-数据映射契约」（ontology-engine 只读端点） */
export interface EntityMappingItem {
  entityCode?: string;
  resourceName?: string;
  datasetId?: string;
  materialized?: boolean;
  fieldMappings?: unknown[];
}

export async function fetchEntityMappings(): Promise<EntityMappingItem[]> {
  try {
    const data = await apiFetchData<any>('/api/v1/ontology/entity-mappings');
    return Array.isArray(data) ? (data as EntityMappingItem[]) : [];
  } catch {
    return [];
  }
}

// 引擎健康响应（engine → ok/latency/message）
export interface EngineHealthMap {
  [key: string]: { ok: boolean; latencyMs?: number; message?: string };
}

export async function fetchEngineHealth(): Promise<EngineHealthMap> {
  try {
    return await apiFetchData<EngineHealthMap>(`${KB_V1}/health`);
  } catch {
    return {};
  }
}

// ── PMO-54 — Graph build (jobs) ──────────────────────────────────────────────

export async function fetchGraphJobs(): Promise<GraphBuildJob[]> {
  try {
    const data = await apiFetchData<any>(`${KB_V1}/sync/jobs`);
    return Array.isArray(data) ? (data as GraphBuildJob[]) : (data?.data as GraphBuildJob[]) || [];
  } catch {
    return [];
  }
}

export async function previewGraphBuild(params?: { dryRun?: boolean }): Promise<GraphBuildPreview> {
  try {
    return await apiFetchData<GraphBuildPreview>(`${KB_V1}/graph/build/preview?${params?.dryRun ? 'dryRun=true' : ''}`, { method: 'POST' });
  } catch {
    return { create: 0, update: 0, skip: 0 };
  }
}

export async function triggerGraphBuild(payload: Record<string, unknown>): Promise<{ jobId: string }> {
  const data = await apiFetchData<{ jobId: string }>(`${KB_V1}/graph/build`, { method: 'POST', body: JSON.stringify(payload) });
  return data || { jobId: '' };
}

export interface JobPreview {
  create: number;
  update: number;
  skip: number;
  samples?: Array<Record<string, unknown>>;
}

export async function previewGraphJob(jobId: string): Promise<JobPreview> {
  try {
    return await apiFetchData<JobPreview>(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/preview`);
  } catch {
    return { create: 0, update: 0, skip: 0 };
  }
}

export async function rollbackGraphJob(jobId: string) {
  return apiFetchData(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/rollback`, { method: 'POST' });
}

export async function fetchGraphJobLogs(jobId: string): Promise<string[]> {
  try {
    const data = await apiFetchData<string[]>(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/logs`);
    return Array.isArray(data) ? data : [String(data)];
  } catch {
    return [];
  }
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

// ── PMO-54 — Knowledge eval ───────────────────────────────────────────────────

export async function fetchEvalSeeds(): Promise<EvalSeedQuery[]> {
  try {
    const data = await apiFetchData<EvalSeedQuery[]>(`${KB_V1}/eval/seeds`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function uploadEvalSeed(file: File): Promise<{ name: string; count: number }> {
  const fd = new FormData();
  fd.append('file', file);
  const token = localStorage.getItem('token') || '';
  const res = await fetch(`${KB_V1}/eval/seeds/upload`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  });
  if (!res.ok) throw new Error(`seed upload failed ${res.status}`);
  const json = await res.json();
  return json?.data || json;
}

export async function runEval(seedSetName: string): Promise<EvalReport> {
  try {
    const data = await apiFetchData<EvalReport>(`${KB_V1}/eval/run`, {
      method: 'POST',
      body: JSON.stringify({ seedSetName }),
    });
    return data || { reportId: '', seedSetName, printedAt: new Date().toISOString(), recallAt5: 0, mrrAt5: 0, ndcgAt5: 0 };
  } catch (e) {
    // Degraded mode: hint that backend isn't ready; run degraded stub locally
    console.info('runEval backend unavailable — degraded', (e as Error).message);
    const evalDegraded: EvalReport = {
      reportId: `local-${Date.now()}`,
      seedSetName,
      printedAt: new Date().toISOString(),
      recallAt5: 0,
      mrrAt5: 0,
      ndcgAt5: 0,
      degraded: true,
    };
    return evalDegraded;
  }
}

// ── PMO-54 — Lifecycle ────────────────────────────────────────────────────────

export async function fetchLifecycleAssets(): Promise<LifecycleAsset[]> {
  try {
    const data = await apiFetchData<any>(`${KB_V1}/assets`);
    const items = Array.isArray(data) ? data : (data?.data as any[]) || [];
    return (items as any[]).map((a: any) => ({
      id: String(a.id ?? a.assetId ?? ''),
      name: String(a.name ?? a.assetName ?? a.id ?? ''),
      type: String(a.type ?? a.assetType ?? 'unknown'),
      state: (a.state ?? a.status ?? 'draft') as LifecycleState,
      updatedAt: String(a.updatedAt ?? a.updateTime ?? new Date().toISOString()),
      updatedBy: a.updatedBy,
    })).filter(a => a.id);
  } catch {
    return [];
  }
}

export async function lifecycleTransition(assetId: string, next: LifecycleState): Promise<LifecycleAsset> {
  return apiFetchData<LifecycleAsset>(`${KB_V1}/assets/${encodeURIComponent(assetId)}/transition`, {
    method: 'POST',
    body: JSON.stringify({ state: next }),
  });
}

export async function fetchLifecycleAudit(): Promise<LifecycleAuditEntry[]> {
  try {
    const data = await apiFetchData<LifecycleAuditEntry[]>(`${KB_V1}/lifecycle/audit`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

// ── PMO-54 — Engine config ─────────────────────────────────────────────────────

export async function fetchEngineConfig(scope: EngineConfigScope): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  try {
    const data = await apiFetchData<EngineConfig>(`${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`);
    return { config: data || {}, version: 1, updatedAt: new Date().toISOString() };
  } catch {
    // fallback: cognitive group + local knowledge_engine group
    try {
      const data = await apiFetchData<EngineConfig>('/api/v1/cognitive/config');
      const result: EngineConfig = {};
      for (const [k, v] of Object.entries(data || {})) result[`knowledge.${scope}.${k}`] = String(v);
      return { config: result, version: 1, updatedAt: new Date().toISOString() };
    } catch {
      return { config: {}, version: 1, updatedAt: new Date().toISOString() };
    }
  }
}

export async function saveEngineConfig(scope: EngineConfigScope, cfg: Record<string, unknown>): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  // Try PMO-54-specific endpoint; fall back to sys_config group=knowledge_engine
  const asyncRp = apiFetchData<{ config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null>(`${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`, {
    method: 'PUT',
    body: JSON.stringify(cfg),
  });

  const settle = async (): Promise<{ config: EngineConfig; version: number; updatedAt: string }> => {
    const primary = await asyncRp.catch(function (): { config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null { return null; });
    if (primary && typeof primary === 'object' && 'config' in primary) return primary as { config: EngineConfig; version: number; updatedAt: string };
    if (primary && typeof primary === 'object' && 'config' in (primary as Record<string, unknown>)) {
      const p = primary as Record<string, unknown>;
      return { config: (p.config as EngineConfig) || (cfg as EngineConfig), version: Number(p.version ?? 1), updatedAt: String(p.updatedAt ?? new Date().toISOString()) };
    }
    if (primary) return { config: (primary as EngineConfig) || (cfg as EngineConfig), version: 1, updatedAt: new Date().toISOString() };
    // 回退到 cognitive/config
    const updates = Object.entries(cfg).map(([config_key, config_value]) => ({ config_key: `knowledge_engine.${scope}.${config_key}`, config_value }));
    const fbResp = await apiFetchData<Record<string, unknown> | null>('/api/v1/cognitive/config', { method: 'PUT', body: JSON.stringify(updates) });
    return { config: (fbResp ? fbResp as EngineConfig : cfg) as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  };
  try {
    return await settle();
  } catch {
    return { config: cfg as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  }
}

// ── PMO-54 — Ingest / ETL workspace ───────────────────────────────────────────

export interface DataWorkbenchSource {
  dsId: string;
  name: string;
  type: string;
  status: 'connected' | 'disconnected' | 'flaky';
  records?: string;
  pipelines?: Array<{ pipelineId: string; name: string }>;
}

export async function fetchDataWorkbenchSources(): Promise<DataWorkbenchSource[]> {
  try {
    const data = await apiFetchData<any>('/api/integration/metadata');
    const items = Array.isArray(data) ? data : (data?.data as any[]) || data?.sources || [];
    return (items as any[]).map((s: any) => ({
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

export async function fetchMetadataDrift(sample?: boolean): Promise<{
  schemaDelta: Array<Record<string, unknown>>;
  rows?: Array<Record<string, unknown>>;
  lineage?: { nodes: Array<Record<string, unknown>>; links: Array<Record<string, unknown>> };
}> {
  try {
    const q = sample ? '?sample=true' : '';
    const data = await apiFetchData<any>(`/api/v1/integration/metadata/drift${q}`);
    return {
      schemaDelta: data?.schemaDelta || data?.fields || [],
      rows: data?.rows || data?.samples,
      lineage: data?.lineage,
    };
  } catch {
    return { schemaDelta: [] };
  }
}

// ── Wave 3 C1 — T8 本体树 / 定时抽取 / 日志 / 状态 ─────────────────────────────

/** 拉取本体三级树（domain → ontology → entityCodes）；空树或失败时兜底返回 [] 不抛 */
export async function fetchOntologyTree(): Promise<OntologyTreeVo[]> {
  try {
    const data = await apiFetchData<OntologyTreeVo[]>(`${KB_V1}/extract/ontology-tree`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 获取定时抽取任务列表 */
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
export async function fetchExtractLogs(jobId: number): Promise<ExtractLogEntry[]> {
  try {
    const data = await apiFetchData<ExtractLogEntry[]>(`${KB_V1}/extract/structured/logs/${jobId}`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

/** 获取抽取日志导出 Blob（POST body 触发下载，供 T10 组件 window.open / a.download 触发） */
export async function fetchExportLogBody(jobId: number): Promise<Blob> {
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
  jobId?: number;
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
  return data || { taskId: '', jobId: 0, status: 'PENDING' };
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

/** 定时抽取任务行（后端 snake_case 形态） */
export interface ScheduledExtractRow {
  id: number;
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

/** Wave 3 C1 api 导出（追加，不影响 knowledgeApi 对象内既有成员） */
export const wave3c1C1Api = {
  fetchOntologyTree,
  fetchExtractJobs,
  fetchExtractJobStatus,
  fetchScheduledExtracts,
  createScheduledExtractRow,
  updateScheduledExtractRow,
  deleteScheduledExtract,
  fetchExtractLogs,
  exportExtractLog,
};

// ── PMO-54 — Consolidated knowledgeApi export (extended with above) ──────────

export const knowledgeApi = {
  fetchGraph,
  fetchNode,
  searchKnowledge,
  findPath,
  graphSearch,
  graphPath,
  fetchNeighbors,
  createNode,
  createEdge,
  getDataSource,
  fetchSyncStatuses,
  triggerFullSync,
  triggerObjectSync,
  fetchSyncLogs,
  fetchOntologyMappings,
  saveOntologyMappings,
  exportOntology,
  fetchIndexStatus,
  syncVectors,
  runRAGQuery,
  runRAGQuerySSE,
  runKnowledgeQuery,
  getSettings,
  updateSettings,
  fetchGlossaryTerms,
  createGlossaryTerm,
  updateGlossaryTerm,
  deleteGlossaryTerm,
  classifyAsset,
  fetchCognitiveConfig,
  updateCognitiveConfig,
  fetchLineageImpact,
  parseLineage,
  fetchIntegrationMetadata,
  fetchIntegrationLogs,
  toggleSimulationDrift,
  fetchRules,
  createRule,
  updateRule,
  deleteRule,
  fetchRuleVersions,
  // PMO-54
  fetchGraphStats,
  fetchEntityMappings,
  fetchEngineHealth,
  fetchGraphJobs,
  previewGraphBuild,
  triggerGraphBuild,
  previewGraphJob,
  rollbackGraphJob,
  fetchGraphJobLogs,
  fetchImportQueue,
  addImportToQueue,
  retryImport,
  uploadDocumentChunked,
  fetchExtractCandidates,
  fetchUploadEnabled,
  fetchTriggerStructuredExtract,
  fetchStructuredJobs,
  fetchStructuredJobDetail,
  fetchExtractEngineConfig,
  fetchExtractCandidateFiles,
  fetchEvalSeeds,
  uploadEvalSeed,
  runEval,
  fetchLifecycleAssets,
  lifecycleTransition,
  fetchLifecycleAudit,
  fetchEngineConfig,
  saveEngineConfig,
  fetchDataWorkbenchSources,
  fetchMetadataDrift,
  // Wave 3 C1 — T8 本体树 / 定时抽取 / 日志 / 状态
  fetchOntologyTree,
  fetchScheduledExtracts,
  createScheduledExtract,
  updateScheduledExtract,
  deleteScheduledExtract,
  fetchExtractLogs,
  fetchExportLogBody,
  triggerStructuredExtract,
  fetchExtractStatus,
};
