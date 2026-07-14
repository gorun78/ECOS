/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, OntologyDomain } from '../../types/ontology';
import OntologyGraph from './OntologyGraph';
import LucideIcon from './LucideIcon';

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
  
  // Tab/Filter States
  // Controlled by parent state

  // Domain Maintenance Form States
  const [editingDomain, setEditingDomain] = useState<OntologyDomain | null>(null);
  const [formId, setFormId] = useState('');
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formColor, setFormColor] = useState('blue');
  const [formAssignedObjects, setFormAssignedObjects] = useState<string[]>([]);
  const [isAddingNew, setIsAddingNew] = useState(false);
  const [formError, setFormError] = useState('');

  // Calculate total properties
  const totalPropsCount = objectTypes.reduce((acc, ot) => acc + ot.properties.length, 0);

  // Mock enterprise life cycle log
  const auditLogs = [
    { id: '1', time: '10分钟前', user: 'guorongxiao@gmail.com', action: '发布了本体版本 v1.2.4', detail: '同步了 飞行员 (Pilot) 接口绑定以及新增了多对多资质表关联映射。', type: 'publish' },
    { id: '2', time: '1小时前', user: 'guorongxiao@gmail.com', action: '更新对象属性', detail: '为 航班 (Flight) 对象新增了「计划起飞时间」和「计划到达时间」高精度时间戳。', type: 'edit' },
    { id: '3', time: '5小时前', user: 'guorongxiao@gmail.com', action: '创建操作类型', detail: '完成了「安排飞机适航维护 (scheduleMaintenanceCheck)」后台原子副作用函数定义。', type: 'create' },
    { id: '4', time: '昨天', user: 'System Agent', action: '智能数据源检查', detail: '确认原始数据集 ds_airport_geolocations 格式契合地理定位 (locatable) 接口契约。', type: 'check' }
  ];

  // Helper mapping for Tailwind classes based on colors
  const getDomainColorClasses = (color: string) => {
    switch (color) {
      case 'blue': 
        return { 
          bg: 'bg-blue-50/70', 
          text: 'text-blue-700', 
          border: 'border-blue-200', 
          activeBg: 'bg-blue-600', 
          dot: 'bg-blue-500', 
          hoverBg: 'hover:bg-blue-50',
          focusRing: 'focus:ring-blue-400',
          leftBorder: 'border-l-4 border-l-blue-500'
        };
      case 'emerald': 
        return { 
          bg: 'bg-emerald-50/70', 
          text: 'text-emerald-700', 
          border: 'border-emerald-200', 
          activeBg: 'bg-emerald-600', 
          dot: 'bg-emerald-500', 
          hoverBg: 'hover:bg-emerald-50',
          focusRing: 'focus:ring-emerald-400',
          leftBorder: 'border-l-4 border-l-emerald-500'
        };
      case 'amber': 
        return { 
          bg: 'bg-amber-50/70', 
          text: 'text-amber-700', 
          border: 'border-amber-200', 
          activeBg: 'bg-amber-600', 
          dot: 'bg-amber-500', 
          hoverBg: 'hover:bg-amber-50',
          focusRing: 'focus:ring-amber-400',
          leftBorder: 'border-l-4 border-l-amber-500'
        };
      case 'purple': 
        return { 
          bg: 'bg-purple-50/70', 
          text: 'text-purple-700', 
          border: 'border-purple-200', 
          activeBg: 'bg-purple-600', 
          dot: 'bg-purple-500', 
          hoverBg: 'hover:bg-purple-50',
          focusRing: 'focus:ring-purple-400',
          leftBorder: 'border-l-4 border-l-purple-500'
        };
      case 'rose': 
        return { 
          bg: 'bg-rose-50/70', 
          text: 'text-rose-700', 
          border: 'border-rose-200', 
          activeBg: 'bg-rose-600', 
          dot: 'bg-rose-500', 
          hoverBg: 'hover:bg-rose-50',
          focusRing: 'focus:ring-rose-400',
          leftBorder: 'border-l-4 border-l-rose-500'
        };
      case 'indigo': 
        return { 
          bg: 'bg-indigo-50/70', 
          text: 'text-indigo-700', 
          border: 'border-indigo-200', 
          activeBg: 'bg-indigo-600', 
          dot: 'bg-indigo-500', 
          hoverBg: 'hover:bg-indigo-50',
          focusRing: 'focus:ring-indigo-400',
          leftBorder: 'border-l-4 border-l-indigo-500'
        };
      case 'slate': 
        return { 
          bg: 'bg-slate-50', 
          text: 'text-slate-700', 
          border: 'border-slate-300', 
          activeBg: 'bg-slate-700', 
          dot: 'bg-slate-500', 
          hoverBg: 'hover:bg-slate-100/50',
          focusRing: 'focus:ring-slate-400',
          leftBorder: 'border-l-4 border-l-slate-400'
        };
      default: 
        return { 
          bg: 'bg-slate-50', 
          text: 'text-slate-700', 
          border: 'border-slate-200', 
          activeBg: 'bg-slate-600', 
          dot: 'bg-slate-400', 
          hoverBg: 'hover:bg-slate-50',
          focusRing: 'focus:ring-slate-400',
          leftBorder: 'border-l-4 border-l-slate-400'
        };
    }
  };

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
    <div className="flex flex-col h-full bg-slate-50 overflow-y-auto p-6 space-y-6 select-none">
      


      {/* Title & Introduction Banner */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs flex items-center justify-between">
        <div className="space-y-1 flex-1">
          <h2 className="text-sm font-semibold text-slate-900 flex items-center gap-1.5">
            <LucideIcon name="Workflow" className="text-blue-600" size={16} />
            航空资产与运行智能本体 (Aviation Core Ontology)
          </h2>
          <p className="text-xs text-slate-500 max-w-3xl leading-relaxed">
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
      <div className="grid grid-cols-4 gap-4">
        {/* 1. Object Types Card */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">业务对象实体</span>
            <div className="text-xl font-bold text-slate-900 font-mono">{objectTypes.length}</div>
            <div className="text-[10px] text-slate-500 flex items-center gap-1">
              <span>{objectTypes.filter(ot => ot.domainId).length} 个已归域</span>
              <span className="text-slate-300">|</span>
              <span className="text-amber-600 font-medium">{objectTypes.filter(ot => !ot.domainId).length} 个未归类</span>
            </div>
          </div>
          <span className="p-2.5 rounded-xl bg-blue-50 text-blue-600 border border-blue-100">
            <LucideIcon name="Box" size={18} />
          </span>
        </div>

        {/* 2. Link Types Card */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">关系链接数量</span>
            <div className="text-xl font-bold text-slate-900 font-mono">{linkTypes.length}</div>
            <div className="text-[10px] text-slate-500">
              包含域内关联与跨域多维关联
            </div>
          </div>
          <span className="p-2.5 rounded-xl bg-emerald-50 text-emerald-600 border border-emerald-100">
            <LucideIcon name="GitMerge" size={18} />
          </span>
        </div>

        {/* 3. Action Types Card */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">系统业务域</span>
            <div className="text-xl font-bold text-slate-900 font-mono">{domains.length}</div>
            <div className="text-[10px] text-slate-500">
              支持按域进行对象隔离与维护
            </div>
          </div>
          <span className="p-2.5 rounded-xl bg-purple-50 text-purple-600 border border-purple-100">
            <LucideIcon name="Layers" size={18} />
          </span>
        </div>

        {/* 4. Combined specs Card */}
        <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-3xs flex items-center justify-between hover:shadow-xs transition-shadow">
          <div className="space-y-1">
            <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider">接口与属性指标</span>
            <div className="text-xl font-bold text-slate-900 font-mono">{interfaces.length + sharedProperties.length}</div>
            <div className="text-[10px] text-slate-500">
              {interfaces.length} 契约规范 · {sharedProperties.length} 共享属性
            </div>
          </div>
          <span className="p-2.5 rounded-xl bg-amber-50 text-amber-600 border border-amber-100">
            <LucideIcon name="Tag" size={18} />
          </span>
        </div>
      </div>

      {/* Main Graph Panel */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 border-b border-slate-100 pb-3">
          <div className="space-y-0.5">
            <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5">
              <LucideIcon name="Network" size={15} className="text-blue-600" />
              关系拓扑图谱 (Ontology ER Diagram)
            </h3>
            <p className="text-[11px] text-slate-500">显示当前实体关联网络。可在上方切换不同业务域以进行视窗隔离和动态聚焦。</p>
          </div>
          
          {/* Dynamic Filter Controls */}
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="text-slate-400 font-bold uppercase text-[9px] tracking-wider">视图按域过滤:</span>
            <button
              onClick={() => onSelectDomainFilter(null)}
              className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${
                !selectedDomainFilter
                  ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                  : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
              }`}
            >
              全局全景 ({objectTypes.length})
            </button>
            {domains.map(d => {
              const isSelected = selectedDomainFilter === d.id;
              const count = objectTypes.filter(ot => ot.domainId === d.id).length;
              const classes = getDomainColorClasses(d.color);
              return (
                <button
                  key={d.id}
                  onClick={() => onSelectDomainFilter(d.id)}
                  className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer flex items-center gap-1.5 text-[11px] ${
                    isSelected
                      ? `${classes.activeBg} text-white border-transparent shadow-xs`
                      : `bg-white text-slate-700 border-slate-200 ${classes.hoverBg}`
                  }`}
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${isSelected ? 'bg-white' : classes.dot}`} />
                  <span>{d.displayName.split(' (')[0]}</span>
                  <span className={`text-[9px] px-1 py-0.2 rounded-full font-mono ${isSelected ? 'bg-white/20' : 'bg-slate-100 text-slate-500'}`}>
                    {count}
                  </span>
                </button>
              );
            })}
            {objectTypes.some(ot => !ot.domainId) && (
              <button
                onClick={() => onSelectDomainFilter('unassigned')}
                className={`px-2.5 py-1 rounded-full transition-all border font-medium cursor-pointer text-[11px] ${
                  selectedDomainFilter === 'unassigned'
                    ? 'bg-slate-500 text-white border-slate-500 shadow-xs'
                    : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                }`}
              >
                未归域 ({objectTypes.filter(ot => !ot.domainId).length})
              </button>
            )}
          </div>
        </div>

        {/* Embedded SVG Graph Canvas with filtered nodes */}
        <div className="relative border border-slate-200 rounded-lg overflow-hidden bg-slate-50/50">
          <OntologyGraph
            objectTypes={displayedObjects}
            linkTypes={displayedLinks}
            onSelectNode={onSelectNode}
            onSelectEdge={onSelectEdge}
          />
        </div>
      </div>

      {/* 🛠️ Domain Management & Classification Maintenance Deck */}
      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div className="flex justify-between items-center border-b border-slate-100 pb-3">
          <div>
            <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5">
              <LucideIcon name="Settings" size={15} className="text-slate-500" />
              业务分级域维护中心 (Business Domain Manager)
            </h3>
            <p className="text-[11px] text-slate-500 mt-0.5">创建、编辑和删除业务分级域，直观进行本体对象归宿分配 (即多对多关系绑定)。</p>
          </div>
          {!isAddingNew && (
            <button
              onClick={handleStartAdd}
              className="bg-slate-900 hover:bg-slate-800 text-white text-xs px-3 py-1.5 rounded-lg font-medium transition-colors flex items-center gap-1 shadow-xs"
            >
              <LucideIcon name="PlusCircle" size={14} />
              新建业务域分级
            </button>
          )}
        </div>

        {isAddingNew ? (
          /* Domain Creation/Edit Form Block */
          <form onSubmit={handleSaveDomain} className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-200 pb-2">
              <h4 className="text-xs font-semibold text-slate-800 flex items-center gap-1.5">
                <LucideIcon name="Edit3" size={14} className="text-blue-500" />
                <span>{editingDomain ? `编辑业务域: ${editingDomain.displayName}` : '新建业务域分级'}</span>
              </h4>
              <button
                type="button"
                onClick={() => { setIsAddingNew(false); setEditingDomain(null); }}
                className="text-slate-400 hover:text-slate-600 p-1 rounded hover:bg-slate-200/50"
              >
                <LucideIcon name="X" size={16} />
              </button>
            </div>

            {formError && (
              <div className="p-2.5 bg-red-50 border border-red-200 text-red-600 text-[11px] rounded-md flex items-center gap-1.5">
                <LucideIcon name="AlertCircle" size={14} />
                <span>{formError}</span>
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-1 md:col-span-1">
                <label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">唯一标识 ID (不可包含空格)</label>
                <input
                  type="text"
                  disabled={!!editingDomain}
                  value={formId}
                  onChange={e => setFormId(e.target.value)}
                  placeholder="如: flight_ops"
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden disabled:bg-slate-100 disabled:text-slate-400 font-mono"
                />
              </div>

              <div className="space-y-1 md:col-span-1">
                <label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">显示名称 (Display Name)</label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="如: 运行控制域"
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                />
              </div>

              <div className="space-y-1 md:col-span-1">
                <label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">代表色调 (Theme Color)</label>
                <div className="flex items-center gap-1.5 py-1">
                  {['blue', 'emerald', 'amber', 'purple', 'rose', 'indigo', 'slate'].map(color => {
                    const isSelected = formColor === color;
                    const classes = getDomainColorClasses(color);
                    return (
                      <button
                        key={color}
                        type="button"
                        onClick={() => setFormColor(color)}
                        className={`w-6 h-6 rounded-full border-2 transition-transform flex items-center justify-center ${classes.bg} ${isSelected ? 'border-slate-800 scale-110 shadow-xs' : 'border-slate-200 hover:scale-105'}`}
                        title={color}
                      >
                        <span className={`w-2.5 h-2.5 rounded-full ${classes.dot}`} />
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">业务描述信息 (Description)</label>
              <textarea
                value={formDesc}
                onChange={e => setFormDesc(e.target.value)}
                placeholder="简述该业务域所承载的核心职能、负责团队或数据流转范围。"
                className="w-full h-16 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
              />
            </div>

            {/* Object types assignment in the form */}
            <div className="space-y-2 border-t border-slate-200 pt-3">
              <label className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block">
                绑定关联的实体对象 (Object Types Assignment)
              </label>
              <div className="text-[10px] text-slate-400 -mt-1">直接勾选属于该业务域的实体类型。一个实体同一时间仅可归属一个业务域。</div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2.5 pt-1.5">
                {objectTypes.map(ot => {
                  const isChecked = formAssignedObjects.includes(ot.id);
                  const isMappedToOther = ot.domainId && ot.domainId !== formId;
                  const otherDomain = isMappedToOther ? domains.find(d => d.id === ot.domainId) : null;
                  
                  return (
                    <div 
                      key={ot.id}
                      onClick={() => !isMappedToOther && toggleObjectAssignment(ot.id)}
                      className={`flex items-center justify-between p-2 rounded-lg border text-xs select-none transition-all ${
                        isMappedToOther 
                          ? 'bg-slate-100 border-slate-200 text-slate-400 cursor-not-allowed opacity-60' 
                          : isChecked 
                          ? 'bg-blue-50 border-blue-300 text-blue-700 font-medium cursor-pointer' 
                          : 'bg-white border-slate-200 text-slate-600 hover:border-slate-300 hover:bg-slate-50 cursor-pointer'
                      }`}
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className={`p-0.5 rounded border ${isChecked ? 'bg-blue-100 border-blue-200' : 'bg-slate-50'}`}>
                          <LucideIcon name={ot.icon} size={12} />
                        </span>
                        <span className="truncate">{ot.displayName}</span>
                      </div>
                      <div className="flex items-center">
                        {isMappedToOther ? (
                          <span className="text-[9px] bg-slate-200 text-slate-500 px-1 py-0.2 rounded font-mono truncate max-w-[65px]" title={`已被域 "${otherDomain?.displayName}" 绑定`}>
                            {otherDomain?.displayName.split(' (')[0]}
                          </span>
                        ) : (
                          <span className={`w-3.5 h-3.5 rounded border flex items-center justify-center ${isChecked ? 'bg-blue-600 border-blue-600 text-white' : 'border-slate-300 bg-white'}`}>
                            {isChecked && <LucideIcon name="Check" size={10} />}
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Form actions */}
            <div className="flex justify-end gap-2 border-t border-slate-200 pt-3">
              <button
                type="button"
                onClick={() => { setIsAddingNew(false); setEditingDomain(null); }}
                className="px-3.5 py-1.5 rounded-lg border border-slate-200 text-slate-600 text-xs font-semibold bg-white hover:bg-slate-50"
              >
                取消
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 rounded-lg text-white text-xs font-semibold bg-blue-600 hover:bg-blue-700 shadow-sm"
              >
                {editingDomain ? '保存修改' : '创建业务域'}
              </button>
            </div>
          </form>
        ) : (
          /* Domains Cards Grid */
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {domains.map(d => {
              const classes = getDomainColorClasses(d.color);
              const domainObjects = objectTypes.filter(ot => ot.domainId === d.id);
              
              return (
                <div 
                  key={d.id} 
                  className={`bg-white border border-slate-200 hover:border-slate-300 rounded-xl p-4 flex flex-col justify-between hover:shadow-2xs transition-all relative overflow-hidden ${classes.leftBorder}`}
                >
                  <div className="space-y-2">
                    <div className="flex justify-between items-start">
                      <div>
                        <h4 className="text-xs font-semibold text-slate-900">{d.displayName}</h4>
                        <span className="text-[10px] text-slate-400 font-mono lowercase tracking-tight">域识别ID: {d.id}</span>
                      </div>
                      
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => handleStartEdit(d)}
                          className="p-1 text-slate-400 hover:text-slate-600 rounded hover:bg-slate-100"
                          title="编辑此业务域"
                        >
                          <LucideIcon name="Edit" size={12} />
                        </button>
                        <button
                          onClick={() => handleDeleteDomain(d.id)}
                          className="p-1 text-slate-400 hover:text-red-600 rounded hover:bg-red-50"
                          title="删除此业务域"
                        >
                          <LucideIcon name="Trash2" size={12} />
                        </button>
                      </div>
                    </div>

                    <p className="text-[11px] text-slate-500 leading-relaxed min-h-[36px] line-clamp-2">
                      {d.description || '暂无业务描述。'}
                    </p>
                  </div>

                  {/* Associated object lists */}
                  <div className="border-t border-slate-100 pt-3 mt-3">
                    <div className="flex items-center justify-between text-[10px] text-slate-400 font-semibold uppercase mb-1.5">
                      <span>已关联对象 ({domainObjects.length})</span>
                      <span className="font-mono">{d.id} domain</span>
                    </div>
                    {domainObjects.length > 0 ? (
                      <div className="flex flex-wrap gap-1.5">
                        {domainObjects.map(ot => (
                          <div 
                            key={ot.id}
                            onClick={() => onQuickNavigate('object', ot.id)}
                            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-slate-200 bg-slate-50 text-slate-600 text-[10px] font-medium hover:border-blue-400 hover:bg-blue-50/20 cursor-pointer transition-colors"
                          >
                            <LucideIcon name={ot.icon} size={10} className="text-slate-400" />
                            <span>{ot.displayName.split(' (')[0]}</span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-[10px] text-slate-400 italic">暂无绑定的业务实体</div>
                    )}
                  </div>
                </div>
              );
            })}

            {/* Unassigned Quick Stats Card */}
            {objectTypes.some(ot => !ot.domainId) && (
              <div className="bg-slate-50/50 border border-slate-200/60 rounded-xl p-4 flex flex-col justify-between border-dashed">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <h4 className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                      <LucideIcon name="AlertCircle" size={13} className="text-amber-500" />
                      <span>未分类实体池</span>
                    </h4>
                    <span className="text-[10px] px-1.5 py-0.2 rounded-full bg-amber-100 text-amber-800 font-semibold font-mono">
                      {objectTypes.filter(ot => !ot.domainId).length} 对象
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 leading-relaxed">
                    当前声明的某些本体实体还未绑定到具体的业务划分域中。未分类对象在全景关系拓扑中可以继续存在，但在业务域治理上未形成职责分级。
                  </p>
                </div>

                <div className="border-t border-slate-200/50 pt-3 mt-3 flex flex-wrap gap-1.5">
                  {objectTypes.filter(ot => !ot.domainId).map(ot => (
                    <div 
                      key={ot.id}
                      onClick={() => onQuickNavigate('object', ot.id)}
                      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full border border-slate-300/80 bg-white text-slate-500 text-[10px] hover:border-amber-400 hover:bg-amber-50/20 cursor-pointer transition-colors"
                    >
                      <LucideIcon name={ot.icon} size={10} />
                      <span>{ot.displayName.split(' (')[0]}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Bottom Row split: Quick Navigator & Enterprise Logs */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Quick List Nav segmented by Domain */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-3">
          <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5">
            <LucideIcon name="Compass" size={14} className="text-slate-500" />
            实体清单快速分级导航
          </h3>
          
          <div className="space-y-4 max-h-[260px] overflow-y-auto pr-1">
            {/* 1. Show Domains */}
            {domains.map(d => {
              const domObjs = objectTypes.filter(ot => ot.domainId === d.id);
              if (domObjs.length === 0) return null;
              const classes = getDomainColorClasses(d.color);
              
              return (
                <div key={d.id} className="space-y-1.5">
                  <div className="flex items-center gap-1.5 text-[10px] font-extrabold text-slate-500 tracking-wider uppercase border-b border-slate-100 pb-1">
                    <span className={`w-2 h-2 rounded-full ${classes.dot}`} />
                    <span>{d.displayName}</span>
                  </div>
                  <div className="space-y-1 pl-1">
                    {domObjs.map(ot => (
                      <div
                        key={ot.id}
                        onClick={() => onQuickNavigate('object', ot.id)}
                        className="flex items-center justify-between p-1.5 rounded-lg border border-slate-100 hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group"
                      >
                        <div className="flex items-center gap-2 truncate">
                          <span className={`p-0.5 rounded border ${ot.color}`}>
                            <LucideIcon name={ot.icon} size={11} />
                          </span>
                          <span className="text-xs font-medium text-slate-700">{ot.displayName}</span>
                        </div>
                        <LucideIcon name="ChevronRight" size={11} className="text-slate-300 group-hover:translate-x-0.5 transition-transform" />
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}

            {/* 2. Show Unassigned if any */}
            {objectTypes.some(ot => !ot.domainId) && (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1.5 text-[10px] font-extrabold text-slate-400 tracking-wider uppercase border-b border-slate-100 pb-1">
                  <span className="w-2 h-2 rounded-full bg-slate-300" />
                  <span>未归类业务对象 (Pool)</span>
                </div>
                <div className="space-y-1 pl-1">
                  {objectTypes.filter(ot => !ot.domainId).map(ot => (
                    <div
                      key={ot.id}
                      onClick={() => onQuickNavigate('object', ot.id)}
                      className="flex items-center justify-between p-1.5 rounded-lg border border-slate-100 hover:border-blue-300 hover:bg-blue-50/20 cursor-pointer transition-all group"
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className={`p-0.5 rounded border ${ot.color}`}>
                          <LucideIcon name={ot.icon} size={11} />
                        </span>
                        <span className="text-xs font-medium text-slate-700">{ot.displayName}</span>
                      </div>
                      <LucideIcon name="ChevronRight" size={11} className="text-slate-300 group-hover:translate-x-0.5 transition-transform" />
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Enterprise lifecycle logs */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs lg:col-span-2 space-y-3">
          <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5">
            <LucideIcon name="History" size={14} className="text-slate-500" />
            生命周期与审计日志 (Foundry Activity Stream)
          </h3>
          <div className="divide-y divide-slate-100 max-h-[260px] overflow-y-auto pr-1">
            {auditLogs.map(log => (
              <div key={log.id} className="py-2.5 first:pt-0 last:pb-0 text-[11px] flex items-start gap-3">
                <div className="mt-0.5">
                  <span className={`w-2 h-2 rounded-full inline-block ${
                    log.type === 'publish' ? 'bg-blue-500' :
                    log.type === 'create' ? 'bg-emerald-500' : 'bg-slate-400'
                  }`} />
                </div>
                <div className="flex-1 space-y-0.5">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-slate-800">{log.action}</span>
                    <span className="text-[10px] text-slate-400 font-mono">{log.time}</span>
                  </div>
                  <p className="text-slate-500 text-[11px] leading-relaxed">{log.detail}</p>
                  <div className="text-[10px] text-slate-400 font-mono">操作员: {log.user}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

    </div>
  );
}
