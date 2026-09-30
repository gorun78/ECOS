/**
 * DomainDropdownMenu — 业务划分域下拉面板（全局透视 + 域列表 + 编辑/发布/废弃/删除）
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理）：
 * JSX 结构、className、点击回调内的调用顺序均与原实现逐字一致。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Archive, Check, Edit, Layers, LayoutDashboard, Rocket, Trash2 } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import { useTheme } from '../../ThemeContext';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import { getDomainColorDotClass } from './domainColors';

type SidebarCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function' | 'glossary';

interface DomainDropdownMenuProps {
  domains: OntologyDomain[];
  allObjectTypes: ObjectType[];
  selectedDomainId: string | null;
  statusMenuFor: string | null;
  setStatusMenuFor: (id: string | null) => void;
  setShowDomainDropdown: (open: boolean) => void;
  onSelectDomainId: (id: string | null) => void;
  onSelectCategory: (category: SidebarCategory, id: string | null) => void;
  onEditDomain: (domain: OntologyDomain) => void;
  onDeleteDomain: (domainId: string) => void;
  onStatusChange: (domain: OntologyDomain, target: 'Published' | 'Deprecated') => void;
}

export default function DomainDropdownMenu({
  domains,
  allObjectTypes,
  selectedDomainId,
  statusMenuFor,
  setStatusMenuFor,
  setShowDomainDropdown,
  onSelectDomainId,
  onSelectCategory,
  onEditDomain,
  onDeleteDomain,
  onStatusChange,
}: DomainDropdownMenuProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`absolute top-10 left-0 right-0 ${styles.cardBg} border ${styles.sidebarBorder} rounded-lg shadow-xl py-1 z-40 max-h-64 overflow-y-auto divide-y ${styles.divider}`}>
      {/* 1. Global Panorama Option */}
      <div
        onClick={() => {
          onSelectDomainId(null);
          onSelectCategory('overview', null);
          setShowDomainDropdown(false);
          setStatusMenuFor(null);
        }}
        className={`px-2.5 py-2 text-xs flex items-center justify-between cursor-pointer transition-colors ${
          selectedDomainId === null ? `${styles.sidebarActiveBg} ${styles.cardText} font-bold` : `${styles.sidebarText} ${styles.sidebarHoverBg}`
        }`}
      >
        <div className="flex items-center gap-1.5">
          <LayoutDashboard size={12} className="text-blue-500" />
          <span>{t('ow.sidebar.globalPanorama')}</span>
        </div>
        {selectedDomainId === null && <Check size={11} className="text-blue-600" />}
      </div>

      {/* 2. Domains Options with Edit/Publish/Deprecate/Delete */}
      {domains.map(d => {
        const isSelected = selectedDomainId === d.id;
        const count = allObjectTypes.filter(ot => ot.domainId === d.id).length;
        return (
          <div
            key={d.id}
            className={`px-2.5 py-1.5 text-xs flex items-center justify-between cursor-pointer group transition-colors ${
              isSelected ? `${styles.sidebarActiveBg} ${styles.cardText} font-bold` : `${styles.sidebarText} ${styles.sidebarHoverBg}`
            }`}
            onClick={() => {
              onSelectDomainId(d.id);
              onSelectCategory('overview', null);
              setShowDomainDropdown(false);
              setStatusMenuFor(null);
            }}
          >
            <div className="flex items-center gap-1.5 min-w-0 flex-1">
              <span className={`w-1.5 h-1.5 rounded-full ${getDomainColorDotClass(d.color)}`} />
              <span className="truncate" title={d.displayName}>{d.displayName}</span>
              <span className={`text-[9px] ${styles.muted} font-mono`}>({count})</span>
            </div>

            {/* Edit / Publish·Deprecate / Delete Icons (T8: 新增状态菜单) */}
            <div className="flex items-center gap-0.5 shrink-0 opacity-40 group-hover:opacity-100 transition-opacity" onClick={e => e.stopPropagation()}>
              <button
                onClick={() => {
                  onEditDomain(d);
                  setShowDomainDropdown(false);
                  setStatusMenuFor(null);
                }}
                className={`p-1 ${styles.sidebarHoverBg} ${styles.muted} hover:${styles.cardText} rounded transition-colors`}
                title={t('ow.btn.editDomain')}
              >
                <Edit size={11} />
              </button>
              <div className="relative">
                <button
                  onClick={() => setStatusMenuFor(statusMenuFor === d.id ? null : d.id)}
                  className={`p-1 ${styles.sidebarHoverBg} ${styles.muted} rounded transition-colors`}
                  title={t('ow.btn.domainStatus')}
                >
                  <Layers size={11} />
                </button>
                {statusMenuFor === d.id && (
                  <div className={`absolute right-0 top-6 w-32 ${styles.cardBg} border ${styles.sidebarBorder} rounded-lg shadow-xl py-1 z-50`}>
                    <button
                      onClick={() => {
                        setStatusMenuFor(null);
                        setShowDomainDropdown(false);
                        onStatusChange(d, 'Published');
                      }}
                      className="w-full text-left px-2.5 py-1.5 flex items-center gap-1.5 text-emerald-600 transition-colors"
                    >
                      <Rocket size={11} />
                      <span>{t('ow.domain.publish')}</span>
                    </button>
                    <button
                      onClick={() => {
                        setStatusMenuFor(null);
                        setShowDomainDropdown(false);
                        onStatusChange(d, 'Deprecated');
                      }}
                      className="w-full text-left px-2.5 py-1.5 flex items-center gap-1.5 text-amber-600 transition-colors"
                    >
                      <Archive size={11} />
                      <span>{t('ow.domain.deprecate')}</span>
                    </button>
                  </div>
                )}
              </div>
              <button
                onClick={() => {
                  onDeleteDomain(d.id);
                  setShowDomainDropdown(false);
                  setStatusMenuFor(null);
                }}
                className={`p-1 hover:bg-red-50 ${styles.muted} hover:text-red-600 rounded transition-colors`}
                title={t('ow.btn.deleteDomain')}
              >
                <Trash2 size={11} />
              </button>
            </div>
          </div>
        );
      })}
      {domains.length === 0 && (
        <div className={`px-2.5 py-2 text-[10px] ${styles.muted}`}>{t('ow.domain.empty')}</div>
      )}
    </div>
  );
}
