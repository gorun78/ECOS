/**
 * ErrorPanel — 失败态面板（详设-08 §2.3「失败」行：5xx / 引擎不可用）。
 *
 * 安全纪律（对 C159 同族问题加前端兜底）：
 * - 只渲染业务语言原因（reason）+ traceId（与卷 00 ApiResponse.traceId /
 *   响应头 X-ECOS-Trace-Id 对齐，便于后端定位）；
 * - `stack` 属性**故意不渲染**：即使调用方误传异常原文堆栈，本组件将其丢弃，
 *   绝不把堆栈行（"at xxx (file:line)"）呈现到 DOM —— 根因修复在后端，此处为前端闸门。
 * 操作出口：重试 / 改用降级 / 联系管理员（均为回调 props，由调用方发起请求）。
 * @license Apache-2.0
 */

import React from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";

export interface ErrorPanelProps {
  /** 业务语言失败原因（引擎/网关回包 message 或映射文案） */
  reason: string;
  /** 追踪号（ApiResponse.traceId / X-ECOS-Trace-Id），缺失不补占位符以外的假值 */
  traceId?: string | null;
  /**
   * 异常堆栈 —— 仅为了「接住」调用方可能传入的 stack 并丢弃；
   * 组件任何路径都不渲染该字段（见文件头安全纪律）。
   */
  stack?: string;
  /** 重试回调 */
  onRetry?: () => void;
  /** 改用降级通道回调（如切轮询 / 走缓存视图） */
  onDegrade?: () => void;
  /** 联系管理员回调（打开工单/指引） */
  onContactAdmin?: () => void;
}

export default function ErrorPanel({ reason, traceId, onRetry, onDegrade, onContactAdmin }: ErrorPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div
      role="alert"
      className={`rounded-xl border p-4 space-y-4 ${styles.dangerBg} ${styles.dangerBorder}`}
    >
      <div className="flex items-start gap-2">
        <AlertTriangle className={`w-5 h-5 shrink-0 mt-0.5 ${styles.dangerText}`} aria-hidden="true" />
        <div className="space-y-1">
          <p className={`text-sm font-semibold ${styles.dangerText}`}>{t("common.status.error_reason")}</p>
          <p className={`text-xs leading-normal ${styles.cardText}`}>{reason}</p>
          {traceId && (
            <p className={`font-mono text-[10px] tracking-wider uppercase ${styles.cardTextMuted}`}>
              {t("common.status.trace_id")}: {traceId}
            </p>
          )}
        </div>
      </div>

      {(onRetry || onDegrade || onContactAdmin) && (
        <div className="flex flex-wrap gap-2">
          {onRetry && (
            <Button variant="primary" size="sm" onClick={onRetry}>
              <RefreshCw className="w-3.5 h-3.5 mr-1" aria-hidden="true" />
              {t("common.status.retry")}
            </Button>
          )}
          {onDegrade && (
            <Button variant="secondary" size="sm" onClick={onDegrade}>
              {t("common.status.use_degraded")}
            </Button>
          )}
          {onContactAdmin && (
            <Button variant="ghost" size="sm" onClick={onContactAdmin}>
              {t("common.status.contact_admin")}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
