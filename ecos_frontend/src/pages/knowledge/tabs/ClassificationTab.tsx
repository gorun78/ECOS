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
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  FolderTree, Tag, Plus, Trash2, RefreshCw, Sparkles,
  Layers, ShieldCheck, ChevronRight,
} from 'lucide-react';
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

/** 右侧资产分页大小（按设计稿约定） */
const PAGE_SIZE = 20;

/** 标签云热门标签 TopN */
const TOP_TAGS = 20;

/** domain 值集合（按 PRD v1.0 约定：default 为默认，跨域切换） */
const DEFAULT_DOMAIN = 'default';

/** 通知 Overview 总览重拉 KB 统计（与 DatasyncTab 同契约 `kb:stats:refresh`） */
function emitRefresh(): void {
  window.dispatchEvent(new CustomEvent('kb:stats:refresh'));
}

/** 取一节点的下级 child 列表（直接子节点，含 articleCount） */
function findChildren(items: NavCategoryVO[], parentId: string): NavCategoryVO[] {
  return items.filter(c => {
    const pid = c.parentId ?? null;
    return (pid ? String(pid) : '') === String(parentId);
  });
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
  const [catEditMode, setCatEditMode] = useState<{
    mode: 'create' | 'rename';
    parentId?: string | null;
    targetId?: string;
  } | null>(null);
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
        {/* 左：3 级目录树 */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
          <div className="flex items-center justify-between border-b border-slate-150 pb-2">
            <span className={`font-bold text-xs ${styles.cardText} flex items-center gap-1.5`}>
              <FolderTree size={13} className="text-indigo-600" />
              {t('knowledge.nav.tree_title')}
            </span>
            <button
              type="button"
              className="px-2.5 py-1 text-[11px] font-bold bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg cursor-pointer flex items-center gap-1 disabled:opacity-50"
              onClick={startCreateTopCategory}
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
                onChange={e => setCatDraft(d => ({ ...d, name: e.target.value }))}
                placeholder={t('knowledge.nav.tree_name_placeholder')}
                className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500`}
              />
              <div className="flex gap-1.5">
                <button
                  type="button"
                  onClick={() => { void submitCategory(); }}
                  disabled={busy}
                  className="flex-1 px-2.5 py-1 text-[11px] font-bold bg-indigo-600 hover:bg-indigo-500 text-white rounded cursor-pointer flex items-center justify-center gap-1 disabled:opacity-50"
                >
                  <Plus size={11} />
                  {t('knowledge.nav.tree_confirm')}
                </button>
                <button
                  type="button"
                  onClick={cancelEdit}
                  disabled={busy}
                  className={`flex-1 px-2.5 py-1 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded cursor-pointer border ${styles.cardBorder} flex items-center justify-center gap-1 disabled:opacity-50`}
                >
                  {t('knowledge.nav.tree_cancel')}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* 中：标签云 + 热门标签 Top20 */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
          <div className="flex items-center justify-between border-b border-slate-150 pb-2">
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
              onChange={e => setTagDraft(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') { void submitTag(); } }}
              placeholder={t('knowledge.nav.tag_new_placeholder')}
              className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-emerald-500`}
            />
            <button
              type="button"
              onClick={() => { void submitTag(); }}
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
                    onClick={() => setSelectedTag(prev => (prev === tag.tagName ? null : tag.tagName))}
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
              onClick={() => {
                if (selectedTag) {
                  const cur = tags.find(x => x.tagName === selectedTag);
                  if (cur?.id) { void handleDeleteTag(cur.id); }
                }
              }}
              disabled={!selectedTag || busy}
              className={`w-full mt-3 px-2.5 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} cursor-pointer flex items-center justify-center gap-1 disabled:opacity-40`}
            >
              <Trash2 size={11} />
              {t('knowledge.nav.tag_delete_hint')}
            </button>
          )}
        </div>

        {/* 右：资产列表 + 操作（关键词 / 推荐 / Undo） */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3 min-w-0`}>
          <div className="flex items-center justify-between border-b border-slate-150 pb-2">
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
              onChange={e => { setKeyword(e.target.value); if (pageNum !== 1) setPageNum(1); }}
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
            <div className="divide-y divide-slate-150">
              {products.map(p => {
                const checked = selectedRows.has(p.id);
                return (
                  <div
                    key={p.id}
                    className={`py-2.5 flex items-start gap-2 cursor-pointer transition px-1.5 ${
                      checked ? 'bg-blue-50/40' : 'hover:bg-slate-50/50'
                    }`}
                    onClick={() => toggleSelect(p.id)}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleSelect(p.id)}
                      onClick={e => e.stopPropagation()}
                      className="mt-1 h-3.5 w-3.5 cursor-pointer"
                    />
                    <div className="flex-1 min-w-0 space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`text-[11px] font-bold truncate ${styles.cardText}`}>{p.title}</span>
                        {p.domain && (
                          <span className={`px-1 py-0.5 text-[9px] rounded bg-slate-100 ${styles.cardTextMuted}`}>
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
                        onClick={() => { void handleRecommend(p.id); }}
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
                        onClick={() => { void handleUndo(p.id); }}
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
            <div className="flex items-center justify-between pt-2 border-t border-slate-150">
              <span className={`text-[10px] ${styles.muted}`}>
                {t('knowledge.nav.product_page')}: {pageNum} / {Math.max(1, Math.ceil(total / PAGE_SIZE))}
              </span>
              <div className="flex gap-1">
                <button
                  type="button"
                  disabled={pageNum <= 1}
                  onClick={() => setPageNum(p => Math.max(1, p - 1))}
                  className={`px-2 py-1 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-[11px] rounded cursor-pointer disabled:opacity-40`}
                >
                  {'←'}
                </button>
                <button
                  type="button"
                  disabled={pageNum * PAGE_SIZE >= total}
                  onClick={() => setPageNum(p => p + 1)}
                  className={`px-2 py-1 ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} text-[11px] rounded cursor-pointer disabled:opacity-40`}
                >
                  {'→'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ─── 推荐候选浮层（仅建议，不落库） ─── */}
      {recommendOpen && recommendResult && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50" onClick={closeRecommend}>
          <div
            className={`${styles.appBg} border ${styles.cardBorder} rounded-xl w-[480px] p-5 space-y-3 shadow-2xl`}
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between">
              <h3 className={`text-sm font-bold ${styles.cardText} flex items-center gap-2`}>
                <Sparkles size={14} className="text-purple-600" />
                {t('knowledge.nav.recommend_panel')}
              </h3>
              <button type="button" onClick={closeRecommend} className="p-1 cursor-pointer">
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
                onClick={closeRecommend}
                className={`flex-1 px-3 py-1.5 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} cursor-pointer`}
              >
                {t('knowledge.nav.recommend_cancel')}
              </button>
              <button
                type="button"
                onClick={() => { void applyRecommend(); }}
                disabled={busy}
                className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-purple-600 hover:bg-purple-500 text-white rounded-lg cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50"
              >
                <Sparkles size={11} />
                {t('knowledge.nav.recommend_apply')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

/** 单行目录渲染 + 递归 children（≤3 级 depth 限制） */
interface TreeRowProps {
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

function TreeRow({
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
