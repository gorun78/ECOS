/**
 * AddEntityModal — 新建实体弹窗
 */
import React from "react";
import { Plus, X } from "lucide-react";

interface Props {
  newEntity: { code: string; name: string; description: string; entityType: string };
  setNewEntity: React.Dispatch<React.SetStateAction<{ code: string; name: string; description: string; entityType: string }>>;
  onClose: () => void;
  onCreate: () => void;
}

export default function AddEntityModal({ newEntity, setNewEntity, onClose, onCreate }: Props) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-xl shadow-2xl w-full sm:w-[400px] animate-in zoom-in-95 mx-4 sm:mx-auto">
        <div className="p-4 border-b flex items-center justify-between">
          <h3 className="text-sm font-bold flex items-center gap-2">
            <Plus className="w-4 h-4 text-indigo-500" /> 新建实体
          </h3>
          <button onClick={onClose}><X className="w-4 h-4 text-slate-400" /></button>
        </div>
        <div className="p-4 space-y-3">
          <input value={newEntity.code} onChange={e => setNewEntity(p => ({...p, code: e.target.value}))}
            className="w-full border rounded-lg px-3 py-2 text-xs" placeholder="编码 (如: Customer)" />
          <input value={newEntity.name} onChange={e => setNewEntity(p => ({...p, name: e.target.value}))}
            className="w-full border rounded-lg px-3 py-2 text-xs" placeholder="名称 (如: 客户)" />
          <textarea value={newEntity.description} onChange={e => setNewEntity(p => ({...p, description: e.target.value}))}
            className="w-full border rounded-lg px-3 py-2 text-xs" placeholder="描述" rows={2} />
          <select value={newEntity.entityType} onChange={e => setNewEntity(p => ({...p, entityType: e.target.value}))}
            className="w-full border rounded-lg px-3 py-2 text-xs">
            <option value="MASTER">主数据 (MASTER)</option>
            <option value="TRANSACTION">事务 (TRANSACTION)</option>
          </select>
        </div>
        <div className="p-4 border-t flex gap-2 justify-end">
          <button onClick={onClose} className="px-4 py-2 text-xs bg-slate-100 rounded-lg">取消</button>
          <button onClick={onCreate} className="px-4 py-2 text-xs bg-indigo-500 text-white rounded-lg hover:bg-indigo-600">创建</button>
        </div>
      </div>
    </div>
  );
}
