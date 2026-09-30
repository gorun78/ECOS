/**
 * 数据资产看板 REST 收口 (H6-T2) — 原 DataAssetsDashboard.api 助手逐字迁入。
 */
import { authOnlyHeaders } from "./auth";

export async function dataAssetsRequest<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...authOnlyHeaders(), ...(init?.headers ?? {}) },
    ...init,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
