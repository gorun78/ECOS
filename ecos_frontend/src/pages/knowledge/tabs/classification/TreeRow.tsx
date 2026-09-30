/**
 * 目录树单行渲染 + 递归 children（自 ClassificationTab 拆出，纯搬迁，行为不变）。
 */
import { ChevronRight, Plus, Trash2 } from 'lucide-react';
import type { NavCategoryVO } from '../../../../services/knowledgeNavApi';

/** 取一节点的下级 child 列表（直接子节点，含 articleCount） */
function findChildren(items: NavCategoryVO[], parentId: string): NavCategoryVO[] {
  return items.filter(c => {
    const pid = c.parentId ?? null;
    return (pid ? String(pid) : '') === String(parentId);
  });
}

/** 单行目录渲染 + 递归 children（≤3 级 depth 限制） */
export interface TreeRowProps {
  category: NavCategoryVO | null;
  categories: NavCategoryVO[];
  depth: number;
  selectedId: string | null;
  expandedIds: Set<string>;
  onSelect: (id: string) => void;
  onToggle: (id: string) => void;
  onRename: (node: NavCategoryVO) => void;
  onCreateChild: (parentId: string) => void;
  onDelete: (id: string) => void;
  styles: Record<string, string>;
  t: (key: string) => string;
}

export function TreeRow({
  category,
  categories,
  depth,
  selectedId,
  expandedIds,
  onSelect,
  onToggle,
  onRename,
  onCreateChild,
  onDelete,
  styles,
  t,
}: TreeRowProps) {
  if (!category) return null;
  const isExpanded = expandedIds.has(category.id);
  const isSelected = selectedId === category.id;
  const children = findChildren(categories, category.id);
  const canHaveChildren = depth < 3;
  const indent = (depth - 1) * 14;

  return (
    <div className="space-y-0.5">
      <div
        className={`group px-2 py-1.5 rounded cursor-pointer flex items-center gap-1.5 ${
          isSelected ? 'bg-blue-50/60' : 'hover:bg-slate-50/50'
        }`}
        style={{ paddingLeft: `${indent + 8}px` }}
        onClick={() => onSelect(category.id)}
      >
        <ChevronRight
          size={12}
          className={`${styles.muted} transition-transform ${isExpanded ? 'rotate-90' : ''}`}
          onClick={e => { e.stopPropagation(); onToggle(category.id); }}
        />
        <span className={`text-[11px] flex-1 truncate ${styles.cardText}`}>{category.name}</span>
        <span className={`text-[9px] ${styles.muted} font-mono`}>({category.articleCount})</span>
        <div className="hidden group-hover:flex gap-0.5" onClick={e => e.stopPropagation()}>
          {canHaveChildren && (
            <button
              type="button"
              title={t('knowledge.nav.tree_new_child')}
              onClick={() => onCreateChild(category.id)}
              className="p-1 rounded bg-indigo-50 hover:bg-indigo-100 text-indigo-600 cursor-pointer"
            >
              <Plus size={10} />
            </button>
          )}
          <button
            type="button"
            title={t('knowledge.nav.tree_rename')}
            onClick={() => onRename(category)}
            className="p-1 rounded bg-amber-50 hover:bg-amber-100 text-amber-700 cursor-pointer"
          >
            <span className="text-[10px] leading-none inline-flex justify-center w-3">§</span>
          </button>
          <button
            type="button"
            title={t('knowledge.nav.tree_delete')}
            onClick={() => onDelete(category.id)}
            className="p-1 rounded bg-rose-50 hover:bg-rose-100 text-rose-600 cursor-pointer"
          >
            <Trash2 size={10} />
          </button>
        </div>
      </div>
      {isExpanded && children.map(child => (
        <TreeRow
          key={child.id}
          category={child}
          categories={categories}
          depth={depth + 1}
          selectedId={selectedId}
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
    </div>
  );
}
