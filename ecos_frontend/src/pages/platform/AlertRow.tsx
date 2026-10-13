/**
 * 告警行 — 单条告警的展示 + 确认(ack) 操作
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { BadgeCheck, Check, Loader2, UserCheck } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { MonitorAlert } from "../../services/platformApi";

interface Props {
  alert: MonitorAlert;
  /** 当前用户 clearance 等级 (L2 及以上可 ack) */
  canAck: boolean;
  busy?: boolean;
  onAck?: (id: string) => void;
  onOpen: (alert: MonitorAlert) => void;
}

function fmtTime(iso?: string): string {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 19);
}

/** severity → 徽章样式（用语义 token，不硬编码颜色） */
function severityVariant(severity: string): "warning" | "danger" | "info" {
  const s = severity?.toUpperCase();
  if (s === "CRITICAL" || s === "FATAL" || s === "P0" || s === "ERROR") return "danger";
  if (s === "HIGH" || s === "P1" || s === "WARN" || s === "WARNING") return "warning";
  return "info";
}

export default function AlertRow({ alert, canAck, busy, onAck, onOpen }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const closed = alert.status === "CLOSED" || alert.status === "ACKED" || alert.status === "RESOLVED";

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onOpen(alert)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onOpen(alert);
        }
      }}
      className={`w-full text-left border-b ${styles.cardBorder} last:border-0 px-4 py-2.5 flex items-center gap-3 transition ${styles.sidebarHoverBg} cursor-pointer`}
    >
      {/* severity pill */}
      <span
        className={`shrink-0 inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide border ${
          severityVariant(alert.severity) === "danger"
            ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
            : severityVariant(alert.severity) === "warning"
              ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
              : `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`
        }`}
      >
        {alert.severity || "—"}
      </span>

      {/* main text */}
      <div className="flex-1 min-w-0">
        <div className={`text-xs font-medium truncate ${styles.cardText}`}>{alert.title || alert.rule_code}</div>
        <div className={`text-[10px] font-mono truncate ${styles.cardTextMuted}`}>
          {alert.rule_code}
          {alert.source_module ? ` · ${alert.source_module}` : ""}
        </div>
      </div>

      {/* occurred time */}
      <span className={`shrink-0 hidden sm:inline text-[10px] font-mono whitespace-nowrap ${styles.cardTextMuted}`}>
        {fmtTime(alert.occurred_at)}
      </span>

      {/* status badge */}
      <span
        className={`shrink-0 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium border ${styles.badgeBg} ${styles.badgeText} ${styles.appBorder}`}
      >
        {closed ? <BadgeCheck className="w-3 h-3" /> : <UserCheck className="w-3 h-3" />}
        {t("platform.alerts.status." + (alert.status || "").toLowerCase()) || alert.status}
      </span>

      {/* ack button — real <button type="button"> (row is role=button on a div, so a
          nested native button is valid HTML). The `canAck && !closed` guard already
          means canAck is true here, so the prior inner canAck/Lock ternaries were dead. */}
      {(canAck && !closed) && (
        <button
          type="button"
          disabled={busy}
          aria-label={t("platform.alerts.ack")}
          onClick={(e) => {
            e.stopPropagation();
            onAck?.(alert.id);
          }}
          className={`shrink-0 inline-flex items-center gap-1 rounded-md px-2 py-1 text-[10px] font-medium border transition cursor-pointer ${styles.successBorder} ${styles.successText} hover:opacity-80 disabled:opacity-50 disabled:cursor-not-allowed`}
        >
          {busy ? <Loader2 className="w-3 h-3 animate-spin" /> : <Check className="w-3 h-3" />}
          {t("platform.alerts.ack")}
        </button>
      )}
      {closed && (
        <span className={`hidden md:inline shrink-0 text-[10px] font-mono ${styles.cardTextMuted}`}>
          {t("platform.alerts.closed")}
        </span>
      )}
    </div>
  );
}
