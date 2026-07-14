/**
 * TenantManager — 租户管理 (租户CRUD + 配额管理 + 用量仪表盘 + 账单)
 * v3 — 全功能重写，对接实际 API 格式
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  Building,
  Gauge,
  Receipt,
  RefreshCw,
  Edit3,
  X,
  Check,
  BarChart3,
  AlertTriangle,
  Plus,
  Trash2,
  Search,
  CheckCircle2,
  AlertCircle,
  Users,
  HardDrive,
  Activity,
  Shield,
  Calendar,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import {
  apiFetchData,
  fetchTenants,
  createTenant,
  updateTenant,
  deleteTenant,
  fetchTenantQuota,
  updateTenantQuota,
} from "../api";
import ErrorBoundary from "../components/common/ErrorBoundary";

// ── Types (matching actual API snake_case returns) ─────────────

/** Raw tenant item from API list response (snake_case) */
interface RawTenant {
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
interface Tenant {
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
interface RawQuota {
  id: number;
  tenant_id: number;
  quota_type: string;
  daily_limit: number;
  monthly_limit: number;
  [key: string]: any;
}

/** Normalized quota item */
interface QuotaItem {
  id: number;
  tenantId: number;
  quotaType: string;
  dailyLimit: number;
  monthlyLimit: number;
  usedCount: number; // from usage array
}

/** Raw usage item */
interface RawDailyUsage {
  usage_date: string;
  quota_type: string;
  used_count: number;
}

/** Raw invoice item */
interface RawInvoiceItem {
  quota_type: string;
  usage: number;
  unit_price: number;
  cost_cents: number;
  cost_display: string;
}

/** Raw invoice */
interface RawInvoice {
  tenant_id: number;
  month: string;
  line_items: RawInvoiceItem[];
  total_cost_cents: number;
  total_cost_display: string;
}

// ── Constants ──────────────────────────────────────────────────

const STATUS_OPTIONS = [
  { value: "", label: "全部", labelEn: "All" },
  { value: "ACTIVE", label: "活跃", labelEn: "Active" },
  { value: "SUSPENDED", label: "已暂停", labelEn: "Suspended" },
  { value: "DELETED", label: "已删除", labelEn: "Deleted" },
];

const STATUS_COLORS: Record<string, string> = {
  ACTIVE: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
  SUSPENDED: "bg-yellow-500/10 text-yellow-400 border-yellow-500/30",
  DELETED: "bg-red-500/10 text-red-400 border-red-500/30",
};

const ISOLATION_MODE_OPTIONS = ["ROW_FILTER", "SCHEMA", "DATABASE_URL"];
const RANGE_OPTIONS = [
  { value: "7d", label: "最近 7 天", labelEn: "Last 7 days" },
  { value: "30d", label: "最近 30 天", labelEn: "Last 30 days" },
  { value: "90d", label: "最近 90 天", labelEn: "Last 90 days" },
];

// ── Normalize helpers ──────────────────────────────────────────

function normalizeTenant(raw: RawTenant): Tenant {
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

function normalizeQuota(raw: RawQuota, usedMap: Record<string, number>): QuotaItem {
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

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

function formatDate(d: string): string {
  if (!d) return "—";
  return d.slice(0, 10);
}

// ── Toast ──────────────────────────────────────────────────────

const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success" ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100"><X className="w-3.5 h-3.5" /></button>
  </div>
);

// ── Delete Confirm Dialog ─────────────────────────────────────

const DeleteConfirm: React.FC<{
  targetName: string;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ targetName, onConfirm, onCancel }) => (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 bg-[#141924] border border-[#1E293B]">
      <h3 className="text-base font-bold mb-2 text-slate-100">确认删除</h3>
      <p className="text-sm text-slate-400 mb-5">确定要删除租户「{targetName}」吗？此操作不可撤销。</p>
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel} className="px-4 py-1.5 rounded text-xs border border-[#1E293B] text-slate-300 hover:bg-white/5">取消</button>
        <button onClick={onConfirm} className="px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700">删除</button>
      </div>
    </div>
  </div>
);

// ── Simple Bar Chart ───────────────────────────────────────────

function BarChart({
  data,
  maxValue,
  height,
}: {
  data: { label: string; value: number; color: string }[];
  maxValue?: number;
  height?: number;
}) {
  const { styles } = useTheme();
  const h = height ?? 180;
  const max = maxValue ?? Math.max(...data.map((d) => d.value), 1);

  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center text-xs text-slate-500" style={{ height: h }}>
        <BarChart3 className="w-5 h-5 mr-2 opacity-40" />No data
      </div>
    );
  }

  return (
    <div className="flex items-end gap-1" style={{ height: h + 28 }}>
      {data.map((item, i) => {
        const pct = (item.value / max) * 100;
        return (
          <div key={i} className="flex-1 flex flex-col items-center gap-1 min-w-0">
            <span className="text-[9px] font-mono opacity-60">{item.value}</span>
            <div className={`w-full rounded-t ${item.color}`} style={{ height: `${Math.max(pct, 1)}%`, minHeight: 2 }} />
            <span className="text-[8px] opacity-50 truncate w-full text-center">{item.label}</span>
          </div>
        );
      })}
    </div>
  );
}

// ── Tenant Form Modal ─────────────────────────────────────────

interface TenantFormModalProps {
  mode: "create" | "edit";
  tenant?: Tenant;
  onSave: (data: { tenantName: string; tenantCode?: string; status?: string; maxUsers?: number; maxStorageMb?: number; maxApiPerDay?: number; isolationMode?: string }) => Promise<void>;
  onClose: () => void;
}

function TenantFormModal({ mode, tenant, onSave, onClose }: TenantFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [tenantName, setTenantName] = useState(tenant?.tenantName ?? "");
  const [tenantCode, setTenantCode] = useState(tenant?.tenantCode ?? "");
  const [status, setStatus] = useState(tenant?.status ?? "ACTIVE");
  const [maxUsers, setMaxUsers] = useState(String(tenant?.maxUsers ?? 100));
  const [maxStorageMb, setMaxStorageMb] = useState(String(tenant?.maxStorageMb ?? 1024));
  const [maxApiPerDay, setMaxApiPerDay] = useState(String(tenant?.maxApiPerDay ?? 10000));
  const [isolationMode, setIsolationMode] = useState(tenant?.isolationMode ?? "ROW_FILTER");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = async () => {
    if (!tenantName.trim()) {
      setError(locale === "zh" ? "租户名称不能为空" : "Tenant name is required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave({
        tenantName: tenantName.trim(),
        tenantCode: tenantCode.trim() || undefined,
        status,
        maxUsers: parseInt(maxUsers, 10) || 0,
        maxStorageMb: parseInt(maxStorageMb, 10) || 0,
        maxApiPerDay: parseInt(maxApiPerDay, 10) || 0,
        isolationMode,
      });
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-lg ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create"
              ? locale === "zh" ? "新建租户" : "Create Tenant"
              : locale === "zh" ? "编辑租户" : "Edit Tenant"}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        {error && (
          <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>
        )}

        <div className="space-y-3 max-h-[60vh] overflow-y-auto">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户名称 *" : "Tenant Name *"}
            </label>
            <input
              value={tenantName}
              onChange={(e) => setTenantName(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={locale === "zh" ? "例如：Acme Corp" : "e.g. Acme Corp"}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户编码" : "Tenant Code"}
            </label>
            <input
              value={tenantCode}
              onChange={(e) => setTenantCode(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              disabled={mode === "edit"}
              placeholder={locale === "zh" ? "例如：acme" : "e.g. acme"}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "状态" : "Status"}
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                <option value="ACTIVE">{locale === "zh" ? "活跃" : "Active"}</option>
                <option value="SUSPENDED">{locale === "zh" ? "已暂停" : "Suspended"}</option>
                <option value="DELETED">{locale === "zh" ? "已删除" : "Deleted"}</option>
              </select>
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "隔离模式" : "Isolation Mode"}
              </label>
              <select
                value={isolationMode}
                onChange={(e) => setIsolationMode(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                {ISOLATION_MODE_OPTIONS.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "最大用户数" : "Max Users"}
              </label>
              <input
                type="number"
                min={0}
                value={maxUsers}
                onChange={(e) => setMaxUsers(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "最大存储(MB)" : "Max Storage(MB)"}
              </label>
              <input
                type="number"
                min={0}
                value={maxStorageMb}
                onChange={(e) => setMaxStorageMb(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "日API限额" : "API/Day"}
              </label>
              <input
                type="number"
                min={0}
                value={maxApiPerDay}
                onChange={(e) => setMaxApiPerDay(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {locale === "zh" ? "取消" : "Cancel"}
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}
          >
            <Check className="w-3.5 h-3.5" />
            {saving ? (locale === "zh" ? "保存中…" : "Saving…") : (locale === "zh" ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Edit Quota Modal ───────────────────────────────────────────

interface EditQuotaModalProps {
  quota: QuotaItem;
  onSave: (dailyLimit: number, monthlyLimit: number) => Promise<void>;
  onClose: () => void;
}

function EditQuotaModal({ quota, onSave, onClose }: EditQuotaModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [dailyLimit, setDailyLimit] = useState(String(quota.dailyLimit));
  const [monthlyLimit, setMonthlyLimit] = useState(String(quota.monthlyLimit));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = async () => {
    const d = Number(dailyLimit);
    const m = Number(monthlyLimit);
    if (isNaN(d) || isNaN(m) || d < 0 || m < 0 || d > m) {
      setError(locale === "zh" ? "请输入有效数值，日限额不能超过月限额" : "Invalid: daily must not exceed monthly");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave(d, m);
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-md ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {locale === "zh" ? "编辑配额" : "Edit Quota"} — {quota.quotaType}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}
        <div className="space-y-3">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{locale === "zh" ? "日限额" : "Daily Limit"}</label>
            <input type="number" min={0} value={dailyLimit} onChange={(e) => setDailyLimit(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{locale === "zh" ? "月限额" : "Monthly Limit"}</label>
            <input type="number" min={0} value={monthlyLimit} onChange={(e) => setMonthlyLimit(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} />
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {locale === "zh" ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (locale === "zh" ? "保存中…" : "Saving…") : (locale === "zh" ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// Main Component
// ═══════════════════════════════════════════════════════════════

type TabId = "management" | "quota" | "usage" | "invoice";

export default function TenantManager() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  // ── Toast ──
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // ── Shared state ──
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [tenantPage, setTenantPage] = useState(1);
  const [tenantTotal, setTenantTotal] = useState(0);
  const [tenantSearch, setTenantSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [selectedTenantId, setSelectedTenantId] = useState<number | null>(null);

  // ── Tabs ──
  const [activeTab, setActiveTab] = useState<TabId>("management");

  // ── Management tab: form/delete state ──
  const [formMode, setFormMode] = useState<"create" | "edit" | null>(null);
  const [editTenant, setEditTenant] = useState<Tenant | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<{ id: number; name: string } | null>(null);
  const [saving, setSaving] = useState(false);

  // ── Quota tab state ──
  const [quotas, setQuotas] = useState<QuotaItem[]>([]);
  const [loadingQuotas, setLoadingQuotas] = useState(false);
  const [editQuota, setEditQuota] = useState<QuotaItem | null>(null);
  const [quotaError, setQuotaError] = useState("");

  // ── Usage tab state ──
  const [dailyUsage, setDailyUsage] = useState<RawDailyUsage[]>([]);
  const [usageRange, setUsageRange] = useState("30d");
  const [loadingUsage, setLoadingUsage] = useState(false);
  const [usageError, setUsageError] = useState("");

  // ── Invoice tab state ──
  const [invoiceMonth, setInvoiceMonth] = useState(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  });
  const [invoice, setInvoice] = useState<RawInvoice | null>(null);
  const [loadingInvoice, setLoadingInvoice] = useState(false);
  const [invoiceError, setInvoiceError] = useState("");

  // ── Load tenants ──
  const loadTenants = useCallback(async (keyword?: string, page?: number, status?: string) => {
    setLoadingTenants(true);
    try {
      const kw = keyword && keyword.trim() ? keyword.trim() : undefined;
      // HACK: pass status as keyword suffix since api doesn't have status filter
      const searchKw = status ? (kw ? `${kw} status:${status}` : `status:${status}`) : kw;
      const result = await fetchTenants(searchKw, page ?? 1, 20);
      const rawList: RawTenant[] = (result as any).data ?? [];
      const total = (result as any).total ?? 0;
      const normalized = rawList
        .filter((r: any) => !status || r.status === status)
        .map(normalizeTenant);
      setTenants(normalized);
      setTenantTotal(total);
      if (normalized.length > 0 && !selectedTenantId) {
        setSelectedTenantId(normalized[0].id);
      }
    } catch (e: any) {
      showToast("error", `加载租户失败: ${e.message}`);
    } finally {
      setLoadingTenants(false);
    }
  }, [selectedTenantId, showToast]);

  useEffect(() => { loadTenants(); }, []);

  // ── Tenant CRUD ──
  const handleCreateTenant = async (data: any) => {
    setSaving(true);
    try {
      await createTenant({ tenantName: data.tenantName });
      showToast("success", locale === "zh" ? "租户创建成功" : "Tenant created");
      await loadTenants(tenantSearch, tenantPage, statusFilter);
    } catch (e: any) {
      throw e;
    } finally {
      setSaving(false);
    }
  };

  const handleUpdateTenant = async (data: any) => {
    if (!editTenant) return;
    setSaving(true);
    try {
      await updateTenant(String(editTenant.id), data as any);
      showToast("success", locale === "zh" ? "租户更新成功" : "Tenant updated");
      await loadTenants(tenantSearch, tenantPage, statusFilter);
    } catch (e: any) {
      throw e;
    } finally {
      setSaving(false);
    }
  };

  const handleDeleteTenant = async () => {
    if (!deleteTarget) return;
    setSaving(true);
    try {
      await deleteTenant(String(deleteTarget.id));
      showToast("success", locale === "zh" ? "租户已删除" : "Tenant deleted");
      setDeleteTarget(null);
      await loadTenants(tenantSearch, tenantPage, statusFilter);
    } catch (e: any) {
      showToast("error", `删除失败: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── Quota: load ──
  const loadQuotas = useCallback(async () => {
    if (!selectedTenantId) return;
    setLoadingQuotas(true);
    setQuotaError("");
    try {
      const result: any = await fetchTenantQuota(String(selectedTenantId));
      const rawQuotas: RawQuota[] = result?.data ?? [];
      const usageArr: { quota_type: string; used_count: number }[] = result?.usage ?? [];
      const usedMap: Record<string, number> = {};
      usageArr.forEach((u: any) => { usedMap[u.quota_type] = u.used_count ?? 0; });
      setQuotas(rawQuotas.map((r) => normalizeQuota(r, usedMap)));
    } catch (e: any) {
      setQuotaError(e.message || "Failed to load quotas");
    } finally {
      setLoadingQuotas(false);
    }
  }, [selectedTenantId]);

  useEffect(() => { if (activeTab === "quota") loadQuotas(); }, [activeTab, selectedTenantId]);

  const handleQuotaSave = async (quotaType: string, dailyLimit: number, monthlyLimit: number) => {
    await updateTenantQuota(String(selectedTenantId!), { quota_type: quotaType, daily_limit: dailyLimit, monthly_limit: monthlyLimit });
    await loadQuotas();
  };

  // ── Usage: load ──
  const loadUsage = useCallback(async () => {
    if (!selectedTenantId) return;
    setLoadingUsage(true);
    setUsageError("");
    try {
      const result: any = await apiFetchData(`/api/v1/system/tenants/${selectedTenantId}/usage?range=${usageRange}`);
      setDailyUsage(result?.daily_usage ?? []);
    } catch (e: any) {
      setUsageError(e.message || "Failed to load usage");
    } finally {
      setLoadingUsage(false);
    }
  }, [selectedTenantId, usageRange]);

  useEffect(() => { if (activeTab === "usage") loadUsage(); }, [activeTab, selectedTenantId, usageRange]);

  // ── Invoice: load ──
  const loadInvoice = useCallback(async () => {
    if (!selectedTenantId) return;
    setLoadingInvoice(true);
    setInvoiceError("");
    try {
      const result: any = await apiFetchData(`/api/v1/system/tenants/${selectedTenantId}/invoice?month=${invoiceMonth}`);
      setInvoice(result && result.line_items ? result : null);
    } catch (e: any) {
      setInvoiceError(e.message || "Failed to load invoice");
    } finally {
      setLoadingInvoice(false);
    }
  }, [selectedTenantId, invoiceMonth]);

  useEffect(() => { if (activeTab === "invoice") loadInvoice(); }, [activeTab, selectedTenantId, invoiceMonth]);

  // ── Month options ──
  const monthOptions = useMemo(() => {
    const opts: string[] = [];
    const now = new Date();
    for (let i = 0; i < 12; i++) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      opts.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`);
    }
    return opts;
  }, []);

  // ── Usage chart data: group by quota_type ──
  const usageChartGroups = useMemo<Record<string, { label: string; value: number; color: string }[]>>(() => {
    const groups: Record<string, { label: string; value: number; color: string }[]> = {};
    const colors = ["bg-indigo-500/60", "bg-emerald-500/60", "bg-amber-500/60", "bg-rose-500/60", "bg-cyan-500/60"];
    let colorIdx = 0;
    dailyUsage.forEach((u) => {
      if (!groups[u.quota_type]) groups[u.quota_type] = [];
      const dateLabel = u.usage_date?.slice(5) ?? u.usage_date;
      groups[u.quota_type].push({
        label: dateLabel,
        value: u.used_count,
        color: colors[colorIdx % colors.length],
      });
    });
    // Assign consistent colors per group
    Object.keys(groups).forEach((key, i) => {
      groups[key] = groups[key].map((d) => ({ ...d, color: colors[i % colors.length] }));
    });
    return groups;
  }, [dailyUsage]);

  // ── Selected tenant ──
  const selectedTenant = tenants.find((t) => t.id === selectedTenantId);

  // ── Tabs definition ──
  const tabs: { id: TabId; label: string; labelZh: string; icon: React.ReactNode }[] = [
    { id: "management", label: "Tenant Management", labelZh: "租户管理", icon: <Building className="w-4 h-4" /> },
    { id: "quota", label: "Quota Management", labelZh: "配额管理", icon: <Gauge className="w-4 h-4" /> },
    { id: "usage", label: "Usage Dashboard", labelZh: "用量仪表盘", icon: <BarChart3 className="w-4 h-4" /> },
    { id: "invoice", label: "Billing", labelZh: "账单查看", icon: <Receipt className="w-4 h-4" /> },
  ];

  // ── Render ──────────────────────────────────────────────────

  return (
    <ErrorBoundary>
      <div className="h-full overflow-y-auto p-6 space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className={`text-xl font-bold ${styles.cardText}`}>
              {locale === "zh" ? "租户管理" : "Tenant Manager"}
            </h1>
            <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户CRUD、配额管理、用量监控与账单查看" : "Tenant CRUD, quota, usage & billing"}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {activeTab === "management" && (
              <button
                onClick={() => { setFormMode("create"); setEditTenant(null); }}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}
              >
                <Plus className="w-3.5 h-3.5" />
                {locale === "zh" ? "新建租户" : "New Tenant"}
              </button>
            )}
            <button
              onClick={() => {
                loadTenants(tenantSearch, tenantPage, statusFilter);
                if (activeTab === "quota") loadQuotas();
                else if (activeTab === "usage") loadUsage();
                else if (activeTab === "invoice") loadInvoice();
              }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium transition-colors ${styles.accentBg} text-white ${styles.accentHover}`}
            >
              <RefreshCw className="w-3.5 h-3.5" />
              {locale === "zh" ? "刷新" : "Refresh"}
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-white/10 gap-1">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-medium border-b-2 transition-colors ${
                activeTab === tab.id ? `${styles.accentText} border-current` : "border-transparent opacity-60 hover:opacity-100"
              }`}
            >
              {tab.icon}
              {locale === "zh" ? tab.labelZh : tab.label}
            </button>
          ))}
        </div>

        {/* ════════════════ Tab 1: 租户管理 (Management) ════════════════ */}
        {activeTab === "management" && (
          <div className="space-y-4">
            {/* Search + Filter bar */}
            <div className="flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-1.5 flex-1 min-w-[200px]">
                <Search className="w-3.5 h-3.5 opacity-50" />
                <input
                  value={tenantSearch}
                  onChange={(e) => setTenantSearch(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") loadTenants(tenantSearch, 1, statusFilter); }}
                  placeholder={locale === "zh" ? "搜索租户名称/编码…" : "Search tenant name/code…"}
                  className={`flex-1 px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                />
              </div>
              <select
                value={statusFilter}
                onChange={(e) => { setStatusFilter(e.target.value); loadTenants(tenantSearch, 1, e.target.value); }}
                className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                {STATUS_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{locale === "zh" ? o.label : o.labelEn}</option>
                ))}
              </select>
            </div>

            {/* Table */}
            {loadingTenants ? (
              <div className="flex items-center gap-2 p-4">
                <RefreshCw className="w-4 h-4 animate-spin text-slate-400" />
                <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "加载中…" : "Loading…"}</span>
              </div>
            ) : tenants.length === 0 ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <Building className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "暂无租户数据" : "No tenants found"}</p>
              </div>
            ) : (
              <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
                <div className="overflow-x-auto">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                        <th className="text-left px-4 py-2.5 font-medium">ID</th>
                        <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "租户名称" : "Name"}</th>
                        <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "编码" : "Code"}</th>
                        <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "状态" : "Status"}</th>
                        <th className="text-right px-4 py-2.5 font-medium"><Users className="w-3 h-3 inline mr-1" />{locale === "zh" ? "用户上限" : "Max Users"}</th>
                        <th className="text-right px-4 py-2.5 font-medium"><HardDrive className="w-3 h-3 inline mr-1" />MB</th>
                        <th className="text-right px-4 py-2.5 font-medium"><Activity className="w-3 h-3 inline mr-1" />API/天</th>
                        <th className="text-left px-4 py-2.5 font-medium"><Shield className="w-3 h-3 inline mr-1" />{locale === "zh" ? "隔离" : "Isolation"}</th>
                        <th className="text-left px-4 py-2.5 font-medium"><Calendar className="w-3 h-3 inline mr-1" />{locale === "zh" ? "创建时间" : "Created"}</th>
                        <th className="text-center px-4 py-2.5 font-medium">{locale === "zh" ? "操作" : "Actions"}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5">
                      {tenants.map((tn) => (
                        <tr key={tn.id} className="hover:bg-white/5">
                          <td className={`px-4 py-2.5 font-mono ${styles.cardTextMuted}`}>{tn.id}</td>
                          <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{tn.tenantName}</td>
                          <td className={`px-4 py-2.5 font-mono text-[11px] ${styles.cardTextMuted}`}>{tn.tenantCode}</td>
                          <td className="px-4 py-2.5">
                            <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-medium border ${STATUS_COLORS[tn.status] ?? "bg-slate-500/10 text-slate-400 border-slate-500/30"}`}>
                              {tn.status}
                            </span>
                          </td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxUsers)}</td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxStorageMb)}</td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxApiPerDay)}</td>
                          <td className={`px-4 py-2.5 font-mono text-[10px] ${styles.cardTextMuted}`}>{tn.isolationMode}</td>
                          <td className={`px-4 py-2.5 text-[11px] ${styles.cardTextMuted}`}>{formatDate(tn.createdAt)}</td>
                          <td className="px-4 py-2.5 text-center">
                            <div className="flex items-center justify-center gap-1">
                              <button
                                onClick={() => { setEditTenant(tn); setFormMode("edit"); }}
                                className="p-1 rounded hover:bg-white/10" title={locale === "zh" ? "编辑" : "Edit"}
                              >
                                <Edit3 className="w-3.5 h-3.5 text-blue-400" />
                              </button>
                              <button
                                onClick={() => setDeleteTarget({ id: tn.id, name: tn.tenantName })}
                                className="p-1 rounded hover:bg-white/10" title={locale === "zh" ? "删除" : "Delete"}
                              >
                                <Trash2 className="w-3.5 h-3.5 text-red-400" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {/* Pagination */}
                {tenantTotal > 20 && (
                  <div className={`flex items-center justify-between px-4 py-2 border-t ${styles.cardBorder}`}>
                    <span className={`text-[11px] ${styles.cardTextMuted}`}>
                      {locale === "zh" ? `共 ${tenantTotal} 条` : `Total ${tenantTotal}`}
                    </span>
                    <div className="flex items-center gap-1">
                      <button
                        disabled={tenantPage <= 1}
                        onClick={() => { const p = tenantPage - 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                        className={`px-2 py-1 rounded text-[11px] border ${styles.cardBorder} disabled:opacity-30`}
                      >
                        {locale === "zh" ? "上一页" : "Prev"}
                      </button>
                      <span className={`text-[11px] px-2 ${styles.cardTextMuted}`}>{tenantPage}</span>
                      <button
                        onClick={() => { const p = tenantPage + 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                        className={`px-2 py-1 rounded text-[11px] border ${styles.cardBorder}`}
                      >
                        {locale === "zh" ? "下一页" : "Next"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* ════════════════ Tab 2: 配额管理 (Quota) ════════════════ */}
        {activeTab === "quota" && (
          <div className="space-y-4">
            {/* Tenant selector */}
            <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
              <div className="flex items-center gap-3">
                <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
                <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
                  {locale === "zh" ? "选择租户" : "Select Tenant"}:
                </span>
                <select
                  value={selectedTenantId ?? ""}
                  onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
                  className={`px-3 py-1.5 rounded text-xs border min-w-[220px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                >
                  <option value="">{locale === "zh" ? "— 请选择 —" : "— Select —"}</option>
                  {tenants.map((t) => (
                    <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
                  ))}
                </select>
              </div>
            </div>

            {quotaError && (
              <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{quotaError}</div>
            )}

            {!selectedTenantId ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <Gauge className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "请先选择租户" : "Select a tenant first"}</p>
              </div>
            ) : loadingQuotas ? (
              <div className="flex items-center gap-2 p-4">
                <RefreshCw className="w-4 h-4 animate-spin text-slate-400" />
                <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "加载配额…" : "Loading quotas…"}</span>
              </div>
            ) : quotas.length === 0 ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <AlertTriangle className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "该租户暂无配额数据" : "No quota data"}</p>
              </div>
            ) : (
              <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
                <table className="w-full text-xs">
                  <thead>
                    <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                      <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "配额类型" : "Quota Type"}</th>
                      <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "日限额" : "Daily Limit"}</th>
                      <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "月限额" : "Monthly Limit"}</th>
                      <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "已用量" : "Used"}</th>
                      <th className="text-center px-4 py-2.5 font-medium">{locale === "zh" ? "使用率" : "Usage %"}</th>
                      <th className="text-center px-4 py-2.5 font-medium">{locale === "zh" ? "操作" : "Action"}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {quotas.map((q) => {
                      const pct = q.dailyLimit > 0 ? Math.min((q.usedCount / q.dailyLimit) * 100, 100) : 0;
                      const barColor = pct > 80 ? "bg-red-500" : pct > 50 ? "bg-yellow-500" : "bg-emerald-500";
                      return (
                        <tr key={q.id} className="hover:bg-white/5">
                          <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{q.quotaType}</td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.dailyLimit)}</td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.monthlyLimit)}</td>
                          <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.usedCount)}</td>
                          <td className="px-4 py-2.5">
                            <div className="flex items-center gap-2">
                              <div className="flex-1 h-1.5 rounded-full bg-white/10 overflow-hidden">
                                <div className={`h-full rounded-full ${barColor}`} style={{ width: `${pct}%` }} />
                              </div>
                              <span className="text-[10px] font-mono w-9 text-right opacity-60">{pct.toFixed(0)}%</span>
                            </div>
                          </td>
                          <td className="px-4 py-2.5 text-center">
                            <button
                              onClick={() => setEditQuota(q)}
                              className={`inline-flex items-center gap-1 px-2 py-1 rounded text-[11px] border transition-colors ${styles.cardBorder} hover:bg-white/5`}
                            >
                              <Edit3 className="w-3 h-3" />
                              {locale === "zh" ? "编辑" : "Edit"}
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ════════════════ Tab 3: 用量仪表盘 (Usage) ════════════════ */}
        {activeTab === "usage" && (
          <div className="space-y-4">
            {/* Tenant selector + range */}
            <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
              <div className="flex items-center gap-4 flex-wrap">
                <div className="flex items-center gap-2">
                  <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
                  <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
                    {locale === "zh" ? "租户" : "Tenant"}:
                  </span>
                  <select
                    value={selectedTenantId ?? ""}
                    onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
                    className={`px-3 py-1.5 rounded text-xs border min-w-[180px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  >
                    <option value="">{locale === "zh" ? "— 请选择 —" : "— Select —"}</option>
                    {tenants.map((t) => (
                      <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
                    ))}
                  </select>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "范围" : "Range"}:</span>
                  <select
                    value={usageRange}
                    onChange={(e) => setUsageRange(e.target.value)}
                    className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  >
                    {RANGE_OPTIONS.map((r) => (
                      <option key={r.value} value={r.value}>{locale === "zh" ? r.label : r.labelEn}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {usageError && (
              <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{usageError}</div>
            )}

            {!selectedTenantId ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <BarChart3 className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "请先选择租户" : "Select a tenant first"}</p>
              </div>
            ) : loadingUsage ? (
              <div className="flex items-center gap-2 p-4">
                <RefreshCw className="w-4 h-4 animate-spin text-slate-400" />
                <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "加载用量…" : "Loading usage…"}</span>
              </div>
            ) : Object.keys(usageChartGroups).length === 0 ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <AlertTriangle className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "暂无用量数据" : "No usage data"}</p>
              </div>
            ) : (
              <div className="space-y-4">
                {Object.entries(usageChartGroups as Record<string, { label: string; value: number; color: string }[]>).map(([quotaType, chartData]) => (
                  <div key={quotaType} className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
                    <h3 className={`text-xs font-semibold mb-3 ${styles.cardText}`}>
                      {quotaType}
                    </h3>
                    <BarChart data={chartData} />
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ════════════════ Tab 4: 账单查看 (Invoice) ════════════════ */}
        {activeTab === "invoice" && (
          <div className="space-y-4">
            {/* Tenant selector + month */}
            <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
              <div className="flex items-center gap-4 flex-wrap">
                <div className="flex items-center gap-2">
                  <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
                  <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
                    {locale === "zh" ? "租户" : "Tenant"}:
                  </span>
                  <select
                    value={selectedTenantId ?? ""}
                    onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
                    className={`px-3 py-1.5 rounded text-xs border min-w-[180px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  >
                    <option value="">{locale === "zh" ? "— 请选择 —" : "— Select —"}</option>
                    {tenants.map((t) => (
                      <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
                    ))}
                  </select>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "月份" : "Month"}:</span>
                  <select
                    value={invoiceMonth}
                    onChange={(e) => setInvoiceMonth(e.target.value)}
                    className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  >
                    {monthOptions.map((m) => (<option key={m} value={m}>{m}</option>))}
                  </select>
                </div>
              </div>
            </div>

            {invoiceError && (
              <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{invoiceError}</div>
            )}

            {!selectedTenantId ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <Receipt className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "请先选择租户" : "Select a tenant first"}</p>
              </div>
            ) : loadingInvoice ? (
              <div className="flex items-center gap-2 p-4">
                <RefreshCw className="w-4 h-4 animate-spin text-slate-400" />
                <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "加载账单…" : "Loading invoice…"}</span>
              </div>
            ) : !invoice ? (
              <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
                <Receipt className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
                <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "该月份暂无账单" : "No invoice for this month"}</p>
              </div>
            ) : (
              <>
                <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
                  <table className="w-full text-xs">
                    <thead>
                      <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                        <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "项目" : "Item"}</th>
                        <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "用量" : "Usage"}</th>
                        <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "单价" : "Unit Price"}</th>
                        <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "费用" : "Cost"}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5">
                      {invoice.line_items && invoice.line_items.length > 0 ? (
                        invoice.line_items.map((item, i) => (
                          <tr key={i} className="hover:bg-white/5">
                            <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{item.quota_type}</td>
                            <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(item.usage)}</td>
                            <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{item.unit_price?.toFixed(4) ?? "—"}</td>
                            <td className={`px-4 py-2.5 text-right font-mono font-semibold ${styles.cardText}`}>{item.cost_display}</td>
                          </tr>
                        ))
                      ) : (
                        <tr><td colSpan={4} className="px-4 py-8 text-center opacity-60">{locale === "zh" ? "暂无账单明细" : "No line items"}</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
                <div className={`rounded-lg border p-4 flex items-center justify-between ${styles.cardBg} ${styles.cardBorder}`}>
                  <span className={`text-sm font-semibold ${styles.cardText}`}>{locale === "zh" ? "合计费用" : "Total Cost"}</span>
                  <span className={`text-lg font-bold ${styles.accentText}`}>{invoice.total_cost_display}</span>
                </div>
              </>
            )}
          </div>
        )}

        {/* ── Modals ── */}
        {formMode && (
          <TenantFormModal
            mode={formMode}
            tenant={editTenant ?? undefined}
            onSave={formMode === "create" ? handleCreateTenant : handleUpdateTenant}
            onClose={() => { setFormMode(null); setEditTenant(null); }}
          />
        )}

        {editQuota && (
          <EditQuotaModal
            quota={editQuota}
            onSave={(dl, ml) => handleQuotaSave(editQuota.quotaType, dl, ml)}
            onClose={() => setEditQuota(null)}
          />
        )}

        {deleteTarget && (
          <DeleteConfirm
            targetName={deleteTarget.name}
            onConfirm={handleDeleteTenant}
            onCancel={() => setDeleteTarget(null)}
          />
        )}

        {toast && <Toast toast={toast} onClose={() => setToast(null)} />}
      </div>
    </ErrorBoundary>
  );
}
