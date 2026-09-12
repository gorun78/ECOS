/**
 * TaskPanel — Task Engine Management Panel
 * Stats cards + filter bar + task list + batch operations + detail drawer
 * Supports 4 categories: Pipeline / Agent / Realtime / Management
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { X, RefreshCw, Search, Play, Pause, RotateCcw, Square, Archive, GitBranch, Bot, Zap, SlidersHorizontal } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import { useToast } from "./common/Toast";
import ConfirmDialog from "./common/ConfirmDialog";

// ── 类型定义 ──────────────────────────────────────────────

type TaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

/** 四类分组标识 */
type TaskCategory = "pipeline" | "agent" | "realtime" | "management";

interface TaskItem {
  taskId: string;
  taskName: string;
  taskType: string;
  description: string;
  priority: string;
  createTime: string;
  createdBy: string;
  parameters: Record<string, any> | null;
}

interface TaskStatusInfo {
  status: TaskStatus;
  progress: number;
  startedAt: string | null;
  completedAt: string | null;
}

interface TaskDetail {
  task: TaskItem;
  status: TaskStatusInfo;
}

interface TaskStats {
  total: number;
  running: number;
  pending: number;
  succeeded: number;
  failed: number;
  cancelled: number;
}

// ── 四类分组常量 ──────────────────────────────────────────

interface CategoryConfig {
  key: TaskCategory;
  /** i18n key for the category label */
  labelKey: string;
  /** 该分类包含的任务类型 */
  types: string[];
  /** Tailwind 颜色（用于卡片左侧色条 + 标题） */
  color: string;
  /** 背景色 */
  bg: string;
  /** 文字色 */
  textColor: string;
  /** Badge 背景 + 文字 */
  badgeBg: string;
  badgeText: string;
  /** lucide-react 图标组件 */
  Icon: typeof GitBranch;
}

const CATEGORIES: CategoryConfig[] = [
  {
    key: "pipeline",
    labelKey: "taskPanel.category.pipeline",
    types: ["DORIS_SQL", "ETL", "DATA_SYNC", "PIPELINE"],
    color: "border-l-blue-500",
    bg: "bg-blue-50/60 dark:bg-blue-900/20",
    textColor: "text-blue-600 dark:text-blue-400",
    badgeBg: "bg-blue-100 dark:bg-blue-900/40",
    badgeText: "text-blue-700 dark:text-blue-300",
    Icon: GitBranch,
  },
  {
    key: "agent",
    labelKey: "taskPanel.category.agent",
    types: ["AGENT", "AI_AGENT", "LLM_TASK", "KG_SYNC"],
    color: "border-l-purple-500",
    bg: "bg-purple-50/60 dark:bg-purple-900/20",
    textColor: "text-purple-600 dark:text-purple-400",
    badgeBg: "bg-purple-100 dark:bg-purple-900/40",
    badgeText: "text-purple-700 dark:text-purple-300",
    Icon: Bot,
  },
  {
    key: "realtime",
    labelKey: "taskPanel.category.realtime",
    types: ["REALTIME", "STREAMING", "MONITOR", "ALERT", "TELEMETRY"],
    color: "border-l-green-500",
    bg: "bg-green-50/60 dark:bg-green-900/20",
    textColor: "text-green-600 dark:text-green-400",
    badgeBg: "bg-green-100 dark:bg-green-900/40",
    badgeText: "text-green-700 dark:text-green-300",
    Icon: Zap,
  },
  {
    key: "management",
    labelKey: "taskPanel.category.management",
    types: ["DATA_QUALITY", "REPORT", "MAINTENANCE", "BACKUP", "CONFIG", "ADMIN"],
    color: "border-l-gray-400",
    bg: "bg-gray-50/60 dark:bg-gray-800/40",
    textColor: "text-gray-600 dark:text-gray-400",
    badgeBg: "bg-gray-100 dark:bg-gray-700",
    badgeText: "text-gray-700 dark:text-gray-300",
    Icon: SlidersHorizontal,
  },
];

/** taskType → TaskCategory 快速查找表 */
const TYPE_TO_CATEGORY: Record<string, TaskCategory> = {};
for (const cat of CATEGORIES) {
  for (const t of cat.types) {
    TYPE_TO_CATEGORY[t] = cat.key;
    // 同时注册小写变体
    TYPE_TO_CATEGORY[t.toLowerCase()] = cat.key;
  }
}

/** 根据 taskType 获取分类配置，未匹配返回 null */
function getCategory(taskType: string): CategoryConfig | null {
  const key = TYPE_TO_CATEGORY[taskType] || TYPE_TO_CATEGORY[taskType.toUpperCase()];
  if (!key) return null;
  return CATEGORIES.find(c => c.key === key) || null;
}

// ── 状态颜色映射 ──────────────────────────────────────────

const STATUS_COLORS: Record<TaskStatus, { bg: string; text: string; dot: string }> = {
  PENDING:   { bg: "bg-gray-100 dark:bg-gray-800", text: "text-gray-700 dark:text-gray-300", dot: "bg-gray-400" },
  RUNNING:   { bg: "bg-blue-100 dark:bg-blue-900/40",  text: "text-blue-700 dark:text-blue-300",  dot: "bg-blue-500" },
  SUCCEEDED: { bg: "bg-green-100 dark:bg-green-900/40",text: "text-green-700 dark:text-green-300",dot: "bg-green-500" },
  FAILED:    { bg: "bg-red-100 dark:bg-red-900/40",   text: "text-red-700 dark:text-red-300",   dot: "bg-red-500" },
  CANCELLED: { bg: "bg-yellow-100 dark:bg-yellow-900/40",text: "text-yellow-700 dark:text-yellow-300",dot: "bg-yellow-500" },
};

const STATUS_LABEL_KEYS: Record<TaskStatus, string> = {
  PENDING: "taskPanel.status.pending", RUNNING: "taskPanel.status.running", SUCCEEDED: "taskPanel.status.succeeded", FAILED: "taskPanel.status.failed", CANCELLED: "taskPanel.status.cancelled",
};

const PRIORITY_LABEL_KEYS: Record<string, string> = {
  HIGH: "taskPanel.priority.high", MEDIUM: "taskPanel.priority.medium", LOW: "taskPanel.priority.low", CRITICAL: "taskPanel.priority.critical",
};

// ── Props ──────────────────────────────────────────────────

interface TaskPanelProps {
  open: boolean;
  onClose: () => void;
}

// ── 组件 ──────────────────────────────────────────────────

export default function TaskPanel({ open, onClose }: TaskPanelProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();

  // 列表 & 统计
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [statusMap, setStatusMap] = useState<Record<string, TaskStatusInfo>>({});
  const [stats, setStats] = useState<TaskStats>({ total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
  const [loading, setLoading] = useState(false);

  // 筛选
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterType, setFilterType] = useState("");
  const [searchType, setSearchType] = useState(""); // actual applied type filter
  const [filterCategory, setFilterCategory] = useState<TaskCategory | "">("");

  // 后端任务类型配置
  const [taskTypes, setTaskTypes] = useState<string[]>([]);

  // 分页
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 20;

  // 选择 & 批量操作
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchActioning, setBatchActioning] = useState(false);

  // 详情抽屉
  const [detailTask, setDetailTask] = useState<TaskDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 批量操作确认框
  const [batchConfirm, setBatchConfirm] = useState<null | "cancel" | "pause" | "resume" | "archive">(null);

  // 操作中状态
  const [actingIds, setActingIds] = useState<Set<string>>(new Set());

  const refreshTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 分类计数（客户端计算） ─────────────────────────────

  const categoryCounts = useMemo(() => {
    const counts: Record<string, number> = { pipeline: 0, agent: 0, realtime: 0, management: 0 };
    for (const t of tasks) {
      const cat = getCategory(t.taskType);
      if (cat) counts[cat.key]++;
    }
    return counts;
  }, [tasks]);

  /** 客户端按分类过滤后的任务列表 */
  const filteredTasks = useMemo(() => {
    if (!filterCategory) return tasks;
    return tasks.filter(t => getCategory(t.taskType)?.key === filterCategory);
  }, [tasks, filterCategory]);

  // ── 数据加载 ─────────────────────────────────────────────

  const fetchTaskTypes = useCallback(async () => {
    try {
      const r = await fetch("/api/v1/task/types");
      const d = await r.json();
      if (d.code === 0 && Array.isArray(d.data)) {
        setTaskTypes(d.data);
      }
    } catch { /* silent */ }
  }, []);

  const fetchStats = useCallback(async () => {
    try {
      const r = await fetch("/api/v1/task/stats");
      const d = await r.json();
      if (d.code === 0 && d.data) setStats(d.data);
    } catch { /* silent */ }
  }, []);

  const fetchTasks = useCallback(async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (searchType) params.set("type", searchType);
      if (filterStatus) params.set("status", filterStatus);
      params.set("page", String(page));
      params.set("size", String(pageSize));

      const r = await fetch(`/api/v1/task/list?${params.toString()}`);
      const d = await r.json();
      if (d.code === 0) {
        setTasks(d.data || []);
        setTotal(d.total || 0);
        // Fetch status for each task
        fetchStatuses(d.data || []);
      }
    } catch { /* silent */ }
    finally { setLoading(false); }
  }, [filterStatus, searchType, page]);

  const fetchStatuses = async (taskList: TaskItem[]) => {
    const map: Record<string, TaskStatusInfo> = {};
    await Promise.all(
      taskList.map(async (t) => {
        try {
          const r = await fetch(`/api/v1/task/${t.taskId}`);
          const d = await r.json();
          if (d.code === 0 && d.data?.status) {
            map[t.taskId] = d.data.status;
          }
        } catch { /* skip */ }
      })
    );
    setStatusMap(prev => ({ ...prev, ...map }));
  };

  const refresh = useCallback(async () => {
    await Promise.all([fetchStats(), fetchTasks()]);
  }, [fetchStats, fetchTasks]);

  // ── 定时刷新 ─────────────────────────────────────────────

  useEffect(() => {
    if (!open) return;
    refresh();
    fetchTaskTypes();
    refreshTimer.current = setInterval(refresh, 10_000);
    return () => {
      if (refreshTimer.current) clearInterval(refreshTimer.current);
    };
  }, [open, refresh, fetchTaskTypes]);

  // 筛选变化时重新加载
  useEffect(() => {
    if (!open) return;
    setPage(1);
    fetchTasks();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterStatus, searchType]);

  // 分类变化时重置分页（纯客户端过滤，不重新请求）
  useEffect(() => {
    setPage(1);
  }, [filterCategory]);

  // ── 操作方法 ─────────────────────────────────────────────

  const doAction = async (taskId: string, action: "execute" | "cancel" | "pause" | "resume" | "archive") => {
    setActingIds(prev => new Set(prev).add(taskId));
    try {
      const r = await fetch(`/api/v1/task/${taskId}/${action}`, { method: "POST" });
      const d = await r.json();
      if (d.code !== 0) showToast("error", `${t("taskPanel.actionFailed")}: ${d.message || t("taskPanel.unknownError")}`);
    } catch (e: any) {
      showToast("error", `${t("taskPanel.actionError")}: ${e.message}`);
    } finally {
      setActingIds(prev => { const s = new Set(prev); s.delete(taskId); return s; });
      refresh();
    }
  };

  const doBatchAction = (action: "cancel" | "pause" | "resume" | "archive") => {
    if (selectedIds.size === 0) return;
    setBatchConfirm(action);
  };

  const confirmBatchAction = async () => {
    const action = batchConfirm;
    setBatchConfirm(null);
    if (!action) return;
    setBatchActioning(true);
    try {
      const r = await fetch("/api/v1/task/batch", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ taskIds: [...selectedIds], action }),
      });
      const d = await r.json();
      if (d.code !== 0) showToast("error", `${t("taskPanel.batchFailed")}: ${d.message || t("taskPanel.unknownError")}`);
      else {
        showToast("success", t("taskPanel.batchDone"));
        setSelectedIds(new Set());
        refresh();
      }
    } catch (e: any) {
      showToast("error", `${t("taskPanel.batchError")}: ${e.message}`);
    } finally {
      setBatchActioning(false);
    }
  };

  // ── 详情加载 ─────────────────────────────────────────────

  const openDetail = async (taskId: string) => {
    setDetailLoading(true);
    setDetailTask(null);
    try {
      const r = await fetch(`/api/v1/task/${taskId}`);
      const d = await r.json();
      if (d.code === 0) setDetailTask(d.data);
    } catch { /* silent */ }
    finally { setDetailLoading(false); }
  };

  // ── 全选 ─────────────────────────────────────────────────

  const toggleAll = () => {
    const displayTasks = filteredTasks;
    if (selectedIds.size === displayTasks.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(displayTasks.map(t => t.taskId)));
    }
  };

  const toggleOne = (taskId: string) => {
    setSelectedIds(prev => {
      const s = new Set(prev);
      if (s.has(taskId)) s.delete(taskId); else s.add(taskId);
      return s;
    });
  };

  // ── 辅助 ─────────────────────────────────────────────────

  const formatTime = (ts: string | null, localeHint?: string) => {
    if (!ts) return "—";
    try {
      // Use the active UI locale so timestamps follow the language toggle
      const loc = localeHint || (typeof window !== "undefined" ? (localStorage.getItem("ecos_locale") === "en" ? "en-US" : "zh-CN") : "zh-CN");
      return new Date(ts).toLocaleString(loc);
    } catch { return ts; }
  };

  const renderStatusBadge = (status: TaskStatus) => {
    const c = STATUS_COLORS[status] || STATUS_COLORS.PENDING;
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${c.bg} ${c.text}`}>
        <span className={`w-1.5 h-1.5 rounded-full ${c.dot}`} />
        {t(STATUS_LABEL_KEYS[status] || "taskPanel.status.pending")}
      </span>
    );
  };

  const renderCategoryBadge = (taskType: string) => {
    const cat = getCategory(taskType);
    if (!cat) {
      return (
        <span className={`text-xs px-1.5 py-0.5 rounded ${styles.appBg} ${styles.cardTextMuted}`}>
          —
        </span>
      );
    }
    return (
      <span className={`inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded font-medium ${cat.badgeBg} ${cat.badgeText}`}>
        <cat.Icon size={12} />
        {t(cat.labelKey)}
      </span>
    );
  };

  const renderProgress = (progress: number) => (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${Math.min(100, Math.max(0, progress))}%`, backgroundColor: "var(--accent)" }}
        />
      </div>
      <span className="text-xs text-gray-500 dark:text-gray-400 w-10 text-right">{progress}%</span>
    </div>
  );

  const totalPages = Math.ceil(total / pageSize);

  // 批量选择的展示任务列表（含分类过滤）
  const displayTasks = filteredTasks;

  // ── 关闭时重置 ───────────────────────────────────────────

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      {/* 遮罩层 */}
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />

      {/* 主面板 */}
      <div className={`relative ${styles.cardBg} rounded-2xl shadow-2xl w-full max-w-5xl max-h-[85vh] flex flex-col overflow-hidden animate-fade-in-down`}>
        {/* ── Header ─────────────────────────────── */}
        <div className={`flex items-center justify-between px-6 py-4 border-b ${styles.cardBorder} shrink-0`}>
          <div>
            <h2 className={`text-lg font-bold ${styles.cardText}`}>{t("taskPanel.title")}</h2>
            <p className={`text-xs ${styles.cardTextMuted}`}>{t("taskPanel.subtitle")}</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={refresh}
              disabled={loading}
              className={`p-2 rounded-lg ${styles.sidebarHoverBg} transition disabled:opacity-50`}
              title={t("taskPanel.refresh")}
            >
              <RefreshCw className={`w-4 h-4 ${styles.cardTextMuted} ${loading ? "animate-spin" : ""}`} />
            </button>
            <button onClick={onClose} className={`p-2 rounded-lg ${styles.sidebarHoverBg} transition ${styles.cardTextMuted}`}>
              <X className={`w-5 h-5 ${styles.cardTextMuted}`} />
            </button>
          </div>
        </div>

        {/* ── Stats Cards — 分类统计 ─────────────── */}
        <div className="grid grid-cols-4 gap-3 px-6 pt-4 shrink-0">
          {CATEGORIES.map(cat => (
            <div key={cat.key} className={`rounded-lg border-l-4 px-4 py-3 ${cat.color} ${cat.bg} transition`}>
              <div className={`text-xs ${styles.cardTextMuted} mb-1`}>
                {t(cat.labelKey)}
              </div>
              <div className={`text-2xl font-bold ${cat.textColor}`}>
                {categoryCounts[cat.key]}
              </div>
            </div>
          ))}
        </div>

        {/* ── Stats Cards — 状态统计 ─────────────── */}
        <div className="grid grid-cols-4 gap-3 px-6 py-3 shrink-0">
          {([
            { key: "taskPanel.status.running", value: stats.running, color: "border-l-blue-500 bg-blue-50/60 dark:bg-blue-900/20", textColor: "text-blue-600 dark:text-blue-400" },
            { key: "taskPanel.status.pending", value: stats.pending, color: "border-l-gray-400 bg-gray-50/60 dark:bg-gray-800/40", textColor: "text-gray-600 dark:text-gray-400" },
            { key: "taskPanel.status.succeeded", value: stats.succeeded, color: "border-l-green-500 bg-green-50/60 dark:bg-green-900/20", textColor: "text-green-600 dark:text-green-400" },
            { key: "taskPanel.status.failed", value: stats.failed, color: "border-l-red-500 bg-red-50/60 dark:bg-red-900/20", textColor: "text-red-600 dark:text-red-400" },
          ] as const).map(({ key, value, color, textColor }) => (
            <div key={key} className={`rounded-lg border-l-4 px-4 py-3 ${color}`}>
              <div className={`text-xs ${styles.cardTextMuted} mb-1`}>{t(key)}</div>
              <div className={`text-2xl font-bold ${textColor}`}>{value}</div>
            </div>
          ))}
        </div>

        {/* ── Filter Bar ─────────────────────────── */}
        <div className={`flex flex-col gap-2 px-6 py-3 border-b ${styles.appBorder} shrink-0`}>
          {/* 分类 Tab 按钮 */}
          <div className="flex items-center gap-1.5">
            <span className={`text-xs ${styles.cardTextMuted} mr-1`}>{t("taskPanel.filter.category")}</span>
            <button
              onClick={() => setFilterCategory("")}
              className={`px-2.5 py-1 text-xs rounded-full transition ${
                filterCategory === ""
                  ? `bg-[var(--card)] text-white font-medium`
                  : `${styles.appBg} ${styles.cardTextMuted} hover:${styles.sidebarHoverBg}`
              }`}
            >
              {t("taskPanel.filter.all")}
            </button>
            {CATEGORIES.map(cat => (
              <button
                key={cat.key}
                onClick={() => setFilterCategory(cat.key)}
                className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs rounded-full transition ${
                  filterCategory === cat.key
                    ? `${cat.badgeBg} ${cat.badgeText} font-medium ring-1 ring-current/30`
                    : `${styles.appBg} ${styles.cardTextMuted} hover:${styles.sidebarHoverBg}`
                }`}
              >
                <cat.Icon size={12} />
                {t(cat.labelKey)}
              </button>
            ))}
          </div>

          {/* 状态 + 类型筛选 */}
          <div className="flex items-center gap-3">
            <select
              value={filterStatus}
              onChange={e => setFilterStatus(e.target.value)}
              className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30`}
            >
              <option value="">{t("taskPanel.filter.allStatus")}</option>
              <option value="PENDING">{t("taskPanel.status.pending")}</option>
              <option value="RUNNING">{t("taskPanel.status.running")}</option>
              <option value="SUCCEEDED">{t("taskPanel.status.succeeded")}</option>
              <option value="FAILED">{t("taskPanel.status.failed")}</option>
              <option value="CANCELLED">{t("taskPanel.status.cancelled")}</option>
            </select>
            <input
              type="text"
              value={filterType}
              onChange={e => setFilterType(e.target.value)}
              onKeyDown={e => { if (e.key === "Enter") setSearchType(filterType.trim()); }}
              placeholder={t("taskPanel.filter.typePlaceholder")}
              className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30 w-40`}
            />
            {taskTypes.length > 0 && (
              <select
                value={filterType}
                onChange={e => { setFilterType(e.target.value); setSearchType(e.target.value); }}
                className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30`}
              >
                <option value="">{t("taskPanel.filter.selectType")}</option>
                {taskTypes.map(t => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            )}
            <button
              onClick={() => setSearchType(filterType.trim())}
              className="flex items-center gap-1.5 px-4 py-1.5 text-sm rounded-lg transition"
              style={{ backgroundColor: "var(--accent)", color: "#fff" }}
            >
              <Search className="w-3.5 h-3.5" />
              {t("taskPanel.filter.searchBtn")}
            </button>
          </div>
        </div>

        {/* ── Batch Action Bar ───────────────────── */}
        {selectedIds.size > 0 && (
          <div className="flex items-center gap-2 px-6 py-2 bg-blue-50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800/30 shrink-0">
            <span className="text-sm font-medium" style={{ color: "var(--accent)" }}>
              {t("taskPanel.batch.selected", { n: selectedIds.size })}
            </span>
            <div className="flex-1" />
            <button onClick={() => doBatchAction("resume")} disabled={batchActioning}
              className="px-3 py-1 text-xs rounded transition disabled:opacity-50" style={{ backgroundColor: "var(--accent)", color: "#fff" }}>{t("taskPanel.batch.resume")}</button>
            <button onClick={() => doBatchAction("pause")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-amber-500 hover:bg-amber-600 text-white rounded transition disabled:opacity-50">{t("taskPanel.batch.pause")}</button>
            <button onClick={() => doBatchAction("cancel")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-red-500 hover:bg-red-600 text-white rounded transition disabled:opacity-50">{t("taskPanel.batch.cancel")}</button>
            <button onClick={() => doBatchAction("archive")} disabled={batchActioning}
              className={`px-3 py-1 text-xs ${styles.cardBg} hover:${styles.appBg} ${styles.cardText} rounded transition disabled:opacity-50`}>{t("taskPanel.batch.archive")}</button>
            <button onClick={() => setSelectedIds(new Set())}
              className={`px-3 py-1 text-xs ${styles.cardTextMuted} transition`}>{t("taskPanel.batch.deselect")}</button>
          </div>
        )}

        {/* ── Task Table ─────────────────────────── */}
        <div className="flex-1 overflow-auto">
          <table className="w-full text-sm">
            <thead className={`sticky top-0 ${styles.appBg} z-10`}>
              <tr className={`border-b ${styles.cardBorder} text-left text-xs ${styles.cardTextMuted} uppercase tracking-wider`}>
                <th className="px-4 py-2.5 w-10">
                  <input type="checkbox" checked={displayTasks.length > 0 && selectedIds.size === displayTasks.length} onChange={toggleAll} />
                </th>
                <th className="px-2 py-2.5">{t("taskPanel.col.name")}</th>
                <th className="px-2 py-2.5">{t("taskPanel.col.category")}</th>
                <th className="px-2 py-2.5">{t("taskPanel.col.type")}</th>
                <th className="px-2 py-2.5">{t("taskPanel.col.status")}</th>
                <th className="px-2 py-2.5 w-36">{t("taskPanel.col.progress")}</th>
                <th className="px-2 py-2.5">{t("taskPanel.col.created")}</th>
                <th className="px-2 py-2.5">{t("taskPanel.col.actions")}</th>
              </tr>
            </thead>
            <tbody>
              {displayTasks.length === 0 && !loading && (
                <tr>
                  <td colSpan={8} className={`text-center py-16 ${styles.cardTextMuted}`}>
                    <div className="text-4xl mb-2" aria-hidden>📋</div>
                    <p>{t("taskPanel.empty")}</p>
                  </td>
                </tr>
              )}
              {displayTasks.map(task => {
                const st = statusMap[task.taskId];
                const status = st?.status || "PENDING";
                const progress = st?.progress ?? 0;
                const isSelected = selectedIds.has(task.taskId);
                const isActing = actingIds.has(task.taskId);
                return (
                  <tr
                    key={task.taskId}
                    className={`border-b ${styles.appBorder} hover:${styles.sidebarHoverBg} transition cursor-pointer ${isSelected ? "bg-blue-50/50 dark:bg-blue-900/10" : ""}`}
                    onClick={() => openDetail(task.taskId)}
                  >
                    <td className="px-4 py-2.5" onClick={e => e.stopPropagation()}>
                      <input type="checkbox" checked={isSelected} onChange={() => toggleOne(task.taskId)} />
                    </td>
                    <td className={`px-2 py-2.5 font-medium ${styles.cardText} max-w-[140px] truncate`} title={task.taskName}>
                      {task.taskName}
                    </td>
                    <td className="px-2 py-2.5">
                      {renderCategoryBadge(task.taskType)}
                    </td>
                    <td className="px-2 py-2.5">
                      <span className={`text-xs px-1.5 py-0.5 rounded ${styles.appBg} ${styles.cardTextMuted}`}>
                        {task.taskType || "—"}
                      </span>
                    </td>
                    <td className="px-2 py-2.5">{renderStatusBadge(status)}</td>
                    <td className="px-2 py-2.5">{renderProgress(progress)}</td>
                    <td className={`px-2 py-2.5 text-xs ${styles.cardTextMuted}`}>
                      {formatTime(task.createTime)}
                    </td>
                    <td className="px-2 py-2.5" onClick={e => e.stopPropagation()}>
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => doAction(task.taskId, "execute")}
                          disabled={isActing || status === "RUNNING"}
                          className="p-1 rounded hover:bg-green-100 dark:hover:bg-green-900/30 text-green-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title={t("taskPanel.action.start")}
                        ><Play className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "pause")}
                          disabled={isActing || status !== "RUNNING"}
                          className="p-1 rounded hover:bg-amber-100 dark:hover:bg-amber-900/30 text-amber-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title={t("taskPanel.action.pause")}
                        ><Pause className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "resume")}
                          disabled={isActing || status === "RUNNING"}
                          className="p-1 rounded hover:bg-blue-100 dark:hover:bg-blue-900/30 text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title={t("taskPanel.action.resume")}
                        ><RotateCcw className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "cancel")}
                          disabled={isActing || status === "SUCCEEDED" || status === "CANCELLED"}
                          className="p-1 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title={t("taskPanel.action.cancel")}
                        ><Square className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "archive")}
                          disabled={isActing}
                          className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title={t("taskPanel.action.archive")}
                        ><Archive className="w-3.5 h-3.5" /></button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* ── Pagination ─────────────────────────── */}
        {totalPages > 1 && (
          <div className={`flex items-center justify-between px-6 py-3 border-t ${styles.appBorder} shrink-0 text-sm`}>
            <span className={`${styles.cardTextMuted}`}>{t("taskPanel.pagination.total", { n: total })}</span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page <= 1}
                className={`px-3 py-1 rounded border ${styles.cardBorder} ${styles.sidebarHoverBg} disabled:opacity-40 transition`}
              >{t("taskPanel.pagination.prev")}</button>
              <span className={`px-2 ${styles.cardText}`}>{page} / {totalPages}</span>
              <button
                onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                disabled={page >= totalPages}
                className={`px-3 py-1 rounded border ${styles.cardBorder} ${styles.sidebarHoverBg} disabled:opacity-40 transition`}
              >{t("taskPanel.pagination.next")}</button>
            </div>
          </div>
        )}
      </div>

      {/* ── 详情抽屉遮罩 ────────────────────────── */}
      {detailTask && (
        <div className="absolute inset-0 bg-transparent" onClick={() => setDetailTask(null)} />
      )}

      {/* ── 详情抽屉 ────────────────────────────── */}
      <div
        className={`absolute top-0 right-0 h-full w-[360px] ${styles.cardBg} shadow-2xl border-l ${styles.cardBorder} overflow-auto transition-transform duration-300 ${
          detailTask ? "translate-x-0" : "translate-x-full"
        }`}
      >
        {detailLoading ? (
          <div className="flex items-center justify-center h-full">
            <RefreshCw className="w-6 h-6 animate-spin" style={{ color: "var(--accent)" }} />
          </div>
        ) : detailTask ? (
          <div className="p-5 space-y-5">
            {/* Header */}
            <div className="flex items-center justify-between">
              <h3 className={`text-base font-bold ${styles.cardText}`}>{t("taskPanel.detail.title")}</h3>
              <button onClick={() => setDetailTask(null)} className={`p-1.5 rounded ${styles.sidebarHoverBg} transition`}>
                <X className={`w-4 h-4 ${styles.cardTextMuted}`} />
              </button>
            </div>

            {/* 执行状态 */}
            <section>
              <h4 className={`text-xs font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-3`}>{t("taskPanel.detail.execution")}</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.status")}</span>
                  {renderStatusBadge(detailTask.status.status)}
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.progress")}</span>
                  <span className="w-48">{renderProgress(detailTask.status.progress)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.startedAt")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{formatTime(detailTask.status.startedAt)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.completedAt")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{formatTime(detailTask.status.completedAt)}</span>
                </div>
              </div>
            </section>

            {/* 基本信息 */}
            <section>
              <h4 className={`text-xs font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-3`}>{t("taskPanel.detail.basicInfo")}</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.taskId")}</span>
                  <span className={`text-sm font-mono ${styles.cardText} text-xs`}>{detailTask.task.taskId}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.taskName")}</span>
                  <span className={`text-sm ${styles.cardText} font-medium`}>{detailTask.task.taskName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.taskCategory")}</span>
                  <span>{renderCategoryBadge(detailTask.task.taskType)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.taskType")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{detailTask.task.taskType || "—"}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.priority")}</span>
                  <span className={`text-sm ${styles.cardText}`}>
                    {t(PRIORITY_LABEL_KEYS[detailTask.task.priority] || "taskPanel.priority.medium")}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.createdBy")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{detailTask.task.createdBy || "—"}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.createdAt")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{formatTime(detailTask.task.createTime)}</span>
                </div>
                {detailTask.task.description && (
                  <div>
                    <span className={`text-sm ${styles.cardTextMuted} block mb-1`}>{t("taskPanel.detail.description")}</span>
                    <p className={`text-sm ${styles.cardText} ${styles.appBg} rounded p-2`}>
                      {detailTask.task.description}
                    </p>
                  </div>
                )}
              </div>
            </section>

            {/* 参数信息 */}
            <section>
              <h4 className={`text-xs font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-3`}>{t("taskPanel.detail.params")}</h4>
              {detailTask.task.parameters && Object.keys(detailTask.task.parameters).length > 0 ? (
                <pre className={`text-xs font-mono ${styles.appBg} rounded-lg p-3 overflow-auto max-h-48 ${styles.cardText}`}>
                  {JSON.stringify(detailTask.task.parameters, null, 2)}
                </pre>
              ) : (
                <p className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.noParams")}</p>
              )}
            </section>
          </div>
        ) : null}
      </div>

      {/* ── 批量操作确认框 ────────────────────────── */}
      {batchConfirm && (
        <ConfirmDialog
          visible
          variant={batchConfirm === "cancel" || batchConfirm === "archive" ? "danger" : "warning"}
          title={t(`taskPanel.batch.confirm.${batchConfirm}`)}
          message={t("taskPanel.batch.confirmMessage", { n: selectedIds.size })}
          onConfirm={confirmBatchAction}
          onCancel={() => setBatchConfirm(null)}
        />
      )}
    </div>
  );
}
