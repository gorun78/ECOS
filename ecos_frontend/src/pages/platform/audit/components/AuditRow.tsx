/**
 * AuditRow — 审计日志行（时间/用户/动作/资源/结果/IP/链验证状态）。
 * props { audit, onOpen }（设计 B §3.2 #3）。结果 denied 用图标+文本，不单靠色块。
 */
import React from "react";
import { CheckCircle2, XCircle, ChevronRight } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import type { AuditLog } from "../../../../services/security";

function chainStatus(audit: AuditLog): "ok" | "broken" | "unstamped" | "n/a" {
  if (audit.prevHash && audit.currentHash) return "ok";
  if (audit.chainStatus) {
    return audit.chainStatus === "broken" ? "broken" : audit.chainStatus === "ok" ? "ok" : "unstamped";
  }
  if (!audit.prevHash && !audit.currentHash) return "n/a";
  return "unstamped";
}

export default function AuditRow({ audit, onOpen }: { audit: AuditLog; onOpen: (a: AuditLog) => void }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const denied = String(audit.result ?? "").toUpperCase().includes("FAIL") || String(audit.result ?? "").toUpperCase().includes("DENY");
  const cs = chainStatus(audit);

  const resultNode = denied ? (
    <span className={`inline-flex items-center gap-1 font-medium ${styles.dangerText}`}>
      <XCircle className="w-3.5 h-3.5" />
      {t("platform.audit.result.denied")}
    </span>
  ) : (
    <span className={`inline-flex items-center gap-1 ${styles.successText}`}>
      <CheckCircle2 className="w-3.5 h-3.5" />
      {t("platform.audit.result.success")}
    </span>
  );

  const chainNode =
    cs === "ok" ? (
      <span className={`inline-flex items-center gap-1 ${styles.successText}`}>
        <CheckCircle2 className="w-3.5 h-3.5" />
        {t("platform.audit.chain.ok")}
      </span>
    ) : cs === "broken" ? (
      <span className={`inline-flex items-center gap-1 font-semibold ${styles.dangerText}`}>
        <XCircle className="w-3.5 h-3.5" />
        {t("platform.audit.chain.broken")}
      </span>
    ) : cs === "unstamped" ? (
      <span className={`inline-flex items-center gap-1 ${styles.muted}`}>
        <XCircle className="w-3.5 h-3.5" />
        {t("platform.audit.chain.unstamped")}
      </span>
    ) : (
      <span className={styles.muted}>—</span>
    );

  return (
    <button
      type="button"
      data-testid={`audit-row-${audit.id}`}
      onClick={() => onOpen(audit)}
      className={`w-full text-left h-full rounded-md border ${styles.appBorder} ${styles.cardBg} px-3 py-2 hover:opacity-95 cursor-pointer transition flex items-center gap-4`}
    >
      <div className={`w-40 shrink-0`}>
        <div className="text-xs font-mono tabular-nums">{audit.timestamp ? new Date(audit.timestamp).toLocaleString() : "—"}</div>
        <div className={`text-[10px] font-mono ${styles.muted}`}>{audit.traceId || audit.requestId || ""}</div>
      </div>
      <div className="w-28 shrink-0 text-xs">{audit.userId ?? "—"}</div>
      <div className={`w-36 shrink-0 text-xs font-mono break-all`}>{audit.action || audit.eventType || "—"}</div>
      <div className={`flex-1 min-w-0 text-xs font-mono break-all ${styles.muted}`}>{audit.resource || audit.resourceType || "—"}</div>
      <div className="w-24 shrink-0">{resultNode}</div>
      <div className={`w-32 shrink-0 text-xs font-mono ${styles.muted}`}>{audit.ipAddress || "—"}</div>
      <div className="w-24 shrink-0">{chainNode}</div>
      <div className={`shrink-0 ${styles.muted} self-center`}><ChevronRight className="w-4 h-4" /></div>
    </button>
  );
}
