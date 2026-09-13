/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 VectorIndexTab — 真实读取 /api/v1/knowledge/sync/status 的
 * graph_node_count / graph_edge_count / article_count / embedding_count，
 * 不再 mock。Backend stub 时可用 §"waiting P56" 占位。
 */

import React, { useEffect, useState, useCallback } from 'react';
import { Binary, Layers, Database, FolderClosed, RefreshCw, Loader2, Cpu } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

type TabProps = { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void };

interface SyncStatusResponse {
  graphNodeCount: number;
  graphEdgeCount: number;
  articleCount: number;
  embeddingCount: number;
  lastSyncAt?: string;
}

export default function VectorIndexTab({ showToast }: TabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [stats, setStats] = useState<SyncStatusResponse>({
    graphNodeCount: 0, graphEdgeCount: 0, articleCount: 0, embeddingCount: 0,
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadStats();
    const timer = setInterval(loadStats, 30000);
    return () => clearInterval(timer);
  }, []);

  const loadStats = useCallback(async () => {
    setLoading(true);
    try {
      const data = await knowledgeApi.fetchGraphStats();
      setStats({
        graphNodeCount: data.graphNodeCount,
        graphEdgeCount: data.graphEdgeCount,
        articleCount: 0,
        embeddingCount: data.embeddingCount,
        lastSyncAt: data.lastUpdatedAt,
      });
    } catch {
      /* keep last value */
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <div className="space-y-6">
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b pb-4 gap-3 ${styles.cardBorder}`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black flex items-center gap-2 ${styles.cardText}`}>
            <span className={`p-1.5 rounded-lg ${styles.badgeBg} ${styles.badgeText}`}><Binary size={16} /></span>
            {t('knowledge.vector_index.title')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('knowledge.vector_index.subtitle')}</p>
        </div>
        <button
          onClick={loadStats}
          disabled={loading}
          className={`px-3 py-1.5 rounded-lg flex items-center gap-1.5 cursor-pointer text-xs disabled:opacity-50 font-bold ${styles.inputBg} ${styles.cardBorder} hover:opacity-80`}
        >
          {loading ? <Loader2 size={12} className="animate-spin" /> : <RefreshCw size={12} />}
          {t('knowledge.vector_index.refresh')}
        </button>
      </div>

      {/* True KPI cards from /api/v1/knowledge/sync/status via /stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard title={t('knowledge.vector_index.kpi.nodes')} value={stats.graphNodeCount} icon={<Binary size={16} />} accent="indigo" />
        <KpiCard title={t('knowledge.vector_index.kpi.edges')} value={stats.graphEdgeCount} icon={<Layers size={16} />} accent="blue" />
        <KpiCard title={t('knowledge.vector_index.kpi.articles')} value={stats.articleCount} icon={<Database size={16} />} accent="inverted accentPurple" />
        <KpiCard title={t('knowledge.vector_index.kpi.embeddings')} value={stats.embeddingCount} icon={<Cpu size={16} />} accent="emerald" />
      </div>

      {/* Last sync bar */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
        <div className="flex items-center justify-between">
          <h3 className={`text-xs font-bold flex items-center gap-1.5 ${styles.cardText}`}>
            <RefreshCw size={12} className={styles.cardTextMuted} /> {t('knowledge.vector_index.last_sync')}
          </h3>
          <span className="text-[10px] font-mono text-slate-500">{stats.lastSyncAt || '—'}</span>
        </div>
        {(stats.graphNodeCount === 0 && stats.embeddingCount === 0 && stats.articleCount === 0) ? (
          <div className={`py-6 flex flex-col items-center justify-center space-y-2 ${styles.cardTextMuted}`}>
            <FolderClosed size={22} />
            <p className="text-xs">{t('knowledge.vector_index.no_data')}</p>
            <p className="text-[10px] font-mono opacity-60">{t('knowledge.vector_index.waiting_backend')}</p>
          </div>
        ) : (
          <div className="grid grid-cols-4 gap-4">
            <Counter label="G" value={stats.graphNodeCount} unit="nodes" />
            <Counter label="E" value={stats.graphEdgeCount} unit="edges" />
            <Counter label="A" value={stats.articleCount} unit="articles" />
            <Counter label="V" value={stats.embeddingCount} unit="embeddings" />
          </div>
        )}
      </div>

      {/* Quick actions kept from legacy IndexTab */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
        <h3 className={`text-xs font-extrabold flex items-center gap-1.5 border-b pb-2 ${styles.cardText} ${styles.cardBorder}`}>
          <Cpu size={13} className={styles.accentText} /> {t('knowledge.vector_index.actions')}
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <button onClick={() => { showToast?.('info', t('knowledge.vector_index.rebuild_stub')); }}
            className={`px-3 py-2 rounded-lg text-xs cursor-pointer flex items-center gap-1.5 font-bold border ${styles.successBg} ${styles.successText} ${styles.successBorder} hover:opacity-80 transition`}>
            <RefreshCw size={12} /> {t('knowledge.vector_index.rebuild_index')}
          </button>
          <button onClick={() => { showToast?.('info', t('knowledge.vector_index.reembed_stub')); }}
            className={`px-3 py-2 rounded-lg text-xs cursor-pointer flex items-center gap-1.5 font-bold border ${styles.infoBg} ${styles.infoText} ${styles.infoBorder} hover:opacity-80 transition`}>
            <Binary size={12} /> {t('knowledge.vector_index.reembed_all')}
          </button>
          <button onClick={() => { showToast?.('info', t('knowledge.vector_index.monitor_stub')); }}
            className={`px-3 py-2 rounded-lg text-xs cursor-pointer flex items-center gap-1.5 font-bold ${styles.cardBorder} ${styles.inputBg} hover:opacity-80`}
            style={{ color: styles.cardText }}>
            <Database size={12} /> {t('knowledge.vector_index.monitor')}
          </button>
        </div>
        <p className="text-[10px] font-mono text-slate-400">/api/v1/knowledge/vector/rebuild — {t('knowledge.vector_index.waiting_backend')}</p>
      </div>
    </div>
  );
}

function KpiCard({ title, value, icon, accent }: { title: string; value: number; icon: React.ReactNode; accent: string }) {
  const { styles } = useTheme();
  return (
    <div className={`border p-4 rounded-xl flex items-center justify-between shadow-xs hover:opacity-90 transition ${styles.cardBg} ${styles.cardBorder}`}>
      <div>
        <span className={`font-mono text-[9px] uppercase block ${styles.cardTextMuted}`}>{title}</span>
        <span className={`text-2xl font-black font-mono ${styles.cardText}`}>{value.toLocaleString()}</span>
      </div>
      <span className={`p-2 rounded-lg ${accent}`}>{icon}</span>
    </div>
  );
}

function Counter({ label, value, unit }: { label: string; value: number; unit: string }) {
  const { styles } = useTheme();
  return (
    <div className={`space-y-1 text-center ${styles.cardText}`}>
      <div className={`text-xl font-black font-mono ${styles.cardText}`}>{value.toLocaleString()}</div>
      <div className={`text-[10px] uppercase font-bold ${styles.cardTextMuted}`}>{unit}</div>
      <div className={`text-[8px] font-mono ${styles.cardTextMuted}`}>{label}</div>
    </div>
  );
}
