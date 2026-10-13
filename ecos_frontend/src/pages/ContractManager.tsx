/**
 * ContractManager — 高速信科合同管理
 * 调用 /api/v1/ecos/objects/Contract 获取合同列表
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  FileText, Search, RefreshCw, AlertCircle, Loader2,
  DollarSign, Calendar, User, Building2, CheckCircle,
  Clock, XCircle, TrendingUp, ChevronDown, ChevronRight
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData, isNoAccessError, NetworkError } from "../api";
import DataTable, { ColumnConfig } from "../components/common/DataTable";
import MobileDataTable, { MobileCardConfig } from "../components/common/MobileDataTable";

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

// 状态徽章走 ThemeContext 语义令牌（success/info/warning/danger/badge/neutral），4 主题可翻；
// 不再硬编 bg-*-100 / text-*-700 / border-*-300 浅色档（dark 主题下浅底深字塌陷）
const statusBadgeClass = (status: string, styles: Record<string, string>): string => {
  switch (status) {
    case "active": return `${styles.successBg} ${styles.successText} ${styles.successBorder}`;
    case "completed": return `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`;
    case "pending": return `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`;
    case "terminated": return `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`;
    case "draft": return `${styles.badgeBg} ${styles.badgeText} ${styles.accentBorder}`;
    default: return `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
  }
};

const PAGE_SIZE = 10;

// ── Helpers ────────────────────────────────────────────
function fmtAmount(v: number | string | undefined | null, t: (k: string, p?: Record<string, string | number>) => string): string {
  if (v == null) return "—";
  const n = Number(v);
  if (isNaN(n)) return String(v);
  if (n >= 100000000) return t("contract.unitYi", { v: (n / 100000000).toFixed(2) });
  if (n >= 10000) return t("contract.unit10k", { v: (n / 10000).toFixed(1) });
  return n.toLocaleString();
}

// ── Component ──────────────────────────────────────────
export default function ContractManager() {
  const { locale, t } = useLanguage();
  const { styles } = useTheme();

  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [searchQ, setSearchQ] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [currentPage, setCurrentPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const loadContracts = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams();
      params.set("page", String(currentPage));
      params.set("pageSize", String(PAGE_SIZE));
      if (statusFilter !== "all") params.set("status", statusFilter);
      if (searchQ.trim()) params.set("search", searchQ.trim());

      const data: any = await apiFetchData(`/api/v1/ecos/objects/Contract?${params.toString()}`);

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
    } catch (e: unknown) {
      // 分级错误：403（无权限）/ 网络断连 / HTTP 状态各自可辨，不直排原始报文
      if (isNoAccessError(e)) {
        setError(t("contract.error.forbidden"));
      } else if (e instanceof NetworkError) {
        setError(t("contract.error.network"));
      } else {
        const status = (e as { status?: number } | null)?.status;
        setError(typeof status === "number" ? t("contract.error.http", { status }) : t("contract.loadFailed"));
      }
      setContracts([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, statusFilter, searchQ, locale, t]);

  useEffect(() => { loadContracts(); }, [loadContracts]);

  // Debounced search resets page（原 `const t = setTimeout` 遮蔽了 i18n 的 t，重命名为 timer）
  useEffect(() => {
    const timer = setTimeout(() => setCurrentPage(1), 400);
    return () => clearTimeout(timer);
  }, [searchQ]);

  // ── Computed KPIs ────────────────────────────────────
  // 口径说明：totalCount = 服务端全量计数；totalAmount/activeCount/completedCount
  // 只统计当前页行 —— KPI 卡标签须标注「本页」，禁止与全量数并列造成混口径误读
  const totalCount = total;
  const totalAmount = contracts.reduce((sum, c) => sum + (Number(c.amount) || 0), 0);
  const activeCount = contracts.filter(c => c.status === "active").length;
  const completedCount = contracts.filter(c => c.status === "completed").length;

  // ── Columns ──────────────────────────────────────────
  const columns: ColumnConfig<Contract>[] = [
    {
      key: "code",
      label: t("contract.col.code"),
      render: (_v, record) => (
        <span className={`font-mono text-[11px] ${styles.cardText}`}>{record.code || record.contractNo || record.id || "—"}</span>
      ),
    },
    {
      key: "name",
      label: t("contract.col.name"),
      render: (_v, record) => (
        // 详情入口：点击展开合同详情（闭环不再止步列表）
        <button type="button"
          className={`text-left font-medium hover:underline flex items-center gap-1 ${styles.cardText}`}
          onClick={(e) => {
            e.stopPropagation();
            setExpandedId(expandedId === record.id ? null : record.id);
          }}
          title={t("contract.detail.entry")}
        >
          {expandedId === record.id
            ? <ChevronDown className="w-3.5 h-3.5 shrink-0" />
            : <ChevronRight className="w-3.5 h-3.5 shrink-0" />
          }
          <span className="truncate max-w-[180px]">{record.name || "—"}</span>
        </button>
      ),
    },
    {
      key: "partyA",
      label: t("contract.col.partyA"),
      render: (_v, record) => {
        const a = record.partyA || record.clientName;
        return (
          <span className={`flex items-center gap-1 text-xs ${styles.cardText}`}>
            <Building2 className={`w-3 h-3 ${styles.cardTextMuted}`} />
            {a || "—"}
          </span>
        );
      },
    },
    {
      key: "amount",
      label: t("contract.col.amount"),
      align: "right",
      render: (_v, record) => (
        <span className={`font-mono text-xs font-semibold ${styles.cardText}`}>{fmtAmount(record.amount, t)}</span>
      ),
    },
    {
      key: "signDate",
      label: t("contract.col.signDate"),
      render: (_v, record) => (
        <span className={`flex items-center gap-1 text-xs whitespace-nowrap ${styles.cardTextMuted}`}>
          <Calendar className={`w-3 h-3 ${styles.cardTextMuted}`} />
          {record.signDate || "—"}
        </span>
      ),
    },
    {
      key: "status",
      label: t("contract.col.status"),
      render: (_v, record) => {
        const color = statusBadgeClass(record.status, styles);
        return (
          <span className={`inline-block px-2 py-0.5 text-[10px] font-semibold rounded border ${color}`}>
            {t(`contract.status.${record.status}`)}
          </span>
        );
      },
    },
  ];

  // ── Mobile card config（移动端卡片态：合同列表）─────────
  // 卡片头：合同编号（主键语义）+ 金额（业务关键）
  // 详情折叠区：合同名称 / 甲方 / 签署日期 / 状态
  const mobileConfig: MobileCardConfig<Contract> = useMemo(
    () => ({
      headerKeys: ["code", "amount"],
      detailKeys: ["name", "partyA", "signDate", "status"],
    }),
    [],
  );

  // ── Loading State ────────────────────────────────────
  if (loading && !contracts.length) {
    return (
      <div className={`h-full flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center space-y-3">
          <Loader2 className={`w-8 h-8 ${styles.accentText} animate-spin mx-auto`} />
          <p className={`text-sm ${styles.muted}`}>
            {t("contract.loading")}
          </p>
        </div>
      </div>
    );
  }

  // ── Error State ──────────────────────────────────────
  if (error && !contracts.length) {
    return (
      <div className={`h-full flex items-center justify-center p-6 ${styles.appBg}`}>
        <div className="text-center max-w-sm">
          <AlertCircle className={`w-10 h-10 ${styles.dangerText} mx-auto mb-3`} />
          <p className={`text-sm font-semibold ${styles.dangerText} mb-1`}>
            {t("contract.error")}
          </p>
          <p className={`text-xs ${styles.muted} mb-4`}>{error}</p>
          <button type="button"
            onClick={loadContracts}
            className={`inline-flex items-center gap-1.5 px-4 py-2 ${styles.accentBg} ${styles.accentHover} text-white text-xs font-semibold rounded-lg transition`}
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {t("biz.retry")}
          </button>
        </div>
      </div>
    );
  }

  // ── Render ───────────────────────────────────────────
  return (
    <div className={`flex-1 overflow-y-auto p-6 font-sans ${styles.appBg} ${styles.appText} space-y-4`}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold flex items-center gap-2">
            <FileText className={`w-5 h-5 ${styles.accentText}`} />
            {t("contract.title")}
          </h1>
          <p className={`text-xs ${styles.muted} mt-1`}>
            {t("contract.subtitle")}
          </p>
        </div>
        <button type="button"
          onClick={() => { setCurrentPage(1); loadContracts(); }}
          disabled={loading}
          className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} hover:opacity-80 transition disabled:opacity-50`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? t("projectTracker.refreshing") : t("projectTracker.refresh")}
        </button>
      </div>

      {/* KPI Cards — 混口径注明：total 为服务端全量，其余三卡仅统计当前页 */}
      {!loading && (
        <div className="space-y-2">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <KpiCard
              icon={FileText}
              label={t("contract.total")}
              value={totalCount}
              color={styles.accentText}
              styles={styles}
            />
            <KpiCard
              icon={DollarSign}
              label={`${t("contract.totalAmount")} ${t("contract.scope.page")}`}
              value={fmtAmount(totalAmount, t)}
              color="text-orange-500"
              styles={styles}
            />
            <KpiCard
              icon={CheckCircle}
              label={`${t("contract.status.active")} ${t("contract.scope.page")}`}
              value={activeCount}
              color="text-green-500"
              styles={styles}
            />
            <KpiCard
              icon={TrendingUp}
              label={`${t("contract.status.completed")} ${t("contract.scope.page")}`}
              value={completedCount}
              color="text-blue-500"
              styles={styles}
            />
          </div>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>{t("contract.caliber.note")}</p>
        </div>
      )}

      {/* Search & Filter */}
      <div className={`flex flex-col sm:flex-row gap-3 p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        <div className="relative flex-1">
          <Search className={`absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 ${styles.muted}`} />
          <input
            type="text"
            value={searchQ}
            onChange={(e) => setSearchQ(e.target.value)}
            placeholder={t("contract.search")}
            className={`w-full pl-9 pr-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
          className={`px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
        >
          <option value="all">{t("projectTracker.all")}</option>
          <option value="active">{t("contract.status.active")}</option>
          <option value="completed">{t("contract.status.completed")}</option>
          <option value="pending">{t("contract.status.pending")}</option>
          <option value="terminated">{t("contract.status.terminated")}</option>
          <option value="draft">{t("contract.status.draft")}</option>
        </select>
      </div>

      {/* DataTable */}
      <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
        <MobileDataTable<Contract>
          mobileConfig={mobileConfig}
          columns={columns}
          data={contracts}
          rowKey="id"
          loading={loading}
          pageSize={PAGE_SIZE}
          currentPage={currentPage}
          total={total}
          onPageChange={setCurrentPage}
          onRowClick={(record) => setExpandedId(expandedId === record.id ? null : record.id)}
          emptyTitle={t("contract.empty.title")}
          emptyDescription={t("contract.empty.desc")}
          emptyIcon={<FileText className="w-12 h-12 opacity-40" />}
        />
      </div>

      {/* Contract detail panel — 详情入口闭环（列表 → 详情） */}
      {expandedId && (() => {
        const c = contracts.find(x => x.id === expandedId);
        if (!c) return null;
        return (
          <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} p-5 space-y-4`}>
            <div className="flex items-center justify-between">
              <h3 className={`text-base font-bold flex items-center gap-2 ${styles.cardText}`}>
                <FileText className={`w-4 h-4 ${styles.accentText}`} />
                {c.name || c.code || c.contractNo || c.id}
              </h3>
              <button type="button"
                onClick={() => setExpandedId(null)}
                className={`text-xs ${styles.cardTextMuted} hover:opacity-70 transition`}
              >
                {t("contract.detail.close")}
              </button>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
              <DetailField icon={FileText} label={t("contract.col.code")} value={c.code || c.contractNo || c.id || "—"} styles={styles} />
              <DetailField icon={Building2} label={t("contract.col.partyA")} value={c.partyA || c.clientName || "—"} styles={styles} />
              <DetailField icon={DollarSign} label={t("contract.col.amount")} value={fmtAmount(c.amount, t)} styles={styles} />
              <DetailField icon={Calendar} label={t("contract.col.signDate")} value={c.signDate || "—"} styles={styles} />
              <DetailField icon={CheckCircle} label={t("contract.col.status")} value={t(`contract.status.${c.status}`)} styles={styles} />
              <DetailField icon={Clock} label="ID" value={c.id} styles={styles} />
            </div>
            {c.description && (
              <div>
                <p className={`text-xs font-semibold ${styles.cardTextMuted} mb-1`}>{t("contract.detail.desc")}</p>
                <p className={`text-sm ${styles.cardText}`}>{c.description}</p>
              </div>
            )}
          </div>
        );
      })()}

      {/* Error banner (when data exists but refresh fails) */}
      {error && contracts.length > 0 && (
        <div className={`flex items-center gap-2 px-4 py-3 ${styles.dangerBg} border ${styles.dangerBorder} rounded-lg text-xs ${styles.dangerText}`}>
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button type="button" onClick={loadContracts} className="ml-auto font-semibold underline hover:no-underline">
            {t("biz.retry")}
          </button>
        </div>
      )}
    </div>
  );
}

// ── KPI Card ────────────────────────────────────────────
function KpiCard({ icon: Icon, label, value, color, styles }: {
  icon: any; label: string; value: string | number; color: string; styles: any;
}) {
  return (
    <div className={`flex items-center gap-3 px-4 py-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
      <Icon className={`w-8 h-8 ${color}`} />
      <div>
        <div className={`text-xl font-bold ${styles.cardText}`}>{value}</div>
        <div className={`text-[10px] ${styles.cardTextMuted}`}>{label}</div>
      </div>
    </div>
  );
}

function DetailField({ icon: Icon, label, value, styles }: {
  icon: React.ComponentType<{ className?: string }>; label: string; value: string; styles: any;
}) {
  return (
    <div className="flex items-start gap-2">
      <Icon className={`w-3.5 h-3.5 ${styles.cardTextMuted} mt-0.5 shrink-0`} />
      <div className="min-w-0">
        <p className={`text-[10px] ${styles.cardTextMuted} uppercase`}>{label}</p>
        <p className={`text-sm font-medium ${styles.cardText} break-words`}>{value}</p>
      </div>
    </div>
  );
}
