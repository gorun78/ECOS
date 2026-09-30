/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, OntologyDomain } from '../../types/ontology';
import OntologyGraph from './OntologyGraph';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import SummaryCards from './overview/SummaryCards';
import GraphPanel from './overview/GraphPanel';
import DomainManagerDeck from './overview/DomainManagerDeck';
import QuickNavPanel from './overview/QuickNavPanel';
import ActivityLogPanel from './overview/ActivityLogPanel';
import { useOverviewDomainForm } from './overview/useOverviewDomainForm';

interface OverviewViewProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  interfaces: InterfaceType[];
  sharedProperties: SharedProperty[];
  datasets: Dataset[];
  domains: OntologyDomain[];
  selectedDomainFilter: string | null;
  onSelectDomainFilter: (id: string | null) => void;

  onSelectNode: (nodeId: string) => void;
  onSelectEdge: (edgeId: string) => void;
  onQuickNavigate: (category: any, id: string) => void;
  onViewModeChange?: (mode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security') => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objectTypes: ObjectType[]) => void;
}

export default function OverviewView({
  objectTypes,
  linkTypes,
  actionTypes,
  interfaces,
  sharedProperties,
  datasets,
  domains,
  selectedDomainFilter,
  onSelectDomainFilter,
  onSelectNode,
  onSelectEdge,
  onQuickNavigate,
  onViewModeChange,
  onUpdateDomains,
  onUpdateObjectTypes
}: OverviewViewProps) {
  const { styles } = useTheme();

  // Tab/Filter States
  // Controlled by parent state

  // Domain Maintenance Form States — extracted hook, original useState order preserved:
  // editingDomain, formId, formName, formDesc, formColor, formAssignedObjects, isAddingNew, formError
  const {
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
    isAddingNew,
    setIsAddingNew,
    formError,
    handleStartAdd,
    handleStartEdit,
    handleDeleteDomain,
    handleSaveDomain,
    toggleObjectAssignment
  } = useOverviewDomainForm({
    domains,
    objectTypes,
    selectedDomainFilter,
    onSelectDomainFilter,
    onUpdateDomains,
    onUpdateObjectTypes
  });

  // Calculate total properties
  const totalPropsCount = objectTypes.reduce((acc, ot) => acc + ot.properties.length, 0);

  // Filter objects/links for graph rendering
  const displayedObjects = !selectedDomainFilter
    ? objectTypes
    : selectedDomainFilter === 'unassigned'
    ? objectTypes.filter(ot => !ot.domainId)
    : objectTypes.filter(ot => ot.domainId === selectedDomainFilter);

  const displayedLinks = linkTypes.filter(lt =>
    displayedObjects.some(o => o.id === lt.sourceObjectType) &&
    displayedObjects.some(o => o.id === lt.targetObjectType)
  );

  return (
    <div className={`flex flex-col h-full ${styles.appBg} overflow-y-auto p-6 space-y-6 select-none`}>


      {/* Title & Introduction Banner */}
      <div className={`${styles.cardBg} border ${styles.appBorder} rounded-xl p-5 shadow-xs flex items-center justify-between`}>
        <div className="space-y-1 flex-1">
          <h2 className={`text-sm font-semibold ${styles.cardText} flex items-center gap-1.5`}>
            <LucideIcon name="Workflow" className="text-blue-600" size={16} />
            航空资产与运行智能本体 (Aviation Core Ontology)
          </h2>
          <p className={`text-xs ${styles.cardTextMuted} max-w-3xl leading-relaxed`}>
            物理世界实体网络庞大异构。为便于大型航司进行数字治理，我们支持将本体对象归属到不同的「业务域 (Domains)」分级中。
            这有助于在大规模本体资产中按业务边界过滤拓扑，维护实体上下级职责和可见性规范。
          </p>
        </div>
        <div className="flex items-center gap-2 bg-blue-50 border border-blue-100 text-blue-700 px-3 py-1.5 rounded-lg text-xs font-medium shrink-0">
          <LucideIcon name="ShieldCheck" size={13} />
          <span>域分级已就绪 · 共 {domains.length} 个业务域</span>
        </div>
      </div>

      {/* Analytics Summary Cards Grid */}
      <SummaryCards
        objectTypes={objectTypes}
        linkTypes={linkTypes}
        interfaces={interfaces}
        sharedProperties={sharedProperties}
        domains={domains}
      />

      {/* Main Graph Panel */}
      <GraphPanel
        objectTypes={objectTypes}
        domains={domains}
        selectedDomainFilter={selectedDomainFilter}
        onSelectDomainFilter={onSelectDomainFilter}
        displayedObjects={displayedObjects}
        displayedLinks={displayedLinks}
        onSelectNode={onSelectNode}
        onSelectEdge={onSelectEdge}
      />

      {/* 🛠️ Domain Management & Classification Maintenance Deck */}
      <DomainManagerDeck
        isAddingNew={isAddingNew}
        handleStartAdd={handleStartAdd}
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
        handleStartEdit={handleStartEdit}
        handleDeleteDomain={handleDeleteDomain}
        onQuickNavigate={onQuickNavigate}
      />

      {/* Bottom Row split: Quick Navigator & Enterprise Logs */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* Quick List Nav segmented by Domain */}
        <QuickNavPanel
          domains={domains}
          objectTypes={objectTypes}
          onQuickNavigate={onQuickNavigate}
        />

        {/* Enterprise lifecycle logs */}
        <ActivityLogPanel />
      </div>

    </div>
  );
}
