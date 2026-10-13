/**
 * TermFormPanel — 术语词条详情表单（详情 Tab）。
 *
 * 职责：渲染词条全部语义字段（含 7 个新增字段）、基础校验、保存（新增/编辑）与状态流转。
 * 数据/接口：services/glossary 的 createGlossaryTerm / updateGlossaryTerm。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useState } from "react";
import { RotateCw } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import ConfirmDialog from "../../components/common/ConfirmDialog";
import { readGlossaryError } from "../../services/glossary";
import {
  createGlossaryTerm,
  updateGlossaryTerm,
  type GlossaryTerm,
  type TermSaveDTO,
} from "../../services/glossary";
import TagListInput from "./TagListInput";

/** 状态流转配置（按钮文案 key + 目标状态 + 样式变体 + 是否需二次确认） */
const TRANSITIONS: Record<string, { key: string; status: string; danger?: boolean; confirm?: boolean }[]> = {
  DRAFT: [{ key: "glossary.transition.submit_review", status: "REVIEW" }],
  REVIEW: [{ key: "glossary.transition.publish", status: "PUBLISHED" }],
  // 废弃 = 终态流转（词条退出可用面），按危险操作加确认门
  PUBLISHED: [{ key: "glossary.transition.deprecate", status: "DEPRECATED", danger: true, confirm: true }],
};

interface TermFormPanelProps {
  /** 待编辑词条；新建时为 null */
  term: GlossaryTerm | null;
  /** 表单模式 */
  mode: "create" | "edit";
  /** 全部词条（用于「上位词条」下拉） */
  terms: GlossaryTerm[];
  /** 保存或状态流转成功后回调（携带最新词条） */
  onSaved: (term: GlossaryTerm) => void;
  /** 取消编辑 */
  onCancel: () => void;
  /** 轻提示 */
  showToast: (type: "success" | "error", msg: string) => void;
}

export default function TermFormPanel({
  term,
  mode,
  terms,
  onSaved,
  onCancel,
  showToast,
}: TermFormPanelProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor } = useDict("glossary_status", locale);
  const domainDict = useDict("glossary_domain", locale);
  const termTypeDict = useDict("glossary_term_type", locale);

  const [saving, setSaving] = useState(false);
  /** 待确认的危险流转（status 值）；null = 无待确认弹窗 */
  const [pendingTransition, setPendingTransition] = useState<string | null>(null);

  // ── 表单字段 ──
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [definition, setDefinition] = useState("");
  const [domain, setDomain] = useState("");
  const [owner, setOwner] = useState("");
  const [termType, setTermType] = useState("CONCEPT");
  const [objectTypeId, setObjectTypeId] = useState("");
  const [parentTermId, setParentTermId] = useState<number | null>(null);
  const [aliases, setAliases] = useState<string[]>([]);
  const [examples, setExamples] = useState<string[]>([]);
  const [tags, setTags] = useState<string[]>([]);

  // 词条切换 / 模式切换时重置表单
  useEffect(() => {
    setName(term?.name ?? "");
    setCode(term?.code ?? "");
    setDefinition(term?.definition ?? "");
    setDomain(term?.domain ?? "");
    setOwner(term?.owner ?? "");
    setTermType(term?.termType || "CONCEPT");
    setObjectTypeId(term?.objectTypeId ?? "");
    setParentTermId(term?.parentTermId ?? null);
    setAliases(term?.aliases ?? []);
    setExamples(term?.examples ?? []);
    setTags(term?.tags ?? []);
  }, [term, mode]);

  /** 统一输入框样式（聚焦态走主题 accentRing，禁裸色板 §七.1） */
  const inputClass = `w-full px-3 py-2 rounded-lg border text-xs outline-none
    focus:ring-2 focus:ring-inset focus-visible:ring-2 ${styles.accentRing}
    disabled:opacity-50 ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`;

  /** 字段标题样式 */
  const labelClass = `text-[11px] font-semibold mb-1 ${styles.cardTextMuted}`;

  /** 保存词条（新增 / 编辑） */
  const handleSave = async () => {
    if (!name.trim()) {
      showToast("error", t("glossary.toast.name_required"));
      return;
    }
    const dto: TermSaveDTO = {
      name: name.trim(),
      code: code.trim() || null,
      definition: definition.trim(),
      domain: domain || null,
      owner: owner.trim() || null,
      termType,
      objectTypeId: objectTypeId.trim() || null,
      // parentTermId=0 表示清空上位词条（后端契约）
      parentTermId: parentTermId ?? 0,
      aliases,
      examples,
      tags,
    };

    setSaving(true);
    try {
      if (mode === "create") {
        const created = await createGlossaryTerm(dto);
        showToast("success", t("glossary.toast.created"));
        onSaved(created);
      } else if (term) {
        const updated = await updateGlossaryTerm(term.id, dto);
        showToast("success", t("glossary.toast.updated"));
        onSaved(updated);
      }
    } catch (e) {
      const { message } = readGlossaryError(e);
      showToast("error", `${t("glossary.toast.save_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 词条切换时收起未确认的危险流转 */
  useEffect(() => {
    setPendingTransition(null);
  }, [term?.id, mode]);

  /** Esc 关闭流转确认弹窗（modal 键盘出口） */
  useEffect(() => {
    if (!pendingTransition) {
      return;
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setPendingTransition(null);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [pendingTransition]);

  /** 状态流转（后端非法流转会返回业务错误 message） */
  const runTransition = async (status: string) => {
    if (!term || !status) {
      return;
    }
    setSaving(true);
    try {
      const updated = await updateGlossaryTerm(term.id, { status });
      setPendingTransition(null);
      // 书名号拼接改走 i18n 插值键（缺词期以既有键组合兜底，见 .fixtmp/need-locale-onto2.md）
      showToast(
        "success",
        t("glossary.toast.status_changed_to", `${t("glossary.toast.status_changed")} ${getLabel(status)}`),
      );
      onSaved(updated);
    } catch (e) {
      const { message } = readGlossaryError(e);
      showToast("error", `${t("glossary.toast.status_failed")}: ${message}`);
    } finally {
      setSaving(false);
    }
  };

  /** 流转入口：危险流转（废弃）先过确认门，其余直接执行 */
  const handleTransition = (target: { status: string; confirm?: boolean }) => {
    if (target.confirm) {
      setPendingTransition(target.status);
      return;
    }
    void runTransition(target.status);
  };

  // 编辑态但词条未命中（列表筛选后遗落等情况）
  if (mode === "edit" && !term) {
    return (
      <div className={`flex items-center justify-center h-full text-xs ${styles.muted}`}>
        {t("glossary.term_not_found")}
      </div>
    );
  }

  const transitions = term ? TRANSITIONS[term.status] ?? [] : [];
  const parentOptions = terms.filter((item) => item.id !== term?.id);

  return (
    <div className="space-y-4">
      <h2 className={`text-base font-bold ${styles.cardText}`}>
        {mode === "create" ? t("glossary.new_term") : term?.name}
      </h2>

      {/* 名称 */}
      <div>
        <div className={labelClass}>
          {t("glossary.field.name")} <span className={styles.dangerText} aria-hidden="true">*</span>
        </div>
        <input
          className={inputClass}
          placeholder={t("glossary.field.name_placeholder")}
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={saving}
        />
      </div>

      {/* 编码 + 领域 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <div className={labelClass}>{t("glossary.field.code")}</div>
          <input
            className={inputClass}
            placeholder={t("glossary.field.code_placeholder")}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            disabled={saving}
          />
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.domain")}</div>
          <select
            className={inputClass}
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
            disabled={saving}
          >
            <option value="">{t("glossary.field.domain_placeholder")}</option>
            {domainDict.getOptions().map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* 词条类型 + 负责人 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <div className={labelClass}>{t("glossary.field.term_type")}</div>
          <select
            className={inputClass}
            value={termType}
            onChange={(e) => setTermType(e.target.value)}
            disabled={saving}
          >
            <option value="">{t("glossary.field.term_type_placeholder")}</option>
            {termTypeDict.getOptions().map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.owner")}</div>
          <input
            className={inputClass}
            placeholder={t("glossary.field.owner_placeholder")}
            value={owner}
            onChange={(e) => setOwner(e.target.value)}
            disabled={saving}
          />
        </div>
      </div>

      {/* 关联本体实体 + 上位词条 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <div className={labelClass}>{t("glossary.field.object_type")}</div>
          <input
            className={inputClass}
            placeholder={t("glossary.field.object_type_placeholder")}
            value={objectTypeId}
            onChange={(e) => setObjectTypeId(e.target.value)}
            disabled={saving}
          />
          <div className={`text-[10px] mt-1 ${styles.muted}`}>
            {t("glossary.field.object_type_hint")}
          </div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.parent_term")}</div>
          <select
            className={inputClass}
            value={parentTermId ?? ""}
            onChange={(e) => setParentTermId(e.target.value ? Number(e.target.value) : null)}
            disabled={saving}
          >
            <option value="">{t("glossary.field.parent_term_placeholder")}</option>
            {parentOptions.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
          <div className={`text-[10px] mt-1 ${styles.muted}`}>
            {t("glossary.field.parent_term_hint")}
          </div>
        </div>
      </div>

      {/* 定义 */}
      <div>
        <div className={labelClass}>{t("glossary.field.definition")}</div>
        <textarea
          className={`${inputClass} resize-none`}
          placeholder={t("glossary.field.definition_placeholder")}
          value={definition}
          onChange={(e) => setDefinition(e.target.value)}
          disabled={saving}
          rows={5}
        />
      </div>

      {/* 别名 */}
      <div>
        <div className={labelClass}>{t("glossary.field.aliases")}</div>
        <TagListInput
          values={aliases}
          onChange={setAliases}
          placeholder={t("glossary.field.aliases_placeholder")}
          addLabel={t("glossary.tags_add_hint")}
          removeLabel={t("glossary.tag_remove")}
          disabled={saving}
        />
      </div>

      {/* 示例值 */}
      <div>
        <div className={labelClass}>{t("glossary.field.examples")}</div>
        <TagListInput
          values={examples}
          onChange={setExamples}
          placeholder={t("glossary.field.examples_placeholder")}
          addLabel={t("glossary.tags_add_hint")}
          removeLabel={t("glossary.tag_remove")}
          disabled={saving}
        />
      </div>

      {/* 标签 */}
      <div>
        <div className={labelClass}>{t("glossary.field.tags")}</div>
        <TagListInput
          values={tags}
          onChange={setTags}
          placeholder={t("glossary.field.tags_placeholder")}
          addLabel={t("glossary.tags_add_hint")}
          removeLabel={t("glossary.tag_remove")}
          disabled={saving}
        />
      </div>

      {/* 状态 + 版本 */}
      {term && (
        <div className="flex items-center gap-4 flex-wrap">
          <div>
            <div className={labelClass}>{t("glossary.field.status")}</div>
            <span
              className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold
                ${getColor(term.status, `${styles.badgeBg} ${styles.badgeText}`)}`}
            >
              {getLabel(term.status, t("glossary.status.draft"))}
            </span>
          </div>
          <div>
            <div className={labelClass}>{t("glossary.field.version")}</div>
            <span className={`text-xs ${styles.cardText}`}>v{term.version}</span>
          </div>
        </div>
      )}

      {/* 状态流转 */}
      {transitions.length > 0 && (
        <div className="flex items-center gap-2 flex-wrap">
          <span className={`text-[11px] font-semibold ${styles.muted}`}>
            {t("glossary.actions")}:
          </span>
          {transitions.map((tr) => (
            <button type="button"
              key={tr.status}
              className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold border transition
                disabled:opacity-50 ${tr.danger
                  ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                  : `${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}`}
              onClick={() => handleTransition(tr)}
              disabled={saving}
            >
              {saving && !pendingTransition ? <RotateCw size={14} className="animate-spin inline mr-1" aria-hidden="true" /> : null}
              {t(tr.key)}
            </button>
          ))}
        </div>
      )}

      {/* 操作按钮 */}
      <div className={`flex gap-2 pt-3 border-t ${styles.cardBorder}`}>
        <button type="button"
          className={`px-4 py-2 rounded-lg text-xs font-semibold transition disabled:opacity-50
            flex items-center gap-1 cursor-pointer ${styles.accentBg} ${styles.accentHover} ${styles.accentSolidText}`}
          onClick={handleSave}
          disabled={saving}
        >
          {saving ? <RotateCw size={14} className="animate-spin" aria-hidden="true" /> : null}
          {t("glossary.save")}
        </button>
        <button type="button"
          className={`px-4 py-2 rounded-lg text-xs font-semibold transition disabled:opacity-50 cursor-pointer
            ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.muted}`}
          onClick={onCancel}
          disabled={saving}
        >
          {t("glossary.cancel")}
        </button>
      </div>

      {/* 危险流转确认门（废弃 = 终态，须显式确认；role=alertdialog 由 ConfirmDialog 提供） */}
      <ConfirmDialog
        open={!!pendingTransition}
        title={t(
          "glossary.transition.confirm_title",
          `${t("glossary.actions")} · ${term?.name ?? ""}`,
        )}
        message={t("glossary.transition.deprecate_confirm_msg", `${term?.name ?? ""} · ${t("common.confirm.irreversible")}`)}
        confirmText={t("glossary.transition.deprecate")}
        cancelText={t("glossary.cancel")}
        variant="danger"
        onConfirm={() => void runTransition(pendingTransition ?? "")}
        onCancel={() => setPendingTransition(null)}
      />
    </div>
  );
}