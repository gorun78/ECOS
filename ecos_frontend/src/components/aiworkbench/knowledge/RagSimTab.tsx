/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { ThemeStyles } from '../../../components/ThemeContext';
import * as Icons from 'lucide-react';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface RagSimTabProps {
  styles: ThemeStyles;
  queryInput: string;
  setQueryInput: (v: string) => void;
  isRetrieving: boolean;
  handleRunRAG: () => Promise<void>;
  retrievedDocs: Array<{ title: string; type: string; snippet: string; score: number }>;
  ragPrompt: string;
  llmOutput: string;
}

export default function RagSimTab({
  styles,
  queryInput,
  setQueryInput,
  isRetrieving,
  handleRunRAG,
  retrievedDocs,
  ragPrompt,
  llmOutput,
}: RagSimTabProps) {
  const { t } = useLanguage();
  return (
    <div className="space-y-6">
      
      {/* Title */}
      <div className={`border-b ${styles.cardBorder} pb-3 space-y-1`}>
        <h2 className={`text-sm font-black ${styles.cardText}`}>{t('aiworkbench.knowledge.ragSim.title')} (Semantic Grounding Sandbox)</h2>
        <p className={`text-xs ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.ragSim.desc')}</p>
      </div>

      {/* Sandbox Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        
        {/* Left query column: 5 columns */}
        <div className="lg:col-span-5 space-y-4">
          
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <div className={`border-b ${styles.cardBorder} pb-2 flex items-center gap-2`}>
              <span className="p-1.5 rounded bg-blue-50 text-blue-600">
                <Icon name="Keyboard" size={13} />
              </span>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>{t('aiworkbench.knowledge.ragSim.step1')} (User Query)</h3>
            </div>

            <div className="space-y-2">
              <textarea
                value={queryInput}
                onChange={e => setQueryInput(e.target.value)}
                rows={3}
                className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs font-sans leading-relaxed ${styles.cardTextMuted} focus:outline-hidden focus:border-blue-500`}
                placeholder="{t('aiworkbench.knowledge.ragSim.queryPlaceholder')}"
              />
              
              {/* Pre-canned prompts */}
              <div className="space-y-1.5">
                <span className={`text-[9px] ${styles.cardTextMuted} font-extrabold uppercase block`}>{t('aiworkbench.knowledge.ragSim.recommendedTests')} (Closed-Loop Test Prompts):</span>
                <div className="flex flex-col gap-1">
                  {[
                    t('aiworkbench.knowledge.ragSim.test1'),
                    t('aiworkbench.knowledge.ragSim.test2'),
                    t('aiworkbench.knowledge.ragSim.test3')
                  ].map((p, idx) => (
                    <button
                      key={idx}
                      onClick={() => setQueryInput(p)}
                      className={`text-left px-2 py-1 ${styles.inputBg} border ${styles.cardBorder} hover:bg-blue-50 hover:border-blue-200 rounded-lg text-[10px] ${styles.cardTextMuted} truncate cursor-pointer transition-all`}
                    >
                      💡 {p}
                    </button>
                  )}
                </div>
              </div>
            </div>

            <button
              onClick={handleRunRAG}
              disabled={isRetrieving || !queryInput.trim()}
              className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                isRetrieving ? 'opacity-70 cursor-not-allowed' : ''
              }`}
            >
              {isRetrieving ? (
                <>
                  <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                  <span>{t('aiworkbench.knowledge.ragSim.retrieving')}</span>
                </>
              ) : (
                <>
                  <Icon name="Flame" size={13} />
                  <span>{t('aiworkbench.knowledge.ragSim.searchBtn')}</span>
                </>
              )}
            </button>
          </div>

          {/* Step 2: Retrieved Grounded Metadata chunks */}
          {retrievedDocs.length > 0 && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
              <div className={`border-b ${styles.cardBorder} pb-2 flex items-center justify-between`}>
                <div className={`flex items-center gap-1.5 font-bold ${styles.cardText} text-xs`}>
                  <Icon name="Layers" size={12} className="text-emerald-500" />
                  <span>{t('aiworkbench.knowledge.ragSim.step2')} (Grounding)</span>
                </div>
                <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>DORIS VEC MATCH</span>
              </div>

              <div className="space-y-2 max-h-56 overflow-y-auto">
                {retrievedDocs.map((doc, idx) => (
                  <div key={idx} className={`p-2 ${styles.inputBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <div className="flex items-center justify-between text-[10px]">
                      <span className={`font-bold ${styles.cardText}`}>{doc.title}</span>
                      <span className="px-1.5 bg-emerald-50 text-emerald-600 text-[9px] font-bold rounded-md">
                        Similarity: {(doc.score * 100).toFixed(0)}%
                      </span>
                    </div>
                    <p className={`text-[9px] ${styles.cardTextMuted} leading-relaxed font-sans`}>{doc.snippet}</p>
                  </div>
                )}
              </div>
            </div>
          )}

        </div>

        {/* Right prompt & model output column: 7 columns */}
        <div className="lg:col-span-7 space-y-4">
          
          {/* RAG prompt box */}
          {ragPrompt && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-2`}>
              <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5 text-indigo-600 border-b ${styles.cardBorder} pb-2`}>
                <Icon name="Sparkles" size={13} />
                <span>{t('aiworkbench.knowledge.ragSim.step3')} (RAG Prompt Context)</span>
              </h3>
              <div className={`${styles.appBg} ${styles.cardTextMuted} rounded-xl p-3 h-32 overflow-y-auto font-mono text-[9px] leading-relaxed`}>
                {ragPrompt}
              </div>
            </div>
          )}

          {/* Final model output response */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-sm space-y-3`}>
            <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5 text-emerald-600 border-b ${styles.cardBorder} pb-2`}>
              <Icon name="Bot" size={14} className="animate-bounce" />
              <span>{t('aiworkbench.knowledge.ragSim.step4')} (AI Output)</span>
            </h3>

            {isRetrieving ? (
              <div className={`py-8 text-center ${styles.cardTextMuted} space-y-2`}>
                <Icon name="RefreshCw" size={24} className={`animate-spin ${styles.cardTextMuted} mx-auto`} />
                <p className="text-xs font-medium">{t('aiworkbench.knowledge.ragSim.reasoning')}</p>
              </div>
            ) : llmOutput ? (
              <div className={`${styles.inputBg} border ${styles.cardBorder} p-4 rounded-xl ${styles.cardTextMuted} text-[11px] font-sans leading-relaxed whitespace-pre-wrap`}>
                {llmOutput}
              </div>
            ) : (
              <div className={`py-8 text-center ${styles.cardTextMuted} space-y-1`}>
                <Icon name="Bot" size={24} className={`${styles.cardTextMuted} mx-auto`} />
                <p>{t('aiworkbench.knowledge.ragSim.idle')}</p>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('aiworkbench.knowledge.ragSim.idleHint')}</p>
              </div>
            )}
          </div>

        </div>

      </div>

    </div>
  );
}