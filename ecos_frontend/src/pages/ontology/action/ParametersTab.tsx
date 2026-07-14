/**
 * ParametersTab — 操作参数定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Box, Plus, X } from 'lucide-react';
import type { ActionType, ActionParameter, ObjectType, ActionParamDataType } from '../../../types/ontology';

interface Props {
  actionType: ActionType;
  objectTypes: ObjectType[];
  newParamName: string; setNewParamName: (v: string) => void;
  newParamType: ActionParamDataType; setNewParamType: (v: ActionParamDataType) => void;
  newParamObjType: string; setNewParamObjType: (v: string) => void;
  handleAddParam: () => void;
  handleRemoveParam: (paramId: string) => void;
  handleParamFieldChange: (paramId: string, field: keyof ActionParameter, value: any) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ParametersTab({
  actionType, objectTypes, newParamName, setNewParamName, newParamType, setNewParamType,
  newParamObjType, setNewParamObjType, handleAddParam, handleRemoveParam, handleParamFieldChange, onNavigateToObject,
}: Props) {
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <p className="text-xs text-slate-500">定义运行该操作所必需的输入。可以是基本类型 (String, Integer) 或本系统内的对象实例 (Object Type)。</p>
        <div className="flex items-center gap-2">
          <input type="text" placeholder="新参数中文名称" value={newParamName} onChange={e => setNewParamName(e.target.value)}
            className="px-3 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden" />
          <select value={newParamType} onChange={e => setNewParamType(e.target.value as any)}
            className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden font-mono">
            <option value="string">string</option><option value="integer">integer</option><option value="decimal">decimal</option>
            <option value="boolean">boolean</option><option value="date">date</option><option value="object">object (对象实例)</option>
          </select>
          {newParamType === 'object' && (
            <select value={newParamObjType} onChange={e => setNewParamObjType(e.target.value)}
              className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden">
              {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
            </select>
          )}
          <button onClick={handleAddParam} className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1">
            <Plus size={13} />配置参数
          </button>
        </div>
      </div>
      <div className="border border-gray-200 rounded-lg overflow-hidden">
        <table className="w-full text-left border-collapse text-xs">
          <thead><tr className="bg-slate-50 border-b border-gray-200 text-slate-700 font-medium">
            <th className="py-2.5 px-4 w-12">必填</th><th className="py-2.5 px-4">显示名称</th><th className="py-2.5 px-4">参数变量 ID</th>
            <th className="py-2.5 px-4">参数数据类型</th><th className="py-2.5 px-4">对象绑定类型</th><th className="py-2.5 px-4">作用描述</th>
            <th className="py-2.5 px-4 text-center">操作</th>
          </tr></thead>
          <tbody className="divide-y divide-gray-100 text-slate-600">
            {actionType.parameters.map(param => (
              <tr key={param.id} className="hover:bg-slate-50/50">
                <td className="py-2.5 px-4"><input type="checkbox" checked={param.isRequired} onChange={e => handleParamFieldChange(param.id, 'isRequired', e.target.checked)} className="rounded border-gray-300 text-blue-600 h-3.5 w-3.5" /></td>
                <td className="py-2.5 px-4"><input type="text" value={param.displayName} onChange={e => handleParamFieldChange(param.id, 'displayName', e.target.value)} className="font-medium text-slate-900 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5" /></td>
                <td className="py-2.5 px-4 font-mono text-slate-500">{param.id}</td>
                <td className="py-2.5 px-4 font-mono text-slate-600">{param.dataType}</td>
                <td className="py-2.5 px-4">{param.dataType === 'object' ? (<div className="flex items-center gap-1 text-blue-600 font-semibold cursor-pointer" onClick={() => param.objectTypeId && onNavigateToObject(param.objectTypeId)}><Box size={12} /><span>{objectTypes.find(o => o.id === param.objectTypeId)?.displayName || param.objectTypeId}</span></div>) : (<span className="text-slate-400 font-mono">—</span>)}</td>
                <td className="py-2.5 px-4"><input type="text" value={param.description} onChange={e => handleParamFieldChange(param.id, 'description', e.target.value)} className="text-slate-500 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full" placeholder="配置描述信息" /></td>
                <td className="py-2.5 px-4 text-center"><button onClick={() => handleRemoveParam(param.id)} className="text-slate-400 hover:text-red-500 p-1 rounded"><X size={14} /></button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
