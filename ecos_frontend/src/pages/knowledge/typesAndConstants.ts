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
  sources: Array<{ title: string; snippet: string; score: number }>;
  tokensUsed: number;
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

export const CHUNK_SIZE_OPTIONS = [256, 512, 1024, 2048] as const;

export const VECTOR_MODELS = [
  { id: 'text-embedding-3-small', dim: 1536, label: 'OpenAI Small' },
  { id: 'bge-large-zh-v1.5', dim: 1024, label: 'BGE 中文大模型' },
  { id: 'text-embedding-004', dim: 256, label: 'Gemini Embedding' },
] as const;

export const KNOWLEDGE_TAB_GROUPS = [
  {
    id: 'closed_loop',
    labelZh: '闭环设计',
    label: 'Closed Loop',
    tabs: [{ id: 'closed_loop' as const, labelZh: '闭环设计', label: 'Closed Loop', icon: 'FileText' as const }],
  },
  {
    id: 'preparation',
    labelZh: '知识准备（轨道A：平台自用）',
    label: 'Preparation (Track A)',
    tabs: [
      { id: 'sync' as const, labelZh: '元数据同步', label: 'Sync', icon: 'Combine' as const },
      { id: 'lineage' as const, labelZh: '血缘解析', label: 'Lineage', icon: 'Network' as const },
      { id: 'ontology' as const, labelZh: '本体对齐', label: 'Ontology', icon: 'Workflow' as const },
    ],
  },
  {
    id: 'construction',
    labelZh: '知识构建（轨道B：智能体知识）',
    label: 'Construction (Track B)',
    tabs: [
      { id: 'graph_sync' as const, labelZh: '图谱同步', label: 'Graph Sync', icon: 'Database' as const },
      { id: 'graph_explore' as const, labelZh: '图谱探索', label: 'Graph Explore', icon: 'Share2' as const },
      { id: 'glossary' as const, labelZh: '术语库', label: 'Glossary', icon: 'BookOpen' as const },
      { id: 'classification' as const, labelZh: '分类体系', label: 'Classification', icon: 'Tag' as const },
    ],
  },
  {
    id: 'retrieval',
    labelZh: '知识检索',
    label: 'Retrieval',
    tabs: [
      { id: 'index' as const, labelZh: '向量索引', label: 'Index', icon: 'Cpu' as const },
      { id: 'rag' as const, labelZh: 'RAG模拟', label: 'RAG', icon: 'SearchCheck' as const },
    ],
  },
  {
    id: 'config',
    labelZh: '配置',
    label: 'Config',
    tabs: [
      { id: 'settings' as const, labelZh: '工作台配置', label: 'Settings', icon: 'Settings' as const },
      { id: 'cognitive_config' as const, labelZh: '认知引擎配置', label: 'Cognitive Config', icon: 'Brain' as const },
    ],
  },
] as const;

export type KnowledgeTabId = typeof KNOWLEDGE_TAB_GROUPS[number]['tabs'][number]['id'];

export const DEFAULT_SETTINGS: KnowledgeSettings = {
  defaultVectorModel: 'text-embedding-004',
  defaultChunkSize: 512,
  defaultOverlap: 50,
  neo4jEnabled: false,
  autoSyncEnabled: false,
  maxRetrievalResults: 5,
};
