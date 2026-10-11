/**
 * 工作台 L2 — 单场景详情（F07-06 三层 IA 第二层，路由 /workbench/:scenarioId）。
 *
 * <p>首屏只展示业务语义（关键指标 + 认知摘要 + 最近动作），
 * 不出现 Mind / Goal Tree / Causal 或后端原始字段。认知摘要经
 * {@link hooks/useCognition} 只读订阅 E1~E4；503 时渲染横幅（不伪造）。
 * 六态（§3.3）：空 / 加载 / 错误（404→未找到、403→无权限）/ 边界 / 权限 / 离线 全覆盖。</p>
 */
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import { useCognition } from '../../../hooks/useCognition';
import { apiFetchData } from '../../../api';
import logger from '../../../services/logger';
import BlueprintPanel from './blueprint/BlueprintPanel';
import AdvancedDebugPanel from './advanced/AdvancedDebugPanel';

interface ScenarioDetailVO {
  id: string;
  name: string;
  description?: string;
  businessGoal?: string;
  department?: string;
  status?: string;
  safetyIndexTarget?: number | string | null;
  actualSafetyIndex?: number | string | null;
  metrics?: Record<string, unknown>;
  createdAt?: string;
}

function fmt(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—';
  return String(v);
}

export default function WorkbenchScenarioDetail() {
  const { scenarioId = '' } = useParams<{ scenarioId: string }>();
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [scenario, setScenario] = useState<ScenarioDetailVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!scenarioId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setNotFound(false);
    setDenied(false);
    apiFetchData<ScenarioDetailVO>(`/api/v1/workspace/scenarios/${scenarioId}`)
      .then((d) => {
        if (!cancelled) setScenario(d);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        logger.error('workbench-detail', e);
        const status = (e as { status?: number }).status;
        if (status === 404) setNotFound(true);
        else if (status === 403 || status === 401) setDenied(true);
        else setError(e instanceof Error ? e.message : String(e));
        setScenario(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId]);

  const { operationEval } = useCognition(scenarioId, { enabled: !!scenario && !loading });

  const metrics = (scenario?.metrics ?? {}) as Record<string, number>;
  const integrity = typeof metrics.integrityScore === 'number' ? `${metrics.integrityScore.toFixed(1)}%` : '—';

  const gridCls = isMobile ? 'grid-cols-2' : 'grid-cols-2 sm:grid-cols-4';

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* Back nav */}
      <div className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex items-center gap-2`}>
        <Link
          to="/workbench"
          className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <LucideIcon name="ArrowLeft" size={12} />
          {t('workspace.l2.back')}
        </Link>
      </div>

      {/* Loading */}
      {loading && (
        <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted} p-6 justify-center`}>
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          {t('workspace.l2.loading')}
        </div>
      )}

      {/* 未找到（404） */}
      {!loading && notFound && (
        <div className={`m-4 p-6 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
          <LucideIcon name="FileQuestion" size={22} className={`${styles.cardTextMuted} mb-2`} />
          <p className={`text-sm font-semibold ${styles.cardText}`}>{t('workspace.l2.not_found')}</p>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l2.not_found_hint')}</p>
        </div>
      )}

      {/* 无权限（403/401） */}
      {!loading && denied && (
        <div className={`m-4 p-4 rounded border text-sm ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <LucideIcon name="ShieldAlert" size={14} className="inline mr-1" />
          {t('workspace.l2.no_permission')}
        </div>
      )}

      {/* 错误 */}
      {!loading && error && !notFound && !denied && (
        <div className={`m-4 p-4 rounded border text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <LucideIcon name="AlertTriangle" size={12} className="inline mr-1" />
          {t('workspace.l2.error', { msg: error })}
        </div>
      )}

      {/* Data */}
      {!loading && scenario && (
        <>
          <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder}`}>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className={`text-lg font-extrabold ${styles.cardText}`}>{scenario.name}</h1>
              <span className={`text-[10px] border px-1.5 py-0.5 rounded font-mono ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                {scenario.status ?? 'DRAFT'}
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-3xl leading-relaxed`}>
              {scenario.businessGoal || scenario.description || '—'}
            </p>
          </div>

          {/* Blueprint — L2 中心对象（补充设计 §6，workspace 归属） */}
          <section className="p-4">
            <BlueprintPanel scenarioId={scenario.id} />
          </section>

          {/* Key metrics */}
          <section className="p-4">
            <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2`}>{t('workspace.l2.stat.title')}</h2>
            <div className={`grid gap-3 ${gridCls}`}>
              <StatCard label={t('workspace.l2.stat.safety')} value={fmt(scenario.actualSafetyIndex)} color={styles.infoText} />
              <StatCard label={t('workspace.l2.stat.integrity')} value={integrity} color={styles.accentText} />
              <StatCard label={t('workspace.l2.stat.pending')} value={evalRowCount(operationEval.data)} color={styles.warningText} />
              <StatCard label={t('workspace.l2.stat.updated')} value={(scenario.createdAt ?? '').split(' ')[0] || '—'} color={styles.successText} />
            </div>
          </section>

          {/* Cognition summary */}
          <section className="px-4 pb-4">
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
              <div className="flex items-center justify-between gap-2 mb-2">
                <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
                  <LucideIcon name="BrainCircuit" size={13} />
                  {t('workspace.l2.cognition.title')}
                </h2>
                <Link
                  to={`/workbench/${scenario.id}/forecast`}
                  className={`text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentBorder} ${styles.cardText}`}
                >
                  {t('workspace.l2.cognition.go_forecast')}
                </Link>
              </div>

              {/* unavailable 503 → 横幅，不伪造 */}
              {operationEval.unavailable && (
                <div className={`px-3 py-2 rounded border text-xs mb-2 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
                  {t('workspace.l2.cognition.unavailable')}
                  <button onClick={operationEval.reload} className={`ml-2 underline cursor-pointer`}>{t('workspace.l1.retry')}</button>
                </div>
              )}
              {operationEval.loading && <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.loading')}</p>}
              {!operationEval.loading && !operationEval.unavailable && !operationEval.data && (
                <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.empty')}</p>
              )}
              {!operationEval.loading && operationEval.data && (
                <pre className={`text-[11px] font-mono ${styles.cardTextMuted} whitespace-pre-wrap overflow-x-auto`}>
                  {JSON.stringify(operationEval.data, null, 2)}
                </pre>
              )}
            </div>
          </section>

          {/* §3.8 R1~R2：认知运行面（原 #/project_workbench 认知 Tab）下沉 L2「高级/调试」子面，
              CognitiveRightPanel 全应用唯一挂载点（迁一删一，去重） */}
          <section className="px-4 pb-4">
            <AdvancedDebugPanel scenarioId={scenario.id} />
          </section>

          {/* Recent actions */}
          <section className="px-4 pb-6">
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
              <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2`}>{t('workspace.l2.recent.title')}</h2>
              <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.recent.empty')}</p>
            </div>
          </section>
        </>
      )}
    </div>
  );
}

function evalRowCount(data: { rows?: unknown[] } | null): string {
  return data && Array.isArray(data.rows) ? String(data.rows.length) : '—';
}

function StatCard({ label, value, color }: { label: string; value: string; color: string }) {
  const { styles } = useTheme();
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
      <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>{label}</span>
      <span className={`text-lg font-extrabold ${color}`}>{value}</span>
    </div>
  );
}
