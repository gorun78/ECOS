/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-54 OverviewDashboard — 知识工作台总览
 *
 * - 顶部 4 KPI 卡（图谱节点 / 边 / 向量条目 / 规则）
 * - 中间：最近同步 mini 折线（最近 7 天 /sync/logs group by day） + 引擎状态（/health 可用 若否则 stub "waiting P56"）
 * - 底层：Top Queries 最近 10 条（本地 history）
 */

import React, { useEffect, useMemo, useState } from 'react';
import {
  LayoutDashboard, Binary, Layers, Database, ShieldCheck,
  Activity, HeartPulse, Search, Clock,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

const RAG_HISTORY_KEY = 'kb_rag_top_queries';

interface TopQuery {
  query: string;
  hits: number;
  when: string;
}

function loadTopQueries(): TopQuery[] {
  try {
    const raw = localStorage.getItem(RAG_HISTORY_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as TopQuery[];
    return Array.isArray(parsed) ? parsed.slice(0, 10) : [];
  } catch {
    return [];
  }
}

export function pushTopQuery(q: string) {
  try {
    const list = loadTopQueries().filter(x => x.query !== q);
    const entry: TopQuery = { query: q, hits: 1, when: new Date().toISOString() };
    list.unshift(entry);
    localStorage.setItem(RAG_HISTORY_KEY, JSON.stringify(list.slice(0, 10)));
  } catch { /* ignore */ }
}

export default function OverviewDashboard() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [graphStats, setGraphStats] = useState<{
    graphNodeCount: number; graphEdgeCount: number; embeddingCount: number; ruleCount: number; lastUpdatedAt?: string;
  }>({ graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0, lastUpdatedAt: undefined });
  const [statsOff, setStatsOff] = useState(false);
  const [syncLogs, setSyncLogs] = useState<{ timestamp?: string; [key: string]: unknown }[]>([]);
  const [engineHealth, setEngineHealth] = useState<Record<string, { ok: boolean; latencyMs?: number; message?: string }>>({});
  const [healthAvailable, setHealthAvailable] = useState<boolean | null>(null);
  const [topQueries, setTopQueries] = useState<TopQuery[]>([]);

  useEffect(() => {
    (async (): Promise<void> => {
      // C-1: 三路并发，每路独立 catch → 即使某路 500 也不代表整体失败
      const [stats, logs, health] = await Promise.all([
        knowledgeApi.fetchGraphStats().catch((): null => null),
        (async (): Promise<{ timestamp?: string; [key: string]: unknown }[]> => {
          try {
            const data = await knowledgeApi.fetchSyncLogs();
            if (!Array.isArray(data)) return [];
            // SyncLog 类型缺 index signature — 走 unknown 中转展开，避免 union type 撞墙
            return data.map(l => ({ ...l, timestamp: (l as { timestamp?: string }).timestamp }));
          } catch {
            return [] as { timestamp?: string; [key: string]: unknown }[];
          }
        })(),
        knowledgeApi.fetchEngineHealth().catch((): null => null),
      ]);
      // stats 失败 → 整段 KPI 卡降级 empty，记录 "backend offline"；不直接 500 白屏
      if (!stats) {
        setStatsOff(true);
        setGraphStats({ graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0 });
      } else {
        setStatsOff(false);
        setGraphStats(stats);
      }
      setSyncLogs(logs);
      const h = health ?? {};
      setEngineHealth(h);
      const keys = Object.keys(h);
      setHealthAvailable(keys.length > 0 && keys.some(k => h[k] != null));
    })();
    setTopQueries(loadTopQueries());
  }, []);

  // 最近7天 daily 同步量（本地 group by 天）
  const dailyCounts = useMemo(() => {
    const days: Record<string, number> = {};
    const now = new Date();
    for (let i = 6; i >= 0; i--) {
      const d = new Date(now);
      d.setDate(d.getDate() - i);
      const key = d.toISOString().substring(0, 10);
      days[key] = 0;
    }
    (syncLogs || []).forEach(l => {
      if (!l?.timestamp) return;
      const key = String(l.timestamp).substring(0, 10);
      if (days[key] != null) days[key] += 1;
    });
    return Object.values(days);
  }, [syncLogs]);

  const maxDaily = Math.max(1, ...dailyCounts);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className={`flex flex-col md:flex-row md:items-center justify-between border-b pb-4 gap-3 ${styles.cardBorder}`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black flex items-center gap-2 ${styles.cardText}`}>
            <LayoutDashboard size={16} className={styles.accentText} />
            {t('knowledge.dashboard.title')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('knowledge.dashboard.subtitle')}</p>
        </div>
        <span className="text-[10px] font-mono text-slate-500">
          {graphStats.lastUpdatedAt || t('knowledge.overview.stats_offline')}
        </span>
      </div>

      {/* KPI cards — 失败时整段降级为 offline empty */}
      {statsOff ? (
        <div className={`rounded-xl border p-6 flex flex-col items-center justify-center ${styles.cardBg} ${styles.dangerBorder} ${styles.dangerBg}`}>
          <Database size={22} className={styles.dangerText} />
          <p className={`text-xs mt-2 ${styles.dangerText}`}>{t('knowledge.overview.stats_offline')}</p>
          <p className="text-[10px] font-mono opacity-70">/api/v1/knowledge/stats · {t('knowledge.overview.retry_hint')}</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard title={t('knowledge.dashboard.kpi.graph_nodes')} value={graphStats.graphNodeCount} icon={<Binary size={16} />} accent="bg-indigo-50 text-indigo-600" />
          <KpiCard title={t('knowledge.dashboard.kpi.graph_edges')} value={graphStats.graphEdgeCount} icon={<Layers size={16} />} accent="bg-blue-50 text-blue-600" />
          <KpiCard title={t('knowledge.dashboard.kpi.chunks')} value={graphStats.embeddingCount} icon={<Database size={16} />} accent="bg-emerald-50 text-emerald-600" />
          <KpiCard title={t('knowledge.dashboard.kpi.active_rules')} value={graphStats.ruleCount} icon={<ShieldCheck size={16} />} accent="bg-violet-50 text-violet-600" />
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* 近7天折线（模拟 mini 折线） */}
        <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
          <div className={`flex items-center justify-between border-b pb-2 ${styles.divider}`}>
            <h3 className={`font-extrabold text-xs flex items-center gap-1.5 ${styles.cardText}`}>
              <Activity size={13} className={styles.accentText} />
              {t('knowledge.dashboard.trend.week_label')}
            </h3>
            <span className="text-[10px] font-mono text-slate-400">/sync/logs · group by day</span>
          </div>
          <div className="h-32 flex items-end gap-2 pt-4">
            {dailyCounts.map((c, i) => (
              <div key={i} className="flex-1 flex flex-col items-center gap-1">
                <div className={`w-full rounded-md relative flex items-end justify-center ${styles.inputBg}`} style={{ height: '90px' }}>
                  <div
                    className={`w-2/3 rounded-t transition-all duration-500 ${styles.accentBg}`}
                    style={{ height: `${Math.round((c / maxDaily) * 100)}%`, minHeight: c > 0 ? '6px' : '0' }}
                    title={`${c}`}
                  />
                </div>
                <span className="text-[8px] font-mono text-slate-400">{i === 6 ? new Date().toISOString().substring(0, 10) : ''}</span>
                <span className={`text-[8px] font-bold ${styles.cardTextMuted}`}>{c}</span>
              </div>
            ))}
          </div>
        </div>

        {/* 引擎健康 */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
          <div className={`flex items-center justify-between border-b pb-2 ${styles.divider}`}>
            <h3 className={`font-extrabold text-xs flex items-center gap-1.5 ${styles.cardText}`}>
              <HeartPulse size={13} className={styles.successText} />
              {t('knowledge.dashboard.h.engine')}
            </h3>
            <span className="text-[9px] font-mono text-slate-400">/api/v1/knowledge/health</span>
          </div>
          {healthAvailable === null || !healthAvailable ? (
            <div className={`py-6 text-center space-y-2 ${styles.cardTextMuted}`}>
              <HeartPulse size={20} className="mx-auto opacity-50" />
              <p className="text-xs">{t('knowledge.dashboard.h.waiting_pmo56')}</p>
              <p className="text-[10px] font-mono opacity-60">{t('knowledge.overview.stats_offline')}</p>
            </div>
          ) : (
            <div className="space-y-1.5">
              {Object.entries(engineHealth).map(([name, h]) => (
                <div key={name}
                  className={`p-2 rounded-lg flex items-center justify-between text-[11px] border ${
                    h.ok ? `${styles.successBg} ${styles.successBorder}` : `${styles.dangerBg} ${styles.dangerBorder}`
                  }`}>
                  <span className={`font-bold ${styles.cardText}`}>{name}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] font-mono text-slate-500">{h.latencyMs ? `${h.latencyMs}ms` : '—'}</span>
                    <span className={`w-2 h-2 rounded-full ${h.ok ? styles.accentBg : styles.dangerBg}`} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Top queries */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
        <div className={`flex items-center justify-between border-b pb-2 ${styles.divider}`}>
          <h3 className={`font-extrabold text-xs flex items-center gap-1.5 ${styles.cardText}`}>
            <Search size={13} className={styles.warningText} />
            {t('knowledge.dashboard.top.title', {n: 10})}
          </h3>
          <span className="text-[10px] font-mono text-slate-400">{topQueries.length}</span>
        </div>
        {topQueries.length === 0 ? (
          <div className={`py-8 text-center space-y-2 ${styles.cardTextMuted}`}>
            <Clock size={20} className="mx-auto opacity-50" />
            <p className="text-xs">{t('knowledge.dashboard.top.empty')}</p>
          </div>
        ) : (
          <div className="space-y-1 max-h-60 overflow-y-auto">
            {topQueries.map((q, i) => (
              <div key={i} className={`flex items-center gap-3 p-2 rounded-lg hover:opacity-70 transition ${styles.cardTextMuted}`}>
                <span className={`w-6 h-6 rounded-full text-[10px] font-bold flex items-center justify-center shrink-0 ${styles.badgeBg} ${styles.badgeText}`}>{i + 1}</span>
                <div className="flex-1 min-w-0">
                  <span className={`text-[11px] font-bold truncate block ${styles.cardText}`}>{q.query}</span>
                </div>
                <span className="text-[9px] font-mono text-slate-400 shrink-0">{new Date(q.when).toLocaleString(t('knowledge.knowledgerulerepository.zh_cn'))}</span>
              </div>
            ))}
          </div>
        )}
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
