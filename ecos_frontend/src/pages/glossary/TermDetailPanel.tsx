/**
 * TermDetailPanel — 术语词条只读详情（详情 Tab 的查看态）。
 *
 * 职责：纯展示词条语义字段，不提供任何写通道；「编辑」出口由父容器切到 TermFormPanel
 * （前端检视报告 §4.3 行169：点列表项不得直进编辑态）。
 * 数据：全部来自父容器传入的词条对象，本组件零请求、零 setState。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { Edit3, FileText } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import type { GlossaryTerm } from "../../services/glossary";

interface TermDetailPanelProps {
  /** 当前选中词条（父容器保证非空） */
  term: GlossaryTerm;
  /** 全量词条（用于把上位词条 id 还原为名称） */
  terms: GlossaryTerm[];
  /** 进入编辑上下文 */
  onEdit: () => void;
}

export default function TermDetailPanel({ term, terms, onEdit }: TermDetailPanelProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor } = useDict("glossary_status", locale);
  const domainDict = useDict("glossary_domain", locale);
  const termTypeDict = useDict("glossary_term_type", locale);

  const labelClass = `text-[11px] font-semibold mb-1 ${styles.cardTextMuted}`;
  const valueClass = `text-xs break-words ${styles.cardText}`;

  /** 元信息字段（缺值显示中性占位，不留空白歧义） */
  const filled = (value: string | null | undefined) => value || "—";
  const parentTerm = term.parentTermId
    ? terms.find((item) => item.id === term.parentTermId)
    : null;

  /** 标签型字段（空数组 = 未配置，按 — 展示） */
  const chipList = (values: string[]) => (
    values.length > 0 ? (
      <div className="flex flex-wrap gap-1.5">
        {values.map((value, index) => (
          <span
            key={`${value}-${index}`}
            className={`inline-block px-2 py-0.5 rounded border text-[11px]
              ${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}
          >
            {value}
          </span>
        ))}
      </div>
    ) : (
      <span className={valueClass}>—</span>
    )
  );

  return (
    <div className="space-y-4">
      {/* 标题 + 状态 + 显式编辑入口 */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className={`text-base font-bold truncate ${styles.cardText}`}>{term.name}</h2>
          <div className={`flex items-center gap-3 mt-1 flex-wrap text-[11px] ${styles.muted}`}>
            <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold
              ${getColor(term.status, `${styles.badgeBg} ${styles.badgeText}`)}`}
            >
              {getLabel(term.status, t("glossary.status.draft"))}
            </span>
            <span>
              {t("glossary.field.version")} v{term.version}
            </span>
            {term.code && <span className={`font-mono ${styles.cardTextMuted}`}>{term.code}</span>}
          </div>
        </div>
        <button type="button"
          className={`shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold
            border transition cursor-pointer ${styles.accentBg} ${styles.accentHover} ${styles.accentSolidText} ${styles.accentBorder}`}
          onClick={onEdit}
        >
          <Edit3 size={13} aria-hidden="true" />
          {t("glossary.edit")}
        </button>
      </div>

      {/* 定义 */}
      <div className={`rounded-lg border p-4 ${styles.cardBorder} ${styles.cardBg}`}>
        <div className={labelClass}>{t("glossary.field.definition")}</div>
        {term.definition ? (
          <p className={`${valueClass} leading-normal whitespace-pre-wrap`}>{term.definition}</p>
        ) : (
          <div className={`flex items-center gap-2 text-xs ${styles.muted}`}>
            <FileText size={14} aria-hidden="true" />
            {t("glossary.field.definition_placeholder")}
          </div>
        )}
      </div>

      {/* 元信息 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <div className={labelClass}>{t("glossary.field.term_type")}</div>
          <div className={valueClass}>{filled(termTypeDict.getLabel(term.termType, term.termType))}</div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.domain")}</div>
          <div className={valueClass}>
            {term.domain ? domainDict.getLabel(term.domain, term.domain) : "—"}
          </div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.owner")}</div>
          <div className={valueClass}>{filled(term.owner)}</div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.object_type")}</div>
          <div className={`${valueClass} font-mono`}>{filled(term.objectTypeId)}</div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.parent_term")}</div>
          <div className={valueClass}>{parentTerm ? parentTerm.name : t("glossary.field.parent_term_placeholder")}</div>
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.status")}</div>
          <div className={valueClass}>{getLabel(term.status, t("glossary.status.draft"))}</div>
        </div>
      </div>

      {/* 标签型字段 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div>
          <div className={labelClass}>{t("glossary.field.aliases")}</div>
          {chipList(term.aliases)}
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.examples")}</div>
          {chipList(term.examples)}
        </div>
        <div>
          <div className={labelClass}>{t("glossary.field.tags")}</div>
          {chipList(term.tags)}
        </div>
      </div>
    </div>
  );
}
