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
  | 'SOURCE_MINIO'
  | 'TRANSFORM_SQL'
  | 'TRANSFORM_UDF'
  | 'TRANSFORM_DOC_PARSE'
  | 'JOIN'
  | 'SINK'
  | 'SINK_MINIO'
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
    // ── TRANSFORM_UDF (Wave 5) ──
    udfId?: string;
    udfName?: string;
    params?: Record<string, unknown>;
    // ── TRANSFORM_DOC_PARSE (B6-2 文档解析节点) ──
    /** 分块大小，取值 ∈ {256,512,1024,2048}，默认 512 */
    chunkSize?: number;
    /** 分块重叠，须 ≥0 且 < chunkSize，默认 64 */
    chunkOverlap?: number;
    // ── JOIN (Wave 5) ──
    joinKeys?: string[];
    joinType?: 'inner' | 'left' | 'right' | 'full' | 'cross';
    leftNode?: string;
    rightNode?: string;
    on?: Array<{ left: string; right: string }>;
    // ── SINK (Wave 5) ──
    targetDatasourceId?: string;
    targetTable?: string;
    target_columns?: string;
    // ── SINK_MINIO (数据采集 → 数据湖近源库) ──
    bucket?: string;
    objectName?: string;
    table?: string;
    format?: 'csv';
    columns?: string[];
    // ── SOURCE_MINIO (数据湖近源层 → 管道，B6-1) ──
    /** 近源区：STRUCTURED（默认）/ UNSTRUCTURED */
    zone?: 'STRUCTURED' | 'UNSTRUCTURED';
    /** 对象 key 的 {source} 段（可空，回退 DAG 内 SOURCE_JDBC 的 datasourceId） */
    source?: string;
    /** 非结构化文档 ID */
    docId?: string;
    /** 非结构化原始文件名 */
    originalFileName?: string;
    /** 指定分区日（YYYY-MM-DD，仅结构化） */
    dt?: string;
    // ── OUTPUT_OBJECT ──
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
  onEngineChange?: (engine: 'memory' | 'doris') => void;
  editingPipeline?: import('../types').DataPipeline | null;
  onBack?: () => void;
}

export interface JoinCondition {
  id: string;
  leftColumn: string;
  operator: string;
  rightColumn: string;
}

export interface TransformRule {
  id: string;
  name: string;
  type: string;
  expression?: string;
  enabled: boolean;
  /** 函数名 (Wave-9 extend — 之前缺失字段导致表达编辑器无法落表达式) */
  function?: string;
  /** 函数参数 (Wave-9 extend) */
  params?: string;
  /** 目标列名 (Wave-9 extend) */
  column?: string;
}
