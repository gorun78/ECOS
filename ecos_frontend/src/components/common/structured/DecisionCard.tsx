/**
 * DecisionCard — 决策建议卡（详设-08 §2.2 / §2.1 P08，消费决策/认知域端点结果）。
 *
 * 真值纪律：候选方案、推荐标记与影响范围只从 props 传入（引擎端点消费结果），
 * 组件不推断推荐项、不补造理由；方案 rationale 缺失显示「无数据」。
 * 「提交决策」等不可逆动作必须由调用方接公共收敛版 ConfirmDialog（§2.2 末行）。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import StructuredCard, { isFieldMissing } from "./StructuredCard";
import Badge from "../Badge";
import Button from "../Button";

/** 单个候选方案（引擎返回） */
export interface DecisionOption {
  id: string;
  /** 方案名 */
  label: string;
  /** 推荐理由；缺失显示「无数据」 */
  rationale?: string | null;
  /** 是否为引擎标记的推荐项（禁止前端自行推断） */
  recommended?: boolean;
}

export interface DecisionCardProps {
  /** 候选方案列表（来自引擎，空/null 时整卡空态） */
  options?: DecisionOption[] | null;
  /** 影响范围（业务语言） */
  impactScope?: string | null;
  /** 方案选择回调（由调用方决定是否挂接不可逆确认） */
  onSelect?: (optionId: string) => void;
}

export default function DecisionCard({ options, impactScope, onSelect }: DecisionCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const optionList = options ?? [];

  return (
    <StructuredCard
      title={t("common.decision.title")}
      fields={[{ key: "impact", label: t("common.decision.impact_scope"), value: impactScope ?? null }]}
    >
      {optionList.length > 0 && (
        <div className="space-y-2">
          <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t("common.decision.options")}</p>
          <ul className={`space-y-2 text-xs ${styles.cardText}`}>
            {optionList.map((option) => (
              <li
                key={option.id}
                className={`rounded-lg border p-2 flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between ${styles.cardBorder}`}
              >
                <div className="space-y-1">
                  <span className="inline-flex items-center gap-2">
                    <span className="font-semibold">{option.label}</span>
                    {option.recommended && <Badge variant="info">{t("common.decision.recommended")}</Badge>}
                  </span>
                  <p className={styles.cardTextMuted}>
                    {isFieldMissing(option.rationale) ? t("common.structured.no_data") : option.rationale}
                  </p>
                </div>
                {onSelect && (
                  <Button variant="secondary" size="sm" onClick={() => onSelect(option.id)}>
                    {t("common.decision.select")}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </StructuredCard>
  );
}
