/**
 * UserRoleBinding — 角色多选绑定组件
 * 功能: 角色多选列表，支持搜索过滤、全选/取消、计数显示
 * @license Apache-2.0
 */

import React, { useState, useMemo } from "react";
import { Search, CheckSquare, Square } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { IamRole } from "../../api";

interface UserRoleBindingProps {
  allRoles: IamRole[];
  selectedRoleIds: string[];
  onToggle: (roleId: string) => void;
  onSelectAll: () => void;
  onClearAll: () => void;
  loading?: boolean;
}

export default function UserRoleBinding({
  allRoles,
  selectedRoleIds,
  onToggle,
  onSelectAll,
  onClearAll,
  loading,
}: UserRoleBindingProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [roleSearch, setRoleSearch] = useState("");

  const filteredRoles = useMemo(() => {
    if (!roleSearch.trim()) return allRoles;
    const q = roleSearch.toLowerCase();
    return allRoles.filter(
      (r) =>
        r.roleName.toLowerCase().includes(q) ||
        r.roleCode.toLowerCase().includes(q)
    );
  }, [allRoles, roleSearch]);

  const allSelected =
    allRoles.length > 0 && selectedRoleIds.length === allRoles.length;

  if (loading) {
    return (
      <div className="space-y-2 py-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div
            key={i}
            className="h-8 rounded bg-gray-100 dark:bg-gray-800 animate-pulse"
          />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* Toolbar */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 w-3 h-3 opacity-40" />
          <input
            value={roleSearch}
            onChange={(e) => setRoleSearch(e.target.value)}
            className={`w-full pl-7 pr-2 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            placeholder={isZh ? "搜索角色…" : "Search roles…"}
          />
        </div>
        <button
          onClick={onSelectAll}
          className={`text-xs px-2 py-1 rounded border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}
        >
          <CheckSquare size={12} className="inline mr-1" />
          {isZh ? "全选" : "All"}
        </button>
        <button
          onClick={onClearAll}
          className={`text-xs px-2 py-1 rounded border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}
        >
          <Square size={12} className="inline mr-1" />
          {isZh ? "清空" : "Clear"}
        </button>
      </div>

      {/* Selected count */}
      <div className={`text-xs ${styles.cardTextMuted}`}>
        {isZh ? "已选" : "Selected"}:{" "}
        <span className="font-semibold">{selectedRoleIds.length}</span>
        {allRoles.length > 0 && (
          <span className="opacity-50">
            {" "}
            / {allRoles.length}
          </span>
        )}
      </div>

      {/* Role list */}
      <div className="max-h-48 overflow-y-auto border rounded p-1.5 space-y-1">
        {filteredRoles.map((r) => {
          const isSelected = selectedRoleIds.includes(r.roleId);
          return (
            <label
              key={r.roleId}
              className={`flex items-center gap-2 px-2.5 py-2 rounded text-xs cursor-pointer transition-colors ${
                isSelected
                  ? "bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300"
                  : "hover:bg-gray-50 dark:hover:bg-gray-800/30"
              }`}
            >
              <input
                type="checkbox"
                checked={isSelected}
                onChange={() => onToggle(r.roleId)}
                className="rounded shrink-0"
              />
              <div className="flex-1 min-w-0">
                <div className="font-medium truncate">{r.roleName}</div>
                <div className="text-[10px] opacity-50 truncate">
                  {r.roleCode} · {r.roleType === "SYSTEM" ? (isZh ? "系统" : "System") : (isZh ? "自定义" : "Custom")}
                </div>
              </div>
              {r.description && (
                <span className="text-[10px] opacity-40 max-w-[120px] truncate hidden sm:inline">
                  {r.description}
                </span>
              )}
            </label>
          );
        })}

        {filteredRoles.length === 0 && (
          <div className="text-center py-6 text-xs opacity-40">
            {isZh ? "暂无匹配角色" : "No matching roles"}
          </div>
        )}
      </div>
    </div>
  );
}
