/**
 * 仿真推演子面板（PMO-59 P4b T1）。
 *
 * <p>人机干预入口之一：填写业务域 + 主变量 + 干预项（SET 定点 / DELTA 平移）+ 采样次数/seed，
 * 调 `POST /api/v1/cognitive/counterfactual`（do(A) 反事实推演，纯 Java 蒙特卡洛，0 LLM），
 * 渲染风险四指标（期望收益/最大回撤/亏损概率/波动区间）+ 敏感性 Top3 + 假设前提留痕
 * （assumptionRefs 有效假设 / excludedAssumptions 已排除假设）。</p>
 */
import React, { useState } from 'react';
import LucideIcon from '../../components/LucideIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { apiFetchData } from '../../api';
import { showToastGlobal } from '../../components/common/Toast';
import { formatNum, type CounterfactualResult, type Intervention } from './mentalTypes';

/** 干预项草稿（表单态，value 用字符串以便空输入不被 Number() 吞成 0） */
interface InterventionDraft {
  variableName: string;
  op: 'SET' | 'DELTA';
  value: string;
}

const EMPTY_DRAFT: InterventionDraft = { variableName: '', op: 'SET', value: '' };

export default function CounterfactualTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [domain, setDomain] = useState('');
  const [variableName, setVariableName] = useState('');
  const [sampleCount, setSampleCount] = useState('1000');
  const [seed, setSeed] = useState('42');
  const [drafts, setDrafts] = useState<InterventionDraft[]>([{ ...EMPTY_DRAFT }]);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<CounterfactualResult | null>(null);

  /** 更新第 index 项干预草稿 */
  const patchDraft = (index: number, patch: Partial<InterventionDraft>) => {
    setDrafts((prev) => prev.map((d, i) => (i === index ? { ...d, ...patch } : d)));
  };

  const addDraft = () => setDrafts((prev) => [...prev, { ...EMPTY_DRAFT }]);

  const removeDraft = (index: number) => {
    setDrafts((prev) => (prev.length <= 1 ? [{ ...EMPTY_DRAFT }] : prev.filter((_, i) => i !== index)));
  };

  /** 发起推演：校验必填 → 组装干预项 → 调后端 → 渲染四指标与敏感性 */
  const handleRun = async () => {
    if (running) {
      return;
    }
    const domainValue = domain.trim();
    const variableValue = variableName.trim();
    if (!domainValue || !variableValue) {
      showToastGlobal('error', t('scenario.iv.cf.needInput'));
      return;
    }
    const interventions: Intervention[] = drafts
      .filter((d) => d.variableName.trim() !== '')
      .map((d) => ({ variableName: d.variableName.trim(), op: d.op, value: Number(d.value) || 0 }));

    setRunning(true);
    try {
      const data = await apiFetchData<CounterfactualResult>('/api/v1/cognitive/counterfactual', {
        method: 'POST',
        body: JSON.stringify({
          domain: domainValue,
          variableName: variableValue,
          sampleCount: Number(sampleCount) || undefined,
          seed: seed.trim() === '' ? undefined : Number(seed),
          interventions,
        }),
      });
      setResult(data);
      showToastGlobal('success', t('scenario.iv.cf.runOk'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.iv.cf.runFail')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setRunning(false);
    }
  };

  const metrics = result?.riskMetrics;
  const volatility = metrics?.volatilityRange ?? [];

  return (
    <div className="space-y-4">
      {/* ── 干预配置 ── */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="SlidersHorizontal" size={14} />
          <span className="text-sm font-semibold">{t('scenario.iv.cf.title')}</span>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} leading-normal`}>{t('scenario.iv.cf.desc')}</p>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.domain')}
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
              {t('scenario.iv.cf.variable')}
            </span>
            <input
              value={variableName}
              onChange={(e) => setVariableName(e.target.value)}
              placeholder={t('scenario.iv.cf.variablePh')}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.sampleCount')}
            </span>
            <input
              type="number"
              min={10}
              max={5000}
              value={sampleCount}
              onChange={(e) => setSampleCount(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
          <label className="block">
            <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.seed')}
            </span>
            <input
              value={seed}
              onChange={(e) => setSeed(e.target.value)}
              className={`w-full mt-1 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
            />
          </label>
        </div>

        {/* ── 干预项（do(A) 列表） ── */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.interventions')}
            </span>
            <button
              onClick={addDraft}
              className={`text-[10px] font-bold px-2 py-1 rounded border ${styles.inputBorder} ${styles.cardTextMuted} hover:border-indigo-500/50 transition-colors cursor-pointer flex items-center gap-1`}
            >
              <LucideIcon name="Plus" size={10} />
              {t('scenario.iv.cf.addIntervention')}
            </button>
          </div>
          {drafts.map((d, i) => (
            <div key={i} className="grid grid-cols-12 gap-2 items-center">
              <input
                value={d.variableName}
                onChange={(e) => patchDraft(i, { variableName: e.target.value })}
                placeholder={t('scenario.iv.cf.interventionPh')}
                className={`col-span-6 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <select
                value={d.op}
                onChange={(e) => patchDraft(i, { op: e.target.value as 'SET' | 'DELTA' })}
                className={`col-span-2 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              >
                <option value="SET">{t('scenario.iv.cf.opSet')}</option>
                <option value="DELTA">{t('scenario.iv.cf.opDelta')}</option>
              </select>
              <input
                type="number"
                step="0.01"
                value={d.value}
                onChange={(e) => patchDraft(i, { value: e.target.value })}
                placeholder={t('scenario.iv.cf.valuePh')}
                className={`col-span-3 px-2 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
              />
              <button
                onClick={() => removeDraft(i)}
                title={t('scenario.iv.cf.removeIntervention')}
                className={`col-span-1 flex items-center justify-center py-1.5 rounded border ${styles.inputBorder} ${styles.cardTextMuted} hover:border-red-500/60 hover:text-red-400 transition-colors cursor-pointer`}
              >
                <LucideIcon name="Trash2" size={11} />
              </button>
            </div>
          ))}
        </div>

        <div className="flex justify-end">
          <button
            onClick={handleRun}
            disabled={running}
            className="px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-[11px] font-bold transition-colors cursor-pointer flex items-center gap-1.5"
          >
            <LucideIcon name="Play" size={12} className={running ? 'animate-pulse' : ''} />
            {running ? t('scenario.iv.cf.running') : t('scenario.iv.cf.run')}
          </button>
        </div>
      </div>

      {/* ── 推演结果 ── */}
      {!result && (
        <p className={`text-xs ${styles.cardTextMuted} flex items-center gap-1.5`}>
          <LucideIcon name="Info" size={12} />
          {t('scenario.iv.cf.idle')}
        </p>
      )}

      {result && (
        <div className="space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex flex-wrap items-center gap-3`}>
            <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-400">
              {t('scenario.iv.cf.resultTitle')}
            </span>
            <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
              N={result.sampleCount} · seed={result.seed}
            </span>
            {result.variableName && (
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{result.variableName}</span>
            )}
          </div>

          {result.scenarioSummary && (
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 text-xs ${styles.cardText} leading-normal`}>
              {result.scenarioSummary}
            </div>
          )}

          {/* 风险四指标 */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
            <MetricBox
              label={t('scenario.iv.cf.expectedBenefit')}
              value={formatNum(metrics?.expectedBenefit)}
              tone="indigo"
            />
            <MetricBox label={t('scenario.iv.cf.maxDrawdown')} value={formatNum(metrics?.maxDrawdown)} tone="amber" />
            <MetricBox
              label={t('scenario.iv.cf.lossProbability')}
              value={formatNum(metrics?.lossProbability)}
              tone="red"
            />
            <MetricBox
              label={t('scenario.iv.cf.volatilityRange')}
              value={volatility.length >= 2 ? `[${formatNum(volatility[0])}, ${formatNum(volatility[1])}]` : '-'}
              tone="emerald"
            />
          </div>

          {/* 基线/干预均值 */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex flex-wrap gap-4 text-[11px]`}>
            <span className={styles.cardTextMuted}>
              {t('scenario.iv.cf.baselineMean')}: <span className={`font-mono ${styles.cardText}`}>{formatNum(result.baselineMean)}</span>
            </span>
            <span className={styles.cardTextMuted}>
              {t('scenario.iv.cf.intervenedMean')}: <span className={`font-mono ${styles.cardText}`}>{formatNum(result.intervenedMean)}</span>
            </span>
          </div>

          {/* 敏感性 Top3 */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.sensitivity')}
            </span>
            {result.sensitivityTop3.length === 0 ? (
              <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.cf.sensitivityEmpty')}</p>
            ) : (
              <ul className="space-y-1">
                {result.sensitivityTop3.map((s) => (
                  <li key={s.variable} className="flex items-center gap-2 text-[11px]">
                    <span className={`font-mono ${styles.cardText}`}>{s.variable}</span>
                    <span className="font-mono text-indigo-400">{formatNum(s.sensitivity)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* 假设前提留痕 */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-2`}>
            <span className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
              {t('scenario.iv.cf.assumptions')}
            </span>
            <div className="space-y-1">
              <span className={`text-[10px] font-bold ${styles.cardTextMuted}`}>{t('scenario.iv.cf.assumptionRefs')}</span>
              {result.assumptionRefs.length === 0 ? (
                <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('scenario.iv.cf.assumptionsEmpty')}</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {result.assumptionRefs.map((id) => (
                    <span
                      key={id}
                      className={`text-[10px] font-mono px-1.5 py-0.5 rounded border ${styles.inputBorder} ${styles.cardTextMuted}`}
                    >
                      {id}
                    </span>
                  ))}
                </div>
              )}
            </div>
            {result.excludedAssumptions.length > 0 && (
              <div className="space-y-1">
                <span className={`text-[10px] font-bold ${styles.cardTextMuted}`}>
                  {t('scenario.iv.cf.excludedAssumptions')}
                </span>
                <ul className="space-y-1">
                  {result.excludedAssumptions.map((a) => (
                    <li key={a.hypothesisId} className="text-[11px] flex flex-wrap items-center gap-2">
                      <span className={`font-mono ${styles.cardText}`}>{a.hypothesisId}</span>
                      <span
                        className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
                      >
                        {a.status}
                      </span>
                      {a.invalidReason && <span className={`${styles.cardTextMuted}`}>{a.invalidReason}</span>}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** 指标卡（四指标共用，主题语义色由 tone 映射） */
function MetricBox({ label, value, tone }: { label: string; value: string; tone: 'indigo' | 'amber' | 'red' | 'emerald' }) {
  const { styles } = useTheme();
  const toneClass: Record<typeof tone, string> = {
    indigo: 'text-indigo-400',
    amber: 'text-amber-400',
    red: 'text-red-400',
    emerald: 'text-emerald-400',
  };
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 space-y-1`}>
      <span className={`text-[10px] font-bold block ${styles.cardTextMuted} uppercase tracking-wider`}>{label}</span>
      <span className={`text-sm font-mono font-semibold ${toneClass[tone]}`}>{value}</span>
    </div>
  );
}
