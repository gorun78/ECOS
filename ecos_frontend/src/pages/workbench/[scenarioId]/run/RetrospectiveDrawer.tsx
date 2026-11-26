/**
 * 场景复盘抽屉（V367 批次 C）：本次运行 + 认知侧失效/证据/告警 + 上次同题运行定位。
 *
 * <p>只呈现服务端原值；认知段 unavailable 时显示原因，不合成结论。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { BellRing, BookOpen, FlaskConical, GitCompare, X } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import logger from '../../../../services/logger';
import {
  fetchScenarioRetrospective,
  type ScenarioRetrospective,
} from '../../../../services/scenarioRetrospectiveClient';

interface Props {
  scenarioId: string;
  runId: string;
  onClose: () => void;
}

export default function RetrospectiveDrawer({ scenarioId, runId, onClose }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [data, setData] = useState<ScenarioRetrospective | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await fetchScenarioRetrospective(scenarioId, runId));
    } catch (e) {
      logger.error('scenario-retro', e);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [scenarioId, runId]);

  useEffect(() => { void load(); }, [load]);

  const cog = data?.cognitive;
  const unavailable = cog?.unavailable === true;

  return (
    <section className={`fixed inset-y-0 right-0 z-50 w-full md:w-[560px] flex flex-col border-l ${styles.cardBg} ${styles.cardBorder} ${styles.appText}`}>
      <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder}`}>
        <h3 className="text-sm font-bold flex items-center gap-1.5">
          <BookOpen size={14} />
          {t('workspace.retro.title')}
        </h3>
        <button type="button" onClick={onClose} aria-label={t('workspace.retro.close')}
          className={`p-1 rounded border cursor-pointer ${styles.cardBorder}`}>
          <X size={14} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {loading && <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.retro.loading')}</p>}
        {error && (
          <div className={`text-xs px-3 py-2 rounded border ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            {error}
            <button type="button" onClick={() => void load()} className="ml-2 underline cursor-pointer">
              {t('workspace.retro.retry')}
            </button>
          </div>
        )}

        {data && (
          <>
            <div className={`text-[11px] ${styles.cardText}`}>
              <div className="font-mono break-all">{String(data.run?.question ?? '')}</div>
              <div className={`mt-1 ${styles.cardTextMuted}`}>
                {t('workspace.retro.intent')}: {String(data.run?.intent ?? '—')} ·{' '}
                {t('workspace.retro.executor')}: {String(data.run?.executor_type ?? 'MENTAL_MODEL')}
                {data.run?.executor_id ? `:${String(data.run.executor_id)}` : ''}
              </div>
            </div>

            <div>
              <h4 className="text-[11px] font-bold flex items-center gap-1 mb-1">
                <FlaskConical size={12} />{t('workspace.retro.algorithm')}
              </h4>
              <pre className={`text-[10px] font-mono whitespace-pre-wrap break-all ${styles.cardText} max-h-40 overflow-y-auto`}>
                {JSON.stringify(data.algorithm ?? null, null, 2)}
              </pre>
            </div>

            <div>
              <h4 className="text-[11px] font-bold flex items-center gap-1 mb-1">
                <BellRing size={12} />{t('workspace.retro.cognitive')}
              </h4>
              {unavailable ? (
                <p className={`text-[11px] ${styles.warningText}`}>
                  {t('workspace.retro.cognitive_unavailable')}: {cog?.message ?? '—'}
                </p>
              ) : (
                <div className={`text-[11px] ${styles.cardText}`}>
                  <div className={styles.cardTextMuted}>
                    {t('workspace.retro.counts')}: {t('workspace.retro.invalidations')}={cog?.invalidations?.length ?? 0} ·{' '}
                    {t('workspace.retro.hypotheses')}={cog?.hypotheses?.length ?? 0} ·{' '}
                    {t('workspace.retro.evidence')}={cog?.evidence?.length ?? 0} ·{' '}
                    {t('workspace.retro.warn')}={cog?.warnAlerts?.length ?? 0}
                  </div>
                  <pre className={`mt-1 text-[10px] font-mono whitespace-pre-wrap break-all max-h-56 overflow-y-auto ${styles.cardTextMuted}`}>
                    {JSON.stringify(cog?.summary ?? {}, null, 2)}
                  </pre>
                </div>
              )}
            </div>

            {data.previous && Object.keys(data.previous).length > 0 && (
              <div>
                <h4 className="text-[11px] font-bold flex items-center gap-1 mb-1">
                  <GitCompare size={12} />{t('workspace.retro.previous')}
                </h4>
                <pre className={`text-[10px] font-mono whitespace-pre-wrap break-all ${styles.cardTextMuted}`}>
                  {JSON.stringify(data.previous, null, 2)}
                </pre>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}
