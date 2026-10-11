/**
 * CandidateReviewPanel — 认知资产候选评审面（议题3 前置 / 设计方案 §3.5，补充设计 §7、§8.4）。
 *
 * <p>统一「生成候选 → 审批 → 正式写入」模式的前端评审 UI：
 * 「生成候选」（POST proposals，SUP-COG-03：只创建候选+审计留痕，原资产行 0 改）与
 * 「应用变更」（POST transitions，§8.4 受控状态迁移）是**两个分离动作**，不合并、不代跳。</p>
 *
 * <p>词表单源：目标状态可选项一律来自服务端 catalog.lifecycleStates（12 态 CognitiveAssetStatus），
 * 本组件不复制枚举。护栏（§3.5）：提案回执按服务端原值渲染（PENDING_APPROVAL 即 PENDING_APPROVAL），
 * 前端绝不把 GENERATED/VALIDATED/回执合成为 APPROVED；非法迁移 409 原样呈现服务端
 * ECOS-COG-409S 信息与 traceId，绝不本地兜底。</p>
 *
 * <p>六态矩阵：未选资产(idle) / 加载 / 就绪 / 错误(503 引擎横幅+traceId / 409 冲突 / 其余) /
 * 结果回执。样式沿用 knowledge 模块基线（lucide-react 具名图标 + styles 内联 token，0 硬编码色）。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { ClipboardPlus, ArrowRightLeft, AlertTriangle, Loader2, ShieldCheck, BadgeAlert } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  resolveCognitiveAsset,
  submitAssetProposal,
  transitionCognitiveAsset,
  isCognitiveApiError,
  type CognitiveAssetResolution,
  type CognitiveAssetProposalReceipt,
  type CognitiveAssetTransitionResult,
} from '../../../services/cognitiveClient';
import {
  assetStatusLabelKey,
  assetStatusTone,
  probKindLabelKey,
  isUncalibratedKind,
} from '../services/cognitiveAsset';

interface Props {
  /** 当前选中资产 id（null → 提示先在列表选择） */
  assetId: string | null;
  /** catalog.lifecycleStates 服务端单源词表（空数组 → 词表未就绪，禁用动作并如实提示） */
  lifecycleStates: string[];
}

type ResolveState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'ok'; data: CognitiveAssetResolution }
  | { kind: 'error'; message: string; engineDown: boolean; traceId?: string };

type ActionState =
  | { kind: 'none' }
  | { kind: 'busy' }
  | { kind: 'proposal'; receipt: CognitiveAssetProposalReceipt }
  | { kind: 'transition'; result: CognitiveAssetTransitionResult }
  | { kind: 'conflict'; message: string; traceId?: string }
  | { kind: 'failed'; message: string; engineDown: boolean; traceId?: string };

export default function CandidateReviewPanel({ assetId, lifecycleStates }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [resolve, setResolve] = useState<ResolveState>({ kind: 'idle' });
  const [targetState, setTargetState] = useState('');
  const [reason, setReason] = useState('');
  const [evidenceRef, setEvidenceRef] = useState('');
  const [operator, setOperator] = useState('');
  const [note, setNote] = useState('');
  const [action, setAction] = useState<ActionState>({ kind: 'none' });

  const runResolve = useCallback(async (id: string) => {
    setResolve({ kind: 'loading' });
    try {
      const data = await resolveCognitiveAsset(id);
      setResolve({ kind: 'ok', data });
    } catch (e) {
      if (isCognitiveApiError(e)) {
        setResolve({
          kind: 'error',
          message: e.message,
          engineDown: e.code === 'ENGINE_DISABLED' || e.code === 'SCHEMA_NOT_READY',
          traceId: e.traceId,
        });
      } else {
        setResolve({ kind: 'error', message: e instanceof Error ? e.message : String(e), engineDown: false });
      }
    }
  }, []);

  useEffect(() => {
    if (assetId) void runResolve(assetId);
    else setResolve({ kind: 'idle' });
    // 切资产即清空动作区（结果属于上一个资产，绝不串行残留冒充）
    setAction({ kind: 'none' });
    setTargetState('');
  }, [assetId, runResolve]);

  if (!assetId) {
    return (
      <div className="rounded-md border p-4 text-xs opacity-60" style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}>
        {t('knowledge.candidate.need_asset')}
      </div>
    );
  }

  const current = resolve.kind === 'ok' ? resolve.data : null;
  const statesReady = lifecycleStates.length > 0;
  const formReady = resolve.kind === 'ok';
  const canSubmit = formReady && statesReady && targetState !== '' && reason.trim() !== '';
  const busy = action.kind === 'busy';

  const failureOf = (e: unknown): ActionState => {
    if (isCognitiveApiError(e)) {
      if (e.status === 409) return { kind: 'conflict', message: e.message, traceId: e.traceId };
      if (e.code === 'ENGINE_DISABLED' || e.code === 'SCHEMA_NOT_READY') {
        return { kind: 'failed', message: e.message, engineDown: true, traceId: e.traceId };
      }
      return { kind: 'failed', message: e.message, engineDown: false, traceId: e.traceId };
    }
    return { kind: 'failed', message: e instanceof Error ? e.message : String(e), engineDown: false };
  };

  const doPropose = async (): Promise<void> => {
    if (!canSubmit) return;
    setAction({ kind: 'busy' });
    try {
      const receipt = await submitAssetProposal(assetId, {
        targetState,
        reason: reason.trim(),
        evidenceRef: evidenceRef.trim() || undefined,
        operator: operator.trim() || undefined,
        note: note.trim() || undefined,
      });
      setAction({ kind: 'proposal', receipt });
    } catch (e) {
      setAction(failureOf(e));
    }
  };

  const doTransition = async (): Promise<void> => {
    if (!canSubmit) return;
    setAction({ kind: 'busy' });
    try {
      const result = await transitionCognitiveAsset(assetId, {
        targetState,
        reason: reason.trim(),
        evidenceRef: evidenceRef.trim() || undefined,
        operator: operator.trim() || undefined,
      });
      setAction({ kind: 'transition', result });
      // 迁移成功 → 重新解析，当前态以服务端为准（不本地改写 current）
      void runResolve(assetId);
    } catch (e) {
      setAction(failureOf(e));
    }
  };

  const tone = assetStatusTone(current?.lifecycleState ?? null);
  const toneStyle =
    tone === 'positive'
      ? { borderColor: styles.successBorder, background: styles.successBg, color: styles.successText }
      : tone === 'warning'
        ? { borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }
        : { borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardTextMuted };

  const stateLabel = (s: string): string => {
    const key = assetStatusLabelKey(s);
    return key ? t(key as string) || s : s;
  };

  const inputCls = 'w-full px-2 py-1.5 rounded-md border text-xs';
  const inputStyle = { borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText };

  return (
    <div className="rounded-md border" style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}>
      <div className="px-4 py-3 border-b flex items-center gap-2" style={{ borderColor: styles.cardBorder }}>
        <ShieldCheck className="w-4 h-4" style={{ color: styles.accentText }} />
        <span className="text-xs font-semibold">{t('knowledge.candidate.title')}</span>
        <span className="text-[10px] opacity-50">{t('knowledge.candidate.subtitle')}</span>
      </div>

      <div className="p-4 flex flex-col gap-4 text-xs">
        {/* 当前资产解析态 */}
        {resolve.kind === 'loading' && (
          <div className="flex items-center gap-2 opacity-60">
            <Loader2 className="w-4 h-4 animate-spin" />{t('knowledge.candidate.loading')}
          </div>
        )}
        {resolve.kind === 'error' && (
          <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
            <div className="flex items-center gap-2">
              <AlertTriangle className="w-4 h-4" />
              <span>{resolve.engineDown ? t('knowledge.candidate.err_engine') : t('knowledge.candidate.err_resolve')}</span>
            </div>
            <div className="opacity-70 break-words">{resolve.message}</div>
            {resolve.traceId && <div className="font-mono text-[10px] opacity-60 break-all">traceId: {resolve.traceId}</div>}
            <button type="button" onClick={() => void runResolve(assetId)} className="inline-flex items-center gap-1 self-start px-2 py-0.5 rounded border text-[11px] cursor-pointer" style={{ borderColor: styles.cardBorder, color: styles.cardText }}>
              {t('knowledge.candidate.retry')}
            </button>
          </div>
        )}

        {current && (
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[11px] opacity-70 break-all">{current.assetId}</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded border" style={{ borderColor: styles.cardBorder }}>
              {t(`knowledge.cognitiveAsset.class.${current.assetClass}`) || current.assetClass}
            </span>
            <span className="text-[10px] px-1.5 py-0.5 rounded border" style={toneStyle}>
              {current.lifecycleState ? stateLabel(current.lifecycleState) : t('knowledge.candidate.state_legacy')}
            </span>
            {isUncalibratedKind(current.confidenceKind ?? null) && (
              <span className="inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded border" style={{ borderColor: styles.warningBorder, color: styles.warningText }}>
                <BadgeAlert className="w-3 h-3" />{t('knowledge.candidate.badge_uncalibrated')}
              </span>
            )}
            {current.confidenceKind && (
              <span className="text-[10px] opacity-50">
                {probKindLabelKey(current.confidenceKind) ? t(probKindLabelKey(current.confidenceKind) as string) || current.confidenceKind : current.confidenceKind}
                {current.confidenceProbability !== null && current.confidenceProbability !== undefined ? ` · ${current.confidenceProbability.toFixed(4)}` : ''}
              </span>
            )}
          </div>
        )}

        {/* 动作表单：生成候选 / 应用变更 两分离按钮（§3.5 护栏） */}
        {formReady && (
          <div className="flex flex-col gap-2.5">
            {!statesReady && (
              <div className="rounded-md border p-2 text-[11px] flex items-center gap-1.5" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
                <AlertTriangle className="w-3.5 h-3.5" />{t('knowledge.candidate.states_not_ready')}
              </div>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              <label className="flex flex-col gap-1">
                <span className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.candidate.label_target')}</span>
                <select className={inputCls} style={inputStyle} value={targetState} onChange={(e) => setTargetState(e.target.value)}>
                  <option value="">{t('knowledge.candidate.target_none')}</option>
                  {lifecycleStates.map((s) => (
                    <option key={s} value={s}>{stateLabel(s)}</option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.candidate.label_reason')}</span>
                <input className={inputCls} style={inputStyle} value={reason} onChange={(e) => setReason(e.target.value)} placeholder={t('knowledge.candidate.reason_ph')} />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.candidate.label_evidence')}</span>
                <input className={inputCls} style={inputStyle} value={evidenceRef} onChange={(e) => setEvidenceRef(e.target.value)} placeholder={t('knowledge.candidate.evidence_ph')} />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.candidate.label_operator')}</span>
                <input className={inputCls} style={inputStyle} value={operator} onChange={(e) => setOperator(e.target.value)} placeholder={t('knowledge.candidate.operator_ph')} />
              </label>
              <label className="flex flex-col gap-1 sm:col-span-2">
                <span className="text-[10px] uppercase tracking-wider opacity-50">{t('knowledge.candidate.label_note')}</span>
                <input className={inputCls} style={inputStyle} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('knowledge.candidate.note_ph')} />
              </label>
            </div>
            {targetState === 'SUPERSEDED' && (
              <div className="text-[10px] opacity-60">{t('knowledge.candidate.superseded_hint')}</div>
            )}
            <div className="flex items-center gap-2 flex-wrap">
              <button
                type="button"
                onClick={() => void doPropose()}
                disabled={!canSubmit || busy}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ borderColor: styles.accentBorder, color: styles.accentText }}
              >
                <ClipboardPlus className="w-3.5 h-3.5" />{busy ? t('knowledge.candidate.busy') : t('knowledge.candidate.action_propose')}
              </button>
              <button
                type="button"
                onClick={() => void doTransition()}
                disabled={!canSubmit || busy}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ borderColor: styles.cardBorder, color: styles.cardText }}
              >
                <ArrowRightLeft className="w-3.5 h-3.5" />{busy ? t('knowledge.candidate.busy') : t('knowledge.candidate.action_transition')}
              </button>
              <span className="text-[10px] opacity-50">{t('knowledge.candidate.guard_note')}</span>
            </div>
          </div>
        )}

        {/* 动作结果区（服务端原值呈现，禁本地合成） */}
        {action.kind === 'proposal' && (
          <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.cardBorder }}>
            <div className="flex items-center gap-2 text-[11px]">
              <ClipboardPlus className="w-3.5 h-3.5" style={{ color: styles.accentText }} />
              <span className="font-semibold">{t('knowledge.candidate.receipt_proposal_title')}</span>
              <span className="text-[10px] px-1.5 py-0.5 rounded border opacity-80">{action.receipt.status ?? '—'}</span>
            </div>
            <div className="font-mono text-[10px] opacity-60 break-all">
              proposalId: {action.receipt.proposalId ?? '—'} · carrier: {action.receipt.carrier ?? '—'} · eventRef: {action.receipt.eventRef ?? '—'}
            </div>
            <div className="text-[10px] opacity-50">{t('knowledge.candidate.receipt_proposal_note')}</div>
          </div>
        )}
        {action.kind === 'transition' && (
          <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.successBorder, background: styles.successBg, color: styles.successText }}>
            <div className="flex items-center gap-2 text-[11px] font-semibold">
              <ArrowRightLeft className="w-3.5 h-3.5" />
              {t('knowledge.candidate.receipt_transition', {
                from: stateLabel(action.result.fromState ?? '—'),
                to: stateLabel(action.result.toState ?? '—'),
              })}
            </div>
            <div className="font-mono text-[10px] opacity-70 break-all">
              eventRef: {action.result.eventRef ?? '—'}{action.result.traceId ? ` · traceId: ${action.result.traceId}` : ''}
            </div>
          </div>
        )}
        {action.kind === 'conflict' && (
          <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.dangerBorder, background: styles.dangerBg, color: styles.dangerText }}>
            <div className="flex items-center gap-2 text-[11px] font-semibold">
              <AlertTriangle className="w-3.5 h-3.5" />{t('knowledge.candidate.err_conflict')}
            </div>
            <div className="opacity-80 break-words">{action.message}</div>
            {action.traceId && <div className="font-mono text-[10px] opacity-60 break-all">traceId: {action.traceId}</div>}
            <div className="text-[10px] opacity-50">{t('knowledge.candidate.conflict_note')}</div>
          </div>
        )}
        {action.kind === 'failed' && (
          <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
            <div className="flex items-center gap-2 text-[11px] font-semibold">
              <AlertTriangle className="w-3.5 h-3.5" />
              {action.engineDown ? t('knowledge.candidate.err_engine') : t('knowledge.candidate.err_action')}
            </div>
            <div className="opacity-70 break-words">{action.message}</div>
            {action.traceId && <div className="font-mono text-[10px] opacity-60 break-all">traceId: {action.traceId}</div>}
          </div>
        )}
      </div>
    </div>
  );
}
