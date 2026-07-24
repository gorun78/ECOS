/**
 * LayoutTab — 操作表单布局 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Layers, Trash } from 'lucide-react';
import type { ActionType } from '../../../types/ontology';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

interface Props {
  actionType: ActionType;
  newSectionTitle: string; setNewSectionTitle: (v: string) => void;
  onUpdate: (updated: ActionType) => void;
}

export default function LayoutTab({ actionType, newSectionTitle, setNewSectionTitle, onUpdate }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const layout = actionType.formLayout || {
    sections: [{ title: t('ow.action.defaultSectionTitle'), parameterIds: actionType.parameters.map(p => p.id) }],
    buttonText: t('ow.action.defaultButtonText').replace('{name}', actionType.displayName)
  };

  const updateLayout = (updatedLayout: typeof layout) => { onUpdate({ ...actionType, formLayout: updatedLayout }); };

  const handleAddSection = () => {
    if (!newSectionTitle.trim()) return;
    updateLayout({ ...layout, sections: [...layout.sections, { title: newSectionTitle.trim(), parameterIds: [] }] });
    setNewSectionTitle('');
  };

  const handleRemoveSection = (sectionIndex: number) => {
    const removedSec = layout.sections[sectionIndex];
    const firstSec = layout.sections[0];
    const updatedSections = layout.sections.filter((_, idx) => idx !== sectionIndex);
    if (firstSec && removedSec) updatedSections[0] = { ...updatedSections[0], parameterIds: Array.from(new Set([...updatedSections[0].parameterIds, ...removedSec.parameterIds])) };
    updateLayout({ ...layout, sections: updatedSections });
  };

  const handleAddParamToSection = (sectionIndex: number, paramId: string) => {
    const cleanedSections = layout.sections.map(sec => ({ ...sec, parameterIds: sec.parameterIds.filter(id => id !== paramId) }));
    cleanedSections[sectionIndex].parameterIds.push(paramId);
    updateLayout({ ...layout, sections: cleanedSections });
  };

  return (
    <div className="space-y-6">
      <p className={`text-xs ${styles.muted}`}>{t('ow.tab.layoutDesc')}</p>
      <div className="grid grid-cols-3 gap-6">
        <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4 col-span-1 h-fit`}>
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.formBehaviorText')}</h4>
          <div className="space-y-1"><label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.label.submitButtonText')}</label><input type="text" value={layout.buttonText || ''} onChange={e => updateLayout({ ...layout, buttonText: e.target.value })} className={`w-full px-3 py-1.5 text-xs border ${styles.cardBorder} rounded ${styles.cardBg} focus:outline-hidden`} placeholder={t('ow.placeholder.submitButtonText')} /></div>
          <hr className={`${styles.cardBorder}`} />
          <div className="space-y-2">
            <h5 className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('ow.section.addFormSection')}</h5>
            <div className="flex gap-2"><input type="text" placeholder={t('ow.placeholder.sectionName')} value={newSectionTitle} onChange={e => setNewSectionTitle(e.target.value)} className={`flex-1 px-2 py-1 text-xs border ${styles.cardBorder} rounded focus:outline-hidden`} /><button onClick={handleAddSection} className={`bg-slate-900 text-white hover:bg-slate-800 text-[11px] px-2.5 py-1 rounded transition-colors`}>{t('ow.btn.addSection')}</button></div>
          </div>
        </div>
        <div className="col-span-2 space-y-4">
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.sectionFieldAssignment')}</h4>
          <div className="space-y-4">
            {layout.sections.map((section, secIdx) => {
              const availableParams = actionType.parameters.filter(p => !section.parameterIds.includes(p.id));
              return (
                <div key={secIdx} className={`border ${styles.cardBorder} rounded-xl p-4 ${styles.cardBg} space-y-3 shadow-2xs relative`}>
                  {secIdx > 0 && (<button onClick={() => handleRemoveSection(secIdx)} className={`absolute top-4 right-4 ${styles.muted} hover:text-red-500`} title="移除此区块"><Trash size={13} /></button>)}
                  <div className={`flex items-center gap-2 border-b ${styles.appBg} pb-2`}><Layers size={13} className="text-blue-500" /><span className={`text-xs font-semibold ${styles.cardText}`}>{section.title}</span><span className={`text-[10px] ${styles.muted}`}>({section.parameterIds.length} {t('ow.label.fields')})</span></div>
                  {section.parameterIds.length === 0 ? (
                    <div className={`text-center py-4 ${styles.muted} italic text-[11px]`}>{t('ow.empty.emptySection')}</div>
                  ) : (
                    <div className="space-y-1.5">
                      {section.parameterIds.map(paramId => {
                        const pDef = actionType.parameters.find(p => p.id === paramId);
                        if (!pDef) return null;
                        return (<div key={paramId} className={`flex justify-between items-center ${styles.appBg} px-3 py-1.5 rounded border ${styles.appBg} text-xs`}><div className="flex items-center gap-2"><span className={`font-mono ${styles.muted} text-[10px]`}>[{pDef.dataType}]</span><span className={`font-semibold ${styles.cardText}`}>{pDef.displayName}</span><span className={`${styles.muted} text-[10px] font-mono`}>{pDef.id}</span></div><span className={`text-[10px] ${styles.badgeBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded-full font-mono`}>{pDef.isRequired ? t('ow.label.required') : t('ow.label.optional')}</span></div>);
                      })}
                    </div>
                  )}
                  {availableParams.length > 0 && (
                    <div className={`flex items-center justify-end gap-2 text-[11px] pt-1 border-t ${styles.appBg} mt-2`}><span className={styles.muted}>{t('ow.label.transferFieldToSection')}</span><select onChange={e => { if (e.target.value) { handleAddParamToSection(secIdx, e.target.value); e.target.value = ''; } }} className={`px-2 py-0.5 border ${styles.cardBorder} rounded ${styles.cardBg} text-[10px] focus:outline-hidden`}><option value="">{t('ow.placeholder.selectTransferableParam')}</option>{availableParams.map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
