/**
 * TaskPanel — 任务引擎管理面板
 * 统计卡片 + 筛选栏 + 任务列表 + 批量操作 + 详情抽屉
 * 支持四类分组: 管道 / Agent / 实时 / 管理
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { X, RefreshCw, Search, Play, Pause, RotateCcw, Square, Archive } from "lucide-react";

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
  label: string;
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
  /** 图标 */
  icon: string;
}

const CATEGORIES: CategoryConfig[] = [
  {
    key: "pipeline",
    label: "管道",
    types: ["DORIS_SQL", "ETL", "DATA_SYNC", "PIPELINE"],
    color: "border-l-blue-500",
    bg: "bg-blue-50/60 dark:bg-blue-900/20",
    textColor: "text-blue-600 dark:text-blue-400",
    badgeBg: "bg-blue-100 dark:bg-blue-900/40",
    badgeText: "text-blue-700 dark:text-blue-300",
    icon: "🔗",
  },
  {
    key: "agent",
    label: "Agent",
    types: ["AGENT", "AI_AGENT", "LLM_TASK", "KG_SYNC"],
    color: "border-l-purple-500",
    bg: "bg-purple-50/60 dark:bg-purple-900/20",
    textColor: "text-purple-600 dark:text-purple-400",
    badgeBg: "bg-purple-100 dark:bg-purple-900/40",
    badgeText: "text-purple-700 dark:text-purple-300",
    icon: "🤖",
  },
  {
    key: "realtime",
    label: "实时",
    types: ["REALTIME", "STREAMING", "MONITOR", "ALERT", "TELEMETRY"],
    color: "border-l-green-500",
    bg: "bg-green-50/60 dark:bg-green-900/20",
    textColor: "text-green-600 dark:text-green-400",
    badgeBg: "bg-green-100 dark:bg-green-900/40",
    badgeText: "text-green-700 dark:text-green-300",
    icon: "⚡",
  },
  {
    key: "management",
    label: "管理",
    types: ["DATA_QUALITY", "REPORT", "MAINTENANCE", "BACKUP", "CONFIG", "ADMIN"],
    color: "border-l-gray-400",
    bg: "bg-gray-50/60 dark:bg-gray-800/40",
    textColor: "text-gray-600 dark:text-gray-400",
    badgeBg: "bg-gray-100 dark:bg-gray-700",
    badgeText: "text-gray-700 dark:text-gray-300",
    icon: "⚙️",
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

const STATUS_LABELS: Record<TaskStatus, string> = {
  PENDING: "等待中", RUNNING: "运行中", SUCCEEDED: "已完成", FAILED: "失败", CANCELLED: "已取消",
};

const PRIORITY_LABELS: Record<string, string> = {
  HIGH: "高", MEDIUM: "中", LOW: "低", CRITICAL: "紧急",
};

// ── Props ──────────────────────────────────────────────────

interface TaskPanelProps {
  open: boolean;
  onClose: () => void;
}

// ── 组件 ──────────────────────────────────────────────────

export default function TaskPanel({ open, onClose }: TaskPanelProps) {
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
      if (d.code !== 0) alert(`操作失败: ${d.message || "未知错误"}`);
    } catch (e: any) {
      alert(`操作异常: ${e.message}`);
    } finally {
      setActingIds(prev => { const s = new Set(prev); s.delete(taskId); return s; });
      refresh();
    }
  };

  const doBatchAction = async (action: "cancel" | "pause" | "resume" | "archive") => {
    if (selectedIds.size === 0) return;
    const actionLabels: Record<string, string> = { cancel: "中止", pause: "暂停", resume: "恢复", archive: "归档" };
    if (!window.confirm(`确定要批量${actionLabels[action]} ${selectedIds.size} 个任务吗？`)) return;
    setBatchActioning(true);
    try {
      const r = await fetch("/api/v1/task/batch", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ taskIds: [...selectedIds], action }),
      });
      const d = await r.json();
      if (d.code !== 0) alert(`批量操作失败: ${d.message || "未知错误"}`);
      else {
        setSelectedIds(new Set());
        refresh();
      }
    } catch (e: any) {
      alert(`批量操作异常: ${e.message}`);
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

  const formatTime = (ts: string | null) => {
    if (!ts) return "—";
    try { return new Date(ts).toLocaleString("zh-CN"); } catch { return ts; }
  };

  const renderStatusBadge = (status: TaskStatus) => {
    const c = STATUS_COLORS[status] || STATUS_COLORS.PENDING;
    return (
      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${c.bg} ${c.text}`}>
        <span className={`w-1.5 h-1.5 rounded-full ${c.dot}`} />
        {STATUS_LABELS[status] || status}
      </span>
    );
  };

  const renderCategoryBadge = (taskType: string) => {
    const cat = getCategory(taskType);
    if (!cat) {
      return (
        <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400">
          —
        </span>
      );
    }
    return (
      <span className={`inline-flex items-center gap-1 text-xs px-1.5 py-0.5 rounded font-medium ${cat.badgeBg} ${cat.badgeText}`}>
        <span>{cat.icon}</span>
        {cat.label}
      </span>
    );
  };

  const renderProgress = (progress: number) => (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500 rounded-full transition-all duration-500"
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
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
      <div className="relative bg-white dark:bg-slate-800 rounded-2xl shadow-2xl w-full max-w-5xl max-h-[85vh] flex flex-col overflow-hidden animate-fade-in-down">
        {/* ── Header ─────────────────────────────── */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 dark:border-slate-700 shrink-0">
          <div>
            <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">任务引擎</h2>
            <p className="text-xs text-slate-400 dark:text-slate-500">任务管理 · 调度监控 · 批量操作</p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={refresh}
              disabled={loading}
              className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition disabled:opacity-50"
              title="刷新"
            >
              <RefreshCw className={`w-4 h-4 text-slate-400 ${loading ? "animate-spin" : ""}`} />
            </button>
            <button onClick={onClose} className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition">
              <X className="w-5 h-5 text-slate-400" />
            </button>
          </div>
        </div>

        {/* ── Stats Cards — 分类统计 ─────────────── */}
        <div className="grid grid-cols-4 gap-3 px-6 pt-4 shrink-0">
          {CATEGORIES.map(cat => (
            <div key={cat.key} className={`rounded-lg border-l-4 px-4 py-3 ${cat.color} ${cat.bg} transition`}>
              <div className="text-xs text-slate-500 dark:text-slate-400 mb-1">
                {cat.icon} {cat.label}
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
            { label: "运行中", value: stats.running, color: "border-l-blue-500 bg-blue-50/60 dark:bg-blue-900/20", textColor: "text-blue-600 dark:text-blue-400" },
            { label: "等待中", value: stats.pending, color: "border-l-gray-400 bg-gray-50/60 dark:bg-gray-800/40", textColor: "text-gray-600 dark:text-gray-400" },
            { label: "已完成", value: stats.succeeded, color: "border-l-green-500 bg-green-50/60 dark:bg-green-900/20", textColor: "text-green-600 dark:text-green-400" },
            { label: "失败", value: stats.failed, color: "border-l-red-500 bg-red-50/60 dark:bg-red-900/20", textColor: "text-red-600 dark:text-red-400" },
          ] as const).map(({ label, value, color, textColor }) => (
            <div key={label} className={`rounded-lg border-l-4 px-4 py-3 ${color}`}>
              <div className="text-xs text-slate-500 dark:text-slate-400 mb-1">{label}</div>
              <div className={`text-2xl font-bold ${textColor}`}>{value}</div>
            </div>
          ))}
        </div>

        {/* ── Filter Bar ─────────────────────────── */}
        <div className="flex flex-col gap-2 px-6 py-3 border-b border-slate-100 dark:border-slate-700/50 shrink-0">
          {/* 分类 Tab 按钮 */}
          <div className="flex items-center gap-1.5">
            <span className="text-xs text-slate-400 dark:text-slate-500 mr-1">分类:</span>
            <button
              onClick={() => setFilterCategory("")}
              className={`px-2.5 py-1 text-xs rounded-full transition ${
                filterCategory === ""
                  ? "bg-slate-700 dark:bg-slate-200 text-white dark:text-slate-800 font-medium"
                  : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600"
              }`}
            >
              全部
            </button>
            {CATEGORIES.map(cat => (
              <button
                key={cat.key}
                onClick={() => setFilterCategory(cat.key)}
                className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs rounded-full transition ${
                  filterCategory === cat.key
                    ? `${cat.badgeBg} ${cat.badgeText} font-medium ring-1 ring-current/30`
                    : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600"
                }`}
              >
                <span>{cat.icon}</span>
                {cat.label}
              </button>
            ))}
          </div>

          {/* 状态 + 类型筛选 */}
          <div className="flex items-center gap-3">
            <select
              value={filterStatus}
              onChange={e => setFilterStatus(e.target.value)}
              className="px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-blue-500/30"
            >
              <option value="">全部状态</option>
              <option value="PENDING">等待中</option>
              <option value="RUNNING">运行中</option>
              <option value="SUCCEEDED">已完成</option>
              <option value="FAILED">失败</option>
              <option value="CANCELLED">已取消</option>
            </select>
            <input
              type="text"
              value={filterType}
              onChange={e => setFilterType(e.target.value)}
              onKeyDown={e => { if (e.key === "Enter") setSearchType(filterType.trim()); }}
              placeholder="任务类型..."
              className="px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-blue-500/30 w-40"
            />
            {taskTypes.length > 0 && (
              <select
                value={filterType}
                onChange={e => { setFilterType(e.target.value); setSearchType(e.target.value); }}
                className="px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-600 rounded-lg bg-white dark:bg-slate-700 text-slate-700 dark:text-slate-200 outline-none focus:ring-2 focus:ring-blue-500/30"
              >
                <option value="">选择类型…</option>
                {taskTypes.map(t => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            )}
            <button
              onClick={() => setSearchType(filterType.trim())}
              className="flex items-center gap-1.5 px-4 py-1.5 text-sm bg-blue-500 hover:bg-blue-600 text-white rounded-lg transition"
            >
              <Search className="w-3.5 h-3.5" />
              搜索
            </button>
          </div>
        </div>

        {/* ── Batch Action Bar ───────────────────── */}
        {selectedIds.size > 0 && (
          <div className="flex items-center gap-2 px-6 py-2 bg-blue-50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800/30 shrink-0">
            <span className="text-sm text-blue-700 dark:text-blue-300 font-medium">
              已选 {selectedIds.size} 项
            </span>
            <div className="flex-1" />
            <button onClick={() => doBatchAction("resume")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-blue-500 hover:bg-blue-600 text-white rounded transition disabled:opacity-50">▶ 批量恢复</button>
            <button onClick={() => doBatchAction("pause")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-amber-500 hover:bg-amber-600 text-white rounded transition disabled:opacity-50">⏸ 批量暂停</button>
            <button onClick={() => doBatchAction("cancel")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-red-500 hover:bg-red-600 text-white rounded transition disabled:opacity-50">⏹ 批量中止</button>
            <button onClick={() => doBatchAction("archive")} disabled={batchActioning}
              className="px-3 py-1 text-xs bg-gray-500 hover:bg-gray-600 text-white rounded transition disabled:opacity-50">📦 批量归档</button>
            <button onClick={() => setSelectedIds(new Set())}
              className="px-3 py-1 text-xs text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 transition">取消选择</button>
          </div>
        )}

        {/* ── Task Table ─────────────────────────── */}
        <div className="flex-1 overflow-auto">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-slate-50 dark:bg-slate-800/80 z-10">
              <tr className="border-b border-slate-200 dark:border-slate-700 text-left text-xs text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                <th className="px-4 py-2.5 w-10">
                  <input type="checkbox" checked={displayTasks.length > 0 && selectedIds.size === displayTasks.length} onChange={toggleAll} />
                </th>
                <th className="px-2 py-2.5">任务名称</th>
                <th className="px-2 py-2.5">分类</th>
                <th className="px-2 py-2.5">类型</th>
                <th className="px-2 py-2.5">状态</th>
                <th className="px-2 py-2.5 w-36">进度</th>
                <th className="px-2 py-2.5">创建时间</th>
                <th className="px-2 py-2.5">操作</th>
              </tr>
            </thead>
            <tbody>
              {displayTasks.length === 0 && !loading && (
                <tr>
                  <td colSpan={8} className="text-center py-16 text-slate-400 dark:text-slate-500">
                    <div className="text-4xl mb-2">📋</div>
                    <p>暂无任务</p>
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
                    className={`border-b border-slate-100 dark:border-slate-700/50 hover:bg-slate-50 dark:hover:bg-slate-800/50 transition cursor-pointer ${isSelected ? "bg-blue-50/50 dark:bg-blue-900/10" : ""}`}
                    onClick={() => openDetail(task.taskId)}
                  >
                    <td className="px-4 py-2.5" onClick={e => e.stopPropagation()}>
                      <input type="checkbox" checked={isSelected} onChange={() => toggleOne(task.taskId)} />
                    </td>
                    <td className="px-2 py-2.5 font-medium text-slate-800 dark:text-slate-200 max-w-[140px] truncate" title={task.taskName}>
                      {task.taskName}
                    </td>
                    <td className="px-2 py-2.5">
                      {renderCategoryBadge(task.taskType)}
                    </td>
                    <td className="px-2 py-2.5">
                      <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300">
                        {task.taskType || "—"}
                      </span>
                    </td>
                    <td className="px-2 py-2.5">{renderStatusBadge(status)}</td>
                    <td className="px-2 py-2.5">{renderProgress(progress)}</td>
                    <td className="px-2 py-2.5 text-xs text-slate-500 dark:text-slate-400">
                      {formatTime(task.createTime)}
                    </td>
                    <td className="px-2 py-2.5" onClick={e => e.stopPropagation()}>
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => doAction(task.taskId, "execute")}
                          disabled={isActing || status === "RUNNING"}
                          className="p-1 rounded hover:bg-green-100 dark:hover:bg-green-900/30 text-green-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title="启动"
                        ><Play className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "pause")}
                          disabled={isActing || status !== "RUNNING"}
                          className="p-1 rounded hover:bg-amber-100 dark:hover:bg-amber-900/30 text-amber-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title="暂停"
                        ><Pause className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "resume")}
                          disabled={isActing || status === "RUNNING"}
                          className="p-1 rounded hover:bg-blue-100 dark:hover:bg-blue-900/30 text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title="恢复"
                        ><RotateCcw className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "cancel")}
                          disabled={isActing || status === "SUCCEEDED" || status === "CANCELLED"}
                          className="p-1 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title="中止"
                        ><Square className="w-3.5 h-3.5" /></button>
                        <button
                          onClick={() => doAction(task.taskId, "archive")}
                          disabled={isActing}
                          className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 disabled:opacity-30 disabled:cursor-not-allowed transition"
                          title="归档"
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
          <div className="flex items-center justify-between px-6 py-3 border-t border-slate-100 dark:border-slate-700/50 shrink-0 text-sm">
            <span className="text-slate-500 dark:text-slate-400">共 {total} 条</span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page <= 1}
                className="px-3 py-1 rounded border border-slate-200 dark:border-slate-600 hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40 transition"
              >上一页</button>
              <span className="px-2 text-slate-600 dark:text-slate-300">{page} / {totalPages}</span>
              <button
                onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                disabled={page >= totalPages}
                className="px-3 py-1 rounded border border-slate-200 dark:border-slate-600 hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-40 transition"
              >下一页</button>
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
        className={`absolute top-0 right-0 h-full w-[360px] bg-white dark:bg-slate-800 shadow-2xl border-l border-slate-200 dark:border-slate-700 overflow-auto transition-transform duration-300 ${
          detailTask ? "translate-x-0" : "translate-x-full"
        }`}
      >
        {detailLoading ? (
          <div className="flex items-center justify-center h-full">
            <RefreshCw className="w-6 h-6 text-blue-500 animate-spin" />
          </div>
        ) : detailTask ? (
          <div className="p-5 space-y-5">
            {/* Header */}
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-800 dark:text-slate-100">任务详情</h3>
              <button onClick={() => setDetailTask(null)} className="p-1.5 rounded hover:bg-slate-100 dark:hover:bg-slate-700 transition">
                <X className="w-4 h-4 text-slate-400" />
              </button>
            </div>

            {/* 执行状态 */}
            <section>
              <h4 className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-3">执行状态</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">状态</span>
                  {renderStatusBadge(detailTask.status.status)}
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">进度</span>
                  <span className="w-48">{renderProgress(detailTask.status.progress)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">启动时间</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">{formatTime(detailTask.status.startedAt)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">完成时间</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">{formatTime(detailTask.status.completedAt)}</span>
                </div>
              </div>
            </section>

            {/* 基本信息 */}
            <section>
              <h4 className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-3">基本信息</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">任务ID</span>
                  <span className="text-sm font-mono text-slate-700 dark:text-slate-300 text-xs">{detailTask.task.taskId}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">任务名称</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300 font-medium">{detailTask.task.taskName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">任务分类</span>
                  <span>{renderCategoryBadge(detailTask.task.taskType)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">任务类型</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">{detailTask.task.taskType || "—"}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">优先级</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">
                    {PRIORITY_LABELS[detailTask.task.priority] || detailTask.task.priority || "—"}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">创建人</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">{detailTask.task.createdBy || "—"}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-slate-500 dark:text-slate-400">创建时间</span>
                  <span className="text-sm text-slate-700 dark:text-slate-300">{formatTime(detailTask.task.createTime)}</span>
                </div>
                {detailTask.task.description && (
                  <div>
                    <span className="text-sm text-slate-500 dark:text-slate-400 block mb-1">描述</span>
                    <p className="text-sm text-slate-700 dark:text-slate-300 bg-slate-50 dark:bg-slate-900/50 rounded p-2">
                      {detailTask.task.description}
                    </p>
                  </div>
                )}
              </div>
            </section>

            {/* 参数信息 */}
            <section>
              <h4 className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-3">参数信息</h4>
              {detailTask.task.parameters && Object.keys(detailTask.task.parameters).length > 0 ? (
                <pre className="text-xs font-mono bg-slate-50 dark:bg-slate-900/50 rounded-lg p-3 overflow-auto max-h-48 text-slate-700 dark:text-slate-300">
                  {JSON.stringify(detailTask.task.parameters, null, 2)}
                </pre>
              ) : (
                <p className="text-sm text-slate-400 dark:text-slate-500">无参数</p>
              )}
            </section>
          </div>
        ) : null}
      </div>
    </div>
  );
}
