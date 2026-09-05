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

interface VectorIndexTabProps {
  styles: ThemeStyles;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
  embeddingModel: string;
  setEmbeddingModel: (v: string) => void;
  chunkSize: number;
  setChunkSize: (v: number) => void;
  overlap: number;
  setOverlap: (v: number) => void;
  isSyncingAll: boolean;
  handleSyncAll: () => Promise<void>;
  syncLogs: string[];
  vectorChunks: any[];
  pgvectorSql: string;
  milvusCode: string;
  persistenceTab: 'pgvector' | 'milvus';
  setPersistenceTab: (v: 'pgvector' | 'milvus') => void;
}

export default function VectorIndexTab({
  styles,
  showToast,
  embeddingModel,
  setEmbeddingModel,
  chunkSize,
  setChunkSize,
  overlap,
  setOverlap,
  isSyncingAll,
  handleSyncAll,
  syncLogs,
  vectorChunks,
  pgvectorSql,
  milvusCode,
  persistenceTab,
  setPersistenceTab,
}: VectorIndexTabProps) {
  const { t } = useLanguage();
  return (
    <div className="space-y-6">
      
      {/* Title */}
      <div className={`border-b ${styles.cardBorder} pb-3 space-y-1`}>
        <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
          <span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600">
            <Icon name="Binary" size={16} />
          </span>
          <span>{t('aiworkbench.knowledge.vectorIndex.title')} (text-embedding-004)</span>
        </h2>
        <p className={`text-xs ${styles.cardTextMuted}`}>
          {t('aiworkbench.knowledge.vectorIndex.desc')}
        </p>
      </div>

      {/* Config & Monitor Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        
        {/* Left Column: Config Panel & Progress (5 cols) */}
        <div className="lg:col-span-5 space-y-4">
          
          {/* Config Panel */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2 flex items-center gap-1.5`}>
              <Icon name="Settings" size={13} className={`${styles.cardTextMuted}`} />
              <span>{t('aiworkbench.knowledge.vectorIndex.chunkConfig')} (Chunking Config)</span>
            </h3>

            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t('aiworkbench.knowledge.vectorIndex.embedModel')} (Embedding Model)</label>
                <select
                  value={embeddingModel}
                  onChange={e => setEmbeddingModel(e.target.value)}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono ${styles.cardBg} ${styles.cardText}`}
                >
                  <option value="text-embedding-004">Google Text-Embedding-004 ({t('aiworkbench.knowledge.vectorIndex.dimUnit')})</option>
                </select>
              </div>

              <div className="space-y-1">
                <div className="flex justify-between items-center">
                  <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t('aiworkbench.knowledge.vectorIndex.chunkSize')} (Chunk Size)</label>
                  <span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.appBg} px-1.5 py-0.5 rounded`}>{chunkSize} {t('aiworkbench.knowledge.vectorIndex.charUnit')}</span>
                </div>
                <input
                  type="range"
                  min={128}
                  max={1024}
                  step={64}
                  value={chunkSize}
                  onChange={e => setChunkSize(Number(e.target.value))}
                  className={`w-full h-1.5 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-indigo-600`}
                />
              </div>

              <div className="space-y-1">
                <div className="flex justify-between items-center">
                  <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t('aiworkbench.knowledge.vectorIndex.overlap')} (Overlap Size)</label>
                  <span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.appBg} px-1.5 py-0.5 rounded`}>{overlap} {t('aiworkbench.knowledge.vectorIndex.charUnit')}</span>
                </div>
                <input
                  type="range"
                  min={10}
                  max={200}
                  step={10}
                  value={overlap}
                  onChange={e => setOverlap(Number(e.target.value))}
                  className={`w-full h-1.5 ${styles.inputBg} rounded-lg appearance-none cursor-pointer accent-indigo-600`}
                />
              </div>
            </div>

            <button
              onClick={handleSyncAll}
              disabled={isSyncingAll}
              className={`w-full py-2 styles.accentBg styles.accentHover text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                isSyncingAll ? 'opacity-75 cursor-not-allowed' : ''
              }`}
            >
              {isSyncingAll ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  <span>{t('aiworkbench.knowledge.vectorIndex.syncing')}</span>
                </>
              ) : (
                <>
                  <Icon name="Cpu" size={13} />
                  <span>{t('aiworkbench.knowledge.vectorIndex.syncBtn')} (Sync)</span>
                </>
              )}
            </button>
          </div>

          {/* Live Monitor Console */}
          <div className={`${styles.cardBg} rounded-xl p-4 border ${styles.cardBorder} space-y-2 shadow-inner`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} font-mono tracking-wider flex items-center gap-1.5`}>
                <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                SYSTEM PIPELINE MONITOR
              </span>
              <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>NODE v20.10.0</span>
            </div>
            <div className="font-mono text-[9.5px] leading-relaxed h-52 overflow-y-auto space-y-1 scrollbar-thin scrollbar-thumb-slate-800">
              {syncLogs.length > 0 ? (
                syncLogs.map((log, idx) => {
                  let textClass = `${styles.cardTextMuted}`;
                  if (log.includes('🚨')) textClass = "text-rose-400 font-bold";
                  else if (log.includes('✅')) textClass = "text-emerald-400";
                  else if (log.includes('🔄')) textClass = "text-amber-400";
                  return (
                    <div key={idx} className={textClass}>
                      {log}
                    </div>
                  );
                })
              ) : (
                <div className={`${styles.cardTextMuted} italic`}>{t('aiworkbench.knowledge.vectorIndex.waiting')}</div>
              )}
            </div>
          </div>

        </div>

        {/* Right Column: Statistics, Real-time Chunks, & Persistence Scripts (7 cols) */}
        <div className="lg:col-span-7 space-y-4">
          
          {/* Stats row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
              <div>
                <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>CUMULATIVE CHUNKS</span>
                <span className={`text-base font-black ${styles.cardText} font-mono`}>{vectorChunks.length} chunks</span>
              </div>
              <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
                <Icon name="Layers" size={14} />
              </span>
            </div>

            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
              <div>
                <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>VECTOR DIMENSION</span>
                <span className={`text-base font-black ${styles.cardText} font-mono`}>768-Dim</span>
              </div>
              <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                <Icon name="Binary" size={14} />
              </span>
            </div>

            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}>
              <div>
                <span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>INDEX INTEGRITY</span>
                <span className="text-base font-black text-emerald-600 font-mono">100% HEALTH</span>
              </div>
              <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
                <Icon name="CheckCircle2" size={14} />
              </span>
            </div>
          </div>

          {/* Real-time Document Chunks Browser */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                <Icon name="Settings" size={13} className={`${styles.cardTextMuted}`} />
                <span>{t('aiworkbench.knowledge.vectorIndex.chunkBrowser')} (Vector Chunk Browser)</span>
              </h3>
              <span className={`text-[10px] ${styles.cardTextMuted}`}>数量: {vectorChunks.length} 个分片</span>
            </div>

            <div className="space-y-2.5 max-h-60 overflow-y-auto">
              {vectorChunks.length > 0 ? (
                vectorChunks.map((chunk, idx) => (
                  <div key={idx} className={`border ${styles.cardBorder} rounded-lg p-2.5 ${styles.inputBg} space-y-1.5`}>
                    <div className="flex items-center justify-between text-[10px]">
                      <div className="flex items-center gap-1.5">
                        <span className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded-md font-bold text-[9px]">
                          {chunk.source === 'security' ? t('aiworkbench.knowledge.vectorIndex.securityTag') : chunk.source === 'ontology' ? t('aiworkbench.knowledge.vectorIndex.ontologyTag') : t('aiworkbench.knowledge.vectorIndex.integrationTag')}
                        </span>
                        <span className={`font-bold ${styles.cardTextMuted} truncate max-w-[200px]`}>{chunk.title}</span>
                      </div>
                      <span className={`${styles.cardTextMuted} font-mono text-[9px]`}>Chunk #{idx + 1} ({chunk.chunkSize} chars)</span>
                    </div>
                    
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans ${styles.cardBg} border ${styles.cardBorder} p-2 rounded-md`}>
                      {chunk.text}
                    </p>

                    <div className={`pt-1 border-t ${styles.cardBorder}/60 flex flex-col gap-0.5`}>
                      <span className={`text-[8.5px] font-bold ${styles.cardTextMuted} font-mono`}>GOOGLE TEXT-EMBEDDING-004 (768-DIM VECTOR):</span>
                      <span className="text-[8.5px] text-indigo-600 bg-indigo-50/50 p-1 rounded font-mono truncate">
                        {chunk.vectorPreview}
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <div className={`py-8 text-center ${styles.cardTextMuted} space-y-1`}>
                  <Icon name="FolderClosed" size={24} className={`${styles.cardTextMuted} mx-auto`} />
                  <p className="text-xs">{t('aiworkbench.knowledge.vectorIndex.emptyChunks')}</p>
                </div>
              )}
            </div>
          </div>

          {/* Local Vector DB Persistence Code Generator */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
            <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-2 gap-2`}>
              <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}>
                <Icon name="Database" size={13} className="text-indigo-600" />
                <span>{t('aiworkbench.knowledge.vectorIndex.persistenceTitle')} (Persistence Sandbox)</span>
              </h3>
              
              {/* Segmented control */}
              <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.cardBorder}/60 shrink-0`}>
                <button
                  onClick={() => setPersistenceTab('pgvector')}
                  className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${
                    persistenceTab === 'pgvector' 
                      ? 'styles.cardBg text-indigo-700 shadow-xs' 
                      : 'styles.cardTextMuted hover:styles.cardText'
                  }`}
                >
                  PGVector (SQL)
                </button>
                <button
                  onClick={() => setPersistenceTab('milvus')}
                  className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${
                    persistenceTab === 'milvus' 
                      ? 'styles.cardBg text-indigo-700 shadow-xs' 
                      : 'styles.cardTextMuted hover:styles.cardText'
                  }`}
                >
                  Milvus (Node.js)
                </button>
              </div>
            </div>

            <div className="space-y-2">
              <div className={`flex justify-between items-center text-[10px] ${styles.cardTextMuted}`}>
                <span>{persistenceTab === 'pgvector' ? '🐘 PostgreSQL + PGVector ' + t('aiworkbench.knowledge.vectorIndex.persistenceDesc').split('pgvector /')[0].trim() + 'pgvector' : '⚡ Milvus ' + t('aiworkbench.knowledge.vectorIndex.persistenceDesc').split('Milvus')[1] || ''}</span>
                <button
                  onClick={() => {
                    const code = persistenceTab === 'pgvector' ? pgvectorSql : milvusCode;
                    if (code) {
                      navigator.clipboard.writeText(code);
                      showToast?.('success', t('aiworkbench.knowledge.vectorIndex.copySuccess'));
                    }
                  }}
                  className="text-indigo-600 hover:text-indigo-800 font-bold flex items-center gap-1 cursor-pointer"
                >
                  <Icon name="Copy" size={11} />
                  <span>{t('aiworkbench.knowledge.vectorIndex.copyBtn')}</span>
                </button>
              </div>

              <div className={`${styles.appBg} ${styles.cardTextMuted} rounded-xl p-3 h-48 overflow-y-auto font-mono text-[9.5px] leading-relaxed border styles.cardBorder`}>
                {persistenceTab === 'pgvector' ? (
                  pgvectorSql ? (
                    <pre className="whitespace-pre">{pgvectorSql}</pre>
                  ) : (
                    <div className={`${styles.cardTextMuted} italic py-12 text-center`}>{t('aiworkbench.knowledge.vectorIndex.noPgvectorCode')}</div>
                  )
                ) : (
                  milvusCode ? (
                    <pre className="whitespace-pre">{milvusCode}</pre>
                  ) : (
                    <div className={`${styles.cardTextMuted} italic py-12 text-center`}>{t('aiworkbench.knowledge.vectorIndex.noMilvusCode')}</div>
                  )
                )}
              </div>
            </div>
          </div>

        </div>

      </div>

    </div>
  );
}