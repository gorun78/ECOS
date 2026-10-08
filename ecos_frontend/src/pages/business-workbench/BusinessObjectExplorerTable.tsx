/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Table (data instances) tab of BusinessObjectExplorer. Extracted verbatim by H6-T4.

import React from 'react';
import { ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface ExplorerTableProps {
  activeObjectType: ObjectType | null;
  processedInstances: any[];
  totalInstanceCount: number;
  localSearch: string;
  onLocalSearchChange: (v: string) => void;
  sortBy: string;
  sortOrder: 'asc' | 'desc';
  onSort: (propId: string) => void;
  selectedInstance: any | null;
  onSelectInstance: (inst: any) => void;
}

export function ExplorerTable({
  activeObjectType,
  processedInstances,
  totalInstanceCount,
  localSearch,
  onLocalSearchChange,
  sortBy,
  sortOrder,
  onSort,
  selectedInstance,
  onSelectInstance,
}: ExplorerTableProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
      {/* Search & Statistics bar */}
      <div className={`px-6 py-2 ${styles.appBg} border-b ${styles.appBorder} flex items-center justify-between`}>
        <div className="relative w-80">
          <span className={`absolute left-2.5 top-2.5 ${styles.cardTextMuted}`}>
            <LucideIcon name="Search" size={12} />
          </span>
          <input
            type="text"
            placeholder={t('ow.exptbl.searchPlaceholder')}
            value={localSearch}
            onChange={e => onLocalSearchChange(e.target.value)}
            className={`w-full h-7 pl-7 pr-3 text-[10px] ${styles.inputBg} border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden ${styles.inputText}`}
          />
        </div>
        <div className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
          {t('ow.exptbl.showing')} <strong>{processedInstances.length}</strong> / {totalInstanceCount} {t('ow.exptbl.instances')}
        </div>
      </div>

      {/* Table stage — 移动端横滚 / 桌面占满剩余高度 */}
      <div className="flex-1 overflow-auto overflow-x-auto md:overflow-visible">
        <table className="w-full text-left border-collapse text-xs select-none">
          <thead>
            <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardTextMuted} font-semibold sticky top-0 ${styles.cardBg} z-10 shadow-3xs`}>
              <th className="py-2.5 px-4 w-10">#</th>
              {activeObjectType?.properties.map(prop => {
                const isSorting = sortBy === prop.id;
                return (
                  <th
                    key={prop.id}
                    onClick={() => onSort(prop.id)}
                    className="py-2.5 px-4 cursor-pointer hover:bg-blue-50/20 transition-colors"
                  >
                    <div className="flex items-center gap-1">
                      <span>{prop.displayName}</span>
                      {isSorting ? (
                        <LucideIcon name={sortOrder === 'asc' ? 'ChevronUp' : 'ChevronDown'} size={11} className="text-blue-600" />
                      ) : (
                        <LucideIcon name="ChevronsUpDown" size={10} className={styles.cardTextMuted} />
                      )}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody className={`divide-y ${styles.divider} ${styles.sidebarText}`}>
            {processedInstances.length === 0 ? (
              <tr>
                <td colSpan={(activeObjectType?.properties.length || 0) + 1} className={`text-center py-24 ${styles.cardTextMuted} font-medium italic`}>
                  {t('ow.exptbl.noResults')}
                </td>
              </tr>
            ) : (
              processedInstances.map((inst, idx) => {
                const isSelected = selectedInstance && selectedInstance[activeObjectType!.primaryKey] === inst[activeObjectType!.primaryKey];
                return (
                  <tr
                    key={idx}
                    onClick={() => onSelectInstance(inst)}
                    className={`hover:bg-blue-50/20 cursor-pointer transition-colors ${
                      isSelected ? 'bg-blue-50/40 text-blue-950 font-medium border-l-2 border-blue-600' : ''
                    }`}
                  >
                    <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{idx + 1}</td>
                    {activeObjectType?.properties.map(prop => {
                      const val = inst[prop.id];
                      const isPk = prop.isPrimaryKey;
                      return (
                        <td key={prop.id} className="py-2.5 px-4">
                          {isPk ? (
                            <span className={`font-mono ${styles.text} ${styles.sidebarBg} border ${styles.appBorder}/80 rounded-md px-1.5 py-0.5 text-[10px] font-semibold`}>
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
      </div>
    </div>
  );
}
