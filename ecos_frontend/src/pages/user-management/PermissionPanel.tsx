/**
 * PermissionPanel — 角色权限分配面板（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import { useState, useEffect } from "react";
import { X, ChevronRight, ChevronLeft, ArrowLeftRight } from "lucide-react";
import { fetchRolePermissions } from "../../api";
import type { IamRole, IamPermission } from "../../api";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function PermissionPanel({ role, allPermissions, onSave, onClose }: {
  role: IamRole; allPermissions: IamPermission[];
  onSave: (permIds: string[]) => Promise<void>; onClose: () => void;
}) {
  const { t } = useLanguage(); const { styles } = useTheme();
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
        <div className={`text-sm ${styles.cardTextMuted}`}>{t("platform.user.permpanel.loading")}</div>
      </div>
    </div>);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-3xl max-h-[80vh] flex flex-col ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-3">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("platform.user.permpanel.title")} — {role.roleName}</h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        <div className="flex-1 flex gap-3 min-h-0">
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>{t("platform.user.permpanel.available")} ({available.length})</div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {available.map(p => (
                <div key={p.permissionId} onClick={() => { setAvailable(prev => prev.filter(x => x.permissionId !== p.permissionId)); setAssigned(prev => [...prev, p]); }}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer hover:bg-indigo-50 dark:hover:bg-indigo-900/20">
                  <span className="truncate"><code className={`text-[11px] ${styles.sidebarBg} px-1 rounded mr-1.5`}>{p.resource}</code><span className="opacity-70">{p.action}</span></span>
                  <ChevronRight className="w-3 h-3 opacity-30 shrink-0" /></div>))}
              {!available.length && <div className="text-xs opacity-30 text-center py-4">{t("platform.user.permpanel.noAvailable")}</div>}
            </div>
          </div>
          <div className="flex flex-col justify-center gap-2 shrink-0">
            <ArrowLeftRight className="w-4 h-4 opacity-30" />
          </div>
          <div className="flex-1 flex flex-col min-w-0">
            <div className={`text-xs font-medium mb-1.5 ${styles.cardTextMuted}`}>{t("platform.user.permpanel.assigned")} ({assigned.length})</div>
            <div className={`flex-1 overflow-y-auto border rounded p-1 space-y-0.5 ${styles.cardBorder}`}>
              {assigned.map(p => (
                <div key={p.permissionId} onClick={() => { setAssigned(prev => prev.filter(x => x.permissionId !== p.permissionId)); setAvailable(prev => [...prev, p]); }}
                  className="flex items-center justify-between px-2 py-1.5 rounded text-xs cursor-pointer bg-indigo-50 dark:bg-indigo-900/20 hover:bg-indigo-100 dark:hover:bg-indigo-900/40">
                  <span className="truncate"><code className="text-[11px] bg-indigo-100 dark:bg-indigo-800 px-1 rounded mr-1.5">{p.resource}</code><span className="opacity-70">{p.action}</span></span>
                  <ChevronLeft className="w-3 h-3 opacity-30 shrink-0" /></div>))}
              {!assigned.length && <div className="text-xs opacity-30 text-center py-4">{t("platform.user.permpanel.noneAssigned")}</div>}
            </div>
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-3">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>{t("platform.user.permpanel.cancel")}</button>
          <button onClick={async () => { setSaving(true); try { await onSave(assigned.map(p => p.permissionId)); onClose(); } catch {} finally { setSaving(false); } }}
            disabled={saving} className={`px-4 py-2 rounded text-xs font-medium text-white ${styles.accentBg} ${styles.accentHover} disabled:opacity-50`}>
            {saving ? t("platform.user.permpanel.saving") : t("platform.user.permpanel.save")}</button>
        </div>
      </div>
    </div>
  );
}
