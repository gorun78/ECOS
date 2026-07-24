/**
 * ValidationTab — 操作验证规则 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Shield, ShieldCheck, Trash2 } from 'lucide-react';
import type { ActionType, ActionValidationRule } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface Props {
  actionType: ActionType;
  newValName: string; setNewValName: (v: string) => void;
  newValExpression: string; setNewValExpression: (v: string) => void;
  newValError: string; setNewValError: (v: string) => void;
  handleAddValidation: () => void;
  handleRemoveValidation: (valId: string) => void;
}

export default function ValidationTab({
  actionType, newValName, setNewValName, newValExpression, setNewValExpression,
  newValError, setNewValError, handleAddValidation, handleRemoveValidation,
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div className="space-y-6">
      <p className={`text-xs ${styles.muted}`}>{t('ow.tab.validationDesc')}</p>
      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
        <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.newValidationRule')}</h4>
        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1"><label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.validationName')}</label><input type="text" placeholder={t('ow.placeholder.validationName')} value={newValName} onChange={e => setNewValName(e.target.value)} className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`} /></div>
          <div className="space-y-1 col-span-2"><label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.validationExpression')}</label><input type="text" placeholder={`如：parameter.new_status_param IN ["ON_TIME", "DELAYED"]`} value={newValExpression} onChange={e => setNewValExpression(e.target.value)} className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} font-mono focus:outline-hidden`} /></div>
        </div>
        <div className="space-y-1"><label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.validationErrorMessage')}</label><input type="text" placeholder={t('ow.placeholder.validationError')} value={newValError} onChange={e => setNewValError(e.target.value)} className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`} /></div>
        <button onClick={handleAddValidation} className={`${styles.accentBg} hover:bg-blue-700 text-white text-xs px-4 py-1.5 rounded font-medium transition-colors flex items-center gap-1.5`}><Shield size={13} />{t('ow.btn.addValidationRule')}</button>
      </div>
      <div className="space-y-4">
        <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.activeValidations')} ({actionType.validationRules.length})</h4>
        {actionType.validationRules.length === 0 ? (
          <div className={`text-center py-8 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-xs`}>{t('ow.empty.noValidationRules')}</div>
        ) : (
          <div className="space-y-3">
            {actionType.validationRules.map(val => (
              <div key={val.id} className={`p-4 border ${styles.cardBorder} rounded-lg ${styles.cardBg} shadow-3xs flex items-start justify-between`}>
                <div className="space-y-2">
                  <div className="flex items-center gap-2"><span className="p-1 rounded-full bg-emerald-50 text-emerald-600"><ShieldCheck size={14} /></span><span className={`text-xs font-semibold ${styles.cardText}`}>{val.displayName}</span></div>
                  <div className={`font-mono text-[10px] ${styles.appBg} ${styles.cardTextMuted} px-2 py-1 rounded border ${styles.appBg}`}>{val.expression}</div>
                  <div className="text-[10px] text-red-500 font-medium"><strong>{t('ow.label.warningText')}</strong> {val.errorMessage}</div>
                </div>
                <button onClick={() => handleRemoveValidation(val.id)} className={`${styles.muted} hover:text-red-500 p-1`} title="删除规则"><Trash2 size={14} /></button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
