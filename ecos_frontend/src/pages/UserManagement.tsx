/**
 * IAM User Management — Users / Roles / Organizations / Permissions
 * v4 — Full rewrite with search/pagination, role assignment, permission transfer panel
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Users, Shield, Building2, Key, Plus, Trash2, Edit3,
  RefreshCw, Search, Check, X, AlertCircle, CheckCircle2,
  ArrowLeftRight, ChevronLeft, ChevronRight, Lock, Unlock,
} from "lucide-react";
import {
  fetchUsers, createUser, updateUser, deleteUser, resetPassword,
  toggleUserStatus, fetchUserRoles, assignUserRoles,
  fetchRoles, createRole, updateRole, deleteRole,
  fetchRolePermissions, assignRolePermissions,
  fetchOrgTree, fetchOrgs, createOrg, updateOrg, deleteOrg,
  fetchPermissions, createPermission, updatePermission, deletePermission,
  IamUser, IamRole, IamOrg, IamPermission,
} from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

type Tab = "users" | "roles" | "orgs" | "permissions";

// ── Toast ──────────────────────────────────────────────────────

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

// ── Delete Confirm Dialog ─────────────────────────────────────

const DeleteConfirm: React.FC<{
  title: string;
  targetName: string;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ title, targetName, onConfirm, onCancel }) => (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 bg-white dark:bg-[#141924] border border-[#E2E8F0] dark:border-[#1E293B]">
      <h3 className="text-base font-bold mb-2 text-slate-800 dark:text-slate-100">{title}</h3>
      <p className="text-sm text-slate-500 dark:text-slate-400 mb-5">
        确定要删除「{targetName}」吗？此操作不可撤销。
      </p>
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel}
          className="px-4 py-1.5 rounded text-xs border border-[#E2E8F0] dark:border-[#1E293B] text-slate-700 dark:text-slate-300 hover:bg-gray-50 dark:hover:bg-white/5">
          取消
        </button>
        <button onClick={onConfirm}
          className="px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700">
          删除
        </button>
      </div>
    </div>
  </div>
);

// ── User Form Modal ───────────────────────────────────────────

interface UserFormModalProps {
  mode: "create" | "edit";
  user?: IamUser | null;
  allRoles: IamRole[];
  orgTree: IamOrg[];
  onSave: (data: Record<string, any>, roleIds: string[]) => Promise<void>;
  onClose: () => void;
}

function UserFormModal({ mode, user, allRoles, orgTree, onSave, onClose }: UserFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [form, setForm] = useState<Record<string, string>>({
    username: user?.username ?? "",
    realName: user?.realName ?? "",
    email: user?.email ?? "",
    phone: user?.phone ?? "",
    orgId: user?.orgId ?? "",
    password: "",
  });
  const [selectedRoleIds, setSelectedRoleIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (mode === "edit" && user?.userId) {
      fetchUserRoles(user.userId).then(setSelectedRoleIds).catch(() => {});
    }
  }, [mode, user]);

  function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
    const result: (IamOrg & { _depth: number })[] = [];
    for (const node of nodes) {
      result.push({ ...node, _depth: depth });
      if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
    }
    return result;
  }

  function update(k: string, v: string) { setForm(f => ({ ...f, [k]: v })); }

  function toggleRole(roleId: string) {
    setSelectedRoleIds(prev =>
      prev.includes(roleId) ? prev.filter(id => id !== roleId) : [...prev, roleId]
    );
  }

  async function handleSave() {
    if (!form.username?.trim()) {
      setError("用户名不能为空");
      return;
    }
    setSaving(true); setError("");
    try {
      await onSave(form, selectedRoleIds);
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally { setSaving(false); }
  }

  const isZh = locale === "zh";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-xl max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create" ? (isZh ? "新建用户" : "Create User") : (isZh ? "编辑用户" : "Edit User")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}

        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {isZh ? "用户名 *" : "Username *"}
              </label>
              <input value={form.username} onChange={e => update("username", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={isZh ? "登录用户名" : "Login username"} />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {isZh ? "真实姓名" : "Real Name"}
              </label>
              <input value={form.realName} onChange={e => update("realName", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={isZh ? "真实姓名" : "Real name"} />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {isZh ? "邮箱" : "Email"}
              </label>
              <input value={form.email} onChange={e => update("email", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder="email@example.com" />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {isZh ? "手机" : "Phone"}
              </label>
              <input value={form.phone} onChange={e => update("phone", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder="13800138000" />
            </div>
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {isZh ? "所属组织" : "Organization"}
            </label>
            <select value={form.orgId} onChange={e => update("orgId", e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
              <option value="">-- {isZh ? "选择组织" : "Select Org"} --</option>
              {flattenTree(orgTree).map(o => (
                <option key={o.orgId} value={o.orgId}>
                  {'\u00A0\u00A0'.repeat(o._depth)}{o.orgName} ({o.orgCode})
                </option>
              ))}
            </select>
          </div>
          {mode === "create" && (
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {isZh ? "密码" : "Password"}
              </label>
              <input type="password" value={form.password} onChange={e => update("password", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={isZh ? "留空则使用默认密码" : "Leave empty for default"} />
            </div>
          )}

          {/* Role Multi-Select */}
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {isZh ? "角色分配" : "Roles"} <span className="opacity-50">({selectedRoleIds.length} {isZh ? "已选" : "selected"})</span>
            </label>
            <div className="max-h-32 overflow-y-auto border rounded p-2 grid grid-cols-2 gap-1">
              {allRoles.map(r => (
                <label key={r.roleId}
                  className={`flex items-center gap-1.5 px-2 py-1 rounded text-xs cursor-pointer transition-colors
                    ${selectedRoleIds.includes(r.roleId)
                      ? "bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300"
                      : "hover:bg-gray-50 dark:hover:bg-gray-800/30"}`}>
                  <input type="checkbox" checked={selectedRoleIds.includes(r.roleId)}
                    onChange={() => toggleRole(r.roleId)} className="rounded" />
                  {r.roleName}
                </label>
              ))}
              {allRoles.length === 0 && (
                <span className="text-xs opacity-40 col-span-2 p-1">{isZh ? "暂无角色" : "No roles available"}</span>
              )}
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose}
            className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Role Form Modal ───────────────────────────────────────────

interface RoleFormModalProps {
  mode: "create" | "edit";
  role?: IamRole | null;
  onSave: (data: Record<string, any>) => Promise<void>;
  onClose: () => void;
}

function RoleFormModal({ mode, role, onSave, onClose }: RoleFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [form, setForm] = useState<Record<string, string>>({
    roleName: role?.roleName ?? "",
    roleCode: role?.roleCode ?? "",
    roleType: role?.roleType ?? "SYSTEM",
    description: role?.description ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const isZh = locale === "zh";

  function update(k: string, v: string) { setForm(f => ({ ...f, [k]: v })); }

  async function handleSave() {
    if (!form.roleName?.trim()) { setError(isZh ? "角色名不能为空" : "Role name required"); return; }
    if (!form.roleCode?.trim()) { setError(isZh ? "角色编码不能为空" : "Role code required"); return; }
    setSaving(true); setError("");
    try { await onSave(form); onClose(); }
    catch (e: any) { setError(e.message || "Save failed"); }
    finally { setSaving(false); }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-md ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create" ? (isZh ? "新建角色" : "Create Role") : (isZh ? "编辑角色" : "Edit Role")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}
        <div className="space-y-3">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "角色名 *" : "Role Name *"}</label>
            <input value={form.roleName} onChange={e => update("roleName", e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "例如：系统管理员" : "e.g. System Admin"} />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "角色编码 *" : "Role Code *"}</label>
            <input value={form.roleCode} onChange={e => update("roleCode", e.target.value)} disabled={mode === "edit"}
              className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} disabled:opacity-50`}
              placeholder="ROLE_ADMIN" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "类型" : "Type"}</label>
              <select value={form.roleType} onChange={e => update("roleType", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="SYSTEM">SYSTEM</option>
                <option value="CUSTOM">CUSTOM</option>
              </select>
            </div>
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={form.description} onChange={e => update("description", e.target.value)} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "角色权限描述…" : "Role description…"} />
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Permission Assignment Panel (Transfer-style for Roles) ────

interface PermissionPanelProps {
  role: IamRole;
  allPermissions: IamPermission[];
  onSave: (permIds: string[]) => Promise<void>;
  onClose: () => void;
}

function PermissionPanel({ role, allPermissions, onSave, onClose }: PermissionPanelProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [available, setAvailable] = useState<IamPermission[]>([]);
  const [assigned, setAssigned] = useState<IamPermission[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const isZh = locale === "zh";

  useEffect(() => {
    fetchRolePermissions(role.roleId).then(permIds => {
      const assignedIds = new Set(permIds);
      setAssigned(allPermissions.filter(p => assignedIds.has(p.permissionId)));
      setAvailable(allPermissions.filter(p => !assignedIds.has(p.permissionId)));
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [role.roleId, allPermissions]);

  function moveToAssigned(p: IamPermission) {
    setAvailable(prev => prev.filter(x => x.permissionId !== p.permissionId));
    setAssigned(prev => [...prev, p]);
  }
  function moveToAvailable(p: IamPermission) {
    setAssigned(prev => prev.filter(x => x.permissionId !== p.permissionId));
    setAvailable(prev => [...prev, p]);
  }

  async function handleSave() {
    setSaving(true);
    try {
      await onSave(assigned.map(p => p.permissionId));
      onClose();
    } catch (e: any) { /* error handled by parent */ }
    finally { setSaving(false); }
  }

  if (loading) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
        <div className={`rounded-lg border p-6 w-full max-w-3xl ${styles.cardBg} ${styles.cardBorder}`}>
          <div className={`text-sm ${styles.cardTextMuted}`}>{isZh ? "加载中…" : "Loading…"}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-3xl max-h-[80vh] flex flex-col ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-3">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {isZh ? "分配权限" : "Assign Permissions"} — {role.roleName}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        <div className="flex-1 flex gap-3 min-h-0">
          {/* Left: Available pool */}
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>
              {isZh ? "可用权限" : "Available"} ({available.length})
            </div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {available.map(p => (
                <div key={p.permissionId}
                  onClick={() => moveToAssigned(p)}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer hover:bg-indigo-50 dark:hover:bg-indigo-900/20 transition-colors">
                  <span className="truncate">
                    <code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1 rounded mr-1.5">{p.resource}</code>
                    <span className="opacity-70">{p.action}</span>
                  </span>
                  <ChevronRight className="w-3 h-3 opacity-30 shrink-0" />
                </div>
              ))}
              {available.length === 0 && (
                <div className="text-xs opacity-30 text-center py-4">{isZh ? "无可用权限" : "No available permissions"}</div>
              )}
            </div>
          </div>

          {/* Center: Transfer arrows */}
          <div className="flex flex-col justify-center gap-2 shrink-0">
            <button onClick={() => {}} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-800 opacity-30 cursor-default">
              <ArrowLeftRight className="w-4 h-4" />
            </button>
          </div>

          {/* Right: Assigned */}
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>
              {isZh ? "已分配权限" : "Assigned"} ({assigned.length})
            </div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {assigned.map(p => (
                <div key={p.permissionId}
                  onClick={() => moveToAvailable(p)}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer bg-indigo-50 dark:bg-indigo-900/20 hover:bg-indigo-100 dark:hover:bg-indigo-900/40 transition-colors">
                  <span className="truncate">
                    <code className="text-[11px] bg-indigo-100 dark:bg-indigo-800 px-1 rounded mr-1.5">{p.resource}</code>
                    <span className="opacity-70">{p.action}</span>
                  </span>
                  <ChevronLeft className="w-3 h-3 opacity-30 shrink-0" />
                </div>
              ))}
              {assigned.length === 0 && (
                <div className="text-xs opacity-30 text-center py-4">{isZh ? "未分配权限" : "No permissions assigned"}</div>
              )}
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-3">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Org Form Modal ────────────────────────────────────────────

interface OrgFormModalProps {
  mode: "create" | "edit";
  org?: IamOrg | null;
  orgTree: IamOrg[];
  onSave: (data: Record<string, any>) => Promise<void>;
  onClose: () => void;
}

function OrgFormModal({ mode, org, orgTree, onSave, onClose }: OrgFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [form, setForm] = useState<Record<string, string>>({
    orgName: org?.orgName ?? "",
    orgCode: org?.orgCode ?? "",
    orgType: org?.orgType ?? "DEPARTMENT",
    parentOrgId: org?.parentOrgId ?? "",
    description: org?.description ?? "",
    status: org?.status ?? "ACTIVE",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const isZh = locale === "zh";

  function update(k: string, v: string) { setForm(f => ({ ...f, [k]: v })); }

  function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
    const result: (IamOrg & { _depth: number })[] = [];
    for (const node of nodes) {
      result.push({ ...node, _depth: depth });
      if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
    }
    return result;
  }

  async function handleSave() {
    if (!form.orgName?.trim()) { setError(isZh ? "机构名称不能为空" : "Org name required"); return; }
    if (!form.orgCode?.trim()) { setError(isZh ? "机构编码不能为空" : "Org code required"); return; }
    setSaving(true); setError("");
    try { await onSave(form); onClose(); }
    catch (e: any) { setError(e.message || "Save failed"); }
    finally { setSaving(false); }
  }

  // filter out self for edit mode
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
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "机构名称 *" : "Org Name *"}</label>
              <input value={form.orgName} onChange={e => update("orgName", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={isZh ? "例如：研发部" : "e.g. R&D Dept"} />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "机构编码 *" : "Org Code *"}</label>
              <input value={form.orgCode} onChange={e => update("orgCode", e.target.value)} disabled={mode === "edit"}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} disabled:opacity-50`}
                placeholder="DEPT_RD" />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "上级机构" : "Parent Org"}</label>
              <select value={form.parentOrgId} onChange={e => update("parentOrgId", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="">-- {isZh ? "无 (顶级)" : "None (Root)"} --</option>
                {orgOptions.map(o => (
                  <option key={o.orgId} value={o.orgId}>
                    {'\u00A0\u00A0'.repeat(o._depth)}{o.orgName} ({o.orgCode})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "类型" : "Type"}</label>
              <select value={form.orgType} onChange={e => update("orgType", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="COMPANY">{isZh ? "公司" : "Company"}</option>
                <option value="DEPARTMENT">{isZh ? "部门" : "Department"}</option>
                <option value="TEAM">{isZh ? "团队" : "Team"}</option>
              </select>
            </div>
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={form.description} onChange={e => update("description", e.target.value)} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "机构描述…" : "Org description…"} />
          </div>
          {mode === "edit" && (
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "状态" : "Status"}</label>
              <select value={form.status} onChange={e => update("status", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
                <option value="ACTIVE">{isZh ? "活跃" : "Active"}</option>
                <option value="DISABLED">{isZh ? "已禁用" : "Disabled"}</option>
              </select>
            </div>
          )}
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Permission Form Modal ─────────────────────────────────────

interface PermissionFormModalProps {
  mode: "create" | "edit";
  permission?: IamPermission | null;
  onSave: (data: Record<string, any>) => Promise<void>;
  onClose: () => void;
}

function PermissionFormModal({ mode, permission, onSave, onClose }: PermissionFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [form, setForm] = useState<Record<string, string>>({
    resource: permission?.resource ?? "",
    action: permission?.action ?? "",
    description: permission?.description ?? "",
    conditionExpr: permission?.conditionExpr ?? "",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const isZh = locale === "zh";

  function update(k: string, v: string) { setForm(f => ({ ...f, [k]: v })); }

  async function handleSave() {
    if (!form.resource?.trim()) { setError(isZh ? "资源不能为空" : "Resource required"); return; }
    if (!form.action?.trim()) { setError(isZh ? "操作不能为空" : "Action required"); return; }
    setSaving(true); setError("");
    try { await onSave(form); onClose(); }
    catch (e: any) { setError(e.message || "Save failed"); }
    finally { setSaving(false); }
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
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "资源 *" : "Resource *"}</label>
              <input value={form.resource} onChange={e => update("resource", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder="user:read" />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "操作 *" : "Action *"}</label>
              <input value={form.action} onChange={e => update("action", e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder="READ,WRITE" />
            </div>
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "条件表达式" : "Condition"}</label>
            <input value={form.conditionExpr} onChange={e => update("conditionExpr", e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border font-mono ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "可选 ABAC 条件" : "Optional ABAC condition"} />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{isZh ? "描述" : "Description"}</label>
            <textarea value={form.description} onChange={e => update("description", e.target.value)} rows={2}
              className={`w-full px-3 py-2 rounded text-sm border resize-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "权限描述…" : "Permission description…"} />
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {isZh ? "取消" : "Cancel"}
          </button>
          <button onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Org Tree Row (recursive, with edit/delete buttons) ────────

function OrgTreeRow({ org, depth, onEdit, onDelete }: {
  org: IamOrg; depth: number;
  onEdit: (org: IamOrg) => void;
  onDelete: (org: IamOrg) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const hasChildren = org.children && org.children.length > 0;

  return (
    <>
      <div
        className="flex items-center gap-2 px-3 py-2 border-b border-gray-200 dark:border-gray-700/30 text-[13px] hover:bg-gray-50 dark:hover:bg-gray-800/20 transition-colors"
        style={{ paddingLeft: `${depth * 24 + 12}px` }}
      >
        <span className="w-4 text-center shrink-0 text-gray-400">
          {hasChildren ? (
            <button onClick={(e) => { e.stopPropagation(); setExpanded(!expanded); }}
              className="hover:text-gray-600 dark:hover:text-gray-300 text-xs">
              {expanded ? '▼' : '▶'}
            </button>
          ) : <span className="text-gray-300">•</span>}
        </span>
        <span className="flex-1 truncate font-medium">{org.orgName}</span>
        <code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{org.orgCode}</code>
        <span className={`text-xs shrink-0 ${org.status === 'ACTIVE' ? 'text-green-500' : 'text-red-500'}`}>
          ● {org.status}
        </span>
        <span className="text-xs opacity-50 shrink-0">{org.orgType}</span>
        <span className="text-xs opacity-40 shrink-0 max-w-[120px] truncate">{org.description || '-'}</span>
        <div className="flex gap-1 shrink-0 ml-2">
          <button onClick={(e) => { e.stopPropagation(); onEdit(org); }}
            className="text-indigo-500 hover:text-indigo-700 p-0.5" title="编辑">
            <Edit3 size={13} />
          </button>
          <button onClick={(e) => { e.stopPropagation(); onDelete(org); }}
            className="text-red-500 hover:text-red-700 p-0.5" title="删除">
            <Trash2 size={13} />
          </button>
        </div>
      </div>
      {hasChildren && expanded && (
        org.children!.map(child => (
          <OrgTreeRow key={child.orgId} org={child} depth={depth + 1} onEdit={onEdit} onDelete={onDelete} />
        ))
      )}
    </>
  );
}

// ═══════════════════════════════════════════════════════════════
// Main Component
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

  // Users: search & pagination
  const [userSearch, setUserSearch] = useState("");
  const [userPage, setUserPage] = useState(1);
  const pageSize = 15;

  // Toast
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // Delete dialog
  const [deleteTarget, setDeleteTarget] = useState<{
    tab: Tab; id: string; name: string;
  } | null>(null);

  // Form / edit modals
  const [userForm, setUserForm] = useState<{ mode: "create" | "edit"; user?: IamUser | null } | null>(null);
  const [roleForm, setRoleForm] = useState<{ mode: "create" | "edit"; role?: IamRole | null } | null>(null);
  const [orgForm, setOrgForm] = useState<{ mode: "create" | "edit"; org?: IamOrg | null } | null>(null);
  const [permForm, setPermForm] = useState<{ mode: "create" | "edit"; perm?: IamPermission | null } | null>(null);

  // Permission assignment panel for roles
  const [permPanelRole, setPermPanelRole] = useState<IamRole | null>(null);

  // Reset password
  const [resetPwdUser, setResetPwdUser] = useState<IamUser | null>(null);
  const [resetPwdPassword, setResetPwdPassword] = useState("");

  const tabs: { id: Tab; label: string; icon: React.FC<any> }[] = [
    { id: "users", label: isZh ? "用户" : "Users", icon: Users },
    { id: "roles", label: isZh ? "角色" : "Roles", icon: Shield },
    { id: "orgs", label: isZh ? "组织机构" : "Orgs", icon: Building2 },
    { id: "permissions", label: isZh ? "权限" : "Permissions", icon: Key },
  ];

  // ── Load data ──────────────────────────────────────────────

  async function loadUsers() {
    setLoading(true); setError("");
    try {
      const res = await fetchUsers(userSearch || undefined, userPage, pageSize);
      setUsers(res.data || []);
      setUserTotal(res.total || 0);
    } catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }

  async function loadRoles() {
    setLoading(true); setError("");
    try { setRoles((await fetchRoles()).data || []); }
    catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }

  async function loadOrgs() {
    setLoading(true); setError("");
    try {
      const tree = await fetchOrgTree();
      setOrgTree(tree);
      // Build flat org map for user table
      const all = await fetchOrgs();
      const map: Record<string, string> = {};
      for (const o of all) { map[o.orgId] = o.orgName; }
      setOrgMap(map);
    } catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }

  async function loadPermissions() {
    setLoading(true); setError("");
    try { setPermissions(await fetchPermissions() || []); }
    catch (e: any) { setError(e.message || String(e)); }
    setLoading(false);
  }

  async function loadData() {
    switch (tab) {
      case "users": await loadUsers(); break;
      case "roles": await loadRoles(); break;
      case "orgs": await loadOrgs(); break;
      case "permissions": await loadPermissions(); break;
    }
  }

  useEffect(() => { loadData(); }, [tab, userPage]);

  // ── CRUD handlers ─────────────────────────────────────────

  async function handleCreateUser(data: Record<string, any>, roleIds: string[]) {
    const payload = { ...data };
    if (!payload.password) payload.password = "ECOS@2026";
    const result: any = await createUser(payload);
    const newUserId = result?.userId || result?.id;
    if (newUserId && roleIds.length > 0) {
      await assignUserRoles(newUserId, roleIds).catch(() => {});
    }
    showToast("success", isZh ? "用户创建成功" : "User created");
    loadUsers();
  }

  async function handleUpdateUser(data: Record<string, any>, roleIds: string[]) {
    const userId = data.userId;
    const { userId: _, password, ...payload } = data;
    await updateUser(userId, payload);
    await assignUserRoles(userId, roleIds).catch(() => {});
    showToast("success", isZh ? "用户更新成功" : "User updated");
    loadUsers();
  }

  async function handleDeleteUser() {
    if (!deleteTarget) return;
    setDeleteTarget(null);
    try {
      await deleteUser(deleteTarget.id);
      showToast("success", isZh ? "用户已删除" : "User deleted");
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }

  async function handleToggleStatus(userId: string, currentStatus: string) {
    const newStatus = currentStatus === "ACTIVE" ? "DISABLED" : "ACTIVE";
    try {
      await toggleUserStatus(userId, newStatus);
      showToast("success", isZh ? "状态已更新" : "Status updated");
      loadUsers();
    } catch (e: any) { showToast("error", e.message); }
  }

  async function handleResetPwd() {
    if (!resetPwdUser || !resetPwdPassword) return;
    if (resetPwdPassword.length < 6) { showToast("error", isZh ? "密码至少6位" : "Min 6 chars"); return; }
    try {
      await resetPassword(resetPwdUser.userId, resetPwdPassword);
      showToast("success", isZh ? "密码已重置" : "Password reset");
      setResetPwdUser(null); setResetPwdPassword("");
    } catch (e: any) { showToast("error", e.message); }
  }

  async function handleCreateRole(data: Record<string, any>) {
    await createRole(data);
    showToast("success", isZh ? "角色创建成功" : "Role created");
    loadRoles();
  }

  async function handleUpdateRole(data: Record<string, any>) {
    await updateRole(data.roleId, data);
    showToast("success", isZh ? "角色更新成功" : "Role updated");
    loadRoles();
  }

  async function handleDeleteRole() {
    if (!deleteTarget) return;
    setDeleteTarget(null);
    try {
      await deleteRole(deleteTarget.id);
      showToast("success", isZh ? "角色已删除" : "Role deleted");
      loadRoles();
    } catch (e: any) { showToast("error", e.message); }
  }

  async function handleSaveRolePermissions(permIds: string[]) {
    if (!permPanelRole) return;
    await assignRolePermissions(permPanelRole.roleId, permIds);
    showToast("success", isZh ? "权限分配成功" : "Permissions saved");
    setPermPanelRole(null);
  }

  async function handleCreateOrg(data: Record<string, any>) {
    await createOrg(data);
    showToast("success", isZh ? "机构创建成功" : "Org created");
    loadOrgs();
  }

  async function handleUpdateOrg(data: Record<string, any>) {
    await updateOrg(data.orgId, data);
    showToast("success", isZh ? "机构更新成功" : "Org updated");
    loadOrgs();
  }

  async function handleDeleteOrg() {
    if (!deleteTarget) return;
    setDeleteTarget(null);
    try {
      await deleteOrg(deleteTarget.id);
      showToast("success", isZh ? "机构已删除" : "Org deleted");
      loadOrgs();
    } catch (e: any) { showToast("error", e.message); }
  }

  async function handleCreatePerm(data: Record<string, any>) {
    await createPermission(data);
    showToast("success", isZh ? "权限创建成功" : "Permission created");
    loadPermissions();
  }

  async function handleUpdatePerm(data: Record<string, any>) {
    await updatePermission(data.permissionId, data);
    showToast("success", isZh ? "权限更新成功" : "Permission updated");
    loadPermissions();
  }

  async function handleDeletePerm() {
    if (!deleteTarget) return;
    setDeleteTarget(null);
    try {
      await deletePermission(deleteTarget.id);
      showToast("success", isZh ? "权限已删除" : "Permission deleted");
      loadPermissions();
    } catch (e: any) { showToast("error", e.message); }
  }

  // ── Search handler (users) ─────────────────────────────────

  function handleUserSearch() { setUserPage(1); loadUsers(); }
  function handleKeyDown(e: React.KeyboardEvent) { if (e.key === "Enter") handleUserSearch(); }

  // ── Pagination ─────────────────────────────────────────────

  const totalPages = Math.max(1, Math.ceil(userTotal / pageSize));

  // ── Helper: flatten org tree ───────────────────────────────

  function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
    const result: (IamOrg & { _depth: number })[] = [];
    for (const node of nodes) {
      result.push({ ...node, _depth: depth });
      if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
    }
    return result;
  }

  // ── Render ─────────────────────────────────────────────────

  const th = "text-left px-3 py-2 text-[11px] font-semibold uppercase tracking-wider opacity-60 border-b border-gray-200 dark:border-gray-700/30";
  const td = "px-3 py-2 text-[13px] border-b border-gray-100 dark:border-gray-700/20";

  return (
    <div className="h-full flex flex-col p-6 space-y-4">
      {/* Toast */}
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}

      {/* Delete Confirm */}
      {deleteTarget && (
        <DeleteConfirm
          title={isZh ? "确认删除" : "Confirm Delete"}
          targetName={deleteTarget.name}
          onConfirm={() => {
            if (deleteTarget.tab === "users") handleDeleteUser();
            else if (deleteTarget.tab === "roles") handleDeleteRole();
            else if (deleteTarget.tab === "orgs") handleDeleteOrg();
            else if (deleteTarget.tab === "permissions") handleDeletePerm();
          }}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* User Form */}
      {userForm && (
        <UserFormModal
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
          onSave={roleForm.mode === "create" ? handleCreateRole : handleUpdateRole}
          onClose={() => setRoleForm(null)}
        />
      )}

      {/* Permission Panel */}
      {permPanelRole && (
        <PermissionPanel
          role={permPanelRole}
          allPermissions={permissions}
          onSave={handleSaveRolePermissions}
          onClose={() => setPermPanelRole(null)}
        />
      )}

      {/* Org Form */}
      {orgForm && (
        <OrgFormModal
          mode={orgForm.mode}
          org={orgForm.org}
          orgTree={orgTree}
          onSave={orgForm.mode === "create" ? handleCreateOrg : handleUpdateOrg}
          onClose={() => setOrgForm(null)}
        />
      )}

      {/* Permission Form */}
      {permForm && (
        <PermissionFormModal
          mode={permForm.mode}
          permission={permForm.perm}
          onSave={permForm.mode === "create" ? handleCreatePerm : handleUpdatePerm}
          onClose={() => setPermForm(null)}
        />
      )}

      {/* Reset Password Modal */}
      {resetPwdUser && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={() => setResetPwdUser(null)}>
          <div className={`rounded-lg border p-6 w-full max-w-sm ${styles.cardBg} ${styles.cardBorder}`} onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {isZh ? "重置密码" : "Reset Password"}
              </h3>
              <button onClick={() => setResetPwdUser(null)} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
            </div>
            <p className={`text-xs mb-3 ${styles.cardTextMuted}`}>
              {isZh ? "为用户" : "Reset password for"} <strong>{resetPwdUser.username}</strong> {isZh ? "设置新密码" : ""}
            </p>
            <input type="password" value={resetPwdPassword} onChange={e => setResetPwdPassword(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border mb-4 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={isZh ? "新密码 (至少6位)" : "New password (min 6 chars)"} autoFocus />
            <div className="flex justify-end gap-2">
              <button onClick={() => setResetPwdUser(null)}
                className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
                {isZh ? "取消" : "Cancel"}
              </button>
              <button onClick={handleResetPwd}
                className="px-4 py-2 rounded text-xs font-medium text-white bg-amber-500 hover:bg-amber-600 flex items-center gap-1.5">
                <Lock className="w-3 h-3" />
                {isZh ? "确认重置" : "Reset"}
              </button>
            </div>
          </div>
        </div>
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
                if (tab === "users") {
                  // Lazy-load org tree if needed
                  if (!orgTree.length) loadOrgs();
                  setUserForm({ mode: "create" });
                } else if (tab === "roles") {
                  setRoleForm({ mode: "create" });
                } else if (tab === "orgs") {
                  setOrgForm({ mode: "create" });
                }
              }}
              className={`flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              <Plus size={14} /> {isZh ? "新建" : "New"}
            </button>
          )}
          {tab === "permissions" && (
            <button
              onClick={() => setPermForm({ mode: "create" })}
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
                tab === t.id
                  ? `${styles.accentBg} text-white`
                  : "hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-600 dark:text-gray-400"
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

      {/* Users: Search bar */}
      {tab === "users" && (
        <div className="flex items-center gap-2">
          <div className="flex-1 flex items-center gap-2">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 opacity-40" />
              <input
                value={userSearch}
                onChange={e => setUserSearch(e.target.value)}
                onKeyDown={handleKeyDown}
                className={`w-full pl-8 pr-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                placeholder={isZh ? "搜索用户名、姓名或邮箱…" : "Search username, name or email…"}
              />
            </div>
            <button onClick={handleUserSearch}
              className={`px-3 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover}`}>
              {isZh ? "搜索" : "Search"}
            </button>
          </div>
          <span className={`text-xs ${styles.cardTextMuted}`}>
            {isZh ? `共 ${userTotal} 条` : `Total: ${userTotal}`}
          </span>
        </div>
      )}

      {/* Content: Table */}
      {loading ? (
        <div className={`text-sm ${styles.cardTextMuted} py-8 text-center`}>
          {isZh ? "加载中…" : "Loading…"}
        </div>
      ) : (
        <div className="overflow-auto flex-1">
          {/* ── Users Table ────────────────────────────────── */}
          {tab === "users" && (
            <table className="w-full border-collapse">
              <thead>
                <tr>
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
                  <tr key={u.userId} className="hover:bg-gray-50 dark:hover:bg-gray-800/20">
                    <td className={td}>
                      <span className="font-medium">{u.username}</span>
                    </td>
                    <td className={td}>{u.realName || "-"}</td>
                    <td className={td}><span className="text-xs opacity-70">{u.email || "-"}</span></td>
                    <td className={td}>
                      <span className="text-xs">{orgMap[u.orgId || ""] || u.orgId || "-"}</span>
                    </td>
                    <td className={td}>
                      <RoleTags userId={u.userId} allRoles={roles} />
                    </td>
                    <td className={td}>
                      <button
                        onClick={() => handleToggleStatus(u.userId, u.status)}
                        className={`px-2 py-0.5 rounded text-xs font-medium cursor-pointer border transition-colors ${
                          u.status === "ACTIVE"
                            ? "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400 border-green-200 dark:border-green-800 hover:bg-green-100 dark:hover:bg-green-900/30"
                            : "bg-red-50 dark:bg-red-900/20 text-red-500 dark:text-red-400 border-red-200 dark:border-red-800 hover:bg-red-100 dark:hover:bg-red-900/30"
                        }`}>
                        ● {u.status === "ACTIVE" ? (isZh ? "活跃" : "Active") : (isZh ? "禁用" : "Disabled")}
                      </button>
                    </td>
                    <td className={td}>
                      <div className="flex gap-1">
                        <button
                          onClick={async () => {
                            if (!orgTree.length) await loadOrgs();
                            setUserForm({ mode: "edit", user: u });
                          }}
                          className="text-indigo-500 hover:text-indigo-700 p-1" title={isZh ? "编辑" : "Edit"}>
                          <Edit3 size={14} />
                        </button>
                        <button
                          onClick={() => setResetPwdUser(u)}
                          className="text-amber-500 hover:text-amber-700 p-1" title={isZh ? "重置密码" : "Reset Pwd"}>
                          <Lock size={14} />
                        </button>
                        <button
                          onClick={() => setDeleteTarget({ tab: "users", id: u.userId, name: u.username })}
                          className="text-red-500 hover:text-red-700 p-1" title={isZh ? "删除" : "Delete"}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* ── Roles Table ────────────────────────────────── */}
          {tab === "roles" && (
            <table className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={th}>{isZh ? "角色名" : "Role Name"}</th>
                  <th className={th}>{isZh ? "编码" : "Code"}</th>
                  <th className={th}>{isZh ? "类型" : "Type"}</th>
                  <th className={th}>{isZh ? "描述" : "Description"}</th>
                  <th className={th}>{isZh ? "操作" : "Actions"}</th>
                </tr>
              </thead>
              <tbody>
                {roles.map(r => (
                  <tr key={r.roleId}
                    className="hover:bg-gray-50 dark:hover:bg-gray-800/20 cursor-pointer"
                    onClick={() => setPermPanelRole(r)}>
                    <td className={td}>
                      <span className="font-medium text-indigo-600 dark:text-indigo-400">{r.roleName}</span>
                    </td>
                    <td className={td}><code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{r.roleCode}</code></td>
                    <td className={td}>
                      <span className={`text-xs px-1.5 py-0.5 rounded ${
                        r.roleType === "SYSTEM"
                          ? "bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400"
                          : "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400"
                      }`}>{r.roleType}</span>
                    </td>
                    <td className={td}><span className="text-xs opacity-60 max-w-[200px] truncate block">{r.description || "-"}</span></td>
                    <td className={td}>
                      <div className="flex gap-1" onClick={e => e.stopPropagation()}>
                        <button onClick={() => setRoleForm({ mode: "edit", role: r })}
                          className="text-indigo-500 hover:text-indigo-700 p-1" title={isZh ? "编辑" : "Edit"}>
                          <Edit3 size={14} />
                        </button>
                        <button onClick={() => setDeleteTarget({ tab: "roles", id: r.roleId, name: r.roleName })}
                          className="text-red-500 hover:text-red-700 p-1" title={isZh ? "删除" : "Delete"}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* ── Orgs Tree ──────────────────────────────────── */}
          {tab === "orgs" && (
            <div className="border rounded overflow-hidden">
              <div className="flex items-center gap-2 px-3 py-2 border-b bg-gray-50 dark:bg-gray-800/30 text-[11px] font-semibold uppercase tracking-wider opacity-60">
                <span className="w-4" />
                <span className="flex-1">{isZh ? "机构名" : "Org Name"}</span>
                <span className="w-20 shrink-0">{isZh ? "编码" : "Code"}</span>
                <span className="w-16 shrink-0">{isZh ? "状态" : "Status"}</span>
                <span className="w-14 shrink-0">{isZh ? "类型" : "Type"}</span>
                <span className="w-[120px] shrink-0">{isZh ? "描述" : "Description"}</span>
                <span className="w-16 shrink-0">{isZh ? "操作" : "Actions"}</span>
              </div>
              {orgTree.map(o => (
                <OrgTreeRow
                  key={o.orgId}
                  org={o}
                  depth={0}
                  onEdit={(org) => setOrgForm({ mode: "edit", org })}
                  onDelete={(org) => setDeleteTarget({ tab: "orgs", id: org.orgId, name: org.orgName })}
                />
              ))}
            </div>
          )}

          {/* ── Permissions Table ──────────────────────────── */}
          {tab === "permissions" && (
            <table className="w-full border-collapse">
              <thead>
                <tr>
                  <th className={th}>{isZh ? "资源" : "Resource"}</th>
                  <th className={th}>{isZh ? "操作" : "Action"}</th>
                  <th className={th}>{isZh ? "条件" : "Condition"}</th>
                  <th className={th}>{isZh ? "描述" : "Description"}</th>
                  <th className={th}>{isZh ? "操作" : "Actions"}</th>
                </tr>
              </thead>
              <tbody>
                {permissions.map(p => (
                  <tr key={p.permissionId} className="hover:bg-gray-50 dark:hover:bg-gray-800/20">
                    <td className={td}>
                      <code className="text-[11px] bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded font-medium">{p.resource}</code>
                    </td>
                    <td className={td}>
                      <span className="text-xs font-mono">{p.action}</span>
                    </td>
                    <td className={td}>
                      <span className="text-xs opacity-50 font-mono">{p.conditionExpr || "-"}</span>
                    </td>
                    <td className={td}><span className="text-xs opacity-60 max-w-[200px] truncate block">{p.description || "-"}</span></td>
                    <td className={td}>
                      <div className="flex gap-1">
                        <button onClick={() => setPermForm({ mode: "edit", perm: p })}
                          className="text-indigo-500 hover:text-indigo-700 p-1" title={isZh ? "编辑" : "Edit"}>
                          <Edit3 size={14} />
                        </button>
                        <button
                          onClick={() => setDeleteTarget({ tab: "permissions", id: p.permissionId, name: `${p.resource}:${p.action}` })}
                          className="text-red-500 hover:text-red-700 p-1" title={isZh ? "删除" : "Delete"}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* Empty state */}
          {((tab === "users" && !users.length) ||
            (tab === "roles" && !roles.length) ||
            (tab === "orgs" && !orgTree.length) ||
            (tab === "permissions" && !permissions.length)) &&
            !loading && (
            <div className="text-center py-12 text-sm opacity-40">
              {isZh ? "暂无数据" : "No data"}
            </div>
          )}
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
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
              const start = Math.max(1, Math.min(userPage - 2, totalPages - 4));
              const page = start + i;
              if (page > totalPages) return null;
              return (
                <button key={page} onClick={() => setUserPage(page)}
                  className={`px-2.5 py-1 rounded text-xs border ${
                    page === userPage
                      ? `${styles.accentBg} text-white ${styles.accentBorder}`
                      : `${styles.cardBorder} ${styles.cardText}`
                  }`}>
                  {page}
                </button>
              );
            })}
            <button disabled={userPage >= totalPages} onClick={() => setUserPage(p => Math.min(totalPages, p + 1))}
              className={`px-2 py-1 rounded text-xs border ${styles.cardBorder} ${styles.cardText} disabled:opacity-30`}>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ── RoleTags (lazy-loads user roles for display) ──────────────

function RoleTags({ userId, allRoles }: { userId: string; allRoles: IamRole[] }) {
  const [roleIds, setRoleIds] = useState<string[] | null>(null);

  useEffect(() => {
    fetchUserRoles(userId).then(setRoleIds).catch(() => setRoleIds([]));
  }, [userId]);

  if (roleIds === null) {
    return <span className="text-xs opacity-30">…</span>;
  }

  const roleLabels = roleIds
    .map(id => allRoles.find(r => r.roleId === id)?.roleName)
    .filter(Boolean) as string[];

  if (roleLabels.length === 0) {
    return <span className="text-xs opacity-30">-</span>;
  }

  return (
    <div className="flex flex-wrap gap-1">
      {roleLabels.slice(0, 3).map((name, i) => (
        <span key={i} className="text-[10px] bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 px-1.5 py-0.5 rounded">
          {name}
        </span>
      ))}
      {roleLabels.length > 3 && (
        <span className="text-[10px] opacity-40">+{roleLabels.length - 3}</span>
      )}
    </div>
  );
}
