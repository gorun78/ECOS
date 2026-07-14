/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { LinkType, ObjectType, Dataset } from '../../types/ontology';
import LucideIcon from './LucideIcon';

interface LinkTypeViewProps {
  linkType: LinkType;
  objectTypes: ObjectType[];
  datasets: Dataset[];
  onUpdate: (updated: LinkType) => void;
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function LinkTypeView({
  linkType,
  objectTypes,
  datasets,
  onUpdate,
  onDelete,
  onNavigateToObject
}: LinkTypeViewProps) {
  const sourceObj = objectTypes.find(o => o.id === linkType.sourceObjectType);
  const targetObj = objectTypes.find(o => o.id === linkType.targetObjectType);

  const selectedDataset = datasets.find(d => d.id === linkType.mapping.datasetId) || datasets[0];

  const handleFieldChange = (key: keyof LinkType, value: any) => {
    onUpdate({
      ...linkType,
      [key]: value
    });
  };

  const handleMappingFieldChange = (key: string, value: any) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        [key]: value
      }
    });
  };

  const handleFkChange = (key: 'sourceKey' | 'targetKey', value: string) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        foreignKeyMapping: {
          sourceKey: linkType.mapping.foreignKeyMapping?.sourceKey || '',
          targetKey: linkType.mapping.foreignKeyMapping?.targetKey || '',
          [key]: value
        }
      }
    });
  };

  const handleJoinChange = (key: 'sourceKey' | 'joinSourceKey' | 'joinTargetKey' | 'targetKey', value: string) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        joinTableMapping: {
          sourceKey: linkType.mapping.joinTableMapping?.sourceKey || '',
          joinSourceKey: linkType.mapping.joinTableMapping?.joinSourceKey || '',
          joinTargetKey: linkType.mapping.joinTableMapping?.joinTargetKey || '',
          targetKey: linkType.mapping.joinTableMapping?.targetKey || '',
          [key]: value
        }
      }
    });
  };

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Detail Header */}
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-slate-300 bg-white text-slate-700 flex items-center justify-center">
            <LucideIcon name="GitMerge" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{linkType.displayName}</h2>
              <span className="text-xs font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
                {linkType.apiName}
              </span>
              <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-semibold">
                {linkType.cardinality} 关联
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{linkType.description || '无详细描述'}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(linkType.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Trash2" size={13} />
          删除关联
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-8">
        {/* Visual Link Diagram */}
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-6">
          <h3 className="text-xs font-semibold text-slate-800 mb-4 flex items-center gap-1.5">
            <LucideIcon name="Eye" size={14} className="text-slate-500" />
            关系拓扑可视化
          </h3>
          <div className="flex items-center justify-center gap-6">
            {/* Source Object */}
            {sourceObj ? (
              <div
                onClick={() => onNavigateToObject(sourceObj.id)}
                className={`w-36 p-3 rounded-lg border-2 bg-white flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${sourceObj.color}`}
              >
                <LucideIcon name={sourceObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{sourceObj.displayName}</span>
                <span className="text-[10px] text-slate-400 font-mono mt-0.5">{sourceObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>源未配置</span>
              </div>
            )}

            {/* Link line with arrows and cardinality */}
            <div className="flex-1 max-w-[120px] flex flex-col items-center justify-center relative">
              <div className="text-[10px] font-bold text-slate-500 bg-slate-200 px-2 py-0.5 rounded-full font-mono mb-2">
                {linkType.cardinality}
              </div>
              <div className="w-full h-0.5 bg-slate-300 relative flex items-center justify-center">
                <div className="absolute right-0 w-1.5 h-1.5 border-t-2 border-r-2 border-slate-400 transform rotate-45" />
              </div>
              <div className="text-[10px] text-slate-400 font-medium mt-1 truncate max-w-full">
                {linkType.displayName}
              </div>
            </div>

            {/* Target Object */}
            {targetObj ? (
              <div
                onClick={() => onNavigateToObject(targetObj.id)}
                className={`w-36 p-3 rounded-lg border-2 bg-white flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${targetObj.color}`}
              >
                <LucideIcon name={targetObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{targetObj.displayName}</span>
                <span className="text-[10px] text-slate-400 font-mono mt-0.5">{targetObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>目标未配置</span>
              </div>
            )}
          </div>
        </div>

        {/* Configurations */}
        <div className="grid grid-cols-2 gap-6">
          <div className="space-y-4">
            <h4 className="text-xs font-semibold text-slate-800 border-b border-gray-100 pb-2">基础信息</h4>
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-xs text-slate-600 font-medium">显示名称 (Display Name)</label>
                <input
                  type="text"
                  value={linkType.displayName}
                  onChange={e => handleFieldChange('displayName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs text-slate-600 font-medium">API 标识名 (API Name)</label>
                <input
                  type="text"
                  value={linkType.apiName}
                  onChange={e => handleFieldChange('apiName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1">
                <label className="text-xs text-slate-600 font-medium">基数控制 (Cardinality)</label>
                <select
                  value={linkType.cardinality}
                  onChange={e => handleFieldChange('cardinality', e.target.value as any)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden"
                >
                  <option value="1:1">1:1 (一对一)</option>
                  <option value="1:N">1:N (一对多)</option>
                  <option value="N:1">N:1 (多对一)</option>
                  <option value="M:N">M:N (多对多)</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className="text-xs text-slate-600 font-medium">业务描述 (Description)</label>
                <textarea
                  value={linkType.description}
                  onChange={e => handleFieldChange('description', e.target.value)}
                  className="w-full h-16 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
            </div>
          </div>

          {/* Mapping settings */}
          <div className="space-y-4">
            <h4 className="text-xs font-semibold text-slate-800 border-b border-gray-100 pb-2">物理数据库机制映射</h4>
            
            <div className="space-y-3">
              <div className="space-y-1">
                <label className="text-xs text-slate-600 font-medium">关联映射机制 (Mapping Strategy)</label>
                <select
                  value={linkType.mapping.type}
                  onChange={e => handleMappingFieldChange('type', e.target.value as any)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden"
                >
                  <option value="foreign_key">外键映射 (Foreign Key - 适合 1:1, 1:N)</option>
                  <option value="join_table">关联表映射 (Join Table - 适合 M:N)</option>
                </select>
              </div>

              {/* FOREIGN KEY CONFIG */}
              {linkType.mapping.type === 'foreign_key' && (
                <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 space-y-3 text-xs">
                  <div className="font-semibold text-slate-800 text-[11px] mb-1">外键关系配置</div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">源对象关联键 ({sourceObj?.displayName})</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.sourceKey || ''}
                        onChange={e => handleFkChange('sourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">-- 请选择属性 --</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">目标对象主键/外键 ({targetObj?.displayName})</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.targetKey || ''}
                        onChange={e => handleFkChange('targetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">-- 请选择属性 --</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <p className="text-[9px] text-slate-400 mt-1 leading-relaxed">
                    系统在运行时将利用底层关联查询。确保源对象的关联键字段值能和目标对象的值相互咬合对应。
                  </p>
                </div>
              )}

              {/* JOIN TABLE CONFIG */}
              {linkType.mapping.type === 'join_table' && (
                <div className="bg-slate-50 p-4 rounded-lg border border-slate-200 space-y-3 text-xs">
                  <div className="font-semibold text-slate-800 text-[11px] mb-1">关联表 (中间表) 配置</div>
                  
                  <div className="space-y-1">
                    <label className="text-[10px] text-slate-500">选择包含关联关系的中间表数据集</label>
                    <select
                      value={linkType.mapping.datasetId || ''}
                      onChange={e => handleMappingFieldChange('datasetId', e.target.value)}
                      className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                    >
                      <option value="">-- 请选择中间表数据集 --</option>
                      {datasets.map(ds => (
                        <option key={ds.id} value={ds.id}>{ds.name} ({ds.id})</option>
                      ))}
                    </select>
                  </div>

                  <div className="grid grid-cols-2 gap-3 border-t border-slate-200/50 pt-2.5">
                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">源主键 ({sourceObj?.displayName})</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.sourceKey || ''}
                        onChange={e => handleJoinChange('sourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">-- 请选择源属性 --</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">中间表源外键字段</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinSourceKey || ''}
                        onChange={e => handleJoinChange('joinSourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white font-mono"
                      >
                        <option value="">-- 中间表字段 --</option>
                        {selectedDataset?.columns.map(col => (
                          <option key={col.name} value={col.name}>{col.name}</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">目标主键 ({targetObj?.displayName})</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.targetKey || ''}
                        onChange={e => handleJoinChange('targetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">-- 目标属性 --</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className="text-[10px] text-slate-500">中间表目标外键字段</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinTargetKey || ''}
                        onChange={e => handleJoinChange('joinTargetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white font-mono"
                      >
                        <option value="">-- 中间表字段 --</option>
                        {selectedDataset?.columns.map(col => (
                          <option key={col.name} value={col.name}>{col.name}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
