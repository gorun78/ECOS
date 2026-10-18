/**
 * DegradationBanner — 降级说明条（详设-08 §2.3「降级」行：Readiness < 目标等级 或 通道降级）。
 *
 * 契约：
 * - 顶部说明条 + 明细入口（onOpenDetails 由调用方挂明细抽屉/页签）；
 * - 轮询兜底（progressChannel="poll"）时必须显示「降级：非实时（轮询 N 秒）」注记
 *   （与 RunProgress 共用 common.status.poll_fallback 单源文案，§2.2/W192 C174：
 *   未拿到 P-4 结论前不得声称流式）；
 * - W run 流 degrade 帧的 `requiresAck` 走本条的只读标注腿：W run 域没有任何确认端点
 *   （全仓 `/ack`、`/acknowledge` 只属于 runtime 告警、DQ 告警与 schema 变更三域，
 *   无一对 Run 降级生效），因此这里**不**渲染确认按钮——
 *   没有回路控件的按钮点了不改变任何状态，属假闭环；
 * - active/reasons/channel 均来自引擎端点消费结果，组件不推断降级原因。
 * @license Apache-2.0
 */

import React from "react";
import { AlertTriangle, ShieldAlert } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import type { ProgressChannel } from "../structured/RunProgress";

export interface DegradationBannerProps {
  /** 是否处于降级态（由调用方按引擎结果判定并传入） */
  active: boolean;
  /** 降级原因列表（业务语言，引擎返回）；为空时仅显示标题 */
  reasons?: string[];
  /** 明细入口回调；缺省不渲染按钮 */
  onOpenDetails?: () => void;
  /** 当前进度通道声明（服务端决定） */
  progressChannel?: ProgressChannel;
  /** 轮询间隔秒数（poll 通道注记展示） */
  pollIntervalSeconds?: number;
  /**
   * degrade 帧 `requiresAck`（帧 6 键面单源）：只读标注「需人工确认」，
   * 由调用方从承流层的 ackRequired 直接传入，组件不自行判定。
   */
  requiresAck?: boolean;
}

export default function DegradationBanner({
  active,
  reasons = [],
  onOpenDetails,
  progressChannel,
  pollIntervalSeconds,
  requiresAck = false,
}: DegradationBannerProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!active) return null;

  return (
    <div
      role="status"
      className={`flex flex-col sm:flex-row sm:items-start gap-2 rounded-xl border px-4 py-3 text-xs ${styles.warningBg} ${styles.warningBorder}`}
    >
      <AlertTriangle className={`w-4 h-4 shrink-0 mt-0.5 ${styles.warningText}`} aria-hidden="true" />
      <div className="flex-1 space-y-1">
        <p className={`font-semibold ${styles.warningText}`}>{t("common.status.degraded_title")}</p>
        {reasons.length > 0 && (
          <ul className={`space-y-0.5 ${styles.cardText}`}>
            {reasons.map((reason, index) => (
              <li key={index}>{reason}</li>
            ))}
          </ul>
        )}
        {progressChannel === "poll" && (
          <p className={styles.warningText}>{t("common.status.poll_fallback", { seconds: pollIntervalSeconds ?? 3 })}</p>
        )}
        {requiresAck && (
          <p className={`flex items-center gap-1 font-semibold ${styles.warningText}`}>
            <ShieldAlert className="w-3.5 h-3.5 shrink-0" aria-hidden="true" />
            {t("common.status.degraded_ack_required")}
          </p>
        )}
      </div>
      {onOpenDetails && (
        <button
          type="button"
          onClick={onOpenDetails}
          className={`shrink-0 font-bold cursor-pointer ${styles.accentText}`}
        >
          {t("common.status.view_details")}
        </button>
      )}
    </div>
  );
}
