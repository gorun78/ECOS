/**
 * TaskPanelTable — 任务列表表格 + 分页（自 TaskPanel.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { Play, Pause, RotateCcw, Square, Archive } from "lucide-react";
import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";
import { formatTime, type TaskItem, type TaskStatusInfo } from "./taskPanelModel";
import { CategoryBadge, ProgressCell, StatusBadge } from "./TaskPanelBadges";

interface TaskPanelTableProps {
  displayTasks: TaskItem[];
  statusMap: Record<string, TaskStatusInfo>;
  selectedIds: Set<string>;
  actingIds: Set<string>;
  loading: boolean;
  openDetail: (taskId: string) => void;
  toggleAll: () => void;
  toggleOne: (taskId: string) => void;
  doAction: (taskId: string, action: "execute" | "cancel" | "pause" | "resume" | "archive") => void;
}

export function TaskPanelTable({
  displayTasks,
  statusMap,
  selectedIds,
  actingIds,
  loading,
  openDetail,
  toggleAll,
  toggleOne,
  doAction,
}: TaskPanelTableProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Task Table ───────────────────────────
  return (
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
                  <CategoryBadge taskType={task.taskType} />
                </td>
                <td className="px-2 py-2.5">
                  <span className={`text-xs px-1.5 py-0.5 rounded ${styles.appBg} ${styles.cardTextMuted}`}>
                    {task.taskType || "—"}
                  </span>
                </td>
                <td className="px-2 py-2.5"><StatusBadge status={status} /></td>
                <td className="px-2 py-2.5"><ProgressCell progress={progress} /></td>
                <td className={`px-2 py-2.5 text-xs ${styles.cardTextMuted}`}>
                  {formatTime(task.createTime)}
                </td>
                <td className="px-2 py-2.5" onClick={e => e.stopPropagation()}>
                  <div className="flex items-center gap-1">
                    <button type="button"
                      onClick={() => doAction(task.taskId, "execute")}
                      disabled={isActing || status === "RUNNING"}
                      className="p-1 rounded hover:bg-green-100 dark:hover:bg-green-900/30 text-green-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                      title={t("taskPanel.action.start")}
                    ><Play className="w-3.5 h-3.5" /></button>
                    <button type="button"
                      onClick={() => doAction(task.taskId, "pause")}
                      disabled={isActing || status !== "RUNNING"}
                      className="p-1 rounded hover:bg-amber-100 dark:hover:bg-amber-900/30 text-amber-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                      title={t("taskPanel.action.pause")}
                    ><Pause className="w-3.5 h-3.5" /></button>
                    <button type="button"
                      onClick={() => doAction(task.taskId, "resume")}
                      disabled={isActing || status === "RUNNING"}
                      className="p-1 rounded hover:bg-blue-100 dark:hover:bg-blue-900/30 text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                      title={t("taskPanel.action.resume")}
                    ><RotateCcw className="w-3.5 h-3.5" /></button>
                    <button type="button"
                      onClick={() => doAction(task.taskId, "cancel")}
                      disabled={isActing || status === "SUCCEEDED" || status === "CANCELLED"}
                      className="p-1 rounded hover:bg-red-100 dark:hover:bg-red-900/30 text-red-600 disabled:opacity-30 disabled:cursor-not-allowed transition"
                      title={t("taskPanel.action.cancel")}
                    ><Square className="w-3.5 h-3.5" /></button>
                    <button
                      type="button"
                      onClick={() => doAction(task.taskId, "archive")}
                      disabled={isActing}
                      className={`p-1 rounded ${styles.sidebarHoverBg} ${styles.muted} disabled:opacity-30 disabled:cursor-not-allowed transition`}
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
  );
}

interface TaskPanelPaginationProps {
  page: number;
  setPage: Dispatch<SetStateAction<number>>;
  total: number;
  totalPages: number;
}

export function TaskPanelPagination({ page, setPage, total, totalPages }: TaskPanelPaginationProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Pagination ───────────────────────────
  return (
    <div className={`flex items-center justify-between px-6 py-3 border-t ${styles.appBorder} shrink-0 text-sm`}>
      <span className={`${styles.cardTextMuted}`}>{t("taskPanel.pagination.total", { n: total })}</span>
      <div className="flex items-center gap-1">
        <button type="button"
          onClick={() => setPage(p => Math.max(1, p - 1))}
          disabled={page <= 1}
          className={`px-3 py-1 rounded border ${styles.cardBorder} ${styles.sidebarHoverBg} disabled:opacity-40 transition`}
        >{t("taskPanel.pagination.prev")}</button>
        <span className={`px-2 ${styles.cardText}`}>{page} / {totalPages}</span>
        <button type="button"
          onClick={() => setPage(p => Math.min(totalPages, p + 1))}
          disabled={page >= totalPages}
          className={`px-3 py-1 rounded border ${styles.cardBorder} ${styles.sidebarHoverBg} disabled:opacity-40 transition`}
        >{t("taskPanel.pagination.next")}</button>
      </div>
    </div>
  );
}
