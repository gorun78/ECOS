/**
 * OperationalApps — 运营应用门户：CRM / 养护 / 财务三大子门户
 * Connected via api.ts → fetchEntityInstances / executeOntologyAction
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { 
  Briefcase, Settings, Cpu, Database, Activity,
  HelpCircle, AlertTriangle, Layers, TrendingUp,
  UserCheck, Lock, Wrench, Play,
  CheckCircle2, ArrowRight, ShieldAlert, Coins
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { fetchEntityInstances, executeOntologyAction } from "../api";

// ── Types ──
interface CustomerData {
  id: string; name: string; isActive: boolean;
  revenue: number; region: string; churn_risk: number;
}

interface MachineData {
  machine_id: string; temperature: number; temp_status: string;
  has_fault: boolean;
}

// ── Main ──────────────────────────────────────
export default function OperationalApps() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [activePortal, setActivePortal] = useState<"crm" | "maint" | "fin">("crm");
  const [customers, setCustomers] = useState<CustomerData[]>([]);
  const [macStatus, setMacStatus] = useState<MachineData[]>([]);
  const [loading, setLoading] = useState(true);

  // ── Load data via api.ts ────────────────
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [cust, mach] = await Promise.all([
        fetchEntityInstances("Customer"),
        fetchEntityInstances("Facility"),
      ]);
      setCustomers(cust);
      setMacStatus(mach);
    } catch {
      console.warn("OperationalApps: backend unavailable, showing empty state");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  // ── State ────────────────────────────────
  const [selectedCustId, setSelectedCustId] = useState("");
  const [creditIncrement, setCreditIncrement] = useState("5000000");
  const [maintPriority, setMaintPriority] = useState("High");
  const [dispatchNote, setDispatchNote] = useState("");
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [errorToast, setErrorToast] = useState<string | null>(null);

  // ── Action execution via api.ts ──────────
  const executeAction = async (actionId: string, payload: any) => {
    setActionSuccess(null);
    setErrorToast(null);

    try {
      const entityType = activePortal === "crm" ? "Customer" : activePortal === "maint" ? "Facility" : "Order";
      const root = await executeOntologyAction({
        actionId,
        entityType,
        instanceId: payload.instanceId,
        operatorName: "Decision-Commander (Operator)",
        fields: payload.fields,
      });

      if (root.success) {
        if (root.auditLog && root.auditLog.status === "pending_approval") {
          setActionSuccess(
            isZh
              ? `高危指令 [${actionId.toUpperCase()}] 已拦截并注册到安全沙箱审批流。凭证号码: ${root.voucherId}`
              : `High-risk directive [${actionId.toUpperCase()}] intercepted and registered to safety sandbox approval flow. Voucher: ${root.voucherId}`
          );
        } else {
          setActionSuccess(
            isZh
              ? `指令 [${actionId.toUpperCase()}] 已成功编译且写入不可篡改区块！区块哈希: ${root.voucherId}`
              : `Directive [${actionId.toUpperCase()}] compiled and committed to immutable block! Block: ${root.voucherId}`
          );
        }

        if (actionId === "suspend_account" && payload.instanceId) {
          setCustomers((prev) =>
            prev.map((c) => (c.id === payload.instanceId ? { ...c, isActive: false } : c))
          );
        }
      } else {
        setErrorToast((isZh ? "执行操作失败: " : "Action execute fail: ") + (root.message || "Internal constraints"));
      }
    } catch (err: any) {
      setErrorToast((isZh ? "网络传输错误: " : "Network transmission error: ") + err.message);
    }
  };

  const handleFreezeAccount = (custId: string) => {
    executeAction("suspend_account", {
      instanceId: custId,
      fields: { reason: "High risk profile flagged by cognitive ruleset" },
    });
  };

  const handleAdjustCredit = (custId: string) => {
    executeAction("adjust_credit", {
      instanceId: custId,
      fields: { increment_amount: creditIncrement },
    });
  };

  const handleDispatchRepair = (macId: string) => {
    executeAction("trigger_maintenance", {
      instanceId: macId,
      fields: { priority: maintPriority, details: dispatchNote || "Outlier hot sensor alert" },
    });
  };

  const currentCust = customers.find((c) => c.id === selectedCustId) || customers[0];

  // ── Render ────────────────────────────────
  return (
    <div className="flex-grow overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">
        
        {/* Title and Intro */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-xl font-bold tracking-tight">{t("ops.title")}</h1>
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>
              {t("ops.desc")}
            </p>
          </div>
          
          <div className="flex items-center gap-1.5 p-1 bg-black/5 dark:bg-black/30 rounded-lg shrink-0 border border-slate-200 dark:border-slate-800">
            {(["crm", "maint", "fin"] as const).map((portal) => (
              <button
                key={portal}
                onClick={() => setActivePortal(portal)}
                className={`px-3 py-1 text-xs font-semibold rounded-md transition cursor-pointer ${
                  activePortal === portal
                    ? "bg-indigo-600 text-white shadow-xs"
                    : "opacity-75 hover:opacity-100"
                }`}
              >
                {t(`ops.tab.${portal}`)}
              </button>
            ))}
          </div>
        </div>

        {/* Action toasts */}
        {actionSuccess && (
          <div className="p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-lg flex items-start gap-3 animate-fade-in text-emerald-600 dark:text-emerald-400">
            <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="text-xs font-mono font-medium">
              <span className="font-bold">EXECUTION_SUCCESS:</span> {actionSuccess}
            </div>
          </div>
        )}

        {errorToast && (
          <div className="p-4 bg-rose-500/10 border border-rose-500/20 rounded-lg flex items-start gap-3 animate-fade-in text-rose-600 dark:text-rose-400">
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="text-xs font-mono font-medium">
              <span className="font-bold">SECURITY_BLOCK:</span> {errorToast}
            </div>
          </div>
        )}

        {/* Operational App View Grid */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          
          {/* Main Workspace Frame */}
          <div className="lg:col-span-8 space-y-6">
            
            {/* ═══ CRM Portal ═══ */}
            {activePortal === "crm" && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <UserCheck className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-bold text-sm">{t("ops.crm.header")}</h3>
                </div>

                {loading ? (
                  <div className="text-center py-12">
                    <div className="animate-spin w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full mx-auto mb-2"></div>
                    <p className="text-xs text-slate-400 font-mono">{t("ops.loading") || "Loading..."}</p>
                  </div>
                ) : customers.length === 0 ? (
                  <div className="text-center py-12 text-slate-400">
                    <Database className="w-8 h-8 mx-auto mb-2 text-slate-500" />
                    <p className="text-xs font-mono">{t("ops.crm.empty") || "No customers found."}</p>
                  </div>
                ) : (
                <div className="space-y-4">
                  <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                    <div className="flex-1">
                      <label className="text-[10px] uppercase font-mono text-slate-400 block mb-1">{t("ops.crm.select")}</label>
                      <select 
                        value={selectedCustId}
                        onChange={(e) => setSelectedCustId(e.target.value)}
                        className={`w-full text-xs font-mono p-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                      >
                        {customers.map((c) => (
                          <option key={c.id} value={c.id} className="text-slate-800">{c.name} ({c.id})</option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className="text-[10px] uppercase font-mono text-slate-400 block mb-1">{t("ops.crm.status")}</label>
                      <span className={`inline-flex px-3 py-1.5 rounded-full text-xs font-extrabold ${
                        currentCust?.isActive 
                          ? "bg-emerald-500/10 text-emerald-500" 
                          : "bg-rose-500/10 text-rose-500"
                      }`}>
                        {currentCust?.isActive ? t("ops.crm.status.active") : t("ops.crm.status.suspended")}
                      </span>
                    </div>
                  </div>

                  {currentCust && (
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 py-4 border-y border-dashed border-slate-200 dark:border-slate-800">
                      <div className="p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border border-slate-200 dark:border-slate-800">
                        <div className="text-[10px] font-mono text-slate-400">{t("ops.crm.revenue")}</div>
                        <div className="text-base font-bold mt-1">${currentCust.revenue?.toLocaleString?.() || "—"}</div>
                      </div>
                      <div className="p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border border-slate-200 dark:border-slate-800">
                        <div className="text-[10px] font-mono text-slate-400">{t("ops.crm.region")}</div>
                        <div className="text-base font-bold mt-1">{currentCust.region === "APAC" && isZh ? "亚太区" : currentCust.region || "—"} ({t("ops.crm.region.sea")})</div>
                      </div>
                      <div className="p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border border-slate-200 dark:border-slate-800">
                        <div className="text-[10px] font-mono text-slate-400">{t("ops.crm.churn")}</div>
                        <div className={`text-base font-bold mt-1 ${(currentCust.churn_risk || 0) > 0.5 ? "text-red-500" : "text-emerald-500"}`}>
                          {((currentCust.churn_risk || 0) * 100).toFixed(0)}%
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Operational execution controllers */}
                  {currentCust && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                      <div className="p-4 rounded-lg border border-indigo-500/10 bg-indigo-500/[0.01] space-y-3">
                        <div className="text-xs font-bold flex items-center gap-1.5"><Coins className="w-4 h-4 text-indigo-500" /> {t("ops.crm.control.credit")}</div>
                        <div className="flex gap-2">
                          <input 
                            type="number" 
                            value={creditIncrement} 
                            onChange={(e) => setCreditIncrement(e.target.value)}
                            className={`flex-1 text-xs font-mono p-1 px-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                          />
                          <button 
                            onClick={() => handleAdjustCredit(currentCust.id)}
                            className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded text-xs font-bold leading-none cursor-pointer"
                          >
                            {t("ops.crm.control.credit.btn")}
                          </button>
                        </div>
                        <span className="text-[9.5px] text-slate-400 block leading-normal">{t("ops.crm.control.credit.hint")}</span>
                      </div>

                      <div className="p-4 rounded-lg border border-red-500/10 bg-red-500/[0.01] space-y-3">
                        <div className="text-xs font-bold text-red-500 flex items-center gap-1.5"><Lock className="w-4 h-4 text-red-500" /> {t("ops.crm.control.freeze")}</div>
                        <button 
                          onClick={() => handleFreezeAccount(currentCust.id)}
                          disabled={!currentCust.isActive}
                          className="w-full py-2 bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white text-xs font-bold rounded cursor-pointer"
                        >
                          {t("ops.crm.control.freeze.btn")}
                        </button>
                        <span className="text-[9.5px] text-slate-400 block leading-normal">{t("ops.crm.control.freeze.hint")}</span>
                      </div>
                    </div>
                  )}
                </div>
                )}
              </div>
            )}

            {/* ═══ Maintenance Portal ═══ */}
            {activePortal === "maint" && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <Wrench className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-bold text-sm">{t("ops.maint.header")}</h3>
                </div>

                {loading ? (
                  <div className="text-center py-12">
                    <div className="animate-spin w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full mx-auto mb-2"></div>
                    <p className="text-xs text-slate-400 font-mono">{t("ops.loading") || "Loading..."}</p>
                  </div>
                ) : macStatus.length === 0 ? (
                  <div className="text-center py-12 text-slate-400">
                    <Database className="w-8 h-8 mx-auto mb-2 text-slate-500" />
                    <p className="text-xs font-mono">{t("ops.maint.empty") || "No machines found."}</p>
                  </div>
                ) : (
                <div className="space-y-4">
                  <div className="overflow-x-auto rounded border border-slate-200 dark:border-slate-800 text-xs">
                    <table className="w-full border-collapse">
                      <thead className="bg-slate-800 text-slate-200 font-mono text-[9px] uppercase tracking-wider">
                        <tr>
                          <th className="p-2 text-left">{t("ops.maint.col.id")}</th>
                          <th className="p-2 text-left">{t("ops.maint.col.temp")}</th>
                          <th className="p-2 text-left">{t("ops.maint.col.status")}</th>
                          <th className="p-2 text-right">{t("ops.maint.col.action")}</th>
                        </tr>
                      </thead>
                      <tbody className="font-mono">
                        {macStatus.map((m) => (
                          <tr key={m.machine_id} className="border-b hover:bg-indigo-500/5 odd:bg-black/[0.01] dark:even:bg-slate-900">
                            <td className="p-2 font-bold">{m.machine_id}</td>
                            <td className="p-2">{m.temperature} °C</td>
                            <td className="p-2">
                              <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                m.temp_status === "Critical" 
                                  ? "bg-rose-500/10 text-rose-500 animate-pulse" 
                                  : m.temp_status === "Warning"
                                  ? "bg-amber-500/10 text-amber-500"
                                  : "bg-emerald-500/10 text-emerald-500"
                              }`}>
                                {m.temp_status}
                              </span>
                            </td>
                            <td className="p-2 text-right">
                              {m.has_fault ? (
                                <button 
                                  onClick={() => handleDispatchRepair(m.machine_id)}
                                  className="px-2 py-1 bg-indigo-600 hover:bg-indigo-700 text-white rounded text-[10px] font-bold cursor-pointer"
                                >
                                  {t("ops.maint.action.btn")}
                                </button>
                              ) : (
                                <span className="text-slate-400 italic font-sans text-[11px]">{t("ops.maint.action.ok")}</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div className="p-4 bg-black/[0.02] dark:bg-white/[0.02] rounded border border-slate-200 dark:border-slate-800 space-y-3 text-xs">
                    <div className="font-bold">{t("ops.maint.settings")}</div>
                    <div className="flex flex-col md:flex-row gap-4">
                      <div className="flex-1">
                        <label className="text-[10px] uppercase font-mono text-slate-400 block mb-1">{t("ops.maint.priority")}</label>
                        <select 
                          value={maintPriority} 
                          onChange={(e) => setMaintPriority(e.target.value)}
                          className={`w-full text-xs p-1 px-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                        >
                          <option value="Critical">Critical {isZh ? "(特急，2小时响应)" : "(2-hr SLA)"}</option>
                          <option value="High">High {isZh ? "(高，12小时响应)" : "(12-hr SLA)"}</option>
                          <option value="Medium">Medium {isZh ? "(日常)" : "(Normal SLA)"}</option>
                        </select>
                      </div>

                      <div className="flex-1">
                        <label className="text-[10px] uppercase font-mono text-slate-400 block mb-1">{t("ops.maint.note")}</label>
                        <input 
                          type="text" 
                          placeholder={t("ops.maint.note.placeholder")}
                          value={dispatchNote}
                          onChange={(e) => setDispatchNote(e.target.value)}
                          className={`w-full text-xs p-1 px-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                        />
                      </div>
                    </div>
                  </div>
                </div>
                )}
              </div>
            )}

            {/* ═══ Finance Portal ═══ */}
            {activePortal === "fin" && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <Layers className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-bold text-sm">{t("ops.fin.header")}</h3>
                </div>

                <div className="space-y-4 text-xs">
                  <div className="p-4 bg-blue-500/5 rounded border border-blue-500/10 space-y-3 leading-normal">
                    <div className="font-bold flex items-center gap-1.5"><AlertTriangle className="w-4 h-4 text-blue-500" /> {t("ops.fin.warning.title")}</div>
                    <p>{t("ops.fin.warning.desc")}</p>
                  </div>

                  <div className="text-center py-12 text-slate-400">
                    <Database className="w-8 h-8 mx-auto mb-2 text-slate-500" />
                    <p className="text-xs font-mono">{t("ops.fin.empty") || "No orders found."}</p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* Sidebar panel */}
          <div className="lg:col-span-4 space-y-6">
            <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-3xs space-y-4`}>
              <h4 className="text-xs font-extrabold uppercase font-mono tracking-wider text-slate-400">{t("ops.panel.title")}</h4>
              
              <div className="space-y-3.5 text-xs leading-normal">
                <p>{t("ops.panel.p1")}</p>

                <div className="p-3 bg-black/5 dark:bg-white/5 rounded font-mono text-[10.5px] border border-slate-200 dark:border-slate-800">
                  <div className="font-extrabold text-blue-500">{t("ops.panel.p2.title")}</div>
                  <p className="mt-1">{t("ops.panel.p2.desc")}</p>
                </div>

                <div className="p-3 bg-black/5 dark:bg-white/5 rounded font-mono text-[10.5px] border border-slate-200 dark:border-slate-800">
                  <div className="font-extrabold text-emerald-500">{t("ops.panel.p3.title")}</div>
                  <p className="mt-1">{t("ops.panel.p3.desc")}</p>
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
