/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface ColumnDefinition {
  name: string;
  type: "string" | "number" | "date" | "boolean" | "double";
  nullable: boolean;
  description: string;
  primaryKey?: boolean;
  qualityScore: number; // 0-100 quality score for this particular field
}

export interface QualityRule {
  id: string;
  name: string;
  type: "null_check" | "duplicate_check" | "reference_integrity" | "outlier_detection";
  status: "pass" | "warn" | "fail";
  description: string;
}

export interface DatasetHistoryRecord {
  id: string;
  version: string;
  action: "Published" | "Modified" | "Schema Changed" | "Imported";
  timestamp: string;
  actor: string;
  summary: string;
}

export interface DataAsset {
  id: string;
  name: string;
  description: string;
  type: "dataset" | "ontology" | "pipeline" | "dashboard" | "source" | "view";
  owner: string;
  domain: string;
  tags: string[];
  status: "Healthy" | "Warning" | "Failed";
  qualityScore: number;
  rows: number;
  columns: number;
  storageSize: string;
  updatedAt: string;
  schema: ColumnDefinition[];
  qualityRules: QualityRule[];
  history: DatasetHistoryRecord[];
  permissions: {
    owner: string[];
    editor: string[];
    viewer: string[];
  };
}

// Lineage & Pipeline
export interface LineageNodeConfig {
  id: string;
  type: "source" | "dataset" | "pipeline" | "application" | "dashboard";
  label: string;
  status: "Healthy" | "Warning" | "Failed" | "Running" | "Idle" | "Queued";
  owner?: string;
  updatedAt?: string;
  rows?: string;
}

export interface LineageLink {
  id: string;
  source: string;
  target: string;
  animated?: boolean;
}

export interface PipelineNode {
  id: string;
  type: "dataset" | "transform" | "aggregate" | "publish";
  label: string;
  status: "Success" | "Running" | "Failed" | "Queued" | "Idle";
  config: {
    sqlSnippet?: string;
    targetName?: string;
    operationType?: string;
    params?: Record<string, string>;
  };
  x: number;
  y: number;
}

// Ontology
export interface PropertyDefinition {
  name: string;
  type: "string" | "number" | "date" | "boolean" | "enum";
  required?: boolean;
  searchable?: boolean;
  editable?: boolean;
  options?: string[]; // for enum
}

export interface RelationshipDefinition {
  name: string;
  targetEntity: string;
  cardinality: "one" | "many";
}

export interface ActionDefinition {
  id: string;
  label: string;
  workflowId?: string;
  description: string;
  impactLevel: "low" | "medium" | "high";
  fields: {
    name: string;
    label: string;
    type: "string" | "number" | "boolean" | "date";
    required: boolean;
    defaultValue?: any;
  }[];
}

export interface EntityDefinition {
  id: string; // e.g. "Customer"
  name: string; // Customer
  description?: string;
  properties: PropertyDefinition[];
  relationships: RelationshipDefinition[];
  actions: ActionDefinition[];
}

export interface EntityInstance {
  entityType: string; // "Customer", "Asset", "Facility"
  id: string;
  properties: Record<string, any>;
}

// Workflow definition
export interface WorkflowDefinition {
  id: string;
  name: string;
  trigger: string;
  status: "active" | "inactive";
  actions: {
    id: string;
    type: "updateStatus" | "notifyOwner" | "createAuditLog" | "triggerExternalApi";
    name: string;
    config: Record<string, any>;
  }[];
}

// Agent Studio (Phase 13 & 14)
export interface ToolDefinition {
  id: string;
  name: string;
  description: string;
  parameters: {
    name: string;
    type: string;
    description: string;
    required: boolean;
  }[];
}

export interface PromptTemplate {
  id: string;
  title: string;
  filename: string;
  content: string;
  version: string;
  category: "planning" | "summary" | "investigation" | "expert";
}

export interface AgentDefinition {
  id: string;
  name: string;
  role: string;
  goal: string;
  tools: string[]; // ToolDefinition IDs
  systemPrompt: string;
  capabilities: string[];
  model?: string; // LLM model name (from Agent Mesh)
}

export interface AgentTraceStep {
  id: string;
  type: "goal" | "plan" | "tool_call" | "evaluation" | "result" | "decision";
  timestamp: string;
  agentId: string;
  summary: string;
  detail?: string;
}

export interface AgentMessage {
  from: string;
  to: string;
  goal: string;
  payload: any;
  timestamp: string;
}

export interface AgentMetrics {
  successRate: number;
  latencyMs: number;
  tokensUsed: number;
  costUSD: number;
  toolCallsCount: number;
}

// Enterprise Cognitive Operating System (C2EOS - Phase 15)
export interface KnowledgeNode {
  id: string;
  label: string;
  nodeType?: string;       // 后端字段
  type?: string;            // 兼容旧mock
  description?: string;     // 后端字段
  propertiesJson?: string;  // 后端JSONB字段 (JSON string)
  properties?: Record<string, any>;  // 兼容旧mock（对象）
  createdAt?: string;
}

export interface KnowledgeEdge {
  id: string;
  sourceNodeId?: string;   // 后端字段
  targetNodeId?: string;   // 后端字段
  source?: string;          // 兼容旧mock
  target?: string;          // 兼容旧mock
  relationship: string;
  weight?: number;
}

export interface Goal {
  id: number | string;
  code?: string;
  name: string;
  description?: string;
  metrics: string[];
  target: string;
  status: "on_track" | "at_risk" | "behind" | "ACTIVE" | "COMPLETED" | "CANCELLED" | string;
  currentValue: number | string;
  targetValue: number | string;
  unit?: string;
  priority?: number;
  category?: string;
  parentId?: number | string | null;
  children?: Goal[];
  // v2 new fields
  goal_type?: "STRATEGIC" | "OKR" | "KPI" | "WORKFLOW" | "AGENT" | string;
  target_value?: number;
  current_value?: number;
  weight?: number;
  org_id?: string;
  owner_user_id?: string;
  start_date?: string;
  end_date?: string;
  linked_workflow_id?: string;
}

export interface CausalLink {
  id: number | string;
  sourceGoalId?: string;
  sourceGoalName?: string;
  targetGoalId?: string;
  targetGoalName?: string;
  relationshipType?: string;
  cause?: string;
  effect?: string;
  weight?: number;
  type?: "positive" | "negative";
  description?: string;
  // v2 fields for world-model
  sourceId?: number | string;
  targetId?: number | string;
  sourceType?: string;
  targetType?: string;
  relationType?: string;
  strength?: number;
  sourceNodeId?: number | string;
  targetNodeId?: number | string;
  relationship?: string;
}

export interface Scenario {
  id: number | string;
  name: string;
  description: string;
  assumptions?: {
    target: string;
    action: string;
    value: string;
  }[];
  outcomes?: {
    metric: string;
    change: string;
    value: string;
    impact: "positive" | "neutral" | "negative";
  }[];
  risksAdded?: string[];
  // v2 fields for world-model
  status?: string;
  probability?: number;
  impactScore?: number;
  impacts?: { goalId: number | string; projectedDelta: number }[];
}

export interface AuditEvent {
  eventId: string;
  eventType: string;  // AUTH, PERMISSION, DATA_ACCESS, CONFIG_CHANGE
  timestamp: string;
  userId: string;
  tenantId?: string;
  resource: string;
  action: string;
  result: string;  // SUCCESS, FAILURE
  ipAddress?: string;
  userAgent?: string;
  requestId?: string;
  duration?: number;
  details?: Record<string, any>;
}

export interface SecurityPolicy {
  id: string;
  role: string;
  objectType: string;
  permission: "read" | "write" | "admin";
  condition?: string; // e.g. "region = APAC"
}

// ── Datanet: 物理表注册 & 元数据采集 ──────────────────────────

export interface DataSource {
  datasourceId: string;
  datasourceName: string;
  datasourceType: string;
  connectionConfig: Record<string, any>;
  status: string;
  description?: string;
  host?: string;
  port?: number;
  databaseName?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DataResource {
  resourceId: string;
  resourceName: string;
  resourceType: string;
  sourcePath: string;
  fieldCount: number;
  datasourceId?: string;
  description?: string;
  schema?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DataField {
  fieldId: string;
  fieldName: string;
  dataType: string;
  dataLength: number;
  nullable: boolean;
  primaryKey: boolean;
  resourceId?: string;
  ordinalPosition?: number;
  defaultValue?: string;
  description?: string;
}

// WorkshopView types (from ceos_new)
export interface ObjectType {
  id: string; apiName: string; displayName: string; description: string;
  icon?: string; domainId?: string; properties?: any[];
}
export interface ActionType {
  id: string; apiName: string; displayName: string; description: string;
  icon?: string; parameters?: any[]; objectTypeId?: string;
}
export interface Dataset {
  id: string; name: string; description: string; objectTypeId?: string;
  columns?: { name: string; type: string }[];
  sampleData?: any[];
}
export interface LinkType {
  id: string; apiName: string; displayName: string;
  sourceObjectId?: string; targetObjectId?: string;
  sourceObjectTypeId?: string; targetObjectTypeId?: string;
}

// FunctionType (from ceos_new FunctionTypeView)
export interface FunctionParameter {
  name: string; type: string; required: boolean; description?: string;
}
export interface FunctionType {
  id: string; apiName: string; displayName: string; description: string;
  icon?: string; parameters?: FunctionParameter[]; returnType?: string;
  objectTypeId?: string;
}
