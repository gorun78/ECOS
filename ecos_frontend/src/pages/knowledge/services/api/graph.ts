// B.9 / K-55 — Tab「graph 知识图谱」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { GRAPH_BASE, KNOWLEDGE_BASE, KB_V1 } from './base';
import type { SyncStatus, SyncLog, GraphBuildJob, GraphBuildPreview } from '../../typesAndConstants';

/**
 * 拉取知识图谱。
 * PMO-C T3: 追加可选 categoryIds → 后端按分类白名单过滤（null/[] = 全量，回归保证）。
 */
export async function fetchGraph(domain?: string, categoryIds?: string[]) {
  const params = new URLSearchParams();
  if (domain) params.set('domain', domain);
  if (categoryIds && categoryIds.length > 0) {
    categoryIds.forEach(cid => params.append('categoryIds', cid));
  }
  const q = params.toString() ? `?${params.toString()}` : '';
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

// PMO-26 T1: Graph full-text search & path
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

// ── PMO-54 — Graph build (jobs) ──────────────────────────────────────────────

// /sync/jobs 端点兼容两种形态：裸数组 或 { data: [...] } 包装（JSON 边界本地收窄）
interface GraphJobsResponse {
  data?: GraphBuildJob[];
}

export async function fetchGraphJobs(): Promise<GraphBuildJob[]> {
  try {
    const data = await apiFetchData<GraphBuildJob[] | GraphJobsResponse | null>(`${KB_V1}/sync/jobs`);
    return Array.isArray(data) ? data : data?.data || [];
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
