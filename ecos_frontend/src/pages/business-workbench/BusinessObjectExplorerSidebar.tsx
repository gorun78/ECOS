/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// LEFT PANEL (object selector + saved searches) and welcome canvas view of
// BusinessObjectExplorer. Extracted verbatim by H6-T4 (pure move, no behavior change).

import React from 'react';
import { ObjectType, Dataset } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { SavedSearch } from './businessObjectExplorerTypes';

interface ExplorerSidebarProps {
  objectTypes: ObjectType[];
  datasets: Dataset[];
  activeObjectTypeId: string | null;
  onSelectObjectType: (id: string) => void;
  savedSearches: SavedSearch[];
  onLoadSavedSearch: (search: SavedSearch) => void;
  onDeleteSavedSearch: (id: string, e: React.MouseEvent) => void;
  apiLoading: boolean;
  apiError: string | null;
}

export function ExplorerSidebar({
  objectTypes,
  datasets,
  activeObjectTypeId,
  onSelectObjectType,
  savedSearches,
  onLoadSavedSearch,
  onDeleteSavedSearch,
  apiLoading,
  apiError,
}: ExplorerSidebarProps) {
  const { styles } = useTheme();

  return (
    <div className={`w-64 border-r ${styles.appBorder} ${styles.cardBg} flex flex-col shrink-0 text-xs`}>
      {/* Section title */}
      <div className={`p-4 border-b ${styles.divider} flex items-center justify-between`}>
        <div className={`font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <LucideIcon name="Compass" size={14} className="text-blue-600" />
          <span>对象浏览器目录</span>
          {apiLoading && (
            <span className="ml-1 inline-flex items-center gap-1 text-[9px] font-normal text-blue-500">
              <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
              同步后端…
            </span>
          )}
        </div>
        {apiError && (
          <span
            title={`后端同步失败：${apiError}（已降级为本地种子数据）`}
            className="text-[9px] text-amber-500 cursor-help"
          >
            离线
          </span>
        )}
      </div>

      {/* Object Types list */}
      <div className="p-3 space-y-1">
        <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block px-2 mb-2`}>对象实体 (Objects)</span>
        {objectTypes.map(ot => {
          const isActive = ot.id === activeObjectTypeId;
          // Get mock instances count
          const ds = datasets.find(d => d.id === ot.mapping?.datasetId);
          const count = ds ? ds.sampleData.length : 0;

          return (
            <button
              key={ot.id}
              onClick={() => onSelectObjectType(ot.id)}
              className={`w-full text-left py-2 px-2.5 rounded-lg flex items-center justify-between transition-all group ${
                isActive
                  ? 'bg-blue-600 text-white font-semibold shadow-xs'
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className={`p-1 rounded border ${isActive ? 'bg-blue-500 border-blue-400 text-white' : ot.color}`}>
                  <LucideIcon name={ot.icon} size={12} />
                </span>
                <span className="truncate">{ot.displayName}</span>
              </div>
              <span className={`font-mono text-[10px] px-1.5 py-0.5 rounded-full ${isActive ? 'bg-blue-500 text-white' : `${styles.sidebarBg} ${styles.cardTextMuted}`}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {/* Saved Search Lists */}
      <div className={`flex-1 border-t ${styles.divider} p-3 space-y-1.5 overflow-y-auto`}>
        <div className="flex justify-between items-center px-2 mb-1">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>我的保存列表 (Object Lists)</span>
          <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1 py-0.2 rounded-sm font-mono`}>{savedSearches.length}</span>
        </div>

        {savedSearches.length === 0 ? (
          <div className={`p-4 text-center ${styles.cardTextMuted} border border-dashed ${styles.sidebarBorder} rounded-lg text-[10px]`}>
            暂无保存的对象列表。
            可以在筛选过滤后，将其保存。
          </div>
        ) : (
          <div className="space-y-1">
            {savedSearches.map(search => (
              <div
                key={search.id}
                onClick={() => onLoadSavedSearch(search)}
                className={`group flex items-center justify-between p-2 rounded-lg border ${styles.divider} hover:border-blue-400 ${styles.appBg} hover:bg-blue-50/20 cursor-pointer transition-all`}
              >
                <div className="flex items-center gap-1.5 truncate">
                  <LucideIcon name="Bookmark" size={11} className="text-blue-500 shrink-0" />
                  <span className={`font-medium ${styles.accentText} truncate`}>{search.name}</span>
                </div>
                <button
                  onClick={(e) => onDeleteSavedSearch(search.id, e)}
                  className={`opacity-0 group-hover:opacity-100 hover:text-red-500 ${styles.cardTextMuted} transition-opacity p-0.5`}
                >
                  <LucideIcon name="Trash2" size={11} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

interface ExplorerWelcomeProps {
  objectTypes: ObjectType[];
  datasets: Dataset[];
  onSelectObjectType: (id: string) => void;
}

export function ExplorerWelcome({ objectTypes, datasets, onSelectObjectType }: ExplorerWelcomeProps) {
  const { styles } = useTheme();

  return (
    <div className={`flex flex-col items-center justify-center h-full p-8 text-center ${styles.appBg}`}>
      <div className="w-16 h-16 rounded-2xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600 mb-4 animate-pulse">
        <LucideIcon name="Compass" size={32} />
      </div>
      <h2 className={`text-sm font-semibold ${styles.cardText}`}>欢迎使用 Palantir Foundry 对象浏览器 (Object Explorer)</h2>
      <p className={`text-xs ${styles.cardTextMuted} max-w-lg leading-relaxed mt-2`}>
        对象浏览器是围绕底层异构数据源构建的数据模型透视工作台。
        在此您可以全局探索所有数字孪生实例、设定复杂的交叉筛选条件、跨对象级联穿透挖掘、以及触发运行微事务 Action。
      </p>

      {/* Grid of quick choices */}
      <div className="grid grid-cols-2 gap-4 w-full max-w-xl mt-8">
        {objectTypes.map(ot => {
          const ds = datasets.find(d => d.id === ot.mapping?.datasetId);
          const count = ds ? ds.sampleData.length : 0;
          return (
            <div
              key={ot.id}
              onClick={() => onSelectObjectType(ot.id)}
              className={`${styles.cardBg} border ${styles.appBorder} hover:border-blue-500 p-4 rounded-xl shadow-3xs hover:shadow-xs transition-all cursor-pointer flex items-start gap-3 group text-left`}
            >
              <span className={`p-2.5 rounded-lg border ${ot.color} shrink-0`}>
                <LucideIcon name={ot.icon} size={16} />
              </span>
              <div className="space-y-0.5">
                <div className={`text-xs font-semibold ${styles.cardText} group-hover:text-blue-600`}>{ot.displayName}</div>
                <p className={`text-[10px] ${styles.cardTextMuted} line-clamp-1`}>{ot.description}</p>
                <div className={`text-[10px] font-mono ${styles.cardTextMuted} mt-1`}>
                  <strong>{count}</strong> 个当前运行实体
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
