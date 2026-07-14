/**
 * KnowledgeGraphHome — Step 1 知识图谱首页
 *
 * 对应设计文档第7章 Step 1 布局：
 * ┌──────────────────────────────────────────────────────────────┐
 * │  本体工作台                              [创建业务域]  [?帮助] │
 * ├──────────────────────────────────────────────────────────────┤
 * │                   ┌──────────────────────────┐               │
 * │                   │   全局知识图谱可视化      │   ┌─────────┐ │
 * │                   │   (只读 SVG 节点连线)    │   │ 图谱统计 │ │
 * │                   │                          │   │         │ │
 * │                   │   ●──●──●               │   │ 实体:N  │ │
 * │                   │   │  │  │               │   │ 关系:N  │ │
 * │                   │   ●──●──●──●            │   │ 业务域:N│ │
 * │                   └──────────────────────────┘   └─────────┘ │
 * │                                                              │
 * │  业务域分类卡片（下方）                                       │
 * │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐        │
 * │  │ 📦 销售域 │ │ 🏭 供应链 │ │ 💰 财务域 │ │ 👥 HR域  │ ...    │
 * │  │ N实体·N关系│ │ N实体·N关系│ │ N实体·N关系│ │ N实体·N关系│       │
 * │  │ [进入设计] │ │ [进入设计] │ │ [进入设计] │ │ [进入设计] │        │
 * │  └──────────┘ └──────────┘ └──────────┘ └──────────┘        │
 * └──────────────────────────────────────────────────────────────┘
 *
 * 数据来源：GET /api/v1/ecos/knowledge-graph
 * 返回结构：{ nodes: [...], edges: [...], stats: {...} }
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo } from 'react';
import {
  Network, Loader2, AlertCircle, Building2, GitBranch,
  ArrowRight, Layers, Box, ChevronRight,
} from 'lucide-react';
import { useTheme } from '../components/ThemeContext';
import { useLanguage } from '../components/LanguageContext';
import { fetchEcosKnowledgeGraph } from '../api';
import DomainStatsPanel from '../components/ontology-workbench/DomainStatsPanel';

// ── 类型定义（内联，待 Agent A 创建 types/workbench.ts 后切换导入）──

/** 知识图谱节点 */
interface KGNode {
  id: string;
  code?: string;
  name?: string;
  label?: string;
  entityCode?: string;
  entityType?: string;
  domain?: string;
  domainName?: string;
}

/** 知识图谱边/关系 */
interface KGEdge {
  id?: string;
  source: string;
  target: string;
  relationshipType?: string;
  label?: string;
}

/** 知识图谱 API 响应 */
interface KnowledgeGraphData {
  nodes: KGNode[];
  edges: KGEdge[];
  stats: {
    nodeCount?: number;
    edgeCount?: number;
    domains?: string[];
    domainCount?: number;
  };
}

/** 从 KG 数据中提取的业务域信息 */
interface DomainInfo {
  code: string;
  name: string;
  entityCount: number;
  relationshipCount: number;
  status: string;
}

// ── 图标映射（按域名称匹配） ──
const DOMAIN_ICONS: Record<string, string> = {
  '销售域': '📦',
  '供应链': '🏭',
  '财务域': '💰',
  'HR域': '👥',
  '人力资源': '👥',
  '采购域': '📋',
  '项目域': '📐',
  '资产域': '🏢',
  '工程域': '🔧',
  '分析域': '📊',
  '研发域': '⚙️',
};

const DOMAIN_COLORS: Record<string, string> = {
  '销售域': 'border-indigo-400/30 bg-indigo-500/10',
  '供应链': 'border-emerald-400/30 bg-emerald-500/10',
  '财务域': 'border-amber-400/30 bg-amber-500/10',
  'HR域': 'border-purple-400/30 bg-purple-500/10',
  '人力资源': 'border-purple-400/30 bg-purple-500/10',
  '采购域': 'border-cyan-400/30 bg-cyan-500/10',
  '项目域': 'border-blue-400/30 bg-blue-500/10',
  '资产域': 'border-pink-400/30 bg-pink-500/10',
  '工程域': 'border-orange-400/30 bg-orange-500/10',
  '分析域': 'border-teal-400/30 bg-teal-500/10',
  '研发域': 'border-red-400/30 bg-red-500/10',
};

// ── 辅助函数：从 KG 数据提取域信息 ──

/**
 * 从知识图谱节点中按 domain 字段分组提取业务域信息。
 * 同时统计每个域下的关系数（边两端节点所属域）。
 */
function extractDomainsFromKG(data: KnowledgeGraphData): DomainInfo[] {
  const { nodes, edges } = data;

  // 按 domain 字段分组
  const domainMap = new Map<string, { name: string; entities: Set<string> }>();

  nodes.forEach((node) => {
    const domainCode = node.domain || 'default';
    const domainName = node.domainName || domainCode;

    if (!domainMap.has(domainCode)) {
      domainMap.set(domainCode, { name: domainName, entities: new Set() });
    }
    domainMap.get(domainCode)!.entities.add(node.id);
  });

  // 统计关系数
  const relCountMap = new Map<string, number>();
  edges.forEach((edge) => {
    const sourceDomain = nodes.find((n) => n.id === edge.source)?.domain;
    const targetDomain = nodes.find((n) => n.id === edge.target)?.domain;

    if (sourceDomain) {
      relCountMap.set(sourceDomain, (relCountMap.get(sourceDomain) || 0) + 1);
    }
    if (targetDomain && targetDomain !== sourceDomain) {
      relCountMap.set(targetDomain, (relCountMap.get(targetDomain) || 0) + 1);
    }
  });

  // 组装结果
  const domains: DomainInfo[] = [];
  domainMap.forEach((info, code) => {
    domains.push({
      code,
      name: info.name,
      entityCount: info.entities.size,
      relationshipCount: relCountMap.get(code) || 0,
      status: 'active',
    });
  });

  return domains;
}

// ── 图谱节点颜色映射 ──

const ENTITY_TYPE_COLORS: Record<string, { fill: string; stroke: string }> = {
  MASTER: { fill: '#6366f1', stroke: '#4f46e5' },
  TRANSACTION: { fill: '#10b981', stroke: '#059669' },
  EVENT: { fill: '#f59e0b', stroke: '#d97706' },
  REFERENCE: { fill: '#8b5cf6', stroke: '#7c3aed' },
  default: { fill: '#64748b', stroke: '#475569' },
};

// ── 简化 SVG 图谱可视化 ──

interface GraphViewProps {
  nodes: KGNode[];
  edges: KGEdge[];
}

function SimpleGraphView({ nodes, edges }: GraphViewProps) {
  // 将节点排列为网格
  const cols = Math.ceil(Math.sqrt(nodes.length)) || 3;
  const cellW = 140;
  const cellH = 90;
  const padding = 30;

  const nodePositions = useMemo(() => {
    const map = new Map<string, { x: number; y: number }>();
    nodes.forEach((node, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      map.set(node.id, {
        x: padding + col * cellW + cellW / 2,
        y: padding + row * cellH + cellH / 2,
      });
    });
    return map;
  }, [nodes, cols]);

  const svgWidth = cols * cellW + padding * 2;
  const svgHeight = Math.ceil(nodes.length / cols) * cellH + padding * 2;

  if (nodes.length === 0) {
    return (
      <div className="flex items-center justify-center h-64 text-slate-500">
        <div className="text-center">
          <Network size={40} className="mx-auto mb-2 opacity-30" />
          <p className="text-xs">暂无图谱数据</p>
        </div>
      </div>
    );
  }

  return (
    <div className="overflow-auto">
      <svg
        width={Math.max(svgWidth, 600)}
        height={Math.max(svgHeight, 300)}
        className="mx-auto"
        style={{ minWidth: '100%' }}
      >
        <defs>
          <marker id="kg-arrow" viewBox="0 0 10 10" refX="14" refY="5"
            markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#475569" />
          </marker>
        </defs>

        {/* 边/关系 */}
        {edges.map((edge, i) => {
          const src = nodePositions.get(edge.source);
          const tgt = nodePositions.get(edge.target);
          if (!src || !tgt) return null;

          // 计算从源节点右边缘到目标节点左边缘的连线
          const x1 = src.x + 30;
          const y1 = src.y;
          const x2 = tgt.x - 30;
          const y2 = tgt.y;

          return (
            <line
              key={`edge-${i}`}
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              stroke="#334155"
              strokeWidth={1.5}
              markerEnd="url(#kg-arrow)"
              opacity={0.6}
            />
          );
        })}

        {/* 节点 */}
        {nodes.map((node) => {
          const pos = nodePositions.get(node.id);
          if (!pos) return null;

          const colors = ENTITY_TYPE_COLORS[node.entityType || ''] || ENTITY_TYPE_COLORS.default;
          const displayName = node.name || node.label || node.code || node.id;
          const displayCode = node.code || node.entityCode || '';

          return (
            <g key={node.id} className="cursor-pointer">
              {/* 节点圆 */}
              <circle
                cx={pos.x}
                cy={pos.y - 8}
                r={18}
                fill={colors.fill}
                stroke={colors.stroke}
                strokeWidth={2}
                opacity={0.9}
              />
              {/* 类型图标（简化为首字母） */}
              <text
                x={pos.x}
                y={pos.y - 4}
                textAnchor="middle"
                fill="white"
                fontSize="9"
                fontWeight="bold"
                fontFamily="monospace"
              >
                {(node.entityType || 'E').charAt(0)}
              </text>
              {/* 节点名称 */}
              <text
                x={pos.x}
                y={pos.y + 24}
                textAnchor="middle"
                fill="#cbd5e1"
                fontSize="10"
                fontWeight="500"
                fontFamily="sans-serif"
              >
                {displayName.length > 10 ? displayName.slice(0, 9) + '…' : displayName}
              </text>
              {/* 节点编码 */}
              {displayCode && (
                <text
                  x={pos.x}
                  y={pos.y + 38}
                  textAnchor="middle"
                  fill="#64748b"
                  fontSize="8"
                  fontFamily="monospace"
                >
                  {displayCode.length > 12 ? displayCode.slice(0, 11) + '…' : displayCode}
                </text>
              )}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

// ── 域卡片 ──

interface DomainCardProps {
  domain: DomainInfo;
  onEnterDesign: (code: string) => void;
}

function DomainCard({ domain, onEnterDesign }: DomainCardProps) {
  const colorClass = DOMAIN_COLORS[domain.name] || 'border-slate-500/30 bg-slate-500/10';
  const icon = DOMAIN_ICONS[domain.name] || '📁';

  return (
    <div className={`rounded-lg border ${colorClass} p-4 flex flex-col hover:border-opacity-60 transition-all hover:scale-[1.02]`}>
      {/* 域图标 + 名称 */}
      <div className="flex items-center gap-2 mb-2">
        <span className="text-lg">{icon}</span>
        <h4 className="text-sm font-semibold text-white truncate">{domain.name}</h4>
      </div>

      {/* 域编码 */}
      <p className="text-[10px] text-slate-500 font-mono mb-3">{domain.code}</p>

      {/* 统计 */}
      <div className="flex items-center gap-3 text-[11px] text-slate-400 mb-3">
        <span className="flex items-center gap-1">
          <Box size={11} className="text-indigo-400" />
          {domain.entityCount} 实体
        </span>
        <span className="flex items-center gap-1">
          <GitBranch size={11} className="text-emerald-400" />
          {domain.relationshipCount} 关系
        </span>
      </div>

      {/* [进入设计] 按钮 */}
      <button
        onClick={() => onEnterDesign(domain.code)}
        className="mt-auto w-full py-1.5 rounded-lg text-xs font-medium
          bg-indigo-600/20 text-indigo-300 border border-indigo-500/30
          hover:bg-indigo-600/30 hover:border-indigo-500/50
          transition flex items-center justify-center gap-1"
      >
        进入设计
        <ChevronRight size={12} />
      </button>
    </div>
  );
}

// ── 主组件 ──

export default function KnowledgeGraphHome() {
  const { t } = useLanguage();
  useTheme();

  // 状态
  const [kgData, setKgData] = useState<KnowledgeGraphData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // 加载知识图谱数据
  useEffect(() => {
    fetchEcosKnowledgeGraph()
      .then((data: KnowledgeGraphData) => {
        setKgData(data);
      })
      .catch((e: any) => {
        setError(e.message || '加载知识图谱失败');
      })
      .finally(() => setLoading(false));
  }, []);

  // 从 KG 数据提取域信息
  const domains = useMemo(() => {
    if (!kgData) return [];
    return extractDomainsFromKG(kgData);
  }, [kgData]);

  // 进入域设计器（导航到 /ontology_workbench/domains/:code）
  const handleEnterDesign = (domainCode: string) => {
    // 使用 HashRouter 导航方式（因为 main.tsx 使用 HashRouter）
    window.location.hash = `#/ontology_workbench?domain=${encodeURIComponent(domainCode)}`;
  };

  // ── 加载态 ──
  if (loading) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[#0f1117]">
        <div className="text-center">
          <Loader2 size={32} className="animate-spin text-indigo-400 mx-auto mb-3" />
          <p className="text-sm text-slate-400">正在加载知识图谱...</p>
        </div>
      </div>
    );
  }

  // ── 错误态 ──
  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center bg-[#0f1117]">
        <div className="text-center p-8">
          <AlertCircle size={40} className="text-red-400 mx-auto mb-3" />
          <p className="text-sm text-red-400 mb-2">加载失败</p>
          <p className="text-xs text-slate-500">{error}</p>
          <button
            onClick={() => window.location.reload()}
            className="mt-4 px-4 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600 transition"
          >
            重新加载
          </button>
        </div>
      </div>
    );
  }

  const nodes = kgData?.nodes || [];
  const edges = kgData?.edges || [];
  const stats = kgData?.stats || {};

  const totalEntities = stats.nodeCount || nodes.length;
  const totalRelationships = stats.edgeCount || edges.length;
  const totalDomains = (stats.domains?.length) || domains.length;

  return (
    <div className="flex-1 flex h-full overflow-hidden bg-[#0f1117]">
      {/* ═══════ 中间主区域：图谱 + 域卡片 ═══════ */}
      <div className="flex-1 flex flex-col overflow-y-auto">
        {/* 页面标题栏 */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#1E293B] bg-[#141924] shrink-0">
          <div className="flex items-center gap-3">
            <Network size={22} className="text-indigo-400" />
            <div>
              <h1 className="text-base font-bold text-white">本体工作台</h1>
              <p className="text-[11px] text-slate-400">
                {totalEntities} 实体 · {totalRelationships} 关系 · {totalDomains} 业务域
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => window.location.reload()}
              className="p-2 hover:bg-white/5 rounded-lg text-slate-400 hover:text-slate-300 transition"
              title="刷新数据"
            >
              <Loader2 size={14} className="hover:animate-spin" />
            </button>
          </div>
        </div>

        {/* 图谱可视化区 */}
        <div className="p-4">
          <div className="bg-[#141924] rounded-lg border border-[#1E293B] overflow-hidden">
            <div className="px-4 py-2 border-b border-[#1E293B] flex items-center gap-2">
              <Layers size={13} className="text-indigo-400" />
              <span className="text-xs font-medium text-slate-300">全局知识图谱</span>
              <span className="text-[10px] text-slate-600 ml-auto">
                只读模式 · {nodes.length} 节点 · {edges.length} 连线
              </span>
            </div>
            <div className="p-2">
              <SimpleGraphView nodes={nodes} edges={edges} />
            </div>
          </div>
        </div>

        {/* 业务域分类卡片 */}
        <div className="px-4 pb-4">
          <div className="flex items-center gap-2 mb-3">
            <Building2 size={14} className="text-indigo-400" />
            <h2 className="text-sm font-semibold text-white">业务域分类</h2>
            <span className="text-[11px] text-slate-500">({domains.length})</span>
          </div>

          {domains.length === 0 ? (
            <div className="text-center py-8 text-slate-500">
              <Box size={32} className="mx-auto mb-2 opacity-30" />
              <p className="text-xs">暂无业务域数据</p>
              <p className="text-[10px] mt-1 opacity-60">知识图谱节点未包含 domain 字段</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {domains.map((domain) => (
                <DomainCard
                  key={domain.code}
                  domain={domain}
                  onEnterDesign={handleEnterDesign}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ═══════ 右侧统计面板 ═══════ */}
      <div className="w-[320px] min-w-[260px] border-l border-[#1E293B] bg-[#141924] flex flex-col shrink-0 overflow-y-auto">
        <DomainStatsPanel
          totalEntities={totalEntities}
          totalRelationships={totalRelationships}
          totalDomains={totalDomains}
        />

        {/* 域快速列表 */}
        {domains.length > 0 && (
          <div className="p-4 border-t border-[#1E293B]">
            <h3 className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">
              业务域列表
            </h3>
            <div className="space-y-1">
              {domains.map((d) => (
                <button
                  key={d.code}
                  onClick={() => handleEnterDesign(d.code)}
                  className="w-full text-left px-3 py-2 rounded-lg text-xs text-slate-300
                    hover:bg-white/5 transition flex items-center gap-2 group"
                >
                  <span>{DOMAIN_ICONS[d.name] || '📁'}</span>
                  <span className="flex-1 truncate">{d.name}</span>
                  <span className="text-[10px] text-slate-600">{d.entityCount}</span>
                  <ArrowRight size={11} className="text-slate-600 opacity-0 group-hover:opacity-100 transition" />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
