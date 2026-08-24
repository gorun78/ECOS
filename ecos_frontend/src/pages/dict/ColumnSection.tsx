import React from "react";
import { Columns3, Plus, RotateCw, Key, Edit3, Trash2, Hash, Layers, HardDrive, User, Calendar, Tag } from "lucide-react";
import { ColumnTypeSelect } from "./SharedComponents";
import { typeBadge, type ColumnFormState } from "./constants";
import type { DictTable, DictColumn } from "../../services/dict";

export interface ColumnSectionProps {
  selectedTable: DictTable;
  saving: boolean;
  colFormOpen: boolean;
  colForm: ColumnFormState;
  setColForm: (updater: (p: ColumnFormState) => ColumnFormState) => void;
  openNewColumn: () => void;
  openEditColumn: (col: DictColumn) => void;
  cancelColumnForm: () => void;
  handleSaveColumn: () => void;
  expandedColId: string | null;
  setExpandedColId: (id: string | null) => void;
  setDeleteTarget: (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => void;
}

export const ColumnSection: React.FC<ColumnSectionProps> = ({
  selectedTable, saving, colFormOpen, colForm, setColForm,
  openNewColumn, openEditColumn, cancelColumnForm, handleSaveColumn,
  expandedColId, setExpandedColId, setDeleteTarget,
}) => {
  return (
    <>
      <div className="border-t border-slate-200 pt-5">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-bold text-slate-700 flex items-center gap-2">
            <Columns3 size={16} className="text-indigo-500" />
            字段列表
            <span className="text-xs font-normal text-slate-400">{selectedTable.columns?.length ?? 0} 个字段</span>
          </h3>
          <button className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={openNewColumn} disabled={saving || colFormOpen}>
            <Plus size={14} />
            添加字段
          </button>
        </div>

        {colFormOpen && (
          <div className="mb-4 p-4 rounded-xl border-2 border-indigo-200 bg-indigo-50/30">
            <div className="text-xs font-semibold text-slate-600 mb-3">{colForm.id ? "编辑字段" : "添加新字段"}</div>
            <div className="grid grid-cols-4 gap-3">
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">字段名 *</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                  placeholder="column_name" value={colForm.name} onChange={e => setColForm(p => ({ ...p, name: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">类型</div>
                <ColumnTypeSelect value={colForm.type} onChange={v => setColForm(p => ({ ...p, type: v }))} disabled={saving} />
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">长度</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                  placeholder="255" value={colForm.length} onChange={e => setColForm(p => ({ ...p, length: e.target.value }))} disabled={saving} />
              </div>
              <div className="flex gap-1.5">
                <div className="flex-1">
                  <div className="text-[10px] font-semibold text-slate-500 mb-0.5">精度</div>
                  <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                    placeholder="10" value={colForm.precision} onChange={e => setColForm(p => ({ ...p, precision: e.target.value }))} disabled={saving} />
                </div>
                <div className="flex-1">
                  <div className="text-[10px] font-semibold text-slate-500 mb-0.5">标度</div>
                  <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                    placeholder="2" value={colForm.scale} onChange={e => setColForm(p => ({ ...p, scale: e.target.value }))} disabled={saving} />
                </div>
              </div>
              <div>
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">默认值</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 font-mono"
                  placeholder="NULL" value={colForm.defaultValue} onChange={e => setColForm(p => ({ ...p, defaultValue: e.target.value }))} disabled={saving} />
              </div>
              <div className="col-span-2">
                <div className="text-[10px] font-semibold text-slate-500 mb-0.5">描述</div>
                <input className="w-full px-2.5 py-1.5 rounded border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400"
                  placeholder="字段说明..." value={colForm.description} onChange={e => setColForm(p => ({ ...p, description: e.target.value }))} disabled={saving} />
              </div>
              <div className="flex items-end gap-3 pb-1">
                <label className="flex items-center gap-1 text-[10px] text-slate-600 cursor-pointer">
                  <input type="checkbox" className="w-3.5 h-3.5 rounded accent-indigo-500" checked={colForm.nullable}
                    onChange={e => setColForm(p => ({ ...p, nullable: e.target.checked }))} disabled={saving} />
                  可为空
                </label>
                <label className="flex items-center gap-1 text-[10px] text-slate-600 cursor-pointer">
                  <input type="checkbox" className="w-3.5 h-3.5 rounded accent-amber-500" checked={colForm.primaryKey}
                    onChange={e => setColForm(p => ({ ...p, primaryKey: e.target.checked }))} disabled={saving} />
                  主键
                </label>
              </div>
            </div>
            <div className="flex gap-2 mt-3 pt-3 border-t border-indigo-100">
              <button className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                onClick={handleSaveColumn} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                {colForm.id ? "更新字段" : "添加字段"}
              </button>
              <button className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-[11px] font-semibold transition"
                onClick={cancelColumnForm} disabled={saving}>
                取消
              </button>
            </div>
          </div>
        )}

        {!selectedTable.columns || selectedTable.columns.length === 0 ? (
          <div className="text-center py-8 text-slate-400 text-xs">
            {colFormOpen ? null : "暂无字段，点击「添加字段」开始定义表结构"}
          </div>
        ) : (
          <div className="border border-slate-200 rounded-xl overflow-hidden">
            <div className="grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2 bg-slate-100 text-[10px] font-semibold text-slate-500 uppercase">
              <span>字段名</span><span>类型</span><span>可空</span><span>主键</span><span>默认值</span><span>操作</span>
            </div>
            {[...selectedTable.columns].sort((a, b) => a.sortOrder - b.sortOrder).map(col => (
              <div key={col.id}>
                <div className={`grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2.5 border-t border-slate-100 text-xs cursor-pointer transition hover:bg-slate-50 ${expandedColId === col.id ? "bg-indigo-50/50" : ""}`}
                  onClick={() => setExpandedColId(expandedColId === col.id ? null : col.id)}>
                  <span className="font-mono font-semibold text-slate-700 truncate flex items-center gap-1.5">
                    {col.primaryKey && <Key size={11} className="text-amber-500 shrink-0" />}{col.name}
                  </span>
                  <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold ${typeBadge(col.type)}`}>
                    {col.type}{col.length ? `(${col.length})` : ""}
                  </span>
                  <span className="text-slate-500 flex items-center gap-1">
                    {col.nullable ? <span className="text-slate-400">YES</span> : <span className="text-red-500 font-semibold">NO</span>}
                  </span>
                  <span>{col.primaryKey ? <Key size={13} className="text-amber-500" /> : <span className="text-slate-300">—</span>}</span>
                  <span className="font-mono text-slate-400 truncate">{col.defaultValue ?? <span className="text-slate-300 italic">NULL</span>}</span>
                  <span className="flex items-center gap-1">
                    <button className="p-0.5 hover:bg-slate-200 rounded" title="编辑"
                      onClick={e => { e.stopPropagation(); openEditColumn(col); }}>
                      <Edit3 size={12} className="text-slate-400" />
                    </button>
                    <button className="p-0.5 hover:bg-red-50 rounded" title="删除"
                      onClick={e => { e.stopPropagation(); setDeleteTarget({ type: "column", id: col.id, name: col.name }); }}>
                      <Trash2 size={12} className="text-red-400" />
                    </button>
                  </span>
                </div>
                {expandedColId === col.id && (
                  <div className="px-4 py-3 bg-slate-50 border-t border-slate-100 text-[11px] text-slate-500 grid grid-cols-2 gap-x-6 gap-y-1">
                    <div><span className="font-semibold text-slate-400">字段名:</span> <span className="font-mono text-slate-700">{col.name}</span></div>
                    <div><span className="font-semibold text-slate-400">类型:</span> <span className="font-mono text-slate-700">{col.type}{col.length ? `(${col.length})` : ""}{col.precision != null ? `(${col.precision},${col.scale ?? 0})` : ""}</span></div>
                    <div><span className="font-semibold text-slate-400">可为空:</span> <span className="text-slate-700">{col.nullable ? "是" : "否"}</span></div>
                    <div><span className="font-semibold text-slate-400">主键:</span> <span className="text-slate-700">{col.primaryKey ? "是" : "否"}</span></div>
                    {col.defaultValue && <div className="col-span-2"><span className="font-semibold text-slate-400">默认值:</span> <span className="font-mono text-slate-700">{col.defaultValue}</span></div>}
                    {col.description && <div className="col-span-2"><span className="font-semibold text-slate-400">描述:</span> <span className="text-slate-700">{col.description}</span></div>}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="border-t border-slate-200 pt-4 mt-2 flex flex-wrap gap-x-6 gap-y-1 text-[11px] text-slate-400">
        {selectedTable.code && <span className="flex items-center gap-1"><Hash size={11} /> 编码: <span className="font-mono text-slate-500">{selectedTable.code}</span></span>}
        {selectedTable.schema && <span className="flex items-center gap-1"><Layers size={11} /> Schema: <span className="font-mono text-slate-500">{selectedTable.schema}</span></span>}
        {selectedTable.source && <span className="flex items-center gap-1"><HardDrive size={11} /> 数据源: <span className="text-slate-500">{selectedTable.source}</span></span>}
        {selectedTable.owner && <span className="flex items-center gap-1"><User size={11} /> 负责人: <span className="text-slate-500">{selectedTable.owner}</span></span>}
        {selectedTable.createdAt && <span className="flex items-center gap-1"><Calendar size={11} /> 创建: <span className="text-slate-500">{new Date(selectedTable.createdAt).toLocaleDateString("zh-CN")}</span></span>}
        {selectedTable.tags && selectedTable.tags.length > 0 && (
          <span className="flex items-center gap-1 flex-wrap">
            <Tag size={11} />
            {selectedTable.tags.map(tg => <span key={tg} className="px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 text-[10px] font-mono">{tg}</span>)}
          </span>
        )}
      </div>
    </>
  );
};
