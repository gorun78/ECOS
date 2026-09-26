import React, { useEffect, useState, useCallback } from 'react';
import { Brain, Lightbulb, Eye, Zap, Database, BookOpen, Network, ArrowRight } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { apiFetchData } from '../../../api';
import { showToastGlobal } from '../../../components/common/Toast';

const METRICS = [
  { label: 'cognition.overview.metrics.models', fallback: '活跃认知模型', icon: Brain, color: 'text-blue-500', path: '/api/v1/cognitive/models' },
  { label: 'cognition.overview.metrics.hypotheses', fallback: '待验证假设', icon: Lightbulb, color: 'text-amber-500', path: '/api/v1/cognitive/hypotheses?status=VALID' },
  { label: 'cognition.overview.metrics.beliefs', fallback: '当前信念', icon: Eye, color: 'text-emerald-500', path: '/api/v1/cognitive/beliefs' },
] as const;

const CHAIN_NODES = [
  { key: 'D', label: '数据层', icon: Database, color: 'bg-slate-100 dark:bg-slate-800 text-slate-400' },
  { key: 'I', label: '信息层', icon: BookOpen, color: 'bg-amber-50 dark:bg-amber-950 text-amber-500' },
  { key: 'K', label: '知识层', icon: Brain, color: 'bg-emerald-50 dark:bg-emerald-950 text-emerald-500' },
  { key: 'C', label: '认知层', icon: Network, color: 'bg-blue-50 dark:bg-blue-950 text-blue-600', highlight: true },
  { key: 'W', label: '智慧层', icon: Zap, color: 'bg-amber-50 dark:bg-amber-950 text-amber-500' },
  { key: '↺D', label: '反馈数据层', icon: ArrowRight, color: 'text-slate-400' },
];

const HEALTH = [
  { label: 'D', value: 92, color: 'bg-blue-500' },
  { label: 'I', value: 85, color: 'bg-amber-500' },
  { label: 'K', value: 78, color: 'bg-emerald-500' },
  { label: 'C', value: 61, color: 'bg-blue-600' },
];

const ACTIVITIES = [
  { time: '14:32', activity: '诊断执行 (项目P1)', object: '毛利分析', status: '已完成', entry: '#' },
  { time: '14:15', activity: '假设验证 (H-003)', object: '采购成本', status: '已支持', entry: '#' },
  { time: '13:50', activity: '情景推演 (基准 vs A)', object: '采购策略', status: '进行中', entry: '#' },
  { time: '13:22', activity: '信念更新 (毛利)', object: '毛利', status: '已更新', entry: '#' },
  { time: '12:48', activity: '诊断执行 (客户C2)', object: '续约风险', status: '已完成', entry: '#' },
  { time: '12:05', activity: '新建假设 (H-012)', object: '人力成本', status: '已创建', entry: '#' },
  { time: '11:40', activity: '推理结束 (项目P3)', object: '工期', status: '已完成', entry: '#' },
  { time: '11:12', activity: '证据入库 (E-208)', object: '采购价', status: '已入库', entry: '#' },
  { time: '10:51', activity: '诊断执行 (供应商S1)', object: '交付质量', status: '已完成', entry: '#' },
  { time: '10:20', activity: '模型发布 (M-004)', object: '利润预测', status: '已发布', entry: '#' },
];

export default function overview() {
  const { t } = useLanguage();
  const [counts, setCounts] = useState<{ models: number; hypotheses: number; beliefs: number } | null>(null);
  const [activeSessions, setActiveSessions] = useState<number | null>(null);
  const [currentObject, setCurrentObject] = useState<string>('');
  const [loading, setLoading] = useState(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [models, hyps, beliefs, health] = await Promise.allSettled([
        apiFetchData<any[]>('/api/v1/cognitive/models'),
        apiFetchData<any[]>('/api/v1/cognitive/hypotheses?status=VALID'),
        apiFetchData<any[]>('/api/v1/cognitive/beliefs'),
        apiFetchData<any>('/api/v1/cognitive/health'),
      ]);
      setCounts({
        models: models.status === 'fulfilled' ? (Array.isArray(models.value) ? models.value.length : (models.value as any)?.length ?? 0) : 0,
        hypotheses: hyps.status === 'fulfilled' ? (Array.isArray(hyps.value) ? hyps.value.length : (hyps.value as any)?.length ?? 0) : 0,
        beliefs: beliefs.status === 'fulfilled' ? (Array.isArray(beliefs.value) ? beliefs.value.length : (beliefs.value as any)?.length ?? 0) : 0,
      });
      if (health.status === 'fulfilled') {
        const h = health.value as any;
        setActiveSessions(h?.activeSessions ?? h?.active ?? null);
      }
    } catch (e) {
      showToastGlobal('error', (e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const chainNodes = CHAIN_NODES.map((n, i) => (
    <React.Fragment key={n.key}>
      <div className={`flex flex-col items-center gap-1 px-3 py-2 rounded-lg border ${n.highlight ? 'ring-2 ring-blue-500 border-blue-300 dark:border-blue-700' : 'border-slate-200 dark:border-slate-700'} bg-white dark:bg-slate-900`}>
        <n.icon className={`w-5 h-5 ${n.color.split(' ')[0]}`} />
        <span className="text-xs font-medium text-slate-700 dark:text-slate-200">{n.key}</span>
      </div>
      {i < CHAIN_NODES.length - 1 && <ArrowRight className="w-4 h-4 text-slate-400 flex-shrink-0" />}
    </React.Fragment>
  ));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('knowledge.cognition.title.overview', '认知总览')}</h1>
        <button onClick={loadData} className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600">{t('knowledge.cognition.refresh', '刷新')}</button>
      </div>

      {/* Metric cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {METRICS.map(m => (
          <div key={m.label} className="bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <m.icon className={`w-4 h-4 ${m.color}`} />
              <span className="text-xs text-slate-500 dark:text-slate-400">{t(m.label, 'fallback')}</span>
            </div>
            <div className="text-2xl font-bold text-slate-900 dark:text-slate-100">
              {loading ? '…' : String(counts?.[m.path === '/api/v1/cognitive/models' ? 'models' : m.path.includes('status=VALID') ? 'hypotheses' : 'beliefs'] ?? 0)}
            </div>
          </div>
        ))}
        <div className="bg-slate-50 dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <Zap className="w-4 h-4 text-purple-500" />
            <span className="text-xs text-slate-500 dark:text-slate-400">{t('knowledge.cognition.realtimeReasoning', '实时推理')}</span>
          </div>
          <div className="text-2xl font-bold text-slate-900 dark:text-slate-100">
            {loading ? '…' : `${activeSessions ?? 0} sessions active`}
          </div>
          <span className="text-xs text-slate-400">{t('knowledge.cognition.noPersistenceNote', '推理结果不落盘')}</span>
        </div>
      </div>

      {/* Cognitive chain */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
        <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('knowledge.cognition.cognitiveChain', '认知链')}</h2>
        <div className="flex items-center gap-1 overflow-x-auto pb-1">{chainNodes}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Current situation */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('knowledge.cognition.currentSituation', '当前认知情境')}</h2>
          <div className="space-y-3">
            <select className="w-full px-3 py-1.5 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
              value={currentObject} onChange={e => setCurrentObject(e.target.value)}>
              <option value="">{t('knowledge.cognition.selectObject', '选择业务对象…')}</option>
              <option value="P1">P1 — 项目</option>
              <option value="C1">C1 — 客户</option>
              <option value="S1">S1 — 供应商</option>
            </select>
            <div className="flex gap-2">
              <span className="px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300">D ✓</span>
              <span className="px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300">I ✓</span>
              <span className="px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 dark:bg-emerald-950 text-emerald-700 dark:text-emerald-300">K ✓</span>
            </div>
          </div>
        </div>

        {/* Health */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('knowledge.cognition.chainHealth', '认知链健康度')}</h2>
          <div className="space-y-3">
            {HEALTH.map(h => (
              <div key={h.label} className="flex items-center gap-2">
                <span className="text-xs font-medium text-slate-500 dark:text-slate-400 w-4">{h.label}</span>
                <div className="flex-1 h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                  <div className={`h-full ${h.color} rounded-full`} style={{ width: `${h.value}%` }} />
                </div>
                <span className="text-xs text-slate-400">{h.value}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Activity table */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg">
        <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 px-4 pt-4 pb-2">{t('knowledge.cognition.recentActivities', '最近认知活动')}</h2>
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-700 text-left text-xs text-slate-500 dark:text-slate-400">
              <th className="px-4 py-2 font-medium">{t('knowledge.cognition.col.time', '时间')}</th>
              <th className="px-4 py-2 font-medium">{t('knowledge.cognition.col.activity', '活动')}</th>
              <th className="px-4 py-2 font-medium">{t('knowledge.cognition.col.object', '对象')}</th>
              <th className="px-4 py-2 font-medium">{t('knowledge.cognition.col.status', '状态')}</th>
              <th className="px-4 py-2 font-medium">{t('knowledge.cognition.col.entry', '入口')}</th>
            </tr>
          </thead>
          <tbody>
            {ACTIVITIES.map((a, i) => (
              <tr key={i} className="border-b border-slate-100 dark:border-slate-800 last:border-0">
                <td className="px-4 py-2 text-slate-500 dark:text-slate-400">{a.time}</td>
                <td className="px-4 py-2 text-slate-900 dark:text-slate-100">{a.activity}</td>
                <td className="px-4 py-2 text-slate-500 dark:text-slate-400">{a.object}</td>
                <td className="px-4 py-2"><span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">{a.status}</span></td>
                <td className="px-4 py-2"><a href={a.entry} className="text-blue-600 dark:text-blue-400 hover:underline">{t('knowledge.cognition.open', '打开')}</a></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
