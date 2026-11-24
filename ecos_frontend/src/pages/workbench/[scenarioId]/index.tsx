/**
 * 工作台 L2 — 单场景工作区（场景运行 / 场景定义次级面，场景工作台简化 v0.4）。
 *
 * <p>默认落在「场景运行」；保留旧详情路由的加载/404/403/错误/离线六态。</p>
 *
 * <p>2026-11-24 界面精简（用户裁决）：运行页去掉「高级/调试」整块——
 * 认知运行面（{@link AdvancedDebugPanel}：运行编排 + 认知四卡 E1~E4 唯一挂载点）
 * 改挂「场景定义」次级面（?tab=definition），能力保留、入口收窄。
 * 场景上下文构建入口见「场景定义」页右上的按钮（见 definition/ScenarioContextBuilder）。</p>
 */
import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  BrainCircuit,
  FileQuestion,
  ShieldAlert,
} from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import { useCognition } from '../../../hooks/useCognition';
import { apiFetchData } from '../../../api';
import { WORKSPACE_SCENARIOS_BASE } from '../../../services/scenarioActionClient';
import { fetchScenarioDefinition } from '../../../services/scenarioDefinitionClient';
import logger from '../../../services/logger';
import AdvancedDebugPanel from './advanced/AdvancedDebugPanel';
import DefinitionTab from './definition/DefinitionTab';
import RunTab from './run/RunTab';

type TabKey = 'definition' | 'run';

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

export default function WorkbenchScenarioDetail() {
  const { scenarioId = '' } = useParams<{ scenarioId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [scenario, setScenario] = useState<ScenarioDetailVO | null>(null);
  const [definition, setDefinition] = useState<Awaited<ReturnType<typeof fetchScenarioDefinition>> | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tab = useMemo<TabKey>(() => {
    // 2026-11-24：Tab 栏摘除，默认落运行面；定义编辑保留为次级入口（?tab=definition 深链可达，
    // 运行页内「编辑场景定义」链接切换）。摘按钮 ≠ 摘能力：绑定/成功标准仍需可维护。
    return searchParams.get('tab') === 'definition' ? 'definition' : 'run';
  }, [searchParams]);

  useEffect(() => {
    if (!scenarioId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setNotFound(false);
    setDenied(false);
    apiFetchData<ScenarioDetailVO>(`${WORKSPACE_SCENARIOS_BASE}/${scenarioId}`)
      .then((d) => { if (!cancelled) setScenario(d); })
      .catch((e: unknown) => {
        if (cancelled) return;
        logger.error('workbench-detail', e);
        const status = (e as { status?: number }).status;
        if (status === 404) setNotFound(true);
        else if (status === 403 || status === 401) setDenied(true);
        else setError(e instanceof Error ? e.message : String(e));
        setScenario(null);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [scenarioId]);

  useEffect(() => {
    if (!scenario || !scenarioId) return;
    let cancelled = false;
    fetchScenarioDefinition(scenarioId)
      .catch((e): Awaited<ReturnType<typeof fetchScenarioDefinition>> => {
        logger.error('definition-load', e);
        return null as unknown as Awaited<ReturnType<typeof fetchScenarioDefinition>>;
      })
      .then((d) => {
        if (!cancelled) setDefinition(d);
      });
    return () => { cancelled = true; };
  }, [scenario, scenarioId]);

  const { operationEval } = useCognition(scenarioId, { enabled: !!scenario && !loading });
  const metrics = (scenario?.metrics ?? {}) as Record<string, number>;
  const integrity = typeof metrics.integrityScore === 'number' ? `${metrics.integrityScore.toFixed(1)}%` : '—';

  const switchTab = (next: TabKey) => {
    const p = new URLSearchParams(searchParams);
    p.set('tab', next);
    setSearchParams(p, { replace: true });
  };

  // 2026-11-24：按分析类型直接发起的「发起运行」通道已自运行页摘除，
  // 运行发起只走 QuestionRunPanel 的问题驱动链路（question-runs 端点）。
  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      <div className={`p-3 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex items-center gap-2`}>
        <Link
          to="/workbench"
          className={`inline-flex items-center gap-1 text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
        >
          <ArrowLeft size={12} />
          {t('workspace.l2.back')}
        </Link>
        {/*
          2026-11-24 裁决：顶部只留「场景运行」+「场景配置」两枚并排按钮。
          「场景定义」Tab 摘除——定义编辑改由「场景配置」页承接（config 页已含模板/算法库，
          定义编辑能力随本批迁入）。运行按钮直接进运行页，不做 Tab 切换。
        */}
        <div className="ml-auto flex items-center gap-2">
          <button
            type="button"
            onClick={() => switchTab('run')}
            className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            {t('workspace.l2.tab.run')}
          </button>
          <button
            type="button"
            onClick={() => navigate('/workbench/config')}
            className={`inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          >
            {t('workspace.l2.config')}
          </button>
        </div>
      </div>

      {loading && (
        <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted} p-6 justify-center`}>
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
          {t('workspace.l2.loading')}
        </div>
      )}
      {!loading && notFound && (
        <div className={`m-4 p-6 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
          <FileQuestion size={22} className={`${styles.cardTextMuted}`} />
          <p className={`text-sm font-semibold ${styles.cardText}`}>{t('workspace.l2.not_found')}</p>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l2.not_found_hint')}</p>
        </div>
      )}
      {!loading && denied && (
        <div className={`m-4 p-4 rounded border text-sm ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <ShieldAlert size={14} className="inline mr-1" />
          {t('workspace.l2.no_permission')}
        </div>
      )}
      {!loading && error && !notFound && !denied && (
        <div className={`m-4 p-4 rounded border text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
          <AlertTriangle size={12} className="inline mr-1" />
          {t('workspace.l2.error', { msg: error })}
        </div>
      )}

      {!loading && scenario && (
        <>
          <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder}`}>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className={`text-lg font-extrabold ${styles.cardText}`}>{scenario.name}</h1>
              {scenario.status ? (
                <span className={`text-[10px] border px-1.5 py-0.5 rounded font-mono ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                  {scenario.status}
                </span>
              ) : null}
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-3xl leading-relaxed`}>
              {scenario.businessGoal || scenario.description || '—'}
            </p>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-2' : 'grid-cols-4'} mt-3`}>
              <StatCard label={t('workspace.l2.stat.safety')} value={fmt(scenario.actualSafetyIndex)} color={styles.infoText} />
              <StatCard label={t('workspace.l2.stat.integrity')} value={integrity} color={styles.accentText} />
              <StatCard label={t('workspace.l2.stat.pending')} value={evalRowCount(operationEval.data)} color={styles.warningText} />
              <StatCard label={t('workspace.l2.stat.updated')} value={(scenario.createdAt ?? '').split(' ')[0] || '—'} color={styles.successText} />
            </div>
          </div>

          {/*
            2026-11-24：Tab 栏摘除，运行面直出。「编辑场景定义」为运行面上的次级文字入口
            （?tab=definition），保证绑定/成功标准仍可维护——摘按钮不等于摘能力。
          */}
          {tab === 'definition' ? (
            <>
              <div className="px-4 pt-3">
                <button
                  type="button"
                  onClick={() => switchTab('run')}
                  className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
                >
                  <ArrowLeft size={11} />
                  {t('workspace.l2.tab.run')}
                </button>
              </div>
              <DefinitionTab
                scenarioId={scenario.id}
                definition={definition}
                onSaved={(d) => setDefinition(d)}
                onPublished={(d) => setDefinition(d)}
              />
              {/*
                认知运行面（2026-11-24 自「场景运行」页迁入定义面）：
                运行编排 CognitionPanel + 认知四卡 CognitiveRightPanel（E1~E4 全应用唯一挂载点，
                E2E COG-IA 单实例断言依赖此唯一性）。运行页只留问题驱动主链路，高级面不占首屏。
              */}
              <div className="px-4 pb-4">
                <AdvancedDebugPanel scenarioId={scenario.id} />
              </div>

              {/*
                认知摘要（随高级面一并自运行页移入定义面）：
                503 时渲染如实横幅 + 重试，禁静默——六态要求认知引擎不可用必须可见。
              */}
              <section className="px-4 pb-4">
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
                      <BrainCircuit size={13} />
                      {t('workspace.l2.cognition.title')}
                    </h2>
                    <Link
                      to={`/workbench/${scenario.id}/forecast`}
                      className={`text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentBorder} ${styles.cardText}`}
                    >
                      {t('workspace.l2.cognition.go_forecast')}
                    </Link>
                  </div>

                  {operationEval.unavailable && (
                    <div className={`px-3 py-2 rounded border text-xs mb-2 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
                      {t('workspace.l2.cognition.unavailable')}
                      <button onClick={operationEval.reload} className="ml-2 underline cursor-pointer">
                        {t('workspace.l1.retry')}
                      </button>
                    </div>
                  )}
                  {operationEval.loading && <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.loading')}</p>}
                  {!operationEval.loading && !operationEval.unavailable && !operationEval.data && (
                    <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.empty')}</p>
                  )}
                  {!operationEval.loading && operationEval.data && <OperationEvalView data={operationEval.data} />}
                </div>
              </section>
            </>
          ) : (
            <RunTab
              scenarioId={scenario.id}
              onEditDefinition={() => switchTab('definition')}
            />
          )}
        </>
      )}
    </div>
  );
}

function fmt(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—';
  return String(v);
}

function evalRowCount(data: { rows?: unknown[] } | null): string {
  return data && Array.isArray(data.rows) ? String(data.rows.length) : '—';
}

type OperationEvalRow = {
  name?: string;
  capability?: string;
  score?: number;
  trend?: string;
  trendSource?: string;
};

/**
 * E2 运营评估结构化呈现（复盘 S5：替代原 JSON.stringify 直排）。
 * 列取契约声明字段（name/capability/score/trend/trendSource）；
 * trend 恒 FLAT 时如实呈现后端 trendSource（本期 NOT_IMPLEMENTED），不合成语义。
 */
function OperationEvalView({ data }: { data: { rows?: OperationEvalRow[]; truncated?: boolean } }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const rows = Array.isArray(data.rows) ? data.rows : [];

  if (rows.length === 0) {
    return <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.eval.empty')}</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className={`w-full text-[11px] ${styles.cardText}`}>
        <caption className={`text-left text-[10px] font-bold uppercase mb-1 ${styles.cardTextMuted}`}>
          {t('workspace.l2.eval.title')}
        </caption>
        <thead>
          <tr className={`border-b ${styles.cardBorder} text-left font-bold ${styles.cardTextMuted}`}>
            <th className="py-1 pr-3 font-bold">{t('workspace.l2.eval.col.name')}</th>
            <th className="py-1 pr-3 font-bold">{t('workspace.l2.eval.col.capability')}</th>
            <th className="py-1 pr-3 font-bold">{t('workspace.l2.eval.col.score')}</th>
            <th className="py-1 font-bold">{t('workspace.l2.eval.col.trend')}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => {
            const trend = r.trend === 'UP' || r.trend === 'DOWN' || r.trend === 'FLAT' ? r.trend : null;
            return (
              <tr key={i} className={`border-b ${styles.cardBorder}`}>
                <td className="py-1 pr-3">{r.name || '—'}</td>
                <td className="py-1 pr-3 font-mono">{r.capability || '—'}</td>
                <td className="py-1 pr-3 font-mono">{typeof r.score === 'number' ? `${(r.score * 100).toFixed(1)}%` : '—'}</td>
                <td className="py-1">
                  {trend ? t(`workspace.l2.eval.trend.${trend}`) : '—'}
                  {r.trendSource ? (
                    <span className={`ml-1 ${styles.cardTextMuted}`}>· {t('workspace.l2.eval.trend_source', { src: String(r.trendSource) })}</span>
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {data.truncated === true && (
        <p className={`mt-1 text-[11px] ${styles.warningText}`}>{t('workspace.l2.eval.truncated')}</p>
      )}
    </div>
  );
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
