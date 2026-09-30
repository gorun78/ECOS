/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * Sidebar — 本体工作台左侧栏（业务划分域 + 本体元素分节 + 新建元素）
 *
 * H6-T4 组件行数治理：域配色纯工具 / 域 CRUD 有状态逻辑 / 域下拉面板 / 域表单弹窗 /
 * 元素分节列表 / 底部新建菜单已机械抽取至 ./sidebar/* ，本文件保留为组合根。
 */

import React, { useState } from 'react';
import { BookOpen, ChevronDown, LayoutDashboard, Plus } from 'lucide-react';
import { useLanguage } from '../LanguageContext';
import { useTheme } from '../ThemeContext';
import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, FunctionType, OntologyDomain } from '../../types/ontology';
import DynamicIcon from './sidebar/SidebarDynamicIcon';
import { getDomainColorText } from './sidebar/domainColors';
import { useDomainManager } from './sidebar/useDomainManager';
import DomainDropdownMenu from './sidebar/DomainDropdownMenu';
import DomainFormModal from './sidebar/DomainFormModal';
import SidebarElementSections from './sidebar/SidebarElementSections';
import CreateElementMenu from './sidebar/CreateElementMenu';

type SidebarCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function' | 'glossary';

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

  selectedCategory: 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function' | 'glossary';
  selectedId: string | null;

  onSelectCategory: (category: SidebarCategory, id: string | null) => void;
  onCreateNew: (type: 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function') => void;
  /** T8: 域 CRUD/workflow 结果 toast（由 Layout 提供 showToast） */
  onToast?: (type: 'success' | 'info' | 'error', message: string) => void;
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
  onCreateNew,
  onToast
}: SidebarProps) {
  const { t } = useLanguage();
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
  const [statusMenuFor, setStatusMenuFor] = useState<string | null>(null);

  const domainManager = useDomainManager({
    domains,
    allObjectTypes,
    selectedDomainId,
    onSelectDomainId,
    onUpdateDomains,
    onUpdateObjectTypes,
    onToast,
  });

  const selectedDomain = domains.find(d => d.id === selectedDomainId);

  return (
    <aside className={`w-64 ${styles.sidebarBg} border-r ${styles.sidebarBorder} flex flex-col h-full select-none shrink-0 text-xs`}>

      {/* Overview Button & Dropdown Selector */}
      <div className={`p-3 border-b ${styles.sidebarBorder} ${styles.cardBg} space-y-2`}>
        <div className="flex items-center gap-1.5">
          {/* Custom Dropdown Trigger */}
          <div className="relative flex-1">
            <button
              onClick={() => {
                setShowDomainDropdown(!showDomainDropdown);
                setStatusMenuFor(null);
                onSelectCategory('overview', null);
              }}
              className={`w-full py-2 px-3 rounded-lg flex items-center justify-between font-semibold transition-all text-xs border ${
                selectedCategory === 'overview'
                  ? `${styles.accentBg} text-white ${styles.accentBorder} shadow-sm`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg} ${styles.sidebarBorder}`
              }`}
            >
              <div className="flex items-center gap-1.5 truncate">
                <DynamicIcon name={selectedDomain ? "Layers" : "LayoutDashboard"} size={13} className={selectedDomain ? getDomainColorText(selectedDomain.color) : 'text-blue-500'} />
                <span className="truncate">{selectedDomain ? selectedDomain.displayName.split(' (')[0] : t('ow.sidebar.overview')}</span>
              </div>
              <ChevronDown size={12} className="opacity-60" />
            </button>

            {/* Dropdown Menu */}
            {showDomainDropdown && (
              <DomainDropdownMenu
                domains={domains}
                allObjectTypes={allObjectTypes}
                selectedDomainId={selectedDomainId}
                statusMenuFor={statusMenuFor}
                setStatusMenuFor={setStatusMenuFor}
                setShowDomainDropdown={setShowDomainDropdown}
                onSelectDomainId={onSelectDomainId}
                onSelectCategory={onSelectCategory}
                onEditDomain={domainManager.handleStartEditDomain}
                onDeleteDomain={domainManager.handleDeleteDomain}
                onStatusChange={domainManager.handleStatusChange}
              />
            )}
          </div>

          {/* Plus button to add domain */}
          <button
            onClick={domainManager.handleStartAddDomain}
            className={`p-2 ${styles.sidebarActiveBg} ${styles.sidebarHoverBg} ${styles.accentText} border ${styles.accentBorder} rounded-lg hover:shadow-xs transition-all cursor-pointer shrink-0`}
            title={t('ow.btn.addDomain')}
          >
            <Plus size={14} />
          </button>
        </div>

        {/* Core Sub-view Switcher inside Workbench */}
        <div className={`flex ${styles.sidebarBg} p-0.5 rounded-lg border ${styles.sidebarBorder} mt-2`}>
          <button
            onClick={() => {
              onSelectCategory('overview', null);
            }}
            className={`w-full py-1.5 rounded-md text-[10px] font-bold flex items-center justify-center gap-1 transition-all ${styles.cardBg} ${styles.cardText} shadow-xs cursor-pointer`}
          >
            <LayoutDashboard size={11} className="text-blue-600" />
            <span>{t('ow.sidebar.configPanorama')}</span>
          </button>
        </div>
      </div>

      {/* Accordions List */}
      <SidebarElementSections
        objectTypes={objectTypes}
        linkTypes={linkTypes}
        actionTypes={actionTypes}
        interfaces={interfaces}
        sharedProperties={sharedProperties}
        datasets={datasets}
        functionTypes={functionTypes}
        selectedCategory={selectedCategory}
        selectedId={selectedId}
        onSelectCategory={onSelectCategory}
        expanded={expanded}
        toggleExpand={toggleExpand}
      />

      {/* Bottom Action bar — T4: 替换原来的 白底/黑底 硬编码 → theme tokens */}
      <CreateElementMenu
        showCreateDropdown={showCreateDropdown}
        setShowCreateDropdown={setShowCreateDropdown}
        onCreateNew={onCreateNew}
      />

      {/* 业务划分域模态对话框 — T8: CRUD 已接后端 (OntologyDomainApiController) */}
      {domainManager.showDomainModal && (
        <DomainFormModal
          editingDomain={domainManager.editingDomain}
          allObjectTypes={allObjectTypes}
          saving={domainManager.saving}
          formId={domainManager.formId}
          formName={domainManager.formName}
          formDesc={domainManager.formDesc}
          formColor={domainManager.formColor}
          formAssignedObjects={domainManager.formAssignedObjects}
          formError={domainManager.formError}
          setFormId={domainManager.setFormId}
          setFormName={domainManager.setFormName}
          setFormDesc={domainManager.setFormDesc}
          setFormColor={domainManager.setFormColor}
          onClose={() => {
            domainManager.setShowDomainModal(false);
            domainManager.setEditingDomain(null);
          }}
          onSave={domainManager.handleSaveDomain}
          onToggleObject={domainManager.toggleObjectAssignment}
        />
      )}

      {/* 📖 术语（Glossary）快速入口 — T4: 原硬编码结构色已替换为 theme tokens */}
      <div className={`border-t ${styles.appBorder} px-3 py-3`}>
        <button
          onClick={() => onSelectCategory('glossary', null)}
          className={`w-full flex items-center gap-2 py-2 px-3 rounded-lg font-semibold text-xs transition-colors ${
            selectedCategory === 'glossary'
              ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'
              : `${styles.sidebarText} ${styles.sidebarHoverBg}`
          }`}
        >
          <BookOpen size={14} />
          <span>{t('ow.sidebar.glossary')}</span>
        </button>
      </div>

    </aside>
  );
}
