/**
 * ObjectTypeDetail — 对象类型详情视图
 *
 * 本体工作台核心详情页，展示单个 ObjectType 的属性/元数据/映射/关联链接/操作。
 * Tab 内容已拆分为独立组件（见 ./object/ 目录）。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { Compass, Trash2 } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';

import type { ObjectType, PropertyType, Dataset, LinkType, ActionType, SharedProperty, InterfaceType, OntologyDomain } from '../../types/ontology';
import PropertiesTab from './object/PropertiesTab';
import MetadataTab from './object/MetadataTab';
import MappingTab from './object/MappingTab';
import LinksTab from './object/LinksTab';
import ActionsTab from './object/ActionsTab';

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

  // ── Handlers ──
  const handleMetaChange = (key: keyof ObjectType, value: any) => {
    onUpdate({ ...objectType, [key]: value });
  };

  const handleDatasetChange = (datasetId: string) => {
    onUpdate({ ...objectType, mapping: { datasetId, propertyMappings: {} } });
  };

  const handlePropMappingChange = (propId: string, colName: string) => {
    onUpdate({
      ...objectType,
      mapping: {
        ...objectType.mapping,
        propertyMappings: { ...objectType.mapping.propertyMappings, [propId]: colName }
      }
    });
  };

  const handleAutoMap = () => {
    if (!selectedDataset) return;
    const newMappings: Record<string, string> = {};
    objectType.properties.forEach(prop => {
      const matchedCol = selectedDataset.columns.find(col => {
        const cNorm = col.name.toLowerCase().replace(/_/g, '');
        const pNormName = prop.displayName.toLowerCase().replace(/_/g, '');
        const pNormApi = prop.apiName.toLowerCase().replace(/_/g, '');
        const pNormId = prop.id.toLowerCase().replace(/_/g, '');
        return cNorm === pNormName || cNorm === pNormApi || cNorm === pNormId;
      });
      if (matchedCol) newMappings[prop.id] = matchedCol.name;
    });
    onUpdate({
      ...objectType,
      mapping: { ...objectType.mapping, propertyMappings: { ...objectType.mapping.propertyMappings, ...newMappings } }
    });
  };

  const handleAddProperty = () => {
    if (!newPropName.trim()) return;
    const propId = newPropName.trim().replace(/\s+/g, '');
    const newProp: PropertyType = {
      id: propId, displayName: newPropName,
      apiName: propId.charAt(0).toLowerCase() + propId.slice(1),
      dataType: newPropType, isPrimaryKey: false,
      description: `关于 ${newPropName} 的详细描述。`
    };
    onUpdate({ ...objectType, properties: [...objectType.properties, newProp] });
    setNewPropName('');
  };

  const { t } = useLanguage();

  const handleRemoveProperty = (propId: string) => {
    if (propId === objectType.primaryKey) { alert(t('ontology.cannot_delete_pk')); return; }
    const updatedProps = objectType.properties.filter(p => p.id !== propId);
    const updatedMappings = { ...objectType.mapping.propertyMappings };
    delete updatedMappings[propId];
    onUpdate({ ...objectType, properties: updatedProps, mapping: { ...objectType.mapping, propertyMappings: updatedMappings } });
  };

  const handleTogglePrimaryKey = (propId: string) => {
    onUpdate({
      ...objectType, primaryKey: propId,
      properties: objectType.properties.map(p => ({ ...p, isPrimaryKey: p.id === propId }))
    });
  };

  const handlePropertyFieldChange = (propId: string, field: keyof PropertyType, value: any) => {
    onUpdate({
      ...objectType,
      properties: objectType.properties.map(p => p.id === propId ? { ...p, [field]: value } : p)
    });
  };

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
            <DynamicIcon name={objectType.icon} size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{objectType.displayName}</h2>
              <span className="text-xs font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">{objectType.apiName}</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                objectType.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
              }`}>{objectType.status === 'ACTIVE' ? '已发布' : '草稿'}</span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{objectType.description || '无详细描述'}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onExploreData && (
            <button onClick={() => onExploreData(objectType.id)}
              className="text-xs text-blue-600 hover:bg-blue-50 px-2.5 py-1.5 rounded border border-blue-200 transition-colors flex items-center gap-1.5 font-semibold">
              <Compass size={13} />探索数据 (Explore)
            </button>
          )}
          <button onClick={() => onDelete(objectType.id)}
            className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5">
            <Trash2 size={13} />删除对象
          </button>
        </div>
      </div>

      {/* Detail Tabs */}
      <div className="flex px-6 border-b border-gray-200 bg-white">
        {(['properties', 'mapping', 'metadata', 'links', 'actions'] as const).map(tab => {
          const tabLabels: Record<string, string> = {
            properties: '属性定义', mapping: '数据源映射', metadata: '元数据配置',
            links: '关联链接', actions: '应用操作'
          };
          return (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}>{tabLabels[tab]}</button>
          );
        })}
      </div>

      {/* Active Tab Panel */}
      <div className="flex-1 overflow-y-auto p-6">
        {activeTab === 'properties' && (
          <PropertiesTab
            objectType={objectType}
            onUpdate={onUpdate}
            newPropName={newPropName} setNewPropName={setNewPropName}
            newPropType={newPropType} setNewPropType={setNewPropType}
            handleAddProperty={handleAddProperty}
            handleTogglePrimaryKey={handleTogglePrimaryKey}
            handlePropertyFieldChange={handlePropertyFieldChange}
            handleRemoveProperty={handleRemoveProperty}
            sharedProperties={sharedProperties}
          />
        )}
        {activeTab === 'metadata' && (
          <MetadataTab objectType={objectType} onUpdate={onUpdate}
            handleMetaChange={handleMetaChange} domains={domains} interfaces={interfaces} />
        )}
        {activeTab === 'mapping' && (
          <MappingTab objectType={objectType} onUpdate={onUpdate}
            datasets={datasets} selectedDataset={selectedDataset}
            handleDatasetChange={handleDatasetChange} handleAutoMap={handleAutoMap}
            handlePropMappingChange={handlePropMappingChange} />
        )}
        {activeTab === 'links' && (
          <LinksTab objectType={objectType} relatedLinks={relatedLinks}
            onNavigateToLink={onNavigateToLink} />
        )}
        {activeTab === 'actions' && (
          <ActionsTab objectType={objectType} relatedActions={relatedActions}
            onNavigateToAction={onNavigateToAction} />
        )}
      </div>
    </div>
  );
}
