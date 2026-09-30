/**
 * useDomainManager — 业务划分域 CRUD / 状态流转的有状态逻辑
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 弹窗开关、表单字段 state 与全部 handler 均按原顺序、原实现搬运，
 * 不改调用时序、不改后端请求体、不改 i18n key。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { useLanguage } from '../../LanguageContext';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import {
  createWorkbenchDomain,
  deleteWorkbenchDomain,
  deprecateWorkbenchDomain,
  publishWorkbenchDomain,
  reassignObjectDomain,
  updateWorkbenchDomain,
} from '../../../services/ontologyApi';
import { normalizeDomainCode } from './domainColors';

interface UseDomainManagerOptions {
  domains: OntologyDomain[];
  allObjectTypes: ObjectType[];
  selectedDomainId: string | null;
  onSelectDomainId: (id: string | null) => void;
  onUpdateDomains: (domains: OntologyDomain[]) => void;
  onUpdateObjectTypes: (objects: ObjectType[]) => void;
  /** T8: 域 CRUD/workflow 结果 toast（由 Layout 提供 showToast） */
  onToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

export function useDomainManager({
  domains,
  allObjectTypes,
  selectedDomainId,
  onSelectDomainId,
  onUpdateDomains,
  onUpdateObjectTypes,
  onToast,
}: UseDomainManagerOptions) {
  const { t } = useLanguage();

  const [showDomainModal, setShowDomainModal] = useState(false);
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);
  const [saving, setSaving] = useState(false);

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
    setFormId(domain.code || domain.id);
    setFormName(domain.displayName);
    setFormDesc(domain.description || '');
    setFormColor(domain.color);
    const assigned = allObjectTypes.filter(ot => ot.domainId === domain.id).map(ot => ot.id);
    setFormAssignedObjects(assigned);
    setFormError('');
    setShowDomainModal(true);
  };

  /** T8: 删除域 → 先打后端，成功后本地移除 + 受影响对象置未分类，失败 toast 不盲改本地 */
  const handleDeleteDomain = (domainId: string) => {
    const targetDomain = domains.find(d => d.id === domainId);
    if (!targetDomain) return;

    if (!window.confirm(t('ow.msg.confirmDeleteDomain').replace('{name}', targetDomain.displayName))) {
      return;
    }

    deleteWorkbenchDomain(targetDomain.code || targetDomain.id)
      .then(() => {
        const updatedDomains = domains.filter(d => d.id !== domainId);
        onUpdateDomains(updatedDomains);
        const updatedObjects = allObjectTypes.map(ot => (ot.domainId === domainId ? { ...ot, domainId: undefined } : ot));
        onUpdateObjectTypes(updatedObjects);
        if (selectedDomainId === domainId) {
          onSelectDomainId(null);
        }
        onToast?.('success', t('ow.domain.deleted').replace('{name}', targetDomain.displayName));
      })
      .catch((e: unknown) => {
        onToast?.('error', t('ow.domain.delete_failed').replace('{error}', String((e as { message?: string } | undefined)?.message || e)));
      });
  };

  /**
   * T8: 创建/编辑域 → 打后端 POST/PUT，成功后本地同步 domains + 对象归属。
   * 校验：code 必填且唯一、name 必填（i18n toast，停留弹窗）。
   */
  const handleSaveDomain = (e: React.FormEvent) => {
    e.preventDefault();
    if (saving) return;
    setFormError('');

    const code = normalizeDomainCode(formId);
    if (!code) {
      setFormError(t('ow.domain.validation.code_required'));
      return;
    }
    if (!formName.trim()) {
      setFormError(t('ow.domain.validation.name_required'));
      return;
    }
    if (!editingDomain && domains.some(d => (d.code || d.id) === code)) {
      setFormError(t('ow.domain.validation.code_dup').replace('{code}', code));
      return;
    }

    const savedName = formName.trim();
    const savedDescription = formDesc.trim();

    setSaving(true);
    const savePromise = editingDomain
      ? updateWorkbenchDomain(editingDomain.code || editingDomain.id, {
          code,
          name: savedName,
          description: savedDescription,
        })
      : createWorkbenchDomain({
          code,
          name: savedName,
          description: savedDescription,
        });

    savePromise
      .then((vo) => {
        const savedDomain: OntologyDomain = {
          // id 取域表主键（与实体表 domain_id 外键口径一致），code 单独保留供删除/更新端点使用
          id: vo?.id || code,
          code: vo?.code || code,
          displayName: vo?.name || savedName,
          description: vo?.description || savedDescription,
          color: formColor,
          status: vo?.status,
        };
        let newDomains: OntologyDomain[];
        if (editingDomain) {
          newDomains = domains.map(d => d.id === editingDomain.id ? savedDomain : d);
        } else {
          newDomains = [...domains, savedDomain];
        }
        onUpdateDomains(newDomains);

        // 对象归属：本地即时映射 + 后端 best-effort reassign（新增绑定逐对象 PUT）
        // 本地 domainId 用域主键（与实体表 domain_id 口径一致）；PUT body 用 domainCode（后端两者皆可解析）
        const assignedSet = new Set(formAssignedObjects);
        const savedDomainKey = savedDomain.id;
        const updatedObjects = allObjectTypes.map(ot => {
          const shouldHave = assignedSet.has(ot.id);
          if (shouldHave && (ot.domainId || undefined) !== savedDomainKey) {
            reassignObjectDomain(ot.id, { domainCode: code }).catch((rErr: unknown) => {
              console.warn('T8 reassignObjectDomain failed:', ot.id, (rErr as { message?: string } | undefined)?.message || rErr);
            });
          }
          if (shouldHave) return { ...ot, domainId: savedDomainKey };
          if (ot.domainId === savedDomainKey) return { ...ot, domainId: undefined };
          return ot;
        });
        onUpdateObjectTypes(updatedObjects);

        setShowDomainModal(false);
        setEditingDomain(null);
        setFormAssignedObjects([]);
        onToast?.('success', (editingDomain ? t('ow.domain.updated') : t('ow.domain.created')).replace('{name}', savedName));
      })
      .catch((err: unknown) => {
        const msg = String((err as { message?: string } | undefined)?.message || err || '');
        // 后端 code 唯一约束 (ONT-009) → 本地 i18n 提示
        if (msg.toLowerCase().includes('ont-009') || msg.toLowerCase().includes('already exists')) {
          setFormError(t('ow.domain.validation.code_dup').replace('{code}', code));
        } else {
          setFormError(t('ow.domain.save_failed').replace('{error}', msg));
        }
      })
      .finally(() => setSaving(false));
  };

  /** T8: 发布/废弃 — 经后端 PUT status（无独立端点），成功后回刷本地 status */
  const handleStatusChange = (domain: OntologyDomain, target: 'Published' | 'Deprecated') => {
    const call = target === 'Published' ? publishWorkbenchDomain : deprecateWorkbenchDomain;
    call(domain.code || domain.id)
      .then((vo) => {
        onUpdateDomains(domains.map(d => d.id === domain.id ? { ...d, status: vo?.status || target, code: vo?.code || d.code, displayName: vo?.name || d.displayName } : d));
        onToast?.('success', t(target === 'Published' ? 'ow.domain.published' : 'ow.domain.deprecated').replace('{name}', domain.displayName));
      })
      .catch((e: unknown) => {
        onToast?.('error', t('ow.domain.status_failed').replace('{error}', String((e as { message?: string } | undefined)?.message || e)));
      });
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
    saving,
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
    handleStatusChange,
    toggleObjectAssignment,
  };
}
