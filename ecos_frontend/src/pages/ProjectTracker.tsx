/**
 * ProjectTracker — 高速信科项目跟踪
 * 调用 /api/v1/ecos/objects/Project 获取项目列表
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  Briefcase, Search, RefreshCw, AlertCircle, Loader2,
  ChevronDown, ChevronRight, Calendar, User, DollarSign,
  Target, Clock
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import DataTable, { ColumnConfig } from "../components/common/DataTable";
import MobileDataTable, { MobileCardConfig } from "../components/common/MobileDataTable";

// ── Types ──────────────────────────────────────────────
interface Project {
  id: string;
  name: string;
  status: string;
  progress?: number;
  manager?: string;
  amount?: number | string;
  startDate?: string;
  endDate?: string;
  description?: string;
  [key: string]: any;
}

// 状态徽章走 ThemeContext 语义令牌（success/info/warning/badge/neutral），4 主题可翻；
// 不再硬编 bg-*-100 / text-*-700 / border-*-300 浅色档（dark 主题下浅底深字塌陷）
const statusBadgeClass = (status: string, styles: Record<string, string>): string => {
  switch (status) {
    case "active": return `${styles.successBg} ${styles.successText} ${styles.successBorder}`;
    case "completed": return `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`;
    case "paused": return `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`;
    case "planning": return `${styles.badgeBg} ${styles.badgeText} ${styles.accentBorder}`;
    default: return `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
  }
};

const PAGE_SIZE = 10;

// ── Component ──────────────────────────────────────────
export default function ProjectTracker() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [searchQ, setSearchQ] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const loadProjects = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams();
      params.set("page", String(currentPage));
      params.set("pageSize", String(PAGE_SIZE));
      if (statusFilter !== "all") params.set("status", statusFilter);
      if (searchQ.trim()) params.set("search", searchQ.trim());

      const data: any = await apiFetchData(`/api/v1/ecos/objects/Project?${params.toString()}`);
      if (Array.isArray(data)) {
        setProjects(data);
        setTotal(data.length);
      } else if (data.records || data.list) {
        setProjects(data.records || data.list || []);
        setTotal(data.total || 0);
      } else {
        setProjects([]);
        setTotal(0);
      }
    } catch (e: any) {
      setError(e.message || t("projectTracker.loadFailed"));
      setProjects([]);
    } finally {
      setLoading(false);
    }
  }, [currentPage, statusFilter, searchQ]);

  useEffect(() => { loadProjects(); }, [loadProjects]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      setCurrentPage(1);
    }, 400);
    return () => clearTimeout(timer);
  }, [searchQ]);

  // ── DataTable columns ───────────────────────────────
  const columns: ColumnConfig<Project>[] = [
    {
      key: "name",
      label: t("projectTracker.col.name"),
      render: (_v, record) => (
        <button
          className={`text-left font-medium hover:underline flex items-center gap-1 ${styles.cardText}`}
          onClick={(e) => {
            e.stopPropagation();
            setExpandedId(expandedId === record.id ? null : record.id);
          }}
        >
          {expandedId === record.id
            ? <ChevronDown className="w-3.5 h-3.5 shrink-0" />
            : <ChevronRight className="w-3.5 h-3.5 shrink-0" />
          }
          <span className="truncate max-w-[200px]">{record.name || "—"}</span>
        </button>
      ),
    },
    {
      key: "status",
      label: t("projectTracker.col.status"),
      render: (_v, record) => {
        const color = statusBadgeClass(record.status, styles);
        const label = t(`projectTracker.status.${record.status}`) || record.status;
        return (
          <span className={`inline-block px-2 py-0.5 text-[10px] font-semibold rounded border ${color}`}>
            {label}
          </span>
        );
      },
    },
    {
      key: "progress",
      label: t("projectTracker.col.progress"),
      render: (_v, record) => {
        const pct = typeof record.progress === "number" ? record.progress : Number(record.progress) || 0;
        return (
          <div className="flex items-center gap-2 min-w-[80px]">
            <div className={`flex-1 h-1.5 ${styles.inputBorder} border rounded-full overflow-hidden ${styles.inputBg}`}>
              <div
                className={`h-full ${styles.accentBg.replace("bg-", "bg-")} rounded-full transition-all`}
                style={{ width: `${Math.min(100, Math.max(0, pct))}%`, backgroundColor: undefined }}
              />
            </div>
            <span className={`text-[10px] font-mono w-8 text-right ${styles.cardTextMuted}`}>{pct}%</span>
          </div>
        );
      },
    },
    {
      key: "manager",
      label: t("projectTracker.col.manager"),
      render: (_v, record) => (
        <span className={`flex items-center gap-1 text-xs ${styles.cardText}`}>
          <User className={`w-3 h-3 ${styles.cardTextMuted}`} />
          {record.manager || "—"}
        </span>
      ),
    },
    {
      key: "amount",
      label: t("projectTracker.col.amount"),
      align: "right",
      render: (_v, record) => {
        const v = record.amount;
        if (v == null) return "—";
        const n = Number(v);
        if (isNaN(n)) return String(v);
        return n >= 10000 ? t("projectTracker.unit10k", { v: (n / 10000).toFixed(1) }) : n.toLocaleString();
      },
    },
    {
      key: "startDate",
      label: t("projectTracker.col.dates"),
      render: (_v, record) => (
        <span className={`text-xs whitespace-nowrap ${styles.cardTextMuted}`}>
          {record.startDate || "—"} ~ {record.endDate || "—"}
        </span>
      ),
    },
  ];

  // ── Mobile card config（移动端卡片态：项目跟踪列表）─────────
  // 卡片头：项目名称（主键语义）+ 状态（最高业务优先级）
  // 详情折叠区：进度 / 负责人 / 金额 / 起止日期
  const mobileConfig: MobileCardConfig<Project> = useMemo(
    () => ({
      headerKeys: ["name", "status"],
      detailKeys: ["progress", "manager", "amount", "startDate"],
    }),
    [],
  );

  // ── Loading State ────────────────────────────────────
  if (loading && !projects.length) {
    return (
      <div className={`h-full flex items-center justify-center ${styles.appBg}`}>
        <div className="text-center space-y-3">
          <Loader2 className={`w-8 h-8 ${styles.accentText} animate-spin mx-auto`} />
          <p className={`text-sm ${styles.muted}`}>
            {t("projectTracker.loading")}
          </p>
        </div>
      </div>
    );
  }

  // ── Error State ──────────────────────────────────────
  if (error && !projects.length) {
    return (
      <div className={`h-full flex items-center justify-center p-6 ${styles.appBg}`}>
        <div className="text-center max-w-sm">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-sm font-semibold text-red-600 mb-1">
            {t("projectTracker.error")}
          </p>
          <p className={`text-xs ${styles.muted} mb-4`}>{error}</p>
          <button
            onClick={loadProjects}
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
            <Briefcase className={`w-5 h-5 ${styles.accentText}`} />
            {t("projectTracker.title")}
          </h1>
          <p className={`text-xs ${styles.muted} mt-1`}>
            {t("projectTracker.subtitle")}
          </p>
        </div>
        <button
          onClick={() => { setCurrentPage(1); loadProjects(); }}
          disabled={loading}
          className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} hover:opacity-80 transition disabled:opacity-50`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? t("projectTracker.refreshing") : t("projectTracker.refresh")}
        </button>
      </div>

      {/* Filters */}
      <div className={`flex flex-col sm:flex-row gap-3 p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        {/* Search */}
        <div className="relative flex-1">
          <Search className={`absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 ${styles.muted}`} />
          <input
            type="text"
            value={searchQ}
            onChange={(e) => setSearchQ(e.target.value)}
            placeholder={t("projectTracker.search")}
            className={`w-full pl-9 pr-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
          />
        </div>

        {/* Status filter */}
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
          className={`px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
        >
          <option value="all">{t("projectTracker.all")}</option>
          <option value="active">{t("projectTracker.status.active")}</option>
          <option value="completed">{t("projectTracker.status.completed")}</option>
          <option value="paused">{t("projectTracker.status.paused")}</option>
          <option value="planning">{t("projectTracker.status.planning")}</option>
        </select>
      </div>

      {/* Summary stats */}
      {!loading && projects.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <StatPill icon={Briefcase} label={t("projectTracker.total")} value={total} color={styles.accentText} styles={styles} />
          <StatPill icon={Target} label={t("projectTracker.status.active")} value={projects.filter(p => p.status === "active").length} color="text-green-500" styles={styles} />
          <StatPill icon={Clock} label={t("projectTracker.status.completed")} value={projects.filter(p => p.status === "completed").length} color="text-blue-500" styles={styles} />
          <StatPill icon={DollarSign} label={t("projectTracker.totalAmount")} value={projects.reduce((sum, p) => sum + (Number(p.amount) || 0), 0) / 10000} color="text-orange-500" fmt="money" unit={t("projectTracker.wan")} styles={styles} />
        </div>
      )}

      {/* DataTable */}
      <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
        <MobileDataTable<Project>
          mobileConfig={mobileConfig}
          columns={columns}
          data={projects}
          rowKey="id"
          loading={loading}
          pageSize={PAGE_SIZE}
          currentPage={currentPage}
          total={total}
          onPageChange={setCurrentPage}
          onRowClick={(record) => setExpandedId(expandedId === record.id ? null : record.id)}
          emptyTitle={t("projectTracker.empty.title")}
          emptyDescription={t("projectTracker.empty.desc")}
          emptyIcon={<Briefcase className="w-12 h-12 opacity-40" />}
        />
      </div>

      {/* Expanded detail panel */}
      {expandedId && (() => {
        const proj = projects.find(p => p.id === expandedId);
        if (!proj) return null;
        return (
          <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} p-5 space-y-4`}>
            <div className="flex items-center justify-between">
              <h3 className={`text-base font-bold flex items-center gap-2 ${styles.cardText}`}>
                <Briefcase className={`w-4 h-4 ${styles.accentText}`} />
                {proj.name}
              </h3>
              <button
                onClick={() => setExpandedId(null)}
                className={`text-xs ${styles.cardTextMuted} hover:opacity-70 transition`}
              >
                {t("projectTracker.collapse")}
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
              <DetailField
                icon={Target}
                label={t("projectTracker.col.status")}
                value={t(`projectTracker.status.${proj.status}`) || proj.status}
                styles={styles}
              />
              <DetailField
                icon={Target}
                label={t("projectTracker.col.progress")}
                value={`${proj.progress ?? 0}%`}
                styles={styles}
              />
              <DetailField
                icon={User}
                label={t("projectTracker.col.manager")}
                value={proj.manager || "—"}
                styles={styles}
              />
              <DetailField
                icon={DollarSign}
                label={t("projectTracker.col.amount")}
                value={proj.amount != null ? t("projectTracker.unit10k", { v: (Number(proj.amount) / 10000).toFixed(1) }) : "—"}
                styles={styles}
              />
              <DetailField
                icon={Calendar}
                label={t("projectTracker.start")}
                value={proj.startDate || "—"}
                styles={styles}
              />
              <DetailField
                icon={Calendar}
                label={t("projectTracker.end")}
                value={proj.endDate || "—"}
                styles={styles}
              />
              <DetailField
                icon={Clock}
                label="ID"
                value={proj.id}
                styles={styles}
              />
            </div>

            {proj.description && (
              <div>
                <p className={`text-xs font-semibold ${styles.cardTextMuted} mb-1`}>
                  {t("projectTracker.desc")}
                </p>
                <p className={`text-sm ${styles.cardText}`}>{proj.description}</p>
              </div>
            )}
          </div>
        );
      })()}
    </div>
  );
}

// ── Sub-components ─────────────────────────────────────
function StatPill({ icon: Icon, label, value, color, fmt, styles, unit }: {
  icon: any; label: string; value: number; color: string; fmt?: string; styles: any; unit?: string;
}) {
  const display = fmt === "money" ? `${value.toFixed(1)} ${unit ?? ""}`.trim() : value;
  return (
    <div className={`flex items-center gap-2 px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
      <Icon className={`w-4 h-4 ${color}`} />
      <div>
        <div className={`text-lg font-bold ${styles.cardText}`}>{display}</div>
        <div className={`text-[10px] ${styles.cardTextMuted}`}>{label}</div>
      </div>
    </div>
  );
}

function DetailField({ icon: Icon, label, value, styles }: {
  icon: any; label: string; value: string; styles: any;
}) {
  return (
    <div className="flex items-start gap-2">
      <Icon className={`w-3.5 h-3.5 ${styles.cardTextMuted} mt-0.5 shrink-0`} />
      <div>
        <p className={`text-[10px] ${styles.cardTextMuted} uppercase`}>{label}</p>
        <p className={`text-sm font-medium ${styles.cardText}`}>{value}</p>
      </div>
    </div>
  );
}
