/**
 * TenantManager 纯函数辅助 — 从 pages/TenantManager.tsx 结构拆分而来（逻辑逐字保留）
 * @license Apache-2.0
 */

import type { QuotaItem, RawQuota, RawTenant, Tenant } from "./types";

// ── Normalize helpers ──────────────────────────────────────────

export function normalizeTenant(raw: RawTenant): Tenant {
  return {
    id: raw.id,
    tenantName: raw.tenant_name,
    tenantCode: raw.tenant_code,
    status: raw.status,
    maxUsers: raw.max_users,
    maxStorageMb: raw.max_storage_mb,
    maxApiPerDay: raw.max_api_per_day,
    isolationMode: raw.isolation_mode,
    schemaName: raw.schema_name ?? "",
    databaseUrl: raw.database_url ?? "",
    createdAt: raw.created_at,
    updatedAt: raw.updated_at,
  };
}

export function normalizeQuota(raw: RawQuota, usedMap: Record<string, number>): QuotaItem {
  return {
    id: raw.id,
    tenantId: raw.tenant_id,
    quotaType: raw.quota_type,
    dailyLimit: raw.daily_limit,
    monthlyLimit: raw.monthly_limit,
    usedCount: usedMap[raw.quota_type] ?? 0,
  };
}

// ── Format helpers ─────────────────────────────────────────────

export function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

export function formatDate(d: string): string {
  if (!d) return "—";
  return d.slice(0, 10);
}
