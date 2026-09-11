/**
 * AI Workbench — API Service Layer
 *
 * Wraps the shared `apiFetchData` helper from `../api.ts` and provides
 * AI Workbench-specific API calls for pipelines, agents, models, guardrails, and audit logs.
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import { apiFetchData } from "../api";
import type { AIPLogicPipeline, AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from "../types/aiworkbench";
import {
  fetchPipelineDefinitions,
  fetchManagedAgents,
  fetchAgentModels,
  fetchGuardrailPolicies,
} from "../pages/aiworkbench/api";

// ── Auth Helpers ────────────────────────────────────────────────

/** Get authorization headers with Bearer token from localStorage */
export function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("token") || "";
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

// ── Type aliases for backend raw responses ──────────────────────

/** Raw agent record from AgentMesh API */
export interface AgentMeshAgentRaw {
  id: string;
  name: string;
  description?: string;
  status?: string;
  modelId?: string;
  systemPrompt?: string;
  tools?: string[];
}

/** Raw policy record from Guardrail/Policy API */
export interface GuardrailPolicyRaw {
  id: string;
  name: string;
  description?: string;
  type?: string;
  isEnabled?: boolean;
  severity?: string;
  parameters?: Record<string, any>;
}

/** Convert a raw AgentMesh agent to AIPAgent format */
export function convertMeshAgentToAIP(raw: AgentMeshAgentRaw): AIPAgent {
  return {
    id: raw.id,
    name: raw.name,
    avatar: "Bot",
    role: raw.description || "Agent",
    description: raw.description || "",
    modelId: raw.modelId || "",
    systemPrompt: raw.systemPrompt || "",
    assignedTools: { actionIds: raw.tools || [], functionIds: [] },
    guardrailIds: [],
    status: raw.status === "active" ? "active" : "development",
    lastModified: new Date().toISOString(),
  };
}

/** Convert a raw Guardrail policy to AIPGuardrail format */
export function convertPolicyToGuardrail(raw: GuardrailPolicyRaw): AIPGuardrail {
  return {
    id: raw.id,
    name: raw.name,
    type: (raw.type as AIPGuardrail["type"]) || "harm_filter",
    description: raw.description || "",
    isEnabled: raw.isEnabled ?? true,
    severity: (raw.severity as AIPGuardrail["severity"]) || "warn",
    parameters: raw.parameters || {},
  };
}

// ── Pipelines ──────────────────────────────────────────────────

/** Fetch all AIP logic pipelines.
 *  @deprecated Use `fetchPipelineDefinitions` from `../pages/aiworkbench/api` (主入口，已补齐转换层) 替代本函数。
 *  保留本函数仅为向后兼容，保持导出名不变，内部重定向到主入口函数。 */
export async function fetchPipelines(): Promise<AIPLogicPipeline[]> {
  console.warn("legacy api, please use /api/v1/aip/pipelines via fetchPipelineDefinitions");
  return fetchPipelineDefinitions();
}

/** Create a new AIP logic pipeline — immutable primary entry, fetched via main entry not here. */
export async function createPipeline(body: Partial<AIPLogicPipeline>): Promise<AIPLogicPipeline> {
  return apiFetchData<AIPLogicPipeline>("/api/v1/aip/pipelines", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** Update an existing AIP logic pipeline */
export async function updatePipeline(id: string, body: Partial<AIPLogicPipeline>): Promise<AIPLogicPipeline> {
  return apiFetchData<AIPLogicPipeline>(`/api/v1/aip/pipelines/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** Delete an AIP logic pipeline */
export async function deletePipeline(id: string): Promise<void> {
  return apiFetchData<void>(`/api/v1/aip/pipelines/${id}`, {
    method: "DELETE",
  });
}

// ── Agents ─────────────────────────────────────────────────────

/** Fetch all AIP agents. Errors propagate (re-throw) — see fetchPipelines. */
export async function fetchAgents(): Promise<AIPAgent[]> {
  return apiFetchData<AIPAgent[]>("/api/v1/aip/agents");
}

/** Create a new AIP agent */
export async function createAgent(body: Partial<AIPAgent>): Promise<AIPAgent> {
  return apiFetchData<AIPAgent>("/api/v1/aip/agents", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** Update an existing AIP agent */
export async function updateAgent(id: string, body: Partial<AIPAgent>): Promise<AIPAgent> {
  return apiFetchData<AIPAgent>(`/api/v1/aip/agents/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** Delete an AIP agent */
export async function deleteAgent(id: string): Promise<void> {
  return apiFetchData<void>(`/api/v1/aip/agents/${id}`, {
    method: "DELETE",
  });
}

// ── Models ─────────────────────────────────────────────────────

/** Fetch all AIP models.
 *  @deprecated Use `fetchAgentModels` from `../pages/aiworkbench/api` (主入口，已补齐模型转换层) 替代本函数。
 *  保留本函数仅为向后兼容，内部重定向到主入口函数。 */
export async function fetchModels(): Promise<AIPModel[]> {
  console.warn("legacy api, please use /api/v1/aip/models via fetchAgentModels");
  return fetchAgentModels();
}

// ── Guardrails ─────────────────────────────────────────────────

/** Fetch all AIP guardrails.
 *  @deprecated Use `fetchGuardrailPolicies` from `../pages/aiworkbench/api` (主入口，已补齐策略转换层) 替代本函数。
 *  保留本函数仅为向后兼容，内部重定向到主入口函数。 */
export async function fetchGuardrails(): Promise<AIPGuardrail[]> {
  console.warn("legacy api, please use /api/v1/aip/guardrails via fetchGuardrailPolicies");
  return fetchGuardrailPolicies();
}

/** Create a new AIP guardrail */
export async function createGuardrail(body: Partial<AIPGuardrail>): Promise<AIPGuardrail> {
  return apiFetchData<AIPGuardrail>("/api/v1/aip/guardrails", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Audit Logs ─────────────────────────────────────────────────

/** Fetch all AIP audit logs.
 *  @deprecated Use the dedicated audit-log query API at
 *  `/api/v1/aip/audit-logs` via `apiFetchData` directly (主入口无对应封装；
 *  建议调用方迁到 pages/aiworkbench/api 或独立封装)。保留导出名以兼容旧调用。 */
export async function fetchAuditLogs(): Promise<AIPAuditLog[]> {
  console.warn("legacy api, please use /api/v1/aip/audit-logs directly");
  return apiFetchData<AIPAuditLog[]>("/api/v1/aip/audit-logs");
}
