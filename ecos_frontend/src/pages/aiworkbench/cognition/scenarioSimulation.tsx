import React, { useState } from 'react';
import { Copy, Loader, Send } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge from '../../../components/common/Badge';

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
  const { styles } = useTheme();
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
      showToastGlobal('success', t('scenario.recalcOk'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.recalcFailed')}: ${(e as Error)?.message ?? e}`);
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
      showToastGlobal('success', t('scenario.decisionSubmitted'));
    } catch (e) {
      showToastGlobal('error', `${t('scenario.decisionFailed')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCopy = async () => {
    try {
      const text = JSON.stringify({ values, timestamp: new Date().toISOString() }, null, 2);
      await navigator.clipboard.writeText(text);
      showToastGlobal('success', t('scenario.copied'));
    } catch {
      showToastGlobal('error', t('scenario.copyFailed'));
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('scenario.title')}</h1>
        <div className="flex gap-2">
          <Button variant="primary" size="md" onClick={handleSubmitDecision} disabled={submitting} aria-label={t('scenario.submitDecision')}>
            {submitting ? <Loader className="w-3.5 h-3.5 inline mr-1 animate-spin" aria-hidden /> : <Send className="w-3.5 h-3.5 inline mr-1" aria-hidden />}
            {t('scenario.submitDecision')}
          </Button>
          <Button variant="secondary" size="md" onClick={handleCopy} aria-label={t('scenario.copy')}>
            <Copy className="w-3.5 h-3.5" aria-hidden />
            {t('scenario.copy')}
          </Button>
        </div>
      </div>

      {/* Scenario cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4" role="group" aria-label={t('scenario.title')}>
        {SCENARIOS.map(s => (
          <div key={s.key} className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
            <p className={`text-sm font-medium ${styles.cardText}`}>{t(s.nameKey, s.name)}</p>
            <div className="flex items-baseline gap-2 mt-2">
              <span className={`text-2xl font-bold ${styles.cardText}`}>{s.metric}</span>
              {s.change !== 0 && (
                // 涨跌双通道：箭头字形（↑/↓）+ 语义徽标色，不依赖颜色单一通道（B.7）
                <Badge variant={s.change > 0 ? 'success' : 'danger'}>
                  {s.change > 0 ? '↑' : '↓'} {Math.abs(s.change)}
                </Badge>
              )}
            </div>
            <p className={`text-xs ${styles.muted} mt-1`}>{t(s.metricKey, '毛利')}</p>
          </div>
        ))}
      </div>

      {/* Variable controls + sensitivity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText}`}>{t('scenario.varControls')}</h2>
          {VARS.map(v => (
            <div key={v.key} className="space-y-1">
              <div className="flex items-center justify-between">
                <label className={`text-sm ${styles.cardText}`}>{t(v.labelKey, v.label)}</label>
                <span className={`text-sm font-mono ${styles.muted}`}>{values[v.key]}{v.unit}</span>
              </div>
              <input type="range" min={v.min} max={v.max} step={v.step} value={values[v.key]}
                aria-label={t(v.labelKey, v.label)}
                onChange={e => handleChange(v.key, Number(e.target.value))}
                className={`w-full h-1.5 rounded ${styles.inputBg} appearance-none cursor-pointer`}
                style={{ accentColor: 'currentColor' }} />
              <div className={`flex justify-between text-xs ${styles.muted}`}>
                <span>{v.min}{v.unit}</span>
                <span>{v.max}{v.unit}</span>
              </div>
            </div>
          ))}
          <Button variant="primary" size="md" onClick={handleRecalc} disabled={calculating} className="w-full" aria-label={t('scenario.recalculate')}>
            {calculating ? <Loader className="w-3.5 h-3.5 inline mr-1 animate-spin" aria-hidden /> : null}
            {t('scenario.recalculate')}
          </Button>
        </div>

        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
          <h2 className={`text-sm font-medium ${styles.cardText} mb-3`}>{t('scenario.sensitivity')}</h2>
          <table className="w-full text-sm" aria-label={t('scenario.sensitivity')}>
            <thead>
              <tr className={`border-b ${styles.cardBorder} text-left text-xs ${styles.muted}`}>
                <th scope="col" className="py-1.5 font-medium">{t('scenario.col.var')}</th>
                <th scope="col" className="py-1.5 font-medium">{t('scenario.col.impact')}</th>
                <th scope="col" className="py-1.5 font-medium">{t('scenario.col.direction')}</th>
              </tr>
            </thead>
            <tbody>
              {SENSITIVITY_ROWS.map(r => (
                <tr key={r.varKey} className={`border-b ${styles.divider} last:border-0`}>
                  <td className={`py-2 ${styles.cardText}`}>{t(r.varKey, r.varLabel)}</td>
                  <td className={`py-2 ${styles.muted}`}>{r.impact.toFixed(2)}</td>
                  <td className="py-2">
                    {/* 方向双通道：↑/↓ 字形本体即非颜色通道，语义色仅做增强（B.7） */}
                    <span className={`text-xs font-medium ${r.direction === '↑' ? styles.dangerText : styles.successText}`}>{r.direction}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className={`text-xs ${styles.muted} mt-3`}>
            {t('scenario.explanation')}
          </p>
        </div>
      </div>
    </div>
  );
}
