/* 异步任务中心 — 对接 ITaskManagementService 真实后端
 * 改造自 mock 实现 (2026-09-07)
 * - 任务列表 / 状态实时 / 操作（执行/取消/暂停/恢复/归档）
 * - 每 5s 轮询任务状态，新任务自动出现
 * - 主题感知 (useTheme), 国际化 (useLanguage, taskPanel.* 或 dw.taskCenter.*)
 * - PMO-72 W4: 数据源 = td_runtime_task* 5 表 (PG sys_man.public) + runtime-task JdbcTaskPersistenceService 持久化 + PG 重启恢复
 */
import React, { useEffect, useMemo, useState, useCallback } from 'react';
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
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

const STATUS_FILTERS: FilterStatus[] = ["ALL", "PENDING", "RUNNING", "PAUSED", "SUCCEEDED", "FAILED", "CANCELLED"];

const POLL_INTERVAL_MS = 5000;

interface RowData extends TaskFull {
  _status: TaskStatusInfo | null;
}

export default function AsyncTaskCenterView({ showToast, onViewModeChange }: AsyncTaskCenterViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<RowData[]>([]);
  const [stats, setStats] = useState<TaskStats>({ total: 0, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
  const [loading, setLoading] = useState(true);
  const [lastRefresh, setLastRefresh] = useState<Date | null>(null);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("ALL");
  const [categoryFilter, setCategoryFilter] = useState<string>("ALL");
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [activeTaskId, setActiveTaskId] = useState<string | null>(null);

  // ── 数据加载 ────────────────────────────────────────
  type FetchListT = Promise<import("../services/taskCenter").TaskListResult | null>;
  type FetchStatsT = Promise<TaskStats | null>;
  type FetchStatusT = Promise<TaskStatusInfo | null>;
  const loadOnce = useCallback(async (): Promise<void> => {
    try {
      const listPromise: FetchListT = fetchTaskList({ offset: 0, limit: 100 });
      const stPromise: FetchStatsT = fetchTaskStats().catch((): TaskStats | null => null);
      const [list, st] = await Promise.all([listPromise, stPromise]);
      if (list && Array.isArray(list.items)) {
        // 为每个任务取实时状态 (避免 Promise.all 类型推断复杂, 用单函数)
        const buildRow = async (task: TaskSummary): Promise<RowData> => {
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
        };
        const withStatus = await Promise.all(list.items.map(buildRow));
        setRows(withStatus);
        setStats(st ?? { total: withStatus.length, running: 0, pending: 0, succeeded: 0, failed: 0, cancelled: 0 });
      }
      setLastRefresh(new Date());
    } catch (e) {
      console.warn("[taskCenter] refresh failed:", e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOnce();
    const timer = setInterval(loadOnce, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [loadOnce]);

  // ── 单/批量操作 ────────────────────────────────────
  const op = useCallback(async (taskId: string, action: "execute" | "cancel" | "pause" | "resume" | "archive") => {
    const map = { execute: executeTask, cancel: cancelTask, pause: pauseTask, resume: resumeTask, archive: archiveTask } as const;
    const i18nKey = { execute: "taskPanel.action.execute", cancel: "taskPanel.action.cancel", pause: "taskPanel.action.pause", resume: "taskPanel.action.resume", archive: "taskPanel.action.archive" } as const;
    const label = t(i18nKey[action]) || action;
    const ok = !!(await map[action](taskId));
    showToast?.(ok ? "success" : "error", `${t("taskPanel.actionFailed")} (${taskNameById(taskId)} → ${label})`);
    // 立即重拉
    setTimeout(loadOnce, 300);
  }, [loadOnce, showToast, t]);

  const taskNameById = (id: string) => rows.find(r => r.taskId === id)?.taskName || id.slice(0, 8);

  const confirmBatch = async (action: "cancel" | "pause" | "resume" | "archive") => {
    const i18nKey = { cancel: "taskPanel.action.cancel", pause: "taskPanel.action.pause", resume: "taskPanel.action.resume", archive: "taskPanel.action.archive" } as const;
    const actionLabel = t(i18nKey[action]) || action;
    const n = selectedIds.size;
    const confirmed = window.confirm(t("taskPanel.batch.confirmMessage")?.replace("{action}", actionLabel).replace("{count}", String(n)) || `Batch ${actionLabel} ${n}?`);
    if (!confirmed) return;
    const res = await batchTask([...selectedIds], action);
    showToast?.(res ? "success" : "error", res ? `${t("taskPanel.batch.done")} (${actionLabel})` : t("taskPanel.batch.failed"));
    setSelectedIds(new Set());
    setTimeout(loadOnce, 300);
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

  const active = activeTaskId ? rows.find(r => r.taskId === activeTaskId) : null;
  const allFilteredSelected = filtered.length > 0 && filtered.every(r => selectedIds.has(r.taskId));

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

  return (
    <div className="h-full flex flex-col gap-3 p-4 font-sans" style={{ background: styles.appBg }}>
      {/* 顶栏: 标题 + 统计 */}
      <div className="flex items-center gap-4 flex-wrap">
        <div className="flex flex-col">
          <h2 className={`text-lg font-bold ${styles.cardText} tracking-tight`}>{t("sidebar.task_center") || "异步任务中心"}</h2>
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
            {lastRefresh ? `· ${t("taskPanel.lastUpdated") || "Refreshed at"} ${lastRefresh.toLocaleTimeString()} · 5s` : (t("taskPanel.live") || "Live · 5s")}
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
        <button
          onClick={() => { setLoading(true); loadOnce().finally(() => setLoading(false)); }}
          className={`px-3 py-1.5 rounded-md border ${styles.cardBorder} ${styles.cardText} hover:${styles.accentText} transition-colors cursor-pointer text-xs flex items-center gap-1.5`}
        >
          <span className={loading ? "animate-spin" : ""}>↻</span>
          {t("taskPanel.refresh") || "刷新"}
        </button>
      </div>

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
            <button
              key={f}
              onClick={() => setStatusFilter(f)}
              className={`px-2.5 py-1 rounded text-[10px] border cursor-pointer transition-colors ${statusFilter === f
                ? `${styles.accentBg} ${styles.accentText} border-${styles.accentBg}`
                : `${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText}`}`}
            >
              {f === "ALL" ? (t("taskPanel.filterAllStatus") || "全部") : f}
            </button>
          ))}
        </div>
      </div>

      {/* 批量操作条 */}
      {selectedIds.size > 0 && (
        <div className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${styles.cardBorder} ${styles.sidebarBg}/50`}>
          <span className={`text-[11px] ${styles.cardText} font-semibold`}>
            {t("taskPanel.batch.selected")?.replace("{count}", String(selectedIds.size)) || `${t("taskPanel.selectedPrefix") || "已选"} ${selectedIds.size} ${t("taskPanel.selectedSuffix") || "个任务"}`}
          </span>
          <button className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardText} hover:${styles.accentText} cursor-pointer`} onClick={() => confirmBatch("resume")}>{t("taskPanel.action.resume") || "恢复"}</button>
          <button className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardText} hover:${styles.accentText} cursor-pointer`} onClick={() => confirmBatch("pause")}>{t("taskPanel.action.pause") || "暂停"}</button>
          <button className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.dangerText} hover:underline cursor-pointer`} onClick={() => confirmBatch("cancel")}>{t("taskPanel.action.cancel") || "中止"}</button>
          <button className={`px-2.5 py-1 rounded text-[10px] ${styles.cardBorder} border ${styles.cardText} hover:${styles.accentText} cursor-pointer`} onClick={() => confirmBatch("archive")}>{t("taskPanel.action.archive") || "归档"}</button>
          <button className={`px-2.5 py-1 rounded text-[10px] ${styles.cardTextMuted} hover:${styles.cardText} cursor-pointer ml-auto`} onClick={() => setSelectedIds(new Set())}>
            {t("taskPanel.batch.deselect") || "清除选择"}
          </button>
        </div>
      )}

      {/* 主体: 表格 + 详情侧栏（移动端纵向堆叠，md 起双栏并列） */}
      <div className="flex-1 flex flex-col md:flex-row gap-3 min-h-0">
        {/* 表格 — 移动端横滚 / 桌面占满剩余空间 */}
        <div className="flex-1 min-w-0 overflow-x-auto md:overflow-visible">
        <table className="w-full text-xs border-collapse" style={{ background: styles.cardBg }}>
          <thead>
            <tr className={`border-b ${styles.cardBorder} ${styles.sidebarBg}/60`}>
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
              <th className="px-2 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.id") || "任务 ID"}</th>
              <th className="px-2 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.name") || "名称 / 类型"}</th>
              <th className="px-2 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.category") || "分类"}</th>
              <th className="px-2 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.status") || "状态"}</th>
              <th className="px-2 py-2 text-left font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.progress") || "进度"}</th>
              <th className="px-2 py-2 text-right font-semibold" style={{ color: styles.cardTextMuted }}>{t("taskPanel.col.actions") || "操作"}</th>
            </tr>
          </thead>
          <tbody>
            {loading && rows.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center" style={{ color: styles.cardTextMuted }}>{t("taskPanel.loading") || "加载中..."}</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={7} className="px-4 py-8 text-center" style={{ color: styles.cardTextMuted }} >
                {t("taskPanel.empty") || "暂无任务"}
              </td></tr>
            ) : filtered.map(r => {
              const st = (r._status?.status ?? r.status) as string;
              const active = r.taskId === activeTaskId;
              const disabled = st === "SUCCEEDED" || st === "FAILED" || st === "CANCELLED" || st === "TIMEOUT";
              const counts = parseProgressCounts(r.statusMessage);
              return (
                <tr
                  key={r.taskId}
                  onClick={() => setActiveTaskId(r.taskId)}
                  className={`border-b cursor-pointer ${styles.cardBorder} transition-colors ${active ? `${styles.accentBg}/10` : "hover:bg-white/3"}`}
                  style={{ color: styles.cardText, background: active ? (styles.accentBg as string) + "0d" : undefined }}
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
                  <td className="px-2 py-2 font-mono text-[10px]" style={{ color: styles.cardTextMuted }}>
                    {r.taskId.slice(0, 8)}…
                  </td>
                  <td className="px-2 py-2">
                    <div className="font-semibold truncate max-w-[240px]" title={r.taskName}>{r.taskName}</div>
                    <div className="text-[9px] font-mono" style={{ color: styles.cardTextMuted }}>
                      {r.taskType} {r.createdBy ? `· by ${r.createdBy}` : ""}
                    </div>
                  </td>
                  <td className="px-2 py-2">
                    <span className={`text-[10px] ${styles.cardBorder} border rounded px-1.5 py-0.5 ${styles.cardTextMuted}`}>
                      {categoryLabel(r.category)}
                    </span>
                  </td>
                  <td className="px-2 py-2">
                    <span className="font-mono text-[10px] font-bold" style={{ color: statusColor(st) }}>
                      {st === "RUNNING" && <span className="inline-block w-1.5 h-1.5 rounded-full mr-1 align-middle" style={{ background: styles.accentText }} />}
                      {t(`taskPanel.status.${st.toLowerCase()}`) || st}
                    </span>
                    {r.statusMessage && <div className="text-[9px] max-w-[200px] truncate" style={{ color: styles.cardTextMuted }} title={r.statusMessage}>{r.statusMessage}</div>}
                  </td>
                  <td className="px-2 py-2">
                    <div className="flex items-center gap-2">
                      <div className="w-20 h-1.5 rounded-full overflow-hidden" style={{ background: styles.cardBorder }}>
                        <div
                          className="h-full transition-all duration-500"
                          style={{ width: `${r.progress ?? 0}%`, background: st === "FAILED" || st === "CANCELLED" ? styles.dangerText : st === "SUCCEEDED" ? styles.successText : styles.accentText }}
                        />
                      </div>
                      <span className="text-[10px] font-mono" style={{ color: styles.cardTextMuted }}>{r.progress ?? 0}%</span>
                      {counts && (
                        <span className="text-[9px] font-mono" style={{ color: styles.cardTextMuted }} title={`${r.statusMessage ?? ""}`}>
                          {counts.done}/{counts.total}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-2 py-2 text-right" onClick={e => e.stopPropagation()}>
                    <div className="flex gap-1 justify-end">
                      {!disabled && st !== "RUNNING" && (
                        <ActionBtn label="▶" title={t("taskPanel.action.execute") || "执行"} tone="accent" onClick={() => op(r.taskId, "execute")} />
                      )}
                      {st === "RUNNING" && <ActionBtn label="⏸" title={t("taskPanel.action.pause") || "暂停"} tone="warn" onClick={() => op(r.taskId, "pause")} />}
                      {st === "PAUSED" && <ActionBtn label="▶" title={t("taskPanel.action.resume") || "恢复"} tone="accent" onClick={() => op(r.taskId, "resume")} />}
                      {(st === "RUNNING" || st === "PAUSED" || st === "PENDING" || st === "PARSING" || st === "PARSED") && (
                        <ActionBtn label="⏹" title={t("taskPanel.action.cancel") || "中止"} tone="danger" onClick={() => op(r.taskId, "cancel")} />
                      )}
                      <ActionBtn label="🗑" title={t("taskPanel.action.archive") || "归档"} tone="muted" onClick={() => op(r.taskId, "archive")} />
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        </div>

        {/* 详情抽屉 (右侧) — 移动端全宽，桌面固定 400px */}
        {active && (
          <div className="w-full md:w-[400px] shrink-0 border rounded-lg overflow-hidden" style={{ background: styles.cardBg, borderColor: styles.cardBorder, color: styles.cardText }}>
            <div className={`px-3 py-2 border-b ${styles.cardBorder} flex items-center justify-between`} style={{ background: (styles.sidebarBg as string) + "80" }}>
              <span className="text-xs font-semibold">{t("taskPanel.detail.title") || "任务详情"}</span>
              <button onClick={() => setActiveTaskId(null)} className="cursor-pointer text-xs hover:opacity-70" style={{ color: styles.cardTextMuted }}>✕</button>
            </div>
            <div className="p-3 space-y-3 text-xs">
              <DetailRow label={t("taskPanel.detail.id") || "任务 ID"} value={<span className="font-mono text-[10px] break-all">{active.taskId}</span>} muted />
              <DetailRow label={t("taskPanel.detail.name") || "名称"} value={active.taskName} />
              <DetailRow label={t("taskPanel.detail.type") || "类型"} value={active.taskType} />
              <DetailRow label={t("taskPanel.detail.category") || "分类"} value={categoryLabel(active.category)} />
              <DetailRow label={t("taskPanel.detail.status") || "状态"} value={(active._status?.status ?? active.status) || "UNKNOWN"} tone={statusColor(active._status?.status)} />
              {active.statusMessage && <DetailRow label={t("taskPanel.detail.message") || "状态信息"} value={active.statusMessage} muted />}
              <DetailRow label={t("taskPanel.detail.progress") || "进度"} value={active.progress != null ? `${active.progress}%` : "-"} />
              {(() => {
                const c = parseProgressCounts(active.statusMessage);
                return c ? <DetailRow label={t("taskPanel.detail.done") || "已完成"} value={`${c.done} / ${c.total}`} /> : null;
              })()}
              {active.startedAt && <DetailRow label={t("taskPanel.detail.started") || "开始时间"} value={<TimeStr v={active.startedAt} />} muted />}
              {active.completedAt && <DetailRow label={t("taskPanel.detail.completed") || "完成时间"} value={<TimeStr v={active.completedAt} />} muted />}
              {active.createdBy && <DetailRow label={t("taskPanel.detail.createdBy") || "创建人"} value={active.createdBy} />}
              {active.priority != null && <DetailRow label={t("taskPanel.detail.priority") || "优先级"} value={String(active.priority)} />}
              {active.description && <DetailRow label={t("taskPanel.detail.desc") || "描述"} value={active.description} />}
              {active.parameters && Object.keys(active.parameters).length > 0 && (
                <div>
                  <div className="text-[10px] mb-1" style={{ color: styles.cardTextMuted }}>{t("taskPanel.detail.params") || "参数"}</div>
                  <pre className={`text-[10px] p-2 rounded border ${styles.cardBorder} overflow-auto max-h-48 font-mono`} style={{ background: (styles.sidebarBg as string) + "60" }}>
                    {(() => { try { return JSON.stringify(active.parameters, null, 2); } catch { return String(active.parameters); } })()}
                  </pre>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

// ── 子组件 ─────────────────────────────────────────────
function ActionBtn({ label, title, tone, onClick }: { label: string; title: string; tone: "accent" | "warn" | "danger" | "muted"; onClick: () => void }) {
  const { styles } = useTheme();
  const color = tone === "accent" ? styles.accentText : tone === "warn" ? styles.warningText : tone === "danger" ? styles.dangerText : styles.cardTextMuted;
  return (
    <button
      onClick={onClick}
      title={title}
      className={`w-6 h-6 rounded border ${styles.cardBorder} flex items-center justify-center text-[11px] cursor-pointer transition-colors hover:opacity-80`}
      style={{ color: color, background: (styles.sidebarBg as string) + "40" }}
    >
      {label}
    </button>
  );
}

function DetailRow({ label, value, tone, muted }: { label: string; value: React.ReactNode; tone?: string; muted?: boolean }) {
  const { styles } = useTheme();
  return (
    <div className="flex items-start gap-2">
      <span className="text-[10px] w-20 shrink-0" style={{ color: styles.cardTextMuted }}>{label}</span>
      <span className={`flex-1 break-words ${muted ? "" : ""}`} style={{ color: tone || styles.cardText }}>{value}</span>
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
