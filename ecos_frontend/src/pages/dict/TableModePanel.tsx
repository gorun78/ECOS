import React from "react";
import { Database, RotateCw, Trash2 } from "lucide-react";
import { STATUS_META, SOURCE_OPTIONS } from "./constants";
import type { DictTable } from "../../services/dict";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";

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
  const { t } = useLanguage();
  const { styles } = useTheme();
  if (tableMode === "view" && !selectedTable) {
    return (
      <div className={`flex-1 ${styles.cardBg} overflow-y-auto`}>
        <div className={`flex flex-col items-center justify-center h-full ${styles.cardTextMuted} text-xs gap-3`}>
          <Database size={48} className="opacity-25" />
          <div className="text-center">{t('platform.dictionary.tableEmptyHint')}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={`flex-1 ${styles.cardBg} overflow-y-auto`}>
      <div className="p-6 space-y-5">
        <div className="flex items-center justify-between">
          <h2 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}>
            <Database size={20} className="text-indigo-500" />
            {tableMode === "create" ? t('platform.dictionary.newTable') : selectedTable?.name ?? t('platform.dictionary.tableDetail')}
          </h2>
          {selectedTable && (
            <span className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold ${
              (STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).bg
            } ${(STATUS_META[selectedTable.status] ?? STATUS_META.DRAFT).text}`}>
              {STATUS_META[selectedTable.status] ? t(STATUS_META[selectedTable.status].labelKey) : selectedTable.status}
            </span>
          )}
        </div>

        <div className={`grid grid-cols-2 gap-4 p-4 rounded-xl border ${styles.appBorder} ${styles.appBg}`}>
          <div>
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.tableName')} *</div>
            <input className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50 font-mono`}
              placeholder={t('platform.dictionary.tableNamePh')} value={tableFormName} onChange={e => setTableFormName(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.tableZhName')}</div>
            <input className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50`}
              placeholder={t('platform.dictionary.tableZhNamePh')} value={tableFormNameZh} onChange={e => setTableFormNameZh(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.schema')}</div>
            <input className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50 font-mono`}
              placeholder={t('platform.dictionary.schemaPh')} value={tableFormSchema} onChange={e => setTableFormSchema(e.target.value)} disabled={saving} />
          </div>
          <div>
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.sourceType')}</div>
            <select className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50`}
              value={tableFormSource} onChange={e => setTableFormSource(e.target.value)} disabled={saving}>
              <option value="">{t('platform.dictionary.selectSource')}</option>
              {SOURCE_OPTIONS.map(s => <option key={s.value} value={s.value}>{s.labelKey ? t(s.labelKey) : s.value}</option>)}
            </select>
          </div>
          <div className="col-span-2">
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.desc')}</div>
            <textarea className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50 resize-none`}
              placeholder={t('platform.dictionary.tableDescPh')} value={tableFormDesc} onChange={e => setTableFormDesc(e.target.value)} disabled={saving} rows={3} />
          </div>
          <div className="col-span-2">
            <div className={`text-[11px] font-semibold ${styles.cardTextMuted} mb-1`}>{t('platform.dictionary.tags')}</div>
            <input className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText} outline-none focus:border-indigo-400 disabled:opacity-50`}
              placeholder={t('platform.dictionary.tagsPh')} value={tableFormTags} onChange={e => setTableFormTags(e.target.value)} disabled={saving} />
          </div>
        </div>

        {transitions.length > 0 && (
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`text-[11px] font-semibold ${styles.cardTextMuted}`}>{t('platform.dictionary.transition')}:</span>
            {transitions.map(tr => (
              <button key={tr.status} type="button"
                className={`px-3 py-1.5 rounded-lg text-[11px] font-semibold transition disabled:opacity-50 ${
                  tr.variant === "primary" ? "bg-indigo-600 hover:bg-indigo-700 text-white"
                  : tr.variant === "danger" ? "bg-red-600 hover:bg-red-700 text-white"
                  : `${styles.appBg} hover:${styles.sidebarBg} ${styles.cardText}`}`}
                onClick={() => handleTransition(tr.status)} disabled={saving}>
                {saving ? <RotateCw size={12} className="animate-spin inline mr-1" /> : null}
                {tr.label}
              </button>
            ))}
          </div>
        )}

        <div className={`flex gap-2 pt-2 border-t ${styles.cardBorder}`}>
          <button type="button" className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
            onClick={handleSaveTable} disabled={saving}>
            {saving ? <RotateCw size={14} className="animate-spin" /> : null}
            {t('platform.dictionary.saveTable')}
          </button>
          <button type="button" className={`px-4 py-2 rounded-lg ${styles.appBg} hover:${styles.sidebarBg} ${styles.cardText} text-xs font-semibold transition disabled:opacity-50`}
            onClick={handleCancel} disabled={saving}>
            {t('common.cancel')}
          </button>
          {selectedTable && (
            <button type="button" className="ml-auto px-4 py-2 rounded-lg bg-red-50 hover:bg-red-100 text-red-600 text-xs font-semibold transition disabled:opacity-50 flex items-center gap-1"
              onClick={() => setDeleteTarget({ type: "table", id: selectedTable.id, name: selectedTable.name })} disabled={saving}>
              <Trash2 size={14} />
              {t('platform.dictionary.deleteTable')}
            </button>
          )}
        </div>

        {children}
      </div>
    </div>
  );
};
