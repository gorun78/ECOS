/**
 * OrgFormModal — 机构新建/编辑对话框（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import { useState } from "react";
import { X } from "lucide-react";
import type { IamOrg } from "../../api";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

function flattenTree(nodes: IamOrg[], depth = 0): (IamOrg & { _depth: number })[] {
  const result: (IamOrg & { _depth: number })[] = [];
  for (const node of nodes) {
    result.push({ ...node, _depth: depth });
    if (node.children?.length) result.push(...flattenTree(node.children, depth + 1));
  }
  return result;
}

export default function OrgFormModal({ mode, org, orgTree, onSave, onClose }: {
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
