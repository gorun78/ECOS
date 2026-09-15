import type { KGNode, KGEdge, GlossaryTerm, GlossaryFilter, Domain } from '../../types/workbench';

export type { KGNode, KGEdge, GlossaryTerm, GlossaryFilter, Domain };

export interface MetadataAsset {
  id: string;
  source: 'integration' | 'ontology' | 'security';
  name: string;
  type: string;
  recordsOrFields: string;
  syncStatus: 'synced' | 'pending' | 'out_of_date';
  chunksCount: number;
  lastSynced: string;
}

export interface KnowledgeAsset {
  id: string;
  name: string;
  track: 'platform' | 'agent';
  sourceType: 'metadata' | 'lineage' | 'ontology' | 'business_object';
  status: 'draft' | 'syncing' | 'indexed' | 'ready' | 'error';
  chunkCount: number;
  vectorDim?: number;
  lastUpdated: string;
}

export interface KnowledgeGraphNode {
  id: string;
  label: string;
  type: string;
  domain: string;
  properties: Record<string, unknown>;
}

export interface KnowledgeGraphEdge {
  id: string;
  source: string;
  target: string;
  type: string;
  weight?: number;
}

export interface ClosedLoopConfig {
  id: string;
  name: string;
  sources: Array<{ track: 'platform' | 'agent'; sourceType: string; enabled: boolean }>;
  vectorModel: string;
  chunkSize: number;
  overlap: number;
  targetIndex: string;
  refreshCron?: string;
}

export interface KnowledgeSettings {
  defaultVectorModel: string;
  defaultChunkSize: number;
  defaultOverlap: number;
  neo4jEnabled: boolean;
  autoSyncEnabled: boolean;
  maxRetrievalResults: number;
}

export interface RagRequest {
  query: string;
  topK?: number;
  enableHyde?: boolean;
  rerankModel?: string;
}

export interface RagResult {
  answer: string;
  /** Whether the answer is produced by LLM (vs source excerpt) — PMO-54 */
  answerGenerated?: boolean;
  sources: Array<{
    title: string;
    type?: string;
    snippet: string;
    score: number;
    page?: number;
  }>;
  tokensUsed: number;
  overallConfidence?: number;
}

export interface SyncStatus {
  objectType: string;
  synced: number;
  unsynced: number;
  total: number;
  lastSyncTime: string | null;
  enabled: boolean;
}

export interface SyncLog {
  id: string;
  timestamp: string;
  objectType: string;
  operation: string;
  status: string;
  message: string;
  /** PMO-50 T2: kg_sync_log.job_id（V115 持久化后回填） */
  jobId?: string;
  /** PMO-50 T2: kg_sync_log.nodes */
  nodes?: number;
  /** PMO-50 T2: kg_sync_log.edges */
  edges?: number;
  /** PMO-50 T2: kg_sync_log.error_message */
  errorMessage?: string;
}

/**
 * PMO-54 — Graph build job returned by `GET /api/v1/knowledge/sync/jobs`
 */
export interface GraphBuildJob {
  jobId: string;
  type: 'FULL' | 'INCREMENTAL' | 'DRY_RUN';
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'ROLLED_BACK';
  sourceCounts?: number;
  targetCounts?: number;
  createdAt: string;
  updatedAt?: string;
  error?: string;
}

/** PMO-54 — Graph build preview payload */
export interface GraphBuildPreview {
  create: number;
  update: number;
  skip: number;
  samples?: Array<{ entityId: string; action: 'CREATE' | 'UPDATE' | 'SKIP' }>;
}

export interface RuleRepository {
  id: string;
  name: string;
  domain: string;
  status: 'DRAFT' | 'IN_REVIEW' | 'ACTIVE' | 'DEPRECATED';
  priority: number;
  version: number;
  description?: string;
  content?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RuleVersion {
  id: string;
  ruleId: string;
  version: number;
  changeLog: string;
  content?: string;
  createdBy?: string;
  createdAt: string;
}

// ── PMO-54 — knowledge ingestion queue ────────────────────────────────────────

export type ImportQueueStatus = 'queued' | 'parsing' | 'vectorizing' | 'done' | 'failed';

export interface ImportQueueItem {
  id: string;
  dsId: string;
  pipelineId?: string;
  label: string;
  status: ImportQueueStatus;
  progress?: number;
  errorMsg?: string;
  enrollmentAt: string;
}

// ── PMO-54 — knowledge eval ───────────────────────────────────────────────────

export interface EvalSeedQuery {
  id: string;
  question: string;
  labeledChunkIds?: string[];
}

export interface EvalReport {
  reportId: string;
  seedSetName: string;
  printedAt: string;
  recallAt5: number;
  mrrAt5: number;
  ndcgAt5: number;
  hallucinationRate?: number;
  citationRate?: number;
  degraded?: boolean;
}

// ── PMO-54 — knowledge lifecycle ──────────────────────────────────────────────

export const LIFECYCLE_STATES = ['draft', 'active', 'deprecated', 'archived'] as const;
export type LifecycleState = (typeof LIFECYCLE_STATES)[number];

export interface LifecycleAsset {
  id: string;
  name: string;
  type: string;
  state: LifecycleState;
  updatedAt: string;
  updatedBy?: string;
}

export interface LifecycleAuditEntry {
  id: string;
  assetId: string;
  from: LifecycleState;
  to: LifecycleState;
  operator: string;
  at: string;
}

// ── PMO-54 — engine config ─────────────────────────────────────────────────────

export type EngineConfigScope = 'pgvector' | 'neo4j' | 'llm' | 'task';

export interface EngineConfig {
  [scope: string]: string;
}

// ── Constants ─────────────────────────────────────────────────────────────────

export const RULE_STATUS_OPTIONS = ['DRAFT', 'IN_REVIEW', 'ACTIVE', 'DEPRECATED'] as const;

export const CHUNK_SIZE_OPTIONS = [256, 512, 1024, 2048] as const;

export const VECTOR_MODELS = [
  { id: 'text-embedding-3-small', dim: 1536, label: 'OpenAI Small' },
  { id: 'bge-large-zh-v1.5', dim: 1024, label: 'BGE zh-Large' },
  { id: 'text-embedding-004', dim: 256, label: 'Gemini Embedding' },
] as const;

/** PMO-54 — KB eval seed-query scoring scale (1–5) */
export const KB_EVAL_LEVELS = [1, 2, 3, 4, 5] as const;

// ── Tab structure ─────────────────────────────────────────────────────────────

export const KNOWLEDGE_TAB_GROUPS = [
  {
    id: 'overview',
    labelZh: '总览',
    label: 'Overview',
    tabs: [
      { id: 'overview' as const, labelZh: '总览', label: 'Overview', icon: 'LayoutDashboard' as const },
    ],
  },
  {
    id: 'ingest',
    labelZh: '知识摄入',
    label: 'Ingest',
    tabs: [
      { id: 'import' as const, labelZh: '数据导入', label: 'Data Import', icon: 'Download' as const },
      { id: 'upload' as const, labelZh: '知识抽取', label: 'Extraction Upload', icon: 'FileText' as const },
      { id: 'review' as const, labelZh: '抽取审核', label: 'Extraction Review', icon: 'ListChecks' as const },
    ],
  },
  {
    id: 'model',
    labelZh: '知识建模',
    label: 'Knowledge Model',
    tabs: [
      { id: 'ontology_model' as const, labelZh: '本体模型', label: 'Ontology Model', icon: 'Workflow' as const },
      { id: 'graph_build' as const, labelZh: '图谱构建', label: 'Graph Build', icon: 'Database' as const },
      { id: 'vector_index' as const, labelZh: '向量库', label: 'Vector Index', icon: 'Binary' as const },
      { id: 'classification' as const, labelZh: '分类体系', label: 'Classification', icon: 'Tag' as const },
    ],
  },
  {
    id: 'retrieval',
    labelZh: '检索与评估',
    label: 'Retrieval & Eval',
    tabs: [
      { id: 'rag' as const, labelZh: 'RAG 实验台', label: 'RAG Lab', icon: 'Zap' as const },
      { id: 'graph_explorer' as const, labelZh: '图谱探索', label: 'Graph Explorer', icon: 'Network' as const },
      { id: 'rules' as const, labelZh: '规则库', label: 'Rule Repository', icon: 'ShieldCheck' as const },
      { id: 'eval' as const, labelZh: '质量评测', label: 'Knowledge Eval', icon: 'Gauge' as const },
    ],
  },
  {
    id: 'operate',
    labelZh: '运营与合规',
    label: 'Operate & Comply',
    tabs: [
      { id: 'lifecycle' as const, labelZh: '生命周期', label: 'Lifecycle', icon: 'GitBranch' as const },
      { id: 'compliance' as const, labelZh: '合规检查', label: 'Compliance Check', icon: 'Shield' as const },
    ],
  },
  {
    id: 'config',
    labelZh: '系统配置',
    label: 'Configuration',
    tabs: [
      { id: 'engine_config' as const, labelZh: '引擎配置', label: 'Engine Config', icon: 'Settings' as const },
    ],
  },
] as const;

/** PMO-54 — Discriminated union of all tab ids — type-safe */
export type KnowledgeTabId = (typeof KNOWLEDGE_TAB_GROUPS)[number]['tabs'][number]['id'];

export const DEFAULT_SETTINGS: KnowledgeSettings = {
  defaultVectorModel: 'text-embedding-004',
  defaultChunkSize: 512,
  defaultOverlap: 50,
  neo4jEnabled: false,
  autoSyncEnabled: false,
  maxRetrievalResults: 5,
};
