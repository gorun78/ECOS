/**
 * ECOS 项目看板 — 嵌入 kanban-web standalone
 */
import React from "react";
import { LayoutDashboard } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";

export default function KanbanBoard() {
  const { t } = useLanguage();
  return (
    <div className="h-full flex flex-col">
      {/* Title bar */}
      <div className="flex items-center gap-2 px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <LayoutDashboard size={20} className="text-indigo-500" />
        <h1 className="text-lg font-bold">ECOS 项目看板</h1>
        <span className="text-xs text-gray-400 ml-2">Sprint 4 — 系统重构 & 产品化</span>
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
