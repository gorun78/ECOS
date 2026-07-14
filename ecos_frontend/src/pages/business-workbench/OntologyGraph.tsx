/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { ObjectType, LinkType } from '../../types/ontology';
import LucideIcon from './LucideIcon';

interface Node {
  id: string;
  label: string;
  icon: string;
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
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  onSelectNode: (nodeId: string) => void;
  onSelectEdge: (edgeId: string) => void;
}

export default function OntologyGraph({
  objectTypes,
  linkTypes,
  onSelectNode,
  onSelectEdge
}: OntologyGraphProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [nodes, setNodes] = useState<Node[]>([]);
  const [edges, setEdges] = useState<Edge[]>([]);
  const [activeDragNodeId, setActiveDragNodeId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });

  // Initialize node positions in a nice default layout
  useEffect(() => {
    const layoutPositions: Record<string, { x: number; y: number }> = {
      flight: { x: 300, y: 220 },
      aircraft: { x: 550, y: 120 },
      airport: { x: 120, y: 220 },
      pilot: { x: 450, y: 350 }
    };

    const parsedNodes: Node[] = objectTypes.map((ot, idx) => {
      const position = layoutPositions[ot.id] || {
        x: 200 + (idx % 3) * 180,
        y: 150 + Math.floor(idx / 3) * 150
      };
      return {
        id: ot.id,
        label: ot.displayName,
        icon: ot.icon,
        color: ot.color,
        x: position.x,
        y: position.y,
        propertiesCount: ot.properties.length
      };
    });

    const parsedEdges: Edge[] = linkTypes.map(lt => ({
      id: lt.id,
      source: lt.sourceObjectType,
      target: lt.targetObjectType,
      label: lt.displayName,
      cardinality: lt.cardinality
    }));

    setNodes(parsedNodes);
    setEdges(parsedEdges);
  }, [objectTypes, linkTypes]);

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
              x: Math.max(40, Math.min(rect.width - 40, mouseX - dragOffset.x)),
              y: Math.max(40, Math.min(rect.height - 40, mouseY - dragOffset.y))
            }
          : n
      )
    );
  };

  const handleMouseUp = () => {
    setActiveDragNodeId(null);
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

  return (
    <div
      ref={containerRef}
      className="relative w-full h-[450px] border border-gray-200 rounded-lg bg-gray-50 overflow-hidden select-none cursor-default"
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
    >
      {/* Background Grid Accent */}
      <div className="absolute inset-0 opacity-[0.03] pointer-events-none" style={{
        backgroundImage: `radial-gradient(#1e293b 1px, transparent 1px)`,
        backgroundSize: '20px 20px'
      }} />

      <div className="absolute top-3 left-3 flex flex-col gap-1 bg-white/80 backdrop-blur-sm px-3 py-2 rounded border border-gray-200 shadow-xs z-10">
        <span className="text-xs font-semibold text-slate-800">本体画布 (Interactive Canvas)</span>
        <span className="text-[10px] text-slate-500">提示：可拖拽对象节点进行微调排版</span>
      </div>

      <svg className="w-full h-full pointer-events-none absolute inset-0">
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
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#94a3b8" />
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
            <g key={edge.id} className="group pointer-events-auto cursor-pointer" onClick={() => onSelectEdge(edge.id)}>
              <line
                x1={cx1}
                y1={cy1}
                x2={cx2}
                y2={cy2}
                stroke="#cbd5e1"
                strokeWidth="2"
                markerEnd="url(#arrow)"
                className="group-hover:stroke-blue-400 transition-colors"
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
                <div className="flex flex-col items-center justify-center bg-white px-2 py-0.5 rounded-full border border-slate-300 shadow-xs text-[10px] text-slate-600 font-mono scale-90 group-hover:border-blue-400 group-hover:text-blue-600 transition-all">
                  <span className="truncate max-w-[85px] leading-tight font-sans">{edge.label}</span>
                  <span className="font-bold text-[8px] text-slate-400 leading-none">{edge.cardinality}</span>
                </div>
              </foreignObject>
            </g>
          );
        })}
      </svg>

      {/* Draw Nodes */}
      {nodes.map(node => {
        const originalObj = objectTypes.find(o => o.id === node.id);
        const hasDomain = originalObj?.domainId;
        const bgClass = hasDomain === 'assets' ? 'bg-blue-600 border-blue-500' : hasDomain === 'operations' ? 'bg-purple-600 border-purple-500' : 'bg-slate-600 border-slate-500';
        const domainLabel = hasDomain === 'assets' ? '资产域' : hasDomain === 'operations' ? '运行域' : '业务域';

        return (
          <div
            key={node.id}
            style={{
              left: `${node.x}px`,
              top: `${node.y}px`,
              transform: 'translate(-50%, -50%)'
            }}
            className={`absolute flex flex-col items-center justify-center w-36 h-20 rounded-xl border-2 shadow-xs transition-shadow cursor-grab hover:shadow-md active:cursor-grabbing select-none z-20 ${node.color}`}
            onMouseDown={(e) => handleMouseDown(e, node.id)}
            onClick={(e) => {
              // Only trigger click if we weren't dragging
              if (activeDragNodeId === null) {
                onSelectNode(node.id);
              }
            }}
          >
            {hasDomain && (
              <div className={`absolute -top-2 px-1.5 py-0.5 text-[8px] font-black tracking-wider text-white rounded-md shadow-xs border ${bgClass}`}>
                {domainLabel}
              </div>
            )}
            <div className="flex items-center gap-1.5 font-medium text-xs">
              <LucideIcon name={node.icon} size={14} />
              <span>{node.label}</span>
            </div>
            <div className="text-[10px] text-slate-500 font-mono mt-1 opacity-80">
              {node.propertiesCount} 属性 · {node.id}
            </div>
          </div>
        );
      })}
    </div>
  );
}
