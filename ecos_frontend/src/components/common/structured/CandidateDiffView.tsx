/**
 * CandidateDiffView — 候选差异对比（详设-08 §2.2「差异对比」：当前 vs 建议，逐项接受）。
 *
 * 真值纪律：纯展示 props —— 数据 = `GET /api/v1/wagent/candidates` 契约消费结果
 * （复用 04/05/03 册 Candidate 契约，禁止页面内二次实现）；接受/拒绝由调用方经
 * `POST /api/v1/wagent/candidates/{id}/approve` 通道处理，本组件只回调。
 * 字段值缺失（null/undefined/''）渲染显式「未设置」，不渲染空串、不补占位值。
 * @license Apache-2.0
 */

import React from "react";
import { Check, X } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import EmptyState from "../EmptyState";
import Button from "../Button";
import { isFieldMissing } from "./StructuredCard";

/** 单行差异项（引擎 Candidate 契约投影） */
export interface CandidateDiffItem {
  /** 字段名（业务语言，来自引擎） */
  field: string;
  /** 当前值 */
  current?: string | null;
  /** 建议值 */
  proposed?: string | null;
  /** 已有审批决定（引擎/调用方状态回传）；未决时缺省 */
  decision?: "accepted" | "rejected" | null;
}

export interface CandidateDiffViewProps {
  items?: CandidateDiffItem[] | null;
  /** 逐项接受回调（调用方负责发审批请求与回执校验，禁止前端伪造 hash） */
  onAcceptItem?: (field: string) => void;
  /** 逐项拒绝回调 */
  onRejectItem?: (field: string) => void;
}

export default function CandidateDiffView({ items, onAcceptItem, onRejectItem }: CandidateDiffViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const list = items ?? [];

  if (list.length === 0) {
    return <EmptyState title={t("common.diff.title")} description={t("common.structured.no_data")} />;
  }

  /** 值单元格：缺失显式「未设置」 */
  const cell = (value?: string | null) =>
    isFieldMissing(value) ? (
      <span className={styles.cardTextMuted}>{t("common.diff.unset")}</span>
    ) : (
      <span className={styles.cardText}>{value}</span>
    );

  return (
    <section className={`rounded-xl border p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`} aria-label={t("common.diff.title")}>
      <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("common.diff.title")}</h3>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className={`text-left ${styles.cardTextMuted}`}>
              <th className="pr-4 py-1 font-medium">{t("common.diff.field")}</th>
              <th className="pr-4 py-1 font-medium">{t("common.diff.current")}</th>
              <th className="pr-4 py-1 font-medium">{t("common.diff.proposed")}</th>
              <th className="py-1 font-medium">{t("common.diff.accept")}</th>
            </tr>
          </thead>
          <tbody>
            {list.map((item) => (
              <tr key={item.field} className={`border-t ${styles.appBorder}`}>
                <td className={`pr-4 py-2 font-semibold ${styles.cardText}`}>{item.field}</td>
                <td className="pr-4 py-2">{cell(item.current)}</td>
                <td className="pr-4 py-2">{cell(item.proposed)}</td>
                <td className="py-2">
                  {item.decision === "accepted" ? (
                    <span className={`inline-flex items-center gap-1 ${styles.successText}`}>
                      <Check className="w-3.5 h-3.5" aria-hidden="true" />
                      {t("common.diff.accepted")}
                    </span>
                  ) : item.decision === "rejected" ? (
                    <span className={`inline-flex items-center gap-1 ${styles.dangerText}`}>
                      <X className="w-3.5 h-3.5" aria-hidden="true" />
                      {t("common.diff.rejected")}
                    </span>
                  ) : (
                    <span className="inline-flex gap-2">
                      {onAcceptItem && (
                        <Button variant="secondary" size="sm" onClick={() => onAcceptItem(item.field)}>
                          {t("common.diff.accept")}
                        </Button>
                      )}
                      {onRejectItem && (
                        <Button variant="ghost" size="sm" onClick={() => onRejectItem(item.field)}>
                          {t("common.diff.reject")}
                        </Button>
                      )}
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
