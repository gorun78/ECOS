/**
 * PropertiesTab — 对象属性定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Plus, Key, X } from 'lucide-react';
import type { PropertiesTabProps } from './types';

export default function PropertiesTab({
  objectType,
  newPropName, setNewPropName,
  newPropType, setNewPropType,
  handleAddProperty,
  handleTogglePrimaryKey,
  handlePropertyFieldChange,
  handleRemoveProperty,
  sharedProperties,
}: PropertiesTabProps) {
  return (
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
            <Plus size={13} />
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
                    <Key size={14} className={objectType.primaryKey === prop.id ? 'fill-amber-500' : ''} />
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
                    <X size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
