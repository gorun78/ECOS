/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';

interface SidebarDomainFormParams {
  domains: OntologyDomain[];
  allObjectTypes: ObjectType[];
  selectedDomainId: string | null;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objects: ObjectType[]) => void;
  onSelectDomainId: (id: string | null) => void;
}

/**
 * Business-domain modal state + handlers, extracted verbatim from Sidebar.tsx.
 * The useState calls preserve the original order: showDomainModal, editingDomain,
 * formId, formName, formDesc, formColor, formAssignedObjects, formError.
 */
export function useSidebarDomainForm({
  domains,
  allObjectTypes,
  selectedDomainId,
  onUpdateDomains,
  onUpdateObjectTypes,
  onSelectDomainId
}: SidebarDomainFormParams) {
  const [showDomainModal, setShowDomainModal] = useState(false);
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);

  // Modal states
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formColor, setFormColor] = useState('blue');
  const [formAssignedObjects, setFormAssignedObjects] = useState<string[]>([]);
  const [formError, setFormError] = useState('');

  const handleStartAddDomain = () => {
    setEditingDomain(null);
    setFormId('');
    setFormName('');
    setFormDesc('');
    setFormColor('blue');
    setFormAssignedObjects([]);
    setFormError('');
    setShowDomainModal(true);
  };

  const handleStartEditDomain = (domain: OntologyDomain) => {
    setEditingDomain(domain);
    setFormId(domain.id);
    setFormName(domain.displayName);
    setFormDesc(domain.description || '');
    setFormColor(domain.color);
    const assigned = allObjectTypes.filter(ot => ot.domainId === domain.id).map(ot => ot.id);
    setFormAssignedObjects(assigned);
    setFormError('');
    setShowDomainModal(true);
  };

  const handleDeleteDomain = (domainId: string) => {
    const targetDomain = domains.find(d => d.id === domainId);
    if (!targetDomain) return;

    if (!window.confirm(`确定要删除业务分级域「${targetDomain.displayName}」吗？关联的实体将变更为未分类。`)) {
      return;
    }

    const updatedDomains = domains.filter(d => d.id !== domainId);
    onUpdateDomains(updatedDomains);

    const updatedObjects = allObjectTypes.map(ot => {
      if (ot.domainId === domainId) {
        return { ...ot, domainId: undefined };
      }
      return ot;
    });
    onUpdateObjectTypes(updatedObjects);

    if (selectedDomainId === domainId) {
      onSelectDomainId(null);
    }
  };

  const handleSaveDomain = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');

    if (!formName.trim()) {
      setFormError('业务域名称不能为空');
      return;
    }

    const domainId = editingDomain
      ? editingDomain.id
      : (formId.trim().toLowerCase().replace(/[^a-z0-9_]/g, '') || `domain_${Date.now().toString().slice(-4)}`);

    if (!editingDomain && domains.some(d => d.id === domainId)) {
      setFormError(`业务域ID "${domainId}" 已存在，请使用唯一标识`);
      return;
    }

    const savedDomain: OntologyDomain = {
      id: domainId,
      displayName: formName.trim(),
      description: formDesc.trim(),
      color: formColor
    };

    let newDomains: OntologyDomain[];
    if (editingDomain) {
      newDomains = domains.map(d => d.id === editingDomain.id ? savedDomain : d);
    } else {
      newDomains = [...domains, savedDomain];
    }

    onUpdateDomains(newDomains);

    const updatedObjects = allObjectTypes.map(ot => {
      const shouldHaveThisDomain = formAssignedObjects.includes(ot.id);
      if (shouldHaveThisDomain) {
        return { ...ot, domainId };
      } else if (ot.domainId === domainId) {
        return { ...ot, domainId: undefined };
      }
      return ot;
    });

    onUpdateObjectTypes(updatedObjects);
    setShowDomainModal(false);
    setEditingDomain(null);
  };

  const toggleObjectAssignment = (objId: string) => {
    setFormAssignedObjects(prev =>
      prev.includes(objId)
        ? prev.filter(id => id !== objId)
        : [...prev, objId]
    );
  };

  return {
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
  };
}
