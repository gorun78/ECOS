/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';

interface OverviewDomainFormParams {
  domains: OntologyDomain[];
  objectTypes: ObjectType[];
  selectedDomainFilter: string | null;
  onSelectDomainFilter: (id: string | null) => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objectTypes: ObjectType[]) => void;
}

/**
 * Domain Maintenance form state + CRUD logic, extracted verbatim from
 * OverviewView.tsx. The useState calls preserve the original order:
 * editingDomain, formId, formName, formDesc, formColor, formAssignedObjects,
 * isAddingNew, formError.
 */
export function useOverviewDomainForm({
  domains,
  objectTypes,
  selectedDomainFilter,
  onSelectDomainFilter,
  onUpdateDomains,
  onUpdateObjectTypes
}: OverviewDomainFormParams) {
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formColor, setFormColor] = useState('blue');
  const [formAssignedObjects, setFormAssignedObjects] = useState<string[]>([]);
  const [isAddingNew, setIsAddingNew] = useState(false);
  const [formError, setFormError] = useState('');

  // Domain CRUD logic
  const handleStartAdd = () => {
    setEditingDomain(null);
    setFormId('');
    setFormName('');
    setFormDesc('');
    setFormColor('blue');
    setFormAssignedObjects([]);
    setIsAddingNew(true);
    setFormError('');
  };

  const handleStartEdit = (domain: OntologyDomain) => {
    setEditingDomain(domain);
    setFormId(domain.id);
    setFormName(domain.displayName);
    setFormDesc(domain.description);
    setFormColor(domain.color);
    // Find all objects mapped to this domain
    const assigned = objectTypes.filter(ot => ot.domainId === domain.id).map(ot => ot.id);
    setFormAssignedObjects(assigned);
    setIsAddingNew(true);
    setFormError('');
  };

  const handleDeleteDomain = (domainId: string) => {
    if (!window.confirm(`确定要删除业务划分域「${domains.find(d => d.id === domainId)?.displayName}」吗？关联的实体将变更为未分类。`)) {
      return;
    }
    // Delete domain
    const updatedDomains = domains.filter(d => d.id !== domainId);
    onUpdateDomains(updatedDomains);

    // Unassign objects
    const updatedObjects = objectTypes.map(ot => {
      if (ot.domainId === domainId) {
        return { ...ot, domainId: undefined };
      }
      return ot;
    });
    onUpdateObjectTypes(updatedObjects);

    if (selectedDomainFilter === domainId) {
      onSelectDomainFilter(null);
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

    // Update Domains state
    onUpdateDomains(newDomains);

    // Update ObjectTypes Domain mapping
    const updatedObjects = objectTypes.map(ot => {
      const shouldHaveThisDomain = formAssignedObjects.includes(ot.id);
      if (shouldHaveThisDomain) {
        return { ...ot, domainId };
      } else if (ot.domainId === domainId) {
        // Was mapped to this domain but unchecked
        return { ...ot, domainId: undefined };
      }
      return ot;
    });

    onUpdateObjectTypes(updatedObjects);
    setIsAddingNew(false);
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
  };
}
