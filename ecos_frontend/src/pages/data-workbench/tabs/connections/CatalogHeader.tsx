/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import { GitCompare } from 'lucide-react';
import LucideIcon from '../../LucideIcon';
import type { DataConnection } from '../../types';
import type { FolderFileVo } from '../../api';
import { useTheme } from "../../../../components/ThemeContext";

interface CatalogHeaderProps {
  conn: DataConnection;
  isFsConn: boolean;
  folderFiles: FolderFileVo[];
  folderFilesLoading: boolean;
  loadingTables: boolean;
  refreshFolderFiles: (dsId: string) => Promise<void>;
  setShowVersionCompare: (v: boolean) => void;
  t: (key: string) => string;
}

/** ② 目录列表 heading（跟随 fs / 结构 分支渲染右侧操作按钮） */
export default function CatalogHeader({
  conn, isFsConn, folderFiles, folderFilesLoading, loadingTables,
  refreshFolderFiles, setShowVersionCompare, t,
}: CatalogHeaderProps) {
  const { styles } = useTheme();

  return (
            <h4 className={`text-xs font-bold ${styles.cardText} flex items-center justify-between`}>
              <span className="flex items-center gap-1.5">
                <LucideIcon name="FolderTree" size={13} className={styles.accentText} />
                <span>{t("dw.txt.42bc1b")}</span>
              </span>
              <div className="flex items-center gap-3">
                <span className={`text-[10px] ${styles.cardTextMuted} font-normal`}>
                  {isFsConn
                    ? `(${folderFiles.length} ${t("dw.folder.fileCountUnit")})`
                    : `${t("dw.ontologyReadonly")} (${conn.tablesAvailable.length} ${t("dw.tablesUnit")})`}
                </span>
                {!isFsConn && (
                  <button type="button"
                    onClick={() => setShowVersionCompare(true)}
                    disabled={loadingTables}
                    className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1 text-[10px]`}
                    title={t("dw.histCompare.button")}
                  >
                    <GitCompare size={12} />
                    <span>{t("dw.histCompare.button")}</span>
                  </button>
                )}
                {isFsConn && (
                  <button type="button"
                    onClick={() => refreshFolderFiles(conn.id)}
                    disabled={folderFilesLoading}
                    className={`p-1 rounded ${styles.cardTextMuted} hover:${styles.accentText} transition-colors cursor-pointer disabled:opacity-50 flex items-center gap-1 text-[10px]`}
                    title={t("dw.conn.refreshTables")}
                  >
                    <LucideIcon name="RefreshCw" size={12} className={folderFilesLoading ? 'animate-spin' : ''} />
                    <span>{t("dw.conn.refreshTables")}</span>
                  </button>
                )}
              </div>
            </h4>
  );
}
