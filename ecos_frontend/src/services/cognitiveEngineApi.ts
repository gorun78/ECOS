import { apiFetchData } from "../api";

const ENGINE_BASE = "/api/v1/engine/cognitive";
const COGNITIVE_BASE = "/api/v1/cognitive";

// ── Diagnosis ────────────────────────────────────────────

export async function runDiagnosis(body: {
  objectId: string;
  objectType?: string;
  question: string;
  reasoningMode?: string;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/diagnose`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchDiagnosisHistory(params?: { page?: number; size?: number }) {
  const qs = new URLSearchParams();
  if (params?.page != null) qs.set("page", String(params.page));
  if (params?.size != null) qs.set("size", String(params.size));
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/diagnose/history${q ? "?" + q : ""}`);
}

// ── Scenario ─────────────────────────────────────────────

export async function runScenario(body: {
  scenarioType: string;
  variables: Record<string, number | string>;
  context?: Record<string, unknown>;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/scenario/simulate`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchScenarios() {
  return apiFetchData(`${COGNITIVE_BASE}/scenario/list`);
}

export async function compareScenarios(body: { baselineId: string; scenarioId: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/scenario/compare`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Counterfactual ───────────────────────────────────────

export async function runCounterfactual(body: {
  variable: string;
  newValue: string;
  context: Record<string, unknown>;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/counterfactual`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Hypotheses ───────────────────────────────────────────

export async function fetchHypotheses(params?: { domain?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  if (params?.status) qs.set("status", params.status);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/hypotheses${q ? "?" + q : ""}`);
}

export async function fetchHypothesis(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/hypotheses/${encodeURIComponent(id)}`);
}

export async function createHypothesis(body: {
  statement: string;
  domain?: string;
  metricRef?: string;
  evidenceIds?: string[];
}) {
  return apiFetchData(`${COGNITIVE_BASE}/hypotheses`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function invalidateHypothesis(id: string, reason?: string) {
  return apiFetchData(`${COGNITIVE_BASE}/hypotheses/${encodeURIComponent(id)}/invalidate`, {
    method: "POST",
    body: JSON.stringify({ reason }),
  });
}

// ── Evidence ─────────────────────────────────────────────

export async function fetchEvidence(params?: { domain?: string; status?: string; sourceType?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  if (params?.status) qs.set("status", params.status);
  if (params?.sourceType) qs.set("sourceType", params.sourceType);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/evidence${q ? "?" + q : ""}`);
}

export async function fetchEvidenceById(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/evidence/${encodeURIComponent(id)}`);
}

export async function createEvidence(body: {
  sourceType: string;
  sourceRef: string;
  blob: Record<string, unknown>;
  confidence?: number;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/evidence`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Beliefs ──────────────────────────────────────────────

interface DistributionPoint {
  outcome: string;
  prob: number;
}

export async function fetchBeliefs(params?: { domain?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/beliefs${q ? "?" + q : ""}`);
}

export async function fetchBelief(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/beliefs/${encodeURIComponent(id)}`);
}

export async function createBelief(body: {
  variableName: string;
  domain: string;
  distribution: DistributionPoint[];
}) {
  return apiFetchData(`${COGNITIVE_BASE}/beliefs`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function updateBeliefByEvidence(variable: string, body: { evidenceId: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/beliefs/${encodeURIComponent(variable)}/update-by-evidence`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function overrideBelief(variable: string, body: {
  distribution: DistributionPoint[];
  reason: string;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/beliefs/${encodeURIComponent(variable)}/override`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function replayBelief(variable: string, version: number) {
  return apiFetchData(`${COGNITIVE_BASE}/beliefs/${encodeURIComponent(variable)}/replay`, {
    method: "POST",
    body: JSON.stringify({ version }),
  });
}

// ── Forecast ─────────────────────────────────────────────

export async function runForecast(body: Record<string, unknown>) {
  return apiFetchData(`${COGNITIVE_BASE}/forecast`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchModels() {
  return apiFetchData(`${COGNITIVE_BASE}/models`);
}

export async function fetchModelById(modelId: string) {
  return apiFetchData(`${COGNITIVE_BASE}/models/${encodeURIComponent(modelId)}`);
}

export async function createModel(body: {
  modelId: string;
  modelType: string;
  features?: Record<string, unknown>;
}) {
  return apiFetchData(`${COGNITIVE_BASE}/models`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Mental Reviews ───────────────────────────────────────

export async function fetchMentalReviews(params?: { tag?: string; since?: string }) {
  const qs = new URLSearchParams();
  if (params?.tag) qs.set("tag", params.tag);
  if (params?.since) qs.set("since", params.since);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/mental-reviews${q ? "?" + q : ""}`);
}

// ── Pipeline ─────────────────────────────────────────────

export async function createPipeline(body: { name: string; config: Record<string, unknown> }) {
  return apiFetchData(`${COGNITIVE_BASE}/pipeline`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchPipelines() {
  return apiFetchData(`${COGNITIVE_BASE}/pipeline`);
}

export async function executePipeline(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/pipeline/${encodeURIComponent(id)}/execute`, {
    method: "POST",
  });
}

export async function deletePipeline(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/pipeline/${encodeURIComponent(id)}`, {
    method: "DELETE",
  });
}

export async function fetchPipelineExecution(pipelineId: string, execId: string) {
  return apiFetchData(
    `${COGNITIVE_BASE}/pipeline/${encodeURIComponent(pipelineId)}/executions/${encodeURIComponent(execId)}`
  );
}

// ── Planner ──────────────────────────────────────────────

export async function createPlan(body: { name: string; steps: unknown[] }) {
  return apiFetchData(`${COGNITIVE_BASE}/plan`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchPlan(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/plan/${encodeURIComponent(id)}`);
}

export async function runOptimize(body: { objectives: string[]; constraints?: Record<string, unknown> }) {
  return apiFetchData(`${COGNITIVE_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Decision ─────────────────────────────────────────────

export async function recordDecision(body: Record<string, unknown>) {
  return apiFetchData(`${COGNITIVE_BASE}/decision/record`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function linkDecision(id: string, body: { linkType: string; targetId: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/decision/${encodeURIComponent(id)}/link`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchSimilarDecisions(params?: { domain?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/decision/similar${q ? "?" + q : ""}`);
}

export async function fetchDecisionChain(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/decision/${encodeURIComponent(id)}/chain`);
}

export async function fetchDecisionImpact(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/decision/${encodeURIComponent(id)}/impact`);
}

export async function checkDecisionRules(id: string) {
  return apiFetchData(`${COGNITIVE_BASE}/decision/${encodeURIComponent(id)}/rules`);
}

// ── Provenance ───────────────────────────────────────────

export async function fetchProvenance(params?: { domain?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  const q = qs.toString();
  return apiFetchData(`${COGNITIVE_BASE}/provenance${q ? "?" + q : ""}`);
}

// ── World Model ──────────────────────────────────────────

export async function fetchWorldState() {
  return apiFetchData(`/api/v1/world-model/state`);
}

export async function runWorldScenarios(body: Record<string, unknown>) {
  return apiFetchData(`/api/v1/world-model/scenarios`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function recommendStrategy(body: Record<string, unknown>) {
  return apiFetchData(`/api/v1/world-model/strategy/recommend`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function fetchCausalGraph(params?: { domain?: string }) {
  const qs = new URLSearchParams();
  if (params?.domain) qs.set("domain", params.domain);
  const q = qs.toString();
  return apiFetchData(`/api/v1/world-model/causal-graph${q ? "?" + q : ""}`);
}

// ── Health ───────────────────────────────────────────────

export async function fetchEngineHealth() {
  return apiFetchData(`${ENGINE_BASE}/health`);
}

export async function fetchCognitiveHealth() {
  return apiFetchData(`${COGNITIVE_BASE}/health`);
}

// ── Engine 控制台 (AiEngineStatusController) ────────────

/** GET /api/v1/engine/cognitive/status — 引擎状态 {name, status} */
export async function fetchEngineStatus() {
  return apiFetchData(`${ENGINE_BASE}/status`);
}

/** POST /api/v1/engine/cognitive/start — 启动引擎 */
export async function startEngine() {
  return apiFetchData(`${ENGINE_BASE}/start`, { method: 'POST' });
}

/** POST /api/v1/engine/cognitive/stop — 停止引擎 */
export async function stopEngine() {
  return apiFetchData(`${ENGINE_BASE}/stop`, { method: 'POST' });
}

/** GET /api/v1/engine/cognitive/config — 引擎配置 */
export async function fetchEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/config`);
}

/** GET /api/v1/engine/cognitive/config — 配置默认值（与 config 同源） */
export async function fetchEngineSettingsDefaults() {
  return apiFetchData(`${ENGINE_BASE}/config`);
}

/** POST /api/v1/engine/cognitive/start — 刷新设置（config 只读，重启生效前借用 start 探测配置重载） */
export async function refreshEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/start`, { method: 'POST' });
}

/** PUT 配置不可写（后端 config 只读），设置更新走 start 触发重载 */
export async function updateEngineSettings(body: Record<string, unknown>) {
  return apiFetchData(`${ENGINE_BASE}/start`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

// ── PromptCompiler (PromptCompilerController) ───────────

/** POST /api/v1/cognitive/compile-context — 联邦 RAG 上下文编译 */
export async function compileContext(body: { missionId: string; agentIds?: string[] }) {
  return apiFetchData(`${COGNITIVE_BASE}/compile-context`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/index-status — RAG 索引状态 */
export async function fetchIndexStatus() {
  return apiFetchData(`${COGNITIVE_BASE}/index-status`);
}

// ── AgentMesh (AgentMeshController, 引擎自有网关前缀 /api/agent-mesh) ──

/** GET /api/agent-mesh/agents — 已注册 Agent 列表 */
export async function fetchMeshAgents() {
  return apiFetchData('/api/agent-mesh/agents');
}

/** GET /api/agent-mesh/missions — Mission 列表 */
export async function fetchMeshMissions() {
  return apiFetchData('/api/agent-mesh/missions');
}

/** POST /api/agent-mesh/route-intent — 意图路由 */
export async function routeIntent(body: { query: string }) {
  return apiFetchData('/api/agent-mesh/route-intent', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

// ── Guardrails (GuardrailsApiController @ /api/v1/guardrails) ──

/** POST /api/v1/guardrails/validate — 内容校验（PII/幻觉/合规） */
export async function validateGuardrails(body: { content: string }) {
  return apiFetchData('/api/v1/guardrails/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/guardrails/policies — 护栏策略列表 */
export async function fetchGuardrailPolicies() {
  return apiFetchData('/api/v1/guardrails/policies');
}

/** POST /api/v1/guardrails/policies — 新增护栏策略 */
export async function createGuardrailPolicy(body: Record<string, unknown>) {
  return apiFetchData('/api/v1/guardrails/policies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** DELETE /api/v1/guardrails/policies/{id} — 删除护栏策略 */
export async function deleteGuardrailPolicy(id: string) {
  return apiFetchData(`/api/v1/guardrails/policies/${id}`, { method: 'DELETE' });
}

// ── ActionBridge (ActionBridgeController @ /api/v1/cognitive) ──

/** POST /api/v1/cognitive/execute-action — LLM 输出 → 本体行动执行 */
export async function executeAction(body: { llmOutput: string }) {
  return apiFetchData(`${COGNITIVE_BASE}/execute-action`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/available-actions — 可用行动列表 */
export async function fetchAvailableActions() {
  return apiFetchData(`${COGNITIVE_BASE}/available-actions`);
}

// ── Export aggregated object ─────────────────────────────

export const cognitiveEngineApi = {
  // Diagnosis
  runDiagnosis,
  fetchDiagnosisHistory,
  // Scenario
  runScenario,
  fetchScenarios,
  compareScenarios,
  // Counterfactual
  runCounterfactual,
  // Hypotheses
  fetchHypotheses,
  fetchHypothesis,
  createHypothesis,
  invalidateHypothesis,
  // Evidence
  fetchEvidence,
  fetchEvidenceById,
  createEvidence,
  // Beliefs
  fetchBeliefs,
  fetchBelief,
  createBelief,
  updateBeliefByEvidence,
  overrideBelief,
  replayBelief,
  // Forecast
  runForecast,
  fetchModels,
  fetchModelById,
  createModel,
  // Mental Reviews
  fetchMentalReviews,
  // Pipeline
  createPipeline,
  fetchPipelines,
  executePipeline,
  deletePipeline,
  fetchPipelineExecution,
  // Planner
  createPlan,
  fetchPlan,
  runOptimize,
  // Decision
  recordDecision,
  linkDecision,
  fetchSimilarDecisions,
  fetchDecisionChain,
  fetchDecisionImpact,
  checkDecisionRules,
  // Provenance
  fetchProvenance,
  // World Model
  fetchWorldState,
  runWorldScenarios,
  recommendStrategy,
  fetchCausalGraph,
  // Health
  fetchEngineHealth,
  fetchCognitiveHealth,
};
