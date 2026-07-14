/**
 * ValidationTab — 操作验证规则 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { Shield, ShieldCheck, Trash2 } from 'lucide-react';
import type { ActionType, ActionValidationRule } from '../../../types/ontology';

interface Props {
  actionType: ActionType;
  newValName: string; setNewValName: (v: string) => void;
  newValExpression: string; setNewValExpression: (v: string) => void;
  newValError: string; setNewValError: (v: string) => void;
  handleAddValidation: () => void;
  handleRemoveValidation: (valId: string) => void;
}

export default function ValidationTab({
  actionType, newValName, setNewValName, newValExpression, setNewValExpression,
  newValError, setNewValError, handleAddValidation, handleRemoveValidation,
}: Props) {
  return (
    <div className="space-y-6">
      <p className="text-xs text-slate-500">定义执行操作前的拦截限制条件。只有当验证表达式计算结果为真 (True) 时，该操作才允许被提交到本体。</p>
      <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 space-y-4">
        <h4 className="text-xs font-semibold text-slate-800">新建验证安全规则</h4>
        <div className="grid grid-cols-3 gap-4">
          <div className="space-y-1"><label className="text-[10px] font-medium text-slate-600 block">验证项名称</label><input type="text" placeholder="如：状态合法性校验" value={newValName} onChange={e => setNewValName(e.target.value)} className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden" /></div>
          <div className="space-y-1 col-span-2"><label className="text-[10px] font-medium text-slate-600 block">验证公式/表达式</label><input type="text" placeholder={`如：parameter.new_status_param IN ["ON_TIME", "DELAYED"]`} value={newValExpression} onChange={e => setNewValExpression(e.target.value)} className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white font-mono focus:outline-hidden" /></div>
        </div>
        <div className="space-y-1"><label className="text-[10px] font-medium text-slate-600 block">验证不通过时的报错警告信息</label><input type="text" placeholder="如：状态代码错误，航班状态必须设定为合法选项。" value={newValError} onChange={e => setNewValError(e.target.value)} className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden" /></div>
        <button onClick={handleAddValidation} className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-4 py-1.5 rounded font-medium transition-colors flex items-center gap-1.5"><Shield size={13} />添加拦截验证规则</button>
      </div>
      <div className="space-y-4">
        <h4 className="text-xs font-semibold text-slate-800">已生效的验证列表 ({actionType.validationRules.length})</h4>
        {actionType.validationRules.length === 0 ? (
          <div className="text-center py-8 border border-dashed border-gray-200 rounded-lg text-slate-400 text-xs">暂无拦截验证规则，该操作在调用时无入参安全性限制。</div>
        ) : (
          <div className="space-y-3">
            {actionType.validationRules.map(val => (
              <div key={val.id} className="p-4 border border-slate-200 rounded-lg bg-white shadow-3xs flex items-start justify-between">
                <div className="space-y-2">
                  <div className="flex items-center gap-2"><span className="p-1 rounded-full bg-emerald-50 text-emerald-600"><ShieldCheck size={14} /></span><span className="text-xs font-semibold text-slate-800">{val.displayName}</span></div>
                  <div className="font-mono text-[10px] bg-slate-50 text-slate-600 px-2 py-1 rounded border border-slate-100">{val.expression}</div>
                  <div className="text-[10px] text-red-500 font-medium"><strong>警告文案:</strong> {val.errorMessage}</div>
                </div>
                <button onClick={() => handleRemoveValidation(val.id)} className="text-slate-400 hover:text-red-500 p-1" title="删除规则"><Trash2 size={14} /></button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
