/**
 * DomainFormModal — 业务划分域创建/编辑模态对话框（含对象归属勾选）
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 表单结构、className、色板与校验展示均与原实现逐字一致。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { AlertCircle, Check, Layers, X } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import { useTheme } from '../../ThemeContext';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import DynamicIcon from './SidebarDynamicIcon';

interface DomainFormModalProps {
  editingDomain: OntologyDomain | null;
  allObjectTypes: ObjectType[];
  saving: boolean;
  formId: string;
  formName: string;
  formDesc: string;
  formColor: string;
  formAssignedObjects: string[];
  formError: string;
  setFormId: (value: string) => void;
  setFormName: (value: string) => void;
  setFormDesc: (value: string) => void;
  setFormColor: (value: string) => void;
  onClose: () => void;
  onSave: (e: React.FormEvent) => void;
  onToggleObject: (objId: string) => void;
}

export default function DomainFormModal({
  editingDomain,
  allObjectTypes,
  saving,
  formId,
  formName,
  formDesc,
  formColor,
  formAssignedObjects,
  formError,
  setFormId,
  setFormName,
  setFormDesc,
  setFormColor,
  onClose,
  onSave,
  onToggleObject,
}: DomainFormModalProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.overlayBg} backdrop-blur-xs`}>
      <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.appBorder} w-full max-w-md overflow-hidden flex flex-col max-h-[85vh]`}>

        {/* Modal Header */}
        <div className={`px-4 py-3 border-b ${styles.appBorder} ${styles.sidebarBg} flex items-center justify-between`}>
          <div className="flex items-center gap-2">
            <span className={`p-1 rounded ${styles.badgeBg} ${styles.badgeText}`}>
              <Layers size={14} />
            </span>
            <h3 className={`text-sm font-bold ${styles.cardText}`}>
              {editingDomain ? t('ow.btn.editDomainTitle') : t('ow.btn.newDomainTitle')}
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className={`${styles.muted} hover:${styles.cardText} p-1 rounded-lg hover:${styles.sidebarHoverBg} transition-colors cursor-pointer`}
          >
            <X size={16} />
          </button>
        </div>

        {/* Modal Form — T8: 表单 error 用 danger semantic + 保存按钮 loading */}
        <form onSubmit={onSave} className="flex-1 overflow-y-auto p-4 space-y-4">
          {formError && (
            <div className={`p-2.5 ${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder} rounded-lg text-xs font-semibold flex items-center gap-2`}>
              <AlertCircle size={13} />
              <span>{formError}</span>
            </div>
          )}

          {/* ID Input (Only shown on Create) */}
          <div className="space-y-1">
            <label className={`block ${styles.sidebarText} font-semibold text-[11px]`}>
              {t('ow.label.domainId')} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              disabled={!!editingDomain}
              value={formId}
              onChange={e => setFormId(e.target.value)}
              placeholder={t('ow.placeholder.domainId')}
              className={`w-full px-3 py-2 border ${styles.appBorder} rounded-lg focus:outline-hidden focus:border-blue-500 font-mono text-xs ${styles.inputBg} disabled:opacity-60`}
              required
            />
          </div>

          {/* Display Name Input */}
          <div className="space-y-1">
            <label className={`block ${styles.sidebarText} font-semibold text-[11px]`}>
              {t('ow.label.domainDisplayName')} <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={formName}
              onChange={e => setFormName(e.target.value)}
              placeholder={t('ow.placeholder.domainDisplayName')}
              className={`w-full px-3 py-2 border ${styles.appBorder} rounded-lg focus:outline-hidden focus:border-blue-500 text-xs ${styles.inputBg} ${styles.inputText}`}
              required
            />
          </div>

          {/* Description Input */}
          <div className="space-y-1">
            <label className={`block ${styles.sidebarText} font-semibold text-[11px]`}>
              {t('ow.label.domainDescription')}
            </label>
            <textarea
              value={formDesc}
              onChange={e => setFormDesc(e.target.value)}
              placeholder={t('ow.placeholder.domainDescription')}
              rows={2}
              className={`w-full px-3 py-2 border ${styles.appBorder} rounded-lg focus:outline-hidden focus:border-blue-500 text-xs resize-none ${styles.inputBg} ${styles.inputText}`}
            />
          </div>

          {/* Color Theme Selector */}
          <div className="space-y-1.5">
            <label className={`block ${styles.sidebarText} font-semibold text-[11px]`}>
              {t('ow.label.domainColor')}
            </label>
            <div className="flex flex-wrap gap-2">
              {['blue', 'emerald', 'amber', 'purple', 'rose', 'indigo', 'slate'].map(color => {
                const isSelected = formColor === color;
                return (
                  <button
                    key={color}
                    type="button"
                    onClick={() => setFormColor(color)}
                    className={`w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all ${
                      isSelected ? `${styles.accentBorder} scale-110 shadow-sm` : 'border-transparent hover:scale-105'
                    }`}
                    style={{ backgroundColor:
                      color === 'blue' ? '#3b82f6' :
                      color === 'emerald' ? '#10b981' :
                      color === 'amber' ? '#f59e0b' :
                      color === 'purple' ? '#8b5cf6' :
                      color === 'rose' ? '#f43f5e' :
                      color === 'indigo' ? '#6366f1' : '#64748b'
                    }}
                  >
                    {isSelected && <Check size={12} className={styles.cardText} />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Assign Object Types Checklist */}
          <div className="space-y-1.5">
            <label className={`block ${styles.sidebarText} font-semibold flex justify-between items-center text-[11px]`}>
              <span>{t('ow.label.domainAssignedObjects').replace('{count}', String(formAssignedObjects.length))}</span>
              <span className={`text-[9px] ${styles.muted} font-normal`}>{t('ow.label.multiSelect')}</span>
            </label>
            <div className={`border ${styles.appBorder} rounded-lg max-h-36 overflow-y-auto p-1 ${styles.inputBg} divide-y`}>
              {allObjectTypes.map(ot => {
                const isChecked = formAssignedObjects.includes(ot.id);
                return (
                  <div
                    key={ot.id}
                    onClick={() => onToggleObject(ot.id)}
                    className={`flex items-center gap-2 py-1 px-1.5 ${styles.sidebarHoverBg} rounded-md cursor-pointer text-xs`}
                  >
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => {}} // Handle on parent div click
                      className={`rounded ${styles.inputBorder} text-blue-600 focus:ring-blue-500 h-3 w-3 pointer-events-none`}
                    />
                    <span className={`p-0.5 rounded border ${styles.cardBg} ${styles.appBorder} ${styles.muted}`}>
                      <DynamicIcon name={ot.icon} size={11} />
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className={`font-semibold ${styles.cardText} truncate text-[11px]`}>{ot.displayName}</p>
                    </div>
                    <span className={`text-[9px] font-mono ${styles.muted} uppercase`}>{ot.id}</span>
                  </div>
                );
              })}
              {allObjectTypes.length === 0 && (
                <div className={`p-4 text-center ${styles.muted}`}>
                  {t('ow.empty.noObjectTypesForAssign')}
                </div>
              )}
            </div>
          </div>

          {/* Footer Actions */}
          <div className={`pt-3 border-t ${styles.appBorder} flex items-center justify-end gap-2`}>
            <button
              type="button"
              onClick={onClose}
              className={`px-3 py-1.5 border ${styles.appBorder} ${styles.cardBg} ${styles.cardText} hover:${styles.sidebarHoverBg} transition-colors font-semibold cursor-pointer text-xs`}
            >
              {t('ow.btn.cancel')}
            </button>
            <button
              type="submit"
              disabled={saving}
              className={`px-3.5 py-1.5 ${styles.accentBg} text-white ${styles.accentHover} rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-xs disabled:opacity-50`}
            >
              {saving ? '...' : t('ow.btn.save')}
            </button>
          </div>
        </form>

      </div>
    </div>
  );
}
