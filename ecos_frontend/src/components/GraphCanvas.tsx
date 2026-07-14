/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useRef, useEffect } from "react";
import { ZoomIn, ZoomOut, Maximize2, Search, Info, Sliders, Play, AlertCircle, CheckCircle, Tag, Database, Activity, Layout, Layers, X, MinusCircle } from "lucide-react";

interface Node {
  id: string;
  type: string; // source, dataset, pipeline, application, dashboard, ontology, goal, risk, metric, cause, effect
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
  /** Set of node IDs on the highlighted path */
  pathNodeIds?: Set<string>;
  /** Set of edge IDs on the highlighted path */
  pathEdgeIds?: Set<string>;
  /** Set of node IDs that have been expanded */
  expandedNodeIds?: Set<string>;
  /** Set of node IDs that are newly added */
  newlyAddedNodeIds?: Set<string>;
}

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

  // Auto-arrange nodes horizontally (Layer-by-Layer) on first mount
  useEffect(() => {
    // Determine ranks based on dependency tracking
    const ranks: Record<string, number> = {};
    const adj: Record<string, string[]> = {};
    const indegree: Record<string, number> = {};

    initialNodes.forEach((n) => {
      ranks[n.id] = 0;
      adj[n.id] = [];
      indegree[n.id] = 0;
    });

    links.forEach((l) => {
      if (adj[l.source]) {
        adj[l.source].push(l.target);
        indegree[l.target] = (indegree[l.target] || 0) + 1;
      }
    });

    // Topological sort approximation for layering
    const queue: string[] = [];
    const visited = new Set<string>();
    initialNodes.forEach((n) => {
      if (indegree[n.id] === 0) {
        queue.push(n.id);
        visited.add(n.id);
      }
    });

    while (queue.length > 0) {
      const u = queue.shift()!;
      adj[u]?.forEach((v) => {
        ranks[v] = Math.max(ranks[v], ranks[u] + 1);
        if (!visited.has(v)) {
          visited.add(v);
          queue.push(v);
        }
      });
    }

    // Distribute nodes in ranks
    const rankGroups: Record<number, string[]> = {};
    initialNodes.forEach((n) => {
      const r = ranks[n.id] || 0;
      if (!rankGroups[r]) rankGroups[r] = [];
      rankGroups[r].push(n.id);
    });

    // Compute coordinates
    const arrangedNodes = initialNodes.map((n) => {
      const r = ranks[n.id] || 0;
      const idx = rankGroups[r].indexOf(n.id);
      const totalInRank = rankGroups[r].length;

      // Spacing layout details
      const xSpacing = 240;
      const ySpacing = 110;
      const x = r * xSpacing + 60;
      const y = idx * ySpacing + (300 - (totalInRank * ySpacing) / 2) + 60;

      return {
        ...n,
        x: n.id === "customer_360" ? x + 20 : x, // Custom slight offset to make it organic!
        y: y
      };
    });

    setNodes(arrangedNodes);
  }, [initialNodes, links]);

  // Track dragging processes
  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    if (!interactive) return;
    e.stopPropagation();
    const node = nodes.find((n) => n.id === nodeId);
    if (!node) return;
    setDraggedNodeId(nodeId);
    setDragOffset({
      x: e.clientX - node.x,
      y: e.clientY - node.y
    });
    if (onSelectNode) onSelectNode(nodeId);
  };

  const handleNodeDoubleClick = (e: React.MouseEvent, nodeId: string) => {
    e.stopPropagation();
    if (onDoubleClickNode) onDoubleClickNode(nodeId);
  };

  const handleCanvasMouseDown = (e: React.MouseEvent) => {
    if (e.button === 0) { // Left click pans
      setIsPanning(true);
      setPanStart({
        x: e.clientX - panOffset.x,
        y: e.clientY - panOffset.y
      });
    }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (draggedNodeId) {
      setNodes((prev) =>
        prev.map((n) => {
          if (n.id === draggedNodeId) {
            return {
              ...n,
              x: e.clientX - dragOffset.x,
              y: e.clientY - dragOffset.y
            };
          }
          return n;
        })
      );
    } else if (isPanning) {
      setPanOffset({
        x: e.clientX - panStart.x,
        y: e.clientY - panStart.y
      });
    }
  };

  const handleMouseUp = () => {
    setDraggedNodeId(null);
    setIsPanning(false);
  };

  // Helper colors for Node Types
  const getNodeColorStyles = (nodeId: string, type: string, queryMatch: boolean) => {
    const isTranslucent = searchQuery && !queryMatch;
    const onPath = pathNodeIds.has(nodeId);
    const isNew = newlyAddedNodeIds.has(nodeId);
    const hasPathActive = pathNodeIds.size > 0;
    const dimmed = hasPathActive && !onPath;
    
    let baseOpacity = isTranslucent ? "opacity-35" : "opacity-100";
    if (dimmed) baseOpacity = "opacity-25";
    if (isNew) baseOpacity = "opacity-100";

    switch (type) {
      case "source":
        return {
          bg: `bg-amber-50 border-amber-200 text-amber-800 hover:border-amber-400 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-amber-100/50",
          icon: Database
        };
      case "dataset":
        return {
          bg: `bg-blue-50 border-blue-200 hover:border-blue-400 text-blue-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-blue-100/50",
          icon: Database
        };
      case "pipeline":
        return {
          bg: `bg-emerald-50 border-emerald-250 hover:border-emerald-400 text-emerald-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-emerald-100/50",
          icon: Activity
        };
      case "application":
        return {
          bg: `bg-purple-55 border-purple-200 hover:border-purple-400 text-purple-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-purple-100/50",
          icon: Layout
        };
      case "dashboard":
        return {
          bg: `bg-cyan-50 border-cyan-200 hover:border-cyan-400 text-cyan-800 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-cyan-100/50",
          icon: Layers
        };
      default:
        return {
          bg: `bg-slate-50 border-slate-200 hover:border-slate-400 text-slate-700 ${baseOpacity}`,
          glow: "shadow-2xs",
          iconBg: "bg-slate-100",
          icon: Info
        };
    }
  };

  // Recurse to discover all upstream & downstream nodes for high-tech glow!
  const getSubtreeHighlight = (selectedId: string | null) => {
    if (!selectedId) return { uppers: new Set(), downers: new Set() };
    
    const uppers = new Set<string>();
    const downers = new Set<string>();
    
    // Core recursive queues
    const crawlUp = (id: string) => {
      links.forEach((l) => {
        if (l.target === id && !uppers.has(l.source)) {
          uppers.add(l.source);
          crawlUp(l.source);
        }
      });
    };

    const crawlDown = (id: string) => {
      links.forEach((l) => {
        if (l.source === id && !downers.has(l.target)) {
          downers.add(l.target);
          crawlDown(l.target);
        }
      });
    };

    crawlUp(selectedId);
    crawlDown(selectedId);
    return { uppers, downers };
  };

  const { uppers, downers } = getSubtreeHighlight(selectedNodeId || null);

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
      {/* Topology controls overlay */}
      <div className="absolute top-3 left-3 z-10 flex items-center gap-2">
        <div className="flex items-center gap-2 px-3 py-1.5 bg-white/95 backdrop-blur-md rounded-xl border border-[#E2E8F0] shadow-sm">
          <Search className="w-3.5 h-3.5 text-slate-400" />
          <input
            type="text"
            className="text-[11px] bg-transparent border-0 outline-hidden text-slate-800 placeholder-slate-400 w-44 font-sans"
            placeholder="Search lineage node..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
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
        <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
        <span>DRAGGABLE SVG CANVAS</span>
      </div>

      <div className="absolute top-3 right-3 z-10 flex flex-col gap-1.5">
        <div className="flex flex-col rounded-xl border border-[#E2E8F0] bg-white p-1 shadow-sm">
          <button
            onClick={() => setZoom((z) => Math.min(z + 0.1, 1.8))}
            className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-50 rounded-lg cursor-pointer transition-all"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setZoom((z) => Math.max(z - 0.1, 0.5))}
            className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-50 rounded-lg cursor-pointer transition-all"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* SVG Container wrapping Node Renderers */}
      <svg
        className="w-full h-full cursor-grab active:cursor-grabbing"
        style={{
          transform: `translate(${panOffset.x}px, ${panOffset.y}px) scale(${zoom})`,
          transformOrigin: "0 0",
          transition: draggedNodeId ? "none" : "transform 0.15s ease-out"
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

        {/* 1. Connecting Link lines */}
        {links.map((link) => {
          const sourceNode = nodes.find((n) => n.id === link.source);
          const targetNode = nodes.find((n) => n.id === link.target);
          if (!sourceNode || !targetNode) return null;

          const isSelectedPath =
            selectedNodeId === link.source ||
            selectedNodeId === link.target ||
            (uppers.has(link.source) && uppers.has(link.target)) ||
            (downers.has(link.source) && downers.has(link.target)) ||
            (link.source === selectedNodeId && downers.has(link.target)) ||
            (link.target === selectedNodeId && uppers.has(link.source));

          const isPathEdge = pathEdgeIds.has(link.id);
          const hasPathActive = pathEdgeIds.size > 0;
          const dimmed = hasPathActive && !isPathEdge;

          // Draw bezier curve paths
          const x1 = sourceNode.x + 190;
          const y1 = sourceNode.y + 35;
          const x2 = targetNode.x;
          const y2 = targetNode.y + 35;

          const controlX1 = x1 + 100;
          const controlY1 = y1;
          const controlX2 = x2 - 100;
          const controlY2 = y2;

          const pathD = `M ${x1} ${y1} C ${controlX1} ${controlY1}, ${controlX2} ${controlY2}, ${x2} ${y2}`;

          const edgeStroke = isPathEdge ? "#EF4444" : isSelectedPath ? "#3B82F6" : dimmed ? "#E2E8F0" : "#CBD5E1";
          const edgeWidth = isPathEdge ? 3 : isSelectedPath ? 2.5 : dimmed ? 0.8 : 1.5;
          const edgeOpacity = dimmed ? 0.3 : 1;

          return (
            <g key={link.id} opacity={edgeOpacity}>
              {/* Backing thicker line for selection glows */}
              <path
                d={pathD}
                fill="none"
                stroke={edgeStroke}
                strokeWidth={edgeWidth}
                className="transition duration-150"
                markerEnd={isPathEdge ? "url(#arrow-path)" : isSelectedPath ? "url(#arrow-active)" : "url(#arrow)"}
              />
              {/* Animated dash particles flying across links */}
              {(isSelectedPath || isPathEdge) && (
                <path
                  d={pathD}
                  fill="none"
                  stroke={isPathEdge ? "#EF4444" : "#3B82F6"}
                  strokeWidth={2}
                  strokeDasharray="6 24"
                  strokeDashoffset={0}
                  className="animate-svg-dash"
                />
              )}
            </g>
          );
        })}

        {/* 2. Interactive SVG/HTML node blocks */}
        {nodes.map((node) => {
          const isSelected = selectedNodeId === node.id;
          const queryMatch = searchQuery ? node.label.toLowerCase().includes(searchQuery.toLowerCase()) : true;
          
          // Upstream & Downstream glow state triggers
          const isUpstream = uppers.has(node.id);
          const isDownstream = downers.has(node.id);
          const onPath = pathNodeIds.has(node.id);
          const isNew = newlyAddedNodeIds.has(node.id);
          const isExpanded = expandedNodeIds.has(node.id);

          const style = getNodeColorStyles(node.id, node.type, queryMatch);
          const Icon = style.icon;

          // Determine border classes
          let borderClass = "";
          if (onPath) {
            borderClass = "border-red-500 ring-2 ring-red-400/40";
          } else if (isSelected) {
            borderClass = "border-indigo-600 ring-2 ring-indigo-650/15";
          } else if (isUpstream) {
            borderClass = "border-amber-400";
          } else if (isDownstream) {
            borderClass = "border-cyan-400";
          }

          // New node gets light green bg
          const newBgClass = isNew ? "bg-green-50 border-green-300" : "";

          return (
            <foreignObject
              key={node.id}
              x={node.x}
              y={node.y}
              width={190}
              height={70}
              onMouseDown={(e) => handleNodeMouseDown(e, node.id)}
              onDoubleClick={(e) => handleNodeDoubleClick(e, node.id)}
              className="cursor-pointer"
            >
              <div
                className={`w-[184px] h-[64px] border rounded-xl px-3.5 py-2.5 text-left flex flex-col justify-between transition-all shadow-2xs ${
                  newBgClass || style.bg
                } ${style.glow} ${borderClass}`}
              >
                {/* Header Node Info */}
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
                  {/* Collapse button for expanded nodes */}
                  {isExpanded && onCollapseNode && (
                    <button
                      onMouseDown={(e) => { e.stopPropagation(); }}
                      onClick={(e) => { e.stopPropagation(); onCollapseNode(node.id); }}
                      className="shrink-0 p-0.5 rounded-full hover:bg-red-100 text-red-400 hover:text-red-600 transition"
                      title="Collapse neighbors"
                    >
                      <MinusCircle className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>

                {/* Status indicator bottom bar */}
                <div className="flex items-center justify-between text-[9px] font-mono text-slate-500 mt-1 first-letter:uppercase leading-none">
                  <span className="truncate">{node.rows || "Active Link"}</span>
                  <div className="flex items-center gap-1">
                    <span
                      className={`w-1.5 h-1.5 rounded-full ${
                        isNew ? "bg-green-500" :
                        node.status === "Healthy" || node.status === "Success"
                          ? "bg-green-500 font-bold"
                          : node.status === "Warning" || node.status === "Degraded"
                          ? "bg-amber-500 font-bold"
                          : node.status === "Failed"
                          ? "bg-red-500 font-bold"
                          : "bg-blue-500"
                      }`}
                    ></span>
                    <span className="text-[9px] text-slate-400 font-bold shrink-0">{isNew ? "New" : node.status || "Ready"}</span>
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
