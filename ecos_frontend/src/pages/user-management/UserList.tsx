/**
 * UserList — 用户表格组件
 * 功能: 用户数据表格 + 操作按钮 + 空状态 + 批量操作(checkbox + 悬浮工具栏)
 * @license Apache-2.0
 */

import React from "react";
import { Edit3, Trash2, LogOut, AlertTriangle } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { fetchUserRoles } from "../../api";
import type { IamUser, IamRole } from "../../api";

interface UserListProps {
  users: IamUser[];
  loading: boolean;
  roles: IamRole[];
  orgMap: Record<string, string>;
  onEdit: (user: IamUser) => void;
  onDelete: (user: IamUser) => void;
  onForceLogout: (user: IamUser) => void;
  onRowClick: (user: IamUser) => void;
  onToggleStatus: (userId: string, currentStatus: string) => void;
  onBatchEnable: (userIds: string[]) => void;
  onBatchDisable: (userIds: string[]) => void;
  onBatchDelete: (userIds: string[]) => void;
}

// ── RoleTags (lazy-loads user roles for display) ──
function RoleTags({ userId, roles }: { userId: string; roles: IamRole[] }) {
  const [roleIds, setRoleIds] = React.useState<string[] | null>(null);
  React.useEffect(() => {
    fetchUserRoles(userId).then(setRoleIds).catch(() => setRoleIds([]));
  }, [userId]);
  if (roleIds === null) return <span className="text-xs opacity-30">…</span>;
  const labels = roleIds.map(id => roles.find(r => r.roleId === id)?.roleName).filter(Boolean) as string[];
  if (!labels.length) return <span className="text-xs opacity-30">-</span>;
  return (
    <div className="flex flex-wrap gap-1">
      {labels.slice(0, 3).map((n, i) => (
        <span key={i} className="text-[10px] bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 px-1.5 py-0.5 rounded">{n}</span>
      ))}
      {labels.length > 3 && <span className="text-[10px] opacity-40">+{labels.length - 3}</span>}
    </div>
  );
}

// ── Batch Confirm Dialog (inline) ──────────────────────────
function BatchConfirmDialog({
  title,
  message,
  onConfirm,
  onCancel,
}: {
  title: string;
  message: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const { styles } = useTheme();
  const { locale } = useLanguage();
  const isZh = locale === "zh";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          <AlertTriangle className="w-4 h-4 text-red-500" />
          <h3 className={`text-base font-bold ${styles.cardText}`}>{title}</h3>
        </div>
        <p className={`text-sm mb-5 ${styles.cardTextMuted}`}>{message}</p>
        <div className="flex gap-2 justify-end">
          <button onClick={onCancel}
            className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={onConfirm}
            className="px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700">
            {isZh ? "确认删除" : "Confirm Delete"}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function UserList({
  users,
  loading,
  roles,
  orgMap,
  onEdit,
  onDelete,
  onForceLogout,
  onRowClick,
  onToggleStatus,
  onBatchEnable,
  onBatchDisable,
  onBatchDelete,
}: UserListProps) {
  const { locale, t } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  // ── Batch selection state ──────────────────────────────
  const [selectedIds, setSelectedIds] = React.useState<Set<string>>(new Set());
  const [showBatchConfirm, setShowBatchConfirm] = React.useState(false);

  // Clear selection when users change
  React.useEffect(() => {
    setSelectedIds(new Set());
  }, [users]);

  const allSelected = users.length > 0 && selectedIds.size === users.length;
  const someSelected = selectedIds.size > 0;

  function toggleSelect(id: string) {
    setSelectedIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(users.map(u => u.userId)));
    }
  }

  function handleRowClick(e: React.MouseEvent, user: IamUser) {
    // Don't trigger detail drawer when clicking checkbox or actions
    if ((e.target as HTMLElement).closest('input[type="checkbox"], button')) return;
    onRowClick(user);
  }

  function handleBatchDelete() {
    onBatchDelete(Array.from(selectedIds));
    setShowBatchConfirm(false);
    setSelectedIds(new Set());
  }

  const selectedArr = Array.from(selectedIds);

  const th = "text-left px-3 py-2 text-[11px] font-semibold uppercase tracking-wider opacity-60 border-b border-gray-200 dark:border-gray-700/30";
  const td = "px-3 py-2 text-[13px] border-b border-gray-100 dark:border-gray-700/20";
  const chkCol = "px-3 py-2 border-b border-gray-100 dark:border-gray-700/20 text-center";
  const chkTh = "text-center px-3 py-2 text-[11px] font-semibold uppercase tracking-wider opacity-60 border-b border-gray-200 dark:border-gray-700/30";

  // Loading skeleton
  if (loading) {
    return (
      <div className="overflow-auto flex-1">
        <table className="w-full border-collapse">
          <thead>
            <tr>
              <th className={chkTh}><div className="w-3.5 h-3.5 mx-auto" /></th>
              {[isZh ? "用户名" : "Username", isZh ? "姓名" : "Name", isZh ? "邮箱" : "Email", isZh ? "组织" : "Org", isZh ? "角色" : "Roles", isZh ? "状态" : "Status", isZh ? "操作" : "Actions"].map((h, i) => (
                <th key={i} className={th}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: 5 }).map((_, i) => (
              <tr key={i}>
                <td className="px-3 py-3 border-b border-gray-100 dark:border-gray-700/20">
                  <div className="h-3.5 w-3.5 mx-auto bg-gray-200 dark:bg-gray-700 rounded animate-pulse" />
                </td>
                {Array.from({ length: 7 }).map((_, j) => (
                  <td key={j} className="px-3 py-3 border-b border-gray-100 dark:border-gray-700/20">
                    <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse" style={{ width: `${60 + Math.random() * 40}%` }} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  // Empty state
  if (users.length === 0) {
    return null; // Parent handles empty state
  }

  return (
    <div className="overflow-auto flex-1 relative">
      <table className="w-full border-collapse">
        <thead>
          <tr>
            <th className={chkTh}>
              <input
                type="checkbox"
                checked={allSelected}
                onChange={toggleSelectAll}
                className="w-3.5 h-3.5 rounded accent-indigo-500 cursor-pointer"
              />
            </th>
            <th className={th}>{isZh ? "用户名" : "Username"}</th>
            <th className={th}>{isZh ? "姓名" : "Name"}</th>
            <th className={th}>{isZh ? "邮箱" : "Email"}</th>
            <th className={th}>{isZh ? "组织" : "Org"}</th>
            <th className={th}>{isZh ? "角色" : "Roles"}</th>
            <th className={th}>{isZh ? "状态" : "Status"}</th>
            <th className={th}>{isZh ? "操作" : "Actions"}</th>
          </tr>
        </thead>
        <tbody>
          {users.map(u => (
            <tr key={u.userId} className="hover:bg-gray-50 dark:hover:bg-gray-800/20 cursor-pointer"
              onClick={(e) => handleRowClick(e, u)}>
              <td className={chkCol} onClick={e => e.stopPropagation()}>
                <input
                  type="checkbox"
                  checked={selectedIds.has(u.userId)}
                  onChange={() => toggleSelect(u.userId)}
                  className="w-3.5 h-3.5 rounded accent-indigo-500 cursor-pointer"
                />
              </td>
              <td className={td}><span className="font-medium">{u.username}</span></td>
              <td className={td}>{u.realName || "-"}</td>
              <td className={td}><span className="text-xs opacity-70">{u.email || "-"}</span></td>
              <td className={td}><span className="text-xs">{orgMap[u.orgId || ""] || (u as any).orgName || u.orgId || "-"}</span></td>
              <td className={td}><RoleTags userId={u.userId} roles={roles} /></td>
              <td className={td} onClick={e => e.stopPropagation()}>
                <button onClick={() => onToggleStatus(u.userId, u.status)}
                  className={`px-2 py-0.5 rounded text-xs font-medium cursor-pointer border transition-colors ${
                    u.status === "ACTIVE"
                      ? "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400 border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/30"
                      : "bg-red-50 dark:bg-red-900/20 text-red-500 dark:text-red-400 border-red-200 dark:border-red-800 hover:bg-red-100 dark:hover:bg-red-900/30"
                  }`}>
                  ● {u.status === "ACTIVE" ? (isZh ? "活跃" : "Active") : (isZh ? "禁用" : "Disabled")}
                </button>
              </td>
              <td className={td} onClick={e => e.stopPropagation()}>
                <div className="flex gap-1">
                  <button onClick={() => onEdit(u)}
                    className="text-indigo-500 hover:text-indigo-700 p-1"><Edit3 size={14} /></button>
                  <button onClick={() => onForceLogout(u)}
                    className="text-amber-500 hover:text-amber-700 p-1" title={isZh ? "强制下线" : "Force Logout"}>
                    <LogOut size={14} /></button>
                  <button onClick={() => onDelete(u)}
                    className="text-red-500 hover:text-red-700 p-1"><Trash2 size={14} /></button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* ── Batch toolbar (floating at bottom) ─────────────── */}
      {someSelected && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-30 flex items-center gap-3 px-4 py-2.5 rounded-lg shadow-xl border bg-white dark:bg-gray-900 border-indigo-200 dark:border-indigo-800">
          <span className="text-xs font-medium opacity-70">
            {t("user.batch.selected").replace("{count}", String(selectedIds.size))}
          </span>
          <div className="w-px h-5 bg-gray-300 dark:bg-gray-700" />
          <button
            onClick={() => { onBatchEnable(selectedArr); setSelectedIds(new Set()); }}
            className="px-3 py-1 rounded text-xs font-medium bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400 border border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors"
          >
            {t("user.batch.enable")}
          </button>
          <button
            onClick={() => { onBatchDisable(selectedArr); setSelectedIds(new Set()); }}
            className="px-3 py-1 rounded text-xs font-medium bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400 border border-amber-200 dark:border-amber-800 hover:bg-amber-100 dark:hover:bg-amber-900/30 transition-colors"
          >
            {t("user.batch.disable")}
          </button>
          <button
            onClick={() => setShowBatchConfirm(true)}
            className="px-3 py-1 rounded text-xs font-medium bg-red-50 dark:bg-red-900/20 text-red-500 dark:text-red-400 border border-red-200 dark:border-red-800 hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
          >
            {t("user.batch.delete")}
          </button>
          <div className="w-px h-5 bg-gray-300 dark:bg-gray-700" />
          <button
            onClick={() => setSelectedIds(new Set())}
            className="text-xs opacity-50 hover:opacity-80 px-1"
          >
            {isZh ? "取消选择" : "Deselect"}
          </button>
        </div>
      )}

      {/* ── Batch delete confirm ──────────────────────────── */}
      {showBatchConfirm && (
        <BatchConfirmDialog
          title={isZh ? "确认批量删除" : "Confirm Batch Delete"}
          message={(isZh ? `确定删除 ${selectedIds.size} 个用户？此操作不可撤销` : `Delete ${selectedIds.size} users? This action is irreversible.`)}
          onConfirm={handleBatchDelete}
          onCancel={() => setShowBatchConfirm(false)}
        />
      )}
    </div>
  );
}
