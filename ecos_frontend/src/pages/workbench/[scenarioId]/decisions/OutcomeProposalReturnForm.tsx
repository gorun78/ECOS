/**
 * OutcomeProposalReturnForm — P09 偏差项「转入候选评审」回流表单（复盘 S2 收口 ③；
 * 设计方案 §3.4 幕10「结果回流：Outcome Evaluation → 偏差诊断 → 更新候选（进认知域
 * 候选状态机，不改生产）」）。
 *
 * <p><b>写通道单源</b>：复用 `services/cognitiveClient#submitAssetProposal`
 * （`POST /api/v1/cognitive/assets/{assetId}/proposals`，SUP-COG-03；后端实证
 * cognitive2 `CognitiveAssetCatalogController.java:63` + `CognitiveAssetLifecycleService
 * .propose:205`）。与知识台 CandidateReviewPanel 同一条 proposals 通道，禁第二套拼接。</p>
 *
 * <p><b>语义护栏（UI 文案显式说明）</b>：proposals 只创建候选提案（审计事件 +
 * status=PENDING_APPROVAL 回执），**原资产行 0 改动、不写知识库**；批准/应用须走候选
 * 评审面另行操作。目标态词表来自服务端 catalog.lifecycleStates（禁本地枚举复制）。</p>
 *
 * <p><b>入参真实性</b>：assetId 只取服务端 `OutcomeDiagnosis.assetRefs`
 * （`"hyp:<id>"/"bel:<id>"` 前缀形态，后端 `diagnose()` 实证）；evidenceRef 自动携带
 * 真实可得的结果标识（evaluationId / run 锚点 / metricRef / 结果 traceId，逐项判存在，
 * 缺项不进串，全缺则该字段不发送）。后端 `propose()` 对 targetState/reason 强校验
 * （缺失或非法 400），故二者为表单必填 —— “目标态可选”在后端契约下不成立，如实收敛。
 * 全程无假成功：回执视图仅在 promise resolve 后渲染；409=冲突横幅（原行未变更）、
 * 503=引擎降级横幅、NETWORK=离线横幅，均带服务端 traceId（有则显示）。</p>
 *
 * <p>七态：form idle / catalog 加载 / catalog 失败(可重试) / 提交中 / 回执成功 /
 * 409 冲突 / 503·其他失败（含离线）。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, ListChecks, X } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import {
  CognitiveApiError,
  fetchCognitiveAssetCatalog,
  isCognitiveApiError,
  submitAssetProposal,
  type CognitiveAssetProposalReceipt,
} from '../../../../services/cognitiveClient';

export interface OutcomeProposalReturnFormProps {
  /** 服务端 OutcomeDiagnosis.assetRefs（候选目标资产唯一定位来源）。 */
  assetRefs: string[];
  evaluationId?: string;
  runId: string;
  metricRef?: string;
  /** 评估结果体 traceId（后端回传则实拿，缺省不猜）。 */
  resultTraceId?: string;
  onClose: () => void;
}

type CatalogState =
  | { kind: 'loading' }
  | { kind: 'ready'; states: string[] }
  | { kind: 'error'; error: CognitiveApiError };

type SubmitState =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'receipt'; receipt: CognitiveAssetProposalReceipt }
  | { kind: 'conflict'; message: string; traceId?: string }
  | { kind: 'degraded'; message: string; traceId?: string }
  | { kind: 'failed'; message: string; traceId?: string };

function toCognitiveError(e: unknown): CognitiveApiError {
  return isCognitiveApiError(e)
    ? e
    : new CognitiveApiError({ code: 'NETWORK', status: 0, message: String(e) });
}

/** evidenceRef 只拼真实可得的标识（逐项判存在；全缺 ⇒ undefined，不造串）。 */
function buildEvidenceRef(parts: Array<string | undefined>): string | undefined {
  const kept = parts.filter((p): p is string => typeof p === 'string' && p.trim() !== '');
  return kept.length === 0 ? undefined : kept.join('&');
}

export default function OutcomeProposalReturnForm({
  assetRefs,
  evaluationId,
  runId,
  metricRef,
  resultTraceId,
  onClose,
}: OutcomeProposalReturnFormProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [catalog, setCatalog] = useState<CatalogState>({ kind: 'loading' });
  const [assetId, setAssetId] = useState<string>(assetRefs[0] ?? '');
  const [targetState, setTargetState] = useState('');
  const [reason, setReason] = useState('');
  const [operator, setOperator] = useState('');
  const [note, setNote] = useState('');
  const [action, setAction] = useState<SubmitState>({ kind: 'idle' });

  const loadCatalog = useCallback(async () => {
    setCatalog({ kind: 'loading' });
    try {
      const c = await fetchCognitiveAssetCatalog();
      setCatalog({ kind: 'ready', states: c.lifecycleStates });
    } catch (e) {
      setCatalog({ kind: 'error', error: toCognitiveError(e) });
    }
  }, []);

  useEffect(() => {
    void loadCatalog();
  }, [loadCatalog]);

  const evidenceRef = useMemo(
    () =>
      buildEvidenceRef([
        evaluationId ? `evaluation=${evaluationId}` : undefined,
        runId ? `run=${runId}` : undefined,
        metricRef ? `metric=${metricRef}` : undefined,
        resultTraceId ? `trace=${resultTraceId}` : undefined,
      ]),
    [evaluationId, runId, metricRef, resultTraceId],
  );

  const statesReady = catalog.kind === 'ready' && catalog.states.length > 0;
  const canSubmit =
    assetId !== '' && targetState !== '' && reason.trim() !== '' && statesReady && action.kind !== 'submitting';

  const submit = useCallback(async () => {
    if (!canSubmit) return;
    setAction({ kind: 'submitting' });
    try {
      const receipt = await submitAssetProposal(assetId, {
        targetState,
        reason: reason.trim(),
        evidenceRef,
        operator: operator.trim() === '' ? undefined : operator.trim(),
        note: note.trim() === '' ? undefined : note.trim(),
      });
      setAction({ kind: 'receipt', receipt });
    } catch (e) {
      const err = toCognitiveError(e);
      if (err.status === 409) {
        setAction({ kind: 'conflict', message: err.message, traceId: err.traceId });
      } else if (err.code === 'ENGINE_DISABLED' || err.code === 'SCHEMA_NOT_READY' || err.status === 503) {
        setAction({ kind: 'degraded', message: err.message, traceId: err.traceId });
      } else {
        setAction({ kind: 'failed', message: err.message, traceId: err.traceId });
      }
    }
  }, [canSubmit, assetId, targetState, reason, evidenceRef, operator, note]);

  const inputCls = `w-full rounded border px-2 py-1 text-xs ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`;

  /* ── 回执视图（真实 proposalId/status；服务端字段原样，缺省「—」） ── */
  if (action.kind === 'receipt') {
    const r = action.receipt;
    return (
      <div
        className={`rounded border p-2 mt-2 ${styles.cardBorder} ${styles.successBg}`}
        data-testid="return-receipt"
      >
        <h4 className={`text-[10px] font-bold uppercase flex items-center gap-1.5 ${styles.successText}`}>
          <CheckCircle2 size={12} />
          {t('scenario.p09.return.receipt_title')}
        </h4>
        <dl className="mt-1.5 space-y-0.5 text-[11px] font-mono">
          <ReceiptRow label={t('scenario.p09.return.receipt_proposal')} value={r.proposalId} />
          <ReceiptRow label={t('scenario.p09.return.receipt_asset')} value={r.assetId ?? assetId} />
          <ReceiptRow label={t('scenario.p09.return.receipt_status')} value={r.status} />
          <ReceiptRow label={t('scenario.p09.return.receipt_carrier')} value={r.carrier} />
          <ReceiptRow label={t('scenario.p09.return.receipt_event')} value={r.eventRef} />
          <ReceiptRow label={t('scenario.p09.return.receipt_trace')} value={r.traceId} />
        </dl>
        <div className="flex items-center justify-between gap-2 mt-2">
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('scenario.p09.return.guard_note')}</span>
          <button
            type="button"
            onClick={onClose}
            className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[10px] cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          >
            <X size={11} />
            {t('scenario.p09.return.close')}
          </button>
        </div>
      </div>
    );
  }

  /* ── 表单（含冲突/降级/失败横幅；均不掩真因） ── */
  return (
    <div className={`rounded border p-2 mt-2 ${styles.cardBorder}`} data-testid="return-form">
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <h4 className={`text-[10px] font-bold uppercase flex items-center gap-1.5 ${styles.cardTextMuted}`}>
          <ListChecks size={12} />
          {t('scenario.p09.return.title')}
        </h4>
        <button
          type="button"
          onClick={onClose}
          className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded border text-[10px] cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`}
          aria-label={t('scenario.p09.return.close')}
        >
          <X size={11} />
        </button>
      </div>

      {/* 409 冲突横幅（后端状态机拒绝，原行未变更） */}
      {action.kind === 'conflict' && (
        <Banner tone="danger" testId="return-conflict" titleKey="scenario.p09.return.conflict_title"
          message={action.message} traceId={action.traceId} />
      )}
      {/* 503 / 引擎禁用降级横幅 */}
      {action.kind === 'degraded' && (
        <Banner tone="warning" testId="return-degraded" titleKey="scenario.p09.return.degraded_title"
          message={action.message} traceId={action.traceId} />
      )}
      {/* 其他失败（400 透传后端 message / NETWORK 离线） */}
      {action.kind === 'failed' && (
        <Banner tone="warning" testId="return-failed" titleKey="scenario.p09.return.failed_title"
          message={action.message} traceId={action.traceId} />
      )}

      <div className="grid gap-2">
        <label className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
            {t('scenario.p09.return.label_asset')}
          </span>
          <select className={inputCls} value={assetId} onChange={(e) => setAssetId(e.target.value)} data-testid="return-asset">
            {assetRefs.map((ref) => (
              <option key={ref} value={ref}>
                {ref}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
            {t('scenario.p09.return.label_target')}
          </span>
          {catalog.kind === 'loading' && (
            <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('scenario.p09.return.catalog_loading')}</span>
          )}
          {catalog.kind === 'error' && (
            <span className={`flex items-center gap-2 text-[10px] ${styles.warningText}`}>
              {t('scenario.p09.return.catalog_failed')}
              <button
                type="button"
                onClick={() => void loadCatalog()}
                className={`underline cursor-pointer ${styles.accentText}`}
              >
                {t('scenario.p09.return.catalog_retry')}
              </button>
            </span>
          )}
          {catalog.kind === 'ready' && (
            <select
              className={inputCls}
              value={targetState}
              onChange={(e) => setTargetState(e.target.value)}
              data-testid="return-target"
            >
              <option value="">—</option>
              {catalog.states.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          )}
        </label>

        <label className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
            {t('scenario.p09.return.label_reason')}
          </span>
          <textarea
            className={`${inputCls} min-h-[48px]`}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder={t('scenario.p09.return.reason_ph')}
            data-testid="return-reason"
          />
        </label>

        <div className="block">
          <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
            {t('scenario.p09.return.label_evidence')}
          </span>
          <span className={`block text-[10px] font-mono break-all ${styles.cardText}`} data-testid="return-evidence">
            {evidenceRef ?? '—'}
          </span>
        </div>

        <div className={`grid gap-2 ${styles.cardText ? '' : ''} grid-cols-1 sm:grid-cols-2`}>
          <label className="block">
            <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
              {t('scenario.p09.return.label_operator')}
            </span>
            <input className={inputCls} value={operator} onChange={(e) => setOperator(e.target.value)} />
          </label>
          <label className="block">
            <span className={`block text-[10px] font-bold uppercase ${styles.cardTextMuted} mb-0.5`}>
              {t('scenario.p09.return.label_note')}
            </span>
            <input className={inputCls} value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
        </div>
      </div>

      <div className="flex items-center justify-between gap-2 mt-2 flex-wrap">
        <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('scenario.p09.return.required_hint')}</span>
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void submit()}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-bold cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed ${styles.infoBg} ${styles.infoText} border ${styles.cardBorder}`}
          data-testid="return-submit"
        >
          {action.kind === 'submitting' ? t('scenario.p09.return.submitting') : t('scenario.p09.return.submit')}
        </button>
      </div>

      <p className={`text-[10px] leading-relaxed mt-2 ${styles.cardTextMuted}`} data-testid="return-guard-note">
        {t('scenario.p09.return.guard_note')}
      </p>
    </div>
  );
}

/** 回执行：服务端回显值原样；缺省「—」。 */
function ReceiptRow({ label, value }: { label: string; value: string | undefined }) {
  const { styles } = useTheme();
  return (
    <div className="flex gap-2 items-baseline">
      <dt className={`shrink-0 opacity-70 ${styles.cardTextMuted}`}>{label}</dt>
      <dd className="break-all">{value && value !== '' ? value : '—'}</dd>
    </div>
  );
}

/** 语义横幅（danger=409 冲突 / warning=503 降级与其他失败），message+traceId 原样呈现。 */
function Banner({
  tone,
  titleKey,
  message,
  traceId,
  testId,
}: {
  tone: 'danger' | 'warning';
  titleKey: string;
  message: string;
  traceId?: string;
  testId: string;
}) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const toneCls =
    tone === 'danger'
      ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
      : `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`;
  return (
    <div className={`rounded border px-2 py-1.5 mb-2 text-[11px] ${toneCls}`} data-testid={testId}>
      <span className="flex items-center gap-1.5 font-bold">
        <AlertTriangle size={12} />
        {t(titleKey)}
      </span>
      {message && <p className="mt-0.5 font-mono text-[10px] break-all opacity-90">{message}</p>}
      {traceId && <p className="mt-0.5 font-mono text-[10px] opacity-80 break-all">traceId: {traceId}</p>}
    </div>
  );
}
