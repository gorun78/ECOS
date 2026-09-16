/**
 * 时间回放子面板（PMO-59 P4b T2）。
 *
 * <p>人机干预入口之二：选择业务域 + 变量 + 历史版本号，调
 * `GET /api/v1/cognitive/beliefs/{variable}/{version}/replay`（只读重算，0 写库），
 * 渲染回放元信息（replayedVersion / currentVersion / believedDistribution / 回放基准时刻 /
 * 当时有效假设数）与风险四指标（可与当前版本对比）。</p>
 *
 * <p>版本来源：`GET /api/v1/cognitive/beliefs?domain=` 读取 V129 版本链（同变量多版本行）。</p>
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import LucideIcon from '../../components/LucideIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import { showToastGlobal } from '../../components/common/Toast';
import {
  formatNum,
  formatTime,
  type BeliefDistribution,
  type CounterfactualResult,
  type Intervention,
} from './mentalTypes';

export default function ReplayTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [domain, setDomain] = useState('');
  const [variable, setVariable] = useState('');
  const [version, setVersion] = useState('');
  const [sampleCount, setSampleCount] = useState('1000');
  const [seed, setSeed] = useState('42');
  const [interventionOn, setInterventionOn] = useState(false);
  const [interventionVariable, setInterventionVariable] = useState('');
  const [interventionOp, setInterventionOp] = useState<'SET' | 'DELTA'>('SET');
  const [interventionValue, setInterventionValue] = useState('');
  const [beliefs, setBeliefs] = useState<BeliefDistribution[]>([]);
  const [loadingBeliefs, setLoadingBeliefs] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<CounterfactualResult | null>(null);

  /** 按变量分组版本链（同变量多版本行 → 版本降序） */
  const grouped = useMemo(() => {
    const map = new Map<string, number[]>();
    beliefs.forEach((b) => {
      const key = b.variableName ?? '';
      if (!key) {
        return;
      }
      const versions = map.get(key) ?? [];
      versions.push(b.version);
      map.set(key, versions);
    });
    return Array.from(map.entries()).map(([name, versions]) => ({
      name,
      versions: [...versions].sort((a, b) => b - a),
    }));
  }, [beliefs]);

  /** 拉取该域下不确定性判断版本链（写后刷新同一入口） */
  const loadBeliefs = useCallback(async (domainValue: string) => {
    setLoadingBeliefs(true);
    try {
      const data = await apiFetchData<BeliefDistribution[]>(
        `/api/v1/cognitive/beliefs?domain=${encodeURIComponent(domainValue)}`
      );
      setBeliefs(Array.isArray(data) ? data : []);
    } catch (e) {
      setBeliefs([]);
      showToastGlobal('error', `${t('scenario.iv.rp.loadFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setLoadingBeliefs(false);
    }
  }, [t]);

  useEffect(() => {
    const domainValue = domain.trim();
    if (!domainValue) {
      setBeliefs([]);
      return;
    }
    const timer = window.setTimeout(() => {
      void loadBeliefs(domainValue);
    }, 400);
    return () => window.clearTimeout(timer);
  }, [domain, loadBeliefs]);

  /** 切换变量时默认选最新版本 */
  const handleVariableChange = (name: string) => {
    setVariable(name);
    const target = grouped.find((g) => g.name === name);
    setVersion(target && target.versions.length > 0 ? String(target.versions[0]) : '');
  };

  const versions = grouped.find((g) => g.name === variable)?.versions ?? [];

  /** 发起回放：拼 query（含可选干预 JSON 串），同参同 seed 可复现 */
  const handleReplay = async () => {
    if (running) {
      return;
    }
    const domainValue = domain.trim();
    const variableValue = variable.trim();
    if (!domainValue || !variableValue || version.trim() === '') {
      showToastGlobal('error', t('scenario.iv.rp.needInput'));
      return;
    }
    const params = new URLSearchParams({ domain: domainValue });
    if (sampleCount.trim() !== '') {
      params.set('sampleCount', sampleCount.trim());
    }
    if (seed.trim() !== '') {
      params.set('seed', seed.trim());
    }
    if (interventionOn && interventionVariable.trim() !== '') {
      const interventions: Intervention[] = [
        {
          variableName: interventionVariable.trim(),
          op: interventionOp,
          value: Number(interventionValue) || 0,
        },
      ];
      params.set('interventions', JSON.stringify(interventions));
    }

    setRunning(true);
    try {
      const data = await apiFetchData<CounterfactualResult>(
        `/api/v1/cognitive/beliefs/${encodeURIComponent(variableValue)}/${encodeURIComponent(version.trim())}/replay?${params.toString()}`
      );
      setResult(data);
      showToastGlobal('success', t('scenario.iv.rp.runOk'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.iv.rp.runFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setRunning(false);
    }
  };

  const meta = result?.replayMeta;
  const metrics = result?.riskMetrics;
  const volatility = metrics?.volatilityRange ?? [];

  return (
    <div className="space-y-4">
      {/* ── 回放配置 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="History" size={14} />
          <span className="text-sm font-semibold">{t('scenario.iv.rp.title')}</span>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-normal`}>{t('scenario.iv.rp.desc')}</p>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rp.domain')}
            </span>
            <input
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
              placeholder={t('scenario.iv.cf.domainPh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rp.variable')}
            </span>
            <select
              value={variable}
              onChange={(e) => handleVariableChange(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            >
              <option value="">{loadingBeliefs ? t('scenario.iv.rp.loading') : t('scenario.iv.rp.variablePh')}</option>
              {grouped.map((g) => (
                <option key={g.name} value={g.name}>
                  {g.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.rp.version')}
            </span>
            <select
              value={version}
              onChange={(e) => setVersion(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            >
              <option value="">{t('scenario.iv.rp.versionPh')}</option>
              {versions.map((v) => (
                <option key={v} value={v}>
                  v{v}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.sampleCount')} / {t('scenario.iv.cf.seed')}
            </span>
            <div className="flex gap-1.5 mt-1">
              <input
                type="number"
                min={10}
                max={5000}
                value={sampleCount}
                onChange={(e) => setSampleCount(e.target.value)}
                className={`w-1/2 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <input
                value={seed}
                onChange={(e) => setSeed(e.target.value)}
                className={`w-1/2 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
            </div>
          </label>
        </div>

        {/* 可选干预（do(A)） */}
        <div className="space-y-2">
          <label className="flex items-center gap-1.5 text-[11px] cursor-pointer">
            <input
              type="checkbox"
              checked={interventionOn}
              onChange={(e) => setInterventionOn(e.target.checked)}
              className="cursor-pointer"
            />
            <span className={styles.cardTextMuted}>{t('scenario.iv.rp.interventionOn')}</span>
          </label>
          {interventionOn && (
            <div className="grid grid-cols-12 gap-2 items-center">
              <input
                value={interventionVariable}
                onChange={(e) => setInterventionVariable(e.target.value)}
                placeholder={t('scenario.iv.cf.interventionPh')}
                className={`col-span-6 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <select
                value={interventionOp}
                onChange={(e) => setInterventionOp(e.target.value as 'SET' | 'DELTA')}
                className={`col-span-2 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              >
                <option value="SET">{t('scenario.iv.cf.opSet')}</option>
                <option value="DELTA">{t('scenario.iv.cf.opDelta')}</option>
              </select>
              <input
                type="number"
                step="0.01"
                value={interventionValue}
                onChange={(e) => setInterventionValue(e.target.value)}
                className={`col-span-4 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
            </div>
          )}
        </div>

        <div className="flex justify-end">
          <button
            onClick={handleReplay}
            disabled={running}
            className="px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-[11px] font-bold transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <LucideIcon name="History" size={12} className={running ? 'animate-pulse' : ''} />
            {running ? t('scenario.iv.rp.running') : t('scenario.iv.rp.run')}
          </button>
        </div>
      </div>

      {!result && (
        <p className={`text-xs ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <LucideIcon name="Info" size={12} />
          {t('scenario.iv.rp.idle')}
        </p>
      )}

      {result && (
        <div className="space-y-4">
          {/* 版本对比 */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400">
              {t('scenario.iv.rp.compare')}
            </span>
            <div className="flex flex-wrap items-center gap-4 text-[11px]">
              <span className={styles.cardTextMuted}>
                {t('scenario.iv.rp.replayedVersion')}:{' '}
                <span className="font-mono text-indigo-400">v{meta?.replayedVersion ?? '-'}</span>
              </span>
              <span className={styles.cardTextMuted}>
                {t('scenario.iv.rp.currentVersion')}:{' '}
                <span className="font-mono text-emerald-400">v{meta?.currentVersion ?? '-'}</span>
              </span>
              <span className={styles.cardTextMuted}>
                {t('scenario.iv.rp.assumptionsValid')}:{' '}
                <span className={`font-mono ${styles.cardText}`}>{meta?.assumptionsValidAtReplayTime ?? 0}</span>
              </span>
              <span className={styles.cardTextMuted}>
                {t('scenario.iv.rp.versionUpdatedAt')}:{' '}
                <span className={`font-mono ${styles.cardText}`}>{formatTime(meta?.versionUpdatedAt, '-')}</span>
              </span>
            </div>
          </div>

          {/* 回放分布快照 */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <div className="flex items-center justify-between">
              <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
                {t('scenario.iv.rp.believedDistribution')}
              </span>
              <span className={`text-[10px] font-bold ${styles.cardTextMuted}`}>
                {t('scenario.iv.rp.snapshotHint')}
              </span>
            </div>
            {(meta?.believedDistribution ?? []).length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.rp.distEmpty')}</p>
            ) : (
              <ul className="space-y-2">
                {(meta?.believedDistribution ?? []).map((p) => (
                  <li key={p.outcome} className="space-y-1">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className={`font-mono ${styles.cardText}`}>{p.outcome}</span>
                      <span className={`font-mono ${styles.cardTextMuted}`}>{formatNum(p.prob)}</span>
                    </div>
                    <div className={`h-1.5 rounded ${styles.inputBg} overflow-hidden`}>
                      <div
                        className="h-full bg-indigo-500"
                        style={{ width: `${Math.max(0, Math.min(1, p.prob)) * 100}%` }}
                      />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* 四指标（回放口径） */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <ReplayMetric label={t('scenario.iv.cf.expectedBenefit')} value={formatNum(metrics?.expectedBenefit)} tone="text-indigo-400" />
            <ReplayMetric label={t('scenario.iv.cf.maxDrawdown')} value={formatNum(metrics?.maxDrawdown)} tone="text-amber-400" />
            <ReplayMetric label={t('scenario.iv.cf.lossProbability')} value={formatNum(metrics?.lossProbability)} tone="text-red-400" />
            <ReplayMetric
              label={t('scenario.iv.cf.volatilityRange')}
              value={volatility.length >= 2 ? `[${formatNum(volatility[0])}, ${formatNum(volatility[1])}]` : '-'}
              tone="text-emerald-400"
            />
          </div>
        </div>
      )}
    </div>
  );
}

/** 回放指标卡（与推演四指标同构，仅数据口径为历史版本重算） */
function ReplayMetric({ label, value, tone }: { label: string; value: string; tone: string }) {
  const { styles } = useTheme();
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-1`}>
      <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>{label}</span>
      <span className={`text-sm font-mono font-semibold ${tone}`}>{value}</span>
    </div>
  );
}
