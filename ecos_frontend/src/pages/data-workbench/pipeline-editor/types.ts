/**
 * Pipeline Editor — shared types
 * Extracted from PipelineFlowEditor.tsx
 * @license Apache-2.0
 */

import type { Node, Edge } from '@xyflow/react';

export type NodeStatus = 'idle' | 'running' | 'success' | 'error';

export interface TransformRule {
  id: string;
  column: string;
  function: string;
  params: string;
}

export interface JoinCondition {
  id: string;
  leftColumn: string;
  rightColumn: string;
  operator: string;
}

export interface NodeConfig {
  label: string;
  nodeType: string;
  sourceTable?: string;
  targetTable?: string;
  transformRules?: TransformRule[];
  joinType?: string;
  joinConditions?: JoinCondition[];
  leftSource?: string;
  rightSource?: string;
  aggregateGroupBy?: string[];
  aggregateFunctions?: { column: string; function: string; alias: string }[];
  nodeStatus?: NodeStatus;
  [key: string]: unknown;
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
