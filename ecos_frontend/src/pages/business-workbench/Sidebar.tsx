/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, FunctionType, OntologyDomain, PropertyType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import DomainSelectorHeader from './sidebar/DomainSelectorHeader';
import {
  ObjectTypesAccordion,
  LinkTypesAccordion,
  ActionTypesAccordion,
  FunctionTypesAccordion,
  InterfacesAccordion,
  SharedPropertiesAccordion,
  DatasetsAccordion
} from './sidebar/SidebarAccordions';
import CreateMenuFooter from './sidebar/CreateMenuFooter';
import DomainModal from './sidebar/DomainModal';
import { useSidebarDomainForm } from './sidebar/useSidebarDomainForm';

interface SidebarProps {
  objectTypes: ObjectType[];
  allObjectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  interfaces: InterfaceType[];
  sharedProperties: SharedProperty[];
  datasets: Dataset[];
  functionTypes: FunctionType[];
  domains: OntologyDomain[];
  selectedDomainId: string | null;
  onSelectDomainId: (id: string | null) => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objects: ObjectType[]) => void;

  selectedCategory: 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
  selectedId: string | null;

  onSelectCategory: (category: any, id: string | null) => void;
  onCreateNew: (type: 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function') => void;
}

export default function Sidebar({
  objectTypes,
  allObjectTypes,
  linkTypes,
  actionTypes,
  interfaces,
  sharedProperties,
  datasets,
  functionTypes,
  domains,
  selectedDomainId,
  onSelectDomainId,
  onUpdateDomains,
  onUpdateObjectTypes,
  selectedCategory,
  selectedId,
  onSelectCategory,
  onCreateNew
}: SidebarProps) {
  const { styles } = useTheme();
  const [expanded, setExpanded] = useState<Record<string, boolean>>({
    object: true,
    link: true,
    action: true,
    function: true,
    interface: false,
    shared_property: false,
    dataset: false
  });

  const toggleExpand = (key: string) => {
    setExpanded(prev => ({ ...prev, [key]: !prev[key] }));
  };

  const [showCreateDropdown, setShowCreateDropdown] = useState(false);
  const [showDomainDropdown, setShowDomainDropdown] = useState(false);

  // Extracted hook — preserves the original useState order:
  // showDomainModal, editingDomain, formId, formName, formDesc, formColor, formAssignedObjects, formError
  const {
    showDomainModal,
    setShowDomainModal,
    editingDomain,
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
    handleStartAddDomain,
    handleStartEditDomain,
    handleDeleteDomain,
    handleSaveDomain,
    toggleObjectAssignment
  } = useSidebarDomainForm({
    domains,
    allObjectTypes,
    selectedDomainId,
    onUpdateDomains,
    onUpdateObjectTypes,
    onSelectDomainId
  });

  const selectedDomain = domains.find(d => d.id === selectedDomainId);

  return (
    <aside className={`w-64 ${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col h-full select-none shrink-0 text-xs`}>

      <DomainSelectorHeader
        selectedCategory={selectedCategory}
        selectedDomain={selectedDomain}
        selectedDomainId={selectedDomainId}
        domains={domains}
        allObjectTypes={allObjectTypes}
        showDomainDropdown={showDomainDropdown}
        setShowDomainDropdown={setShowDomainDropdown}
        onSelectDomainId={onSelectDomainId}
        onSelectCategory={onSelectCategory}
        handleStartAddDomain={handleStartAddDomain}
        handleStartEditDomain={handleStartEditDomain}
        handleDeleteDomain={handleDeleteDomain}
      />

      {/* Accordions List */}
      <div className="flex-1 overflow-y-auto py-3 space-y-1">

        {/* 1. OBJECT TYPES */}
        <ObjectTypesAccordion
          objectTypes={objectTypes}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 2. LINK TYPES */}
        <LinkTypesAccordion
          linkTypes={linkTypes}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 3. ACTION TYPES */}
        <ActionTypesAccordion
          actionTypes={actionTypes}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 3.5. FUNCTION TYPES */}
        <FunctionTypesAccordion
          functionTypes={functionTypes}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 4. INTERFACE TYPES */}
        <InterfacesAccordion
          interfaces={interfaces}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 5. SHARED PROPERTIES */}
        <SharedPropertiesAccordion
          sharedProperties={sharedProperties}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />

        {/* 6. RAW DATASETS */}
        <DatasetsAccordion
          datasets={datasets}
          expanded={expanded}
          toggleExpand={toggleExpand}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={onSelectCategory}
        />
      </div>

      {/* Bottom Action bar */}
      <CreateMenuFooter
        showCreateDropdown={showCreateDropdown}
        setShowCreateDropdown={setShowCreateDropdown}
        onCreateNew={onCreateNew}
      />

      {/* 业务划分域模态对话框 */}
      {showDomainModal && (
        <DomainModal
          editingDomain={editingDomain}
          setShowDomainModal={setShowDomainModal}
          setEditingDomain={setEditingDomain}
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
          allObjectTypes={allObjectTypes}
          handleSaveDomain={handleSaveDomain}
          toggleObjectAssignment={toggleObjectAssignment}
        />
      )}

    </aside>
  );
}
