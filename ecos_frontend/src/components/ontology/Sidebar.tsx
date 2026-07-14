/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { AlertCircle, BookOpen, Box, Check, ChevronDown, ChevronRight, ChevronUp, Code, Database, Edit, GitMerge, Layers, LayoutDashboard, Plus, PlusCircle, Tag, Trash2, X, Zap } from 'lucide-react';
// Dynamic icon resolver for runtime icon names (from ceos_new)
import * as LucideIcons from 'lucide-react';
function DynamicIcon({ name, size = 14, className }: { name: string; size?: number; className?: string }) {
  const IconComponent = (LucideIcons as any)[name] || LucideIcons.HelpCircle;
  return <IconComponent size={size} className={className} />;
}


import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, FunctionType, OntologyDomain } from '../../types/ontology';
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
  const [showDomainModal, setShowDomainModal] = useState(false);
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);
  
  // Modal states
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formColor, setFormColor] = useState('blue');
  const [formAssignedObjects, setFormAssignedObjects] = useState<string[]>([]);
  const [formError, setFormError] = useState('');

  const getDomainColorText = (color: string) => {
    switch (color) {
      case 'blue': return 'text-blue-500';
      case 'emerald': return 'text-emerald-500';
      case 'amber': return 'text-amber-500';
      case 'purple': return 'text-purple-500';
      case 'rose': return 'text-rose-500';
      case 'indigo': return 'text-indigo-500';
      case 'slate': return 'text-slate-500';
      default: return 'text-slate-500';
    }
  };

  const getDomainColorDotClass = (color: string) => {
    switch (color) {
      case 'blue': return 'bg-blue-500';
      case 'emerald': return 'bg-emerald-500';
      case 'amber': return 'bg-amber-500';
      case 'purple': return 'bg-purple-500';
      case 'rose': return 'bg-rose-500';
      case 'indigo': return 'bg-indigo-500';
      case 'slate': return 'bg-slate-500';
      default: return 'bg-slate-500';
    }
  };

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

  const selectedDomain = domains.find(d => d.id === selectedDomainId);

  return (
    <aside className="w-64 bg-slate-100 border-r border-slate-200 flex flex-col h-full select-none shrink-0 text-xs">
      
      {/* Overview Button & Dropdown Selector */}
      <div className="p-3 border-b border-slate-200 bg-white space-y-2">
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
                  ? 'bg-slate-900 text-white border-slate-900 shadow-sm'
                  : 'text-slate-700 hover:bg-slate-50 border-slate-200'
              }`}
            >
              <div className="flex items-center gap-1.5 truncate">
                <DynamicIcon name={selectedDomain ? "Layers" : "LayoutDashboard"} size={13} className={selectedDomain ? getDomainColorText(selectedDomain.color) : 'text-blue-500'} />
                <span className="truncate">{selectedDomain ? selectedDomain.displayName.split(' (')[0] : '本体全景与总览'}</span>
              </div>
              <ChevronDown size={12} className="opacity-60" />
            </button>

            {/* Dropdown Menu */}
            {showDomainDropdown && (
              <div className="absolute top-10 left-0 right-0 bg-white border border-slate-200 rounded-lg shadow-xl py-1 z-40 max-h-64 overflow-y-auto divide-y divide-slate-100">
                {/* 1. Global Panorama Option */}
                <div
                  onClick={() => {
                    onSelectDomainId(null);
                    onSelectCategory('overview', null);
                    setShowDomainDropdown(false);
                  }}
                  className={`px-2.5 py-2 text-xs flex items-center justify-between cursor-pointer transition-colors ${
                    selectedDomainId === null ? 'bg-slate-50 text-slate-950 font-bold' : 'text-slate-600 hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center gap-1.5">
                    <LayoutDashboard size={12} className="text-blue-500" />
                    <span>全局全景 (All)</span>
                  </div>
                  {selectedDomainId === null && <Check size={11} className="text-blue-600" />}
                </div>

                {/* 2. Domains Options with Edit/Delete */}
                {domains.map(d => {
                  const isSelected = selectedDomainId === d.id;
                  const count = allObjectTypes.filter(ot => ot.domainId === d.id).length;
                  return (
                    <div
                      key={d.id}
                      className={`px-2.5 py-1.5 text-xs flex items-center justify-between cursor-pointer group transition-colors ${
                        isSelected ? 'bg-slate-50 text-slate-950 font-bold' : 'text-slate-600 hover:bg-slate-50'
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
                        <span className="text-[9px] text-slate-400 font-mono">({count})</span>
                      </div>
                      
                      {/* Edit/Delete Icons */}
                      <div className="flex items-center gap-0.5 shrink-0 opacity-40 group-hover:opacity-100 transition-opacity" onClick={e => e.stopPropagation()}>
                        <button
                          onClick={() => {
                            handleStartEditDomain(d);
                            setShowDomainDropdown(false);
                          }}
                          className="p-1 hover:bg-slate-100 text-slate-500 hover:text-slate-800 rounded transition-colors"
                          title="修改业务域"
                        >
                          <Edit size={11} />
                        </button>
                        <button
                          onClick={() => {
                            handleDeleteDomain(d.id);
                            setShowDomainDropdown(false);
                          }}
                          className="p-1 hover:bg-red-50 text-slate-500 hover:text-red-600 rounded transition-colors"
                          title="删除业务域"
                        >
                          <Trash2 size={11} />
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
            <Plus size={14} />
          </button>
        </div>

        {/* Core Sub-view Switcher inside Workbench */}
        <div className="flex bg-slate-100 p-0.5 rounded-lg border border-slate-200/50 mt-2">
          <button
            onClick={() => {
              onSelectCategory('overview', null);
            }}
            className="w-full py-1.5 rounded-md text-[10px] font-bold flex items-center justify-center gap-1 transition-all bg-white text-slate-900 shadow-xs cursor-pointer"
          >
            <LayoutDashboard size={11} className="text-blue-600" />
            <span>配置全景</span>
          </button>
        </div>
      </div>

      {/* Accordions List */}
      <div className="flex-1 overflow-y-auto py-3 space-y-1">
        
        {/* 1. OBJECT TYPES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('object')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.object ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>对象类型 (Object Types)</span>
            </div>
            <span>{objectTypes.length}</span>
          </button>
          {expanded.object && (
            <div className="px-2 space-y-0.5">
              {objectTypes.map(ot => {
                const isActive = selectedCategory === 'object' && selectedId === ot.id;
                return (
                  <button
                    key={ot.id}
                    onClick={() => onSelectCategory('object', ot.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className={`p-0.5 rounded border ${isActive ? 'bg-blue-100 border-blue-300 text-blue-800' : 'bg-white border-slate-200 text-slate-500'}`}>
                        <DynamicIcon name={ot.icon} size={11} />
                      </span>
                      <span className="truncate">{ot.displayName}</span>
                    </div>
                    <span className="text-[9px] font-mono opacity-65 uppercase">{ot.id}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 2. LINK TYPES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('link')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.link ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>链接关系 (Link Types)</span>
            </div>
            <span>{linkTypes.length}</span>
          </button>
          {expanded.link && (
            <div className="px-2 space-y-0.5">
              {linkTypes.map(lt => {
                const isActive = selectedCategory === 'link' && selectedId === lt.id;
                return (
                  <button
                    key={lt.id}
                    onClick={() => onSelectCategory('link', lt.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-slate-400">
                        <GitMerge size={11} />
                      </span>
                      <span className="truncate">{lt.displayName}</span>
                    </div>
                    <span className="text-[9px] font-mono opacity-50 font-bold">{lt.cardinality}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 3. ACTION TYPES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('action')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.action ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>操作类型 (Action Types)</span>
            </div>
            <span>{actionTypes.length}</span>
          </button>
          {expanded.action && (
            <div className="px-2 space-y-0.5">
              {actionTypes.map(at => {
                const isActive = selectedCategory === 'action' && selectedId === at.id;
                return (
                  <button
                    key={at.id}
                    onClick={() => onSelectCategory('action', at.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-amber-500">
                        <Zap size={11} className="fill-amber-400/30" />
                      </span>
                      <span className="truncate">{at.displayName}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 3.5. FUNCTION TYPES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('function')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.function ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>逻辑函数 (Functions)</span>
            </div>
            <span>{functionTypes.length}</span>
          </button>
          {expanded.function && (
            <div className="px-2 space-y-0.5">
              {functionTypes.map(fn => {
                const isActive = selectedCategory === 'function' && selectedId === fn.id;
                return (
                  <button
                    key={fn.id}
                    onClick={() => onSelectCategory('function', fn.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-violet-500">
                        <Code size={11} />
                      </span>
                      <span className="truncate">{fn.displayName}</span>
                    </div>
                    <span className="text-[9px] font-mono opacity-50 uppercase">{fn.returnType}</span>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 4. INTERFACE TYPES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('interface')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.interface ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>接口规范 (Interfaces)</span>
            </div>
            <span>{interfaces.length}</span>
          </button>
          {expanded.interface && (
            <div className="px-2 space-y-0.5">
              {interfaces.map(it => {
                const isActive = selectedCategory === 'interface' && selectedId === it.id;
                return (
                  <button
                    key={it.id}
                    onClick={() => onSelectCategory('interface', it.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-indigo-500">
                        <Layers size={11} />
                      </span>
                      <span className="truncate">{it.displayName}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 5. SHARED PROPERTIES */}
        <div className="space-y-0.5">
          <button
            onClick={() => toggleExpand('shared_property')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.shared_property ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>共享属性 (Shared Properties)</span>
            </div>
            <span>{sharedProperties.length}</span>
          </button>
          {expanded.shared_property && (
            <div className="px-2 space-y-0.5">
              {sharedProperties.map(sp => {
                const isActive = selectedCategory === 'shared_property' && selectedId === sp.id;
                return (
                  <button
                    key={sp.id}
                    onClick={() => onSelectCategory('shared_property', sp.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-teal-500">
                        <Tag size={11} />
                      </span>
                      <span className="truncate">{sp.displayName}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* 6. RAW DATASETS */}
        <div className="space-y-0.5 border-t border-slate-200/60 pt-2 mt-2">
          <button
            onClick={() => toggleExpand('dataset')}
            className="w-full py-1.5 px-3 flex items-center justify-between text-slate-500 hover:text-slate-800 font-semibold uppercase tracking-wider text-[10px]"
          >
            <div className="flex items-center gap-1">
              <DynamicIcon name={expanded.dataset ? "ChevronDown" : "ChevronRight"} size={12} />
              <span>原始数据集 (Datasets)</span>
            </div>
            <span>{datasets.length}</span>
          </button>
          {expanded.dataset && (
            <div className="px-2 space-y-0.5">
              {datasets.map(ds => {
                const isActive = selectedCategory === 'dataset' && selectedId === ds.id;
                return (
                  <button
                    key={ds.id}
                    onClick={() => onSelectCategory('dataset', ds.id)}
                    className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                        : 'text-slate-600 hover:bg-slate-200/50'
                    }`}
                  >
                    <div className="flex items-center gap-2 truncate">
                      <span className="text-slate-400">
                        <Database size={11} />
                      </span>
                      <span className="truncate font-mono text-[10px]">{ds.name}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* Bottom Action bar */}
      <div className="p-3 border-t border-slate-200 bg-white relative">
        <button
          onClick={() => setShowCreateDropdown(!showCreateDropdown)}
          className="w-full bg-slate-900 hover:bg-slate-800 text-white font-medium py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-colors shadow-xs"
        >
          <PlusCircle size={14} />
          <span>新建本体元素</span>
          <DynamicIcon name={showCreateDropdown ? "ChevronDown" : "ChevronUp"} size={12} />
        </button>

        {/* Create Dropdown */}
        {showCreateDropdown && (
          <div className="absolute bottom-14 left-3 right-3 bg-white border border-slate-200 rounded-lg shadow-lg py-1 z-30 divide-y divide-slate-100">
            <button
              onClick={() => {
                onCreateNew('object');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-blue-500">
                <Box size={13} />
              </span>
              <span>新建对象类型 (Object Type)</span>
            </button>
            <button
              onClick={() => {
                onCreateNew('link');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-slate-400">
                <GitMerge size={13} />
              </span>
              <span>新建链接关系 (Link Type)</span>
            </button>
            <button
              onClick={() => {
                onCreateNew('action');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-amber-500">
                <Zap size={13} />
              </span>
              <span>新建操作类型 (Action Type)</span>
            </button>
            <button
              onClick={() => {
                onCreateNew('interface');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-indigo-500">
                <Layers size={13} />
              </span>
              <span>新建接口定义 (Interface)</span>
            </button>
            <button
              onClick={() => {
                onCreateNew('shared_property');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-teal-500">
                <Tag size={13} />
              </span>
              <span>新建共享属性 (Shared Property)</span>
            </button>
            <button
              onClick={() => {
                onCreateNew('function');
                setShowCreateDropdown(false);
              }}
              className="w-full text-left px-3 py-2 text-slate-700 hover:bg-slate-50 flex items-center gap-2 transition-colors"
            >
              <span className="text-violet-500">
                <Code size={13} />
              </span>
              <span>新建逻辑函数 (Function)</span>
            </button>
          </div>
        )}
      </div>

      {/* 业务划分域模态对话框 */}
      {showDomainModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs">
          <div className="bg-white rounded-xl shadow-2xl border border-slate-200 w-full max-w-md overflow-hidden flex flex-col max-h-[85vh]">
            
            {/* Modal Header */}
            <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="p-1 rounded bg-blue-100 text-blue-600">
                  <Layers size={14} />
                </span>
                <h3 className="text-sm font-bold text-slate-800">
                  {editingDomain ? '编辑业务域' : '新建业务域'}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => {
                  setShowDomainModal(false);
                  setEditingDomain(null);
                }}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X size={16} />
              </button>
            </div>

            {/* Modal Form */}
            <form onSubmit={handleSaveDomain} className="flex-1 overflow-y-auto p-4 space-y-4">
              {formError && (
                <div className="p-2.5 bg-red-50 text-red-600 border border-red-200 rounded-lg text-xs font-semibold flex items-center gap-2">
                  <AlertCircle size={13} />
                  <span>{formError}</span>
                </div>
              )}

              {/* ID Input (Only shown on Create) */}
              <div className="space-y-1">
                <label className="block text-slate-600 font-semibold text-[11px]">
                  业务域标识 (ID/Key) <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  disabled={!!editingDomain}
                  value={formId}
                  onChange={e => setFormId(e.target.value)}
                  placeholder="例如: customer_domain (英文/数字/下划线)"
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-hidden focus:border-blue-500 font-mono text-xs bg-slate-50/50 disabled:bg-slate-100 disabled:text-slate-500"
                  required
                />
              </div>

              {/* Display Name Input */}
              <div className="space-y-1">
                <label className="block text-slate-600 font-semibold text-[11px]">
                  业务域名称 (Display Name) <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="例如: 客户域"
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-hidden focus:border-blue-500 text-xs"
                  required
                />
              </div>

              {/* Description Input */}
              <div className="space-y-1">
                <label className="block text-slate-600 font-semibold text-[11px]">
                  描述 (Description)
                </label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder="对该业务分级域的业务范围和职责进行说明"
                  rows={2}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-hidden focus:border-blue-500 text-xs resize-none"
                />
              </div>

              {/* Color Theme Selector */}
              <div className="space-y-1.5">
                <label className="block text-slate-600 font-semibold text-[11px]">
                  视觉主题色 (Color Accent)
                </label>
                <div className="flex flex-wrap gap-2">
                  {['blue', 'emerald', 'amber', 'purple', 'rose', 'indigo', 'slate'].map(color => {
                    const isSelected = formColor === color;
                    return (
                      <button
                        key={color}
                        type="button"
                        onClick={() => setFormColor(color)}
                        className={`w-6 h-6 rounded-full border-2 flex items-center justify-center transition-all ${
                          isSelected ? 'border-slate-800 scale-110 shadow-sm' : 'border-transparent hover:scale-105'
                        }`}
                        style={{ backgroundColor: 
                          color === 'blue' ? '#3b82f6' :
                          color === 'emerald' ? '#10b981' :
                          color === 'amber' ? '#f59e0b' :
                          color === 'purple' ? '#8b5cf6' :
                          color === 'rose' ? '#f43f5e' :
                          color === 'indigo' ? '#6366f1' : '#64748b'
                        }}
                      >
                        {isSelected && <Check size={12} className="text-white font-bold" />}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Assign Object Types Checklist */}
              <div className="space-y-1.5">
                <label className="block text-slate-600 font-semibold flex justify-between items-center text-[11px]">
                  <span>包含的对象类型 ({formAssignedObjects.length})</span>
                  <span className="text-[9px] text-slate-400 font-normal">多选指派</span>
                </label>
                <div className="border border-slate-200 rounded-lg max-h-36 overflow-y-auto p-1 bg-slate-50/30 divide-y divide-slate-150">
                  {allObjectTypes.map(ot => {
                    const isChecked = formAssignedObjects.includes(ot.id);
                    return (
                      <div
                        key={ot.id}
                        onClick={() => toggleObjectAssignment(ot.id)}
                        className="flex items-center gap-2 py-1 px-1.5 hover:bg-slate-100 rounded-md cursor-pointer text-xs"
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => {}} // Handle on parent div click
                          className="rounded border-slate-300 text-blue-600 focus:ring-blue-500 h-3 w-3 pointer-events-none"
                        />
                        <span className={`p-0.5 rounded border bg-white text-slate-500`}>
                          <DynamicIcon name={ot.icon} size={11} />
                        </span>
                        <div className="flex-1 min-w-0">
                          <p className="font-semibold text-slate-700 truncate text-[11px]">{ot.displayName}</p>
                        </div>
                        <span className="text-[9px] font-mono text-slate-400 uppercase">{ot.id}</span>
                      </div>
                    );
                  })}
                  {allObjectTypes.length === 0 && (
                    <div className="p-4 text-center text-slate-400">
                      暂无对象类型可供指派
                    </div>
                  )}
                </div>
              </div>

              {/* Footer Actions */}
              <div className="pt-3 border-t border-slate-200 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setShowDomainModal(false);
                    setEditingDomain(null);
                  }}
                  className="px-3 py-1.5 border border-slate-200 rounded-lg hover:bg-slate-50 text-slate-700 transition-colors font-semibold cursor-pointer text-xs"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-xs"
                >
                  保存
                </button>
              </div>
            </form>

          </div>
        </div>
      )}

      {/* 📖 术语库快速入口 */}
      <div className="border-t border-slate-200 px-3 py-3">
        <button
          onClick={() => onSelectCategory('glossary', null)}
          className={`w-full flex items-center gap-2 py-2 px-3 rounded-lg font-semibold text-xs transition-colors ${
            selectedCategory === 'glossary'
              ? 'bg-blue-50 text-blue-700 border-l-2 border-blue-600'
              : 'text-slate-600 hover:bg-slate-100'
          }`}
        >
          <BookOpen size={14} />
          <span>术语库</span>
        </button>
      </div>

    </aside>
  );
}
