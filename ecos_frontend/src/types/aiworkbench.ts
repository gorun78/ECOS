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
