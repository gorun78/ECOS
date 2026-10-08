import React, { useEffect, useState, useCallback } from 'react';
import { Brain, Lightbulb, Eye, Zap, Database, BookOpen, Network, ArrowRight } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme, type ThemeStyles } from '../../../components/ThemeContext';
import { apiFetchData } from '../../../api';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge from '../../../components/common/Badge';

// 主题合规（§七.1）：色值不再以 Tailwind 字面量出现，改存 useTheme() token 键，渲染期取 styles[key]。
type TokenKey = keyof ThemeStyles & string;

const METRICS: ReadonlyArray<{ label: string; fallback: string; icon: typeof Brain; colorKey: TokenKey; path: string }> = [
  { label: 'cognition.overview.metrics.models', fallback: '活跃认知模型', icon: Brain, colorKey: 'accentText', path: '/api/v1/cognitive/models' },
  { label: 'cognition.overview.metrics.hypotheses', fallback: '待验证假设', icon: Lightbulb, colorKey: 'warningText', path: '/api/v1/cognitive/hypotheses?status=VALID' },
  { label: 'cognition.overview.metrics.beliefs', fallback: '当前信念', icon: Eye, colorKey: 'successText', path: '/api/v1/cognitive/beliefs' },
];

const CHAIN_NODES: ReadonlyArray<{ key: string; label: string; icon: typeof Brain; colorKey: TokenKey; highlight?: boolean }> = [
  { key: 'D', label: '数据层', icon: Database, colorKey: 'muted' },
  { key: 'I', label: '信息层', icon: BookOpen, colorKey: 'warningText' },
  { key: 'K', label: '知识层', icon: Brain, colorKey: 'successText' },
  { key: 'C', label: '认知层', icon: Network, colorKey: 'accentText', highlight: true },
  { key: 'W', label: '智慧层', icon: Zap, colorKey: 'warningText' },
  { key: '↺D', label: '反馈数据层', icon: ArrowRight, colorKey: 'muted' },
];

const HEALTH: ReadonlyArray<{ label: string; value: number }> = [
  { label: 'D', value: 92 },
  { label: 'I', value: 85 },
  { label: 'K', value: 78 },
  { label: 'C', value: 61 },
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
  const { styles } = useTheme();
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
      <div className={`flex flex-col items-center gap-1 px-3 py-2 rounded-lg border ${
        n.highlight ? `border-2 ${styles.accentBorder}` : `${styles.cardBorder}`
      } ${styles.cardBg}`}>
        <n.icon className={`w-5 h-5 ${styles[n.colorKey]}`} aria-hidden />
        <span className={`text-xs font-medium ${styles.cardText}`}>{n.key}</span>
      </div>
      {i < CHAIN_NODES.length - 1 && <ArrowRight className={`w-4 h-4 ${styles.muted} flex-shrink-0`} aria-hidden />}
    </React.Fragment>
  ));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('knowledge.cognition.title.overview')}</h1>
        <Button variant="primary" size="md" onClick={loadData} aria-label={t('knowledge.cognition.refresh')}>
          {t('knowledge.cognition.refresh')}
        </Button>
      </div>

      {/* Metric cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4" role="group" aria-label={t('knowledge.cognition.title.overview')}>
        {METRICS.map(m => (
          <div key={m.label} role="status" aria-label={t(m.label)} className={`${styles.appBg} border ${styles.cardBorder} rounded-lg p-4 flex flex-col gap-2`}>
            <div className="flex items-center gap-2">
              <m.icon className={`w-4 h-4 ${styles[m.colorKey]}`} aria-hidden />
              <span className={`text-xs ${styles.muted}`}>{t(m.label)}</span>
            </div>
            <div className={`text-2xl font-bold ${styles.cardText}`}>
              {loading ? '…' : String(counts?.[m.path === '/api/v1/cognitive/models' ? 'models' : m.path.includes('status=VALID') ? 'hypotheses' : 'beliefs'] ?? 0)}
            </div>
          </div>
        ))}
        <div className={`${styles.appBg} border ${styles.cardBorder} rounded-lg p-4 flex flex-col gap-2`}>
          <div className="flex items-center gap-2">
            <Zap className={`w-4 h-4 ${styles.accentText}`} aria-hidden />
            <span className={`text-xs ${styles.muted}`}>{t('knowledge.cognition.realtimeReasoning')}</span>
          </div>
          <div className={`text-2xl font-bold ${styles.cardText}`}>
            {loading ? '…' : `${activeSessions ?? 0} sessions active`}
          </div>
          <span className={`text-xs ${styles.muted}`}>{t('knowledge.cognition.noPersistenceNote')}</span>
        </div>
      </div>

      {/* Cognitive chain */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('knowledge.cognition.cognitiveChain')}</h2>
        <div className="flex items-center gap-1 overflow-x-auto pb-1">{chainNodes}</div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Current situation */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('knowledge.cognition.currentSituation')}</h2>
          <div className="space-y-3">
            <select aria-label={t('knowledge.cognition.selectObject')}
              className={`w-full px-3 py-1.5 text-sm border ${styles.inputBorder} rounded-md ${styles.inputBg} ${styles.inputText}`}
              value={currentObject} onChange={e => setCurrentObject(e.target.value)}>
              <option value="">{t('knowledge.cognition.selectObject')}</option>
              <option value="P1">P1 — 项目</option>
              <option value="C1">C1 — 客户</option>
              <option value="S1">S1 — 供应商</option>
            </select>
            <div className="flex gap-2">
              <Badge variant="success" className="font-medium">D ✓</Badge>
              <Badge variant="success" className="font-medium">I ✓</Badge>
              <Badge variant="success" className="font-medium">K ✓</Badge>
            </div>
          </div>
        </div>

        {/* Health */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('knowledge.cognition.chainHealth')}</h2>
          <div className="space-y-3">
            {HEALTH.map(h => (
              <div key={h.label} className="flex items-center gap-2">
                <span className={`text-xs font-medium ${styles.muted} w-4`}>{h.label}</span>
                <div className={`flex-1 h-2 ${styles.inputBg} rounded-full overflow-hidden`}>
                  <div className={`h-full ${styles.accentBg} rounded-full`} style={{ width: `${h.value}%` }} />
                </div>
                <span className={`text-xs ${styles.muted}`}>{h.value}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Activity table */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg`}>
        <h2 className={`text-sm font-medium ${styles.cardText} px-4 pt-4 pb-2`}>{t('knowledge.cognition.recentActivities')}</h2>
        <table className="w-full text-sm" aria-label={t('knowledge.cognition.recentActivities')}>
          <thead>
            <tr className={`border-b ${styles.cardBorder} text-left text-xs ${styles.muted}`}>
              <th scope="col" className="px-4 py-2 font-medium">{t('knowledge.cognition.col.time')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('knowledge.cognition.col.activity')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('knowledge.cognition.col.object')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('knowledge.cognition.col.status')}</th>
              <th scope="col" className="px-4 py-2 font-medium">{t('knowledge.cognition.col.entry')}</th>
            </tr>
          </thead>
          <tbody>
            {ACTIVITIES.map((a, i) => (
              <tr key={i} className={`border-b ${styles.divider} last:border-0`}>
                <td className={`px-4 py-2 ${styles.muted}`}>{a.time}</td>
                <td className={`px-4 py-2 ${styles.cardText}`}>{a.activity}</td>
                <td className={`px-4 py-2 ${styles.muted}`}>{a.object}</td>
                <td className="px-4 py-2"><Badge variant="neutral">{a.status}</Badge></td>
                <td className="px-4 py-2"><a href={a.entry} className={`${styles.accentText} hover:underline`}>{t('knowledge.cognition.open')}</a></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
