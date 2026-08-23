import React from "react";
import { BookOpen, FolderTree, Plus, Search, RotateCw, Edit3, Trash2 } from "lucide-react";
import type { DictType, DictItem } from "../../../api";

export interface DictModePanelProps {
  selectedDictType: string | null;
  dictTypes: DictType[];
  openNewDictItem: () => void;
  saving: boolean;
  dictItemFormOpen: boolean;
  dictSearch: string;
  setDictSearch: (v: string) => void;
  dictItemForm: {
    editCode?: string; dictCode: string; extValue: string;
    dictLabel: string; status: string; sortOrder: string; description: string;
  };
  setDictItemForm: (updater: (p: DictModePanelProps["dictItemForm"]) => DictModePanelProps["dictItemForm"]) => void;
  handleSaveDictItem: (savingFn: (b: boolean) => void) => void;
  cancelDictItemForm: () => void;
  dictLoading: boolean;
  filteredDictItems: DictItem[];
  openEditDictItem: (item: DictItem) => void;
  setDeleteTarget: (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => void;
}

export const DictModePanel: React.FC<DictModePanelProps> = ({
  selectedDictType, dictTypes, openNewDictItem, saving, dictItemFormOpen,
  dictSearch, setDictSearch, dictItemForm, setDictItemForm,
  handleSaveDictItem, cancelDictItemForm, dictLoading, filteredDictItems,
  openEditDictItem, setDeleteTarget,
}) => {
  if (!selectedDictType) {
    return (
      <div className="flex-1 bg-white overflow-y-auto">
        <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs gap-3">
          <BookOpen size={48} className="opacity-25" />
          <div className="text-center">从左侧选择一个字典类型查看其字典项</div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 bg-white overflow-y-auto">
      <div className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
            <FolderTree size={20} className="text-emerald-500" />
            {dictTypes.find(dt => dt.dictType === selectedDictType)?.dictName ?? selectedDictType}
            <span className="text-sm font-normal text-slate-400 font-mono">({selectedDictType})</span>
          </h2>
          <button className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={openNewDictItem} disabled={saving || dictItemFormOpen}>
            <Plus size={14} />
            新增字典项
          </button>
        </div>

        <div className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs w-64">
          <Search size={14} className="text-slate-400 shrink-0" />
          <input placeholder="搜索字典项..." value={dictSearch} onChange={e => setDictSearch(e.target.value)}
            className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400" />
        </div>

        {dictItemFormOpen && (
          <div className="p-4 rounded-xl border-2 border-emerald-200 bg-emerald-50/30">
            <div className="text-xs font-semibold text-slate-600 mb-3">{dictItemForm.editCode ? "编辑字典项" : "新增字典项"}</div>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">编码 *</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono disabled:opacity-40"
                  placeholder="dict_code" value={dictItemForm.dictCode} onChange={e => setDictItemForm(p => ({ ...p, dictCode: e.target.value }))} disabled={saving || !!dictItemForm.editCode} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">值</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono"
                  placeholder="dict_value" value={dictItemForm.extValue} onChange={e => setDictItemForm(p => ({ ...p, extValue: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">标签 *</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                  placeholder="显示名称" value={dictItemForm.dictLabel} onChange={e => setDictItemForm(p => ({ ...p, dictLabel: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">状态</div>
                <select className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                  value={dictItemForm.status} onChange={e => setDictItemForm(p => ({ ...p, status: e.target.value }))} disabled={saving}>
                  <option value="active">启用</option>
                  <option value="inactive">禁用</option>
                </select>
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">排序</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400 font-mono"
                  placeholder="0" value={dictItemForm.sortOrder} onChange={e => setDictItemForm(p => ({ ...p, sortOrder: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">描述</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-emerald-400"
                  placeholder="备注说明..." value={dictItemForm.description} onChange={e => setDictItemForm(p => ({ ...p, description: e.target.value }))} disabled={saving} />
              </div>
            </div>
            <div className="flex gap-2 mt-3 pt-3 border-t border-emerald-100">
              <button className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                onClick={() => handleSaveDictItem(() => {})} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                {dictItemForm.editCode ? "更新" : "创建"}
              </button>
              <button className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-[11px] font-semibold transition"
                onClick={cancelDictItemForm} disabled={saving}>
                取消
              </button>
            </div>
          </div>
        )}

        {dictLoading ? (
          <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
            <RotateCw size={18} className="animate-spin" />
            <div>加载中...</div>
          </div>
        ) : filteredDictItems.length === 0 ? (
          <div className="text-center py-12 text-slate-400 text-xs">
            {dictSearch ? `未找到匹配「${dictSearch}」的字典项` : "暂无字典项，点击「新增字典项」开始添加"}
          </div>
        ) : (
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <div className="grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2 bg-slate-100 text-[10px] font-semibold text-slate-500 uppercase">
              <span>编码</span><span>值</span><span>标签</span><span>排序</span><span>状态</span><span>操作</span>
            </div>
            {filteredDictItems.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map(item => (
              <div key={item.dictCode} className="grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2.5 border-t border-slate-100 text-xs hover:bg-slate-50">
                <span className="font-mono font-semibold text-slate-700 truncate">{item.dictCode}</span>
                <span className="font-mono text-slate-600 truncate">{item.extValue || "—"}</span>
                <span className="text-slate-700 truncate">{item.dictLabel}</span>
                <span className="text-slate-400">{item.sortOrder ?? 0}</span>
                <span>
                  <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${item.status === "active" ? "bg-green-50 text-green-600" : "bg-slate-100 text-slate-500"}`}>
                    {item.status === "active" ? "启用" : "禁用"}
                  </span>
                </span>
                <span className="flex items-center gap-1">
                  <button className="p-0.5 hover:bg-slate-200 rounded" title="编辑" onClick={() => openEditDictItem(item)}>
                    <Edit3 size={12} className="text-slate-400" />
                  </button>
                  <button className="p-0.5 hover:bg-red-50 rounded" title="删除"
                    onClick={() => setDeleteTarget({ type: "dictItem", id: item.dictCode, name: item.dictLabel })}>
                    <Trash2 size={12} className="text-red-400" />
                  </button>
                </span>
              </div>
            ))}
          </div>
        )}

        {selectedDictType && (
          <div className="border-t border-slate-200 pt-3 mt-2 text-[11px] text-slate-400">
            {(() => {
              const dt = dictTypes.find(d => d.dictType === selectedDictType);
              return dt?.description ? <span>说明: {dt.description}</span> : null;
            })()}
          </div>
        )}
      </div>
    </div>
  );
};
