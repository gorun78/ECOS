/**
 * TenantManager Tab4 账单查看 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import { Building, Receipt, RefreshCw } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { formatNumber } from "./helpers";
import type { RawInvoice, Tenant } from "./types";

export interface InvoiceTabProps {
  tenants: Tenant[];
  selectedTenantId: number | null;
  setSelectedTenantId: React.Dispatch<React.SetStateAction<number | null>>;
  invoiceMonth: string;
  setInvoiceMonth: React.Dispatch<React.SetStateAction<string>>;
  monthOptions: string[];
  invoiceError: string;
  loadingInvoice: boolean;
  invoice: RawInvoice | null;
}

/** ════════════════ Tab 4: 账单查看 (Invoice) ════════════════ */
export function InvoiceTab({
  tenants,
  selectedTenantId,
  setSelectedTenantId,
  invoiceMonth,
  setInvoiceMonth,
  monthOptions,
  invoiceError,
  loadingInvoice,
  invoice,
}: InvoiceTabProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      {/* Tenant selector + month */}
      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-2">
            <Building className={`w-4 h-4 ${styles.cardTextMuted}`} />
            <span className={`text-xs font-medium ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户" : "Tenant"}:
            </span>
            <select
              value={selectedTenantId ?? ""}
              onChange={(e) => setSelectedTenantId(e.target.value ? Number(e.target.value) : null)}
              className={`px-3 py-1.5 rounded text-xs border min-w-[180px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            >
              <option value="">{locale === "zh" ? "— 请选择 —" : "— Select —"}</option>
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>{t.tenantName} (#{t.id})</option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "月份" : "Month"}:</span>
            <select
              value={invoiceMonth}
              onChange={(e) => setInvoiceMonth(e.target.value)}
              className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            >
              {monthOptions.map((m) => (<option key={m} value={m}>{m}</option>))}
            </select>
          </div>
        </div>
      </div>

      {invoiceError && (
        <div className="p-3 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-sm">{invoiceError}</div>
      )}

      {!selectedTenantId ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <Receipt className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "请先选择租户" : "Select a tenant first"}</p>
        </div>
      ) : loadingInvoice ? (
        <div className="flex items-center gap-2 p-4">
          <RefreshCw className={`w-4 h-4 animate-spin ${styles.cardTextMuted}`} />
          <span className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "加载账单…" : "Loading invoice…"}</span>
        </div>
      ) : !invoice ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <Receipt className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{locale === "zh" ? "该月份暂无账单" : "No invoice for this month"}</p>
        </div>
      ) : (
        <>
          <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder} overflow-x-auto md:overflow-visible`}>
            <table className="w-full text-xs">
              <thead>
                <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                  <th className="text-left px-4 py-2.5 font-medium">{locale === "zh" ? "项目" : "Item"}</th>
                  <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "用量" : "Usage"}</th>
                  <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "单价" : "Unit Price"}</th>
                  <th className="text-right px-4 py-2.5 font-medium">{locale === "zh" ? "费用" : "Cost"}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {invoice.line_items && invoice.line_items.length > 0 ? (
                  invoice.line_items.map((item, i) => (
                    <tr key={i} className="hover:bg-white/5">
                      <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{item.quota_type}</td>
                      <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(item.usage)}</td>
                      <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{item.unit_price?.toFixed(4) ?? "—"}</td>
                      <td className={`px-4 py-2.5 text-right font-mono font-semibold ${styles.cardText}`}>{item.cost_display}</td>
                    </tr>
                  ))
                ) : (
                  <tr><td colSpan={4} className="px-4 py-8 text-center opacity-60">{locale === "zh" ? "暂无账单明细" : "No line items"}</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className={`rounded-lg border p-4 flex items-center justify-between ${styles.cardBg} ${styles.cardBorder}`}>
            <span className={`text-sm font-semibold ${styles.cardText}`}>{locale === "zh" ? "合计费用" : "Total Cost"}</span>
            <span className={`text-lg font-bold ${styles.accentText}`}>{invoice.total_cost_display}</span>
          </div>
        </>
      )}
    </div>
  );
}

export default InvoiceTab;
