/**
 * TaskPanelDetailDrawer — 详情抽屉（遮罩 + 抽屉本体，自 TaskPanel.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import { X, RefreshCw } from "lucide-react";
import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";
import { formatTime, PRIORITY_LABEL_KEYS, type TaskDetail } from "./taskPanelModel";
import { CategoryBadge, ProgressCell, StatusBadge } from "./TaskPanelBadges";

interface TaskPanelDetailDrawerProps {
  detailTask: TaskDetail | null;
  detailLoading: boolean;
  onClose: () => void;
}

export function TaskPanelDetailDrawer({ detailTask, detailLoading, onClose }: TaskPanelDetailDrawerProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <>
      {/* ── 详情抽屉遮罩 ────────────────────────── */}
      {detailTask && (
        <div className="absolute inset-0 bg-transparent" onClick={onClose} />
      )}

      {/* ── 详情抽屉 ────────────────────────────── */}
      <div
        className={`absolute top-0 right-0 h-full w-[360px] max-w-full ${styles.cardBg} shadow-2xl border-l ${styles.cardBorder} overflow-auto transition-transform duration-300 ${
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
              <button type="button" onClick={onClose} className={`p-1.5 rounded ${styles.sidebarHoverBg} transition`}>
                <X className={`w-4 h-4 ${styles.cardTextMuted}`} />
              </button>
            </div>

            {/* 执行状态 */}
            <section>
              <h4 className={`text-xs font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-3`}>{t("taskPanel.detail.execution")}</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.status")}</span>
                  <StatusBadge status={detailTask.status.status} />
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.progress")}</span>
                  <span className="w-48"><ProgressCell progress={detailTask.status.progress} /></span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.started")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{formatTime(detailTask.status.startedAt)}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.completed")}</span>
                  <span className={`text-sm ${styles.cardText}`}>{formatTime(detailTask.status.completedAt)}</span>
                </div>
              </div>
            </section>

            {/* 基本信息 */}
            <section>
              <h4 className={`text-xs font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-3`}>{t("taskPanel.detail.basicInfo")}</h4>
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.id")}</span>
                  <span className={`text-sm font-mono ${styles.cardText} text-xs`}>{detailTask.task.taskId}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.name")}</span>
                  <span className={`text-sm ${styles.cardText} font-medium`}>{detailTask.task.taskName}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.category")}</span>
                  <span><CategoryBadge taskType={detailTask.task.taskType} /></span>
                </div>
                <div className="flex items-center justify-between">
                  <span className={`text-sm ${styles.cardTextMuted}`}>{t("taskPanel.detail.type")}</span>
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
                    <span className={`text-sm ${styles.cardTextMuted} block mb-1`}>{t("taskPanel.detail.desc")}</span>
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
    </>
  );
}
