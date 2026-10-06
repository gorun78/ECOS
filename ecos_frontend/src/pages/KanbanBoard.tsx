/**
 * ECOS 项目看板 — 嵌入 kanban-web standalone
 */
import React from "react";
import { LayoutDashboard } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

export default function KanbanBoard() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="h-full flex flex-col">
      {/* Title bar */}
      <div className={`flex items-center gap-2 px-6 py-4 border-b ${styles.cardBorder}`}>
        <LayoutDashboard size={20} className={styles.accentText} />
        <h1 className={`text-lg font-bold ${styles.appText}`}>{t('kanban.title')}</h1>
        <span className={`text-xs ml-2 ${styles.cardTextMuted}`}>{t('kanban.subtitle')}</span>
      </div>
      {/* Kanban iframe */}
      <iframe
        src="/kanban/ecos-kanban.html"
        className="flex-1 w-full border-0"
        title="ECOS Kanban"
        sandbox="allow-scripts allow-same-origin"
      />
    </div>
  );
}
