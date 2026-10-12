/**
 * 左：3 级目录树面板（自 ClassificationTab 拆出，纯搬迁，行为不变）。
 */
import { FolderTree, Plus, RefreshCw } from 'lucide-react';
import type { NavCategorySaveDTO, NavCategoryVO } from '../../../../services/knowledgeNavApi';
import { TreeRow } from './TreeRow';
import type { CatEditMode } from './types';

export interface CategoryTreePanelProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  categories: NavCategoryVO[];
  topLevelItems: NavCategoryVO[];
  categoriesLoading: boolean;
  busy: boolean;
  selectedCatId: string | null;
  expandedIds: Set<string>;
  catEditMode: CatEditMode | null;
  catDraft: NavCategorySaveDTO;
  onSelect: (id: string) => void;
  onToggle: (id: string) => void;
  onRename: (node: NavCategoryVO) => void;
  onCreateChild: (parentId: string) => void;
  onDelete: (id: string) => void;
  onCreateTop: () => void;
  onDraftNameChange: (name: string) => void;
  onSubmit: () => void;
  onCancelEdit: () => void;
}

export function CategoryTreePanel({
  styles,
  t,
  categories,
  topLevelItems,
  categoriesLoading,
  busy,
  selectedCatId,
  expandedIds,
  catEditMode,
  catDraft,
  onSelect,
  onToggle,
  onRename,
  onCreateChild,
  onDelete,
  onCreateTop,
  onDraftNameChange,
  onSubmit,
  onCancelEdit,
}: CategoryTreePanelProps) {
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
        <span className={`font-bold text-xs ${styles.cardText} flex items-center gap-1.5`}>
          <FolderTree size={13} className="text-indigo-600" />
          {t('knowledge.nav.tree_title')}
        </span>
        <button
          type="button"
          className="px-2.5 py-1 text-[11px] font-bold bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg cursor-pointer flex items-center gap-1 disabled:opacity-50"
          onClick={onCreateTop}
          disabled={busy}
        >
          <Plus size={12} />
          {t('knowledge.nav.tree_new_top')}
        </button>
      </div>
      {categories.length === 0 && !categoriesLoading ? (
        <div className="py-8 text-center">
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('knowledge.nav.tree_empty')}</p>
        </div>
      ) : (
        <div className="space-y-1.5 max-h-[480px] overflow-y-auto pr-1">
          {topLevelItems.map(node => (
            <TreeRow
              key={node.id}
              category={categories.find(c => c.id === node.id) ?? null}
              categories={categories}
              depth={1}
              selectedId={selectedCatId}
              expandedIds={expandedIds}
              onSelect={onSelect}
              onToggle={onToggle}
              onRename={onRename}
              onCreateChild={onCreateChild}
              onDelete={onDelete}
              styles={styles}
              t={t}
            />
          ))}
          {categoriesLoading && (
            <div className="py-4 text-center">
              <RefreshCw size={18} className={`mx-auto animate-spin ${styles.muted}`} />
            </div>
          )}
        </div>
      )}
      {/* 目录编辑态（新增一级 / 子级 / 重命名） */}
      {catEditMode && (
        <div className={`mt-3 ${styles.cardBg} border ${styles.inputBorder} rounded-lg p-2.5 space-y-2`}>
          <span className={`text-[10px] font-bold ${styles.muted} uppercase tracking-wider block`}>
            {catEditMode.mode === 'create'
              ? t('knowledge.nav.tree_new_node')
              : t('knowledge.nav.tree_rename')}
          </span>
          <input
            type="text"
            value={catDraft.name}
            onChange={e => onDraftNameChange(e.target.value)}
            placeholder={t('knowledge.nav.tree_name_placeholder')}
            className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500`}
          />
          <div className="flex gap-1.5">
            <button
              type="button"
              onClick={onSubmit}
              disabled={busy}
              className="flex-1 px-2.5 py-1 text-[11px] font-bold bg-indigo-600 hover:bg-indigo-500 text-white rounded cursor-pointer flex items-center justify-center gap-1 disabled:opacity-50"
            >
              <Plus size={11} />
              {t('knowledge.nav.tree_confirm')}
            </button>
            <button
              type="button"
              onClick={onCancelEdit}
              disabled={busy}
              className={`flex-1 px-2.5 py-1 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded cursor-pointer border ${styles.cardBorder} flex items-center justify-center gap-1 disabled:opacity-50`}
            >
              {t('knowledge.nav.tree_cancel')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
