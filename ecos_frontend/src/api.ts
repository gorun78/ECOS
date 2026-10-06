/**
 * C2EOS API Service Layer
 * All API calls go through the BFF (server.ts) which proxies to the Java backend.
 * Falls back to mock data when backend is unavailable.
 */
import type {
  EntityDefinition, EntityInstance, AuditEvent,
  RelationshipDefinition, ActionDefinition,
  AgentDefinition, ToolDefinition, PromptTemplate,
  KnowledgeNode, KnowledgeEdge, Goal, CausalLink, Scenario,
  DataSource, DataResource, DataField, AuditStats,
} from "./types";

// Import mock data as fallback
import {
  MOCK_ONTOLOGY_ENTITIES, MOCK_ENTITY_INSTANCES,
} from "./mockData";

import {
  apiFetch,
  apiFetchData,
  doFetch,
  toNetworkError,
  notifyNetworkDown,
} from "./services/httpClient";

// HTTP 传输原语已单源化至 src/services/httpClient.ts (PMO-74.6 H6-T2) —
// 此处 re-export 保持既有 `from "./api"` 导入面不变。
export {
  API_BASE,
  NetworkError,
  isErrorResponse,
  notifyNetworkDown,
  markNetworkDown,
  notifyNetworkUp,
  toAbortError,
  setAuthGracePeriod,
  handleAuthExpired,
  checkAuthExpired,
  NoAccessError,
  NO_ACCESS_ERROR_NAME,
  NO_ACCESS_EVENT,
  notifyNoAccess,
  isNoAccessError,
  encodeQueryString,
  apiFetch,
  apiFetchData,
  doFetch,
  authLogin,
  apiHealth,
} from "./services/httpClient";
export type { LoginRequest, LoginResponse } from "./services/httpClient";
export { ApiError } from "./services/apiError";
export type { ApiErrorKind, ApiErrorBody } from "./services/apiError";

// ── E4.1 域拆分 barrel 层 (P0 顶层共因) ──────────────────────────
// 现有 2 片: ① IAM/Tenant → src/api_iam.ts (原 L1494-1751)
//           ② Data Quality → src/api_dq.ts   (原 L1021-1071)
// 通过 export * re-export 保持 168 个 importer `from "./api"` 契约零改动。
export * from "./api_iam";
export * from "./api_dq";
export * from "./api_world";
export * from "./api_market";
export * from "./api_security";
export * from "./api_datalake";
export * from "./api_syscfg";


// ── Ontology ────────────────────────────────────────────
export async function fetchOntology(): Promise<{
  entities: EntityDefinition[];
  instances: Record<string, EntityInstance[]>;
}> {
  try {
    const resp = await apiFetchData<{ data?: any[]; records?: any[] }>(`/api/v1/ecos/ontologies/ont001/entities`);
    const records = resp?.data || resp?.records || [];

    if (records.length > 0) {
      const entities: EntityDefinition[] = records.map((rec: any) => ({
        id: rec.code || rec.id || rec.name,
        name: rec.name || rec.code || "",
        description: rec.description || "",
        properties: (rec.properties || []).map((p: any) => ({
          name: p.code || p.name || "",
          type: (p.propertyType || "string") as any,
          searchable: p.searchableFlag === 1,
          editable: true,
        })),
        relationships: [] as RelationshipDefinition[],
        actions: [] as ActionDefinition[],
      }));
      return { entities, instances: {} };
    }
  } catch (e) {
    console.warn("fetchOntology failed, using mock fallback", e);
  }

  // Mock fallback
  return buildMockOntology();
}

function buildMockOntology(): { entities: EntityDefinition[]; instances: Record<string, EntityInstance[]> } {
  const entities: EntityDefinition[] = [
    { id: "ent_customer", name: "客户 (Customer)", description: "企业客户主数据实体", properties: [
      { name: "customer_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "region", type: "string", searchable: true, editable: true },
      { name: "revenue", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "has", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_order", name: "订单 (Order)", description: "销售订单实体", properties: [
      { name: "order_id", type: "string", searchable: true, editable: false },
      { name: "amount", type: "number", searchable: false, editable: true },
      { name: "status", type: "string", searchable: true, editable: true },
    ], relationships: [{ name: "belongs_to", targetEntity: "ent_customer", cardinality: "one" }, { name: "contains", targetEntity: "ent_product", cardinality: "many" }], actions: [] },
    { id: "ent_product", name: "产品 (Product)", description: "产品主数据", properties: [
      { name: "product_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "category", type: "string", searchable: true, editable: true },
      { name: "price", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "part_of", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_machine", name: "设备 (Machine)", description: "生产设备实体", properties: [
      { name: "machine_id", type: "string", searchable: true, editable: false },
      { name: "temperature_c", type: "number", searchable: false, editable: true },
      { name: "has_fault", type: "boolean", searchable: true, editable: true },
    ], relationships: [], actions: [] },
  ];
  return { entities, instances: {} };
}

/** Build a fallback EntityDefinition from instance data */
function buildFallbackEntity(name: string, instances: EntityInstance[]): EntityDefinition {
  // Extract property names from the first instance
  const propNames: string[] = [];
  if (instances.length > 0) {
    Object.keys(instances[0].properties).forEach(k => {
      if (!propNames.includes(k)) propNames.push(k);
    });
  }
  
  return {
    id: name,
    name: `${name} Entity`,
    description: `${name} 实体（动态解析）`,
    properties: propNames.map(pn => ({
      name: pn,
      type: "string" as const,
      searchable: pn === "name" || pn === "code",
      editable: true,
    })),
    relationships: [],
    actions: [],
  };
}

// ── Agent Chat ──────────────────────────────────────────
/** @deprecated legacy path `/agent/chat`; please use `/api/v1/agent/chat` via the primary entry `pages/aiworkbench/api`. 保留本函数仅为兼容 AgentTestConsole 等旧调用方。 */
export async function agentChat(
  agentId: string, message: string, promptTemplate?: string, datasetContext?: any
): Promise<{
  success: boolean;
  source: string;
  responseText: string;
  thoughtTrace: { type: string; summary: string }[];
  logId: string;
  // ── Human-in-the-loop: optional Action Proposal returned by the agent ──
  actionProposal?: {
    actionId: string;
    actionName?: string;
    payload?: Record<string, string>;
    status?: "pending" | "approved" | "rejected";
  };
}> {
  console.warn("legacy api, please use /api/v1/agent/*");
  return apiFetch("/agent/chat", {
    method: "POST",
    body: JSON.stringify({ agentId, message, promptTemplate, datasetContext }),
  });
}

/** @deprecated legacy path `/agent/agents`; please use `/api/v1/agent/agents` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchAgents(): Promise<AgentDefinition[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/agents");
    const raw = resp.data || [];
    return raw.map((a: any) => {
      let tools: string[] = [];
      if (a.toolset) {
        try { tools = typeof a.toolset === "string" ? JSON.parse(a.toolset) : a.toolset; } catch { tools = []; }
      } else if (a.tools) {
        tools = a.tools;
      }
      return {
        id: a.id,
        name: a.name,
        role: a.role,
        goal: a.goal || a.description || "",
        tools,
        systemPrompt: a.systemPrompt || "",
        capabilities: a.capabilities || [],
      };
    });
  } catch {
    return [];
  }
}

/** @deprecated legacy path `/agent/tools`; please use `/api/v1/agent/tools` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchTools(): Promise<ToolDefinition[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: ToolDefinition[] }>("/agent/tools");
    return resp.data || [];
  } catch {
    return [];
  }
}

/** @deprecated legacy path `/agent/prompts`; please use `/api/v1/agent/prompts` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchPrompts(): Promise<PromptTemplate[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/prompts");
    const raw = resp.data || [];
    return raw.map((p: any) => ({
      id: p.id,
      title: p.name || p.title || "",
      filename: p.filename || `${p.id}.md`,
      content: p.template || p.content || "",
      version: p.version || "1.0",
      category: p.category || "planning",
    }));
  } catch {
    return [];
  }
}

// ── Security Profile ───────────────────────────────────────
export interface SecurityProfile {
  clearanceLevel: number;
  linkedWorkstation: string;
  auditMode: string;
  sandboxMandatory: boolean;
  // Scope
  scopeType?: string;
  tenantId?: string;
  orgId?: string;
  // Password policy
  passwordMinLength?: number;
  mfaEnabled?: boolean;
  passwordExpireDays?: number;
  // Session management
  sessionTimeout?: number;
  maxConcurrentSessions?: number;
}

/** Fetch the current security profile — GET /api/v1/security/profile */
export async function fetchSecurityProfile(params?: string): Promise<SecurityProfile> {
  const path = params ? `/v1/security/profile${params}` : "/v1/security/profile";
  const json = await apiFetch<{ code: number; data: SecurityProfile }>(path);
  return json.data;
}

/** Update the security profile — PUT /api/v1/security/profile */
export async function updateSecurityProfile(
  body: Partial<SecurityProfile>
): Promise<SecurityProfile> {
  const json = await apiFetch<{ code: number; data: SecurityProfile }>("/v1/security/profile", {
    method: "PUT",
    body: JSON.stringify(body),
  });
  return json.data;
}

// ── Per-User Security Profile (td_user_security_profile) ─────

/** Fetch a specific user's security profile — GET /api/v1/security-profiles/user/{userId} */
export async function fetchUserSecurityProfile(userId: string): Promise<SecurityProfile & { levelName?: string; isDefault?: boolean }> {
  try {
    return await apiFetchData<SecurityProfile & { levelName?: string; isDefault?: boolean }>(
      `/api/v1/security-profiles/user/${userId}`
    );
  } catch (e) {
    console.warn("fetchUserSecurityProfile: backend unavailable", e);
    return { clearanceLevel: 0, linkedWorkstation: "", auditMode: "standard", sandboxMandatory: false };
  }
}

/** Update a specific user's security profile — PUT /api/v1/security-profiles/user/{userId} */
export async function updateUserSecurityProfile(
  userId: string,
  body: Partial<SecurityProfile>
): Promise<SecurityProfile & { levelName?: string }> {
  return apiFetchData<SecurityProfile & { levelName?: string }>(
    `/api/v1/security-profiles/user/${userId}`,
    { method: "PUT", body: JSON.stringify(body) }
  );
}

// ── Audit Logs ──────────────────────────────────────────
// 对接 AuditController (/api/v1/audit/logs)
export async function fetchAuditLogs(
  userId?: string,
  action?: string,
  resourceType?: string,
  page = 1,
  pageSize = 50
): Promise<{ data: AuditEvent[]; total: number; page: number; pageSize: number }> {
    const params = new URLSearchParams();
    if (userId) params.set('userId', userId);
    if (action) params.set('action', action);
    if (resourceType) params.set('resourceType', resourceType);
    params.set('page', String(page));
    params.set('pageSize', String(pageSize));
    try {
      return await apiFetchData(`/api/v1/audit/logs?${params.toString()}`);
    } catch (e) {
      // T1: catch must propagate — no silent empty return. Surface the
      // outage to the global NetworkErrorBanner, then rethrow a typed
      // NetworkError so the caller can render a real error state.
      notifyNetworkDown(e);
      throw toNetworkError(e, 'audit/logs');
    }
}

// ── Audit Stats ───────────────────────────────────────────
// 对接 AuditController: GET /api/v1/audit/stats
export async function fetchAuditStats(): Promise<AuditStats> {
  try {
    return await apiFetchData<AuditStats>('/api/v1/audit/stats');
  } catch (e) {
    console.warn("fetchAuditStats: backend unavailable", e);
    return { todayCount: 0, failureCount: 0, activeUsers: 0, anomalyIps: 0 };
  }
}

// ── Crypto Audit Logs ────────────────────────────────────
// 对接 CryptoAuditController: POST /api/v1/audit/crypto/record, GET /api/v1/audit/crypto/logs, GET /api/v1/audit/crypto/verify, GET /api/v1/audit/crypto/logs/{id}

export interface CryptAuditLog {
  id: number;
  eventType: string;
  resource: string;
  action: string;
  operatorId: string;
  payload?: string;
  hash: string;
  previousHash: string;
  timestamp: string;
  verified: boolean;
}

export interface CryptAuditLogPage {
  data: CryptAuditLog[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CryptAuditVerifyResult {
  intact: boolean;
  totalBlocks: number;
  tamperedBlocks: number[];
  message: string;
}

export async function fetchCryptAuditLogs(
  keyword?: string,
  page = 1,
  pageSize = 20
): Promise<CryptAuditLogPage> {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    try {
      return await apiFetchData<CryptAuditLogPage>(
        `/v1/audit/crypto/logs?${params.toString()}`
      );
    } catch (e) {
      // T1: same propagation rule as fetchAuditLogs — no silent empty
      // return. Surface the outage then rethrow a typed NetworkError.
      notifyNetworkDown(e);
      throw toNetworkError(e, 'audit/crypto/logs');
    }
}

export async function fetchCryptAuditVerify(): Promise<CryptAuditVerifyResult> {
  try {
    return await apiFetchData<CryptAuditVerifyResult>("/v1/audit/crypto/verify");
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Chain verification failed");
  }
}

export async function postCryptAuditRecord(body: {
  eventType: string;
  resource: string;
  action: string;
  operatorId: string;
  payload?: string;
}): Promise<CryptAuditLog> {
  try {
    return await apiFetch<CryptAuditLog>("/v1/audit/crypto/record", {
      method: "POST",
      body: JSON.stringify(body),
    });
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to post crypto audit record");
  }
}

export async function fetchCryptAuditLogById(id: number): Promise<CryptAuditLog> {
  try {
    return await apiFetch<CryptAuditLog>(`/v1/audit/crypto/logs/${id}`);
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to fetch crypto audit log");
  }
}

// ── Knowledge Graph ─────────────────────────────────────
export async function fetchKnowledgeGraph(): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  try {
    const resp = await apiFetch<{ success: boolean; data: { nodes: any[]; edges: any[] } }>("/knowledge/graph");
    if (resp.data && resp.data.nodes && resp.data.nodes.length > 0) {
      return {
        nodes: resp.data.nodes.map((n: any) => ({
          id: n.id,
          label: n.label,
          type: n.nodeType || n.type,
          nodeType: n.nodeType,
          description: n.description,
          propertiesJson: n.propertiesJson,
          properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
          createdAt: n.createdAt,
        })),
        edges: resp.data.edges.map((e: any) => ({
          id: e.id,
          source: e.sourceNodeId || e.source,
          target: e.targetNodeId || e.target,
          sourceNodeId: e.sourceNodeId,
          targetNodeId: e.targetNodeId,
          relationship: e.relationship,
          weight: e.weight,
        })),
      };
    }
    return (resp.data as any) || { nodes: [], edges: [] };
  } catch {
    return { nodes: [], edges: [] };
  }
}

// ── Object Explorer (Object Runtime) ─────────────────────────
const OBJ_BASE = "/api/v1/ecos/objects";

export interface ObjectData {
  id: string;
  entityCode: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  [key: string]: any;
}

export interface SchemaProperty {
  code: string;
  name: string;
  type: string;
  required: boolean;
  searchable: boolean;
}

export async function fetchObjects(entityCode: string, keyword?: string, page = 1, size = 50) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(size) });
  if (keyword) params.set("keyword", keyword);
  return apiFetchData<{ data: ObjectData[]; total: number; page: number; pageSize: number }>(`${OBJ_BASE}/${entityCode}?${params}`);
}

export async function searchObjects(q: string, entityCode?: string) {
  const params = new URLSearchParams({ q, pageSize: "50" });
  if (entityCode) params.set("entityCode", entityCode);
  return apiFetchData<{ data: ObjectData[]; total: number }>(`${OBJ_BASE}/search?${params}`);
}

export async function fetchObjectDetail(entityCode: string, id: string) {
  return apiFetchData<ObjectData & { relations: any[]; timeline: any[] }>(`${OBJ_BASE}/${entityCode}/${id}`);
}

export async function fetchObjectSchema(entityCode: string) {
  return apiFetchData<{ entityCode: string; entityName: string; properties: SchemaProperty[] }>(`${OBJ_BASE}/${entityCode}/schema`);
}

export async function createObject(entityCode: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}`, {
    method: "POST",
    body: JSON.stringify(properties),
  });
}

export async function updateObject(entityCode: string, id: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}`, {
    method: "PUT",
    body: JSON.stringify(properties),
  });
}

export async function changeObjectStatus(entityCode: string, id: string, status: string, override = false) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, override: String(override) }),
  });
}

export async function deleteObject(entityCode: string, id: string) {
  return apiFetchData<null>(`${OBJ_BASE}/${entityCode}/${id}`, { method: "DELETE" });
}

// ── Gap 1: Status transitions ──
export interface ObjectTransition {
  transitionCode: string;
  toStatus: string;
  transitionName: string;
  requireRole: string | null;
}

export interface AvailableTransitions {
  currentStatus: string;
  availableTransitions: ObjectTransition[];
}

export async function fetchAvailableTransitions(entityCode: string, id: string) {
  return apiFetchData<AvailableTransitions>(`${OBJ_BASE}/${entityCode}/${id}/transitions`);
}

export async function executeTransition(entityCode: string, id: string, transition: string, actor: string, comment?: string) {
  return apiFetchData<{ newStatus: string; [key: string]: any }>(`${OBJ_BASE}/${entityCode}/${id}/transition`, {
    method: "POST",
    body: JSON.stringify({ transition, actor, comment: comment || "" }),
  });
}

// ── Gap 2: Object relationships ──
export async function createObjectRelationship(
  entityCode: string,
  id: string,
  body: {
    targetObjectId: string;
    targetEntityCode: string;
    relationshipCode: string;
    relationshipType: string;
    properties?: Record<string, any>;
  }
) {
  return apiFetchData<any>(`${OBJ_BASE}/${entityCode}/${id}/relationships`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Gap 3: Timeline pagination ──
export interface TimelineEvent {
  id: string;
  eventType: string;
  eventDetail: any;
  operator: string;
  createdAt: string;
}

export interface TimelinePage {
  data: TimelineEvent[];
  total: number;
  page: number;
  size: number;
}

export async function fetchObjectTimeline(entityCode: string, id: string, page = 1, size = 20) {
  return apiFetchData<TimelinePage>(`${OBJ_BASE}/${entityCode}/${id}/timeline?page=${page}&size=${size}`);
}

// ── Agent Mesh ───────────────────────────────────────────────
const AGENT_MESH_BASE = "/api/v1/agent-mesh";

export interface AgentMeshAgent {
  id: string;
  name: string;
  role: string;
  description: string;
  systemPrompt: string;
  toolset: string;
  model: string;
  maxIterations: number;
  status: string;
}

export interface AgentMeshMission {
  id: string;
  title: string;
  description: string;
  mode: string;
  status: string;
  inputParams?: string;
  outputResult?: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface AgentMeshTask {
  id: string;
  missionId: string;
  seq: number;
  agentId: string;
  agentName: string;
  instruction: string;
  status: string;
  resultSummary?: string;
  resultDetail?: string;
  errorMessage?: string;
  durationMs?: number;
}

export async function fetchAgentMeshAgents(): Promise<AgentMeshAgent[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/agents`);
}

export async function fetchAgentMeshMissions(limit = 30): Promise<AgentMeshMission[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions?limit=${limit}`);
}

export async function fetchAgentMeshMission(id: string): Promise<{ mission: AgentMeshMission; tasks: AgentMeshTask[] }> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}`);
}

export async function fetchAgentMeshMissionTasks(id: string): Promise<AgentMeshTask[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/tasks`);
}

export async function createAgentMeshMission(body: any): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function executeAgentMeshMission(id: string): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/execute`, { method: "POST" });
}

// ── Workflow Designer ────────────────────────────────────────
const WF_BASE = "/api/v1/ecos/workflows";

export async function fetchWorkflows(pageSize = 50): Promise<{ data: any[]; total: number }> {
  return apiFetchData(`${WF_BASE}?pageSize=${pageSize}`);
}

export async function fetchWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`);
}

export async function createWorkflow(body?: any): Promise<any> {
  return apiFetchData(WF_BASE, {
    method: "POST",
    body: JSON.stringify(body || { name: "新建流程", code: "wf_" + Date.now(), workflowType: "APPROVAL" }),
  });
}

export async function updateWorkflow(id: string, body: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function publishWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/publish`, { method: "PATCH" });
}

export async function testWorkflow(id: string, body?: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/test`, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
}

// ── Pipeline Builder ──────────────────────────────────────────
const PIPELINE_BASE = "/api/v1/pipeline/definitions";

export interface PipelineDefinition {
  id?: string;
  name: string;
  description?: string;
  nodes: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges: { from: string; to: string; label?: string }[];
  createdAt?: string;
  updatedAt?: string;
  status?: string;
}

export interface PipelineExecution {
  id: string;
  pipelineId: string;
  status: "pending" | "running" | "success" | "failed";
  startedAt?: string;
  finishedAt?: string;
  logs?: string[];
  error?: string;
}

/** GET /api/pipeline/definitions — 列表 */
export async function fetchPipelines(pageSize = 50): Promise<{ data: PipelineDefinition[]; total: number }> {
  return apiFetchData(`${PIPELINE_BASE}?pageSize=${pageSize}`);
}

/** GET /api/pipeline/definitions/{id} — 详情 */
export async function fetchPipeline(id: string): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`);
}

/** POST /api/pipeline/definitions — 创建 */
export async function createPipeline(body: {
  name: string;
  description?: string;
  nodes?: { nodeId: string; type: string; config?: Record<string, any> }[];
  edges?: { from: string; to: string; label?: string }[];
}): Promise<PipelineDefinition> {
  return apiFetchData(PIPELINE_BASE, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** PUT /api/pipeline/definitions/{id} — 更新 */
export async function updatePipeline(id: string, body: Partial<PipelineDefinition>): Promise<PipelineDefinition> {
  return apiFetchData(`${PIPELINE_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** DELETE /api/pipeline/definitions/{id} — 删除 */
export async function deletePipeline(id: string): Promise<void> {
  await apiFetchData(`${PIPELINE_BASE}/${id}`, { method: "DELETE" });
}

/** POST /api/pipeline/definitions/{id}/execute — 执行 */
export async function executePipeline(id: string): Promise<PipelineExecution> {
  return apiFetchData(`${PIPELINE_BASE}/${id}/execute`, { method: "POST" });
}

/** GET /api/pipeline/executions/{id} — 执行状态 */
export async function getExecution(executionId: string): Promise<PipelineExecution> {
  return apiFetchData(`/api/v1/pipeline/executions/${executionId}`);
}

// ── Knowledge Search (Cognitive Operating System) ─────────────
const KNOWLEDGE_SEARCH_BASE = "/api/v1/knowledge";

export async function searchKnowledge(q: string): Promise<any> {
  return doFetch(`${KNOWLEDGE_SEARCH_BASE}/search?q=${encodeURIComponent(q)}`);
}

/** GET /api/knowledge/path?s=srcId&t=tgtId — find shortest path between two nodes */
export async function fetchKnowledgePath(s: string, t: string): Promise<{
  path: string[];
  edges: { id: string; source: string; target: string; relationship: string }[];
  length: number;
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/v1/knowledge/path?s=${encodeURIComponent(s)}&t=${encodeURIComponent(t)}`
  ).then(r => r.data);
}

/** GET /api/knowledge/neighbors/{id}?d=1 — get neighbors of a node */
export async function fetchKnowledgeNeighbors(id: string, d: number = 1): Promise<{
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}> {
  return apiFetch<{ success: boolean; data: any }>(
    `/v1/knowledge/neighbors/${encodeURIComponent(id)}?d=${d}`
  ).then(r => {
    const data = r.data || { nodes: [], edges: [] };
    return {
      nodes: (data.nodes || []).map((n: any) => ({
        id: n.id,
        label: n.label,
        type: n.nodeType || n.type,
        nodeType: n.nodeType,
        description: n.description,
        propertiesJson: n.propertiesJson,
        properties: (() => { try { return typeof n.propertiesJson === 'string' ? JSON.parse(n.propertiesJson) : n.propertiesJson; } catch { return {}; } })(),
        createdAt: n.createdAt,
      })),
      edges: (data.edges || []).map((e: any) => ({
        id: e.id,
        source: e.sourceNodeId || e.source,
        target: e.targetNodeId || e.target,
        sourceNodeId: e.sourceNodeId,
        targetNodeId: e.targetNodeId,
        relationship: e.relationship,
        weight: e.weight,
      })),
    };
  });
}

// ── Global Unified Search ──────────────────────────────────────

/** 后端 /api/portal/search 返回的条目 */
export interface SearchHit {
  type: string;   // OntologyEntity|Asset|Goal|Scenario|Object|Workflow|Pipeline|Knowledge|Agent
  id: string;
  name: string;
  url: string;    // SPA hash route, e.g. "/app/ontology-designer"
}

/**
 * 统一全局搜索 — 跨 Ontology / Asset / Goal / Scenario / Object
 * / Workflow / Pipeline / Knowledge / Agent 多表 ILIKE 检索。
 *
 * @param q    搜索关键词
 * @param type 搜索范围: all | ontology | asset | goal | scenario | object | workflow | pipeline | knowledge | agent
 */
export async function globalSearch(
  q: string,
  type: string = 'all'
): Promise<SearchHit[]> {
  return apiFetchData<SearchHit[]>(`/api/v1/portal/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}`);
}

// ── Biz Dashboard ──────────────────────────────────────────
export async function fetchBizDashboard(): Promise<any> {
  return apiFetchData('/api/v1/ecos/biz/dashboard');
}

// ── Goal Tracking ────────────────────────────────────────
// NOTE: 裸路径 /api/dq/goal-tracking → DqDashboardController (datanet:18082, P3-A)
//       不用 /api/v1/dq（DqGovernanceController 没有 goal-tracking 子路径）
export async function fetchGoalTracking(goalId?: number): Promise<any> {
  const qs = goalId ? `?goalId=${goalId}` : '';
  return apiFetchData(`/api/dq/goal-tracking${qs}`);
}

// ── ECOS Knowledge Graph ─────────────────────────────────
export async function fetchEcosKnowledgeGraph(): Promise<any> {
  return apiFetchData('/api/v1/knowledge/ecos-graph');
}

// ── Diagnostic Agent ──────────────────────────────────────
export async function callDiagnosticAgent(query: string): Promise<any> {
  return apiFetchData('/api/v1/agent/call', {
    method: "POST",
    body: JSON.stringify({ agent: "diagnostic", query }),
  });
}

// ── Entity Instances (Operational Apps) ──────────────────
export async function fetchEntityInstances(entityType: string): Promise<any[]> {
  try {
    return await apiFetchData<any[]>(`/api/v1/ontology/entities/${entityType}/instances`);
  } catch {
    return [];
  }
}
// ── Policy Engine (OPA Rego) ─────────────────────────────

export interface PolicyEngineStatus {
  engine: string;
  status: string;
  connected: boolean;
  opaLatency: string;
  policies: number;
  timestamp: number;
}

export interface PolicyEvalResult {
  allow: boolean;
  result: boolean;
  policy?: string;
  opaStatus?: number;
  details?: string;
}

/** GET /api/v1/policy-engine/policies — 获取所有策略名称列表 */
export async function fetchPolicyEnginePolicies(): Promise<string[]> {
  try {
    const resp = await apiFetch<{ data: string[] }>("/v1/policy-engine/policies");
    return resp.data || [];
  } catch (e) {
    console.warn("fetchPolicyEnginePolicies: backend unavailable", e);
    return [];
  }
}

/** GET /api/v1/policy-engine/policies/{name} — 获取指定策略的 Rego 源码 */
export async function fetchPolicyEnginePolicy(name: string): Promise<string> {
  try {
    const resp = await apiFetch<{ data: { name: string; content: string } }>(`/v1/policy-engine/policies/${encodeURIComponent(name)}`);
    return resp.data?.content || "";
  } catch (e: unknown) {
    throw new Error((e as { message?: string } | undefined)?.message || "Failed to fetch policy content");
  }
}

/** PUT /api/v1/policy-engine/policies/{name} — 更新 Rego 策略并热加载 */
export async function updatePolicyEnginePolicy(
  name: string,
  body: { content: string }
): Promise<void> {
  await apiFetch(`/v1/policy-engine/policies/${encodeURIComponent(name)}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/policy-engine/evaluate — 评估策略 */
export async function evaluatePolicyEngine(body: {
  policy: string;
  input: Record<string, any>;
}): Promise<PolicyEvalResult> {
  const resp = await apiFetch<{ data: { policy: string; allow: boolean; opaStatus: number } }>("/v1/policy-engine/evaluate", {
    method: "POST",
    body: JSON.stringify(body),
  });
  return {
    ...resp.data,
    result: resp.data.allow,
  };
}

/** GET /api/v1/policy-engine/status — OPA 连接状态 */
export async function fetchPolicyEngineStatus(): Promise<PolicyEngineStatus> {
  try {
    const resp = await apiFetch<{ data: { engine: string; status: string; opaLatency: string; policies: number; timestamp: number } }>("/v1/policy-engine/status");
    return {
      ...resp.data,
      connected: resp.data.status === "connected",
    };
  } catch (e) {
    console.warn("fetchPolicyEngineStatus: backend unavailable", e);
    throw e;
  }
}

// ── Digital Twin ─────────────────────────────────────────────
// 对接 DeviceTwinController (/api/twins)

export interface TwinHealth {
  mqtt: { status: string };
  device_count: number;
}

export interface TwinDevice {
  deviceId: string;
  name: string;
  type: string;
  unit: string;
  status: string;
}

export interface TwinTelemetry {
  deviceId: string;
  value: number;
  ts: string;
}

export interface TwinShadowState {
  desired: Record<string, any>;
  reported: Record<string, any>;
}

export interface TwinDeviceStatus {
  deviceId: string;
  status: string;
  shadow: TwinShadowState;
  telemetryCount: number;
}

export interface TwinCommandResult {
  status: string;
  deviceId: string;
  command: string;
  params: Record<string, any>;
}

export async function fetchTwinHealth(): Promise<TwinHealth> {
  try {
    return await apiFetchData<TwinHealth>('/api/v1/twins/health');
  } catch {
    console.warn('fetchTwinHealth: backend unavailable');
    return { mqtt: { status: 'DOWN' }, device_count: 0 };
  }
}

export async function fetchTwinDevices(): Promise<TwinDevice[]> {
  try {
    return await apiFetchData<TwinDevice[]>('/api/v1/twins/devices');
  } catch {
    console.warn('fetchTwinDevices: backend unavailable');
    return [];
  }
}

export async function fetchTwinTelemetry(deviceId: string, limit = 20): Promise<TwinTelemetry[]> {
  try {
    return await apiFetchData<TwinTelemetry[]>(`/api/v1/twins/${encodeURIComponent(deviceId)}/telemetry?limit=${limit}`);
  } catch {
    console.warn(`fetchTwinTelemetry(${deviceId}): backend unavailable`);
    return [];
  }
}

export async function sendTwinCommand(deviceId: string, command: string, params: Record<string, any> = {}): Promise<TwinCommandResult> {
  return apiFetchData<TwinCommandResult>(`/api/v1/twins/${encodeURIComponent(deviceId)}/command`, {
    method: 'POST',
    body: JSON.stringify({ command, params }),
  });
}

export async function fetchTwinDeviceStatus(deviceId: string): Promise<TwinDeviceStatus> {
  try {
    return await apiFetchData<TwinDeviceStatus>(`/api/v1/twins/${encodeURIComponent(deviceId)}/status`);
  } catch {
    console.warn(`fetchTwinDeviceStatus(${deviceId}): backend unavailable`);
    return { deviceId, status: 'offline', shadow: { desired: {}, reported: {} }, telemetryCount: 0 };
  }
}

// ── Cognitive Engine (S5-1.5) ──────────────────────────────────
const COGNITIVE_BASE = "/api/v1/cognitive";

/** POST /api/v1/cognitive/reason — 规则推理 / 因果分析 */
export async function apiCognitiveReason(body: {
  mode: string;
  facts: Record<string, any>;
  context?: Record<string, any>;
  options?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/reason`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** POST /api/v1/cognitive/optimize — 帕累托优化 */
export async function apiCognitiveOptimize(body: {
  problem: Record<string, any>;
  params?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/blueprint — 六层蓝图健康度 */
export async function apiCognitiveBlueprint(layer?: string): Promise<any> {
  const qs = layer ? `?layer=${encodeURIComponent(layer)}` : '';
  return apiFetchData(`${COGNITIVE_BASE}/blueprint${qs}`);
}

/** POST /api/v1/cognitive/plan — 创建执行计划 */
export async function apiCognitiveCreatePlan(body: {
  source: Record<string, any>;
  priority?: string;
  targets?: Record<string, any>;
}): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** GET /api/v1/cognitive/plan/{id} — 查询执行计划 */
export async function apiCognitiveGetPlan(id: string): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/plan/${encodeURIComponent(id)}`);
}

/** GET /api/v1/cognitive/health — 认知引擎健康检查 */
export async function apiCognitiveHealth(): Promise<any> {
  return apiFetchData(`${COGNITIVE_BASE}/health`);
}

