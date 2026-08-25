/**
 * Pipeline Editor — shared types
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 Pipeline node type enumeration (PMO-3J T1).
 * @license Apache-2.0
 */

import type { Node, Edge } from '@xyflow/react';

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

export interface PipelineData {
  id?: string;
  name: string;
  description?: string;
  nodes: Node[];
  edges: Edge[];
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
