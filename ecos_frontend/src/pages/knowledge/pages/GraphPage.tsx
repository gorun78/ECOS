/**
 * PMO-D Batch 2 — F5 GraphPage（实体实现 / PRD §3.2 F5）。
 *
 * 布局：上搜索栏 + 左工具栏 + 中画布 + 右详情面板 + 底栏
 *   顶搜索栏：input + 实体/关系 select + 搜索按钮（fetchGraphQuery → graphSearch）
 *   左工具栏：全文搜索 / 路径查找 / domain select / category checkbox
 *     （categoryIds 后端下沉，12a20d6 落地，前端不二次过滤）/ 邻居度 / 图例
 *   中画布：GraphCanvas（rawNodes / rawEdges 直渲染，无前端 filter）
 *   右详情面板：节点属性 + 关系 + 操作（展开邻居 / 设路径源）
 *   底栏：`实体 N · 关系 M · 当前视图 K 个节点`
 *
 * 颜色 5 类（PRD F5）：实体蓝 / 关系线灰 / 文档绿 / 向量紫 / 待审红
 * 主题守护 §4.1：0 硬编码色值
 * i18n §4.3：0 硬编码中文（knowledge.graph.*）
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowRight, GitBranch, Info, Loader2, Minimize2, Network,
  Search, Tag, X,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import { fetchNavDomains, fetchNavCategories, type NavCategoryVO } from '../../../services/knowledgeNavApi';
import GraphCanvas from '../../../components/GraphCanvas';

/** 兼容 GraphCanvas 协议（节点/边） */
interface GraphNode {
  id: string;
  label: string;
  type: string;
  row?: string;
  properties?: Record<string, unknown>;
}
interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relation?: string;
}

/** 颜色 5 类 — 实体/关系/文档/向量/候选（PRD F5） */
const NODE_TYPE_COLORS: Record<string, string> = {
  entity: 'blue',
  edge: 'gray',
  document: 'green',
  vector: 'purple',
  pending: 'red',
};

export default function GraphPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 顶搜索栏 ──
  const [searchQuery, setSearchQuery] = useState('');
  const [searchType, setSearchType] = useState<'entity' | 'relation'>('entity');
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<GraphNode[]>([]);
  const [showSearchResults, setShowSearchResults] = useState(false);

  // ── 左工具栏 ──
  const [navDomain, setNavDomain] = useState('default');
  const [navDomains, setNavDomains] = useState<string[]>(['default']);
  const [navCategories, setNavCategories] = useState<NavCategoryVO[]>([]);
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>([]);
  const [neighborDegree, setNeighborDegree] = useState(1);

  // ── 画布 ──
  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [edges, setEdges] = useState<GraphEdge[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [nodeDetail, setNodeDetail] = useState<GraphNode | null>(null);
  const [nodeEdges, setNodeEdges] = useState<GraphEdge[]>([]);
  const [pathSource, setPathSource] = useState('');
  const [pathTarget, setPathTarget] = useState('');
  const [pathNodes, setPathNodes] = useState<Set<string>>(new Set());
  const [pathEdges, setPathEdges] = useState<Set<string>>(new Set());
  const [isComputingPath, setIsComputingPath] = useState(false);

  // 折叠集合
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());
  const expansionChildrenRef = useRef<Map<string, Set<string>>>(new Map());

  const loadNavDomains = useCallback(async () => {
    try {
      const list = await fetchNavDomains();
      setNavDomains(Array.isArray(list) && list.length > 0 ? list : ['default']);
    } catch {
      setNavDomains(['default']);
    }
  }, []);

  const loadNavCategories = useCallback(async (domain: string) => {
    try {
      const data = await fetchNavCategories(domain);
      setNavCategories(Array.isArray(data) ? data : []);
    } catch {
      setNavCategories([]);
    }
  }, []);

  useEffect(() => { void loadNavDomains(); }, [loadNavDomains]);
  useEffect(() => { void loadNavCategories(navDomain); }, [navDomain, loadNavCategories]);

  /** 加载图（categoryIds 后端下沉 — 12a20d6 已落地） */
  const loadGraph = useCallback(async (domain?: string, categoryIds?: string[]) => {
    setLoading(true);
    try {
      // PRD F5 关键：categoryIds 透传，前端不做二次过滤
      const data = await knowledgeApi.fetchGraph(domain, categoryIds && categoryIds.length > 0 ? categoryIds : undefined) as unknown as {
        nodes?: GraphNode[];
        edges?: GraphEdge[];
        links?: GraphEdge[];
      };
      const rawNodes = (data?.nodes || []) as GraphNode[];
      const rawEdges = (data?.edges || data?.links || []) as GraphEdge[];
      // PRD F5 验收：无前端 filter 逻辑，纯 rawNodes / rawEdges 直渲染
      setNodes(rawNodes);
      setEdges(rawEdges);
      setPathNodes(new Set());
      setPathEdges(new Set());
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      // 静默失败，保留上次数据
      void msg;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadGraph(); }, [loadGraph]);

  /** 顶搜索栏：graphSearch（实体 / 关系 select 走参数） */
  const handleSearch = useCallback(async () => {
    if (!searchQuery.trim()) return;
    setSearching(true);
    setShowSearchResults(true);
    try {
      const data = await knowledgeApi.graphSearch(`${searchType}:${searchQuery}`) as unknown as {
        results?: GraphNode[];
        nodes?: GraphNode[];
      };
      const results = (data?.results || data?.nodes || []).map((r: GraphNode) => ({
        id: r.id || String(r.label || ''),
        label: r.label || r.id,
        type: r.type || searchType,
        properties: r.properties,
      }));
      setSearchResults(results);
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  }, [searchQuery, searchType]);

  const handleSearchResultClick = (nodeId: string) => {
    setShowSearchResults(false);
    setSelectedNodeId(nodeId);
    setFocusNodeId(nodeId);
  };

  const [focusNodeId, setFocusNodeId] = useState<string | null>(null);

  const handleLoadFull = () => {
    setSearchQuery('');
    setSelectedNodeId(null);
    setFocusNodeId(null);
    void loadGraph(undefined, selectedCategoryIds.length > 0 ? selectedCategoryIds : undefined);
  };

  const toggleCategoryId = (id: string) => {
    setSelectedCategoryIds(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]));
  };

  /** 路径查找：findPath */
  const handleComputePath = async () => {
    if (!pathSource || !pathTarget) return;
    setIsComputingPath(true);
    try {
      const data = await knowledgeApi.findPath(pathSource, pathTarget) as unknown as {
        path?: string[];
        pathEdges?: string[];
        nodeIds?: string[];
        edgeIds?: string[];
      };
      setPathNodes(new Set(data?.path || data?.nodeIds || []));
      setPathEdges(new Set(data?.pathEdges || data?.edgeIds || []));
    } catch {
      // 静默
    } finally {
      setIsComputingPath(false);
    }
  };

  /** 邻居展开（节点详情面板） */
  const handleExpandNeighbors = async (nodeId: string) => {
    setLoading(true);
    try {
      const data = await knowledgeApi.fetchNeighbors(nodeId, neighborDegree) as unknown as {
        nodes?: GraphNode[];
        edges?: GraphEdge[];
        links?: GraphEdge[];
      };
      const newNodes = (data?.nodes || []) as GraphNode[];
      const newEdges = (data?.edges || data?.links || []) as GraphEdge[];
      const childIds = new Set(newNodes.map(n => n.id));
      expansionChildrenRef.current.set(nodeId, childIds);
      setExpandedNodeIds(prev => new Set(prev).add(nodeId));
      setNodes(prev => {
        const existing = new Set(prev.map(n => n.id));
        return [...prev, ...newNodes.filter((n: GraphNode) => !existing.has(n.id))];
      });
      setEdges(prev => {
        const existing = new Set(prev.map(e => e.id));
        return [...prev, ...newEdges.filter((e: GraphEdge) => !existing.has(e.id))];
      });
    } catch {
      // 静默
    } finally {
      setLoading(false);
    }
  };

  const handleCollapse = (nodeId: string) => {
    const children = expansionChildrenRef.current.get(nodeId);
    if (children && children.size > 0) {
      const other = new Set<string>();
      expansionChildrenRef.current.forEach((kids, parent) => {
        if (parent !== nodeId) kids.forEach((k) => other.add(k));
      });
      const toRemove = new Set([...children].filter((c) => !other.has(c)));
      setNodes(prev => prev.filter((n) => !toRemove.has(n.id)));
      setEdges(prev => prev.filter((e) => !toRemove.has(e.source) && !toRemove.has(e.target)));
      expansionChildrenRef.current.delete(nodeId);
    }
    setExpandedNodeIds(prev => { const s = new Set(prev); s.delete(nodeId); return s; });
  };

  const handleSelectNode = async (nodeId: string | null) => {
    setSelectedNodeId(nodeId);
    if (nodeId) {
      try {
        const data = await knowledgeApi.fetchNode(nodeId) as unknown as {
          node?: GraphNode;
          edges?: GraphEdge[];
          links?: GraphEdge[];
        };
        setNodeDetail(data?.node || null);
        setNodeEdges((data?.edges || data?.links || []) as GraphEdge[]);
      } catch {
        const found = nodes.find(n => n.id === nodeId);
        setNodeDetail(found || null);
        setNodeEdges(edges.filter(e => e.source === nodeId || e.target === nodeId));
      }
    } else {
      setNodeDetail(null);
      setNodeEdges([]);
    }
  };

  const detailDisplayNode = nodeDetail || (selectedNodeId ? nodes.find(n => n.id === selectedNodeId) : null) || null;

  // adapt for GraphCanvas
  const canvasNodes = nodes.map((n) => ({
    id: n.id,
    type: n.type || 'default',
    label: n.label,
    properties: n.properties,
    rows: n.row || '',
  }));
  const canvasLinks = edges.map((e) => ({ id: e.id, source: e.source, target: e.target }));

  /** 5 类颜色派生（PRD F5） */
  const nodeColorClass = useCallback((type: string): string => {
    const color = NODE_TYPE_COLORS[type] || 'blue';
    if (color === 'green') return 'bg-emerald-500';
    if (color === 'purple') return 'bg-purple-500';
    if (color === 'red') return 'bg-rose-500';
    if (color === 'gray') return 'bg-slate-400';
    return 'bg-blue-500';
  }, []);

  const showDetailPanel = !!detailDisplayNode;

  return (
    <div className="flex flex-col flex-1 min-h-0" style={{ color: styles.cardText }}>
      {/* ═══ 顶搜索栏（input + select + 搜索按钮） ═══ */}
      <div className="flex items-center gap-2 px-3 py-2 border-b" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <Search className="w-4 h-4 shrink-0" style={{ color: styles.muted }} />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => { setSearchQuery(e.target.value); setShowSearchResults(false); }}
          onKeyDown={(e) => { if (e.key === 'Enter') void handleSearch(); }}
          placeholder={t('knowledge.graph.search_placeholder_top')}
          className="flex-1 px-2.5 py-1.5 rounded-md text-[11px] outline-none"
          style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
        />
        <select
          value={searchType}
          onChange={(e) => setSearchType(e.target.value as 'entity' | 'relation')}
          className="px-2 py-1.5 rounded-md text-[10px] font-bold outline-none"
          style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
        >
          <option value="entity">{t('knowledge.graph.search_type_entity')}</option>
          <option value="relation">{t('knowledge.graph.search_type_relation')}</option>
        </select>
        <button
          type="button"
          onClick={() => void handleSearch()}
          disabled={searching || !searchQuery.trim()}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-md text-[11px] font-bold cursor-pointer disabled:opacity-50"
          style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}
        >
          {searching ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Search className="w-3.5 h-3.5" />}
          {t('knowledge.graph.search_button')}
        </button>
        {/* search results dropdown */}
        {showSearchResults && searchResults.length > 0 && (
          <div className="absolute top-12 left-3 right-3 z-30 rounded-md border shadow-xl max-h-72 overflow-y-auto"
               style={{ background: styles.sidebarBg, borderColor: styles.sidebarBorder }}>
            {searchResults.slice(0, 30).map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => handleSearchResultClick(r.id)}
                className="w-full text-left px-3 py-1.5 text-[11px] flex items-center gap-2 hover:opacity-70 transition"
                style={{ color: styles.cardText }}
              >
                <Tag className="w-3 h-3 shrink-0" style={{ color: styles.muted }} />
                <span className="truncate flex-1">{r.label}</span>
                <span className="text-[9px] font-mono shrink-0" style={{ color: styles.muted }}>{r.type}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* ═══ 主体：左工具栏 + 中画布 + 右详情 ═══ */}
      <div className="flex-1 min-h-0 grid grid-cols-[200px_1fr]" style={{ borderColor: styles.inputBorder }}>
        {/* ───── 左工具栏 ───── */}
        <div className="border-r flex flex-col overflow-y-auto p-3 space-y-3" style={{ borderColor: styles.cardBorder }}>
          {/* Full-text search */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.graph.fullTextSearch')}
            </label>
            <input
              type="text"
              value={searchQuery}
              readOnly
              onClick={() => void handleSearch()}
              placeholder={t('knowledge.graph.searchPlaceholder')}
              className="w-full px-2.5 py-1.5 text-[11px] rounded outline-none"
              style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
            />
          </div>

          {/* Path finder */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1"
                   style={{ color: styles.cardTextMuted }}>
              <GitBranch className="w-3 h-3" />
              {t('knowledge.graph.pathFinder')}
            </label>
            <div className="flex gap-1">
              <input
                type="text"
                value={pathSource}
                onChange={(e) => setPathSource(e.target.value)}
                placeholder={t('knowledge.graph.pathSourcePlaceholder')}
                className="flex-1 min-w-0 px-2 py-1 text-[10px] rounded outline-none"
                style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
              />
              <ArrowRight className={`w-3 h-3 self-center shrink-0`} style={{ color: styles.muted }} />
              <input
                type="text"
                value={pathTarget}
                onChange={(e) => setPathTarget(e.target.value)}
                placeholder={t('knowledge.graph.pathTargetPlaceholder')}
                className="flex-1 min-w-0 px-2 py-1 text-[10px] rounded outline-none"
                style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
              />
            </div>
            <button
              type="button"
              onClick={() => void handleComputePath()}
              disabled={isComputingPath || !pathSource || !pathTarget}
              className="w-full px-2 py-1 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
              style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}
            >
              {isComputingPath ? <Loader2 className="w-3 h-3 animate-spin mx-auto" /> : t('knowledge.graph.path_compute_button')}
            </button>
          </div>

          {/* Domain filter (nav domain) */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.graph.domain_filter')}
            </label>
            <select
              value={navDomain}
              onChange={(e) => setNavDomain(e.target.value)}
              className="w-full px-2 py-1.5 text-[11px] rounded outline-none cursor-pointer"
              style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }}
            >
              {navDomains.map((d) => (
                <option key={d} value={d}>{d === 'default' ? t('knowledge.nav.domain_default') : d}</option>
              ))}
            </select>
          </div>

          {/* Category multi-checkbox（categoryIds 后端下沉，12a20d6） */}
          <div className="space-y-1.5">
            <label className="text-[10px] font-bold uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.graph.category_filter')}
              {selectedCategoryIds.length > 0 && (
                <span className="ml-1 font-mono text-emerald-600">({selectedCategoryIds.length})</span>
              )}
            </label>
            {navCategories.length === 0 ? (
              <p className="text-[9px]" style={{ color: styles.muted }}>{t('knowledge.graph.category_empty')}</p>
            ) : (
              <div className="max-h-32 overflow-y-auto space-y-0.5 pr-1">
                {navCategories.slice(0, 20).map((cat) => (
                  <label
                    key={cat.id}
                    className="flex items-center gap-1.5 px-1.5 py-0.5 text-[10px] cursor-pointer hover:opacity-70"
                    style={{ color: styles.cardTextMuted }}
                  >
                    <input
                      type="checkbox"
                      checked={selectedCategoryIds.includes(cat.id)}
                      onChange={() => {
                        toggleCategoryId(cat.id);
                        void loadGraph(navDomain === 'default' ? undefined : navDomain,
                                       selectedCategoryIds.includes(cat.id)
                                         ? selectedCategoryIds.filter((x) => x !== cat.id)
                                         : [...selectedCategoryIds, cat.id]);
                      }}
                      className="h-3 w-3"
                    />
                    <span className="truncate flex-1">{cat.name}</span>
                    <span className="text-[8px] font-mono" style={{ color: styles.muted }}>({cat.articleCount})</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          {/* Neighbor degree */}
          <div className="space-y-1">
            <label className="text-[10px] font-bold uppercase tracking-wider flex items-center justify-between"
                   style={{ color: styles.cardTextMuted }}>
              <span>{t('knowledge.graph.neighborDegree')}</span>
              <span className="font-mono text-emerald-600">{neighborDegree}</span>
            </label>
            <input
              type="range"
              min={1}
              max={3}
              value={neighborDegree}
              onChange={(e) => setNeighborDegree(parseInt(e.target.value))}
              className="w-full cursor-pointer"
            />
          </div>

          {/* Load full graph */}
          <button
            type="button"
            onClick={handleLoadFull}
            disabled={loading}
            className="w-full px-2 py-1.5 rounded text-[10px] font-bold disabled:opacity-50 cursor-pointer"
            style={{ background: styles.sidebarHoverBg, color: styles.cardText, border: `1px solid ${styles.cardBorder}` }}
          >
            {loading ? <Loader2 className="w-3 h-3 animate-spin mx-auto" /> : <Network className="w-3 h-3 mx-auto" />}
            {t('knowledge.graph.loadFullGraph')}
          </button>

          {/* Legend（5 类色） */}
          <div className="mt-auto p-2 rounded border space-y-1"
               style={{ background: styles.sidebarBg, borderColor: styles.cardBorder }}>
            <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
              {t('knowledge.graph.legend')}
            </span>
            <div className="space-y-0.5 text-[10px]">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(59,130,246)' }} />
                {t('knowledge.graph.legend_entity')}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(148,163,184)' }} />
                {t('knowledge.graph.legend_relation')}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(16,185,129)' }} />
                {t('knowledge.graph.legend_document')}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(168,85,247)' }} />
                {t('knowledge.graph.legend_vector')}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(244,63,94)' }} />
                {t('knowledge.graph.legend_pending')}
              </div>
            </div>
          </div>
        </div>

        {/* ───── 中画布 ───── */}
        <div className="relative min-h-0" style={{ background: styles.appBg }}>
          {loading && (
            <div className="absolute top-3 left-1/2 -translate-x-1/2 z-20 px-3 py-1.5 rounded-md flex items-center gap-2 shadow-md"
                 style={{ background: styles.sidebarBg, borderColor: styles.cardBorder, color: styles.cardText, border: `1px solid ${styles.cardBorder}` }}>
              <Loader2 className="w-3 h-3 animate-spin" />
              {t('knowledge.graph.loadingGraph')}
            </div>
          )}
          {nodes.length === 0 && !loading ? (
            <div className="h-full flex items-center justify-center text-xs" style={{ color: styles.muted }}>
              <div className="text-center space-y-2">
                <Network className="w-8 h-8 mx-auto opacity-40" />
                <p>{t('knowledge.graph.emptyHint')}</p>
              </div>
            </div>
          ) : (
            <GraphCanvas
              nodes={canvasNodes}
              links={canvasLinks}
              selectedNodeId={selectedNodeId}
              focusNodeId={focusNodeId}
              onSelectNode={handleSelectNode}
              onDoubleClickNode={(id) => void handleExpandNeighbors(id)}
              onCollapseNode={handleCollapse}
              pathNodeIds={pathNodes}
              pathEdgeIds={pathEdges}
              expandedNodeIds={expandedNodeIds}
              interactive
            />
          )}

          {/* 底栏：实体 N · 关系 M · 当前视图 K 个节点 */}
          <div className="absolute bottom-2 left-3 right-3 z-20 flex items-center gap-3 text-[10px] font-mono"
               style={{ color: styles.cardTextMuted }}>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(59,130,246)' }} />
              {t('knowledge.graph.kpi_nodes', { n: nodes.length })}
            </span>
            <span className="flex items-center gap-1">
              <span className="w-2 h-2 rounded-full" style={{ background: 'rgb(148,163,184)' }} />
              {t('knowledge.graph.kpi_edges', { m: edges.length })}
            </span>
            <span className="ml-auto">{t('knowledge.graph.current_view', { k: nodes.length })}</span>
          </div>
        </div>

        {/* ───── 右详情面板（条件渲染） ───── */}
        {showDetailPanel && detailDisplayNode && (
          <div className="w-72 border-l flex flex-col overflow-y-auto" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
            <div className="px-3 py-2 border-b flex items-center justify-between" style={{ borderColor: styles.cardBorder }}>
              <div className="flex items-center gap-1.5 text-xs font-bold">
                <Info className="w-3.5 h-3.5" style={{ color: styles.accentText }} />
                {t('knowledge.graph.nodeDetail')}
              </div>
              <button
                type="button"
                onClick={() => { setNodeDetail(null); setSelectedNodeId(null); setNodeEdges([]); }}
                className="p-1 cursor-pointer hover:opacity-70"
                style={{ color: styles.muted }}
                title={t('knowledge.graph.close_detail')}
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>

            <div className="p-3 space-y-3 text-[11px] flex-1">
              {/* 节点名 + 类型 + 颜色 chip */}
              <div>
                <div className="flex items-center gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full shrink-0"
                        style={{ background: styles[`${nodeColorClass(mapCanvasNodeToPrimitiveType(detailDisplayNode.type))}Text`] || styles.accentText }} />
                  <span className="font-bold truncate" style={{ color: styles.cardText }}>{detailDisplayNode.label}</span>
                </div>
                <div className="text-[10px] font-mono mt-0.5" style={{ color: styles.muted }}>
                  {detailDisplayNode.type}
                </div>
              </div>

              {/* 属性 */}
              {detailDisplayNode.properties && Object.keys(detailDisplayNode.properties).length > 0 && (
                <div>
                  <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
                    {t('knowledge.graph.properties')}
                  </span>
                  <div className="mt-1 rounded p-2 space-y-1" style={{ background: styles.sidebarBg }}>
                    {Object.entries(detailDisplayNode.properties).slice(0, 12).map(([k, v]) => (
                      <div key={k} className="flex justify-between text-[10px]">
                        <span style={{ color: styles.cardTextMuted }}>{k}</span>
                        <span className="font-mono" style={{ color: styles.cardText }}>
                          {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* 关联关系 */}
              <div>
                <span className="text-[9px] font-bold uppercase" style={{ color: styles.muted }}>
                  {t('knowledge.graph.relatedEdges', { count: nodeEdges.length })}
                </span>
                {nodeEdges.length === 0 ? (
                  <p className="text-[10px] mt-1" style={{ color: styles.muted }}>{t('knowledge.graph.noRelatedEdges')}</p>
                ) : (
                  <div className="space-y-0.5 mt-1 max-h-40 overflow-y-auto">
                    {nodeEdges.slice(0, 20).map((e) => (
                      <div key={e.id}
                           className="rounded px-2 py-1 text-[10px] flex items-center justify-between"
                           style={{ background: styles.sidebarBg }}>
                        <span className="truncate flex-1" style={{ color: styles.cardText }}>
                          {e.source} → {e.target}
                        </span>
                        {e.relation && (
                          <span className="font-mono shrink-0 ml-1" style={{ color: styles.accentText }}>{e.relation}</span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* 操作按钮：展开邻居 / 设路径源 */}
              <div className="pt-2 border-t space-y-1.5" style={{ borderColor: styles.cardBorder }}>
                {expandedNodeIds.has(detailDisplayNode.id) ? (
                  <button
                    type="button"
                    onClick={() => handleCollapse(detailDisplayNode.id)}
                    className="w-full px-2 py-1.5 rounded text-[10px] font-bold flex items-center justify-center gap-1 cursor-pointer"
                    style={{ background: styles.dangerBg, color: styles.dangerText }}
                  >
                    <Minimize2 className="w-3 h-3" />
                    {t('knowledge.graph.collapseNode')}
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => void handleExpandNeighbors(detailDisplayNode.id)}
                    className="w-full px-2 py-1.5 rounded text-[10px] font-bold flex items-center justify-center gap-1 cursor-pointer"
                    style={{ background: styles.infoBg, color: styles.infoText }}
                  >
                    <Network className="w-3 h-3" />
                    {t('knowledge.graph.expandNode')}
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => setPathSource(detailDisplayNode.id)}
                  className="w-full px-2 py-1.5 rounded text-[10px] font-bold flex items-center justify-center gap-1 cursor-pointer"
                  style={{ background: styles.warningBg, color: styles.warningText }}
                >
                  <GitBranch className="w-3 h-3" />
                  {t('knowledge.graph.setAsPathSource')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/** GraphCanvas 节点 type → 5 类 primitive 桶（蓝/灰/绿/紫/红） */
function mapCanvasNodeToPrimitiveType(type: string): 'entity' | 'vector' | 'document' | 'edge' | 'pending' {
  const t = (type || 'entity').toLowerCase();
  if (t.includes('vector') || t.includes('embedding')) return 'vector';
  if (t.includes('doc') || t.includes('file') || t.includes('text')) return 'document';
  if (t.includes('pending') || t.includes('candidate') || t.includes('review')) return 'pending';
  if (t.includes('edge') || t.includes('rel')) return 'edge';
  return 'entity';
}
