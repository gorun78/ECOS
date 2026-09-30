/**
 * ExplorerTableView — 表格视图（本地搜索 / 排序 / 行选择 / 分页）
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { ChevronsUpDown, Search } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { ObjectType } from '../../types/ontology';

/** 实例数据行：后端返回的动态列名 JSON 记录，无编译期 schema，值以 unknown 收敛 */
type InstanceRow = Record<string, unknown>;

interface Props {
  activeObjectType: ObjectType | null;
  localSearch: string;
  setLocalSearch: (v: string) => void;
  dataLoading: boolean;
  allInstances: InstanceRow[];
  processedInstances: InstanceRow[];
  sortBy: string;
  sortOrder: 'asc' | 'desc';
  setSortBy: (v: string) => void;
  setSortOrder: (v: 'asc' | 'desc') => void;
  selectedInstance: InstanceRow | null;
  setSelectedInstance: (inst: InstanceRow) => void;
  setDetailTab: (tab: 'properties' | 'relations' | 'activity') => void;
  dataPage: number;
  setDataPage: React.Dispatch<React.SetStateAction<number>>;
  dataTotalPages: number;
}

export default function ExplorerTableView({
  activeObjectType,
  localSearch,
  setLocalSearch,
  dataLoading,
  allInstances,
  processedInstances,
  sortBy,
  sortOrder,
  setSortBy,
  setSortOrder,
  selectedInstance,
  setSelectedInstance,
  setDetailTab,
  dataPage,
  setDataPage,
  dataTotalPages
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
      {/* Search & Statistics bar */}
      <div className={`px-6 py-2 ${styles.appBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
        <div className="relative w-80">
          <span className={`absolute left-2.5 top-2.5 ${styles.muted}`}>
            <Search size={12} />
          </span>
          <input
            type="text"
            placeholder={t('ow.placeholder.localSearch')}
            value={localSearch}
            onChange={e => setLocalSearch(e.target.value)}
            className={`w-full h-7 pl-7 pr-3 text-[10px] ${styles.cardBg} border ${styles.cardBorder} rounded focus:border-blue-500 focus:outline-hidden ${styles.cardTextMuted}`}
          />
        </div>
        <div className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
          {t('ow.explore.showingInstances')} <strong>{processedInstances.length}</strong> / {allInstances.length}
        </div>
      </div>

      {dataLoading ? (
        <div className={`flex-1 flex items-center justify-center py-24 ${styles.muted} font-medium italic text-xs`}>
          {t('ow.label.loadingData')}
        </div>
      ) : allInstances.length === 0 ? (
        <div className={`flex-1 flex items-center justify-center py-24 ${styles.muted} font-medium italic text-xs`}>
          {t('ow.empty.noInstanceData')}
        </div>
      ) : (
      <div className="flex-1 overflow-auto overflow-x-auto md:overflow-visible">
        <table className="w-full text-left border-collapse text-xs select-none">
          <thead>
            <tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-semibold sticky top-0 ${styles.cardBg} z-10 shadow-3xs`}>
              <th className="py-2.5 px-4 w-10">#</th>
              {activeObjectType?.properties.map(prop => {
                const isSorting = sortBy === prop.id;
                return (
                  <th
                    key={prop.id}
                    onClick={() => {
                      setSortBy(prop.id);
                      setSortOrder(isSorting && sortOrder === 'asc' ? 'desc' : 'asc');
                    }}
                    className={`py-2.5 px-4 cursor-pointer ${styles.sidebarHoverBg} transition-colors`}
                  >
                    <div className="flex items-center gap-1">
                      <span>{prop.displayName}</span>
                      {isSorting ? (
                        <DynamicIcon name={sortOrder === 'asc' ? 'ChevronUp' : 'ChevronDown'} size={11} className={styles.accentText} />
                      ) : (
                        <ChevronsUpDown size={10} className={`${styles.muted} opacity-40`} />
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className={`divide-y ${styles.divider} ${styles.cardTextMuted}`}>
            {processedInstances.length === 0 ? (
              <tr>
                <td colSpan={(activeObjectType?.properties.length || 0) + 1} className={`text-center py-24 ${styles.muted} font-medium italic`}>
                  {t('ow.empty.noResults')}
                </td>
              </tr>
            ) : (
              processedInstances.map((inst, idx) => {
                const isSelected = selectedInstance && selectedInstance[activeObjectType!.primaryKey] === inst[activeObjectType!.primaryKey];
                return (
                  <tr
                    key={idx}
                    onClick={() => {
                      setSelectedInstance(inst);
                      setDetailTab('properties');
                    }}
                    className={`${styles.sidebarHoverBg} cursor-pointer transition-colors ${
                      isSelected ? `bg-blue-50/40 text-blue-950 font-medium border-l-2 ${styles.accentBorder}` : ''
                    }`}
                  >
                    <td className={`py-2.5 px-4 font-mono ${styles.muted}`}>{idx + 1}</td>
                    {activeObjectType?.properties.map(prop => {
                      const val = inst[prop.id];
                      const isPk = prop.isPrimaryKey;
                      return (
                        <td key={prop.id} className="py-2.5 px-4">
                          {isPk ? (
                            <span className={`font-mono ${styles.cardText} ${styles.appBg} border ${styles.cardBorder} rounded-md px-1.5 py-0.5 text-[10px] font-semibold`}>
                              {String(val ?? '')}
                            </span>
                          ) : prop.id === 'status' ? (
                            <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                              val === 'ACTIVE' || val === 'ON_TIME' ? 'bg-emerald-100 text-emerald-800' :
                              val === 'MAINTENANCE' || val === 'DELAYED' ? 'bg-amber-100 text-amber-800 font-semibold' :
                              `${styles.appBg} ${styles.cardTextMuted}`
                            }`}>
                              {String(val ?? '')}
                            </span>
                          ) : (
                            <span className="truncate max-w-[160px] inline-block">{String(val ?? '')}</span>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                );
              })
            )}
          </tbody>
         </table>
        <div className={`px-6 py-2 border-t ${styles.cardBorder} flex items-center justify-between`}>
          <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
            {t('ow.explore.showingInstances')} {dataPage} / {dataTotalPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              disabled={dataPage <= 1}
              onClick={() => setDataPage(p => Math.max(1, p - 1))}
              className={`h-6 px-2.5 rounded text-[10px] ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} disabled:opacity-40 disabled:cursor-not-allowed ${styles.sidebarHoverBg} transition-colors`}
            >
              {t('ow.btn.previousPage')}
            </button>
            <button
              disabled={dataPage >= dataTotalPages}
              onClick={() => setDataPage(p => Math.min(dataTotalPages, p + 1))}
              className={`h-6 px-2.5 rounded text-[10px] ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} disabled:opacity-40 disabled:cursor-not-allowed ${styles.sidebarHoverBg} transition-colors`}
            >
              {t('ow.btn.nextPage')}
            </button>
          </div>
        </div>
      </div>
      )}
     </div>
  );
}
