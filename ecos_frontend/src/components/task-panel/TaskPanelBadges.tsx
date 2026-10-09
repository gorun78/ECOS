/**
 * TaskPanelBadges — 状态徽章 / 分类徽章 / 进度条（自 TaskPanel.tsx render* 闭包函数原样迁出，H6-T4）
 * 呈现型组件：内部各自 useTheme() / useLanguage()，不接收 styles 参数。
 * @license Apache-2.0
 */

import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";
import {
  getCategory,
  STATUS_COLORS,
  STATUS_LABEL_KEYS,
  type TaskStatus,
} from "./taskPanelModel";

export function StatusBadge({ status }: { status: TaskStatus }) {
  const { t } = useLanguage();
  const c = STATUS_COLORS[status] || STATUS_COLORS.PENDING;
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${c.bg} ${c.text}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${c.dot}`} />
      {t(STATUS_LABEL_KEYS[status] || "taskPanel.status.pending")}
    </span>
  );
}

export function CategoryBadge({ taskType }: { taskType: string }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
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
}

export function ProgressCell({ progress }: { progress: number }) {
  const { styles } = useTheme();
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          className="h-full rounded-full transition-all duration-500"
          style={{ width: `${Math.min(100, Math.max(0, progress))}%`, backgroundColor: "var(--accent)" }}
        />
      </div>
      <span className={`text-xs ${styles.muted} w-10 text-right`}>{progress}%</span>
    </div>
  );
}
