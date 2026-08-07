/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import type { ThemeStyles } from '../../ThemeContext';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface SimulationTabProps {
  simUserId: string;
  setSimUserId: (v: string) => void;
  simDatasetId: string;
  setSimDatasetId: (v: string) => void;
  simQuery: string;
  setSimQuery: (v: string) => void;
  isSimulating: boolean;
  simResult: any | null;
  expandedNodes: Record<string, boolean>;
  toggleNodeExpanded: (nodeId: string) => void;
  handleRunSimulation: () => Promise<void>;
  styles: ThemeStyles;
}

export default function SimulationTab({
  simUserId,
  setSimUserId,
  simDatasetId,
  setSimDatasetId,
  simQuery,
  setSimQuery,
  isSimulating,
  simResult,
  expandedNodes,
  toggleNodeExpanded,
  handleRunSimulation,
  styles,
}: SimulationTabProps) {
  const { t } = useLanguage();
  return (
    <div className={`flex-1 flex flex-col overflow-hidden ${styles.inputBg}`}>
      
      {/* Form Inputs Panel */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} space-y-3 shrink-0`}>
        <h3 className={`text-[11px] font-black ${styles.cardText} flex items-center gap-1`}>
          <Icon name="SlidersHorizontal" size={12} className={styles.accentText} />
          <span>{t("aiworkbench.agentStudio.simContextInputs")}</span>
        </h3>

        <div className="grid grid-cols-2 gap-2 text-[10px]">
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-bold`}>{t("aiworkbench.agentStudio.simUser")}</label>
            <select
              value={simUserId}
              onChange={e => setSimUserId(e.target.value)}
              className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
            >
              <option value="analyst_li">{t("aiworkbench.agentStudio.optionAnalyst")}</option>
              <option value="hr_manager">{t("aiworkbench.agentStudio.optionHR")}</option>
              <option value="external_auditor">{t("aiworkbench.agentStudio.optionAuditor")}</option>
              <option value="EU_DPO">{t("aiworkbench.agentStudio.optionDPO")}</option>
              <option value="admin_guorong">{t("aiworkbench.agentStudio.optionCSO")}</option>
            </select>
          </div>

          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-bold`}>{t("aiworkbench.agentStudio.simDataset")}</label>
            <select
              value={simDatasetId}
              onChange={e => setSimDatasetId(e.target.value)}
              className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
            >
              <option value="ds_pilots_biography">{t("aiworkbench.agentStudio.optionPilots")}</option>
              <option value="ds_flights_clean">{t("aiworkbench.agentStudio.optionFlights")}</option>
              <option value="ds_ticket_sales">{t("aiworkbench.agentStudio.optionSales")}</option>
            </select>
          </div>
        </div>

        {/* Natural Language Query Input */}
        <div className="space-y-1">
          <label className={`block text-[10px] ${styles.cardTextMuted} font-bold`}>{t("aiworkbench.agentStudio.simQueryLabel")}</label>
          <textarea
            value={simQuery}
            onChange={e => setSimQuery(e.target.value)}
            placeholder={t("aiworkbench.agentStudio.simQueryPlaceholder")}
            rows={2}
            className={`w-full px-2.5 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg text-xs font-medium resize-none focus:outline-hidden focus:${styles.cardBorder}`}
          />
        </div>

        {/* Suggestion tags */}
        <div className="flex flex-wrap gap-1">
          <span className={`text-[9px] ${styles.cardTextMuted} self-center font-bold mr-1`}>{t("aiworkbench.agentStudio.presetHighRiskScenarios")}</span>
          <button
            onClick={() => {
              setSimUserId('analyst_li');
              setSimDatasetId('ds_pilots_biography');
              setSimQuery(t("aiworkbench.agentStudio.presetQuerySSN",)};
            }}
            className="px-2 py-0.5 bg-red-50 hover:bg-red-100 border border-red-100 rounded text-[9px] text-red-700 font-bold"
          >
            {t("aiworkbench.agentStudio.scenarioSSNLeak")}
          </button>
          <button
            onClick={() => {
              setSimUserId('EU_DPO');
              setSimDatasetId('ds_pilots_biography');
              setSimQuery(t("aiworkbench.agentStudio.presetQueryDPO",)};
            }}
            className="px-2 py-0.5 bg-emerald-50 hover:bg-emerald-100 border border-emerald-100 rounded text-[9px] text-emerald-700 font-bold"
          >
            {t("aiworkbench.agentStudio.scenarioDPOCompliance")}
          </button>
          <button
            onClick={() => {
              setSimUserId('hr_manager');
              setSimDatasetId('ds_flights_clean');
              setSimQuery(t("aiworkbench.agentStudio.presetQueryFlights",)};
            }}
            className={`px-2 py-0.5 ${styles.badgeBg} hover:opacity-80 ${styles.accentBorder} border rounded text-[9px] ${styles.accentText} font-bold`}
          >
            {t("aiworkbench.agentStudio.scenarioNormalQuery")}
          </button>
        </div>

        {/* Simulation Trigger Button */}
        <button
          onClick={handleRunSimulation}
          disabled={isSimulating}
          className={`w-full py-2 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg font-black text-xs transition-all cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50`}
        >
          {isSimulating ? (
            <>
              <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              <span>{t("aiworkbench.agentStudio.simComputing")}</span>
            </>
          ) : (
            <>
              <Icon name="Play" size={11} className="fill-current text-emerald-400" />
              <span>{t("aiworkbench.agentStudio.runSimulator")}</span>
            </>
          )}
        </button>
      </div>

      {/* Simulation Output Area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        
        {isSimulating && (
          <div className={`flex flex-col items-center justify-center py-20 ${styles.cardTextMuted} space-y-3`}>
            <Icon name="ShieldAlert" size={32} className={`${styles.accentText} animate-pulse`} />
            <div className="text-center space-y-1">
              <p className={`font-extrabold ${styles.cardTextMuted}`}>{t("aiworkbench.agentStudio.evaluatingZeroTrust")}</p>
              <p className="text-[10px]">Org IP Whitelist ➔ Project DAC ➔ Marking MAC ➔ Purpose PBAC</p>
            </div>
          </div>
        )}

        {!isSimulating && !simResult && (
          <div className={`flex flex-col items-center justify-center py-24 ${styles.cardTextMuted} text-center space-y-2`}>
            <Icon name="Tv" size={28} className={styles.cardTextMuted} />
            <span className={`font-bold ${styles.cardTextMuted}`}>{t("aiworkbench.agentStudio.pendingSimulation")}</span>
            <p className={`text-[10px] ${styles.cardTextMuted} max-w-xs`}>{t("aiworkbench.agentStudio.simSetupHint")}</p>
          </div>
        )}

        {!isSimulating && simResult && (
          <div className="space-y-4">
            
            {/* Overall Status Banner */}
            <div className={`p-3.5 rounded-xl border flex items-center justify-between ${
              simResult.overallVerdict === 'GRANTED'
                ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                : 'bg-rose-50 border-rose-200 text-rose-800'
            }`}>
              <div className="flex items-center gap-2.5">
                <span className={`p-1.5 rounded-lg shrink-0 ${
                  simResult.overallVerdict === 'GRANTED' ? 'bg-emerald-100 text-emerald-600' : 'bg-rose-100 text-rose-600'
                }`}>
                  <Icon name={simResult.overallVerdict === 'GRANTED' ? 'CheckCircle2' : 'ShieldAlert'} size={18} />
                </span>
                <div>
                  <p className="font-black text-xs">{t("aiworkbench.agentStudio.overallVerdict")} {simResult.overallVerdict}</p>
                  <p className="text-[10px] opacity-80">{t("aiworkbench.agentStudio.securityCompilerExecuted")}</p>
                </div>
              </div>
              <span className={`px-2.5 py-1 text-[10px] font-black rounded-md uppercase tracking-wider ${
                simResult.overallVerdict === 'GRANTED'
                  ? 'bg-emerald-600 text-white shadow-xs'
                  : 'bg-rose-600 text-white shadow-xs'
              }`}>
                {simResult.overallVerdict === 'GRANTED' ? 'Passed' : 'Intercepted'}
              </span>
            </div>

            {/* Nodes Section */}
            <div className="space-y-3">
              <h4 className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1`}>
                <Icon name="Route" size={11} />
                <span>{t("aiworkbench.agentStudio.decisionTracesLog")}</span>
              </h4>

              {simResult.nodes.map((node: any) => {
                const isExpanded = expandedNodes[node.id];
                const nodePassed = node.verdict === 'GRANTED';
                
                return (
                  <div key={node.id} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
                    
                    {/* Node Title Header */}
                    <div
                      onClick={() => toggleNodeExpanded(node.id)}
                      className={`p-3 ${styles.inputBg} flex items-center justify-between cursor-pointer select-none border-b ${styles.cardBorder}`}
                    >
                      <div className="flex items-center gap-2">
                        <span className={`p-1 rounded-md text-[9px] font-bold ${
                          nodePassed ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'
                        }`}>
                          <Icon name={nodePassed ? 'Check' : 'X'} size={10} />
                        </span>
                        <span className={`font-extrabold ${styles.cardText} text-[11px]`}>{node.name}</span>
                      </div>

                      <div className="flex items-center gap-2">
                        <span className={`px-1.5 py-0.5 text-[8px] font-bold rounded uppercase ${
                          nodePassed ? 'bg-emerald-500/10 text-emerald-600' : 'bg-rose-500/10 text-rose-600'
                        }`}>
                          {node.verdict}
                        </span>
                        <Icon
                          name={isExpanded ? 'ChevronDown' : 'ChevronRight'}
                          size={12}
                          className={styles.cardTextMuted}
                        />
                      </div>
                    </div>

                    {/* Expanded Node Content */}
                    {isExpanded && (
                      <div className={`p-3 space-y-3 ${styles.cardBg} text-[10px]`}>
                        
                        {/* Node Trace logs */}
                        <div className={`${styles.appBg} ${styles.cardTextMuted} p-2.5 rounded-lg font-mono text-[9px] leading-relaxed space-y-1`}>
                          {node.traces.map((trace: string, tIdx: number) => {
                            const isErr = trace.includes('❌') || trace.includes('FAIL') || trace.includes(t("aiworkbench.agentStudio.rejected",)};
                            const isAlert = trace.includes('⚠️');
                            return (
                              <div key={tIdx} className={`flex items-start gap-1 ${isErr ? 'text-rose-400' : isAlert ? 'text-amber-400' : ''}`}>
                                <span className={`${styles.cardTextMuted} shrink-0`}>▶</span>
                                <span>{trace}</span>
                              </div>
                            );
                          })}
                        </div>

                        {/* Special Node 2: RAG */}
                        {node.id === 'node_rag_retrieval' && (
                          <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                            <div className="flex items-center justify-between">
                              <span className={`font-bold ${styles.cardTextMuted}`}>{t("aiworkbench.agentStudio.ragRerankPayload")}</span>
                              {node.isMaskedEnforced && (
                                <span className="px-2 py-0.5 bg-red-500 text-white rounded text-[8px] font-black animate-pulse flex items-center gap-0.5">
                                  <Icon name="Shield" size={8} />
                                  <span>{t("aiworkbench.agentStudio.regexMasked")}</span>
                                </span>
                              )}
                            </div>

                            <div className={`p-2 ${styles.inputBg} border ${styles.cardBorder} rounded-lg text-[10px] ${styles.cardTextMuted} max-h-32 overflow-y-auto whitespace-pre-wrap font-mono`}>
                              {node.groundedContext}
                            </div>
                          </div>
                        )}

                        {/* Special Node 3: LLM */}
                        {node.id === 'node_llm_inference' && (
                          <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                            <span className={`font-bold ${styles.cardTextMuted} block`}>{t("aiworkbench.agentStudio.llmFinalAnswer")}</span>
                            <div className={`p-3 ${styles.appBg} text-emerald-400 rounded-lg text-[10px] leading-relaxed whitespace-pre-wrap font-sans border ${styles.cardBorder}`}>
                              {node.answer}
                            </div>
                          </div>
                        )}

                        {/* Special Node 4: Data Firewall */}
                        {node.id === 'node_data_masking' && (
                          <div className={`space-y-2 border-t ${styles.cardBorder} pt-2.5`}>
                            <span className={`font-bold ${styles.cardTextMuted} block`}>{t("aiworkbench.agentStudio.maskedRecords")}</span>
                            {node.dataRows && node.dataRows.length > 0 ? (
                              <div className={`p-2 ${styles.cardBg} ${styles.cardTextMuted} rounded-lg text-[9px] font-mono overflow-x-auto max-h-36`}>
                                <pre className="leading-tight">{JSON.stringify(node.dataRows, null, 2)}</pre>
                              </div>
                            ) : (
                              <p className="text-[10px] text-rose-500 font-bold bg-rose-50 p-1.5 rounded">
                                {t("aiworkbench.agentStudio.interceptWarning")}
                              </p>
                            )}
                          </div>
                        )}

                      </div>
                    )}

                  </div>
                );
              })}
            </div>

          </div>
        )}

      </div>

    </div>
  );
}
