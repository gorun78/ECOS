/**
 * MappingTab — 数据源映射 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Database, Table, Layers, Wand2 } from 'lucide-react';
import DynamicIcon from '../../../components/ontology/DynamicIcon';
import type { MappingTabProps } from './types';

export default function MappingTab({
  objectType,
  datasets,
  selectedDataset,
  handleDatasetChange,
  handleAutoMap,
  handlePropMappingChange,
}: MappingTabProps) {
  return (
    <div className="space-y-6">
      <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 flex justify-between items-center">
        <div className="flex items-center gap-3">
          <Database className="text-slate-500" size={18} />
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
            <Wand2 size={13} />
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
              <Table size={14} />
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
              <Layers size={14} />
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
                      <DynamicIcon name={objectType.primaryKey === prop.id ? 'Key' : 'CircleDot'} size={12} />
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
  );
}
