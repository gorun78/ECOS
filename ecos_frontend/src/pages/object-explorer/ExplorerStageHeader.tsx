/**
 * ExplorerStageHeader — 当前对象类型标题 + 视图切换 + 过滤/保存搜索工具栏
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { BarChart3, Bookmark, Filter, Plus, Table2 } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { ObjectType } from '../../types/ontology';
import type { FilterQuery } from './types';

interface Props {
  activeObjectType: ObjectType;
  activeTab: 'table' | 'analytics';
  setActiveTab: (tab: 'table' | 'analytics') => void;
  activeFilters: FilterQuery[];
  handleRemoveFilter: (index: number) => void;
  showFilterCreator: boolean;
  setShowFilterCreator: (v: boolean) => void;
  newFilterProp: string;
  setNewFilterProp: (v: string) => void;
  newFilterOp: FilterQuery['operator'];
  setNewFilterOp: (op: FilterQuery['operator']) => void;
  newFilterVal: string;
  setNewFilterVal: (v: string) => void;
  handleAddFilter: () => void;
  setShowSaveModal: (v: boolean) => void;
}

export default function ExplorerStageHeader({
  activeObjectType,
  activeTab,
  setActiveTab,
  activeFilters,
  handleRemoveFilter,
  showFilterCreator,
  setShowFilterCreator,
  newFilterProp,
  setNewFilterProp,
  newFilterOp,
  setNewFilterOp,
  newFilterVal,
  setNewFilterVal,
  handleAddFilter,
  setShowSaveModal
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`${styles.cardBg} border-b ${styles.cardBorder} px-6 py-4 flex flex-col gap-3`}>
      {/* Breadcrumb & Title */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <span className={`p-1.5 rounded-lg border ${activeObjectType.color}`}>
            <DynamicIcon name={activeObjectType.icon} size={15} />
          </span>
          <div>
            <h2 className={`text-sm font-bold ${styles.cardText} flex items-center gap-1.5`}>
              {activeObjectType.displayName}
              <span className={`text-[10px] ${styles.appBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono uppercase`}>{activeObjectType.id}</span>
            </h2>
            <p className={`text-[10px] ${styles.muted} mt-0.5`}>{activeObjectType.description}</p>
          </div>
        </div>

        {/* View Selector Tabs */}
        <div className={`flex ${styles.appBg} p-1 rounded-lg`}>
          <button type="button"
            onClick={() => setActiveTab('table')}
            className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
              activeTab === 'table' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : `${styles.cardTextMuted} ${styles.sidebarHoverBg}`
            }`}
          >
            <Table2 size={13} />
            {t('ow.explore.tabTable')}
          </button>
          <button type="button"
            onClick={() => setActiveTab('analytics')}
            className={`px-3 py-1.5 rounded-md text-[11px] font-semibold flex items-center gap-1.5 transition-all ${
              activeTab === 'analytics' ? `${styles.cardBg} ${styles.cardText} shadow-3xs` : `${styles.cardTextMuted} ${styles.sidebarHoverBg}`
            }`}
          >
            <BarChart3 size={13} />
            {t('ow.explore.tabAnalytics')}
          </button>
        </div>
      </div>

      {/* Quick Filter & Save Search Toolbar */}
      <div className={`flex flex-wrap items-center gap-3 ${styles.appBg} p-2.5 rounded-lg border ${styles.cardBorder}`}>
        <div className={`flex items-center gap-1.5 text-[11px] font-semibold ${styles.cardTextMuted} shrink-0`}>
          <Filter size={13} />
          {t('ow.explore.filtersLabel')}
        </div>

        {/* Existing active filters badges */}
        {activeFilters.length === 0 && (
          <span className={`text-[10px] ${styles.muted} italic`}>{t('ow.empty.noFilters')}</span>
        )}
        {activeFilters.map((f, idx) => {
          const prop = activeObjectType.properties.find(p => p.id === f.propertyId);
          const propName = prop ? prop.displayName : f.propertyId;
          
          const opName = f.operator === 'equals' ? '=' 
            : f.operator === 'contains' ? t('ow.explore.opContains')
            : f.operator === 'gt' ? '>'
            : f.operator === 'lt' ? '<'
            : f.operator === 'is_empty' ? t('ow.explore.opIsEmpty') : t('ow.explore.opIsNotEmpty');

          return (
            <span key={idx} className={`flex items-center gap-1 ${styles.sidebarActiveBg} border ${styles.accentBorder} text-blue-700 px-2 py-1 rounded font-medium text-[10px]`}>
              <span className="text-blue-500">{propName}</span>
              <span className="text-blue-400 italic font-mono">{opName}</span>
              {f.operator !== 'is_empty' && f.operator !== 'is_not_empty' && (
                <strong className="text-blue-900 font-semibold">{f.value}</strong>
              )}
              <button type="button"
                onClick={() => handleRemoveFilter(idx)}
                className="text-blue-400 hover:text-blue-600 ml-1 font-bold"
              >
                ×
              </button>
            </span>
          );
        })}

        {/* Add filter creator dropdown trigger */}
        <div className="relative ml-auto flex items-center gap-2">
          <button type="button"
            onClick={() => setShowFilterCreator(!showFilterCreator)}
            className={`${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} ${styles.sidebarHoverBg} text-[10px] font-semibold py-1 px-2 rounded-md flex items-center gap-1 transition-colors`}
          >
            <Plus size={11} />
            {t('ow.btn.addFilter')}
          </button>

          {/* Filter Creator Popover */}
          {showFilterCreator && (
            <div className={`absolute right-0 top-7 ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-lg p-3 z-30 w-72 space-y-3`}>
              <h4 className={`font-semibold ${styles.cardText} text-[11px]`}>{t('ow.explore.newFilterRule')}</h4>
              <div className="space-y-2">
                <div>
                  <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.selectProperty')}</label>
                  <select
                    value={newFilterProp}
                    onChange={e => setNewFilterProp(e.target.value)}
                    className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                  >
                    <option value="">{t('ow.placeholder.selectOption')}</option>
                    {activeObjectType.properties.map(p => (
                      <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                    ))}
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.comparisonOperator')}</label>
                    <select
                      value={newFilterOp}
                      onChange={e => setNewFilterOp(e.target.value as FilterQuery['operator'])}
                      className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                    >
                      <option value="equals">{t('ow.explore.opEquals')}</option>
                      <option value="contains">{t('ow.explore.opContainsFull')}</option>
                      <option value="gt">{t('ow.explore.opGreaterThan')}</option>
                      <option value="lt">{t('ow.explore.opLessThan')}</option>
                      <option value="is_empty">{t('ow.explore.opIsEmptyFull')}</option>
                      <option value="is_not_empty">{t('ow.explore.opIsNotEmptyFull')}</option>
                    </select>
                  </div>
                  <div>
                    <label className={`text-[10px] ${styles.muted} block mb-0.5`}>{t('ow.label.setValue')}</label>
                    <input
                      type="text"
                      disabled={newFilterOp === 'is_empty' || newFilterOp === 'is_not_empty'}
                      placeholder={t('ow.placeholder.searchValue')}
                      value={newFilterVal}
                      onChange={e => setNewFilterVal(e.target.value)}
                      className={`w-full h-8 text-[11px] ${styles.appBg} border ${styles.cardBorder} rounded px-2`}
                    />
                  </div>
                </div>
              </div>

              <div className="flex justify-end gap-1.5 pt-1">
                <button type="button"
                  onClick={() => setShowFilterCreator(false)}
                  className={`h-7 px-2.5 rounded text-[10px] ${styles.appBg} ${styles.sidebarHoverBg} ${styles.cardTextMuted}`}
                >
                  {t('ow.btn.cancel')}
                </button>
                <button type="button"
                  onClick={handleAddFilter}
                  disabled={!newFilterProp}
                  className={`h-7 px-3 rounded text-[10px] ${styles.accentBg} hover:bg-blue-500 text-white disabled:opacity-50 disabled:cursor-not-allowed`}
                >
                  {t('ow.btn.applyRule')}
                </button>
              </div>
            </div>
          )}

          {/* Save exploration list */}
          {activeFilters.length > 0 && (
            <button type="button"
              onClick={() => setShowSaveModal(true)}
              className={`${styles.sidebarActiveBg} border ${styles.accentBorder} text-blue-700 hover:bg-blue-100 text-[10px] font-semibold py-1 px-2.5 rounded-md flex items-center gap-1 transition-colors`}
            >
              <Bookmark size={11} />
              {t('ow.btn.saveAsList')}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
