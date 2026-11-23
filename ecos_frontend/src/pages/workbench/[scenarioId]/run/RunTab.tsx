/**
 * 场景运行页（场景工作台简化 v0.3）。
 *
 * <p>发起器：分析类型 + 运行方式 + 业务输入；运行记录读锚点表；
 * 复盘按时间线展示运行 + 评估 + 动作。What-If 试算不进本面。</p>
 */
import React, { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Brain, GitCompare, Play, RefreshCw, TrendingUp, Zap } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { useMediaQuery } from '../../../../hooks/useMediaQuery';
import type { AnalysisRunVO } from '../../../../services/scenarioAnalysisClient';
import { compareScenarioAnalyses } from '../../../../services/scenarioAnalysisClient';
import { showToastGlobal } from '../../../../components/common/Toast';
import logger from '../../../../services/logger';

interface Props {
  scenarioId: string;
  analyses: AnalysisRunVO[];
  onRun: (payload: Record<string, unknown>) => Promise<AnalysisRunVO>;
  onRefresh: () => Promise<AnalysisRunVO[]>;
}

type AnalysisType = 'CAUSAL' | 'FORECAST' | 'SIMULATION';

const TYPES: Array<{ key: AnalysisType; icon: React.ReactNode; labelKey: string; hintKey: string }> = [
  { key: 'CAUSAL', icon: <Brain size={16} />, labelKey: 'workspace.run.type.causal', hintKey: 'workspace.run.type.causalHint' },
  { key: 'FORECAST', icon: <TrendingUp size={16} />, labelKey: 'workspace.run.type.forecast', hintKey: 'workspace.run.type.forecastHint' },
  { key: 'SIMULATION', icon: <Zap size={16} />, labelKey: 'workspace.run.type.simulation', hintKey: 'workspace.run.type.simulationHint' },
];

export default function RunTab({ scenarioId, analyses, onRun, onRefresh }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [type, setType] = useState<AnalysisType>('CAUSAL');
  const [mode, setMode] = useState<'FORMAL' | 'SANDBOX'>('FORMAL');
  const [metric, setMetric] = useState('');
  const [deviation, setDeviation] = useState('');
  const [caliberId, setCaliberId] = useState('');
  const [caliberVersion, setCaliberVersion] = useState('');
  const [asOfTime, setAsOfTime] = useState('');
  const [snapshotId, setSnapshotId] = useState('');
  const [formulaVersion, setFormulaVersion] = useState('');
  const [running, setRunning] = useState(false);
  const [baselineId, setBaselineId] = useState('');
  const [candidateId, setCandidateId] = useState('');
  const [compareResult, setCompareResult] = useState<Record<string, unknown> | null>(null);
  const [comparing, setComparing] = useState(false);

  const forecastRuns = useMemo(
    () => analyses.filter((a) => a.analysisType === 'FORECAST' && a.sourceType === 'FORECAST_RUN'),
    [analyses],
  );

  const submit = async () => {
    setRunning(true);
    try {
      const payload: Record<string, unknown> = { analysisType: type, runMode: mode };
      if (metric.trim()) payload.metric = metric.trim();
      if (deviation.trim()) payload.deviation = Number(deviation.trim());
      if (type === 'FORECAST') {
        if (!caliberId.trim() || !caliberVersion.trim() || !asOfTime.trim() || !snapshotId.trim() || !formulaVersion.trim()) {
          showToastGlobal('info', t('workspace.run.forecast.missing'));
          setRunning(false);
          return;
        }
        payload.caliberId = caliberId.trim();
        payload.caliberVersion = caliberVersion.trim();
        payload.asOfTime = asOfTime.trim();
        payload.snapshotId = snapshotId.trim();
        payload.formulaVersion = formulaVersion.trim();
      }
      const result = await onRun(payload);
      const createdId = result.analysisId ?? result.id ?? '';
      showToastGlobal('success', t('workspace.run.created', { id: String(createdId) }));
      await onRefresh();
    } catch (e) {
      logger.error('run-analysis', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setRunning(false);
    }
  };

  const compare = async () => {
    if (!baselineId || !candidateId) {
      showToastGlobal('info', t('workspace.run.compare.needBoth'));
      return;
    }
    setComparing(true);
    try {
      const result = await compareScenarioAnalyses(baselineId, candidateId);
      setCompareResult(result);
    } catch (e) {
      logger.error('compare', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
      setCompareResult(null);
    } finally {
      setComparing(false);
    }
  };

  return (
    <div className="p-4 space-y-4">
      {/* 发起器 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.run.launcher.title')}</h3>
        <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-3'}`}>
          {TYPES.map((tp) => (
            <button
              key={tp.key}
              type="button"
              onClick={() => setType(tp.key)}
              className={`text-left border rounded-lg p-3 cursor-pointer transition ${
                type === tp.key ? `${styles.accentBg} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardBorder}`
              }`}
            >
              <div className={`flex items-center gap-2 ${styles.cardText}`}>{tp.icon}<span className="text-xs font-bold">{t(tp.labelKey)}</span></div>
              <p className={`text-[11px] mt-1 ${styles.cardTextMuted}`}>{t(tp.hintKey)}</p>
            </button>
          ))}
        </div>
        <div className={`grid gap-3 mt-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-3'}`}>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.mode')}</span>
            <select value={mode} onChange={(e) => setMode(e.target.value as 'FORMAL' | 'SANDBOX')} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
              <option value="FORMAL">{t('workspace.run.mode.formal')}</option>
              <option value="SANDBOX">{t('workspace.run.mode.sandbox')}</option>
            </select>
          </label>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.metric')}</span>
            <input value={metric} onChange={(e) => setMetric(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
          <label className="space-y-1">
            <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.deviation')}</span>
            <input value={deviation} onChange={(e) => setDeviation(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          </label>
        </div>
        {type === 'FORECAST' && (
          <div className={`grid gap-3 mt-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
            {[
              ['caliberId', caliberId, setCaliberId],
              ['caliberVersion', caliberVersion, setCaliberVersion],
              ['asOfTime', asOfTime, setAsOfTime],
              ['snapshotId', snapshotId, setSnapshotId],
              ['formulaVersion', formulaVersion, setFormulaVersion],
            ].map(([key, value, setter]) => (
              <label key={String(key)} className="space-y-1">
                <span className={`text-xs ${styles.cardTextMuted}`}>{t(`workspace.run.forecast.${key}`)}</span>
                <input value={String(value)} onChange={(e) => (setter as React.Dispatch<React.SetStateAction<string>>)(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              </label>
            ))}
          </div>
        )}
        <div className="mt-4 flex items-center gap-2">
          <button
            type="button"
            disabled={running}
            onClick={submit}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            <Play size={12} />
            {running ? t('workspace.run.running') : t('workspace.run.run')}
          </button>
          <button
            type="button"
            onClick={() => void onRefresh()}
            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
          >
            <RefreshCw size={12} />
            {t('workspace.run.refresh')}
          </button>
        </div>
      </section>

      {/* 比较 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.run.compare.title')}</h3>
        {forecastRuns.length < 2 ? (
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.compare.empty')}</p>
        ) : (
          <>
            <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-2'}`}>
              <label className="space-y-1">
                <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.compare.baseline')}</span>
                <select value={baselineId} onChange={(e) => setBaselineId(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
                  <option value="">{t('workspace.run.compare.select')}</option>
                  {forecastRuns.map((r) => {
                    const id = String(r.analysisId ?? r.id ?? '');
                    return (
                      <option key={`base-${id}`} value={id}>
                        {id} · {r.createTime ?? ''}
                      </option>
                    );
                  })}
                </select>
              </label>
              <label className="space-y-1">
                <span className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.compare.candidate')}</span>
                <select value={candidateId} onChange={(e) => setCandidateId(e.target.value)} className={`w-full text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
                  <option value="">{t('workspace.run.compare.select')}</option>
                  {forecastRuns.map((r) => {
                    const id = String(r.analysisId ?? r.id ?? '');
                    return (
                      <option key={`cand-${id}`} value={id}>
                        {id} · {r.createTime ?? ''}
                      </option>
                    );
                  })}
                </select>
              </label>
            </div>
            <div className="mt-3">
              <button
                type="button"
                disabled={comparing}
                onClick={compare}
                className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
              >
                <GitCompare size={12} />
                {comparing ? t('workspace.run.compare.running') : t('workspace.run.compare.compare')}
              </button>
            </div>
            {compareResult && (
              <pre className={`mt-3 text-[11px] ${styles.cardText} ${styles.cardBorder} border rounded p-3 overflow-auto`}>
                {JSON.stringify(compareResult, null, 2)}
              </pre>
            )}
          </>
        )}
      </section>

      {/* 运行记录 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.run.history.title')}</h3>
        {analyses.length === 0 ? (
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.history.empty')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className={`w-full text-xs ${styles.cardText}`}>
              <thead>
                <tr className={`border-b ${styles.cardBorder} ${styles.cardTextMuted}`}>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.id')}</th>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.type')}</th>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.status')}</th>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.version')}</th>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.mode')}</th>
                  <th className="py-1 pr-2 text-left">{t('workspace.run.history.time')}</th>
                </tr>
              </thead>
              <tbody>
                {analyses.map((a) => {
                  const key = String(a.analysisId ?? a.id ?? '');
                  const typeKey = String(a.analysisType ?? a.runType ?? '').toLowerCase();
                  return (
                    <tr key={key} className={`border-b ${styles.cardBorder}`}>
                      <td className="py-1.5 pr-2 font-mono">{key}</td>
                      <td className="py-1.5 pr-2">{t(`workspace.run.type.${typeKey}Short`, String(a.analysisType ?? a.runType ?? '—'))}</td>
                      <td className="py-1.5 pr-2">{a.status ?? '—'}</td>
                      <td className="py-1.5 pr-2 font-mono">{a.definitionVersion ?? '—'}</td>
                      <td className="py-1.5 pr-2">{a.runMode ?? '—'}</td>
                      <td className="py-1.5 pr-2">{a.createTime ?? '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* 复盘 */}
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <div className="flex items-center justify-between mb-3">
          <h3 className={`text-sm font-bold ${styles.cardText}`}>{t('workspace.run.retro.title')}</h3>
          <Link
            to={`/workbench/${scenarioId}/decisions`}
            className={`text-xs font-bold px-2 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
          >
            {t('workspace.run.retro.link')}
          </Link>
        </div>
        <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.run.retro.desc')}</p>
      </section>
    </div>
  );
}
