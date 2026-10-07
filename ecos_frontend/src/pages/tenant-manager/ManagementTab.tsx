/**
 * TenantManager Tab1 租户管理 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import {
  Activity,
  Building,
  Calendar,
  Edit3,
  HardDrive,
  RefreshCw,
  Search,
  Shield,
  Trash2,
  Users,
} from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { STATUS_COLORS, STATUS_OPTIONS } from "./constants";
import { formatDate, formatNumber } from "./helpers";
import type { Tenant } from "./types";

export interface ManagementTabProps {
  isMobile: boolean;
  tenants: Tenant[];
  loadingTenants: boolean;
  tenantPage: number;
  tenantTotal: number;
  tenantSearch: string;
  statusFilter: string;
  setTenantSearch: React.Dispatch<React.SetStateAction<string>>;
  setStatusFilter: React.Dispatch<React.SetStateAction<string>>;
  setTenantPage: React.Dispatch<React.SetStateAction<number>>;
  loadTenants: (keyword?: string, page?: number, status?: string) => Promise<void>;
  setEditTenant: React.Dispatch<React.SetStateAction<Tenant | null>>;
  setFormMode: React.Dispatch<React.SetStateAction<"create" | "edit" | null>>;
  setDeleteTarget: React.Dispatch<React.SetStateAction<{ id: number; name: string } | null>>;
}

/** ════════════════ Tab 1: 租户管理 (Management) ════════════════ */
export function ManagementTab({
  isMobile,
  tenants,
  loadingTenants,
  tenantPage,
  tenantTotal,
  tenantSearch,
  statusFilter,
  setTenantSearch,
  setStatusFilter,
  setTenantPage,
  loadTenants,
  setEditTenant,
  setFormMode,
  setDeleteTarget,
}: ManagementTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-4">
      {/* Search + Filter bar */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="flex items-center gap-1.5 flex-1 min-w-[200px]">
          <Search className="w-3.5 h-3.5 opacity-50" />
          <input
            value={tenantSearch}
            onChange={(e) => setTenantSearch(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") loadTenants(tenantSearch, 1, statusFilter); }}
            placeholder={t("platform.tenant.mgmt.searchPlaceholder")}
            className={`flex-1 px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); loadTenants(tenantSearch, 1, e.target.value); }}
          className={`px-3 py-1.5 rounded text-xs border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
        >
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{t(`platform.tenant.status.${o.value || "all"}`)}</option>
          ))}
        </select>
      </div>

      {/* Table / 移动端卡片态 */}
      {loadingTenants ? (
        <div className="flex items-center gap-2 p-4">
          <RefreshCw className={`w-4 h-4 animate-spin ${styles.cardTextMuted}`} />
          <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.mgmt.loading")}</span>
        </div>
      ) : tenants.length === 0 ? (
        <div className={`rounded-lg border p-8 text-center ${styles.cardBg} ${styles.cardBorder}`}>
          <Building className={`w-6 h-6 mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.tenant.mgmt.empty")}</p>
        </div>
      ) : isMobile ? (
        <div className="space-y-2">
          {tenants.map((tn) => (
            <div key={tn.id} className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
              <div className="flex items-center justify-between px-3 py-2.5">
                <div className="flex items-center gap-2 min-w-0">
                  <Shield className="w-3.5 h-3.5 shrink-0 opacity-50" />
                  <span className={`text-xs font-semibold truncate ${styles.cardText}`} title={tn.tenantName}>{tn.tenantName}</span>
                </div>
                <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-medium border shrink-0 ${STATUS_COLORS[tn.status] ?? "bg-slate-500/10 text-slate-400 border-slate-500/30"}`}>
                  {tn.status}
                </span>
              </div>
              <div className="px-3 pb-2">
                <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{tn.tenantCode} · ID {tn.id}</span>
              </div>
              <div className="px-3 py-2 border-t grid grid-cols-2 gap-2" style={{ borderColor: styles.cardBorder }}>
                <div>
                  <div className="text-[10px] opacity-40 uppercase tracking-wider">{t("platform.tenant.mgmt.maxUsersCol")}</div>
                  <div className={`text-xs font-mono ${styles.cardText}`}>{formatNumber(tn.maxUsers)}</div>
                </div>
                <div>
                  <div className="text-[10px] opacity-40 uppercase tracking-wider">{t("platform.tenant.mgmt.storageCol")}</div>
                  <div className={`text-xs font-mono ${styles.cardText}`}>{formatNumber(tn.maxStorageMb)}</div>
                </div>
                <div>
                  <div className="text-[10px] opacity-40 uppercase tracking-wider">{t("platform.tenant.mgmt.apiCol")}</div>
                  <div className={`text-xs font-mono ${styles.cardText}`}>{formatNumber(tn.maxApiPerDay)}</div>
                </div>
                <div>
                  <div className="text-[10px] opacity-40 uppercase tracking-wider">{t("platform.tenant.mgmt.isolationCol")}</div>
                  <div className={`text-xs font-mono ${styles.cardText}`}>{tn.isolationMode}</div>
                </div>
              </div>
              <div className="px-3 py-2 border-t flex items-center justify-between gap-2 flex-wrap" style={{ borderColor: styles.cardBorder }}>
                <span className={`text-[11px] ${styles.cardTextMuted}`}>{formatDate(tn.createdAt)}</span>
                <div className="flex items-center gap-1.5">
                  <button onClick={() => { setEditTenant(tn); setFormMode("edit"); }}
                    className="px-2.5 py-1 rounded text-[11px] font-medium text-white shrink-0 bg-indigo-500 hover:bg-indigo-600">
                    {t("platform.tenant.mgmt.edit")}
                  </button>
                  <button onClick={() => setDeleteTarget({ id: tn.id, name: tn.tenantName })}
                    className="px-2.5 py-1 rounded text-[11px] font-medium text-white shrink-0 bg-red-500 hover:bg-red-600">
                    {t("platform.tenant.mgmt.dl")}
                  </button>
                </div>
              </div>
            </div>
          ))}
          {tenantTotal > 20 && (
            <div className="flex items-center justify-center gap-2 py-2">
              <button disabled={tenantPage <= 1}
                onClick={() => { const p = tenantPage - 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                className={`px-3 py-1 rounded text-[11px] border ${styles.cardBorder} disabled:opacity-30`}>
                {t("platform.tenant.mgmt.prev")}
              </button>
              <span className={`text-[11px] ${styles.cardTextMuted}`}>{tenantPage} / {Math.max(1, Math.ceil(tenantTotal / 20))}</span>
              <button onClick={() => { const p = tenantPage + 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                className={`px-3 py-1 rounded text-[11px] border ${styles.cardBorder}`}>
                {t("platform.tenant.mgmt.next")}
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="overflow-x-auto md:overflow-visible">
            <table className="w-full text-xs">
              <thead>
                <tr className={`border-b ${styles.cardBorder} opacity-60`}>
                  <th className="text-left px-4 py-2.5 font-medium">ID</th>
                  <th className="text-left px-4 py-2.5 font-medium">{t("platform.tenant.mgmt.nameCol")}</th>
                  <th className="text-left px-4 py-2.5 font-medium">{t("platform.tenant.mgmt.codeCol")}</th>
                  <th className="text-left px-4 py-2.5 font-medium">{t("platform.tenant.mgmt.statusCol")}</th>
                  <th className="text-right px-4 py-2.5 font-medium"><Users className="w-3 h-3 inline mr-1" />{t("platform.tenant.mgmt.maxUsersCol")}</th>
                  <th className="text-right px-4 py-2.5 font-medium"><HardDrive className="w-3 h-3 inline mr-1" />MB</th>
                  <th className="text-right px-4 py-2.5 font-medium"><Activity className="w-3 h-3 inline mr-1" />API/天</th>
                  <th className="text-left px-4 py-2.5 font-medium"><Shield className="w-3 h-3 inline mr-1" />{t("platform.tenant.mgmt.isolationCol")}</th>
                  <th className="text-left px-4 py-2.5 font-medium"><Calendar className="w-3 h-3 inline mr-1" />{t("platform.tenant.mgmt.createdCol")}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t("platform.tenant.mgmt.actionsCol")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/5">
                {tenants.map((tn) => (
                  <tr key={tn.id} className="hover:bg-white/5">
                    <td className={`px-4 py-2.5 font-mono ${styles.cardTextMuted}`}>{tn.id}</td>
                    <td className={`px-4 py-2.5 font-medium ${styles.cardText}`}>{tn.tenantName}</td>
                    <td className={`px-4 py-2.5 font-mono text-[11px] ${styles.cardTextMuted}`}>{tn.tenantCode}</td>
                    <td className="px-4 py-2.5">
                      <span className={`inline-block px-2 py-0.5 rounded text-[10px] font-medium border ${STATUS_COLORS[tn.status] ?? "bg-slate-500/10 text-slate-400 border-slate-500/30"}`}>
                        {tn.status}
                      </span>
                    </td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxUsers)}</td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxStorageMb)}</td>
                    <td className={`px-4 py-2.5 text-right font-mono ${styles.cardText}`}>{formatNumber(tn.maxApiPerDay)}</td>
                    <td className={`px-4 py-2.5 font-mono text-[10px] ${styles.cardTextMuted}`}>{tn.isolationMode}</td>
                    <td className={`px-4 py-2.5 text-[11px] ${styles.cardTextMuted}`}>{formatDate(tn.createdAt)}</td>
                    <td className="px-4 py-2.5 text-center">
                      <div className="flex items-center justify-center gap-1">
                        <button
                          onClick={() => { setEditTenant(tn); setFormMode("edit"); }}
                          className="p-1 rounded hover:bg-white/10" title={t("platform.tenant.mgmt.edit")}
                        >
                          <Edit3 className="w-3.5 h-3.5 text-blue-400" />
                        </button>
                        <button
                          onClick={() => setDeleteTarget({ id: tn.id, name: tn.tenantName })}
                          className="p-1 rounded hover:bg-white/10" title={t("platform.tenant.mgmt.dl")}
                        >
                          <Trash2 className="w-3.5 h-3.5 text-red-400" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {/* Pagination */}
          {tenantTotal > 20 && (
            <div className={`flex items-center justify-between px-4 py-2 border-t ${styles.cardBorder}`}>
              <span className={`text-[11px] ${styles.cardTextMuted}`}>
                {t("platform.tenant.mgmt.total", { n: tenantTotal })}
              </span>
              <div className="flex items-center gap-1">
                <button
                  disabled={tenantPage <= 1}
                  onClick={() => { const p = tenantPage - 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                  className={`px-2 py-1 rounded text-[11px] border ${styles.cardBorder} disabled:opacity-30`}
                >
                  {t("platform.tenant.mgmt.prev")}
                </button>
                <span className={`text-[11px] px-2 ${styles.cardTextMuted}`}>{tenantPage}</span>
                <button
                  onClick={() => { const p = tenantPage + 1; setTenantPage(p); loadTenants(tenantSearch, p, statusFilter); }}
                  className={`px-2 py-1 rounded text-[11px] border ${styles.cardBorder}`}
                >
                  {t("platform.tenant.mgmt.next")}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default ManagementTab;
