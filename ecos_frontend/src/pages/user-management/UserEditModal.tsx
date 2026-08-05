/**
 * UserEditModal — 用户编辑弹窗 (三Tab: 基本信息 / 角色绑定 / 安全配置)
 * + 密码强度指示器 (创建模式)
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  X, Check, User, Shield, Key, AlertCircle,
  Lock, Unlock,
} from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { fetchUserRoles } from "../../api";
import type { IamUser, IamRole, IamOrg } from "../../api";
import UserRoleBinding from "./UserRoleBinding";

type EditTab = "basic" | "roles" | "security";

interface UserEditModalProps {
  mode: "create" | "edit";
  user?: IamUser | null;
  allRoles: IamRole[];
  orgTree: IamOrg[];
  onSave: (data: Record<string, any>, roleIds: string[]) => Promise<void>;
  onClose: () => void;
}

function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
  const result: (IamOrg & { _depth: number })[] = [];
  for (const node of nodes) {
    result.push({ ...node, _depth: depth });
    if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
  }
  return result;
}

export default function UserEditModal({
  mode,
  user,
  allRoles,
  orgTree,
  onSave,
  onClose,
}: UserEditModalProps) {
  const { locale, t } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [tab, setTab] = useState<EditTab>("basic");

  // Form state
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

  // Load user's existing roles for edit mode
  useEffect(() => {
    if (mode === "edit" && user?.userId) {
      fetchUserRoles(user.userId)
        .then(setSelectedRoleIds)
        .catch(() => setSelectedRoleIds([]));
    }
  }, [mode, user]);

  function update(k: string, v: string) {
    setForm((f) => ({ ...f, [k]: v }));
  }

  // ── Password strength ─────────────────────────────────
  const passwordStrength = React.useMemo(() => {
    const pwd = form.password || "";
    if (!pwd) return 0; // no password entered
    const hasDigit = /\d/.test(pwd);
    const hasUpper = /[A-Z]/.test(pwd);
    const hasSpecial = /[^a-zA-Z0-9]/.test(pwd);
    const checks = [hasDigit, hasUpper, hasSpecial].filter(Boolean).length;

    if (pwd.length < 8) return 1; // weak
    if (pwd.length >= 8 && checks >= 3) return 3; // strong
    if (pwd.length >= 8 && checks >= 2) return 2; // medium
    return 1; // weak fallback
  }, [form.password]);

  const strengthInfo: Record<number, { label: string; color: string }> = {
    1: { label: t("user.password.strength.weak"), color: "bg-red-500" },
    2: { label: t("user.password.strength.medium"), color: "bg-amber-500" },
    3: { label: t("user.password.strength.strong"), color: "bg-green-500" },
  };

  function toggleRole(roleId: string) {
    setSelectedRoleIds((prev) =>
      prev.includes(roleId) ? prev.filter((id) => id !== roleId) : [...prev, roleId]
    );
  }

  function selectAllRoles() {
    setSelectedRoleIds(allRoles.map((r) => r.roleId));
  }

  function clearAllRoles() {
    setSelectedRoleIds([]);
  }

  async function handleSave() {
    if (!form.username?.trim()) {
      setError(isZh ? "用户名不能为空" : "Username is required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave(form, selectedRoleIds);
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  }

  const tabs: { id: EditTab; label: string; icon: React.FC<any> }[] = [
    { id: "basic", label: isZh ? "基本信息" : "Basic", icon: User },
    { id: "roles", label: isZh ? "角色绑定" : "Roles", icon: Shield },
    ...(mode === "edit"
      ? [{ id: "security" as EditTab, label: isZh ? "安全配置" : "Security", icon: Key }]
      : []),
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div
        className={`rounded-lg border w-full max-w-xl max-h-[85vh] flex flex-col ${styles.cardBg} ${styles.cardBorder}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-5 pb-2">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create"
              ? isZh ? "新建用户" : "Create User"
              : isZh ? "编辑用户" : "Edit User"}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Error */}
        {error && (
          <div className="mx-6 mb-2 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs flex items-center gap-1.5">
            <AlertCircle className="w-3.5 h-3.5 shrink-0" />
            {error}
          </div>
        )}

        {/* Tabs */}
        <div className={`flex gap-1 px-6 border-b ${styles.appBorder}`}>
          {tabs.map((t) => {
            const Icon = t.icon;
            return (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs rounded-t transition-colors ${
                  tab === t.id
                    ? `${styles.accentBg} text-white`
                    : "hover:bg-gray-100 dark:hover:bg-gray-800 text-gray-500 dark:text-gray-400"
                }`}
              >
                <Icon size={13} />
                {t.label}
              </button>
            );
          })}
        </div>

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto px-6 py-4 min-h-0">
          {/* Basic Info Tab */}
          {tab === "basic" && (
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                    {isZh ? "用户名 *" : "Username *"}
                  </label>
                  <input
                    value={form.username}
                    onChange={(e) => update("username", e.target.value)}
                    className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder={isZh ? "登录用户名" : "Login username"}
                  />
                </div>
                <div>
                  <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                    {isZh ? "真实姓名" : "Real Name"}
                  </label>
                  <input
                    value={form.realName}
                    onChange={(e) => update("realName", e.target.value)}
                    className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder={isZh ? "真实姓名" : "Real name"}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                    {isZh ? "邮箱" : "Email"}
                  </label>
                  <input
                    value={form.email}
                    onChange={(e) => update("email", e.target.value)}
                    className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder="email@example.com"
                  />
                </div>
                <div>
                  <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                    {isZh ? "手机" : "Phone"}
                  </label>
                  <input
                    value={form.phone}
                    onChange={(e) => update("phone", e.target.value)}
                    className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder="13800138000"
                  />
                </div>
              </div>
              <div>
                <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                  {isZh ? "所属组织" : "Organization"}
                </label>
                <select
                  value={form.orgId}
                  onChange={(e) => update("orgId", e.target.value)}
                  className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                >
                  <option value="">
                    -- {isZh ? "选择组织" : "Select Org"} --
                  </option>
                  {flattenTree(orgTree).map((o) => (
                    <option key={o.orgId} value={o.orgId}>
                      {"\u00A0\u00A0".repeat(o._depth)}
                      {o.orgName} ({o.orgCode})
                    </option>
                  ))}
                </select>
              </div>
              {mode === "create" && (
                <div>
                  <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                    {isZh ? "密码" : "Password"}
                  </label>
                  <input
                    type="password"
                    value={form.password}
                    onChange={(e) => update("password", e.target.value)}
                    className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder={isZh ? "留空则使用默认密码" : "Leave empty for default"}
                  />
                  {/* ── Password strength indicator ────── */}
                  {form.password && (
                    <div className="mt-2">
                      <div className="flex gap-1 mb-1">
                        {[1, 2, 3].map((level) => (
                          <div
                            key={level}
                            className={`h-1 flex-1 rounded-full transition-colors ${
                              passwordStrength >= level
                                ? strengthInfo[passwordStrength]?.color || "bg-gray-300"
                                : "bg-gray-200 dark:bg-gray-700"
                            }`}
                          />
                        ))}
                      </div>
                      <span className={`text-[10px] ${
                        passwordStrength >= 3 ? "text-green-600 dark:text-green-400" :
                        passwordStrength >= 2 ? "text-amber-600 dark:text-amber-400" :
                        "text-red-500 dark:text-red-400"
                      }`}>
                        {strengthInfo[passwordStrength]?.label || ""}
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Roles Tab */}
          {tab === "roles" && (
            <UserRoleBinding
              allRoles={allRoles}
              selectedRoleIds={selectedRoleIds}
              onToggle={toggleRole}
              onSelectAll={selectAllRoles}
              onClearAll={clearAllRoles}
            />
          )}

          {/* Security Tab (edit only) */}
          {tab === "security" && mode === "edit" && user && (
            <div className="space-y-4">
              <div className={`p-4 rounded-lg border ${styles.cardBorder}`}>
                <div className="flex items-center justify-between">
                  <div>
                    <div className={`text-xs mb-1 ${styles.cardTextMuted}`}>
                      {isZh ? "账户状态" : "Account Status"}
                    </div>
                    <div
                      className={`text-sm font-semibold ${
                        user.status === "ACTIVE"
                          ? "text-green-600 dark:text-green-400"
                          : "text-red-500 dark:text-red-400"
                      }`}
                    >
                      ●{" "}
                      {user.status === "ACTIVE"
                        ? isZh ? "正常" : "Active"
                        : isZh ? "锁定" : "Locked"}
                    </div>
                  </div>
                  <div
                    className={`px-2 py-1 rounded text-xs font-medium ${
                      user.status === "ACTIVE"
                        ? "bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400"
                        : "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400"
                    }`}
                  >
                    {user.status === "ACTIVE" ? (
                      <Lock className="w-3 h-3 inline mr-1" />
                    ) : (
                      <Unlock className="w-3 h-3 inline mr-1" />
                    )}
                    {user.status === "ACTIVE"
                      ? isZh ? "可被锁定" : "Can be locked"
                      : isZh ? "可被解锁" : "Can be unlocked"}
                  </div>
                </div>
              </div>

              <div className={`p-4 rounded-lg border ${styles.cardBorder} space-y-3`}>
                <div className="grid grid-cols-2 gap-3 text-xs">
                  <div>
                    <span className="opacity-50">{isZh ? "用户ID" : "User ID"}:</span>
                    <div className="font-mono text-[11px] mt-0.5 opacity-70">
                      {user.userId}
                    </div>
                  </div>
                  <div>
                    <span className="opacity-50">{isZh ? "最后登录" : "Last Login"}:</span>
                    <div className="mt-0.5 opacity-70">
                      {user.lastLoginTime || "-"}
                    </div>
                  </div>
                  <div>
                    <span className="opacity-50">{isZh ? "创建时间" : "Created"}:</span>
                    <div className="mt-0.5 opacity-70">
                      {user.createdTime || "-"}
                    </div>
                  </div>
                  <div>
                    <span className="opacity-50">{isZh ? "锁定状态" : "Lock State"}:</span>
                    <div className="mt-0.5 opacity-70">
                      {user.locked === "1" ? (isZh ? "已锁定" : "Locked") : (isZh ? "未锁定" : "Unlocked")}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 px-6 pb-5 pt-2">
          <button
            onClick={onClose}
            className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}
          >
            {isZh ? "取消" : "Cancel"}
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}
          >
            <Check className="w-3.5 h-3.5" />
            {saving ? (isZh ? "保存中…" : "Saving…") : (isZh ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}
