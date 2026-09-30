/**
 * 告警详情抽屉 — 展示单条告警全字段 + ack/replay 操作位
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect } from "react";
import { Check, Clock, X, Fingerprint, Terminal } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { MonitorAlert } from "../../services/platformApi";

interface Props {
  alert: MonitorAlert | null;
  canAck: boolean;
  busy?: boolean;
  onAck?: (id: string) => void;
  onClose: () => void;
}

function fmtTime(iso?: string): string {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 19);
}

export default function AlertDetailDrawer({ alert, canAck, busy, onAck, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ESC to close
  useEffect(() => {
    if (!alert) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [alert, onClose]);

  if (!alert) return null;

  const closed = alert.status === "CLOSED" || alert.status === "ACKED" || alert.status === "RESOLVED";
  const showAck = canAck && !closed;

  const fields: Array<{ label: string; value: string; mono?: boolean }> = [
    { label: t("platform.alerts.detail.ruleCode"), value: alert.rule_code, mono: true },
    { label: t("platform.alerts.detail.source"), value: alert.source_module },
    { label: t("platform.alerts.detail.severity"), value: alert.severity },
    { label: t("platform.alerts.detail.status"), value: alert.status },
    { label: t("platform.alerts.detail.occurred"), value: fmtTime(alert.occurred_at), mono: true },
    { label: t("platform.alerts.detail.ackBy"), value: alert.ack_by || "—" },
    { label: t("platform.alerts.detail.ackTime"), value: fmtTime(alert.ack_time), mono: true },
    { label: t("platform.alerts.detail.closeTime"), value: fmtTime(alert.close_time), mono: true },
  ];

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* overlay */}
      <div
        className={`absolute inset-0 ${styles.overlayBg}`}
        onClick={onClose}
        aria-hidden="true"
      />
      {/* panel */}
      <div
        className={`relative h-full w-full sm:w-[420px] ${styles.cardBg} border-l ${styles.cardBorder} shadow-xl flex flex-col`}
        role="dialog"
        aria-modal="true"
      >
        {/* header */}
        <div className={`shrink-0 flex items-start justify-between gap-3 px-5 py-4 border-b ${styles.cardBorder}`}>
          <div className="min-w-0">
            <h2 className={`text-sm font-bold ${styles.cardText}`}>{t("platform.alerts.detail.title")}</h2>
            <p className={`mt-0.5 text-xs truncate ${styles.cardTextMuted}`} title={alert.title || alert.rule_code}>
              {alert.title || alert.rule_code}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className={`shrink-0 p-1 rounded-md hover:bg-black/5 dark:hover:bg-white/10 transition cursor-pointer ${styles.cardTextMuted}`}
            aria-label="close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* body */}
        <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4 space-y-4">
          {/* trace id */}
          {alert.trace_id && (
            <FieldRow
              icon={<Fingerprint className="w-3.5 h-3.5" />}
              label={t("platform.alerts.detail.traceId")}
              value={alert.trace_id}
              mono
            />
          )}

          <div className="grid grid-cols-1 gap-2.5">
            {fields.map((f) => (
              <FieldRow key={f.label} label={f.label} value={f.value} mono={f.mono} icon={<Clock className="w-3.5 h-3.5" />} />
            ))}
          </div>

          {showAck && (
            <div className={`rounded-lg border p-3 ${styles.warningBg} ${styles.warningBorder}`}>
              <p className={`text-[11px] ${styles.warningText}`}>{t("platform.alerts.ackHint")}</p>
            </div>
          )}
        </div>

        {/* footer / actions */}
        <div className={`shrink-0 border-t ${styles.cardBorder} px-5 py-3 flex items-center justify-end gap-2`}>
          <button
            type="button"
            onClick={onClose}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardText} transition cursor-pointer hover:opacity-90`}
          >
            {t("platform.alerts.detail.close")}
          </button>
          {canAck ? (
            <button
              type="button"
              disabled={!showAck || busy}
              onClick={() => onAck?.(alert.id)}
              className={`flex items-center gap-1.5 px-3.5 py-1.5 text-xs font-bold rounded-lg transition cursor-pointer ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-40 disabled:cursor-not-allowed`}
            >
              <Check className="w-3.5 h-3.5" />
              {t("platform.alerts.ack")}
            </button>
          ) : (
            <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-[11px] rounded-lg border ${styles.appBorder} ${styles.cardTextMuted}`}>
              <Terminal className="w-3.5 h-3.5" />
              {t("platform.alerts.requiresL2")}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

function FieldRow({
  label,
  value,
  mono,
  icon,
}: {
  label: string;
  value: string;
  mono?: boolean;
  icon?: React.ReactNode;
}) {
  const { styles } = useTheme();
  return (
    <div className="flex items-start gap-2">
      {icon && <span className={`mt-0.5 shrink-0 ${styles.cardTextMuted}`}>{icon}</span>}
      <div className="flex-1 min-w-0">
        <div className={`text-[10px] uppercase tracking-wider ${styles.cardTextMuted}`}>{label}</div>
        <div className={`text-xs break-words ${mono ? "font-mono" : ""} ${styles.cardText}`}>{value}</div>
      </div>
    </div>
  );
}
