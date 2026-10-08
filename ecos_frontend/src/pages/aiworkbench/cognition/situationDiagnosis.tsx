import React, { useState, useCallback } from 'react';
import { CheckCircle, AlertCircle, Loader } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import Button from '../../../components/common/Button';
import Badge from '../../../components/common/Badge';

const STEPS = ['对象', '问题', '上下文', '方式', '检查'];

const PREFLIGHT_CHECKS = [
  { key: 'diagnosis.pf.objects', fallback: '对象已定义' },
  { key: 'diagnosis.pf.question', fallback: '问题描述完整' },
  { key: 'diagnosis.pf.data', fallback: '数据可用' },
  { key: 'diagnosis.pf.ontology', fallback: '本体已加载' },
  { key: 'diagnosis.pf.knowledge', fallback: '知识库可达' },
];

const MODE_OPTIONS = [
  { id: 'HYBRID', labelKey: 'diagnosis.mode.hybrid', label: '混合推理', descKey: 'diagnosis.mode.hybridDesc', desc: '推荐的默认模式', recommended: true },
  { id: 'CAUSAL', labelKey: 'diagnosis.mode.causal', label: '因果推理', descKey: 'diagnosis.mode.causalDesc', desc: '针对因果归因' },
  { id: 'SCENARIO', labelKey: 'diagnosis.mode.scenario', label: '情景推演', descKey: 'diagnosis.mode.scenarioDesc', desc: '多情景对比' },
];

interface SituationDiagnosisProps {
  /** Switch back to the workbench overview sub-page (in-page cross-link). */
  onBackToOverview?: () => void;
}

export default function situationDiagnosis({ onBackToOverview }: SituationDiagnosisProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [step, setStep] = useState(0);
  const [objectType, setObjectType] = useState('');
  const [businessObject, setBusinessObject] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [question, setQuestion] = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [reasoningMode, setReasoningMode] = useState('HYBRID');
  const [preflight, setPreflight] = useState<Record<string, boolean> | null>(null);
  const [running, setRunning] = useState(false);
  const [runResult, setRunResult] = useState<unknown>(null);

  const toggleTag = (tag: string) => {
    setSelectedTags(prev => prev.includes(tag) ? prev.filter(x => x !== tag) : [...prev, tag]);
  };

  const runPreflight = useCallback(() => {
    const checks: Record<string, boolean> = {};
    PREFLIGHT_CHECKS.forEach(c => { checks[c.key] = true; });
    setPreflight(checks);
  }, []);

  const handleNext = () => {
    if (step < 3) { setStep(step + 1); }
    else if (step === 3) { runPreflight(); setStep(4); }
  };

  const handleRun = async () => {
    if (running) return;
    setRunning(true);
    try {
      const result = await cognitiveEngineApi.runDiagnosis({
        objectId: businessObject || objectType,
        objectType: objectType || undefined,
        question,
        reasoningMode,
      });
      setRunResult(result);
      showToastGlobal('success', t('diagnosis.runSuccess'));
    } catch (e) {
      showToastGlobal('error', `${t('diagnosis.runFailed')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setRunning(false);
    }
  };

  const goStep = (i: number) => { if (i < step) setStep(i); };

  const field = `w-full px-3 py-2 text-sm border ${styles.cardBorder} rounded-md ${styles.inputBg} ${styles.inputText}`;
  const fieldLabel = `block text-sm font-medium ${styles.cardText}`;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <button type="button" onClick={() => onBackToOverview?.()} aria-label={t('diagnosis.back')}
          className={`text-sm ${styles.accentText} hover:underline`}>
          {t('diagnosis.back')}
        </button>
        <h1 className={`text-lg font-semibold ${styles.cardText}`}>{t('diagnosis.title')}</h1>
        <div className="w-20" />
      </div>

      {/* Progress bar — 步骤圆点走公共 Button（反色豁免收敛于组件内，§七.7） */}
      <div className="flex items-center gap-0" role="group" aria-label={t('diagnosis.title')}>
        {STEPS.map((s, i) => (
          <React.Fragment key={s}>
            <div className="flex flex-col items-center gap-1 cursor-pointer" onClick={() => goStep(i)}>
              <Button
                variant={i === step ? 'primary' : 'secondary'}
                size="sm"
                aria-label={t(`diagnosis.step.${i}`, s)}
                aria-current={i === step ? 'step' : undefined}
                className={`w-6 h-6 rounded-full p-0 gap-0 text-xs font-medium ${i > step ? styles.muted : ''}`}>
                {i + 1}
              </Button>
              <span className={`text-xs ${styles.muted}`}>{t(`diagnosis.step.${i}`, s)}</span>
            </div>
            {i < STEPS.length - 1 && <div className={`flex-1 h-0.5 mx-1 ${i < step ? styles.accentBg : styles.inputBg}`} />}
          </React.Fragment>
        ))}
      </div>

      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-6 max-w-2xl`}>
        {step === 0 && (
          <div className="space-y-4">
            <label className={fieldLabel}>{t('diagnosis.objectType')}</label>
            <select aria-label={t('diagnosis.objectType')} className={field}
              value={objectType} onChange={e => setObjectType(e.target.value)}>
              <option value="">{t('common.select')}</option>
              <option value="PROJECT">{t('diagnosis.type.project')}</option>
              <option value="CUSTOMER">{t('diagnosis.type.customer')}</option>
              <option value="PRODUCT">{t('diagnosis.type.product')}</option>
              <option value="SUPPLIER">{t('diagnosis.type.supplier')}</option>
            </select>
            <label className={fieldLabel}>{t('diagnosis.businessObject')}</label>
            <input aria-label={t('diagnosis.businessObject')} className={field}
              placeholder={t('diagnosis.objectPlaceholder')} value={businessObject} onChange={e => setBusinessObject(e.target.value)} />
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className={`${fieldLabel} mb-1`}>{t('diagnosis.startDate')}</label>
                <input type="date" aria-label={t('diagnosis.startDate')} className={field}
                  value={startDate} onChange={e => setStartDate(e.target.value)} />
              </div>
              <div>
                <label className={`${fieldLabel} mb-1`}>{t('diagnosis.endDate')}</label>
                <input type="date" aria-label={t('diagnosis.endDate')} className={field}
                  value={endDate} onChange={e => setEndDate(e.target.value)} />
              </div>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <label className={fieldLabel}>{t('diagnosis.question')}</label>
            <textarea aria-label={t('diagnosis.question')} className={`${field} min-h-[100px]`}
              placeholder={t('diagnosis.questionPlaceholder')} value={question} onChange={e => setQuestion(e.target.value)} />
            <div className="flex gap-2">
              {['因果归因', '风险', '情景'].map(tag => {
                const on = selectedTags.includes(tag);
                return (
                  <button key={tag} type="button" onClick={() => toggleTag(tag)} aria-pressed={on}
                    className={`px-3 py-1 rounded-full text-xs font-medium border cursor-pointer transition ${
                      on ? `${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText}`
                         : `${styles.cardBorder} ${styles.inputBg} ${styles.cardTextMuted}`
                    }`}>
                    {tag}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="grid grid-cols-3 gap-4">
            {[
              { label: 'D — Data', count: 12, readonly: true },
              { label: 'I — Information', count: 8, readonly: true },
              { label: 'K — Knowledge', count: 5, readonly: true },
            ].map(c => (
              <div key={c.label} className={`border ${styles.cardBorder} rounded-lg p-3 text-center`}>
                <p className={`text-xs ${styles.muted}`}>{c.label}</p>
                <p className={`text-2xl font-bold ${styles.cardText} my-1`}>{c.count}</p>
                <Badge variant="neutral">{t('diagnosis.readonly')}</Badge>
              </div>
            ))}
          </div>
        )}

        {step === 3 && (
          <div className="space-y-3">
            <p className={`text-sm font-medium ${styles.cardText}`}>{t('diagnosis.selectMode')}</p>
            {MODE_OPTIONS.map(opt => (
              <label key={opt.id} className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition ${
                reasoningMode === opt.id ? `${styles.accentBorder} ${styles.infoBg}` : `${styles.cardBorder}`
              }`}>
                <input type="radio" name="mode" value={opt.id} checked={reasoningMode === opt.id}
                  onChange={() => setReasoningMode(opt.id)} className="mt-0.5" />
                <div>
                  <span className={`text-sm font-medium ${styles.cardText}`}>{t(opt.labelKey, opt.label)}</span>
                  {opt.recommended && <span className="ml-2"><Badge variant="info">{t('diagnosis.recommended')}</Badge></span>}
                  <p className={`text-xs ${styles.muted} mt-0.5`}>{t(opt.descKey, opt.desc)}</p>
                </div>
              </label>
            ))}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4">
            <p className={`text-sm font-medium ${styles.cardText}`}>{t('diagnosis.preflight')}</p>
            <div className="space-y-2">
              {PREFLIGHT_CHECKS.map(c => (
                <div key={c.key} className="flex items-center gap-2">
                  {preflight?.[c.key]
                    ? <CheckCircle className={`w-4 h-4 ${styles.successText}`} aria-hidden />
                    : <AlertCircle className={`w-4 h-4 ${styles.warningText}`} aria-hidden />}
                  <span className={`text-sm ${styles.cardText}`}>{t(c.key, c.fallback)}</span>
                  <span className="ml-auto">
                    <Badge variant={preflight?.[c.key] ? 'success' : 'warning'}>
                      {preflight?.[c.key] ? t('diagnosis.pass') : t('diagnosis.warn')}
                    </Badge>
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Footer buttons */}
        <div className={`flex justify-between mt-6 pt-4 border-t ${styles.cardBorder}`}>
          <Button variant="secondary" disabled={step === 0} onClick={() => step > 0 && setStep(step - 1)}
            aria-label={t('common.prev')}>
            {t('common.prev')}
          </Button>
          {step < STEPS.length - 1 ? (
            <Button variant="primary" onClick={handleNext} aria-label={t('common.next')}>
              {t('common.next')}
            </Button>
          ) : (
            <Button variant="primary" onClick={handleRun} disabled={running} aria-label={t('diagnosis.run')}>
              {running ? <Loader className="w-4 h-4 inline mr-1 animate-spin" aria-hidden /> : null}
              {t('diagnosis.run')}
            </Button>
          )}
        </div>

        {runResult !== null && (
          <div className={`mt-4 p-3 rounded ${styles.infoBg} ${styles.infoText} text-sm`} role="status">
            {t('diagnosis.resultLabel')}
            <pre className="mt-1 text-xs max-h-40 overflow-auto">{JSON.stringify(runResult, null, 2)}</pre>
          </div>
        )}
      </div>
    </div>
  );
}
