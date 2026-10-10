/**
 * AlertDetailDrawer — 告警详情抽屉（右侧滑入 / md 全屏），?alertId= 驱动。
 * 关闭键：Esc + 关闭按钮 + 点击 overlay。
 */
import React, { useEffect } from "react";
import { X, Loader2, Copy, Check } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { useMediaQuery } from "../../../../hooks/useMediaQuery";
import type { AlertRecord } from "../../../../services/platform";

interface Props {
  alert: AlertRecord | null;
  loading: boolean;
  onClose: () => void;
}

export default function AlertDetailDrawer({ alert, loading, onClose }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery("(max-width: 1023px)");

  // Esc 关闭
  useEffect(() => {
    if (!alert) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [alert, onClose]);

  if (!alert) return null;

  const detailText =
    alert.detail != null
      ? typeof alert.detail === "string"
        ? alert.detail
        : JSON.stringify(alert.detail, null, 2)
      : null;

  const renderField = (labelKey: string, value: React.ReactNode, testid?: string) => (
    <div className="flex items-start gap-2 min-w-0" data-testid={testid}>
      <span className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted} shrink-0 w-28`}>
        {t(labelKey)}
      </span>
      <div className="text-xs flex-1 break-words min-w-0">{value}</div>
    </div>
  );

  return (
    <>
      <div
        className={`fixed inset-0 z-40 ${styles.overlayBg}`}
        onClick={onClose}
        data-testid="alert-drawer-overlay"
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="alert-drawer-title"
        data-testid="alert-detail-drawer"
        className={`fixed z-50 ${styles.cardBg} ${styles.cardText} shadow-2xl
          ${isMobile ? "inset-0" : "right-0 top-0 bottom-0 w-[480px] max-w-[calc(100vw-24px)]"}
          border-l ${styles.cardBorder} flex flex-col flex-wrap`}
      >
        <header className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} shrink-0`}>
          <h2 id="alert-drawer-title" className="text-sm font-semibold">
            {t("platform.alerts.detail.title")}
          </h2>
          <button
            type="button"
            aria-label={t("platform.alerts.detail.dismiss")}
            onClick={onClose}
            className={`p-1.5 rounded ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/10 transition cursor-pointer`}
          >
            <X className="w-4 h-4" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-thin">
          {loading && (
            <div className="flex items-center gap-2 text-xs muted">
              <Loader2 className="w-4 h-4 animate-spin" />
              {alert.id}
            </div>
          )}
          {!loading && (
            <>
              <div className={`text-xs font-mono ${styles.muted}`}>{alert.id}</div>
              {renderField("platform.alerts.field.title", <strong>{alert.title}</strong>, "alert-detail-title")}
              {renderField("platform.alerts.field.severity", alert.severity, "alert-detail-severity")}
              {renderField("platform.alerts.field.status", t(`platform.alerts.status.${alert.status}`), "alert-detail-status")}
              {alert.sourceModule && renderField("platform.alerts.field.source", alert.sourceModule, "alert-detail-source")}
              {alert.occurredAt &&
                renderField("platform.alerts.field.occurredAt", new Date(alert.occurredAt).toLocaleString(), "alert-detail-occurred")}
              {alert.ruleCode && renderField("platform.alerts.field.ruleCode", alert.ruleCode, "alert-detail-rule")}
              {alert.upgradeSlaMin != null &&
                renderField("platform.alerts.field.upgradeSla", `${alert.upgradeSlaMin} min`, "alert-detail-upgradeSla")}
              {alert.ackBy && renderField("platform.alerts.field.ackBy", alert.ackBy, "alert-detail-ackby")}
              {alert.ackTime &&
                renderField("platform.alerts.field.ackTime", new Date(alert.ackTime).toLocaleString(), "alert-detail-acktime")}
              {alert.closeTime &&
                renderField("platform.alerts.field.closeTime", new Date(alert.closeTime).toLocaleString(), "alert-detail-closetime")}
              {alert.traceId &&
                renderField(
                  "platform.alerts.field.traceId",
                  <code className="font-mono text-[11px] break-all">{alert.traceId}</code>,
                  "alert-detail-trace"
                )}
              {detailText && (
                <div className="flex flex-col gap-1 min-w-0" data-testid="alert-detail-json">
                  <span className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>
                    {t("platform.alerts.field.detail")}
                  </span>
                  <pre className={`p-2 rounded-md text-[11px] font-mono whitespace-pre-wrap break-all ${styles.inputBg} border ${styles.inputBorder}`}>
                    {detailText}
                  </pre>
                </div>
              )}
            </>
          )}
        </div>
      </aside>
    </>
  );
}
