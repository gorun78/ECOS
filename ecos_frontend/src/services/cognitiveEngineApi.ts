import { apiFetchData } from "../api";

const ENGINE_BASE = "/api/v1/engine/cognitive";
const COGNITIVE_BASE = "/api/v1/cognitive";
const MESH_BASE = "/api/agent-mesh";

// ── Engine Status / Start / Stop ─────────────────────────────

export async function fetchEngineHealth() {
  return apiFetchData(`${ENGINE_BASE}/health`);
}

export async function fetchEngineStatus() {
  return apiFetchData(`${ENGINE_BASE}/status`);
}

export async function startEngine() {
  return apiFetchData(`${ENGINE_BASE}/start`, { method: "POST" });
}

export async function stopEngine() {
  return apiFetchData(`${ENGINE_BASE}/stop`, { method: "POST" });
}

// ── Engine Config ────────────────────────────────────────────

export async function fetchEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/settings`);
}

export async function fetchEngineSettingsDefaults() {
  return apiFetchData(`${ENGINE_BASE}/settings/defaults`);
}

export async function updateEngineSettings(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData(`${ENGINE_BASE}/settings`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function refreshEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/settings/refresh`, { method: "POST" });
}

// ── Sub-engine 1: PromptCompiler (联邦RAG+上下文编译) ────────

export async function compileContext(body: { missionId: string; agentIds: string[]; maxTokens?: number }) {
  return apiFetchData(`${COGNITIVE_BASE}/compile-context`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchIndexStatus() {
  return apiFetchData(`${COGNITIVE_BASE}/index-status`);
}

// ── Sub-engine 2: AgentMesh (多Agent协同+意图路由) ────────────

export async function routeIntent(body: { query: string; topK?: number }) {
  return apiFetchData(`${MESH_BASE}/route-intent`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchMeshAgents() {
  return apiFetchData(`${MESH_BASE}/agents`);
}

export async function fetchMeshMissions() {
  return apiFetchData(`${MESH_BASE}/missions`);
}

// ── Sub-engine 3: Guardrails (零信任护栏) ─────────────────────

export async function validateGuardrails(body: { content: string; policyIds?: string[] }) {
  return apiFetchData("/api/v1/guardrails/validate", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchGuardrailPolicies() {
  return apiFetchData("/api/v1/guardrails/policies");
}

export async function createGuardrailPolicy(body: Record<string, unknown>) {
  return apiFetchData("/api/v1/guardrails/policies", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function deleteGuardrailPolicy(id: string) {
  return apiFetchData(`/api/v1/guardrails/policies/${id}`, { method: "DELETE" });
}

// ── Sub-engine 4: ActionBridge (LLM输出→本体Action→自动执行) ─

export async function executeAction(body: { llmOutput: string; domain?: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/execute-action`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchAvailableActions() {
  return apiFetchData(`${COGNITIVE_BASE}/available-actions`);
}

// ── Cognitive Core (blueprint/reason/optimize/plan) ───────────

export async function fetchBlueprint() {
  return apiFetchData(`${COGNITIVE_BASE}/blueprint`);
}

export async function runReasoning(body: { event: string; depth?: number }) {
  return apiFetchData(`${COGNITIVE_BASE}/reason`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function runOptimize(body: { objectives: string[]; constraints?: Record<string, unknown> }) {
  return apiFetchData(`${COGNITIVE_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function createPlan(body: { name: string; steps: Record<string, unknown>[]; priority?: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/plan`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchPlan(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/plan/${id}`);
}

// ── Export aggregated object ─────────────────────────────────

export const cognitiveEngineApi = {
  fetchEngineHealth,
  fetchEngineStatus,
  startEngine,
  stopEngine,
  fetchEngineSettings,
  fetchEngineSettingsDefaults,
  updateEngineSettings,
  refreshEngineSettings,
  compileContext,
  fetchIndexStatus,
  routeIntent,
  fetchMeshAgents,
  fetchMeshMissions,
  validateGuardrails,
  fetchGuardrailPolicies,
  createGuardrailPolicy,
  deleteGuardrailPolicy,
  executeAction,
  fetchAvailableActions,
  fetchBlueprint,
  runReasoning,
  runOptimize,
  createPlan,
  fetchPlan,
};
