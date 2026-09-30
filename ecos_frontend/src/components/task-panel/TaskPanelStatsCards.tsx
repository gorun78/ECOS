/**
 * TaskPanelStatsCards — 分类统计卡 + 状态统计卡（自 TaskPanel.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";
import { CATEGORIES } from "./taskPanelModel";

export function TaskPanelCategoryStats({ categoryCounts }: { categoryCounts: Record<string, number> }) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Stats Cards — 分类统计 ───────────────
  return (
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
  );
}

export function TaskPanelStatusStats({ stats }: { stats: { running: number; pending: number; succeeded: number; failed: number } }) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Stats Cards — 状态统计 ───────────────
  return (
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
  );
}
