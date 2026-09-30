/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

interface DomainModalProps {
  editingDomain: OntologyDomain | null;
  setShowDomainModal: (value: boolean) => void;
  setEditingDomain: (value: OntologyDomain | null) => void;
  formId: string;
  setFormId: (value: string) => void;
  formName: string;
  setFormName: (value: string) => void;
  formDesc: string;
  setFormDesc: (value: string) => void;
  formColor: string;
  setFormColor: (value: string) => void;
  formAssignedObjects: string[];
  formError: string;
  allObjectTypes: ObjectType[];
  handleSaveDomain: (e: React.FormEvent) => void;
  toggleObjectAssignment: (objId: string) => void;
}

export default function DomainModal({
  editingDomain,
  setShowDomainModal,
  setEditingDomain,
  formId,
  setFormId,
  formName,
  setFormName,
  formDesc,
  setFormDesc,
  formColor,
  setFormColor,
  formAssignedObjects,
  formError,
  allObjectTypes,
  handleSaveDomain,
  toggleObjectAssignment
}: DomainModalProps) {
  const { styles } = useTheme();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--muted,#0F172A)]/40 backdrop-blur-xs">
      <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden flex flex-col max-h-[85vh]`}>

        {/* Modal Header */}
        <div className={`px-4 py-3 border-b ${styles.divider} ${styles.appBg} flex items-center justify-between`}>
          <div className="flex items-center gap-2">
            <span className="p-1 rounded bg-blue-100 text-blue-600">
              <LucideIcon name="Layers" size={14} />
            </span>
            <h3 className={`text-sm font-bold ${styles.cardText}`}>
              {editingDomain ? '编辑业务域' : '新建业务域'}
            </h3>
          </div>
          <button
            type="button"
            onClick={() => {
              setShowDomainModal(false);
              setEditingDomain(null);
            }}
            className={`p-1 ${styles.cardTextMuted} opacity-80 hover:opacity-100 rounded-lg ${styles.sidebarHoverBg} transition-colors cursor-pointer`}
          >
            <LucideIcon name="X" size={16} />
          </button>
        </div>

        {/* Modal Form */}
        <form onSubmit={handleSaveDomain} className="flex-1 overflow-y-auto p-4 space-y-4">
          {formError && (
            <div className="p-2.5 bg-red-50 text-red-600 border border-red-200 rounded-lg text-xs font-semibold flex items-center gap-2">
              <LucideIcon name="AlertCircle" size={13} />
              <span>{formError}</span>
            </div>
          )}

          {/* ID Input (Only shown on Create) */}
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold text-[11px]`}>
              业务域标识 (ID/Key) <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              disabled={!!editingDomain}
              value={formId}
              onChange={e => setFormId(e.target.value)}
              placeholder="例如: customer_domain (英文/数字/下划线)"
              className={`w-full px-3 py-2 border ${styles.inputBorder} rounded-lg focus:outline-hidden focus:border-blue-500 font-mono text-xs ${styles.inputBg} disabled:bg-blue-50/20 ${styles.cardTextMuted}`}
              required
            />
          </div>

          {/* Display Name Input */}
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold text-[11px]`}>
              业务域名称 (Display Name) <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={formName}
              onChange={e => setFormName(e.target.value)}
              placeholder="例如: 客户域"
              className={`w-full px-3 py-2 border ${styles.inputBorder} rounded-lg focus:outline-hidden focus:border-blue-500 text-xs ${styles.inputBg}`}
              required
            />
          </div>

          {/* Description Input */}
          <div className="space-y-1">
            <label className={`block ${styles.cardTextMuted} font-semibold text-[11px]`}>
              描述 (Description)
            </label>
            <textarea
              value={formDesc}
              onChange={e => setFormDesc(e.target.value)}
              placeholder="对该业务分级域的业务范围和职责进行说明"
              rows={2}
              className={`w-full px-3 py-2 border ${styles.inputBorder} rounded-lg focus:outline-hidden focus:border-blue-500 text-xs resize-none ${styles.inputBg}`}
            />
          </div>

          {/* Color Theme Selector */}
          <div className="space-y-1.5">
            <label className={`block ${styles.cardTextMuted} font-semibold text-[11px]`}>
              视觉主题色 (Color Accent)
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
                      isSelected ? 'border-[var(--card,#334155)] scale-110 shadow-sm' : 'border-transparent hover:scale-105'
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
                    {isSelected && <LucideIcon name="Check" size={12} className="text-white font-bold" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Assign Object Types Checklist */}
          <div className="space-y-1.5">
            <label className={`block ${styles.cardTextMuted} font-semibold flex justify-between items-center text-[11px]`}>
              <span>包含的对象类型 ({formAssignedObjects.length})</span>
              <span className={`text-[9px] ${styles.muted} font-normal`}>多选指派</span>
            </label>
            <div className={`border ${styles.sidebarBorder} rounded-lg max-h-36 overflow-y-auto p-1 ${styles.sidebarBg} divide-y ${styles.divider}`}>
              {allObjectTypes.map(ot => {
                const isChecked = formAssignedObjects.includes(ot.id);
                return (
                  <div
                    key={ot.id}
                    onClick={() => toggleObjectAssignment(ot.id)}
                    className={`flex items-center gap-2 py-1 px-1.5 hover:bg-blue-50/20 rounded-md cursor-pointer text-xs`}
                  >
                    <input
                      type="checkbox"
                      checked={isChecked}
                      onChange={() => {}} // Handle on parent div click
                      className={`rounded border ${styles.inputBorder} text-blue-600 focus:ring-blue-500 h-3 w-3 pointer-events-none`}
                    />
                    <span className={`p-0.5 rounded border ${styles.cardBg} ${styles.cardTextMuted} ${styles.sidebarBorder}`}>
                      <LucideIcon name={ot.icon} size={11} />
                    </span>
                    <div className="flex-1 min-w-0">
                      <p className={`font-semibold ${styles.cardText} truncate text-[11px]`}>{ot.displayName}</p>
                    </div>
                    <span className={`text-[9px] font-mono ${styles.cardTextMuted} uppercase`}>{ot.id}</span>
                  </div>
                );
              })}
              {allObjectTypes.length === 0 && (
                <div className={`p-4 text-center ${styles.cardTextMuted}`}>
                  暂无对象类型可供指派
                </div>
              )}
            </div>
          </div>

          {/* Footer Actions */}
          <div className={`pt-3 border-t ${styles.divider} flex items-center justify-end gap-2`}>
            <button
              type="button"
              onClick={() => {
                setShowDomainModal(false);
                setEditingDomain(null);
              }}
              className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:bg-blue-50/20 ${styles.cardText} transition-colors font-semibold cursor-pointer text-xs ${styles.cardBg}`}
            >
              取消
            </button>
            <button
              type="submit"
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-xs"
            >
              保存
            </button>
          </div>
        </form>

      </div>
    </div>
  );
}
