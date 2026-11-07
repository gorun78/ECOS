/**
 * CompletenessPanel — 场景完整度只读展示（详细设计-场景工作台 §3.3/§2.3）。
 *
 * <p>唯一数据源：{@link services/scenarioCompletenessClient#getCompleteness}（后端单源算法），
 * 前端只渲染 coverage/missingEdges/islands/probeErrors，不在本地重算。
 * 六态：loading / ready / empty(无必需边) / denied(403) / engine(503) / error。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, CircleOff, RefreshCw, ShieldAlert, ShieldCheck } from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  getCompleteness,
  isCompletenessApiError,
  type CompletenessReportVO,
} from '../../../../services/scenarioCompletenessClient';

interface Props {
  scenarioId: string;
}

type Phase = 'loading' | 'ready' | 'empty' | 'denied' | 'engine' | 'error';

export default function CompletenessPanel({ scenarioId }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [phase, setPhase] = useState<Phase>('loading');
  const [report, setReport] = useState<CompletenessReportVO | null>(null);
  const [err, setErr] = useState<{ msg: string; traceId?: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (refresh = false) => {
    setBusy(true);
    setErr(null);
    try {
      const data = await getCompleteness(scenarioId, refresh);
      setReport(data);
      setPhase(data.requiredEdges.length === 0 ? 'empty' : 'ready');
    } catch (e) {
      if (isCompletenessApiError(e)) {
        if (e.status === 403 || e.status === 401) setPhase('denied');
        else if (e.status === 503) setPhase('engine');
        else setPhase('error');
        setErr({ msg: e.message, traceId: e.traceId });
      } else {
        setPhase('error');
        setErr({ msg: e instanceof Error ? e.message : String(e) });
      }
    } finally {
      setBusy(false);
    }
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (phase === 'loading') {
    return <div className={`text-xs p-3 ${styles.cardTextMuted}`}>{t('workspace.completeness.loading')}</div>;
  }
  if (phase === 'denied') {
    return (
      <div className={`flex items-center gap-2 text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <ShieldAlert size={14} /> {t('workspace.completeness.denied')}
      </div>
    );
  }
  if (phase === 'engine') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <div className="flex items-center gap-2 font-bold">
          <AlertTriangle size={14} /> {t('workspace.completeness.engine')}
        </div>
        {err?.traceId ? <div className={styles.cardTextMuted}>traceId: {err.traceId}</div> : null}
      </div>
    );
  }
  if (phase === 'error') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        {t('workspace.completeness.error')}: {err?.msg}
      </div>
    );
  }
  if (phase === 'empty') {
    return (
      <div className={`text-xs p-3 rounded border ${styles.cardBorder} ${styles.cardText}`}>
        <CircleOff size={14} className="inline mr-1" /> {t('workspace.completeness.not_applicable')}
      </div>
    );
  }

  const coverage = report?.coverage === null || report?.coverage === undefined
    ? '—'
    : `${Math.round(report.coverage * 100)}%`;

  return (
    <div className="space-y-2 text-xs">
      <div className="flex items-center justify-between">
        <div className={`flex items-center gap-1 font-bold ${styles.cardText}`}>
          <ShieldCheck size={14} /> {t('workspace.completeness.title')}
        </div>
        <button type="button" disabled={busy} onClick={() => void load(true)}
          className={`inline-flex items-center gap-1 ${styles.cardTextMuted}`}>
          <RefreshCw size={12} /> {t('workspace.completeness.refresh')}
        </button>
      </div>
      <div className={`rounded border p-2 ${styles.cardBorder}`}>
        <div className={`font-bold ${styles.cardText}`}>
          {t('workspace.completeness.coverage')}: {coverage} · {report?.verdict}
        </div>
        <div className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.completeness.single_source')}</div>
      </div>
      {report?.missingEdges.length ? (
        <div className={`rounded border p-2 ${styles.cardBorder}`}>
          <div className={`font-bold ${styles.cardText}`}>{t('workspace.completeness.missing_edges')}</div>
          {report.missingEdges.map((e) => (
            <div key={`${e.type}-${e.labelKey}`} className={styles.cardTextMuted}>
              [{e.severity ?? 'BLOCKER'}] [{e.type}] {t(e.labelKey)} · {e.reason}
            </div>
          ))}
        </div>
      ) : null}
      {report?.islands.length ? (
        <div className={`rounded border p-2 ${styles.cardBorder}`}>
          <div className={`font-bold ${styles.cardText}`}>{t('workspace.completeness.islands')}</div>
          {report.islands.map((i) => (
            <div key={i.bindingId} className={styles.cardTextMuted}>
              [{i.severity ?? 'BLOCKER'}] {i.name} ({i.bindingType}) · {i.reason}
            </div>
          ))}
        </div>
      ) : null}
      {report?.probeErrors.length ? (
        <div className={`rounded border p-2 ${styles.cardBorder}`}>
          <div className={`font-bold ${styles.cardText}`}>{t('workspace.completeness.probe_errors')}</div>
          {report.probeErrors.map((p) => (
            <div key={p} className={styles.cardTextMuted}>{p}</div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
