/**
 * 知识检索 / RAG / 本体编译 REST 收口 (H6-T2)。
 * 原语义保持：鉴权头有无与迁移前各调用点一致；非 2xx 抛错（errorMessage 缺省 `HTTP ${status}`）。
 *
 * 传输合流（#143/B5-11d，C2′ 判据）：本文件 5 条腿原各自裸 `fetch(`，现全部走
 * `services/httpClient.ts` 单源的两条裸响应通道 ——
 *   - `rawAuthedUrlFetch` = 带 Bearer（仅 `knowledgeQueryWithAuth` 一条）；
 *   - `rawAnonUrlFetch`   = 显式匿名（其余 4 条，**改前就不带身份，改后仍不带**）。
 * 两条通道都不解析响应体、不做 401/403 副作用 ⇒ 各腿的 `resp.json()`/`ok` 判据逐字保留。
 *
 * 唯一的**行为差**（与 #143/B5-11b/c 同族，非回归）：网络层失败改前抛 `TypeError`，
 * 改后按单源 PMO-43 口径归一为 `NetworkError` 并广播 `ecos-network-down`；
 * HTTP 层的状态码语义一条都没动。
 *
 * ⚠ 4 条匿名腿的**可用性**不属本批结论：`AnonymousEndpointRegistry`（permitAll 单源）内
 * 无 `knowledge/query`、`knowledge/rag`、`ontology/compiler/compile`、`security/audit-logs`
 * ⇒ 经网关恒 401（vite dev 直连引擎才可过）。登记为 **D-B5-36**，处置需产品裁决。
 *
 * <p>**基址单源（#143/B5-11e 同批追加）**：5 条腿的 `/api` 前缀全部改由 `API_BASE` 派生
 * ⇒ 从 `api-base-single-source` 基线剪除本文件（URL 逐字不变，`"/api/v1/…"` 与
 * `` `${API_BASE}/v1/…` `` 展开后同一串）。</p>
 *
 * <p>**#145/B6-6 族基址单源**：`/v1/knowledge/query`、`/v1/knowledge/rag` 两族改前由本文件与
 * `pages/knowledge/services/wiki.ts` 各写一份（棘轮 dupBoth 在册），现由本文件导出
 * `KNOWLEDGE_QUERY_BASE` / `KNOWLEDGE_RAG_BASE` 单源持有，wiki 侧改 import。属主落本文件（该族原服务
 * 拆分文件，只依赖 `./httpClient` ⇒ wiki 引它不成环）与 #145/B6-4「属主 = 该族原服务拆分文件」同口径。
 * 两枚声明**刻意保持** `` `${API_BASE}/v1/…` `` 裸形态而非拼常量：族体留在生产面 ⇒ 该族继续在棘轮索引
 * 里、只是属主降到 1；若写成二级常量会让族隐形＝关掉可见性却不减少债。</p>
 */
import { API_BASE, rawAnonUrlFetch, rawAuthedUrlFetch } from "./httpClient";

/** 族 `/v1/knowledge/query` 单源基址（#145/B6-6）。 */
export const KNOWLEDGE_QUERY_BASE = `${API_BASE}/v1/knowledge/query`;

/** 族 `/v1/knowledge/rag` 单源基址（#145/B6-6）。 */
export const KNOWLEDGE_RAG_BASE = `${API_BASE}/v1/knowledge/rag`;

/** POST /api/v1/knowledge/query — 带 Bearer（自 AIPKnowledgeView 迁入），返回整包 JSON */
export async function knowledgeQueryWithAuth(body: Record<string, unknown>): Promise<any> {
  const resp = await rawAuthedUrlFetch(KNOWLEDGE_QUERY_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return resp.json();
}

/** POST /api/v1/knowledge/query — 仅 Content-Type 无鉴权（自 AIPCopilotDrawer 迁入） */
export async function knowledgeQueryAnonymous(body: Record<string, unknown>): Promise<any> {
  const resp = await rawAnonUrlFetch(KNOWLEDGE_QUERY_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return resp.json();
}

/** GET /api/v1/security/audit-logs — 无鉴权（自 AIPCopilotDrawer 迁入），仅 ok 时解析 */
export async function fetchSecurityAuditLogs(): Promise<{ ok: boolean; json?: any }> {
  const resp = await rawAnonUrlFetch(`${API_BASE}/v1/security/audit-logs`);
  return { ok: resp.ok, json: resp.ok ? await resp.json() : undefined };
}

/** POST /api/v1/knowledge/rag — 无鉴权（自 queries/useEcosApi 迁入） */
export async function ragQueryRaw(request: Record<string, unknown>, errorMessage?: string): Promise<any> {
  const resp = await rawAnonUrlFetch(KNOWLEDGE_RAG_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}

/** POST /api/v1/ontology/compiler/compile — 无鉴权（自 queries/useEcosApi 迁入） */
export async function compileOntologyRaw(request: Record<string, unknown>, errorMessage?: string): Promise<any> {
  const resp = await rawAnonUrlFetch(`${API_BASE}/v1/ontology/compiler/compile`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}
