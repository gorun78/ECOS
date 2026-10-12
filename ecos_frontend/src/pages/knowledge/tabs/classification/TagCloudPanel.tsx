/**
 * 中：标签云 + 热门标签 Top20 面板（自 ClassificationTab 拆出，纯搬迁，行为不变）。
 */
import { Plus, Tag, Trash2 } from 'lucide-react';
import type { NavTagVO } from '../../../../services/knowledgeNavApi';

export interface TagCloudPanelProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  tags: NavTagVO[];
  hotTags: NavTagVO[];
  selectedTag: string | null;
  tagDraft: string;
  busy: boolean;
  onTagDraftChange: (v: string) => void;
  onSubmitTag: () => void;
  onToggleTag: (tagName: string) => void;
  onDeleteSelectedTag: () => void;
}

export function TagCloudPanel({
  styles,
  t,
  tags,
  hotTags,
  selectedTag,
  tagDraft,
  busy,
  onTagDraftChange,
  onSubmitTag,
  onToggleTag,
  onDeleteSelectedTag,
}: TagCloudPanelProps) {
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
        <span className={`font-bold text-xs ${styles.cardText} flex items-center gap-1.5`}>
          <Tag size={13} className="text-emerald-600" />
          {t('knowledge.nav.tags_title')}
        </span>
        <span className={`text-[9px] ${styles.muted} font-mono`}>
          {tags.length}
        </span>
      </div>
      <div className="space-y-1.5">
        <input
          type="text"
          value={tagDraft}
          onChange={e => onTagDraftChange(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') onSubmitTag(); }}
          placeholder={t('knowledge.nav.tag_new_placeholder')}
          className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-emerald-500`}
        />
        <button
          type="button"
          onClick={onSubmitTag}
          disabled={busy || !tagDraft.trim()}
          className="w-full px-2.5 py-1 text-[11px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded cursor-pointer flex items-center justify-center gap-1 disabled:opacity-50"
        >
          <Plus size={11} />
          {t('knowledge.nav.tag_new')}
        </button>
      </div>
      <div className="flex flex-wrap gap-1.5 mt-3">
        {hotTags.length === 0 ? (
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('knowledge.nav.tag_empty')}</p>
        ) : (
          hotTags.map(tag => {
            const isHot = tag.useCount > 0;
            const isSel = selectedTag === tag.tagName;
            return (
              <button
                key={tag.id}
                type="button"
                title={isSel ? t('knowledge.nav.tag_selected') : t('knowledge.nav.tag_count').replace('{n}', String(tag.useCount))}
                onClick={() => onToggleTag(tag.tagName)}
                className={`px-2 py-1 text-[11px] rounded-lg border cursor-pointer transition ${
                  isSel
                    ? 'bg-emerald-600 text-white border-emerald-600'
                    : isHot
                      ? 'bg-emerald-50 text-emerald-800 border-emerald-300 hover:bg-emerald-100'
                      : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'
                }`}
              >
                <span className="inline-flex items-center gap-1">
                  <Tag size={10} className={isSel ? '' : `text-emerald-500`} />
                  {tag.tagName}
                  <span className={`text-[9px] ${isSel ? 'opacity-90' : 'opacity-70'}`}>
                    ({tag.useCount})
                  </span>
                </span>
              </button>
            );
          })
        )}
      </div>
      {tags.length > 0 && (
        <button
          type="button"
          onClick={onDeleteSelectedTag}
          disabled={!selectedTag || busy}
          className={`w-full mt-3 px-2.5 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} cursor-pointer flex items-center justify-center gap-1 disabled:opacity-40`}
        >
          <Trash2 size={11} />
          {t('knowledge.nav.tag_delete_hint')}
        </button>
      )}
    </div>
  );
}
