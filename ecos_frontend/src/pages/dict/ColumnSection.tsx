import React from "react";
import { Columns3, Plus, RotateCw, Key, Edit3, Trash2, Hash, Layers, HardDrive, User, Calendar, Tag } from "lucide-react";
import { ColumnTypeSelect } from "./SharedComponents";
import { typeBadge, type ColumnFormState } from "./constants";
import type { DictTable, DictColumn } from "../../services/dict";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";

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
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <>
      <div className={`border-t ${styles.cardBorder} pt-5`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            <Columns3 size={16} className="text-indigo-500" />
            {t('platform.dictionary.fieldList')}
            <span className={`text-xs font-normal ${styles.cardTextMuted}`}>{selectedTable.columns?.length ?? 0} {t('platform.dictionary.columnsUnit')}</span>
          </h3>
          <button type="button" className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={openNewColumn} disabled={saving || colFormOpen}>
            <Plus size={14} />
            {t('platform.dictionary.addField')}
          </button>
        </div>

        {colFormOpen && (
          <div className="mb-4 p-4 rounded-xl border-2 border-indigo-200 bg-indigo-50/30">
            <div className={`text-xs font-semibold ${styles.cardText} mb-3`}>{colForm.id ? t('platform.dictionary.editField') : t('platform.dictionary.addNewField')}</div>
            <div className="grid grid-cols-4 gap-3">
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.fieldName')} *</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 font-mono`}
                  placeholder="column_name" value={colForm.name} onChange={e => setColForm(p => ({ ...p, name: e.target.value }))} disabled={saving} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.type')}</div>
                <ColumnTypeSelect value={colForm.type} onChange={v => setColForm(p => ({ ...p, type: v }))} disabled={saving} />
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.length')}</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 font-mono`}
                  placeholder="255" value={colForm.length} onChange={e => setColForm(p => ({ ...p, length: e.target.value }))} disabled={saving} />
              </div>
              <div className="flex gap-1.5">
                <div className="flex-1">
                  <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.precision')}</div>
                  <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 font-mono`}
                    placeholder="10" value={colForm.precision} onChange={e => setColForm(p => ({ ...p, precision: e.target.value }))} disabled={saving} />
                </div>
                <div className="flex-1">
                  <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.scale')}</div>
                  <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 font-mono`}
                    placeholder="2" value={colForm.scale} onChange={e => setColForm(p => ({ ...p, scale: e.target.value }))} disabled={saving} />
                </div>
              </div>
              <div>
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.defaultValue')}</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 font-mono`}
                  placeholder="NULL" value={colForm.defaultValue} onChange={e => setColForm(p => ({ ...p, defaultValue: e.target.value }))} disabled={saving} />
              </div>
              <div className="col-span-2">
                <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-0.5`}>{t('platform.dictionary.desc')}</div>
                <input className={`w-full px-2.5 py-1.5 rounded border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400`}
                  placeholder={t('platform.dictionary.fieldDescPh')} value={colForm.description} onChange={e => setColForm(p => ({ ...p, description: e.target.value }))} disabled={saving} />
              </div>
              <div className="flex items-end gap-3 pb-1">
                <label className={`flex items-center gap-1 text-[10px] ${styles.cardText} cursor-pointer`}>
                  <input type="checkbox" className="w-3.5 h-3.5 rounded accent-indigo-500" checked={colForm.nullable}
                    onChange={e => setColForm(p => ({ ...p, nullable: e.target.checked }))} disabled={saving} />
                  {t('platform.dictionary.nullable')}
                </label>
                <label className={`flex items-center gap-1 text-[10px] ${styles.cardText} cursor-pointer`}>
                  <input type="checkbox" className="w-3.5 h-3.5 rounded accent-amber-500" checked={colForm.primaryKey}
                    onChange={e => setColForm(p => ({ ...p, primaryKey: e.target.checked }))} disabled={saving} />
                  {t('platform.dictionary.primaryKey')}
                </label>
              </div>
            </div>
            <div className="flex gap-2 mt-3 pt-3 border-t border-indigo-100">
              <button type="button" className="px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-[11px] font-semibold transition disabled:opacity-50 flex items-center gap-1"
                onClick={handleSaveColumn} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin" /> : null}
                {colForm.id ? t('platform.dictionary.updateField') : t('platform.dictionary.addField')}
              </button>
              <button type="button" className={`px-3 py-1.5 rounded-lg ${styles.appBg} hover:${styles.sidebarBg} ${styles.cardText} text-[11px] font-semibold transition`}
                onClick={cancelColumnForm} disabled={saving}>
                {t('common.cancel')}
              </button>
            </div>
          </div>
        )}

        {!selectedTable.columns || selectedTable.columns.length === 0 ? (
          <div className={`text-center py-8 ${styles.cardTextMuted} text-xs`}>
            {colFormOpen ? null : t('platform.dictionary.emptyColumns')}
          </div>
        ) : (
          <div className={`border ${styles.appBorder} rounded-xl overflow-hidden`}>
            <div className={`grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2 ${styles.appBg} text-[10px] font-semibold ${styles.cardTextMuted} uppercase`}>
              <span>{t('platform.dictionary.fieldName')}</span><span>{t('platform.dictionary.type')}</span><span>{t('platform.dictionary.nullCol')}</span><span>{t('platform.dictionary.primaryKey')}</span><span>{t('platform.dictionary.defaultValue')}</span><span>{t('platform.dictionary.actions')}</span>
            </div>
            {[...selectedTable.columns].sort((a, b) => a.sortOrder - b.sortOrder).map(col => (
              <div key={col.id}>
                <div className={`grid grid-cols-[1fr_120px_80px_70px_100px_80px] gap-2 px-4 py-2.5 border-t ${styles.cardBorder} text-xs cursor-pointer transition hover:${styles.appBg} ${expandedColId === col.id ? "bg-indigo-50/50" : ""}`}
                  onClick={() => setExpandedColId(expandedColId === col.id ? null : col.id)}>
                  <span className={`font-mono font-semibold ${styles.cardText} truncate flex items-center gap-1.5`}>
                    {col.primaryKey && <Key size={11} className="text-amber-500 shrink-0" />}{col.name}
                  </span>
                  <span className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-mono font-semibold ${typeBadge(col.type)}`}>
                    {col.type}{col.length ? `(${col.length})` : ""}
                  </span>
                  <span className={`${styles.cardTextMuted} flex items-center gap-1`}>
                    {col.nullable ? <span className={styles.cardTextMuted}>YES</span> : <span className="text-red-500 font-semibold">NO</span>}
                  </span>
                  <span>{col.primaryKey ? <Key size={13} className="text-amber-500" /> : <span className={styles.appBg}>—</span>}</span>
                  <span className={`font-mono ${styles.cardTextMuted} truncate`}>{col.defaultValue ?? <span className={`italic ${styles.appBg}`}>NULL</span>}</span>
                  <span className="flex items-center gap-1">
                    <button type="button" className={`p-0.5 hover:${styles.sidebarBg} rounded`} title={t('platform.dictionary.edit')}
                      onClick={e => { e.stopPropagation(); openEditColumn(col); }}>
                      <Edit3 size={12} className={styles.cardTextMuted} />
                    </button>
                    <button type="button" className="p-0.5 hover:bg-red-50 rounded" title={t('platform.dictionary.delete')}
                      onClick={e => { e.stopPropagation(); setDeleteTarget({ type: "column", id: col.id, name: col.name }); }}>
                      <Trash2 size={12} className="text-red-400" />
                    </button>
                  </span>
                </div>
                {expandedColId === col.id && (
                  <div className={`px-4 py-3 ${styles.appBg} border-t ${styles.appBorder} text-[11px] ${styles.cardTextMuted} grid grid-cols-2 gap-x-6 gap-y-1`}>
                    <div><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.fieldName')}:</span> <span className={`font-mono ${styles.cardText}`}>{col.name}</span></div>
                    <div><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.type')}:</span> <span className={`font-mono ${styles.cardText}`}>{col.type}{col.length ? `(${col.length})` : ""}{col.precision != null ? `(${col.precision},${col.scale ?? 0})` : ""}</span></div>
                    <div><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.nullable')}:</span> <span className={styles.cardText}>{col.nullable ? t('platform.dictionary.yes') : t('platform.dictionary.no')}</span></div>
                    <div><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.primaryKey')}:</span> <span className={styles.cardText}>{col.primaryKey ? t('platform.dictionary.yes') : t('platform.dictionary.no')}</span></div>
                    {col.defaultValue && <div className="col-span-2"><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.defaultValue')}:</span> <span className={`font-mono ${styles.cardText}`}>{col.defaultValue}</span></div>}
                    {col.description && <div className="col-span-2"><span className={`font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.desc')}:</span> <span className={styles.cardText}>{col.description}</span></div>}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <div className={`border-t ${styles.appBorder} pt-4 mt-2 flex flex-wrap gap-x-6 gap-y-1 text-[11px] ${styles.cardTextMuted}`}>
        {selectedTable.code && <span className="flex items-center gap-1"><Hash size={11} /> {t('platform.dictionary.code')}: <span className={`font-mono ${styles.cardTextMuted}`}>{selectedTable.code}</span></span>}
        {selectedTable.schema && <span className="flex items-center gap-1"><Layers size={11} /> Schema: <span className={`font-mono ${styles.cardTextMuted}`}>{selectedTable.schema}</span></span>}
        {selectedTable.source && <span className="flex items-center gap-1"><HardDrive size={11} /> {t('platform.dictionary.source')}: <span className={styles.cardTextMuted}>{selectedTable.source}</span></span>}
        {selectedTable.owner && <span className="flex items-center gap-1"><User size={11} /> {t('platform.dictionary.owner')}: <span className={styles.cardTextMuted}>{selectedTable.owner}</span></span>}
        {selectedTable.createdAt && <span className="flex items-center gap-1"><Calendar size={11} /> {t('platform.dictionary.created')}: <span className={styles.cardTextMuted}>{new Date(selectedTable.createdAt).toLocaleDateString("zh-CN")}</span></span>}
        {selectedTable.tags && selectedTable.tags.length > 0 && (
          <span className="flex items-center gap-1 flex-wrap">
            <Tag size={11} />
            {selectedTable.tags.map(tg => <span key={tg} className={`px-1.5 py-0.5 rounded ${styles.appBg} ${styles.cardTextMuted} text-[10px] font-mono`}>{tg}</span>)}
          </span>
        )}
      </div>
    </>
  );
};
