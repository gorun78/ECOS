/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import type { ThemeStyles } from '../../ThemeContext';
import type { AIPGuardrail } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import Pagination from '../../common/Pagination';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface GuardrailSimulatorTabProps {
  styles: ThemeStyles;
  guardrails: AIPGuardrail[];
  handleToggle: (id: string) => void;
  testInput: string;
  setTestInput: (v: string) => void;
  isSimulating: boolean;
  sandboxTrace: string[];
  sandboxResult: { status: 'passed' | 'warned' | 'blocked'; processedText: string; triggeredFilters: string[] } | null;
  handleRunSimulator: () => Promise<void>;
}

export default function GuardrailSimulatorTab({
  styles,
  guardrails,
  handleToggle,
  testInput,
  setTestInput,
  isSimulating,
  sandboxTrace,
  sandboxResult,
  handleRunSimulator,
}: GuardrailSimulatorTabProps) {
  const { t } = useLanguage();
  const [guardPage, setGuardPage] = useState(1);
  const [guardPageSize] = useState(6);
  const [guardSortBy, setGuardSortBy] = useState<string>("name");
  const [guardSortOrder, setGuardSortOrder] = useState<"asc" | "desc">("asc");
  return (
    <div className="flex-1 overflow-y-auto">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left Column: Guardrail cards config */}
        <div className="lg:col-span-2 space-y-4">
          <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>{t("aiworkbench.guardrails.rulesConfig")} ({guardrails.length})</h3>
          
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {(() => {
              const sorted = [...guardrails].sort((a: any, b: any) => {
                const aVal = String(a[guardSortBy] ?? "");
                const bVal = String(b[guardSortBy] ?? "");
                return guardSortOrder === "asc" ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
              });
              return sorted.slice((guardPage - 1) * guardPageSize, guardPage * guardPageSize).map(g => (
              <div key={g.id} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col justify-between hover:shadow-md transition-shadow`}>
                
                <div className="space-y-2">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2">
                      <span className={`p-1.5 rounded-lg ${g.isEnabled ? `${styles.badgeBg} ${styles.accentText}` : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
                        <Icon name={
                          g.type === 'pii_redaction' ? 'FileLock2' :
                          g.type === 'human_approval' ? 'Users' :
                          g.type === 'hallucination_check' ? 'EyeOff' : 'ShieldCheck'
                        } size={15} />
                      </span>
                      <h4 className={`font-bold ${styles.cardText} text-xs`}>{g.name}</h4>
                    </div>
                    
                    <button
                      type="button"
                      onClick={() => handleToggle(g.id)}
                      className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-hidden ${
                        g.isEnabled ? styles.accentBg : `${styles.inputBorder}`
                      }`}
                    >
                      <span className={`pointer-events-none inline-block h-4 w-4 transform rounded-full styles.cardBg shadow-xs ring-0 transition duration-200 ease-in-out ${
                        g.isEnabled ? 'translate-x-4' : 'translate-x-0'
                      }`} />
                    </button>
                  </div>
                  
                  <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>{g.description}</p>
                </div>

                <div className={`border-t ${styles.cardBorder} pt-3 mt-3 flex items-center justify-between text-[10px]`}>
                  <span className={`${styles.cardTextMuted} font-mono font-bold uppercase`}>{g.type}</span>
                  <span className={`px-1.5 py-0.5 rounded font-bold text-[9px] ${
                    g.severity === 'block' ? 'bg-red-50 text-red-600 border border-red-200' : 'bg-amber-50 text-amber-600 border border-amber-200'
                  }`}>
                    {g.severity === 'block' ? t("aiworkbench.guardrails.forceBlock") : t("aiworkbench.guardrails.auditLog")}
                  </span>
                </div>

              </div>
            )}
          })})()}
          </div>
          {guardrails.length > guardPageSize && (
            <Pagination
              page={guardPage}
              pageSize={guardPageSize}
              total={guardrails.length}
              onPageChange={setGuardPage}
            />
          )}
        </div>

        {/* Right Column: Dynamic simulator */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col space-y-4`}>
          <div className={`border-b ${styles.cardBorder} pb-3 flex items-center justify-between`}>
            <div className="flex items-center gap-2">
              <span className="p-1 rounded bg-rose-50 text-rose-600">
                <Icon name="ShieldAlert" size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t("aiworkbench.guardrails.complianceSandbox")}</h3>
            </div>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase`}>AUDIT SIMULATOR</span>
          </div>

          <div className="space-y-1.5">
            <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t("aiworkbench.guardrails.auditTestInput")}</label>
            <textarea
              value={testInput}
              onChange={e => setTestInput(e.target.value)}
              rows={4}
              className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500 font-sans leading-relaxed ${styles.cardTextMuted} ${styles.cardBg}`}
              placeholder={t("aiworkbench.guardrails.auditPlaceholder")}
            />
          </div>

          <button
            onClick={handleRunSimulator}
            disabled={isSimulating || !testInput.trim()}
            className={`w-full py-2 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
              isSimulating ? 'opacity-70 cursor-not-allowed' : ''
            }`}
          >
            {isSimulating ? (
              <>
                <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                <span>{t("aiworkbench.guardrails.complianceTesting")}</span>
              </>
            ) : (
              <>
                <Icon name="ShieldCheck" size={13} />
                <span>{t("aiworkbench.guardrails.runAuditTest")}</span>
              </>
            )}
          </button>

          {sandboxTrace.length > 0 && (
            <div className="space-y-2">
              <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>{t("aiworkbench.guardrails.auditTrace")}</h4>
              <div className={`${styles.appBg} rounded-xl p-3 max-h-48 overflow-y-auto space-y-2 text-[10px] font-mono ${styles.cardTextMuted}`}>
                {sandboxTrace.map((log, idx) => (
                  <p key={idx} className="leading-relaxed">{log}</p>
                )}
              </div>
            </div>
          )}

          {sandboxResult && (
            <div className="space-y-2">
              <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>{t("aiworkbench.guardrails.maskingResult")}</h4>
              <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-3 text-xs space-y-3`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t("aiworkbench.guardrails.interceptStatus")}</span>
                  <span className="px-2 py-0.5 bg-red-100 text-red-700 border border-red-200 rounded-full font-bold text-[9px] inline-block mt-1">
                    {t("aiworkbench.guardrails.blocked")}
                  </span>
                </div>

                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t("aiworkbench.guardrails.triggeredViolation")}</span>
                  <div className="space-y-1 mt-1">
                    {sandboxResult.triggeredFilters.map((filter, idx) => (
                      <span key={idx} className={`block text-[10px] text-rose-600 font-mono font-bold ${styles.cardBg} px-2 py-0.5 border ${styles.cardBorder} rounded-md`}>
                        {filter}
                      </span>
                    )}
                  </div>
                </div>

                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t("aiworkbench.guardrails.maskedCompliance")}</span>
                  <p className={`${styles.cardBg} p-2.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} font-mono text-[10px] mt-1 leading-relaxed`}>
                    {sandboxResult.processedText}
                  </p>
                </div>
              </div>
            </div>
          )}

        </div>
      </div>
    </div>
  );
}
