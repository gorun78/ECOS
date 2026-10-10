/**
 * DeniedPanel — 无权限态面板（详设-08 §2.3「无权限」行：security-engine DENY / allowedColumns=[]）。
 *
 * 契约（W203/C185 + 01 册四态）：
 * - 说明缺少的权限（missingPermissions 来自引擎 DENY 回执，禁止客户端推断/合成 DENY）；
 * - 申请入口（onRequestAccess 回调）；
 * - 掩码字段一律渲染 token 掩码符 MASK_PLACEHOLDER（"••••"），
 *   **不得渲染为空串**、不得回显真实值 —— 即使调用方误传 value 也只出掩码符（§六-4 凭据/敏感值纪律）。
 * @license Apache-2.0
 */

import React from "react";
import { ShieldAlert } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";

/** 掩码占位符单源：任何 DENY/掩码态字段的展示值（非空串，防「渲染为空」误判为无数据） */
export const MASK_PLACEHOLDER = "••••";

/** 被掩码的字段（label 来自 allowedColumns=[] / 列过滤回执） */
export interface MaskedField {
  label: string;
  /** 真实值——本组件任何情况下都不渲染此字段，仅出 MASK_PLACEHOLDER */
  value?: string | null;
}

export interface DeniedPanelProps {
  /** 缺少的权限说明列表（引擎 DENY 回执原文/映射文案） */
  missingPermissions?: string[];
  /** 掩码字段列表（allow_with_mask / allowedColumns=[] 场景） */
  maskedFields?: MaskedField[];
  /** 申请权限入口回调（跳权限申请页/工单） */
  onRequestAccess?: () => void;
  /** 追踪号（可选，与 ErrorPanel 同语义） */
  traceId?: string | null;
}

export default function DeniedPanel({ missingPermissions = [], maskedFields = [], onRequestAccess, traceId }: DeniedPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div
      role="alert"
      className={`rounded-xl border p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}
    >
      <div className="flex items-start gap-2">
        <ShieldAlert className={`w-5 h-5 shrink-0 mt-0.5 ${styles.dangerText}`} aria-hidden="true" />
        <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("common.status.denied_title")}</h3>
      </div>

      {missingPermissions.length > 0 && (
        <div className="space-y-1">
          <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t("common.status.denied_missing")}</p>
          <ul className={`space-y-1 text-xs list-disc pl-4 ${styles.cardText}`}>
            {missingPermissions.map((permission, index) => (
              <li key={index}>{permission}</li>
            ))}
          </ul>
        </div>
      )}

      {maskedFields.length > 0 && (
        <div className="space-y-1">
          <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t("common.status.masked_fields")}</p>
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
            {maskedFields.map((field) => (
              <div key={field.label} className={`rounded-lg border p-2 ${styles.cardBorder}`}>
                <dt className={styles.cardTextMuted}>{field.label}</dt>
                {/* 掩码态恒渲染 MASK_PLACEHOLDER：不回显 value、不渲染空串 */}
                <dd className={`font-mono ${styles.cardText}`} aria-label={field.label}>
                  {MASK_PLACEHOLDER}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}

      {traceId && (
        <p className={`font-mono text-[10px] tracking-wider uppercase ${styles.cardTextMuted}`}>
          {t("common.status.trace_id")}: {traceId}
        </p>
      )}

      {onRequestAccess && (
        <div className="pt-1">
          <Button variant="primary" size="sm" onClick={onRequestAccess}>
            {t("common.status.request_access")}
          </Button>
        </div>
      )}
    </div>
  );
}
