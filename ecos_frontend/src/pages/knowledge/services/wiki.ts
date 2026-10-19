/**
 * wiki.ts — 知识导航 / 治理词条 / 检索设置 契约（B.9 / F04-17）。
 *
 * 覆盖 Tab：`wiki` + `govern/retrieval`（RAG 调试）
 * base 单源：
 *   - KNOWLEDGE_BASE = /api/v1/knowledge
 *   - GLOSSARY_BASE  = /api/v1/ontology/glossary
 *   - RULES_BASE     = /api/v1/knowledge/compliance-rules
 *
 * 错误态语义（F04-03）：
 *   - wiki 主数据源（nav taxon / settings）走 `kbFetchData` → 5xx 或 `ECOS-KB-*`
 *     抛 `KbApiError`，WikiPage catch 分支据此渲染「导航表缺失 / 不可用」故障态
 *     （K-55 / ECOS-KB-050），不再静默吞 500 → 空态。
 *   - 一般 RAG 查询走 `apiFetchData`（Tab 侧 `runRAGQuerySSE` 已有降级路径）。
 */
import { apiFetchData } from '../../../api';
import { kbFetchData } from './kbFetch';
import { API_BASE, encodeQueryString } from '../../../services/httpClient';
import type {
  NavProductQuery,
  NavProductPage,
  NavCategoryVO,
} from '../../../services/knowledgeNavApi';
import type {
  KnowledgeSettings,
  GlossaryTerm,
  RuleRepository,
  RuleVersion,
  RagRequest,
  RagResult,
} from '../typesAndConstants';

const KNOWLEDGE_BASE = `${API_BASE}/v1/knowledge`;
const KB_V1 = KNOWLEDGE_BASE;
const GLOSSARY_BASE = `${API_BASE}/v1/ontology/glossary`;
const RULES_BASE = `${API_BASE}/v1/knowledge/compliance-rules`;

// ── KB Workbench settings（检索参数：vector/chunk/overlap/maxRetrieval） ─────────

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

// ── Glossary（术语表 / nav taxon，K5 知识导航主数据源） ─────────────────────────

/** k5 术语表；5xx / ECOS-KB-*（含 ECOS-KB-050「nav 表不存在」）抛 KbApiError */
export async function kbFetchGlossaryTerms(
  params?: { domain?: string; status?: string },
  limit = 200,
): Promise<GlossaryTerm[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  qs.set('limit', String(limit));
  const url = `${GLOSSARY_BASE}/terms?${qs.toString()}`;
  return kbFetchData<GlossaryTerm[]>(url);
}

/** 通用 lexicon CRUD（拿 KbApiError 供 Tab 判定 error/empty 分治） */
export async function kbCreateGlossaryTerm(data: { name: string; definition: string; domain?: string }) {
  return kbFetchData(`${GLOSSARY_BASE}/terms`, { method: 'POST', body: JSON.stringify(data) });
}

export async function kbUpdateGlossaryTerm(id: string, data: Record<string, unknown>) {
  return kbFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'PUT', body: JSON.stringify(data) });
}

export async function kbDeleteGlossaryTerm(id: string) {
  return kbFetchData(`${GLOSSARY_BASE}/terms/${id}`, { method: 'DELETE' });
}

// 兼容：保留旧 silent 版（旧 Tab 不 catch KbApiError 时不会崩）
export async function fetchGlossaryTerms(
  params?: { domain?: string; status?: string },
): Promise<GlossaryTerm[]> {
  try {
    const data = await apiFetchData<GlossaryTerm[]>(
      (() => {
        const qs = new URLSearchParams();
        if (params?.domain) qs.set('domain', params.domain);
        if (params?.status) qs.set('status', params.status);
        const q = qs.toString();
        return q ? `${GLOSSARY_BASE}/terms?${q}` : `${GLOSSARY_BASE}/terms`;
      })(),
    );
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

// ── RAG / retrieval（govern/retrieval Tab） ─────────────────────────────────────

export async function runRAGQuery(req: RagRequest): Promise<RagResult> {
  try {
    const data = await apiFetchData<RagResult>(`${API_BASE}/v1/knowledge/rag`, {
      method: 'POST',
      body: JSON.stringify(req),
    });
    return data || { answer: '', sources: [], tokensUsed: 0 };
  } catch {
    return { answer: '', sources: [], tokensUsed: 0 };
  }
}

/**
 * 显式 Rpc 版本 — 供 govern/retrieval Tab 走 KbApiError 判定 error 态。
 * `RagResult.vectorDegraded?` 由 govern/retrieval Tab 展示「向量降级」横幅。
 */
export async function kbRunRAGQuery(req: RagRequest): Promise<RagResult> {
  return kbFetchData<RagResult>(`${API_BASE}/v1/knowledge/rag`, {
    method: 'POST',
    body: JSON.stringify(req),
  });
}

export async function runKnowledgeQuery(query: string) {
  try {
    return await apiFetchData(`${API_BASE}/v1/knowledge/query`, {
      method: 'POST',
      body: JSON.stringify({ query }),
    });
  } catch {
    return [];
  }
}

// ── RuleRepository（K6 治理规则库，兼容旧 import surface） ─────────────────────

/**
 * KbApiError 版知识导航资产列表（F04-03）。
 * 走 kbFetchData → 5xx / ECOS-KB-050「nav 表不存在」抛 KbApiError，
 * WikiPage 据此渲染显式故障态（而非「暂无数据」空态）。
 */
export async function kbFetchNavProducts(query: NavProductQuery): Promise<NavProductPage> {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.domain) params.set('domain', query.domain);
  if (query.categoryIds && query.categoryIds.length > 0) {
    query.categoryIds.forEach((cid) => params.append('categoryIds', cid));
  }
  if (query.tags && query.tags.length > 0) {
    query.tags.forEach((t) => params.append('tags', t));
  }
  params.set('pageNum', String(query.pageNum ?? 1));
  params.set('pageSize', String(query.pageSize ?? 20));
  const url = encodeQueryString(`${API_BASE}/v1/knowledge/nav/products?${params.toString()}`);
  return kbFetchData<NavProductPage>(url);
}

/** KbApiError 版知识导航分类树（F04-03，供 wiki 分类树懒加载显式故障态） */
export async function kbFetchNavCategories(domain: string, parentId?: string | null, search?: string): Promise<NavCategoryVO[]> {
  const params = new URLSearchParams();
  params.set('domain', domain);
  if (parentId) params.set('parentId', parentId);
  if (search) params.set('search', search);
  return kbFetchData<NavCategoryVO[]>(`${API_BASE}/v1/knowledge/nav/categories?${params.toString()}`);
}

export async function fetchRules(
  params?: { domain?: string; status?: string; keyword?: string },
): Promise<RuleRepository[]> {
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

export async function kbFetchRules(
  params?: { domain?: string; status?: string; keyword?: string },
): Promise<RuleRepository[]> {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set('domain', params.domain);
  if (params?.status) qs.set('status', params.status);
  if (params?.keyword) qs.set('keyword', params.keyword);
  const query = qs.toString();
  const url = query ? `${RULES_BASE}?${query}` : RULES_BASE;
  return kbFetchData<RuleRepository[]>(url);
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

export async function kbFetchRuleVersions(ruleId: string): Promise<RuleVersion[]> {
  return kbFetchData<RuleVersion[]>(`${RULES_BASE}/${ruleId}/versions`);
}
