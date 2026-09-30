/**
 * RoleFormModal — 角色新建/编辑对话框（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import { useState, useEffect } from "react";
import { X } from "lucide-react";
import { fetchRolePermissions } from "../../api";
import type { IamRole, IamPermission } from "../../api";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function RoleFormModal({ mode, role, allPermissions, onSave, onClose, onManagePermissions }: {
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
