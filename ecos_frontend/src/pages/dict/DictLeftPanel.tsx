import React from "react";
import {
  Search, Plus, Database, RotateCw, BookOpen,
  Columns3, Layers, HardDrive, Edit3, Trash2,
  ChevronDown, FolderTree,
} from "lucide-react";
import { STATUS_OPTIONS, STATUS_META, G1_G5_LABELS } from "./constants";
import type { DictTable, DictColumn } from "../../services/dict";
import type { DictType } from "../../../api";

export interface DictLeftPanelProps {
  viewMode: "table" | "dict";
  handleSwitchMode: (mode: "table" | "dict") => void;
  counts: { all: number; draft: number; published: number; deprecated: number };
  search: string;
  handleSearchChange: (v: string) => void;
  statusFilter: string;
  handleStatusFilter: (v: string) => void;
  saving: boolean;
  handleCreate: () => void;
  loading: boolean;
  filteredTables: DictTable[];
  selectedTable: DictTable | null;
  selectTable: (t: DictTable) => void;
  setDeleteTarget: (t: { type: "table" | "column" | "dictItem"; id: string; name: string } | null) => void;
  dictTypes: DictType[];
  dictSearch: string;
  setDictSearch: (v: string) => void;
  dictLoading: boolean;
  subsystemGroups: Record<string, DictType[]>;
  expandedGroups: Set<string>;
  toggleGroup: (g: string) => void;
  handleSelectDictType: (dictType: string) => void;
  selectedDictType: string | null;
}

export const DictLeftPanel: React.FC<DictLeftPanelProps> = (props) => {
  const {
    viewMode, handleSwitchMode, counts, search, handleSearchChange,
    statusFilter, handleStatusFilter, saving, handleCreate, loading,
    filteredTables, selectedTable, selectTable, setDeleteTarget,
    dictTypes, dictSearch, setDictSearch, dictLoading,
    subsystemGroups, expandedGroups, toggleGroup, handleSelectDictType, selectedDictType,
  } = props;

  return (
    <div className="w-[340px] min-w-[280px] border-r border-[#E2E8F0] bg-white flex flex-col shrink-0">
      <div className="p-4 border-b border-[#E2E8F0]">
        <div className="flex mb-3 rounded-lg bg-slate-100 p-0.5">
          <button
            className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition ${viewMode === "table" ? "bg-white text-slate-800 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
            onClick={() => handleSwitchMode("table")}
          >
            <Database size={13} className="inline mr-1" />
            数据表管理
          </button>
          <button
            className={`flex-1 py-1.5 rounded-md text-xs font-semibold transition ${viewMode === "dict" ? "bg-white text-slate-800 shadow-sm" : "text-slate-500 hover:text-slate-700"}`}
            onClick={() => handleSwitchMode("dict")}
          >
            <BookOpen size={13} className="inline mr-1" />
            字典项管理
          </button>
        </div>

        {viewMode === "table" && (
          <>
            <div className="text-base font-bold text-slate-800 mb-3 flex items-center gap-2">
              <Database size={18} className="text-indigo-500" />
              数据字典
              <span className="text-xs font-normal text-slate-400">{counts.all} 张表</span>
            </div>
            <div className="flex gap-2 mb-2.5">
              <div className="flex items-center gap-1.5 flex-1 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs">
                <Search size={14} className="text-slate-400 shrink-0" />
                <input placeholder="搜索表名..." value={search} onChange={e => handleSearchChange(e.target.value)}
                  className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400" />
              </div>
              <select className="px-2.5 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700 min-w-[110px] outline-none"
                value={statusFilter} onChange={e => handleStatusFilter(e.target.value)}>
                {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
              </select>
            </div>
            <button className="w-full py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer transition disabled:opacity-50"
              onClick={handleCreate} disabled={saving}>
              <Plus size={16} />
              新建数据表
            </button>
            <div className="flex gap-3 mt-2.5 text-[11px] text-slate-400">
              <span>草稿 {counts.draft}</span>
              <span>已发布 {counts.published}</span>
              <span>已废弃 {counts.deprecated}</span>
            </div>
          </>
        )}

        {viewMode === "dict" && (
          <>
            <div className="text-base font-bold text-slate-800 mb-3 flex items-center gap-2">
              <BookOpen size={18} className="text-emerald-500" />
              字典分组
              <span className="text-xs font-normal text-slate-400">{dictTypes.length} 个类型</span>
            </div>
            <div className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs mb-2.5">
              <Search size={14} className="text-slate-400 shrink-0" />
              <input placeholder="搜索字典类型..." value={dictSearch} onChange={e => setDictSearch(e.target.value)}
                className="border-none outline-none flex-1 bg-transparent text-xs text-slate-700 placeholder-slate-400" />
            </div>
          </>
        )}
      </div>

      {viewMode === "table" && (
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
              <RotateCw size={18} className="animate-spin" />
              <div>加载中...</div>
            </div>
          ) : filteredTables.length === 0 ? (
            <div className="flex items-center justify-center py-12 text-slate-400 text-xs px-4 text-center">
              {search ? `未找到匹配「${search}」的数据表` : "暂无数据表，点击上方「+ 新建数据表」开始创建"}
            </div>
          ) : (
            filteredTables.map(t => {
              const meta = STATUS_META[t.status] ?? STATUS_META.DRAFT;
              const isActive = t.id === selectedTable?.id;
              const colCount = t.columns?.length ?? 0;
              return (
                <div key={t.id}
                  className={`flex flex-col gap-1 px-3 py-2.5 border-b border-slate-100 cursor-pointer transition text-xs ${isActive ? "bg-indigo-50 border-l-2 border-l-indigo-500" : "hover:bg-slate-50"}`}
                  onClick={() => selectTable(t)}>
                  <div className="flex items-center gap-2">
                    <Database size={13} className="text-slate-400 shrink-0" />
                    <span className="flex-1 font-semibold text-slate-700 truncate">{t.name}</span>
                    <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${meta.bg} ${meta.text}`}>{meta.label}</span>
                  </div>
                  <div className="flex items-center gap-3 text-[10px] text-slate-400 pl-5">
                    {t.schema && <span className="flex items-center gap-0.5"><Layers size={10} />{t.schema}</span>}
                    {t.source && <span className="flex items-center gap-0.5"><HardDrive size={10} />{t.source}</span>}
                    <span className="flex items-center gap-0.5"><Columns3 size={10} />{colCount} 字段</span>
                  </div>
                  <div className="flex gap-1 pl-5">
                    <button className="p-0.5 hover:bg-slate-200 rounded" title="编辑"
                      onClick={e => { e.stopPropagation(); selectTable(t); }}>
                      <Edit3 size={12} className="text-slate-400" />
                    </button>
                    <button className="p-0.5 hover:bg-red-50 rounded" title="删除"
                      onClick={e => { e.stopPropagation(); setDeleteTarget({ type: "table", id: t.id, name: t.name }); }}>
                      <Trash2 size={12} className="text-red-400" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {viewMode === "dict" && (
        <div className="flex-1 overflow-y-auto">
          {dictLoading ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-400 text-xs gap-2">
              <RotateCw size={18} className="animate-spin" />
              <div>加载中...</div>
            </div>
          ) : Object.keys(subsystemGroups).length === 0 && dictTypes.length === 0 ? (
            <div className="flex items-center justify-center py-12 text-slate-400 text-xs px-4 text-center">
              暂无可用的字典类型
            </div>
          ) : (
            (Object.keys(subsystemGroups).length > 0 ? Object.keys(subsystemGroups).sort() : ["G1","G2","G3","G4","G5"]).map(groupKey => {
              const groupTypes = subsystemGroups[groupKey] || [];
              const isExpanded = expandedGroups.has(groupKey);
              const gMeta = G1_G5_LABELS[groupKey] || { zh: groupKey, color: "text-slate-600", border: "border-slate-200", bg: "bg-slate-50" };
              const visibleTypes = groupTypes.filter(dt =>
                !dictSearch || (dt.dictName || dt.dictType).toLowerCase().includes(dictSearch.toLowerCase()) ||
                dt.dictType.toLowerCase().includes(dictSearch.toLowerCase()));
              if (dictSearch && visibleTypes.length === 0 && groupTypes.length > 0) return null;
              const totalItems = groupTypes.reduce((sum, dt) => sum + (dt.itemCount || 0), 0);
              return (
                <div key={groupKey} className="border-b border-slate-100">
                  <div className={`flex items-center gap-2 px-3 py-2 cursor-pointer transition text-xs ${gMeta.bg} border-l-2 ${gMeta.border}`}
                    onClick={() => toggleGroup(groupKey)}>
                    <ChevronDown size={12} className={`transition ${isExpanded ? "" : "-rotate-90"} text-slate-400`} />
                    <span className={`font-bold ${gMeta.color}`}>{gMeta.zh}</span>
                    <span className="text-slate-400">{groupTypes.length} 类型 · {totalItems} 项</span>
                  </div>
                  {isExpanded && visibleTypes.map(dt => {
                    const isActive = dt.dictType === selectedDictType;
                    return (
                      <div key={dt.dictType}
                        className={`flex flex-col gap-1 pl-7 pr-3 py-2 border-b border-slate-50 cursor-pointer transition text-xs ${isActive ? "bg-emerald-50 border-l-2 border-l-emerald-500" : "hover:bg-slate-50"}`}
                        onClick={() => handleSelectDictType(dt.dictType)}>
                        <div className="flex items-center gap-2">
                          <FolderTree size={12} className={isActive ? "text-emerald-500" : "text-slate-400"} />
                          <span className={`flex-1 font-semibold truncate ${isActive ? "text-emerald-700" : "text-slate-700"}`}>
                            {dt.dictName || dt.dictType}
                          </span>
                          {dt.itemCount != null && (
                            <span className="text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded-full">{dt.itemCount}</span>
                          )}
                        </div>
                        <div className="text-[10px] text-slate-400 pl-4">
                          <span className="font-mono">{dt.dictType}</span>
                          {dt.description && <span className="truncate"> — {dt.description}</span>}
                        </div>
                      </div>
                    );
                  })}
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
};
