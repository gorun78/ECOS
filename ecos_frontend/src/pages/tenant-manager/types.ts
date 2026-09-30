/**
 * TenantManager 共享类型 — 从 pages/TenantManager.tsx 结构拆分而来（内容逐字保留）
 * @license Apache-2.0
 */

// ── Types (matching actual API snake_case returns) ─────────────

/** Raw tenant item from API list response (snake_case) */
export interface RawTenant {
  id: number;
  tenant_name: string;
  tenant_code: string;
  status: string;
  max_users: number;
  max_storage_mb: number;
  max_api_per_day: number;
  isolation_mode: string;
  schema_name?: string;
  database_url?: string;
  created_at: string;
  updated_at: string;
}

/** Normalized tenant for UI */
export interface Tenant {
  id: number;
  tenantName: string;
  tenantCode: string;
  status: string;
  maxUsers: number;
  maxStorageMb: number;
  maxApiPerDay: number;
  isolationMode: string;
  schemaName: string;
  databaseUrl: string;
  createdAt: string;
  updatedAt: string;
}

/** Raw quota item from API */
export interface RawQuota {
  id: number;
  tenant_id: number;
  quota_type: string;
  daily_limit: number;
  monthly_limit: number;
  [key: string]: any;
}

/** Normalized quota item */
export interface QuotaItem {
  id: number;
  tenantId: number;
  quotaType: string;
  dailyLimit: number;
  monthlyLimit: number;
  usedCount: number; // from usage array
}

/** Raw usage item */
export interface RawDailyUsage {
  usage_date: string;
  quota_type: string;
  used_count: number;
}

/** Raw invoice item */
export interface RawInvoiceItem {
  quota_type: string;
  usage: number;
  unit_price: number;
  cost_cents: number;
  cost_display: string;
}

/** Raw invoice */
export interface RawInvoice {
  tenant_id: number;
  month: string;
  line_items: RawInvoiceItem[];
  total_cost_cents: number;
  total_cost_display: string;
}

/** Bar chart data point（原 TenantManager 内联匿名类型） */
export interface ChartDataPoint {
  label: string;
  value: number;
  color: string;
}

/** Toast state（原 TenantManager 内联匿名类型） */
export interface ToastState {
  type: "success" | "error";
  msg: string;
}

/** 租户管理页 Tab 标识 */
export type TabId = "management" | "quota" | "usage" | "invoice";
