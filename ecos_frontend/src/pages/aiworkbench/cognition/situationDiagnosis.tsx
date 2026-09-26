import React, { useState, useCallback } from 'react';
import { CheckCircle, AlertCircle, Loader, ChevronDown } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import CognitionView from '../CognitionView';

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
  // PMO-60 scenario-driven execution hub (folded in from the former standalone tab)
  const [showExecPanel, setShowExecPanel] = useState(false);

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
      showToastGlobal('success', t('diagnosis.runSuccess', '诊断完成'));
    } catch (e) {
      showToastGlobal('error', `${t('diagnosis.runFailed', '诊断失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setRunning(false);
    }
  };

  const goStep = (i: number) => { if (i < step) setStep(i); };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <button onClick={() => onBackToOverview?.()} className="text-sm text-blue-600 dark:text-blue-400 hover:underline">
          {t('diagnosis.back', '← 返回总览')}
        </button>
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('diagnosis.title', '情境诊断')}</h1>
        <div className="w-20" />
      </div>

      {/* Progress bar */}
      <div className="flex items-center gap-0">
        {STEPS.map((s, i) => (
          <React.Fragment key={s}>
            <div className="flex flex-col items-center gap-1 cursor-pointer" onClick={() => goStep(i)}>
              <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium ${
                i === step ? 'bg-blue-600 text-white' : i < step ? 'bg-blue-500 text-white' : 'bg-slate-200 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
              }`}>{i + 1}</div>
              <span className="text-xs text-slate-500 dark:text-slate-400">{t(`diagnosis.step.${i}`, s)}</span>
            </div>
            {i < STEPS.length - 1 && <div className={`flex-1 h-0.5 mx-1 ${i < step ? 'bg-blue-500' : 'bg-slate-200 dark:bg-slate-700'}`} />}
          </React.Fragment>
        ))}
      </div>

      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-6 max-w-2xl">
        {step === 0 && (
          <div className="space-y-4">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">{t('diagnosis.objectType', '对象类型')}</label>
            <select className="w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
              value={objectType} onChange={e => setObjectType(e.target.value)}>
              <option value="">{t('common.select', '选择…')}</option>
              <option value="PROJECT">{t('diagnosis.type.project', '项目')}</option>
              <option value="CUSTOMER">{t('diagnosis.type.customer', '客户')}</option>
              <option value="PRODUCT">{t('diagnosis.type.product', '产品')}</option>
              <option value="SUPPLIER">{t('diagnosis.type.supplier', '供应商')}</option>
            </select>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">{t('diagnosis.businessObject', '业务对象')}</label>
            <input className="w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
              placeholder={t('diagnosis.objectPlaceholder', '输入业务对象名称…')} value={businessObject} onChange={e => setBusinessObject(e.target.value)} />
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('diagnosis.startDate', '开始时间')}</label>
                <input type="date" className="w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
                  value={startDate} onChange={e => setStartDate(e.target.value)} />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('diagnosis.endDate', '结束时间')}</label>
                <input type="date" className="w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100"
                  value={endDate} onChange={e => setEndDate(e.target.value)} />
              </div>
            </div>
          </div>
        )}

        {step === 1 && (
          <div className="space-y-4">
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">{t('diagnosis.question', '诊断问题')}</label>
            <textarea className="w-full px-3 py-2 text-sm border border-slate-200 dark:border-slate-700 rounded-md bg-white dark:bg-slate-900 text-slate-900 dark:text-slate-100 min-h-[100px]"
              placeholder={t('diagnosis.questionPlaceholder', '描述你要分析的问题…')} value={question} onChange={e => setQuestion(e.target.value)} />
            <div className="flex gap-2">
              {['因果归因', '风险', '情景'].map(tag => (
                <button key={tag} onClick={() => toggleTag(tag)}
                  className={`px-3 py-1 rounded-full text-xs font-medium border ${
                    selectedTags.includes(tag)
                      ? 'bg-blue-600 text-white border-blue-600'
                      : 'bg-slate-50 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700'
                  }`}>
                  {tag}
                </button>
              ))}
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
              <div key={c.label} className="border border-slate-200 dark:border-slate-700 rounded-lg p-3 text-center">
                <p className="text-xs text-slate-500 dark:text-slate-400">{c.label}</p>
                <p className="text-2xl font-bold text-slate-900 dark:text-slate-100 my-1">{c.count}</p>
                <span className="inline-block px-2 py-0.5 rounded text-xs bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400">
                  {t('diagnosis.readonly', '只读')}
                </span>
              </div>
            ))}
          </div>
        )}

        {step === 3 && (
          <div className="space-y-3">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('diagnosis.selectMode', '选择推理方式')}</p>
            {MODE_OPTIONS.map(opt => (
              <label key={opt.id} className={`flex items-start gap-3 p-3 border rounded-lg cursor-pointer transition ${
                reasoningMode === opt.id ? 'border-blue-400 bg-blue-50 dark:bg-blue-950' : 'border-slate-200 dark:border-slate-700'
              }`}>
                <input type="radio" name="mode" value={opt.id} checked={reasoningMode === opt.id}
                  onChange={() => setReasoningMode(opt.id)} className="mt-0.5" />
                <div>
                  <span className="text-sm font-medium text-slate-900 dark:text-slate-100">{t(opt.labelKey, opt.label)}</span>
                  {opt.recommended && <span className="ml-2 text-xs px-1.5 py-0.5 rounded bg-blue-100 dark:bg-blue-950 text-blue-600 dark:text-blue-300">{t('diagnosis.recommended', '推荐作为默认模式')}</span>}
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{t(opt.descKey, opt.desc)}</p>
                </div>
              </label>
            ))}
          </div>
        )}

        {step === 4 && (
          <div className="space-y-4">
            <p className="text-sm font-medium text-slate-700 dark:text-slate-300">{t('diagnosis.preflight', '预检结果')}</p>
            <div className="space-y-2">
              {PREFLIGHT_CHECKS.map(c => (
                <div key={c.key} className="flex items-center gap-2">
                  {preflight?.[c.key]
                    ? <CheckCircle className="w-4 h-4 text-green-500" />
                    : <AlertCircle className="w-4 h-4 text-yellow-500" />}
                  <span className="text-sm text-slate-700 dark:text-slate-300">{t(c.key, c.fallback)}</span>
                  <span className={`ml-auto text-xs px-2 py-0.5 rounded ${
                    preflight?.[c.key] ? 'bg-green-100 dark:bg-green-950 text-green-700 dark:text-green-300' : 'bg-yellow-100 dark:bg-yellow-950 text-yellow-700 dark:text-yellow-300'
                  }`}>
                    {preflight?.[c.key] ? t('diagnosis.pass', '通过') : t('diagnosis.warn', '警告')}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Footer buttons */}
        <div className="flex justify-between mt-6 pt-4 border-t border-slate-200 dark:border-slate-700">
          <button disabled={step === 0}
            className="px-3 py-1.5 rounded-md text-sm font-medium bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 disabled:opacity-40 hover:bg-slate-200 dark:hover:bg-slate-700"
            onClick={() => step > 0 && setStep(step - 1)}>
            {t('common.prev', '上一步')}
          </button>
          {step < STEPS.length - 1 ? (
            <button className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600"
              onClick={handleNext}>
              {t('common.next', '下一步')}
            </button>
          ) : (
            <button onClick={handleRun} disabled={running}
              className="px-4 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
              {running ? <Loader className="w-4 h-4 inline mr-1 animate-spin" /> : null}
              {t('diagnosis.run', '运行认知推理')}
            </button>
          )}
        </div>

        {runResult !== null && (
          <div className="mt-4 p-3 rounded bg-blue-50 dark:bg-blue-950 text-blue-800 dark:text-blue-200 text-sm">
            {t('diagnosis.resultLabel', '诊断结果：')}
            <pre className="mt-1 text-xs max-h-40 overflow-auto">{JSON.stringify(runResult, null, 2)}</pre>
          </div>
        )}
      </div>

      {/* PMO-60 场景认知执行（四件套）— the retained scenario+mind driven hub
          (formerly the standalone AI-Workbench cognition tab). Folded into the
          situation-diagnosis sub-page instead of staying a parallel cognition entry. */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg">
        <button
          type="button"
          onClick={() => setShowExecPanel(v => !v)}
          aria-expanded={showExecPanel}
          className="w-full flex items-center justify-between px-4 py-3 text-left cursor-pointer"
        >
          <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {t('knowledge.cognition.exec.title', '场景认知执行（四件套）')}
          </span>
          <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${showExecPanel ? 'rotate-180' : ''}`} />
        </button>
        {showExecPanel && (
          <div className="border-t border-slate-200 dark:border-slate-700">
            <CognitionView />
          </div>
        )}
      </div>
    </div>
  );
}
