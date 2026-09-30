/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * GraphExplorerTab — 知识图谱探索器
 * 图谱可视化交互、节点搜索、邻居展开、路径分析
 * 复用 GraphCanvas 组件做图谱可视化渲染
 *
 * 组件拆分（前端开发规范 §十 收口）：左工具栏 / 右详情面板 / 新建弹窗
 *   已拆至 ./graph-explorer/ 子目录，本文件仅保留状态与业务处理器。
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  ArrowRight, Loader2, Network, Plus,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import { fetchNavCategories, fetchNavDomains, type NavCategoryVO } from '../../../services/knowledgeNavApi';
import GraphCanvas from '../../../components/GraphCanvas';
import type { GraphEdge, GraphNode } from './graph-explorer/types';
import { LeftToolbar } from './graph-explorer/LeftToolbar';
import { DetailPanel } from './graph-explorer/DetailPanel';
import { CreateModal } from './graph-explorer/CreateModal';

interface GraphExplorerTabProps {
  // Independent tab, no external props
}

/**
 * PMO-74 H6-T3 M3-a — 本地响应类型（仅描述本文件既有消费形状，不改运行时；
 * knowledgeApi 返回 unknown/占位类型，共享化留待后续单元）。
 */
interface GraphPayload {
  nodes?: unknown[];
  edges?: unknown[];
  links?: unknown[];
}

interface RawSearchNode {
  id?: string;
  nodeId?: string;
  label?: string;
  name?: string;
  type?: string;
  nodeType?: string;
  properties?: Record<string, unknown>;
  description?: string;
}

interface GraphSearchResponse {
  results?: RawSearchNode[];
  nodes?: RawSearchNode[];
}

/** fetchNode 响应：既可能是 { node, edges } 包装，也可能直接就是节点本身（与既有 `data?.node || data` 消费一致） */
interface NodeDetailResponse extends GraphNode {
  node?: GraphNode;
  edges?: GraphEdge[];
  links?: GraphEdge[];
}

interface PathResponse {
  path?: string[];
  nodeIds?: string[];
  pathEdges?: string[];
  edgeIds?: string[];
}

export default function GraphExplorerTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [edges, setEdges] = useState<GraphEdge[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<GraphNode[]>([]);
  const [showSearchResults, setShowSearchResults] = useState(false);
  const [domainFilter, setDomainFilter] = useState('');
  const [neighborDegree, setNeighborDegree] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [focusNodeId, setFocusNodeId] = useState<string | null>(null);
  const [showDetailPanel, setShowDetailPanel] = useState(false);
  const [nodeDetail, setNodeDetail] = useState<GraphNode | null>(null);
  const [nodeEdges, setNodeEdges] = useState<GraphEdge[]>([]);
  const [pathSource, setPathSource] = useState('');
  const [pathTarget, setPathTarget] = useState('');
  const [pathNodes, setPathNodes] = useState<Set<string>>(new Set());
  const [pathEdges, setPathEdges] = useState<Set<string>>(new Set());
  const [isComputingPath, setIsComputingPath] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState<'node' | 'edge' | null>(null);
  const [newNodeForm, setNewNodeForm] = useState({ label: '', nodeType: '', description: '', properties: '' });
  const [newEdgeForm, setNewEdgeForm] = useState({ sourceNodeId: '', targetNodeId: '', relationship: '', weight: '1' });
  const [toast, setToast] = useState<{ type: string; msg: string } | null>(null);
  const showToast = (type: string, msg: string) => { setToast({ type, msg }); setTimeout(() => setToast(null), 3000); };

  // PMO-B T3 — 按业务域过滤（默认空 = 全量，回归保证）
  // PMO-C T2 — nav domain 切换（kb_nav_category.domain，与上方 graph domainFilter 区分）
  const [navDomain, setNavDomain] = useState<string>('default');
  const [navDomains, setNavDomains] = useState<string[]>(['default']);
  const [navCategories, setNavCategories] = useState<NavCategoryVO[]>([]);
  const [selectedCategoryIds, setSelectedCategoryIds] = useState<string[]>([]);
  const loadNavDomains = useCallback(async (): Promise<void> => {
    try {
      const list = await fetchNavDomains();
      setNavDomains(Array.isArray(list) && list.length > 0 ? list : ['default']);
    } catch {
      setNavDomains(['default']);
    }
  }, []);
  const loadNavCategories = useCallback(async (d: string): Promise<void> => {
    try {
      const data = await fetchNavCategories(d);
      setNavCategories(Array.isArray(data) ? data : []);
    } catch {
      setNavCategories([]);
    }
  }, []);
  useEffect(() => { void loadNavDomains(); }, [loadNavDomains]);
  // domain 变化时重拉目录树（categories 依赖 activeDomain）
  useEffect(() => { void loadNavCategories(navDomain); }, [navDomain, loadNavCategories]);
  const toggleCategoryId = (id: string): void => {
    setSelectedCategoryIds(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]));
  };

  // ── Expand/collapse tracking ──
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());
  const expansionChildrenRef = useRef<Map<string, Set<string>>>(new Map());

  const loadGraph = useCallback(async (domain?: string) => {
    setIsLoading(true);
    try {
      // PMO-C T3 — 后端下沉：categoryIds 非空时透传，后端按 kb_nav_article_rel 白名单过滤
      const data = await knowledgeApi.fetchGraph(domain, selectedCategoryIds.length > 0 ? selectedCategoryIds : undefined) as GraphPayload | null;
      const rawNodes = (data?.nodes || []) as GraphNode[];
      const rawEdges = (data?.edges || data?.links || []) as GraphEdge[];
      // P3 优化点：后端已按 nav 白名单过滤 nodes/edges；前端保留二次兜底（兼容旧后端无 categoryIds 参数）
      if (selectedCategoryIds.length > 0) {
        const idSet = new Set(selectedCategoryIds);
        const filteredNodes = rawNodes.filter(n => {
          const props = (n.properties ?? {}) as Record<string, unknown>;
          const cid = String(props.categoryId ?? props.navCategoryId ?? n.type ?? '');
          return idSet.has(cid) || n.id === 'kg-root';
        });
        const nodeIdSet = new Set(filteredNodes.map(n => n.id));
        const filteredEdges = rawEdges.filter(e => nodeIdSet.has(e.source) && nodeIdSet.has(e.target));
        setNodes(filteredNodes);
        setEdges(filteredEdges);
      } else {
        setNodes(rawNodes);
        setEdges(rawEdges);
      }
      setPathNodes(new Set());
      setPathEdges(new Set());
    } catch (e) {
      showToast('error', t('knowledge.graph.loadGraphError') + e.message);
    } finally {
      setIsLoading(false);
    }
  }, [t, selectedCategoryIds]);

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setIsLoading(true);
    setShowSearchResults(true);
    try {
      const data = await knowledgeApi.graphSearch(searchQuery) as GraphSearchResponse | null;
      const results = (data?.results || data?.nodes || []).map(r => ({
        id: r.id || r.nodeId,
        label: r.label || r.name || r.id,
        type: r.type || r.nodeType || 'default',
        properties: r.properties,
        description: r.description,
      }));
      setSearchResults(results);
      if (results.length === 0) showToast('info', t('knowledge.graph.noMatch'));
    } catch (e) {
      showToast('error', t('knowledge.graph.searchError') + e.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSearchResultClick = (nodeId: string) => {
    setShowSearchResults(false);
    // Ensure the node is loaded in the graph
    const existing = nodes.find(n => n.id === nodeId);
    if (!existing) {
      const fromResults = searchResults.find(r => r.id === nodeId);
      if (fromResults) {
        setNodes(prev => [...prev.filter(n => n.id !== nodeId), fromResults]);
      }
    }
    setFocusNodeId(nodeId);
    setSelectedNodeId(nodeId);
  };

  const handleDomainChange = (domain: string) => {
    setDomainFilter(domain);
    loadGraph(domain || undefined);
  };

  const handleLoadFullGraph = () => { setSearchQuery(''); setDomainFilter(''); loadGraph(); };

  const handleExpandNeighbors = async (nodeId: string) => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchNeighbors(nodeId, neighborDegree) as GraphPayload | null;
      const newNodes = (data?.nodes || []) as GraphNode[];
      const newEdges = (data?.edges || data?.links || []) as GraphEdge[];
      // Track expansion children for collapse
      const childIds = new Set(newNodes.map(n => n.id));
      expansionChildrenRef.current.set(nodeId, childIds);
      setExpandedNodeIds(prev => new Set(prev).add(nodeId));
      setNodes(prev => { const existing = new Set(prev.map(n => n.id)); return [...prev, ...newNodes.filter((n: GraphNode) => !existing.has(n.id))]; });
      setEdges(prev => { const existing = new Set(prev.map(e => e.id)); return [...prev, ...newEdges.filter((e: GraphEdge) => !existing.has(e.id))]; });
      showToast('success', t('knowledge.graph.expandSuccess', { nodeId }));
    } catch (e) {
      showToast('error', t('knowledge.graph.expandError') + e.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCollapseNode = (nodeId: string) => {
    const children = expansionChildrenRef.current.get(nodeId);
    if (children && children.size > 0) {
      // Collect children of other expanded nodes (shared children stay)
      const otherChildren = new Set<string>();
      expansionChildrenRef.current.forEach((kids, parent) => {
        if (parent !== nodeId) kids.forEach(k => otherChildren.add(k));
      });
      const toRemove = new Set([...children].filter(c => !otherChildren.has(c)));
      if (toRemove.size > 0) {
        setNodes(prev => prev.filter(n => !toRemove.has(n.id)));
        setEdges(prev => prev.filter(e => !toRemove.has(e.source) && !toRemove.has(e.target)));
      }
      expansionChildrenRef.current.delete(nodeId);
    }
    setExpandedNodeIds(prev => { const s = new Set(prev); s.delete(nodeId); return s; });
    showToast('success', t('knowledge.graph.collapseSuccess', { nodeId }));
  };

  const handleSelectNode = async (nodeId: string | null) => {
    setSelectedNodeId(nodeId);
    if (nodeId) {
      setShowDetailPanel(true);
      try {
        const data = await knowledgeApi.fetchNode(nodeId) as NodeDetailResponse | null;
        setNodeDetail(data?.node || data || null);
        setNodeEdges(data?.edges || data?.links || []);
      } catch {
        const found = nodes.find(n => n.id === nodeId);
        setNodeDetail(found || null);
        setNodeEdges(edges.filter(e => e.source === nodeId || e.target === nodeId));
      }
    } else { setShowDetailPanel(false); setNodeDetail(null); setNodeEdges([]); }
  };

  const handleDoubleClickNode = (nodeId: string) => { handleExpandNeighbors(nodeId); };

  const handleComputePath = async () => {
    if (!pathSource || !pathTarget) { showToast('error', t('knowledge.graph.pathRequired')); return; }
    setIsComputingPath(true);
    try {
      const data = await knowledgeApi.findPath(pathSource, pathTarget) as PathResponse | null;
      const pathNodeList: string[] = data?.path || data?.nodeIds || [];
      const pathEdgeList: string[] = data?.pathEdges || data?.edgeIds || [];
      setPathNodes(new Set(pathNodeList));
      setPathEdges(new Set(pathEdgeList));
      showToast('success', t('knowledge.graph.pathSuccess', { count: pathNodeList.length }));
    } catch (e) {
      showToast('error', t('knowledge.graph.pathError') + e.message);
    } finally {
      setIsComputingPath(false);
    }
  };

  const handleCreateNode = async () => {
    if (!newNodeForm.label.trim()) { showToast('error', t('knowledge.graph.nodeLabelRequired')); return; }
    try {
      let properties = {};
      if (newNodeForm.properties.trim()) { try { properties = JSON.parse(newNodeForm.properties); } catch { showToast('error', t('knowledge.graph.propertiesJsonError')); return; } }
      await knowledgeApi.createNode({ label: newNodeForm.label, nodeType: newNodeForm.nodeType || 'default', description: newNodeForm.description, properties });
      showToast('success', t('knowledge.graph.nodeCreateSuccess'));
      setShowCreateForm(null);
      setNewNodeForm({ label: '', nodeType: '', description: '', properties: '' });
      loadGraph();
    } catch (e) {
      showToast('error', t('knowledge.graph.nodeCreateError') + e.message);
    }
  };

  const handleCreateEdge = async () => {
    if (!newEdgeForm.sourceNodeId || !newEdgeForm.targetNodeId) { showToast('error', t('knowledge.graph.edgeRequired')); return; }
    try {
      await knowledgeApi.createEdge({ sourceNodeId: newEdgeForm.sourceNodeId, targetNodeId: newEdgeForm.targetNodeId, relationship: newEdgeForm.relationship || 'related_to', weight: parseFloat(newEdgeForm.weight) || 1 });
      showToast('success', t('knowledge.graph.edgeCreateSuccess'));
      setShowCreateForm(null);
      setNewEdgeForm({ sourceNodeId: '', targetNodeId: '', relationship: '', weight: '1' });
      loadGraph();
    } catch (e) {
      showToast('error', t('knowledge.graph.edgeCreateError') + e.message);
    }
  };

  // ── Adapted node/link for GraphCanvas ──
  const canvasNodes = nodes.map(n => ({
    id: n.id,
    type: n.type || 'default',
    label: n.label,
    properties: n.properties,
    rows: n.description || '',
  }));

  const canvasLinks = edges.map(e => ({
    id: e.id,
    source: e.source,
    target: e.target,
  }));

  const detailDisplayNode = nodeDetail || (selectedNodeId ? nodes.find(n => n.id === selectedNodeId) : null) || null;

  return (
    <div className={`flex flex-1 min-h-0 ${styles.appBg} rounded-xl border ${styles.cardBorder} overflow-hidden`}>
      <LeftToolbar
        styles={styles}
        t={t}
        searchQuery={searchQuery}
        onSearchQueryChange={(v) => { setSearchQuery(v); setShowSearchResults(false); }}
        onSearchSubmit={handleSearch}
        onSearchInputFocus={() => { if (searchResults.length > 0) setShowSearchResults(true); }}
        showSearchResults={showSearchResults}
        searchResults={searchResults}
        onSearchResultClick={handleSearchResultClick}
        pathSource={pathSource}
        pathTarget={pathTarget}
        onPathSourceChange={setPathSource}
        onPathTargetChange={setPathTarget}
        onComputePath={handleComputePath}
        onPathMissing={() => showToast('error', t('knowledge.graph.pathRequired'))}
        isComputingPath={isComputingPath}
        domainFilter={domainFilter}
        onDomainChange={handleDomainChange}
        navDomain={navDomain}
        onNavDomainChange={setNavDomain}
        navDomains={navDomains}
        navCategories={navCategories}
        selectedCategoryIds={selectedCategoryIds}
        onCategoryToggle={(id) => {
          toggleCategoryId(id);
          // 选择变更立即重载（保持 domainFilter 联动）
          loadGraph(domainFilter || undefined);
        }}
        onLoadFullGraph={handleLoadFullGraph}
        isLoading={isLoading}
        neighborDegree={neighborDegree}
        onNeighborDegreeChange={(n) => setNeighborDegree(n)}
      />

      {/* Middle Graph Canvas */}
      <div className="flex-1 min-h-0 relative">
        {isLoading && (
          <div className={`absolute top-4 left-1/2 -translate-x-1/2 z-20 px-3 py-1.5 ${styles.sidebarBg} border ${styles.cardBorder} rounded-lg text-[11px] ${styles.cardText} flex items-center gap-2 shadow-lg`}>
            <Loader2 size={12} className="animate-spin" />
            {t('knowledge.graph.loadingGraph')}
          </div>
        )}

        {nodes.length === 0 && !isLoading ? (
          <div className={`flex items-center justify-center h-full ${styles.muted} text-xs`}>
            <div className="text-center space-y-2">
              <Network size={32} className={`mx-auto ${styles.muted}`} />
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
            onDoubleClickNode={handleDoubleClickNode}
            onCollapseNode={handleCollapseNode}
            pathNodeIds={pathNodes}
            pathEdgeIds={pathEdges}
            expandedNodeIds={expandedNodeIds}
            interactive={true}
          />
        )}

        {/* Bottom action bar */}
        <div className="absolute bottom-3 left-3 right-3 z-20 flex gap-2">
          <button
            onClick={() => setShowCreateForm('node')}
            className="px-3 py-1.5 text-[11px] font-bold bg-blue-600 hover:bg-blue-500 text-white rounded-lg flex items-center gap-1.5 transition cursor-pointer shadow-lg"
          >
            <Plus size={12} />
            {t('knowledge.graph.newNode')}
          </button>
          <button
            onClick={() => setShowCreateForm('edge')}
            className="px-3 py-1.5 text-[11px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg flex items-center gap-1.5 transition cursor-pointer shadow-lg"
          >
            <ArrowRight size={12} />
            {t('knowledge.graph.newEdge')}
          </button>
          <span className={`text-[10px] ${styles.muted} self-center ml-auto`}>
            {t('knowledge.graph.nodeEdgeCount', { nodes: nodes.length, edges: edges.length })}
          </span>
        </div>
      </div>

      {/* Right Detail Panel (slide-out) */}
      {showDetailPanel && (
        <DetailPanel
          styles={styles}
          t={t}
          node={detailDisplayNode}
          nodeEdges={nodeEdges}
          expandedNodeIds={expandedNodeIds}
          onCollapse={handleCollapseNode}
          onExpand={handleExpandNeighbors}
          onSetPathSource={setPathSource}
          onClose={() => { setShowDetailPanel(false); setSelectedNodeId(null); }}
        />
      )}

      {/* Create Node/Edge Modal */}
      {showCreateForm && (
        <CreateModal
          styles={styles}
          t={t}
          mode={showCreateForm}
          nodes={nodes}
          newNodeForm={newNodeForm}
          onNewNodeFormChange={(patch) => setNewNodeForm(p => ({ ...p, ...patch }))}
          newEdgeForm={newEdgeForm}
          onNewEdgeFormChange={(patch) => setNewEdgeForm(p => ({ ...p, ...patch }))}
          onCreateNode={handleCreateNode}
          onCreateEdge={handleCreateEdge}
          onClose={() => setShowCreateForm(null)}
        />
      )}
    </div>
  );
}
