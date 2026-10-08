import React, { useState, useCallback, useEffect } from 'react';
import { Database, Lightbulb, Eye, RefreshCw, ArrowRight } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme, type ThemeStyles } from '../../../components/ThemeContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge, { type BadgeVariant } from '../../../components/common/Badge';
import type { Belief } from './types';

type TokenKey = keyof ThemeStyles & string;

// 主题合规（§七.1 / design-05 B.9-4）：置信度档位色 → Badge 语义变体（success/warning/danger）。
const STATUS_BADGE: Record<string, BadgeVariant> = {
  high: 'success',
  mid: 'warning',
  low: 'danger',
};

const STATUS_LABELS: Record<string, string> = { high: '高', mid: '中', low: '低' };

const TRACE_NODES = ['D', 'I', 'K', 'Evidence', 'Hypothesis', 'Belief', 'C', 'W'];

interface EvolutionItem { time?: string; action?: string; variable?: string; detail?: string; }

export default function cognitiveState() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [counts] = useState({ evidence: 184, hypothesis: 27, belief: 34 });
  const [beliefs, setBeliefs] = useState<Belief[]>([]);
  const [loading, setLoading] = useState(true);
  const [evolution, setEvolution] = useState<EvolutionItem[]>([]);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [beliefsRes, reviewsRes] = await Promise.allSettled([
        cognitiveEngineApi.fetchBeliefs(),
        cognitiveEngineApi.fetchMentalReviews(),
      ]);
      if (beliefsRes.status === 'fulfilled') {
        const data: any = beliefsRes.value;
        const list: Belief[] = Array.isArray(data) ? data : ((data as any)?.data ?? (data as any)?.records ?? []);
        setBeliefs(list);
      }
      if (reviewsRes.status === 'fulfilled') {
        const data: any = reviewsRes.value;
        const items: EvolutionItem[] = Array.isArray(data) ? data : ((data as any)?.data ?? (data as any)?.records ?? []) as EvolutionItem[];
        setEvolution(items);
      }
    } catch (e) {
      showToastGlobal('error', `${t('state.loadFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData().finally(() => setRefreshing(false));
  };

  const metricCards: ReadonlyArray<{ key: string; icon: typeof Database; colorKey: TokenKey; labelKey: string; label: string; count: number }> = [
    { key: 'evidence', icon: Database, colorKey: 'accentText', labelKey: 'state.metric.evidence', label: 'Evidence', count: counts.evidence },
    { key: 'hypothesis', icon: Lightbulb, colorKey: 'warningText', labelKey: 'state.metric.hypothesis', label: 'Hypothesis', count: counts.hypothesis },
    { key: 'belief', icon: Eye, colorKey: 'successText', labelKey: 'state.metric.belief', label: 'Belief', count: counts.belief },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('state.title')}</h1>
        <Button variant="primary" size="md" onClick={handleRefresh} disabled={refreshing} aria-label={t('state.refresh')}>
          <RefreshCw className={`w-3.5 h-3.5 inline mr-1 ${refreshing ? 'animate-spin' : ''}`} aria-hidden />
          {t('state.refresh')}
        </Button>
      </div>

      {/* Metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4" role="group" aria-label={t('state.metric.evidence') + ' / ' + t('state.metric.hypothesis') + ' / ' + t('state.metric.belief')}>
        {metricCards.map(m => (
          <div key={m.key} className={`${styles.appBg} border ${styles.cardBorder} rounded-lg p-4`} role="status" aria-label={`${t(m.labelKey)}: ${m.count}`}>
            <div className="flex items-center gap-2 mb-2">
              <m.icon className={`w-4 h-4 ${styles[m.colorKey]}`} aria-hidden />
              <span className={`text-xs ${styles.muted}`}>{t(m.labelKey)}</span>
            </div>
            <p className={`text-2xl font-bold ${styles.cardText}`}>{m.count}</p>
          </div>
        ))}
      </div>

      {/* Belief table */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-x-auto`}>
        <div className="px-4 pt-4 pb-2">
          <h2 className={`text-sm font-medium ${styles.cardText}`}>{t('state.beliefTable')}</h2>
        </div>
        <table className="w-full text-sm" aria-label={t('state.beliefTable')}>
          <caption className="sr-only">{t('state.beliefTable')}</caption>
          <thead>
            <tr className={`border-b ${styles.cardBorder} text-left text-xs ${styles.muted}`}>
              <th scope="col" className="px-4 py-2 font-medium">{t('state.col.proposition')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('state.col.support')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('state.col.status')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('state.col.evidenceChange')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('state.col.updated')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className={`px-4 py-6 text-center ${styles.muted}`} role="status" aria-live="polite">{t('state.refresh')}…</td></tr>
            ) : beliefs.length === 0 ? (
              <tr><td colSpan={5} className={`px-4 py-6 text-center ${styles.muted}`}>{t('state.noBeliefs')}</td></tr>
            ) : beliefs.map((b, i) => {
              const statusKey = b.status?.toLowerCase() ?? 'mid';
              return (
                <tr key={b.variableName + i} className={`border-b ${styles.divider} last:border-0`}>
                  <td className="px-4 py-2">
                    <span className={`${styles.cardText} font-medium`}>{b.variableName}</span>
                    <div className="flex gap-1 mt-0.5">
                      {b.distribution.slice(0, 3).map((d, di) => (
                        <span key={di} className={`text-xs px-1 py-0.5 rounded ${styles.badgeBg} ${styles.badgeText}`}>
                          {d.outcome}: {(d.prob * 100).toFixed(0)}%
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <div className={`w-20 h-1.5 ${styles.inputBg} rounded-full overflow-hidden`}>
                        <div className={`h-full ${styles.accentBg} rounded-full`}
                          style={{ width: `${Math.max(0, Math.min(1, b.distribution[0]?.prob ?? 0)) * 100}%` }} />
                      </div>
                      <span className={`text-xs font-mono ${styles.cardText}`}>
                        {(b.distribution[0]?.prob ?? 0).toFixed(2)}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <Badge variant={STATUS_BADGE[statusKey] ?? 'neutral'}>
                      {STATUS_LABELS[statusKey] ?? b.status}
                    </Badge>
                  </td>
                  <td className={`px-4 py-2 text-xs ${styles.muted}`}>+2 {t('state.support')} / +1 {t('state.rebuttal')}</td>
                  <td className={`px-4 py-2 text-xs ${styles.muted}`}>{(b as any).updatedAt ?? (b as any).updateTime ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Evolution + Trace */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('state.evolution')}</h2>
          {evolution.length === 0 ? (
            <p className={`text-xs ${styles.muted}`}>{t('state.noEvolution')}</p>
          ) : (
            <ol className="space-y-3">
              {evolution.slice(0, 6).map((item, i) => (
                <li key={i} className="flex gap-3">
                  <div className="flex flex-col items-center">
                    <div className={`w-2 h-2 rounded-full ${styles.accentBg} mt-1.5 flex-shrink-0`} aria-hidden />
                    {i < Math.min(evolution.length, 6) - 1 && <div className={`w-px flex-1 ${styles.inputBg}`} />}
                  </div>
                  <div className="flex-1 pb-1">
                    <p className={`text-xs ${styles.muted}`}>{item.time ?? ''}</p>
                    <p className={`text-sm ${styles.cardText}`}>{item.variable ?? ''}</p>
                    {item.detail && <p className={`text-xs ${styles.muted} mt-0.5`}>{item.detail}</p>}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('state.fullTrace')}</h2>
          <div className="flex items-center gap-2 overflow-x-auto pb-1">
            {TRACE_NODES.map((n, i) => (
              <React.Fragment key={n}>
                <span className={`text-xs px-2.5 py-1 rounded ${styles.badgeBg} ${styles.cardText} whitespace-nowrap font-medium`}>{n}</span>
                {i < TRACE_NODES.length - 1 && <ArrowRight className={`w-3.5 h-3.5 ${styles.muted} flex-shrink-0`} aria-hidden />}
              </React.Fragment>
            ))}
          </div>
          <p className={`text-xs ${styles.muted} mt-3`}>
            {t('state.traceDesc')}
          </p>
        </div>
      </div>
    </div>
  );
}
