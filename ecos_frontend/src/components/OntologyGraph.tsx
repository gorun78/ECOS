/**
 * OntologyGraph — 本体对象关系画布（受控展示件）。
 *
 * 唯一在用 importer：pages/ontology/OverviewView.tsx（仅消费 objectTypes / linkTypes /
 * onSelectNode / onSelectEdge 四个 prop）。
 *
 * 前端检视报告 §4.3 行170 修复说明：
 *  - 移除服务已作废页面（OntologyDesigner，见 main.tsx:155 死页面登记）的
 *    `nodes`/`edges` 预置 props 与 GraphNode/GraphEdge 类型导出——全库 grep 零 importer；
 *  - 演示坐标硬编码（flight/aircraft/airport/pilot）→ 确定性网格自动布局；
 *    后端 ObjectType 模型无坐标字段，故画布显式标注「示意布局」（无假真理）；
 *  - 补空态与键盘可达（Tab 聚焦节点、方向键微调位置、回车/空格选中）；
 *  - 全部文案走 t()、全部颜色走主题 token（原 `#1e293b` 网格点改用 styles.accentStroke，
 *    原 `styles.borderColor` 为不存在 token，已改 styles.muted 经 currentColor 驱动描边）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import * as LucideIcons from 'lucide-react';
import { Network } from 'lucide-react';
import { useTheme } from './ThemeContext';
import { useLanguage } from './LanguageContext';
import { ObjectType, LinkType } from '../types/ontology';

/** 运行时图标名解析（lucide-only；未知名显式回落 Circle，不伪装存在） */
const ICON_MAP = LucideIcons as unknown as Record<string, React.ComponentType<{ size?: number; className?: string }>>;

function DynamicIcon({ name, size = 14, className }: { name?: string; size?: number; className?: string }) {
  const IconComponent = (name && ICON_MAP[name]) || ICON_MAP.Circle;
  return <IconComponent size={size} className={className} />;
}

interface Node {
  id: string;
  label: string;
  icon?: string;
  color: string;
  x: number;
  y: number;
  propertiesCount: number;
}

interface Edge {
  id: string;
  source: string;
  target: string;
  label: string;
  cardinality: string;
}

interface OntologyGraphProps {
  objectTypes?: ObjectType[];
  linkTypes?: LinkType[];
  onSelectNode?: (nodeId: string) => void;
  onSelectEdge?: (edgeId: string) => void;
}

/** 节点拖拽/键盘移动的边界钳制 */
const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));

/**
 * 确定性网格自动布局：cols = ceil(sqrt(n))，逐行铺放取单元格中心。
 * 相同输入恒得相同坐标（无随机、无后端坐标可依赖 → 显式声明为「示意布局」）。
 */
function gridLayout(count: number, width: number, height: number): Array<{ x: number; y: number }> {
  if (count <= 0) return [];
  const cols = Math.max(1, Math.ceil(Math.sqrt(count)));
  const rows = Math.max(1, Math.ceil(count / cols));
  const cellW = width / cols;
  const cellH = height / rows;
  return Array.from({ length: count }, (_, i) => ({
    x: (i % cols + 0.5) * cellW,
    y: (Math.floor(i / cols) + 0.5) * cellH,
  }));
}

export default function OntologyGraph({
  objectTypes = [],
  linkTypes = [],
  onSelectNode = () => {},
  onSelectEdge = () => {}
}: OntologyGraphProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const containerRef = useRef<HTMLDivElement>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [activeDragNodeId, setActiveDragNodeId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [size, setSize] = useState({ w: 860, h: 450 });

  // 实测容器尺寸（布局与边界钳制依赖真实宽高）
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const measure = () => {
      const rect = el.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        setSize({ w: rect.width, h: rect.height });
      }
    };
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // 数据 / 尺寸变化时重建布局（用户拖拽微调在下次数据变化时重置——与修复前行为一致）
  useEffect(() => {
    const positions = gridLayout(objectTypes.length, size.w, size.h);
    setNodes(objectTypes.map((ot, idx) => ({
      id: ot.id,
      label: ot.displayName,
      icon: ot.icon,
      color: ot.color,
      x: positions[idx]?.x ?? size.w / 2,
      y: positions[idx]?.y ?? size.h / 2,
      propertiesCount: ot.properties.length
    })));
    setEdges(linkTypes.map(lt => ({
      id: lt.id,
      source: lt.sourceObjectType,
      target: lt.targetObjectType,
      label: lt.displayName,
      cardinality: lt.cardinality
    })));
  }, [objectTypes, linkTypes, size]);

  // Handle Dragging
  const handleMouseDown = (e: React.MouseEvent, nodeId: string) => {
    e.preventDefault();
    const node = nodes.find(n => n.id === nodeId);
    if (!node || !containerRef.current) return;

    const rect = containerRef.current.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    setActiveDragNodeId(nodeId);
    setDragOffset({
      x: mouseX - node.x,
      y: mouseY - node.y
    });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!activeDragNodeId || !containerRef.current) return;

    const rect = containerRef.current.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    setNodes(prev =>
      prev.map(n =>
        n.id === activeDragNodeId
          ? {
              ...n,
              x: clamp(mouseX - dragOffset.x, 40, rect.width - 40),
              y: clamp(mouseY - dragOffset.y, 40, rect.height - 40)
            }
          : n
      )
    );
  };

  const handleMouseUp = () => {
    setActiveDragNodeId(null);
  };

  /** 键盘操作：回车/空格 = 选中；方向键 = 微调位置（Shift 加速） */
  const handleNodeKeyDown = (e: React.KeyboardEvent, nodeId: string) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelectNode(nodeId);
      return;
    }
    const step = e.shiftKey ? 24 : 8;
    const delta: Record<string, [number, number]> = {
      ArrowUp: [0, -step],
      ArrowDown: [0, step],
      ArrowLeft: [-step, 0],
      ArrowRight: [step, 0],
    };
    const d = delta[e.key];
    if (!d) return;
    e.preventDefault();
    setNodes(prev =>
      prev.map(n =>
        n.id === nodeId
          ? { ...n, x: clamp(n.x + d[0], 40, size.w - 40), y: clamp(n.y + d[1], 40, size.h - 40) }
          : n
      )
    );
  };

  // Get edge path coordinates
  const getEdgeCoordinates = (edge: Edge) => {
    const sourceNode = nodes.find(n => n.id === edge.source);
    const targetNode = nodes.find(n => n.id === edge.target);

    if (!sourceNode || !targetNode) return null;

    return {
      x1: sourceNode.x,
      y1: sourceNode.y,
      x2: targetNode.x,
      y2: targetNode.y
    };
  };

  const isEmpty = nodes.length === 0;

  const domainBadge = useMemo(() => ({
    // assets 域徽章压在 accentBg 实底上 → 前景用 accentSolidText（per-theme 反相），禁 text-white
    assets: `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`,
    operations: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
    default: `${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`,
  }), [styles]);

  return (
    <div
      ref={containerRef}
      className={`relative w-full h-[450px] border rounded-lg overflow-hidden select-none cursor-default ${styles.cardBg} ${styles.cardBorder}`}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
      role="group"
      aria-label={t('ontology.graph.title')}
    >
      {/* Background Grid Accent（网格点色取主题 accentStroke，SVG/canvas 类场景允许具体色值） */}
      <div className="absolute inset-0 opacity-[0.03] pointer-events-none" style={{
        backgroundImage: `radial-gradient(${styles.accentStroke} 1px, transparent 1px)`,
        backgroundSize: '20px 20px'
      }} />

      <div className={`absolute top-3 left-3 flex flex-col gap-1 ${styles.cardBg} backdrop-blur-sm px-3 py-2 rounded border ${styles.cardBorder} shadow-xs z-10`}>
        <span className={`text-xs font-semibold ${styles.cardText}`}>{t('ontology.graph.title')}</span>
        <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('ontology.graph.drag_hint')}</span>
        {/* 无假真理：后端模型无坐标字段，坐标为前端确定性网格排版产物 → 显式标注 */}
        <span className={`text-[10px] font-semibold ${styles.warningText}`}>{t('ontology.graph.schematic')}</span>
      </div>

      {/* 空态：无对象类型时明示，而非空白画布 */}
      {isEmpty && (
        <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
          <Network size={32} className={`mb-2 opacity-40 ${styles.muted}`} aria-hidden="true" />
          <p className={`text-xs ${styles.muted}`}>{t('ontology.graph.empty')}</p>
        </div>
      )}

      <svg className="w-full h-full pointer-events-none absolute inset-0" aria-hidden={isEmpty}>
        <defs>
          <marker
            id="arrow"
            viewBox="0 0 10 10"
            refX="28"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" className={`${styles.muted} fill-current`} />
          </marker>
        </defs>

        {/* Draw Edges */}
        {edges.map(edge => {
          const coords = getEdgeCoordinates(edge);
          if (!coords) return null;

          const dx = coords.x2 - coords.x1;
          const dy = coords.y2 - coords.y1;
          const length = Math.sqrt(dx * dx + dy * dy) || 1;

          // Slight offsets for parallel connections
          const offsetAmount = 15;
          const nx = -dy / length * offsetAmount;
          const ny = dx / length * offsetAmount;

          const isDoubleEdge = edges.some(e => e.id !== edge.id && e.source === edge.target && e.target === edge.source);
          const cx1 = coords.x1 + (isDoubleEdge ? nx : 0);
          const cy1 = coords.y1 + (isDoubleEdge ? ny : 0);
          const cx2 = coords.x2 + (isDoubleEdge ? nx : 0);
          const cy2 = coords.y2 + (isDoubleEdge ? ny : 0);

          // Find midpoint for card/badge
          const midX = (cx1 + cx2) / 2;
          const midY = (cy1 + cy2) / 2;

          return (
            <g
              key={edge.id}
              className="group pointer-events-auto cursor-pointer"
              onClick={() => onSelectEdge(edge.id)}
              role="button"
              aria-label={`${edge.label} ${edge.cardinality}`}
            >
              <title>{`${edge.label} · ${edge.cardinality}`}</title>
              <line
                x1={cx1} y1={cy1} x2={cx2} y2={cy2}
                stroke="currentColor"
                strokeWidth="2"
                markerEnd="url(#arrow)"
                className={`${styles.muted} transition-opacity`}
              />
              <line
                x1={cx1}
                y1={cy1}
                x2={cx2}
                y2={cy2}
                stroke="transparent"
                strokeWidth="12"
              />
              {/* Card Badge on edge */}
              <foreignObject
                x={midX - 55}
                y={midY - 14}
                width="110"
                height="28"
                className="pointer-events-none"
              >
                <div className={`flex flex-col items-center justify-center ${styles.cardBg} px-2 py-0.5 rounded-full border ${styles.cardBorder} shadow-xs text-[10px] ${styles.cardText} font-mono scale-90 transition-all`}>
                  <span className="truncate max-w-[85px] leading-tight font-sans">{edge.label}</span>
                  <span className={`font-bold text-[8px] ${styles.cardTextMuted} leading-none`}>{edge.cardinality}</span>
                </div>
              </foreignObject>
            </g>
          );
        })}
      </svg>

      {/* Draw Nodes */}
      {nodes.map(node => {
        const originalObj = objectTypes.find(o => o.id === node.id);
        const domainId = originalObj?.domainId;
        const badgeClass = domainId === 'assets'
          ? domainBadge.assets
          : domainId === 'operations'
            ? domainBadge.operations
            : domainBadge.default;
        const domainLabel = domainId === 'assets'
          ? t('ontology.graph.domain.assets')
          : domainId === 'operations'
            ? t('ontology.graph.domain.operations')
            : t('ontology.graph.domain.business');

        return (
          <div
            key={node.id}
            style={{
              left: `${node.x}px`,
              top: `${node.y}px`,
              transform: 'translate(-50%, -50%)'
            }}
            className={`absolute flex flex-col items-center justify-center w-36 h-20 rounded-xl border-2 shadow-xs transition-shadow cursor-grab hover:shadow-md active:cursor-grabbing select-none z-20 focus:outline-none focus:ring-2 ${styles.accentRing} ${node.color}`}
            onMouseDown={(e) => handleMouseDown(e, node.id)}
            onClick={(e) => {
              // Only trigger click if we weren't dragging
              if (activeDragNodeId === null) {
                onSelectNode(node.id);
              }
            }}
            tabIndex={0}
            role="button"
            aria-label={t('ontology.graph.select_node_aria', { label: node.label })}
            onKeyDown={(e) => handleNodeKeyDown(e, node.id)}
          >
            {domainId && (
              <div className={`absolute -top-2 px-1.5 py-0.5 text-[8px] font-black tracking-wider rounded-md shadow-xs border ${badgeClass}`}>
                {domainLabel}
              </div>
            )}
            <div className="flex items-center gap-1.5 font-medium text-xs">
              <DynamicIcon name={node.icon} size={14} />
              <span>{node.label}</span>
            </div>
            <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-1 opacity-80`}>
              {t('ontology.graph.node_meta', { count: node.propertiesCount, id: node.id })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
