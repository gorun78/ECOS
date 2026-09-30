/**
 * TenantManager — 租户管理 (租户CRUD + 配额管理 + 用量仪表盘 + 账单)
 * v3 — 全功能重写，对接实际 API 格式
 * 结构说明：类型/常量/纯函数/子组件位于 ./tenant-manager/（本文件与拆分前行为一致）
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  Building,
  Gauge,
  Receipt,
  RefreshCw,
  BarChart3,
  Plus,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { useMediaQuery } from "../hooks/useMediaQuery";
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
import {
  normalizeQuota,
  normalizeTenant,
} from "./tenant-manager/helpers";
import type {
  QuotaItem,
  RawDailyUsage,
  RawInvoice,
  RawQuota,
  RawTenant,
  TabId,
  Tenant,
} from "./tenant-manager/types";
import Toast from "./tenant-manager/Toast";
import DeleteConfirm from "./tenant-manager/DeleteConfirm";
import TenantFormModal from "./tenant-manager/TenantFormModal";
import EditQuotaModal from "./tenant-manager/EditQuotaModal";
import ManagementTab from "./tenant-manager/ManagementTab";
import QuotaTab from "./tenant-manager/QuotaTab";
import UsageTab from "./tenant-manager/UsageTab";
import InvoiceTab from "./tenant-manager/InvoiceTab";

// ═══════════════════════════════════════════════════════════════
// Main Component
// ═══════════════════════════════════════════════════════════════

export default function TenantManager() {
  const isMobile = useMediaQuery("(max-width: 767px)");
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
          <ManagementTab
            isMobile={isMobile}
            tenants={tenants}
            loadingTenants={loadingTenants}
            tenantPage={tenantPage}
            tenantTotal={tenantTotal}
            tenantSearch={tenantSearch}
            statusFilter={statusFilter}
            setTenantSearch={setTenantSearch}
            setStatusFilter={setStatusFilter}
            setTenantPage={setTenantPage}
            loadTenants={loadTenants}
            setEditTenant={setEditTenant}
            setFormMode={setFormMode}
            setDeleteTarget={setDeleteTarget}
          />
        )}

        {/* ════════════════ Tab 2: 配额管理 (Quota) ════════════════ */}
        {activeTab === "quota" && (
          <QuotaTab
            tenants={tenants}
            selectedTenantId={selectedTenantId}
            setSelectedTenantId={setSelectedTenantId}
            quotas={quotas}
            loadingQuotas={loadingQuotas}
            quotaError={quotaError}
            setEditQuota={setEditQuota}
          />
        )}

        {/* ════════════════ Tab 3: 用量仪表盘 (Usage) ════════════════ */}
        {activeTab === "usage" && (
          <UsageTab
            tenants={tenants}
            selectedTenantId={selectedTenantId}
            setSelectedTenantId={setSelectedTenantId}
            usageRange={usageRange}
            setUsageRange={setUsageRange}
            usageError={usageError}
            loadingUsage={loadingUsage}
            usageChartGroups={usageChartGroups}
          />
        )}

        {/* ════════════════ Tab 4: 账单查看 (Invoice) ════════════════ */}
        {activeTab === "invoice" && (
          <InvoiceTab
            tenants={tenants}
            selectedTenantId={selectedTenantId}
            setSelectedTenantId={setSelectedTenantId}
            invoiceMonth={invoiceMonth}
            setInvoiceMonth={setInvoiceMonth}
            monthOptions={monthOptions}
            invoiceError={invoiceError}
            loadingInvoice={loadingInvoice}
            invoice={invoice}
          />
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
