/**
 * AI Workbench — API layer
 * Connects to ECOS backend controllers: AgentMesh, Guardrails, Pipeline, Models.
 * Gracefully returns empty arrays on error so UI never whitescreens.
 * @license Apache-2.0
 */
import { apiFetchData } from '../../api';
import type { AIPAgent, AIPGuardrail, AIPLogicPipeline, AIPModel } from '../../types/aiworkbench';

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
    const raw = await apiFetchData<AgentMeshAgentRaw[]>('/api/agent-mesh/agents');
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

// ── Models ─────────────────────────────────────────────────────

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
