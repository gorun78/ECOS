/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO-26 T1: Force-directed Graph Canvas
 * 力导向布局 + 1-hop neighbour highlight + drag snap-back + focusNodeId centering
 */

import React, { useState, useRef, useEffect, useCallback } from "react";
import { ZoomIn, ZoomOut, Maximize2, Search, Info, Database, Activity, Layout, Layers } from "lucide-react";

interface Node {
  id: string;
  type: string;
  label: string;
  status?: string;
  owner?: string;
  updatedAt?: string;
  rows?: string;
  properties?: Record<string, any>;
  idX?: string;
}

interface Link {
  id: string;
  source: string;
  target: string;
  animated?: boolean;
}

interface GraphCanvasProps {
  nodes: Node[];
  links: Link[];
  onSelectNode?: (nodeId: string | null) => void;
  onDoubleClickNode?: (nodeId: string) => void;
  onCollapseNode?: (nodeId: string) => void;
  selectedNodeId?: string | null;
  interactive?: boolean;
  pathNodeIds?: Set<string>;
  pathEdgeIds?: Set<string>;
  expandedNodeIds?: Set<string>;
  newlyAddedNodeIds?: Set<string>;
  /** When set, pans/zooms to center on this node */
  focusNodeId?: string | null;
}

// ── Force simulation constants ──
const REPULSION = 6000;
const ATTRACTION = 0.003;
const DAMPING = 0.65;
const IDEAL_EDGE_LEN = 180;
const MAX_SIM_ITER = 200;

export default function GraphCanvas({
  nodes: initialNodes,
  links,
  onSelectNode,
  onDoubleClickNode,
  onCollapseNode,
  selectedNodeId,
  interactive = true,
  pathNodeIds = new Set(),
  pathEdgeIds = new Set(),
  expandedNodeIds = new Set(),
  newlyAddedNodeIds = new Set(),
  focusNodeId,
}: GraphCanvasProps) {
  const [nodes, setNodes] = useState<any[]>([]);
  const [draggedNodeId, setDraggedNodeId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [searchQuery, setSearchQuery] = useState("");
  const [zoom, setZoom] = useState(1);
  const [panOffset, setPanOffset] = useState({ x: 20, y: 20 });
  const [isPanning, setIsPanning] = useState(false);
  const [panStart, setPanStart] = useState({ x: 0, y: 0 });

  const canvasRef = useRef<HTMLDivElement>(null);
  const simTimerRef = useRef<number | null>(null);
  const prevNodeIdsRef = useRef<string>("");
  const manMovedRef = useRef<Set<string>>(new Set());

  // ── 1-hop neighbours ──
  const getOneHopNeighbors = useCallback((nodeId: string | null | undefined): Set<string> => {
    if (!nodeId) return new Set();
    const set = new Set<string>();
    set.add(nodeId);
    links.forEach(l => {
      if (l.source === nodeId) set.add(l.target);
      if (l.target === nodeId) set.add(l.source);
    });
    return set;
  }, [links]);

  const oneHopNeighbors = getOneHopNeighbors(selectedNodeId);
  const hasSelection = !!selectedNodeId && oneHopNeighbors.size > 1;

  // ── Force-directed layout ──
  const runForceSimulation = useCallback((seedNodes: any[], seedLinks: Link[]) => {
    if (simTimerRef.current) {
      clearTimeout(simTimerRef.current);
      simTimerRef.current = null;
    }

    if (seedNodes.length === 0) {
      setNodes([]);
      return;
    }

    // Initialize positions
    interface Pos { id: string; x: number; y: number }
    const posMap: Pos[] = seedNodes.map((n, i) => ({
      id: n.id,
      x: n.x ?? 200 + (i % 8) * 160 + Math.random() * 40,
      y: n.y ?? 100 + Math.floor(i / 8) * 120 + Math.random() * 40,
    }));

    const velMap = new Map<string, { vx: number; vy: number }>();
    posMap.forEach(p => velMap.set(p.id, { vx: 0, vy: 0 }));

    // Sync initial positions to state
    setNodes(seedNodes.map(n => {
      const p = posMap.find(pp => pp.id === n.id);
      return p ? { ...n, x: p.x, y: p.y } : n;
    }));

    let iter = 0;

    const tick = () => {
      if (iter >= MAX_SIM_ITER) {
        simTimerRef.current = null;
        return;
      }

      // Repulsion (all-pairs)
      for (let i = 0; i < posMap.length; i++) {
        for (let j = i + 1; j < posMap.length; j++) {
          const a = posMap[i], b = posMap[j];
          const dx = b.x - a.x, dy = b.y - a.y;
          const dist = Math.sqrt(dx * dx + dy * dy) || 1;
          const force = REPULSION / (dist * dist);
          const fx = (dx / dist) * force, fy = (dy / dist) * force;
          const va = velMap.get(a.id)!, vb = velMap.get(b.id)!;
          va.vx -= fx; va.vy -= fy;
          vb.vx += fx; vb.vy += fy;
        }
      }

      // Edge attraction
      seedLinks.forEach(l => {
        const a = posMap.find(p => p.id === l.source);
        const b = posMap.find(p => p.id === l.target);
        if (!a || !b) return;
        const dx = b.x - a.x, dy = b.y - a.y;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const force = (dist - IDEAL_EDGE_LEN) * ATTRACTION;
        const fx = (dx / dist) * force, fy = (dy / dist) * force;
        const va = velMap.get(a.id)!, vb = velMap.get(b.id)!;
        va.vx += fx; va.vy += fy;
        vb.vx -= fx; vb.vy -= fy;
      });

      // Apply velocities with damping
      posMap.forEach(p => {
        const v = velMap.get(p.id)!;
        v.vx *= DAMPING;
        v.vy *= DAMPING;
        p.x += v.vx;
        p.y += v.vy;
      });

      // Map back to nodes state
      setNodes(prev =>
        prev.map(n => {
          const p = posMap.find(pp => pp.id === n.id);
          if (!p) return n;
          // Don't overwrite manually-dragged nodes mid-sim
          if (manMovedRef.current.has(n.id)) return n;
          return { ...n, x: p.x, y: p.y };
        })
      );

      iter++;
      simTimerRef.current = window.setTimeout(tick, 16);
    };

    tick();
  }, []);

  // Run force layout when nodes change (by ID set)
  useEffect(() => {
    const idsKey = initialNodes.map(n => n.id).sort().join(",");
    if (idsKey !== prevNodeIdsRef.current) {
      prevNodeIdsRef.current = idsKey;
      manMovedRef.current.clear();
      runForceSimulation(initialNodes, links);
    }
  }, [initialNodes, links, runForceSimulation]);

  // ── Focus on node ──
  useEffect(() => {
    if (!focusNodeId || !canvasRef.current) return;
    const target = nodes.find(n => n.id === focusNodeId);
    if (!target) return;
    const rect = canvasRef.current.getBoundingClientRect();
    const newZoom = 1.2;
    setZoom(newZoom);
    setPanOffset({
      x: rect.width / 2 - target.x * newZoom,
      y: rect.height / 2 - target.y * newZoom,
    });
    if (onSelectNode) onSelectNode(focusNodeId);
  }, [focusNodeId]);

  // ── Drag handlers ──
  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    if (!interactive) return;
    e.stopPropagation();
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;
    manMovedRef.current.add(nodeId);
    setDraggedNodeId(nodeId);
    setDragOffset({ x: e.clientX - node.x, y: e.clientY - node.y });
    if (onSelectNode) onSelectNode(nodeId);
  };

  const handleNodeDoubleClick = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    if (onDoubleClickNode) onDoubleClickNode(nodeId);
  };

  const handleCanvasMouseDown = (e: React.MouseEvent) => {
    if (e.button === 0) {
      setIsPanning(true);
      setPanStart({ x: e.clientX - panOffset.x, y: e.clientY - panOffset.y });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (draggedNodeId) {
      setNodes(prev =>
        prev.map(n => {
          if (n.id === draggedNodeId) {
            return {
              ...n,
              x: e.clientX - dragOffset.x,
              y: e.clientY - dragOffset.y,
            };
          }
          return n;
        })
      );
    } else if (isPanning) {
      setPanOffset({ x: e.clientX - panStart.x, y: e.clientY - panStart.y });
    }
  };

  // On mouse up: release drag, restart force sim for snap-back
  const handleMouseUp = useCallback(() => {
    if (draggedNodeId) {
      manMovedRef.current.delete(draggedNodeId);
      // Restart simulation so the released node snaps back
      const currentNodes = nodes;
      runForceSimulation(currentNodes, links);
    }
    setDraggedNodeId(null);
    setIsPanning(false);
  }, [draggedNodeId, nodes, links, runForceSimulation]);

  // ── Node color styles ──
  const getNodeColorStyles = (nodeId: string, type: string) => {
    const isNew = newlyAddedNodeIds.has(nodeId);

    let baseOpacity = "opacity-100";
    if (hasSelection && !oneHopNeighbors.has(nodeId)) {
      baseOpacity = "opacity-30";
    }

    switch (type) {
      case "source":
        return {
          bg: `bg-amber-50 border-amber-200 text-amber-800 hover:border-amber-400 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-amber-100/50",
          icon: Database,
        };
      case "dataset":
        return {
          bg: `bg-blue-50 border-blue-200 hover:border-blue-400 text-blue-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-blue-100/50",
          icon: Database,
        };
      case "pipeline":
        return {
          bg: `bg-emerald-50 border-emerald-250 hover:border-emerald-400 text-emerald-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-emerald-100/50",
          icon: Activity,
        };
      case "application":
        return {
          bg: `bg-purple-55 border-purple-200 hover:border-purple-400 text-purple-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-purple-100/50",
          icon: Layout,
        };
      case "dashboard":
        return {
          bg: `bg-cyan-50 border-cyan-200 hover:border-cyan-400 text-cyan-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-cyan-100/50",
          icon: Layers,
        };
      default:
        return {
          bg: `bg-slate-50 border-slate-200 hover:border-slate-400 text-slate-700 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-slate-100",
          icon: Info,
        };
    }
  };

  const handleFitView = () => {
    setZoom(0.95);
    setPanOffset({ x: 30, y: 30 });
  };

  return (
    <div
      ref={canvasRef}
      className="relative flex-1 bg-slate-50 border border-slate-200 rounded-xl overflow-hidden select-none"
      onMouseDown={handleCanvasMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
    >
      {/* Search overlay */}
      <div className="absolute top-3 left-3 z-10 flex items-center gap-2">
        <div className="flex items-center gap-2 px-3 py-1.5 bg-white/95 backdrop-blur-md rounded-xl border border-[#E2E8F0] shadow-sm">
          <Search className="w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            className="text-[11px] bg-transparent border-0 outline-hidden text-slate-800 placeholder-slate-400 w-44 font-sans"
            placeholder="Search lineage node..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
          />
        </div>

        <button
          onClick={handleFitView}
          className="p-2 bg-white text-slate-550 hover:bg-slate-50 hover:text-slate-800 rounded-xl border border-[#E2E8F0] shadow-xs cursor-pointer transition"
          title="Fit View"
        >
          <Maximize2 className="w-3.5 h-3.5" />
        </button>
      </div>

      <div className="absolute bottom-3 left-3 z-10 flex items-center gap-1.5 bg-white/95 border border-[#E2E8F0] px-2.5 py-1 rounded-lg text-[10px] font-mono text-slate-450 shadow-xs">
        <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
        <span>FORCE-DIRECTED CANVAS</span>
      </div>

      {/* Zoom controls */}
      <div className="absolute top-3 right-3 z-10 flex flex-col gap-1.5">
        <div className="flex flex-col rounded-xl border border-[#E2E8F0] bg-white p-1 shadow-sm">
          <button
            onClick={() => setZoom(z => Math.min(z + 0.1, 1.8))}
            className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-50 rounded-lg cursor-pointer transition-all"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setZoom(z => Math.max(z - 0.1, 0.5))}
            className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-50 rounded-lg cursor-pointer transition-all"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* SVG Graph */}
      <svg
        className="w-full h-full cursor-grab active:cursor-grabbing"
        style={{
          transform: `translate(${panOffset.x}px, ${panOffset.y}px) scale(${zoom})`,
          transformOrigin: "0 0",
          transition: draggedNodeId ? "none" : "transform 0.15s ease-out",
        }}
      >
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="17" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#CBD5E1" />
          </marker>
          <marker id="arrow-active" viewBox="0 0 10 10" refX="17" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#3b82f6" />
          </marker>
          <marker id="arrow-path" viewBox="0 0 10 10" refX="17" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="#EF4444" />
          </marker>
        </defs>

        {/* Edges */}
        {links.map(link => {
          const sourceNode = nodes.find(n => n.id === link.source);
          const targetNode = nodes.find(n => n.id === link.target);
          if (!sourceNode || !targetNode) return null;

          const isPathEdge = pathEdgeIds.has(link.id);
          const hasPathActive = pathEdgeIds.size > 0;
          const dimmed = hasPathActive && !isPathEdge;

          const x1 = sourceNode.x + 190;
          const y1 = sourceNode.y + 35;
          const x2 = targetNode.x;
          const y2 = targetNode.y + 35;
          const cx1 = x1 + 100, cy1 = y1;
          const cx2 = x2 - 100, cy2 = y2;
          const pathD = `M ${x1} ${y1} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${x2} ${y2}`;

          const edgeStroke = isPathEdge ? "#EF4444" : "#CBD5E1";
          const edgeWidth = isPathEdge ? 3 : dimmed ? 0.8 : 1.5;
          const edgeOpacity = dimmed ? 0.3 : 1;

          return (
            <g key={link.id} opacity={edgeOpacity}>
              <path
                d={pathD}
                fill="none"
                stroke={edgeStroke}
                strokeWidth={edgeWidth}
                className="transition duration-150"
                markerEnd={isPathEdge ? "url(#arrow-path)" : "url(#arrow)"}
              />
              {isPathEdge && (
                <path
                  d={pathD}
                  fill="none"
                  stroke="#EF4444"
                  strokeWidth={2}
                  strokeDasharray="6 24"
                  strokeDashoffset={0}
                  className="animate-svg-dash"
                />
              )}
            </g>
          );
        })}

        {/* Nodes */}
        {nodes.map(node => {
          const isSelected = selectedNodeId === node.id;
          const onPath = pathNodeIds.has(node.id);
          const isNew = newlyAddedNodeIds.has(node.id);
          const isExpanded = expandedNodeIds.has(node.id);

          const style = getNodeColorStyles(node.id, node.type);
          const Icon = style.icon;

          let borderClass = "";
          if (onPath) {
            borderClass = "border-red-500 ring-2 ring-red-400/40";
          } else if (isSelected) {
            borderClass = "border-indigo-600 ring-2 ring-indigo-650/15";
          }

          const newBgClass = isNew ? "bg-green-50 border-green-300" : "";

          return (
            <foreignObject
              key={node.id}
              x={node.x}
              y={node.y}
              width={190}
              height={70}
              onMouseDown={e => handleNodeMouseDown(e, node.id)}
              onDoubleClick={e => handleNodeDoubleClick(e, node.id)}
              className="cursor-pointer"
            >
              <div
                className={`w-[184px] h-[64px] border rounded-xl px-3.5 py-2.5 text-left flex flex-col justify-between transition-all shadow-2xs ${
                  newBgClass || style.bg
                } ${style.glow} ${borderClass}`}
              >
                <div className="flex items-center gap-2 min-w-0">
                  <div className={`p-1 rounded bg-white border border-[#E2E8F0] shrink-0 ${isNew ? "text-green-600" : "text-slate-550"}`}>
                    <Icon className="w-3.5 h-3.5 text-current shrink-0" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <span className="text-[11px] font-bold block truncate leading-tight tracking-wide text-slate-800">
                      {node.label}
                    </span>
                    <span className="text-[8.5px] uppercase font-mono text-slate-400 block leading-none mt-1 font-bold">
                      {node.type}
                    </span>
                  </div>
                </div>

                <div className="flex items-center justify-between text-[9px] font-mono text-slate-500 mt-1 first-letter:uppercase leading-none">
                  <span className="truncate">{node.rows || "Active Link"}</span>
                  <div className="flex items-center gap-1">
                    <span
                      className={`w-1.5 h-1.5 rounded-full ${
                        isNew ? "bg-green-500"
                        : node.status === "Healthy" || node.status === "Success" ? "bg-green-500 font-bold"
                        : node.status === "Warning" || node.status === "Degraded" ? "bg-amber-500 font-bold"
                        : node.status === "Failed" ? "bg-red-500 font-bold"
                        : "bg-blue-500"
                      }`}
                    />
                    <span className="text-[9px] text-slate-400 font-bold shrink-0">
                      {isNew ? "New" : node.status || "Ready"}
                    </span>
                  </div>
                </div>
              </div>
            </foreignObject>
          );
        })}
      </svg>
    </div>
  );
}
