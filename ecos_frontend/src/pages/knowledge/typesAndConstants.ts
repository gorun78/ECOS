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

export type EngineConfigScope = 'pgvector' | 'neo4j' | 'llm' | 'task' | 'extract';

/** K1 结构化抽取临时文件上传门禁（引擎配置 extract.allow_direct_upload 驱动） */
export interface ExtractUploadGate {
  allowed: boolean;
  hint: string;
}

/** K1 结构化（映射驱动）实例抽取报告 — 与 kb EntityInstanceExtractionReportVO 对齐 */
export interface StructuredExtractReport {
  mode: string;
  dryRun: boolean;
  ontologyId: string;
  ontologyCount: number;
  entityCount: number;
  nodeCreated: number;
  nodeUpdated: number;
  edgeCreated: number;
  nodeSkipped: number;
  invalidMappings: number;
  nextWatermark: string | null;
  durationMs: number;
  issues: { code: string; message: string }[];
}

/** K1 结构化抽取作业（列表行；jobId 缺失时为 null） */
export interface StructuredExtractJob {
  jobId: string | null;
  mode: string;
  status: string;
  startedAt: string | null;
  durationMs: number | null;
}

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

/**
 * 6 组 Tab（Wave 0 重构：知识抽取/融合/存储/更新收敛进「知识管理」，
 * 存储能力上提总览概要卡，引擎配置置底）：
 *   overview → 总览（横切：图谱概要 + 向量库概要 + 引擎状态）
 *   manage   → 知识管理（数据同步 / 知识萃取 / 知识融合 / 分类体系 / 知识更新）
 *   retrieve → 知识查询（K5）
 *   govern   → 知识治理（K6）
 *   config   → 引擎配置（横切，置底）
 *
 * 注：import / upload / vector_index / graph_build 从导航撤下（路由组件保留不删），
 * 其能力上提总览概要卡或删除入口由后续 Wave 承接；
 * ontology_model 属本体工作台（金 I）职责，不在此处。
 */
export const KNOWLEDGE_TAB_GROUPS = [
  {
    id: 'overview',
    tabs: [
      { id: 'overview' as const, icon: 'LayoutDashboard' as const },
    ],
  },
  {
    id: 'manage',
    tabs: [
      { id: 'datasync'       as const, icon: 'Database'   as const },
      { id: 'streaming'      as const, icon: 'Workflow'   as const },
      { id: 'review'         as const, icon: 'ListChecks' as const },
      { id: 'classification' as const, icon: 'Tag'        as const },
      { id: 'update'         as const, icon: 'RefreshCw'  as const },
    ],
  },
  {
    id: 'retrieve',
    tabs: [
      { id: 'rag'            as const, icon: 'Zap'     as const },
      { id: 'graph_explorer' as const, icon: 'Network' as const },
    ],
  },
  {
    id: 'govern',
    tabs: [
      { id: 'rules'      as const, icon: 'ShieldCheck' as const },
      { id: 'eval'       as const, icon: 'Gauge'       as const },
      { id: 'lifecycle'  as const, icon: 'GitBranch'   as const },
      { id: 'compliance' as const, icon: 'Shield'      as const },
    ],
  },
  {
    id: 'config',
    tabs: [
      { id: 'engine_config' as const, icon: 'Settings' as const },
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
