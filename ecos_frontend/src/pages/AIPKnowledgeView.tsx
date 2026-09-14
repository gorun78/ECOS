/**
 * AIPKnowledgeView — standalone AIP Knowledge base page.
 * RAG search subtab extracted from ceos_new AIPWorkbench/KnowledgeView,
 * adapted for ECOS: lucide-react icons, Bearer auth, /api/v1/ endpoint prefix.
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import {
  GitBranch, Keyboard, Flame, Layers, Sparkles, Bot, RefreshCw,
  Search, Database, Shield, Network, Lightbulb, Loader2
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { useToast } from '../components/common/Toast';

// ═══════════════════ API helpers ═══════════════════

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) headers.Authorization = ['Bearer', token].join(' ');
  return headers;
}

// ═══════════════════ Component ═══════════════════

export default function AIPKnowledgeView() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();
  const [queryInput, setQueryInput] = useState('');
  const [isRetrieving, setIsRetrieving] = useState(false);
  const [retrievedDocs, setRetrievedDocs] = useState<Array<{ title: string; type: string; snippet: string; score: number }>>([]);
  const [ragPrompt, setRagPrompt] = useState<string>('');
  const [llmOutput, setLlmOutput] = useState<string>('');

  const applyQuery = (key: string) => setQueryInput(t(key));

  const handleRunRAG = async () => {
    if (!queryInput.trim()) return;
    setIsRetrieving(true);
    setRetrievedDocs([]);
    setRagPrompt('');
    setLlmOutput('');

    try {
      const response = await fetch('/api/v1/knowledge/query', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ query: queryInput })
      });
      const data = await response.json();

      if (data.success || data.code === 0 || data.code === 200) {
      const payload = data.data || data;
      setRetrievedDocs((payload.groundedDocs || []).map((doc: any) => ({
        title: doc.title,
        type: doc.title?.includes('Security') ? 'security' : doc.title?.includes('Ontology') ? 'ontology' : 'integration',
        snippet: doc.snippet || t('common.na'),
        score: doc.score,
      })));
        setRagPrompt(payload.promptGrounded || '');
        setLlmOutput(payload.answer || '');
      } else {
        showToast('error', data.error || t('aipKnowledge.errRetrieve'));
      }
    } catch (e) {
      showToast('error', t('aipKnowledge.errLlm'));
    } finally {
      setIsRetrieving(false);
    }
  };

  const stepHeader = (icon: React.ReactNode, label: string, badge?: string) => (
    <div className={`border-b ${styles.cardBg} pb-2 flex items-center justify-between ${styles.divider}`}>
      <div className="flex items-center gap-1.5 font-bold text-xs">
        {icon}
        <span>{label}</span>
      </div>
      {badge && <span className={`text-[9px] ${styles.muted} font-mono`}>{badge}</span>}
    </div>
  );

  const card = `rounded-xl p-4 ${styles.cardBg} ${styles.cardBorder} shadow-sm space-y-3`;

  return (
    <div className={`flex h-full select-none text-xs overflow-hidden ${styles.appBg}`}>

      {/* Main Panel — full-width RAG search page */}
      <div className="flex-1 p-6 overflow-y-auto h-full">

        {/* Header */}
        <div className={`mb-6 border-b pb-4 space-y-1 ${styles.divider}`}>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg" style={{ backgroundColor: 'var(--accent)' }}>
              <Search size={15} className="text-white" />
            </span>
            <h1 className={`text-base font-black ${styles.text}`}>{t('aipKnowledge.title')}</h1>
          </div>
          <p className={`text-xs max-w-2xl leading-relaxed ${styles.muted}`}>
            {t('aipKnowledge.desc')}
          </p>
        </div>

        {/* Four-step flow */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">

          {/* Left column: Step 1 + Step 2 */}
          <div className="lg:col-span-5 space-y-4">

            {/* Step 1: Query Input */}
            <div className={card}>
              {stepHeader(
                <span className="p-1.5 rounded bg-blue-500/10 text-blue-600 dark:text-blue-400">
                  <Keyboard size={13} />
                </span>,
                t('aipKnowledge.step1')
              )}

              <div className="space-y-2">
                <textarea
                  value={queryInput}
                  onChange={e => setQueryInput(e.target.value)}
                  rows={3}
                  className={`w-full px-3 py-2 border rounded-lg text-xs font-sans leading-relaxed focus:outline-none transition ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  placeholder={t('aipKnowledge.placeholder')}
                />

                <div className="space-y-1.5">
                  <span className={`text-[9px] ${styles.muted} font-extrabold uppercase block`}>{t('aipKnowledge.suggested')}</span>
                  <div className="flex flex-col gap-1">
                    {['aipKnowledge.suggested1', 'aipKnowledge.suggested2', 'aipKnowledge.suggested3'].map((key, idx) => (
                      <button
                        key={idx}
                        onClick={() => applyQuery(key)}
                        className={`text-left px-2 py-1 border rounded-lg text-[10px] truncate cursor-pointer transition-all ${styles.cardBg} ${styles.cardBorder} ${styles.muted} hover:opacity-80`}
                      >
                        <Lightbulb size={10} className="inline mr-1 text-amber-500" />
                        {t(key)}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <button
                onClick={handleRunRAG}
                disabled={isRetrieving || !queryInput.trim()}
                className="w-full py-2 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer disabled:opacity-70 disabled:cursor-not-allowed"
                style={{ backgroundColor: 'var(--accent)' }}
              >
                {isRetrieving ? (
                  <>
                    <Loader2 size={13} className="animate-spin" />
                    <span>{t('aipKnowledge.retrieving')}</span>
                  </>
                ) : (
                  <>
                    <Flame size={13} />
                    <span>{t('aipKnowledge.run')}</span>
                  </>
                )}
              </button>
            </div>

            {/* Step 2: Retrieved Documents */}
            {retrievedDocs.length > 0 && (
              <div className={card}>
                {stepHeader(
                  <Layers size={12} className="text-emerald-500" />,
                  t('aipKnowledge.step2'),
                  'VECTOR MATCH'
                )}

                <div className="space-y-2 max-h-56 overflow-y-auto">
                  {retrievedDocs.map((doc, idx) => (
                    <div key={idx} className={`p-2 ${styles.cardBg} ${styles.cardBorder} border rounded-lg space-y-1`}>
                      <div className="flex items-center justify-between text-[10px]">
                        <span className={`font-bold flex items-center gap-1 ${styles.text}`}>
                          {doc.type === 'security' ? <Shield size={10} className="text-rose-500" /> :
                           doc.type === 'ontology' ? <Network size={10} className="text-indigo-500" /> :
                           <Database size={10} className="text-emerald-500" />}
                          {doc.title}
                        </span>
                        <span className="px-1.5 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-[9px] font-bold rounded-md" title={t('aipKnowledge.score')}>
                          {(doc.score * 100).toFixed(0)}%
                        </span>
                      </div>
                      <p className={`text-[9px] leading-relaxed font-sans ${styles.muted}`}>{doc.snippet}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

          </div>

          {/* Right column: Step 3 + Step 4 */}
          <div className="lg:col-span-7 space-y-4">

            {/* Step 3: RAG Prompt */}
            {ragPrompt && (
              <div className={card}>
                <div className={`flex items-center gap-1.5 font-bold text-xs border-b pb-2 ${styles.divider}`}>
                  <Sparkles size={13} className="text-indigo-500" />
                  <span className="text-indigo-600 dark:text-indigo-400">{t('aipKnowledge.step3')}</span>
                </div>
                <div className={`rounded-xl p-3 ${styles.inputBg} ${styles.inputText} h-32 overflow-y-auto font-mono text-[9px] leading-relaxed`}>
                  {ragPrompt}
                </div>
              </div>
            )}

            {/* Step 4: LLM Output */}
            <div className={card}>
              <div className={`flex items-center gap-1.5 font-bold text-xs border-b pb-2 ${styles.divider}`}>
                <Bot size={14} className="animate-bounce text-emerald-500" />
                <span className="text-emerald-600 dark:text-emerald-400">{t('aipKnowledge.step4')}</span>
              </div>

              {isRetrieving ? (
                <div className={`py-8 text-center space-y-2 ${styles.muted}`}>
                  <RefreshCw size={24} className="animate-spin mx-auto opacity-40" />
                  <p className="text-xs font-medium">{t('aipKnowledge.thinking')}</p>
                </div>
              ) : llmOutput ? (
                <div className={`p-4 rounded-xl border ${styles.cardBg} ${styles.cardBorder} text-[11px] font-sans leading-relaxed whitespace-pre-wrap ${styles.text}`}>
                  {llmOutput}
                </div>
              ) : (
                <div className={`py-8 text-center space-y-1 ${styles.muted}`}>
                  <Bot size={24} className={`${styles.muted} mx-auto opacity-30`} />
                  <p className={styles.text}>{t('aipKnowledge.idleTitle')}</p>
                  <p className="text-[10px]">{t('aipKnowledge.idleHint')}</p>
                </div>
              )}
            </div>

          </div>

        </div>

      </div>
    </div>
  );
}
