/**
 * ExplorerLeftPanel — 左侧对象类型选择器与已保存搜索列表
 * Extracted verbatim from ObjectExplorerView.tsx (pure structural refactor)
 * @license Apache-2.0
 */
import React from 'react';
import { Bookmark, Compass, Trash2 } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { DataRecord, ObjectType } from '../../types/ontology';
import type { SavedSearch } from './types';

interface Props {
  objectTypes: ObjectType[];
  activeObjectTypeId: string | null;
  instanceData: DataRecord[];
  dataTotal: number;
  relatedInstanceCache: Record<string, DataRecord[]>;
  savedSearches: SavedSearch[];
  setActiveObjectTypeId: React.Dispatch<React.SetStateAction<string | null>>;
  setActiveTab: (tab: 'table' | 'analytics') => void;
  handleLoadSavedSearch: (search: SavedSearch) => void;
  handleDeleteSavedSearch: (id: string, e: React.MouseEvent) => void;
}

export default function ExplorerLeftPanel({
  objectTypes,
  activeObjectTypeId,
  instanceData,
  dataTotal,
  relatedInstanceCache,
  savedSearches,
  setActiveObjectTypeId,
  setActiveTab,
  handleLoadSavedSearch,
  handleDeleteSavedSearch
}: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className={`w-64 border-r ${styles.cardBorder} ${styles.cardBg} flex flex-col shrink-0 text-xs`}>
      {/* Section title */}
      <div className={`p-4 border-b ${styles.cardBorder} flex items-center justify-between`}>
        <div className={`font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <Compass size={14} className={styles.accentText} />
          <span>{t('ow.explore.directoryTitle')}</span>
        </div>
      </div>

      {/* Object Types list */}
      <div className="p-3 space-y-1">
        <span className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider block px-2 mb-2`}>{t('ow.explore.objectsSection')}</span>
        {objectTypes.map(ot => {
          const isActive = ot.id === activeObjectTypeId;
          const count = (ot.id === activeObjectTypeId && instanceData.length > 0) ? dataTotal : (relatedInstanceCache[ot.id]?.length ?? 0);

          return (
            <button
              key={ot.id}
              onClick={() => {
                setActiveObjectTypeId(ot.id);
                setActiveTab('table');
              }}
              className={`w-full text-left py-2 px-2.5 rounded-lg flex items-center justify-between transition-all group ${
                isActive
                  ? `${styles.accentBg} text-white font-semibold shadow-xs`
                  : `${styles.cardTextMuted} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className={`p-1 rounded border ${isActive ? 'bg-blue-500 border-blue-400 text-white' : ot.color}`}>
                  <DynamicIcon name={ot.icon} size={12} />
                </span>
                <span className="truncate">{ot.displayName}</span>
              </div>
              <span className={`font-mono text-[10px] px-1.5 py-0.5 rounded-full ${isActive ? 'bg-blue-500 text-white' : `${styles.appBg} ${styles.cardTextMuted}`}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>

      {/* Saved Search Lists */}
      <div className={`flex-1 border-t ${styles.cardBorder} p-3 space-y-1.5 overflow-y-auto`}>
        <div className="flex justify-between items-center px-2 mb-1">
          <span className={`text-[10px] ${styles.muted} font-bold uppercase tracking-wider`}>{t('ow.explore.savedLists')}</span>
          <span className={`text-[10px] ${styles.appBg} ${styles.cardTextMuted} px-1 py-0.2 rounded-sm font-mono`}>{savedSearches.length}</span>
        </div>

        {savedSearches.length === 0 ? (
          <div className={`p-4 text-center ${styles.muted} border border-dashed ${styles.cardBorder} rounded-lg text-[10px]`}>
            {t('ow.empty.noSavedLists')}
            {t('ow.empty.noSavedListsHint')}
          </div>
        ) : (
          <div className="space-y-1">
            {savedSearches.map(search => (
              <div
                key={search.id}
                onClick={() => handleLoadSavedSearch(search)}
                className={`group flex items-center justify-between p-2 rounded-lg border ${styles.cardBorder} hover:border-blue-400 ${styles.appBg} hover:bg-blue-50/20 cursor-pointer transition-all`}
              >
                <div className="flex items-center gap-1.5 truncate">
                  <Bookmark size={11} className="text-blue-500 shrink-0" />
                  <span className={`font-medium ${styles.cardTextMuted} truncate`}>{search.name}</span>
                </div>
                <button
                  onClick={(e) => handleDeleteSavedSearch(search.id, e)}
                  className={`opacity-0 group-hover:opacity-100 hover:text-red-500 ${styles.muted} transition-opacity p-0.5`}
                >
                  <Trash2 size={11} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
