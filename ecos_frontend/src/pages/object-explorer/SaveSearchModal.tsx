/**
 * SaveSearchModal — 保存搜索结果弹窗
 * @license Apache-2.0
 */
import React from 'react';
import { Bookmark, X } from 'lucide-react';

interface Props {
  newSearchName: string;
  setNewSearchName: (v: string) => void;
  setShowSaveModal: (v: boolean) => void;
  handleSaveSearch: () => void;
}

export default function SaveSearchModal({ newSearchName, setNewSearchName, setShowSaveModal, handleSaveSearch }: Props) {
  return (
    <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-3xs flex items-center justify-center z-50">
      <div className="bg-white border border-slate-200 rounded-xl shadow-2xl p-5 w-96 space-y-4">
        <div className="flex justify-between items-center pb-2 border-b border-slate-100">
          <h3 className="text-xs font-semibold text-slate-900 flex items-center gap-1.5"><Bookmark size={13} className="text-blue-600" />保存当前过滤器为对象列表</h3>
          <button onClick={() => setShowSaveModal(false)} className="text-slate-400 hover:text-slate-600"><X size={14} /></button>
        </div>
        <div><label className="text-[10px] text-slate-400 block mb-1">输入对象列表名称</label><input type="text" placeholder="例如：旧金山基地待检修飞机" value={newSearchName} onChange={e => setNewSearchName(e.target.value)} className="w-full h-8 text-[11px] bg-slate-50 border border-slate-200 rounded px-2.5 focus:border-blue-500 focus:outline-hidden" /></div>
        <div className="flex justify-end gap-2 pt-1 text-[11px]"><button onClick={() => setShowSaveModal(false)} className="h-8 px-3 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold">取消</button><button onClick={handleSaveSearch} disabled={!newSearchName.trim()} className="h-8 px-4 rounded bg-blue-600 hover:bg-blue-500 text-white font-semibold disabled:bg-slate-200 disabled:cursor-not-allowed">确定保存</button></div>
      </div>
    </div>
  );
}
