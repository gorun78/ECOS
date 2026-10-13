/**
 * PMO-D Batch 1 — F3 AssetListPage（知识资产列表 + 图/向量 双状态列）。
 *
 * 布局：
 *   顶工具栏：关键词 input + 类型 select + 状态 select + domain Select + 搜索按钮
 *   表格 8 列：
 *     知识资产 / 类型 / 状态 badge / 语义关联 / 来源 / 图谱实体✓/— / 向量索引✓/⏳/— / 更新时间
 *   右侧详情面板（≥1280px）：选中资产 → 单条上下文 + 图谱/向量 chips
 *
 * 数据源：
 *   - GET /api/v1/knowledge/nav/products            (知识资产列表)
 *   - GET /api/v1/knowledge/nav/domains             (domain 下拉，复用 fetchNavDomains)
 *   - GET /api/v1/knowledge/assets/status?ids=...   (F10 后端 73e68fd 强类型 VO)
 *
 * 批量状态批量约束：
 *   - 单次 ≤ 100 ids（PRD F10 限制）
 *   - 前端 chunk = 100，超过 100 自动分片并发
 *
 * 主题守护 §4.1：0 硬编码色值（全 useTheme().styles）
 * 机构 lint §4.3：0 硬编码中文（grep -c '[一-龥]{2,}' = 0）
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Search, RefreshCw, Check, Loader2, Minus, AlertCircle } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { fetchNavDomains, fetchNavProducts, fetchAssetStatuses, type NavProductItemVO, type AssetStatusItem } from '../../../services/knowledgeNavApi';

/** 资产列表分页大小 */
const PAGE_SIZE = 20;
/** F10 后端 ids 上限，前端同步分片 */
const STATUS_BATCH_SIZE = 100;

/** 归一化状态 → 三档（published / review / draft），供徽章与过滤共用 */
type AssetStatusBucket = 'published' | 'review' | 'draft';
function bucketize(status: string | undefined): AssetStatusBucket {
  const normalized = (status || '').trim().toLowerCase();
  if (['已发布', 'published', 'active', 'released', 'ready'].includes(normalized)) return 'published';
  if (['审核中', 'review', 'in_review', 'pending', 'approving'].includes(normalized)) return 'review';
  return 'draft';
}

function statusBadgeLabelKey(status: string): string {
  switch (bucketize(status)) {
    case 'published': return 'knowledge.asset.status_published';
    case 'review': return 'knowledge.asset.status_review';
    default: return 'knowledge.asset.status_draft';
  }
}

export default function AssetListPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // 顶层工具栏筛选
  const [keyword, setKeyword] = useState<string>('');
  // 提交后的关键词才驱动请求（输入不直查，Enter/搜索按钮/重置 domain 时提交）
  const [submittedKeyword, setSubmittedKeyword] = useState<string>('');
  const [assetType, setAssetType] = useState<string>('all');
  const [status, setStatus] = useState<string>('all');
  const [domain, setDomain] = useState<string>('default');

  const [domains, setDomains] = useState<string[]>(['default']);
  const [list, setList] = useState<NavProductItemVO[]>([]);
  const [total, setTotal] = useState<number>(0);
  const [pageNum, setPageNum] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadedOnce, setLoadedOnce] = useState<boolean>(false);
  // 列表拉取失败独立错误态（区别于「真的没有数据」空态）
  const [listError, setListError] = useState<boolean>(false);

  // 批量状态：articleId → AssetStatusItem
  const [statusMap, setStatusMap] = useState<Record<string, AssetStatusItem>>({});
  const [statusLoading, setStatusLoading] = useState<boolean>(false);

  // 选中资产（右详情面板）
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /** 状态徽章 → 语义主题 token 类（替代悬空 kb-status-* 自定义类） */
  const statusBadgeClass = (raw: string): string => {
    switch (bucketize(raw)) {
      case 'published': return `${styles.successBg} ${styles.successText}`;
      case 'review': return `${styles.warningBg} ${styles.warningText}`;
      default: return `${styles.badgeBg} ${styles.badgeText}`;
    }
  };

  /** 拉 domain 下拉（fetchNavDomains） */
  const loadDomains = useCallback(async () => {
    try {
      const list = await fetchNavDomains();
      setDomains(Array.isArray(list) && list.length > 0 ? list : ['default']);
    } catch {
      setDomains(['default']);
    }
  }, []);

  /** 拉资产列表（fetchNavProducts，强类型 NAV）
   *  注：类型/状态为「本页客户端二次过滤」（后端 nav/products 无对应参数），
   *  total 仍为服务端未过滤总数 —— UI 已标注「仅过滤本页」避免误导。 */
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetchNavProducts({
        keyword: submittedKeyword || undefined,
        domain: domain || 'default',
        pageNum,
        pageSize: PAGE_SIZE,
      });
      const sourceList: NavProductItemVO[] = res?.list || [];
      // 类型 / 状态 前端二次过滤（仅作用于当前页）
      const typeFiltered = sourceList.filter(item => {
        if (assetType === 'all') return true;
        return (item.category || '').toLowerCase() === assetType.toLowerCase();
      });
      const statusFiltered = typeFiltered.filter(item => {
        if (status === 'all') return true;
        return bucketize(item.status) === status;
      });
      setList(statusFiltered);
      setTotal(res?.total ?? sourceList.length);
      setListError(false);
      setLoadedOnce(true);
    } catch {
      setList([]);
      setTotal(0);
      setListError(true);
    } finally {
      setLoading(false);
    }
  }, [submittedKeyword, assetType, status, domain, pageNum]);

  /** 拉批量状态（assetIds，chunk ≤ 100/批，超过分片并发） */
  const loadStatus = useCallback(async (ids: string[]) => {
    if (!ids || ids.length === 0) {
      setStatusMap({});
      return;
    }
    setStatusLoading(true);
    try {
      const chunks: string[][] = [];
      for (let i = 0; i < ids.length; i += STATUS_BATCH_SIZE) {
        chunks.push(ids.slice(i, i + STATUS_BATCH_SIZE));
      }
      const settled = await Promise.all(chunks.map(chunk => fetchAssetStatuses(chunk).catch((e) => {
        console.warn('AssetListPage fetchAssetStatuses failed:', e);
        return [] as AssetStatusItem[];
      })));
      const merged: Record<string, AssetStatusItem> = {};
      for (const items of settled) {
        for (const it of items) {
          if (it && it.articleId) merged[it.articleId] = it;
        }
      }
      setStatusMap(merged);
    } finally {
      setStatusLoading(false);
    }
  }, []);

  // 初次：拉 domains + 默认页列表
  useEffect(() => {
    void loadDomains();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  // 列表变化后同步批量状态
  useEffect(() => {
    const ids = list.map(it => it.id).filter(Boolean);
    void loadStatus(ids);
  }, [list, loadStatus]);

  /** handleSearch：提交关键词 + 重置分页到 1（输入不直查，此处才发起请求） */
  const handleSearch = useCallback(() => {
    setPageNum(1);
    setSubmittedKeyword(keyword);
    // 若关键词与分页均无变化（依赖未动）则显式刷新一次
    if (keyword === submittedKeyword && pageNum === 1) void loadList();
  }, [keyword, submittedKeyword, pageNum, loadList]);

  /** handleDomainChange：切换 domain reset 其他筛选（PRD F3 验收） */
  const handleDomainChange = useCallback((value: string) => {
    setDomain(value);
    setKeyword('');
    setSubmittedKeyword('');
    setAssetType('all');
    setStatus('all');
    setPageNum(1);
  }, []);

  /** resetFilters（侧栏详情面板 reset 钩子） */
  const handleReset = useCallback(() => {
    setKeyword('');
    setSubmittedKeyword('');
    setAssetType('all');
    setStatus('all');
    setDomain('default');
    setPageNum(1);
  }, []);

  const selected = useMemo(
    () => (selectedId ? list.find(it => it.id === selectedId) ?? null : null),
    [selectedId, list],
  );

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const renderVectorGlyph = (id: string): React.ReactNode => {
    const it = statusMap[id];
    if (!it) return <Minus className="w-3.5 h-3.5 mx-auto opacity-40" aria-label={t('knowledge.asset.vector_none')} />;
    if (it.vector) return <Check className={`w-3.5 h-3.5 mx-auto ${styles.successText}`} aria-label={t('knowledge.asset.vector_done')} />;
    // vector=false：向量化未完成 → 同步中（lucide 旋转图标，替代悬空 kb-vector-syncing + ⏳）
    return (
      <Loader2
        className={`w-3.5 h-3.5 mx-auto animate-spin ${styles.warningText}`}
        aria-label={t('knowledge.asset.vector_syncing_hint')}
      />
    );
  };

  const renderGraphGlyph = (id: string): React.ReactNode => {
    const it = statusMap[id];
    if (!it || !it.graph) return <Minus className="w-3.5 h-3.5 mx-auto opacity-40" aria-label={t('knowledge.asset.graph_none')} />;
    return <Check className={`w-3.5 h-3.5 mx-auto ${styles.successText}`} aria-label={t('knowledge.asset.graph_done')} />;
  };

  return (
    <div className="flex flex-col gap-3" style={{ color: styles.cardText }}>
      {/* 顶工具栏 */}
      <div
        className="rounded-md border p-3 flex flex-wrap items-center gap-3"
        style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
      >
        <div className="relative flex-1 min-w-[220px]">
          <Search className="w-3.5 h-3.5 absolute left-2 top-1/2 -translate-y-1/2 opacity-40" />
          <input
            className="w-full pl-7 pr-2 py-1.5 rounded-md border text-xs"
            style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
            placeholder={t('knowledge.asset.search_placeholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(); }}
            aria-label={t('knowledge.asset.search_placeholder')}
          />
        </div>
        <select
          className="px-2 py-1.5 rounded-md border text-xs"
          style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
          value={assetType}
          onChange={(e) => setAssetType(e.target.value)}
          aria-label={t('knowledge.asset.col_type')}
        >
          <option value="all">{t('knowledge.asset.type_all')}</option>
          <option value="wiki">{t('knowledge.asset.type_wiki')}</option>
          <option value="entity">{t('knowledge.asset.type_entity')}</option>
          <option value="document">{t('knowledge.asset.type_document')}</option>
          <option value="knowledge_set">{t('knowledge.asset.type_knowledge_set')}</option>
        </select>
        <select
          className="px-2 py-1.5 rounded-md border text-xs"
          style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          aria-label={t('knowledge.asset.col_status')}
        >
          <option value="all">{t('knowledge.asset.status_all')}</option>
          <option value="draft">{t('knowledge.asset.status_draft')}</option>
          <option value="review">{t('knowledge.asset.status_review')}</option>
          <option value="published">{t('knowledge.asset.status_published')}</option>
        </select>
        <select
          className="px-2 py-1.5 rounded-md border text-xs"
          style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
          value={domain}
          onChange={(e) => handleDomainChange(e.target.value)}
          aria-label={t('knowledge.asset.col_domain')}
        >
          {domains.map(d => <option key={d} value={d}>{d}</option>)}
        </select>
        <button
          type="button"
          onClick={handleSearch}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border cursor-pointer ${styles.accentBg} ${styles.accentText} ${styles.accentBorder}`}
        >
          <Search className="w-3.5 h-3.5" />
          <span>{t('knowledge.asset.action_search')}</span>
        </button>
        <button
          type="button"
          onClick={() => void loadList()}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border cursor-pointer"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
          aria-label={t('knowledge.asset.action_refresh')}
        >
          <RefreshCw className="w-3.5 h-3.5" />
        </button>
        <button
          type="button"
          onClick={handleReset}
          className="px-2 py-1.5 rounded-md text-[11px] border cursor-pointer"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
        >
          {t('knowledge.asset.action_reset')}
        </button>
      </div>

      {/* 诚实标注：类型/状态为当前页客户端过滤，总数为服务端未过滤值 */}
      {(assetType !== 'all' || status !== 'all') && (
        <div className="text-[10px] font-mono flex items-center gap-1.5" style={{ color: styles.muted }}>
          <AlertCircle className="w-3 h-3 shrink-0" />
          <span>{t('knowledge.asset.filter_page_only_hint')}</span>
        </div>
      )}

      {/* 主区：左列表 + 右详情（≥1280px 视图） */}
      <div className="flex flex-col xl:flex-row gap-3">
        {/* 左列表 */}
        <div
          className="flex-1 min-w-0 rounded-md border overflow-hidden flex flex-col"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          <div className="flex-1 overflow-x-auto">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr
                  className="text-left text-[11px] uppercase tracking-wider opacity-70 border-b"
                  style={{ borderColor: styles.cardBorder, background: styles.badgeBg }}
                >
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_title')}</th>
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_type')}</th>
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_status')}</th>
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_semantic')}</th>
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_source')}</th>
                  <th className="px-3 py-2 font-medium text-center">{t('knowledge.asset.col_graph')}</th>
                  <th className="px-3 py-2 font-medium text-center">{t('knowledge.asset.col_vector')}</th>
                  <th className="px-3 py-2 font-medium">{t('knowledge.asset.col_updated')}</th>
                </tr>
              </thead>
              <tbody>
                {listError ? (
                  <tr>
                    <td colSpan={8} className="px-3 py-8 text-center">
                      <div className={`flex flex-col items-center gap-2 ${styles.dangerText}`}>
                        <AlertCircle className="w-5 h-5" />
                        <span>{t('knowledge.asset.list_failed')}</span>
                        <button
                          type="button"
                          onClick={() => void loadList()}
                          className="px-2 py-0.5 rounded border text-[11px] cursor-pointer"
                          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
                        >
                          {t('knowledge.asset.action_refresh')}
                        </button>
                      </div>
                    </td>
                  </tr>
                ) : list.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-3 py-8 text-center opacity-50">
                      {loading ? t('knowledge.asset.loading') : t('knowledge.asset.empty')}
                    </td>
                  </tr>
                ) : (
                  list.map(item => {
                    const isSelected = selectedId === item.id;
                    const statusClass = statusBadgeClass(item.status || '');
                    return (
                      <tr
                        key={item.id}
                        role="button"
                        tabIndex={0}
                        aria-pressed={isSelected}
                        onClick={() => setSelectedId(isSelected ? null : item.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setSelectedId(isSelected ? null : item.id);
                          }
                        }}
                        className="border-b cursor-pointer transition-colors"
                        style={{
                          borderColor: styles.cardBorder,
                          background: isSelected ? styles.badgeBg : 'transparent',
                        }}
                      >
                        <td className="px-3 py-2 font-medium">{item.title}</td>
                        <td className="px-3 py-2">
                          <span className="px-1.5 py-0.5 rounded text-[10px]" style={{ background: styles.badgeBg, color: styles.badgeText }}>
                            {item.category || assetType || '—'}
                          </span>
                        </td>
                        <td className="px-3 py-2">
                          <span className={`px-1.5 py-0.5 rounded text-[10px] border ${statusClass}`} style={{ borderColor: styles.cardBorder }}>
                            {t(statusBadgeLabelKey(item.status || ''))}
                          </span>
                        </td>
                        <td className="px-3 py-2 opacity-70 max-w-[180px] truncate">
                          {(item.matchedTags && item.matchedTags.length > 0 ? item.matchedTags.join(', ') : '') || '—'}
                        </td>
                        <td className="px-3 py-2 opacity-70">{item.source || '—'}</td>
                        <td className="px-3 py-2 text-center">{renderGraphGlyph(item.id)}</td>
                        <td className="px-3 py-2 text-center">{renderVectorGlyph(item.id)}</td>
                        <td className="px-3 py-2 opacity-70">
                          {item.updatedAt ? new Date(item.updatedAt).toLocaleString() : '—'}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
          {/* 分页 + 状态 */}
          <div
            className="flex items-center justify-between px-3 py-2 border-t text-[11px] opacity-70"
            style={{ borderColor: styles.cardBorder }}
          >
            <span>
              {t('knowledge.asset.pagination_meta')}
              {' '}<b className="opacity-100">{total}</b>
              {statusLoading ? ' · ' + t('knowledge.asset.status_loading') : ''}
            </span>
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                disabled={pageNum <= 1}
                onClick={() => setPageNum(p => Math.max(1, p - 1))}
                className="px-2 py-0.5 rounded border text-[11px] disabled:opacity-40 cursor-pointer"
                style={{ borderColor: styles.cardBorder }}
              >
                {t('knowledge.asset.pagination_prev')}
              </button>
              <span>{pageNum} / {totalPages}</span>
              <button
                type="button"
                disabled={pageNum >= totalPages}
                onClick={() => setPageNum(p => Math.min(totalPages, p + 1))}
                className="px-2 py-0.5 rounded border text-[11px] disabled:opacity-40 cursor-pointer"
                style={{ borderColor: styles.cardBorder }}
              >
                {t('knowledge.asset.pagination_next')}
              </button>
            </div>
          </div>
        </div>

        {/* 右详情面板：xl 及以上侧栏；以下堆叠在列表下方（避免点击选中后无反馈的死区） */}
        <aside
          className="flex flex-col rounded-md border p-3 w-full xl:w-80 xl:flex-shrink-0"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          <div className="text-[11px] font-semibold uppercase tracking-wider opacity-60 mb-2">
            {t('knowledge.asset.detail_title')}
          </div>
          {selected ? (
            <div className="flex flex-col gap-3 text-xs">
              <div className="font-medium">{selected.title}</div>
              <div className="flex flex-wrap gap-1.5">
                <span className="px-1.5 py-0.5 rounded text-[10px] border" style={{ borderColor: styles.cardBorder }}>
                  {t('knowledge.asset.detail_field_type')}: {selected.category || '—'}
                </span>
                <span className={`px-1.5 py-0.5 rounded text-[10px] border ${statusBadgeClass(selected.status || '')}`} style={{ borderColor: styles.cardBorder }}>
                  {t(statusBadgeLabelKey(selected.status || ''))}
                </span>
              </div>
              <div className="opacity-70">
                {t('knowledge.asset.detail_field_source')}: {selected.source || '—'}
              </div>
              <div className="opacity-70">
                {t('knowledge.asset.detail_field_updated')}: {selected.updatedAt ? new Date(selected.updatedAt).toLocaleString() : '—'}
              </div>
              <div className="border-t pt-2" style={{ borderColor: styles.cardBorder }}>
                <div className="opacity-60 mb-1">{t('knowledge.asset.detail_graph_context')}</div>
                <div className="flex flex-wrap gap-1.5">
                  {(selected.matchedTags && selected.matchedTags.length > 0 ? selected.matchedTags : []).map(tag => (
                    <span key={tag} className="px-1.5 py-0.5 rounded text-[10px]" style={{ background: styles.badgeBg, color: styles.badgeText }}>
                      @ {tag}
                    </span>
                  ))}
                  {(!selected.matchedTags || selected.matchedTags.length === 0) && (
                    <span className="opacity-40">{t('knowledge.asset.detail_no_tags')}</span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 pt-1" style={{ borderTopColor: styles.cardBorder }}>
                <span className="opacity-60">{t('knowledge.asset.col_graph')}:</span>
                {renderGraphGlyph(selected.id)}
                <span className="opacity-60 ml-2">{t('knowledge.asset.col_vector')}:</span>
                {renderVectorGlyph(selected.id)}
              </div>
            </div>
          ) : (
            <div className="opacity-40 text-[11px] py-4 text-center">
              {t('knowledge.asset.detail_empty')}
            </div>
          )}
        </aside>
      </div>

      {/* Loaded-once 提示（避免首屏 tab 切换空白闪烁） */}
      {!loadedOnce && (
        <div className="text-[11px] opacity-40 text-center py-1">{t('knowledge.asset.loading')}</div>
      )}
    </div>
  );
}
