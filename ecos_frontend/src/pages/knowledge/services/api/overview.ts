// B.9 / K-55 — Tab「overview 知识总览」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { KB_V1 } from './base';

// Wave 0 — 向量库概要新增可选字段（后端只加不改：embeddingDim = embedding_vec 维数，docCount = 覆盖文档数）
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

// /knowledge/stats 原始响应形态（JSON 边界本地收窄，字段均可能缺失；仅本文件消费）
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

/** Wave 0 数据同步 — 读本体工作台「实体-数据映射契约」（ontology-engine 只读端点） */
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

// 引擎健康响应（engine → ok/latency/message）
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
