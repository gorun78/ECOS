/**
 * E4.1 域拆分 · Marketplace (assets / browser / dashboard)
 * 原 api.ts L587-718
 */
import { apiFetch } from "./services/httpClient";

// ── Marketplace ──────────────────────────────────────────
export interface MarketplaceAsset {
  assetId: string;
  name: string;
  type: string;
  owner: string;
  tags: string[];
  views: number;
  stars: number;
  downloads: number;
  createdAt: string;
  summary: string;
  hotScore: number;
}

export async function fetchMarketplaceAssets(
  sort: string = "popular",
  limit: number = 10
): Promise<{ total: number; items: MarketplaceAsset[]; sort: string }> {
  const resp = await apiFetch<{
    success: boolean;
    data: { total: number; items: MarketplaceAsset[]; sort: string };
  }>(`/v1/marketplace/assets?sort=${sort}&limit=${limit}`);
  return resp.data || { total: 0, items: [], sort };
}

export async function requestMarketplaceAccess(
  assetId: string,
  reason: string
): Promise<{ requestId: string; status: string }> {
  return apiFetch("/v1/marketplace/request-access", {
    method: "POST",
    body: JSON.stringify({ assetId, reason }),
  }).then(r => (r as any).data || r);
}

// ── Marketplace P1-4 Browser ────────────────────────────────

export interface MarketplaceBrowserAsset {
  id: string;
  name: string;
  description: string;
  category: string;
  owner: string;
  rating: number;
  popularity: number;
  status: "published" | "draft" | "archived";
  tags: string[];
  createdAt: string;
  updatedAt: string;
  reviews?: MarketplaceReview[];
}

export interface MarketplaceReview {
  id: string;
  userId: string;
  userName: string;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface MarketplaceDashboard {
  totalAssets: number;
  avgRating: number;
  pendingRequests: number;
}

/** GET /api/marketplace/assets — 获取市场资产列表，支持关键词和分类筛选 */
export async function fetchMarketAssets(
  params?: { keyword?: string; category?: string; page?: number; pageSize?: number }
): Promise<{ data: MarketplaceBrowserAsset[]; total: number }> {
  const qs = new URLSearchParams();
  if (params?.keyword) qs.set("keyword", params.keyword);
  if (params?.category) qs.set("category", params.category);
  if (params?.page) qs.set("page", String(params.page));
  if (params?.pageSize) qs.set("pageSize", String(params.pageSize));
  const q = qs.toString();
  const raw = await apiFetch<{ success: boolean; data: { items?: MarketplaceBrowserAsset[]; total?: number; sort?: string } }>(
    `/v1/marketplace/assets${q ? "?" + q : ""}`
  );
  return { data: raw.data?.items || [], total: raw.data?.total || 0 };
}

/** GET /api/marketplace/dashboard — 市场仪表盘统计数据 */
export async function fetchMarketDashboard(): Promise<MarketplaceDashboard> {
  return apiFetch<{ success: boolean; data: MarketplaceDashboard }>("/v1/marketplace/dashboard")
    .then(r => r.data || { totalAssets: 0, avgRating: 0, pendingRequests: 0 });
}

/** POST /api/marketplace/assets — 发布新资产 */
export async function publishMarketAsset(body: {
  name: string;
  description: string;
  category: string;
  tags?: string[];
}): Promise<MarketplaceBrowserAsset> {
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset }>("/v1/marketplace/assets", {
    method: "POST",
    body: JSON.stringify(body),
  }).then(r => r.data);
}

/** GET /api/marketplace/search — 搜索市场资产 */
export async function searchMarketAssets(
  keyword: string,
  category?: string
): Promise<MarketplaceBrowserAsset[]> {
  const qs = new URLSearchParams();
  qs.set("keyword", keyword);
  if (category) qs.set("category", category);
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset[] }>(
    `/v1/marketplace/search?${qs.toString()}`
  ).then(r => r.data || []);
}

/** GET /api/marketplace/assets/{id} — 获取单个资产详情 */
export async function fetchMarketAssetDetail(id: string): Promise<MarketplaceBrowserAsset> {
  return apiFetch<{ success: boolean; data: MarketplaceBrowserAsset }>(`/v1/marketplace/assets/${id}`)
    .then(r => r.data);
}

/** POST /api/marketplace/request-access — 申请资产访问权限 */
export async function requestAccess(
  assetId: string,
  reason: string
): Promise<{ requestId: string; status: string }> {
  return apiFetch<{ success: boolean; data: { requestId: string; status: string } }>(
    "/v1/marketplace/request-access",
    { method: "POST", body: JSON.stringify({ assetId, reason }) }
  ).then(r => r.data || r as any);
}
