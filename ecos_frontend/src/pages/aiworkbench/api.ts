/**
 * AI Workbench — API layer
 * Connects to ECOS backend controllers: AgentMesh, Guardrails, Pipeline, Models.
 * Gracefully returns empty arrays on error so UI never whitescreens.
 * @license Apache-2.0
 */
import { apiFetchData } from '../../api';
import type { AIPAgent, AIPGuardrail, AIPLogicPipeline, AIPModel, AIPActionType, ExecuteActionResult, AIPAgentTemplate, AIPAgentMetrics, AIPAgentError, AIPAgentVersion } from '../../types/aiworkbench';

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

export async function fetchAIPAgentsFromMesh(): Promise<AIPAgent[]> {
  try {
    const raw = await apiFetchData<AgentMeshAgentRaw[]>('/api/v1/agent-mesh/agents');
    return (Array.isArray(raw) ? raw : []).map(convertMeshAgentToAIP);
  } catch (e) {
    console.warn('[ai-workbench] fetchAIPAgentsFromMesh failed', e);
    return [];
  }
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

  return {
    id: raw.id,
    name: raw.name || raw.id,
    avatar: 'Bot',
    role: raw.role || 'assistant',
    description: raw.description || '',
    modelId: raw.model || 'gemini-1.5-pro',
    systemPrompt,
    assignedTools: { actionIds, functionIds },
    guardrailIds: [],
    status: (raw.status || '').toUpperCase() === 'ACTIVE' ? 'active' : 'development',
    lastModified: raw.updatedAt || raw.createdAt || new Date().toISOString(),
  };
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
  try {
    const data = await apiFetchData<GuardrailPolicyRaw[]>('/api/v1/guardrails/policies');
    return (Array.isArray(data) ? data : []).map(convertPolicyToGuardrail);
  } catch (e) {
    console.warn('[ai-workbench] fetchGuardrailPolicies failed', e);
    return [];
  }
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
  try {
    const data = await apiFetchData<PipelineDefinitionRaw[]>('/api/v1/pipeline/definitions');
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
  } catch (e) {
    console.warn('[ai-workbench] fetchPipelineDefinitions failed', e);
    return [];
  }
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
  try {
    const url = objectTypeId
      ? `/api/v1/ontology/actions?objectTypeId=${encodeURIComponent(objectTypeId)}`
      : '/api/v1/ontology/actions';
    const data = await apiFetchData<ActionTypeRaw[]>(url);
    return (Array.isArray(data) ? data : []).map(convertActionType);
  } catch (e) {
    console.warn('[ai-workbench] fetchActionTypes failed', e);
    return [];
  }
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
  const result = await apiFetchData<ActionTypeRaw>('/api/v1/ontology/actions', {
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
  const result = await apiFetchData<ActionTypeRaw>(`/api/v1/ontology/actions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
  return convertActionType(result);
}

export async function deleteActionType(id: string): Promise<void> {
  await apiFetchData<void>(`/api/v1/ontology/actions/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

export async function executeActionType(
  id: string,
  objectId: string,
  context?: Record<string, unknown>
): Promise<ExecuteActionResult> {
  return apiFetchData<ExecuteActionResult>(`/api/v1/ontology/actions/${encodeURIComponent(id)}/execute`, {
    method: 'POST',
    body: JSON.stringify({ objectId, context }),
  });
}

export async function fetchAgentModels(): Promise<AIPModel[]> {
  try {
    const raw = await apiFetchData<any[]>('/api/v1/aip/models');
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
      maxContext: item.maxContext || '128K',
      latencyMs: item.latencyMs || 150,
      costPerMillion: item.costPerMillion || '$0.005',
      inputCost: item.inputCost || '$0.003',
      outputCost: item.outputCost || '$0.006',
      healthRate: item.healthRate || 99.5,
      temperature: item.temperature || 0.7,
    }));
  } catch (e) {
    console.warn('[ai-workbench] fetchAgentModels failed', e);
    return [];
  }
}

// ── Agent Market (T9-1) ──────────────────────────────────────

export async function fetchAgentTemplates(): Promise<AIPAgentTemplate[]> {
  try {
    const raw = await apiFetchData<any[]>('/api/v1/aip/agents/templates');
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
  } catch (e) {
    console.warn('[ai-workbench] fetchAgentTemplates failed', e);
    return [];
  }
}

export async function instantiateAgent(
  templateId: string,
  name: string
): Promise<AIPAgent> {
  const data = await apiFetchData<any>('/api/v1/aip/agents/instantiate', {
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
  try {
    const raw = await apiFetchData<any[]>('/api/v1/aip/agents');
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
  } catch (e) {
    console.warn('[ai-workbench] fetchManagedAgents failed', e);
    return [];
  }
}

export async function updateManagedAgent(
  id: string,
  data: { name?: string; systemPrompt?: string; model?: string; temperature?: number; maxIterations?: number }
): Promise<void> {
  await apiFetchData(`/api/v1/aip/agents/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function toggleAgentStatus(id: string, status: 'active' | 'development'): Promise<void> {
  await apiFetchData(`/api/v1/aip/agents/${encodeURIComponent(id)}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status: status === 'active' ? 'ACTIVE' : 'INACTIVE' }),
  });
}

export async function fetchAgentVersions(agentId: string): Promise<AIPAgentVersion[]> {
  try {
    const raw = await apiFetchData<any[]>(`/api/v1/aip/agents/${encodeURIComponent(agentId)}/versions`);
    return (Array.isArray(raw) ? raw : []).map((v: any) => ({
      id: v.id,
      agentId: v.agentId || agentId,
      version: v.version || 0,
      config: typeof v.config === 'string' ? v.config : JSON.stringify(v.config || {}),
      createdAt: v.createdAt || new Date().toISOString(),
    }));
  } catch (e) {
    console.warn('[ai-workbench] fetchAgentVersions failed', e);
    return [];
  }
}

export async function rollbackAgent(agentId: string, version: number): Promise<void> {
  await apiFetchData(`/api/v1/aip/agents/${encodeURIComponent(agentId)}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  });
}

// ── Agent Monitor (T9-3) ─────────────────────────────────────

export async function fetchAgentMetrics(agentId: string): Promise<AIPAgentMetrics> {
  try {
    const data = await apiFetchData<any>(`/api/v1/agent-metrics/${encodeURIComponent(agentId)}`);
    return {
      agentId: data.agentId || agentId,
      agentName: data.agentName || agentId,
      totalCalls: data.totalCalls || 0,
      successRate: data.successRate || 0,
      avgLatencyMs: data.avgLatencyMs || 0,
      p99LatencyMs: data.p99LatencyMs || 0,
      trend24h: Array.isArray(data.trend24h) ? data.trend24h : [],
      trend7d: Array.isArray(data.trend7d) ? data.trend7d : [],
      trend30d: Array.isArray(data.trend30d) ? data.trend30d : [],
      lastUpdated: data.lastUpdated || new Date().toISOString(),
    };
  } catch (e) {
    console.warn('[ai-workbench] fetchAgentMetrics failed', e);
    throw e;
  }
}

export async function fetchAgentErrors(agentId: string): Promise<AIPAgentError[]> {
  try {
    const raw = await apiFetchData<any[]>(`/api/v1/agent-metrics/${encodeURIComponent(agentId)}/errors`);
    return (Array.isArray(raw) ? raw : []).map((e: any) => ({
      id: e.id || `err-${Date.now()}`,
      timestamp: e.timestamp || new Date().toISOString(),
      agentId: e.agentId || agentId,
      agentName: e.agentName || agentId,
      errorMessage: e.errorMessage || e.message || 'Unknown error',
      traceId: e.traceId || '',
      status: e.status || 'unresolved',
    }));
  } catch (e) {
    console.warn('[ai-workbench] fetchAgentErrors failed', e);
    return [];
  }
}
