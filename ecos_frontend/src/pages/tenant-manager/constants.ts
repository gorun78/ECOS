/**
 * TenantManager 常量 — 从 pages/TenantManager.tsx 结构拆分而来（内容逐字保留）
 * @license Apache-2.0
 */

// ── Constants ──────────────────────────────────────────────────

export const STATUS_OPTIONS = [
  { value: "", label: "全部", labelEn: "All" },
  { value: "ACTIVE", label: "活跃", labelEn: "Active" },
  { value: "SUSPENDED", label: "已暂停", labelEn: "Suspended" },
  { value: "DELETED", label: "已删除", labelEn: "Deleted" },
];

export const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
  SUSPENDED: "bg-yellow-500/10 text-yellow-400 border-yellow-500/30",
  DELETED: "bg-red-500/10 text-red-400 border-red-500/30",
};

export const ISOLATION_MODE_OPTIONS = ["ROW_FILTER", "SCHEMA", "DATABASE_URL"];

export const RANGE_OPTIONS = [
  { value: "7d", label: "最近 7 天", labelEn: "Last 7 days" },
  { value: "30d", label: "最近 30 天", labelEn: "Last 30 days" },
  { value: "90d", label: "最近 90 天", labelEn: "Last 90 days" },
];
