/**
 * GlossaryManager — Wiki（词条）管理容器。
 *
 * 布局：左栏 = 词条列表（搜索 / 状态筛选 / 新建 / 编辑 / 删除）；右栏 = 3 个 Tab
 * （详情 = 词条表单 / 关系 = 关系维护 / 图谱 = 关系图谱）。
 * 状态与副作用集中在本容器，表单、关系、图谱分别下沉到 pages/glossary/ 子组件。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Search, Plus, Edit3, Trash2, CheckCircle2, AlertCircle, X, FileText, RotateCw,
} from "lucide-react";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";
import { useDict } from "../hooks/useDict";
import {
  listGlossaryTerms, deleteGlossaryTerm, type GlossaryTerm,
} from "../services/glossary";
import TermFormPanel from "./glossary/TermFormPanel";
import GlossaryRelationPanel from "./glossary/GlossaryRelationPanel";
import GlossaryGraphPanel from "./glossary/GlossaryGraphPanel";

/** 右栏 Tab 定义 */
type TabKey = "detail" | "relations" | "graph";
const TABS: { key: TabKey; labelKey: string }[] = [
  { key: "detail", labelKey: "glossary.tab.detail" },
  { key: "relations", labelKey: "glossary.tab.relations" },
  { key: "graph", labelKey: "glossary.tab.graph" },
];

/** Toast 轻提示 */
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg
      text-sm font-medium transition-all border
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"}`}
  >
    {toast.type === "success"
      ? <CheckCircle2 className="w-4 h-4 shrink-0" />
      : <AlertCircle className="w-4 h-4 shrink-0" />}
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100">
      <X className="w-3.5 h-3.5" />
    </button>
  </div>
);

/** 删除二次确认弹窗 */
const DeleteConfirm: React.FC<{
  termName: string;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ termName, onConfirm, onCancel }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center">
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6
        border ${styles.cardBg} ${styles.cardBorder}`}>
        <h3 className={`text-base font-bold mb-2 ${styles.cardText}`}>
          {t("glossary.delete_confirm_title")}
        </h3>
        <p className={`text-sm mb-5 ${styles.cardTextMuted}`}>
          {t("glossary.delete_confirm_msg", { name: termName })}
        </p>
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className={`px-4 py-1.5 rounded-lg border bg-transparent cursor-pointer text-sm
              ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
          >
            {t("glossary.cancel")}
          </button>
          <button
            onClick={onConfirm}
            className="px-4 py-1.5 rounded-lg bg-red-600 hover:bg-red-700 text-white cursor-pointer
              text-sm font-semibold"
          >
            {t("glossary.delete")}
          </button>
        </div>
      </div>
    </div>
  );
};

export default function GlossaryManager() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor, getOptions } = useDict("glossary_status", locale);

  // ── 数据状态 ──
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  // ── 筛选状态 ──
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  // ── 选中 / 模式 / Tab ──
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [mode, setMode] = useState<"view" | "create" | "edit">("view");
  const [tab, setTab] = useState<TabKey>("detail");

  // ── 提示 / 删除确认 ──
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<GlossaryTerm | null>(null);

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  /** 最新词条列表引用（供稳定的回调用，避免闭包过期） */
  const termsRef = useRef<GlossaryTerm[]>([]);

  /** 轻提示（3s 自动关闭） */
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    toastTimerRef.current = setTimeout(() => setToast(null), 3000);
  }, []);

  // 卸载时清理定时器
  useEffect(() => {
    return () => {
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
    };
  }, []);

  /** 加载词条列表（按状态筛选） */
  const loadTerms = useCallback(async (status?: string) => {
    setLoading(true);
    try {
      const items = await listGlossaryTerms(status ? { status } : undefined);
      setTerms(items);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      showToast("error", `${t("glossary.toast.load_failed")}: ${message}`);
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => {
    void loadTerms();
  }, [loadTerms]);

  /** 状态筛选变更 → 重拉列表 */
  const handleStatusFilter = (value: string) => {
    setStatusFilter(value);
    setSelectedId(null);
    setMode("view");
    setTab("detail");
    void loadTerms(value || undefined);
  };

  /** 选中词条（进入详情 Tab） */
  const selectTerm = (term: GlossaryTerm) => {
    setSelectedId(term.id);
    setMode("edit");
    setTab("detail");
  };

  /** 新建词条（进入详情 Tab 的表单） */
  const handleCreate = () => {
    setSelectedId(null);
    setMode("create");
    setTab("detail");
  };

  /** 取消编辑 */
  const handleCancel = () => {
    setMode("view");
    setSelectedId(null);
    setTab("detail");
  };

  /** 表单保存 / 状态流转成功 → 定位到该词条并刷新列表 */
  const handleSaved = useCallback(async (term: GlossaryTerm) => {
    setSelectedId(term.id);
    setMode("edit");
    await loadTerms(statusFilter || undefined);
  }, [loadTerms, statusFilter]);

  /** 图谱点击节点 → 切到详情 Tab 并选中该词条 */
  const handleSelectTermFromGraph = useCallback((id: number) => {
    setSelectedId(id);
    setMode("edit");
    setTab("detail");
    // 图谱可达但不在当前列表中的词条，补一次刷新以保证详情可渲染
    if (!termsRef.current.some((item) => item.id === id)) {
      void loadTerms(statusFilter || undefined);
    }
  }, [loadTerms, statusFilter]);

  /** 删除词条 */
  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    const target = deleteTarget;
    setSaving(true);
    try {
      await deleteGlossaryTerm(target.id);
      showToast("success", `「${target.name}」${t("glossary.toast.deleted")}`);
      setDeleteTarget(null);
      if (selectedId === target.id) {
        setSelectedId(null);
        setMode("view");
        setTab("detail");
      }
      await loadTerms(statusFilter || undefined);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      showToast("error", `${t("glossary.toast.delete_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── 派生数据 ──
  const filteredTerms = useMemo(
    () => terms.filter((term) => !search || term.name.toLowerCase().includes(search.toLowerCase())),
    [terms, search],
  );
  const selected = useMemo(
    () => terms.find((term) => term.id === selectedId) ?? null,
    [terms, selectedId],
  );
  const counts = useMemo(() => ({
    all: terms.length,
    draft: terms.filter((term) => term.status === "DRAFT").length,
    review: terms.filter((term) => term.status === "REVIEW").length,
    published: terms.filter((term) => term.status === "PUBLISHED").length,
  }), [terms]);
  termsRef.current = terms;

  const hasSelection = selectedId !== null;
  const inputClass = `px-2.5 py-2 rounded-lg border text-xs outline-none
    ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`;

  // ═══════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════
  return (
    <div className={`flex-1 ${styles.appBg} flex h-full overflow-hidden font-sans`}>
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}
      {deleteTarget && (
        <DeleteConfirm
          termName={deleteTarget.name}
          onConfirm={handleDelete}
          onCancel={() => setDeleteTarget(null)}
        />
      )}

      {/* ═══════════ 左栏：词条列表 ═══════════ */}
      <div className={`w-[320px] min-w-[280px] border-r flex flex-col shrink-0
        ${styles.cardBorder} ${styles.sidebarBg}`}>
        <div className={`p-4 border-b ${styles.cardBorder}`}>
          <div className={`text-base font-bold mb-3 flex items-center gap-2 ${styles.cardText}`}>
            <FileText size={18} />
            {t("glossary.title")}
            <span className={`text-xs font-normal ${styles.muted}`}>
              {counts.all} {t("glossary.unit")}
            </span>
          </div>

          <div className="flex gap-2 mb-2.5">
            <div className={`flex items-center gap-1.5 flex-1 px-3 py-2 rounded-lg border text-xs
              ${styles.inputBorder} ${styles.inputBg}`}>
              <Search size={14} className={`${styles.muted} shrink-0`} />
              <input
                placeholder={t("glossary.search_placeholder")}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className={`border-none outline-none flex-1 bg-transparent text-xs
                  ${styles.inputText}`}
              />
            </div>
            <select
              className={`${inputClass} min-w-[110px]`}
              value={statusFilter}
              onChange={(e) => handleStatusFilter(e.target.value)}
            >
              <option value="">{t("glossary.all")}</option>
              {getOptions().map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>

          <button
            className={`w-full py-2 rounded-lg text-xs font-semibold flex items-center justify-center
              gap-1.5 cursor-pointer transition disabled:opacity-50 text-white
              ${styles.accentBg} ${styles.accentHover}`}
            onClick={handleCreate}
            disabled={saving}
          >
            <Plus size={16} />
            {t("glossary.new_term")}
          </button>

          <div className={`flex gap-3 mt-2.5 text-[11px] ${styles.muted}`}>
            <span>{t("glossary.status.draft")} {counts.draft}</span>
            <span>{t("glossary.status.review")} {counts.review}</span>
            <span>{t("glossary.status.published")} {counts.published}</span>
          </div>
        </div>

        {/* 词条列表 */}
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className={`flex flex-col items-center justify-center py-12 text-xs gap-2 ${styles.muted}`}>
              <RotateCw size={18} className="animate-spin" />
              <div>{t("glossary.loading")}</div>
            </div>
          ) : filteredTerms.length === 0 ? (
            <div className={`flex items-center justify-center py-12 text-xs px-4 text-center ${styles.muted}`}>
              {search ? `${t("glossary.no_match")}「${search}」` : t("glossary.empty")}
            </div>
          ) : (
            filteredTerms.map((term) => {
              const color = getColor(term.status, `${styles.badgeBg} ${styles.badgeText}`);
              const label = getLabel(term.status, t("glossary.status.draft"));
              const isActive = term.id === selectedId;
              return (
                <div
                  key={term.id}
                  className={`flex items-center gap-2 px-3 py-2.5 border-b cursor-pointer transition
                    text-xs ${styles.cardBorder} ${isActive ? styles.sidebarActiveBg : styles.sidebarHoverBg}`}
                  onClick={() => selectTerm(term)}
                >
                  <span className={`flex-1 font-semibold truncate ${styles.cardText}`}>{term.name}</span>
                  <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${color}`}>
                    {label}
                  </span>
                  <button
                    className={`p-1 rounded ${styles.sidebarHoverBg}`}
                    title={t("glossary.edit")}
                    onClick={(e) => { e.stopPropagation(); selectTerm(term); }}
                  >
                    <Edit3 size={14} className={styles.muted} />
                  </button>
                  <button
                    className="p-1 rounded hover:bg-red-500/10"
                    title={t("glossary.delete")}
                    onClick={(e) => { e.stopPropagation(); setDeleteTarget(term); }}
                  >
                    <Trash2 size={14} className="text-red-400" />
                  </button>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* ═══════════ 右栏：Tab 容器 ═══════════ */}
      <div className={`flex-1 flex flex-col min-h-0 ${styles.cardBg}`}>
        {mode === "view" && !selected ? (
          <div className={`flex flex-col items-center justify-center h-full text-xs gap-3 ${styles.muted}`}>
            <FileText size={48} className="opacity-25" />
            <div className="text-center">{t("glossary.empty_hint")}</div>
          </div>
        ) : (
          <>
            {/* Tab 栏 */}
            <div className={`flex items-center gap-1 px-4 pt-3 border-b shrink-0 ${styles.cardBorder}`}>
              {TABS.map((item) => {
                const disabled = item.key !== "detail" && !hasSelection;
                const active = tab === item.key;
                return (
                  <button
                    key={item.key}
                    className={`px-3 py-1.5 rounded-t-lg text-xs font-semibold transition
                      disabled:opacity-40 disabled:cursor-not-allowed
                      ${active ? `${styles.badgeBg} ${styles.badgeText}` : styles.muted}`}
                    onClick={() => setTab(item.key)}
                    disabled={disabled}
                  >
                    {t(item.labelKey)}
                  </button>
                );
              })}
            </div>

            {/* Tab 内容 */}
            <div className="flex-1 min-h-0 overflow-y-auto p-5">
              {tab === "detail" && (
                <TermFormPanel
                  term={selected}
                  mode={mode === "create" ? "create" : "edit"}
                  terms={terms}
                  onSaved={handleSaved}
                  onCancel={handleCancel}
                  showToast={showToast}
                />
              )}
              {tab === "relations" && selectedId !== null && (
                <GlossaryRelationPanel termId={selectedId} terms={terms} showToast={showToast} />
              )}
              {tab === "graph" && selectedId !== null && (
                <GlossaryGraphPanel termId={selectedId} onSelectTerm={handleSelectTermFromGraph} />
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}