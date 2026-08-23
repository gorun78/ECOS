import React from "react";
import { Database, RotateCw, Trash2 } from "lucide-react";
import { STATUS_META, SOURCE_OPTIONS } from "./constants";
import type { DictTable } from "../../../services/dict";

export interface TableModePanelProps {
  tableMode: "view" | "create" | "edit";
  selectedTable: DictTable | null;
  saving: boolean;
  tableFormName: string; setTableFormName: (v: string) => void;
  tableFormNameZh: string; setTableFormNameZh: (v: string) => void;
  tableFormSchema: string; setTableFormSchema: (v: string) => void;
  tableFormSource: string; setTableFormSource: (v: string) => void;
  tableFormDesc: string; setTableFormDesc: (v: string) => void;
  tableFormTags: string; setTableFormTags: (v: string) => void;
  transitions: { label: string; status: string; variant: "primary" | "danger" | "secondary" }[];
  handleTransition: (s: string) => void;
  handleSaveTable: () => void;
  handleCancel: () => void;
  setDeleteTarget: (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => void;
  children?: React.ReactNode;
}

export const TableModePanel: React.FC<TableModePanelProps> = ({
  tableMode, selectedTable, saving, tableFormName, setTableFormName,
  tableFormNameZh, setTableFormNameZh, tableFormSchema, setTableFormSchema,
  tableFormSource, setTableFormSource, tableFormDesc, setTableFormDesc,
  tableFormTags, setTableFormTags, transitions, handleTransition,
  handleSaveTable, handleCancel, setDeleteTarget, children,
}) => {
  if (tableMode === "view" && !selectedTable) {
    return (
      <div className="flex-1 bg-white overflow-y-auto">
        <div className="flex flex-col items-center justify-center h-full text-slate-400 text-xs gap-3">
          <Database size={48} className="opacity-25" />
          <div className="text-center">点击「+ 新建数据表」开始，或从左侧选择一个数据表查看详情</div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 bg-white overflow-y-auto">
      <div className="p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-800 flex items-center gap-2">
            <Database size={20} className="text-indigo-500" />
            {tableMode === "create" ? "新建数据表" : selectedTable?.name ?? "表详情"}
          </h2>
          {selectedTable && (
            <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold ${
              (STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).bg
            } ${(STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).text}`}>
              {STATUS_META[selectedTable.status]?.label ?? selectedTable.status}
            </span>
          )}
        </div>

        <div className="grid grid-cols-2 gap-4 p-4 rounded-xl border border-slate-200 bg-slate-50/50">
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">表名 *</div>
            <input className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 font-mono"
              placeholder="例如：user_info" value={tableFormName} onChange={e => setTableFormName(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">中文名称</div>
            <input className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
              placeholder="例如：用户信息表" value={tableFormNameZh} onChange={e => setTableFormNameZh(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">Schema / 库名</div>
            <input className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 font-mono"
              placeholder="例如：public" value={tableFormSchema} onChange={e => setTableFormSchema(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className="text-[11px] font-semibold text-slate-500 mb-1">数据源类型</div>
            <select className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
              value={tableFormSource} onChange={e => setTableFormSource(e.target.value)} disabled={saving}>
              <option value="">选择数据源</option>
              {SOURCE_OPTIONS.map(s => <option key={s} value={s}>{s}</option>)}
            </select>
          </div>
          <div className="col-span-2">
            <div className="text-[11px] font-semibold text-slate-500 mb-1">描述</div>
            <textarea className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50 resize-none"
              placeholder="描述该表的用途、业务含义..." value={tableFormDesc} onChange={e => setTableFormDesc(e.target.value)} disabled={saving} rows={3} />
          </div>
          <div className="col-span-2">
            <div className="text-[11px] font-semibold text-slate-500 mb-1">标签（逗号分隔）</div>
            <input className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 outline-none focus:border-indigo-400 disabled:opacity-50"
              placeholder="例如：核心, 客户域, PII" value={tableFormTags} onChange={e => setTableFormTags(e.target.value)} disabled={saving} />
          </div>
        </div>

        {transitions.length > 0 && (
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[11px] font-semibold text-slate-400">状态流转:</span>
            {transitions.map(tr => (
              <button key={tr.status}
                className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold transition disabled:opacity-50 ${
                  tr.variant === "primary" ? "bg-indigo-600 hover:bg-indigo-700 text-white"
                  : tr.variant === "danger" ? "bg-red-600 hover:bg-red-700 text-white"
                  : "bg-slate-100 hover:bg-slate-200 text-slate-600"}`}
                onClick={() => handleTransition(tr.status)} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin inline mr-1" /> : null}
                {tr.label}
              </button>
            ))}
          </div>
        )}

        <div className="flex gap-2 pt-2 border-t border-slate-100">
          <button className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={handleSaveTable} disabled={saving}>
            {saving ? <RotateCw size={14} className="animate-spin" /> : null}
            保存表信息
          </button>
          <button className="px-4 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 text-xs font-semibold transition disabled:opacity-50"
            onClick={handleCancel} disabled={saving}>
            取消
          </button>
          {selectedTable && (
            <button className="ml-auto px-4 py-2 rounded-lg bg-red-50 hover:bg-red-100 text-red-600 text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
              onClick={() => setDeleteTarget({ type: "table", id: selectedTable.id, name: selectedTable.name })} disabled={saving}>
              <Trash2 size={14} />
              删除此表
            </button>
          )}
        </div>

        {children}
      </div>
    </div>
  );
};
