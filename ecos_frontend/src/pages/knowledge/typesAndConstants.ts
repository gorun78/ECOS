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

/** 菜单文案统一走 i18n：分组 t('knowledge.group.*') / 子项 t('knowledge.nav.*') */
export const KNOWLEDGE_TAB_GROUPS = [
  {
    id: 'overview',
    tabs: [
      { id: 'overview' as const, icon: 'LayoutDashboard' as const },
    ],
  },
  {
    id: 'ingest',
    tabs: [
      { id: 'import' as const, icon: 'Download' as const },
      { id: 'upload' as const, icon: 'FileText' as const },
      { id: 'review' as const, icon: 'ListChecks' as const },
    ],
  },
  {
    id: 'model',
    tabs: [
      { id: 'ontology_model' as const, icon: 'Workflow' as const },
      { id: 'graph_build' as const, icon: 'Database' as const },
      { id: 'vector_index' as const, icon: 'Binary' as const },
      { id: 'classification' as const, icon: 'Tag' as const },
    ],
  },
  {
    id: 'retrieval',
    tabs: [
      { id: 'rag' as const, icon: 'Zap' as const },
      { id: 'graph_explorer' as const, icon: 'Network' as const },
      { id: 'rules' as const, icon: 'ShieldCheck' as const },
      { id: 'eval' as const, icon: 'Gauge' as const },
    ],
  },
  {
    id: 'operate',
    tabs: [
      { id: 'lifecycle' as const, icon: 'GitBranch' as const },
      { id: 'compliance' as const, icon: 'Shield' as const },
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
