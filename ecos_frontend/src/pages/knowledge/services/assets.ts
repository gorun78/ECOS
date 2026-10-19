/**
 * assets.ts — 知识资产（文档/条目/规则）契约（B.9 / K-55 拆分）。
 *
 * 覆盖 Tab：`assets`（PRD B.2：列表 → 详情 → 提交审批）
 * base 单源：KNOWLEDGE_BASE = /api/v1/knowledge
 *   - /assets            资产列表（文档 / 条目 / 规则）
 *   - /assets/:id/transition  生命周期流转（draft/active/deprecated/archived）
 *   - /lifecycle/audit   生命周期审计流
 *   - /catalog/assets/:id/auto-classify  单资产自动分类
 *
 * 错误态语义（F04-03）：
 *   - `kbFetchLifecycleAssets` / `kbLifecycleTransition` 走 `kbFetchData` →
 *     5xx / ECOS-KB-* 抛 `KbApiError`，AssetListPage 5xx 显式错误码展示（PRD B.5）。
 */
import { apiFetchData } from '../../../api';
import { API_BASE } from '../../../services/httpClient';
import { kbFetchData } from './kbFetch';
import type {
  LifecycleAsset,
  LifecycleAuditEntry,
  LifecycleState,
} from '../typesAndConstants';

const KB_V1 = `${API_BASE}/v1/knowledge`;

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

function toAsset(a: LifecycleAssetRaw): LifecycleAsset {
  return {
    id: String(a.id ?? a.assetId ?? ''),
    name: String(a.name ?? a.assetName ?? a.id ?? ''),
    type: String(a.type ?? a.assetType ?? 'unknown'),
    state: (a.state ?? a.status ?? 'draft') as LifecycleState,
    updatedAt: String(a.updatedAt ?? a.updateTime ?? new Date().toISOString()),
    updatedBy: a.updatedBy,
  };
}

/** 静默版（AssetListPage 主列表，兼容既有 Tab 的 catch 兜底模式） */
export async function fetchLifecycleAssets(): Promise<LifecycleAsset[]> {
  try {
    const data = await apiFetchData<LifecycleAssetRaw[] | LifecycleAssetsResponse>(`${KB_V1}/assets`);
    const items: LifecycleAssetRaw[] = Array.isArray(data) ? data : data?.data || [];
    return items.map(toAsset).filter((a) => a.id);
  } catch {
    return [];
  }
}

/** K-55 分页版 —— 新增 `limit` 参数保护拉取面（默认 20，硬上限 200） */
export async function fetchLifecycleAssetsPage(opts?: {
  limit?: number;
  offset?: number;
  state?: LifecycleState;
}): Promise<LifecycleAsset[]> {
  const limit = Math.min(Math.max(1, opts?.limit ?? 20), 200);
  const offset = Math.max(0, opts?.offset ?? 0);
  const qs = new URLSearchParams();
  qs.set('limit', String(limit));
  qs.set('offset', String(offset));
  if (opts?.state) qs.set('state', opts.state);
  try {
    const data = await apiFetchData<LifecycleAssetRaw[] | LifecycleAssetsResponse>(`${KB_V1}/assets?${qs.toString()}`);
    const items: LifecycleAssetRaw[] = Array.isArray(data) ? data : data?.data || [];
    return items.map(toAsset).filter((a) => a.id);
  } catch {
    return [];
  }
}

/** KbApiError 版（F04-03 错误态分治；AssetListPage 5xx 显式错误码展示） */
export async function kbFetchLifecycleAssets(opts?: {
  limit?: number;
  offset?: number;
  state?: LifecycleState;
}): Promise<LifecycleAsset[]> {
  const limit = Math.min(Math.max(1, opts?.limit ?? 20), 200);
  const offset = Math.max(0, opts?.offset ?? 0);
  const qs = new URLSearchParams();
  qs.set('limit', String(limit));
  qs.set('offset', String(offset));
  if (opts?.state) qs.set('state', opts.state);
  const data = await kbFetchData<LifecycleAssetRaw[] | LifecycleAssetsResponse>(`${KB_V1}/assets?${qs.toString()}`);
  const items: LifecycleAssetRaw[] = Array.isArray(data) ? data : data?.data || [];
  return items.map(toAsset).filter((a) => a.id);
}

export async function lifecycleTransition(assetId: string, next: LifecycleState): Promise<LifecycleAsset> {
  return apiFetchData<LifecycleAsset>(`${KB_V1}/assets/${encodeURIComponent(assetId)}/transition`, {
    method: 'POST',
    body: JSON.stringify({ state: next }),
  });
}

export async function kbLifecycleTransition(assetId: string, next: LifecycleState): Promise<LifecycleAsset> {
  return kbFetchData<LifecycleAsset>(`${KB_V1}/assets/${encodeURIComponent(assetId)}/transition`, {
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

export async function kbFetchLifecycleAudit(): Promise<LifecycleAuditEntry[]> {
  return kbFetchData<LifecycleAuditEntry[]>(`${KB_V1}/lifecycle/audit`);
}

/** catalog auto-classify — 单资产分类触发（PRD B.2「详情 → 自动分类」流程入口） */
export async function classifyAsset(assetId: string) {
  return apiFetchData(`${API_BASE}/v1/catalog/assets/${encodeURIComponent(assetId)}/auto-classify`, { method: 'POST' });
}

export async function kbClassifyAsset(assetId: string) {
  return kbFetchData(`${API_BASE}/v1/catalog/assets/${encodeURIComponent(assetId)}/auto-classify`, { method: 'POST' });
}
