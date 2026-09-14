/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ActionType, ActionParameter, ActionRule, ActionValidationRule, ObjectType, ActionParamDataType, ActionRuleType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';

interface ActionTypeViewProps {
  actionType: ActionType;
  objectTypes: ObjectType[];
  onUpdate: (updated: ActionType) => void;
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function ActionTypeView({
  actionType,
  objectTypes,
  onUpdate,
  onDelete,
  onNavigateToObject
}: ActionTypeViewProps) {
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<'parameters' | 'rules' | 'validation' | 'layout'>('parameters');
  const [newParamName, setNewParamName] = useState('');
  const [newParamType, setNewParamType] = useState<ActionParamDataType>('string');
  const [newParamObjType, setNewParamObjType] = useState(objectTypes[0]?.id || '');

  // Layout-specific state
  const [newSectionTitle, setNewSectionTitle] = useState('');

  const [newValName, setNewValName] = useState('');
  const [newValExpression, setNewValExpression] = useState('');
  const [newValError, setNewValError] = useState('');

  const handleFieldChange = (key: keyof ActionType, value: any) => {
    onUpdate({
      ...actionType,
      [key]: value
    });
  };

  // Add parameter
  const handleAddParam = () => {
    if (!newParamName.trim()) return;
    const paramId = newParamName.trim().replace(/\s+/g, '_').toLowerCase() + '_param';
    const newParam: ActionParameter = {
      id: paramId,
      displayName: newParamName,
      dataType: newParamType,
      isRequired: true,
      description: `关于参数 ${newParamName} 的用途。`,
      objectTypeId: newParamType === 'object' ? newParamObjType : undefined
    };

    onUpdate({
      ...actionType,
      parameters: [...actionType.parameters, newParam]
    });
    setNewParamName('');
  };

  // Remove parameter
  const handleRemoveParam = (paramId: string) => {
    onUpdate({
      ...actionType,
      parameters: actionType.parameters.filter(p => p.id !== paramId),
      // Also remove any rules referencing this parameter to avoid dangling references
      rules: actionType.rules.filter(r => r.targetParameterId !== paramId)
    });
  };

  // Update specific parameter fields
  const handleParamFieldChange = (paramId: string, field: keyof ActionParameter, value: any) => {
    onUpdate({
      ...actionType,
      parameters: actionType.parameters.map(p =>
        p.id === paramId ? { ...p, [field]: value } : p
      )
    });
  };

  // Add a new Rule
  const handleAddRule = (type: ActionRuleType) => {
    const ruleId = `rule_${Date.now()}`;
    const newRule: ActionRule = {
      id: ruleId,
      type,
      targetObjectTypeId: type === 'create_object' ? objectTypes[0]?.id : undefined,
      targetParameterId: type !== 'create_object' ? actionType.parameters.find(p => p.dataType === 'object')?.id : undefined,
      propertyEdits: []
    };

    onUpdate({
      ...actionType,
      rules: [...actionType.rules, newRule]
    });
  };

  // Delete a rule
  const handleRemoveRule = (ruleId: string) => {
    onUpdate({
      ...actionType,
      rules: actionType.rules.filter(r => r.id !== ruleId)
    });
  };

  // Update Rule properties
  const handleRuleChange = (ruleId: string, field: keyof ActionRule, value: any) => {
    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, [field]: value } : r
      )
    });
  };

  // Add property edit to a rule
  const handleAddPropertyEdit = (ruleId: string, propertyId: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = [...(rule.propertyEdits || [])];
    if (propertyEdits.some(pe => pe.propertyId === propertyId)) return; // Avoid duplicate

    propertyEdits.push({
      propertyId,
      valueExpression: 'parameter.' // Default expression stub
    });

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Update property edit expression
  const handlePropertyEditValueChange = (ruleId: string, propertyId: string, expr: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = (rule.propertyEdits || []).map(pe =>
      pe.propertyId === propertyId ? { ...pe, valueExpression: expr } : pe
    );

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Remove property edit from a rule
  const handleRemovePropertyEdit = (ruleId: string, propertyId: string) => {
    const rule = actionType.rules.find(r => r.id === ruleId);
    if (!rule) return;

    const propertyEdits = (rule.propertyEdits || []).filter(pe => pe.propertyId !== propertyId);

    onUpdate({
      ...actionType,
      rules: actionType.rules.map(r =>
        r.id === ruleId ? { ...r, propertyEdits } : r
      )
    });
  };

  // Add validation rule
  const handleAddValidation = () => {
    if (!newValName.trim() || !newValExpression.trim()) return;
    const newVal: ActionValidationRule = {
      id: `val_${Date.now()}`,
      displayName: newValName,
      expression: newValExpression,
      errorMessage: newValError || '验证未通过，请检查您的输入参数。'
    };

    onUpdate({
      ...actionType,
      validationRules: [...actionType.validationRules, newVal]
    });

    setNewValName('');
    setNewValExpression('');
    setNewValError('');
  };

  // Remove validation rule
  const handleRemoveValidation = (valId: string) => {
    onUpdate({
      ...actionType,
      validationRules: actionType.validationRules.filter(v => v.id !== valId)
    });
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-full border border-amber-300 bg-amber-50 text-amber-700 flex items-center justify-center">
            <LucideIcon name="Zap" size={20} className="fill-amber-500" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{actionType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.appBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {actionType.apiName}
              </span>
              <span className={`text-xs ${styles.appBg} ${styles.cardTextMuted} px-2 py-0.5 rounded-full font-mono`}>
                {actionType.rules.length} 副作用规则
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{actionType.description || '无详细描述'}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(actionType.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <LucideIcon name="Trash2" size={13} />
          删除操作
        </button>
      </div>

      {/* Tab bar */}
      <div className={`flex px-6 border-b ${styles.appBorder} ${styles.cardBg}`}>
        {(['parameters', 'rules', 'validation', 'layout'] as const).map(tab => {
          const labels = {
            parameters: '1. 参数定义 (Parameters)',
            rules: '2. 副作用逻辑 (Rules / Effects)',
            validation: '3. 提交前验证 (Validation)',
            layout: '4. 表单与布局 (Form & Layout)'
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-100 opacity-80`
              }`}
            >
              {labels[tab]}
            </button>
          );
        })}
      </div>

      {/* Tab panel */}
      <div className="flex-1 overflow-y-auto p-6">
        
        {/* PARAMETERS TAB */}
        {activeTab === 'parameters' && (
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
        )}

        {/* RULES / EFFECTS TAB */}
        {activeTab === 'rules' && (
          <div className="space-y-6">
            <div className="flex justify-between items-center">
              <div>
                <h4 className={`text-xs font-semibold ${styles.cardText}`}>配置操作副作用 (Ontology Rules)</h4>
                <p className={`text-[11px] ${styles.cardTextMuted} mt-0.5`}>当此操作执行时，在本体库中会触发何种数据写入或修改指令。</p>
              </div>
              <div className="flex gap-2">
                <button
                  onClick={() => handleAddRule('modify_object')}
                  className={`border ${styles.sidebarBorder} hover:bg-blue-50/20 ${styles.cardText} text-xs px-3 py-1.5 rounded transition-all flex items-center gap-1.5`}
                >
                  <LucideIcon name="Edit3" size={13} />
                  + 修改对象属性
                </button>
                <button
                  onClick={() => handleAddRule('create_object')}
                  className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1.5 rounded transition-all flex items-center gap-1.5"
                >
                  <LucideIcon name="FilePlus" size={13} />
                  + 新建对象实例
                </button>
                <button
                  onClick={() => handleAddRule('delete_object')}
                  className="border border-red-300 hover:bg-red-50 text-red-700 text-xs px-3 py-1.5 rounded transition-all flex items-center gap-1.5"
                >
                  <LucideIcon name="Trash2" size={13} className="text-red-500" />
                  + 删除对象实例
                </button>
              </div>
            </div>

            {actionType.rules.length === 0 ? (
              <div className={`text-center py-12 border-2 border-dashed ${styles.sidebarBorder} rounded-xl ${styles.cardTextMuted} text-xs space-y-2`}>
                <LucideIcon name="Activity" size={24} className={`mx-auto ${styles.muted}`} />
                <div>当前操作尚无任何生效的副作用逻辑，执行时将不改变任何本体数据。</div>
              </div>
            ) : (
              <div className="space-y-4">
                {actionType.rules.map((rule, idx) => {
                  const targetObjType = objectTypes.find(
                    ot => ot.id === (rule.type === 'create_object' ? rule.targetObjectTypeId : 
                      actionType.parameters.find(p => p.id === rule.targetParameterId)?.objectTypeId)
                  );

                  return (
                    <div key={rule.id} className={`border ${styles.cardBorder} rounded-xl p-5 ${styles.appBg} space-y-4 shadow-2xs relative`}>
                      <button
                        onClick={() => handleRemoveRule(rule.id)}
                        className={`absolute top-4 right-4 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500 transition-colors p-1 rounded ${styles.cardBg}`}
                        title="删除此逻辑块"
                      >
                        <LucideIcon name="Trash" size={14} />
                      </button>

                      <div className="flex items-center gap-2">
                        <span className={`text-xs font-semibold ${styles.sidebarBg} ${styles.cardText} px-2 py-0.5 rounded-full font-mono`}>
                          规则 {idx + 1}
                        </span>
                        <div className={`text-xs font-semibold ${styles.cardText}`}>
                          {rule.type === 'create_object' ? '新建对象实例 (Create Object)' : 
                           rule.type === 'delete_object' ? '删除对象实例 (Delete Object)' :
                           '修改对象属性 (Modify Object Properties)'}
                        </div>
                      </div>

                      <div className={`grid grid-cols-2 gap-4 ${styles.cardBg} p-4 rounded-lg border ${styles.cardBorder}`}>
                        {/* Selector Target */}
                        {rule.type === 'create_object' ? (
                          <div className="space-y-1 text-xs">
                            <label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>实例化对象类型</label>
                            <select
                              value={rule.targetObjectTypeId || ''}
                              onChange={e => handleRuleChange(rule.id, 'targetObjectTypeId', e.target.value)}
                              className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full`}
                            >
                              {objectTypes.map(ot => (
                                <option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>
                              ))}
                            </select>
                          </div>
                        ) : rule.type === 'delete_object' ? (
                          <div className="space-y-1 text-xs">
                            <label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>目标删除参数 (绑定对象)</label>
                            <select
                              value={rule.targetParameterId || ''}
                              onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)}
                              className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full`}
                            >
                              <option value="">-- 请选择需要删除的对象参数 --</option>
                              {actionType.parameters
                                .filter(p => p.dataType === 'object')
                                .map(p => (
                                  <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                                ))}
                            </select>
                          </div>
                        ) : (
                          <div className="space-y-1 text-xs">
                            <label className={`text-[11px] font-medium ${styles.cardTextMuted} block`}>目标修改参数 (绑定对象)</label>
                            <select
                              value={rule.targetParameterId || ''}
                              onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)}
                              className={`px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.inputBg} w-full`}
                            >
                              <option value="">-- 请选择对象类型参数 --</option>
                              {actionType.parameters
                                .filter(p => p.dataType === 'object')
                                .map(p => (
                                  <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                                ))}
                            </select>
                          </div>
                        )}

                        <div className="flex items-end justify-between text-xs">
                          <div className={`${styles.cardTextMuted} text-[11px]`}>
                            {targetObjType ? (
                              <span>已关联至：<strong>{targetObjType.displayName}</strong> ({targetObjType.properties.length} 可映射属性)</span>
                            ) : (
                              <span className="text-red-500">※ 未关联有效实体类型</span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Property edit statements */}
                      {targetObjType && (
                        <div className={`space-y-3 ${styles.cardBg} p-4 rounded-lg border ${styles.cardBorder}`}>
                          <div className={`flex justify-between items-center border-b ${styles.divider} pb-2`}>
                            <span className={`text-xs font-semibold ${styles.cardText}`}>具体字段修改行为</span>
                            
                            {/* Selector to add property edit */}
                            <select
                              onChange={e => {
                                if (e.target.value) {
                                  handleAddPropertyEdit(rule.id, e.target.value);
                                  e.target.value = ''; // Reset select
                                }
                              }}
                              className={`px-2 py-1 text-[11px] border ${styles.inputBorder} rounded ${styles.inputBg}`}
                            >
                              <option value="">+ 添加待修改的字段...</option>
                              {targetObjType.properties.map(p => (
                                <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                              ))}
                            </select>
                          </div>

                          {rule.propertyEdits && rule.propertyEdits.length === 0 ? (
                            <div className={`text-center py-4 ${styles.cardTextMuted} text-xs italic`}>
                              暂未添加任何待更改属性。请在上方选择字段并写入表达式。
                            </div>
                          ) : (
                            <div className="space-y-2">
                              {rule.propertyEdits?.map(edit => {
                                const propDef = targetObjType.properties.find(p => p.id === edit.propertyId);
                                return (
                                  <div key={edit.propertyId} className={`flex items-center gap-3 ${styles.cardBg} px-3 py-2 rounded border ${styles.divider} text-xs`}>
                                    <div className="w-1/3 flex items-center gap-1.5">
                                      <LucideIcon name={targetObjType.primaryKey === edit.propertyId ? 'Key' : 'Tag'} size={12} className={targetObjType.primaryKey === edit.propertyId ? 'text-amber-500' : styles.cardTextMuted} />
                                      <span className={`font-semibold ${styles.cardText}`}>{propDef?.displayName || edit.propertyId}</span>
                                      <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>({propDef?.dataType})</span>
                                    </div>
                                    <div className="flex-1 flex items-center gap-2">
                                      <span className={`${styles.cardTextMuted} text-[10px]`}>设为 ＝</span>
                                      <input
                                        type="text"
                                        value={edit.valueExpression}
                                        onChange={e => handlePropertyEditValueChange(rule.id, edit.propertyId, e.target.value)}
                                        className={`flex-1 px-2.5 py-1 text-xs border ${styles.inputBorder} rounded font-mono ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
                                        placeholder="例如 parameter.new_status 或 &quot;MAINTENANCE&quot;"
                                      />
                                    </div>
                                    <button
                                      onClick={() => handleRemovePropertyEdit(rule.id, edit.propertyId)}
                                      className={`p-1 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500`}
                                    >
                                      <LucideIcon name="Trash2" size={13} />
                                    </button>
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* VALIDATIONS TAB */}
        {activeTab === 'validation' && (
          <div className="space-y-6">
            <p className={`text-xs ${styles.cardTextMuted}`}>
              定义执行操作前的拦截限制条件。只有当验证表达式 (Validation Expression) 计算结果为真 (True) 时，该操作才允许被提交到本体。
            </p>

            <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
              <h4 className={`text-xs font-semibold ${styles.cardText}`}>新建验证安全规则</h4>
              <div className="grid grid-cols-3 gap-4">
                <div className="space-y-1">
                  <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证项名称</label>
                  <input
                    type="text"
                    placeholder="如：状态合法性校验"
                    value={newValName}
                    onChange={e => setNewValName(e.target.value)}
                    className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
                  />
                </div>
                <div className="space-y-1 col-span-2">
                  <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证公式/表达式 (Logic Expression)</label>
                  <input
                    type="text"
                    placeholder="如：parameter.new_status_param IN [&quot;ON_TIME&quot;, &quot;DELAYED&quot;]"
                    value={newValExpression}
                    onChange={e => setNewValExpression(e.target.value)}
                    className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} font-mono focus:outline-hidden`}
                  />
                </div>
              </div>
              <div className="space-y-1">
                <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>验证不通过时的报错警告信息 (Error Message)</label>
                <input
                  type="text"
                  placeholder="如：状态代码错误，航班状态必须设定为合法选项。"
                  value={newValError}
                  onChange={e => setNewValError(e.target.value)}
                  className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
                />
              </div>
              <button
                onClick={handleAddValidation}
                className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-4 py-1.5 rounded font-medium transition-colors flex items-center gap-1.5"
              >
                <LucideIcon name="Shield" size={13} />
                添加拦截验证规则
              </button>
            </div>

            <div className="space-y-4">
              <h4 className={`text-xs font-semibold ${styles.cardText}`}>已生效的验证列表 ({actionType.validationRules.length})</h4>
              {actionType.validationRules.length === 0 ? (
                <div className={`text-center py-8 border border-dashed ${styles.sidebarBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
                  暂无拦截验证规则，该操作在调用时无入参安全性限制。
                </div>
              ) : (
                <div className="space-y-3">
                  {actionType.validationRules.map(val => (
                    <div key={val.id} className={`p-4 border ${styles.cardBorder} rounded-lg ${styles.cardBg} shadow-3xs flex items-start justify-between`}>
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <span className="p-1 rounded-full bg-emerald-50 text-emerald-600">
                            <LucideIcon name="ShieldCheck" size={14} />
                          </span>
                          <span className={`text-xs font-semibold ${styles.cardText}`}>{val.displayName}</span>
                        </div>
                        <div className={`font-mono text-[10px] ${styles.appBg} ${styles.cardTextMuted} px-2 py-1 rounded border ${styles.divider}`}>
                          {val.expression}
                        </div>
                        <div className="text-[10px] text-red-500 font-medium">
                          <strong>警告文案:</strong> {val.errorMessage}
                        </div>
                      </div>
                      <button
                        onClick={() => handleRemoveValidation(val.id)}
                        className={`p-1 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500`}
                        title="删除规则"
                      >
                        <LucideIcon name="Trash2" size={14} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* LAYOUT / FORM TAB */}
        {activeTab === 'layout' && (() => {
          // Initialize default form layout if not defined
          const layout = actionType.formLayout || {
            sections: [
              {
                title: '基本参数信息',
                parameterIds: actionType.parameters.map(p => p.id)
              }
            ],
            buttonText: `确认执行: ${actionType.displayName}`
          };

          const updateLayout = (updatedLayout: typeof layout) => {
            onUpdate({
              ...actionType,
              formLayout: updatedLayout
            });
          };

          const handleAddSection = () => {
            if (!newSectionTitle.trim()) return;
            updateLayout({
              ...layout,
              sections: [
                ...layout.sections,
                {
                  title: newSectionTitle.trim(),
                  parameterIds: []
                }
              ]
            });
            setNewSectionTitle('');
          };

          const handleRemoveSection = (sectionIndex: number) => {
            const removedSec = layout.sections[sectionIndex];
            // Move its parameters back to the first section to avoid losing them
            const firstSec = layout.sections[0];
            const updatedSections = layout.sections.filter((_, idx) => idx !== sectionIndex);
            if (firstSec && removedSec) {
              updatedSections[0] = {
                ...updatedSections[0],
                parameterIds: Array.from(new Set([...updatedSections[0].parameterIds, ...removedSec.parameterIds]))
              };
            }
            updateLayout({
              ...layout,
              sections: updatedSections
            });
          };

          const handleAddParamToSection = (sectionIndex: number, paramId: string) => {
            // Remove from other sections first
            const cleanedSections = layout.sections.map(sec => ({
              ...sec,
              parameterIds: sec.parameterIds.filter(id => id !== paramId)
            }));
            
            // Add to this section
            cleanedSections[sectionIndex].parameterIds.push(paramId);
            
            updateLayout({
              ...layout,
              sections: cleanedSections
            });
          };

          return (
            <div className="space-y-6">
              <p className={`text-xs ${styles.cardTextMuted}`}>
                配置当用户在主应用 Workshop 或 Object Explorer 中运行此操作时，所呈现的操作弹窗表单布局与文案。
              </p>

              <div className="grid grid-cols-3 gap-6">
                {/* Visual Customization */}
                <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4 col-span-1 h-fit`}>
                  <h4 className={`text-xs font-semibold ${styles.cardText}`}>表单行为文案</h4>
                  
                  <div className="space-y-1">
                    <label className={`text-[10px] font-medium ${styles.cardTextMuted} block`}>提交按钮文字 (Submit Text)</label>
                    <input
                      type="text"
                      value={layout.buttonText || ''}
                      onChange={e => updateLayout({ ...layout, buttonText: e.target.value })}
                      className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
                      placeholder="如：确认修改状态"
                    />
                  </div>

                  <hr className={styles.divider} />

                  <div className="space-y-2">
                    <h5 className={`text-[11px] font-semibold ${styles.cardText}`}>添加新的表单区块 (Section)</h5>
                    <div className="flex gap-2">
                      <input
                        type="text"
                        placeholder="区块名称 (e.g. 附加选项)"
                        value={newSectionTitle}
                        onChange={e => setNewSectionTitle(e.target.value)}
                        className={`flex-1 px-2 py-1 text-xs border ${styles.inputBorder} rounded focus:outline-hidden`}
                      />
                      <button
                        onClick={handleAddSection}
                        className="bg-[var(--card,#0F172A)] text-white hover:bg-[var(--muted,#1E293B)] text-[11px] px-2.5 py-1 rounded transition-colors"
                      >
                        + 区块
                      </button>
                    </div>
                  </div>
                </div>

                {/* Form layout builder panels */}
                <div className="col-span-2 space-y-4">
                  <h4 className={`text-xs font-semibold ${styles.cardText}`}>区块划分与字段归属</h4>
                  
                  <div className="space-y-4">
                    {layout.sections.map((section, secIdx) => {
                      // Find parameters not currently inside this section to allow adding
                      const availableParams = actionType.parameters.filter(
                        p => !section.parameterIds.includes(p.id)
                      );

                      return (
                        <div key={secIdx} className={`border ${styles.cardBorder} rounded-xl p-4 ${styles.cardBg} space-y-3 shadow-2xs relative`}>
                          {secIdx > 0 && (
                            <button
                              onClick={() => handleRemoveSection(secIdx)}
                              className={`absolute top-4 right-4 ${styles.cardTextMuted} opacity-70 hover:opacity-100 hover:text-red-500 transition-colors`}
                              title="移除此区块并将字段退回第一区块"
                            >
                              <LucideIcon name="Trash" size={13} />
                            </button>
                          )}

                          <div className={`flex items-center gap-2 border-b ${styles.divider} pb-2`}>
                            <LucideIcon name="Layers" size={13} className="text-blue-500" />
                            <span className={`text-xs font-semibold ${styles.cardText}`}>{section.title}</span>
                            <span className={`text-[10px] ${styles.cardTextMuted}`}>({section.parameterIds.length} 字段)</span>
                          </div>

                          {/* Parameter list in this section */}
                          {section.parameterIds.length === 0 ? (
                            <div className={`text-center py-4 ${styles.cardTextMuted} italic text-[11px]`}>
                              该区块目前为空，请在下方选择参数移入此区。
                            </div>
                          ) : (
                            <div className="space-y-1.5">
                              {section.parameterIds.map(paramId => {
                                const pDef = actionType.parameters.find(p => p.id === paramId);
                                if (!pDef) return null;
                                return (
                                  <div key={paramId} className={`flex justify-between items-center ${styles.appBg} px-3 py-1.5 rounded border ${styles.divider} text-xs`}>
                                    <div className="flex items-center gap-2">
                                      <span className={`font-mono ${styles.cardTextMuted} text-[10px]`}>[{pDef.dataType}]</span>
                                      <span className={`font-semibold ${styles.cardText}`}>{pDef.displayName}</span>
                                      <span className={`${styles.cardTextMuted} text-[10px] font-mono`}>({pDef.id})</span>
                                    </div>
                                    <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded-full font-mono`}>
                                      {pDef.isRequired ? '必填' : '选填'}
                                    </span>
                                  </div>
                                );
                              })}
                            </div>
                          )}

                          {/* Add parameter to section dropdown */}
                          {availableParams.length > 0 && (
                            <div className={`flex items-center justify-end gap-2 text-[11px] pt-1 border-t ${styles.divider} mt-2`}>
                              <span className={styles.cardTextMuted}>划转字段入此区:</span>
                              <select
                                onChange={e => {
                                  if (e.target.value) {
                                    handleAddParamToSection(secIdx, e.target.value);
                                    e.target.value = '';
                                  }
                                }}
                                className={`px-2 py-0.5 border ${styles.inputBorder} rounded ${styles.inputBg} text-[10px] focus:outline-hidden`}
                              >
                                <option value="">-- 选择可移入的变量 --</option>
                                {availableParams.map(p => (
                                  <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                                ))}
                              </select>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            </div>
          );
        })()}
      </div>
    </div>
  );
}
