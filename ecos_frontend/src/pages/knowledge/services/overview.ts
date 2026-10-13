/**
 * overview.ts — 知识总览 / 底座契约（B.9 / K-55 拆分）。
 *
 * 覆盖 Tab：`overview`（资产健康度 + 双形态状态 + 降级徽标）
 * base 单源：KNOWLEDGE_BASE = /api/v1/knowledge（+ 少量 ontology/cognitive 只读端点）
 * auth 头：既有 `apiFetchData` 路径不变（回归保证）；KbApiError/K-55 语义
 *        集中在 `kbFetch.ts`，overview 主仪表盘沿用 silent-degrade 行为。
 */
import { apiFetchData } from '../../../api';
import { kbFetchData } from './kbFetch';
import type {
  SyncStatus,
  SyncLog,
  EngineConfigScope,
  EngineConfig,
} from '../typesAndConstants';

const KNOWLEDGE_BASE = '/api/v1/knowledge';
const KB_V1 = KNOWLEDGE_BASE;

// ── Overview / Dashboard ────────────────────────────────────────────────────────

/** Wave 0 — 向量库概要新增可选字段（后端只加不改） */
export interface GraphStats {
  graphNodeCount: number;
  graphEdgeCount: number;
  embeddingCount: number;
  ruleCount: number;
  articleCount?: number;
  docCount?: number;
  complianceRuleCount?: number;
  lastUpdatedAt?: string;
  embeddingDim?: number;
}

// /knowledge/stats 原始响应形态（JSON 边界本地收窄；仅本文件消费）
interface KnowledgeStatsRaw {
  graphNodeCount?: number;
  graphEdgeCount?: number;
  embeddingCount?: number;
  ruleCount?: number;
  complianceRuleCount?: number;
  articleCount?: number;
  docCount?: number;
  lastUpdatedAt?: string;
  embeddingDim?: number;
}

export async function fetchGraphStats(): Promise<GraphStats> {
  try {
    const data = await apiFetchData<KnowledgeStatsRaw | null>('/api/v1/knowledge/stats');
    return {
      graphNodeCount: data?.graphNodeCount ?? 0,
      graphEdgeCount: data?.graphEdgeCount ?? 0,
      embeddingCount: data?.embeddingCount ?? 0,
      ruleCount: data?.ruleCount ?? data?.complianceRuleCount ?? 0,
      articleCount: data?.articleCount,
      docCount: data?.docCount,
      complianceRuleCount: data?.complianceRuleCount,
      lastUpdatedAt: data?.lastUpdatedAt,
      embeddingDim: data?.embeddingDim,
    };
  } catch {
    return { graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0 };
  }
}

/**
 * K-55 严格版：/stats 5xx 或 ECOS-KB-* 抛 KbApiError（网络异常同样抛出）。
 * OverviewPage 用其区分「真实 0 值」与「拉取失败」，杜绝失败显示 0=一切正常。
 */
export async function kbFetchGraphStats(): Promise<GraphStats> {
  const data = await kbFetchData<KnowledgeStatsRaw | null>('/api/v1/knowledge/stats');
  return {
    graphNodeCount: data?.graphNodeCount ?? 0,
    graphEdgeCount: data?.graphEdgeCount ?? 0,
    embeddingCount: data?.embeddingCount ?? 0,
    ruleCount: data?.ruleCount ?? data?.complianceRuleCount ?? 0,
    articleCount: data?.articleCount,
    docCount: data?.docCount,
    complianceRuleCount: data?.complianceRuleCount,
    lastUpdatedAt: data?.lastUpdatedAt,
    embeddingDim: data?.embeddingDim,
  };
}

/** Wave 0 — 读本体工作台「实体-数据映射契约」（ontology-engine 只读端点） */
export interface EntityMappingItem {
  entityCode?: string;
  resourceName?: string;
  datasetId?: string;
  materialized?: boolean;
  fieldMappings?: unknown[];
}

export async function fetchEntityMappings(): Promise<EntityMappingItem[]> {
  try {
    const data = await apiFetchData<unknown>('/api/v1/ontology/entity-mappings');
    return Array.isArray(data) ? (data as EntityMappingItem[]) : [];
  } catch {
    return [];
  }
}

/** 引擎健康响应（engine → ok/latency/message） */
export interface EngineHealthMap {
  [key: string]: { ok: boolean; latencyMs?: number; message?: string };
}

export async function fetchEngineHealth(): Promise<EngineHealthMap> {
  try {
    return await apiFetchData<EngineHealthMap>(`${KB_V1}/health`);
  } catch {
    return {};
  }
}

// ── Sync health ─────────────────────────────────────────────────────────────────

export async function fetchSyncStatuses(): Promise<SyncStatus[]> {
  try {
    const data = await apiFetchData<Record<string, unknown>>(`${KNOWLEDGE_BASE}/sync/status`);
    return (data?.objectTypes as SyncStatus[]) || [];
  } catch {
    return [];
  }
}

export async function triggerFullSync() {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/trigger`, { method: 'POST' });
}

export async function triggerObjectSync(objectType: string) {
  return apiFetchData(`${KNOWLEDGE_BASE}/sync/object/${encodeURIComponent(objectType)}`, { method: 'POST' });
}

export async function fetchSyncLogs(): Promise<SyncLog[]> {
  try {
    const data = await apiFetchData<SyncLog[]>(`${KNOWLEDGE_BASE}/sync/logs`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function fetchOntologyMappings() {
  try {
    return await apiFetchData('/api/v1/ontology/mappings');
  } catch {
    return { mappings: [] };
  }
}

export async function saveOntologyMappings(mappings: unknown) {
  try {
    return await apiFetchData('/api/v1/ontology/mappings', {
      method: 'POST',
      body: JSON.stringify(mappings),
    });
  } catch {
    return null;
  }
}

export async function exportOntology() {
  try {
    return await apiFetchData('/api/v1/ontology/export');
  } catch {
    return '';
  }
}

export async function fetchIndexStatus() {
  try {
    return await apiFetchData(`${KNOWLEDGE_BASE}/index-status`);
  } catch {
    return { nodeCount: 0, relationshipCount: 0 };
  }
}

export async function syncVectors(config: Record<string, unknown>) {
  try {
    return await apiFetchData('/api/v1/knowledge/sync', {
      method: 'POST',
      body: JSON.stringify(config),
    });
  } catch {
    return { status: 'queued' };
  }
}

// ── Graph build jobs ────────────────────────────────────────────────────────────

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

export interface GraphBuildPreview {
  create: number;
  update: number;
  skip: number;
  samples?: Array<{ entityId: string; action: 'CREATE' | 'UPDATE' | 'SKIP' }>;
}

interface GraphJobsResponse {
  data?: GraphBuildJob[];
}

export async function fetchGraphJobs(): Promise<GraphBuildJob[]> {
  try {
    const data = await apiFetchData<GraphBuildJob[] | GraphJobsResponse | null>(`${KB_V1}/sync/jobs`);
    return Array.isArray(data) ? data : data?.data || [];
  } catch {
    return [];
  }
}

/** K-55 严格版：KG/向量构建作业列表；失败抛错供 OverviewPage 索引待办降级标记。 */
export async function kbFetchGraphJobs(): Promise<GraphBuildJob[]> {
  const data = await kbFetchData<GraphBuildJob[] | GraphJobsResponse | null>(`${KB_V1}/sync/jobs`);
  return Array.isArray(data) ? data : data?.data || [];
}

export async function previewGraphBuild(params?: { dryRun?: boolean }): Promise<GraphBuildPreview> {
  try {
    return await apiFetchData<GraphBuildPreview>(`${KB_V1}/graph/build/preview?${params?.dryRun ? 'dryRun=true' : ''}`, { method: 'POST' });
  } catch {
    return { create: 0, update: 0, skip: 0 };
  }
}

export async function triggerGraphBuild(payload: Record<string, unknown>): Promise<{ jobId: string }> {
  const data = await apiFetchData<{ jobId: string }>(`${KB_V1}/graph/build`, { method: 'POST', body: JSON.stringify(payload) });
  return data || { jobId: '' };
}

export interface JobPreview {
  create: number;
  update: number;
  skip: number;
  samples?: Array<Record<string, unknown>>;
}

export async function previewGraphJob(jobId: string): Promise<JobPreview> {
  try {
    return await apiFetchData<JobPreview>(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/preview`);
  } catch {
    return { create: 0, update: 0, skip: 0 };
  }
}

export async function rollbackGraphJob(jobId: string) {
  return apiFetchData(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/rollback`, { method: 'POST' });
}

export async function fetchGraphJobLogs(jobId: string): Promise<string[]> {
  try {
    const data = await apiFetchData<string[]>(`${KB_V1}/sync/jobs/${encodeURIComponent(jobId)}/logs`);
    return Array.isArray(data) ? data : [String(data)];
  } catch {
    return [];
  }
}

// ── Engine config（cognitive fallback） ─────────────────────────────────────────

export async function fetchCognitiveConfig() {
  return apiFetchData('/api/v1/cognitive/config');
}

export async function updateCognitiveConfig(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData('/api/v1/cognitive/config', {
    method: 'PUT',
    body: JSON.stringify(updates),
  });
}

export async function fetchEngineConfig(scope: EngineConfigScope): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  try {
    const data = await apiFetchData<EngineConfig>(`${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`);
    return { config: data || {}, version: 1, updatedAt: new Date().toISOString() };
  } catch {
    try {
      const data = await apiFetchData<EngineConfig>('/api/v1/cognitive/config');
      const result: EngineConfig = {};
      for (const [k, v] of Object.entries(data || {})) result[`knowledge.${scope}.${k}`] = String(v);
      return { config: result, version: 1, updatedAt: new Date().toISOString() };
    } catch {
      return { config: {}, version: 1, updatedAt: new Date().toISOString() };
    }
  }
}

export async function saveEngineConfig(scope: EngineConfigScope, cfg: Record<string, unknown>): Promise<{ config: EngineConfig; version: number; updatedAt: string }> {
  const asyncRp = apiFetchData<{ config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null>(
    `${KB_V1}/engine-config?scope=${encodeURIComponent(scope)}`,
    { method: 'PUT', body: JSON.stringify(cfg) },
  );

  const settle = async (): Promise<{ config: EngineConfig; version: number; updatedAt: string }> => {
    const primary = await asyncRp.catch(function (): { config: EngineConfig; version: number; updatedAt: string } | EngineConfig | null {
      return null;
    });
    if (primary && typeof primary === 'object' && 'config' in primary) return primary as { config: EngineConfig; version: number; updatedAt: string };
    if (primary && typeof primary === 'object' && 'config' in (primary as Record<string, unknown>)) {
      const p = primary as Record<string, unknown>;
      return {
        config: (p.config as EngineConfig) || (cfg as EngineConfig),
        version: Number(p.version ?? 1),
        updatedAt: String(p.updatedAt ?? new Date().toISOString()),
      };
    }
    if (primary) return { config: (primary as EngineConfig) || (cfg as EngineConfig), version: 1, updatedAt: new Date().toISOString() };
    const updates = Object.entries(cfg).map(([config_key, config_value]) => ({
      config_key: `knowledge_engine.${scope}.${config_key}`,
      config_value,
    }));
    const fbResp = await apiFetchData<Record<string, unknown> | null>('/api/v1/cognitive/config', {
      method: 'PUT',
      body: JSON.stringify(updates),
    });
    return { config: (fbResp ? (fbResp as EngineConfig) : cfg) as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  };
  try {
    return await settle();
  } catch {
    return { config: cfg as EngineConfig, version: 1, updatedAt: new Date().toISOString() };
  }
}
