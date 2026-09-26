import React, { useState, useCallback, useEffect } from 'react';
import { Database, Lightbulb, Eye, RefreshCw, ArrowRight } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import type { Belief } from './types';

const STATUS_STYLES: Record<string, string> = {
  high: 'bg-green-100 dark:bg-green-950 text-green-700 dark:text-green-300',
  mid: 'bg-amber-100 dark:bg-amber-950 text-amber-700 dark:text-amber-300',
  low: 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300',
};

const STATUS_LABELS: Record<string, string> = { high: '高', mid: '中', low: '低' };

const TRACE_NODES = ['D', 'I', 'K', 'Evidence', 'Hypothesis', 'Belief', 'C', 'W'];

interface EvolutionItem { time?: string; action?: string; variable?: string; detail?: string; }

export default function cognitiveState() {
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
      showToastGlobal('error', `${t('state.loadFail', '加载认知状态失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { loadData(); }, [loadData]);

  const handleRefresh = () => {
    setRefreshing(true);
    loadData().finally(() => setRefreshing(false));
  };

  const metricCards = [
    { key: 'evidence', icon: Database, color: 'text-blue-500', labelKey: 'state.metric.evidence', label: 'Evidence', count: counts.evidence },
    { key: 'hypothesis', icon: Lightbulb, color: 'text-amber-500', labelKey: 'state.metric.hypothesis', label: 'Hypothesis', count: counts.hypothesis },
    { key: 'belief', icon: Eye, color: 'text-emerald-500', labelKey: 'state.metric.belief', label: 'Belief', count: counts.belief },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('state.title', '认知状态')}</h1>
        <button onClick={handleRefresh} disabled={refreshing}
          className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
          <RefreshCw className={`w-3.5 h-3.5 inline mr-1 ${refreshing ? 'animate-spin' : ''}`} />
          {t('state.refresh', '刷新状态')}
        </button>
      </div>

      {/* Metric cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {metricCards.map(m => (
          <div key={m.key} className="bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <m.icon className={`w-4 h-4 ${m.color}`} />
              <span className="text-xs text-slate-500 dark:text-slate-400">{t(m.labelKey, m.label)}</span>
            </div>
            <p className="text-2xl font-bold text-slate-900 dark:text-slate-100">{m.count}</p>
          </div>
        ))}
      </div>

      {/* Belief table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg overflow-x-auto">
        <div className="px-4 pt-4 pb-2">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100">{t('state.beliefTable', 'Belief 状态')}</h2>
        </div>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-700 text-left text-xs text-slate-500 dark:text-slate-400">
              <th className="px-4 py-2 font-medium">{t('state.col.proposition', '命题')}</th>
              <th className="px-4 py-2 font-medium">{t('state.col.support', '支持度')}</th>
              <th className="px-4 py-2 font-medium">{t('state.col.status', '状态')}</th>
              <th className="px-4 py-2 font-medium">{t('state.col.evidenceChange', '证据变化')}</th>
              <th className="px-4 py-2 font-medium">{t('state.col.updated', '更新时间')}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-400">Loading…</td></tr>
            ) : beliefs.length === 0 ? (
              <tr><td colSpan={5} className="px-4 py-6 text-center text-slate-400">{t('state.noBeliefs', '暂无 Belief 记录')}</td></tr>
            ) : beliefs.map((b, i) => {
              const statusKey = b.status?.toLowerCase() ?? 'mid';
              return (
                <tr key={b.variableName + i} className="border-b border-slate-100 dark:border-slate-800 last:border-0">
                  <td className="px-4 py-2">
                    <span className="text-slate-900 dark:text-slate-100 font-medium">{b.variableName}</span>
                    <div className="flex gap-1 mt-0.5">
                      {b.distribution.slice(0, 3).map((d, di) => (
                        <span key={di} className="text-xs px-1 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400">
                          {d.outcome}: {(d.prob * 100).toFixed(0)}%
                        </span>
                      ))}
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <div className="w-20 h-1.5 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                        <div className="h-full bg-blue-500 rounded-full"
                          style={{ width: `${Math.max(0, Math.min(1, b.distribution[0]?.prob ?? 0)) * 100}%` }} />
                      </div>
                      <span className="text-xs font-mono text-slate-600 dark:text-slate-300">
                        {(b.distribution[0]?.prob ?? 0).toFixed(2)}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-2">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${STATUS_STYLES[statusKey] ?? 'bg-slate-100 dark:bg-slate-800 text-slate-500'}`}>
                      {STATUS_LABELS[statusKey] ?? b.status}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-xs text-slate-500 dark:text-slate-400">+2 {t('state.support', '支持')} / +1 {t('state.rebuttal', '反驳')}</td>
                  <td className="px-4 py-2 text-xs text-slate-400">{(b as any).updatedAt ?? (b as any).updateTime ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Evolution + Trace */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('state.evolution', '认知演化')}</h2>
          {evolution.length === 0 ? (
            <p className="text-xs text-slate-400">{t('state.noEvolution', '暂无演化记录')}</p>
          ) : (
            <ol className="space-y-3">
              {evolution.slice(0, 6).map((item, i) => (
                <li key={i} className="flex gap-3">
                  <div className="flex flex-col items-center">
                    <div className="w-2 h-2 rounded-full bg-blue-500 mt-1.5 flex-shrink-0" />
                    {i < Math.min(evolution.length, 6) - 1 && <div className="w-px flex-1 bg-slate-200 dark:bg-slate-700" />}
                  </div>
                  <div className="flex-1 pb-1">
                    <p className="text-xs text-slate-500 dark:text-slate-400">{item.time ?? ''}</p>
                    <p className="text-sm text-slate-700 dark:text-slate-300">{item.variable ?? ''}</p>
                    {item.detail && <p className="text-xs text-slate-400 mt-0.5">{item.detail}</p>}
                  </div>
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('state.fullTrace', '完整追溯')}</h2>
          <div className="flex items-center gap-2 overflow-x-auto pb-1">
            {TRACE_NODES.map((n, i) => (
              <React.Fragment key={n}>
                <span className="text-xs px-2.5 py-1 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 whitespace-nowrap font-medium">{n}</span>
                {i < TRACE_NODES.length - 1 && <ArrowRight className="w-3.5 h-3.5 text-slate-300 dark:text-slate-600 flex-shrink-0" />}
              </React.Fragment>
            ))}
          </div>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-3">
            {t('state.traceDesc', '数据 → 信息 → 知识 → 证据 → 假设 → 信念 → 认知 → 智慧 完整溯源链路。')}
          </p>
        </div>
      </div>
    </div>
  );
}
