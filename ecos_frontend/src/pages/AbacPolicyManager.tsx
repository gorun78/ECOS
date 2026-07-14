/**
 * ABAC Policy Manager — ECOS P1-5
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Shield, Plus, Search, Trash2, Edit3, X, AlertTriangle, Loader2, ChevronLeft, ChevronRight
} from "lucide-react";
import {
  fetchAbacPolicies, createAbacPolicy, updateAbacPolicy, deleteAbacPolicy,
  type AbacPolicy, type AbacPolicyListResponse
} from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

export default function AbacPolicyManager() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [policies, setPolicies] = useState<AbacPolicy[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [keyword, setKeyword] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<AbacPolicy | null>(null);
  const [form, setForm] = useState({ name: "", resource: "", action: "allow", effect: "allow", conditionExpression: "", priority: 100, scopeType: "GLOBAL", scopeId: "" });
  const [saving, setSaving] = useState(false);

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<AbacPolicy | null>(null);

  const loadPolicies = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res: AbacPolicyListResponse = await fetchAbacPolicies(keyword, page, pageSize);
      setPolicies(res.data || []);
      setTotal(res.total || 0);
    } catch (e: any) {
      setError(e.message || "Failed to load policies");
      setPolicies([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [keyword, page, pageSize]);

  useEffect(() => {
    loadPolicies();
  }, [loadPolicies]);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setKeyword(searchInput);
    setPage(1);
  };

  const openCreate = () => {
    setEditingPolicy(null);
    setForm({ name: "", resource: "", action: "allow", effect: "allow", conditionExpression: "", priority: 100, scopeType: "GLOBAL", scopeId: "" });
    setModalOpen(true);
  };

  const openEdit = (policy: AbacPolicy) => {
    setEditingPolicy(policy);
    setForm({
      name: policy.name || "",
      resource: policy.resource || "",
      action: policy.action || "allow",
      effect: policy.effect || "allow",
      conditionExpression: policy.conditionExpression || "",
      priority: policy.priority ?? 100,
      scopeType: policy.scopeType || "GLOBAL",
      scopeId: policy.scopeId || "",
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (editingPolicy) {
        await updateAbacPolicy(editingPolicy.id, form);
      } else {
        await createAbacPolicy(form);
      }
      setModalOpen(false);
      loadPolicies();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteAbacPolicy(deleteTarget.id);
      setDeleteTarget(null);
      loadPolicies();
    } catch (e: any) {
      setError(e.message || "Delete failed");
      setDeleteTarget(null);
    }
  };

  const L = (zh: string, en: string) => locale === "zh" ? zh : en;

  return (
    <div className={`flex-1 overflow-y-auto p-5 flex flex-col h-full font-sans ${styles.appBg} ${styles.appText}`}>
      {/* Header */}
      <div className="flex justify-between items-center mb-5 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight flex items-center gap-2 ${styles.cardText}`}>
            <Shield className="text-blue-500 w-5 h-5 shrink-0" />
            {L("ABAC 策略管理", "ABAC Policy Manager")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
            {L("基于属性的访问控制策略 — 创建、编辑与管理", "Attribute-Based Access Control — create, edit & manage policies")}
          </p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-1.5 px-3.5 py-2 bg-blue-600 hover:bg-blue-700 text-white text-xs font-medium rounded-md transition-colors"
        >
          <Plus className="w-3.5 h-3.5" />
          {L("新建策略", "New Policy")}
        </button>
      </div>

      {/* Search bar */}
      <form onSubmit={handleSearch} className="flex items-center gap-2 mb-4 shrink-0">
        <div className="relative flex-1 max-w-md">
          <Search className={`absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 ${styles.cardTextMuted}`} />
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder={L("搜索策略名称或资源...", "Search policy name or resource...")}
            className={`w-full pl-9 pr-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} placeholder:${styles.cardTextMuted}`}
          />
        </div>
        <button
          type="submit"
          className={`px-3 py-2 border rounded-md transition-colors text-xs ${styles.inputBg} ${styles.inputBorder} ${styles.cardText} hover:${styles.cardBorder}`}
        >
          {L("搜索", "Search")}
        </button>
        {keyword && (
          <button
            onClick={() => { setSearchInput(""); setKeyword(""); setPage(1); }}
            className={`px-3 py-2 text-xs transition-colors ${styles.cardTextMuted} hover:${styles.cardText}`}
          >
            {L("清除", "Clear")}
          </button>
        )}
      </form>

      {/* Error banner */}
      {error && (
        <div className="flex items-center gap-2 mb-4 p-3 bg-red-900/30 border border-red-800/50 rounded-md text-xs text-red-300">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      {/* Table */}
      <div className={`flex-1 min-h-0 overflow-auto rounded-md border ${styles.cardBg} ${styles.cardBorder}`}>
        {loading ? (
          <div className={`flex items-center justify-center h-40 ${styles.cardTextMuted}`}>
            <Loader2 className="w-5 h-5 animate-spin mr-2" />
            {L("加载中...", "Loading...")}
          </div>
        ) : policies.length === 0 ? (
          <div className={`flex flex-col items-center justify-center h-40 text-xs gap-2 ${styles.cardTextMuted}`}>
            <Shield className="w-8 h-8 opacity-30" />
            {L("暂无 ABAC 策略", "No ABAC policies found")}
          </div>
        ) : (
          <table className="w-full text-xs">
            <thead>
              <tr className={`border-b uppercase tracking-wider text-[10px] ${styles.cardBorder} ${styles.cardTextMuted}`}>
                <th className="text-left px-4 py-3 font-medium">{L("策略名称", "Name")}</th>
                <th className="text-left px-4 py-3 font-medium">{L("资源", "Resource")}</th>
                <th className="text-left px-4 py-3 font-medium">{L("操作", "Action")}</th>
                <th className="text-left px-4 py-3 font-medium">{L("效果", "Effect")}</th>
                <th className="text-left px-4 py-3 font-medium">{L("优先级", "Priority")}</th>
                <th className="text-left px-4 py-3 font-medium">{L("作用域", "Scope")}</th>
                <th className="text-right px-4 py-3 font-medium">{L("操作", "Actions")}</th>
              </tr>
            </thead>
            <tbody className={`divide-y ${styles.cardBorder}`}>
              {policies.map((p) => (
                <tr key={p.id} className="hover:bg-white/5 transition-colors">
                  <td className={`px-4 py-3 font-medium ${styles.cardText}`}>{p.name}</td>
                  <td className={`px-4 py-3 font-mono text-[11px] ${styles.cardTextMuted}`}>{p.resource}</td>
                  <td className={`px-4 py-3 ${styles.cardText}`}>{p.action}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                      p.effect === "allow" || p.effect === "ALLOW"
                        ? "bg-green-900/40 text-green-400 border border-green-800/50"
                        : "bg-red-900/40 text-red-400 border border-red-800/50"
                    }`}>
                      {p.effect}
                    </span>
                  </td>
                  <td className={`px-4 py-3 font-mono ${styles.cardTextMuted}`}>{p.priority}</td>
                  <td className={`px-4 py-3 ${styles.cardTextMuted}`}>
                    {p.scopeType && p.scopeType !== "GLOBAL"
                      ? `${p.scopeType}:${p.scopeId || "—"}`
                      : p.scopeType || "GLOBAL"}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        onClick={() => openEdit(p)}
                        className={`p-1.5 rounded transition-colors ${styles.cardTextMuted} hover:text-blue-400 hover:bg-blue-900/30`}
                        title={L("编辑", "Edit")}
                      >
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                      <button
                        onClick={() => setDeleteTarget(p)}
                        className={`p-1.5 rounded transition-colors ${styles.cardTextMuted} hover:text-red-400 hover:bg-red-900/30`}
                        title={L("删除", "Delete")}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {total > pageSize && (
        <div className={`flex items-center justify-between mt-3 shrink-0 text-xs ${styles.cardTextMuted}`}>
          <span>
            {L(`共 ${total} 条，第 ${page}/${totalPages} 页`, `Showing ${(page-1)*pageSize + 1}-${Math.min(page*pageSize, total)} of ${total}`)}
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={page <= 1}
              className={`p-1.5 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors border ${styles.inputBg} ${styles.inputBorder} hover:${styles.cardBorder}`}
            >
              <ChevronLeft className="w-3.5 h-3.5" />
            </button>
            <span className={`px-2 ${styles.cardTextMuted}`}>{page} / {totalPages}</span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={page >= totalPages}
              className={`p-1.5 rounded disabled:opacity-30 disabled:cursor-not-allowed transition-colors border ${styles.inputBg} ${styles.inputBorder} hover:${styles.cardBorder}`}
            >
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      )}

      {/* Create/Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setModalOpen(false)}>
          <div
            className={`rounded-lg w-full max-w-lg mx-4 shadow-2xl border ${styles.cardBg} ${styles.inputBorder}`}
            onClick={(e) => e.stopPropagation()}
          >
            <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
              <h2 className={`text-sm font-bold ${styles.cardText}`}>
                {editingPolicy ? L("编辑 ABAC 策略", "Edit ABAC Policy") : L("创建 ABAC 策略", "Create ABAC Policy")}
              </h2>
              <button onClick={() => setModalOpen(false)} className={`transition-colors ${styles.cardTextMuted} hover:${styles.cardText}`}>
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="px-5 py-4 space-y-4">
              {/* Name */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("策略名称", "Policy Name")} *
                </label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  placeholder={L("例如: read-customer-apac", "e.g. read-customer-apac")}
                />
              </div>

              {/* Resource */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("资源", "Resource")} *
                </label>
                <input
                  type="text"
                  value={form.resource}
                  onChange={(e) => setForm({ ...form, resource: e.target.value })}
                  className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  placeholder={L("例如: dataset:customer_360", "e.g. dataset:customer_360")}
                />
              </div>

              {/* Action */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("操作类型", "Action")} *
                </label>
                <select
                  value={form.action}
                  onChange={(e) => setForm({ ...form, action: e.target.value })}
                  className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                >
                  <option value="allow">{L("允许 (Allow)", "Allow")}</option>
                  <option value="deny">{L("拒绝 (Deny)", "Deny")}</option>
                </select>
              </div>

              {/* Effect */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("效果", "Effect")}
                </label>
                <select
                  value={form.effect}
                  onChange={(e) => setForm({ ...form, effect: e.target.value })}
                  className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                >
                  <option value="allow">{L("允许 (Allow)", "Allow")}</option>
                  <option value="deny">{L("拒绝 (Deny)", "Deny")}</option>
                </select>
              </div>

              {/* Condition Expression */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("条件表达式", "Condition Expression")}
                </label>
                <textarea
                  value={form.conditionExpression}
                  onChange={(e) => setForm({ ...form, conditionExpression: e.target.value })}
                  rows={3}
                  className={`w-full px-3 py-2 rounded-md text-xs font-mono focus:outline-none focus:border-blue-500/60 resize-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  placeholder={L("例如: region == 'APAC' && clearance >= 3", "e.g. region == 'APAC' && clearance >= 3")}
                />
              </div>

              {/* Scope */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                    {L("作用域类型", "Scope Type")}
                  </label>
                  <select
                    value={form.scopeType}
                    onChange={(e) => setForm({ ...form, scopeType: e.target.value, scopeId: "" })}
                    className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  >
                    <option value="GLOBAL">{L("全局", "Global")}</option>
                    <option value="TENANT">{L("租户", "Tenant")}</option>
                    <option value="ORG">{L("机构", "Organization")}</option>
                  </select>
                </div>
                {form.scopeType !== "GLOBAL" && (
                  <div>
                    <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                      {L("作用域ID", "Scope ID")}
                    </label>
                    <input
                      type="text"
                      value={form.scopeId}
                      onChange={(e) => setForm({ ...form, scopeId: e.target.value })}
                      className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                      placeholder={form.scopeType === "TENANT" ? "tenant-id" : "org-id"}
                    />
                  </div>
                )}
              </div>

              {/* Priority */}
              <div>
                <label className={`block text-[10px] uppercase tracking-wider mb-1 font-medium ${styles.cardTextMuted}`}>
                  {L("优先级", "Priority")}
                </label>
                <input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: parseInt(e.target.value) || 0 })}
                  className={`w-full px-3 py-2 rounded-md text-xs focus:outline-none focus:border-blue-500/60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  placeholder="100"
                  min={0}
                  max={9999}
                />
              </div>
            </div>

            <div className={`flex items-center justify-end gap-2 px-5 py-4 border-t ${styles.cardBorder}`}>
              <button
                onClick={() => setModalOpen(false)}
                className={`px-4 py-2 text-xs rounded-md transition-colors border ${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.cardText}`}
              >
                {L("取消", "Cancel")}
              </button>
              <button
                onClick={handleSave}
                disabled={saving || !form.name.trim() || !form.resource.trim()}
                className="px-4 py-2 text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed rounded-md transition-colors flex items-center gap-1.5"
              >
                {saving && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                {saving ? L("保存中...", "Saving...") : L("保存", "Save")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setDeleteTarget(null)}>
          <div
            className={`rounded-lg w-full max-w-sm mx-4 shadow-2xl border ${styles.cardBg} ${styles.inputBorder}`}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="px-5 py-4">
              <div className="flex items-center gap-3 mb-3">
                <div className="p-2 bg-red-900/30 rounded-full">
                  <AlertTriangle className="w-5 h-5 text-red-400" />
                </div>
                <div>
                  <h3 className={`text-sm font-bold ${styles.cardText}`}>
                    {L("确认删除", "Confirm Delete")}
                  </h3>
                  <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
                    {L(`确定要删除策略 "${deleteTarget.name}" 吗？此操作不可撤销。`, `Are you sure you want to delete "${deleteTarget.name}"? This action cannot be undone.`)}
                  </p>
                </div>
              </div>
            </div>
            <div className={`flex items-center justify-end gap-2 px-5 py-3 border-t ${styles.cardBorder}`}>
              <button
                onClick={() => setDeleteTarget(null)}
                className={`px-4 py-2 text-xs rounded-md transition-colors border ${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.cardText}`}
              >
                {L("取消", "Cancel")}
              </button>
              <button
                onClick={handleDelete}
                className="px-4 py-2 text-xs font-medium text-white bg-red-600 hover:bg-red-700 rounded-md transition-colors"
              >
                {L("删除", "Delete")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
