/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface AIPLogicBlock {
  id: string;
  type: 'input' | 'query_ontology' | 'llm' | 'ontology_action' | 'output';
  name: string;
  config: {
    variableName?: string;
    dataType?: string;
    queryTarget?: string;
    queryFilter?: string;
    modelId?: string;
    systemPrompt?: string;
    userPromptTemplate?: string;
    temperature?: number;
    actionTypeId?: string;
    actionMapping?: Record<string, string>;
    outputSchema?: string;
  };
}

export interface AIPLogicPipeline {
  id: string;
  name: string;
  description: string;
  status: 'active' | 'draft' | 'deprecated';
  creator: string;
  lastUpdated: string;
  inputs: Array<{ name: string; type: string; placeholder?: string }>;
  blocks: AIPLogicBlock[];
  testInputs?: Record<string, string>;
}

export interface AIPAgent {
  id: string;
  name: string;
  avatar: string; // Lucide icon name
  role: string;
  description: string;
  modelId: string;
  systemPrompt: string;
  assignedTools: {
    actionIds: string[];
    functionIds: string[];
  };
  guardrailIds: string[];
  status: 'active' | 'development';
  lastModified: string;
}

export interface AIPModel {
  id: string;
  displayName: string;
  /** 历史/兼容字段（displayName 的别名） */
  name?: string;
  provider: 'Google' | 'Anthropic' | 'OpenAI' | 'Meta' | 'On-Premises';
  type: 'language' | 'vision' | 'embedding' | 'audio';
  status: 'connected' | 'testing' | 'offline';
  maxContext: string;
  latencyMs: number;
  costPerMillion: string;
  inputCost: string;
  outputCost: string;
  healthRate: number;
  temperature: number;
}

export interface AIPGuardrail {
  id: string;
  name: string;
  type: 'pii_redaction' | 'hallucination_check' | 'human_approval' | 'harm_filter' | 'compliance_eval';
  description: string;
  isEnabled: boolean;
  severity: 'block' | 'warn' | 'audit_only';
  parameters: {
    piiTypes?: string[];
    confidenceThreshold?: number;
    requiredActionIds?: string[];
    toxicThreshold?: number;
  };
}

export interface AIPAuditLog {
  id: string;
  timestamp: string;
  source: string; // e.g., 'Agent Studio', 'Logic Pipeline'
  assetName: string;
  user: string;
  inputTokens: number;
  outputTokens: number;
  status: 'allowed' | 'blocked' | 'flagged' | 'pending_approval';
  actionTaken?: string;
  details: string;
}

export interface AIPPostAction {
  type: string;
  params: Record<string, string>;
}

export interface AIPActionType {
  id: string;
  name: string;
  objectTypeId: string;
  objectTypeName: string;
  preconditions: Record<string, unknown>[];
  postActions: AIPPostAction[];
  auditEnabled: boolean;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecuteActionResult {
  success: boolean;
  auditId?: string;
  preconditionResults: Array<{ key: string; passed: boolean; message: string }>;
  changes: Array<{ field: string; before: string; after: string }>;
  postActionStatuses: Array<{ type: string; status: 'success' | 'failed'; message: string }>;
}

// ── Evaluation Types ────────────────────────────────────────────

export interface EvaluationQuestion {
  id: string;
  question: string;
  category: string;
}

export interface EvaluationQuestionSet {
  id: string;
  name: string;
  description: string;
  questions: EvaluationQuestion[];
}

export interface EvaluationResult {
  questionId: string;
  question: string;
  category: string;
  score: number;
  status: 'pending' | 'running' | 'completed' | 'failed';
  response?: string;
  latency?: number;
}

export interface EvaluationSession {
  id: string;
  agentId: string;
  questionSetId: string;
  status: 'idle' | 'running' | 'completed';
  progress: number;
  results: EvaluationResult[];
  radarScores: Record<string, number>;
}

// ── Agent Platform Types (PMO-17) ────────────────────────────

export interface AIPAgentTemplate {
  id: string;
  name: string;
  icon: string;
  description: string;
  model: string;
  temperature: number;
  maxIterations: number;
  category: 'chat' | 'workflow' | 'retrieval' | 'codegen' | 'vision' | 'data';
  isInstantiated: boolean;
}

export interface AIPAgentMetrics {
  agentId: string;
  agentName: string;
  totalCalls: number;
  successRate: number;
  avgLatencyMs: number;
  p99LatencyMs: number;
  // 24h/7d/30d trend data points
  trend24h: number[];
  trend7d: number[];
  trend30d: number[];
  lastUpdated: string;
}

export interface AIPAgentError {
  id: string;
  timestamp: string;
  agentId: string;
  agentName: string;
  errorMessage: string;
  traceId: string;
  status: 'unresolved' | 'investigating' | 'resolved';
}

export interface AIPAgentVersion {
  id: string;
  agentId: string;
  version: number;
  config: string; // JSON string of the full agent config
  createdAt: string;
}

// ── Logic Canvas Types (PMO-18) ────────────────────────────

export type LogicNodeType = 'llm' | 'tool' | 'ontology' | 'approval' | 'condition' | 'trigger';

export type LogicNodeStatus = 'idle' | 'running' | 'success' | 'error';

export interface LogicLLMConfig {
  model: string;
  temperature: number;
  maxTokens: number;
  systemPrompt: string;
}

export interface LogicToolConfig {
  toolName: string;
  parameters: string; // JSON string
}

export interface LogicOntologyConfig {
  objectType: string;
  queryType: 'get' | 'list' | 'search' | 'query';
  filter: string;
}

export interface LogicApprovalConfig {
  approver: string;
  timeout: number; // seconds
}

export interface LogicConditionConfig {
  conditionExpr: string; // JSONPath
  thenBranch: string;
  elseBranch: string;
}

export interface LogicTriggerConfig {
  cronExpr: string;
  timezone: string;
}

export type LogicNodeConfig =
  | LogicLLMConfig
  | LogicToolConfig
  | LogicOntologyConfig
  | LogicApprovalConfig
  | LogicConditionConfig
  | LogicTriggerConfig;

export interface LogicNodeData {
  type: LogicNodeType;
  label: string;
  status: LogicNodeStatus;
  duration?: number;
  config: LogicNodeConfig;
}

export interface LogicEdgeData {
  condition?: string;
}
