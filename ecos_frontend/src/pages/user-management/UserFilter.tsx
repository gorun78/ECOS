/**
 * UserFilter — 用户筛选栏组件
 * 功能: 角色下拉筛选 + 状态下拉筛选 + 搜索框 + CSV导入/导出按钮
 * @license Apache-2.0
 */

import React from "react";
import { Search, Upload, Download } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { IamRole } from "../../api";

export interface UserFilterValues {
  keyword: string;
  status: string;
  roleId: string;
}

interface UserFilterProps {
  search: string;
  onSearchChange: (v: string) => void;
  onSearch: () => void;
  statusFilter: string;
  onStatusChange: (v: string) => void;
  roleFilter: string;
  onRoleChange: (v: string) => void;
  roles: IamRole[];
  onImportClick: () => void;
  onExportClick: () => void;
  totalCount: number;
  loading?: boolean;
}

export default function UserFilter({
  search,
  onSearchChange,
  onSearch,
  statusFilter,
  onStatusChange,
  roleFilter,
  onRoleChange,
  roles,
  onImportClick,
  onExportClick,
  totalCount,
  loading,
}: UserFilterProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") onSearch();
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      {/* Search box */}
      <div className="relative flex-1 max-w-sm">
        <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 opacity-40" />
        <input
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          onKeyDown={handleKeyDown}
          className={`w-full pl-8 pr-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          placeholder={t("platform.user.filter.searchPh")}
        />
      </div>

      {/* Status filter */}
      <select
        value={statusFilter}
        onChange={(e) => { onStatusChange(e.target.value); }}
        className={`px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
      >
        <option value="">{t("platform.user.filter.allStatus")}</option>
        <option value="ACTIVE">{t("platform.user.filter.statusActive")}</option>
        <option value="DISABLED">{t("platform.user.filter.statusLocked")}</option>
      </select>

      {/* Role filter */}
      <select
        value={roleFilter}
        onChange={(e) => { onRoleChange(e.target.value); }}
        className={`px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
      >
        <option value="">{t("platform.user.filter.allRoles")}</option>
        {roles.map((r) => (
          <option key={r.roleId} value={r.roleId}>
            {r.roleName}
          </option>
        ))}
      </select>

      {/* Search button */}
      <button
        onClick={onSearch}
        disabled={loading}
        className={`px-3 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}
      >
        <Search size={14} />
        {t("platform.user.filter.search")}
      </button>

      {/* CSV Import */}
      <button
        type="button"
        onClick={onImportClick}
        className={`flex items-center gap-1 px-3 py-2 rounded text-xs border ${styles.cardBorder} ${styles.sidebarText} ${styles.sidebarHoverBg}`}
      >
        <Upload size={14} />
        {t("platform.user.filter.csvImport")}
      </button>

      {/* CSV Export */}
      <button
        type="button"
        onClick={onExportClick}
        className={`flex items-center gap-1 px-3 py-2 rounded text-xs border ${styles.cardBorder} ${styles.sidebarText} ${styles.sidebarHoverBg}`}
      >
        <Download size={14} />
        {t("platform.user.filter.csvExport")}
      </button>

      {/* Total count */}
      <span className={`text-xs ml-auto opacity-60 ${styles.cardTextMuted}`}>
        {t("platform.user.filter.total", { n: totalCount })}
      </span>
    </div>
  );
}
