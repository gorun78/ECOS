/**
 * AI Workbench — API layer
 * Connects to ECOS backend controllers: AgentMesh, Guardrails, Pipeline, Models.
 *
 * PMO-43 P0 (2026-09-05 FE Check Report item 2): network/HTTP errors are
 * RE-THROWN so callers can surface explicit error + retry UI. apiFetchData
 * (src/api.ts) emits the `NetworkDownEvent` for the global NetworkErrorBanner;
 * this layer no longer swallows failures into empty arrays.
 * @license Apache-2.0
 */
import { apiFetchData } from '../../api';
import { API_BASE, rawStreamUrlFetch } from '../../services/httpClient';
// #145/B6-4：本体提案族基址单源（services/ontology/ontologyProposalApi 导出）；execute 子路径腿仍走 ${API_BASE} 保可见
import { ONTOLOGY_PROPOSALS_BASE } from '../../services/ontology/ontologyProposalApi';
// #145/B6-5：pipeline 定义族基址单源（属主 = api/dataClient 的导出常量）；走显式属主路径而非 src/api 门面，供守恒门钉来源
import { PIPELINE_DEFINITIONS_BASE } from '../../api/dataClient';
import { createSseParser, decodeFrameData, type SseRawEvent } from '../../services/sseParser';
import type { AIPAgent, AIPGuardrail, AIPLogicPipeline, AIPModel, AIPActionType, ExecuteActionResult, AIPAgentTemplate, AIPAgentMetrics, AIPAgentError, AIPAuditLog } from '../../types/aiworkbench';

/**
 * AIP 域基址单源：`/api/v1/aip/agents` 与 `/api/v1/aip/audit-logs`（#145/B6-3）。
 * 本域两族的裸拼/派生点只允许出现在这里，`services/aiworkbenchApi.ts` 一律 import 取值。
 * 子路径族 `/v1/aip/agents/templates`、`/v1/aip/agents/instantiate` 各只有本文件一个生产方，
 * 按 #145/B6-1 口径保持 `${API_BASE}` 原样，不并进常量（并进会让族面对棘轮隐形）。
 */
export const AIP_AGENTS_BASE = `${API_BASE}/v1/aip/agents`;
export const AIP_AUDIT_LOGS_BASE = `${API_BASE}/v1/aip/audit-logs`;

// ── AgentMesh ──────────────────────────────────────────────────

interface AgentMeshAgentRaw {
  id: string;
  name: string;
  role: string;
  description: string;
  systemPrompt: string;
  capability?: string;
  model: string;
  maxIterations: number;
  status: string;
  endpoint?: string;
  metadata?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * AgentMesh 网关路径（VersionPrefixRewriteFilter: /api/v1/agent-mesh/ → /api/agent-mesh/），
 * 支持 GET/POST/PUT/DELETE /agents（AgentMeshController，真实持久化到 ecos_agent_registry）。
 */
export async function fetchAIPAgentsFromMesh(): Promise<AIPAgent[]> {
  // PMO-43 T3: no silent catch — caller renders error + retry state
  const raw = await apiFetchData<AgentMeshAgentRaw[]>(`${API_BASE}/v1/agent-mesh/agents`);
  return (Array.isArray(raw) ? raw : []).map(convertMeshAgentToAIP);
}

function convertMeshAgentToAIP(raw: AgentMeshAgentRaw): AIPAgent {
  let systemPrompt = raw.systemPrompt || '';
  let actionIds: string[] = [];
  let functionIds: string[] = [];
  try {
    const parsed = JSON.parse(systemPrompt);
    if (parsed && typeof parsed === 'object') {
      if (typeof parsed.systemPrompt === 'string') systemPrompt = parsed.systemPrompt;
      const tools: string[] = Array.isArray(parsed.tools) ? parsed.tools : [];
      actionIds = tools.filter((t: string) => t.startsWith('act_'));
      functionIds = tools.filter((t: string) => t.startsWith('func_'));
    }
  } catch { /* plain text */ }
  // guardrailIds 存于 metadata JSON（{guardrails:[...]}），无上报则为空数组（不合成）
  let guardrailIds: string[] = [];
  try {
    const meta = raw.metadata ? JSON.parse(raw.metadata) : null;
    if (meta && Array.isArray(meta.guardrails)) guardrailIds = meta.guardrails as string[];
  } catch { /* plain metadata */ }

  return {
    id: raw.id,
    name: raw.name || raw.id,
    avatar: 'Bot',
    role: raw.role || 'assistant',
    description: raw.description || '',
    modelId: raw.model || 'gemini-1.5-pro',
    systemPrompt,
    assignedTools: { actionIds, functionIds },
    guardrailIds,
    status: (raw.status || '').toUpperCase() === 'ACTIVE' ? 'active' : 'development',
    lastModified: raw.updatedAt || raw.createdAt || new Date().toISOString(),
  };
}

/** AIPAgent → AgentMesh 注册实体（systemPrompt 内嵌 JSON 工具表、metadata 内嵌护栏，与转换器互逆） */
function toMeshAgentEntity(a: Partial<AIPAgent> & { id: string }): Record<string, unknown> {
  const tools = [
    ...(a.assignedTools?.actionIds ?? []),
    ...(a.assignedTools?.functionIds ?? []),
  ];
  return {
    id: a.id,
    name: a.name ?? '',
    role: a.role ?? 'assistant',
    description: a.description ?? '',
    systemPrompt: JSON.stringify({ systemPrompt: a.systemPrompt ?? '', tools }),
    model: a.modelId ?? 'gemini-1.5-pro',
    maxIterations: 10,
    status: a.status === 'development' ? 'INACTIVE' : 'ACTIVE',
    metadata: JSON.stringify({ guardrails: a.guardrailIds ?? [] }),
    capability: '{}',
    endpoint: '',
  };
}

/** 注册新 Agent — POST /api/v1/agent-mesh/agents（真实写 ecos_agent_registry） */
export async function createMeshAgent(input: Omit<Partial<AIPAgent>, 'id'> & { id?: string }): Promise<AIPAgent> {
  const body = { ...toMeshAgentEntity({ ...input, id: input.id || '' }), id: input.id || undefined };
  const created = await apiFetchData<AgentMeshAgentRaw>(`${API_BASE}/v1/agent-mesh/agents`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
  return convertMeshAgentToAIP(created);
}

/** 更新 Agent — PUT /api/v1/agent-mesh/agents/{id}（404 表示不存在，由 apiFetchData 抛出） */
export async function updateMeshAgent(id: string, patch: Partial<AIPAgent>): Promise<AIPAgent> {
  const updated = await apiFetchData<AgentMeshAgentRaw>(`${API_BASE}/v1/agent-mesh/agents/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(toMeshAgentEntity({ ...patch, id })),
  });
  return convertMeshAgentToAIP(updated);
}

/** 删除 Agent — DELETE /api/v1/agent-mesh/agents/{id} */
export async function deleteMeshAgent(id: string): Promise<void> {
  await apiFetchData<void>(`${API_BASE}/v1/agent-mesh/agents/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

// ── Guardrails ─────────────────────────────────────────────────

interface GuardrailPolicyRaw {
  id?: string;
  name: string;
  type?: string;
  description?: string;
  enabled?: boolean;
  severity?: string;
  parameters?: Record<string, unknown>;
}

export async function fetchGuardrailPolicies(): Promise<AIPGuardrail[]> {
  const data = await apiFetchData<GuardrailPolicyRaw[]>(`${API_BASE}/v1/guardrails/policies`);
  return (Array.isArray(data) ? data : []).map(convertPolicyToGuardrail);
}

function convertPolicyToGuardrail(raw: GuardrailPolicyRaw): AIPGuardrail {
  const typeMap: Record<string, AIPGuardrail['type']> = {
    pii_redaction: 'pii_redaction', PII_REDACTION: 'pii_redaction',
    hallucination_check: 'hallucination_check', HALLUCINATION_CHECK: 'hallucination_check',
    human_approval: 'human_approval', HUMAN_APPROVAL: 'human_approval',
    harm_filter: 'harm_filter', HARM_FILTER: 'harm_filter',
    compliance_eval: 'compliance_eval', COMPLIANCE_EVAL: 'compliance_eval',
  };
  const sevMap: Record<string, AIPGuardrail['severity']> = {
    block: 'block', BLOCK: 'block',
    warn: 'warn', WARN: 'warn',
    audit_only: 'audit_only', AUDIT_ONLY: 'audit_only',
  };
  const params = (raw.parameters || {}) as Partial<AIPGuardrail['parameters']>;
  return {
    id: raw.id || raw.name,
    name: raw.name,
    type: (raw.type && typeMap[raw.type]) || 'harm_filter',
    description: raw.description || '',
    isEnabled: raw.enabled !== false,
    severity: (raw.severity && sevMap[raw.severity]) || 'warn',
    parameters: {
      piiTypes: Array.isArray(params.piiTypes) ? params.piiTypes : undefined,
      confidenceThreshold: typeof params.confidenceThreshold === 'number' ? params.confidenceThreshold : undefined,
      requiredActionIds: Array.isArray(params.requiredActionIds) ? params.requiredActionIds : undefined,
      toxicThreshold: typeof params.toxicThreshold === 'number' ? params.toxicThreshold : undefined,
    },
  };
}

// ── Pipelines ──────────────────────────────────────────────────

interface PipelineDefinitionRaw {
  id?: string;
  name: string;
  description?: string;
  status?: string;
  [key: string]: unknown;
}

export async function fetchPipelineDefinitions(): Promise<AIPLogicPipeline[]> {
  const data = await apiFetchData<PipelineDefinitionRaw[]>(PIPELINE_DEFINITIONS_BASE);
  return (Array.isArray(data) ? data : []).map((p: PipelineDefinitionRaw) => ({
    id: p.id || p.name,
    name: p.name,
    description: p.description || '',
    status: (p.status as AIPLogicPipeline['status']) || 'draft',
    creator: (p as any).creator || 'system',
    lastUpdated: (p as any).lastUpdated || new Date().toISOString(),
    inputs: Array.isArray((p as any).inputs) ? (p as any).inputs : [],
    blocks: (Array.isArray((p as any).blocks) ? (p as any).blocks : []) as AIPLogicPipeline['blocks'],
  }));
}

// ── ActionTypes (Ontology) ─────────────────────────────────────

interface ActionTypeRaw {
  id?: string;
  name: string;
  objectTypeId: string;
  objectTypeName?: string;
  preconditions?: Record<string, unknown>[];
  postActions?: Array<{ type: string; params: Record<string, string> }>;
  auditEnabled?: boolean;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export async function fetchActionTypes(objectTypeId?: string): Promise<AIPActionType[]> {
  const url = objectTypeId
    ? `${API_BASE}/v1/ontology/actions?objectTypeId=${encodeURIComponent(objectTypeId)}`
    : `${API_BASE}/v1/ontology/actions`;
  const data = await apiFetchData<ActionTypeRaw[]>(url);
  return (Array.isArray(data) ? data : []).map(convertActionType);
}

function convertActionType(raw: ActionTypeRaw): AIPActionType {
  return {
    id: raw.id || raw.name,
    name: raw.name,
    objectTypeId: raw.objectTypeId,
    objectTypeName: raw.objectTypeName || raw.objectTypeId,
    preconditions: Array.isArray(raw.preconditions) ? raw.preconditions : [],
    postActions: Array.isArray(raw.postActions) ? raw.postActions : [],
    auditEnabled: raw.auditEnabled !== false,
    enabled: raw.enabled !== false,
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
}

export async function createActionType(data: {
  name: string;
  objectTypeId: string;
  preconditions?: Record<string, unknown>[];
  postActions?: Array<{ type: string; params: Record<string, string> }>;
  auditEnabled?: boolean;
}): Promise<AIPActionType> {
  const result = await apiFetchData<ActionTypeRaw>(`${API_BASE}/v1/ontology/actions`, {
    method: 'POST',
    body: JSON.stringify(data),
  });
  return convertActionType(result);
}

export async function updateActionType(id: string, data: {
  name?: string;
  objectTypeId?: string;
  preconditions?: Record<string, unknown>[];
  postActions?: Array<{ type: string; params: Record<string, string> }>;
  auditEnabled?: boolean;
}): Promise<AIPActionType> {
  const result = await apiFetchData<ActionTypeRaw>(`${API_BASE}/v1/ontology/actions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
  return convertActionType(result);
}

export async function deleteActionType(id: string): Promise<void> {
  await apiFetchData<void>(`${API_BASE}/v1/ontology/actions/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

export async function executeActionType(
  id: string,
  objectId: string,
  context?: Record<string, unknown>
): Promise<ExecuteActionResult> {
  return apiFetchData<ExecuteActionResult>(`${API_BASE}/v1/ontology/actions/${encodeURIComponent(id)}/execute`, {
    method: 'POST',
    body: JSON.stringify({ objectId, context }),
  });
}

export async function fetchAgentModels(): Promise<AIPModel[]> {
  // PMO-43 T1: mock fallback removed — re-throw so ModelCatalogView can
  // distinguish "backend down" from "no models registered".
  // §4.5/W204：缺失指标一律保持 undefined（UI 渲染「未知」），禁止 `||150`、
  // `||'$0.005'`、`||99.5` 之类的合成假指标。
  const raw = await apiFetchData<any[]>(`${API_BASE}/v1/aip/models`);
  return (Array.isArray(raw) ? raw : []).map((item: any) => ({
    id: item.id,
    displayName: item.name || item.displayName || item.id,
    provider: item.provider || 'On-Premises',
    type: item.modelType === 'embedding' ? 'embedding'
        : item.modelType === 'vision' ? 'vision'
        : item.modelType === 'audio' ? 'audio'
        : 'language',
    status: item.status === 'active' ? 'connected'
        : item.status === 'testing' ? 'testing'
        : 'offline',
    maxContext: item.maxContext ?? undefined,
    latencyMs: typeof item.latencyMs === 'number' ? item.latencyMs : undefined,
    costPerMillion: item.costPerMillion ?? undefined,
    inputCost: item.inputCost ?? undefined,
    outputCost: item.outputCost ?? undefined,
    healthRate: typeof item.healthRate === 'number' ? item.healthRate : undefined,
    temperature: typeof item.temperature === 'number' ? item.temperature : undefined,
  }));
}

// ── Agent Market (T9-1) ──────────────────────────────────────

export async function fetchAgentTemplates(): Promise<AIPAgentTemplate[]> {
  // PMO-43 T3: no silent catch — caller (AgentMarket) merges with BUILTIN
  // defaults and surfaces an inline error state when the backend is down.
  const raw = await apiFetchData<any[]>(`${API_BASE}/v1/aip/agents/templates`);
  return (Array.isArray(raw) ? raw : []).map((t: any) => ({
    id: t.id,
    name: t.name || t.id,
    icon: t.icon || 'Bot',
    description: t.description || '',
    model: t.model || 'gemini-1.5-pro',
    temperature: t.temperature || 0.7,
    maxIterations: t.maxIterations || 10,
    category: t.category || 'chat',
    isInstantiated: t.isInstantiated === true,
  }));
}

export async function instantiateAgent(
  templateId: string,
  name: string
): Promise<AIPAgent> {
  const data = await apiFetchData<any>(`${API_BASE}/v1/aip/agents/instantiate`, {
    method: 'POST',
    body: JSON.stringify({ templateId, name }),
  });
  return {
    id: data.id || `agent-${Date.now()}`,
    name: data.name || name,
    avatar: data.icon || 'Bot',
    role: data.role || 'assistant',
    description: data.description || '',
    modelId: data.model || 'gemini-1.5-pro',
    systemPrompt: data.systemPrompt || '',
    assignedTools: { actionIds: (data.tools || []) as string[], functionIds: [] as string[] },
    guardrailIds: (data.guardrails || []) as string[],
    status: 'active',
    lastModified: new Date().toISOString(),
  };
}

// ── Agent Manager (T9-2) ─────────────────────────────────────

export async function fetchManagedAgents(): Promise<AIPAgent[]> {
  const raw = await apiFetchData<any[]>(AIP_AGENTS_BASE);
  return (Array.isArray(raw) ? raw : []).map((a: any) => ({
    id: a.id,
    name: a.name || a.id,
    avatar: a.icon || 'Bot',
    role: a.role || 'assistant',
    description: a.description || '',
    modelId: a.model || 'gemini-1.5-pro',
    systemPrompt: a.systemPrompt || '',
    assignedTools: { actionIds: (a.tools || []) as string[], functionIds: [] as string[] },
    guardrailIds: (a.guardrails || []) as string[],
    status: (a.status || '').toUpperCase() === 'ACTIVE' ? 'active' : 'development',
    lastModified: a.updatedAt || a.createdAt || new Date().toISOString(),
  }));
}

export async function updateManagedAgent(
  id: string,
  data: { name?: string; systemPrompt?: string; model?: string; temperature?: number; maxIterations?: number }
): Promise<void> {
  await apiFetchData(`${AIP_AGENTS_BASE}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// §4.5（2026-10-13 检视报告）：原 toggleAgentStatus / fetchAgentVersions / rollbackAgent
// 指向后端不存在的映射（/agents/{id}/status、/versions、/rollback 均为 ghost 路径），
// 已删除。Agent 发布 / 版本回滚能力未上线——调用方应禁用相应控件并标注「能力未上线」。

// ── Agent Monitor (T9-3) ─────────────────────────────────────

/**
 * 获取 Agent 指标汇总
 * GET /api/v1/aip/agent-metrics/{agentId}（主路径，对齐后端 AgentMetricsController）
 * PMO-41: 由前端自承的别名路径 /api/v1/agent-metrics/... 改为主路径 /api/v1/aip/agent-metrics。
 * 字段对齐后端实际返回: agentId/totalCount/successCount/successRate/avgElapsedMs/p50Ms/p99Ms 等。
 */
export async function fetchAgentMetrics(agentId: string): Promise<AIPAgentMetrics> {
  const data = await apiFetchData<any>(`${API_BASE}/v1/aip/agent-metrics/${encodeURIComponent(agentId)}`);
  return {
    agentId: data.agentId || agentId,
    agentName: data.agentName || agentId,
    // 后端返回 totalCount，前端展示字段为 totalCalls
    totalCalls: data.totalCalls ?? data.totalCount ?? 0,
    successRate: data.successRate ?? 0,
    // 后端返回 avgElapsedMs / p99Ms，前端展示字段为 avgLatencyMs / p99LatencyMs
    avgLatencyMs: data.avgLatencyMs ?? data.avgElapsedMs ?? 0,
    p99LatencyMs: data.p99LatencyMs ?? data.p99Ms ?? 0,
    trend24h: Array.isArray(data.trend24h) ? data.trend24h : [],
    trend7d: Array.isArray(data.trend7d) ? data.trend7d : [],
    trend30d: Array.isArray(data.trend30d) ? data.trend30d : [],
    lastUpdated: data.lastUpdated || new Date().toISOString(),
  };
}

/**
 * 获取 Agent 错误/告警列表
 * GET /api/v1/aip/agent-metrics/{agentId}/errors（主路径，对齐后端 AgentMetricsController）
 * PMO-41: 由前端自承的别名路径 /api/v1/agent-metrics/... 改为主路径 /api/v1/aip/agent-metrics。
 * 后端返回 { agentId, total, errors: [...] }，errors 为数组。
 */
export async function fetchAgentErrors(agentId: string): Promise<AIPAgentError[]> {
  const resp = await apiFetchData<{ total?: number; errors?: any[] }>(
    `${API_BASE}/v1/aip/agent-metrics/${encodeURIComponent(agentId)}/errors`
  );
  const raw = Array.isArray(resp) ? resp : (resp?.errors ?? []);
  return (Array.isArray(raw) ? raw : []).map((e: any) => ({
    id: e.id || `err-${Date.now()}`,
    timestamp: e.timestamp || new Date().toISOString(),
    agentId: e.agentId || agentId,
    agentName: e.agentName || agentId,
    errorMessage: e.errorMessage || e.message || 'Unknown error',
    traceId: e.traceId || '',
    status: e.status || 'unresolved',
  }));
}

// ── OAG Playground (SSE) ──────────────────────────────
/**
 * OAG 流式调用处理器集合。
 * 各事件对应后端 SSE 推送的事件名（node / response / blocked / error / done）。
 */
export interface OagSseHandlers {
  /** SSE event:node — 节点执行进度 */
  onStep: (event: OagStepEvent) => void;
  /** SSE event:response — 模型最终回复（content + traceId） */
  onResponse: (data: { content: string; traceId: string }) => void;
  /** SSE event:done — 会话结束（traceId/sessionId/耗时/意图/安全判定） */
  onDone: (data: { traceId: string; sessionId: string; elapsedMs: number; status: string; intent?: string; securityPassed?: boolean }) => void;
  /** SSE event:blocked — 被安全护栏拦截 */
  onBlocked: (data: { reason: string; traceId: string }) => void;
  /** SSE event:error — 错误 */
  onError: (data: { message: string; traceId?: string }) => void;
}

/** OAG 步骤事件（event:node）的载荷结构；后端可能追加字段，故保留索引签名 */
export interface OagStepEvent {
  node?: string;
  status?: 'RUNNING' | 'DONE';
  nodeElapsedMs?: number;
  traceId?: string;
  intent?: string;
  securityPassed?: boolean;
  message?: string;
  // 后端追加字段
  [k: string]: unknown;
}

/** OAG playground 流式调用参数 */
interface OagPlaygroundParams {
  /** 选择的当前 Agent（前端逻辑用，后端 OAG 暂不消费） */
  agentId?: string;
  /** 复用已有会话 */
  sessionId?: string;
  /** 用户输入 */
  message: string;
  /** 模型标识 */
  model?: string;
  /** 采样温度 */
  temperature?: number;
  /** 最大 token 数 */
  maxTokens?: number;
  /** 用于取消流的中止信号 */
  abortSignal: AbortSignal;
  /** 事件处理器集合 */
  handlers: OagSseHandlers;
}

/**
 * OAG Playground SSE 流式调用 — POST /api/v1/oag/chat/stream。
 * 浏览器原生 EventSource 不支持 POST，因此用 fetch + ReadableStream。
 * 后端 SSE 事件名：node / response / blocked / error / done。
 */
export async function oagPlaygroundStream(params: OagPlaygroundParams): Promise<void> {
  // W206/C188：流式 URL 基址出自 httpClient API_BASE 单源；#143/B5-13 传输腿合流具名流式通道。
  // 头集合与改前 `authHeaders()` 逐键一致（Content-Type 是既有键，Authorization 由单源拼）。
  const res = await rawStreamUrlFetch(`${API_BASE}/v1/oag/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: params.message,
      model: params.model,
      temperature: params.temperature,
      maxTokens: params.maxTokens,
      sessionId: params.sessionId,
    }),
    signal: params.abortSignal,
  });
  if (!res.ok || !res.body) throw new Error(`OAG stream failed: ${res.status}`);
  await parseSseStream(res, params.handlers);
}

/**
 * Reusable SSE 流解析 — parse the EventSource `text/event-stream` format via ReadableStream。
 *
 * 线路级切分单源在 `services/sseParser`（D-B5-11 收口：此处此前自留 `indexOf('\n\n')`，
 * 对 Spring 默认写出的 `\r\n` 分隔一个事件都派发不出来 ⇒ 界面「跑完什么都没显示」）。
 * 本函数只负责把已闭合事件按 OAG 事件名派发进 handlers，不认识 SSE 行协议。
 */
export async function parseSseStream(res: Response, handlers: OagSseHandlers): Promise<void> {
  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  const parser = createSseParser();
  const dispatch = (evt: SseRawEvent) => {
    // 非 JSON 载荷按原文进 message 位（后端 error 事件的纯文本形态），不静默丢弃
    const data = (decodeFrameData(evt.data) ?? { message: evt.data }) as Record<string, unknown>;
    switch (evt.event) {
      case 'node': handlers.onStep(data as unknown as OagStepEvent); break;
      case 'response':
        handlers.onResponse({ content: (data.content as string) || '', traceId: (data.traceId as string) || '' });
        break;
      case 'blocked':
        handlers.onBlocked({ reason: (data.reason as string) || '', traceId: (data.traceId as string) || '' });
        break;
      case 'error':
        handlers.onError({ message: (data.message as string) || '', traceId: data.traceId as string | undefined });
        break;
      case 'done':
        handlers.onDone({
          traceId: (data.traceId as string) || '',
          sessionId: (data.sessionId as string) || '',
          elapsedMs: (data.elapsedMs as number) || 0,
          status: (data.status as string) || '',
          intent: data.intent as string | undefined,
          securityPassed: data.securityPassed as boolean | undefined,
        });
        break;
      default:
        // 契约外事件名整条忽略：禁把未知帧冒充已知帧渲染
        break;
    }
  };
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    for (const evt of parser.push(decoder.decode(value, { stream: true }))) dispatch(evt);
  }
  // 尾块未以空行闭合 ⇒ 由单源解析器丢弃（半截 JSON 绝不当事件消费）
  parser.end();
}

// ── Agent Session (复用已有 AgentSessionService) ──────

/** 已保存的 Agent 会话（来自后端 AgentSessionService） */
export interface AgentSessionSaved {
  id: string;
  agentId?: string;
  userId: string;
  tenantId?: string;
  status: 'ACTIVE' | 'EXPIRED';
  messageCount: number;
  createdAt: string;
  lastActiveAt: string;
}

/** 获取 Agent 会话列表（可按 agentId 过滤） — GET /api/v1/agent-loop/sessions */
export async function fetchAgentSessions(agentId?: string): Promise<AgentSessionSaved[]> {
  const url = agentId
    ? `${API_BASE}/v1/agent-loop/sessions?agentId=${encodeURIComponent(agentId)}`
    : `${API_BASE}/v1/agent-loop/sessions`;
  return apiFetchData<AgentSessionSaved[]>(url);
}

// ── Evals (AIP) — 真实端点 POST /api/v1/aip/eval/run + GET /api/v1/aip/eval/{id}/report ──
// 原 GET /api/v1/aip/evals/run?agentId=（fetchAgentEval）为 ghost 路径（后端不存在），
// 连同 try/catch→null 的吞错通道一并移除（§4.5 W204：评估必须走真实引擎）。

/** 评估单题结果（对齐 AgentEvalController itemToMap） */
export interface AipEvalItem {
  questionId: string;
  question: string;
  category: string;
  score: number;
  rawScore?: number;
  agentAnswer?: string;
  actualTool?: string;
  matchedKeywords?: string[];
  dimensions?: Record<string, number>;
  judgeReason?: string;
  durationMs?: number;
}

/** 评估报告（对齐 AgentEvalController GET /{id}/report 的 ApiResponse.data） */
export interface AipEvalReport {
  evalId: string;
  questionSet: string;
  status: string; // running | completed | failed
  totalQuestions?: number;
  totalScore?: number;
  avgScore?: number;
  grade?: string;
  totalDurationMs?: number;
  dimensionAvgs?: Record<string, number>;
  startedAt?: string;
  completedAt?: string;
  items?: AipEvalItem[];
}

/** 启动评估（异步）— POST /api/v1/aip/eval/run，立即返回 evalId */
export async function startAipEval(questionSet: 'default' | 'finance_20' | 'legal_20' | 'compliance_20'): Promise<{ evalId: string; questionSet: string }> {
  return apiFetchData<{ evalId: string; questionSet: string }>(`${API_BASE}/v1/aip/eval/run`, {
    method: 'POST',
    body: JSON.stringify({ questionSet }),
  });
}

/** 查询评估报告 — GET /api/v1/aip/eval/{id}/report；未就绪时后端 404 → 抛出，由调用方轮询/展示 */
export async function fetchAipEvalReport(evalId: string): Promise<AipEvalReport> {
  return apiFetchData<AipEvalReport>(`${API_BASE}/v1/aip/eval/${encodeURIComponent(evalId)}/report`);
}

// ── Agent Loop 对话（真实推理通道） ─────────────────────────────
// POST /api/v1/agent-loop/chat（stream=false → JSON）。
// ApiResponse.data 字段：success / content / totalTokens / toolCalls / errorMsg。

export interface AgentLoopToolCall {
  toolName?: string;
  success?: boolean;
  durationMs?: number;
  [k: string]: unknown;
}

export interface AgentLoopChatData {
  success?: boolean;
  content?: string;
  totalTokens?: number;
  toolCalls?: AgentLoopToolCall[];
  errorMsg?: string;
}

export async function agentLoopChat(params: { agentId: string; message: string; sessionId?: string }): Promise<AgentLoopChatData> {
  return apiFetchData<AgentLoopChatData>(`${API_BASE}/v1/agent-loop/chat`, {
    method: 'POST',
    body: JSON.stringify(params),
  });
}

// ── AIP 审计日志 — GET /api/v1/aip/audit-logs（AIPGuardrailController）──

interface AipAuditLogRaw {
  id?: string;
  event?: string;
  agentId?: string;
  user?: string;
  action?: string;
  result?: string;
  timestamp?: string;
  details?: { tokensUsed?: number; durationMs?: number } & Record<string, unknown>;
}

/**
 * 拉取真实审计日志并映射为 AIPAuditLog。
 * 后端仅上报 tokensUsed/durationMs（合并用量），未拆分 input/output tokens →
 * 两个字段记 0 并把真实上报值放进 details 文本（不合成、不拆假）。
 */
export async function fetchAIPAuditLogs(): Promise<AIPAuditLog[]> {
  const raw = await apiFetchData<AipAuditLogRaw[]>(AIP_AUDIT_LOGS_BASE);
  const statusMap: Record<string, AIPAuditLog['status']> = {
    success: 'allowed', SUCCESS: 'allowed', allowed: 'allowed',
    blocked: 'blocked', BLOCKED: 'blocked',
    flagged: 'flagged', FLAGGED: 'flagged',
    pending: 'pending_approval', PENDING: 'pending_approval',
  };
  return (Array.isArray(raw) ? raw : []).map((r) => ({
    id: r.id || `audit-${Date.now()}`,
    timestamp: r.timestamp || '',
    source: r.event || 'AIP',
    assetName: r.agentId || '',
    user: r.user || '',
    inputTokens: 0,
    outputTokens: 0,
    status: statusMap[String(r.result ?? '')] || 'allowed',
    actionTaken: r.action,
    details: `tokensUsed=${r.details?.tokensUsed ?? '-'} durationMs=${r.details?.durationMs ?? '-'}`,
    localOrigin: false,
  }));
}

// ── Ontology 提案（真实通道：/api/v1/ontology/proposals） ───────

/** 提案 VO 宽松形态：后端字段直出，前端只渲染实际存在的字段（无假回执）。 */
export interface OntologyProposalVo {
  id?: string;
  status?: string;
  actionId?: string;
  actionName?: string;
  agentId?: string;
  agentName?: string;
  payload?: Record<string, unknown>;
  proposedBy?: string;
  executionDetail?: string;
  transactionHash?: string;
  verificationMatrix?: Array<{ logicalField?: string; physicalCol?: string; expectedValue?: string; readbackValue?: string }>;
  [k: string]: unknown;
}

/** 提交提案 — POST /api/v1/ontology/proposals */
export async function submitOntologyProposal(body: {
  actionId: string;
  actionName: string;
  agentId: string;
  agentName: string;
  payload: Record<string, string>;
  proposedBy: string;
}): Promise<OntologyProposalVo> {
  return apiFetchData<OntologyProposalVo>(ONTOLOGY_PROPOSALS_BASE, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** 执行提案 — POST /api/v1/ontology/proposals/{id}/execute（非 PENDING 状态 409 → 抛出） */
export async function executeOntologyProposal(
  id: string,
  body: { userRole: string; userName: string }
): Promise<OntologyProposalVo> {
  return apiFetchData<OntologyProposalVo>(`${API_BASE}/v1/ontology/proposals/${encodeURIComponent(id)}/execute`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
