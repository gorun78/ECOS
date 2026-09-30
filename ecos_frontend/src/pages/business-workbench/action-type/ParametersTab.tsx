/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ActionType, ActionParameter, ObjectType, ActionParamDataType } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

interface ParametersTabProps {
  actionType: ActionType;
  objectTypes: ObjectType[];
  newParamName: string;
  setNewParamName: (value: string) => void;
  newParamType: ActionParamDataType;
  setNewParamType: (value: ActionParamDataType) => void;
  newParamObjType: string;
  setNewParamObjType: (value: string) => void;
  handleAddParam: () => void;
  handleRemoveParam: (paramId: string) => void;
  handleParamFieldChange: (paramId: string, field: keyof ActionParameter, value: any) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ParametersTab({
  actionType,
  objectTypes,
  newParamName,
  setNewParamName,
  newParamType,
  setNewParamType,
  newParamObjType,
  setNewParamObjType,
  handleAddParam,
  handleRemoveParam,
  handleParamFieldChange,
  onNavigateToObject
}: ParametersTabProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <p className={`text-xs ${styles.cardTextMuted}`}>
          定义运行该操作所必需的输入。可以是基本类型 (String, Integer) 或本系统内的对象实例 (Object Type)。
        </p>
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="新参数中文名称"
            value={newParamName}
            onChange={e => setNewParamName(e.target.value)}
            className={`px-3 py-1 text-xs border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden`}
          />
          <select
            value={newParamType}
            onChange={e => setNewParamType(e.target.value as any)}
            className={`px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden font-mono`}
          >
            <option value="string">string</option>
            <option value="integer">integer</option>
            <option value="decimal">decimal</option>
            <option value="boolean">boolean</option>
            <option value="date">date</option>
            <option value="object">object (对象实例)</option>
          </select>
          {newParamType === 'object' && (
            <select
              value={newParamObjType}
              onChange={e => setNewParamObjType(e.target.value)}
              className={`px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
            >
              {objectTypes.map(ot => (
                <option key={ot.id} value={ot.id}>{ot.displayName}</option>
              ))}
            </select>
          )}
          <button
            onClick={handleAddParam}
            className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1"
          >
            <LucideIcon name="Plus" size={13} />
            配置参数
          </button>
        </div>
      </div>

      <div className={`border ${styles.appBorder} rounded-lg overflow-hidden`}>
        <table className="w-full text-left border-collapse text-xs">
          <thead>
            <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardText} font-medium`}>
              <th className="py-2.5 px-4 w-12">必填</th>
              <th className="py-2.5 px-4">显示名称</th>
              <th className="py-2.5 px-4">参数变量 ID</th>
              <th className="py-2.5 px-4">参数数据类型</th>
              <th className="py-2.5 px-4">对象绑定类型</th>
              <th className="py-2.5 px-4">作用描述</th>
              <th className="py-2.5 px-4 text-center">操作</th>
            </tr>
          </thead>
          <tbody className={`divide-y ${styles.divider} ${styles.cardTextMuted}`}>
            {actionType.parameters.map(param => (
              <tr key={param.id} className="hover:bg-blue-50/20">
                <td className="py-2.5 px-4">
                  <input
                    type="checkbox"
                    checked={param.isRequired}
                    onChange={e => handleParamFieldChange(param.id, 'isRequired', e.target.checked)}
                    className="rounded border-gray-300 text-blue-600 focus:ring-blue-500 h-3.5 w-3.5"
                  />
                </td>
                <td className="py-2.5 px-4">
                  <input
                    type="text"
                    value={param.displayName}
                    onChange={e => handleParamFieldChange(param.id, 'displayName', e.target.value)}
                    className={`font-medium ${styles.cardText} border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5`}
                  />
                </td>
                <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{param.id}</td>
                <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{param.dataType}</td>
                <td className="py-2.5 px-4">
                  {param.dataType === 'object' ? (
                    <div className="flex items-center gap-1 text-blue-600 font-semibold cursor-pointer" onClick={() => param.objectTypeId && onNavigateToObject(param.objectTypeId)}>
                      <LucideIcon name="Box" size={12} />
                      <span>{objectTypes.find(o => o.id === param.objectTypeId)?.displayName || param.objectTypeId}</span>
                    </div>
                  ) : (
                    <span className={`${styles.muted} font-mono`}>—</span>
                  )}
                </td>
                <td className="py-2.5 px-4">
                  <input
                    type="text"
                    value={param.description}
                    onChange={e => handleParamFieldChange(param.id, 'description', e.target.value)}
                    className={`${styles.cardTextMuted} border-b border-transparent hover:border-blue-300 focus:border-blue-500 focus:outline-hidden py-0.5 w-full`}
                    placeholder="配置描述信息"
                  />
                </td>
                <td className="py-2.5 px-4 text-center">
                  <button
                    onClick={() => handleRemoveParam(param.id)}
                    className={`p-1 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500 rounded transition-opacity`}
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
  );
}
