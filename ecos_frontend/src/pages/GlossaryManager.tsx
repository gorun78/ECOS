/**
 * GlossaryManager — 术语全生命周期管理
 * CRUD + 状态流转 + 搜索筛选 + Toast 通知
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback, useRef } from "react";
import {
  Search, Plus, Edit3, Trash2, CheckCircle2,
  AlertCircle, X, FileText, RotateCw,
} from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import {
  getGlossaryTerms, createGlossaryTerm,
  updateGlossaryTerm, deleteGlossaryTerm,
  type GlossaryTerm,
} from "../services/glossary";
import { useDict } from "../hooks/useDict";

const DOMAINS = ["数据管理", "AI技术", "业务术语", "技术架构", "安全合规", "其他"];

// ── Toast component ──
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3
      rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success"
      ? <CheckCircle2 className="w-4 h-4 shrink-0" />
      : <AlertCircle className="w-4 h-4 shrink-0" />
    }
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100">
      <X className="w-3.5 h-3.5" />
    </button>
  </div>
);

// ── Delete confirm dialog ──
const DeleteConfirm: React.FC<{
  termName: string;
  onConfirm: () => void;
  onCancel: () => void;
  t: (key: string) => string;
}> = ({ termName, onConfirm, onCancel, t }) => (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div
      className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700"
    >
      <h3 className="text-base font-bold mb-2 text-slate-800 dark:text-slate-100">
        {t("glossary.delete_confirm_title")}
      </h3>
      <p className="text-sm text-slate-500 dark:text-slate-400 mb-5">
        {t("glossary.delete_confirm_msg").replace("{name}", termName)}
      </p>
      <div className="flex gap-2 justify-end">
        <button
          onClick={onCancel}
          className="px-4 py-1.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-transparent cursor-pointer text-sm text-slate-600 dark:text-slate-300"
        >
          {t("glossary.cancel")}
        </button>
        <button
          onClick={onConfirm}
          className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white cursor-pointer text-sm font-semibold"
        >
          {t("glossary.delete")}
        </button>
      </div>
    </div>
  </div>
);

// ═══════════════════════════════════════════
// Main Component
// ═══════════════════════════════════════════
export default function GlossaryManager() {
  const { t } = useLanguage();
  useTheme();
  // ── data state ──
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // ── filter state ──
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  // ── selection / form state ──
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [mode, setMode] = useState<"view" | "create" | "edit">("view");  // view=blank right panel

  // ── form fields ──
  const [formName, setFormName] = useState("");
  const [formDomain, setFormDomain] = useState("");
  const [formDefinition, setFormDefinition] = useState("");

  // ── toast ──
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);

  // ── delete confirm ──
  const [deleteTarget, setDeleteTarget] = useState<GlossaryTerm | null>(null);

  // ── debounce ref ──
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ── dict hook ──
  const { getLabel, getColor, getOptions } = useDict('glossary_status');

  // ── helper ──
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3000);
  }, []);

  // ── load terms ──
  const loadTerms = useCallback(async (status?: string) => {
    setLoading(true);
    try {
      const result = await getGlossaryTerms(status ? { status } : undefined);
      setTerms(result.items);
    } catch (e: any) {
      showToast("error", `${t("glossary.toast.load_failed")}: ${e.message}`);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { loadTerms(); }, [loadTerms]);

  // ── status filter → refetch ──
  const handleStatusFilter = (v: string) => {
    setStatusFilter(v);
    setSelectedId(null);
    setMode("view");
    loadTerms(v || undefined);
  };

  // ── search (frontend filter with 300ms debounce) ──
  const handleSearchChange = (v: string) => {
    setSearch(v);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      // no-op — filtering is derived below
    }, 300);
  };

  // ── derived: filtered terms ──
  const filteredTerms = terms.filter(t =>
    !search || t.name.toLowerCase().includes(search.toLowerCase())
  );

  // ── derived: selected term ──
  const selected = terms.find(t => t.id === selectedId) ?? null;

  // ── select term ──
  const selectTerm = (t: GlossaryTerm) => {
    setSelectedId(t.id);
    setMode("edit");
    setFormName(t.name);
    setFormDomain(t.domain ?? "");
    setFormDefinition(t.definition ?? "");
  };

  // ── create new ──
  const handleCreate = () => {
    setSelectedId(null);
    setMode("create");
    setFormName("");
    setFormDomain("");
    setFormDefinition("");
  };

  // ── cancel form ──
  const handleCancel = () => {
    setMode("view");
    setSelectedId(null);
  };

  // ── save (create or update) ──
  const handleSave = async () => {
    if (!formName.trim()) {
      showToast("error", `${t("glossary.toast.name_required")}`);
      return;
    }
    setSaving(true);
    try {
      if (mode === "create") {
        const res = await createGlossaryTerm({
          name: formName.trim(),
          definition: formDefinition.trim(),
          domain: formDomain || undefined,
        });
        showToast("success", t("glossary.toast.created"));
        await loadTerms(statusFilter || undefined);
        // select newly created term
        setSelectedId(res.id);
        setMode("edit");
      } else if (selected) {
        await updateGlossaryTerm(selected.id, {
          name: formName.trim(),
          definition: formDefinition.trim(),
        });
        showToast("success", t("glossary.toast.updated"));
        await loadTerms(statusFilter || undefined);
      }
    } catch (e: any) {
      showToast("error", `${t("glossary.toast.save_failed")}: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── status transition ──
  const handleTransition = async (newStatus: string) => {
    if (!selected) return;
    setSaving(true);
    try {
      await updateGlossaryTerm(selected.id, { status: newStatus });
      showToast("success", `${t("glossary.toast.status_changed")}「${getLabel(newStatus)}」`);
      await loadTerms(statusFilter || undefined);
      // keep selection
      setSelectedId(selected.id);
    } catch (e: any) {
      showToast("error", `${t("glossary.toast.status_failed")}: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── delete ──
  const handleDelete = async () => {
    if (!deleteTarget) return;
    const name = deleteTarget.name;
    setSaving(true);
    try {
      await deleteGlossaryTerm(deleteTarget.id);
      showToast("success", `「${name}」${t("glossary.toast.deleted")}`);
      setDeleteTarget(null);
      if (selectedId === deleteTarget.id) {
        setSelectedId(null);
        setMode("view");
      }
      await loadTerms(statusFilter || undefined);
    } catch (e: any) {
      showToast("error", `${t("glossary.toast.delete_failed")}: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── transition button config ──
  const transitions = selected
    ? (selected.status === "DRAFT"
        ? [{ label: t("glossary.transition.submit_review"), status: "REVIEW", variant: "primary" as const }]
        : selected.status === "REVIEW"
        ? [{ label: t("glossary.transition.publish"), status: "PUBLISHED", variant: "primary" as const }]
        : selected.status === "PUBLISHED"
        ? [{ label: t("glossary.transition.deprecate"), status: "DEPRECATED", variant: "danger" as const }]
        : [])
    : [];

  // ── count summary ──
  const counts = {
    all: terms.length,
    draft: terms.filter(t => t.status === "DRAFT").length,
    review: terms.filter(t => t.status === "REVIEW").length,
    published: terms.filter(t => t.status === "PUBLISHED").length,
  };

  // ═══════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════
  return (
    <div className="flex-1 bg-slate-50 flex h-full overflow-hidden font-sans">
      {/* ── Toast ── */}
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}

      {/* ── Delete Confirm ── */}
      {deleteTarget && (
        <DeleteConfirm
          termName={deleteTarget.name}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
          t={t}
        />
      )}

      {/* ═══════════ Left Panel: Term List ═══════════ */}
      <div className="w-[320px] min-w-[280px] border-r border-slate-200 bg-white flex flex-col shrink-0">
        <div className="p-4 border-b border-slate-200">
          <div className="text-base font-bold text-slate-800 mb-3 flex items-center gap-2">
            <FileText size={18} />
            {t("glossary.title")}
            <span className="text-xs font-normal text-slate-400">{counts.all} {t("glossary.unit")}</span>
          </div>

          {/* search + filter */}
          <div className="flex gap-2 mb-2.5">
            <div className="flex items-center gap-1.5 flex-1 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs">
              <Search size={14} className="text-slate-400 shrink-0" />
              <input
                placeholder={t("glossary.search_placeholder")}
                value={search}
                onChange={e => handleSearchChange(e.target.value)}
                className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400"
              />
            </div>
            <select
              className="px-2.5 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 min-w-[110px] outline-none"
              value={statusFilter}
              onChange={e => handleStatusFilter(e.target.value)}
            >
              <option value="">{t("glossary.all")}</option>
              {getOptions().map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>

          {/* new term button */}
          <button
            className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer transition disabled:opacity-50"
            onClick={handleCreate}
            disabled={saving}
          >
            <Plus size={16} />
            {t("glossary.new_term")}
          </button>
          {/* quick stats */}
          <div className="flex gap-3 mt-2.5 text-[11px] text-slate-400">
            <span>{t("glossary.status.draft")} {counts.draft}</span>
            <span>{t("glossary.status.review")} {counts.review}</span>
            <span>{t("glossary.status.published")} {counts.published}</span>
          </div>
        </div>

        {/* term list */}
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
              <RotateCw size={18} className="animate-spin" />
              <div>{t("glossary.loading")}</div>
            </div>
          ) : filteredTerms.length === 0 ? (
            <div className="flex items-center justify-center py-12 text-slate-400 text-xs px-4 text-center">
              {search ? `${t("glossary.no_match")}「${search}」` : t("glossary.empty")}
            </div>
          ) : (
            filteredTerms.map(term => {
              const color = getColor(term.status, 'DRAFT');
              const label = getLabel(term.status, 'DRAFT');
              const isActive = term.id === selectedId;
              return (
                <div
                  key={term.id}
                  className={`flex items-center gap-2 px-3 py-2.5 border-b border-slate-100 cursor-pointer transition text-xs ${
                    isActive ? "bg-indigo-50 border-l-2 border-l-indigo-500" : "hover:bg-slate-50"
                  }`}
                  onClick={() => selectTerm(term)}
                >
                  <span className="flex-1 font-semibold text-slate-700 truncate">{term.name}</span>
                  <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${color}`}>{label}</span>

                  {/* actions */}
                  <button
                    className="p-1 hover:bg-slate-200 rounded"
                    title={t("glossary.edit")}
                    onClick={e => { e.stopPropagation(); selectTerm(term); }}
                  >
                    <Edit3 size={14} className="text-slate-400" />
                  </button>
                  <button
                    className="p-1 hover:bg-red-50 rounded"
                    title={t("glossary.delete")}
                    onClick={e => { e.stopPropagation(); setDeleteTarget(term); }}
                  >
                    <Trash2 size={14} className="text-red-400" />
                  </button>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* ═══════════ Right Panel: Detail / Form ═══════════ */}
      <div className="flex-1 bg-white p-6 overflow-y-auto">
        {mode === "view" && !selected ? (
          <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs gap-3">
          <FileText size={48} className="opacity-25" />
          <div className="text-center">{t("glossary.empty_hint")}</div>
          </div>
          ) : (
          <div className="space-y-4">
          <h2 className="text-base font-bold text-slate-800">
            {mode === "create" ? t("glossary.new_term") : selected?.name ?? t("glossary.term_detail")}
          </h2>

          {/* name */}
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">{t("glossary.field.name")} *</div>
            <input
              className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
              placeholder={t("glossary.field.name_placeholder")}
              value={formName}
              onChange={e => setFormName(e.target.value)}
              disabled={saving}
            />
          </div>

          {/* domain */}
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">{t("glossary.field.domain")}</div>
            <select
              className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
              value={formDomain}
              onChange={e => setFormDomain(e.target.value)}
              disabled={saving}
            >
              <option value="">{t("glossary.field.domain_placeholder")}</option>
              {DOMAINS.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>

          {/* definition */}
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">{t("glossary.field.definition")}</div>
            <textarea
              className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 resize-none"
              placeholder={t("glossary.field.definition_placeholder")}
              value={formDefinition}
              onChange={e => setFormDefinition(e.target.value)}
              disabled={saving}
              rows={5}
            />
          </div>

          {/* status display */}
          {selected && (
            <div>
              <div className="text-[11px] font-semibold text-slate-500 mb-1">{t("glossary.field.status")}</div>
              <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold ${getColor(selected.status, 'DRAFT')}`}>
                {getLabel(selected.status)}
              </span>
            </div>
          )}

          {/* status transitions */}
          {transitions.length > 0 && (
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[11px] font-semibold text-slate-400">{t("glossary.actions")}:</span>
                {transitions.map(tr => (
                  <button
                    key={tr.status}
                    className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold transition disabled:opacity-50 ${
                      tr.variant === "primary" ? "bg-indigo-600 hover:bg-indigo-700 text-white" :
                      tr.variant === "danger" ? "bg-red-600 hover:bg-red-700 text-white" :
                      "bg-slate-100 hover:bg-slate-200 text-slate-600"
                    }`}
                    onClick={() => handleTransition(tr.status)}
                    disabled={saving}
                  >
                    {saving ? <RotateCw size={14} className="animate-spin inline mr-1" /> : null}
                    {tr.label}
                  </button>
                ))}
              </div>
            )}

            {/* action buttons */}
            <div className="flex gap-2 pt-3 border-t border-slate-100">
              <button
                className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
                onClick={handleSave}
                disabled={saving}
              >
                {saving ? <RotateCw size={14} className="animate-spin" /> : null}
                {t("glossary.save")}
              </button>
              <button
                className="px-4 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-semibold transition disabled:opacity-50"
                onClick={handleCancel}
                disabled={saving}
              >
                {t("glossary.cancel")}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
