import React, { useState } from 'react';
import { Copy, Loader, Send } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';

interface Variable { key: string; label: string; labelKey: string; min: number; max: number; unit: string; step: number; }

const VARS: Variable[] = [
  { key: 'procurementCost', label: '采购成本', labelKey: 'scenario.var.procurement', min: -20, max: 30, unit: '%', step: 1 },
  { key: 'duration', label: '工期', labelKey: 'scenario.var.duration', min: -30, max: 60, unit: '天', step: 1 },
  { key: 'laborCost', label: '人力成本', labelKey: 'scenario.var.labor', min: -20, max: 30, unit: '%', step: 1 },
];

const SCENARIOS = [
  { key: 'baseline', nameKey: 'scenario.name.baseline', name: '基准', metricKey: 'scenario.metric.baseline', metric: '18.2%', change: 0 },
  { key: 'A', nameKey: 'scenario.name.A', name: '情景 A', metricKey: 'scenario.metric.A', metric: '15.8%', change: -2.4 },
  { key: 'B', nameKey: 'scenario.name.B', name: '情景 B', metricKey: 'scenario.metric.B', metric: '20.1%', change: +1.9 },
];

const SENSITIVITY_ROWS = [
  { varKey: 'scenario.var.procurement', varLabel: '采购成本', impact: 0.72, direction: '↑' },
  { varKey: 'scenario.var.duration', varLabel: '工期', impact: 0.41, direction: '↑' },
  { varKey: 'scenario.var.labor', varLabel: '人力成本', impact: 0.33, direction: '↓' },
];

export default function scenarioSimulation() {
  const { t } = useLanguage();
  const [values, setValues] = useState<Record<string, number>>({ procurementCost: 0, duration: 0, laborCost: 0 });
  const [calculating, setCalculating] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (key: string, val: number) => setValues(prev => ({ ...prev, [key]: val }));

  const handleRecalc = async () => {
    if (calculating) return;
    setCalculating(true);
    try {
      const variables = Object.fromEntries(VARS.map(v => [v.key, values[v.key]]));
      await cognitiveEngineApi.runScenario({ scenarioType: 'A', variables });
      showToastGlobal('success', t('scenario.recalcOk', '重新计算完成'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.recalcFailed', '重新计算失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setCalculating(false);
    }
  };

  const handleSubmitDecision = async () => {
    if (submitting) return;
    setSubmitting(true);
    try {
      const variables = Object.fromEntries(VARS.map(v => [v.key, values[v.key]]));
      await cognitiveEngineApi.recordDecision({
        decisionType: 'COGNITIVE_SCENARIO',
        payload: { variables, scenario: 'A' },
      });
      showToastGlobal('success', t('scenario.decisionSubmitted', 'W 决策输入已提交'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.decisionFailed', '提交失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopy = async () => {
    try {
      const text = JSON.stringify({ values, timestamp: new Date().toISOString() }, null, 2);
      await navigator.clipboard.writeText(text);
      showToastGlobal('success', t('scenario.copied', '情景已复制到剪贴板'));
    } catch {
      showToastGlobal('error', t('scenario.copyFailed', '复制失败'));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('scenario.title', '情景推演')}</h1>
        <div className="flex gap-2">
          <button onClick={handleSubmitDecision} disabled={submitting}
            className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
            {submitting ? <Loader className="w-3.5 h-3.5 inline mr-1 animate-spin" /> : <Send className="w-3.5 h-3.5 inline mr-1" />}
            {t('scenario.submitDecision', '提交 W 决策输入')}
          </button>
          <button onClick={handleCopy}
            className="px-3 py-1.5 rounded-md text-sm font-medium bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center gap-1">
            <Copy className="w-3.5 h-3.5" />
            {t('scenario.copy', '复制情景')}
          </button>
        </div>
      </div>

      {/* Scenario cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {SCENARIOS.map(s => (
          <div key={s.key} className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">{t(s.nameKey, s.name)}</p>
            <div className="flex items-baseline gap-2 mt-2">
              <span className="text-2xl font-bold text-slate-900 dark:text-slate-100">{s.metric}</span>
              {s.change !== 0 && (
                <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${
                  s.change > 0 ? 'bg-green-100 dark:bg-green-950 text-green-700 dark:text-green-300' : 'bg-red-100 dark:bg-red-950 text-red-700 dark:text-red-300'
                }`}>
                  {s.change > 0 ? '↑' : '↓'} {Math.abs(s.change)}
                </span>
              )}
            </div>
            <p className="text-xs text-slate-400 mt-1">{t(s.metricKey, '毛利')}</p>
          </div>
        ))}
      </div>

      {/* Variable controls + sensitivity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 space-y-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100">{t('scenario.varControls', '变量控制')}</h2>
          {VARS.map(v => (
            <div key={v.key} className="space-y-1">
              <div className="flex items-center justify-between">
                <label className="text-sm text-slate-700 dark:text-slate-300">{t(v.labelKey, v.label)}</label>
                <span className="text-sm font-mono text-slate-500 dark:text-slate-400">{values[v.key]}{v.unit}</span>
              </div>
              <input type="range" min={v.min} max={v.max} step={v.step} value={values[v.key]}
                onChange={e => handleChange(v.key, Number(e.target.value))}
                className="w-full h-1.5 rounded bg-slate-200 dark:bg-slate-700 appearance-none cursor-pointer accent-blue-600" />
              <div className="flex justify-between text-xs text-slate-400">
                <span>{v.min}{v.unit}</span>
                <span>{v.max}{v.unit}</span>
              </div>
            </div>
          ))}
          <button onClick={handleRecalc} disabled={calculating}
            className="w-full px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
            {calculating ? <Loader className="w-3.5 h-3.5 inline mr-1 animate-spin" /> : null}
            {t('scenario.recalculate', '重新计算')}
          </button>
        </div>

        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-3">{t('scenario.sensitivity', '敏感性与解释')}</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700 text-left text-xs text-slate-500 dark:text-slate-400">
                <th className="py-1.5 font-medium">{t('scenario.col.var', '变量')}</th>
                <th className="py-1.5 font-medium">{t('scenario.col.impact', '影响')}</th>
                <th className="py-1.5 font-medium">{t('scenario.col.direction', '方向')}</th>
              </tr>
            </thead>
            <tbody>
              {SENSITIVITY_ROWS.map(r => (
                <tr key={r.varKey} className="border-b border-slate-100 dark:border-slate-800 last:border-0">
                  <td className="py-2 text-slate-700 dark:text-slate-300">{t(r.varKey, r.varLabel)}</td>
                  <td className="py-2 text-slate-500 dark:text-slate-400">{r.impact.toFixed(2)}</td>
                  <td className="py-2">
                    <span className={`text-xs font-medium ${r.direction === '↑' ? 'text-red-500' : 'text-green-500'}`}>{r.direction}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-3">
            {t('scenario.explanation', '采购成本是研发投入波动的主要驱动因素，其次是工期延长导致的运营成本增加。')}
          </p>
        </div>
      </div>
    </div>
  );
}
