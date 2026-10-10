/**
 * ClsPolicyTab — CLS 列级安全策略列表（可见列/阻止列/模式）。
 * 设计：详细设计-01 B §3.1。列级策略以「可见 / 阻止」两组列 + 优先级/生效
 * 呈现；<L3 只读。
 */
import React from "react";
import { Inbox } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import type { ClsPolicy } from "../../../../services/security";

export default function ClsPolicyTab({ policies, loading }: { policies: ClsPolicy[]; loading: boolean }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  if (loading) {
    return (
      <div className="space-y-2" aria-busy="true">
        {[1, 2, 3].map((i) => (
          <div key={i} className={`h-12 rounded border ${styles.appBorder} animate-pulse`} />
        ))}
      </div>
    );
  }
  if (!policies.length) {
    return (
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-6 flex flex-col items-center gap-2`}>
        <Inbox className={`w-7 h-7 ${styles.muted}`} />
        <div className={`text-sm ${styles.cardText}`}>{t("platform.security.state.empty")}</div>
      </div>
    );
  }
  return (
    <div className="overflow-x-auto" data-testid="cls-table-wrap">
      <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="cls-table">
        <thead>
          <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.policyName")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.table")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.visibleCols")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.blockedCols")}</th>
            <th className="text-center px-2 py-1.5">{t("platform.security.field.mode")}</th>
            <th className="text-right px-2 py-1.5">{t("platform.security.field.priority")}</th>
            <th className="text-center px-2 py-1.5">{t("platform.security.field.enabled")}</th>
          </tr>
        </thead>
        <tbody className={`divide-y ${styles.appBorder}`}>
          {policies.map((p) => (
            <tr key={p.id}>
              <td className="px-2 py-1.5 font-medium">{p.policyName}</td>
              <td className="px-2 py-1.5 font-mono">{p.tableName}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-xs">{(p.visibleCols ?? []).join(", ") || "—"}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-xs">{(p.blockedCols ?? []).join(", ") || "—"}</td>
              <td className="px-2 py-1.5 text-center">
                <span className={p.mode === "deny" ? styles.dangerText : styles.successText}>
                  {t(`platform.security.mode.${p.mode === "deny" ? "deny" : "allow"}`)}
                </span>
              </td>
              <td className="px-2 py-1.5 text-right tabular-nums">{p.priority ?? 0}</td>
              <td className="px-2 py-1.5 text-center">
                <span className={p.enabled === false ? styles.muted : styles.successText}>
                  {t(`platform.security.state.${p.enabled === false ? "off" : "on"}`)}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
