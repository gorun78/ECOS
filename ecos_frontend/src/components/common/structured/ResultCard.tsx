/**
 * ResultCard — 认知结果卡（详设-08 §2.2 / §2.1 P06，消费认知/智能域端点结果）。
 *
 * 真值纪律（W204/C186 + 07 册 F07-12/22）：
 * - 结论/置信度只从 props 传入（useCognition / 引擎端点消费结果），组件内禁止合成 verdict/answer；
 * - 结论缺失显示「无数据」；置信度缺失显示「无数据」而**不是 0**（0 是合法引擎值）；
 * - 无证据时证据挂点显式显示「无来源」，不得补占位。
 * @license Apache-2.0
 */

import React from "react";
import StructuredCard from "./StructuredCard";
import { useLanguage } from "../../LanguageContext";

export interface ResultCardProps {
  /** 认知结论原文（引擎返回） */
  conclusion?: string | null;
  /** 置信度（0~1 原样展示，禁止前端四舍五入以外的加工）；null/undefined 显示「无数据」 */
  confidence?: number | null;
  /** 引擎是否返回了证据（来自端点结果，禁止组件推断） */
  evidenceAvailable?: boolean;
  /** 打开证据抽屉回调 */
  onOpenEvidence?: () => void;
  /** 动作区（如「采纳结论」「发起干预」，由调用方传入） */
  actions?: React.ReactNode;
}

/** 置信度显示：合法数字原样字符串化；缺失返回 null 交由外壳渲染「无数据」 */
function formatConfidence(confidence?: number | null): string | null {
  if (confidence === undefined || confidence === null || Number.isNaN(confidence)) return null;
  return String(confidence);
}

export default function ResultCard({
  conclusion,
  confidence,
  evidenceAvailable,
  onOpenEvidence,
  actions,
}: ResultCardProps) {
  const { t } = useLanguage();

  return (
    <StructuredCard
      title={t("common.result.title")}
      fields={[
        { key: "conclusion", label: t("common.result.conclusion"), value: conclusion ?? null },
        { key: "confidence", label: t("common.result.confidence"), value: formatConfidence(confidence) },
      ]}
      evidenceAvailable={evidenceAvailable}
      onOpenEvidence={onOpenEvidence}
      actions={actions}
    />
  );
}
