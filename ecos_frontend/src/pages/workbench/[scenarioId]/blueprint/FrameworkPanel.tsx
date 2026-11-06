/**
 * FrameworkPanel — 认知框架注册表 / 策略选择 / 计划校验面板
 * （详细设计-认知引擎 v1.0 §5/§6；唯一通道 services/cognitiveFrameworkClient）。
 *
 * <p>无假真理：候选分只展示后端 `scoreKind=ORDINAL_ONLY` 与 `calibrated=false`
 * 的排序值，不解释为概率/置信度；硬约束排除项原样展示原因码；计划校验失败原样展示 issues。
 * 六态：loading / ready / empty / denied(403) / engine(503) / error，均带 traceId。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  BrainCircuit,
  CheckCircle2,
  CircleOff,
  PlayCircle,
  RefreshCw,
  ShieldAlert,
} from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  isCognitiveFrameworkApiError,
  listFrameworks,
  selectFrameworks,
  type CognitiveFrameworkVO,
  type FrameworkSelectionResultVO,
} from '../../../../services/cognitiveFrameworkClient';

interface Props {
  scenarioId: string;
  blueprintId?: string;
  blueprintVersion?: number | null;
}

type Phase = 'loading' | 'ready' | 'empty' | 'denied' | 'engine' | 'error';

function splitCsv(v: string): string[] {
  return v
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

export default function FrameworkPanel({ scenarioId, blueprintId, blueprintVersion }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [phase, setPhase] = useState<Phase>('loading');
  const [frameworks, setFrameworks] = useState<CognitiveFrameworkVO[]>([]);
  const [problemType, setProblemType] = useState('problem_framing');
  const [domain, setDomain] = useState('enterprise_operations');
  const [presentInputs, setPresentInputs] = useState('');
  const [capabilities, setCapabilities] = useState('');
  const [riskLevel, setRiskLevel] = useState('MEDIUM');
  const [selection, setSelection] = useState<FrameworkSelectionResultVO | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<{ status: number; msg: string; traceId?: string } | null>(null);

  const load = useCallback(async () => {
    setPhase('loading');
    setErr(null);
    try {
      const rows = await listFrameworks({ status: 'PUBLISHED' });
      setFrameworks(rows);
      setPhase(rows.length === 0 ? 'empty' : 'ready');
    } catch (e) {
      if (isCognitiveFrameworkApiError(e)) {
        if (e.status === 403 || e.status === 401) setPhase('denied');
        else if (e.status === 503) setPhase('engine');
        else setPhase('error');
        setErr({ status: e.status, msg: e.message, traceId: e.traceId });
      } else {
        setPhase('error');
        setErr({ status: 0, msg: e instanceof Error ? e.message : String(e) });
      }
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const runSelect = useCallback(async () => {
    setBusy(true);
    setErr(null);
    try {
      const result = await selectFrameworks({
        requestId: `ui-${Date.now()}`,
        scenarioId,
        blueprintId,
        blueprintVersion,
        problemType,
        domain,
        presentInputs: splitCsv(presentInputs),
        availableCapabilities: splitCsv(capabilities),
        riskLevel,
        authorized: true,
        budget: { maxSteps: 10, maxTokens: 12000 },
      });
      setSelection(result);
    } catch (e) {
      if (isCognitiveFrameworkApiError(e)) {
        if (e.status === 403 || e.status === 401) setPhase('denied');
        else if (e.status === 503) setPhase('engine');
        setErr({ status: e.status, msg: e.message, traceId: e.traceId });
      } else {
        setErr({ status: 0, msg: e instanceof Error ? e.message : String(e) });
      }
    } finally {
      setBusy(false);
    }
  }, [scenarioId, blueprintId, blueprintVersion, problemType, domain, presentInputs, capabilities, riskLevel]);

  if (phase === 'loading') {
    return <div className={`text-xs p-3 ${styles.cardTextMuted}`}>{t('workspace.framework.loading')}</div>;
  }
  if (phase === 'denied') {
    return (
      <div className={`flex items-center gap-2 text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <ShieldAlert size={14} />
        {t('workspace.framework.denied')}
      </div>
    );
  }
  if (phase === 'engine') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <div className="flex items-center gap-2 font-bold">
          <AlertTriangle size={14} />
          {t('workspace.framework.engine')}
        </div>
        {err?.traceId ? <div className={`mt-1 ${styles.cardTextMuted}`}>traceId: {err.traceId}</div> : null}
        <button type="button" onClick={() => void load()} className={`mt-2 inline-flex items-center gap-1 font-bold ${styles.accentText}`}>
          <RefreshCw size={12} /> {t('workspace.framework.retry')}
        </button>
      </div>
    );
  }
  if (phase === 'error') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        {t('workspace.framework.error')}: {err?.msg}
        {err?.traceId ? <span className={`ml-2 ${styles.cardTextMuted}`}>traceId: {err.traceId}</span> : null}
      </div>
    );
  }
  if (phase === 'empty') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <CircleOff size={14} className="inline mr-1" />
        {t('workspace.framework.empty')}
      </div>
    );
  }

  return (
    <div className="space-y-3 text-xs">
      <div className="flex items-center justify-between">
        <div className={`flex items-center gap-1 font-bold ${styles.cardText}`}>
          <BrainCircuit size={14} /> {t('workspace.framework.title')}
        </div>
        <button type="button" onClick={() => void load()} className={`inline-flex items-center gap-1 ${styles.cardTextMuted}`}>
          <RefreshCw size={12} /> {t('workspace.framework.refresh')}
        </button>
      </div>

      <div className={`rounded border p-2 space-y-2 ${styles.cardBorder}`}>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <label className="space-y-1">
            <span className={styles.cardTextMuted}>{t('workspace.framework.problem_type')}</span>
            <input value={problemType} onChange={(e) => setProblemType(e.target.value)}
              className={`w-full rounded border px-2 py-1 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`} />
          </label>
          <label className="space-y-1">
            <span className={styles.cardTextMuted}>{t('workspace.framework.domain')}</span>
            <input value={domain} onChange={(e) => setDomain(e.target.value)}
              className={`w-full rounded border px-2 py-1 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`} />
          </label>
          <label className="space-y-1">
            <span className={styles.cardTextMuted}>{t('workspace.framework.risk_level')}</span>
            <select value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}
              className={`w-full rounded border px-2 py-1 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}>
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
            </select>
          </label>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          <label className="space-y-1">
            <span className={styles.cardTextMuted}>{t('workspace.framework.present_inputs')}</span>
            <input value={presentInputs} onChange={(e) => setPresentInputs(e.target.value)}
              placeholder={t('workspace.framework.present_inputs_ph')}
              className={`w-full rounded border px-2 py-1 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`} />
          </label>
          <label className="space-y-1">
            <span className={styles.cardTextMuted}>{t('workspace.framework.capabilities')}</span>
            <input value={capabilities} onChange={(e) => setCapabilities(e.target.value)}
              placeholder={t('workspace.framework.capabilities_ph')}
              className={`w-full rounded border px-2 py-1 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`} />
          </label>
        </div>
        <button type="button" disabled={busy} onClick={() => void runSelect()}
          className={`inline-flex items-center gap-1 rounded border px-2 py-1 font-bold disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
          <PlayCircle size={12} /> {busy ? t('workspace.framework.selecting') : t('workspace.framework.select')}
        </button>
      </div>

      <div className="space-y-1">
        <div className={`font-bold ${styles.cardText}`}>{t('workspace.framework.registered')} ({frameworks.length})</div>
        {frameworks.map((f) => (
          <div key={`${f.frameworkId}@${f.version}`} className={`rounded border px-2 py-1 ${styles.cardBorder}`}>
            <span className={`font-bold ${styles.cardText}`}>{f.frameworkId}</span>
            <span className={styles.cardTextMuted}> · {f.version} · {f.name}</span>
            <span className={`ml-2 ${styles.badgeBg} ${styles.badgeText} px-1 rounded`}>{f.status}</span>
          </div>
        ))}
      </div>

      {err ? (
        <div className={`rounded border px-2 py-1 ${styles.cardBorder} ${styles.cardText}`}>
          {err.msg}{err.traceId ? <span className={`ml-2 ${styles.cardTextMuted}`}>traceId: {err.traceId}</span> : null}
        </div>
      ) : null}

      {selection ? (
        <div className="space-y-2">
          <div className={`font-bold ${styles.cardText}`}>
            {t('workspace.framework.selection_status')}: {selection.status}
          </div>
          <div className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.framework.ordinal_note')}</div>
          {selection.selectedFrameworks.map((f) => (
            <div key={`sel-${f.frameworkId}`} className={`rounded border px-2 py-1 ${styles.cardBorder}`}>
              <CheckCircle2 size={12} className="inline mr-1" />
              {f.frameworkId}@{f.version} · {t('workspace.framework.order_score')}={f.score} · {f.scoreKind}
              {f.calibrated === false ? ` · ${t('workspace.framework.not_calibrated')}` : ''}
            </div>
          ))}
          {selection.excludedFrameworks.map((f) => (
            <div key={`ex-${f.frameworkId}`} className={`rounded border px-2 py-1 ${styles.cardBorder}`}>
              <CircleOff size={12} className="inline mr-1" />
              {f.frameworkId}@{f.version} · {(f.reasons ?? []).join(', ')}
            </div>
          ))}
          {selection.missingInputs.length ? (
            <div className={styles.cardTextMuted}>
              {t('workspace.framework.missing_inputs')}: {selection.missingInputs.join(', ')}
            </div>
          ) : null}
          {selection.planValidation ? (
            <div className={`rounded border px-2 py-1 ${styles.cardBorder}`}>
              <div className={styles.cardText}>
                {t('workspace.framework.plan_valid')}: {String(selection.planValidation.passed)} · DAG:{' '}
                {String(selection.planValidation.dagAcyclic)}
              </div>
              {selection.planValidation.issues.map((i) => (
                <div key={i.code + i.detail} className={styles.cardTextMuted}>[{i.severity}] {i.code}: {i.detail}</div>
              ))}
            </div>
          ) : null}
          {selection.usedFrameworkVersions?.length ? (
            <div className={`text-[11px] ${styles.cardTextMuted}`}>
              {t('workspace.framework.used_versions')}:{' '}
              {selection.usedFrameworkVersions.map((u) => `${u.frameworkId}@${u.version}`).join(', ')}
            </div>
          ) : null}
          {selection.planRevision ? (
            <div className={`rounded border px-2 py-1 ${styles.cardBorder}`}>
              <div className={styles.cardText}>
                {t('workspace.framework.plan_revision')}: {selection.planRevision.decision} · {selection.planRevision.reason}
              </div>
              {(selection.planRevision.issues ?? []).map((i) => (
                <div key={`rev-${i.code}-${i.detail}`} className={styles.cardTextMuted}>[{i.severity}] {i.code}: {i.detail}</div>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
