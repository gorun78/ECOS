// B.9 / K-55 — Tab「assets 知识资产 + governance(lifecycle/eval/ETL)」API 切片。
// 内容自原 knowledgeApi.ts 逐字搬移；原文件退化为 barrel，语义与签名保持不变（API 只增不改）。
import { apiFetchData } from '../../../../api';
import { KNOWLEDGE_BASE, KB_V1 } from './base';
import type {
  KnowledgeSettings,
  LifecycleAsset,
  LifecycleAuditEntry,
  LifecycleState,
  EvalSeedQuery,
  EvalReport,
} from '../../typesAndConstants';

/** PMO-B T3 — 按业务域过滤的资产列表（走分类 TaxonService 路由） */
export async function fetchNavProductsByCategory(categoryIds: string[], pageNum = 1, pageSize = 20) {
  try {
    const params = new URLSearchParams();
    categoryIds.forEach(cid => params.append('categoryIds', cid));
    params.set('pageNum', String(pageNum));
    params.set('pageSize', String(pageSize));
    return await apiFetchData<{ list: Array<Record<string, unknown>>; total: number }>(
      `/api/v1/knowledge/nav/products?${params.toString()}`,
    );
  } catch {
    return { list: [], total: 0 };
  }
}

export async function getSettings(): Promise<KnowledgeSettings> {
  try {
    const data = await apiFetchData<KnowledgeSettings>(`${KNOWLEDGE_BASE}/settings`);
    return data || {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  } catch {
    return {
      defaultVectorModel: 'text-embedding-004',
      defaultChunkSize: 512,
      defaultOverlap: 50,
      neo4jEnabled: false,
      autoSyncEnabled: false,
      maxRetrievalResults: 5,
    };
  }
}

export async function updateSettings(data: Partial<KnowledgeSettings>) {
  return apiFetchData(`${KNOWLEDGE_BASE}/settings`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

// ── PMO-54 — Knowledge eval ───────────────────────────────────────────────────

export async function fetchEvalSeeds(): Promise<EvalSeedQuery[]> {
  try {
    const data = await apiFetchData<EvalSeedQuery[]>(`${KB_V1}/eval/seeds`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

export async function uploadEvalSeed(file: File): Promise<{ name: string; count: number }> {
  const fd = new FormData();
  fd.append('file', file);
  const token = localStorage.getItem('token') || '';
  const res = await fetch(`${KB_V1}/eval/seeds/upload`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: fd,
  });
  if (!res.ok) throw new Error(`seed upload failed ${res.status}`);
  const json = await res.json();
  return json?.data || json;
}

export async function runEval(seedSetName: string): Promise<EvalReport> {
  try {
    const data = await apiFetchData<EvalReport>(`${KB_V1}/eval/run`, {
      method: 'POST',
      body: JSON.stringify({ seedSetName }),
    });
    return data || { reportId: '', seedSetName, printedAt: new Date().toISOString(), recallAt5: 0, mrrAt5: 0, ndcgAt5: 0 };
  } catch (e: unknown) {
    // Degraded mode: hint that backend isn't ready; run degraded stub locally
    console.info('runEval backend unavailable — degraded', (e as { message?: string } | undefined)?.message);
    const evalDegraded: EvalReport = {
      reportId: `local-${Date.now()}`,
      seedSetName,
      printedAt: new Date().toISOString(),
      recallAt5: 0,
      mrrAt5: 0,
      ndcgAt5: 0,
      degraded: true,
    };
    return evalDegraded;
  }
}

// ── PMO-54 — Lifecycle ────────────────────────────────────────────────────────

// /assets 原始行形态（后端字段命名新旧并存：assetId/id、assetName/name…；仅本文件消费）
interface LifecycleAssetRaw {
  id?: string | number;
  assetId?: string | number;
  name?: string;
  assetName?: string;
  type?: string;
  assetType?: string;
  state?: LifecycleState;
  status?: string;
  updatedAt?: string;
  updateTime?: string;
  updatedBy?: string;
}

interface LifecycleAssetsResponse {
  data?: LifecycleAssetRaw[];
}

export async function fetchLifecycleAssets(): Promise<LifecycleAsset[]> {
  try {
    const data = await apiFetchData<LifecycleAssetRaw[] | LifecycleAssetsResponse>(`${KB_V1}/assets`);
    const items: LifecycleAssetRaw[] = Array.isArray(data) ? data : data?.data || [];
    return items.map((a) => ({
      id: String(a.id ?? a.assetId ?? ''),
      name: String(a.name ?? a.assetName ?? a.id ?? ''),
      type: String(a.type ?? a.assetType ?? 'unknown'),
      state: (a.state ?? a.status ?? 'draft') as LifecycleState,
      updatedAt: String(a.updatedAt ?? a.updateTime ?? new Date().toISOString()),
      updatedBy: a.updatedBy,
    })).filter(a => a.id);
  } catch {
    return [];
  }
}

export async function lifecycleTransition(assetId: string, next: LifecycleState): Promise<LifecycleAsset> {
  return apiFetchData<LifecycleAsset>(`${KB_V1}/assets/${encodeURIComponent(assetId)}/transition`, {
    method: 'POST',
    body: JSON.stringify({ state: next }),
  });
}

export async function fetchLifecycleAudit(): Promise<LifecycleAuditEntry[]> {
  try {
    const data = await apiFetchData<LifecycleAuditEntry[]>(`${KB_V1}/lifecycle/audit`);
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

// ── PMO-54 — Ingest / ETL workspace ───────────────────────────────────────────

export interface DataWorkbenchSource {
  dsId: string;
  name: string;
  type: string;
  status: 'connected' | 'disconnected' | 'flaky';
  records?: string;
  pipelines?: Array<{ pipelineId: string; name: string }>;
}

// /integration/metadata 原始行形态（sources/data 两种包装，字段新旧并存；仅本文件消费）
interface DataWorkbenchSourceRaw {
  id?: string | number;
  dsId?: string;
  name?: string;
  tableName?: string;
  sourceType?: string;
  type?: string;
  status?: string;
  syncStatus?: string;
  records?: string;
  recordsOrFields?: string;
  pipelines?: DataWorkbenchSource['pipelines'];
}

interface DataWorkbenchSourcesResponse {
  data?: DataWorkbenchSourceRaw[];
  sources?: DataWorkbenchSourceRaw[];
}

export async function fetchDataWorkbenchSources(): Promise<DataWorkbenchSource[]> {
  try {
    const data = await apiFetchData<DataWorkbenchSourceRaw[] | DataWorkbenchSourcesResponse>('/api/v1/integration/metadata');
    const items = Array.isArray(data) ? data : data?.data || data?.sources || [];
    return items.map((s) => ({
      dsId: String(s.id ?? s.dsId ?? s.name ?? ''),
      name: String(s.name ?? s.tableName ?? s.id ?? ''),
      type: String(s.sourceType ?? s.type ?? 'integration'),
      status: (s.status ?? (s.syncStatus === 'synced' ? 'connected' : 'disconnected')) as DataWorkbenchSource['status'],
      records: s.records ?? s.recordsOrFields,
      pipelines: Array.isArray(s.pipelines) ? s.pipelines : undefined,
    })).filter(s => s.dsId);
  } catch {
    return [];
  }
}

// /metadata/drift 原始响应形态（schemaDelta/fields、rows/samples 两套命名；仅本文件消费）
interface MetadataDriftRaw {
  schemaDelta?: Array<Record<string, unknown>>;
  fields?: Array<Record<string, unknown>>;
  rows?: Array<Record<string, unknown>>;
  samples?: Array<Record<string, unknown>>;
  lineage?: { nodes: Array<Record<string, unknown>>; links: Array<Record<string, unknown>> };
}

export async function fetchMetadataDrift(sample?: boolean): Promise<{
  schemaDelta: Array<Record<string, unknown>>;
  rows?: Array<Record<string, unknown>>;
  lineage?: { nodes: Array<Record<string, unknown>>; links: Array<Record<string, unknown>> };
}> {
  try {
    const q = sample ? '?sample=true' : '';
    const data = await apiFetchData<MetadataDriftRaw | null>(`/api/v1/integration/metadata/drift${q}`);
    return {
      schemaDelta: data?.schemaDelta || data?.fields || [],
      rows: data?.rows || data?.samples,
      lineage: data?.lineage,
    };
  } catch {
    return { schemaDelta: [] };
  }
}
