/**
 * 本体工作台 REST 收口 (H6-T2) — 自 ontology-workbench 组件与 BusinessObjectExplorer 迁入。
 * 原语义保持（含各自的错误消息与解包方式）。
 */
import { doFetch, apiFetchData } from "./httpClient";

/**
 * 本体工作台 REST 收口 (H6-T2) — 自 ontology-workbench 组件与 BusinessObjectExplorer 迁入。
 * Wave-3 W3 raw-fetch 收口：两个具名助手此前是裸 fetch（绕开共享 httpClient 的
 * 401/403、network-down/up、auth-expired 处理）。现委托基线共享函数，语义等价：
 *   - ontologyDomainApiFetch(path, opts) → apiFetchData（解包 .data ?? 整包，同原 `json.data ?? json`）
 *   - fetchOntologyDataJson()            → doFetch（ok-check + 整包 JSON，caller 读 resp.code，
 *     对 null 已在成功路径容忍，故无行为回归）
 */

/** 本体域 CRUD JSON 助手（原 OntologyDomainPanel 本地 apiFetch）。解包 .data，日志用 route reason。 */
export function ontologyDomainApiFetch(path: string, options?: RequestInit): Promise<any> {
  return apiFetchData(path, options);
}

/** GET /api/v1/ontology/data — ok-check + 整包 JSON（normalizeObjectTypes 留在页面层）。 */
export function fetchOntologyDataJson(): Promise<any> {
  return doFetch("/api/v1/ontology/data");
}
