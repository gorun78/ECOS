/**
 * ECOS 场景工作台 — 认知诊断与预测 Tab（PMO-53 T3）。
 *
 * <p>发起 ScenarioRun（POST /api/v1/workspace/scenarios/{id}/runs）→
 * 渲染因果诊断（因果链 + ReasoningPath step 高亮，RuleRef/PrecedentRef 可点击展开）
 * + 统计基线预测（SVG 置信区间图）+ 情景模拟/策略摘要 + 运行历史 + 模型注册表。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import LucideIcon from '../../components/LucideIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme, type ThemeStyles } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import type { BusinessScenario } from '../project-workbench/types';
import CounterfactualTab from './CounterfactualTab';
import ReplayTab from './ReplayTab';
import OverrideTab from './OverrideTab';
import MentalReviewTab from './MentalReviewTab';

/** ScenarioRun 响应中诊断类结果 */
interface DiagnosisResult {
  diagnosisId?: string;
  degraded?: boolean;
  degradeReason?: string;
  rootCause?: string;
  causalChain?: Array<{
    depth?: number;
    node?: string;
    description?: string;
    confidence?: number;
    source?: string;
    ruleName?: string;
  }>;
  suggestions?: string[];
  affectedMetrics?: string[];
  reasoningPath?: {
    steps?: Array<{
      stepId?: string;
      description?: string;
      confidence?: number;
      sourceType?: string;
      stepIndex?: number;
      ruleApplied?: string;
      ruleRef?: { ruleId?: string; ruleName?: string; condition?: string; action?: string; category?: string };
      precedentRef?: { precedentId?: string; decisionId?: string; summary?: string; outcome?: string; similarity?: number };
    }>;
    conclusion?: string;
    justification?: string;
  };
}

/** 预测结果（PMO-51） */
interface ForecastResult {
  metric?: string;
  modelId?: string;
  confidence?: number;
  kgAdjusted?: boolean;
  summary?: string;
  points?: Array<{ step?: number; t?: number; value?: number; lowerBound?: number; upperBound?: number }>;
  justifications?: string[];
}

interface RunResult {
  runId?: string;
  scenarioId?: string;
  status?: string;
  degraded?: boolean;
  diagnosis?: DiagnosisResult | null;
  forecast?: ForecastResult | null;
  simulation?: Record<string, unknown> | null;
  strategy?: Record<string, unknown> | null;
  metricsWritten?: string | number | null;
}

interface RunHistoryRow {
  id: string;
  scenario_id?: string;
  run_type?: string;
  status?: string;
  metric?: string;
  deviation?: number | null;
  degraded?: boolean;
  diagnosis_result?: string | null;
  forecast_result?: string | null;
  simulation_result?: string | null;
  strategy_result?: string | null;
  create_time?: string;
}

interface CognitiveModelRow {
  id: string;
  model_id?: string;
  model_type?: string;
  version?: number;
  backtest_score?: number;
  status?: string;
  create_time?: string;
}

const RUN_TYPES: Array<{ key: 'DIAGNOSE' | 'FORECAST' | 'SIMULATE' | 'STRATEGY'; icon: string }> = [
  { key: 'DIAGNOSE', icon: 'Search' },
  { key: 'FORECAST', icon: 'TrendingUp' },
  { key: 'SIMULATE', icon: 'FlaskConical' },
  { key: 'STRATEGY', icon: 'Lightbulb' },
];

/** 心智层子视图（PMO-59 P4b 人机干预面板：诊断/推演/回放/覆写/复盘） */
const SUB_TABS: Array<{ key: 'diagnosis' | 'counterfactual' | 'replay' | 'override' | 'review'; icon: string; labelKey: string }> = [
  { key: 'diagnosis', icon: 'BrainCircuit', labelKey: 'scenario.iv.tab.diagnosis' },
  { key: 'counterfactual', icon: 'SlidersHorizontal', labelKey: 'scenario.iv.tab.counterfactual' },
  { key: 'replay', icon: 'History', labelKey: 'scenario.iv.tab.replay' },
  { key: 'override', icon: 'UserCog', labelKey: 'scenario.iv.tab.override' },
  { key: 'review', icon: 'BellRing', labelKey: 'scenario.iv.tab.review' },
];

/** jsonb 字段在 JDBC queryForList 中为字符串，防御性解析 */
function safeParseJson<T>(raw: string | null | undefined): T | null {
  if (!raw) return null;
  if (typeof raw !== 'string') return raw as unknown as T;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

export default function CognitionPanel({
  scenario,
  onRunFinished,
}: {
  scenario: BusinessScenario | undefined;
  onRunFinished?: () => void;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [runTypes, setRunTypes] = useState<string[]>(['DIAGNOSE', 'FORECAST']);
  const [metric, setMetric] = useState('');
  const [deviation, setDeviation] = useState(0);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<RunResult | null>(null);
  const [history, setHistory] = useState<RunHistoryRow[]>([]);
  const [models, setModels] = useState<CognitiveModelRow[]>([]);
  const [activeStepId, setActiveStepId] = useState<string | null>(null);
  const [openRuleId, setOpenRuleId] = useState<string | null>(null);
  const [openPrecedentId, setOpenPrecedentId] = useState<string | null>(null);
  const [subTab, setSubTab] = useState<(typeof SUB_TABS)[number]['key']>('diagnosis');

  const fetchHistory = useCallback(async () => {
    if (!scenario) return;
    try {
      const d = await apiFetchData<RunHistoryRow[]>(`/api/v1/workspace/scenarios/${scenario.id}/runs?limit=20`);
      setHistory(Array.isArray(d) ? d : []);
    } catch (e) {
      console.error('Failed to fetch scenario run history', e);
    }
  }, [scenario]);

  const fetchModels = useCallback(async () => {
    try {
      const d = await apiFetchData<CognitiveModelRow[]>('/api/v1/cognitive/models');
      setModels(Array.isArray(d) ? d : []);
    } catch (e) {
      console.error('Failed to fetch cognitive models', e);
    }
  }, []);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  const toggleRunType = (k: string) =>
    setRunTypes((prev) => (prev.includes(k) ? prev.filter((x) => x !== k) : [...prev, k]));

  const handleRun = async () => {
    if (!scenario || running) return;
    if (runTypes.length === 0) {
      alert(t('scenario.cog.needTypes'));
      return;
    }
    setRunning(true);
    setResult(null);
    setActiveStepId(null);
    try {
      const d = await apiFetchData<RunResult>(`/api/v1/workspace/scenarios/${scenario.id}/runs`, {
        method: 'POST',
        body: JSON.stringify({
          runTypes,
          metric: metric.trim() || undefined,
          deviation,
        }),
      });
      setResult(d);
      fetchHistory();
      onRunFinished?.();
    } catch (e) {
      console.error('Scenario run failed', e);
      setResult({ runId: undefined, status: 'ERROR' } as RunResult);
    } finally {
      setRunning(false);
    }
  };

  const diagnosis = result?.diagnosis && !isErrorPayload(result.diagnosis) ? result.diagnosis : null;
  const forecast = result?.forecast && !isErrorPayload(result.forecast) ? result.forecast : null;
  const simulation = result?.simulation && !isErrorPayload(result.simulation) ? result.simulation : null;
  const strategy = result?.strategy && !isErrorPayload(result.strategy) ? result.strategy : null;
  const steps = diagnosis?.reasoningPath?.steps ?? [];

  return (
    <div className="space-y-4">
      {/* ── 心智层子视图切换（PMO-59 P4b 人机干预面板入口） ── */}
      <div className="flex flex-wrap items-center gap-1.5">
        {SUB_TABS.map((st) => (
          <button
            key={st.key}
            onClick={() => setSubTab(st.key)}
            className={`text-[10px] font-bold px-2.5 py-1 rounded border transition-colors cursor-pointer flex items-center gap-1.5 ${
              subTab === st.key
                ? 'bg-indigo-600 text-white border-indigo-500'
                : `${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted} hover:border-indigo-500/50`
            }`}
          >
            <LucideIcon name={st.icon} size={11} />
            {t(st.labelKey)}
          </button>
        ))}
      </div>

      {subTab === 'counterfactual' && <CounterfactualTab />}
      {subTab === 'replay' && <ReplayTab />}
      {subTab === 'override' && <OverrideTab />}
      {subTab === 'review' && <MentalReviewTab />}

      <div className={subTab === 'diagnosis' ? 'space-y-4' : 'hidden'}>
      {/* ── 运行配置 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="BrainCircuit" size={14} />
          <span className="text-sm font-semibold">{t('scenario.cog.title')}</span>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-normal`}>{t('scenario.cog.desc')}</p>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <div>
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.cog.types')}
            </span>
            <div className="flex flex-wrap gap-1.5 mt-1">
              {RUN_TYPES.map((rt) => {
                const on = runTypes.includes(rt.key);
                return (
                  <button
                    key={rt.key}
                    onClick={() => toggleRunType(rt.key)}
                    className={`text-[10px] font-bold px-2 py-1 rounded border transition-colors cursor-pointer ${
                      on
                        ? 'bg-indigo-600 text-white border-indigo-500'
                        : `${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted} hover:border-indigo-500/50`
                    }`}
                  >
                    {rt.key}
                  </button>
                );
              })}
            </div>
          </div>
          <div>
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.cog.metric')}
            </span>
            <input
              value={metric}
              onChange={(e) => setMetric(e.target.value)}
              placeholder={t('scenario.cog.metricPh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </div>
          <div>
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.cog.deviation')}
            </span>
            <input
              type="number"
              value={deviation}
              onChange={(e) => setDeviation(Number(e.target.value) || 0)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </div>
          <div className="flex items-end">
            <button
              onClick={handleRun}
              disabled={running || !scenario || runTypes.length === 0}
              className="w-full px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-[11px] font-bold transition-colors cursor-pointer flex items-center justify-center gap-1.5"
            >
              <LucideIcon name="Play" size={12} className={running ? 'animate-pulse' : ''} />
              {running ? t('scenario.cog.running') : t('scenario.cog.run')}
            </button>
          </div>
        </div>
      </div>

      {/* ── 本次运行结果 ── */}
      {result && !scenario && null}
      {!scenario && <p className={`text-xs ${styles.cardTextMuted}`}>{t('scenario.cog.loading')}</p>}
      {scenario && !result && (
        <p className={`text-xs ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <LucideIcon name="Info" size={12} />
          {t('scenario.cog.empty')}
        </p>
      )}
      {result && (
        <div className="space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex flex-wrap items-center gap-3`}>
            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400">
              {t('scenario.cog.resultTitle')}
            </span>
            <StatusChip status={result.status ?? 'ERROR'} degraded={result.degraded} text={t} />
            {result.runId && (
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                {t('scenario.cog.runId')}: {result.runId}
              </span>
            )}
          </div>

          {/* 因果诊断 */}
          {diagnosis && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
              <SectionHeader icon="Search" title={t('scenario.cog.diagnosis')} />
              <div className="p-4 space-y-3">
                {diagnosis.degraded && diagnosis.degradeReason && (
                  <div className="text-[11px] text-amber-400 border border-amber-700/40 bg-amber-950/30 rounded p-2">
                    {diagnosis.degradeReason}
                  </div>
                )}
                {diagnosis.rootCause && (
                  <div className="text-xs space-y-1">
                    <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
                      {t('scenario.cog.rootCause')}
                    </span>
                    <p className={`text-sm font-semibold ${styles.cardText}`}>{diagnosis.rootCause}</p>
                  </div>
                )}
                {diagnosis.causalChain && diagnosis.causalChain.length > 0 && (
                  <div className="space-y-1">
                    <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
                      {t('scenario.cog.chain')}
                    </span>
                    {diagnosis.causalChain.map((n, i) => (
                      <div key={`${n.depth}-${i}`} className={`flex items-center gap-2 text-xs ${i > 0 ? 'pl-6' : ''}`}>
                        <span className="font-mono text-[10px] text-indigo-400 w-8">L{n.depth ?? i + 1}</span>
                        <span className={styles.cardText}>{n.node || n.description}</span>
                        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                          conf {(n.confidence ?? 0).toFixed(2)} · {n.source ?? '-'}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
                {steps.length > 0 && (
                  <div className="space-y-1.5">
                    <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
                      {t('scenario.cog.path')}
                    </span>
                    {steps.map((s, i) => {
                      const active = activeStepId === s.stepId;
                      return (
                        <button
                          key={s.stepId ?? i}
                          onClick={() => setActiveStepId(active ? null : s.stepId ?? null)}
                          className={`w-full text-left p-2.5 rounded border transition-colors cursor-pointer ${
                            active
                              ? 'border-indigo-500 bg-indigo-950/30'
                              : `${styles.inputBg} ${styles.inputBorder} hover:border-indigo-500/40`
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            <span className="font-mono text-[10px] text-indigo-400">
                              {t('scenario.cog.step')} {i + 1} · {s.sourceType ?? '-'}
                            </span>
                            {typeof s.confidence === 'number' && (
                              <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                                {t('scenario.cog.conf')} {(s.confidence * 100).toFixed(0)}%
                              </span>
                            )}
                            {s.ruleRef?.ruleName && (
                              <RuleRefChip
                                rule={s.ruleRef}
                                open={openRuleId === s.ruleRef.ruleId}
                                onToggle={() => setOpenRuleId(openRuleId === s.ruleRef!.ruleId ? null : s.ruleRef!.ruleId ?? null)}
                                t={t}
                              />
                            )}
                            {s.precedentRef && (
                              <PrecedentRefChip
                                ref_={s.precedentRef}
                                open={openPrecedentId === s.precedentRef.precedentId}
                                onToggle={() =>
                                  setOpenPrecedentId(
                                    openPrecedentId === s.precedentRef!.precedentId
                                      ? null
                                      : s.precedentRef!.precedentId ?? null,
                                  )
                                }
                                t={t}
                              />
                            )}
                          </div>
                          {active && s.description && (
                            <p className={`mt-1.5 text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{s.description}</p>
                          )}
                        </button>
                      );
                    })}
                  </div>
                )}
                {diagnosis.suggestions && diagnosis.suggestions.length > 0 && (
                  <div className="space-y-1">
                    <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
                      {t('scenario.cog.suggestions')}
                    </span>
                    <ul className="space-y-1">
                      {diagnosis.suggestions.map((sg, i) => (
                        <li key={i} className={`text-[11px] ${styles.cardText} flex gap-1.5`}>
                          <span className="text-indigo-400">›</span>
                          {sg}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 预测 */}
          {forecast && forecast.points && forecast.points.length > 0 && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
              <SectionHeader icon="TrendingUp" title={t('scenario.cog.forecast')} />
              <div className="p-4 space-y-3">
                <div className="flex flex-wrap gap-3 text-[11px] font-mono">
                  <span className={styles.cardTextMuted}>
                    {t('scenario.cog.modelId')}: {forecast.modelId ?? '-'}
                  </span>
                  {typeof forecast.confidence === 'number' && (
                    <span className={styles.cardTextMuted}>
                      {t('scenario.cog.conf')} {(forecast.confidence * 100).toFixed(0)}%
                    </span>
                  )}
                  {forecast.kgAdjusted && <span className="text-teal-400">KG ×</span>}
                </div>
                <ForecastChart points={forecast.points} />
                {forecast.summary && <p className={`text-[11px] ${styles.cardTextMuted}`}>{forecast.summary}</p>}
                {forecast.justifications && forecast.justifications.length > 0 && (
                  <ul className="space-y-0.5">
                    {forecast.justifications.map((j, i) => (
                      <li key={i} className={`text-[10px] ${styles.cardTextMuted}`}>
                        · {j}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}

          {/* 模拟 / 策略 */}
          {simulation && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
              <SectionHeader icon="FlaskConical" title={t('scenario.cog.simulation')} />
              <JsonSummary data={simulation} styles={styles} />
            </div>
          )}
          {strategy && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
              <SectionHeader icon="Lightbulb" title={t('scenario.cog.strategy')} />
              <JsonSummary data={strategy} styles={styles} />
            </div>
          )}
        </div>
      )}

      {/* ── 运行历史 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
        <SectionHeader icon="History" title={t('scenario.cog.history')} />
        {history.length === 0 ? (
          <p className={`p-4 text-xs ${styles.cardTextMuted}`}>{t('scenario.cog.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[11px]">
              <thead>
                <tr className={`border-b ${styles.cardBorder} text-left`}>
                  <Th t={t('scenario.cog.historyAt')} styles={styles} />
                  <Th t={t('scenario.cog.runTypesKey')} styles={styles} />
                  <Th t={t('scenario.cog.metricKey')} styles={styles} />
                  <Th t={t('scenario.cog.status')} styles={styles} />
                </tr>
              </thead>
              <tbody>
                {history.map((h) => (
                  <tr key={h.id} className={`border-b border-b ${styles.cardBorder}`}>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardTextMuted}`}>{h.create_time ?? '-'}</td>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardText}`}>{h.run_type ?? '-'}</td>
                    <td className={`px-3 py-1.5 ${styles.cardText}`}>
                      {h.metric ?? '-'}
                      {h.deviation != null && h.deviation !== 0 && (
                        <span className="text-amber-400 font-mono"> ({h.deviation}%)</span>
                      )}
                    </td>
                    <td className="px-3 py-1.5">
                      <StatusChip status={h.status ?? 'ERROR'} degraded={h.degraded} text={t} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── 模型注册表 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg overflow-hidden`}>
        <SectionHeader icon="Cpu" title={t('scenario.toml.modelsBtn')} />
        {models.length === 0 ? (
          <p className={`p-4 text-xs ${styles.cardTextMuted}`}>{t('scenario.cog.noData')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-[11px]">
              <thead>
                <tr className={`border-b ${styles.cardBorder} text-left`}>
                  <Th t="model_id" styles={styles} />
                  <Th t="type" styles={styles} />
                  <Th t="ver" styles={styles} />
                  <Th t="backtest" styles={styles} />
                  <Th t="status" styles={styles} />
                </tr>
              </thead>
              <tbody>
                {models.map((m) => (
                  <tr key={m.id} className={`border-b border-b ${styles.cardBorder}`}>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardText}`}>{m.model_id ?? '-'}</td>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardTextMuted}`}>{m.model_type ?? '-'}</td>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardText}`}>v{m.version ?? 1}</td>
                    <td className={`px-3 py-1.5 font-mono ${styles.cardTextMuted}`}>
                      {m.backtest_score != null ? Number(m.backtest_score).toFixed(2) : '-'}
                    </td>
                    <td className={`px-3 py-1.5 font-mono ${m.status === 'active' ? 'text-emerald-400' : styles.cardTextMuted}`}>
                      {m.status ?? '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      </div>
    </div>
  );
}

function isErrorPayload(v: unknown): boolean {
  return v != null && typeof v === 'object' && (v as Record<string, unknown>).kind === 'error';
}

function StatusChip({
  status,
  degraded,
  text,
}: {
  status: string;
  degraded?: boolean;
  text: (k: string) => string;
}) {
  const ok = status === 'SUCCEEDED';
  const deg = status === 'SUCCEEDED_DEGRADED' || degraded;
  return (
    <span
      className={`text-[10px] font-bold px-2 py-0.5 rounded font-mono ${
        ok
          ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/50'
          : deg
            ? 'bg-amber-950 text-amber-400 border border-amber-900/50'
            : 'bg-rose-950 text-rose-400 border border-rose-900/50'
      }`}
    >
      {status}
    </span>
  );
}

function SectionHeader({ icon, title }: { icon: string; title: string }) {
  return (
    <div className="p-3 border-b flex items-center gap-2">
      <LucideIcon name={icon} size={13} />
      <span className="text-xs font-bold">{title}</span>
    </div>
  );
}

function Th({ t: label, styles }: { t: string; styles: ThemeStyles }) {
  return <th className={`px-3 py-2 text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>{label}</th>;
}

/** 规则引用芯片 — 点击展开 condition/action */
function RuleRefChip({
  rule,
  open,
  onToggle,
  t,
}: {
  rule: { ruleId?: string; ruleName?: string; condition?: string; action?: string; category?: string };
  open: boolean;
  onToggle: () => void;
  t: (k: string) => string;
}) {
  const { styles } = useTheme();
  return (
    <span className="inline-flex flex-col">
      <button
        onClick={(e) => {
          e.stopPropagation();
          onToggle();
        }}
        className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-blue-950 text-blue-300 border border-blue-800/50 hover:border-blue-500/70 transition-colors cursor-pointer"
      >
        {t('scenario.cog.rule')}: {rule.ruleName}
      </button>
      {open && (
        <span className={`mt-1 text-[10px] leading-relaxed ${styles.cardTextMuted} max-w-md`}>
          {rule.condition && <span className="block">cond: {rule.condition}</span>}
          {rule.action && <span className="block">action: {rule.action}</span>}
        </span>
      )}
    </span>
  );
}

/** 先例引用芯片 — 点击展开 summary/outcome/similarity */
function PrecedentRefChip({
  ref_,
  open,
  onToggle,
  t,
}: {
  ref_: { precedentId?: string; decisionId?: string; summary?: string; outcome?: string; similarity?: number };
  open: boolean;
  onToggle: () => void;
  t: (k: string) => string;
}) {
  const { styles } = useTheme();
  return (
    <span className="inline-flex flex-col">
      <button
        onClick={(e) => {
          e.stopPropagation();
          onToggle();
        }}
        className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-purple-950 text-purple-300 border border-purple-800/50 hover:border-purple-500/70 transition-colors cursor-pointer"
      >
        {t('scenario.cog.precedent')}: {ref_.precedentId ?? ref_.decisionId ?? '-'}
      </button>
      {open && (
        <span className={`mt-1 text-[10px] leading-relaxed ${styles.cardTextMuted} max-w-md`}>
          {ref_.summary && <span className="block">{ref_.summary}</span>}
          {ref_.outcome && <span className="block">outcome: {ref_.outcome}</span>}
          {ref_.similarity != null && ref_.similarity >= 0 && (
            <span className="block">sim {(ref_.similarity * 100).toFixed(0)}%</span>
          )}
        </span>
      )}
    </span>
  );
}

/** 预测置信区间图 — 纯 SVG，历史点 + 预测点 + 上下界带 */
function ForecastChart({ points }: { points: NonNullable<ForecastResult['points']> }) {
  const W = 560;
  const H = 140;
  const PAD = 24;
  if (!points.length) return null;
  const values = points.flatMap((p) => [p.value ?? 0, p.lowerBound ?? 0, p.upperBound ?? 0]);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const x = (i: number) => PAD + (i * (W - PAD * 2)) / Math.max(points.length - 1, 1);
  const y = (v: number) => H - PAD - ((v - min) * (H - PAD * 2)) / range;
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value ?? 0).toFixed(1)}`).join(' ');
  const upper = points.map((p, i) => `${x(i).toFixed(1)},${y(p.upperBound ?? p.value ?? 0).toFixed(1)}`).join(' ');
  const lower = [...points].reverse().map((p, i) => {
    const idx = points.length - 1 - i;
    return `${x(idx).toFixed(1)},${y(p.lowerBound ?? p.value ?? 0).toFixed(1)}`;
  }).join(' ');
  const firstForecastIdx = Math.max(points.length - 3, 0);
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="w-full" role="img">
      <polygon points={`${upper} ${lower}`} className="fill-indigo-500/15" />
      <path d={line} fill="none" className="stroke-indigo-500" strokeWidth={2} />
      <line
        x1={x(firstForecastIdx)}
        y1={PAD - 8}
        x2={x(firstForecastIdx)}
        y2={H - PAD + 8}
        className="stroke-amber-500/60"
        strokeDasharray="4 3"
      />
      {points.map((p, i) => (
        <circle key={i} cx={x(i)} cy={y(p.value ?? 0)} r={3} className={i >= firstForecastIdx ? 'fill-amber-400' : 'fill-indigo-500'} />
      ))}
      <text x={PAD} y={H - 6} className="fill-slate-500 text-[9px] font-mono">min {min.toFixed(2)}</text>
      <text x={W - PAD} y={H - 6} textAnchor="end" className="fill-slate-500 text-[9px] font-mono">
        max {max.toFixed(2)}
      </text>
    </svg>
  );
}

/** 通用 JSON 摘要（模拟/策略结果字段不可预知，防御性渲染） */
function JsonSummary({ data, styles }: { data: Record<string, unknown>; styles: ThemeStyles }) {
  const { t } = useLanguage();
  const entries = Object.entries(data).slice(0, 12);
  return (
    <div className="p-4">
      <ul className="space-y-1">
        {entries.map(([k, v]) => (
          <li key={k} className="text-[11px]">
            <span className={`font-mono font-bold ${styles.cardTextMuted}`}>{k}: </span>
            <span className={styles.cardText}>
              {typeof v === 'object' && v !== null ? JSON.stringify(v).slice(0, 200) : String(v ?? t('scenario.cog.noData'))}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
