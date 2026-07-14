/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, PropertyType, Dataset, LinkType, ActionType, SharedProperty, InterfaceType, OntologyDomain } from '../../types/ontology';
import LucideIcon from './LucideIcon';

interface ObjectTypeViewProps {
  objectType: ObjectType;
  datasets: Dataset[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  sharedProperties: SharedProperty[];
  interfaces: InterfaceType[];
  domains: OntologyDomain[];
  onUpdate: (updated: ObjectType) => void;
  onDelete: (id: string) => void;
  onNavigateToLink: (linkId: string) => void;
  onNavigateToAction: (actionId: string) => void;
  onExploreData?: (id: string) => void;
}

export default function ObjectTypeView({
  objectType,
  datasets,
  linkTypes,
  actionTypes,
  sharedProperties,
  interfaces,
  domains,
  onUpdate,
  onDelete,
  onNavigateToLink,
  onNavigateToAction,
  onExploreData
}: ObjectTypeViewProps) {
  const [activeTab, setActiveTab] = useState<'metadata' | 'properties' | 'mapping' | 'links' | 'actions'>('properties');
  const [newPropName, setNewPropName] = useState('');
  const [newPropType, setNewPropType] = useState<'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint'>('string');

  const selectedDataset = datasets.find(d => d.id === objectType.mapping.datasetId) || datasets[0];

  // Handler for metadata changes
  const handleMetaChange = (key: keyof ObjectType, value: any) => {
    onUpdate({
      ...objectType,
      [key]: value
    });
  };

  // Handler for mapping dataset changes
  const handleDatasetChange = (datasetId: string) => {
    onUpdate({
      ...objectType,
      mapping: {
        datasetId,
        propertyMappings: {} // Reset or keep empty to allow manually mapping
      }
    });
  };

  // Update specific property mapping
  const handlePropMappingChange = (propId: string, colName: string) => {
    onUpdate({
      ...objectType,
      mapping: {
        ...objectType.mapping,
        propertyMappings: {
          ...objectType.mapping.propertyMappings,
          [propId]: colName
        }
      }
    });
  };

  // Auto-map based on name similarity
  const handleAutoMap = () => {
    if (!selectedDataset) return;
    const newMappings: Record<string, string> = {};
    objectType.properties.forEach(prop => {
      // Find a column that matches by lowercase and replacing underscores
      const matchedCol = selectedDataset.columns.find(col => {
        const cNorm = col.name.toLowerCase().replace(/_/g, '');
        const pNormName = prop.displayName.toLowerCase().replace(/_/g, '');
        const pNormApi = prop.apiName.toLowerCase().replace(/_/g, '');
        const pNormId = prop.id.toLowerCase().replace(/_/g, '');
        return cNorm === pNormName || cNorm === pNormApi || cNorm === pNormId;
      });

      if (matchedCol) {
        newMappings[prop.id] = matchedCol.name;
      }
    });

    onUpdate({
      ...objectType,
      mapping: {
        ...objectType.mapping,
        propertyMappings: {
          ...objectType.mapping.propertyMappings,
          ...newMappings
        }
      }
    });
  };

  // Add new property
  const handleAddProperty = () => {
    if (!newPropName.trim()) return;
    const propId = newPropName.trim().replace(/\s+/g, '');
    const newProp: PropertyType = {
      id: propId,
      displayName: newPropName,
      apiName: propId.charAt(0).toLowerCase() + propId.slice(1),
      dataType: newPropType,
      isPrimaryKey: false,
      description: `关于 ${newPropName} 的详细描述。`
    };

    onUpdate({
      ...objectType,
      properties: [...objectType.properties, newProp]
    });
    setNewPropName('');
  };

  // Remove property
  const handleRemoveProperty = (propId: string) => {
    if (propId === objectType.primaryKey) {
      alert('无法删除主键属性！');
      return;
    }
    const updatedProps = objectType.properties.filter(p => p.id !== propId);
    const updatedMappings = { ...objectType.mapping.propertyMappings };
    delete updatedMappings[propId];

    onUpdate({
      ...objectType,
      properties: updatedProps,
      mapping: {
        ...objectType.mapping,
        propertyMappings: updatedMappings
      }
    });
  };

  // Toggle PK
  const handleTogglePrimaryKey = (propId: string) => {
    onUpdate({
      ...objectType,
      primaryKey: propId,
      properties: objectType.properties.map(p => ({
        ...p,
        isPrimaryKey: p.id === propId
      }))
    });
  };

  // Change individual property fields
  const handlePropertyFieldChange = (propId: string, field: keyof PropertyType, value: any) => {
    onUpdate({
      ...objectType,
      properties: objectType.properties.map(p =>
        p.id === propId ? { ...p, [field]: value } : p
      )
    });
  };

  // Filter linked and actions
  const relatedLinks = linkTypes.filter(
    l => l.sourceObjectType === objectType.id || l.targetObjectType === objectType.id
  );

  const relatedActions = actionTypes.filter(action =>
    action.parameters.some(param => param.dataType === 'object' && param.objectTypeId === objectType.id)
  );

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Detail Header */}
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg border-2 ${objectType.color} flex items-center justify-center`}>
            <LucideIcon name={objectType.icon} size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{objectType.displayName}</h2>
              <span className="text-xs font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                {objectType.apiName}
              </span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                objectType.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
              }`}>
                {objectType.status === 'ACTIVE' ? '已发布' : '草稿'}
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{objectType.description || '无详细描述'}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onExploreData && (
            <button
              onClick={() => onExploreData(objectType.id)}
              className="text-xs text-blue-600 hover:bg-blue-50 px-2.5 py-1.5 rounded border border-blue-200 transition-colors flex items-center gap-1.5 font-semibold"
            >
              <LucideIcon name="Compass" size={13} />
              探索数据 (Explore)
            </button>
          )}
          <button
            onClick={() => onDelete(objectType.id)}
            className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
          >
            <LucideIcon name="Trash2" size={13} />
            删除对象
          </button>
        </div>
      </div>

      {/* Detail Tabs */}
      <div className="flex px-6 border-b border-gray-200 bg-white">
        {(['properties', 'mapping', 'metadata', 'links', 'actions'] as const).map(tab => {
          const tabLabels = {
            properties: '属性定义',
            mapping: '数据源映射',
            metadata: '元数据配置',
            links: '关联链接',
            actions: '应用操作'
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {tabLabels[tab]}
            </button>
          );
        })}
      </div>

      {/* Active Tab Panel */}
      <div className="flex-1 overflow-y-auto p-6">
        {/* PROPERTIES TAB */}
        {activeTab === 'properties' && (
          <div className="space-y-6">
            <div className="flex justify-between items-center">
              <div className="text-xs text-slate-500">
                定义构成此对象类型的所有核心属性。其中必须设定唯一的主键 (Primary Key)。
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder="新属性中文名"
                  value={newPropName}
                  onChange={e => setNewPropName(e.target.value)}
                  className="px-3 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
                <select
                  value={newPropType}
                  onChange={e => setNewPropType(e.target.value as any)}
                  className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="string">String (字符串)</option>
                  <option value="integer">Integer (整型)</option>
                  <option value="decimal">Decimal (高精度浮点)</option>
                  <option value="boolean">Boolean (布尔)</option>
                  <option value="date">Date (日期)</option>
                  <option value="timestamp">Timestamp (时间戳)</option>
                  <option value="geopoint">Geopoint (地理坐标)</option>
                </select>
                <button
                  onClick={handleAddProperty}
                  className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1"
                >
                  <LucideIcon name="Plus" size={13} />
                  添加属性
                </button>
              </div>
            </div>

            <div className="overflow-x-auto border border-gray-200 rounded-lg">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-slate-50 border-b border-gray-200 text-slate-700 font-medium">
                    <th className="py-2.5 px-4 w-12 text-center">主键</th>
                    <th className="py-2.5 px-4">显示名称</th>
                    <th className="py-2.5 px-4">API 字段名</th>
                    <th className="py-2.5 px-4">数据类型</th>
                    <th className="py-2.5 px-4">描述说明</th>
                    <th className="py-2.5 px-4">共享属性绑定</th>
                    <th className="py-2.5 px-4 text-center">操作</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 text-slate-600">
                  {objectType.properties.map(prop => (
                    <tr key={prop.id} className="hover:bg-slate-50/50 transition-colors">
                      <td className="py-2.5 px-4 text-center">
                        <button
                          onClick={() => handleTogglePrimaryKey(prop.id)}
                          className={`p-1.5 rounded-full transition-colors ${
                            objectType.primaryKey === prop.id
                              ? 'text-amber-500 hover:bg-amber-50'
                              : 'text-slate-300 hover:text-slate-400 hover:bg-slate-100'
                          }`}
                          title={objectType.primaryKey === prop.id ? '当前为主键' : '设为主键'}
                        >
                          <LucideIcon name="Key" size={14} className={objectType.primaryKey === prop.id ? 'fill-amber-500' : ''} />
                        </button>
                      </td>
                      <td className="py-2.5 px-4">
                        <input
                          type="text"
                          value={prop.displayName}
                          onChange={e => handlePropertyFieldChange(prop.id, 'displayName', e.target.value)}
                          className="font-medium text-slate-900 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1"
                        />
                      </td>
                      <td className="py-2.5 px-4 font-mono text-slate-500">
                        <input
                          type="text"
                          value={prop.apiName}
                          onChange={e => handlePropertyFieldChange(prop.id, 'apiName', e.target.value)}
                          className="border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full"
                        />
                      </td>
                      <td className="py-2.5 px-4">
                        <select
                          value={prop.dataType}
                          onChange={e => handlePropertyFieldChange(prop.id, 'dataType', e.target.value)}
                          className="bg-transparent border border-gray-200 rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden font-mono"
                        >
                          <option value="string">string</option>
                          <option value="integer">integer</option>
                          <option value="decimal">decimal</option>
                          <option value="boolean">boolean</option>
                          <option value="date">date</option>
                          <option value="timestamp">timestamp</option>
                          <option value="geopoint">geopoint</option>
                        </select>
                      </td>
                      <td className="py-2.5 px-4">
                        <input
                          type="text"
                          value={prop.description}
                          onChange={e => handlePropertyFieldChange(prop.id, 'description', e.target.value)}
                          className="text-slate-500 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full"
                          placeholder="暂无描述"
                        />
                      </td>
                      <td className="py-2.5 px-4">
                        <select
                          value={prop.sharedPropertyId || ''}
                          onChange={e => handlePropertyFieldChange(prop.id, 'sharedPropertyId', e.target.value || undefined)}
                          className="bg-transparent border border-gray-200 rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden text-slate-600"
                        >
                          <option value="">未绑定 (无)</option>
                          {sharedProperties.map(sp => (
                            <option key={sp.id} value={sp.id}>{sp.displayName} ({sp.apiName})</option>
                          ))}
                        </select>
                      </td>
                      <td className="py-2.5 px-4 text-center">
                        <button
                          onClick={() => handleRemoveProperty(prop.id)}
                          className="text-slate-400 hover:text-red-500 p-1 rounded hover:bg-slate-100 transition-colors"
                          title="删除属性"
                        >
                          <LucideIcon name="X" size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* METADATA CONFIG TAB */}
        {activeTab === 'metadata' && (
          <div className="space-y-6 max-w-2xl">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">对象显示名称 (Display Name)</label>
                <input
                  type="text"
                  value={objectType.displayName}
                  onChange={e => handleMetaChange('displayName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">API 标识名 (API Name)</label>
                <input
                  type="text"
                  value={objectType.apiName}
                  onChange={e => handleMetaChange('apiName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-slate-700">对象描述信息 (Description)</label>
              <textarea
                value={objectType.description}
                onChange={e => handleMetaChange('description', e.target.value)}
                className="w-full h-20 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                placeholder="为此实体对象输入详细的业务背景和使用建议。"
              />
            </div>

            <div className="space-y-1.5 border-t border-gray-100 pt-4">
              <label className="text-xs font-semibold text-slate-700">划分业务域 (Ontology Domain Hierarchy)</label>
              <select
                value={objectType.domainId || ''}
                onChange={e => handleMetaChange('domainId', e.target.value || undefined)}
                className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
              >
                <option value="">-- 未分类 (不属于任何业务域) --</option>
                {domains.map(d => (
                  <option key={d.id} value={d.id}>{d.displayName}</option>
                ))}
              </select>
              <p className="text-[10px] text-slate-400">选择该实体所属的顶级业务大类。可前往“本体全景与总览”页面创建和维护更多业务域分级。</p>
            </div>

            <div className="grid grid-cols-2 gap-4 border-t border-gray-100 pt-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">标题展示属性 (Title Property)</label>
                <select
                  value={objectType.titleProperty}
                  onChange={e => handleMetaChange('titleProperty', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  {objectType.properties.map(p => (
                    <option key={p.id} value={p.id}>{p.displayName} ({p.apiName})</option>
                  ))}
                </select>
                <p className="text-[10px] text-slate-400">用于在图谱、搜索结果和关系列表里展示此对象的默认文本标题。</p>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">运营状态 (Status)</label>
                <select
                  value={objectType.status}
                  onChange={e => handleMetaChange('status', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="DRAFT">草稿 (DRAFT)</option>
                  <option value="ACTIVE">启用 (ACTIVE)</option>
                  <option value="DEPRECATED">弃用 (DEPRECATED)</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">界面显示图标 (Lucide Icon)</label>
                <select
                  value={objectType.icon}
                  onChange={e => handleMetaChange('icon', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="Plane">Plane (飞机)</option>
                  <option value="Building2">Building2 (机场/楼宇)</option>
                  <option value="Navigation">Navigation (导航/指针)</option>
                  <option value="UserSquare2">UserSquare2 (飞行员/人员)</option>
                  <option value="Database">Database (数据源)</option>
                  <option value="ShieldAlert">ShieldAlert (安全性)</option>
                  <option value="FileText">FileText (文档)</option>
                  <option value="Heart">Heart (健康度)</option>
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold text-slate-700">视觉主题颜色 (Color Theme)</label>
                <select
                  value={objectType.color}
                  onChange={e => handleMetaChange('color', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="border-blue-500 bg-blue-50 text-blue-700">皇家蓝 (Blue)</option>
                  <option value="border-emerald-500 bg-emerald-50 text-emerald-700">活力绿 (Emerald)</option>
                  <option value="border-purple-500 bg-purple-50 text-purple-700">星空紫 (Purple)</option>
                  <option value="border-orange-500 bg-orange-50 text-orange-700">温暖橘 (Orange)</option>
                  <option value="border-red-500 bg-red-50 text-red-700">警戒红 (Red)</option>
                  <option value="border-slate-500 bg-slate-50 text-slate-700">中性灰 (Slate)</option>
                </select>
              </div>
            </div>

            {/* Implements Interfaces */}
            <div className="space-y-2 border-t border-gray-100 pt-4">
              <label className="text-xs font-semibold text-slate-700 block">实现的接口 (Implements Interfaces)</label>
              <div className="flex flex-wrap gap-2">
                {interfaces.map(intf => {
                  const isChecked = (objectType.interfaces || []).includes(intf.id);
                  return (
                    <label key={intf.id} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs cursor-pointer select-none transition-colors ${
                      isChecked ? 'bg-blue-50 border-blue-300 text-blue-700' : 'bg-white border-gray-200 text-slate-600 hover:bg-gray-50'
                    }`}>
                      <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={(e) => {
                          const current = objectType.interfaces || [];
                          const updated = e.target.checked
                            ? [...current, intf.id]
                            : current.filter(id => id !== intf.id);
                          handleMetaChange('interfaces', updated);
                        }}
                        className="sr-only"
                      />
                      <LucideIcon name="Layers" size={13} />
                      <span>{intf.displayName}</span>
                    </label>
                  );
                })}
              </div>
              <p className="text-[10px] text-slate-400">对象类型通过实现特定接口，将继承该接口规范的一系列属性和行为逻辑。</p>
            </div>
          </div>
        )}

        {/* DATA SOURCE MAPPING TAB */}
        {activeTab === 'mapping' && (
          <div className="space-y-6">
            <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 flex justify-between items-center">
              <div className="flex items-center gap-3">
                <LucideIcon name="Database" className="text-slate-500" size={18} />
                <div>
                  <div className="text-xs font-semibold text-slate-800">当前绑定的原始数据集</div>
                  <div className="text-[10px] text-slate-500 font-mono mt-0.5">{selectedDataset?.path}</div>
                </div>
              </div>
              <div className="flex gap-2">
                <select
                  value={objectType.mapping.datasetId}
                  onChange={e => handleDatasetChange(e.target.value)}
                  className="px-3 py-1.5 text-xs border border-slate-300 rounded bg-white focus:outline-hidden"
                >
                  {datasets.map(ds => (
                    <option key={ds.id} value={ds.id}>{ds.name}</option>
                  ))}
                </select>
                <button
                  onClick={handleAutoMap}
                  className="bg-slate-200 hover:bg-slate-300 text-slate-700 text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1"
                >
                  <LucideIcon name="Wand2" size={13} />
                  智能映射
                </button>
              </div>
            </div>

            {/* Split Screen Mapping Grid */}
            <div className="grid grid-cols-2 gap-8 relative">
              {/* Left Column: Raw Datasource */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-semibold text-slate-800 flex items-center gap-1">
                    <LucideIcon name="Table" size={14} />
                    原始表字段 (Schema)
                  </div>
                  <span className="text-[10px] bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded font-mono">
                    {selectedDataset?.columns.length} 列
                  </span>
                </div>
                <div className="border border-slate-200 rounded-lg bg-white overflow-hidden divide-y divide-slate-100">
                  {selectedDataset?.columns.map(col => (
                    <div key={col.name} className="flex justify-between items-center px-4 py-2.5 hover:bg-slate-50/50">
                      <div className="font-mono text-xs font-medium text-slate-700">{col.name}</div>
                      <div className="text-[10px] text-slate-400 font-mono italic uppercase bg-slate-100 px-1.5 py-0.5 rounded">
                        {col.type}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Right Column: Object Properties Mapping */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-semibold text-slate-800 flex items-center gap-1">
                    <LucideIcon name="Layers" size={14} />
                    对象属性映射关系
                  </div>
                  <span className="text-[10px] bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded font-mono">
                    {Object.keys(objectType.mapping.propertyMappings).length} / {objectType.properties.length} 已映射
                  </span>
                </div>
                <div className="border border-slate-200 rounded-lg bg-white overflow-hidden divide-y divide-slate-100">
                  {objectType.properties.map(prop => {
                    const mappedCol = objectType.mapping.propertyMappings[prop.id] || '';
                    return (
                      <div key={prop.id} className="flex justify-between items-center px-4 py-2.5 bg-white hover:bg-slate-50/50">
                        <div className="flex items-center gap-2">
                          <span className={objectType.primaryKey === prop.id ? 'text-amber-500' : 'text-slate-400'}>
                            <LucideIcon name={objectType.primaryKey === prop.id ? 'Key' : 'CircleDot'} size={12} />
                          </span>
                          <div>
                            <div className="text-xs font-medium text-slate-900">{prop.displayName}</div>
                            <div className="text-[10px] text-slate-400 font-mono mt-0.5">{prop.id} · {prop.dataType}</div>
                          </div>
                        </div>

                        {/* Mapped Selector */}
                        <div className="flex items-center gap-2">
                          <span className="text-slate-400 text-[10px]">←</span>
                          <select
                            value={mappedCol}
                            onChange={e => handlePropMappingChange(prop.id, e.target.value)}
                            className={`px-2 py-1 text-xs border rounded bg-white focus:outline-hidden font-mono text-slate-700 ${
                              mappedCol ? 'border-emerald-300 bg-emerald-50/30' : 'border-amber-300 bg-amber-50/10'
                            }`}
                          >
                            <option value="">-- 未映射 (不填) --</option>
                            {selectedDataset?.columns.map(col => (
                              <option key={col.name} value={col.name}>{col.name}</option>
                            ))}
                          </select>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* RELATED LINKS TAB */}
        {activeTab === 'links' && (
          <div className="space-y-4">
            <div className="text-xs text-slate-500">
              在此查看与 {objectType.displayName} 相关联的所有多维链接关系模式。
            </div>
            {relatedLinks.length === 0 ? (
              <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
                暂无任何链接关系定义关联到此对象。
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-4">
                {relatedLinks.map(link => {
                  const isSource = link.sourceObjectType === objectType.id;
                  const otherObjId = isSource ? link.targetObjectType : link.sourceObjectType;

                  return (
                    <div
                      key={link.id}
                      onClick={() => onNavigateToLink(link.id)}
                      className="p-4 border border-slate-200 rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer bg-white group flex items-start justify-between"
                    >
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="p-1 rounded-md bg-slate-100 text-slate-600 group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors">
                            <LucideIcon name="GitMerge" size={14} />
                          </span>
                          <div className="font-semibold text-xs text-slate-800 group-hover:text-blue-600">
                            {link.displayName}
                          </div>
                          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1 rounded">
                            {link.cardinality}
                          </span>
                        </div>
                        <p className="text-[10px] text-slate-500">{link.description}</p>
                        <div className="flex items-center gap-1.5 text-[10px] text-slate-600 pt-1">
                          <span className={isSource ? 'font-semibold text-blue-600' : ''}>
                            {isSource ? '源' : '源(' + link.sourceObjectType + ')'}
                          </span>
                          <span>→</span>
                          <span className={!isSource ? 'font-semibold text-blue-600' : ''}>
                            {!isSource ? '目标' : '目标(' + link.targetObjectType + ')'}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                        <span>跳转配置</span>
                        <LucideIcon name="ChevronRight" size={12} />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* RELATED ACTIONS TAB */}
        {activeTab === 'actions' && (
          <div className="space-y-4">
            <div className="text-xs text-slate-500">
              在此查看能够被应用、修改或实例化 {objectType.displayName} 对象的有界业务操作。
            </div>
            {relatedActions.length === 0 ? (
              <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
                目前尚无操作类型注册针对此对象的操作修改。
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-4">
                {relatedActions.map(action => (
                  <div
                    key={action.id}
                    onClick={() => onNavigateToAction(action.id)}
                    className="p-4 border border-slate-200 rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer bg-white group flex items-start justify-between"
                  >
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <span className="p-1.5 rounded-full bg-amber-50 text-amber-600 group-hover:bg-amber-100 transition-colors">
                          <LucideIcon name="Zap" size={13} className="fill-amber-500" />
                        </span>
                        <div className="font-semibold text-xs text-slate-800 group-hover:text-blue-600">
                          {action.displayName}
                        </div>
                      </div>
                      <p className="text-[10px] text-slate-500">{action.description}</p>
                      <div className="flex items-center gap-2 font-mono text-[9px] text-slate-400 bg-slate-50 p-1.5 rounded border border-slate-100">
                        <div>
                          <strong>参数:</strong> {action.parameters.length} | <strong>副作用:</strong> {action.rules.length} 条
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                      <span>跳转配置</span>
                      <LucideIcon name="ChevronRight" size={12} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
