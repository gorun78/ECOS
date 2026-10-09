/**
 * TaskPanelFilterBar — 分类 Tab + 状态/类型筛选（自 TaskPanel.tsx JSX 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import type { Dispatch, SetStateAction } from "react";
import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";
import { Search } from "lucide-react";
import { CATEGORIES, type TaskCategory } from "./taskPanelModel";

interface TaskPanelFilterBarProps {
  filterCategory: TaskCategory | "";
  setFilterCategory: Dispatch<SetStateAction<TaskCategory | "">>;
  filterStatus: string;
  setFilterStatus: Dispatch<SetStateAction<string>>;
  filterType: string;
  setFilterType: Dispatch<SetStateAction<string>>;
  setSearchType: Dispatch<SetStateAction<string>>;
  taskTypes: string[];
}

export function TaskPanelFilterBar({
  filterCategory,
  setFilterCategory,
  filterStatus,
  setFilterStatus,
  filterType,
  setFilterType,
  setSearchType,
  taskTypes,
}: TaskPanelFilterBarProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── Filter Bar ───────────────────────────
  return (
    <div className={`flex flex-col gap-2 px-6 py-3 border-b ${styles.appBorder} shrink-0`}>
      {/* 分类 Tab 按钮 */}
      <div className="flex items-center gap-1.5">
        <span className={`text-xs ${styles.cardTextMuted} mr-1`}>{t("taskPanel.filter.category")}</span>
        <button
          onClick={() => setFilterCategory("")}
          className={`px-2.5 py-1 text-xs rounded-full transition ${
            filterCategory === ""
              ? `bg-[var(--card)] text-white font-medium`
              : `${styles.appBg} ${styles.cardTextMuted} hover:${styles.sidebarHoverBg}`
          }`}
        >
          {t("taskPanel.filterAll")}
        </button>
        {CATEGORIES.map(cat => (
          <button
            key={cat.key}
            onClick={() => setFilterCategory(cat.key)}
            className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs rounded-full transition ${
              filterCategory === cat.key
                ? `${cat.badgeBg} ${cat.badgeText} font-medium ring-1 ring-current/30`
                : `${styles.appBg} ${styles.cardTextMuted} hover:${styles.sidebarHoverBg}`
            }`}
          >
            <cat.Icon size={12} />
            {t(cat.labelKey)}
          </button>
        ))}
      </div>

      {/* 状态 + 类型筛选 */}
      <div className="flex items-center gap-3">
        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}
          className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30`}
        >
          <option value="">{t("taskPanel.filterAllStatus")}</option>
          <option value="PENDING">{t("taskPanel.status.pending")}</option>
          <option value="RUNNING">{t("taskPanel.status.running")}</option>
          <option value="SUCCEEDED">{t("taskPanel.status.succeeded")}</option>
          <option value="FAILED">{t("taskPanel.status.failed")}</option>
          <option value="CANCELLED">{t("taskPanel.status.cancelled")}</option>
        </select>
        <input
          type="text"
          value={filterType}
          onChange={e => setFilterType(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter") setSearchType(filterType.trim()); }}
          placeholder={t("taskPanel.filter.typePlaceholder")}
          className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30 w-40`}
        />
        {taskTypes.length > 0 && (
          <select
            value={filterType}
            onChange={e => { setFilterType(e.target.value); setSearchType(e.target.value); }}
            className={`px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-lg ${styles.inputBg} ${styles.inputText} outline-none focus:ring-2 focus:ring-blue-500/30`}
          >
            <option value="">{t("taskPanel.filter.selectType")}</option>
            {taskTypes.map(t => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        )}
        <button
          onClick={() => setSearchType(filterType.trim())}
          className="flex items-center gap-1.5 px-4 py-1.5 text-sm rounded-lg transition"
          style={{ backgroundColor: "var(--accent)", color: "#fff" }}
        >
          <Search className="w-3.5 h-3.5" />
          {t("taskPanel.filter.searchBtn")}
        </button>
      </div>
    </div>
  );
}
