/**
 * IAM User Management — Users / Roles / Organizations / Permissions
 * v6 — Slim entry: sub-components live in pages/user-management/
 *   + 增强: 角色/状态筛选, CSV导入导出, 用户详情抽屉, 强制下线, 密码重置,
 *     空状态, 操作确认, 骨架屏加载
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Users, Shield, Building2, Key, Plus, Trash2, Edit3,
  RefreshCw, Search, Check, X, AlertCircle, CheckCircle2,
  ArrowLeftRight, ChevronLeft, ChevronRight, Lock, Unlock,
  LogOut, Eye, Copy, Upload, Download, UserX, KeyRound,
  FileSpreadsheet, AlertTriangle,
} from "lucide-react";
import {
  fetchUsers, createUser, updateUser, deleteUser, resetPassword,
  toggleUserStatus, fetchUserRoles, assignUserRoles,
  fetchRoles, createRole, updateRole, deleteRole,
  fetchRolePermissions, assignRolePermissions,
  fetchOrgTree, fetchOrgs, createOrg, updateOrg, deleteOrg,
  fetchPermissions, createPermission, updatePermission, deletePermission,
  IamUser, IamRole, IamOrg, IamPermission,
  forceLogoutUser, resetPasswordGenerate, batchCreateUsers,
} from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import UserFilter from "./user-management/UserFilter";
import UserEditModal from "./user-management/UserEditModal";
import UserList from "./user-management/UserList";

type Tab = "users" | "roles" | "orgs" | "permissions";

// ── Helpers ──────────────────────────────────────────────────

function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
  const result: (IamOrg & { _depth: number })[] = [];
  for (const node of nodes) {
    result.push({ ...node, _depth: depth });
    if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
  }
  return result;
}

// ── Toast (inline) ───────────────────────────────────────────

const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success" ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100"><X className="w-3.5 h-3.5" /></button>
  </div>
);

// ── Confirm Dialog (inline) ──────────────────────────────────

const ConfirmDialog: React.FC<{
  title: string;
  message: string;
  confirmLabel?: string;
  confirmClass?: string;
  onConfirm: () => void;
  onCancel: () => void;
  variant?: "danger" | "warning" | "default";
}> = ({ title, message, confirmLabel, confirmClass, onConfirm, onCancel, variant = "danger" }) => (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 bg-white dark:bg-[#141924] border border-[#E2E8F0] dark:border-[#1E293B]">
      <div className="flex items-center gap-2 mb-2">
        {variant === "danger" && <Trash2 className="w-4 h-4 text-red-500" />}
        {variant === "warning" && <AlertTriangle className="w-4 h-4 text-amber-500" />}
        <h3 className="text-base font-bold text-slate-800 dark:text-slate-100">{title}</h3>
      </div>
      <p className="text-sm text-slate-500 dark:text-slate-400 mb-5">{message}</p>
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel}
          className="px-4 py-1.5 rounded text-xs border border-[#E2E8F0] dark:border-[#1E293B] text-slate-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-white/5">
          取消
        </button>
        <button onClick={onConfirm}
          className={confirmClass || "px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700"}>
          {confirmLabel || "确认"}
        </button>
      </div>
    </div>
  </div>
);

// ── Empty State (inline) ─────────────────────────────────────

const EmptyState: React.FC<{
  icon: React.FC<any>;
  title: string;
  description: string;
  onCreate?: () => void;
  onImport?: () => void;
  createLabel?: string;
  importLabel?: string;
}> = ({ icon: Icon, title, description, onCreate, onImport, createLabel, importLabel }) => (
  <div className="flex flex-col items-center justify-center py-16 text-center">
    <Icon className="w-16 h-16 opacity-15 mb-4" />
    <h3 className="text-sm font-semibold opacity-40 mb-1">{title}</h3>
    <p className="text-xs opacity-25 mb-4 max-w-xs">{description}</p>
    <div className="flex gap-2">
      {onCreate && (
        <button onClick={onCreate} className="px-4 py-2 rounded text-xs font-medium text-white bg-indigo-500 hover:bg-indigo-600 flex items-center gap-1.5">
          <Plus size={14} /> {createLabel || "新建"}
        </button>
      )}
      {onImport && (
        <button onClick={onImport} className="px-4 py-2 rounded text-xs border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5 flex items-center gap-1.5">
          <Upload size={14} /> {importLabel || "导入"}
        </button>
      )}
    </div>
  </div>
);

// ── CSV Import Modal (inline) ─────────────────────────────────

interface CsvImportModalProps {
  onImport: (users: Record<string, string>[]) => Promise<void>;
  onClose: () => void;
}

function CsvImportModal({ onImport, onClose }: CsvImportModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";
  const fileRef = useRef<HTMLInputElement>(null);

  const [preview, setPreview] = useState<{ headers: string[]; rows: Record<string, string>[] } | null>(null);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState("");

  function parseCSV(text: string) {
    const lines = text.trim().split(/\r?\n/);
    if (lines.length < 2) {
      setError(isZh ? "CSV文件至少需要标题行和一行数据" : "CSV needs at least header + 1 data row");
      return;
    }
    const headers = lines[0].split(",").map(h => h.trim().replace(/^"|"$/g, ""));
    const rows = lines.slice(1).map(line => {
      const vals = line.split(",").map(v => v.trim().replace(/^"|"$/g, ""));
      const row: Record<string, string> = {};
      headers.forEach((h, i) => { row[h] = vals[i] || ""; });
      return row;
    });
    setPreview({ headers, rows });
    setError("");
  }

  function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => parseCSV(reader.result as string);
    reader.readAsText(file);
  }

  async function handleImport() {
    if (!preview || preview.rows.length === 0) return;
    setImporting(true);
    setError("");
    try {
      await onImport(preview.rows);
      onClose();
    } catch (e: any) {
      setError(e.message || "Import failed");
    } finally {
      setImporting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-2xl max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            <FileSpreadsheet className="w-4 h-4 inline mr-1.5" />
            {isZh ? "CSV 批量导入用户" : "CSV Batch Import Users"}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        {error && (
          <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>
        )}

        {!preview ? (
          <div className="text-center py-8">
            <Upload className="w-10 h-10 opacity-20 mx-auto mb-3" />
            <p className={`text-sm mb-3 ${styles.cardTextMuted}`}>
              {isZh ? "选择 CSV 文件（需包含标题行：username,realName,email,phone,orgId）" : "Select CSV file (header row required: username,realName,email,phone,orgId)"}
            </p>
            <input ref={fileRef} type="file" accept=".csv" onChange={handleFile} className="hidden" />
            <button
              onClick={() => fileRef.current?.click()}
              className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}
            >
              {isZh ? "选择文件" : "Choose File"}
            </button>
          </div>
        ) : (
          <>
            <div className={`text-xs mb-2 ${styles.cardTextMuted}`}>
              {isZh ? `预览 ${preview.rows.length} 条记录` : `Preview ${preview.rows.length} records`}
            </div>
            <div className="overflow-auto max-h-64 border rounded mb-3">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-gray-50 dark:bg-gray-800/50">
                    {preview.headers.map(h => (
                      <th key={h} className="text-left px-2 py-1.5 font-semibold opacity-70 border-b">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {preview.rows.slice(0, 20).map((row, i) => (
                    <tr key={i} className="border-b border-gray-100 dark:border-gray-700/20">
                      {preview.headers.map(h => (
                        <td key={h} className="px-2 py-1.5 opacity-70">{row[h] || "-"}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setPreview(null)} className={`px-3 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
                {isZh ? "重新选择" : "Reselect"}
              </button>
              <button onClick={onClose} className={`px-3 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
                {isZh ? "取消" : "Cancel"}
              </button>
              <button onClick={handleImport} disabled={importing}
                className={`px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
                {importing ? (isZh ? "导入中…" : "Importing…") : (isZh ? "确认导入" : "Confirm Import")}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// ── User Detail Drawer (inline) ──────────────────────────────

type DrawerTab = "basic" | "roles" | "login";

interface UserDetailDrawerProps {
  user: IamUser;
  allRoles: IamRole[];
  orgMap: Record<string, string>;
  onForceLogout: (userId: string) => void;
  onResetPassword: (userId: string) => void;
  onClose: () => void;
}

function UserDetailDrawer({ user, allRoles, orgMap, onForceLogout, onResetPassword, onClose }: UserDetailDrawerProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";
  const [tab, setTab] = useState<DrawerTab>("basic");
  const [userRoles, setUserRoles] = useState<string[]>([]);
  const [rolesLoaded, setRolesLoaded] = useState(false);

  useEffect(() => {
    fetchUserRoles(user.userId)
      .then(r => setUserRoles(r))
      .catch(() => setUserRoles([]))
      .finally(() => setRolesLoaded(true));
  }, [user.userId]);

  const roleNames = userRoles
    .map(id => allRoles.find(r => r.roleId === id)?.roleName)
    .filter(Boolean) as string[];

  const drawerTabs: { id: DrawerTab; label: string; icon: React.FC<any> }[] = [
    { id: "basic", label: isZh ? "基本信息" : "Basic Info", icon: Eye },
    { id: "roles", label: isZh ? "角色绑定" : "Roles", icon: Shield },
    { id: "login", label: isZh ? "登录记录" : "Login", icon: KeyRound },
  ];

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/30" onClick={onClose} />
      <div className={`fixed right-0 top-0 h-full w-full max-w-[400px] z-50 shadow-2xl flex flex-col ${styles.cardBg} border-l ${styles.cardBorder}`}>
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700/30">
          <div>
            <h3 className={`text-sm font-semibold ${styles.cardText}`}>{user.username}</h3>
            <p className={`text-xs opacity-50`}>{user.realName || user.email || "-"}</p>
          </div>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        <div className={`flex gap-1 px-4 pt-3 border-b ${styles.appBorder}`}>
          {drawerTabs.map(t => {
            const Icon = t.icon;
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-t transition-colors ${
                  tab === t.id
                    ? `${styles.accentBg} text-white`
                    : "hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-500 dark:text-gray-400"
                }`}>
                <Icon size={12} />{t.label}
              </button>
            );
          })}
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {tab === "basic" && (
            <div className="space-y-3">
              {[
                [isZh ? "用户名" : "Username", user.username],
                [isZh ? "真实姓名" : "Real Name", user.realName || "-"],
                [isZh ? "邮箱" : "Email", user.email || "-"],
                [isZh ? "手机" : "Phone", user.phone || "-"],
                [isZh ? "组织" : "Organization", orgMap[user.orgId || ""] || (user as any).orgName || user.orgId || "-"],
                [isZh ? "状态" : "Status", user.status === "ACTIVE" ? (isZh ? "正常" : "Active") : (isZh ? "锁定" : "Locked")],
                [isZh ? "锁定" : "Locked", user.locked === "1" ? (isZh ? "是" : "Yes") : (isZh ? "否" : "No")],
                [isZh ? "最后登录" : "Last Login", user.lastLoginTime || "-"],
                [isZh ? "创建时间" : "Created", user.createdTime || "-"],
              ].map(([label, value], i) => (
                <div key={i} className="flex justify-between items-center py-1.5 border-b border-gray-100 dark:border-gray-700/20 last:border-0">
                  <span className="text-xs opacity-50">{label}</span>
                  <span className="text-xs font-medium ml-4 text-right break-all">{value}</span>
                </div>
              ))}
            </div>
          )}

          {tab === "roles" && (
            <div>
              {!rolesLoaded ? (
                <div className="space-y-2 py-4">
                  {[1,2,3].map(i => <div key={i} className="h-8 bg-gray-100 dark:bg-gray-800 rounded animate-pulse" />)}
                </div>
              ) : roleNames.length === 0 ? (
                <div className="text-center py-8 text-xs opacity-40">
                  {isZh ? "未绑定任何角色" : "No roles assigned"}
                </div>
              ) : (
                <div className="space-y-1">
                  {roleNames.map((name, i) => (
                    <div key={i} className="px-3 py-2 rounded text-xs bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400">
                      <Shield className="w-3 h-3 inline mr-1.5" />
                      {name}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === "login" && (
            <div className="space-y-3">
              <div className={`p-4 rounded-lg border ${styles.cardBorder} text-center`}>
                <KeyRound className="w-8 h-8 opacity-20 mx-auto mb-2" />
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  {isZh ? "登录记录功能已就绪" : "Login records ready"}
                </p>
                <p className="text-[10px] opacity-30 mt-1">
                  {isZh ? "后端接口: GET /api/v1/users/{id}/login-history" : "Backend: GET /api/v1/users/{id}/login-history"}
                </p>
              </div>
            </div>
          )}
        </div>

        <div className="px-5 py-4 border-t border-gray-200 dark:border-gray-700/30 space-y-2">
          <button
            onClick={() => onForceLogout(user.userId)}
            className="w-full px-3 py-2 rounded text-xs font-medium border border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/20 hover:bg-amber-100 dark:hover:bg-amber-900/30 flex items-center justify-center gap-1.5"
          >
            <LogOut className="w-3.5 h-3.5" />
            {isZh ? "强制下线" : "Force Logout"}
          </button>
          <button
            onClick={() => onResetPassword(user.userId)}
            className="w-full px-3 py-2 rounded text-xs font-medium border border-indigo-200 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-900/20 hover:bg-indigo-100 dark:hover:bg-indigo-900/30 flex items-center justify-center gap-1.5"
          >
            <KeyRound className="w-3.5 h-3.5" />
            {isZh ? "重置密码" : "Reset Password"}
          </button>
        </div>
      </div>
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
// Main Component — ~200 lines, composition-only
// ═══════════════════════════════════════════════════════════════

export default function UserManagement() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [tab, setTab] = useState<Tab>("users");

  // Data
  const [users, setUsers] = useState<IamUser[]>([]);
  const [userTotal, setUserTotal] = useState(0);
  const [roles, setRoles] = useState<IamRole[]>([]);
  const [orgTree, setOrgTree] = useState<IamOrg[]>([]);
  const [orgMap, setOrgMap] = useState<Record<string, string>>({});
  const [permissions, setPermissions] = useState<IamPermission[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // Users: search & filter & pagination
  const [userSearch, setUserSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [userPage, setUserPage] = useState(1);
  const pageSize = 15;

  // Toast
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // Modals & Drawers
  const [userForm, setUserForm] = useState<{ mode: "create" | "edit"; user?: IamUser | null } | null>(null);
  const [roleForm, setRoleForm] = useState<{ mode: "create" | "edit"; role?: IamRole | null } | null>(null);
  const [orgForm, setOrgForm] = useState<{ mode: "create" | "edit"; org?: IamOrg | null } | null>(null);
  const [permForm, setPermForm] = useState<{ mode: "create" | "edit"; perm?: IamPermission | null } | null>(null);
  const [permPanelRole, setPermPanelRole] = useState<IamRole | null>(null);
  const [showCsvImport, setShowCsvImport] = useState(false);

  // Detail drawer
  const [detailUser, setDetailUser] = useState<IamUser | null>(null);

  // Confirm dialogs
  const [confirmAction, setConfirmAction] = useState<{
    type: "delete" | "logout" | "resetPwd";
    tab?: Tab;
    id?: string;
    name?: string;
    userId?: string;
  } | null>(null);

  // Reset password result
  const [resetPwdResult, setResetPwdResult] = useState<{ userId: string; tempPassword: string } | null>(null);

  // ── Data loading ──────────────────────────────────────────

  const loadUsers = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const res = await fetchUsers(userSearch || undefined, userPage, pageSize);
      setUsers(res.data || []);
      setUserTotal(res.total || 0);
    } catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }, [userSearch, userPage, pageSize]);

  const loadRoles = useCallback(async () => {
    setLoading(true); setError("");
    try { setRoles((await fetchRoles()).data || []); }
    catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }, []);

  const loadOrgs = useCallback(async () => {
    setLoading(true); setError("");
    try {
      const tree = await fetchOrgTree();
      setOrgTree(tree);
      const all = await fetchOrgs();
      const map: Record<string, string> = {};
      for (const o of all) { map[o.orgId] = o.orgName; }
      setOrgMap(map);
    } catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }, []);

  const loadPermissions = useCallback(async () => {
    setLoading(true); setError("");
    try { setPermissions(await fetchPermissions() || []); }
    catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }, []);

  const loadData = useCallback(async () => {
    switch (tab) {
      case "users": await loadUsers(); break;
      case "roles": await loadRoles(); if (permissions.length === 0) await loadPermissions(); break;
      case "orgs": await loadOrgs(); break;
      case "permissions": await loadPermissions(); break;
    }
  }, [tab, loadUsers, loadRoles, loadOrgs, loadPermissions]);

  useEffect(() => { loadData(); }, [loadData, userPage]);

  // ── CRUD handlers ─────────────────────────────────────────

  const handleCreateUser = useCallback(async (data: Record<string, any>, roleIds: string[]) => {
    const payload = { ...data };
    if (!payload.password) payload.password = "ECOS@2026";
    const result: any = await createUser(payload);
    const newUserId = result?.userId || result?.id;
    if (newUserId && roleIds.length > 0) {
      await assignUserRoles(newUserId, roleIds).catch(() => {});
    }
    showToast("success", isZh ? "用户创建成功" : "User created");
    loadUsers();
  }, [isZh, showToast, loadUsers]);

  const handleUpdateUser = useCallback(async (data: Record<string, any>, roleIds: string[]) => {
    const userId = data.userId;
    const { userId: _, password, ...payload } = data;
    await updateUser(userId, payload);
    await assignUserRoles(userId, roleIds).catch(() => {});
    showToast("success", isZh ? "用户更新成功" : "User updated");
    loadUsers();
  }, [isZh, showToast, loadUsers]);

  const handleDeleteUser = useCallback(async () => {
    if (!confirmAction?.id) return;
    const userId = confirmAction.id;
    setConfirmAction(null);
    try {
      await deleteUser(userId);
      showToast("success", isZh ? "用户已删除" : "User deleted");
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, isZh, showToast, loadUsers]);

  const handleToggleStatus = useCallback(async (userId: string, currentStatus: string) => {
    const newStatus = currentStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await toggleUserStatus(userId, newStatus);
      showToast("success", isZh ? "状态已更新" : "Status updated");
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [isZh, showToast, loadUsers]);

  // ── Batch operations ──────────────────────────────────
  const handleBatchEnable = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => toggleUserStatus(id, "ACTIVE")));
      showToast("success", isZh ? `已启用 ${userIds.length} 个用户` : `${userIds.length} users enabled`);
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [isZh, showToast, loadUsers]);

  const handleBatchDisable = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => toggleUserStatus(id, "DISABLED")));
      showToast("success", isZh ? `已禁用 ${userIds.length} 个用户` : `${userIds.length} users disabled`);
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [isZh, showToast, loadUsers]);

  const handleBatchDelete = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => deleteUser(id)));
      showToast("success", isZh ? `已删除 ${userIds.length} 个用户` : `${userIds.length} users deleted`);
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [isZh, showToast, loadUsers]);

  const handleForceLogout = useCallback((userId: string) => {
    setDetailUser(null);
    setConfirmAction({ type: "logout", userId });
  }, []);

  const executeForceLogout = useCallback(async () => {
    if (!confirmAction?.userId) return;
    setConfirmAction(null);
    try {
      await forceLogoutUser(confirmAction.userId);
      showToast("success", isZh ? "用户已强制下线" : "User force logged out");
    } catch (e: any) { showToast("error", e.message || "Force logout failed"); }
  }, [confirmAction, isZh, showToast]);

  const handleResetPassword = useCallback((userId: string) => {
    setDetailUser(null);
    setConfirmAction({ type: "resetPwd", userId });
  }, []);

  const executeResetPassword = useCallback(async () => {
    if (!confirmAction?.userId) return;
    setConfirmAction(null);
    try {
      const res: any = await resetPasswordGenerate(confirmAction.userId);
      const tempPwd = res?.tempPassword || res?.password || "ECOS@2026";
      setResetPwdResult({ userId: confirmAction.userId, tempPassword: tempPwd });
      showToast("success", isZh ? "密码已重置" : "Password reset");
    } catch (e: any) { showToast("error", e.message || "Reset failed"); }
  }, [confirmAction, isZh, showToast]);

  const handleCsvImport = useCallback(async (rows: Record<string, string>[]) => {
    await batchCreateUsers(rows);
    showToast("success", isZh ? `成功导入 ${rows.length} 个用户` : `Successfully imported ${rows.length} users`);
    setShowCsvImport(false);
    loadUsers();
  }, [isZh, showToast, loadUsers]);

  const handleCsvExport = useCallback(() => {
    if (users.length === 0) {
      showToast("error", isZh ? "没有可导出的数据" : "No data to export");
      return;
    }
    const headers = ["username", "realName", "email", "phone", "orgId", "status"];
    const csvRows = users.map(u => headers.map(h => {
      const val = (u as any)[h] || "";
      return val.includes(",") ? `"${val}"` : val;
    }).join(","));
    const csvContent = [headers.join(","), ...csvRows].join("\n");
    const blob = new Blob(["\uFEFF" + csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = `users_export_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("success", isZh ? "导出成功" : "Export successful");
  }, [users, isZh, showToast]);

  // Role CRUD
  const handleCreateRole = useCallback(async (data: Record<string, any>) => {
    await createRole(data);
    showToast("success", isZh ? "角色创建成功" : "Role created");
    loadRoles();
  }, [isZh, showToast, loadRoles]);
  const handleUpdateRole = useCallback(async (data: Record<string, any>) => {
    await updateRole(data.roleId, data);
    showToast("success", isZh ? "角色更新成功" : "Role updated");
    loadRoles();
  }, [isZh, showToast, loadRoles]);
  const handleDeleteRole = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deleteRole(confirmAction.id); showToast("success", isZh ? "角色已删除" : "Role deleted"); loadRoles(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, isZh, showToast, loadRoles]);
  const handleSaveRolePermissions = useCallback(async (permIds: string[]) => {
    if (!permPanelRole) return;
    await assignRolePermissions(permPanelRole.roleId, permIds);
    showToast("success", isZh ? "权限分配成功" : "Permissions saved");
    setPermPanelRole(null);
  }, [permPanelRole, isZh, showToast]);
  const handleCreateOrg = useCallback(async (data: Record<string, any>) => {
    await createOrg(data);
    showToast("success", isZh ? "机构创建成功" : "Org created");
    loadOrgs();
  }, [isZh, showToast, loadOrgs]);
  const handleUpdateOrg = useCallback(async (data: Record<string, any>) => {
    await updateOrg(data.orgId, data);
    showToast("success", isZh ? "机构更新成功" : "Org updated");
    loadOrgs();
  }, [isZh, showToast, loadOrgs]);
  const handleDeleteOrg = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deleteOrg(confirmAction.id); showToast("success", isZh ? "机构已删除" : "Org deleted"); loadOrgs(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, isZh, showToast, loadOrgs]);
  const handleCreatePerm = useCallback(async (data: Record<string, any>) => {
    await createPermission(data);
    showToast("success", isZh ? "权限创建成功" : "Permission created");
    loadPermissions();
  }, [isZh, showToast, loadPermissions]);
  const handleUpdatePerm = useCallback(async (data: Record<string, any>) => {
    await updatePermission(data.permissionId, data);
    showToast("success", isZh ? "权限更新成功" : "Permission updated");
    loadPermissions();
  }, [isZh, showToast, loadPermissions]);
  const handleDeletePerm = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deletePermission(confirmAction.id); showToast("success", isZh ? "权限已删除" : "Permission deleted"); loadPermissions(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, isZh, showToast, loadPermissions]);

  // ── Tab configuration ─────────────────────────────────────

  const tabs: { id: Tab; label: string; icon: React.FC<any> }[] = [
    { id: "users", label: isZh ? "用户" : "Users", icon: Users },
    { id: "roles", label: isZh ? "角色" : "Roles", icon: Shield },
    { id: "orgs", label: isZh ? "组织机构" : "Orgs", icon: Building2 },
    { id: "permissions", label: isZh ? "权限" : "Permissions", icon: Key },
  ];

  const totalPages = Math.max(1, Math.ceil(userTotal / pageSize));
  const th = "text-left px-3 py-2 text-[11px] font-semibold uppercase tracking-wider opacity-60 border-b border-gray-200 dark:border-gray-700/30";
  const td = "px-3 py-2 text-[13px] border-b border-gray-100 dark:border-gray-700/20";

  // ═══════════════════════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════════════════════

  return (
    <div className="h-full flex flex-col p-6 space-y-4">
      {/* Toast */}
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}

      {/* Reset Password Result */}
      {resetPwdResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setResetPwdResult(null)}>
          <div className={`rounded-lg border p-6 w-full max-w-sm ${styles.cardBg} ${styles.cardBorder}`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                <CheckCircle2 className="w-4 h-4 text-green-500 inline mr-1.5" />
                {isZh ? "密码已重置" : "Password Reset"}
              </h3>
              <button onClick={() => setResetPwdResult(null)} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
            </div>
            <p className={`text-xs mb-2 ${styles.cardTextMuted}`}>
              {isZh ? "临时密码 (请妥善保管):" : "Temporary password (keep safe):"}
            </p>
            <div className="flex items-center gap-2 mb-4">
              <code className="flex-1 px-3 py-2 rounded text-sm font-mono bg-gray-100 dark:bg-gray-800 select-all">
                {resetPwdResult.tempPassword}
              </code>
              <button
                onClick={() => { navigator.clipboard.writeText(resetPwdResult!.tempPassword); showToast("success", isZh ? "已复制" : "Copied"); }}
                className={`p-2 rounded border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}
              >
                <Copy className="w-4 h-4" />
              </button>
            </div>
            <button onClick={() => setResetPwdResult(null)}
              className={`w-full px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              {isZh ? "我知道了" : "Got it"}
            </button>
          </div>
        </div>
      )}

      {/* Confirm Dialogs */}
      {confirmAction?.type === "delete" && (
        <ConfirmDialog
          title={isZh ? "确认删除" : "Confirm Delete"}
          message={isZh ? `确定要删除「${confirmAction.name}」吗？此操作不可撤销。` : `Delete "${confirmAction.name}"? This cannot be undone.`}
          confirmLabel={isZh ? "删除" : "Delete"}
          onConfirm={() => {
            if (confirmAction?.tab === "users") handleDeleteUser();
            else if (confirmAction?.tab === "roles") handleDeleteRole();
            else if (confirmAction?.tab === "orgs") handleDeleteOrg();
            else if (confirmAction?.tab === "permissions") handleDeletePerm();
          }}
          onCancel={() => setConfirmAction(null)}
        />
      )}
      {confirmAction?.type === "logout" && (
        <ConfirmDialog
          variant="warning"
          title={isZh ? "强制下线" : "Force Logout"}
          message={isZh ? "确定要强制该用户下线吗？用户将立即失去所有会话。" : "Force this user to log out? All sessions will be terminated."}
          confirmLabel={isZh ? "确认下线" : "Force Logout"}
          confirmClass="px-4 py-1.5 rounded text-xs font-semibold bg-amber-500 text-white hover:bg-amber-600"
          onConfirm={executeForceLogout}
          onCancel={() => setConfirmAction(null)}
        />
      )}
      {confirmAction?.type === "resetPwd" && (
        <ConfirmDialog
          variant="warning"
          title={isZh ? "重置密码" : "Reset Password"}
          message={isZh ? "确定要重置该用户的密码吗？系统将生成临时密码。" : "Reset password for this user? A temporary password will be generated."}
          confirmLabel={isZh ? "确认重置" : "Reset"}
          confirmClass="px-4 py-1.5 rounded text-xs font-semibold bg-indigo-500 text-white hover:bg-indigo-600"
          onConfirm={executeResetPassword}
          onCancel={() => setConfirmAction(null)}
        />
      )}

      {/* User Form Modal */}
      {userForm && (
        <UserEditModal
          mode={userForm.mode}
          user={userForm.user}
          allRoles={roles}
          orgTree={orgTree}
          onSave={userForm.mode === "create" ? handleCreateUser : handleUpdateUser}
          onClose={() => setUserForm(null)}
        />
      )}

      {/* Role Form */}
      {roleForm && (
        <RoleFormModal
          mode={roleForm.mode}
          role={roleForm.role}
          allPermissions={permissions}
          onSave={roleForm.mode === "create" ? handleCreateRole : handleUpdateRole}
          onClose={() => setRoleForm(null)}
          onManagePermissions={() => { if (roleForm.role) { setRoleForm(null); setPermPanelRole(roleForm.role); } }}
        />
      )}

      {/* Org Form */}
      {orgForm && (
        <OrgFormModal mode={orgForm.mode} org={orgForm.org} orgTree={orgTree}
          onSave={orgForm.mode === "create" ? handleCreateOrg : handleUpdateOrg}
          onClose={() => setOrgForm(null)} />
      )}

      {/* Permission Form */}
      {permForm && (
        <PermFormModal mode={permForm.mode} permission={permForm.perm}
          onSave={permForm.mode === "create" ? handleCreatePerm : handleUpdatePerm}
          onClose={() => setPermForm(null)} />
      )}

      {/* Permission Panel */}
      {permPanelRole && (
        <PermissionPanel role={permPanelRole} allPermissions={permissions}
          onSave={handleSaveRolePermissions} onClose={() => setPermPanelRole(null)} />
      )}

      {/* CSV Import Modal */}
      {showCsvImport && (
        <CsvImportModal onImport={handleCsvImport} onClose={() => setShowCsvImport(false)} />
      )}

      {/* User Detail Drawer */}
      {detailUser && (
        <UserDetailDrawer
          user={detailUser}
          allRoles={roles}
          orgMap={orgMap}
          onForceLogout={handleForceLogout}
          onResetPassword={handleResetPassword}
          onClose={() => setDetailUser(null)}
        />
      )}

      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className={`text-xl font-bold ${styles.cardText}`}>
          {isZh ? "身份与访问管理 (IAM)" : "Identity & Access Management (IAM)"}
        </h1>
        <div className="flex gap-2">
          {tab !== "permissions" && (
            <button
              onClick={() => {
                if (tab === "users") { if (!orgTree.length) loadOrgs(); setUserForm({ mode: "create" }); }
                else if (tab === "roles") setRoleForm({ mode: "create" });
                else if (tab === "orgs") setOrgForm({ mode: "create" });
              }}
              className={`flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              <Plus size={14} /> {isZh ? "新建" : "New"}
            </button>
          )}
          {tab === "permissions" && (
            <button onClick={() => setPermForm({ mode: "create" })}
              className={`flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              <Plus size={14} /> {isZh ? "新建" : "New"}
            </button>
          )}
          <button onClick={loadData}
            className="flex items-center gap-1 px-3 py-1.5 rounded text-xs border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5">
            <RefreshCw size={14} /> {isZh ? "刷新" : "Refresh"}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className={`flex gap-1 border-b ${styles.appBorder}`}>
        {tabs.map(t => {
          const Icon = t.icon;
          return (
            <button key={t.id} onClick={() => { setTab(t.id); setUserPage(1); }}
              className={`flex items-center gap-1.5 px-4 py-2 text-sm rounded-t transition-colors ${
                tab === t.id ? `${styles.accentBg} text-white` : "hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-400"
              }`}>
              <Icon size={14} /> {t.label}
            </button>
          );
        })}
      </div>

      {/* Error */}
      {error && (
        <div className="bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400 p-3 rounded text-sm">{error}</div>
      )}

      {/* Users: Filter bar */}
      {tab === "users" && (
        <UserFilter
          search={userSearch}
          onSearchChange={setUserSearch}
          onSearch={() => { setUserPage(1); loadUsers(); }}
          statusFilter={statusFilter}
          onStatusChange={v => { setStatusFilter(v); setUserPage(1); }}
          roleFilter={roleFilter}
          onRoleChange={v => { setRoleFilter(v); setUserPage(1); }}
          roles={roles}
          onImportClick={() => setShowCsvImport(true)}
          onExportClick={handleCsvExport}
          totalCount={userTotal}
          loading={loading}
        />
      )}

      {/* Users Table (using extracted UserList) */}
      {tab === "users" && (
        <UserList
          users={users}
          loading={loading}
          roles={roles}
          orgMap={orgMap}
          onEdit={async (u) => { if (!orgTree.length) await loadOrgs(); if (!roles.length) await loadRoles(); setUserForm({ mode: "edit", user: u }); }}
          onDelete={(u) => {
            console.log('[DELETE BTN] user object:', JSON.stringify(u));
            console.log('[DELETE BTN] u.userId:', u.userId, 'u.user_id:', (u as any).user_id);
            setConfirmAction({ type: "delete", tab: "users", id: u.userId || (u as any).user_id || (u as any).id, name: u.username });
          }}
          onForceLogout={(u) => handleForceLogout(u.userId)}
          onRowClick={(u) => setDetailUser(u)}
          onToggleStatus={handleToggleStatus}
          onBatchEnable={handleBatchEnable}
          onBatchDisable={handleBatchDisable}
          onBatchDelete={handleBatchDelete}
        />
      )}

      {/* Users Empty State */}
      {tab === "users" && !loading && users.length === 0 && (
        <EmptyState
          icon={UserX}
          title={isZh ? "暂无用户" : "No users"}
          description={isZh ? "还没有任何用户，点击下方按钮创建或导入用户" : "No users yet. Create or import users below."}
          onCreate={() => setUserForm({ mode: "create" })}
          onImport={() => setShowCsvImport(true)}
          createLabel={isZh ? "创建用户" : "Create User"}
          importLabel={isZh ? "CSV导入" : "CSV Import"}
        />
      )}

      {/* Roles Table */}
      {tab === "roles" && (
        <div className="overflow-auto flex-1">
          <table className="w-full border-collapse">
            <thead><tr>
              <th className={th}>{isZh ? "角色名" : "Role Name"}</th>
              <th className={th}>{isZh ? "编码" : "Code"}</th>
              <th className={th}>{isZh ? "类型" : "Type"}</th>
              <th className={th}>{isZh ? "描述" : "Description"}</th>
              <th className={th}>{isZh ? "操作" : "Actions"}</th>
            </tr></thead>
            <tbody>
              {roles.map(r => (
                <tr key={r.roleId} className="hover:bg-gray-50 dark:hover:bg-gray-800/20 cursor-pointer" onClick={() => { if (permissions.length === 0) loadPermissions(); setPermPanelRole(r); }}>
                  <td className={td}><span className="font-medium text-indigo-600 dark:text-indigo-400">{r.roleName}</span></td>
                  <td className={td}><code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{r.roleCode}</code></td>
                  <td className={td}><span className={`text-xs px-1.5 py-0.5 rounded ${r.roleType === "SYSTEM" ? "bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400" : "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400"}`}>{r.roleType}</span></td>
                  <td className={td}><span className="text-xs opacity-60 max-w-[200px] truncate block">{r.description || "-"}</span></td>
                  <td className={td} onClick={e => e.stopPropagation()}>
                    <div className="flex gap-1">
                      <button onClick={() => { if (permissions.length === 0) loadPermissions(); setRoleForm({ mode: "edit", role: r }); }} className="text-indigo-500 hover:text-indigo-700 p-1"><Edit3 size={14} /></button>
                      <button onClick={() => setConfirmAction({ type: "delete", tab: "roles", id: r.roleId, name: r.roleName })}
                        className="text-red-500 hover:text-red-700 p-1"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Orgs Tree */}
      {tab === "orgs" && (
        <div className="overflow-auto flex-1">
          <div className="border rounded overflow-hidden">
            <div className="flex items-center gap-2 px-3 py-2 border-b bg-gray-50 dark:bg-gray-800/30 text-[11px] font-semibold uppercase tracking-wider opacity-60">
              <span className="w-4" /><span className="flex-1">{isZh ? "机构名" : "Org Name"}</span>
              <span className="w-20 shrink-0">{isZh ? "编码" : "Code"}</span>
              <span className="w-16 shrink-0">{isZh ? "状态" : "Status"}</span>
              <span className="w-14 shrink-0">{isZh ? "类型" : "Type"}</span>
              <span className="w-[120px] shrink-0">{isZh ? "描述" : "Description"}</span>
              <span className="w-16 shrink-0">{isZh ? "操作" : "Actions"}</span>
            </div>
            {orgTree.map(o => <OrgTreeRow key={o.orgId} org={o} depth={0}
              onEdit={org => setOrgForm({ mode: "edit", org })}
              onDelete={org => setConfirmAction({ type: "delete", tab: "orgs", id: org.orgId, name: org.orgName })} />)}
          </div>
        </div>
      )}

      {/* Permissions Table */}
      {tab === "permissions" && (
        <div className="overflow-auto flex-1">
          <table className="w-full border-collapse">
            <thead><tr>
              <th className={th}>{isZh ? "资源" : "Resource"}</th>
              <th className={th}>{isZh ? "操作" : "Action"}</th>
              <th className={th}>{isZh ? "条件" : "Condition"}</th>
              <th className={th}>{isZh ? "描述" : "Description"}</th>
              <th className={th}>{isZh ? "操作" : "Actions"}</th>
            </tr></thead>
            <tbody>
              {permissions.map(p => (
                <tr key={p.permissionId} className="hover:bg-gray-50 dark:hover:bg-gray-800/20">
                  <td className={td}><code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded font-medium">{p.resource}</code></td>
                  <td className={td}><span className="text-xs font-mono">{p.action}</span></td>
                  <td className={td}><span className="text-xs opacity-50 font-mono">{p.conditionExpr || "-"}</span></td>
                  <td className={td}><span className="text-xs opacity-60 max-w-[200px] truncate block">{p.description || "-"}</span></td>
                  <td className={td}>
                    <div className="flex gap-1">
                      <button onClick={() => setPermForm({ mode: "edit", perm: p })} className="text-indigo-500 hover:text-indigo-700 p-1"><Edit3 size={14} /></button>
                      <button onClick={() => setConfirmAction({ type: "delete", tab: "permissions", id: p.permissionId, name: `${p.resource}:${p.action}` })}
                        className="text-red-500 hover:text-red-700 p-1"><Trash2 size={14} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination (Users only) */}
      {tab === "users" && userTotal > 0 && (
        <div className="flex items-center justify-between pt-1">
          <span className={`text-xs ${styles.cardTextMuted}`}>
            {isZh ? `第 ${userPage}/${totalPages} 页，共 ${userTotal} 条` : `Page ${userPage}/${totalPages}, total ${userTotal}`}
          </span>
          <div className="flex gap-1">
            <button disabled={userPage <= 1} onClick={() => setUserPage(p => Math.max(1, p - 1))}
              className={`px-2 py-1 rounded text-xs border ${styles.cardBorder} ${styles.cardText} disabled:opacity-30`}>
              <ChevronLeft className="w-3.5 h-3.5" /></button>
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
              const start = Math.max(1, Math.min(userPage - 2, totalPages - 4));
              const page = start + i;
              if (page > totalPages) return null;
              return <button key={page} onClick={() => setUserPage(page)}
                className={`px-2.5 py-1 rounded text-xs border ${page === userPage ? `${styles.accentBg} text-white` : `${styles.cardBorder} ${styles.cardText}`}`}>{page}</button>;
            })}
            <button disabled={userPage >= totalPages} onClick={() => setUserPage(p => Math.min(totalPages, p + 1))}
              className={`px-2 py-1 rounded text-xs border ${styles.cardBorder} ${styles.cardText} disabled:opacity-30`}>
              <ChevronRight className="w-3.5 h-3.5" /></button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Role Form Modal (inline) ──────────────────────────────────

function RoleFormModal({ mode, role, allPermissions, onSave, onClose, onManagePermissions }: {
  mode: "create" | "edit"; role?: IamRole | null;
  allPermissions: IamPermission[];
  onSave: (d: Record<string, any>) => Promise<void>; onClose: () => void;
  onManagePermissions?: () => void;
}) {
  const { locale } = useLanguage(); const { styles } = useTheme(); const isZh = locale === "zh";
  const [f, setF] = useState({ roleName: role?.roleName ?? "", roleCode: role?.roleCode ?? "", roleType: role?.roleType ?? "SYSTEM", description: role?.description ?? "" });
  const [saving, setSaving] = useState(false); const [err, setErr] = useState("");
  const [assignedPerms, setAssignedPerms] = useState<IamPermission[]>([]);
  const [loadingPerms, setLoadingPerms] = useState(false);

  // 编辑模式下加载已分配权限
  useEffect(() => {
    if (mode === "edit" && role?.roleId) {
      setLoadingPerms(true);
      fetchRolePermissions(role.roleId).then(permIds => {
        const idSet = new Set(permIds);
        setAssignedPerms(allPermissions.filter(p => idSet.has(p.permissionId)));
      }).catch(() => {}).finally(() => setLoadingPerms(false));
    }
  }, [mode, role?.roleId, allPermissions]);

  async function save() {
    if (!f.roleName?.trim()) { setErr(isZh ? "角色名不能为空" : "Role name required"); return; }
    if (!f.roleCode?.trim()) { setErr(isZh ? "角色编码不能为空" : "Role code required"); return; }
    setSaving(true); setErr(""); try { await onSave(f); onClose(); } catch (e: any) { setErr(e.message); } finally { setSaving(false); }
  }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-md max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create" ? (isZh ? "新建角色" : "Create Role") : (isZh ? "编辑角色" : "Edit Role")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {err && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{err}</div>}
        <div className="space-y-3">
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "角色名 *" : "Role Name *"}</label>
            <input value={f.roleName} onChange={e => setF(p => ({...p, roleName: e.target.value}))}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "角色编码 *" : "Role Code *"}</label>
            <input value={f.roleCode} onChange={e => setF(p => ({...p, roleCode: e.target.value}))} disabled={mode === "edit"}
              className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} disabled:opacity-50`} /></div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "类型" : "Type"}</label>
            <select value={f.roleType} onChange={e => setF(p => ({...p, roleType: e.target.value}))}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
              <option value="SYSTEM">SYSTEM</option><option value="CUSTOM">CUSTOM</option></select></div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={f.description} onChange={e => setF(p => ({...p, description: e.target.value}))} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>

          {/* 编辑模式：显示已分配权限 */}
          {mode === "edit" && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className={`block text-xs ${styles.cardTextMuted}`}>
                  {isZh ? `已分配权限 (${assignedPerms.length})` : `Assigned Permissions (${assignedPerms.length})`}
                </label>
                {onManagePermissions && (
                  <button type="button" onClick={onManagePermissions}
                    className={`text-xs text-indigo-500 hover:text-indigo-700 ${styles.cardTextMuted}`}>
                    {isZh ? "管理权限 →" : "Manage →"}
                  </button>
                )}
              </div>
              <div className={`rounded border p-2 max-h-32 overflow-y-auto text-xs space-y-1 ${styles.inputBg} ${styles.inputBorder}`}>
                {loadingPerms ? (
                  <div className={`${styles.cardTextMuted}`}>{isZh ? "加载中…" : "Loading…"}</div>
                ) : assignedPerms.length === 0 ? (
                  <div className={`${styles.cardTextMuted}`}>{isZh ? "暂无已分配权限" : "No permissions assigned"}</div>
                ) : (
                  assignedPerms.map(p => (
                    <div key={p.permissionId} className="flex items-center gap-1.5">
                      <span className={`font-mono text-[10px] px-1 rounded ${styles.cardBorder}`}>{p.resource}:{p.action}</span>
                      <span className={`truncate ${styles.cardTextMuted}`}>{p.description || ""}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>{isZh ? "取消" : "Cancel"}</button>
          <button onClick={save} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}</button>
        </div>
      </div>
    </div>
  );
}

// ── Org Form Modal (inline) ──────────────────────────────────

function OrgFormModal({ mode, org, orgTree, onSave, onClose }: {
  mode: "create" | "edit"; org?: IamOrg | null; orgTree: IamOrg[];
  onSave: (d: Record<string, any>) => Promise<void>; onClose: () => void;
}) {
  const { locale } = useLanguage(); const { styles } = useTheme(); const isZh = locale === "zh";
  const [f, setF] = useState({ orgName: org?.orgName ?? "", orgCode: org?.orgCode ?? "", orgType: org?.orgType ?? "DEPARTMENT", parentOrgId: org?.parentOrgId ?? "", description: org?.description ?? "", status: org?.status ?? "ACTIVE" });
  const [saving, setSaving] = useState(false); const [err, setErr] = useState("");
  async function save() {
    if (!f.orgName?.trim()) { setErr(isZh ? "机构名称不能为空" : "Org name required"); return; }
    if (!f.orgCode?.trim()) { setErr(isZh ? "机构编码不能为空" : "Org code required"); return; }
    setSaving(true); setErr(""); try { await onSave(f); onClose(); } catch (e: any) { setErr(e.message); } finally { setSaving(false); }
  }
  const orgOptions = flattenTree(orgTree).filter(o => o.orgId !== org?.orgId);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-lg ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create" ? (isZh ? "新建机构" : "Create Org") : (isZh ? "编辑机构" : "Edit Org")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {err && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{err}</div>}
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "机构名称 *" : "Org Name *"}</label>
              <input value={f.orgName} onChange={e => setF(p => ({...p, orgName: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "机构编码 *" : "Org Code *"}</label>
              <input value={f.orgCode} onChange={e => setF(p => ({...p, orgCode: e.target.value}))} disabled={mode === "edit"}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} disabled:opacity-50`} /></div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "上级机构" : "Parent Org"}</label>
              <select value={f.parentOrgId} onChange={e => setF(p => ({...p, parentOrgId: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="">-- {isZh ? "无 (顶级)" : "None (Root)"} --</option>
                {orgOptions.map(o => <option key={o.orgId} value={o.orgId}>{'\u00A0\u00A0'.repeat(o._depth)}{o.orgName} ({o.orgCode})</option>)}
              </select></div>
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "类型" : "Type"}</label>
              <select value={f.orgType} onChange={e => setF(p => ({...p, orgType: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="COMPANY">{isZh ? "公司" : "Company"}</option>
                <option value="DEPARTMENT">{isZh ? "部门" : "Department"}</option>
                <option value="TEAM">{isZh ? "团队" : "Team"}</option>
              </select></div>
          </div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={f.description} onChange={e => setF(p => ({...p, description: e.target.value}))} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
          {mode === "edit" && (
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "状态" : "Status"}</label>
              <select value={f.status} onChange={e => setF(p => ({...p, status: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="ACTIVE">{isZh ? "活跃" : "Active"}</option>
                <option value="DISABLED">{isZh ? "已禁用" : "Disabled"}</option>
              </select></div>
          )}
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>{isZh ? "取消" : "Cancel"}</button>
          <button onClick={save} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}</button>
        </div>
      </div>
    </div>
  );
}

// ── Perm Form Modal (inline) ─────────────────────────────────

function PermFormModal({ mode, permission, onSave, onClose }: {
  mode: "create" | "edit"; permission?: IamPermission | null;
  onSave: (d: Record<string, any>) => Promise<void>; onClose: () => void;
}) {
  const { locale } = useLanguage(); const { styles } = useTheme(); const isZh = locale === "zh";
  const [f, setF] = useState({ resource: permission?.resource ?? "", action: permission?.action ?? "", description: permission?.description ?? "", conditionExpr: permission?.conditionExpr ?? "" });
  const [saving, setSaving] = useState(false); const [err, setErr] = useState("");
  async function save() {
    if (!f.resource?.trim()) { setErr(isZh ? "资源不能为空" : "Resource required"); return; }
    if (!f.action?.trim()) { setErr(isZh ? "操作不能为空" : "Action required"); return; }
    setSaving(true); setErr(""); try { await onSave(f); onClose(); } catch (e: any) { setErr(e.message); } finally { setSaving(false); }
  }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-md ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create" ? (isZh ? "新建权限" : "Create Permission") : (isZh ? "编辑权限" : "Edit Permission")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {err && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{err}</div>}
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "资源 *" : "Resource *"}</label>
              <input value={f.resource} onChange={e => setF(p => ({...p, resource: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
            <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "操作 *" : "Action *"}</label>
              <input value={f.action} onChange={e => setF(p => ({...p, action: e.target.value}))}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
          </div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "条件表达式" : "Condition"}</label>
            <input value={f.conditionExpr} onChange={e => setF(p => ({...p, conditionExpr: e.target.value}))}
              className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
          <div><label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={f.description} onChange={e => setF(p => ({...p, description: e.target.value}))} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} /></div>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>{isZh ? "取消" : "Cancel"}</button>
          <button onClick={save} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}</button>
        </div>
      </div>
    </div>
  );
}

// ── Permission Panel (inline) ─────────────────────────────────

function PermissionPanel({ role, allPermissions, onSave, onClose }: {
  role: IamRole; allPermissions: IamPermission[];
  onSave: (permIds: string[]) => Promise<void>; onClose: () => void;
}) {
  const { locale } = useLanguage(); const { styles } = useTheme(); const isZh = locale === "zh";
  const [available, setAvailable] = useState<IamPermission[]>([]);
  const [assigned, setAssigned] = useState<IamPermission[]>([]);
  const [loading, setLoading] = useState(true); const [saving, setSaving] = useState(false);
  useEffect(() => {
    fetchRolePermissions(role.roleId).then(permIds => {
      const ids = new Set(permIds);
      setAssigned(allPermissions.filter(p => ids.has(p.permissionId)));
      setAvailable(allPermissions.filter(p => !ids.has(p.permissionId)));
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [role.roleId, allPermissions]);
  if (loading) return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-3xl ${styles.cardBg} ${styles.cardBorder}`}>
        <div className={`text-sm ${styles.cardTextMuted}`}>{isZh ? "加载中…" : "Loading…"}</div>
      </div>
    </div>);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-3xl max-h-[80vh] flex flex-col ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-3">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>{isZh ? "分配权限" : "Assign Permissions"} — {role.roleName}</h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        <div className="flex-1 flex gap-3 min-h-0">
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>{isZh ? "可用权限" : "Available"} ({available.length})</div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {available.map(p => (
                <div key={p.permissionId} onClick={() => { setAvailable(prev => prev.filter(x => x.permissionId !== p.permissionId)); setAssigned(prev => [...prev, p]); }}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer hover:bg-indigo-50 dark:hover:bg-indigo-900/20">
                  <span className="truncate"><code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1 rounded mr-1.5">{p.resource}</code><span className="opacity-70">{p.action}</span></span>
                  <ChevronRight className="w-3 h-3 opacity-30 shrink-0" /></div>))}
              {!available.length && <div className="text-xs opacity-30 text-center py-4">{isZh ? "无可用权限" : "No available"}</div>}
            </div>
          </div>
          <div className="flex flex-col justify-center gap-2 shrink-0">
            <ArrowLeftRight className="w-4 h-4 opacity-30" />
          </div>
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>{isZh ? "已分配" : "Assigned"} ({assigned.length})</div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {assigned.map(p => (
                <div key={p.permissionId} onClick={() => { setAssigned(prev => prev.filter(x => x.permissionId !== p.permissionId)); setAvailable(prev => [...prev, p]); }}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer bg-indigo-50 dark:bg-indigo-900/20 hover:bg-indigo-100 dark:hover:bg-indigo-900/40">
                  <span className="truncate"><code className="text-[11px] bg-indigo-100 dark:bg-indigo-800 px-1 rounded mr-1.5">{p.resource}</code><span className="opacity-70">{p.action}</span></span>
                  <ChevronLeft className="w-3 h-3 opacity-30 shrink-0" /></div>))}
              {!assigned.length && <div className="text-xs opacity-30 text-center py-4">{isZh ? "未分配" : "None assigned"}</div>}
            </div>
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-3">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>{isZh ? "取消" : "Cancel"}</button>
          <button onClick={async () => { setSaving(true); try { await onSave(assigned.map(p => p.permissionId)); onClose(); } catch {} finally { setSaving(false); } }}
            disabled={saving} className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}</button>
        </div>
      </div>
    </div>
  );
}

// ── Org Tree Row (inline) ───────────────────────────────────

function OrgTreeRow({ org, depth, onEdit, onDelete }: {
  org: IamOrg; depth: number; onEdit: (o: IamOrg) => void; onDelete: (o: IamOrg) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = org.children && org.children.length > 0;
  return (
    <>
      <div className="flex items-center gap-2 px-3 py-2 border-b border-gray-200 dark:border-gray-700/30 text-[13px] hover:bg-gray-50 dark:hover:bg-gray-800/20"
        style={{ paddingLeft: `${depth * 24 + 12}px` }}>
        <span className="w-4 text-center shrink-0 text-gray-400">
          {hasChildren ? <button onClick={e => { e.stopPropagation(); setExpanded(!expanded); }} className="hover:text-gray-600 dark:hover:text-gray-300 text-xs">{expanded ? '▼' : '▶'}</button>
            : <span className="text-gray-300">•</span>}
        </span>
        <span className="flex-1 truncate font-medium">{org.orgName}</span>
        <code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{org.orgCode}</code>
        <span className={`text-xs shrink-0 ${org.status === 'ACTIVE' ? 'text-green-500' : 'text-red-500'}`}>● {org.status}</span>
        <span className="text-xs opacity-50 shrink-0">{org.orgType}</span>
        <span className="text-xs opacity-40 shrink-0 max-w-[120px] truncate">{org.description || '-'}</span>
        <div className="flex gap-1 shrink-0 ml-2">
          <button onClick={e => { e.stopPropagation(); onEdit(org); }} className="text-indigo-500 hover:text-indigo-700 p-0.5"><Edit3 size={13} /></button>
          <button onClick={e => { e.stopPropagation(); onDelete(org); }} className="text-red-500 hover:text-red-700 p-0.5"><Trash2 size={13} /></button>
        </div>
      </div>
      {hasChildren && expanded && org.children!.map(child => <OrgTreeRow key={child.orgId} org={child} depth={depth + 1} onEdit={onEdit} onDelete={onDelete} />)}
    </>
  );
}
