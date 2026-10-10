/**
 * AuditDetailDrawer — 审计详情抽屉（右滑入 / md 全屏），?auditId= 驱动。
 * 含 detail_json 折叠展示 + prevHash/currentHash 链哈希。
 * 无障碍（B §3.2 #8）：role=dialog + aria-modal + 焦点陷阱 + Esc 关闭。
 */
import React, { useEffect, useRef } from "react";
import { X, CheckCircle2, XCircle, Loader2 } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { useMediaQuery } from "../../../../hooks/useMediaQuery";
import type { AuditLog } from "../../../../services/security";

interface Props {
  audit: AuditLog | null;
  loading: boolean;
  onClose: () => void;
}

export default function AuditDetailDrawer({ audit, loading, onClose }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const isMobile = useMediaQuery("(max-width: 1023px)");
  const panelRef = useRef<HTMLElement | null>(null);

  // Esc 关闭 + 焦点陷阱
  useEffect(() => {
    if (!audit) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "Tab" && panelRef.current) {
        const focusables = panelRef.current.querySelectorAll<HTMLElement>(
          'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        if (!focusables.length) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    window.addEventListener("keydown", onKey);
    // 初始焦点进抽屉
    const first = panelRef.current?.querySelector<HTMLElement>("button");
    if (first) setTimeout(() => first.focus(), 0);
    return () => window.removeEventListener("keydown", onKey);
  }, [audit, onClose]);

  if (!audit) return null;

  const detailRaw = audit.detailJson;
  const detailText =
    detailRaw == null
      ? ""
      : typeof detailRaw === "string"
        ? detailRaw
        : JSON.stringify(detailRaw, null, 2);

  const isDenied = String(audit.result ?? "").toUpperCase().includes("FAIL") || String(audit.result ?? "").toUpperCase().includes("DENY");

  const field = (label: string, value: React.ReactNode, testid?: string) => (
    <div className="flex items-start gap-2 min-w-0" data-testid={testid}>
      <span className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted} shrink-0 w-28`}>{label}</span>
      <div className="text-xs flex-1 break-words min-w-0">{value}</div>
    </div>
  );

  return (
    <>
      <div className={`fixed inset-0 z-40 ${styles.overlayBg}`} onClick={onClose} data-testid="audit-drawer-overlay" />
      <aside
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="audit-drawer-title"
        data-testid="audit-detail-drawer"
        className={`fixed z-50 ${styles.cardBg} ${styles.cardText} shadow-2xl
          ${isMobile ? "inset-0" : "right-0 top-0 bottom-0 w-[480px] max-w-[calc(100vw-24px)]"}
          border-l ${styles.cardBorder} flex flex-col flex-wrap`}
      >
        <header className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} shrink-0`}>
          <h2 id="audit-drawer-title" className="text-sm font-semibold">
            {t("platform.audit.detail.title")}
          </h2>
          <button
            type="button"
            aria-label={t("platform.audit.detail.dismiss")}
            onClick={onClose}
            data-testid="audit-drawer-close"
            className={`p-1.5 rounded ${styles.cardTextMuted} hover:opacity-70 transition cursor-pointer`}
          >
            <X className="w-4 h-4" />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-thin">
          {loading ? (
            <div className="flex items-center gap-2 text-xs">
              <Loader2 className="w-4 h-4 animate-spin" />
              <span>{t("platform.audit.state.loading")}</span>
            </div>
          ) : (
            <>
              <div className={`text-xs font-mono ${styles.muted}`}>{audit.id}</div>
              {field(t("platform.audit.field.time"), audit.timestamp ? new Date(audit.timestamp).toLocaleString() : "—", "audit-detail-time")}
              {field(t("platform.audit.field.user"), audit.userId ?? "—", "audit-detail-user")}
              {field(t("platform.audit.field.action"), audit.action || audit.eventType || "—", "audit-detail-action")}
              {field(t("platform.audit.field.resource"), audit.resource || audit.resourceType || "—", "audit-detail-resource")}
              {field(
                t("platform.audit.field.result"),
                isDenied ? (
                  <span className="inline-flex items-center gap-1 font-medium"><XCircle className="w-3.5 h-3.5" /><span>{audit.result}</span></span>
                ) : (
                  <span className="inline-flex items-center gap-1 font-medium"><CheckCircle2 className="w-3.5 h-3.5" /><span>{audit.result}</span></span>
                ),
                "audit-detail-result"
              )}
              {audit.ipAddress && field(t("platform.audit.field.ip"), audit.ipAddress, "audit-detail-ip")}
              {audit.userAgent && field(t("platform.audit.field.userAgent"), audit.userAgent, "audit-detail-ua")}
              {audit.traceId && field(t("platform.audit.field.traceId"), <code className="font-mono text-[11px] break-all">{audit.traceId}</code>, "audit-detail-trace")}
              {audit.duration != null && field(t("platform.audit.field.duration"), `${audit.duration} ms`, "audit-detail-duration")}

              {/* 链哈希 */}
              {(audit.prevHash || audit.currentHash) && (
                <div className={`rounded-md border ${styles.appBorder} p-3 space-y-2`} data-testid="audit-detail-chain">
                  <div className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>
                    {t("platform.audit.field.chainHash")}
                  </div>
                  {audit.prevHash && (
                    <div className="flex flex-col gap-0.5">
                      <span className={`text-[10px] ${styles.muted}`}>prevHash</span>
                      <code className="font-mono text-[10px] break-all">{audit.prevHash}</code>
                    </div>
                  )}
                  {audit.currentHash && (
                    <div className="flex flex-col gap-0.5">
                      <span className={`text-[10px] ${styles.muted}`}>currentHash</span>
                      <code className="font-mono text-[10px] break-all">{audit.currentHash}</code>
                    </div>
                  )}
                </div>
              )}

              {/* detail_json 折叠 */}
              {detailText && (
                <details className={`rounded-md border ${styles.appBorder} p-2`} data-testid="audit-detail-json-details">
                  <summary className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted} cursor-pointer select-none`}>
                    {t("platform.audit.field.detail")}
                  </summary>
                  <pre className="mt-2 p-2 rounded text-[11px] font-mono whitespace-pre-wrap break-all">
                    {detailText}
                  </pre>
                </details>
              )}
            </>
          )}
        </div>
      </aside>
    </>
  );
}
