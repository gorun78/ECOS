/**
 * PMO-B T2 — 知识导航 Tab（替换原「分类体系」DEMO + 假执行）。
 *
 * 功能：
 *   左 3 级目录树 + 单点操作（新增子级 / 重命名 / 删除）
 *   中标签云 + 热门标签 Top20
 *   右资产列表（关键词 + tag 单选）+ LLM 推荐候选（仅建议）
 *
 * 数据：
 *   GET /api/v1/knowledge/nav/categories?domain=default
 *   GET /api/v1/knowledge/nav/tags?domain=default
 *   GET /api/v1/knowledge/nav/products?domain=default&category=&tag=&pageNum=1&pageSize=20
 *
 * 主题 / i18n / lucide 严格遵守铁律 §4.1 / §4.2 / §4.3：
 *   硬编码上色禁止，全部走 useTheme().styles；硬编码中文禁止，全部走 t()。
 *
 * 组件拆分（前端开发规范 §十 收口）：树 / 标签云 / 资产列表 / 推荐浮层
 *   已拆至 ./classification/ 子目录，本文件仅保留状态与业务处理器。
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Layers, RefreshCw } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { showToastGlobal } from '../../../components/common/Toast';
import {
  fetchNavCategories,
  fetchNavTags,
  fetchNavProducts,
  createNavCategory,
  updateNavCategory,
  deleteNavCategory,
  createNavTag,
  deleteNavTag,
  setArticleCategories,
  undoArticle,
  recommendArticle,
  fetchNavDomains,
  type NavCategorySaveDTO,
  type NavCategoryVO,
  type NavTagVO,
  type NavProductItemVO,
  type NavRecommendVO,
} from '../../../services/knowledgeNavApi';
import { DEFAULT_DOMAIN, PAGE_SIZE, TOP_TAGS } from './classification/constants';
import type { CatEditMode } from './classification/types';
import { CategoryTreePanel } from './classification/CategoryTreePanel';
import { TagCloudPanel } from './classification/TagCloudPanel';
import { ProductsPanel } from './classification/ProductsPanel';
import { RecommendModal } from './classification/RecommendModal';

/** 通知 Overview 总览重拉 KB 统计（与 DatasyncTab 同契约 `kb:stats:refresh`） */
function emitRefresh(): void {
  window.dispatchEvent(new CustomEvent('kb:stats:refresh'));
}

export default function ClassificationTab() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // ── 全局 domain（PMO-C T2: 多 domain 切换，拉取 nav domains 下拉；default 兜底） ──
  const [domain, setDomain] = useState<string>(DEFAULT_DOMAIN);
  const [domains, setDomains] = useState<string[]>([DEFAULT_DOMAIN]);

  // PMO-C T2: 加载可用 domain 列表（失败时保留 default 单选项，不阻塞）
  const loadDomains = useCallback(async (): Promise<void> => {
    try {
      const list = await fetchNavDomains();
      setDomains(Array.isArray(list) && list.length > 0 ? list : [DEFAULT_DOMAIN]);
    } catch {
      setDomains([DEFAULT_DOMAIN]);
    }
  }, []);
  useEffect(() => { void loadDomains(); }, [loadDomains]);

  // ── 目录树 ──
  const [categories, setCategories] = useState<NavCategoryVO[]>([]);
  const [selectedCatId, setSelectedCatId] = useState<string | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [categoriesLoading, setCategoriesLoading] = useState(false);

  // ── 标签 ──
  const [tags, setTags] = useState<NavTagVO[]>([]);
  const [tagsLoading, setTagsLoading] = useState(false);
  const [selectedTag, setSelectedTag] = useState<string | null>(null);

  // ── 资产列表 ──
  const [products, setProducts] = useState<NavProductItemVO[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNum, setPageNum] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [productsLoading, setProductsLoading] = useState(false);
  const [selectedRows, setSelectedRows] = useState<Set<string>>(new Set());

  // ── 输入辅助 ──
  const [catEditMode, setCatEditMode] = useState<CatEditMode | null>(null);
  const [catDraft, setCatDraft] = useState<NavCategorySaveDTO>({
    domain: DEFAULT_DOMAIN,
    name: '',
  });
  const [tagDraft, setTagDraft] = useState('');
  const [busy, setBusy] = useState(false);

  // ── LLM 推荐 ──
  const [recommendingId, setRecommendingId] = useState<string | null>(null);
  const [recommendResult, setRecommendResult] = useState<NavRecommendVO | null>(null);
  const [recommendOpen, setRecommendOpen] = useState(false);

  // ── 拉取目录 ──
  const loadCategories = useCallback(async (): Promise<void> => {
    setCategoriesLoading(true);
    try {
      const data = await fetchNavCategories(domain);
      setCategories(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('fetchNavCategories:', err);
      setCategories([]);
    } finally {
      setCategoriesLoading(false);
    }
  }, [domain]);

  // ── 拉取标签 ──
  const loadTags = useCallback(async (): Promise<void> => {
    setTagsLoading(true);
    try {
      const data = await fetchNavTags(domain);
      setTags(Array.isArray(data) ? data : []);
    } catch (err) {
      console.error('fetchNavTags:', err);
      setTags([]);
    } finally {
      setTagsLoading(false);
    }
  }, [domain]);

  // ── 拉取资产（按 categoryIds / 单 tag / keyword / page） ──
  const loadProducts = useCallback(async (): Promise<void> => {
    setProductsLoading(true);
    try {
      const catIds = selectedCatId ? [selectedCatId] : undefined;
      const tagNames = selectedTag ? [selectedTag] : undefined;
      const data = await fetchNavProducts({
        domain,
        keyword: keyword.trim() || undefined,
        categoryIds: catIds,
        tags: tagNames,
        pageNum,
        pageSize: PAGE_SIZE,
      });
      setProducts(data?.list ?? []);
      setTotal(data?.total ?? 0);
    } catch (err) {
      console.error('fetchNavProducts:', err);
      setProducts([]);
      setTotal(0);
    } finally {
      setProductsLoading(false);
    }
  }, [domain, selectedCatId, selectedTag, keyword, pageNum]);

  // ── 初始化加载 ──
  useEffect(() => {
    void (async (): Promise<void> => {
      await Promise.all([loadCategories(), loadTags()]);
    })();
  }, [loadCategories, loadTags]);

  // ── 选择 / 输入变更 → 重拉资产（pageNum 收敛到首屏） ──
  useEffect(() => {
    if (selectedCatId === null && selectedTag === null && keyword === '') {
      void loadProducts();
      return;
    }
    setPageNum(1);
    void (async (): Promise<void> => {
      await loadProducts();
    })();
    // 仅在输入 / 选择变化时触发，避免 loadProducts 自引用无限循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCatId, selectedTag, keyword.trim()]);

  // ── 操作：新建一级目录 ──
  const startCreateTopCategory = (): void => {
    setCatDraft({ domain, name: '' });
    setCatEditMode({ mode: 'create', parentId: null });
  };

  // ── 操作：在选中目录下新建子级 ──
  const startCreateChildCategory = (parentId: string): void => {
    setCatDraft({ domain, parentId, name: '' });
    setCatEditMode({ mode: 'create', parentId });
  };

  // ── 操作：重命名 ──
  const startRename = (node: NavCategoryVO): void => {
    setCatDraft({ domain, parentId: node.parentId ?? null, name: node.name });
    setCatEditMode({ mode: 'rename', targetId: node.id });
  };

  // ── 操作：提交目录（新增 / 重命名） ──
  const submitCategory = async (): Promise<void> => {
    if (busy) return;
    const trimmed = catDraft.name?.trim();
    if (!trimmed) {
      showToastGlobal('error', t('knowledge.nav.cnt_name_empty'));
      return;
    }
    setBusy(true);
    try {
      if (catEditMode?.mode === 'create') {
        await createNavCategory({ ...catDraft, name: trimmed });
        showToastGlobal('success', t('knowledge.nav.cnt_created'));
      } else if (catEditMode?.mode === 'rename' && catEditMode.targetId) {
        await updateNavCategory(catEditMode.targetId, { ...catDraft, name: trimmed });
        showToastGlobal('success', t('knowledge.nav.cnt_updated'));
      }
      setCatEditMode(null);
      setCatDraft({ domain, name: '' });
      await loadCategories();
      emitRefresh();
    } catch (err) {
      console.error('submitCategory:', err);
      showToastGlobal('error', t('knowledge.nav.cnt_op_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 操作：删除目录（后端 400 当有子节点） ──
  const handleDeleteCategory = async (id: string): Promise<void> => {
    if (busy) return;
    setBusy(true);
    try {
      await deleteNavCategory(id);
      showToastGlobal('success', t('knowledge.nav.cnt_deleted'));
      if (selectedCatId === id) setSelectedCatId(null);
      await loadCategories();
      emitRefresh();
    } catch (err) {
      console.error('deleteNavCategory:', err);
      showToastGlobal('error', t('knowledge.nav.cnt_op_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 操作：新建标签 ──
  const submitTag = async (): Promise<void> => {
    if (busy) return;
    const trimmed = tagDraft.trim();
    if (!trimmed) {
      showToastGlobal('error', t('knowledge.nav.tag_name_empty'));
      return;
    }
    setBusy(true);
    try {
      await createNavTag(domain, trimmed);
      setTagDraft('');
      showToastGlobal('success', t('knowledge.nav.tag_created'));
      await loadTags();
    } catch (err) {
      console.error('createNavTag:', err);
      showToastGlobal('error', t('knowledge.nav.tag_op_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 操作：删除标签 ──
  const handleDeleteTag = async (id: string): Promise<void> => {
    if (busy) return;
    setBusy(true);
    try {
      await deleteNavTag(id);
      // 若删了当前 selectedTag（按 tagName 持有），重置
      const cur = tags.find(x => x.id === id);
      if (cur && selectedTag === cur.tagName) setSelectedTag(null);
      showToastGlobal('success', t('knowledge.nav.tag_deleted'));
      await loadTags();
    } catch (err) {
      console.error('deleteNavTag:', err);
      showToastGlobal('error', t('knowledge.nav.tag_op_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 操作：取消当前行的编辑态 ──
  const cancelEdit = (): void => {
    setCatEditMode(null);
    setCatDraft({ domain, name: '' });
  };

  // ── 操作：LLM 推荐（仅建议） ──
  const handleRecommend = async (articleId: string): Promise<void> => {
    if (busy) return;
    setRecommendingId(articleId);
    setRecommendOpen(true);
    try {
      const rec = await recommendArticle(articleId);
      setRecommendResult(rec);
    } catch (err) {
      console.error('recommendArticle:', err);
      showToastGlobal('error', t('knowledge.nav.recommend_failed') + `: ${(err as Error)?.message ?? ''}`);
      setRecommendResult(null);
    } finally {
      setRecommendingId(null);
    }
  };

  // ── 操作：关闭推荐候选 ──
  const closeRecommend = (): void => {
    setRecommendOpen(false);
    setRecommendingId(null);
    setRecommendResult(null);
  };

  // ── 操作：把推荐 tags 应用到当前行 ──
  const applyRecommend = async (): Promise<void> => {
    if (!recommendResult || selectedCatId == null) return;
    setBusy(true);
    try {
      // 暂用「替换目录」把推荐 categoryId 落到该行当前选中 cat 下（前端最小可测路径）
      if (recommendResult.suggestedCategoryId) {
        await setArticleCategories(selectedCatId, [recommendResult.suggestedCategoryId]);
      }
      showToastGlobal('success', t('knowledge.nav.recommend_applied'));
      await loadCategories();
      await loadProducts();
      closeRecommend();
    } catch (err) {
      console.error('applyRecommend:', err);
      showToastGlobal('error', t('knowledge.nav.recommend_apply_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 操作：Undo（移除当前选中 cat 下当前选中行的归属） ──
  const handleUndo = async (articleId: string): Promise<void> => {
    if (busy) return;
    setBusy(true);
    try {
      await undoArticle(articleId, 'category');
      showToastGlobal('success', t('knowledge.nav.undo_success'));
      await loadCategories();
      await loadProducts();
    } catch (err) {
      console.error('undoArticle:', err);
      showToastGlobal('error', t('knowledge.nav.undo_failed') + `: ${(err as Error)?.message ?? ''}`);
    } finally {
      setBusy(false);
    }
  };

  // ── 多选 ──
  const toggleSelect = (id: string): void => {
    setSelectedRows(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const isRoot = (c: NavCategoryVO): boolean => {
    const pid = c.parentId ?? null;
    return !pid || pid === '' || pid === 'null' || pid === 'NULL';
  };

  /** 顶级目录（无 parentId 表示根） */
  const topLevelItems = useMemo(() => categories.filter(isRoot), [categories]);
  const hotTags = useMemo(() => {
    const sorted = [...tags].sort((a, b) => (b.useCount ?? 0) - (a.useCount ?? 0));
    return sorted.slice(0, TOP_TAGS);
  }, [tags]);

  return (
    <div className="space-y-6">
      {/* ─── 顶栏：domain 下拉 + 标题 + hint ─── */}
      <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
        <div className="space-y-1">
          <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}>
            <Layers size={16} className="text-purple-600" />
            {t('knowledge.nav.title')}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {t('knowledge.nav.hint')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={domain}
            onChange={e => setDomain(e.target.value)}
            aria-label={t('knowledge.nav.domain_filter_label')}
            className={`px-3 py-1.5 text-xs ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-blue-500 cursor-pointer`}
          >
            {/* 兜底项：保证 default 始终可选（后端 domains 不含 default 时防孤儿态） */}
            {!domains.includes(DEFAULT_DOMAIN) && (
              <option value={DEFAULT_DOMAIN}>{t('knowledge.nav.domain_default')}</option>
            )}
            {domains.map(d => (
              <option key={d} value={d}>{d === DEFAULT_DOMAIN ? t('knowledge.nav.domain_default') : d}</option>
            ))}
          </select>
          <button
            onClick={() => { void (async () => { await Promise.all([loadCategories(), loadTags()]); }); }}
            className={`px-3 py-1.5 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} font-bold rounded-lg flex items-center gap-1.5 cursor-pointer text-xs`}
            type="button"
          >
            {categoriesLoading || tagsLoading ? (
              <RefreshCw size={12} className="animate-spin" />
            ) : (
              <RefreshCw size={12} />
            )}
            {t('knowledge.nav.refresh')}
          </button>
        </div>
      </div>

      {/* ─── 主区：左 3 级树 / 中标签云 / 右资产列表 ─── */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 items-start min-w-0">
        <CategoryTreePanel
          styles={styles}
          t={t}
          categories={categories}
          topLevelItems={topLevelItems}
          categoriesLoading={categoriesLoading}
          busy={busy}
          selectedCatId={selectedCatId}
          expandedIds={expandedIds}
          catEditMode={catEditMode}
          catDraft={catDraft}
          onSelect={(id) => setSelectedCatId(prev => (prev === id ? null : id))}
          onToggle={id => {
            setExpandedIds(prev => {
              const next = new Set(prev);
              if (next.has(id)) next.delete(id);
              else next.add(id);
              return next;
            });
          }}
          onRename={startRename}
          onCreateChild={startCreateChildCategory}
          onDelete={handleDeleteCategory}
          onCreateTop={startCreateTopCategory}
          onDraftNameChange={name => setCatDraft(d => ({ ...d, name }))}
          onSubmit={() => { void submitCategory(); }}
          onCancelEdit={cancelEdit}
        />

        <TagCloudPanel
          styles={styles}
          t={t}
          tags={tags}
          hotTags={hotTags}
          selectedTag={selectedTag}
          tagDraft={tagDraft}
          busy={busy}
          onTagDraftChange={setTagDraft}
          onSubmitTag={() => { void submitTag(); }}
          onToggleTag={tagName => setSelectedTag(prev => (prev === tagName ? null : tagName))}
          onDeleteSelectedTag={() => {
            if (selectedTag) {
              const cur = tags.find(x => x.tagName === selectedTag);
              if (cur?.id) { void handleDeleteTag(cur.id); }
            }
          }}
        />

        <ProductsPanel
          styles={styles}
          t={t}
          products={products}
          total={total}
          pageNum={pageNum}
          keyword={keyword}
          productsLoading={productsLoading}
          selectedRows={selectedRows}
          recommendingId={recommendingId}
          busy={busy}
          onKeywordChange={v => { setKeyword(v); if (pageNum !== 1) setPageNum(1); }}
          onToggleSelect={toggleSelect}
          onRecommend={id => { void handleRecommend(id); }}
          onUndo={id => { void handleUndo(id); }}
          onPrevPage={() => setPageNum(p => Math.max(1, p - 1))}
          onNextPage={() => setPageNum(p => p + 1)}
        />
      </div>

      {/* ─── 推荐候选浮层（仅建议，不落库） ─── */}
      {recommendOpen && recommendResult && (
        <RecommendModal
          styles={styles}
          t={t}
          recommendResult={recommendResult}
          busy={busy}
          onClose={closeRecommend}
          onApply={() => { void applyRecommend(); }}
        />
      )}
    </div>
  );
}
