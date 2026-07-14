/**
 * SignatureTab — 函数签名与参数定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Plus, X } from 'lucide-react';
import type { FunctionType, FunctionParameter, ObjectType } from '../../../types/ontology';

interface SignatureTabProps {
  func: FunctionType;
  objectTypes: ObjectType[];
  handleFieldChange: (key: keyof FunctionType, value: any) => void;
  handleAddParam: () => void;
  handleRemoveParam: (name: string) => void;
  handleParamFieldChange: (name: string, field: keyof FunctionParameter, value: any) => void;
  newParamName: string;
  setNewParamName: (v: string) => void;
  newParamType: string;
  setNewParamType: (v: string) => void;
  newParamObjType: string;
  setNewParamObjType: (v: string) => void;
}

export default function SignatureTab({
  func, objectTypes, handleFieldChange,
  handleAddParam, handleRemoveParam, handleParamFieldChange,
  newParamName, setNewParamName, newParamType, setNewParamType,
  newParamObjType, setNewParamObjType,
}: SignatureTabProps) {
  return (
    <div className="flex-1 overflow-y-auto p-6 space-y-6">
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4">
        <h3 className="text-xs font-semibold text-slate-800">函数出参及归属 (Return Type / Output)</h3>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <label className="text-[10px] font-medium text-slate-600 block">返回参数类型 (Return Type)</label>
            <select value={func.returnType} onChange={e => handleFieldChange('returnType', e.target.value)}
              className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full font-mono">
              <option value="string">string (字符串)</option>
              <option value="integer">integer (整数)</option>
              <option value="decimal">decimal (小数)</option>
              <option value="boolean">boolean (布尔值)</option>
              <option value="date">date (日期)</option>
              <option value="timestamp">timestamp (时间戳)</option>
              <option value="ObjectType">ObjectType (特定对象实例)</option>
              <option value="ObjectTypeSet">ObjectTypeSet (对象集合)</option>
            </select>
          </div>
          {(func.returnType === 'ObjectType' || func.returnType === 'ObjectTypeSet') && (
            <div className="space-y-1">
              <label className="text-[10px] font-medium text-slate-600 block">返回对象类型</label>
              <select value={func.returnObjectTypeId || ''} onChange={e => handleFieldChange('returnObjectTypeId', e.target.value)}
                className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full">
                {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}
              </select>
            </div>
          )}
        </div>
        <div className="grid grid-cols-2 gap-4 text-xs">
          <div className="space-y-1">
            <label className="text-[10px] font-medium text-slate-600 block">API标识名称 (API Name)</label>
            <input type="text" value={func.apiName} onChange={e => handleFieldChange('apiName', e.target.value)}
              className="w-full px-2.5 py-1.5 border border-gray-300 rounded bg-white font-mono focus:outline-hidden" />
          </div>
          <div className="space-y-1">
            <label className="text-[10px] font-medium text-slate-600 block">关联的核心对象</label>
            <select value={func.associatedObjectType || ''} onChange={e => handleFieldChange('associatedObjectType', e.target.value)}
              className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full">
              <option value="">-- 无特定关联对象 (全局函数) --</option>
              {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}
            </select>
          </div>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex justify-between items-center">
          <h3 className="text-xs font-semibold text-slate-800">定义函数入参 (Input Parameters)</h3>
          <div className="flex items-center gap-2">
            <input type="text" placeholder="新参数变量名 (e.g. airportCode)" value={newParamName}
              onChange={e => setNewParamName(e.target.value)}
              className="px-2.5 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden font-mono" />
            <select value={newParamType} onChange={e => setNewParamType(e.target.value)}
              className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden font-mono">
              <option value="string">string</option>
              <option value="integer">integer</option>
              <option value="decimal">decimal</option>
              <option value="boolean">boolean</option>
              <option value="date">date</option>
              <option value="timestamp">timestamp</option>
              <option value="ObjectType">ObjectType (对象实例)</option>
              <option value="ObjectTypeSet">ObjectTypeSet (对象集合)</option>
            </select>
            {(newParamType === 'ObjectType' || newParamType === 'ObjectTypeSet') && (
              <select value={newParamObjType} onChange={e => setNewParamObjType(e.target.value)}
                className="px-2 py-1 text-xs border border-gray-300 rounded bg-white focus:outline-hidden">
                {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
              </select>
            )}
            <button onClick={handleAddParam}
              className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1">
              <Plus size={13} />添加参数
            </button>
          </div>
        </div>
        <div className="border border-gray-200 rounded-lg overflow-hidden">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-slate-50 border-b border-gray-200 text-slate-700 font-medium">
                <th className="py-2.5 px-4 w-12">必选</th>
                <th className="py-2.5 px-4">变量标识</th>
                <th className="py-2.5 px-4">参数类型</th>
                <th className="py-2.5 px-4">绑定实体类型</th>
                <th className="py-2.5 px-4">参数业务描述</th>
                <th className="py-2.5 px-4 text-center">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 text-slate-600">
              {func.parameters.length === 0 ? (
                <tr><td colSpan={6} className="text-center py-8 text-slate-400 italic">当前函数无输入参数，只能执行无参静态逻辑。</td></tr>
              ) : (
                func.parameters.map(p => (
                  <tr key={p.name} className="hover:bg-slate-50/50">
                    <td className="py-2.5 px-4">
                      <input type="checkbox" checked={p.isRequired}
                        onChange={e => handleParamFieldChange(p.name, 'isRequired', e.target.checked)}
                        className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 h-3.5 w-3.5" />
                    </td>
                    <td className="py-2.5 px-4 font-mono font-medium text-slate-900">{p.name}</td>
                    <td className="py-2.5 px-4 font-mono text-slate-500">{p.dataType}</td>
                    <td className="py-2.5 px-4">
                      {(p.dataType === 'ObjectType' || p.dataType === 'ObjectTypeSet') ? (
                        <select value={p.objectTypeId || ''}
                          onChange={e => handleParamFieldChange(p.name, 'objectTypeId', e.target.value)}
                          className="px-2 py-0.5 border border-gray-200 rounded bg-white focus:outline-hidden">
                          {objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName}</option>))}
                        </select>
                      ) : (<span className="text-slate-400 font-mono">—</span>)}
                    </td>
                    <td className="py-2.5 px-4">
                      <input type="text" value={p.description}
                        onChange={e => handleParamFieldChange(p.name, 'description', e.target.value)}
                        className="text-slate-500 border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full"
                        placeholder="配置描述信息" />
                    </td>
                    <td className="py-2.5 px-4 text-center">
                      <button onClick={() => handleRemoveParam(p.name)} className="text-slate-400 hover:text-red-500 p-1 rounded"><X size={14} /></button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
