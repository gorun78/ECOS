/**
 * TenantManager Tab3 用量仪表盘 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import { AlertTriangle, BarChart3, Building, RefreshCw } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { RANGE_OPTIONS } from "./constants";
import BarChart from "./BarChart";
import type { ChartDataPoint, Tenant } from "./types";

export interface UsageTabProps {
  tenants: Tenant[];
  selectedTenantId: number | null;
  setSelectedTenantId: React.Dispatch<React.SetStateAction<number | null>>;
  usageRange: string;
  setUsageRange: React.Dispatch<React.SetStateAction<string>>;
  usageError: string;
  loadingUsage: boolean;
  usageChartGroups: Record<string, ChartDataPoint[]>;
}

/** ════════════════ Tab 3: 用量仪表盘 (Usage) ════════════════ */
export function UsageTab({
  tenants,
  selectedTenantId,
  setSelectedTenantId,
  usageRange,
  setUsageRange,
  usageError,
  loadingUsage,
  usageChartGroups,
}: UsageTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      {/* Tenant selector + range */}
      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
            <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
              {t("platform.tenant.invoice.tenant")}:
            </span>
            <select
              value={selectedTenantId ?? ""}
              onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
              className={`px-3 py-1.5 rounded text-xs border min-w-[180px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            >
              <option value="">{t("platform.tenant.common.select")}</option>
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.usage.range")}:</span>
            <select
              value={usageRange}
              onChange={(e) => setUsageRange(e.target.value)}
              className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            >
              {RANGE_OPTIONS.map((r) => (
                <option key={r.value} value={r.value}>{t(`platform.tenant.range.${r.value}`)}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {usageError && (
        <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{usageError}</div>
      )}

      {!selectedTenantId ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <BarChart3 className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.invoice.selectHint")}</p>
        </div>
      ) : loadingUsage ? (
        <div className="flex items-center gap-2 p-4">
          <RefreshCw className={`w-4 h-4 animate-spin ${styles.cardTextMuted}`} />
          <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.usage.loading")}</span>
        </div>
      ) : Object.keys(usageChartGroups).length === 0 ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <AlertTriangle className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.usage.empty")}</p>
        </div>
      ) : (
        <div className="space-y-4">
          {Object.entries(usageChartGroups as Record<string, { label: string; value: number; color: string }[]>).map(([quotaType, chartData]) => (
            <div key={quotaType} className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
              <h3 className={`text-xs font-semibold mb-3 ${styles.cardText}`}>
                {quotaType}
              </h3>
              <BarChart data={chartData} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default UsageTab;
