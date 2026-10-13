/* 异步任务中心 — 对接 ITaskManagementService 真实后端
 * 改造自 mock 实现 (2026-09-07)
 * - 任务列表 / 状态实时 / 操作（执行/取消/暂停/恢复/归档）
 * - 轮询策略（前端检视报告 §4.1 订正）：后端无批量状态端点，
 *   仅对「非终态行」逐条轮询状态（5s），全量列表+统计降频为 30s 一次，
 *   首屏拉全量状态一次。
 * - 主题走 useTheme tokens；破坏性操作（取消/归档/批量）过 ConfirmDialog 确认门
 * - PMO-72 W4: 数据源 = td_runtime_task* 5 表 (PG sys_man.public) + runtime-task JdbcTaskPersistenceService 持久化 + PG 重启恢复
 */
import React, { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { RotateCw, Play, Pause, Square, Archive, X, AlertTriangle } from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import { useMediaQuery } from "../hooks/useMediaQuery";
import ConfirmDialog from "../components/common/ConfirmDialog";
import { logger } from "../services/logger";
import {
  fetchTaskList, fetchTaskStatus, fetchTaskStats,
  executeTask, cancelTask, pauseTask, resumeTask, archiveTask, batchTask,
  TASK_CATEGORIES, categorize, type TaskCategoryKey,
} from "../services/taskCenter";
import type { TaskSummary, TaskStatusInfo, TaskStats, TaskFull } from "../services/taskCenter";

interface AsyncTaskCenterViewProps {
  showToast?: (type: "success" | "info" | "error", message: string) => void;
  onViewModeChange?: (mode: unknown) => void;
}

type FilterStatus = "ALL" | "RUNNING" | "PENDING" | "SUCCEEDED" | "FAILED" | "CANCELLED" | "PAUSED";
type RowAction = "execute" | "cancel" | "pause" | "resume" | "archive";
type BatchAction = "cancel" | "pause" | "resume" | "archive";

const STATUS_FILTERS: FilterStatus[] = ["ALL", "PENDING", "RUNNING", "PAUSED", "SUCCEEDED", "FAILED", "CANCELLED"];

/** 非终态行状态轮询间隔（无批量端点，逐条、只轮非终态行） */
const POLL_STATUS_MS = 5000;
/** 全量列表 + 统计刷新间隔（降频，避免 N+1 风暴） */
const FULL_REFRESH_MS = 30000;

const TERMINAL_STATUSES = new Set(["SUCCEEDED", "FAILED", "CANCELLED", "TIMEOUT"]);

interface RowData extends TaskFull {
  _status: TaskStatusInfo | null;
}

/** 待确认操作：单行破坏性操作或批量操作 */
type PendingOp =
  | { mode: "single"; taskId: string; action: RowAction }
  | { mode: "batch"; action: BatchAction }
  | null;

const ACTION_I18N: Record<RowAction, string> = {
  execute: "taskPanel.action.execute",
  cancel: "taskPanel.action.cancel",
  pause: "taskPanel.action.pause",
  resume: "taskPanel.action.resume",
  archive: "taskPanel.action.archive",
};

export default function AsyncTaskCenterView({ showToast, onViewModeChange }: AsyncTaskCenterViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<RowData[]>([]);
  const [stats, setStats] = useState<TaskStats>({ total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date | null>(null);
  const [pendingOp, setPendingOp] = useState<PendingOp>(null);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);

  const rowsRef = useRef<RowData[]>([]);
  useEffect(() => { rowsRef.current = rows; }, [rows]);

  // ── 数据加载 ────────────────────────────────────────
  type FetchListT = Promise<import("../services/taskCenter").TaskListResult | null>;
  type FetchStatsT = Promise<TaskStats | null>;

  const buildRow = useCallback(async (task: TaskSummary): Promise<RowData> => {
    const si: TaskStatusInfo | null = await fetchTaskStatus(task.taskId).catch((): TaskStatusInfo | null => null);
    // 优先用 task.category；否则按 taskType 归类（TASK_CATEGORIES 动态，可加新类）
    const cat: string | null = task.category ?? categorize(task.taskType);
    return {
      ...task,
      category: cat as RowData["category"],
      _status: si,
      status: si?.status ?? "UNKNOWN",
      statusMessage: si?.statusMessage,
      progress: si?.progress ?? 0,
      startedAt: si?.startedAt,
      completedAt: si?.completedAt,
    };
  }, []);

  /** 全量加载：列表 + 每任务状态 + 统计。失败上屏（可重试），不再静默 catch。 */
  const loadOnce = useCallback(async (): Promise<void> => {
    try {
      const listPromise: FetchListT = fetchTaskList({ offset: 0, limit: 100 });
      const stPromise: FetchStatsT = fetchTaskStats().catch((): TaskStats | null => null);
      const [list, st] = await Promise.all([listPromise, stPromise]);
      if (!list) {
        setListError(t("dashboard.taskCenter.offlineBanner"));
        return;
      }
      setListError(null);
      if (Array.isArray(list.items)) {
        const withStatus = await Promise.all(list.items.map(buildRow));
        setRows(withStatus);
        setStats(st ?? { total: withStatus.length, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
      }
      setLastRefresh(new Date());
    } catch (e) {
      logger.warn("[taskCenter] refresh failed:", e);
      setListError(t("dashboard.taskCenter.offlineBanner"));
    } finally {
      setLoading(false);
    }
  }, [buildRow, t]);

  /**
   * 增量轮询：后端无批量状态端点（services/taskCenter.ts 仅逐条 GET /status），
   * 因此只对「非终态行」逐条轮询（通常 ≤ 个位数），终态行不再重复请求；
   * 全量列表/统计由 30s 的 loadOnce 负责。
   */
  const pollActiveStatus = useCallback(async () => {
    const activeRows = rowsRef.current.filter(r => !TERMINAL_STATUSES.has(String(r._status?.status ?? r.status)));
    if (activeRows.length === 0) return;
    const updates = await Promise.all(activeRows.map(async r => {
      const si: TaskStatusInfo | null = await fetchTaskStatus(r.taskId).catch((): TaskStatusInfo | null => null);
      return { id: r.taskId, si };
    }));
    setRows(prev => prev.map(row => {
      const u = updates.find(x => x.id === row.taskId);
      if (!u || !u.si) return row;
      return {
        ...row,
        _status: u.si,
        status: u.si.status ?? row.status,
        statusMessage: u.si.statusMessage ?? row.statusMessage,
        progress: u.si.progress ?? row.progress,
        startedAt: u.si.startedAt ?? row.startedAt,
        completedAt: u.si.completedAt ?? row.completedAt,
      };
    }));
  }, []);

  useEffect(() => {
    loadOnce();
    const fullTimer = setInterval(loadOnce, FULL_REFRESH_MS);
    const pollTimer = setInterval(pollActiveStatus, POLL_STATUS_MS);
    return () => { clearInterval(fullTimer); clearInterval(pollTimer); };
  }, [loadOnce, pollActiveStatus]);

  // ── 单/批量操作 ────────────────────────────────────
  const taskNameById = (id: string) => rows.find(r => r.taskId === id)?.taskName || id.slice(0, 8);

  const runOp = useCallback(async (taskId: string, action: RowAction) => {
    const map = { execute: executeTask, cancel: cancelTask, pause: pauseTask, resume: resumeTask, archive: archiveTask } as const;
    const label = t(ACTION_I18N[action]) || action;
    const ok = !!(await map[action](taskId));
    // 无假成功：仅真实成功报 success；失败报 actionFailed
    if (ok) {
      showToast?.("success", t("dashboard.taskCenter.opSuccess", { action: label }));
    } else {
      showToast?.("error", `${t("taskPanel.actionFailed")} (${taskNameById(taskId)} → ${label})`);
    }
    // 立即重拉
    setTimeout(loadOnce, 300);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadOnce, showToast, t, rows]);

  /** 破坏性操作（取消/归档）过确认门；其余直发。 */
  const op = useCallback((taskId: string, action: RowAction) => {
    if (action === "cancel" || action === "archive") {
      setPendingOp({ mode: "single", taskId, action });
      return;
    }
    runOp(taskId, action);
  }, [runOp]);

  const runBatch = useCallback(async (action: BatchAction) => {
    const actionLabel = t(ACTION_I18N[action]) || action;
    const ids = [...selectedIds];
    const res = await batchTask(ids, action);
    if (res) {
      showToast?.("success", `${t("taskPanel.batch.done")} (${actionLabel}, ${res.successCount}/${ids.length})`);
    } else {
      showToast?.("error", t("taskPanel.batch.failed"));
    }
    setSelectedIds(new Set());
    setTimeout(loadOnce, 300);
  }, [batchTask, selectedIds, showToast, loadOnce, t]);

  const confirmBatch = (action: BatchAction) => {
    // window.confirm 替换为统一确认门
    setPendingOp({ mode: "batch", action });
  };

  const pendingCopy = pendingOp
    ? pendingOp.mode === "single"
      ? {
          title: t("dashboard.taskCenter.confirm.title", { action: t(ACTION_I18N[pendingOp.action]) }),
          message: t("dashboard.taskCenter.confirm.message", {
            action: t(ACTION_I18N[pendingOp.action]),
            name: taskNameById(pendingOp.taskId),
          }),
        }
      : {
          title: t("dashboard.taskCenter.confirm.batchTitle", { action: t(ACTION_I18N[pendingOp.action]) }),
          message: t("taskPanel.batch.confirmMessage", { action: t(ACTION_I18N[pendingOp.action]), count: selectedIds.size }),
        }
    : { title: "", message: "" };

  const onConfirmPending = () => {
    const po = pendingOp;
    setPendingOp(null);
    if (!po) return;
    if (po.mode === "single") runOp(po.taskId, po.action);
    else runBatch(po.action);
  };

  // ── 过滤 / 排序 ───────────────────────────────────
  const filtered = useMemo(() => {
    let out = rows;
    if (statusFilter !== "ALL") out = out.filter(r => (r._status?.status ?? r.status) === statusFilter);
    if (categoryFilter !== "ALL") out = out.filter(r => r.category === categoryFilter);
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      out = out.filter(r =>
        r.taskId.toLowerCase().includes(q)
        || (r.taskName || "").toLowerCase().includes(q)
        || (r.taskType || "").toLowerCase().includes(q)
      );
    }
    // 按状态优先 + 创建时间倒序
    const order: Record<string, number> = { RUNNING: 0, PENDING: 1, PAUSED: 2, FAILED: 3, CANCELLED: 4, SUCCEEDED: 5, TIMEOUT: 6, PARSING: 0, PARSED: 1, UNKNOWN: 9 };
    return [...out].sort((a, b) => {
      const sa = order[(a._status?.status ?? a.status)] ?? 9;
      const sb = order[(b._status?.status ?? b.status)] ?? 9;
      if (sa !== sb) return sa - sb;
      return (b.createTime ?? "").localeCompare(a.createTime ?? "");
    });
  }, [rows, statusFilter, categoryFilter, search]);

  const activeRow = activeTaskId ? rows.find(r => r.taskId === activeTaskId) : null;
  const isMobile = useMediaQuery("(max-width: 767px)");
  const allFilteredSelected = filtered.length > 0 && filtered.every(r => selectedIds.has(r.taskId));

  const statusLabel = (s: string) => t(`taskPanel.status.${s.toLowerCase()}`, s);

  // ── 渲染 ──────────────────────────────────────────
  const statTiles: { key: keyof TaskStats; label: string; tone: string }[] = [
    { key: "total", label: t("taskPanel.stat.total") || "Total", tone: styles.cardText },
    { key: "running", label: t("taskPanel.status.running"), tone: styles.successText },
    { key: "pending", label: t("taskPanel.status.pending"), tone: styles.accentText },
    { key: "succeeded", label: t("taskPanel.status.succeeded"), tone: styles.successText },
    { key: "failed", label: t("taskPanel.status.failed"), tone: styles.dangerText },
    { key: "cancelled", label: t("taskPanel.status.cancelled"), tone: styles.cardTextMuted },
  ];

  const statusColor = (s?: string) => {
    if (!s) return styles.cardTextMuted;
    return s === "SUCCEEDED" || s === "PARSED" ? styles.successText
      : s === "FAILED" || s === "TIMEOUT" ? styles.dangerText
      : s === "RUNNING" || s === "PARSING" ? styles.accentText
      : s === "PAUSED" ? styles.warningText
      : s === "CANCELLED" ? styles.cardTextMuted
      : styles.cardText;
  };

  /** 进度条/状态点的着色：token 类 + currentColor（token 是 class，不能作 CSS 色值内联）。 */
  const progressToneClass = (st: string) =>
    st === "FAILED" || st === "CANCELLED" ? styles.dangerText
      : st === "SUCCEEDED" ? styles.successText
        : styles.accentText;

  // i18n 分类 label：优先使用 t('taskPanel.category.<key>')；未定义时回退到短英文
  const categoryLabel = (key?: string | null): string => {
    if (!key) return "-";
    const i18n = t(`taskPanel.category.${key}`);
    if (i18n && i18n !== `taskPanel.category.${key}`) return i18n;
    return key;  // 兜底显示原始 key
  };

  // 从 statusMessage 中抽取 "ok=x failed=y" 或 "ok x / failed y" 等常见格式 → 已处理/总数
  const parseProgressCounts = (msg?: string): { done?: number; total?: number } | null => {
    if (!msg) return null;
    // 形如 "采集表 3 / 12" 或 "processing 3 / 12"
    const m1 = msg.match(/(\d+)\s*\/\s*(\d+)/);
    if (m1) return { done: parseInt(m1[1], 10), total: parseInt(m1[2], 10) };
    // 形如 "ok=3 failed=1"
    const m2 = msg.match(/ok\s*=\s*(\d+)\s*failed\s*=\s*(\d+)/i);
    if (m2) return { done: parseInt(m2[1], 10) + parseInt(m2[2], 10), total: parseInt(m2[1], 10) + parseInt(m2[2], 10) };
    return null;
  };

  const renderRowActions = (r: RowData, st: string, disabled: boolean) => (
    <div className="flex gap-1 justify-end">
      {!disabled && st !== "RUNNING" && (
        <ActionBtn icon={Play} ariaLabel={t("taskPanel.action.execute")} title={t("taskPanel.action.execute")} tone="accent" onClick={() => op(r.taskId, "execute")} />
      )}
      {st === "RUNNING" && <ActionBtn icon={Pause} ariaLabel={t("taskPanel.action.pause")} title={t("taskPanel.action.pause")} tone="warn" onClick={() => op(r.taskId, "pause")} />}
      {st === "PAUSED" && <ActionBtn icon={Play} ariaLabel={t("taskPanel.action.resume")} title={t("taskPanel.action.resume")} tone="accent" onClick={() => op(r.taskId, "resume")} />}
      {(st === "RUNNING" || st === "PAUSED" || st === "PENDING" || st === "PARSING" || st === "PARSED") && (
        <ActionBtn icon={Square} ariaLabel={t("taskPanel.action.cancel")} title={t("taskPanel.action.cancel")} tone="danger" onClick={() => op(r.taskId, "cancel")} />
      )}
      <ActionBtn icon={Archive} ariaLabel={t("taskPanel.action.archive")} title={t("taskPanel.action.archive")} tone="muted" onClick={() => op(r.taskId, "archive")} />
    </div>
  );

  return (
    <div className={`h-full flex flex-col gap-3 p-4 font-sans ${styles.appBg}`}>
      {/* 顶栏: 标题 + 统计 */}
      <div className="flex items-center gap-4 flex-wrap">
        <div className="flex flex-col">
          <h2 className={`text-lg font-bold ${styles.cardText} tracking-tight`}>{t("sidebar.task_center") || "异步任务中心"}</h2>
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
            {lastRefresh ? `· ${t("taskPanel.lastUpdated") || "Refreshed at"} ${lastRefresh.toLocaleTimeString()} · ${FULL_REFRESH_MS / 1000}s` : (t("taskPanel.live") || "Live")}
          </span>
        </div>
        <div className="flex gap-2 flex-1 min-w-[400px]">
          {statTiles.map(tile => (
            <div key={tile.key} className={`px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.cardBg} flex-1 min-w-[88px]`}>
              <div className={`text-[10px] ${styles.cardTextMuted}`}>{tile.label}</div>
              <div className={`text-base font-bold ${tile.tone}`}>{stats[tile.key] ?? 0}</div>
            </div>
          ))}
        </div>
        <button type="button"
          onClick={() => { setLoading(true); loadOnce().finally(() => setLoading(false)); }}
          className={`px-3 py-1.5 rounded-md border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText} hover:opacity-80 transition-colors cursor-pointer text-xs flex items-center gap-1.5`}
        >
          <RotateCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("taskPanel.refresh") || "刷新"}
        </button>
      </div>

      {/* 加载失败横幅：真实错误态 + 重试 */}
      {listError && (
        <div className={`flex items-center gap-2 border rounded-lg px-3 py-2 text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{listError}</span>
          <button
            type="button"
            onClick={() => { setLoading(true); loadOnce().finally(() => setLoading(false)); }}
            className={`ml-auto shrink-0 px-2 py-1 rounded border text-[10px] font-bold cursor-pointer ${styles.cardBorder} ${styles.cardBg}`}
          >
            {t("dashboard.taskCenter.retry")}
          </button>
        </div>
      )}

      {/* 控制栏: 搜索 + 筛选 */}
      <div className="flex gap-2 items-center flex-wrap">
        <input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder={t("taskPanel.search") || "搜索任务 ID / 名称 / 类型..."}
          className={`flex-1 min-w-[220px] text-xs p-2 rounded border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}
        />
        <select
          value={categoryFilter}
          onChange={e => setCategoryFilter(e.target.value)}
          className={`text-xs p-2 rounded border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}
        >
          <option value="ALL">{t("taskPanel.filterAll") || "全部分类"}</option>
          {TASK_CATEGORIES.map(c => (
            <option key={c.key} value={c.key}>{categoryLabel(c.key)}</option>
          ))}
        </select>
        <div className="flex gap-1 flex-wrap">
          {STATUS_FILTERS.map(f => (
            <button type="button"
              key={f}
              onClick={() => setStatusFilter(f)}
              className={`px-2.5 py-1 rounded text-[10px] border cursor-pointer transition-colors ${statusFilter === f
                ? `${styles.accentBg} text-white ${styles.accentBorder}`
                : `${styles.cardBorder} ${styles.cardBg} ${styles.cardTextMuted} hover:opacity-80`}`}
            >
              {f === "ALL" ? t("taskPanel.filterAllStatus") : statusLabel(f)}
            </button>
          ))}
        </div>
      </div>

      {/* 批量操作条 */}
      {selectedIds.size > 0 && (
        <div className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.sidebarBg}`}>
          <span className={`text-[11px] ${styles.cardText} font-semibold`}>
            {t("taskPanel.batch.selected", { count: selectedIds.size }) || `${t("taskPanel.selectedPrefix") || "已选"} ${selectedIds.size} ${t("taskPanel.selectedSuffix") || "个任务"}`}
          </span>
          <button type="button" className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardBg} ${styles.cardText} hover:opacity-80 cursor-pointer`} onClick={() => confirmBatch("resume")}>{t("taskPanel.action.resume") || "恢复"}</button>
          <button type="button" className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardBg} ${styles.cardText} hover:opacity-80 cursor-pointer`} onClick={() => confirmBatch("pause")}>{t("taskPanel.action.pause") || "暂停"}</button>
          <button type="button" className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardBg} ${styles.dangerText} hover:underline cursor-pointer`} onClick={() => confirmBatch("cancel")}>{t("taskPanel.action.cancel") || "中止"}</button>
          <button type="button" className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardBg} ${styles.cardText} hover:opacity-80 cursor-pointer`} onClick={() => confirmBatch("archive")}>{t("taskPanel.action.archive") || "归档"}</button>
          <button type="button" className={`px-2.5 py-1 rounded text-[10px] ${styles.cardTextMuted} hover:opacity-80 cursor-pointer ml-auto`} onClick={() => setSelectedIds(new Set())}>
            {t("taskPanel.batch.deselect") || "清除选择"}
          </button>
        </div>
      )}

      {/* 主体: 表格 + 详情侧栏（移动端纵向堆叠，md 起双栏并列） */}
      <div className="flex-1 flex flex-col md:flex-row gap-3 min-h-0">
        {/* 表格 / 移动端折叠卡片 — md 起切回原生 table */}
        <div className="flex-1 min-w-0 overflow-x-auto md:overflow-visible">
        {isMobile ? (
          <div className="flex flex-col gap-2">
            {loading && filtered.length === 0 ? (
              <div className={`px-4 py-8 text-center text-xs ${styles.cardTextMuted}`}>{t("taskPanel.loading") || "加载中..."}</div>
            ) : filtered.length === 0 ? (
              <div className={`px-4 py-8 text-center text-xs ${styles.cardTextMuted}`}>{t("taskPanel.empty") || "暂无任务"}</div>
            ) : filtered.map(r => {
              const st = (r._status?.status ?? r.status) as string;
              const rowActive = r.taskId === activeTaskId;
              const disabled = TERMINAL_STATUSES.has(st);
              const counts = parseProgressCounts(r.statusMessage);
              return (
                <div
                  key={r.taskId}
                  onClick={() => setActiveTaskId(r.taskId)}
                  className={`rounded-md border cursor-pointer transition-colors ${styles.cardBorder} ${rowActive ? styles.badgeBg : styles.cardBg} ${styles.cardText}`}
                >
                  {/* 卡片头：checkbox + 任务名 + 状态 + 分类 */}
                  <div className="flex items-center gap-2 px-2.5 py-2">
                    <input
                      type="checkbox"
                      checked={selectedIds.has(r.taskId)}
                      onClick={e => e.stopPropagation()}
                      onChange={() => {
                        setSelectedIds(prev => {
                          const next = new Set(prev);
                          if (next.has(r.taskId)) next.delete(r.taskId); else next.add(r.taskId);
                          return next;
                        });
                      }}
                      className="cursor-pointer shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span className="font-semibold text-xs truncate" title={r.taskName}>{r.taskName}</span>
                      </div>
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className={`text-[10px] font-mono ${statusColor(st)}`}>
                          {st === "RUNNING" && <span className={`inline-block w-1.5 h-1.5 rounded-full mr-1 align-middle ${styles.accentText}`} style={{ backgroundColor: "currentColor" }} />}
                          {statusLabel(st)}
                        </span>
                        <span className={`text-[9px] ${styles.cardTextMuted}`}>
                          {r.taskId.slice(0, 8)}…
                        </span>
                      </div>
                    </div>
                    <span className={`text-[10px] shrink-0 px-1.5 py-0.5 border rounded ${styles.cardBorder} ${styles.cardTextMuted}`}>
                      {categoryLabel(r.category)}
                    </span>
                  </div>
                  {/* 进度条（头部下方常驻，不折叠） */}
                  <div className="px-2.5 pt-1 flex items-center gap-2">
                    <div className={`flex-1 h-1 rounded-full overflow-hidden ${styles.cardBorder} border`}>
                      <div
                        className={`h-full transition-all duration-500 ${progressToneClass(st)}`}
                        style={{ width: `${r.progress ?? 0}%`, backgroundColor: "currentColor" }}
                      />
                    </div>
                    <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{r.progress ?? 0}%
                      {counts ? ` · ${counts.done}/${counts.total}` : ""}
                    </span>
                  </div>
                  {/* 状态消息（有则显示，截断） */}
                  {r.statusMessage && (
                    <div className={`px-2.5 pt-1 text-[10px] truncate ${styles.cardTextMuted}`} title={r.statusMessage}>
                      {r.statusMessage}
                    </div>
                  )}
                  {/* 操作按钮组（卡片底部，复用全局 op / ActionBtn，不重写查询逻辑） */}
                  <div className="px-2.5 py-1.5 flex items-center justify-end gap-1 flex-wrap" onClick={e => e.stopPropagation()}>
                    {renderRowActions(r, st, disabled)}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
        <table className={`w-full text-xs border-collapse ${styles.cardBg}`}>
          <thead>
            <tr className={`border-b ${styles.cardBorder} ${styles.sidebarBg}`}>
              <th className="w-8 px-2 py-2">
                <input
                  type="checkbox"
                  checked={allFilteredSelected}
                  onChange={() => {
                    setSelectedIds(prev => {
                      const next = new Set(prev);
                      if (allFilteredSelected) filtered.forEach(r => next.delete(r.taskId));
                      else filtered.forEach(r => next.add(r.taskId));
                      return next;
                    });
                  }}
                  className="cursor-pointer"
                />
              </th>
              <th className={`px-2 py-2 text-left font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.id") || "任务 ID"}</th>
              <th className={`px-2 py-2 text-left font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.name") || "名称 / 类型"}</th>
              <th className={`px-2 py-2 text-left font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.category") || "分类"}</th>
              <th className={`px-2 py-2 text-left font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.status") || "状态"}</th>
              <th className={`px-2 py-2 text-left font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.progress") || "进度"}</th>
              <th className={`px-2 py-2 text-right font-semibold ${styles.cardTextMuted}`}>{t("taskPanel.col.actions") || "操作"}</th>
            </tr>
          </thead>
          <tbody>
            {loading && rows.length === 0 ? (
              <tr><td colSpan={7} className={`px-4 py-8 text-center ${styles.cardTextMuted}`}>{t("taskPanel.loading") || "加载中..."}</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} className={`px-4 py-8 text-center ${styles.cardTextMuted}`}>
                {t("taskPanel.empty") || "暂无任务"}
              </td></tr>
            ) : filtered.map(r => {
              const st = (r._status?.status ?? r.status) as string;
              const rowActive = r.taskId === activeTaskId;
              const disabled = TERMINAL_STATUSES.has(st);
              const counts = parseProgressCounts(r.statusMessage);
              return (
                <tr
                  key={r.taskId}
                  onClick={() => setActiveTaskId(r.taskId)}
                  className={`border-b cursor-pointer ${styles.cardBorder} transition-colors ${rowActive ? styles.badgeBg : styles.sidebarHoverBg} ${styles.cardText}`}
                >
                  <td className="px-2 py-2" onClick={e => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      checked={selectedIds.has(r.taskId)}
                      onChange={() => {
                        setSelectedIds(prev => {
                          const next = new Set(prev);
                          if (next.has(r.taskId)) next.delete(r.taskId); else next.add(r.taskId);
                          return next;
                        });
                      }}
                      className="cursor-pointer"
                    />
                  </td>
                  <td className={`px-2 py-2 font-mono text-[10px] ${styles.cardTextMuted}`}>
                    {r.taskId.slice(0, 8)}…
                  </td>
                  <td className="px-2 py-2">
                    <div className="font-semibold truncate max-w-[240px]" title={r.taskName}>{r.taskName}</div>
                    <div className={`text-[9px] font-mono ${styles.cardTextMuted}`}>
                      {r.taskType} {r.createdBy ? `· by ${r.createdBy}` : ""}
                    </div>
                  </td>
                  <td className="px-2 py-2">
                    <span className={`text-[10px] ${styles.cardBorder} border rounded px-1.5 py-0.5 ${styles.cardTextMuted}`}>
                      {categoryLabel(r.category)}
                    </span>
                  </td>
                  <td className="px-2 py-2">
                    <span className={`font-mono text-[10px] font-bold ${statusColor(st)}`}>
                      {st === "RUNNING" && <span className={`inline-block w-1.5 h-1.5 rounded-full mr-1 align-middle ${styles.accentText}`} style={{ backgroundColor: "currentColor" }} />}
                      {statusLabel(st)}
                    </span>
                    {r.statusMessage && <div className={`text-[9px] max-w-[200px] truncate ${styles.cardTextMuted}`} title={r.statusMessage}>{r.statusMessage}</div>}
                  </td>
                  <td className="px-2 py-2">
                    <div className="flex items-center gap-2">
                      <div className={`w-20 h-1.5 rounded-full overflow-hidden border ${styles.cardBorder}`}>
                        <div
                          className={`h-full transition-all duration-500 ${progressToneClass(st)}`}
                          style={{ width: `${r.progress ?? 0}%`, backgroundColor: "currentColor" }}
                        />
                      </div>
                      <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{r.progress ?? 0}%</span>
                      {counts && (
                        <span className={`text-[9px] font-mono ${styles.cardTextMuted}`} title={`${r.statusMessage ?? ""}`}>
                          {counts.done}/{counts.total}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-2 py-2 text-right" onClick={e => e.stopPropagation()}>
                    {renderRowActions(r, st, disabled)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        )}
        </div>

        {/* 详情抽屉 (右侧) — 移动端全宽，桌面固定 400px */}
        {activeRow && (
          <div
            role="region"
            aria-label={t("dashboard.taskCenter.detailAria")}
            className={`w-full md:w-[400px] shrink-0 border rounded-lg overflow-hidden ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
          >
            <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.sidebarBg} flex items-center justify-between`}>
              <span className="text-xs font-semibold">{t("taskPanel.detail.title") || "任务详情"}</span>
              <button type="button" onClick={() => setActiveTaskId(null)} aria-label={t("common.close")} className="cursor-pointer text-xs hover:opacity-70">
                <X className={`w-3.5 h-3.5 ${styles.cardTextMuted}`} />
              </button>
            </div>
            <div className="p-3 space-y-3 text-xs">
              <DetailRow label={t("taskPanel.detail.id") || "任务 ID"} value={<span className="font-mono text-[10px] break-all">{activeRow.taskId}</span>} muted />
              <DetailRow label={t("taskPanel.detail.name") || "名称"} value={activeRow.taskName} />
              <DetailRow label={t("taskPanel.detail.type") || "类型"} value={activeRow.taskType} />
              <DetailRow label={t("taskPanel.detail.category") || "分类"} value={categoryLabel(activeRow.category)} />
              <DetailRow label={t("taskPanel.detail.status") || "状态"} value={statusLabel(activeRow._status?.status ?? activeRow.status ?? "UNKNOWN")} tone={statusColor(activeRow._status?.status)} />
              {activeRow.statusMessage && <DetailRow label={t("taskPanel.detail.message") || "状态信息"} value={activeRow.statusMessage} muted />}
              <DetailRow label={t("taskPanel.detail.progress") || "进度"} value={activeRow.progress != null ? `${activeRow.progress}%` : "-"} />
              {(() => {
                const c = parseProgressCounts(activeRow.statusMessage);
                return c ? <DetailRow label={t("taskPanel.detail.done") || "已完成"} value={`${c.done} / ${c.total}`} /> : null;
              })()}
              {activeRow.startedAt && <DetailRow label={t("taskPanel.detail.started") || "开始时间"} value={<TimeStr v={activeRow.startedAt} />} muted />}
              {activeRow.completedAt && <DetailRow label={t("taskPanel.detail.completed") || "完成时间"} value={<TimeStr v={activeRow.completedAt} />} muted />}
              {activeRow.createdBy && <DetailRow label={t("taskPanel.detail.createdBy") || "创建人"} value={activeRow.createdBy} />}
              {activeRow.priority != null && <DetailRow label={t("taskPanel.detail.priority") || "优先级"} value={String(activeRow.priority)} />}
              {activeRow.description && <DetailRow label={t("taskPanel.detail.desc") || "描述"} value={activeRow.description} />}
              {activeRow.parameters && Object.keys(activeRow.parameters).length > 0 && (
                <div>
                  <div className={`text-[10px] mb-1 ${styles.cardTextMuted}`}>{t("taskPanel.detail.params") || "参数"}</div>
                  <pre className={`text-[10px] p-2 rounded border ${styles.cardBorder} ${styles.sidebarBg} overflow-auto max-h-48 font-mono`}>
                    {(() => { try { return JSON.stringify(activeRow.parameters, null, 2); } catch { return String(activeRow.parameters); } })()}
                  </pre>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* 破坏性/批量操作确认门 */}
      <ConfirmDialog
        visible={pendingOp != null}
        title={pendingCopy.title}
        message={pendingCopy.message}
        danger
        onConfirm={onConfirmPending}
        onCancel={() => setPendingOp(null)}
      />
    </div>
  );
}

// ── 子组件 ─────────────────────────────────────────────
function ActionBtn({ icon: Icon, title, ariaLabel, tone, onClick }: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  ariaLabel: string;
  tone: "accent" | "warn" | "danger" | "muted";
  onClick: () => void;
}) {
  const { styles } = useTheme();
  const color = tone === "accent" ? styles.accentText : tone === "warn" ? styles.warningText : tone === "danger" ? styles.dangerText : styles.cardTextMuted;
  return (
    <button type="button"
      onClick={onClick}
      title={title}
      aria-label={ariaLabel}
      className={`w-6 h-6 rounded border ${styles.cardBorder} ${styles.cardBg} flex items-center justify-center cursor-pointer transition-colors hover:opacity-80`}
    >
      <Icon className={`w-3 h-3 ${color}`} />
    </button>
  );
}

function DetailRow({ label, value, tone, muted }: { label: string; value: React.ReactNode; tone?: string; muted?: boolean }) {
  const { styles } = useTheme();
  return (
    <div className="flex items-start gap-2">
      <span className={`text-[10px] w-20 shrink-0 ${styles.cardTextMuted}`}>{label}</span>
      <span className={`flex-1 break-words ${tone || styles.cardText}`}>{value}</span>
    </div>
  );
}

function TimeStr({ v }: { v: string }) {
  try {
    const d = new Date(v);
    if (isNaN(d.getTime())) return v;
    return d.toLocaleString();
  } catch { return v; }
}
