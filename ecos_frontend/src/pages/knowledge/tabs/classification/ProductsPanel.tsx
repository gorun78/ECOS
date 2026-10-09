/**
 * 右：资产列表 + 操作（关键词 / 推荐 / Undo / 分页）面板（自 ClassificationTab 拆出，纯搬迁，行为不变）。
 */
import { RefreshCw, ShieldCheck, Sparkles } from 'lucide-react';
import type { NavProductItemVO } from '../../../../services/knowledgeNavApi';
import { PAGE_SIZE } from './constants';

export interface ProductsPanelProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  products: NavProductItemVO[];
  total: number;
  pageNum: number;
  keyword: string;
  productsLoading: boolean;
  selectedRows: Set<string>;
  recommendingId: string | null;
  busy: boolean;
  onKeywordChange: (v: string) => void;
  onToggleSelect: (id: string) => void;
  onRecommend: (id: string) => void;
  onUndo: (id: string) => void;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function ProductsPanel({
  styles,
  t,
  products,
  total,
  pageNum,
  keyword,
  productsLoading,
  selectedRows,
  recommendingId,
  busy,
  onKeywordChange,
  onToggleSelect,
  onRecommend,
  onUndo,
  onPrevPage,
  onNextPage,
}: ProductsPanelProps) {
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
        <span className={`font-bold text-xs ${styles.cardText} flex items-center gap-1.5`}>
          <ShieldCheck size={13} className="text-amber-600" />
          {t('knowledge.nav.products_title')}
          <span className={`text-[9px] ${styles.muted} font-mono`}>({total})</span>
        </span>
      </div>
      <div className="space-y-2">
        <input
          type="text"
          value={keyword}
          onChange={e => onKeywordChange(e.target.value)}
          placeholder={t('knowledge.nav.product_search_placeholder')}
          className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-amber-500`}
        />
      </div>

      {/* 资产表 */}
      {products.length === 0 && !productsLoading ? (
        <div className="py-10 text-center">
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('knowledge.nav.products_empty')}</p>
        </div>
      ) : (
        <div className="divide-y divide-black/5 dark:divide-white/10">
          {products.map(p => {
            const checked = selectedRows.has(p.id);
            return (
              <div
                key={p.id}
                className={`py-2.5 flex items-start gap-2 cursor-pointer transition px-1.5 ${
                  checked ? 'bg-blue-50/40' : 'hover:bg-slate-50/50'
                }`}
                onClick={() => onToggleSelect(p.id)}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() => onToggleSelect(p.id)}
                  onClick={e => e.stopPropagation()}
                  className="mt-1 h-3.5 w-3.5 cursor-pointer"
                />
                <div className="flex-1 min-w-0 space-y-1">
                  <div className="flex items-center gap-2">
                    <span className={`text-[11px] font-bold truncate ${styles.cardText}`}>{p.title}</span>
                    {p.domain && (
                      <span className={`px-1 py-0.5 text-[9px] rounded ${styles.appBg} ${styles.cardTextMuted}`}>
                        {p.domain}
                      </span>
                    )}
                  </div>
                  {p.matchedTags && p.matchedTags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-0.5">
                      {p.matchedTags.slice(0, 4).map(tn => (
                        <span
                          key={tn}
                          className={`px-1.5 py-0.5 text-[9px] rounded bg-emerald-50 text-emerald-700 border border-emerald-200`}
                        >
                          #{tn}
                        </span>
                      ))}
                    </div>
                  )}
                  {p.updatedAt && (
                    <p className={`text-[9px] font-mono ${styles.cardTextMuted}`}>
                      {t('knowledge.nav.product_updated')} {p.updatedAt}
                    </p>
                  )}
                </div>
                <div className="flex gap-1 shrink-0" onClick={e => e.stopPropagation()}>
                  <button
                    type="button"
                    title={t('knowledge.nav.product_recommend')}
                    onClick={() => onRecommend(p.id)}
                    disabled={busy || recommendingId === p.id}
                    className={`p-1.5 rounded-lg ${styles.sidebarBg} hover:bg-blue-50 text-blue-500 cursor-pointer disabled:opacity-50`}
                  >
                    {recommendingId === p.id ? (
                      <RefreshCw size={11} className="animate-spin" />
                    ) : (
                      <Sparkles size={11} />
                    )}
                  </button>
                  <button
                    type="button"
                    title={t('knowledge.nav.product_undo')}
                    onClick={() => onUndo(p.id)}
                    disabled={busy}
                    className={`p-1.5 rounded-lg ${styles.sidebarBg} hover:bg-rose-50 text-rose-500 cursor-pointer disabled:opacity-50`}
                  >
                    <RefreshCw size={11} className="-scale-x-100" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
      {productsLoading && (
        <div className="py-6 text-center">
          <RefreshCw size={18} className={`mx-auto animate-spin ${styles.muted}`} />
        </div>
      )}
      {/* 分页控制 */}
      {total > 0 && (
        <div className={`flex items-center justify-between pt-2 border-t ${styles.cardBorder}`}>
          <span className={`text-[10px] ${styles.muted}`}>
            {t('knowledge.nav.product_page')}: {pageNum} / {Math.max(1, Math.ceil(total / PAGE_SIZE))}
          </span>
          <div className="flex gap-1">
            <button
              type="button"
              disabled={pageNum <= 1}
              onClick={onPrevPage}
              className={`px-2 py-1 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-[11px] rounded cursor-pointer disabled:opacity-40`}
            >
              {'←'}
            </button>
            <button
              type="button"
              disabled={pageNum * PAGE_SIZE >= total}
              onClick={onNextPage}
              className={`px-2 py-1 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-[11px] rounded cursor-pointer disabled:opacity-40`}
            >
              {'→'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
