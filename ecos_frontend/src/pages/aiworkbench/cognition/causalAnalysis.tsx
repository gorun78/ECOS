/**
 * 因果分析（认知工作台 Tab）— design-05 B.0 / B.5 / F05-16。
 *
 * <p>本文件不再持有任何硬编码 mock 因果链（原 NODES/EDGE_PAIRS/固定 0.82/87% 等
 * 假数据已按 F05-16「禁 200 伪数据」删除）。因果图复用全仓唯一 cytoscape 图组件
 * {@link CausalGraphView}（其内部经 `fetchCausalGraph` 读真实世界模型因果边）；
 * 诊断结论区仅渲染 `POST /api/v1/cognitive/diagnose` 的真实响应，
 * 无数据时按 B.5 走空态（`role=status` 灰色占位，不是错误、不是假数）。</p>
 *
 * <p>主题全部走 `useTheme()` token；文案全部走 `useLanguage()`（无内联中文 fallback）；
 * 图标仅 lucide-react。stub/错误态经 CognitiveApiError 显式渲染。</p>
 *
 * @license Apache-2.0
 */
import React, { useCallback, useMemo, useRef, useState } from 'react';
import { Brain, Loader2 } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { CognitiveEmptyState, CognitiveErrorState, CognitiveSkeleton, cognitiveErrorTitleKey } from '../../../pages/scenario/cognition/CognitionStateViews';
import CausalGraphView from '../../../components/CausalGraphView';
import {
  runDiagnosis,
  CognitiveApiError,
  isCognitiveApiError,
  debounce,
  COGNITION_DEBOUNCE_MS,
} from '../../../services/cognitiveClient';

/** diagnose 响应的因果链节点（后端 schema 之外的字段一律忽略，禁前端补数）。 */
interface DiagnosisChainNode {
  depth?: number;
  node?: string;
  description?: string;
  confidence?: number;
  source?: string;
}

interface DiagnosisResult {
  degraded?: boolean;
  degradeReason?: string;
  rootCause?: string;
  causalChain?: DiagnosisChainNode[];
  suggestions?: string[];
}

type DiagStatus =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'empty' }
  | { kind: 'ok'; data: DiagnosisResult }
  | { kind: 'error'; error: CognitiveApiError };

interface CausalAnalysisProps {
  /** Switch the workbench to the scenario-simulation sub-page (in-page cross-link). */
  onGoScenario?: () => void;
}

export default function causalAnalysis({ onGoScenario }: CausalAnalysisProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [objectId, setObjectId] = useState('');
  const [question, setQuestion] = useState('');
  const [status, setStatus] = useState<DiagStatus>({ kind: 'idle' });
  const reqSeq = useRef(0);

  const handleRun = useCallback(async () => {
    if (!objectId.trim() || !question.trim()) return;
    const seq = ++reqSeq.current;
    setStatus({ kind: 'loading' });
    try {
      const data = (await runDiagnosis({
        objectId: objectId.trim(),
        question: question.trim(),
      })) as DiagnosisResult | null;
      if (seq !== reqSeq.current) return;
      if (!data || (typeof data === 'object' && Object.keys(data).length === 0)) {
        setStatus({ kind: 'empty' });
        return;
      }
      setStatus({ kind: 'ok', data });
    } catch (e) {
      if (seq !== reqSeq.current) return;
      const err = isCognitiveApiError(e)
        ? e
        : new CognitiveApiError({
            code: 'NETWORK',
            status: 0,
            message: e instanceof Error ? e.message : String(e),
          });
      setStatus({ kind: 'error', error: err });
    }
  }, [objectId, question]);

  // 连点合并为末次（契约 debounce 100–300ms）
  const runDebounced = useMemo(() => debounce(() => void handleRun(), COGNITION_DEBOUNCE_MS), [handleRun]);

  const canRun = !!objectId.trim() && !!question.trim() && status.kind !== 'loading';

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('causal.title')}</h1>
        <button
          type="button"
          onClick={() => onGoScenario?.()}
          aria-label={t('causal.toScenario')}
          className={`px-3 py-1.5 rounded-md text-sm font-medium cursor-pointer border ${styles.inputBorder} ${styles.inputBg} ${styles.cardText} hover:opacity-80 transition-opacity`}
        >
          {t('causal.toScenario')}
        </button>
      </div>

      {/* Diagnosis request (real API only; no preset question, no mock notice) */}
      <div className={`rounded-lg border p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2">
          <Brain size={14} className={styles.accentText} />
          <span className={`text-sm font-semibold ${styles.cardText}`}>{t('causal.conclusion')}</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-[1fr_2fr_auto] gap-2">
          <label className="flex flex-col gap-1">
            <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
              {t('causal.objectIdLabel')}
            </span>
            <input
              value={objectId}
              onChange={(e) => setObjectId(e.target.value)}
              placeholder={t('causal.objectIdPh')}
              aria-label={t('causal.objectIdLabel')}
              className={`px-2 py-1.5 text-[12px] rounded border outline-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
              {t('causal.questionLabel')}
            </span>
            <input
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder={t('causal.questionPh')}
              aria-label={t('causal.questionLabel')}
              className={`px-2 py-1.5 text-[12px] rounded border outline-none ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
            />
          </label>
          <div className="flex items-end">
            <button
              type="button"
              onClick={runDebounced}
              disabled={!canRun}
              aria-label={t('causal.reRun')}
              className={`px-3 py-1.5 rounded-md text-sm font-medium cursor-pointer inline-flex items-center gap-1.5 border ${styles.accentBorder} ${styles.accentBg} ${styles.cardText} disabled:opacity-50 transition-opacity`}
            >
              {status.kind === 'loading' ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}
              {t('causal.reRun')}
            </button>
          </div>
        </div>

        {/* B.5 seven states for the diagnosis result area */}
        {status.kind === 'idle' && <CognitiveEmptyState labelKey="causal.empty" testId="causal-idle" />}
        {status.kind === 'loading' && <CognitiveSkeleton rows={2} />}
        {status.kind === 'empty' && <CognitiveEmptyState labelKey="causal.empty" testId="causal-empty" />}
        {status.kind === 'error' && (
          <CognitiveErrorState error={status.error} titleKey={cognitiveErrorTitleKey(status.error)} onRetry={runDebounced} />
        )}
        {status.kind === 'ok' && (
          <div className="space-y-2 text-[12px]" data-testid="causal-result">
            {status.data.degraded && status.data.degradeReason && (
              <div className={`rounded p-2 text-[11px] border ${styles.warningBorder} ${styles.warningBg} ${styles.warningText}`} role="status">
                {status.data.degradeReason}
              </div>
            )}
            {status.data.rootCause && (
              <div>
                <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>{t('causal.rootCause')}</span>
                <p className={`font-semibold ${styles.cardText}`}>{status.data.rootCause}</p>
              </div>
            )}
            {Array.isArray(status.data.causalChain) && status.data.causalChain.length > 0 && (
              <div>
                <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>{t('causal.chain')}</span>
                <ul className="mt-1 space-y-1">
                  {status.data.causalChain.map((n, i) => (
                    <li key={`${n.depth ?? i}-${i}`} className={`flex items-center gap-2 text-[11px] ${styles.cardText}`}>
                      <span className={`font-mono text-[10px] ${styles.accentText}`}>L{n.depth ?? i + 1}</span>
                      <span className={styles.cardText}>{n.node || n.description}</span>
                      {typeof n.confidence === 'number' && (
                        <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                          {t('causal.confidence')} {n.confidence.toFixed(2)}
                        </span>
                      )}
                      {n.source && <span className={`font-mono text-[10px] ${styles.muted}`}>{n.source}</span>}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {Array.isArray(status.data.suggestions) && status.data.suggestions.length > 0 && (
              <div>
                <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>{t('scenario.cog.suggestions')}</span>
                <ul className="mt-1 space-y-0.5">
                  {status.data.suggestions.map((s, i) => (
                    <li key={i} className={`text-[11px] ${styles.cardText}`}>
                      · {s}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Real causal graph — single-source cytoscape component reading the world-model API */}
      <div className={`rounded-lg border p-4 space-y-2 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between gap-2">
          <h2 className={`text-sm font-medium ${styles.cardText}`}>{t('causal.graph')}</h2>
          <span className={`text-[10px] ${styles.muted}`}>{t('causal.graphHint')}</span>
        </div>
        <CausalGraphView showWhatIf={false} />
      </div>
    </div>
  );
}
