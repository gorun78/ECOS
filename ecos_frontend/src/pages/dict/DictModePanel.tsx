import React from "react";
import { BookOpen, FolderTree, Plus, Search, RotateCw, Edit3, Trash2 } from "lucide-react";
import type { DictType, DictItem } from "../../api";
import { useTheme } from "../../components/ThemeContext";

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
  const { styles } = useTheme();
  if (!selectedDictType) {
    return (
      <div className={`flex-1 ${styles.cardBg} overflow-y-auto`}>
        <div className={`flex flex-col items-center justify-center h-full ${styles.cardTextMuted} text-xs gap-3`}>
          <BookOpen size={48} className="opacity-25" />
          <div className="text-center">从左侧选择一个字典类型查看其字典项</div>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex-1 ${styles.cardBg} overflow-y-auto`}>
      <div className="p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
            <FolderTree size={20} className="text-emerald-500" />
            {dictTypes.find(dt => dt.dictType === selectedDictType)?.dictName ?? selectedDictType}
            <span className={`text-sm font-normal ${styles.cardTextMuted} font-mono`}>({selectedDictType})</span>
          </h2>
          <button className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={openNewDictItem} disabled={saving || dictItemFormOpen}>
            <Plus size={14} />
            新增字典项
          </button>
        </div>

        <div className={`flex items-center gap-1.5 px-3 py-2 rounded-lg border ${styles.appBorder} ${styles.cardBg} text-xs w-64`}>
          <Search size={14} className={`${styles.cardTextMuted} shrink-0`} />
          <input placeholder="搜索字典项..." value={dictSearch} onChange={e => setDictSearch(e.target.value)}
            className={`border-none outline-none flex-1 bg-transparent text-xs ${styles.inputText} placeholder:opacity-50`} />
        </div>

        {dictItemFormOpen && (
          <div className="p-4 rounded-xl border-2 border-emerald-200 bg-emerald-50/30">
            <div className={`text-xs font-semibold ${styles.cardText} mb-3`}>{dictItemForm.editCode ? "编辑字典项" : "新增字典项"}</div>
            <div className="grid grid-cols-3 gap-3">
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>编码 *</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400 font-mono disabled:opacity-40`}
                  placeholder="dict_code" value={dictItemForm.dictCode} onChange={e => setDictItemForm(p => ({ ...p, dictCode: e.target.value }))} disabled={saving || !!dictItemForm.editCode} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>值</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400 font-mono`}
                  placeholder="dict_value" value={dictItemForm.extValue} onChange={e => setDictItemForm(p => ({ ...p, extValue: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>标签 *</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400`}
                  placeholder="显示名称" value={dictItemForm.dictLabel} onChange={e => setDictItemForm(p => ({ ...p, dictLabel: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>状态</div>
                <select className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400`}
                  value={dictItemForm.status} onChange={e => setDictItemForm(p => ({ ...p, status: e.target.value }))} disabled={saving}>
                  <option value="active">启用</option>
                  <option value="inactive">禁用</option>
                </select>
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>排序</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400 font-mono`}
                  placeholder="0" value={dictItemForm.sortOrder} onChange={e => setDictItemForm(p => ({ ...p, sortOrder: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>描述</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-emerald-400`}
                  placeholder="备注说明..." value={dictItemForm.description} onChange={e => setDictItemForm(p => ({ ...p, description: e.target.value }))} disabled={saving} />
              </div>
            </div>
            <div className="flex gap-2 mt-3 pt-3 border-t border-emerald-100">
              <button className="px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                onClick={() => handleSaveDictItem(() => {})} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                {dictItemForm.editCode ? "更新" : "创建"}
              </button>
              <button className={`px-3 py-1.5 rounded-lg ${styles.appBg} hover:${styles.sidebarBg} ${styles.cardText} text-[11px] font-semibold transition`}
                onClick={cancelDictItemForm} disabled={saving}>
                取消
              </button>
            </div>
          </div>
        )}

        {dictLoading ? (
          <div className={`flex flex-col items-center justify-center py-12 ${styles.cardTextMuted} text-xs gap-2`}>
            <RotateCw size={18} className="animate-spin" />
            <div>加载中...</div>
          </div>
        ) : filteredDictItems.length === 0 ? (
          <div className={`text-center py-12 ${styles.cardTextMuted} text-xs`}>
            {dictSearch ? `未找到匹配「${dictSearch}」的字典项` : "暂无字典项，点击「新增字典项」开始添加"}
          </div>
        ) : (
          <div className={`border ${styles.appBorder} rounded-xl overflow-hidden`}>
            <div className={`grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2 ${styles.appBg} text-[10px] font-semibold ${styles.cardTextMuted} uppercase`}>
              <span>编码</span><span>值</span><span>标签</span><span>排序</span><span>状态</span><span>操作</span>
            </div>
            {filteredDictItems.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map(item => (
              <div key={item.dictCode} className={`grid grid-cols-[1fr_1fr_1.5fr_80px_80px_100px] gap-2 px-4 py-2.5 border-t ${styles.appBorder} text-xs hover:${styles.appBg}`}>
                <span className={`font-mono font-semibold ${styles.cardText} truncate`}>{item.dictCode}</span>
                <span className={`font-mono ${styles.cardText} truncate`}>{item.extValue || "—"}</span>
                <span className={`${styles.cardText} truncate`}>{item.dictLabel}</span>
                <span className={styles.cardTextMuted}>{item.sortOrder ?? 0}</span>
                <span>
                  <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-semibold ${item.status === "active" ? "bg-green-50 text-green-600" : `${styles.appBg} ${styles.cardTextMuted}`}`}>
                    {item.status === "active" ? "启用" : "禁用"}
                  </span>
                </span>
                <span className="flex items-center gap-1">
                  <button className={`p-0.5 hover:${styles.sidebarBg} rounded`} title="编辑" onClick={() => openEditDictItem(item)}>
                    <Edit3 size={12} className={styles.cardTextMuted} />
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
          <div className={`border-t ${styles.appBorder} pt-3 mt-2 text-[11px] ${styles.cardTextMuted}`}>
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
