import React, { useEffect, useState } from "react";
import { useLanguage } from "../../components/LanguageContext";
import { ChevronRight, ChevronDown, Search } from "lucide-react";

/** 将树形数据展平为带缩进的列表 */
function flattenTree(
  nodes: any[],
  depth: number = 0,
  idKey = "id",
  nameKey = "name",
  childrenKey = "children"
): { label: string; value: any; depth: number }[] {
  const result: { label: string; value: any; depth: number }[] = [];
  for (const node of nodes) {
    const prefix = depth > 0 ? "  ".repeat(depth) + "└ " : "";
    result.push({
      label: prefix + (node[nameKey] || node[idKey] || "?"),
      value: node[idKey],
      depth,
    });
    const children = node[childrenKey];
    if (children && children.length > 0) {
      result.push(...flattenTree(children, depth + 1, idKey, nameKey, childrenKey));
    }
  }
  return result;
}

export default function CrudDialog({
  open, dlgType, form, setForm, onSave, onClose, styles,
  goals, goalTree, orgTree, userList,
}: {
  open: boolean; dlgType: string;
  form: Record<string, any>; setForm: (f: Record<string, any>) => void;
  onSave: () => void; onClose: () => void;
  styles: any;
  goals?: any[];
  goalTree?: any[];
  orgTree?: any[];
  userList?: any[];
}) {
  const { locale } = useLanguage();
  const [userSearch, setUserSearch] = useState("");

  if (!open) return null;
  const isGoal = dlgType === "goals";
  const isScenario = dlgType === "scenarios";
  const isLink = dlgType === "causal-links";

  const parentOptions = goalTree ? flattenTree(goalTree, 0, "id", "name", "children") : 
    (goals || []).map(g => ({ label: g.name, value: g.id, depth: 0 }));
  const orgOptions = orgTree ? flattenTree(orgTree, 0, "orgId", "orgName", "children") : [];
  const filteredUsers = (userList || []).filter((u: any) =>
    !userSearch || (u.real_name || u.username || "").toLowerCase().includes(userSearch.toLowerCase())
  );

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-[1000]" onClick={onClose}>
      <div
        className={`w-full sm:w-[560px] mx-4 rounded-xl p-6 max-h-[90vh] overflow-y-auto border shadow-2xl ${styles.cardBg} ${styles.cardBorder}`}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-bold mb-4">
          {form.id ? (locale === "zh" ? "编辑" : "Edit") : (locale === "zh" ? "新建" : "New")}{" "}
          {isGoal ? (locale === "zh" ? "目标" : "Goal") : isScenario ? (locale === "zh" ? "情景" : "Scenario") : (locale === "zh" ? "因果链接" : "Causal Link")}
        </h2>

        <div className="space-y-3">
          {/* Code — auto-generated, read-only */}
          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none opacity-60 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            placeholder={locale === "zh" ? "编码（自动生成）" : "Code (auto-generated)"}
            value={form.code || ""}
            readOnly
          />

          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none transition-colors ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
            placeholder={locale === "zh" ? "名称 *" : "Name *"}
            value={form.name || ""}
            onChange={e => setForm({ ...form, name: e.target.value })}
          />
          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none transition-colors ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
            placeholder={locale === "zh" ? "描述" : "Description"}
            value={form.description || ""}
            onChange={e => setForm({ ...form, description: e.target.value })}
          />

          {isGoal && (
            <>
              {/* Goal type */}
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.goalType || ""}
                onChange={e => setForm({ ...form, goalType: e.target.value })}
              >
                <option value="">{locale === "zh" ? "选择目标类型" : "Select Goal Type"}</option>
                <option value="STRATEGIC">📌 {locale === "zh" ? "战略" : "Strategic"}</option>
                <option value="OKR">🏢 OKR</option>
                <option value="KPI">📋 KPI</option>
                <option value="WORKFLOW">🔗 {locale === "zh" ? "工作流" : "Workflow"}</option>
                <option value="AGENT">🤖 Agent</option>
                <option value="FINANCIAL">💰 {locale === "zh" ? "财务" : "Financial"}</option>
              </select>

              {/* Parent goal — tree view */}
              <div>
                <label className="text-xs opacity-60 mb-1 block">
                  {locale === "zh" ? "父目标" : "Parent Goal"}
                </label>
                <select
                  className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.parentId ?? ""}
                  onChange={e => setForm({ ...form, parentId: e.target.value ? Number(e.target.value) : null })}
                >
                  <option value="">{locale === "zh" ? "无（顶级目标）" : "None (top-level)"}</option>
                  {parentOptions
                    .filter(o => o.value !== form.id)
                    .map(o => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                </select>
              </div>

              {/* Quantitative metrics */}
              <div className="grid grid-cols-2 gap-2">
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="number" placeholder={locale === "zh" ? "目标值" : "Target Value"}
                  value={form.targetValue ?? ""}
                  onChange={e => setForm({ ...form, targetValue: parseFloat(e.target.value) || 0 })}
                />
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="number" placeholder={locale === "zh" ? "当前值" : "Current Value"}
                  value={form.currentValue ?? ""}
                  onChange={e => setForm({ ...form, currentValue: parseFloat(e.target.value) || 0 })}
                />
              </div>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={locale === "zh" ? "单位 (如 %, pts, 万元)" : "Unit (e.g. %, pts)"}
                value={form.unit || ""}
                onChange={e => setForm({ ...form, unit: e.target.value })}
              />
              <div className="grid grid-cols-2 gap-2">
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="number" placeholder={locale === "zh" ? "权重" : "Weight"} step="0.1" min="0" max="1"
                  value={form.weight ?? ""}
                  onChange={e => setForm({ ...form, weight: parseFloat(e.target.value) || 0 })}
                />
                <select
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.status || "ACTIVE"}
                  onChange={e => setForm({ ...form, status: e.target.value })}
                >
                  <option value="ACTIVE">{locale === "zh" ? "活跃" : "Active"}</option>
                  <option value="COMPLETED">{locale === "zh" ? "已完成" : "Completed"}</option>
                  <option value="CANCELLED">{locale === "zh" ? "已取消" : "Cancelled"}</option>
                  <option value="on_track">{locale === "zh" ? "进展中" : "On Track"}</option>
                  <option value="at_risk">{locale === "zh" ? "有风险" : "At Risk"}</option>
                  <option value="behind">{locale === "zh" ? "落后" : "Behind"}</option>
                </select>
              </div>

              {/* Org — tree select */}
              <div>
                <label className="text-xs opacity-60 mb-1 block">
                  {locale === "zh" ? "所属组织" : "Organization"}
                </label>
                <select
                  className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.orgId || ""}
                  onChange={e => setForm({ ...form, orgId: e.target.value || null })}
                >
                  <option value="">{locale === "zh" ? "不指定" : "None"}</option>
                  {orgOptions.map(o => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Owner — searchable user select */}
              <div>
                <label className="text-xs opacity-60 mb-1 block">
                  {locale === "zh" ? "负责人" : "Owner"}
                </label>
                <div className="relative mb-1">
                  <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 opacity-40" />
                  <input
                    className={`w-full pl-8 pr-3 py-1.5 rounded border text-xs outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder={locale === "zh" ? "搜索用户..." : "Search users..."}
                    value={userSearch}
                    onChange={e => setUserSearch(e.target.value)}
                  />
                </div>
                <select
                  className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.ownerUserId || ""}
                  onChange={e => setForm({ ...form, ownerUserId: e.target.value || null })}
                  size={Math.min(6, Math.max(2, filteredUsers.length))}
                >
                  <option value="">{locale === "zh" ? "不指定" : "None"}</option>
                  {filteredUsers.map((u: any) => (
                    <option key={u.user_id || u.id} value={u.user_id || u.id}>
                      {u.real_name || u.username} {u.org_name ? `[${u.org_name}]` : ""}
                    </option>
                  ))}
                </select>
              </div>

              {/* Dates — defaults to this year */}
              <div className="grid grid-cols-2 gap-2">
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="date" placeholder={locale === "zh" ? "开始日期" : "Start Date"}
                  value={form.startDate || ""}
                  onChange={e => setForm({ ...form, startDate: e.target.value })}
                />
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="date" placeholder={locale === "zh" ? "结束日期" : "End Date"}
                  value={form.endDate || ""}
                  onChange={e => setForm({ ...form, endDate: e.target.value })}
                />
              </div>
            </>
          )}

          {isScenario && (
            <>
              <div className="space-y-1">
                <span className="text-xs opacity-60">
                  {locale === "zh" ? "概率" : "Probability"}: {Math.round((form.probability || 0.5) * 100)}%
                </span>
                <input type="range" min="0" max="1" step="0.05"
                  value={form.probability || 0.5}
                  onChange={e => setForm({ ...form, probability: parseFloat(e.target.value) })}
                  className="w-full" />
              </div>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                type="number" placeholder={locale === "zh" ? "影响分" : "Impact Score"}
                value={form.impactScore ?? ""}
                onChange={e => setForm({ ...form, impactScore: parseInt(e.target.value) || 0 })}
              />
            </>
          )}

          {isLink && (
            <>
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.sourceType || "GOAL"}
                onChange={e => setForm({ ...form, sourceType: e.target.value })}
              >
                <option value="GOAL">{locale === "zh" ? "源类型: 目标" : "Source: Goal"}</option>
                <option value="SCENARIO">{locale === "zh" ? "源类型: 情景" : "Source: Scenario"}</option>
              </select>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={locale === "zh" ? "源ID" : "Source ID"}
                value={form.sourceId || ""}
                onChange={e => setForm({ ...form, sourceId: e.target.value })}
              />
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.targetType || "GOAL"}
                onChange={e => setForm({ ...form, targetType: e.target.value })}
              >
                <option value="GOAL">{locale === "zh" ? "目标类型: 目标" : "Target: Goal"}</option>
                <option value="SCENARIO">{locale === "zh" ? "目标类型: 情景" : "Target: Scenario"}</option>
              </select>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={locale === "zh" ? "目标ID" : "Target ID"}
                value={form.targetId || ""}
                onChange={e => setForm({ ...form, targetId: e.target.value })}
              />
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.relationType || "ENABLES"}
                onChange={e => setForm({ ...form, relationType: e.target.value })}
              >
                <option value="ENABLES">{locale === "zh" ? "促进" : "Enables"}</option>
                <option value="INHIBITS">{locale === "zh" ? "抑制" : "Inhibits"}</option>
                <option value="CORRELATES">{locale === "zh" ? "相关" : "Correlates"}</option>
                <option value="CAUSES">{locale === "zh" ? "因果" : "Causes"}</option>
              </select>
              <div className="space-y-1">
                <span className="text-xs opacity-60">
                  {locale === "zh" ? "强度" : "Strength"}: {form.strength || 0.5}
                </span>
                <input type="range" min="0" max="1" step="0.05"
                  value={form.strength || 0.5}
                  onChange={e => setForm({ ...form, strength: parseFloat(e.target.value) })}
                  className="w-full" />
              </div>
            </>
          )}
        </div>

        <div className="flex gap-2 mt-6">
          <button onClick={onSave}
            className={`flex-1 px-4 py-2 rounded text-sm font-medium transition-all ${styles.accentBg} ${styles.accentHover} text-white`}>
            {locale === "zh" ? "保存" : "Save"}
          </button>
          <button onClick={onClose}
            className={`flex-1 px-4 py-2 rounded border text-sm font-medium transition-all ${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}>
            {locale === "zh" ? "取消" : "Cancel"}
          </button>
        </div>
      </div>
    </div>
  );
}
