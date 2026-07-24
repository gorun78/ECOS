/**
 * CodeTab — TypeScript 代码编辑 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Calculator, Info, Shield, Sparkles, TrendingUp } from 'lucide-react';
import type { FunctionType } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface CodeTabProps {
  func: FunctionType;
  handleFieldChange: (key: keyof FunctionType, value: any) => void;
  loadTemplate: (type: 'validation' | 'default' | 'computed' | 'aggregation') => void;
}

export default function CodeTab({ func, handleFieldChange, loadTemplate }: CodeTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="flex-1 flex overflow-hidden">
      <div className={`w-56 border-r ${styles.cardBorder} ${styles.appBg} p-4 flex flex-col gap-4 overflow-y-auto select-none`}>
        <div>
          <h4 className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t('ow.section.funcTemplates')}</h4>
          <p className={`text-[10px] ${styles.muted} mt-0.5`}>{t('ow.section.funcTemplatesDesc')}</p>
        </div>
        <div className="space-y-2">
          <button onClick={() => loadTemplate('validation')}
            className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardTextMuted} transition-all flex items-start gap-2`}>
            <Shield size={14} className="text-emerald-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">{t('ow.func.templateValidation')}</div><div className={`text-[10px] font-normal ${styles.muted} mt-0.5`}>{t('ow.func.templateValidationDesc')}</div></div>
          </button>
          <button onClick={() => loadTemplate('default')}
            className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardTextMuted} transition-all flex items-start gap-2`}>
            <Sparkles size={14} className="text-amber-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">{t('ow.func.templateDefault')}</div><div className={`text-[10px] font-normal ${styles.muted} mt-0.5`}>{t('ow.func.templateDefaultDesc')}</div></div>
          </button>
          <button onClick={() => loadTemplate('computed')}
            className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardTextMuted} transition-all flex items-start gap-2`}>
            <Calculator size={14} className="text-blue-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">{t('ow.func.templateComputed')}</div><div className={`text-[10px] font-normal ${styles.muted} mt-0.5`}>{t('ow.func.templateComputedDesc')}</div></div>
          </button>
          <button onClick={() => loadTemplate('aggregation')}
            className={`w-full text-left p-2.5 ${styles.cardBg} border ${styles.cardBorder} hover:border-blue-500 rounded-lg text-xs font-medium ${styles.cardTextMuted} transition-all flex items-start gap-2`}>
            <TrendingUp size={14} className="text-indigo-500 mt-0.5 shrink-0" />
            <div><div className="text-[11px] font-semibold">{t('ow.func.templateAggregation')}</div><div className={`text-[10px] font-normal ${styles.muted} mt-0.5`}>{t('ow.func.templateAggregationDesc')}</div></div>
          </button>
        </div>
        <div className={`mt-auto ${styles.sidebarActiveBg} border border-blue-100 rounded-lg p-3 text-[11px] ${styles.accentText} leading-relaxed`}>
          <div className="font-semibold flex items-center gap-1 mb-1"><Info size={12} /><span>{t('ow.label.tsCodeRequirements')}</span></div>
          {t('ow.label.tsCodeRequirementsDesc')}
        </div>
      </div>

      <div className="flex-1 flex flex-col bg-slate-900 overflow-hidden relative">
        <div className="px-4 py-2 border-b border-slate-800 flex justify-between items-center text-[10px] text-slate-400 select-none font-mono">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse"></span>
            <span>TypeScript 1.84 - ECOS API Sync: ACTIVE</span>
          </div>
          <div className="flex items-center gap-3"><span>UTF-8</span><span>Tab Size: 4</span></div>
        </div>
        <div className="flex-1 flex font-mono text-xs overflow-hidden leading-relaxed">
          <div className="w-12 bg-slate-950 text-slate-600 text-right pr-3 select-none pt-4 flex flex-col">
            {Array.from({ length: 45 }).map((_, i) => (<div key={i}>{i + 1}</div>))}
          </div>
          <textarea value={func.code} onChange={e => handleFieldChange('code', e.target.value)}
            className="flex-1 bg-slate-900 text-slate-150 p-4 border-0 focus:outline-hidden font-mono text-xs resize-none h-full overflow-y-auto leading-relaxed outline-hidden"
            spellCheck="false" />
        </div>
      </div>
    </div>
  );
}
