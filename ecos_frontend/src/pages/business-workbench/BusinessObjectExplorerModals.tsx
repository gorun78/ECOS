/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Modals of BusinessObjectExplorer: save-filtered-list + execute-action form.
// Extracted verbatim by H6-T4.

import React from 'react';
import { ActionType, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface SaveSearchListModalProps {
  newSearchName: string;
  onNameChange: (v: string) => void;
  onClose: () => void;
  onSave: () => void;
}

export function SaveSearchListModal({ newSearchName, onNameChange, onClose, onSave }: SaveSearchListModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`fixed inset-0 ${styles.overlayBg} backdrop-blur-3xs flex items-center justify-center z-50`}>
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl shadow-2xl p-5 w-96 space-y-4`}>
        <div className={`flex justify-between items-center pb-2 border-b ${styles.divider}`}>
          <h3 className={`text-xs font-semibold ${styles.text} flex items-center gap-1.5`}>
            <LucideIcon name="Bookmark" size={13} className="text-blue-600" />
            {t('ow.boem.save.title')}
          </h3>
          <button type="button" onClick={onClose} className={`${styles.cardTextMuted} hover:${styles.text}`}>
            <LucideIcon name="X" size={14} />
          </button>
        </div>
        <div>
          <label className={`text-[10px] ${styles.cardTextMuted} block mb-1`}>{t('ow.boem.save.nameLabel')}</label>
          <input
            type="text"
            placeholder={t('ow.boem.save.namePlaceholder')}
            value={newSearchName}
            onChange={e => onNameChange(e.target.value)}
            className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2.5 focus:border-blue-500 focus:outline-hidden`}
          />
        </div>
        <div className="flex justify-end gap-2 pt-1 text-[11px]">
          <button
            type="button"
            onClick={onClose}
            className={`h-8 px-3 rounded ${styles.appBg} hover:bg-blue-50/20 ${styles.cardTextMuted} font-semibold`}
          >
            {t('ow.boem.save.cancel')}
          </button>
          <button
            type="button"
            onClick={onSave}
            disabled={!newSearchName.trim()}
            className="h-8 px-4 rounded bg-blue-600 hover:bg-blue-500 text-white font-semibold disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {t('ow.boem.save.confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

interface ExecuteActionModalProps {
  action: ActionType;
  activeObjectType: ObjectType | null;
  actionParams: Record<string, string>;
  onActionParamsChange: (next: Record<string, string>) => void;
  actionError: string | null;
  onClose: () => void;
  onExecute: () => void;
}

export function ExecuteActionModal({
  action,
  activeObjectType,
  actionParams,
  onActionParamsChange,
  actionError,
  onClose,
  onExecute,
}: ExecuteActionModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`fixed inset-0 ${styles.overlayBg} backdrop-blur-3xs flex items-center justify-center z-50`}>
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl shadow-2xl p-5 w-full sm:w-[420px] mx-4 sm:mx-auto space-y-4`}>
        <div className={`flex justify-between items-center pb-2.5 border-b ${styles.divider}`}>
          <div className="flex items-center gap-1.5">
            <LucideIcon name="Zap" size={14} className="text-amber-500 fill-amber-500/10" />
            <h3 className={`text-xs font-semibold ${styles.text}`}>
              {t('ow.boem.exec.title')}：{action.displayName}
            </h3>
          </div>
          <button type="button" onClick={onClose} className={`${styles.cardTextMuted} hover:${styles.text}`}>
            <LucideIcon name="X" size={14} />
          </button>
        </div>

        <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>{action.description}</p>

        <div className="space-y-3 pt-1">
          {action.parameters.map(param => {
            const isObjectParam = param.dataType === 'object';
            const isLocked = isObjectParam && param.objectTypeId === activeObjectType?.id;

            return (
              <div key={param.id} className="space-y-1 text-xs">
                <label className={`text-[10px] ${styles.cardTextMuted} font-semibold flex items-center justify-between`}>
                  <span>{param.displayName} ({param.id})</span>
                  {param.isRequired && <span className="text-red-500 font-bold">{t('ow.boem.exec.required')}</span>}
                </label>

                {isLocked ? (
                  <input
                    type="text"
                    disabled
                    value={actionParams[param.id] || ''}
                    className={`w-full h-8 text-[11px] ${styles.sidebarBg} border ${styles.inputBorder} rounded px-2.5 ${styles.cardTextMuted} font-mono`}
                  />
                ) : param.id === 'new_status_param' ? (
                  <select
                    value={actionParams[param.id] || ''}
                    onChange={e => onActionParamsChange({ ...actionParams, [param.id]: e.target.value })}
                    className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2 focus:border-blue-500`}
                  >
                    <option value="">{t('ow.boem.exec.optSelect')}</option>
                    {activeObjectType?.id === 'flight' && (
                      <>
                        <option value="ON_TIME">ON_TIME ({t('ow.boem.exec.stOnTime')})</option>
                        <option value="DELAYED">DELAYED ({t('ow.boem.exec.stDelayed')})</option>
                        <option value="BOARDING">BOARDING ({t('ow.boem.exec.stBoarding')})</option>
                        <option value="CANCELLED">CANCELLED ({t('ow.boem.exec.stCancelled')})</option>
                      </>
                    )}
                    {activeObjectType?.id === 'aircraft' && (
                      <>
                        <option value="ACTIVE">ACTIVE ({t('ow.boem.exec.stActive')})</option>
                        <option value="MAINTENANCE">MAINTENANCE ({t('ow.boem.exec.stMaintenance')})</option>
                        <option value="INSPECTION">INSPECTION ({t('ow.boem.exec.stInspection')})</option>
                      </>
                    )}
                  </select>
                ) : param.dataType === 'date' ? (
                  <input
                    type="date"
                    value={actionParams[param.id] || ''}
                    onChange={e => onActionParamsChange({ ...actionParams, [param.id]: e.target.value })}
                    className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2.5 focus:border-blue-500`}
                  />
                ) : (
                  <input
                    type="text"
                    placeholder={t('ow.boem.exec.inputPlaceholder', { name: param.displayName })}
                    value={actionParams[param.id] || ''}
                    onChange={e => onActionParamsChange({ ...actionParams, [param.id]: e.target.value })}
                    className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2.5 focus:border-blue-500`}
                  />
                )}

                <p className={`text-[9px] ${styles.cardTextMuted}`}>{param.description}</p>
              </div>
            );
          })}
        </div>

        {/* validation error line */}
        {actionError && (
          <div className="p-2 bg-red-50 border border-red-200 rounded text-[10px] text-red-600 font-medium">
            ❌ {t('ow.boem.exec.constraintError')}：{actionError}
          </div>
        )}

        <div className={`flex justify-end gap-2 pt-2 border-t ${styles.divider} text-[11px]`}>
          <button
            type="button"
            onClick={onClose}
            className={`h-8 px-3 rounded ${styles.appBg} hover:bg-blue-50/20 ${styles.cardTextMuted} font-semibold`}
          >
            {t('ow.boem.exec.cancel')}
          </button>
          <button
            type="button"
            onClick={onExecute}
            className="h-8 px-4 rounded bg-amber-500 hover:bg-amber-400 text-white font-semibold flex items-center gap-1"
          >
            <LucideIcon name="CheckCircle" size={12} />
            <span>{t('ow.boem.exec.execute')}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
