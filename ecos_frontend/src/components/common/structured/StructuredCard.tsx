/**
 * StructuredCard — 结构化卡片公共外壳（详设-08 §2.2「对话 + 卡片」总则）。
 *
 * GoalConfirmCard / ReadinessCard / ResultCard / DecisionCard 四张卡共享本外壳
 * （标题 / 正文 / 动作区 / 证据挂点），四卡只做列名与文案配置差异，禁止二次实现。
 *
 * 真值纪律（W204/C186）：
 * - 卡片数据只从 props 传入（= 引擎端点消费结果），组件内禁止合成/推断任何值；
 * - 字段缺失（undefined / null / 空串）一律渲染显式「无数据」空态文案，禁止补 0 / 占位值；
 * - 整卡无数据时复用既有 EmptyState 组件呈现空态。
 *
 * 主题纪律：结构性颜色全部取 useTheme() token（§七.1），文案全部走 t()（§六）。
 * @license Apache-2.0
 */

import React from "react";
import { FileSearch } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import EmptyState from "../EmptyState";

/** 单个字段配置：label 由调用方经 t() 提供，value 为引擎返回的原始值 */
export interface StructuredCardField {
  key: string;
  label: string;
  /** 字段原始值；undefined/null/'' 一律视为缺失，渲染「无数据」而非补零 */
  value?: string | number | null;
}

export interface StructuredCardProps {
  /** 卡片标题（调用方经 t() 传入） */
  title: string;
  /** 字段列表；全部缺失时字段值显示「无数据」，列表为空且无 children 时整卡显示空态 */
  fields?: StructuredCardField[];
  /** 正文附加区（差距清单 / 方案列表等子结构，数据同样只来自 props） */
  children?: React.ReactNode;
  /** 动作区（按钮等，文案由调用方 t() 提供） */
  actions?: React.ReactNode;
  /** 证据挂点：提供时渲染「查看证据」入口；证据缺失的语义由调用方决定并传入 evidenceAvailable */
  onOpenEvidence?: () => void;
  /** 是否存在证据（来自引擎端点结果，禁止组件内推断）；false/缺省时证据入口显示「无来源」 */
  evidenceAvailable?: boolean;
  /** 无数据时的空态说明文案（调用方可覆写，默认 common.structured.no_data） */
  emptyText?: string;
  className?: string;
}

/** 判断字段值是否缺失（空串 / null / undefined 均算缺失，0 是合法值） */
export function isFieldMissing(value?: string | number | null): boolean {
  return value === undefined || value === null || value === "";
}

/**
 * StructuredCard — 四卡共享外壳。
 * 排版遵循规范 §七.4：卡片标题 `font-semibold text-sm`，正文 `text-xs`。
 */
export default function StructuredCard({
  title,
  fields = [],
  children,
  actions,
  onOpenEvidence,
  evidenceAvailable = false,
  emptyText,
  className = "",
}: StructuredCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const noData = emptyText ?? t("common.structured.no_data");
  const hasBody = fields.length > 0 || children !== undefined && children !== null;

  return (
    <section
      className={`rounded-xl border p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder} ${className}`}
      aria-label={title}
    >
      <header className="flex items-center justify-between gap-2">
        <h3 className={`text-sm font-semibold ${styles.cardText}`}>{title}</h3>
        {onOpenEvidence && (
          <button
            type="button"
            onClick={onOpenEvidence}
            aria-disabled={!evidenceAvailable}
            className={`inline-flex items-center gap-1 text-xs ${
              evidenceAvailable ? styles.accentText : styles.cardTextMuted
            }`}
          >
            <FileSearch className="w-4 h-4" aria-hidden="true" />
            {evidenceAvailable ? t("common.result.open_evidence") : t("common.result.no_source")}
          </button>
        )}
      </header>

      {!hasBody ? (
        <EmptyState title={noData} className="py-8" />
      ) : (
        <div className="space-y-4">
          {fields.length > 0 && (
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {fields.map((field) => (
                <div key={field.key}>
                  <dt className={`text-xs ${styles.cardTextMuted}`}>{field.label}</dt>
                  <dd className={`text-xs leading-normal ${styles.cardText}`}>
                    {isFieldMissing(field.value) ? noData : String(field.value)}
                  </dd>
                </div>
              ))}
            </dl>
          )}
          {children}
        </div>
      )}

      {actions && (
        <footer className={`flex flex-wrap gap-2 pt-2 border-t ${styles.appBorder}`}>{actions}</footer>
      )}
    </section>
  );
}
