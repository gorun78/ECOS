/**
 * PermFormModal — 权限新建/编辑对话框（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import { useState } from "react";
import { X } from "lucide-react";
import type { IamPermission } from "../../api";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function PermFormModal({ mode, permission, onSave, onClose }: {
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
