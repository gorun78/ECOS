/**
 * OutcomeQuadView — P09 四联视图（H-8 第一批；详设-09 §五 P09 / PRD-09 §4.2）
 *
 * <p>四联 = <b>当时预测 / 实际 / 误差 / 待复审假设</b>。端点在位实证（grep 结论，非臆造）：
 * `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/controller/
 * OutcomeEvaluateController.java:38` `@RequestMapping("/api/v1/cognitive")` +
 * `:72 GET /outcomes/by-run/{runId}` ⇒ **端点真实存在**，本视图直接消费
 * `services/cognitiveClient#fetchOutcomesByRun`（路径单源，禁组件内硬编码）。</p>
 *
 * <p>真值纪律（架构铁律 v2.0 :14）：
 * ① 四联数值一律取服务端 `MetricEvaluation`（predicted / actual / absoluteError / relativeError）
 * 与 `OutcomeEvaluateResult`（mae / mape）原值；<b>前端不做 predicted−actual 之类的兜底计算</b>；
 * ② 后端未给数值即渲染「—」，`applicable=false` 行保留并显示「不适用 + 原因」；
 * ③ 待复审假设只取 `OutcomeDiagnosis.pendingHypotheses`（服务端产出），缺失即空态；
 * ④ run 锚点缺失 ⇒ 空态提示，绝不默认取「最近一次 run」猜测；
 * ⑤ 501/503/404 一律走既有 `CognitiveErrorState` 降级渲染（stub 与非 stub 视觉严格可分）。</p>
 *
 * <p>DEMO 徽标：四源（预算/实际账/计量/关账）未串通前恒显示（见
 * `services/forecastScenarioMode.ts` 判定条件）。</p>
 *
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { CircleDashed, ClipboardList, Gauge, ListChecks, Scale, Sparkles } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import MobileDataTable from '../../../../components/common/MobileDataTable';
import type { ColumnConfig } from '../../../../components/common/DataTable';
import Badge from '../../../../components/common/Badge';
import DemoBadge from '../../../../components/common/status/DemoBadge';
import {
  CognitiveEmptyState,
  CognitiveErrorState,
  CognitiveSkeleton,
  cognitiveErrorTitleKey,
} from '../../../../pages/scenario/cognition/CognitionStateViews';
import {
  CognitiveApiError,
  isCognitiveApiError,
} from '../../../../services/cognitiveClient';
import {
  fetchOutcomesByRun,
  type OutcomeEvaluateResultDto,
  type OutcomeMetricEvaluation,
} from '../../../../services/cognitive/outcomesApi';
import OutcomeProposalReturnForm from './OutcomeProposalReturnForm';

export interface OutcomeQuadViewProps {
  /** 预测运行锚点（URL `?runId=` 或动作回链带入）；空串 ⇒ 空态，不猜测。 */
  runId: string;
  scenarioId: string;
}

/** 数值原样回显（字符串保精度）；null/undefined/空串 ⇒ 「—」，禁 0 填充。 */
function raw(v: string | number | null | undefined): string {
  if (v === null || v === undefined) return '—';
  const s = String(v).trim();
  return s === '' ? '—' : s;
}

export default function OutcomeQuadView({ runId, scenarioId }: OutcomeQuadViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [rows, setRows] = useState<OutcomeEvaluateResultDto[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<CognitiveApiError | null>(null);
  const [picked, setPicked] = useState(0);
  /** 幕10 回流：当前展开「转入候选评审」表单的诊断项 key（单开，切换评估/重新加载即收起）。 */
  const [returnOpenKey, setReturnOpenKey] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!runId) {
      setRows(null);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    setReturnOpenKey(null);
    try {
      const data = await fetchOutcomesByRun(runId);
      setRows(data);
      setPicked(0);
    } catch (e) {
      setRows(null);
      setError(
        isCognitiveApiError(e)
          ? e
          : new CognitiveApiError({ code: 'NETWORK', status: 0, message: String(e) }),
      );
    } finally {
      setLoading(false);
    }
  }, [runId]);

  useEffect(() => {
    void load();
  }, [load]);

  const current = rows && rows.length > 0 ? rows[picked] ?? rows[0] : null;

  const columns = useMemo<ColumnConfig<OutcomeMetricEvaluation & Record<string, unknown>>[]>(
    () => [
      { key: 'metricRef', label: t('scenario.p09.quad.col.metric') },
      {
        key: 'predicted',
        label: t('scenario.p09.quad.col.predicted'),
        align: 'right',
        render: (v) => <span className="font-mono" data-testid="quad-predicted">{raw(v as string | number | null)}</span>,
      },
      {
        key: 'actual',
        label: t('scenario.p09.quad.col.actual'),
        align: 'right',
        render: (v) => <span className="font-mono" data-testid="quad-actual">{raw(v as string | number | null)}</span>,
      },
      {
        key: 'absoluteError',
        label: t('scenario.p09.quad.col.abs_error'),
        align: 'right',
        render: (v) => <span className="font-mono">{raw(v as string | number | null)}</span>,
      },
      {
        key: 'relativeError',
        label: t('scenario.p09.quad.col.rel_error'),
        align: 'right',
        render: (v) => <span className="font-mono">{raw(v as string | number | null)}</span>,
      },
      {
        key: 'applicable',
        label: t('scenario.p09.quad.col.applicable'),
        render: (_v, record) =>
          record.applicable === false ? (
            <span className={styles.warningText} data-testid="quad-not-applicable">
              {record.notApplicableReason || t('scenario.p09.quad.not_applicable')}
            </span>
          ) : (
            <Badge variant="neutral">{t('scenario.p09.quad.applicable')}</Badge>
          ),
      },
      {
        key: 'calculationRef',
        label: t('scenario.p09.quad.col.calc_ref'),
        render: (v) => <span className="font-mono text-[10px]">{raw(v as string | null)}</span>,
      },
    ],
    [styles.warningText, t],
  );

  const pendingHypotheses = useMemo(() => {
    if (!current) return [];
    return current.diagnoses.flatMap((d) => (Array.isArray(d.pendingHypotheses) ? d.pendingHypotheses : []));
  }, [current]);

  return (
    <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`} data-testid="outcome-quad-view" data-scenario-id={scenarioId}>
      <header className="flex items-center justify-between gap-2 mb-2 flex-wrap">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <Gauge size={13} />
          {t('scenario.p09.quad.title')}
        </h2>
        <div className="flex items-center gap-2">
          {runId && (
            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`} data-testid="quad-run-anchor">
              {t('scenario.p09.quad.run', { runId })}
            </span>
          )}
          <DemoBadge />
        </div>
      </header>

      {/* 无 run 锚点：空态提示，禁默认「最近一次 run」猜测 */}
      {!runId && <CognitiveEmptyState labelKey="scenario.p09.quad.no_run" testId="quad-no-run" />}

      {runId && loading && <CognitiveSkeleton rows={4} />}

      {runId && !loading && error && (
        <CognitiveErrorState error={error} titleKey={cognitiveErrorTitleKey(error)} onRetry={() => void load()} />
      )}

      {runId && !loading && !error && rows && rows.length === 0 && (
        <CognitiveEmptyState labelKey="scenario.p09.quad.empty" testId="quad-empty" />
      )}

      {runId && !loading && !error && current && (
        <div className="space-y-3">
          {/* 多次评估选择（一个 run 可能多次评估；全部来自服务端 id，不合成） */}
          {rows && rows.length > 1 && (
            <div className="flex items-center gap-1.5 flex-wrap">
              {rows.map((r, i) => (
                <button
                  key={r.evaluationId ?? i}
                  type="button"
                  onClick={() => setPicked(i)}
                  className={`px-2 py-1 text-[10px] font-mono rounded border cursor-pointer ${
                    i === picked ? `${styles.accentBorder} ${styles.accentText}` : `${styles.cardBorder} ${styles.cardTextMuted}`
                  }`}
                  data-testid={`quad-eval-${String(r.evaluationId ?? i)}`}
                >
                  {r.evaluationId ?? `#${i + 1}`}
                </button>
              ))}
            </div>
          )}

          {/* 状态与口径（服务端词表原样；未识别值直接回显，不映射成成功） */}
          <div className="flex items-center gap-2 flex-wrap">
            <Badge variant={current.status === 'EVALUATED' ? 'success' : current.status ? 'warning' : 'neutral'}>
              {current.status || t('scenario.p09.quad.status.unknown')}
            </Badge>
            {current.caliberAlignment && (
              <span className={`text-[10px] ${styles.cardTextMuted}`}>
                {t('scenario.p09.quad.caliber')}: {current.caliberAlignment}
              </span>
            )}
            {typeof current.totalSampleCount === 'number' && (
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`} data-testid="quad-sample-count">
                {t('scenario.p09.quad.samples', { applicable: current.applicableSampleCount ?? 0, total: current.totalSampleCount })}
              </span>
            )}
          </div>

          {/* MAE / MAPE：服务端确定性计算值；缺失显示「—」，禁前端由 metrics 反算 */}
          <div className="grid grid-cols-2 gap-2">
            <StatTile
              icon={<Scale size={12} />}
              label={t('scenario.p09.quad.mae')}
              value={raw(current.mae)}
              testId="quad-mae"
            />
            <StatTile
              icon={<CircleDashed size={12} />}
              label={t('scenario.p09.quad.mape')}
              value={raw(current.mape)}
              testId="quad-mape"
            />
          </div>

          {/* 四联 1~3：当时预测 / 实际 / 误差（同一行并置，逐指标对齐） */}
          {current.metrics.length === 0 ? (
            <CognitiveEmptyState labelKey="scenario.p09.quad.empty_metrics" testId="quad-metrics-empty" />
          ) : (
            <MobileDataTable<OutcomeMetricEvaluation & Record<string, unknown>>
              columns={columns}
              data={current.metrics as unknown as Array<OutcomeMetricEvaluation & Record<string, unknown>>}
              rowKey={(r) => String(r.metricRef ?? JSON.stringify(r))}
              mobileConfig={{ headerKeys: ['metricRef', 'predicted'], detailKeys: ['actual', 'absoluteError', 'relativeError', 'applicable', 'calculationRef'] }}
              hidePagination
            />
          )}

          {/* 四联 4：诊断裁定 + 待复审假设（服务端产出） */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <div className={`rounded-lg border p-3 ${styles.cardBorder}`}>
              <h3 className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-2 flex items-center gap-1.5`}>
                <ClipboardList size={12} />
                {t('scenario.p09.quad.diagnoses')}
              </h3>
              {current.diagnoses.length === 0 ? (
                <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.p09.quad.diagnoses_empty')}</p>
              ) : (
                <ul className="space-y-2">
                  {current.diagnoses.map((d, i) => {
                    const refKey = `${picked}:${d.metricRef ?? 'd'}:${i}`;
                    const assetRefs = Array.isArray(d.assetRefs) ? d.assetRefs.filter((r) => typeof r === 'string' && r !== '') : [];
                    const open = returnOpenKey === refKey;
                    return (
                      <li key={refKey} className="text-[11px]">
                        <div className="flex items-center gap-1.5">
                          <Badge variant={d.verdict === 'SUPPORTED' ? 'success' : d.verdict === 'REFUTED' ? 'danger' : 'neutral'}>
                            {d.verdict || t('scenario.p09.quad.verdict_unknown')}
                          </Badge>
                          <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{d.metricRef ?? '—'}</span>
                        </div>
                        {d.explanation && <p className={`mt-1 ${styles.cardText}`}>{d.explanation}</p>}
                        {/* 幕10 结果回流：偏差诊断 → 候选提案（proposals 通道，不改生产资产行）。
                            目标资产唯一定位来源 = 服务端 assetRefs；无引用时禁用并如实说明，禁猜资产。 */}
                        <div className="mt-1.5 flex items-center gap-2 flex-wrap">
                          {assetRefs.length > 0 ? (
                            <button
                              type="button"
                              onClick={() => setReturnOpenKey(open ? null : refKey)}
                              className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[10px] font-bold cursor-pointer ${styles.cardBg} ${styles.accentText} ${styles.cardBorder}`}
                              data-testid="quad-return-btn"
                            >
                              <ListChecks size={11} />
                              {t('scenario.p09.return.btn')}
                            </button>
                          ) : (
                            <span className={`text-[10px] ${styles.cardTextMuted}`} data-testid="quad-return-disabled">
                              {t('scenario.p09.return.no_refs')}
                            </span>
                          )}
                        </div>
                        {open && assetRefs.length > 0 && (
                          <OutcomeProposalReturnForm
                            assetRefs={assetRefs}
                            evaluationId={current.evaluationId}
                            runId={runId}
                            metricRef={d.metricRef}
                            resultTraceId={current.traceId}
                            onClose={() => setReturnOpenKey(null)}
                          />
                        )}
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            <div className={`rounded-lg border p-3 ${styles.cardBorder}`}>
              <h3 className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-2 flex items-center gap-1.5`}>
                <Sparkles size={12} />
                {t('scenario.p09.quad.pending_hypotheses')}
              </h3>
              {pendingHypotheses.length === 0 ? (
                <p className={`text-[11px] ${styles.cardTextMuted}`} data-testid="quad-hypotheses-empty">
                  {t('scenario.p09.quad.pending_hypotheses_empty')}
                </p>
              ) : (
                <ul className="space-y-1.5">
                  {pendingHypotheses.map((h, i) => (
                    <li key={`${h}-${i}`} className={`text-[11px] font-mono ${styles.cardText}`} data-testid="quad-pending-hypothesis">
                      {h}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* 阻断/告警：原样列出服务端 blocker（能力未上线时后端即在此给出真因） */}
          {(current.blockers.length > 0 || current.warnings.length > 0) && (
            <div className={`rounded-lg border p-3 text-[11px] ${styles.warningBorder} ${styles.warningBg}`}>
              {current.blockers.map((b, i) => (
                <p key={`b-${i}`} className={styles.warningText} data-testid="quad-blocker">{String(b)}</p>
              ))}
              {current.warnings.map((w, i) => (
                <p key={`w-${i}`} className={styles.cardTextMuted}>{String(w)}</p>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

/** 统计小块：只回显服务端值（value 已归一为字符串，缺值即「—」）。 */
function StatTile({ icon, label, value, testId }: { icon: React.ReactNode; label: string; value: string; testId: string }) {
  const { styles } = useTheme();
  return (
    <div className={`rounded-lg border p-3 ${styles.cardBorder}`} data-testid={testId}>
      <span className={`flex items-center gap-1 text-[10px] font-bold uppercase ${styles.cardTextMuted}`}>
        {icon}
        {label}
      </span>
      <span className={`block mt-1 text-base font-extrabold font-mono ${styles.cardText}`}>{value}</span>
    </div>
  );
}
