/* Extracted from ConnectionsTab.tsx */
import React from 'react';
import LucideIcon from '../../LucideIcon';
import { useTheme } from "../../../../components/ThemeContext";
import type { FolderFileVo } from '../../api';
import { fmtBytes } from './constants';

interface FolderFilesPanelProps {
  folderFiles: FolderFileVo[];
  folderFilesLoading: boolean;
  selectedNames: Set<string>;
  setSelectedNames: (v: Set<string>) => void;
  t: (key: string) => string;
}

/** 文件夹数据源（fs）目录文件列表 — 以目录文件替代数据库表目录 */
export default function FolderFilesPanel({
  folderFiles, folderFilesLoading, selectedNames, setSelectedNames, t,
}: FolderFilesPanelProps) {
  const { styles } = useTheme();

  if (folderFilesLoading) {
    return (
                <div className={`p-8 text-center ${styles.cardTextMuted} text-xs flex items-center justify-center gap-2`}>
                  <LucideIcon name="RefreshCw" size={14} className="animate-spin" />
                  {t('dw.loading') || 'Loading...'}
                </div>
    );
  }

  if (folderFiles.length === 0) {
    return (
                <div className={`p-8 border border-dashed ${styles.cardBorder} rounded-xl text-center ${styles.cardTextMuted} text-xs flex flex-col items-center gap-2`}>
                  <LucideIcon name="FolderOpen" size={24} className={`${styles.warningText}`} />
                  <span>{t('dw.folder.empty')}</span>
                  <span>{t('dw.folder.emptyHint')}</span>
                </div>
    );
  }

  return (
                <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden`}>
                  <table className="w-full text-left text-[11px]">
                    <thead className={`${styles.sidebarBg} ${styles.cardTextMuted}`}>
                      <tr>
                        <th className="px-3 py-2 font-semibold w-8">
                          <input
                            type="checkbox"
                            className="accent-indigo-500"
                            checked={folderFiles.length > 0 && folderFiles.every(f => selectedNames.has(f.name))}
                            onChange={e => {
                              const target = new Set<string>();
                              if (e.target.checked) folderFiles.forEach(f => target.add(f.name));
                              setSelectedNames(target);
                            }}
                            title={t('dw.ingest.selectAll') || '全选'}
                          />
                        </th>
                        <th className="px-3 py-2 font-semibold">{t('dw.folder.colName')}</th>
                        <th className="px-3 py-2 font-semibold w-24">{t('dw.folder.colSize')}</th>
                        <th className="px-3 py-2 font-semibold w-44">{t('dw.folder.colModified')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {folderFiles.map(f => (
                        <tr key={f.name} className={`border-t ${styles.cardBorder}`}>
                          <td className={`px-2 py-1.5 ${styles.cardText}`}>
                            <input
                              type="checkbox"
                              className="accent-indigo-500"
                              checked={selectedNames.has(f.name)}
                              onChange={e => {
                                const next = new Set(selectedNames);
                                if (e.target.checked) next.add(f.name); else next.delete(f.name);
                                setSelectedNames(next);
                              }}
                            />
                          </td>
                          <td className={`px-3 py-1.5 font-mono truncate max-w-0 ${styles.cardText}`} title={f.name}>{f.name}</td>
                          <td className={`px-3 py-1.5 font-mono ${styles.cardTextMuted}`}>{fmtBytes(f.size)}</td>
                          <td className={`px-3 py-1.5 font-mono ${styles.cardTextMuted}`}>
                            {f.lastModified ? new Date(f.lastModified).toLocaleString() : '-'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
  );
}
