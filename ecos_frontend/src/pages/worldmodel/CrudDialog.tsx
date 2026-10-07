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
  const { t } = useLanguage();
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
          {form.id ? t("platform.wm.crud.edit") : t("platform.wm.crud.new")}{" "}
          {isGoal ? t("platform.wm.crud.entity.goal") : isScenario ? t("platform.wm.crud.entity.scenario") : t("platform.wm.crud.entity.causalLink")}
        </h2>

        <div className="space-y-3">
          {/* Code — auto-generated, read-only */}
          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none opacity-60 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            placeholder={t("platform.wm.crud.code")}
            value={form.code || ""}
            readOnly
          />

          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none transition-colors ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
            placeholder={t("platform.wm.crud.name")}
            value={form.name || ""}
            onChange={e => setForm({ ...form, name: e.target.value })}
          />
          <input
            className={`w-full px-3 py-2 rounded border text-sm outline-none transition-colors ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
            placeholder={t("platform.wm.crud.description")}
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
                <option value="">{t("platform.wm.crud.goalTypeSelect")}</option>
                <option value="STRATEGIC">📌 {t("platform.wm.crud.goalTypeStrategic")}</option>
                <option value="OKR">🏢 OKR</option>
                <option value="KPI">📋 KPI</option>
                <option value="WORKFLOW">🔗 {t("platform.wm.crud.goalTypeWorkflow")}</option>
                <option value="AGENT">🤖 Agent</option>
                <option value="FINANCIAL">💰 {t("platform.wm.crud.goalTypeFinancial")}</option>
              </select>

              {/* Parent goal — tree view */}
              <div>
                <label className="text-xs opacity-60 mb-1 block">
                  {t("platform.wm.crud.parentGoal")}
                </label>
                <select
                  className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.parentId ?? ""}
                  onChange={e => setForm({ ...form, parentId: e.target.value ? Number(e.target.value) : null })}
                >
                  <option value="">{t("platform.wm.crud.parentNone")}</option>
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
                  type="number" placeholder={t("platform.wm.crud.targetValue")}
                  value={form.targetValue ?? ""}
                  onChange={e => setForm({ ...form, targetValue: parseFloat(e.target.value) || 0 })}
                />
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="number" placeholder={t("platform.wm.crud.currentValue")}
                  value={form.currentValue ?? ""}
                  onChange={e => setForm({ ...form, currentValue: parseFloat(e.target.value) || 0 })}
                />
              </div>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={t("platform.wm.crud.unit")}
                value={form.unit || ""}
                onChange={e => setForm({ ...form, unit: e.target.value })}
              />
              <div className="grid grid-cols-2 gap-2">
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="number" placeholder={t("platform.wm.crud.weight")} step="0.1" min="0" max="1"
                  value={form.weight ?? ""}
                  onChange={e => setForm({ ...form, weight: parseFloat(e.target.value) || 0 })}
                />
                <select
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.status || "ACTIVE"}
                  onChange={e => setForm({ ...form, status: e.target.value })}
                >
                  <option value="ACTIVE">{t("platform.wm.crud.statusActive")}</option>
                  <option value="COMPLETED">{t("platform.wm.crud.statusCompleted")}</option>
                  <option value="CANCELLED">{t("platform.wm.crud.statusCancelled")}</option>
                  <option value="on_track">{t("platform.wm.crud.statusOnTrack")}</option>
                  <option value="at_risk">{t("platform.wm.crud.statusAtRisk")}</option>
                  <option value="behind">{t("platform.wm.crud.statusBehind")}</option>
                </select>
              </div>

              {/* Org — tree select */}
              <div>
                <label className="text-xs opacity-60 mb-1 block">
                  {t("platform.wm.crud.organization")}
                </label>
                <select
                  className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                  value={form.orgId || ""}
                  onChange={e => setForm({ ...form, orgId: e.target.value || null })}
                >
                  <option value="">{t("platform.wm.crud.none")}</option>
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
                  {t("platform.wm.crud.owner")}
                </label>
                <div className="relative mb-1">
                  <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 opacity-40" />
                  <input
                    className={`w-full pl-8 pr-3 py-1.5 rounded border text-xs outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                    placeholder={t("platform.wm.crud.ownerSearch")}
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
                  <option value="">{t("platform.wm.crud.none")}</option>
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
                  type="date" placeholder={t("platform.wm.crud.startDate")}
                  value={form.startDate || ""}
                  onChange={e => setForm({ ...form, startDate: e.target.value })}
                />
                <input
                  className={`px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                  type="date" placeholder={t("platform.wm.crud.endDate")}
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
                  {t("platform.wm.crud.probability")}: {Math.round((form.probability || 0.5) * 100)}%
                </span>
                <input type="range" min="0" max="1" step="0.05"
                  value={form.probability || 0.5}
                  onChange={e => setForm({ ...form, probability: parseFloat(e.target.value) })}
                  className="w-full" />
              </div>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                type="number" placeholder={t("platform.wm.crud.impactScore")}
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
                <option value="GOAL">{t("platform.wm.crud.sourceGoal")}</option>
                <option value="SCENARIO">{t("platform.wm.crud.sourceScenario")}</option>
              </select>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={t("platform.wm.crud.sourceId")}
                value={form.sourceId || ""}
                onChange={e => setForm({ ...form, sourceId: e.target.value })}
              />
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.targetType || "GOAL"}
                onChange={e => setForm({ ...form, targetType: e.target.value })}
              >
                <option value="GOAL">{t("platform.wm.crud.targetGoal")}</option>
                <option value="SCENARIO">{t("platform.wm.crud.targetScenario")}</option>
              </select>
              <input
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder} focus:border-indigo-500`}
                placeholder={t("platform.wm.crud.targetId")}
                value={form.targetId || ""}
                onChange={e => setForm({ ...form, targetId: e.target.value })}
              />
              <select
                className={`w-full px-3 py-2 rounded border text-sm outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
                value={form.relationType || "ENABLES"}
                onChange={e => setForm({ ...form, relationType: e.target.value })}
              >
                <option value="ENABLES">{t("platform.wm.crud.relationEnables")}</option>
                <option value="INHIBITS">{t("platform.wm.crud.relationInhibits")}</option>
                <option value="CORRELATES">{t("platform.wm.crud.relationCorrelates")}</option>
                <option value="CAUSES">{t("platform.wm.crud.relationCauses")}</option>
              </select>
              <div className="space-y-1">
                <span className="text-xs opacity-60">
                  {t("platform.wm.crud.strength")}: {form.strength || 0.5}
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
            {t("platform.wm.crud.save")}
          </button>
          <button onClick={onClose}
            className={`flex-1 px-4 py-2 rounded border text-sm font-medium transition-all ${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}>
            {t("platform.wm.crud.cancel")}
          </button>
        </div>
      </div>
    </div>
  );
}
