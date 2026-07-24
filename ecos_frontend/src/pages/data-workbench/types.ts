/**
 * Data Workbench — shared type definitions
 * Aligned with DataWorkbenchLayout.tsx actual usage patterns.
 * @license Apache-2.0
 */

export interface ObjectType {
  id: string;
  name: string;
  domain: string;
  displayName?: string;
  color?: string;
  icon?: string;
  properties?: Record<string, unknown>[];
}

export interface Dataset {
  id: string;
  name: string;
  path: string;
  columns?: string[];
  rowCount?: number;
  ontologyNodeId?: string;
}

export interface TableInfo {
  name: string;
  rowCount: number;
  columns: { name: string; type: string }[];
}

export interface DataConnection {
  id: string;
  name: string;
  type: 'postgresql' | 'mysql' | 'doris' | 's3' | 'sftp' | 'sap' | 'rest_api' | 'kafka' | 'mongodb';
  status: 'connected' | 'disconnected' | 'error' | 'pending' | 'testing';
  config: {
    host: string;
    port: number;
    database?: string;
    username?: string;
    schema?: string;
    warehouse?: string;
    bucket?: string;
    endpointUrl?: string;
    role?: string;
    lastTested?: string;
  };
  lastTested?: string;
  description?: string;
  category?: string;
  tablesAvailable: TableInfo[];
}

export interface DataSyncTask {
  id: string;
  name: string;
  sourceConnectionId: string;
  targetTable?: string;
  sourceTable?: string;
  targetDatasetId?: string;
  status: 'active' | 'paused' | 'running' | 'error' | 'completed' | 'success' | 'failed';
  schedule?: string;
  cronExpression?: string;
  lastRun?: string;
  lastRunTime?: string;
  rowsSynced?: number;
  recordsSynced?: number;
  errorMessage?: string;
  description?: string;
  syncMode?: 'full' | 'incremental' | 'cdc' | 'snapshot' | 'append';
  taskType?: 'TRANSFORM' | 'SYNC' | 'LAKE_EXPORT';
  durationMs?: number;
}

export interface PipelineNode {
  id: string;
  type: string;
  name?: string;
  recordCount?: number;
  outputColumns?: { name: string; type: string }[];
  left?: string;
  right?: string;
  join?: { type: string; on: string[] };
  inputs?: string[];
  config?: any;
}

export interface DataPipeline {
  id: string;
  name: string;
  status: 'active' | 'draft' | 'running' | 'success' | 'error';
  lastExecuted?: string;
  sourceConnections?: string[];
  targetDataset?: string;
  description?: string;
  expressionsCount?: number;
  computeEngine?: 'doris' | 'memory';
  nodes?: PipelineNode[];
}

export interface DataHealthCheck {
  id: string;
  name: string;
  status: 'ok' | 'warning' | 'error' | 'pending' | 'passed' | 'failed';
  message?: string;
  checkType: 'null_check' | 'range_check' | 'uniqueness' | 'freshness' | 'custom_sql' | 'row_count' | 'schema_check';
  lastChecked?: string;
  targetTable?: string;
  datasetId?: string;
  threshold?: string;
  config?: any;
}

// ── Pipeline Builder ──────────────────────────────────────────────

/** 转换规则 */
export interface TransformRule {
  id: string;
  functionName: string;
  column: string;
  args: string[];
  description: string;
}

/** ReactFlow 节点的 data 类型 */
export interface PipelineNodeData {
  nodeType: 'source' | 'transform' | 'join' | 'aggregate' | 'sink';
  label: string;
  status: 'idle' | 'running' | 'success' | 'error';
  sourceTable?: string;
  targetDataset?: string;
  transforms?: TransformRule[];
  joinCondition?: string;
  groupByColumns?: string[];
  engine?: 'memory' | 'doris';
}

/** 保存 Pipeline 时的数据格式 */
export interface PipelineSaveData {
  id?: string;
  name: string;
  description?: string;
  nodes: PipelineNodeData[];
  edges: {
    source: string;
    target: string;
    sourceHandle?: string;
    targetHandle?: string;
  }[];
  computeEngine: 'memory' | 'doris';
}

// ── Lineage ───────────────────────────────────────────────────────

/** 血缘节点 data 类型 */
export interface LineageNodeData {
  phase: 1 | 2 | 3 | 4 | 5;
  label: string;
  sublabel?: string;
  status?: string;
  metadata?: Record<string, unknown>;
}

/** 信息面板 tab 枚举 */
export type LineageInfoTab = 'info' | 'relationships' | 'transforms' | 'execution';
