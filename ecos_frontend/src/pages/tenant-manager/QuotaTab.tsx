/**
 * TenantManager Tab2 配额管理 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import { AlertTriangle, Building, Edit3, Gauge, RefreshCw } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { formatNumber } from "./helpers";
import type { QuotaItem, Tenant } from "./types";

export interface QuotaTabProps {
  tenants: Tenant[];
  selectedTenantId: number | null;
  setSelectedTenantId: React.Dispatch<React.SetStateAction<number | null>>;
  quotas: QuotaItem[];
  loadingQuotas: boolean;
  quotaError: string;
  setEditQuota: React.Dispatch<React.SetStateAction<QuotaItem | null>>;
}

/** ════════════════ Tab 2: 配额管理 (Quota) ════════════════ */
export function QuotaTab({
  tenants,
  selectedTenantId,
  setSelectedTenantId,
  quotas,
  loadingQuotas,
  quotaError,
  setEditQuota,
}: QuotaTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      {/* Tenant selector */}
      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-3">
          <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
          <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
            {t("platform.tenant.quota.selectTenant")}:
          </span>
          <select
            value={selectedTenantId ?? ""}
            onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
            className={`px-3 py-1.5 rounded text-xs border min-w-[220px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          >
            <option value="">{t("platform.tenant.common.select")}</option>
            {tenants.map((t) => (
              <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
            ))}
          </select>
        </div>
      </div>

      {quotaError && (
        <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{quotaError}</div>
      )}

      {!selectedTenantId ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <Gauge className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.quota.selectHint")}</p>
        </div>
      ) : loadingQuotas ? (
        <div className="flex items-center gap-2 p-4">
          <RefreshCw className={`w-4 h-4 animate-spin ${styles.cardTextMuted}`} />
          <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.quota.loading")}</span>
        </div>
      ) : quotas.length === 0 ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <AlertTriangle className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.quota.empty")}</p>
        </div>
      ) : (
        <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder} overflow-x-auto md:overflow-visible`}>
          <table className="w-full text-xs">
            <thead>
              <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                <th className="text-left px-4 py-2.5 font-medium">{t("platform.tenant.quota.typeCol")}</th>
                <th className="text-right px-4 py-2.5 font-medium">{t("platform.tenant.quota.dailyLimitCol")}</th>
                <th className="text-right px-4 py-2.5 font-medium">{t("platform.tenant.quota.monthlyLimitCol")}</th>
                <th className="text-right px-4 py-2.5 font-medium">{t("platform.tenant.quota.usedCol")}</th>
                <th className="text-center px-4 py-2.5 font-medium">{t("platform.tenant.quota.usagePctCol")}</th>
                <th className="text-center px-4 py-2.5 font-medium">{t("platform.tenant.quota.actionCol")}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-white/5">
              {quotas.map((q) => {
                const pct = q.dailyLimit > 0 ? Math.min((q.usedCount / q.dailyLimit) * 100, 100) : 0;
                const barColor = pct > 80 ? "bg-red-500" : pct > 50 ? "bg-yellow-500" : "bg-emerald-500";
                return (
                  <tr key={q.id} className="hover:bg-white/5">
                    <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{q.quotaType}</td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.dailyLimit)}</td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.monthlyLimit)}</td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(q.usedCount)}</td>
                    <td className="px-4 py-2.5">
                      <div className="flex items-center gap-2">
                        <div className="flex-1 h-1.5 rounded-full bg-white/10 overflow-hidden">
                          <div className={`h-full rounded-full ${barColor}`} style={{ width: `${pct}%` }} />
                        </div>
                        <span className="text-[10px] font-mono w-9 text-right opacity-60">{pct.toFixed(0)}%</span>
                      </div>
                    </td>
                    <td className="px-4 py-2.5 text-center">
                      <button
                        onClick={() => setEditQuota(q)}
                        className={`inline-flex items-center gap-1 px-2 py-1 rounded text-[11px] border transition-colors ${styles.cardBorder} hover:bg-white/5`}
                      >
                        <Edit3 className="w-3 h-3" />
                        {t("platform.tenant.mgmt.edit")}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default QuotaTab;
