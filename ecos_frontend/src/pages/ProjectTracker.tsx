/**
 * ProjectTracker — 高速信科项目跟踪
 * 调用 /api/v1/gsxk/objects/Project 获取项目列表
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Briefcase, Search, RefreshCw, AlertCircle, Loader2,
  ChevronDown, ChevronRight, Calendar, User, DollarSign,
  Target, Clock
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";
import DataTable, { ColumnConfig } from "../components/common/DataTable";

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

const STATUS_MAP: Record<string, { label: string; color: string }> = {
  active: { label: "进行中", color: "bg-green-100 text-green-700 border-green-300 dark:bg-green-900/30 dark:text-green-400 dark:border-green-700" },
  completed: { label: "已完成", color: "bg-blue-100 text-blue-700 border-blue-300 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-700" },
  paused: { label: "暂停", color: "bg-amber-100 text-amber-700 border-amber-300 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-700" },
  planning: { label: "规划中", color: "bg-gray-100 text-gray-600 border-gray-300 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-600" },
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

      const data: any = await apiFetchData(`/api/v1/gsxk/objects/Project?${params.toString()}`);
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
      setError(e.message || "加载项目列表失败");
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
      label: locale === "zh" ? "项目名称" : "Project Name",
      render: (_v, record) => (
        <button
          className="text-left font-medium hover:underline flex items-center gap-1"
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
    {
      key: "progress",
      label: locale === "zh" ? "进度" : "Progress",
      render: (_v, record) => {
        const pct = typeof record.progress === "number" ? record.progress : Number(record.progress) || 0;
        return (
          <div className="flex items-center gap-2 min-w-[80px]">
            <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-indigo-500 rounded-full transition-all"
                style={{ width: `${Math.min(100, Math.max(0, pct))}%` }}
              />
            </div>
            <span className="text-[10px] font-mono w-8 text-right">{pct}%</span>
          </div>
        );
      },
    },
    {
      key: "manager",
      label: locale === "zh" ? "负责人" : "Manager",
      render: (_v, record) => (
        <span className="flex items-center gap-1 text-xs">
          <User className="w-3 h-3 opacity-50" />
          {record.manager || "—"}
        </span>
      ),
    },
    {
      key: "amount",
      label: locale === "zh" ? "合同金额" : "Amount",
      align: "right",
      render: (_v, record) => {
        const v = record.amount;
        if (v == null) return "—";
        const n = Number(v);
        if (isNaN(n)) return String(v);
        return n >= 10000 ? `${(n / 10000).toFixed(1)} 万` : n.toLocaleString();
      },
    },
    {
      key: "startDate",
      label: locale === "zh" ? "起止日期" : "Duration",
      render: (_v, record) => (
        <span className="text-xs whitespace-nowrap">
          {record.startDate || "—"} ~ {record.endDate || "—"}
        </span>
      ),
    },
  ];

  // ── Loading State ────────────────────────────────────
  if (loading && !projects.length) {
    return (
      <div className="h-full flex items-center justify-center">
        <div className="text-center space-y-3">
          <Loader2 className="w-8 h-8 text-indigo-500 animate-spin mx-auto" />
          <p className="text-sm text-slate-400">
            {locale === "zh" ? "加载项目数据..." : "Loading projects..."}
          </p>
        </div>
      </div>
    );
  }

  // ── Error State ──────────────────────────────────────
  if (error && !projects.length) {
    return (
      <div className="h-full flex items-center justify-center p-6">
        <div className="text-center max-w-sm">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-sm font-semibold text-red-600 mb-1">
            {locale === "zh" ? "数据加载失败" : "Failed to load data"}
          </p>
          <p className="text-xs text-slate-500 mb-4">{error}</p>
          <button
            onClick={loadProjects}
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
            <Briefcase className="w-5 h-5 text-indigo-500" />
            {locale === "zh" ? "项目跟踪" : "Project Tracker"}
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            {locale === "zh" ? "高速信科工程项目全生命周期管理" : "GSXK project lifecycle management"}
          </p>
        </div>
        <button
          onClick={() => { setCurrentPage(1); loadProjects(); }}
          disabled={loading}
          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-lg border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800 transition disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {loading ? (locale === "zh" ? "刷新中..." : "Refreshing...") : (locale === "zh" ? "刷新" : "Refresh")}
        </button>
      </div>

      {/* Filters */}
      <div className={`flex flex-col sm:flex-row gap-3 p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        {/* Search */}
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            value={searchQ}
            onChange={(e) => setSearchQ(e.target.value)}
            placeholder={locale === "zh" ? "搜索项目名称..." : "Search projects..."}
            className={`w-full pl-9 pr-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
          />
        </div>

        {/* Status filter */}
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setCurrentPage(1); }}
          className={`px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-2 focus:ring-indigo-500/30 transition`}
        >
          <option value="all">{locale === "zh" ? "全部状态" : "All Status"}</option>
          <option value="active">{locale === "zh" ? "进行中" : "Active"}</option>
          <option value="completed">{locale === "zh" ? "已完成" : "Completed"}</option>
          <option value="paused">{locale === "zh" ? "暂停" : "Paused"}</option>
          <option value="planning">{locale === "zh" ? "规划中" : "Planning"}</option>
        </select>
      </div>

      {/* Summary stats */}
      {!loading && projects.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <StatPill icon={Briefcase} label={locale === "zh" ? "总项目" : "Total"} value={total} color="text-indigo-500" />
          <StatPill icon={Target} label={locale === "zh" ? "进行中" : "Active"} value={projects.filter(p => p.status === "active").length} color="text-green-500" />
          <StatPill icon={Clock} label={locale === "zh" ? "已完成" : "Done"} value={projects.filter(p => p.status === "completed").length} color="text-blue-500" />
          <StatPill icon={DollarSign} label={locale === "zh" ? "总金额(万)" : "Total (万)"} value={projects.reduce((sum, p) => sum + (Number(p.amount) || 0), 0) / 10000} color="text-orange-500" fmt="money" />
        </div>
      )}

      {/* DataTable */}
      <div className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}>
        <DataTable<Project>
          columns={columns}
          data={projects}
          rowKey="id"
          loading={loading}
          pageSize={PAGE_SIZE}
          currentPage={currentPage}
          total={total}
          onPageChange={setCurrentPage}
          onRowClick={(record) => setExpandedId(expandedId === record.id ? null : record.id)}
          emptyTitle={locale === "zh" ? "暂无项目数据" : "No Projects"}
          emptyDescription={locale === "zh" ? "当前没有符合条件的项目记录" : "No matching project records found"}
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
              <h3 className="text-base font-bold flex items-center gap-2">
                <Briefcase className="w-4 h-4 text-indigo-500" />
                {proj.name}
              </h3>
              <button
                onClick={() => setExpandedId(null)}
                className="text-xs text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition"
              >
                {locale === "zh" ? "收起 ▲" : "Collapse ▲"}
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
              <DetailField
                icon={Target}
                label={locale === "zh" ? "状态" : "Status"}
                value={STATUS_MAP[proj.status]?.label || proj.status}
              />
              <DetailField
                icon={Target}
                label={locale === "zh" ? "进度" : "Progress"}
                value={`${proj.progress ?? 0}%`}
              />
              <DetailField
                icon={User}
                label={locale === "zh" ? "负责人" : "Manager"}
                value={proj.manager || "—"}
              />
              <DetailField
                icon={DollarSign}
                label={locale === "zh" ? "合同金额" : "Amount"}
                value={proj.amount != null ? `${(Number(proj.amount) / 10000).toFixed(1)} 万` : "—"}
              />
              <DetailField
                icon={Calendar}
                label={locale === "zh" ? "开始日期" : "Start Date"}
                value={proj.startDate || "—"}
              />
              <DetailField
                icon={Calendar}
                label={locale === "zh" ? "结束日期" : "End Date"}
                value={proj.endDate || "—"}
              />
              <DetailField
                icon={Clock}
                label="ID"
                value={proj.id}
              />
            </div>

            {proj.description && (
              <div>
                <p className="text-xs font-semibold text-slate-500 mb-1">
                  {locale === "zh" ? "项目描述" : "Description"}
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
function StatPill({ icon: Icon, label, value, color, fmt }: {
  icon: any; label: string; value: number; color: string; fmt?: string;
}) {
  const display = fmt === "money" ? `${value.toFixed(1)} 万` : value;
  return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900/50">
      <Icon className={`w-4 h-4 ${color}`} />
      <div>
        <div className="text-lg font-bold">{display}</div>
        <div className="text-[10px] text-slate-400">{label}</div>
      </div>
    </div>
  );
}

function DetailField({ icon: Icon, label, value }: {
  icon: any; label: string; value: string;
}) {
  return (
    <div className="flex items-start gap-2">
      <Icon className="w-3.5 h-3.5 text-slate-400 mt-0.5 shrink-0" />
      <div>
        <p className="text-[10px] text-slate-400 uppercase">{label}</p>
        <p className="text-sm font-medium">{value}</p>
      </div>
    </div>
  );
}
