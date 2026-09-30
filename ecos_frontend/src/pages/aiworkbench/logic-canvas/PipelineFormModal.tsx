/**
 * PipelineFormModal — LogicView 新建/编辑 Pipeline 弹窗表单。
 * 由 LogicView.tsx 机械抽取（H6-T4），JSX 结构与样式与原文逐行一致；
 * 表单值与 setter 以 props 透传，父组件保持原有状态所有权。
 * @license Apache-2.0
 */
import React from 'react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { AIPLogicPipeline } from '../../../types/aiworkbench';
import { Icon } from './LogicIcon';

export default function PipelineFormModal({
  editingPipeline,
  formName,
  setFormName,
  formDesc,
  setFormDesc,
  formInputName,
  setFormInputName,
  formInputType,
  setFormInputType,
  onSave,
  onClose,
}: {
  editingPipeline: AIPLogicPipeline | null;
  formName: string;
  setFormName: (v: string) => void;
  formDesc: string;
  setFormDesc: (v: string) => void;
  formInputName: string;
  setFormInputName: (v: string) => void;
  formInputType: string;
  setFormInputType: (v: string) => void;
  onSave: (e: React.FormEvent) => void;
  onClose: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
      <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <h3 className={`font-bold ${styles.cardText} text-xs`}>
            {editingPipeline ? t('aiworkbench.logic.modal.editTitle') : t('aiworkbench.logic.modal.createTitle')}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className={`${styles.cardTextMuted} hover:opacity-70 cursor-pointer transition-opacity`}
          >
            <Icon name="X" size={15} />
          </button>
        </div>
        <form onSubmit={onSave} className="p-4 space-y-4">
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.name')} <span className={`${styles.dangerText}`}>*</span></label>
            <input
              type="text"
              value={formName}
              onChange={e => setFormName(e.target.value)}
              placeholder={t('aiworkbench.logic.modal.namePlaceholder')}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
              required
            />
          </div>
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.desc')} <span className={`${styles.dangerText}`}>*</span></label>
            <textarea
              value={formDesc}
              onChange={e => setFormDesc(e.target.value)}
              placeholder={t('aiworkbench.logic.modal.descPlaceholder')}
              rows={2}
              className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none`}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.inputName')}</label>
              <input
                type="text"
                value={formInputName}
                onChange={e => setFormInputName(e.target.value)}
                placeholder={t('aiworkbench.logic.modal.inputNamePlaceholder')}
                className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono`}
              />
            </div>
            <div className="space-y-1">
              <label className={`block ${styles.cardTextMuted} font-semibold`}>{t('aiworkbench.logic.modal.inputType')}</label>
              <select
                value={formInputType}
                onChange={e => setFormInputType(e.target.value)}
                className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
              >
                <option value="string">String</option>
                <option value="integer">Integer</option>
                <option value="boolean">Boolean</option>
              </select>
            </div>
          </div>
          <div className={`pt-2 border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
            <button
              type="button"
              onClick={onClose}
              className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} transition-opacity hover:opacity-80 cursor-pointer text-[11px] font-semibold`}
            >
              {t('aiworkbench.logic.modal.cancel')}
            </button>
            <button
              type="submit"
              className={`px-4 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.accentText} rounded-lg transition-opacity font-bold shadow-sm cursor-pointer text-[11px]`}
            >
              {t('aiworkbench.logic.modal.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
