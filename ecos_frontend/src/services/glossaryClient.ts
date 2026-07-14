/**
 * 术语库客户端 — 本体工作台术语集成服务层
 *
 * 封装术语查询接口，供 GlossaryBindingPanel / TermSearchModal 使用。
 * 使用 apiFetchData 统一处理后端 ApiResponse<T> 包裹格式。
 *
 * @license Apache-2.0
 */

import { apiFetchData } from "../api";
import type { GlossaryTerm, GlossaryFilter } from "../types/workbench";

const GLOSSARY_BASE = "/api/glossary";

/**
 * 获取术语库术语列表
 *
 * @param filters 可选的过滤条件 (domain, status, keyword)
 * @returns 术语列表
 */
export async function fetchTerms(
  filters?: GlossaryFilter
): Promise<GlossaryTerm[]> {
  const params = new URLSearchParams();
  if (filters?.domain) params.set("domain", filters.domain);
  if (filters?.status) params.set("status", filters.status);
  if (filters?.keyword) params.set("keyword", filters.keyword);

  const query = params.toString();
  const url = query ? `${GLOSSARY_BASE}/terms?${query}` : `${GLOSSARY_BASE}/terms`;

  const data = await apiFetchData<GlossaryTerm[]>(url);
  return Array.isArray(data) ? data : [];
}
