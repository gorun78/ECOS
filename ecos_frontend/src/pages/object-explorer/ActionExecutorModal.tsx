/**
 * ActionExecutorModal — 操作执行参数表单弹窗
 * @license Apache-2.0
 */
import React from 'react';
import { CheckCircle, X, Zap } from 'lucide-react';
import type { ActionType, ObjectType } from '../../types/ontology';

interface Props {
  selectedAction: ActionType;
  activeObjectType: ObjectType | null;
  actionParams: Record<string, string>;
  setActionParams: React.Dispatch<React.SetStateAction<Record<string, string>>>;
  actionError: string | null;
  setSelectedAction: (v: null) => void;
  handleExecuteAction: () => void;
}

export default function ActionExecutorModal({ selectedAction, activeObjectType, actionParams, setActionParams, actionError, setSelectedAction, handleExecuteAction }: Props) {
  return (
    <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-3xs flex items-center justify-center z-50">
      <div className="bg-white border border-slate-200 rounded-xl shadow-2xl p-5 w-[420px] space-y-4">
        <div className="flex justify-between items-center pb-2.5 border-b border-slate-100">
          <div className="flex items-center gap-1.5"><Zap size={14} className="text-amber-500 fill-amber-500/10" /><h3 className="text-xs font-semibold text-slate-900">执行操作：{selectedAction.displayName}</h3></div>
          <button onClick={() => setSelectedAction(null)} className="text-slate-400 hover:text-slate-600"><X size={14} /></button>
        </div>
        <p className="text-[10px] text-slate-400 leading-relaxed">{selectedAction.description}</p>
        <div className="space-y-3 pt-1">
          {selectedAction.parameters.map(param => {
            const isObjectParam = param.dataType === 'object';
            const isLocked = isObjectParam && param.objectTypeId === activeObjectType?.id;
            return (
              <div key={param.id} className="space-y-1 text-xs">
                <label className="text-[10px] text-slate-500 font-semibold flex items-center justify-between"><span>{param.displayName} ({param.id})</span>{param.isRequired && <span className="text-red-500 font-bold">* 必填</span>}</label>
                {isLocked ? (<input type="text" disabled value={actionParams[param.id] || ''} className="w-full h-8 text-[11px] bg-slate-100 border border-slate-200 rounded px-2.5 text-slate-500 font-mono" />)
                : param.id === 'new_status_param' ? (<select value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2 focus:border-blue-500"><option value="">-- 请选择目标状态 --</option>{activeObjectType?.id === 'flight' && (<><option value="ON_TIME">ON_TIME (准点)</option><option value="DELAYED">DELAYED (延误)</option><option value="BOARDING">BOARDING (登机中)</option><option value="CANCELLED">CANCELLED (取消)</option></>)}{activeObjectType?.id === 'aircraft' && (<><option value="ACTIVE">ACTIVE (活跃运行)</option><option value="MAINTENANCE">MAINTENANCE (适航检修)</option><option value="INSPECTION">INSPECTION (深度安全安检)</option></>)}</select>)
                : param.dataType === 'date' ? (<input type="date" value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500" />)
                : (<input type="text" placeholder={`请输入 ${param.displayName}`} value={actionParams[param.id] || ''} onChange={e => setActionParams({ ...actionParams, [param.id]: e.target.value })} className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500" />)}
                <p className="text-[9px] text-slate-400">{param.description}</p>
              </div>
            );
          })}
        </div>
        {actionError && (<div className="p-2 bg-red-50 border border-red-200 rounded text-[10px] text-red-600 font-medium">❌ 事务约束违背：{actionError}</div>)}
        <div className="flex justify-end gap-2 pt-2 border-t border-slate-100 text-[11px]"><button onClick={() => setSelectedAction(null)} className="h-8 px-3 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold">取消</button><button onClick={handleExecuteAction} className="h-8 px-4 rounded bg-amber-500 hover:bg-amber-400 text-slate-900 font-semibold flex items-center gap-1"><CheckCircle size={12} /><span>执行写回 (Execute)</span></button></div>
      </div>
    </div>
  );
}
