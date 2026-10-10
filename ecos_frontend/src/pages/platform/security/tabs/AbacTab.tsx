/**
 * AbacTab — ABAC 属性策略列表（subject/resource/action/environment 四条件 + 效果）。
 * 只读展示：ABAC 策略运行时经 OPA 裁决，结构复杂，控制面只供审阅。
 */
import React from "react";
import { Inbox, ShieldCheck, ShieldOff } from "lucide-react";
import { useLanguage } from "../../../../components/LanguageContext";
import { useTheme } from "../../../../components/ThemeContext";
import type { AbacPolicy } from "../../../../services/security";

export default function AbacTab({ policies, loading }: { policies: AbacPolicy[]; loading: boolean }) {
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
    <div className="overflow-x-auto" data-testid="abac-table-wrap">
      <table className={`w-full text-xs border-collapse ${styles.cardText}`} data-testid="abac-table">
        <thead>
          <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
            <th className="text-left px-2 py-1.5">{t("platform.security.field.policyName")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.abac.subject")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.abac.resource")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.abac.action")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.abac.env")}</th>
            <th className="text-left px-2 py-1.5">{t("platform.security.abac.scope")}</th>
            <th className="text-center px-2 py-1.5">{t("platform.security.abac.effect")}</th>
            <th className="text-right px-2 py-1.5">{t("platform.security.field.priority")}</th>
          </tr>
        </thead>
        <tbody className={`divide-y ${styles.appBorder}`}>
          {policies.map((p) => (
            <tr key={p.id}>
              <td className="px-2 py-1.5 font-medium">{p.policyName}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-[10rem]">{p.subjectCondition || "—"}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-[10rem]">{p.resourceCondition || "—"}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-[8rem]">{p.actionCondition || "—"}</td>
              <td className="px-2 py-1.5 font-mono break-all max-w-[10rem]">{p.environmentCondition || "—"}</td>
              <td className="px-2 py-1.5">{p.scopeType ?? "—"}</td>
              <td className="px-2 py-1.5 text-center">
                {String(p.effect ?? "").toUpperCase() === "ALLOW" ? (
                  <span className={`inline-flex items-center gap-1 ${styles.successText}`}><ShieldCheck className="w-3.5 h-3.5" />{t("platform.security.abac.effect.allow")}</span>
                ) : (
                  <span className={`inline-flex items-center gap-1 ${styles.dangerText}`}><ShieldOff className="w-3.5 h-3.5" />{t("platform.security.abac.effect.deny")}</span>
                )}
              </td>
              <td className="px-2 py-1.5 text-right tabular-nums">{p.priority ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
