/**
 * 知识检索 / RAG / 本体编译 REST 收口 (H6-T2)。
 * 原语义保持：鉴权头有无与迁移前各调用点一致；非 2xx 抛错（errorMessage 缺省 `HTTP ${status}`）。
 */
import { authHeaders } from "./auth";

/** POST /api/v1/knowledge/query — 带 Bearer（自 AIPKnowledgeView 迁入），返回整包 JSON */
export async function knowledgeQueryWithAuth(body: Record<string, unknown>): Promise<any> {
  const resp = await fetch("/api/v1/knowledge/query", {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  return resp.json();
}

/** POST /api/v1/knowledge/query — 仅 Content-Type 无鉴权（自 AIPCopilotDrawer 迁入） */
export async function knowledgeQueryAnonymous(body: Record<string, unknown>): Promise<any> {
  const resp = await fetch("/api/v1/knowledge/query", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return resp.json();
}

/** GET /api/v1/security/audit-logs — 无鉴权（自 AIPCopilotDrawer 迁入），仅 ok 时解析 */
export async function fetchSecurityAuditLogs(): Promise<{ ok: boolean; json?: any }> {
  const resp = await fetch("/api/v1/security/audit-logs");
  return { ok: resp.ok, json: resp.ok ? await resp.json() : undefined };
}

/** POST /api/v1/knowledge/rag — 无鉴权（自 queries/useEcosApi 迁入） */
export async function ragQueryRaw(request: Record<string, unknown>, errorMessage?: string): Promise<any> {
  const resp = await fetch("/api/v1/knowledge/rag", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}

/** POST /api/v1/ontology/compiler/compile — 无鉴权（自 queries/useEcosApi 迁入） */
export async function compileOntologyRaw(request: Record<string, unknown>, errorMessage?: string): Promise<any> {
  const resp = await fetch("/api/v1/ontology/compiler/compile", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}
