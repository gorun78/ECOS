/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, OntologyDomain } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { getDomainColorText, getDomainColorDotClass } from './domainColors';

interface DomainSelectorHeaderProps {
  selectedCategory: 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
  selectedDomain: OntologyDomain | undefined;
  selectedDomainId: string | null;
  domains: OntologyDomain[];
  allObjectTypes: ObjectType[];
  showDomainDropdown: boolean;
  setShowDomainDropdown: (value: boolean) => void;
  onSelectDomainId: (id: string | null) => void;
  onSelectCategory: (category: any, id: string | null) => void;
  handleStartAddDomain: () => void;
  handleStartEditDomain: (domain: OntologyDomain) => void;
  handleDeleteDomain: (domainId: string) => void;
}

export default function DomainSelectorHeader({
  selectedCategory,
  selectedDomain,
  selectedDomainId,
  domains,
  allObjectTypes,
  showDomainDropdown,
  setShowDomainDropdown,
  onSelectDomainId,
  onSelectCategory,
  handleStartAddDomain,
  handleStartEditDomain,
  handleDeleteDomain
}: DomainSelectorHeaderProps) {
  const { styles } = useTheme();

  return (
    <>
      {/* Overview Button & Dropdown Selector */}
      <div className={`p-3 border-b ${styles.sidebarBorder} ${styles.cardBg} space-y-2`}>
        <div className="flex items-center gap-1.5">
          {/* Custom Dropdown Trigger */}
          <div className="relative flex-1">
            <button
              onClick={() => {
                setShowDomainDropdown(!showDomainDropdown);
                onSelectCategory('overview', null);
              }}
              className={`w-full py-2 px-3 rounded-lg flex items-center justify-between font-semibold transition-all text-xs border ${
                selectedCategory === 'overview'
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} shadow-sm`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg} ${styles.sidebarBorder}`
              }`}
            >
              <div className="flex items-center gap-1.5 truncate">
                <LucideIcon name={selectedDomain ? "Layers" : "LayoutDashboard"} size={13} className={selectedDomain ? getDomainColorText(selectedDomain.color) : 'text-blue-500'} />
                <span className="truncate">{selectedDomain ? selectedDomain.displayName.split(' (')[0] : '本体全景与总览'}</span>
              </div>
              <LucideIcon name="ChevronDown" size={12} className="opacity-60" />
            </button>

            {/* Dropdown Menu */}
            {showDomainDropdown && (
              <div className={`absolute top-10 left-0 right-0 ${styles.cardBg} border ${styles.appBorder} rounded-lg shadow-xl py-1 z-40 max-h-64 overflow-y-auto ${styles.appBorder}`}>
                {/* 1. Global Panorama Option */}
                <div
                  onClick={() => {
                    onSelectDomainId(null);
                    onSelectCategory('overview', null);
                    setShowDomainDropdown(false);
                  }}
                  className={`px-2.5 py-2 text-xs flex items-center justify-between cursor-pointer transition-colors ${
                    selectedDomainId === null ? `${styles.sidebarHoverBg} ${styles.cardText} font-bold` : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                  }`}
                >
                  <div className="flex items-center gap-1.5">
                    <LucideIcon name="LayoutDashboard" size={12} className="text-blue-500" />
                    <span>全局全景 (All)</span>
                  </div>
                  {selectedDomainId === null && <LucideIcon name="Check" size={11} className="text-blue-600" />}
                </div>

                {/* 2. Domains Options with Edit/Delete */}
                {domains.map(d => {
                  const isSelected = selectedDomainId === d.id;
                  const count = allObjectTypes.filter(ot => ot.domainId === d.id).length;
                  return (
                    <div
                      key={d.id}
                      className={`px-2.5 py-1.5 text-xs flex items-center justify-between cursor-pointer group transition-colors ${
                        isSelected ? `${styles.sidebarHoverBg} ${styles.cardText} font-bold` : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                      }`}
                      onClick={() => {
                        onSelectDomainId(d.id);
                        onSelectCategory('overview', null);
                        setShowDomainDropdown(false);
                      }}
                    >
                      <div className="flex items-center gap-1.5 min-w-0 flex-1">
                        <span className={`w-1.5 h-1.5 rounded-full ${getDomainColorDotClass(d.color)}`} />
                        <span className="truncate" title={d.displayName}>{d.displayName}</span>
                        <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>({count})</span>
                      </div>

                      {/* Edit/Delete Icons */}
                      <div className="flex items-center gap-0.5 shrink-0 opacity-40 group-hover:opacity-100 transition-opacity" onClick={e => e.stopPropagation()}>
                        <button
                          onClick={() => {
                            handleStartEditDomain(d);
                            setShowDomainDropdown(false);
                          }}
                          className={`p-1 ${styles.sidebarHoverBg} ${styles.cardTextMuted} rounded transition-colors`}
                          title="修改业务域"
                        >
                          <LucideIcon name="Edit" size={11} />
                        </button>
                        <button
                          onClick={() => {
                            handleDeleteDomain(d.id);
                            setShowDomainDropdown(false);
                          }}
                          className={`p-1 hover:bg-red-50 ${styles.cardTextMuted} hover:text-red-600 rounded transition-colors`}
                          title="删除业务域"
                        >
                          <LucideIcon name="Trash2" size={11} />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Plus button to add domain */}
          <button
            onClick={handleStartAddDomain}
            className="p-2 bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200 rounded-lg hover:shadow-xs transition-all cursor-pointer shrink-0"
            title="添加业务分级域"
          >
            <LucideIcon name="Plus" size={14} />
          </button>
        </div>

        {/* Core Sub-view Switcher inside Workbench */}
        <div className={`flex ${styles.appBg} p-0.5 rounded-lg border ${styles.appBorder} mt-2`}>
          <button
            onClick={() => {
              onSelectCategory('overview', null);
            }}
            className={`w-full py-1.5 rounded-md text-[10px] font-bold flex items-center justify-center gap-1 transition-all ${styles.cardBg} ${styles.cardText} shadow-xs cursor-pointer`}
          >
            <LucideIcon name="LayoutDashboard" size={11} className="text-blue-600" />
            <span>配置全景</span>
          </button>
        </div>
      </div>
    </>
  );
}
