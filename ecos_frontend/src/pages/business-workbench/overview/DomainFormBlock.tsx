/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { getDomainColorClasses } from './domainColorClasses';

interface DomainFormBlockProps {
  editingDomain: OntologyDomain | null;
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
  objectTypes: ObjectType[];
  domains: OntologyDomain[];
  handleSaveDomain: (e: React.FormEvent) => void;
  toggleObjectAssignment: (objId: string) => void;
  setIsAddingNew: (value: boolean) => void;
  setEditingDomain: (value: OntologyDomain | null) => void;
}

export default function DomainFormBlock({
  editingDomain,
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
  objectTypes,
  domains,
  handleSaveDomain,
  toggleObjectAssignment,
  setIsAddingNew,
  setEditingDomain
}: DomainFormBlockProps) {
  const { styles } = useTheme();

  return (
    /* Domain Creation/Edit Form Block */
    <form onSubmit={handleSaveDomain} className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
      <div className={`flex items-center justify-between border-b ${styles.divider} pb-2`}>
        <h4 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <LucideIcon name="Edit3" size={14} className="text-blue-500" />
          <span>{editingDomain ? `编辑业务域: ${editingDomain.displayName}` : '新建业务域分级'}</span>
        </h4>
        <button
          type="button"
          onClick={() => { setIsAddingNew(false); setEditingDomain(null); }}
          className={`${styles.cardTextMuted} opacity-70 hover:opacity-100 p-1 rounded hover:bg-blue-50/20`}
        >
          <LucideIcon name="X" size={16} />
        </button>
      </div>

      {formError && (
        <div className="p-2.5 bg-red-50 border border-red-200 text-red-600 text-[11px] rounded-md flex items-center gap-1.5">
          <LucideIcon name="AlertCircle" size={14} />
          <span>{formError}</span>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="space-y-1 md:col-span-1">
          <label className={`text-[11px] font-bold ${styles.cardTextMuted} uppercase tracking-wider block`}>唯一标识 ID (不可包含空格)</label>
          <input
            type="text"
            disabled={!!editingDomain}
            value={formId}
            onChange={e => setFormId(e.target.value)}
            placeholder="如: flight_ops"
            className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden disabled:bg-blue-50/20 ${styles.cardTextMuted} font-mono`}
          />
        </div>

        <div className="space-y-1 md:col-span-1">
          <label className={`text-[11px] font-bold ${styles.cardTextMuted} uppercase tracking-wider block`}>显示名称 (Display Name)</label>
          <input
            type="text"
            value={formName}
            onChange={e => setFormName(e.target.value)}
            placeholder="如: 运行控制域"
            className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
          />
        </div>

        <div className="space-y-1 md:col-span-1">
          <label className={`text-[11px] font-bold ${styles.cardTextMuted} uppercase tracking-wider block`}>代表色调 (Theme Color)</label>
          <div className="flex items-center gap-1.5 py-1">
            {['blue', 'emerald', 'amber', 'purple', 'rose', 'indigo', 'slate'].map(color => {
              const isSelected = formColor === color;
              const classes = getDomainColorClasses(color);
              return (
                <button
                  key={color}
                  type="button"
                  onClick={() => setFormColor(color)}
                  className={`w-6 h-6 rounded-full border-2 transition-transform flex items-center justify-center ${classes.bg} ${isSelected ? 'border-[var(--card,#334155)] scale-110 shadow-xs' : `${styles.sidebarBorder} hover:scale-105`}`}
                  title={color}
                >
                  <span className={`w-2.5 h-2.5 rounded-full ${classes.dot}`} />
                </button>
              );
            })}
          </div>
        </div>
      </div>

      <div className="space-y-1">
        <label className={`text-[11px] font-bold ${styles.cardTextMuted} uppercase tracking-wider block`}>业务描述信息 (Description)</label>
        <textarea
          value={formDesc}
          onChange={e => setFormDesc(e.target.value)}
          placeholder="简述该业务域所承载的核心职能、负责团队或数据流转范围。"
          className={`w-full h-16 px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
        />
      </div>

      {/* Object types assignment in the form */}
      <div className={`space-y-2 border-t ${styles.divider} pt-3`}>
        <label className={`text-[11px] font-bold ${styles.cardTextMuted} uppercase tracking-wider block`}>
          绑定关联的实体对象 (Object Types Assignment)
        </label>
        <div className={`text-[10px] ${styles.cardTextMuted} -mt-1`}>直接勾选属于该业务域的实体类型。一个实体同一时间仅可归属一个业务域。</div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 pt-1.5">
          {objectTypes.map(ot => {
            const isChecked = formAssignedObjects.includes(ot.id);
            const isMappedToOther = ot.domainId && ot.domainId !== formId;
            const otherDomain = isMappedToOther ? domains.find(d => d.id === ot.domainId) : null;

            return (
              <div
                key={ot.id}
                onClick={() => !isMappedToOther && toggleObjectAssignment(ot.id)}
                className={`flex items-center justify-between p-2 rounded-lg border text-xs select-none transition-all ${
                  isMappedToOther
                    ? `${styles.sidebarBg} ${styles.sidebarBorder} ${styles.cardTextMuted} cursor-not-allowed opacity-60`
                    : isChecked
                    ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium cursor-pointer'
                    : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:border-blue-400 hover:bg-blue-50/20 cursor-pointer`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={`p-0.5 rounded border ${isChecked ? 'bg-blue-100 border-blue-200' : `${styles.appBg} ${styles.cardBorder}`}`}>
                    <LucideIcon name={ot.icon} size={12} />
                  </span>
                  <span className="truncate">{ot.displayName}</span>
                </div>
                <div className="flex items-center">
                  {isMappedToOther ? (
                    <span className={`text-[9px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1 py-0.2 rounded font-mono truncate max-w-[65px]`} title={`已被域 "${otherDomain?.displayName}" 绑定`}>
                      {otherDomain?.displayName.split(' (')[0]}
                    </span>
                  ) : (
                    <span className={`w-3.5 h-3.5 rounded border flex items-center justify-center ${isChecked ? 'bg-blue-600 border-blue-600 text-white' : `${styles.cardBorder} ${styles.cardBg}`}`}>
                      {isChecked && <LucideIcon name="Check" size={10} />}
                    </span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Form actions */}
      <div className={`flex justify-end gap-2 border-t ${styles.divider} pt-3`}>
        <button
          type="button"
          onClick={() => { setIsAddingNew(false); setEditingDomain(null); }}
          className={`px-3.5 py-1.5 rounded-lg border ${styles.cardBorder} ${styles.cardTextMuted} text-xs font-semibold ${styles.cardBg} hover:bg-blue-50/20`}
        >
          取消
        </button>
        <button
          type="submit"
          className="px-4 py-1.5 rounded-lg text-white text-xs font-semibold bg-blue-600 hover:bg-blue-700 shadow-sm"
        >
          {editingDomain ? '保存修改' : '创建业务域'}
        </button>
      </div>
    </form>
  );
}
