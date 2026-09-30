/**
 * 本体工作台 REST 收口 (H6-T2) — 自 ontology-workbench 组件与 BusinessObjectExplorer 迁入。
 * 原语义保持（含各自的错误消息与解包方式）。
 */
import { getAuthToken, authHeaders } from "./auth";

/** GET /api/v1/knowledge/ecos-graph — 可选 Bearer；非 2xx 抛 Error(String(status)) */
export async function fetchEcosGraphJson(): Promise<any> {
  const t = getAuthToken();
  const headers: Record<string, string> = {};
  if (t) headers["Authorization"] = "Bearer " + t;
  const r = await fetch("/api/v1/knowledge/ecos-graph", { headers });
  if (!r.ok) throw new Error(String(r.status));
  return r.json();
}

/** 本体域 CRUD JSON 助手（原 OntologyDomainPanel 本地 apiFetch）。 */
export async function ontologyDomainApiFetch(path: string, options?: RequestInit): Promise<any> {
  const token = getAuthToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(path, { ...options, headers });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  return json.data ?? json;
}

/** GET /api/v1/ontology/data — Bearer + JSON，返回整包（normalizeObjectTypes 留在页面层）。 */
export async function fetchOntologyDataJson(): Promise<any> {
  const res = await fetch('/api/v1/ontology/data', { headers: authHeaders() });
  return res.json();
}
