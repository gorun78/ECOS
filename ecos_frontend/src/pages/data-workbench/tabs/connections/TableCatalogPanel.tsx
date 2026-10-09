/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import type { DataConnection } from '../../types';
import { useTheme } from "../../../../components/ThemeContext";
import TableExpandRow from './TableExpandRow';

interface TableCatalogPanelProps {
  conn: DataConnection;
  loadingTables: boolean;
  tablePage: number;
  setTablePage: React.Dispatch<React.SetStateAction<number>>;
  tablePageSize: number;
  selectedNames: Set<string>;
  setSelectedNames: (v: Set<string>) => void;
  t: (key: string) => string;
}

/** 结构数据源表目录（加载态 / 空态 / 卡片列表 + 分页） */
export default function TableCatalogPanel({
  conn, loadingTables, tablePage, setTablePage, tablePageSize,
  selectedNames, setSelectedNames, t,
}: TableCatalogPanelProps) {
  const { styles } = useTheme();

  if (loadingTables) {
    return (
              <div className={`p-8 text-center ${styles.cardTextMuted} text-xs flex items-center justify-center gap-2`}>
                <LucideIcon name="RefreshCw" size={14} className="animate-spin" />
                {t('dw.loading') || 'Loading...'}
              </div>
    );
  }

  if (conn.tablesAvailable.length === 0) {
    return (
              <div className={`p-8 border border-dashed ${styles.cardBorder} rounded-xl text-center ${styles.cardTextMuted} text-xs flex flex-col items-center gap-2`}>
                <LucideIcon name="AlertTriangle" size={24} className={`${styles.warningText}`} />
                <span>{t("dw.txt.2ce9e0")}</span>
                <span>{t("dw.txt.44e8b3")}</span>
              </div>
    );
  }

  return (
              <>
                <div className="space-y-4">
                  {conn.tablesAvailable.slice((tablePage - 1) * tablePageSize, tablePage * tablePageSize).map(tbl => (
                    <TableExpandRow
                      key={tbl.name}
                      connId={conn.id}
                      table={tbl}
                      selected={selectedNames.has(tbl.name)}
                      onToggle={() => {
                        const next = new Set(selectedNames);
                        if (next.has(tbl.name)) next.delete(tbl.name); else next.add(tbl.name);
                        setSelectedNames(next);
                      }}
                    />
                  ))}
                </div>
                {conn.tablesAvailable.length > tablePageSize && (
                  <div className={`flex items-center justify-between pt-2 border-t ${styles.cardBorder}`}>
                    <button type="button"
                      onClick={() => setTablePage(p => Math.max(1, p - 1))}
                      disabled={tablePage <= 1}
                      className={`px-3 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1`}
                    >
                      <LucideIcon name="ChevronLeft" size={11} />
                      {t("dw.conn.pagePrev")}
                    </button>
                    <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                      {t("dw.conn.pageInfo").replace('{page}', String(tablePage)).replace('{total}', String(Math.ceil(conn.tablesAvailable.length / tablePageSize)))}
                    </span>
                    <button type="button"
                      onClick={() => setTablePage(p => Math.min(Math.ceil(conn.tablesAvailable.length / tablePageSize), p + 1))}
                      disabled={tablePage >= Math.ceil(conn.tablesAvailable.length / tablePageSize)}
                      className={`px-3 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1`}
                    >
                      {t("dw.conn.pageNext")}
                      <LucideIcon name="ChevronRight" size={11} />
                    </button>
                  </div>
                )}
              </>
  );
}
