/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import DomainFormBlock from './DomainFormBlock';
import DomainCardsGrid from './DomainCardsGrid';

interface DomainManagerDeckProps {
  isAddingNew: boolean;
  handleStartAdd: () => void;
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
  handleStartEdit: (domain: OntologyDomain) => void;
  handleDeleteDomain: (domainId: string) => void;
  onQuickNavigate: (category: any, id: string) => void;
}

export default function DomainManagerDeck({
  isAddingNew,
  handleStartAdd,
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
  setEditingDomain,
  handleStartEdit,
  handleDeleteDomain,
  onQuickNavigate
}: DomainManagerDeckProps) {
  const { styles } = useTheme();

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 shadow-xs space-y-4`}>
      <div className={`flex justify-between items-center border-b ${styles.divider} pb-3`}>
        <div>
          <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
            <LucideIcon name="Settings" size={15} className={styles.cardTextMuted} />
            业务分级域维护中心 (Business Domain Manager)
          </h3>
          <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5`}>创建、编辑和删除业务分级域，直观进行本体对象归宿分配 (即多对多关系绑定)。</p>
        </div>
        {!isAddingNew && (
          <button
            onClick={handleStartAdd}
            className="bg-[var(--card,#0F172A)] hover:bg-[var(--muted,#1E293B)] text-white text-xs px-3 py-1.5 rounded-lg font-medium transition-colors flex items-center gap-1 shadow-xs"
          >
            <LucideIcon name="PlusCircle" size={14} />
            新建业务域分级
          </button>
        )}
      </div>

      {isAddingNew ? (
        <DomainFormBlock
          editingDomain={editingDomain}
          formId={formId}
          setFormId={setFormId}
          formName={formName}
          setFormName={setFormName}
          formDesc={formDesc}
          setFormDesc={setFormDesc}
          formColor={formColor}
          setFormColor={setFormColor}
          formAssignedObjects={formAssignedObjects}
          formError={formError}
          objectTypes={objectTypes}
          domains={domains}
          handleSaveDomain={handleSaveDomain}
          toggleObjectAssignment={toggleObjectAssignment}
          setIsAddingNew={setIsAddingNew}
          setEditingDomain={setEditingDomain}
        />
      ) : (
        <DomainCardsGrid
          domains={domains}
          objectTypes={objectTypes}
          handleStartEdit={handleStartEdit}
          handleDeleteDomain={handleDeleteDomain}
          onQuickNavigate={onQuickNavigate}
        />
      )}
    </div>
  );
}
