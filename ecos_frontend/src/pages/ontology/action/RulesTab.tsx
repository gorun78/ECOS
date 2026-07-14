/**
 * RulesTab — 操作副作用规则 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Activity, Edit3, FilePlus, Trash, Trash2 } from 'lucide-react';
import DynamicIcon from '../../../components/ontology/DynamicIcon';
import type { ActionType, ActionRule, ActionRuleType, ObjectType } from '../../../types/ontology';

interface Props {
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
  actionType, objectTypes, handleAddRule, handleRemoveRule, handleRuleChange,
  handleAddPropertyEdit, handlePropertyEditValueChange, handleRemovePropertyEdit,
}: Props) {
  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div><h4 className="text-xs font-semibold text-slate-800">配置操作副作用 (Ontology Rules)</h4><p className="text-[11px] text-slate-500 mt-0.5">当此操作执行时，在本体库中会触发何种数据写入或修改指令。</p></div>
        <div className="flex gap-2">
          <button onClick={() => handleAddRule('modify_object')} className="border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs px-3 py-1.5 rounded flex items-center gap-1.5"><Edit3 size={13} />+ 修改对象属性</button>
          <button onClick={() => handleAddRule('create_object')} className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1.5 rounded flex items-center gap-1.5"><FilePlus size={13} />+ 新建对象实例</button>
          <button onClick={() => handleAddRule('delete_object')} className="border border-red-300 hover:bg-red-50 text-red-700 text-xs px-3 py-1.5 rounded flex items-center gap-1.5"><Trash2 size={13} className="text-red-500" />+ 删除对象实例</button>
        </div>
      </div>
      {actionType.rules.length === 0 ? (
        <div className="text-center py-12 border-2 border-dashed border-gray-200 rounded-xl text-slate-400 text-xs space-y-2"><Activity size={24} className="mx-auto text-slate-300" /><div>当前操作尚无任何生效的副作用逻辑，执行时将不改变任何本体数据。</div></div>
      ) : (
        <div className="space-y-4">
          {actionType.rules.map((rule, idx) => {
            const targetObjType = objectTypes.find(ot => ot.id === (rule.type === 'create_object' ? rule.targetObjectTypeId : actionType.parameters.find(p => p.id === rule.targetParameterId)?.objectTypeId));
            return (
              <div key={rule.id} className="border border-slate-200 rounded-xl p-5 bg-slate-50/50 space-y-4 shadow-2xs relative">
                <button onClick={() => handleRemoveRule(rule.id)} className="absolute top-4 right-4 text-slate-400 hover:text-red-500 p-1 rounded hover:bg-white" title="删除此逻辑块"><Trash size={14} /></button>
                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold bg-slate-200 text-slate-700 px-2 py-0.5 rounded-full font-mono">规则 {idx + 1}</span>
                  <div className="text-xs font-semibold text-slate-800">{rule.type === 'create_object' ? '新建对象实例 (Create Object)' : rule.type === 'delete_object' ? '删除对象实例 (Delete Object)' : '修改对象属性 (Modify Object Properties)'}</div>
                </div>
                <div className="grid grid-cols-2 gap-4 bg-white p-4 rounded-lg border border-slate-200">
                  {rule.type === 'create_object' ? (
                    <div className="space-y-1 text-xs"><label className="text-[11px] font-medium text-slate-600 block">实例化对象类型</label><select value={rule.targetObjectTypeId || ''} onChange={e => handleRuleChange(rule.id, 'targetObjectTypeId', e.target.value)} className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full">{objectTypes.map(ot => (<option key={ot.id} value={ot.id}>{ot.displayName} ({ot.id})</option>))}</select></div>
                  ) : rule.type === 'delete_object' ? (
                    <div className="space-y-1 text-xs"><label className="text-[11px] font-medium text-slate-600 block">目标删除参数 (绑定对象)</label><select value={rule.targetParameterId || ''} onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)} className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full"><option value="">-- 请选择需要删除的对象参数 --</option>{actionType.parameters.filter(p => p.dataType === 'object').map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  ) : (
                    <div className="space-y-1 text-xs"><label className="text-[11px] font-medium text-slate-600 block">目标修改参数 (绑定对象)</label><select value={rule.targetParameterId || ''} onChange={e => handleRuleChange(rule.id, 'targetParameterId', e.target.value)} className="px-2.5 py-1.5 border border-gray-300 rounded bg-white w-full"><option value="">-- 请选择对象类型参数 --</option>{actionType.parameters.filter(p => p.dataType === 'object').map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}</select></div>
                  )}
                  <div className="flex items-end justify-between text-xs"><div className="text-slate-500 text-[11px]">{targetObjType ? (<span>目标实体: <strong className="text-slate-800">{targetObjType.displayName}</strong></span>) : (<span className="italic">等待参数绑定</span>)}</div></div>
                </div>
                {targetObjType && (
                  <div className="space-y-3 bg-white p-4 rounded-lg border border-slate-200">
                    <div className="flex justify-between items-center border-b border-gray-100 pb-2">
                      <span className="text-xs font-semibold text-slate-800">具体字段修改行为</span>
                      <select onChange={e => { if (e.target.value) { handleAddPropertyEdit(rule.id, e.target.value); e.target.value = ''; } }} className="px-2 py-1 text-[11px] border border-gray-300 rounded bg-white">
                        <option value="">+ 添加待修改的字段...</option>
                        {targetObjType.properties.map(p => (<option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>))}
                      </select>
                    </div>
                    {rule.propertyEdits && rule.propertyEdits.length === 0 ? (
                      <div className="text-center py-4 text-slate-400 text-xs italic">暂未添加任何待更改属性。请在上方选择字段并写入表达式。</div>
                    ) : (
                      <div className="space-y-2">
                        {rule.propertyEdits?.map(edit => {
                          const propDef = targetObjType.properties.find(p => p.id === edit.propertyId);
                          return (
                            <div key={edit.propertyId} className="flex items-center gap-3 bg-slate-50 px-3 py-2 rounded border border-slate-150 text-xs">
                              <div className="w-1/3 flex items-center gap-1.5"><DynamicIcon name={targetObjType.primaryKey === edit.propertyId ? 'Key' : 'Tag'} size={12} className={targetObjType.primaryKey === edit.propertyId ? 'text-amber-500' : 'text-slate-400'} /><span className="font-semibold text-slate-800">{propDef?.displayName || edit.propertyId}</span><span className="text-[10px] text-slate-400 font-mono">({propDef?.dataType})</span></div>
                              <div className="flex-1 flex items-center gap-2"><span className="text-slate-400 text-[10px]">设为 ＝</span><input type="text" value={edit.valueExpression} onChange={e => handlePropertyEditValueChange(rule.id, edit.propertyId, e.target.value)} className="flex-1 px-2.5 py-1 text-xs border border-gray-300 rounded font-mono bg-white" placeholder={`例如 parameter.new_status 或 "MAINTENANCE"`} /></div>
                              <button onClick={() => handleRemovePropertyEdit(rule.id, edit.propertyId)} className="text-slate-400 hover:text-red-500 p-1"><Trash2 size={13} /></button>
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
