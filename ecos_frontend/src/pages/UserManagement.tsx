/**
 * IAM User Management — Users / Roles / Organizations / Permissions
 * v6 — Slim entry: sub-components live in pages/user-management/
 *   + 增强: 角色/状态筛选, CSV导入导出, 用户详情抽屉, 强制下线, 密码重置,
 *     空状态, 操作确认, 骨架屏加载
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Users, Shield, Building2, Key, Plus, Trash2, Edit3,
  RefreshCw, X, CheckCircle2, Copy,
  ChevronLeft, ChevronRight, UserX,
} from "lucide-react";
import {
  fetchUsers, createUser, updateUser, deleteUser,
  toggleUserStatus, assignUserRoles,
  fetchRoles, createRole, updateRole, deleteRole,
  assignRolePermissions,
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
import Toast from "./user-management/Toast";
import ConfirmDialog from "./user-management/ConfirmDialog";
import EmptyState from "./user-management/EmptyState";
import CsvImportModal from "./user-management/CsvImportModal";
import UserDetailDrawer from "./user-management/UserDetailDrawer";
import RoleFormModal from "./user-management/RoleFormModal";
import OrgFormModal from "./user-management/OrgFormModal";
import PermFormModal from "./user-management/PermFormModal";
import PermissionPanel from "./user-management/PermissionPanel";
import OrgTreeRow from "./user-management/OrgTreeRow";

type Tab = "users" | "roles" | "orgs" | "permissions";

// ═══════════════════════════════════════════════════════════════
// Main Component — orchestration only
// ═══════════════════════════════════════════════════════════════

export default function UserManagement() {
  const { t } = useLanguage();
  const { styles } = useTheme();

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
    showToast("success", t("platform.user.host.created"));
    loadUsers();
  }, [t, showToast, loadUsers]);

  const handleUpdateUser = useCallback(async (data: Record<string, any>, roleIds: string[]) => {
    const userId = data.userId;
    const { userId: _, password, ...payload } = data;
    await updateUser(userId, payload);
    await assignUserRoles(userId, roleIds).catch(() => {});
    showToast("success", t("platform.user.host.updated"));
    loadUsers();
  }, [t, showToast, loadUsers]);

  const handleDeleteUser = useCallback(async () => {
    if (!confirmAction?.id) return;
    const userId = confirmAction.id;
    setConfirmAction(null);
    try {
      await deleteUser(userId);
      showToast("success", t("platform.user.host.deleted"));
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, t, showToast, loadUsers]);

  const handleToggleStatus = useCallback(async (userId: string, currentStatus: string) => {
    const newStatus = currentStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await toggleUserStatus(userId, newStatus);
      showToast("success", t("platform.user.host.statusUpdated"));
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [t, showToast, loadUsers]);

  // ── Batch operations ──────────────────────────────────
  const handleBatchEnable = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => toggleUserStatus(id, "ACTIVE")));
      showToast("success", t("platform.user.host.batchEnabled", { n: userIds.length }));
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [t, showToast, loadUsers]);

  const handleBatchDisable = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => toggleUserStatus(id, "DISABLED")));
      showToast("success", t("platform.user.host.batchDisabled", { n: userIds.length }));
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [t, showToast, loadUsers]);

  const handleBatchDelete = useCallback(async (userIds: string[]) => {
    try {
      await Promise.all(userIds.map(id => deleteUser(id)));
      showToast("success", t("platform.user.host.batchDeleted", { n: userIds.length }));
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }, [t, showToast, loadUsers]);

  const handleForceLogout = useCallback((userId: string) => {
    setDetailUser(null);
    setConfirmAction({ type: "logout", userId });
  }, []);

  const executeForceLogout = useCallback(async () => {
    if (!confirmAction?.userId) return;
    setConfirmAction(null);
    try {
      await forceLogoutUser(confirmAction.userId);
      showToast("success", t("platform.user.host.forceLogoutToast"));
    } catch (e: any) { showToast("error", e.message || "Force logout failed"); }
  }, [confirmAction, t, showToast]);

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
      showToast("success", t("platform.user.host.passwordResetToast"));
    } catch (e: any) { showToast("error", e.message || "Reset failed"); }
  }, [confirmAction, t, showToast]);

  const handleCsvImport = useCallback(async (rows: Record<string, string>[]) => {
    await batchCreateUsers(rows);
    showToast("success", t("platform.user.host.csvImported", { n: rows.length }));
    setShowCsvImport(false);
    loadUsers();
  }, [t, showToast, loadUsers]);

  const handleCsvExport = useCallback(() => {
    if (users.length === 0) {
      showToast("error", t("platform.user.host.exportEmpty"));
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
    showToast("success", t("platform.user.host.exportDone"));
  }, [users, t, showToast]);

  // Role CRUD
  const handleCreateRole = useCallback(async (data: Record<string, any>) => {
    await createRole(data);
    showToast("success", t("platform.user.host.roleCreated"));
    loadRoles();
  }, [t, showToast, loadRoles]);
  const handleUpdateRole = useCallback(async (data: Record<string, any>) => {
    await updateRole(data.roleId, data);
    showToast("success", t("platform.user.host.roleUpdated"));
    loadRoles();
  }, [t, showToast, loadRoles]);
  const handleDeleteRole = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deleteRole(confirmAction.id); showToast("success", t("platform.user.host.roleDeleted")); loadRoles(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, t, showToast, loadRoles]);
  const handleSaveRolePermissions = useCallback(async (permIds: string[]) => {
    if (!permPanelRole) return;
    await assignRolePermissions(permPanelRole.roleId, permIds);
    showToast("success", t("platform.user.host.permAssigned"));
    setPermPanelRole(null);
  }, [permPanelRole, t, showToast]);
  const handleCreateOrg = useCallback(async (data: Record<string, any>) => {
    await createOrg(data);
    showToast("success", t("platform.user.host.orgCreated"));
    loadOrgs();
  }, [t, showToast, loadOrgs]);
  const handleUpdateOrg = useCallback(async (data: Record<string, any>) => {
    await updateOrg(data.orgId, data);
    showToast("success", t("platform.user.host.orgUpdated"));
    loadOrgs();
  }, [t, showToast, loadOrgs]);
  const handleDeleteOrg = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deleteOrg(confirmAction.id); showToast("success", t("platform.user.host.orgDeleted")); loadOrgs(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, t, showToast, loadOrgs]);
  const handleCreatePerm = useCallback(async (data: Record<string, any>) => {
    await createPermission(data);
    showToast("success", t("platform.user.host.permCreated"));
    loadPermissions();
  }, [t, showToast, loadPermissions]);
  const handleUpdatePerm = useCallback(async (data: Record<string, any>) => {
    await updatePermission(data.permissionId, data);
    showToast("success", t("platform.user.host.permUpdated"));
    loadPermissions();
  }, [t, showToast, loadPermissions]);
  const handleDeletePerm = useCallback(async () => {
    if (!confirmAction?.id) return;
    setConfirmAction(null);
    try { await deletePermission(confirmAction.id); showToast("success", t("platform.user.host.permDeleted")); loadPermissions(); }
    catch (e: any) { showToast("error", e.message); }
  }, [confirmAction, t, showToast, loadPermissions]);

  // ── Tab configuration ─────────────────────────────────────

  const tabs: { id: Tab; label: string; icon: React.FC<any> }[] = [
    { id: "users", label: t("platform.user.host.tabUsers"), icon: Users },
    { id: "roles", label: t("platform.user.host.tabRoles"), icon: Shield },
    { id: "orgs", label: t("platform.user.host.tabOrgs"), icon: Building2 },
    { id: "permissions", label: t("platform.user.host.tabPermissions"), icon: Key },
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
                {t("platform.user.host.resetPwdResultTitle")}
              </h3>
              <button onClick={() => setResetPwdResult(null)} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
            </div>
            <p className={`text-xs mb-2 ${styles.cardTextMuted}`}>
              {t("platform.user.host.tempPwdPrompt")}
            </p>
            <div className="flex items-center gap-2 mb-4">
              <code className="flex-1 px-3 py-2 rounded text-sm font-mono bg-gray-100 dark:bg-gray-800 select-all">
                {resetPwdResult.tempPassword}
              </code>
              <button
                onClick={() => { navigator.clipboard.writeText(resetPwdResult!.tempPassword); showToast("success", t("platform.user.host.copied")); }}
                className={`p-2 rounded border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}
              >
                <Copy className="w-4 h-4" />
              </button>
            </div>
            <button onClick={() => setResetPwdResult(null)}
              className={`w-full px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              {t("platform.user.host.gotIt")}
            </button>
          </div>
        </div>
      )}

      {/* Confirm Dialogs */}
      {confirmAction?.type === "delete" && (
        <ConfirmDialog
          title={t("platform.user.host.confirmDeleteTitle")}
          message={t("platform.user.host.confirmDeleteMsg", { name: confirmAction!.name || "" })}
          confirmLabel={t("platform.user.host.confirmDeleteBtn")}
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
          title={t("platform.user.host.forceLogoutTitle")}
          message={t("platform.user.host.forceLogoutMsg")}
          confirmLabel={t("platform.user.host.forceLogoutConfirm")}
          confirmClass="px-4 py-1.5 rounded text-xs font-semibold bg-amber-500 text-white hover:bg-amber-600"
          onConfirm={executeForceLogout}
          onCancel={() => setConfirmAction(null)}
        />
      )}
      {confirmAction?.type === "resetPwd" && (
        <ConfirmDialog
          variant="warning"
          title={t("platform.user.host.resetPwdTitle")}
          message={t("platform.user.host.resetPwdMsg")}
          confirmLabel={t("platform.user.host.resetPwdConfirm")}
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
          {t("platform.user.host.pageTitle")}
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
              <Plus size={14} /> {t("platform.user.host.newButton")}
            </button>
          )}
          {tab === "permissions" && (
            <button onClick={() => setPermForm({ mode: "create" })}
              className={`flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              <Plus size={14} /> {t("platform.user.host.newButton")}
            </button>
          )}
          <button onClick={loadData}
            className="flex items-center gap-1 px-3 py-1.5 rounded text-xs border border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5">
            <RefreshCw size={14} /> {t("platform.user.host.refresh")}
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
          title={t("platform.user.host.emptyUsers")}
          description={t("platform.user.host.emptyUsersDesc")}
          onCreate={() => setUserForm({ mode: "create" })}
          onImport={() => setShowCsvImport(true)}
          createLabel={t("platform.user.host.emptyUsersCreate")}
          importLabel={t("platform.user.host.emptyUsersImport")}
        />
      )}

      {/* Roles Table */}
      {tab === "roles" && (
        <div className="overflow-auto flex-1">
          <table className="w-full border-collapse">
            <thead><tr>
              <th className={th}>{t("platform.user.host.roleColName")}</th>
              <th className={th}>{t("platform.user.host.roleColCode")}</th>
              <th className={th}>{t("platform.user.host.roleColType")}</th>
              <th className={th}>{t("platform.user.host.roleColDesc")}</th>
              <th className={th}>{t("platform.user.host.roleColActions")}</th>
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
              <span className="w-4" /><span className="flex-1">{t("platform.user.host.orgColName")}</span>
              <span className="w-20 shrink-0">{t("platform.user.host.roleColCode")}</span>
              <span className="w-16 shrink-0">{t("platform.user.host.orgColStatus")}</span>
              <span className="w-14 shrink-0">{t("platform.user.host.roleColType")}</span>
              <span className="w-[120px] shrink-0">{t("platform.user.host.roleColDesc")}</span>
              <span className="w-16 shrink-0">{t("platform.user.host.roleColActions")}</span>
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
              <th className={th}>{t("platform.user.host.permColResource")}</th>
              <th className={th}>{t("platform.user.host.permColAction")}</th>
              <th className={th}>{t("platform.user.host.permColCondition")}</th>
              <th className={th}>{t("platform.user.host.roleColDesc")}</th>
              <th className={th}>{t("platform.user.host.roleColActions")}</th>
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
            {t("platform.user.host.pager", { page: userPage, total: totalPages, count: userTotal })}
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
