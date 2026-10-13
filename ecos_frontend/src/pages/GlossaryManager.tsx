/**
 * GlossaryManager — 术语（Glossary）管理容器。
 *
 * 布局：左栏 = 词条列表（搜索 / 状态筛选 / 新建 / 编辑 / 删除）；右栏 = 3 个 Tab
 * （详情 = 只读查看或词条表单 / 关系 = 关系维护 / 图谱 = 关系图谱）。
 * 状态与副作用集中在本容器，表单、只读详情、关系、图谱分别下沉到 pages/glossary/ 子组件。
 *
 * 两条口径订正（前端检视报告 §4.3 行161/169，2026-10-13）：
 * ① 列表恒按「全量」拉取，状态筛选与关键词过滤在前端派生 ⇒ 顶部三计数不随筛选漂移
 *    （后端 GlossaryController 无计数端点，故计数只能由全量集合聚合）；
 * ② 点击列表项进入**只读查看态**，显式「编辑」才进编辑上下文，避免误触即改。
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
import ConfirmDialog from "../components/common/ConfirmDialog";
import ErrorPanel from "../components/common/status/ErrorPanel";
import {
  listGlossaryTerms, deleteGlossaryTerm, readGlossaryError, type GlossaryTerm,
} from "../services/glossary";
import TermFormPanel from "./glossary/TermFormPanel";
import TermDetailPanel from "./glossary/TermDetailPanel";
import GlossaryRelationPanel from "./glossary/GlossaryRelationPanel";
import GlossaryGraphPanel from "./glossary/GlossaryGraphPanel";

/** 右栏 Tab 定义 */
type TabKey = "detail" | "relations" | "graph";
const TABS: { key: TabKey; labelKey: string }[] = [
  { key: "detail", labelKey: "glossary.tab.detail" },
  { key: "relations", labelKey: "glossary.tab.relations" },
  { key: "graph", labelKey: "glossary.tab.graph" },
];

/** Toast 轻提示（主题 token 取色，禁裸色板 —— 前端开发规范 §七.1） */
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => {
  const { styles } = useTheme();
  const tone = toast.type === "success"
    ? `${styles.successBg} ${styles.successBorder} ${styles.successText}`
    : `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}`;
  return (
    <div
      role="status"
      aria-live="polite"
      className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg
        text-sm font-medium transition-all border ${tone}`}
    >
      {toast.type === "success"
        ? <CheckCircle2 className="w-4 h-4 shrink-0" aria-hidden="true" />
        : <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />}
      <span>{toast.msg}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="close"
        className="ml-2 opacity-60 hover:opacity-100"
      >
        <X className="w-3.5 h-3.5" aria-hidden="true" />
      </button>
    </div>
  );
};

export default function GlossaryManager() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor, getOptions } = useDict("glossary_status", locale);

  // ── 数据状态 ──
  /** 全量词条（不受筛选影响：计数口径 + 详情回查的唯一来源） */
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  /** 独立失败态：与「列表为空」严格区分（报告 §4.3 行161） */
  const [loadError, setLoadError] = useState<{ reason: string; traceId?: string } | null>(null);

  // ── 筛选状态（前端派生，不触发服务端重拉） ──
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

  /** Esc 关闭删除确认（modal 无障碍：ConfirmDialog 提供 role/aria-modal，键盘出口在此补齐） */
  useEffect(() => {
    if (!deleteTarget) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setDeleteTarget(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [deleteTarget]);

  /**
   * 加载**全量**词条列表（状态/关键词筛选在前端派生）。
   * 失败不再只发 3s toast：置独立 loadError（含 traceId）并保留重试出口。
   */
  const loadTerms = useCallback(async () => {
    setLoading(true);
    try {
      const items = await listGlossaryTerms();
      setTerms(items);
      setLoadError(null);
    } catch (e) {
      const { message, traceId } = readGlossaryError(e);
      setLoadError({
        reason: `${t("glossary.toast.load_failed")}: ${message}`,
        traceId,
      });
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadTerms();
  }, [loadTerms]);

  /** 状态筛选变更 → 仅切换派生视图，不重拉（计数保持全量口径） */
  const handleStatusFilter = (value: string) => {
    setStatusFilter(value);
    setSelectedId(null);
    setMode("view");
    setTab("detail");
  };

  /** 选中词条 → 只读查看态（报告 §4.3 行169：点列表不得直进编辑） */
  const selectTerm = (term: GlossaryTerm, nextMode: "view" | "edit" = "view") => {
    setSelectedId(term.id);
    setMode(nextMode);
    setTab("detail");
  };

  /** 新建词条（进入详情 Tab 的表单） */
  const handleCreate = () => {
    setSelectedId(null);
    setMode("create");
    setTab("detail");
  };

  /** 取消编辑 / 退出查看：保留选中项，回到只读查看态 */
  const handleCancel = () => {
    setMode("view");
    setTab("detail");
  };

  /** 表单保存 / 状态流转成功 → 定位到该词条（只读态）并刷新列表 */
  const handleSaved = useCallback(async (term: GlossaryTerm) => {
    setSelectedId(term.id);
    setMode("view");
    await loadTerms();
  }, [loadTerms]);

  /** 图谱点击节点 → 切到详情 Tab 并只读选中该词条 */
  const handleSelectTermFromGraph = useCallback((id: number) => {
    setSelectedId(id);
    setMode("view");
    setTab("detail");
    // 图谱可达但不在当前列表中的词条，补一次刷新以保证详情可渲染
    if (!termsRef.current.some((item) => item.id === id)) {
      void loadTerms();
    }
  }, [loadTerms]);

  /** 删除词条 */
  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    const target = deleteTarget;
    setSaving(true);
    try {
      await deleteGlossaryTerm(target.id);
      // 书名号拼接文案改为 i18n 插值键（缺词期以既有键组合兜底，见 .fixtmp/need-locale-onto2.md）
      showToast(
        "success",
        t("glossary.toast.deleted_named", `${target.name} ${t("glossary.toast.deleted")}`),
      );
      setDeleteTarget(null);
      if (selectedId === target.id) {
        setSelectedId(null);
        setMode("view");
        setTab("detail");
      }
      await loadTerms();
    } catch (e) {
      const { message } = readGlossaryError(e);
      showToast("error", `${t("glossary.toast.delete_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  // ── 派生数据 ──
  /** 左栏可见列表 = 全量集合按状态 + 关键词派生 */
  const visibleTerms = useMemo(
    () => terms.filter((term) => (
      (!statusFilter || term.status === statusFilter)
      && (!search || term.name.toLowerCase().includes(search.toLowerCase()))
    )),
    [terms, statusFilter, search],
  );
  const selected = useMemo(
    () => terms.find((term) => term.id === selectedId) ?? null,
    [terms, selectedId],
  );
  /** 三计数恒在全量列表上计算（筛 DRAFT 时 published 不再恒 0） */
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

  const statusOptions = getOptions();
  const statusNoMatch = statusOptions.find((o) => o.value === statusFilter)?.label ?? "";

  // ═══════════════════════════════════════════
  // Render
  // ═══════════════════════════════════════════
  return (
    <div className={`flex-1 ${styles.appBg} flex h-full overflow-hidden font-sans`}>
      {toast && <Toast toast={toast} onClose={() => setToast(null)} />}
      <ConfirmDialog
        open={!!deleteTarget}
        title={t("glossary.delete_confirm_title")}
        message={t("glossary.delete_confirm_msg", { name: deleteTarget?.name ?? "" })}
        confirmText={t("glossary.delete")}
        cancelText={t("glossary.cancel")}
        variant="danger"
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />

      {/* ═══════════ 左栏：词条列表 ═══════════ */}
      <div className={`w-full lg:w-[320px] min-w-[280px] border-r flex flex-col shrink-0
        ${styles.cardBorder} ${styles.sidebarBg}`}>
        <div className={`p-4 border-b ${styles.cardBorder}`}>
          <div className={`text-base font-bold mb-3 flex items-center gap-2 ${styles.cardText}`}>
            <FileText size={18} aria-hidden="true" />
            {t("glossary.title")}
            <span className={`text-xs font-normal ${styles.muted}`}>
              {counts.all} {t("glossary.unit")}
            </span>
          </div>

          <div className="flex gap-2 mb-2.5">
            <div className={`flex items-center gap-1.5 flex-1 px-3 py-2 rounded-lg border text-xs
              ${styles.inputBorder} ${styles.inputBg}`}>
              <Search size={14} className={`${styles.muted} shrink-0`} aria-hidden="true" />
              <input
                aria-label={t("glossary.search_placeholder")}
                placeholder={t("glossary.search_placeholder")}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className={`border-none outline-none flex-1 bg-transparent text-xs
                  ${styles.inputText}`}
              />
            </div>
            <select
              aria-label={t("glossary.field.status")}
              className={`${inputClass} min-w-[110px]`}
              value={statusFilter}
              onChange={(e) => handleStatusFilter(e.target.value)}
            >
              <option value="">{t("glossary.all")}</option>
              {statusOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>

          <button type="button"
            className={`w-full py-2 rounded-lg text-xs font-semibold flex items-center justify-center
              gap-1.5 cursor-pointer transition disabled:opacity-50
              ${styles.accentBg} ${styles.accentHover} ${styles.accentSolidText}`}
            onClick={handleCreate}
            disabled={saving}
          >
            <Plus size={16} aria-hidden="true" />
            {t("glossary.new_term")}
          </button>

          {/* 全量口径计数（与下方筛选视图无关） */}
          <div className={`flex gap-3 mt-2.5 text-[11px] ${styles.muted}`}>
            <span>{t("glossary.status.draft")} {counts.draft}</span>
            <span>{t("glossary.status.review")} {counts.review}</span>
            <span>{t("glossary.status.published")} {counts.published}</span>
          </div>
        </div>

        {/* 词条列表：加载 / 失败（含 traceId + 重试）/ 空 / 有数据 四态互斥 */}
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className={`flex flex-col items-center justify-center py-12 text-xs gap-2 ${styles.muted}`}>
              <RotateCw size={18} className="animate-spin" aria-hidden="true" />
              <div>{t("glossary.loading")}</div>
            </div>
          ) : loadError ? (
            <div className="p-3">
              <ErrorPanel
                reason={loadError.reason}
                traceId={loadError.traceId}
                onRetry={() => void loadTerms()}
              />
            </div>
          ) : visibleTerms.length === 0 ? (
            <div className={`flex items-center justify-center py-12 text-xs px-4 text-center ${styles.muted}`}>
              {search || statusFilter
                ? t(
                  "glossary.no_match_named",
                  `${t("glossary.no_match")}${search ? ` ${search}` : ""}${statusNoMatch ? ` · ${statusNoMatch}` : ""}`,
                )
                : t("glossary.empty")}
            </div>
          ) : (
            visibleTerms.map((term) => {
              const color = getColor(term.status, `${styles.badgeBg} ${styles.badgeText}`);
              const label = getLabel(term.status, t("glossary.status.draft"));
              const isActive = term.id === selectedId;
              return (
                <div
                  key={term.id}
                  role="button"
                  tabIndex={0}
                  aria-label={term.name}
                  aria-current={isActive ? "true" : undefined}
                  className={`flex items-center gap-2 px-3 py-2.5 border-b cursor-pointer transition
                    text-xs outline-none focus-visible:ring-1 ${styles.cardBorder}
                    ${styles.accentRing} ${isActive ? styles.sidebarActiveBg : styles.sidebarHoverBg}`}
                  onClick={() => selectTerm(term)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      selectTerm(term);
                    }
                  }}
                >
                  <span className={`flex-1 font-semibold truncate ${styles.cardText}`}>{term.name}</span>
                  <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${color}`}>
                    {label}
                  </span>
                  <button type="button"
                    className={`p-1 rounded ${styles.sidebarHoverBg}`}
                    title={t("glossary.edit")}
                    aria-label={t("glossary.edit")}
                    onClick={(e) => { e.stopPropagation(); selectTerm(term, "edit"); }}
                  >
                    <Edit3 size={14} className={styles.muted} aria-hidden="true" />
                  </button>
                  <button type="button"
                    className={`p-1 rounded ${styles.sidebarHoverBg} ${styles.dangerText}`}
                    title={t("glossary.delete")}
                    aria-label={t("glossary.delete")}
                    onClick={(e) => { e.stopPropagation(); setDeleteTarget(term); }}
                  >
                    <Trash2 size={14} aria-hidden="true" />
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
            <FileText size={48} className="opacity-25" aria-hidden="true" />
            <div className="text-center">{t("glossary.empty_hint")}</div>
          </div>
        ) : (
          <>
            {/* Tab 栏 + 显式「编辑」入口（只读查看态唯一的写通道开关） */}
            <div className={`flex items-center gap-1 px-4 pt-3 border-b shrink-0 ${styles.cardBorder}`}>
              {TABS.map((item) => {
                const disabled = item.key !== "detail" && !hasSelection;
                const active = tab === item.key;
                return (
                  <button type="button"
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
              {hasSelection && mode === "view" && (
                <button type="button"
                  className={`ml-auto mb-1 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs
                    font-semibold border transition ${styles.cardBorder} ${styles.sidebarHoverBg} ${styles.cardText}`}
                  onClick={() => setMode("edit")}
                >
                  <Edit3 size={13} aria-hidden="true" />
                  {t("glossary.edit")}
                </button>
              )}
            </div>

            {/* Tab 内容 */}
            <div className="flex-1 min-h-0 overflow-y-auto p-5">
              {tab === "detail" && (
                mode === "view" && selected ? (
                  <TermDetailPanel
                    term={selected}
                    terms={terms}
                    onEdit={() => setMode("edit")}
                  />
                ) : (
                  <TermFormPanel
                    term={selected}
                    mode={mode === "create" ? "create" : "edit"}
                    terms={terms}
                    onSaved={handleSaved}
                    onCancel={handleCancel}
                    showToast={showToast}
                  />
                )
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
