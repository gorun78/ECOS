/**
 * 问题驱动运行 · 执行过程与结果面板（2026-11-24 场景运行页重构）。
 *
 * <p>三段呈现：执行过程（真实节点耗时）→ 分析结论（心智模型原文）→ 决策建议。
 * 执行失败时如实显示失败态与原因，不给「假结论文本」。</p>
 */
import React from 'react';
import { AlertTriangle, CheckCircle2, Clock, FlaskConical, Lightbulb, ListChecks } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import type { ExecStep, QuestionRunResult } from '../../../../services/scenarioQuestionRunClient';

interface Props {
  result: QuestionRunResult | null;
  /** V367 批次 C：打开本次运行的复盘抽屉（服务端按 run 聚合）。 */
  onOpenRetro?: () => void;
}

export default function QuestionRunResultPanel({ result, onOpenRetro }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!result) return null;

  const totalMs = result.trace.reduce((s, x) => s + (x.elapsedMs || 0), 0);

  return (
    <div className="space-y-3">
      {/* 执行过程 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
        <div className="flex items-center justify-between mb-2">
          <h4 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <Clock size={12} />
            {t('workspace.qrun.trace')}
          </h4>
          <div className="flex items-center gap-2">
            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
              {result.trace.length} {t('workspace.qrun.steps')} · {totalMs}ms
            </span>
            {onOpenRetro && (
              <button
                type="button"
                onClick={onOpenRetro}
                className={`text-[10px] font-bold px-2 py-0.5 rounded border cursor-pointer ${styles.cardBorder} ${styles.cardText}`}
              >
                {t('workspace.retro.open')}
              </button>
            )}
          </div>
        </div>
        <ol className="space-y-1.5">
          {result.trace.map((s: ExecStep, i) => (
            <li key={`${s.step}-${i}`} className="flex items-start gap-2 text-[11px]">
              <span className={`shrink-0 mt-0.5 ${s.status === 'success' ? styles.successText : s.status === 'skipped' ? styles.cardTextMuted : styles.dangerText}`}>
                {s.status === 'success' ? <CheckCircle2 size={11} /> : <AlertTriangle size={11} />}
              </span>
              <span className={`font-mono shrink-0 ${styles.cardTextMuted}`}>{s.elapsedMs}ms</span>
              <span className={`shrink-0 font-semibold ${styles.cardText}`}>{t(`workspace.qrun.step.${s.step}`)}</span>
              <span className={`${styles.cardTextMuted} break-all`}>{s.detail}</span>
            </li>
          ))}
        </ol>
      </section>

      {/* 算法执行结果（数值唯一来源；LLM 只解释） */}
      {result.algorithmStatus && result.algorithmStatus !== 'SKIPPED' && (
        <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
          <div className="flex items-center justify-between mb-2">
            <h4 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
              <FlaskConical size={12} />
              {t('workspace.qrun.algo.title')}
            </h4>
            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
              {result.algorithmStatus} · {result.algorithmResult?.elapsedMs ?? 0}ms
            </span>
          </div>
          <div className={`text-[11px] mb-1 ${styles.cardText}`}>
            <span className="font-semibold">{result.algorithmResult?.name ?? result.algorithmId}</span>
            {result.algorithmResult?.code ? (
              <span className={`font-mono ${styles.cardTextMuted}`}> ({result.algorithmResult.code})</span>
            ) : null}
          </div>
          {result.algorithmResult?.message ? (
            <p className={`text-[11px] ${styles.warningText} break-all`}>{result.algorithmResult.message}</p>
          ) : null}
          {result.algorithmResult?.metrics && Object.keys(result.algorithmResult.metrics).length > 0 && (
            <pre className={`text-[10px] font-mono whitespace-pre-wrap break-all ${styles.cardText} max-h-64 overflow-y-auto`}>
              {JSON.stringify(result.algorithmResult.metrics, null, 2)}
            </pre>
          )}
          {(result.algorithmResult?.assumptions ?? []).length > 0 && (
            <ul className={`mt-1 space-y-0.5 text-[10px] ${styles.cardTextMuted}`}>
              {(result.algorithmResult?.assumptions ?? []).map((a, i) => (
                <li key={`${a}-${i}`}>· {a}</li>
              ))}
            </ul>
          )}
        </section>
      )}

      {/* 分析结论 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
        <h4 className={`text-xs font-bold ${styles.cardText} mb-2`}>
          {t('workspace.qrun.conclusion')} · {result.mentalModelName}
          {result.executorType ? (
            <span className={`ml-1 text-[10px] font-mono ${styles.cardTextMuted}`}>
              [{result.executorType}:{result.executorId}]
            </span>
          ) : null}
        </h4>
        {result.degraded || !result.result ? (
          <div className={`px-3 py-2 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            {t('workspace.qrun.degraded')}
          </div>
        ) : (
          <pre className={`text-[11px] whitespace-pre-wrap break-words ${styles.cardText} font-sans leading-relaxed max-h-96 overflow-y-auto`}>
            {result.result}
          </pre>
        )}
      </section>

      {/* 决策建议 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3`}>
        <div className="flex items-center justify-between mb-2">
          <h4 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <Lightbulb size={12} />
            {t('workspace.qrun.advice')}
          </h4>
          <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded border ${styles.cardBorder} ${styles.cardTextMuted}`}>
            {t('workspace.qrun.confidence')}: {result.advice.confidence}
          </span>
        </div>
        <p className={`text-xs ${styles.cardText} mb-2 leading-relaxed`}>{result.advice.summary || '—'}</p>
        {result.advice.actions.length > 0 && (
          <div className="mb-2">
            <div className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1 flex items-center gap-1`}>
              <ListChecks size={10} />
              {t('workspace.qrun.actions')}
            </div>
            <ul className={`text-[11px] ${styles.cardText} list-disc pl-4 space-y-0.5`}>
              {result.advice.actions.map((a, i) => <li key={i}>{a}</li>)}
            </ul>
          </div>
        )}
        {result.advice.risks.length > 0 && (
          <div>
            <div className={`text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-1`}>{t('workspace.qrun.risks')}</div>
            <ul className={`text-[11px] ${styles.warningText} list-disc pl-4 space-y-0.5`}>
              {result.advice.risks.map((r, i) => <li key={i}>{r}</li>)}
            </ul>
          </div>
        )}
      </section>
    </div>
  );
}
