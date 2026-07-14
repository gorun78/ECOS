/**
 * Knowledge Graph — 企业知识图谱（图+属性页展示）
 * 使用 GraphCanvas 组件交互式展示节点与关系，选中后右侧展示属性页
 * 功能：路径高亮、节点展开/收起、图统计面板
 * @license Apache-2.0
 */

import React, { useEffect, useState, useCallback, useMemo } from "react";
import { Network, Search, RefreshCw, AlertCircle, X, Database, FileText, Users, Building, Tag, Route, BarChart3, GitBranch } from "lucide-react";
import { fetchKnowledgeGraph, searchKnowledge, fetchKnowledgePath, fetchKnowledgeNeighbors } from "../api";
import type { KnowledgeNode, KnowledgeEdge } from "../types";
import { useLanguage } from "../components/LanguageContext";
import GraphCanvas from "../components/GraphCanvas";

const TYPE_ICONS: Record<string, any> = {
  concept: Tag, entity: Building, person: Users, document: FileText, dataset: Database,
};

const TYPE_COLORS: Record<string, { bg: string; text: string }> = {
  concept: { bg: "bg-indigo-50", text: "text-indigo-600" },
  entity: { bg: "bg-emerald-50", text: "text-emerald-600" },
  person: { bg: "bg-amber-50", text: "text-amber-600" },
  document: { bg: "bg-blue-50", text: "text-blue-600" },
  dataset: { bg: "bg-purple-50", text: "text-purple-600" },
};

export default function KnowledgeGraphPage() {
  const { locale } = useLanguage();
  const [kgNodes, setKgNodes] = useState<KnowledgeNode[]>([]);
  const [kgEdges, setKgEdges] = useState<KnowledgeEdge[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  // ── Feature 1: Path Highlight ──
  const [pathSource, setPathSource] = useState("");
  const [pathTarget, setPathTarget] = useState("");
  const [pathNodeIds, setPathNodeIds] = useState<Set<string>>(new Set());
  const [pathEdgeIds, setPathEdgeIds] = useState<Set<string>>(new Set());
  const [pathLength, setPathLength] = useState<number | null>(null);
  const [pathLoading, setPathLoading] = useState(false);
  const [pathError, setPathError] = useState<string | null>(null);

  // ── Feature 2: Node Expand/Collapse ──
  const [expandedNodeIds, setExpandedNodeIds] = useState<Set<string>>(new Set());
  const [newlyAddedNodeIds, setNewlyAddedNodeIds] = useState<Set<string>>(new Set());
  const [expandLoading, setExpandLoading] = useState(false);

  const loadGraph = async (query?: string) => {
    setLoading(true);
    setError(null);
    try {
      if (query && query.trim()) {
        const results = await searchKnowledge(query);
        if (results?.data && Array.isArray(results.data)) {
          setKgNodes(results.data);
          setKgEdges([]);
        }
      } else {
        const data = await fetchKnowledgeGraph();
        setKgNodes(data.nodes || []);
        setKgEdges(data.edges || []);
      }
      // Clear path highlight when graph reloads
      clearPathHighlight();
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadGraph(); }, []);

  // ── Feature 1: Path highlight handler ──
  const clearPathHighlight = () => {
    setPathNodeIds(new Set());
    setPathEdgeIds(new Set());
    setPathLength(null);
    setPathError(null);
  };

  const handleFindPath = async () => {
    if (!pathSource || !pathTarget) {
      setPathError(locale === "zh" ? "请选择起点和终点" : "Please select source and target");
      return;
    }
    if (pathSource === pathTarget) {
      setPathError(locale === "zh" ? "起点和终点不能相同" : "Source and target cannot be the same");
      return;
    }
    setPathLoading(true);
    setPathError(null);
    try {
      const result = await fetchKnowledgePath(pathSource, pathTarget);
      if (result && result.path && result.path.length > 0) {
        setPathNodeIds(new Set(result.path));
        setPathEdgeIds(new Set((result.edges || []).map((e: any) => e.id)));
        setPathLength(result.length ?? result.path.length - 1);
      } else {
        setPathError(locale === "zh" ? "未找到路径" : "No path found");
        clearPathHighlight();
      }
    } catch (e: any) {
      setPathError(e.message || "Path lookup failed");
      clearPathHighlight();
    } finally {
      setPathLoading(false);
    }
  };

  // ── Feature 2: Node expand/collapse handler ──
  const handleNodeDoubleClick = useCallback(async (nodeId: string) => {
    if (expandedNodeIds.has(nodeId)) return; // already expanded
    setExpandLoading(true);
    try {
      const result = await fetchKnowledgeNeighbors(nodeId, 1);
      if (!result || !result.nodes || result.nodes.length === 0) {
        // Mark as expanded even if no neighbors, to avoid repeated calls
        setExpandedNodeIds(prev => new Set([...prev, nodeId]));
        return;
      }

      // Dedup: only add nodes/edges that don't already exist
      const existingNodeIds = new Set(kgNodes.map(n => n.id));
      const existingEdgeIds = new Set(kgEdges.map(e => e.id));
      
      const newNodes: KnowledgeNode[] = [];
      const newEdges: KnowledgeEdge[] = [];
      const newNodeIds: string[] = [];

      for (const n of result.nodes) {
        if (!existingNodeIds.has(n.id)) {
          newNodes.push(n);
          newNodeIds.push(n.id);
        }
      }
      for (const e of result.edges) {
        const edgeId = e.id || `${e.sourceNodeId || e.source}_${e.targetNodeId || e.target}_${e.relationship}`;
        if (!existingEdgeIds.has(edgeId)) {
          newEdges.push({ ...e, id: edgeId });
        }
      }

      if (newNodes.length > 0 || newEdges.length > 0) {
        setKgNodes(prev => [...prev, ...newNodes]);
        setKgEdges(prev => [...prev, ...newEdges]);
        setNewlyAddedNodeIds(prev => new Set([...prev, ...newNodeIds]));
      }
      setExpandedNodeIds(prev => new Set([...prev, nodeId]));
    } catch (e: any) {
      console.warn("Expand neighbors failed:", e);
    } finally {
      setExpandLoading(false);
    }
  }, [kgNodes, kgEdges, expandedNodeIds]);

  const handleCollapseNode = useCallback((nodeId: string) => {
    // Remove the node's neighbors that were added via expansion
    // For simplicity, we remove expanded status; the nodes stay in the graph
    // but the collapse button disappears. The user can always reload to reset.
    setExpandedNodeIds(prev => {
      const next = new Set(prev);
      next.delete(nodeId);
      return next;
    });
    // Also clear newly-added highlights for this node's neighbors
    setNewlyAddedNodeIds(prev => {
      const next = new Set(prev);
      // Find neighbor edges and clear their target new status
      const neighborIds = kgEdges
        .filter(e => (e.sourceNodeId || e.source) === nodeId || (e.targetNodeId || e.target) === nodeId)
        .map(e => (e.sourceNodeId || e.source) === nodeId ? (e.targetNodeId || e.target) : (e.sourceNodeId || e.source));
      neighborIds.forEach(id => next.delete(id));
      return next;
    });
  }, [kgEdges]);

  // ── Feature 3: Graph statistics ──
  const graphStats = useMemo(() => {
    const nodeCount = kgNodes.length;
    const edgeCount = kgEdges.length;
    
    // Node type distribution
    const typeDist: Record<string, number> = {};
    kgNodes.forEach(n => {
      const t = n.type || n.nodeType || "unknown";
      typeDist[t] = (typeDist[t] || 0) + 1;
    });

    // Graph density: 2*E / (N*(N-1)) for directed graph
    const maxEdges = nodeCount * (nodeCount - 1);
    const density = nodeCount > 1 ? (2 * edgeCount) / maxEdges : 0;
    
    // Average degree
    const avgDegree = nodeCount > 0 ? (2 * edgeCount) / nodeCount : 0;

    return { nodeCount, edgeCount, typeDist, density, avgDegree };
  }, [kgNodes, kgEdges]);

  // ── Map data for GraphCanvas ──
  const graphNodes = kgNodes.map(n => ({
    id: n.id,
    type: n.type || n.nodeType || "concept",
    label: n.label || n.name || n.id,
    status: "active",
    properties: n.properties || (n.propertiesJson ? (() => { try { return JSON.parse(n.propertiesJson); } catch { return {}; } })() : {}),
    updatedAt: n.createdAt || "",
    owner: (n.properties?.owner as string) || "",
  }));

  const graphLinks = kgEdges.map(e => ({
    id: e.id,
    source: e.sourceNodeId || e.source || "",
    target: e.targetNodeId || e.target || "",
    animated: true,
  }));

  const selectedNode = kgNodes.find(n => n.id === selectedNodeId);
  const relatedEdges = selectedNodeId
    ? kgEdges.filter(e => (e.sourceNodeId || e.source) === selectedNodeId || (e.targetNodeId || e.target) === selectedNodeId)
    : [];
  const relatedNodes = selectedNodeId
    ? kgNodes.filter(n => relatedEdges.some(e => (e.sourceNodeId || e.source) === n.id || (e.targetNodeId || e.target) === n.id))
    : [];
  const typeCfg = selectedNode ? (TYPE_COLORS[selectedNode.type || selectedNode.nodeType || "concept"] || TYPE_COLORS.concept) : null;
  const TypeIcon = selectedNode ? (TYPE_ICONS[selectedNode.type || selectedNode.nodeType || "concept"] || Tag) : Tag;

  // Node options for path dropdowns
  const nodeOptions = kgNodes.map(n => ({ id: n.id, label: n.label || n.name || n.id }));

  return (
    <div className="flex-1 bg-[#F8FAFC] flex flex-col h-full font-sans overflow-hidden">
      {/* Header */}
      <div className="bg-white border-b border-[#E2E8F0] p-4 shrink-0">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h1 className="text-lg font-bold text-slate-800 flex items-center gap-2">
              <Network className="text-indigo-600 w-5 h-5" />
              {locale === "zh" ? "企业知识图谱" : "Enterprise Knowledge Graph"}
            </h1>
            <p className="text-[11px] text-slate-400 mt-0.5">
              {kgNodes.length} {locale === "zh" ? "实体" : "entities"} · {kgEdges.length} {locale === "zh" ? "关系" : "relations"}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5 bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1">
              <Search className="w-3 h-3 text-slate-400" />
              <input type="text"
                className="bg-transparent outline-none text-[11px] text-slate-700 placeholder-slate-400 w-36"
                placeholder={locale === "zh" ? "搜索实体..." : "Search..."}
                value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                onKeyDown={e => e.key === "Enter" && loadGraph(searchQuery)} />
            </div>
            <button onClick={() => loadGraph(searchQuery)}
              className="px-3 py-1 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-[11px] font-semibold">
              {locale === "zh" ? "搜索" : "Search"}
            </button>
            <button onClick={() => { setSearchQuery(""); loadGraph(); }}
              className="p-1.5 rounded hover:bg-slate-100 text-slate-500">
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            </button>
          </div>
        </div>

        {/* ── Path Highlight Row ── */}
        <div className="flex items-center gap-2 mt-3 pt-3 border-t border-slate-100">
          <Route className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-[10px] text-slate-500 font-semibold uppercase tracking-wide">
            {locale === "zh" ? "路径查询" : "Path Find"}
          </span>
          <select
            value={pathSource}
            onChange={e => { setPathSource(e.target.value); clearPathHighlight(); }}
            className="text-[11px] border border-slate-200 rounded-md px-2 py-1 bg-white text-slate-700 outline-none focus:border-indigo-400"
          >
            <option value="">{locale === "zh" ? "起点..." : "Source..."}</option>
            {nodeOptions.map(n => (
              <option key={n.id} value={n.id}>{n.label}</option>
            ))}
          </select>
          <span className="text-[10px] text-slate-400">→</span>
          <select
            value={pathTarget}
            onChange={e => { setPathTarget(e.target.value); clearPathHighlight(); }}
            className="text-[11px] border border-slate-200 rounded-md px-2 py-1 bg-white text-slate-700 outline-none focus:border-indigo-400"
          >
            <option value="">{locale === "zh" ? "终点..." : "Target..."}</option>
            {nodeOptions.map(n => (
              <option key={n.id} value={n.id}>{n.label}</option>
            ))}
          </select>
          <button
            onClick={handleFindPath}
            disabled={pathLoading || !pathSource || !pathTarget}
            className="px-2.5 py-1 bg-red-500 hover:bg-red-600 disabled:bg-slate-300 text-white rounded-md text-[10px] font-semibold transition flex items-center gap-1"
          >
            {pathLoading ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Route className="w-3 h-3" />}
            {locale === "zh" ? "查找路径" : "Find Path"}
          </button>
          {pathLength !== null && (
            <span className="text-[10px] text-red-600 font-semibold bg-red-50 px-2 py-0.5 rounded">
              {locale === "zh" ? "路径长度" : "Length"}: {pathLength}
            </span>
          )}
          {pathNodeIds.size > 0 && (
            <button onClick={clearPathHighlight} className="text-[10px] text-slate-400 hover:text-slate-600 underline">
              {locale === "zh" ? "清除" : "Clear"}
            </button>
          )}
        </div>
      </div>

      {/* Error banner */}
      {error && (
        <div className="bg-amber-50 border-b border-amber-200 px-4 py-2 text-[11px] text-amber-700 flex items-center gap-2 shrink-0">
          <AlertCircle className="w-3 h-3" /> {error}
        </div>
      )}
      {pathError && (
        <div className="bg-red-50 border-b border-red-200 px-4 py-2 text-[11px] text-red-700 flex items-center gap-2 shrink-0">
          <AlertCircle className="w-3 h-3" /> {pathError}
        </div>
      )}

      {/* ── Feature 3: Graph Statistics Panel ── */}
      {!loading && graphNodes.length > 0 && (
        <div className="px-4 py-2 bg-white border-b border-[#E2E8F0] shrink-0">
          <div className="flex items-center gap-4 flex-wrap">
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg">
              <BarChart3 className="w-3.5 h-3.5 text-indigo-500" />
              <div>
                <span className="text-[9px] text-slate-400 uppercase tracking-wide block">
                  {locale === "zh" ? "总节点" : "Nodes"}
                </span>
                <span className="text-sm font-bold text-slate-800">{graphStats.nodeCount}</span>
              </div>
            </div>
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg">
              <GitBranch className="w-3.5 h-3.5 text-emerald-500" />
              <div>
                <span className="text-[9px] text-slate-400 uppercase tracking-wide block">
                  {locale === "zh" ? "总边" : "Edges"}
                </span>
                <span className="text-sm font-bold text-slate-800">{graphStats.edgeCount}</span>
              </div>
            </div>
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg">
              <span className="text-[9px] text-slate-400 uppercase tracking-wide">
                {locale === "zh" ? "类型分布" : "Types"}
              </span>
              <div className="flex items-center gap-1.5">
                {Object.entries(graphStats.typeDist).map(([type, count]) => {
                  const tc = TYPE_COLORS[type] || { bg: "bg-slate-100", text: "text-slate-600" };
                  return (
                    <span key={type} className={`text-[9px] font-semibold px-1.5 py-0.5 rounded-full ${tc.bg} ${tc.text}`}>
                      {type}: {count}
                    </span>
                  );
                })}
              </div>
            </div>
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg">
              <span className="text-[9px] text-slate-400 uppercase tracking-wide">
                {locale === "zh" ? "密度" : "Density"}
              </span>
              <span className="text-sm font-bold text-slate-700">
                {(graphStats.density * 100).toFixed(2)}%
              </span>
            </div>
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-50 rounded-lg">
              <span className="text-[9px] text-slate-400 uppercase tracking-wide">
                {locale === "zh" ? "平均度" : "Avg Deg"}
              </span>
              <span className="text-sm font-bold text-slate-700">
                {graphStats.avgDegree.toFixed(1)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Main: Graph + Property Panel */}
      <div className="flex-1 flex min-h-0">
        {/* Graph Canvas */}
        <div className="flex-1 min-w-0 flex">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <RefreshCw className="w-10 h-10 text-slate-300 animate-spin" />
            </div>
          ) : graphNodes.length === 0 ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-center space-y-3">
                <Network className="w-10 h-10 text-slate-300 mx-auto" />
                <p className="text-sm text-slate-400">
                  {locale === "zh" ? "暂无知识图谱数据" : "No knowledge graph data"}
                </p>
              </div>
            </div>
          ) : (
            <GraphCanvas
              nodes={graphNodes}
              links={graphLinks}
              selectedNodeId={selectedNodeId}
              onSelectNode={setSelectedNodeId}
              onDoubleClickNode={handleNodeDoubleClick}
              onCollapseNode={handleCollapseNode}
              interactive={true}
              pathNodeIds={pathNodeIds}
              pathEdgeIds={pathEdgeIds}
              expandedNodeIds={expandedNodeIds}
              newlyAddedNodeIds={newlyAddedNodeIds}
            />
          )}
          {/* Expand loading indicator */}
          {expandLoading && (
            <div className="absolute bottom-4 right-4 z-10 bg-white border border-slate-200 rounded-lg px-3 py-1.5 shadow flex items-center gap-2">
              <RefreshCw className="w-3 h-3 animate-spin text-indigo-500" />
              <span className="text-[10px] text-slate-600">
                {locale === "zh" ? "正在展开邻居..." : "Expanding neighbors..."}
              </span>
            </div>
          )}
        </div>

        {/* Property Panel */}
        {selectedNode && (
          <div className="w-[300px] bg-white border-l border-[#E2E8F0] p-5 overflow-y-auto shrink-0 shadow-lg">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                {TypeIcon && <TypeIcon className={`w-4 h-4 ${typeCfg?.text || "text-slate-600"}`} />}
                {locale === "zh" ? "实体属性" : "Entity Properties"}
              </h3>
              <button onClick={() => setSelectedNodeId(null)}
                className="p-1 hover:bg-slate-100 rounded">
                <X className="w-4 h-4 text-slate-400" />
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <span className="text-[10px] text-slate-400 uppercase tracking-wider block">ID</span>
                <span className="font-mono text-slate-700 text-[11px]">{selectedNode.id}</span>
              </div>
              <div>
                <span className="text-[10px] text-slate-400 uppercase tracking-wider block">{locale === "zh" ? "名称" : "Name"}</span>
                <span className="font-semibold text-slate-800">{selectedNode.label || selectedNode.name || selectedNode.id}</span>
              </div>
              <div>
                <span className="text-[10px] text-slate-400 uppercase tracking-wider block">{locale === "zh" ? "类型" : "Type"}</span>
                <span className={`inline-block px-2 py-0.5 rounded-full text-[10px] font-semibold ${typeCfg?.bg || "bg-slate-50"} ${typeCfg?.text || "text-slate-600"}`}>
                  {selectedNode.type || selectedNode.nodeType || "concept"}
                </span>
              </div>
              {selectedNode.description && (
                <div>
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block">{locale === "zh" ? "描述" : "Description"}</span>
                  <p className="text-slate-600 leading-relaxed">{selectedNode.description}</p>
                </div>
              )}
              {selectedNode.createdAt && (
                <div>
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block">{locale === "zh" ? "创建时间" : "Created"}</span>
                  <span className="text-slate-500">{selectedNode.createdAt}</span>
                </div>
              )}

              {/* Properties */}
              {(selectedNode.properties || (selectedNode.propertiesJson && (() => { try { return JSON.parse(selectedNode.propertiesJson); } catch { return null; } })())) && (
                <div className="pt-3 border-t border-slate-100">
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block mb-2">
                    {locale === "zh" ? "属性" : "Properties"}
                  </span>
                  <div className="space-y-1.5">
                    {Object.entries(
                      selectedNode.properties || (() => { try { return JSON.parse(selectedNode.propertiesJson || "{}"); } catch { return {}; } })()
                    ).slice(0, 10).map(([k, v]) => (
                      <div key={k} className="flex justify-between bg-slate-50 rounded px-2 py-1">
                        <span className="font-mono text-[10px] text-slate-500">{k}</span>
                        <span className="text-[10px] text-slate-700 truncate ml-2 max-w-[140px]">{String(v)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Related Entities */}
              {relatedNodes.length > 0 && (
                <div className="pt-3 border-t border-slate-100">
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block mb-2">
                    {locale === "zh" ? "关联实体" : "Related"} ({relatedNodes.length})
                  </span>
                  <div className="space-y-1">
                    {relatedNodes.map(rn => {
                      const rnType = rn.type || rn.nodeType || "concept";
                      const rnColors = TYPE_COLORS[rnType] || TYPE_COLORS.concept;
                      const RnIcon = TYPE_ICONS[rnType] || Tag;
                      const edge = relatedEdges.find(e => (e.sourceNodeId || e.source) === rn.id || (e.targetNodeId || e.target) === rn.id);
                      return (
                        <button key={rn.id}
                          onClick={() => setSelectedNodeId(rn.id)}
                          className={`w-full text-left flex items-center gap-1.5 p-2 rounded-lg text-[10px] hover:shadow-sm transition ${rnColors.bg}`}>
                          <RnIcon className={`w-3 h-3 ${rnColors.text}`} />
                          <span className={`font-semibold ${rnColors.text}`}>{rn.label || rn.name || rn.id}</span>
                          {edge?.label && <span className="text-[9px] text-slate-400 ml-auto">{edge.label}</span>}
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
