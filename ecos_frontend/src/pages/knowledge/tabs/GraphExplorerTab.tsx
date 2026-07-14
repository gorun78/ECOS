/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * GraphExplorerTab — 知识图谱探索器
 * 图谱可视化交互、节点搜索、邻居展开、路径分析
 * 复用 GraphCanvas 组件做图谱可视化渲染
 */

import React, { useState, useCallback } from 'react';
import {
  Search, Share2, Network, Plus, ArrowRight, Loader2,
  X, ExternalLink, GitBranch, Info, Hash, Tag
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import GraphCanvas from '../../../components/GraphCanvas';

// ── Graph Node / Edge types (compatible with GraphCanvas) ──
interface GraphNode {
  id: string;
  label: string;
  type: string;
  properties?: Record<string, any>;
  description?: string;
}

interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relationship?: string;
  weight?: number;
}

interface GraphExplorerTabProps {
  // Independent tab, no external props
}

const DOMAIN_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'ontology', label: '本体域' },
  { value: 'data', label: '数据域' },
  { value: 'business', label: '业务域' },
];

export default function GraphExplorerTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [nodes, setNodes] = useState<GraphNode[]>([]);
  const [edges, setEdges] = useState<GraphEdge[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [domainFilter, setDomainFilter] = useState('');
  const [neighborDegree, setNeighborDegree] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [showDetailPanel, setShowDetailPanel] = useState(false);
  const [nodeDetail, setNodeDetail] = useState<GraphNode | null>(null);
  const [nodeEdges, setNodeEdges] = useState<GraphEdge[]>([]);
  const [showPathModal, setShowPathModal] = useState(false);
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

  const loadGraph = useCallback(async (domain?: string) => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchGraph(domain) as any;
      setNodes(data?.nodes || []);
      setEdges(data?.edges || data?.links || []);
      setPathNodes(new Set());
      setPathEdges(new Set());
    } catch (e: any) {
      showToast('error', '加载图谱异常: ' + e.message);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const handleSearch = () => {
    if (!searchQuery.trim()) return;
    setIsLoading(true);
    knowledgeApi.searchKnowledge(searchQuery).then((data: any) => {
      setNodes(data?.nodes || []);
      setEdges(data?.edges || data?.links || []);
    }).catch((e: any) => showToast('error', '搜索异常: ' + e.message)).finally(() => setIsLoading(false));
  };

  const handleDomainChange = (domain: string) => {
    setDomainFilter(domain);
    loadGraph(domain || undefined);
  };

  const handleLoadFullGraph = () => { setSearchQuery(''); setDomainFilter(''); loadGraph(); };

  const handleExpandNeighbors = async (nodeId: string) => {
    setIsLoading(true);
    try {
      const data = await knowledgeApi.fetchNeighbors(nodeId, neighborDegree) as any;
      const newNodes = data?.nodes || [];
      const newEdges = data?.edges || data?.links || [];
      setNodes(prev => { const existing = new Set(prev.map(n => n.id)); return [...prev, ...newNodes.filter((n: GraphNode) => !existing.has(n.id))]; });
      setEdges(prev => { const existing = new Set(prev.map(e => e.id)); return [...prev, ...newEdges.filter((e: GraphEdge) => !existing.has(e.id))]; });
      showToast('success', `已展开 ${nodeId} 的邻居节点`);
    } catch (e: any) {
      showToast('error', '展开邻居异常: ' + e.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSelectNode = async (nodeId: string | null) => {
    setSelectedNodeId(nodeId);
    if (nodeId) {
      setShowDetailPanel(true);
      try {
        const data = await knowledgeApi.fetchNode(nodeId) as any;
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
    if (!pathSource || !pathTarget) { showToast('error', '请选择起点和终点节点'); return; }
    setIsComputingPath(true);
    try {
      const data = await knowledgeApi.findPath(pathSource, pathTarget) as any;
      const pathNodeList: string[] = data?.path || data?.nodeIds || [];
      const pathEdgeList: string[] = data?.pathEdges || data?.edgeIds || [];
      setPathNodes(new Set(pathNodeList));
      setPathEdges(new Set(pathEdgeList));
      showToast('success', `路径计算完成，共 ${pathNodeList.length} 个节点`);
    } catch (e: any) {
      showToast('error', '路径计算异常: ' + e.message);
    } finally {
      setIsComputingPath(false);
      setShowPathModal(false);
    }
  };

  const handleCreateNode = async () => {
    if (!newNodeForm.label.trim()) { showToast('error', '节点标签不能为空'); return; }
    try {
      let properties = {};
      if (newNodeForm.properties.trim()) { try { properties = JSON.parse(newNodeForm.properties); } catch { showToast('error', '属性JSON格式错误'); return; } }
      await knowledgeApi.createNode({ label: newNodeForm.label, nodeType: newNodeForm.nodeType || 'default', description: newNodeForm.description, properties });
      showToast('success', '节点创建成功');
      setShowCreateForm(null);
      setNewNodeForm({ label: '', nodeType: '', description: '', properties: '' });
      loadGraph();
    } catch (e: any) {
      showToast('error', '创建节点异常: ' + e.message);
    }
  };

  const handleCreateEdge = async () => {
    if (!newEdgeForm.sourceNodeId || !newEdgeForm.targetNodeId) { showToast('error', '源节点和目标节点不能为空'); return; }
    try {
      await knowledgeApi.createEdge({ sourceNodeId: newEdgeForm.sourceNodeId, targetNodeId: newEdgeForm.targetNodeId, relationship: newEdgeForm.relationship || 'related_to', weight: parseFloat(newEdgeForm.weight) || 1 });
      showToast('success', '边创建成功');
      setShowCreateForm(null);
      setNewEdgeForm({ sourceNodeId: '', targetNodeId: '', relationship: '', weight: '1' });
      loadGraph();
    } catch (e: any) {
      showToast('error', '创建边异常: ' + e.message);
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
    <div className="flex h-full bg-slate-900 rounded-xl border border-slate-700 overflow-hidden">
      {/* Left Toolbar */}
      <div className="w-56 border-r border-slate-700 flex flex-col shrink-0 p-3 space-y-3 bg-slate-900">
        {/* Search */}
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">搜索节点</label>
          <div className="flex gap-1.5">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              placeholder="节点标签..."
              className="flex-1 px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-blue-500"
            />
            <button
              onClick={handleSearch}
              className="px-2 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg cursor-pointer transition"
            >
              <Search size={13} />
            </button>
          </div>
        </div>

        {/* Domain Filter */}
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">领域过滤</label>
          <select
            value={domainFilter}
            onChange={(e) => handleDomainChange(e.target.value)}
            className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-blue-500 cursor-pointer"
          >
            {DOMAIN_OPTIONS.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        {/* Load Full Graph */}
        <button
          onClick={handleLoadFullGraph}
          disabled={isLoading}
          className="w-full px-3 py-2 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg border border-slate-600 flex items-center justify-center gap-2 transition cursor-pointer disabled:opacity-50"
        >
          {isLoading ? <Loader2 size={12} className="animate-spin" /> : <Network size={12} />}
          加载全图
        </button>

        {/* Neighbor Expand */}
        <div className="space-y-1.5">
          <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
            邻居展开度数 (1-3)
          </label>
          <input
            type="range"
            min={1}
            max={3}
            value={neighborDegree}
            onChange={(e) => setNeighborDegree(parseInt(e.target.value))}
            className="w-full accent-blue-500 cursor-pointer"
          />
          <div className="flex justify-between text-[10px] text-slate-500">
            <span>1</span>
            <span className="font-bold text-blue-400">{neighborDegree}</span>
            <span>3</span>
          </div>
        </div>

        {/* Path Analysis */}
        <button
          onClick={() => setShowPathModal(true)}
          className="w-full px-3 py-2 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-amber-400 rounded-lg border border-slate-600 flex items-center justify-center gap-2 transition cursor-pointer"
        >
          <GitBranch size={12} />
          路径分析
        </button>

        {/* Legend */}
        <div className="mt-auto p-2.5 bg-slate-800 rounded-lg border border-slate-700 space-y-1.5">
          <span className="text-[9px] font-bold text-slate-500 uppercase">图例</span>
          <div className="space-y-1 text-[10px] text-slate-400">
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-blue-500" /> 数据域
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-emerald-500" /> 本体域
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full bg-amber-500" /> 业务域
            </div>
          </div>
        </div>
      </div>

      {/* Middle Graph Canvas */}
      <div className="flex-1 relative">
        {isLoading && (
          <div className="absolute top-4 left-1/2 -translate-x-1/2 z-20 px-3 py-1.5 bg-slate-800 border border-slate-600 rounded-lg text-[11px] text-slate-300 flex items-center gap-2 shadow-lg">
            <Loader2 size={12} className="animate-spin" />
            加载图谱中...
          </div>
        )}

        {nodes.length === 0 && !isLoading ? (
          <div className="flex items-center justify-center h-full text-slate-500 text-xs">
            <div className="text-center space-y-2">
              <Network size={32} className="mx-auto text-slate-600" />
              <p>点击"加载全图"或搜索节点开始探索</p>
            </div>
          </div>
        ) : (
          <GraphCanvas
            nodes={canvasNodes}
            links={canvasLinks}
            selectedNodeId={selectedNodeId}
            onSelectNode={handleSelectNode}
            onDoubleClickNode={handleDoubleClickNode}
            pathNodeIds={pathNodes}
            pathEdgeIds={pathEdges}
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
            新建节点
          </button>
          <button
            onClick={() => setShowCreateForm('edge')}
            className="px-3 py-1.5 text-[11px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg flex items-center gap-1.5 transition cursor-pointer shadow-lg"
          >
            <ArrowRight size={12} />
            新建边
          </button>
          <span className="text-[10px] text-slate-500 self-center ml-auto">
            {nodes.length} 节点 · {edges.length} 边
          </span>
        </div>
      </div>

      {/* Right Detail Panel (slide-out) */}
      {showDetailPanel && (
        <div className="w-72 border-l border-slate-700 flex flex-col shrink-0 bg-slate-900 overflow-y-auto">
          <div className="px-3 py-2.5 border-b border-slate-700 flex items-center justify-between">
            <h3 className="text-xs font-bold text-slate-300 flex items-center gap-1.5">
              <Info size={12} className="text-blue-400" />
              节点详情
            </h3>
            <button
              onClick={() => { setShowDetailPanel(false); setSelectedNodeId(null); }}
              className="p-1 hover:bg-slate-800 rounded text-slate-500 hover:text-slate-300 cursor-pointer transition"
            >
              <X size={13} />
            </button>
          </div>

          {detailDisplayNode ? (
            <div className="p-3 space-y-3 text-[11px]">
              {/* Basic Info */}
              <div className="space-y-1.5">
                <div className="text-sm font-bold text-slate-100">
                  {detailDisplayNode.label}
                </div>
                <div className="flex items-center gap-1.5 text-slate-400">
                  <Tag size={10} />
                  <span>{detailDisplayNode.type || 'N/A'}</span>
                </div>
                {detailDisplayNode.description && (
                  <p className="text-slate-400 leading-relaxed">
                    {detailDisplayNode.description}
                  </p>
                )}
              </div>

              {/* Properties */}
              {detailDisplayNode.properties && Object.keys(detailDisplayNode.properties).length > 0 && (
                <div className="space-y-1.5">
                  <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                    属性
                  </span>
                  <div className="bg-slate-800 rounded-lg p-2 space-y-1">
                    {Object.entries(detailDisplayNode.properties).map(([key, value]) => (
                      <div key={key} className="flex justify-between text-[10px]">
                        <span className="text-slate-400">{key}</span>
                        <span className="text-slate-200 font-mono">
                          {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Related Edges */}
              <div className="space-y-1.5">
                <span className="text-[10px] font-bold text-slate-500 uppercase tracking-wider">
                  关联边 ({nodeEdges.length})
                </span>
                {nodeEdges.length === 0 ? (
                  <p className="text-slate-600 text-[10px]">暂无关联边</p>
                ) : (
                  <div className="space-y-1 max-h-40 overflow-y-auto">
                    {nodeEdges.map((edge) => (
                      <div
                        key={edge.id}
                        className="bg-slate-800 rounded-md px-2 py-1.5 text-[10px] flex items-center justify-between"
                      >
                        <span className="text-slate-300 truncate flex-1">
                          {edge.source} → {edge.target}
                        </span>
                        {edge.relationship && (
                          <span className="text-blue-400 font-bold shrink-0 ml-1">
                            {edge.relationship}
                          </span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Action Buttons */}
              <div className="space-y-1.5 pt-2 border-t border-slate-800">
                <button
                  onClick={() => handleExpandNeighbors(detailDisplayNode.id)}
                  className="w-full px-3 py-1.5 text-[11px] font-bold bg-blue-600/20 hover:bg-blue-600/30 text-blue-400 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer"
                >
                  <ExternalLink size={11} />
                  展开邻居
                </button>
                <button
                  onClick={() => {
                    setPathSource(detailDisplayNode.id);
                    setShowPathModal(true);
                  }}
                  className="w-full px-3 py-1.5 text-[11px] font-bold bg-amber-500/20 hover:bg-amber-500/30 text-amber-400 rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer"
                >
                  <GitBranch size={11} />
                  高亮路径
                </button>
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-center h-full text-slate-500 text-[11px]">
              加载中...
            </div>
          )}
        </div>
      )}

      {/* Path Analysis Modal */}
      {showPathModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-slate-900 border border-slate-700 rounded-xl w-96 p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                <GitBranch size={14} className="text-amber-400" />
                路径分析
              </h3>
              <button
                onClick={() => setShowPathModal(false)}
                className="p-1 hover:bg-slate-800 rounded text-slate-500 hover:text-slate-300 cursor-pointer"
              >
                <X size={14} />
              </button>
            </div>

            <div className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  起点节点 ID
                </label>
                <select
                  value={pathSource}
                  onChange={(e) => setPathSource(e.target.value)}
                  className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-amber-500 cursor-pointer"
                >
                  <option value="">选择起点节点</option>
                  {nodes.map(n => (
                    <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                  终点节点 ID
                </label>
                <select
                  value={pathTarget}
                  onChange={(e) => setPathTarget(e.target.value)}
                  className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-amber-500 cursor-pointer"
                >
                  <option value="">选择终点节点</option>
                  {nodes.map(n => (
                    <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex gap-2 pt-2">
              <button
                onClick={() => setShowPathModal(false)}
                className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg border border-slate-600 transition cursor-pointer"
              >
                取消
              </button>
              <button
                onClick={handleComputePath}
                disabled={isComputingPath}
                className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-amber-600 hover:bg-amber-500 text-white rounded-lg flex items-center justify-center gap-1.5 transition cursor-pointer disabled:opacity-50"
              >
                {isComputingPath
                  ? <Loader2 size={12} className="animate-spin" />
                  : <GitBranch size={12} />
                }
                计算路径
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create Node/Edge Modal */}
      {showCreateForm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="bg-slate-900 border border-slate-700 rounded-xl w-96 p-5 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-200 flex items-center gap-2">
                {showCreateForm === 'node'
                  ? <><Plus size={14} className="text-blue-400" /> 新建节点</>
                  : <><ArrowRight size={14} className="text-emerald-400" /> 新建边</>
                }
              </h3>
              <button
                onClick={() => setShowCreateForm(null)}
                className="p-1 hover:bg-slate-800 rounded text-slate-500 hover:text-slate-300 cursor-pointer"
              >
                <X size={14} />
              </button>
            </div>

            {showCreateForm === 'node' ? (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">标签 *</label>
                  <input
                    type="text"
                    value={newNodeForm.label}
                    onChange={(e) => setNewNodeForm(p => ({ ...p, label: e.target.value }))}
                    placeholder="节点显示名称"
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-blue-500"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">节点类型</label>
                  <input
                    type="text"
                    value={newNodeForm.nodeType}
                    onChange={(e) => setNewNodeForm(p => ({ ...p, nodeType: e.target.value }))}
                    placeholder="例如: ontology, data, business"
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-blue-500"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">描述</label>
                  <textarea
                    value={newNodeForm.description}
                    onChange={(e) => setNewNodeForm(p => ({ ...p, description: e.target.value }))}
                    placeholder="节点描述..."
                    rows={2}
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-blue-500 resize-none"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">属性 (JSON)</label>
                  <textarea
                    value={newNodeForm.properties}
                    onChange={(e) => setNewNodeForm(p => ({ ...p, properties: e.target.value }))}
                    placeholder='例如: {"domain": "finance", "owner": "admin"}'
                    rows={2}
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-blue-500 resize-none font-mono"
                  />
                </div>
                <div className="flex gap-2 pt-2">
                  <button
                    onClick={() => setShowCreateForm(null)}
                    className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg border border-slate-600 transition cursor-pointer"
                  >
                    取消
                  </button>
                  <button
                    onClick={handleCreateNode}
                    className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition cursor-pointer"
                  >
                    创建节点
                  </button>
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">源节点 ID *</label>
                  <select
                    value={newEdgeForm.sourceNodeId}
                    onChange={(e) => setNewEdgeForm(p => ({ ...p, sourceNodeId: e.target.value }))}
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-emerald-500 cursor-pointer"
                  >
                    <option value="">选择源节点</option>
                    {nodes.map(n => (
                      <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">目标节点 ID *</label>
                  <select
                    value={newEdgeForm.targetNodeId}
                    onChange={(e) => setNewEdgeForm(p => ({ ...p, targetNodeId: e.target.value }))}
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-emerald-500 cursor-pointer"
                  >
                    <option value="">选择目标节点</option>
                    {nodes.map(n => (
                      <option key={n.id} value={n.id}>{n.label} ({n.id})</option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">关系类型</label>
                  <input
                    type="text"
                    value={newEdgeForm.relationship}
                    onChange={(e) => setNewEdgeForm(p => ({ ...p, relationship: e.target.value }))}
                    placeholder="例如: depends_on, belongs_to"
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 placeholder-slate-500 outline-none focus:border-emerald-500"
                  />
                </div>
                <div className="space-y-1.5">
                  <label className="text-[10px] font-bold text-slate-400 uppercase">权重</label>
                  <input
                    type="number"
                    value={newEdgeForm.weight}
                    onChange={(e) => setNewEdgeForm(p => ({ ...p, weight: e.target.value }))}
                    min="0"
                    step="0.1"
                    className="w-full px-2.5 py-1.5 text-[11px] bg-slate-800 border border-slate-600 rounded-lg text-slate-200 outline-none focus:border-emerald-500"
                  />
                </div>
                <div className="flex gap-2 pt-2">
                  <button
                    onClick={() => setShowCreateForm(null)}
                    className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg border border-slate-600 transition cursor-pointer"
                  >
                    取消
                  </button>
                  <button
                    onClick={handleCreateEdge}
                    className="flex-1 px-3 py-1.5 text-[11px] font-bold bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg transition cursor-pointer"
                  >
                    创建边
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
