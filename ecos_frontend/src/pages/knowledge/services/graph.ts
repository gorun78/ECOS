/**
 * graph.ts — 知识图谱 契约（B.9 / K-55 拆分）。
 *
 * 覆盖 Tab：`graph`
 * base 单源：GRAPH_BASE = /api/v1/knowledge
 *
 * K-55 纠正（关键新增）：
 *   - `fetchGraph` 追加 `limit` 参数（缺省 200、上限 2000），
 *     不再全量拉取图谱（后端需支持 limit；缺省 200 保护性能）。
 *   - 走 `kbFetchData` → 5xx / ECOS-KB-* 抛 `KbApiError`，
 *     GraphPage catch 分支据此渲染错误态（而非静默/空态）。
 */
import { kbFetchData } from './kbFetch';

const GRAPH_BASE = '/api/v1/knowledge';

/** K-55：图谱拉取默认/上限。默认 200，硬上限 2000（防误用）。 */
export const GRAPH_FETCH_DEFAULT_LIMIT = 200;
export const GRAPH_FETCH_MAX_LIMIT = 2000;

export interface FetchGraphOptions {
  domain?: string;
  categoryIds?: string[];
  /** 节点数量上限（K-55）；缺省 200，硬上限 2000。 */
  limit?: number;
}

/**
 * 拉取知识图谱。
 * PMO-C T3: 追加 categoryIds 后端白名单下沉过滤（null/[] = 全量）。
 * K-55: 追加 limit，缺省 200 / 上限 2000 —— 保留 domain + categoryIds 语义不变，
 *        仅新增 limit 参数，不改动既有请求路径/handler。
 */
export async function fetchGraph(
  domain?: string,
  categoryIds?: string[],
  limit?: number,
  options?: FetchGraphOptions,
) {
  const params = new URLSearchParams();
  const useDomain = options?.domain ?? domain;
  const useCategories = options?.categoryIds ?? categoryIds;
  if (useDomain) params.set('domain', useDomain);
  if (useCategories && useCategories.length > 0) {
    useCategories.forEach((cid) => params.append('categoryIds', cid));
  }
  const clampedLimit = clampLimit(limit ?? options?.limit ?? GRAPH_FETCH_DEFAULT_LIMIT);
  params.set('limit', String(clampedLimit));
  const q = `?${params.toString()}`;
  // 走 kbFetchData：5xx / ECOS-KB-* → KbApiError；其余兼容 apiFetchData 语义
  return kbFetchData<{ nodes: unknown[]; edges: unknown[] }>(`${GRAPH_BASE}/graph${q}`);
}

/** 硬上限保护：≤0 走默认；> MAX 走 MAX。 */
export function clampLimit(limit: number): number {
  if (!Number.isFinite(limit) || limit <= 0) return GRAPH_FETCH_DEFAULT_LIMIT;
  return Math.min(Math.floor(limit), GRAPH_FETCH_MAX_LIMIT);
}

export async function fetchNode(id: string) {
  return kbFetchData(`${GRAPH_BASE}/nodes/${encodeURIComponent(id)}`);
}

export async function searchKnowledge(query: string) {
  return kbFetchData(`${GRAPH_BASE}/search?q=${encodeURIComponent(query)}`);
}

export async function findPath(source: string, target: string) {
  return kbFetchData(`${GRAPH_BASE}/path?s=${encodeURIComponent(source)}&t=${encodeURIComponent(target)}`);
}

// ── PMO-26 T1: Graph full-text search & path ──
export async function graphSearch(query: string) {
  return kbFetchData(`/api/v1/knowledge/graph/search?q=${encodeURIComponent(query)}`);
}

export async function graphPath(source: string, target: string) {
  return kbFetchData('/api/v1/knowledge/graph/path', {
    method: 'POST',
    body: JSON.stringify({ source, target }),
  });
}

export async function fetchNeighbors(id: string, depth = 1) {
  return kbFetchData(`${GRAPH_BASE}/neighbors/${encodeURIComponent(id)}?d=${depth}`);
}

export async function createNode(data: Record<string, unknown>) {
  return kbFetchData(`${GRAPH_BASE}/nodes`, { method: 'POST', body: JSON.stringify(data) });
}

export async function createEdge(data: Record<string, unknown>) {
  return kbFetchData(`${GRAPH_BASE}/edges`, { method: 'POST', body: JSON.stringify(data) });
}

export async function getDataSource() {
  return kbFetchData(`${GRAPH_BASE}/source`);
}
