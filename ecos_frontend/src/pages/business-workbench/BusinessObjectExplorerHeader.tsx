/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Active-stage header (breadcrumb, view tabs, filter toolbar with the
// filter-creator popover) of BusinessObjectExplorer. Extracted verbatim by H6-T4.

import React from 'react';
import { ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { FilterQuery } from './businessObjectExplorerTypes';

interface ExplorerHeaderProps {
  activeObjectType: ObjectType;
  activeTab: 'table' | 'analytics';
  onTabChange: (tab: 'table' | 'analytics') => void;
  activeFilters: FilterQuery[];
  onRemoveFilter: (index: number) => void;
  showFilterCreator: boolean;
  onToggleFilterCreator: (show: boolean) => void;
  newFilterProp: string;
  onNewFilterPropChange: (v: string) => void;
  newFilterOp: FilterQuery['operator'];
  onNewFilterOpChange: (v: FilterQuery['operator']) => void;
  newFilterVal: string;
  onNewFilterValChange: (v: string) => void;
  onAddFilter: () => void;
  onOpenSaveModal: () => void;
}

export function ExplorerHeader({
  activeObjectType,
  activeTab,
  onTabChange,
  activeFilters,
  onRemoveFilter,
  showFilterCreator,
  onToggleFilterCreator,
  newFilterProp,
  onNewFilterPropChange,
  newFilterOp,
  onNewFilterOpChange,
  newFilterVal,
  onNewFilterValChange,
  onAddFilter,
  onOpenSaveModal,
}: ExplorerHeaderProps) {
  const { styles } = useTheme();

  return (
    <div className={`${styles.cardBg} border-b ${styles.appBorder} px-6 py-4 flex flex-col gap-3`}>
      {/* Breadcrumb & Title */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
            <LucideIcon name={activeObjectType.icon} size={15} />
          </span>
          <div>
            <h2 className={`text-sm font-bold ${styles.text} flex items-center gap-1.5`}>
              {activeObjectType.displayName}
              <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono uppercase`}>{activeObjectType.id}</span>
            </h2>
            <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5`}>{activeObjectType.description}</p>
          </div>
        </div>

        {/* View Selector Tabs */}
        <div className={`flex ${styles.sidebarBg} p-1 rounded-lg`}>
          <button
            onClick={() => onTabChange('table')}
            className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
              activeTab === 'table' ? `${styles.cardBg} ${styles.text} shadow-3xs` : `${styles.cardTextMuted} hover:${styles.text}`
            }`}
          >
            <LucideIcon name="Table2" size={13} />
            数据实例列表
          </button>
          <button
            onClick={() => onTabChange('analytics')}
            className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
              activeTab === 'analytics' ? `${styles.cardBg} ${styles.text} shadow-3xs` : `${styles.cardTextMuted} hover:${styles.text}`
            }`}
          >
            <LucideIcon name="BarChart3" size={13} />
            运行统计与聚合
          </button>
        </div>
      </div>

      {/* Quick Filter & Save Search Toolbar */}
      <div className={`flex flex-wrap items-center gap-3 ${styles.appBg} p-2.5 rounded-lg border ${styles.divider}`}>
        <div className={`flex items-center gap-1.5 text-[11px] font-semibold ${styles.cardTextMuted} shrink-0`}>
          <LucideIcon name="Filter" size={13} />
          筛选器:
        </div>

        {/* Existing active filters badges */}
        {activeFilters.length === 0 && (
          <span className={`text-[10px] ${styles.cardTextMuted} italic`}>当前没有添加任何筛选过滤器</span>
        )}
        {activeFilters.map((f, idx) => {
          const prop = activeObjectType.properties.find(p => p.id === f.propertyId);
          const propName = prop ? prop.displayName : f.propertyId;

          const opName = f.operator === 'equals' ? '='
            : f.operator === 'contains' ? '包含'
            : f.operator === 'gt' ? '>'
            : f.operator === 'lt' ? '<'
            : f.operator === 'is_empty' ? '为空' : '不为空';

          return (
            <span key={idx} className="flex items-center gap-1 bg-blue-50 border border-blue-200 text-blue-700 px-2 py-1 rounded font-medium text-[10px]">
              <span className="text-blue-500">{propName}</span>
              <span className="text-blue-400 italic font-mono">{opName}</span>
              {f.operator !== 'is_empty' && f.operator !== 'is_not_empty' && (
                <strong className="text-blue-900 font-semibold">{f.value}</strong>
              )}
              <button
                onClick={() => onRemoveFilter(idx)}
                className="text-blue-400 hover:text-blue-600 ml-1 font-bold"
              >
                ×
              </button>
            </span>
          );
        })}

        {/* Add filter creator dropdown trigger */}
        <div className="relative ml-auto flex items-center gap-2">
          <button
            onClick={() => onToggleFilterCreator(!showFilterCreator)}
            className={`${styles.cardBg} border ${styles.inputBorder} ${styles.inputText} hover:bg-blue-50/20 text-[10px] font-semibold py-1 px-2 rounded-md flex items-center gap-1 transition-colors`}
          >
            <LucideIcon name="Plus" size={11} />
            添加筛选过滤器
          </button>

          {/* Filter Creator Popover */}
          {showFilterCreator && (
            <div className={`absolute right-0 top-7 ${styles.cardBg} border ${styles.inputBorder} rounded-lg shadow-lg p-3 z-30 w-72 space-y-3`}>
              <h4 className={`font-semibold ${styles.cardText} text-[11px]`}>新建筛选规则</h4>
              <div className="space-y-2">
                <div>
                  <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>选择属性</label>
                  <select
                    value={newFilterProp}
                    onChange={e => onNewFilterPropChange(e.target.value)}
                    className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2`}
                  >
                    <option value="">-- 请选择 --</option>
                    {activeObjectType.properties.map(p => (
                      <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>比较算子</label>
                    <select
                      value={newFilterOp}
                      onChange={e => onNewFilterOpChange(e.target.value as any)}
                      className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2`}
                    >
                      <option value="equals">等于 (Equals)</option>
                      <option value="contains">包含 (Contains)</option>
                      <option value="gt">大于 (&gt;)</option>
                      <option value="lt">小于 (&lt;)</option>
                      <option value="is_empty">为空 (Is Empty)</option>
                      <option value="is_not_empty">不为空 (Is Not Empty)</option>
                    </select>
                  </div>
                  <div>
                    <label className={`text-[10px] ${styles.cardTextMuted} block mb-0.5`}>设定值</label>
                    <input
                      type="text"
                      disabled={newFilterOp === 'is_empty' || newFilterOp === 'is_not_empty'}
                      placeholder="搜索值"
                      value={newFilterVal}
                      onChange={e => onNewFilterValChange(e.target.value)}
                      className={`w-full h-8 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded px-2`}
                    />
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-1.5 pt-1">
                <button
                  onClick={() => onToggleFilterCreator(false)}
                  className={`h-7 px-2.5 rounded text-[10px] ${styles.appBg} hover:bg-blue-50/20 ${styles.cardTextMuted}`}
                >
                  取消
                </button>
                <button
                  onClick={onAddFilter}
                  disabled={!newFilterProp}
                  className="h-7 px-3 rounded text-[10px] bg-blue-600 hover:bg-blue-500 text-white disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  应用规则
                </button>
              </div>
            </div>
          )}

          {/* Save exploration list */}
          {activeFilters.length > 0 && (
            <button
              onClick={onOpenSaveModal}
              className="bg-blue-50 border border-blue-200 text-blue-700 hover:bg-blue-100 text-[10px] font-semibold py-1 px-2.5 rounded-md flex items-center gap-1 transition-colors"
            >
              <LucideIcon name="Bookmark" size={11} />
              保存为对象列表
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
