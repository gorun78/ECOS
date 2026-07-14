/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { InterfaceType, SharedProperty, Dataset, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';

// ==========================================
// 1. Interface View Component
// ==========================================
interface InterfaceViewProps {
  intf: InterfaceType;
  objectTypes: ObjectType[];
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export function InterfaceView({ intf, objectTypes, onDelete, onNavigateToObject }: InterfaceViewProps) {
  const implementingObjects = objectTypes.filter(ot => ot.interfaces?.includes(intf.id));

  return (
    <div className="flex flex-col h-full bg-white">
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-700 flex items-center justify-center">
            <LucideIcon name="Layers" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{intf.displayName}</h2>
              <span className="text-xs font-mono bg-indigo-100 text-indigo-700 px-1.5 py-0.5 rounded font-bold">
                {intf.apiName}
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{intf.description}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(intf.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200"
        >
          删除接口
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className="space-y-3">
          <h3 className="text-xs font-semibold text-slate-800">1. 规定接口属性 (Interface Properties)</h3>
          <p className="text-[11px] text-slate-500">
            任何实现此接口的对象类型都必须具备以下声明的属性，以保证其行为能够以抽象接口的形式在各种微应用中通用。
          </p>

          <div className="border border-slate-200 rounded-lg overflow-hidden">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-slate-50 border-b border-gray-200 text-slate-700 font-medium">
                  <th className="py-2.5 px-4">必填</th>
                  <th className="py-2.5 px-4">显示名称</th>
                  <th className="py-2.5 px-4">API 标识</th>
                  <th className="py-2.5 px-4">规定数据类型</th>
                  <th className="py-2.5 px-4">规格描述</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-slate-600">
                {intf.properties.map(p => (
                  <tr key={p.id} className="hover:bg-slate-50/50">
                    <td className="py-2.5 px-4">
                      <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${p.isRequired ? 'bg-red-100 text-red-800' : 'bg-slate-100 text-slate-500'}`}>
                        {p.isRequired ? 'REQUIRED' : 'OPTIONAL'}
                      </span>
                    </td>
                    <td className="py-2.5 px-4 font-semibold text-slate-900">{p.displayName}</td>
                    <td className="py-2.5 px-4 font-mono text-slate-500">{p.apiName}</td>
                    <td className="py-2.5 px-4 font-mono text-slate-600">{p.dataType}</td>
                    <td className="py-2.5 px-4 text-slate-500">{p.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="space-y-3 border-t border-gray-100 pt-6">
          <h3 className="text-xs font-semibold text-slate-800">2. 实现此接口的对象类型 ({implementingObjects.length})</h3>
          {implementingObjects.length === 0 ? (
            <div className="text-center py-6 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
              当前没有对象类型实现该接口，请在对象的「元数据配置」中勾选实现。
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-4">
              {implementingObjects.map(ot => (
                <div
                  key={ot.id}
                  onClick={() => onNavigateToObject(ot.id)}
                  className={`p-3 rounded-lg border-2 bg-white flex items-center gap-3 cursor-pointer hover:shadow-xs transition-shadow ${ot.color}`}
                >
                  <LucideIcon name={ot.icon} size={16} />
                  <div>
                    <div className="text-xs font-semibold">{ot.displayName}</div>
                    <div className="text-[10px] opacity-80 mt-0.5 font-mono">{ot.apiName}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ==========================================
// 2. Shared Property View Component
// ==========================================
interface SharedPropertyViewProps {
  sp: SharedProperty;
  objectTypes: ObjectType[];
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export function SharedPropertyView({ sp, objectTypes, onDelete, onNavigateToObject }: SharedPropertyViewProps) {
  const referencedObjects = objectTypes.filter(ot =>
    ot.properties.some(prop => prop.sharedPropertyId === sp.id)
  );

  return (
    <div className="flex flex-col h-full bg-white">
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-teal-200 bg-teal-50 text-teal-700 flex items-center justify-center">
            <LucideIcon name="Tag" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-lg font-semibold text-slate-900">{sp.displayName}</h2>
              <span className="text-xs font-mono bg-teal-100 text-teal-700 px-1.5 py-0.5 rounded font-bold">
                {sp.apiName}
              </span>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{sp.description}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(sp.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200"
        >
          删除共享属性
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-2">
          <h4 className="text-xs font-semibold text-slate-800">共享属性的作用机制</h4>
          <p className="text-[11px] text-slate-600 leading-relaxed">
            在 Foundry 中，<strong>共享属性类型 (Shared Property Types)</strong> 用于在整个系统层面上标准化业务术语。
            例如，“状态”或“创建时间”字段在许多对象中都有。一旦定义了共享属性，你就可以将其与多个不同对象类型的本地字段进行绑定，
            这让跨实体联合查询和通用前端组件呈现变得更轻量和统一。
          </p>
        </div>

        <div className="space-y-3">
          <h3 className="text-xs font-semibold text-slate-800">数据规范</h3>
          <div className="grid grid-cols-2 gap-4">
            <div className="p-3 border border-slate-200 rounded-lg">
              <span className="text-[10px] text-slate-400 uppercase block">共享属性 API 标识</span>
              <span className="font-mono text-xs font-semibold text-slate-800 mt-1 block">{sp.apiName}</span>
            </div>
            <div className="p-3 border border-slate-200 rounded-lg">
              <span className="text-[10px] text-slate-400 uppercase block">标准数据格式 (DataType)</span>
              <span className="font-mono text-xs font-semibold text-teal-600 mt-1 block uppercase">{sp.dataType}</span>
            </div>
          </div>
        </div>

        <div className="space-y-3 border-t border-gray-100 pt-6">
          <h3 className="text-xs font-semibold text-slate-800">绑定了此共享属性的对象类型 ({referencedObjects.length})</h3>
          {referencedObjects.length === 0 ? (
            <div className="text-center py-6 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">
              当前尚无任何对象类型绑定此共享属性。您可以在对象「属性定义」中为其设置关联。
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              {referencedObjects.map(ot => {
                const boundLocalProps = ot.properties.filter(p => p.sharedPropertyId === sp.id);
                return (
                  <div
                    key={ot.id}
                    onClick={() => onNavigateToObject(ot.id)}
                    className="p-4 border border-slate-200 rounded-xl hover:border-teal-400 hover:shadow-xs transition-all cursor-pointer bg-white group"
                  >
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`p-1.5 rounded-md border ${ot.color}`}>
                        <LucideIcon name={ot.icon} size={14} />
                      </span>
                      <span className="text-xs font-semibold text-slate-800 group-hover:text-teal-600">{ot.displayName}</span>
                    </div>
                    <div className="text-[10px] text-slate-500 space-y-1">
                      {boundLocalProps.map(lp => (
                        <div key={lp.id} className="flex justify-between font-mono bg-slate-50 p-1.5 rounded">
                          <span>本地属性：{lp.displayName} ({lp.id})</span>
                          <span className="text-slate-400">{lp.dataType}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ==========================================
// 3. Tabular Dataset Preview View Component
// ==========================================
interface DatasetViewProps {
  dataset: Dataset;
  objectTypes: ObjectType[];
  onNavigateToObject: (objectId: string) => void;
}

export function DatasetView({ dataset, objectTypes, onNavigateToObject }: DatasetViewProps) {
  const mappedObject = objectTypes.find(ot => ot.mapping.datasetId === dataset.id);

  return (
    <div className="flex flex-col h-full bg-white">
      <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50/50">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-slate-300 bg-white text-slate-700 flex items-center justify-center shadow-3xs">
            <LucideIcon name="Database" size={20} className="text-slate-500" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold font-mono text-slate-900">{dataset.name}</h2>
              <span className="text-[10px] font-bold bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded font-mono uppercase">
                原始数据表
              </span>
            </div>
            <p className="text-[10px] text-slate-500 font-mono mt-0.5">Foundry 路径: {dataset.path}</p>
          </div>
        </div>

        {mappedObject && (
          <div
            onClick={() => onNavigateToObject(mappedObject.id)}
            className={`px-3 py-1.5 rounded-lg border text-xs cursor-pointer hover:shadow-xs transition-shadow flex items-center gap-1.5 ${mappedObject.color}`}
          >
            <LucideIcon name="Layers" size={13} />
            <span>映射至对象：<strong>{mappedObject.displayName}</strong></span>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-hidden flex flex-col p-6 space-y-6">
        {/* Schema Summary */}
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-slate-800">表结构定义 (Schema)</h3>
          <div className="flex flex-wrap gap-2">
            {dataset.columns.map(col => (
              <div key={col.name} className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 px-2 py-1 rounded font-mono text-[10px]">
                <span className="text-slate-600 font-medium">{col.name}</span>
                <span className="text-slate-400 italic">({col.type})</span>
              </div>
            ))}
          </div>
        </div>

        {/* Tabular Preview */}
        <div className="flex-1 flex flex-col space-y-2 overflow-hidden border border-slate-200 rounded-lg">
          <div className="bg-slate-50 px-4 py-2 text-xs font-semibold text-slate-700 border-b border-slate-200 flex items-center gap-1.5">
            <LucideIcon name="Table" size={14} className="text-slate-500" />
            数据预览 (前 10 行记录)
          </div>
          <div className="flex-1 overflow-auto bg-white">
            <table className="w-full text-left border-collapse font-mono text-[11px]">
              <thead>
                <tr className="bg-slate-50/50 border-b border-slate-200 text-slate-500 font-medium sticky top-0 bg-white">
                  {dataset.columns.map(col => (
                    <th key={col.name} className="py-2 px-3">{col.name}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {dataset.sampleData.map((row, idx) => (
                  <tr key={idx} className="hover:bg-slate-50/30">
                    {dataset.columns.map(col => (
                      <td key={col.name} className="py-2.5 px-3 truncate max-w-[150px]" title={String(row[col.name] ?? '')}>
                        {row[col.name] === undefined ? <span className="text-slate-300">null</span> : String(row[col.name])}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
