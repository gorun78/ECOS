/**
 * KnowledgeCategoryFilter — 知识资产七分类筛选面板（#147U / F-KB-18）。
 *
 * 主题守护 §4.1：0 硬编码色（全 useTheme().styles）；图标仅 lucide-react；文案全 t()。
 * 状态分治（禁把失败渲染成空态）：
 *   未选分类 → 提示选择（后端分类必填，前端不发无分类请求）
 *   加载中 → 骨架文案；失败 → 显式错误码 + 重试；空结果 → 空态并说明「存量未分类」成因
 *
 * 后端契约见 `../services/categoryFilter.ts`（不在本组件拼 URL）。
 */
import React, { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Filter, Loader2, RefreshCw, Tags } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  KNOWLEDGE_CATEGORIES,
  fetchArticlesByCategoryPage,
  type KnowledgeArticleRow,
  type KnowledgeCategoryCode,
} from '../services/categoryFilter';
import { KbApiError } from '../services/kbFetch';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const CATEGORY_LABEL_KEY: Record<KnowledgeCategoryCode, string> = {
  Fact: 'knowledge.categoryFact',
  Concept: 'knowledge.categoryConcept',
  Procedure: 'knowledge.categoryProcedure',
  Rule: 'knowledge.categoryRule',
  Case: 'knowledge.categoryCase',
  Evidence: 'knowledge.categoryEvidence',
  Pattern: 'knowledge.categoryPattern',
};

export default function KnowledgeCategoryFilter() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [selected, setSelected] = useState<KnowledgeCategoryCode | null>(null);
  const [rows, setRows] = useState<KnowledgeArticleRow[]>([]);
  const [state, setState] = useState<LoadState>('idle');
  const [errorCode, setErrorCode] = useState<string | null>(null);
  // 游标分页（#147BG）：nextCursor 为 null 表示已到末页；loadingMore 只影响「加载更多」按钮
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState(false);

  const load = useCallback(async (category: KnowledgeCategoryCode) => {
    setState('loading');
    setErrorCode(null);
    setNextCursor(null);
    setLoadMoreError(false);
    try {
      const page = await fetchArticlesByCategoryPage(category);
      setRows(page.items);
      setNextCursor(page.nextCursor);
      setState('ready');
    } catch (err) {
      setRows([]);
      setErrorCode(err instanceof KbApiError ? (err.code ?? `HTTP ${err.status}`) : 'NETWORK');
      setState('error');
    }
  }, []);

  /** 追加下一页；失败不清空已加载行（把已看到的数据一并洗掉等于倒退）。 */
  const loadMore = useCallback(async () => {
    if (!selected || !nextCursor || loadingMore) {
      return;
    }
    setLoadingMore(true);
    setLoadMoreError(false);
    try {
      const page = await fetchArticlesByCategoryPage(selected, undefined, nextCursor);
      setRows((prev) => [...prev, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setLoadMoreError(true);
    } finally {
      setLoadingMore(false);
    }
  }, [selected, nextCursor, loadingMore]);

  useEffect(() => {
    if (selected) {
      void load(selected);
    }
  }, [selected, load]);

  const chip = (code: KnowledgeCategoryCode) => {
    const active = selected === code;
    return (
      <button
        key={code}
        type="button"
        onClick={() => setSelected(code)}
        className="px-2.5 py-1 rounded-md border text-xs transition-colors"
        style={{
          borderColor: active ? styles.accentBorder : styles.cardBorder,
          background: active ? styles.badgeBg : 'transparent',
          color: active ? styles.cardText : styles.cardTextMuted,
        }}
      >
        {t(CATEGORY_LABEL_KEY[code])}
      </button>
    );
  };

  return (
    <section
      className="rounded-md border p-3 flex flex-col gap-3"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardText }}
      data-testid="knowledge-category-filter"
    >
      <div className="flex items-center gap-2">
        <Filter className="w-4 h-4" style={{ color: styles.cardTextMuted }} />
        <span className="text-sm font-medium">{t('knowledge.categoryFilterTitle')}</span>
        <span className="text-xs" style={{ color: styles.cardTextMuted }}>
          {t('knowledge.categoryFilterHint')}
        </span>
      </div>

      <div className="flex flex-wrap gap-1.5">{KNOWLEDGE_CATEGORIES.map(chip)}</div>

      {state === 'idle' && (
        <div className="text-xs flex items-center gap-2" style={{ color: styles.cardTextMuted }}
             data-testid="knowledge-category-idle">
          <Tags className="w-3.5 h-3.5" />
          {t('knowledge.categoryFilterIdle')}
        </div>
      )}

      {state === 'loading' && (
        <div className="text-xs flex items-center gap-2" style={{ color: styles.cardTextMuted }}
             data-testid="knowledge-category-loading">
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
          {t('knowledge.categoryFilterLoading')}
        </div>
      )}

      {state === 'error' && (
        <div className="text-xs flex items-center gap-2" style={{ color: styles.warningText }}
             data-testid="knowledge-category-error">
          <AlertTriangle className="w-3.5 h-3.5" />
          {t('knowledge.categoryFilterError')}（{errorCode}）
          <button
            type="button"
            className="ml-1 px-2 py-0.5 rounded border inline-flex items-center gap-1"
            style={{ borderColor: styles.cardBorder, color: styles.cardText }}
            onClick={() => selected && void load(selected)}
          >
            <RefreshCw className="w-3 h-3" />
            {t('knowledge.categoryFilterRetry')}
          </button>
        </div>
      )}

      {state === 'ready' && rows.length === 0 && (
        <div className="text-xs flex flex-col gap-1" style={{ color: styles.cardTextMuted }}
             data-testid="knowledge-category-empty">
          <span>{t('knowledge.categoryFilterEmpty')}</span>
          <span>{t('knowledge.categoryFilterEmptyHint')}</span>
        </div>
      )}

      {state === 'ready' && rows.length > 0 && (
        <ul className="flex flex-col divide-y" style={{ borderColor: styles.cardBorder }}
            data-testid="knowledge-category-list">
          {rows.map((row) => (
            <li key={row.id} className="py-1.5 flex items-center gap-2 text-xs">
              <span className="flex-1 truncate">{row.title}</span>
              {row.status && (
                <span className="px-1.5 py-0.5 rounded" style={{ background: styles.badgeBg, color: styles.badgeText }}>
                  {row.status}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      {state === 'ready' && rows.length > 0 && nextCursor && (
        <div className="flex items-center gap-2 text-xs">
          <button
            type="button"
            data-testid="knowledge-category-load-more"
            disabled={loadingMore}
            onClick={() => void loadMore()}
            className="px-2 py-0.5 rounded border inline-flex items-center gap-1 disabled:opacity-60"
            style={{ borderColor: styles.cardBorder, color: styles.cardText }}
          >
            {loadingMore ? <Loader2 className="w-3 h-3 animate-spin" /> : <Tags className="w-3 h-3" />}
            {loadingMore ? t('knowledge.categoryLoadingMore') : t('knowledge.categoryLoadMore')}
          </button>
          {loadMoreError && (
            <span data-testid="knowledge-category-load-more-error" style={{ color: styles.warningText }}>
              {t('knowledge.categoryLoadMoreError')}
            </span>
          )}
        </div>
      )}
    </section>
  );
}
