/**
 * OperationalApps — 运营应用门户：CRM / 养护 / 财务三大子门户
 * 只读数据经 apiFetchData → /api/v1/ontology/entities/{type}/instances
 * 写操作（冻结/调额/派工）：GSXK 桥已熔断且动作端点未接通，按「无假真理」铁律
 * 显式降级为禁用态 + DemoBadge（见 WRITE_CAPABILITY_ONLINE 注释）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { 
  Database,
  Layers,
  UserCheck, Lock, Wrench,
  ShieldAlert, Coins
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData, isNoAccessError, NetworkError, API_BASE } from "../api";

// ── Types ──
interface CustomerData {
  id: string; name: string; isActive: boolean;
  revenue: number; region: string; churn_risk: number;
}

interface MachineData {
  machine_id: string; temperature: number; temp_status: string;
  has_fault: boolean;
}

/** 页面级错误（分类后呈现，不谎报 SECURITY_BLOCK、不直排原始报文） */
interface UiError {
  tag: string;    // 已翻译的错误类别标签，如 HTTP_ERROR / PERMISSION_DENIED
  message: string; // 已翻译的可读描述
}

/**
 * 写操作能力门（无假真理铁律）：
 * 原 GSXK 桥已熔断，冻结/调额/派工依赖的本体动作端点（/api/v1/ontology/actions）
 * 尚未接通 —— 旧 executeAction 是必然 throw 的占位 stub，成功分支永不可达，
 * 却把三个写操作呈现为可用控件。现显式降级：控件禁用 + DemoBadge「能力未上线」。
 * TODO(重新启用条件): 后端动作端点接通后恢复真实调用，并同步补
 *   ① 冻结账户二次确认门（属主确认，P1 遗留，禁用态下暂无触发路径）
 *   ② 真实 traceId 透传（当前不发请求，无从生成）。
 */
const WRITE_CAPABILITY_ONLINE = false;

// ── DemoBadge（components/common/DemoBadge 尚不存在，按任务口径内联；单文件 ≤800 行不新增共享件）──
function DemoBadge({ label }: { label: string }) {
  return (
    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded border border-amber-500/40 bg-amber-500/10 text-[9px] font-bold uppercase tracking-wider text-amber-600 dark:text-amber-400 shrink-0">
      <Lock className="w-3 h-3" />
      {label}
    </span>
  );
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

  // ── Error classification（按真实错误类型分级，不再统一谎报 SECURITY_BLOCK；
  //    不直排原始报文，catch 不再吞错）────────────────
  const classifyError = useCallback((e: unknown): UiError => {
    if (isNoAccessError(e)) {
      return { tag: t("ops.error.tag.permission"), message: t("ops.error.permission") };
    }
    if (e instanceof NetworkError) {
      return { tag: t("ops.error.tag.network"), message: t("ops.error.network") };
    }
    const status = (e as { status?: number } | null)?.status;
    if (typeof status === "number") {
      return { tag: t("ops.error.tag.http"), message: t("ops.error.http", { status }) };
    }
    return { tag: t("ops.error.tag.unknown"), message: t("ops.error.unknown") };
  }, [t]);

  const [opError, setOpError] = useState<UiError | null>(null);

  // ── Load data（失败 → 错误态 + 重试，不再静默置空冒充"无数据"）──
  const loadData = useCallback(async () => {
    setLoading(true);
    setOpError(null);
    try {
      const [cust, mach] = await Promise.all([
        apiFetchData<CustomerData[]>(`${API_BASE}/v1/ontology/entities/Customer/instances`),
        apiFetchData<MachineData[]>(`${API_BASE}/v1/ontology/entities/Facility/instances`),
      ]);
      setCustomers(Array.isArray(cust) ? cust : []);
      setMacStatus(Array.isArray(mach) ? mach : []);
    } catch (e) {
      setOpError(classifyError(e));
      setCustomers([]);
      setMacStatus([]);
    } finally {
      setLoading(false);
    }
  }, [classifyError]);

  useEffect(() => { loadData(); }, [loadData]);

  // ── State ────────────────────────────────
  const [selectedCustId, setSelectedCustId] = useState("");
  const [creditIncrement, setCreditIncrement] = useState("5000000");
  const [maintPriority, setMaintPriority] = useState("High");
  const [dispatchNote, setDispatchNote] = useState("");

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
          
          <div className={`flex items-center gap-1.5 p-1 ${styles.appBg} rounded-lg shrink-0 border ${styles.cardBorder}`}>
            {(["crm", "maint", "fin"] as const).map((portal) => (
              <button type="button"
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

        {/* Capability-not-online notice（写操作全部禁用并如实标注，无假可用） */}
        {!WRITE_CAPABILITY_ONLINE && (
          <div role="status" className={`flex items-center gap-2 px-3 py-2 rounded-lg border ${styles.warningBg} ${styles.warningBorder} ${styles.warningText}`}>
            <DemoBadge label={t("ops.demo.badge")} />
            <span className="text-[11px] leading-normal">{t("ops.demo.notice")}</span>
          </div>
        )}

        {/* Error toast（真实分类 + 重试；不再统一贴 SECURITY_BLOCK 前缀谎报安全拦截） */}
        {opError && (
          <div role="alert" className={`p-4 border rounded-lg flex items-start gap-3 text-xs font-mono ${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}`}>
            <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            <div className="font-medium">
              <span className="font-bold">{opError.tag}:</span> {opError.message}
            </div>
            <button type="button" onClick={loadData} className="ml-auto font-bold underline hover:no-underline shrink-0">
              {t("ops.retry")}
            </button>
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
                    <p className={`text-xs ${styles.cardTextMuted} font-mono`}>{t("ops.loading") || "Loading..."}</p>
                  </div>
                ) : customers.length === 0 ? (
                  <div className={`text-center py-12 ${styles.cardTextMuted}`}>
                    <Database className={`w-8 h-8 mx-auto mb-2 ${styles.muted}`} />
                    <p className="text-xs font-mono">{t("ops.crm.empty") || "No customers found."}</p>
                  </div>
                ) : (
                <div className="space-y-4">
                  <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                    <div className="flex-1">
                      <label className={`text-[10px] uppercase font-mono ${styles.cardTextMuted} block mb-1`}>{t("ops.crm.select")}</label>
                      <select 
                        value={selectedCustId}
                        onChange={(e) => setSelectedCustId(e.target.value)}
                        className={`w-full text-xs font-mono p-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                      >
                        {customers.map((c) => (
                          <option key={c.id} value={c.id} className={styles.cardText}>{c.name} ({c.id})</option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className={`text-[10px] uppercase font-mono ${styles.cardTextMuted} block mb-1`}>{t("ops.crm.status")}</label>
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
                    <div className={`grid grid-cols-1 md:grid-cols-3 gap-4 py-4 border-y border-dashed ${styles.cardBorder}`}>
                      <div className={`p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border ${styles.cardBorder}`}>
                        <div className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{t("ops.crm.revenue")}</div>
                        <div className="text-base font-bold mt-1">${currentCust.revenue?.toLocaleString?.() || "—"}</div>
                      </div>
                      <div className={`p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border ${styles.cardBorder}`}>
                        <div className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{t("ops.crm.region")}</div>
                        <div className="text-base font-bold mt-1">{currentCust.region === "APAC" && isZh ? t("ops.crm.region.apac") : (currentCust.region || "—")} ({t("ops.crm.region.sea")})</div>
                      </div>
                      <div className={`p-3 bg-black/[0.01] dark:bg-white/[0.01] rounded border ${styles.cardBorder}`}>
                        <div className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{t("ops.crm.churn")}</div>
                        <div className={`text-base font-bold mt-1 ${(currentCust.churn_risk || 0) > 0.5 ? "text-red-500" : "text-emerald-500"}`}>
                          {((currentCust.churn_risk || 0) * 100).toFixed(0)}%
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Operational execution controllers — 能力未上线：全部禁用 + DemoBadge，不发送任何写请求 */}
                  {currentCust && (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                      <div className="p-4 rounded-lg border border-indigo-500/10 bg-indigo-500/[0.01] space-y-3">
                        <div className="text-xs font-bold flex items-center gap-1.5">
                          <Coins className="w-4 h-4 text-indigo-500" /> {t("ops.crm.control.credit")}
                          {!WRITE_CAPABILITY_ONLINE && <DemoBadge label={t("ops.demo.badge")} />}
                        </div>
                        <div className="flex gap-2">
                          <input 
                            type="number" 
                            value={creditIncrement} 
                            onChange={(e) => setCreditIncrement(e.target.value)}
                            disabled
                            className={`flex-1 text-xs font-mono p-1 px-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText} disabled:opacity-50`}
                          />
                          <button type="button" 
                            disabled
                            title={t("ops.demo.badge")}
                            className="px-3 py-1.5 bg-indigo-600 text-white rounded text-xs font-bold leading-none opacity-50 cursor-not-allowed"
                          >
                            {t("ops.crm.control.credit.btn")}
                          </button>
                        </div>
                        <span className={`text-[9.5px] ${styles.cardTextMuted} block leading-normal`}>{t("ops.crm.control.credit.hint")}</span>
                      </div>

                      <div className="p-4 rounded-lg border border-red-500/10 bg-red-500/[0.01] space-y-3">
                        <div className="text-xs font-bold text-red-500 flex items-center gap-1.5">
                          <Lock className="w-4 h-4 text-red-500" /> {t("ops.crm.control.freeze")}
                          {!WRITE_CAPABILITY_ONLINE && <DemoBadge label={t("ops.demo.badge")} />}
                        </div>
                        <button type="button" 
                          disabled
                          title={t("ops.demo.badge")}
                          className="w-full py-2 bg-rose-500 text-white text-xs font-bold rounded opacity-50 cursor-not-allowed"
                        >
                          {t("ops.crm.control.freeze.btn")}
                        </button>
                        <span className={`text-[9.5px] ${styles.cardTextMuted} block leading-normal`}>
                          {t("ops.crm.control.freeze.hint")}
                        </span>
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
                    <p className={`text-xs ${styles.cardTextMuted} font-mono`}>{t("ops.loading") || "Loading..."}</p>
                  </div>
                ) : macStatus.length === 0 ? (
                  <div className={`text-center py-12 ${styles.cardTextMuted}`}>
                    <Database className={`w-8 h-8 mx-auto mb-2 ${styles.muted}`} />
                    <p className="text-xs font-mono">{t("ops.maint.empty") || "No machines found."}</p>
                  </div>
                ) : (
                <div className="space-y-4">
                  <div className={`overflow-x-auto rounded border ${styles.cardBorder} text-xs`}>
                    <table className="w-full border-collapse">
                      <thead className="bg-[var(--card,#334155)] text-[var(--card,#E2E8F0)] font-mono text-[9px] uppercase tracking-wider">
                        <tr>
                          <th className="p-2 text-left">{t("ops.maint.col.id")}</th>
                          <th className="p-2 text-left">{t("ops.maint.col.temp")}</th>
                          <th className="p-2 text-left">{t("ops.maint.col.status")}</th>
                          <th className="p-2 text-right">{t("ops.maint.col.action")}</th>
                        </tr>
                      </thead>
                      <tbody className="font-mono">
                        {macStatus.map((m) => (
                          <tr key={m.machine_id} className="border-b hover:bg-indigo-500/5 odd:bg-black/[0.01] dark:even:bg-[#111827]">
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
                                <button type="button" 
                                  disabled
                                  title={t("ops.demo.badge")}
                                  className="px-2 py-1 bg-indigo-600 text-white rounded text-[10px] font-bold opacity-50 cursor-not-allowed inline-flex items-center gap-1"
                                >
                                  {t("ops.maint.action.btn")}
                                  <Lock className="w-3 h-3" />
                                </button>
                              ) : (
                                <span className={`${styles.cardTextMuted} italic font-sans text-[11px]`}>{t("ops.maint.action.ok")}</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div className={`p-4 bg-black/[0.02] dark:bg-white/[0.02] rounded border ${styles.cardBorder} space-y-3 text-xs`}>
                    <div className="font-bold">{t("ops.maint.settings")}</div>
                    <div className="flex flex-col md:flex-row gap-4">
                      <div className="flex-1">
                        <label className={`text-[10px] uppercase font-mono ${styles.cardTextMuted} block mb-1`}>{t("ops.maint.priority")}</label>
                        <select 
                          value={maintPriority} 
                          onChange={(e) => setMaintPriority(e.target.value)}
                          className={`w-full text-xs p-1 px-2 rounded border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText}`}
                        >
                          <option value="Critical">Critical {t("ops.maint.sla.critical")}</option>
                          <option value="High">High {t("ops.maint.sla.high")}</option>
                          <option value="Medium">Medium {t("ops.maint.sla.medium")}</option>
                        </select>
                      </div>

                      <div className="flex-1">
                        <label className={`text-[10px] uppercase font-mono ${styles.cardTextMuted} block mb-1`}>{t("ops.maint.note")}</label>
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

            {/* ═══ Finance Portal ═══ — 数据源从未接入：摘除"永久空态假入口"与
                虚构的对账告警文案（原 ops.fin.warning.* 描述不真实事件），如实标注能力未上线 */}
            {activePortal === "fin" && (
              <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs`}>
                <div className="flex items-center gap-2 mb-4">
                  <Layers className="w-5 h-5 text-indigo-500" />
                  <h3 className="font-bold text-sm">{t("ops.fin.header")}</h3>
                  <DemoBadge label={t("ops.demo.badge")} />
                </div>

                <div className={`text-center py-12 ${styles.cardTextMuted}`}>
                  <Database className={`w-8 h-8 mx-auto mb-2 ${styles.muted}`} />
                  <p className="text-xs font-mono max-w-md mx-auto leading-normal">{t("ops.fin.notImplemented")}</p>
                </div>
              </div>
            )}
          </div>

          {/* Sidebar panel */}
          <div className="lg:col-span-4 space-y-6">
            <div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-4 shadow-3xs space-y-4`}>
              <h4 className={`text-xs font-extrabold uppercase font-mono tracking-wider ${styles.cardTextMuted}`}>{t("ops.panel.title")}</h4>
              
              <div className="space-y-3.5 text-xs leading-normal">
                <p>{t("ops.panel.p1")}</p>

                <div className={`p-3 bg-black/5 dark:bg-white/5 rounded font-mono text-[10.5px] border ${styles.cardBorder}`}>
                  <div className="font-extrabold text-blue-500">{t("ops.panel.p2.title")}</div>
                  <p className="mt-1">{t("ops.panel.p2.desc")}</p>
                </div>

                <div className={`p-3 bg-black/5 dark:bg-white/5 rounded font-mono text-[10.5px] border ${styles.cardBorder}`}>
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
