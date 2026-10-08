/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Guardrail Rules + text-redaction simulator tab of AiGuardrailsView.
// Extracted verbatim by H6-T4.

import React from 'react';
import { AIPGuardrail } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { Icon } from './AiGuardrailsShared';

interface GuardrailsTabProps {
  guardrails: AIPGuardrail[];
  onToggle: (id: string) => void;
  testInput: string;
  onTestInputChange: (v: string) => void;
  isSimulating: boolean;
  onRunSimulator: () => void;
  sandboxTrace: string[];
  sandboxResult: {
    status: 'passed' | 'warned' | 'blocked';
    processedText: string;
    triggeredFilters: string[];
  } | null;
}

export default function AiGuardrailsGuardrailsTab({
  guardrails,
  onToggle,
  testInput,
  onTestInputChange,
  isSimulating,
  onRunSimulator,
  sandboxTrace,
  sandboxResult,
}: GuardrailsTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Left Column: Guardrail cards config */}
        <div className="lg:col-span-2 space-y-4">
          <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>{t('aiworkbench.gr.rulesConfig')} ({guardrails.length})</h3>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {guardrails.map(g => (
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
                      onClick={() => onToggle(g.id)}
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
                    {g.severity === 'block' ? t('aiworkbench.gr.sevBlock') : t('aiworkbench.gr.sevRecord')}
                  </span>
                </div>

              </div>
            ))}
          </div>
        </div>

        {/* Right Column: Dynamic simulator */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col space-y-4`}>
          <div className={`border-b ${styles.cardBorder} pb-3 flex items-center justify-between`}>
            <div className="flex items-center gap-2">
              <span className="p-1 rounded bg-rose-50 text-rose-600">
                <Icon name="ShieldAlert" size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('aiworkbench.gr.sandboxTitle')}</h3>
            </div>
            <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase`}>AUDIT SIMULATOR</span>
          </div>

          <div className="space-y-1.5">
            <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t('aiworkbench.gr.inputLabel')}</label>
            <textarea
              value={testInput}
              onChange={e => onTestInputChange(e.target.value)}
              rows={4}
              className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500 font-sans leading-relaxed ${styles.cardTextMuted} ${styles.cardBg}`}
              placeholder={t('aiworkbench.gr.inputPlaceholder')}
            />
          </div>

          <button
            type="button"
            onClick={onRunSimulator}
            disabled={isSimulating || !testInput.trim()}
            className={`w-full py-2 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
              isSimulating ? 'opacity-70 cursor-not-allowed' : ''
            }`}
          >
            {isSimulating ? (
              <>
                <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                <span>{t('aiworkbench.gr.simulating')}</span>
              </>
            ) : (
              <>
                <Icon name="ShieldCheck" size={13} />
                <span>{t('aiworkbench.gr.runSimulator')}</span>
              </>
            )}
          </button>

          {sandboxTrace.length > 0 && (
            <div className="space-y-2">
              <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>{t('aiworkbench.gr.traceTitle')}</h4>
              <div className={`${styles.appBg} rounded-xl p-3 max-h-48 overflow-y-auto space-y-2 text-[10px] font-mono ${styles.cardTextMuted}`}>
                {sandboxTrace.map((log, idx) => (
                  <p key={idx} className="leading-relaxed">{log}</p>
                ))}
              </div>
            </div>
          )}

          {sandboxResult && (
            <div className="space-y-2">
              <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>{t('aiworkbench.gr.resultTitle')}</h4>
              <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-3 text-xs space-y-3`}>
                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t('aiworkbench.gr.statusLabel')}</span>
                  <span className="px-2 py-0.5 bg-red-100 text-red-700 border border-red-200 rounded-full font-bold text-[9px] inline-block mt-1">
                    {t('aiworkbench.gr.statusBlocked')}
                  </span>
                </div>

                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t('aiworkbench.gr.triggeredLabel')}</span>
                  <div className="space-y-1 mt-1">
                    {sandboxResult.triggeredFilters.map((filter, idx) => (
                      <span key={idx} className={`block text-[10px] text-rose-600 font-mono font-bold ${styles.cardBg} px-2 py-0.5 border ${styles.cardBorder} rounded-md`}>
                        {filter}
                      </span>
                    ))}
                  </div>
                </div>

                <div>
                  <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>{t('aiworkbench.gr.compliantLabel')}</span>
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
