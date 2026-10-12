/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ActionType } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

interface LayoutTabProps {
  actionType: ActionType;
  onUpdate: (updated: ActionType) => void;
  newSectionTitle: string;
  setNewSectionTitle: (value: string) => void;
}

export default function LayoutTab({
  actionType,
  onUpdate,
  newSectionTitle,
  setNewSectionTitle
}: LayoutTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // Initialize default form layout if not defined
  const layout = actionType.formLayout || {
    sections: [
      {
        title: t('ow.act.layout.defaultSectionTitle'),
        parameterIds: actionType.parameters.map(p => p.id)
      }
    ],
    buttonText: t('ow.act.layout.defaultButtonText', { name: actionType.displayName })
  };

  const updateLayout = (updatedLayout: typeof layout) => {
    onUpdate({
      ...actionType,
      formLayout: updatedLayout
    });
  };

  const handleAddSection = () => {
    if (!newSectionTitle.trim()) return;
    updateLayout({
      ...layout,
      sections: [
        ...layout.sections,
        {
          title: newSectionTitle.trim(),
          parameterIds: []
        }
      ]
    });
    setNewSectionTitle('');
  };

  const handleRemoveSection = (sectionIndex: number) => {
    const removedSec = layout.sections[sectionIndex];
    // Move its parameters back to the first section to avoid losing them
    const firstSec = layout.sections[0];
    const updatedSections = layout.sections.filter((_, idx) => idx !== sectionIndex);
    if (firstSec && removedSec) {
      updatedSections[0] = {
        ...updatedSections[0],
        parameterIds: Array.from(new Set([...updatedSections[0].parameterIds, ...removedSec.parameterIds]))
      };
    }
    updateLayout({
      ...layout,
      sections: updatedSections
    });
  };

  const handleAddParamToSection = (sectionIndex: number, paramId: string) => {
    // Remove from other sections first
    const cleanedSections = layout.sections.map(sec => ({
      ...sec,
      parameterIds: sec.parameterIds.filter(id => id !== paramId)
    }));

    // Add to this section
    cleanedSections[sectionIndex].parameterIds.push(paramId);

    updateLayout({
      ...layout,
      sections: cleanedSections
    });
  };

  return (
    <div className="space-y-6">
      <p className={`text-xs ${styles.cardTextMuted}`}>
        {t('ow.act.layout.intro')}
      </p>

      <div className="grid grid-cols-3 gap-6">
        {/* Visual Customization */}
        <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4 col-span-1 h-fit`}>
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.act.layout.behaviorTitle')}</h4>

          <div className="space-y-1">
            <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>{t('ow.act.layout.submitText')}</label>
            <input
              type="text"
              value={layout.buttonText || ''}
              onChange={e => updateLayout({ ...layout, buttonText: e.target.value })}
              className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
              placeholder={t('ow.act.layout.submitTextPlaceholder')}
            />
          </div>

          <hr className={styles.divider} />

          <div className="space-y-2">
            <h5 className={`text-[11px] font-semibold ${styles.cardText}`}>{t('ow.act.layout.addSection')}</h5>
            <div className="flex gap-2">
              <input
                type="text"
                placeholder={t('ow.act.layout.sectionNamePlaceholder')}
                value={newSectionTitle}
                onChange={e => setNewSectionTitle(e.target.value)}
                className={`flex-1 px-2 py-1 text-xs border ${styles.inputBorder} rounded focus:outline-hidden`}
              />
              <button
                type="button"
                onClick={handleAddSection}
                className="bg-[var(--card,#0F172A)] text-white hover:bg-[var(--muted,#1E293B)] text-[11px] px-2.5 py-1 rounded transition-colors"
              >
                {t('ow.act.layout.addSectionBtn')}
              </button>
            </div>
          </div>
        </div>

        {/* Form layout builder panels */}
        <div className="col-span-2 space-y-4">
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.act.layout.sectionTitle')}</h4>

          <div className="space-y-4">
            {layout.sections.map((section, secIdx) => {
              // Find parameters not currently inside this section to allow adding
              const availableParams = actionType.parameters.filter(
                p => !section.parameterIds.includes(p.id)
              );

              return (
                <div key={secIdx} className={`border ${styles.cardBorder} rounded-xl p-4 ${styles.cardBg} space-y-3 shadow-2xs relative`}>
                  {secIdx > 0 && (
                    <button
                      type="button"
                      onClick={() => handleRemoveSection(secIdx)}
                      className={`absolute top-4 right-4 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500 transition-colors`}
                      title={t('ow.act.layout.removeSectionTitle')}
                    >
                      <LucideIcon name="Trash" size={13} />
                    </button>
                  )}

                  <div className={`flex items-center gap-2 border-b ${styles.divider} pb-2`}>
                    <LucideIcon name="Layers" size={13} className="text-blue-500" />
                    <span className={`text-xs font-semibold ${styles.cardText}`}>{section.title}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>({section.parameterIds.length} {t('ow.act.layout.fieldUnit')})</span>
                  </div>

                  {/* Parameter list in this section */}
                  {section.parameterIds.length === 0 ? (
                    <div className={`text-center py-4 ${styles.cardTextMuted} italic text-[11px]`}>
                      {t('ow.act.layout.sectionEmpty')}
                    </div>
                  ) : (
                    <div className="space-y-1.5">
                      {section.parameterIds.map(paramId => {
                        const pDef = actionType.parameters.find(p => p.id === paramId);
                        if (!pDef) return null;
                        return (
                          <div key={paramId} className={`flex justify-between items-center ${styles.appBg} px-3 py-1.5 rounded border ${styles.divider} text-xs`}>
                            <div className="flex items-center gap-2">
                              <span className={`font-mono ${styles.cardTextMuted} text-[10px]`}>[{pDef.dataType}]</span>
                              <span className={`font-semibold ${styles.cardText}`}>{pDef.displayName}</span>
                              <span className={`${styles.cardTextMuted} text-[10px] font-mono`}>({pDef.id})</span>
                            </div>
                            <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded-full font-mono`}>
                              {pDef.isRequired ? t('ow.act.layout.requiredTag') : t('ow.act.layout.optionalTag')}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {/* Add parameter to section dropdown */}
                  {availableParams.length > 0 && (
                    <div className={`flex items-center justify-end gap-2 text-[11px] pt-1 border-t ${styles.divider} mt-2`}>
                      <span className={styles.cardTextMuted}>{t('ow.act.layout.moveField')}</span>
                      <select
                        onChange={e => {
                          if (e.target.value) {
                            handleAddParamToSection(secIdx, e.target.value);
                            e.target.value = '';
                          }
                        }}
                        className={`px-2 py-0.5 border ${styles.inputBorder} rounded ${styles.inputBg} text-[10px] focus:outline-hidden`}
                      >
                        <option value="">{t('ow.act.layout.optSelect')}</option>
                        {availableParams.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>
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
