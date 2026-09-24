/**
 * PMO-B T2 — 知识导航前端 API 服务（kb-nav 端点封装）。
 *
 * 后端契约（kb-engine-impl / NavTaxonController，基线 /api/v1/knowledge/nav/*）：
 * - listings:  GET /api/v1/knowledge/nav/categories?domain=&parentId=&search=
 *              GET /api/v1/knowledge/nav/tags?domain=
 *              GET /api/v1/knowledge/nav/products?category=&tag=&keyword=&pageNum=&pageSize=
 * - mutations: POST/PUT/DELETE /nav/categories, /nav/categories/move, /nav/tags
 *              POST/PUT/DELETE /nav/products/{articleId}/categories|tags
 *              POST /nav/products/{articleId}/undo?scope=category|tag
 * - LLM:       GET /nav/recommend/{articleId}
 *
 * 与 `pages/knowledge/services/knowledgeApi.ts` 的 `apiFetchData` 模式一致。
 * 路由前缀 `/api` 走 gateway :8080。
 */
import { apiFetchData } from '../api';

const NAV_BASE = '/api/v1/knowledge/nav';

/** 目录节点（与 kb_nav_category 列对齐） */
export interface NavCategoryVO {
  id: string;
  domain: string;
  parentId?: string | null;
  name: string;
  path?: string;
  level: number;
  sortOrder: number;
  childCount: number;
  articleCount: number;
  parentName?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 标签字典（与 kb_nav_tag 列对齐） */
export interface NavTagVO {
  id: string;
  domain: string;
  tagName: string;
  useCount: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 子树统计 */
export interface NavNodeStatsVO {
  nodeId: string;
  scope: 'category' | 'tag';
  articleCount: number;
  tagCount: number;
  tagNames: string[];
}

/** 资产行（NavProductItemVO） */
export interface NavProductItemVO {
  id: string;
  title: string;
  source?: string;
  domain?: string;
  category?: string;
  status?: string;
  updatedAt?: string;
  matchedCategoryIds?: string[];
  matchedTags?: string[];
}

/** 资产分页响应（GET /products 强类型 body） */
export interface NavProductPage {
  list: NavProductItemVO[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/** LLM 候选（仅建议） */
export interface NavRecommendVO {
  tags: string[];
  suggestedCategoryId?: string | null;
  suggestedCategoryPath?: string | null;
  reason?: string;
  generatedAt?: string;
}

/** 资产列表查询表单 */
export interface NavProductQuery {
  keyword?: string;
  domain?: string;
  categoryIds?: string[];
  tags?: string[];
  pageNum?: number;
  pageSize?: number;
}

/** 目录 / 标签 Save 请求（PMO-A 既有契约 — 强类型，禁 Map） */
export interface NavCategorySaveDTO {
  domain?: string;
  parentId?: string | null;
  name: string;
  sortOrder?: number;
}

/** 目录树拉取 */
export async function fetchNavCategories(
  domain: string,
  parentId?: string | null,
  search?: string,
): Promise<NavCategoryVO[]> {
  const params = new URLSearchParams();
  params.set('domain', domain);
  if (parentId) params.set('parentId', parentId);
  if (search) params.set('search', search);
  return apiFetchData<NavCategoryVO[]>(`${NAV_BASE}/categories?${params.toString()}`);
}

/**
 * PMO-C T2: 列出当前 nav 体系下所有 distinct domain（目录 ∪ 标签，升序）。
 * 供多 domain 切换下拉使用；后端无数据时回退为 ['default']。
 */
export async function fetchNavDomains(): Promise<string[]> {
  return apiFetchData<string[]>(`${NAV_BASE}/domains`);
}

/** 创建目录（一级 parentId=null，二级/三级指定 parentId） */
export async function createNavCategory(dto: NavCategorySaveDTO): Promise<NavCategoryVO> {
  return apiFetchData<NavCategoryVO>(`${NAV_BASE}/categories`, {
    method: 'POST',
    body: JSON.stringify(dto),
  });
}

/** 更新目录（仅 name / sortOrder） */
export async function updateNavCategory(
  id: string,
  dto: NavCategorySaveDTO,
): Promise<NavCategoryVO> {
  return apiFetchData<NavCategoryVO>(`${NAV_BASE}/categories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(dto),
  });
}

/** 逻辑删除目录（有子节点时后端 400） */
export async function deleteNavCategory(id: string): Promise<void> {
  return apiFetchData<void>(`${NAV_BASE}/categories/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

/** 批量移动目录到目标父节点（targetParentId=null = 移回根） */
export async function moveNavCategories(
  ids: string[],
  targetParentId: string | null,
): Promise<NavCategoryVO[]> {
  return apiFetchData<NavCategoryVO[]>(`${NAV_BASE}/categories/move`, {
    method: 'POST',
    body: JSON.stringify({ ids, targetParentId }),
  });
}

/** 标签 — 列表 */
export async function fetchNavTags(domain: string): Promise<NavTagVO[]> {
  return apiFetchData<NavTagVO[]>(`${NAV_BASE}/tags?domain=${encodeURIComponent(domain)}`);
}

/** 标签 — 创建（同 domain 下名唯一，重名 400） */
export async function createNavTag(domain: string, tagName: string): Promise<NavTagVO> {
  return apiFetchData<NavTagVO>(
    `${NAV_BASE}/tags?domain=${encodeURIComponent(domain)}&tagName=${encodeURIComponent(tagName)}`,
    { method: 'POST' },
  );
}

/** 标签 — 逻辑删除 */
export async function deleteNavTag(tagId: string): Promise<void> {
  return apiFetchData<void>(`${NAV_BASE}/tags/${encodeURIComponent(tagId)}`, {
    method: 'DELETE',
  });
}

/** 资产分页列表（强类型） */
export async function fetchNavProducts(query: NavProductQuery): Promise<NavProductPage> {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.domain) params.set('domain', query.domain);
  if (query.categoryIds && query.categoryIds.length > 0) {
    query.categoryIds.forEach(cid => params.append('categoryIds', cid));
  }
  if (query.tags && query.tags.length > 0) {
    query.tags.forEach(t => params.append('tags', t));
  }
  params.set('pageNum', String(query.pageNum ?? 1));
  params.set('pageSize', String(query.pageSize ?? 20));
  return apiFetchData<NavProductPage>(`${NAV_BASE}/products?${params.toString()}`);
}

/** 替换文章目录归属（单选/多选取并打平） */
export async function setArticleCategories(
  articleId: string,
  categoryIds: string[],
): Promise<{ mode: string; count: number }> {
  return apiFetchData<{ mode: string; count: number }>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/categories`,
    { method: 'PUT', body: JSON.stringify(categoryIds) },
  );
}

/** 追加文章目录归属（UNION） */
export async function addArticleCategories(
  articleId: string,
  categoryIds: string[],
): Promise<{ mode: string; count: number }> {
  return apiFetchData<{ mode: string; count: number }>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/categories`,
    { method: 'POST', body: JSON.stringify(categoryIds) },
  );
}

/** 删除文章目录归属中的指定项 */
export async function removeArticleCategories(
  articleId: string,
  categoryIds: string[],
): Promise<{ mode: string; count: number }> {
  const qs = new URLSearchParams();
  (categoryIds || []).forEach(cid => qs.append('ids', cid));
  const tail = qs.toString() ? `?${qs.toString()}` : '';
  return apiFetchData<{ mode: string; count: number }>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/categories${tail}`,
    { method: 'DELETE' },
  );
}

/** 替换文章标签（按名称，PMO-A 既有契约） */
export async function setArticleTags(
  articleId: string,
  tagNames: string[],
): Promise<{ mode: string; count: number }> {
  return apiFetchData<{ mode: string; count: number }>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/tags`,
    { method: 'PUT', body: JSON.stringify(tagNames) },
  );
}

/** 追加文章标签（UNION） */
export async function addArticleTags(
  articleId: string,
  tagNames: string[],
): Promise<{ mode: string; count: number }> {
  return apiFetchData<{ mode: string; count: number }>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/tags`,
    { method: 'POST', body: JSON.stringify(tagNames) },
  );
}

/**
 * PMO-D Batch 1（F10）— 知识资产批量状态接口。
 *
 * 后端契约（KnowledgeArticleController GET /api/v1/knowledge/assets/status?ids=）：
 * - 入参：`ids` — 逗号分隔的文章 ID，单次 ≤ 100
 * - 返回：`{ items: AssetStatusItem[] }`（强类型 VO 非 Map，铁律 §2.1）
 * - 双引擎并行批查（graph 状态 = Neo4j 存在性；vector 状态 = pgvector 状态）
 *
 * 前端 Policy：超过 100 ids 时由调用方（AssetListPage）chunk = 100 并发请求，
 * 此函数单次 ids ≤ 100，否则报错（避免静默截断）。
 */
export interface AssetStatusItem {
  articleId: string;
  /** 是否已入图谱（Neo4j / graph_node） */
  graph: boolean;
  /** 是否已入向量索引（pgvector 且 is_deleted=0） */
  vector: boolean;
}

/**
 * 批量查询知识资产的图谱 / 向量双写状态。
 *
 * @param ids 资产 ID 列表（单次 ≤ 100，超过 400 由调用方分片；本函数做最后的安全检查直接拒绝）
 * @returns `AssetStatusItem[]`；后端无命中时返回 items=[] 或空数组
 */
export async function fetchAssetStatuses(ids: string[]): Promise<AssetStatusItem[]> {
  if (!ids || ids.length === 0) return [];
  if (ids.length > 100) {
    throw new Error('fetchAssetStatuses: ids must be ≤ 100 per batch (caller must chunk)');
  }
  const qs = `?ids=${encodeURIComponent(ids.join(','))}`;
  const data = await apiFetchData<{ items?: AssetStatusItem[] } | AssetStatusItem[]>(
    `/api/v1/knowledge/assets/status${qs}`,
  );
  if (Array.isArray(data)) return data;
  if (data && Array.isArray(data.items)) return data.items;
  return [];
}

/** Undo — 删除文章在某 scope（category / tag）下的全部 rel 行 */
export async function undoArticle(articleId: string, scope: 'category' | 'tag'): Promise<Record<string, string>> {
  return apiFetchData<Record<string, string>>(
    `${NAV_BASE}/products/${encodeURIComponent(articleId)}/undo?scope=${scope}`,
    { method: 'POST' },
  );
}

/** LLM 候选 — 仅建议，不落库（不可用 / 异常时后端回空 tags + reason） */
export async function recommendArticle(articleId: string): Promise<NavRecommendVO> {
  return apiFetchData<NavRecommendVO>(
    `${NAV_BASE}/recommend/${encodeURIComponent(articleId)}`,
  );
}
