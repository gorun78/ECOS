/**
 * Pipeline Editor — shared types
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 Pipeline node type enumeration (PMO-3J T1).
 * @license Apache-2.0
 */

/** P2-01 node type enumeration — the only standard for pipeline node types. */
export type PipelineNodeType =
  | 'SOURCE_JDBC'
  | 'SOURCE_CSV'
  | 'SOURCE_REST'
  | 'SOURCE_CDC'
  | 'TRANSFORM_SQL'
  | 'OUTPUT_OBJECT';

export type NodeStatus = 'idle' | 'running' | 'success' | 'error';

/**
 * NodeConfig — aligned with P2-01 Pipeline schema.
 * The `config` object holds per-node-type fields; only the fields
 * relevant to a given `nodeType` are populated.
 */
export interface NodeConfig {
  label: string;
  /** P2-01 enumeration value (see PipelineNodeType). */
  nodeType: PipelineNodeType;
  config: {
    // ── SOURCE_JDBC ──
    datasourceId?: string;
    sql?: string;
    fetchSize?: number;
    incrementalColumn?: string;
    lastSyncValue?: string;
    // ── SOURCE_CSV ──
    filePath?: string;
    delimiter?: string;
    header?: boolean;
    encoding?: string;
    // ── SOURCE_REST ──
    url?: string;
    method?: string;
    headers?: Record<string, string>;
    body?: string;
    pagination?: string;
    // ── TRANSFORM_SQL ──
    transformSql?: string;
    timeout?: number;
    // ── OUTPUT_OBJECT ──
    targetTable?: string;
    mode?: 'append' | 'overwrite';
    batchSize?: number;
  };
  /** Upstream node ids this node depends on (derived from edges). */
  dependsOn?: string[];
  nodeStatus?: NodeStatus;
}

/**
 * Save payload sent to the backend via onSave.
 * `nodes`/`edges` are already in the backend PipelineNode / dependency-edge
 * shape (PMO-3J T3) — the parent forwards them to api.createPipeline /
 * api.updatePipeline / api.savePipelineDefinition.
 */
export interface PipelineSaveNode {
  id: string;
  nodeId: string;
  type: string; // P2-01 enumeration value
  config: Record<string, unknown>;
  positionX: number;
  positionY: number;
}

export interface PipelineSaveEdge {
  from: string;
  to: string;
}

export interface PipelineData {
  id?: string;
  name: string;
  description?: string;
  nodes: PipelineSaveNode[];
  edges: PipelineSaveEdge[];
  computeEngine: 'memory' | 'doris';
}

export interface PipelineFlowEditorProps {
  connections: import('../types').DataConnection[];
  pipelines: import('../types').DataPipeline[];
  onSave: (pipeline: PipelineData) => void;
  onExecute: (pipelineId: string) => void;
  showToast?: (type: 'success' | 'error' | 'info', msg: string) => void;
  computeEngine: 'memory' | 'doris';
  onEngineChange: (engine: 'memory' | 'doris') => void;
  editingPipeline?: import('../types').DataPipeline | null;
  onBack?: () => void;
}
