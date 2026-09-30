/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ActionType, ActionRule, ActionRuleType, ObjectType } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

interface RulesTabProps {
  actionType: ActionType;
  objectTypes: ObjectType[];
  handleAddRule: (type: ActionRuleType) => void;
  handleRemoveRule: (ruleId: string) => void;
  handleRuleChange: (ruleId: string, field: keyof ActionRule, value: any) => void;
  handleAddPropertyEdit: (ruleId: string, propertyId: string) => void;
  handlePropertyEditValueChange: (ruleId: string, propertyId: string, expr: string) => void;
  handleRemovePropertyEdit: (ruleId: string, propertyId: string) => void;
}

export default function RulesTab({
  actionType,
  objectTypes,
  handleAddRule,
  handleRemoveRule,
  handleRuleChange,
  handleAddPropertyEdit,
  handlePropertyEditValueChange,
  handleRemovePropertyEdit
}: RulesTabProps) {
  const { styles } = useTheme();

  return (
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
  );
}
