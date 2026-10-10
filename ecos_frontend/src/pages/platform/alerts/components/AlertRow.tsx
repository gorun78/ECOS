/**
 * AlertRow — 告警列表行（桌面表格行 + 移动端卡片由 MobileDataTable 另派）
 * 行 tabindex=0、Enter 开抽屉；ack/close 按钮独立调用。
 * 组件归属 src/pages/platform/alerts/components/ → 相对 ../../../.. 指 src/。
 */
import React from "react";
import {
  Check,
  XCircle,
  AlertTriangle,
  Info,
  X as XIcon,
  ChevronRight,
} from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import type { AlertRecord, AlertSeverity, AlertStatus } from "../../../../services/platform";

const SEVERITY_ICON: Record<AlertSeverity, React.ComponentType<{ className?: string }>> = {
  critical: AlertTriangle,
  error: XCircle,
  warn: AlertTriangle,
  info: Info,
};

const STATUS_TEXT: Record<AlertStatus, string> = {
  open: "open",
  acked: "success",
  closed: "muted",
};

interface AlertRowProps {
  alert: AlertRecord;
  onOpen: (alert: AlertRecord) => void;
  onAck: (alert: AlertRecord) => void;
  onClose: (alert: AlertRecord) => void;
  /** User with <L2 clearance 不可见 ack/close 按钮 */
  canOperate?: boolean;
  busy?: boolean;
}

export default function AlertRow({
  alert,
  onOpen,
  onAck,
  onClose,
  canOperate = true,
  busy = false,
}: AlertRowProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const SevIcon = SEVERITY_ICON[alert.severity] ?? Info;
  // 严重度/状态色 token（显式映射，避免 string-keyed 强转）
  const sevCls =
    alert.severity === "critical" || alert.severity === "error"
      ? styles.dangerText
      : alert.severity === "warn"
        ? styles.warningText
        : styles.infoText;
  const stCls =
    alert.status === "acked"
      ? styles.successText
      : alert.status === "closed"
        ? styles.muted
        : styles.muted;

  const onRowKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpen(alert);
    }
  };

  return (
    <div
      tabIndex={0}
      role="button"
      aria-label={`${t("platform.alerts.field.title")}: ${alert.title}`}
      data-testid={`alert-row-${alert.id}`}
      data-status={alert.status}
      data-severity={alert.severity}
      onClick={() => onOpen(alert)}
      onKeyDown={onRowKeyDown}
      className={`grid grid-cols-[auto_minmax(0,1fr)_auto_auto_auto_auto] items-center gap-3 px-3 py-2 rounded-md cursor-pointer
        border ${styles.appBorder} ${styles.cardBg} hover:opacity-90 focus:outline-none ${styles.accentBorder} focus:outline-hidden transition`}
    >
      <span className={`inline-flex items-center justify-center w-7 h-7 rounded-full ${styles.badgeBg}`}>
        <SevIcon className={`w-4 h-4 ${sevCls}`} aria-hidden />
      </span>

      <div className="min-w-0 flex flex-col">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-xs font-semibold truncate" data-testid="alert-title">
            {alert.title}
          </span>
          {alert.ruleCode && (
            <span className={`text-[10px] font-mono ${styles.muted} shrink-0`} title={alert.ruleCode}>
              {alert.ruleCode}
            </span>
          )}
        </div>
        <div className={`text-[10px] font-mono ${styles.muted}`}>
          {alert.sourceModule && <span>{alert.sourceModule}</span>}
          {alert.occurredAt && (
            <span className="ml-2">{new Date(alert.occurredAt).toLocaleString()}</span>
          )}
        </div>
      </div>

      <span className={`text-[10px] font-mono ${styles.muted} tabular-nums shrink-0`} data-testid={`alert-sev-${alert.id}`}>
        {t(`platform.alerts.severity.${alert.severity}`)}
      </span>

      <span className={`text-[10px] font-medium shrink-0 ${stCls}`} data-testid={`alert-status-${alert.id}`}>
        {t(`platform.alerts.status.${alert.status}`)}
      </span>

      <span className={`text-[10px] ${styles.muted} w-10 shrink-0 text-right`}>
        {alert.upgradeSlaMin != null ? `${alert.upgradeSlaMin}m` : "—"}
      </span>

      <div className="flex items-center gap-1 shrink-0">
        {canOperate && alert.status === "open" && (
          <button
            type="button"
            data-testid={`alert-ack-${alert.id}`}
            aria-label={t("platform.alerts.action.ack")}
            disabled={busy}
            onClick={(e) => {
              e.stopPropagation();
              onAck(alert);
            }}
            className={`px-2 py-1 rounded-md text-[10px] font-semibold border ${styles.appBorder} ${styles.inputBg} ${styles.inputText} hover:opacity-80 transition cursor-pointer disabled:opacity-40`}
          >
            <Check className="inline w-3 h-3 mr-1" />
            {t("platform.alerts.action.ack")}
          </button>
        )}
        {canOperate && alert.status !== "closed" && (
          <button
            type="button"
            data-testid={`alert-close-${alert.id}`}
            aria-label={t("platform.alerts.action.close")}
            disabled={busy}
            onClick={(e) => {
              e.stopPropagation();
              onClose(alert);
            }}
            className={`px-2 py-1 rounded-md text-[10px] font-semibold border ${styles.appBorder} ${styles.inputBg} ${styles.muted} hover:opacity-80 transition cursor-pointer disabled:opacity-40`}
          >
            <XIcon className="inline w-3 h-3 mr-1" />
            {t("platform.alerts.action.close")}
          </button>
        )}
        {!canOperate && <ChevronRight className={`w-3.5 h-3.5 ${styles.muted}`} aria-hidden />}
      </div>
    </div>
  );
}
