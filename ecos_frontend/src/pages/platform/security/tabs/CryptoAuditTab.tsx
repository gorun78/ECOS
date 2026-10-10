/**
 * CryptoAuditTab — 加解密审计 + 链完整性验证。
 * 展示加密审计台账（每行含 prevHash/currentHash，verified 徽标）+
 * 「验证链完整性」按钮 → GET /api/v1/security/audit/crypto/verify。
 * 链验证结果写 aria-live（设计 B §3.2 #7/#8）。
 */
import React, { useCallback, useEffect, useState } from "react";
import { RefreshCw, ShieldCheck, ShieldOff, Inbox, KeyRound, Link as LinkIcon } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import { useToast } from "../../../../components/common/Toast";
import {
  listCryptoAuditLogs,
  verifyCryptoAudit,
  type CryptoAuditLog,
  type CryptoVerifyResult,
} from "../../../../services/security";

export default function CryptoAuditTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();

  const [logs, setLogs] = useState<CryptoAuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [verify, setVerify] = useState<CryptoVerifyResult | null>(null);
  const [verifying, setVerifying] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listCryptoAuditLogs();
      setLogs(res.items);
    } catch (e) {
      setError(String((e as Error)?.message ?? ""));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const doVerify = useCallback(async () => {
    setVerifying(true);
    try {
      setVerify(await verifyCryptoAudit());
    } catch (e) {
      setVerify({ valid: false });
      showToast("error", String((e as Error)?.message ?? ""));
    } finally {
      setVerifying(false);
    }
  }, [showToast]);

  const verifyValid = verify?.valid === true;
  const hasBroken = (verify?.brokenAt ?? []).length > 0;

  return (
    <div className="space-y-4" data-testid="crypto-tab">
      {/* 链验证区 */}
      <div className={`rounded-md border ${styles.appBorder} ${styles.cardBg} p-3`}>
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <LinkIcon className={`w-4 h-4 ${styles.accentText}`} />
            <span className={`text-sm font-semibold ${styles.cardText}`}>
              {t("platform.security.crypto.chainTitle")}
            </span>
          </div>
          <button
            type="button"
            onClick={() => void doVerify()}
            disabled={verifying}
            data-testid="crypto-verify-btn"
            aria-busy={verifying}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.accentBorder} ${styles.accentText} hover:opacity-80 disabled:opacity-50`}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${verifying ? "animate-spin" : ""}`} />
            {t("platform.security.crypto.verify")}
          </button>
        </div>
        <div aria-live="polite" aria-atomic="true" data-testid="crypto-verify-live">
          {verify && (
            <div className={`mt-2 flex items-center gap-2 text-xs ${verifyValid ? styles.successText : styles.dangerText}`}>
              {verifyValid ? <ShieldCheck className="w-4 h-4" /> : <ShieldOff className="w-4 h-4" />}
              <span>{verifyValid ? t("platform.security.crypto.valid") : t("platform.security.crypto.broken")}</span>
              {typeof verify.totalChecked === "number" && (
                <span className={styles.muted}>{t("platform.security.crypto.checked")} {verify.totalChecked}</span>
              )}
              {hasBroken && (
                <span className={`font-mono ${styles.dangerText}`}>
                  {t("platform.security.crypto.brokenAt")}: {verify!.brokenAt!.join(", ")}
                </span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* 台账列表 */}
      {loading ? (
        <div className="space-y-2" aria-busy="true">
          {[1, 2, 3].map((i) => (
            <div key={i} className={`h-12 rounded border ${styles.appBorder} animate-pulse`} />
          ))}
        </div>
      ) : error && !logs.length ? (
        <p role="alert" className={`text-xs ${styles.dangerText}`}>{error}</p>
      ) : logs.length === 0 ? (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-6 flex flex-col items-center gap-2`}>
          <Inbox className={`w-7 h-7 ${styles.muted}`} />
          <div className={`text-sm ${styles.cardText}`}>{t("platform.security.state.empty")}</div>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="crypto-table">
            <thead>
              <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
                <th className="text-left px-2 py-1.5">{t("platform.security.crypto.time")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.crypto.event")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.crypto.resource")}</th>
                <th className="text-left px-2 py-1.5">{t("platform.security.crypto.operator")}</th>
                <th className="text-center px-2 py-1.5">{t("platform.security.crypto.verified")}</th>
              </tr>
            </thead>
            <tbody className={`divide-y ${styles.appBorder}`}>
              {logs.map((l) => (
                <tr key={l.id}>
                  <td className="px-2 py-1.5 font-mono tabular-nums">{l.timestamp ?? "—"}</td>
                  <td className="px-2 py-1.5 font-mono">{l.eventType}</td>
                  <td className="px-2 py-1.5 font-mono break-all max-w-[12rem]">{l.resource || l.action || "—"}</td>
                  <td className="px-2 py-1.5 font-mono">{l.operatorId || "—"}</td>
                  <td className="px-2 py-1.5 text-center">
                    {l.verified === false ? (
                      <span className={`inline-flex items-center gap-1 ${styles.dangerText}`}><ShieldOff className="w-3.5 h-3.5" /><KeyRound className="w-3 h-3" />{t("platform.security.crypto.verified.no")}</span>
                    ) : (
                      <span className={`inline-flex items-center gap-1 ${styles.successText}`}><ShieldCheck className="w-3.5 h-3.5" />{t("platform.security.crypto.verified.yes")}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
