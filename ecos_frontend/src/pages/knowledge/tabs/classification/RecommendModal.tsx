/**
 * 推荐候选浮层（仅建议，不落库），自 ClassificationTab 拆出，纯搬迁，行为不变。
 */
import { RefreshCw, Sparkles } from 'lucide-react';
import type { NavRecommendVO } from '../../../../services/knowledgeNavApi';

export interface RecommendModalProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  recommendResult: NavRecommendVO;
  busy: boolean;
  onClose: () => void;
  onApply: () => void;
}

export function RecommendModal({
  styles,
  t,
  recommendResult,
  busy,
  onClose,
  onApply,
}: RecommendModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className={`${styles.appBg} border ${styles.cardBorder} rounded-xl w-[480px] p-5 space-y-3 shadow-2xl`}
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
            <Sparkles size={14} className="text-purple-600" />
            {t('knowledge.nav.recommend_panel')}
          </h3>
          <button type="button" onClick={onClose} className="p-1 cursor-pointer">
            <RefreshCw size={14} className="rotate-90" />
          </button>
        </div>
        <div className="space-y-2">
          {recommendResult.tags && recommendResult.tags.length > 0 ? (
            <div className="space-y-1.5">
              <span className={`text-[10px] font-bold ${styles.muted} uppercase`}>
                {t('knowledge.nav.recommend_tags')}
              </span>
              <div className="flex flex-wrap gap-1.5">
                {recommendResult.tags.map((tn, i) => (
                  <span key={`${tn}-${i}`} className="px-2 py-0.5 text-[11px] bg-purple-50 text-purple-700 rounded border border-purple-200">
                    #{tn}
                  </span>
                ))}
              </div>
            </div>
          ) : (
            <p className={`text-[11px] ${styles.muted}`}>{t('knowledge.nav.recommend_tags_empty')}</p>
          )}
          {recommendResult.suggestedCategoryPath && (
            <div className="space-y-1.5">
              <span className={`text-[10px] font-bold ${styles.muted} uppercase`}>
                {t('knowledge.nav.recommend_category_suggested')}
              </span>
              <p className={`text-[11px] ${styles.cardText} font-mono`}>{recommendResult.suggestedCategoryPath}</p>
            </div>
          )}
          {recommendResult.reason && (
            <p className={`text-[10px] ${styles.cardTextMuted}`}>{recommendResult.reason}</p>
          )}
        </div>
        <div className="flex gap-2 pt-3 border-t border-slate-150">
          <button
            type="button"
            onClick={onClose}
            className={`flex-1 px-3 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} cursor-pointer`}
          >
            {t('knowledge.nav.recommend_cancel')}
          </button>
          <button
            type="button"
            onClick={onApply}
            disabled={busy}
            className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-purple-600 hover:bg-purple-500 text-white rounded-lg cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50"
          >
            <Sparkles size={11} />
            {t('knowledge.nav.recommend_apply')}
          </button>
        </div>
      </div>
    </div>
  );
}
