/**
 * TaskPanel — Task Engine Management Panel
 * Stats cards + filter bar + task list + batch operations + detail drawer
 * Supports 4 categories: Pipeline / Agent / Realtime / Management
 *
 * H6-T4 拆分：类型/常量 → ./task-panel/taskPanelModel；状态逻辑 → ./task-panel/useTaskPanel；
 * JSX 区块 → ./task-panel/TaskPanel* 展示组件。行为与 API 路径保持不变。
 * @license Apache-2.0
 */

import React from "react";
import { X, RefreshCw } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import { useToast } from "./common/Toast";
import ConfirmDialog from "./common/ConfirmDialog";
import { useTaskPanel } from "./task-panel/useTaskPanel";
import { TaskPanelCategoryStats, TaskPanelStatusStats } from "./task-panel/TaskPanelStatsCards";
import { TaskPanelFilterBar } from "./task-panel/TaskPanelFilterBar";
import { TaskPanelBatchBar } from "./task-panel/TaskPanelBatchBar";
import { TaskPanelPagination, TaskPanelTable } from "./task-panel/TaskPanelTable";
import { TaskPanelDetailDrawer } from "./task-panel/TaskPanelDetailDrawer";

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

  const panel = useTaskPanel(open, t, showToast);

  const {
    stats,
    loading,
    categoryCounts,
    filteredTasks,
    totalPages,
    filterStatus,
    setFilterStatus,
    filterType,
    setFilterType,
    setSearchType,
    filterCategory,
    setFilterCategory,
    taskTypes,
    page,
    setPage,
    total,
    statusMap,
    selectedIds,
    setSelectedIds,
    batchActioning,
    batchConfirm,
    setBatchConfirm,
    actingIds,
    detailTask,
    setDetailTask,
    detailLoading,
    refresh,
    doAction,
    doBatchAction,
    confirmBatchAction,
    openDetail,
    toggleAll,
    toggleOne,
  } = panel;

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

        <TaskPanelCategoryStats categoryCounts={categoryCounts} />

        <TaskPanelStatusStats stats={stats} />

        <TaskPanelFilterBar
          filterCategory={filterCategory}
          setFilterCategory={setFilterCategory}
          filterStatus={filterStatus}
          setFilterStatus={setFilterStatus}
          filterType={filterType}
          setFilterType={setFilterType}
          setSearchType={setSearchType}
          taskTypes={taskTypes}
        />

        {/* ── Batch Action Bar ───────────────────── */}
        {selectedIds.size > 0 && (
          <TaskPanelBatchBar
            selectedCount={selectedIds.size}
            batchActioning={batchActioning}
            doBatchAction={doBatchAction}
            setSelectedIds={setSelectedIds}
          />
        )}

        <TaskPanelTable
          displayTasks={displayTasks}
          statusMap={statusMap}
          selectedIds={selectedIds}
          actingIds={actingIds}
          loading={loading}
          openDetail={openDetail}
          toggleAll={toggleAll}
          toggleOne={toggleOne}
          doAction={doAction}
        />

        {/* ── Pagination ─────────────────────────── */}
        {totalPages > 1 && (
          <TaskPanelPagination page={page} setPage={setPage} total={total} totalPages={totalPages} />
        )}
      </div>

      <TaskPanelDetailDrawer
        detailTask={detailTask}
        detailLoading={detailLoading}
        onClose={() => setDetailTask(null)}
      />

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
