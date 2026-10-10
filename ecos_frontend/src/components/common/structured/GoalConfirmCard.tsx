/**
 * GoalConfirmCard — 目标确认卡（详设-08 §2.2，消费 `POST /api/v1/wagent/goals/clarify` 结果，≤3 项一轮）。
 *
 * 真值纪律：任务目标与澄清问题只从 props 传入（引擎端点消费结果）；
 * 回答缺失渲染「无数据」，禁止客户端合成问题或预填答案（W204/C186）。
 * 本卡是 StructuredCard 外壳的列名/文案配置实例。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import StructuredCard from "./StructuredCard";
import Button from "../Button";

/** 单条澄清项（01 册四态语义中的 require_approval 前置澄清，问题来自引擎） */
export interface GoalClarifyItem {
  id: string;
  /** 引擎返回的澄清问题 */
  question: string;
  /** 用户已填回答；缺失时显示「无数据」 */
  answer?: string | null;
}

export interface GoalConfirmCardProps {
  /** 任务目标原文（引擎回显），缺失时字段显示「无数据」 */
  mission?: string | null;
  /** 澄清问题列表（一轮 ≤3 项，条数由服务端决定） */
  items?: GoalClarifyItem[];
  /** 确认目标回调（由调用方发起写请求并处理回执，组件不发请求） */
  onConfirm?: () => void;
  /** 修改目标回调 */
  onRevise?: () => void;
}

export default function GoalConfirmCard({ mission, items = [], onConfirm, onRevise }: GoalConfirmCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const actions = (
    <>
      {onConfirm && (
        <Button variant="primary" size="sm" onClick={onConfirm}>
          {t("common.goal.confirm")}
        </Button>
      )}
      {onRevise && (
        <Button variant="secondary" size="sm" onClick={onRevise}>
          {t("common.goal.revise")}
        </Button>
      )}
    </>
  );

  return (
    <StructuredCard
      title={t("common.goal.title")}
      fields={[{ key: "mission", label: t("common.goal.mission"), value: mission ?? null }]}
      actions={onConfirm || onRevise ? actions : undefined}
    >
      {items.length > 0 && (
        <div className="space-y-2">
          <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t("common.goal.questions")}</p>
          <ul className={`space-y-2 text-xs ${styles.cardText}`}>
            {items.map((item) => (
              <li key={item.id} className={`rounded-lg border p-2 ${styles.cardBorder}`}>
                <p>{item.question}</p>
                <p className={`mt-1 ${styles.cardTextMuted}`}>
                  {t("common.goal.answer")}: {item.answer ? item.answer : t("common.structured.no_data")}
                </p>
              </li>
            ))}
          </ul>
        </div>
      )}
    </StructuredCard>
  );
}
