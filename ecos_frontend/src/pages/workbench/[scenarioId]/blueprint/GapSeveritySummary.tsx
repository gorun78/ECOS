/**
 * GapSeveritySummary — 缺口严重级服务端分类摘要（补充设计 §6.5）。
 *
 * <p>唯一数据源 = `GET {id}/gaps`（后端分类 BLOCKER/WARNING/SUGGESTION/UNKNOWN 与
 * blocking 判定）；本组件只展示，绝不本地重算或把未分级缺口默认成警告。
 * 六态：loading / ready / empty(counts 全 0) / denied(401/403) / engine(503) / error。</p>
 */
import React, { useEffect, useState } from 'react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  getBlueprintGaps,
  isBlueprintApiError,
  type BlueprintGapSummary,
} from '../../../../services/scenarioBlueprintClient';

type Phase =
  | { kind: 'loading' }
  | { kind: 'ready'; data: BlueprintGapSummary }
  | { kind: 'denied'; traceId?: string }
  | { kind: 'engine'; traceId?: string }
  | { kind: 'error' };

export default function GapSeveritySummary({ scenarioId, blueprintId }: { scenarioId: string; blueprintId: string }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    setPhase({ kind: 'loading' });
    getBlueprintGaps(scenarioId, blueprintId)
      .then((data) => {
        if (!cancelled) setPhase({ kind: 'ready', data });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (isBlueprintApiError(e) && (e.status === 401 || e.status === 403)) {
          setPhase({ kind: 'denied', traceId: e.traceId });
          return;
        }
        if (isBlueprintApiError(e) && e.status === 503) {
          setPhase({ kind: 'engine', traceId: e.traceId });
          return;
        }
        setPhase({ kind: 'error' });
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId, blueprintId]);

  if (phase.kind === 'loading') {
    return <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.bp.gap_summary.loading')}</p>;
  }
  if (phase.kind === 'denied') {
    return (
      <p className={`text-[10px] ${styles.dangerText}`}>
        {t('workspace.bp.gap_summary.denied')}
        {phase.traceId ? ` · ${t('workspace.bp.gap_summary.trace', { id: phase.traceId })}` : ''}
      </p>
    );
  }
  if (phase.kind === 'engine') {
    return (
      <p className={`text-[10px] ${styles.warningText}`}>
        {t('workspace.bp.gap_summary.engine')}
        {phase.traceId ? ` · ${t('workspace.bp.gap_summary.trace', { id: phase.traceId })}` : ''}
      </p>
    );
  }
  if (phase.kind === 'error') {
    return <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.bp.gap_summary.error')}</p>;
  }

  const { counts, openBlockers, blocking } = phase.data;
  return (
    <div className="space-y-0.5" data-testid="gap-severity-summary">
      <p className={`text-[10px] ${styles.cardTextMuted}`}>
        {t('workspace.bp.gap_summary.counts', {
          blocker: counts.BLOCKER,
          warning: counts.WARNING,
          suggestion: counts.SUGGESTION,
          unknown: counts.UNKNOWN,
        })}
      </p>
      {blocking ? (
        <p className={`text-[10px] font-bold ${styles.dangerText}`}>
          {t('workspace.bp.gap_summary.blocking', { n: openBlockers })}
        </p>
      ) : null}
    </div>
  );
}
