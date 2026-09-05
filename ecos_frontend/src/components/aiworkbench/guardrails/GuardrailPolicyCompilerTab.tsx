/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import type { ThemeStyles } from '../../ThemeContext';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface GuardrailPolicyCompilerTabProps {
  styles: ThemeStyles;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  columnPolicies: any[];
  rowPolicies: any[];
  policyStatus: string;
  compiledAt: string;
  compileLogs: string[];
  isCompiling: boolean;
  previewData: { raw: { flights: any[]; pilots: any[] }; compiled: { flights: any[]; pilots: any[] } } | null;
  previewTable: 'pilots' | 'flights';
  setPreviewTable: (v: 'pilots' | 'flights') => void;
  handleSaveAndCompilePolicy: () => void;
  handleToggleColumnPolicy: (id: string) => void;
  handleChangeColumnMaskType: (id: string, type: 'REDACT' | 'PARTIAL' | 'HASH') => void;
  handleUpdateRowFilterCondition: (id: string, condition: string) => void;
  handleToggleRowPolicy: (id: string) => void;
}

export default function GuardrailPolicyCompilerTab({
  styles,
  columnPolicies,
  rowPolicies,
  policyStatus,
  compiledAt,
  compileLogs,
  isCompiling,
  previewData,
  previewTable,
  setPreviewTable,
  handleSaveAndCompilePolicy,
  handleToggleColumnPolicy,
  handleChangeColumnMaskType,
  handleUpdateRowFilterCondition,
  handleToggleRowPolicy,
}: GuardrailPolicyCompilerTabProps) {
  const { t } = useLanguage();
  return (
    <div className="flex-1 flex flex-col min-h-0 gap-6">
      
      {/* Top Banner: Compiler Status */}
      <div className={`${styles.cardBg} rounded-xl p-4 border ${styles.cardBorder} shrink-0 flex flex-col lg:flex-row items-center justify-between gap-4`}>
        <div className="flex items-center gap-3 text-left">
          <div className="p-2.5 bg-rose-500/20 text-rose-400 rounded-full border border-rose-500/30">
            <Icon name="Binary" size={18} />
          </div>
          <div className="space-y-0.5">
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>{t("aiworkbench.guardrails.policyCompilerTitle")}</span>
            <div className="flex items-center gap-2">
              <span className={`font-extrabold text-sm ${styles.cardText}`}>
                Sovereign-Grid Security Policy Compiler v1.4
              </span>
              <span className={`px-2 py-0.5 rounded-full text-[9px] font-mono font-black uppercase inline-flex items-center gap-1 ${
                policyStatus === 'COMPILED'
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                  : 'bg-amber-500/20 text-amber-400 border border-amber-500/30 animate-pulse'
              }`}>
                <span className={`w-1.5 h-1.5 rounded-full ${policyStatus === 'COMPILED' ? 'bg-emerald-400' : 'bg-amber-400'}`} />
                <span>{policyStatus === 'COMPILED' ? 'COMPILED & DEPLOYED' : 'DRAFT (NEEDS COMPILATION)'}</span>
              </span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          {compiledAt && (
            <div className="text-right hidden sm:block">
              <p className={`text-[10px] ${styles.cardTextMuted} font-semibold`}>{t("aiworkbench.guardrails.lastCompileTime")}</p>
              <p className={`font-mono text-[10px] ${styles.cardTextMuted} font-bold`}>{compiledAt}</p>
            </div>
          )}
          <button
            onClick={handleSaveAndCompilePolicy}
            disabled={isCompiling}
            className="px-4 py-2 bg-rose-600 hover:bg-rose-700 disabled:opacity-75 text-white font-bold rounded-lg shadow-md transition-all flex items-center gap-1.5 cursor-pointer"
          >
            {isCompiling ? (
              <>
                <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                <span>{t("aiworkbench.guardrails.compiling")}</span>
              </>
            ) : (
              <>
                <Icon name="Play" size={12} />
                <span>{t("aiworkbench.guardrails.compileDeploy")}</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* Main workspace grids */}
      <div className="flex-1 grid grid-cols-1 xl:grid-cols-5 gap-6 min-h-0 overflow-y-auto">
        
        {/* Left: Configurations panel (2/5 width) */}
        <div className="xl:col-span-2 space-y-6 flex flex-col">
          
          {/* 1. Column Masking Rules */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <div className={`border-b ${styles.cardBorder} pb-2.5 flex items-center justify-between`}>
              <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5`}>
                <Icon name="FileLock2" size={13} className={styles.accentText} />
                <span>{t("aiworkbench.guardrails.columnMaskingRules")}</span>
              </span>
              <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase font-mono`}>COLUMN_MASKING</span>
            </div>

            <div className="space-y-3">
              {columnPolicies.map((pol) => (
                <div key={pol.id} className={`p-3 border ${styles.cardBorder} rounded-xl ${styles.appBg} flex items-center justify-between gap-4 hover:${styles.inputBg} transition-colors`}>
                  <div className="space-y-1">
                    <div className="flex items-center gap-1.5">
                      <span className={`font-bold ${styles.cardText} text-[11px] font-mono`}>{pol.column}</span>
                      <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>({pol.table})</span>
                    </div>
                    <p className={`text-[10px] ${styles.cardTextMuted}`}>
                      {pol.column === 'ssn_number' ? t("aiworkbench.guardrails.ssnHighSensitivity") :
                       pol.column === 'base_salary' ? t("aiworkbench.guardrails.salaryHighSensitivity") :
                       t("aiworkbench.guardrails.pilotName")}
                    </p>
                  </div>

                  <div className="flex items-center gap-2">
                    {pol.isEnabled && (
                      <select
                        value={pol.type}
                        onChange={(e) => handleChangeColumnMaskType(pol.id, e.target.value as any)}
                        className={`${styles.cardBg} border ${styles.cardBorder} rounded-md px-1.5 py-1 text-[10px] font-bold ${styles.cardTextMuted} focus:outline-hidden`}
                      >
                        <option value="REDACT">{t("aiworkbench.guardrails.redact")}</option>
                        <option value="PARTIAL">{t("aiworkbench.guardrails.partial")}</option>
                        <option value="HASH">{t("aiworkbench.guardrails.hash")}</option>
                      </select>
                    )}
                    
                    <button
                      onClick={() => handleToggleColumnPolicy(pol.id)}
                      className={`relative inline-flex h-4.5 w-8 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden ${
                        pol.isEnabled ? styles.accentBg : `${styles.inputBorder}`
                      }`}
                    >
                      <span className={`pointer-events-none inline-block h-3.5 w-3.5 transform rounded-full styles.cardBg shadow-xs ring-0 transition duration-200 ease-in-out ${
                        pol.isEnabled ? 'translate-x-3.5' : 'translate-x-0'
                      }`} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* 2. Row Filtering SQL Logic */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <div className={`border-b ${styles.cardBorder} pb-2.5 flex items-center justify-between`}>
              <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5`}>
                <Icon name="Filter" size={13} className="text-amber-500" />
                <span>{t("aiworkbench.guardrails.rowLevelFiltering")}</span>
              </span>
              <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase font-mono`}>ROW_ISOLATION</span>
            </div>

            <div className="space-y-4">
              {rowPolicies.map((pol) => (
                <div key={pol.id} className={`p-3.5 border ${styles.cardBorder} rounded-xl ${styles.appBg} space-y-3 hover:${styles.inputBg} transition-colors`}>
                  <div className="flex items-center justify-between">
                    <div className="space-y-0.5">
                      <span className={`font-extrabold ${styles.cardText} text-[11px] font-mono block`}>Table: {pol.table}</span>
                      <span className={`text-[10px] ${styles.cardTextMuted}`}>
                        {pol.table === 'ds_pilots_biography' ? t("aiworkbench.guardrails.pilotTableFilter") : t("aiworkbench.guardrails.flightTableFilter")}
                      </span>
                    </div>
                    
                    <button
                      onClick={() => handleToggleRowPolicy(pol.id)}
                      className={`relative inline-flex h-4.5 w-8 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden ${
                        pol.isEnabled ? 'bg-amber-500' : `${styles.inputBorder}`
                      }`}
                    >
                      <span className={`pointer-events-none inline-block h-3.5 w-3.5 transform rounded-full styles.cardBg shadow-xs ring-0 transition duration-200 ease-in-out ${
                        pol.isEnabled ? 'translate-x-3.5' : 'translate-x-0'
                      }`} />
                    </button>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className={`text-[9px] ${styles.cardTextMuted} font-bold font-mono`}>SQL WHERE PREDICATE</span>
                      <span className={`text-[8px] ${styles.cardTextMuted} font-bold ${styles.appBg} px-1 py-0.5 rounded font-mono`}>DORIS PARSABLE</span>
                    </div>
                    <div className={`flex items-center gap-1 ${styles.appBg} rounded-lg px-2.5 py-1.5 border ${styles.cardBorder}`}>
                      <span className={`font-mono ${styles.cardTextMuted} font-bold text-[10px] select-none`}>WHERE</span>
                      <input
                        type="text"
                        value={pol.condition}
                        disabled={!pol.isEnabled}
                        onChange={(e) => handleUpdateRowFilterCondition(pol.id, e.target.value)}
                        className={`bg-transparent border-0 font-mono text-[10px] ${styles.cardTextMuted} font-bold focus:ring-0 focus:outline-hidden w-full disabled:opacity-50`}
                        placeholder="SQL condition e.g. hours_flown > 6000"
                      />
                    </div>
                    
                    {pol.isEnabled && (
                      <div className="flex flex-wrap items-center gap-1.5 mt-2">
                        <span className={`text-[8px] ${styles.cardTextMuted} font-bold uppercase`}>{t("aiworkbench.guardrails.recommendedTemplates")}</span>
                        {pol.table === 'ds_pilots_biography' ? (
                          <>
                            <button
                              onClick={() => { handleUpdateRowFilterCondition(pol.id, 'hours_flown > 6000'); }}
                              className={`px-1.5 py-0.5 ${styles.inputBg} hover:opacity-80 rounded font-mono text-[8px] font-bold ${styles.cardTextMuted} cursor-pointer`}
                            >
                              hours_flown &gt; 6000
                            </button>
                            <button
                              onClick={() => { handleUpdateRowFilterCondition(pol.id, "licence_rating = 'B737-MAX'"); }}
                              className={`px-1.5 py-0.5 ${styles.inputBg} hover:opacity-80 rounded font-mono text-[8px] font-bold ${styles.cardTextMuted} cursor-pointer`}
                            >
                              licence_rating='B737-MAX'
                            </button>
                            <button
                              onClick={() => { handleUpdateRowFilterCondition(pol.id, 'base_salary < 80000'); }}
                              className={`px-1.5 py-0.5 ${styles.inputBg} hover:opacity-80 rounded font-mono text-[8px] font-bold ${styles.cardTextMuted} cursor-pointer`}
                            >
                              base_salary &lt; 80000
                            </button>
                          </>
                        ) : (
                          <>
                            <button
                              onClick={() => { handleUpdateRowFilterCondition(pol.id, "status = 'ON_TIME'"); }}
                              className={`px-1.5 py-0.5 ${styles.inputBg} hover:opacity-80 rounded font-mono text-[8px] font-bold ${styles.cardTextMuted} cursor-pointer`}
                            >
                              status='ON_TIME'
                            </button>
                            <button
                              onClick={() => { handleUpdateRowFilterCondition(pol.id, 'delay_minutes > 0'); }}
                              className={`px-1.5 py-0.5 ${styles.inputBg} hover:opacity-80 rounded font-mono text-[8px] font-bold ${styles.cardTextMuted} cursor-pointer`}
                            >
                              delay_minutes &gt; 0
                            </button>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>

        </div>

        {/* Right: Compile output + Side-by-side Sandbox (3/5 width) */}
        <div className="xl:col-span-3 space-y-6 flex flex-col min-h-0">
          
          {/* Compiler stream logs */}
          <div className={`${styles.cardBg} rounded-xl border ${styles.cardBorder} p-4 shadow-md font-mono flex flex-col space-y-2.5 shrink-0`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500 animate-pulse" />
                <span className={`font-bold text-xs ${styles.cardTextMuted}`}>Compiler Stage Stream logs</span>
              </div>
              <span className={`text-[9px] ${styles.cardTextMuted} font-bold`}>SECURE_GRID_COMPILER_BUS</span>
            </div>
            
            <div className="space-y-1.5 max-h-40 overflow-y-auto text-[10px] leading-relaxed select-text">
              {compileLogs.map((log: string, idx: number) => (
                <p key={idx} className={
                  log.includes('✅') ? 'text-emerald-400 font-extrabold' :
                  log.includes('⚠️') ? 'text-amber-400 font-semibold' :
                  log.includes('🔒') ? 'text-sky-400 font-medium' : styles.cardTextMuted
                }>
                  {log}
                </p>
              ))}
            </div>
          </div>

          {/* Side-by-side Dry-run database comparison view */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs p-4 flex-1 flex flex-col min-h-0`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3 mb-3 shrink-0`}>
              <div className="space-y-0.5">
                <span className={`font-extrabold ${styles.cardTextMuted} flex items-center gap-1.5 text-xs`}>
                  <Icon name="RefreshCw" size={13} className="text-emerald-500" />
                  <span>{t("aiworkbench.guardrails.dryRunSandbox")}</span>
                </span>
                <p className={`text-[10px] ${styles.cardTextMuted} font-sans`}>
                  {t("aiworkbench.guardrails.dryRunDescription")}
                </p>
              </div>

              <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder} shrink-0`}>
                <button
                  onClick={() => setPreviewTable('pilots')}
                  className={`px-2.5 py-1 rounded font-bold text-[9px] cursor-pointer transition-colors ${
                    previewTable === 'pilots' ? `${styles.cardBg} ${styles.cardText} shadow-xs` : styles.cardTextMuted
                  }`}
                >
                  ds_pilots_biography
                </button>
                <button
                  onClick={() => setPreviewTable('flights')}
                  className={`px-2.5 py-1 rounded font-bold text-[9px] cursor-pointer transition-colors ${
                    previewTable === 'flights' ? `${styles.cardBg} ${styles.cardText} shadow-xs` : styles.cardTextMuted
                  }`}
                >
                  ds_flights_clean
                </button>
              </div>
            </div>

            {previewData ? (
              <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-4 min-h-0 overflow-hidden text-[9px]">
                
                {/* Left Panel: Raw Unsecured View */}
                <div className={`flex flex-col border ${styles.cardBorder} rounded-xl overflow-hidden min-h-0 ${styles.appBg}/20`}>
                  <div className={`p-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
                    <span className={`font-bold ${styles.cardTextMuted} flex items-center gap-1`}>
                      <Icon name="LockOpen" size={10} className={styles.cardTextMuted} />
                      <span>{t("aiworkbench.guardrails.rawDbView")}</span>
                    </span>
                    <span className={`px-1 py-0.5 rounded ${styles.inputBg} ${styles.cardTextMuted} text-[8px] font-mono`}>PLAIN_TEXT</span>
                  </div>

                  <div className="flex-1 overflow-auto p-2">
                    {previewTable === 'pilots' ? (
                      <table className="w-full text-left font-mono leading-relaxed">
                        <thead className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-extrabold`}>
                          <tr>
                            <th className="p-1">{t("aiworkbench.guardrails.name")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.ssnNumber")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.baseSalary")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.safeFlightHours")}</th>
                          </tr>
                        </thead>
                        <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                          {previewData.raw.pilots.map((p: any) => (
                            <tr key={p.pilot_id} className={`hover:${styles.inputBg}`}>
                              <td className={`p-1 font-sans font-bold ${styles.cardText}`}>{p.pilot_name}</td>
                              <td className="p-1 font-semibold">{p.ssn_number}</td>
                              <td className={`p-1 ${styles.cardTextMuted}`}>￥{p.base_salary.toLocaleString()}</td>
                              <td className={`p-1 ${styles.accentText}`}>{p.hours_flown}h</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <table className="w-full text-left font-mono leading-relaxed">
                        <thead className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-extrabold`}>
                          <tr>
                            <th className="p-1">{t("aiworkbench.guardrails.flightNumber")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.route")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.pilotHighSensitivity")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.delayStatus")}</th>
                          </tr>
                        </thead>
                        <tbody className={`divide-y ${styles.cardBorder} ${styles.cardTextMuted}`}>
                          {previewData.raw.flights.map((f: any) => (
                            <tr key={f.flight_id} className={`hover:${styles.inputBg}`}>
                              <td className={`p-1 font-extrabold ${styles.accentText}`}>{f.flight_num}</td>
                              <td className="p-1">{f.dep_airport}➔{f.arr_airport}</td>
                              <td className={`p-1 font-sans font-medium ${styles.cardTextMuted}`}>{f.pilot_name}</td>
                              <td className={`p-1 ${styles.cardTextMuted}`}>{f.status} ({f.delay_minutes}min)</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>

                {/* Right Panel: Secure View */}
                <div className="flex flex-col border border-rose-200 bg-rose-50/5 rounded-xl overflow-hidden min-h-0">
                  <div className="p-2 border-b border-rose-100 bg-rose-500/5 flex items-center justify-between">
                    <span className="font-extrabold text-rose-800 flex items-center gap-1">
                      <Icon name="Lock" size={10} className="text-rose-600" />
                      <span>{t("aiworkbench.guardrails.secureView")}</span>
                    </span>
                    <span className="px-1 py-0.5 rounded bg-rose-600 text-white text-[8px] font-mono">MASKED_&_SLICED</span>
                  </div>

                  <div className="flex-1 overflow-auto p-2">
                    {previewTable === 'pilots' ? (
                      <table className="w-full text-left font-mono leading-relaxed">
                        <thead className={`bg-rose-500/5 border-b border-rose-100 ${styles.cardTextMuted} font-extrabold`}>
                          <tr>
                            <th className="p-1">{t("aiworkbench.guardrails.name")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.ssnMasked")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.salaryMasked")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.safeFlightHours")}</th>
                          </tr>
                        </thead>
                        <tbody className={`divide-y divide-rose-100/30 ${styles.cardTextMuted}`}>
                          {previewData.compiled.pilots.length === 0 ? (
                            <tr>
                              <td colSpan={4} className={`p-4 text-center ${styles.cardTextMuted} font-sans font-bold italic`}>
                                {t("aiworkbench.guardrails.rowFilterActive")}
                              </td>
                            </tr>
                          ) : (
                            previewData.compiled.pilots.map((p: any) => {
                              const rawP = previewData.raw.pilots.find((rp: any) => rp.pilot_id === p.pilot_id);
                              const isSsnMasked = rawP && rawP.ssn_number !== p.ssn_number;
                              const isSalaryMasked = rawP && rawP.base_salary !== p.base_salary;
                              return (
                                <tr key={p.pilot_id} className="hover:bg-rose-500/5">
                                  <td className={`p-1 font-sans font-bold ${styles.cardText}`}>{p.pilot_name}</td>
                                  <td className={`p-1 font-extrabold ${isSsnMasked ? 'text-amber-600 bg-amber-50 rounded-sm px-1 font-mono text-[8.5px]' : ''}`}>
                                    {p.ssn_number}
                                  </td>
                                  <td className={`p-1 font-extrabold ${isSalaryMasked ? 'text-red-600 bg-red-50 rounded-sm px-1 font-mono text-[8.5px]' : ''}`}>
                                    {typeof p.base_salary === 'number' ? `￥${p.base_salary.toLocaleString()}` : p.base_salary}
                                  </td>
                                  <td className={`p-1 ${styles.accentText}`}>{p.hours_flown}h</td>
                                </tr>
                              );
                            })
                          )}
                        </tbody>
                      </table>
                    ) : (
                      <table className="w-full text-left font-mono leading-relaxed">
                        <thead className={`bg-rose-500/5 border-b border-rose-100 ${styles.cardTextMuted} font-extrabold`}>
                          <tr>
                            <th className="p-1">{t("aiworkbench.guardrails.flightNumber")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.route")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.pilotMasked")}</th>
                            <th className="p-1">{t("aiworkbench.guardrails.delayStatus")}</th>
                          </tr>
                        </thead>
                        <tbody className={`divide-y divide-rose-100/30 ${styles.cardTextMuted}`}>
                          {previewData.compiled.flights.length === 0 ? (
                            <tr>
                              <td colSpan={4} className={`p-4 text-center ${styles.cardTextMuted} font-sans font-bold italic`}>
                                {t("aiworkbench.guardrails.rowFilterActive")}
                              </td>
                            </tr>
                          ) : (
                            previewData.compiled.flights.map((f: any) => {
                              const rawF = previewData.raw.flights.find((rf: any) => rf.flight_id === f.flight_id);
                              const isPilotNameMasked = rawF && rawF.pilot_name !== f.pilot_name;
                              return (
                                <tr key={f.flight_id} className="hover:bg-rose-500/5">
                                  <td className={`p-1 font-extrabold ${styles.accentText}`}>{f.flight_num}</td>
                                  <td className="p-1">{f.dep_airport}➔{f.arr_airport}</td>
                                  <td className={`p-1 font-sans font-bold ${isPilotNameMasked ? 'text-purple-600 bg-purple-50 rounded-sm px-1 text-[8.5px]' : styles.cardText}`}>
                                    {f.pilot_name}
                                  </td>
                                  <td className={`p-1 ${styles.cardTextMuted}`}>{f.status} ({f.delay_minutes}min)</td>
                                </tr>
                              );
                            })
                          )}
                        </tbody>
                      </table>
                    )}
                  </div>
                </div>

              </div>
            ) : (
              <div className={`flex-1 flex items-center justify-center ${styles.cardTextMuted}`}>
                <span>{t("aiworkbench.guardrails.generatingDryRun")}</span>
              </div>
            )}
          </div>

        </div>

      </div>

    </div>
  );
}
