/**
 * logic-canvas 共享类型 — 由 LogicView.tsx 机械抽取（H6-T4），定义与原文逐行一致。
 * @license Apache-2.0
 */
import type { Node, Edge } from '@xyflow/react';
import type { LogicNodeData, LogicEdgeData } from '../../../types/aiworkbench';

export type HistoryEntry = { nodes: Node<LogicNodeData>[]; edges: Edge<LogicEdgeData>[] };

/** 节点 trace：来自后端 executions 端点的节点级别执行数据（P2 后端无该字段时为空） */
export interface NodeTrace {
  input?: string;
  output?: string;
  latencyMs?: number;
  errorMessage?: string;
}

/** 后端 PipelineExecution 的节点状态映射（后端仅 running/completed/failed 三种）*/
export type BackendRunStatus = 'running' | 'completed' | 'failed';

export interface BackendPipelineExec {
  executionId: string;
  pipelineId: string;
  status: BackendRunStatus;
  startedAt: string;
  completedAt?: string;
  result?: { message?: string; pipelineId?: string; params?: Record<string, unknown> };
}
