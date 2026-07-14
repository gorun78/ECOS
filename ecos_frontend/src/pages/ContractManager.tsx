/**
 * ContractManager — 高速信科合同管理
 * 调用 /api/v1/gsxk/objects/Contract 获取合同列表
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  FileText, Search, RefreshCw, AlertCircle, Loader2,
  DollarSign, Calendar, User, Building2, CheckCircle,
  Clock, XCircle, TrendingUp
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import DataTable, { ColumnConfig } from "../components/common/DataTable";

// ── Types ──────────────────────────────────────────────
interface Contract {
  id: string;
  code?: string;
  contractNo?: string;
  name: string;
  partyA?: string;
  clientName?: string;
  amount?: number | string;
  signDate?: string;
  status: string;
  type?: string;
  description?: string;
  [key: string]: any;
}

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  active: { label: "履约中", color: "bg-green-100 text-green-700 border-green-300 dark:bg-green-900/30 dark:text-green-400 dark:border-green-700" },
  completed: { label: "已完成", color: "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-700" },
  pending: { label: "待签署", color: "bg-amber-100 text-amber-700 border-amber-300 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-700" },
  terminated: { label: "已终止", color: "bg-red-100 text-red-700 border-red-300 dark:bg-red-900/30 dark:text-red-400 dark:border-red-700" },
  draft: { label: "草稿", color: "bg-gray-100 text-gray-600 border-gray-300 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-600" },
};

const PAGE_SIZE = 10;

// ── Helpers ────────────────────────────────────────────
function fmtAmount(v: number | string | undefined | null): string {
  if (v == null) return "—";
  const n = Number(v);
  if (isNaN(n)) return String(v);
  if (n >= 100000000) return `${(n / 100000000).toFixed(2)} 亿`;
  if (n >= 10000) return `${(n / 10000).toFixed(1)} 万`;
  return n.toLocaleString();
}

// ── Component ──────────────────────────────────────────
export default function ContractManager() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [searchQ, setSearchQ] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [total, setTotal] = useState(0);

  const loadContracts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams();
      params.set("page", String(currentPage));
      params.set("pageSize", String(PAGE_SIZE));
      if (statusFilter !== "all") params.set("status", statusFilter);
      if (searchQ.trim()) params.set("search", searchQ.trim());

      const data: any = await apiFetchData(`/api/v1/gsxk/objects/Contract?${params.toString()}`);

      if (Array.isArray(data)) {
        setContracts(data);
        setTotal(data.length);
      } else if (data.records || data.list) {
        setContracts(data.records || data.list || []);
        setTotal(data.total || 0);
      } else {
        setContracts([]);
        setTotal(0);
      }
    } catch (e: any) {
      setError(e.message || (locale === "zh" ? "加载合同列表失败" : "Failed to load contracts"));
      setContracts([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, statusFilter, searchQ, locale]);

  useEffect(() => { loadContracts(); }, [loadContracts]);

  // Debounced search resets page
  useEffect(() => {
    const t = setTimeout(() => setCurrentPage(1), 400);
    return () => clearTimeout(t);
  }, [searchQ]);

  // ── Computed KPIs ────────────────────────────────────
  const totalCount = total;
  const totalAmount = contracts.reduce((sum, c) => sum + (Number(c.amount) || 0), 0);
  const activeCount = contracts.filter(c => c.status === "active").length;
  const completedCount = contracts.filter(c => c.status === "completed").length;

  // ── Columns ──────────────────────────────────────────
  const columns: ColumnConfig<Contract>[] = [
    {
      key: "code",
      label: locale === "zh" ? "合同编号" : "Contract No.",
      render: (_v, record) => (
        <span className="font-mono text-[11px]">{record.code || record.contractNo || record.id || "—"}</span>
      ),
    },
    {
      key: "name",
      label: locale === "zh" ? "合同名称" : "Contract Name",
      render: (_v, record) => (
        <span className="font-medium truncate max-w-[180px] block">{record.name || "—"}</span>
      ),
    },
    {
      key: "partyA",
      label: locale === "zh" ? "甲方" : "Party A",
      render: (_v, record) => {
        const a = record.partyA || record.clientName;
        return (
          <span className="flex items-center gap-1 text-xs">
            <Building2 className="w-3 h-3 opacity-40" />
            {a || "—"}
          </span>
        );
      },
    },
    {
      key: "amount",
      label: locale === "zh" ? "金额" : "Amount",
      align: "right",
      render: (_v, record) => (
        <span className="font-mono text-xs font-semibold">{fmtAmount(record.amount)}</span>
      ),
    },
    {
      key: "signDate",
      label: locale === "zh" ? "签署日期" : "Sign Date",
      render: (_v, record) => (
        <span className="flex items-center gap-1 text-xs whitespace-nowrap">
          <Calendar className="w-3 h-3 opacity-40" />
          {record.signDate || "—"}
        </span>
      ),
    },
    {
      key: "status",
      label: locale === "zh" ? "状态" : "Status",
      render: (_v, record) => {
        const s = STATUS_MAP[record.status] || { label: record.status, color: "bg-gray-100 text-gray-600 border-gray-300" };
        return (
          <span className={`inline-block px-2 py-0.5 text-[10px] font-semibold rounded border ${s.color}`}>
            {s.label}
          </span>
        );
      },
    },
  ];

  // ── Loading State ────────────────────────────────────
  if (loading && !contracts.length) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 className="w-8 h-8 text-indigo-500 animate-spin mx-auto" />
          <p className="text-sm text-slate-400">
            {locale === "zh" ? "加载合同数据..." : "Loading contracts..."}
          </p>
        </div>
      </div>
    );
  }

  // ── Error State ──────────────────────────────────────
  if (error && !contracts.length) {
    return (
      <div className="h-full flex items-center justify-center p-6">
        <div className="text-center max-w-sm">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-sm font-semibold text-red-600 mb-1">
            {locale === "zh" ? "数据加载失败" : "Failed to load data"}
          </p>
          <p className="text-xs text-slate-500 mb-4">{error}</p>
          <button
            onClick={loadContracts}
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg transition"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {locale === "zh" ? "重试" : "Retry"}
          </button>
        </div>
      </div>
    );
  }

  // ── Render ───────────────────────────────────────────
  return (
    <div className="h-full overflow-auto p-4 sm:p-6 space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold flex items-center gap-2">
            <FileText className="w-5 h-5 text-indigo-500" />
            {locale === "zh" ? "合同管理" : "Contract Manager"}
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            {locale === "zh" ? "高速信科合同全生命周期管理" : "GSXK contract lifecycle management"}
          </p>
        </div>
        <button
          onClick={() => { setCurrentPage(1); loadContracts(); }}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? (locale === "zh" ? "刷新中..." : "Refreshing...") : (locale === "zh" ? "刷新" : "Refresh")}
        </button>
      </div>

      {/* KPI Cards */}
      {!loading && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <KpiCard
            icon={FileText}
            label={locale === "zh" ? "合同总数" : "Total Contracts"}
            value={totalCount}
            color="text-indigo-500"
            bgColor="bg-indigo-50 dark:bg-indigo-900/20"
          />
          <KpiCard
            icon={DollarSign}
            label={locale === "zh" ? "合同总金额" : "Total Amount"}
            value={fmtAmount(totalAmount)}
            color="text-orange-500"
            bgColor="bg-orange-50 dark:bg-orange-900/20"
          />
          <KpiCard
            icon={CheckCircle}
            label={locale === "zh" ? "履约中" : "Active"}
            value={activeCount}
            color="text-green-500"
            bgColor="bg-green-50 dark:bg-green-900/20"
          />
          <KpiCard
            icon={TrendingUp}
            label={locale === "zh" ? "已完成" : "Completed"}
            value={completedCount}
            color="text-blue-500"
            bgColor="bg-blue-50 dark:bg-blue-900/20"
          />
        </div>
      )}

      {/* Search & Filter */}
      <div className={`flex flex-col sm:flex-row gap-3 p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            value={searchQ}
            onChange={(e) => setSearchQ(e.target.value)}
            placeholder={locale === "zh" ? "搜索合同编号、名称或甲方..." : "Search by number, name, or party..."}
            className={`w-full pl-9 pr-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
          className={`px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
        >
          <option value="all">{locale === "zh" ? "全部状态" : "All Status"}</option>
          <option value="active">{locale === "zh" ? "履约中" : "Active"}</option>
          <option value="completed">{locale === "zh" ? "已完成" : "Completed"}</option>
          <option value="pending">{locale === "zh" ? "待签署" : "Pending"}</option>
          <option value="terminated">{locale === "zh" ? "已终止" : "Terminated"}</option>
          <option value="draft">{locale === "zh" ? "草稿" : "Draft"}</option>
        </select>
      </div>

      {/* DataTable */}
      <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
        <DataTable<Contract>
          columns={columns}
          data={contracts}
          rowKey="id"
          loading={loading}
          pageSize={PAGE_SIZE}
          currentPage={currentPage}
          total={total}
          onPageChange={setCurrentPage}
          emptyTitle={locale === "zh" ? "暂无合同数据" : "No Contracts"}
          emptyDescription={locale === "zh" ? "当前没有符合条件的合同记录，请调整筛选条件或刷新重试" : "No matching contract records. Adjust filters or refresh."}
          emptyIcon={<FileText className="w-12 h-12 opacity-40" />}
        />
      </div>

      {/* Error banner (when data exists but refresh fails) */}
      {error && contracts.length > 0 && (
        <div className="flex items-center gap-2 px-4 py-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-xs text-red-700 dark:text-red-400">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={loadContracts} className="ml-auto font-semibold underline hover:no-underline">
            {locale === "zh" ? "重试" : "Retry"}
          </button>
        </div>
      )}
    </div>
  );
}

// ── KPI Card ────────────────────────────────────────────
function KpiCard({ icon: Icon, label, value, color, bgColor }: {
  icon: any; label: string; value: string | number; color: string; bgColor: string;
}) {
  return (
    <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border border-gray-200 dark:border-gray-700 ${bgColor}`}>
      <Icon className={`w-8 h-8 ${color}`} />
      <div>
        <div className="text-xl font-bold">{value}</div>
        <div className="text-[10px] text-slate-500 dark:text-slate-400">{label}</div>
      </div>
    </div>
  );
}
