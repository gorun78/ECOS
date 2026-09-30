/**
 * TaskPanelBatchBar — 批量操作条（自 TaskPanel.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";

interface TaskPanelBatchBarProps {
  selectedCount: number;
  batchActioning: boolean;
  doBatchAction: (action: "cancel" | "pause" | "resume" | "archive") => void;
  setSelectedIds: Dispatch<SetStateAction<Set<string>>>;
}

export function TaskPanelBatchBar({ selectedCount, batchActioning, doBatchAction, setSelectedIds }: TaskPanelBatchBarProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="flex items-center gap-2 px-6 py-2 bg-blue-50 dark:bg-blue-900/20 border-b border-blue-100 dark:border-blue-800/30 shrink-0">
      <span className="text-sm font-medium" style={{ color: "var(--accent)" }}>
        {t("taskPanel.batch.selected", { n: selectedCount })}
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
  );
}
