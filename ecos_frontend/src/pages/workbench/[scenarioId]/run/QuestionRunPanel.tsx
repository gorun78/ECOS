/**
 * 场景运行页 · 问题驱动主面板（2026-11-24 重构）。
 *
 * <h3>流程（对应需求 2a~2d）</h3>
 * <ol>
 *   <li>用户输入基于本场景的问题；</li>
 *   <li>「分析」→ 后端意图识别 + 知识库检索假设 + 算法推荐 + 心智模型清单（不执行）；</li>
 *   <li>用户逐条确认/取消假设，选定心智模型（决定运行路径）与算法；</li>
 *   <li>「执行」→ 真实跑心智模型编排 → 执行过程 + 结论 + 决策建议；</li>
 *   <li>每次运行落库，历史可两次对比。</li>
 * </ol>
 *
 * <p>场景上下文（数据/知识资产）由后端在假设检索阶段读取；心智模型决定运行路径。
 * 依赖不可用时如实降级提示，不伪造结果。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Brain, GitCompare, Play, Search, Sparkles } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import { showToastGlobal } from '../../../../components/common/Toast';
import logger from '../../../../services/logger';
import {
  compareQuestionRuns,
  executeQuestionRun,
  listQuestionRuns,
  prepareQuestionRun,
  type Hypothesis,
  type QuestionRunPrepare,
  type QuestionRunRecord,
  type QuestionRunResult,
} from '../../../../services/scenarioQuestionRunClient';
import QuestionRunResultPanel from './QuestionRunResultPanel';
import RetrospectiveDrawer from './RetrospectiveDrawer';

interface Props {
  scenarioId: string;
}

type Phase = 'idle' | 'preparing' | 'confirm' | 'running' | 'done';

export default function QuestionRunPanel({ scenarioId }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [question, setQuestion] = useState('');
  const [phase, setPhase] = useState<Phase>('idle');
  const [prep, setPrep] = useState<QuestionRunPrepare | null>(null);
  const [hypotheses, setHypotheses] = useState<Hypothesis[]>([]);
  const [modelCode, setModelCode] = useState('');
  const [executorType, setExecutorType] = useState<'MENTAL_MODEL' | 'SCENARIO_PIPELINE' | 'AI_AGENT'>('MENTAL_MODEL');
  const [executorId, setExecutorId] = useState('');
  const [algorithmId, setAlgorithmId] = useState('');
  const [algorithmInputsText, setAlgorithmInputsText] = useState('');
  const [retroRunId, setRetroRunId] = useState<string | null>(null);
  const [result, setResult] = useState<QuestionRunResult | null>(null);
  const [history, setHistory] = useState<QuestionRunRecord[]>([]);
  const [baseA, setBaseA] = useState('');
  const [baseB, setBaseB] = useState('');
  const [diff, setDiff] = useState<Record<string, { left: unknown; right: unknown; same: boolean }> | null>(null);

  const loadHistory = useCallback(async () => {
    try {
      const rows = await listQuestionRuns(scenarioId, 20);
      setHistory(rows);
    } catch (e) {
      logger.error('qrun-history', e);
    }
  }, [scenarioId]);

  useEffect(() => { void loadHistory(); }, [loadHistory]);

  const analyze = async () => {
    const q = question.trim();
    if (!q) {
      showToastGlobal('info', t('workspace.qrun.question_required'));
      return;
    }
    setPhase('preparing');
    setResult(null);
    setDiff(null);
    try {
      const p = await prepareQuestionRun(scenarioId, q);
      setPrep(p);
      setHypotheses(p.hypotheses ?? []);
      setModelCode(p.mentalModels?.[0]?.code ?? '');
      setExecutorType('MENTAL_MODEL');
      setExecutorId('');
      setAlgorithmId((p.algorithms ?? []).find((a) => a.executable)?.id ?? '');
      setPhase('confirm');
    } catch (e) {
      logger.error('qrun-prepare', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
      setPhase('idle');
    }
  };

  const execute = async () => {
    const effectiveId = executorType === 'MENTAL_MODEL' ? modelCode : executorId;
    if (!effectiveId) {
      showToastGlobal('info', t(executorType === 'MENTAL_MODEL'
        ? 'workspace.qrun.model_required' : 'workspace.qrun.executor.required'));
      return;
    }
    let algorithmInputs: Record<string, unknown> | undefined;
    if (algorithmId && algorithmInputsText.trim()) {
      try {
        algorithmInputs = JSON.parse(algorithmInputsText) as Record<string, unknown>;
      } catch {
        showToastGlobal('error', t('workspace.qrun.algo.inputs_invalid'));
        return;
      }
    }
    setPhase('running');
    try {
      const r = await executeQuestionRun(scenarioId, {
        question: question.trim(),
        mentalModel: executorType === 'MENTAL_MODEL' ? modelCode : undefined,
        executorType,
        executorId: effectiveId,
        hypotheses,
        algorithmId: algorithmId || undefined,
        algorithmInputs,
      });
      setResult(r);
      setPhase('done');
      await loadHistory();
    } catch (e) {
      logger.error('qrun-execute', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
      setPhase('confirm');
    }
  };

  const toggleHypothesis = (idx: number) => {
    setHypotheses((prev) => prev.map((h, i) => (i === idx ? { ...h, confirmed: !h.confirmed } : h)));
  };

  const doCompare = async () => {
    if (!baseA || !baseB) {
      showToastGlobal('info', t('workspace.qrun.compare_need_two'));
      return;
    }
    try {
      const r = await compareQuestionRuns(baseA, baseB);
      setDiff(r.diff);
    } catch (e) {
      logger.error('qrun-compare', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
      setDiff(null);
    }
  };

  const reset = () => {
    setPhase('idle');
    setPrep(null);
    setResult(null);
    setDiff(null);
    setHypotheses([]);
    setModelCode('');
    setAlgorithmId('');
  };

  return (
    <div className="space-y-3">
      {/* ① 问题输入 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-2 flex items-center gap-1.5`}>
          <Sparkles size={14} />
          {t('workspace.qrun.title')}
        </h3>
        <p className={`text-[11px] ${styles.cardTextMuted} mb-2 leading-relaxed`}>{t('workspace.qrun.hint')}</p>
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={t('workspace.qrun.question_ph')}
          rows={3}
          disabled={phase === 'preparing' || phase === 'running'}
          className={`w-full text-xs rounded border px-2.5 py-2 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} resize-y disabled:opacity-60`}
        />
        <div className="mt-2 flex items-center gap-2">
          <button
            type="button"
            onClick={() => void analyze()}
            disabled={phase === 'preparing' || phase === 'running'}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded border cursor-pointer transition disabled:opacity-50 disabled:cursor-not-allowed ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            <Search size={12} />
            {phase === 'preparing' ? t('workspace.qrun.analyzing') : t('workspace.qrun.analyze')}
          </button>
          {phase !== 'idle' && (
            <button
              type="button"
              onClick={reset}
              className={`text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
            >
              {t('workspace.qrun.reset')}
            </button>
          )}
        </div>
      </section>

      {/* ② 意图 + 假设确认 + 模型/算法选择 */}
      {prep && (phase === 'confirm' || phase === 'running' || phase === 'done') && (
        <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
          <div className={`flex items-center gap-2 flex-wrap text-[11px] ${styles.cardTextMuted}`}>
            <span className={`px-1.5 py-0.5 rounded border font-mono ${styles.accentBorder} ${styles.accentText}`}>
              {t('workspace.qrun.intent')}: {prep.intent}
            </span>
            {prep.intentDetail && <span className="break-all">{prep.intentDetail}</span>}
          </div>

          {/* 假设与信念（可勾选确认） */}
          <div>
            <div className={`text-[11px] font-bold ${styles.cardText} mb-1.5`}>
              {t('workspace.qrun.hypotheses')} · {t('workspace.qrun.hypotheses_hint')}
            </div>
            {hypotheses.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.qrun.hypotheses_empty')}</p>
            ) : (
              <ul className="space-y-1">
                {hypotheses.map((h, i) => (
                  <li key={`${h.text}-${i}`}>
                    <label className={`flex items-start gap-2 text-[11px] cursor-pointer rounded px-1.5 py-1 border ${h.confirmed ? styles.accentBorder : styles.cardBorder}`}>
                      <input
                        type="checkbox"
                        checked={h.confirmed !== false}
                        onChange={() => toggleHypothesis(i)}
                        className="mt-0.5 shrink-0"
                      />
                      <span className={styles.cardText}>
                        <span className="font-mono text-[10px] opacity-70 mr-1.5">[{h.kind}]</span>
                        {h.text}
                        {h.source ? <span className={`block text-[10px] ${styles.cardTextMuted} mt-0.5`}>{h.source}</span> : null}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* 主执行载体（心智模型 / 场景编排 / 智能体，三选一） */}
          <div>
            <div className={`text-[11px] font-bold ${styles.cardText} mb-1.5`}>{t('workspace.qrun.executor.title')}</div>
            <div className="flex flex-wrap gap-1.5">
              {(['MENTAL_MODEL', 'SCENARIO_PIPELINE', 'AI_AGENT'] as const).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => {
                    setExecutorType(k);
                    setExecutorId(k === 'MENTAL_MODEL' ? modelCode : '');
                  }}
                  className={`px-2.5 py-1 rounded border text-[11px] font-bold cursor-pointer transition ${
                    executorType === k
                      ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`
                      : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`
                  }`}
                >
                  {t(`workspace.qrun.executor.${k.toLowerCase()}`)}
                </button>
              ))}
            </div>
            <p className={`text-[10px] mt-1 ${styles.cardTextMuted}`}>{t('workspace.qrun.executor.hint')}</p>
          </div>

          {executorType === 'MENTAL_MODEL' && (
            <div>
              <div className={`text-[11px] font-bold ${styles.cardText} mb-1.5`}>{t('workspace.qrun.mental_model')}</div>
              <div className={`grid gap-1.5 ${isMobile ? 'grid-cols-2' : 'grid-cols-3'}`}>
                {(prep.mentalModels ?? []).map((m) => {
                  const pid = m.pipelineId ?? m.pipeline_id;
                  const runnable = !!(pid && String(pid).trim());
                  return (
                    <button
                      key={m.code}
                      type="button"
                      onClick={() => runnable && setModelCode(m.code)}
                      disabled={!runnable}
                      title={m.description ?? ''}
                      className={`text-left px-2 py-1.5 rounded border text-[11px] transition ${
                        modelCode === m.code
                          ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`
                          : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`
                      } ${runnable ? 'cursor-pointer hover:opacity-90' : 'opacity-40 cursor-not-allowed'}`}
                    >
                      <span className="font-bold flex items-center gap-1"><Brain size={11} />{m.name}</span>
                      {!runnable && <span className="block text-[10px] mt-0.5">{t('workspace.qrun.not_wired')}</span>}
                    </button>
                  );
                })}
                {(prep.mentalModels ?? []).length === 0 && (
                  <p className={`text-[11px] ${styles.cardTextMuted} col-span-full`}>{t('workspace.qrun.models_empty')}</p>
                )}
              </div>
            </div>
          )}

          {executorType !== 'MENTAL_MODEL' && (
            <label className="block">
              <span className={`text-[11px] font-bold ${styles.cardText}`}>
                {t(executorType === 'SCENARIO_PIPELINE'
                  ? 'workspace.qrun.executor.pipeline_pick'
                  : 'workspace.qrun.executor.agent_pick')}
              </span>
              <select
                value={executorId}
                onChange={(e) => setExecutorId(e.target.value)}
                className={`mt-1 w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              >
                <option value="">{t('workspace.qrun.executor.select')}</option>
                {(executorType === 'SCENARIO_PIPELINE'
                  ? (prep.executors?.pipelines ?? [])
                  : (prep.executors?.agents ?? [])
                ).map((b) => {
                  const id = b.target_id ?? b.targetId ?? '';
                  return <option key={id} value={id}>{id}</option>;
                })}
              </select>
              {((executorType === 'SCENARIO_PIPELINE'
                ? (prep.executors?.pipelines ?? [])
                : (prep.executors?.agents ?? [])).length === 0) && (
                <p className={`text-[10px] mt-1 ${styles.cardTextMuted}`}>{t('workspace.qrun.executor.none_bound')}</p>
              )}
            </label>
          )}

          {/* 算法（可选，执行结果作为唯一数值事实） */}
          {(prep.algorithms ?? []).length > 0 && (
            <div>
              <label className="block">
                <span className={`text-[11px] font-bold ${styles.cardText}`}>{t('workspace.qrun.algorithm')}</span>
                <select
                  value={algorithmId}
                  onChange={(e) => setAlgorithmId(e.target.value)}
                  className={`mt-1 w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                >
                  <option value="">{t('workspace.qrun.algorithm_none')}</option>
                  {(prep.algorithms ?? []).map((a) => (
                    <option key={a.id} value={a.id} disabled={a.executable === false}>
                      {a.name} ({a.category ?? a.analysisType ?? a.analysis_type})
                      {a.executable === false ? ` · ${a.unavailableReason ?? t('workspace.qrun.algo.unavailable')}` : ''}
                    </option>
                  ))}
                </select>
              </label>
              {algorithmId && (
                <>
                  <p className={`text-[10px] mt-1 ${styles.cardTextMuted}`}>{t('workspace.qrun.algo.inputs_hint')}</p>
                  <textarea
                    value={algorithmInputsText}
                    onChange={(e) => setAlgorithmInputsText(e.target.value)}
                    rows={3}
                    placeholder='{"x":[1,2,3],"y":[3,5,7]}'
                    className={`mt-1 w-full text-[11px] font-mono rounded border px-2 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                  />
                </>
              )}
            </div>
          )}

          <button
            type="button"
            onClick={() => void execute()}
            disabled={phase === 'running'}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded border cursor-pointer transition disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            <Play size={12} />
            {phase === 'running' ? t('workspace.qrun.running') : t('workspace.qrun.execute')}
          </button>
        </section>
      )}

      {/* ③ 执行过程与结果 */}
      <QuestionRunResultPanel result={result} onOpenRetro={result ? () => setRetroRunId(result.runId) : undefined} />

      {retroRunId && (
        <RetrospectiveDrawer
          scenarioId={scenarioId}
          runId={retroRunId}
          onClose={() => setRetroRunId(null)}
        />
      )}

      {/* ④ 历史运行 + 对比 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-2 flex items-center gap-1.5`}>
          <GitCompare size={14} />
          {t('workspace.qrun.history')}
        </h3>
        {history.length === 0 ? (
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.qrun.history_empty')}</p>
        ) : (
          <>
            <ul className="space-y-1 mb-2 max-h-56 overflow-y-auto">
              {history.map((h) => (
                <li key={h.id} className={`flex items-start gap-2 text-[11px] border-b ${styles.cardBorder} pb-1`}>
                  <input
                    type="checkbox"
                    checked={baseA === h.id || baseB === h.id}
                    onChange={() => {
                      if (baseA === h.id) setBaseA('');
                      else if (baseB === h.id) setBaseB('');
                      else if (!baseA) setBaseA(h.id);
                      else if (!baseB) setBaseB(h.id);
                      else { setBaseA(baseB); setBaseB(h.id); }
                    }}
                    className="mt-0.5 shrink-0"
                    aria-label={h.id}
                  />
                  <span className={`font-mono text-[10px] shrink-0 ${styles.cardTextMuted}`}>{(h.create_time ?? '').slice(5, 16)}</span>
                  <span className={`px-1 rounded border font-mono text-[10px] shrink-0 ${h.degraded ? styles.warningBorder + ' ' + styles.warningText : styles.successBorder + ' ' + styles.successText}`}>
                    {h.mental_model}
                  </span>
                  <span className={`${styles.cardText} break-all flex-1`}>{h.question}</span>
                  <button
                    type="button"
                    onClick={() => setRetroRunId(h.id)}
                    className={`shrink-0 text-[10px] font-bold px-1.5 py-0.5 rounded border cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted}`}
                  >
                    {t('workspace.retro.open')}
                  </button>
                </li>
              ))}
            </ul>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => void doCompare()}
                className={`text-[11px] font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
              >
                {t('workspace.qrun.compare')}
              </button>
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                {baseA ? baseA.slice(0, 12) : '—'} ↔ {baseB ? baseB.slice(0, 12) : '—'}
              </span>
            </div>
            {diff && (
              <div className={`mt-2 rounded border ${styles.cardBorder} overflow-hidden`}>
                <table className="w-full text-[11px]">
                  <thead>
                    <tr className={`${styles.inputBg}`}>
                      <th className={`text-left px-2 py-1 font-bold ${styles.cardTextMuted}`}>{t('workspace.qrun.field')}</th>
                      <th className={`text-left px-2 py-1 font-bold ${styles.cardTextMuted}`}>A</th>
                      <th className={`text-left px-2 py-1 font-bold ${styles.cardTextMuted}`}>B</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(diff).map(([k, v]) => (
                      <tr key={k} className={`border-t ${styles.cardBorder}`}>
                        <td className={`px-2 py-1 font-mono ${v.same ? styles.cardTextMuted : styles.cardText}`}>{k}</td>
                        <td className={`px-2 py-1 break-all ${styles.cardText}`}>{String(v.left ?? '—')}</td>
                        <td className={`px-2 py-1 break-all ${v.same ? styles.cardTextMuted : styles.cardText}`}>{String(v.right ?? '—')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
