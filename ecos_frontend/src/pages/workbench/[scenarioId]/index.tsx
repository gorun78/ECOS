/**
 * 工作台 L2 — 单场景详情（F07-06 三层 IA 第二层，路由 /workbench/:scenarioId）。
 *
 * <p>首屏只展示业务语义（关键指标 + 认知摘要 + 最近动作），
 * 不出现 Mind / Goal Tree / Causal 或后端原始字段。认知摘要经
 * {@link hooks/useCognition} 只读订阅 E1~E4；503 时渲染横幅（不伪造）。
 * 六态（§3.3）实装口径：加载 / 未找到（404）/ 无权限（403/401）/
 * 错误（其余 status，后端 message 直排）/ 空（scenario=null 且无错误时各卡片落空态）；
 * 最近动作区（H-8 P08 通道 listScenarioActions）另覆盖 加载/错误(含 traceId)/空。
 * 本页无缓存层 ⇒ 离线态不呈现缓存快照（不假称）；蓝图版本选择见 BlueprintPanel。</p>
 */
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import LucideIcon from '../../../components/LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { useMediaQuery } from '../../../hooks/useMediaQuery';
import { useCognition } from '../../../hooks/useCognition';
import { apiFetchData } from '../../../api';
import { API_BASE } from '../../../services/httpClient';
import type { OperationEvalRow } from '../../../services/cognitiveClient';
import {
  isScenarioActionError,
  listScenarioActions,
  type ScenarioActionVO,
} from '../../../services/scenarioActionClient';
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
    apiFetchData<ScenarioDetailVO>(`${API_BASE}/v1/workspace/scenarios/${scenarioId}`)
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

  // 最近动作（行253/260 订正：接 scenarioActionClient 真实端点，禁恒 empty 假空态）
  const [actions, setActions] = useState<ScenarioActionVO[] | null>(null);
  const [actionsLoading, setActionsLoading] = useState(false);
  const [actionsError, setActionsError] = useState<{ msg: string; traceId?: string } | null>(null);

  useEffect(() => {
    if (!scenarioId || !scenario) return;
    let cancelled = false;
    setActionsLoading(true);
    setActionsError(null);
    listScenarioActions(scenarioId)
      .then((a) => {
        if (!cancelled) setActions(a);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        logger.error('workbench-recent-actions', e);
        const err = isScenarioActionError(e) ? e : null;
        setActions(null);
        setActionsError({
          msg: err?.message ?? (e instanceof Error ? e.message : String(e)),
          traceId: err?.traceId,
        });
      })
      .finally(() => {
        if (!cancelled) setActionsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId, scenario]);

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
              {/* 行275 订正：status 缺字段不兜底成 'DRAFT' 真实徽章（禁假状态），缺省即不渲染 */}
              {scenario.status ? (
                <span className={`text-[10px] border px-1.5 py-0.5 rounded font-mono ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                  {scenario.status}
                </span>
              ) : null}
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
                <OperationEvalView data={operationEval.data} />
              )}
            </div>
          </section>

          {/* §3.8 R1~R2：认知运行面（原 #/project_workbench 认知 Tab）下沉 L2「高级/调试」子面，
              CognitiveRightPanel 全应用唯一挂载点（迁一删一，去重） */}
          <section className="px-4 pb-4">
            <AdvancedDebugPanel scenarioId={scenario.id} />
          </section>

          {/* Recent actions — H-8 P08：动作闭环面（L3 同级，不新开顶层导航）；
              数据源 = scenarioActionClient.listScenarioActions（与决策看板同一单源通道） */}
          <section className="px-4 pb-6">
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
              <div className="flex items-center justify-between gap-2 mb-2">
                <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.l2.recent.title')}</h2>
                <Link
                  to={`/workbench/${scenario.id}/decisions`}
                  className={`text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.cardText} ${styles.accentBorder}`}
                  data-testid="l2-go-decisions"
                >
                  {t('workspace.l2.recent.go_decisions')}
                </Link>
              </div>
              <RecentActions
                loading={actionsLoading}
                error={actionsError}
                actions={actions}
              />
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

/** 最近动作上限（超出仅如实说明，不假装全量）。 */
const RECENT_ACTIONS_LIMIT = 5;

/**
 * 最近动作渲染（P08 通道数据）：加载 / 错误(后端 message + traceId) / 空 / 数据 四分支。
 * 行内容取服务端字段（description→controlMetric 回退）；status 仅 APPROVED/PENDING 走
 * 已译徽章，其余值按服务端词表原样回显（OutcomeQuadView 同口径，禁映射成成功/失败）。
 */
function RecentActions({
  loading,
  error,
  actions,
}: {
  loading: boolean;
  error: { msg: string; traceId?: string } | null;
  actions: ScenarioActionVO[] | null;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (loading) {
    return (
      <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted}`}>
        <span className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
        {t('workspace.l2.recent.loading')}
      </div>
    );
  }
  if (error) {
    return (
      <div className={`text-[11px] rounded border px-2 py-1.5 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
        {t('workspace.l2.recent.error', { msg: error.msg })}
        {error.traceId ? <span className={`ml-1 font-mono ${styles.cardTextMuted}`}>traceId={error.traceId}</span> : null}
      </div>
    );
  }
  if (!actions || actions.length === 0) {
    return <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.recent.empty')}</p>;
  }
  const visible = actions.slice(0, RECENT_ACTIONS_LIMIT);
  return (
    <div className="space-y-1.5">
      <ul className="space-y-1">
        {visible.map((a, i) => {
          const status = typeof a.status === 'string' ? a.status : '';
          const approved = status === 'APPROVED';
          const statusLabel = approved
            ? t('scenario.p08.board.status.approved')
            : status === 'PENDING'
              ? t('scenario.p08.board.status.pending')
              : status || '—';
          const primary = a.description || a.controlMetric || '—';
          return (
            <li key={a.actionId ?? i} className={`text-xs ${styles.cardText} flex items-start justify-between gap-2`}>
              <span className="min-w-0 flex-1 truncate">{primary}</span>
              <span className={`shrink-0 flex items-center gap-2 font-mono text-[10px] ${styles.cardTextMuted}`}>
                {a.dueDate ? <span>{t('workspace.l2.recent.due', { date: a.dueDate })}</span> : null}
                <span
                  className={`px-1.5 py-0.5 rounded border ${
                    approved ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.cardBorder} ${styles.cardTextMuted}`
                  }`}
                >
                  {statusLabel}
                </span>
              </span>
            </li>
          );
        })}
      </ul>
      {actions.length > visible.length && (
        <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.l2.recent.more', { n: String(RECENT_ACTIONS_LIMIT) })}</p>
      )}
    </div>
  );
}

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
                <td className={`py-1 pr-3 font-mono`}>{r.capability || '—'}</td>
                <td className="py-1 pr-3 font-mono">{typeof r.score === 'number' ? `${(r.score * 100).toFixed(1)}%` : '—'}</td>
                <td className="py-1">
                  {trend ? t(`workspace.l2.eval.trend.${trend}`) : '—'}
                  {r.trendSource ? <span className={`ml-1 ${styles.cardTextMuted}`}>· {t('workspace.l2.eval.trend_source', { src: String(r.trendSource) })}</span> : null}
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
