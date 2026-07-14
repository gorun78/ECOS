/**
 * DomainGraphCanvas — 领域本体画布容器
 *
 * ReactFlow 画布容器，整合自定义 EntityNode 和 RelationshipEdge，
 * 提供可编辑的拖拽连线式本体建模能力。
 *
 * 功能:
 * - 从 useWorkbenchStore 获取 entities + relationships → 渲染画布
 * - 双击空白弹出 CreateEntityModal（通过回调）
 * - 拖拽连线创建关系（通过 store.createRelationship）
 * - 单击节点选中实体（store.selectEntity）
 * - 集成 CanvasToolbar 工具栏
 *
 * 对应设计文档第5章组件树中的 WorkbenchCanvas。
 *
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useRef } from "react";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType,
  type Connection,
  type Node,
  type Edge,
  type FitViewOptions,
  type OnConnectStart,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import EntityNode from "./nodes/EntityNode";
import RelationshipEdge from "./edges/RelationshipEdge";
import CanvasToolbar from "./CanvasToolbar";

import { useWorkbenchStore } from "../../stores/useWorkbenchStore";
import { mapEntitiesToFlow, applyForceLayout } from "../../adapters/flowAdapter";
import type { EntityNodeData } from "../../adapters/flowAdapter";
import type { RelationshipEdgeData } from "../../adapters/flowAdapter";

// ── Props ────────────────────────────────────────────────────

export interface DomainGraphCanvasProps {
  /** 当前域名称（显示在工具栏） */
  domainName?: string;
  /** 回调：双击空白 → 弹出添加实体对话框 */
  onDoubleClickPane?: () => void;
  /** 回调：请求添加实体（工具栏按钮） */
  onRequestAddEntity?: () => void;
  /** 是否只读模式（隐藏添加按钮，禁用连线） */
  readOnly?: boolean;
  /** 画布容器的额外 className */
  className?: string;
}

// ── 自定义节点/边类型注册 ────────────────────────────────────

const nodeTypes = {
  entityNode: EntityNode,
};

const edgeTypes = {
  relationshipEdge: RelationshipEdge,
};

// ── FitView 配置 ─────────────────────────────────────────────

const FIT_VIEW_OPTIONS: FitViewOptions = {
  padding: 0.3,
  duration: 300,
  maxZoom: 1.5,
};

// ── 默认边样式 ───────────────────────────────────────────────

const defaultEdgeOptions = {
  type: "relationshipEdge",
  animated: false,
  style: { stroke: "#334155", strokeWidth: 1.5 },
  markerEnd: {
    type: MarkerType.ArrowClosed,
    color: "#334155",
    width: 14,
    height: 14,
  },
};

// ── MiniMap 样式 ─────────────────────────────────────────────

const miniMapStyle = {
  backgroundColor: "#0f1117",
  maskColor: "rgba(30, 41, 59, 0.6)",
  nodeColor: (node: Node) => {
    const type = (node.data as EntityNodeData)?.entityType || "MASTER";
    const colorMap: Record<string, string> = {
      MASTER: "#818cf8", // indigo-400
      TRANSACTION: "#34d399", // emerald-400
      EVENT: "#fbbf24", // amber-400
      REFERENCE: "#a855f7", // purple-400
    };
    return colorMap[type] || "#94a3b8";
  },
};

// ── 主组件 ──────────────────────────────────────────────────

export default function DomainGraphCanvas({
  domainName,
  onDoubleClickPane,
  onRequestAddEntity,
  readOnly = false,
  className = "",
}: DomainGraphCanvasProps) {
  // ── 从 Store 获取状态 ──
  const entities = useWorkbenchStore((s) => s.entities);
  const relationships = useWorkbenchStore((s) => s.relationships);
  const storeCanvasNodes = useWorkbenchStore((s) => s.canvasNodes);
  const storeCanvasEdges = useWorkbenchStore((s) => s.canvasEdges);
  const selectedEntityId = useWorkbenchStore((s) => s.selectedEntityId);
  const domain = useWorkbenchStore((s) => s.currentDomain());

  const selectEntity = useWorkbenchStore((s) => s.selectEntity);
  const createRelationship = useWorkbenchStore((s) => s.createRelationship);
  const syncCanvasFromData = useWorkbenchStore((s) => s.syncCanvasFromData);

  // ── ReactFlow 本地状态 ──
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<EntityNodeData>>(
    storeCanvasNodes as Node<EntityNodeData>[]
  );
  const [edges, setEdges, onEdgesChange] = useEdgesState<
    Edge<RelationshipEdgeData>
  >(storeCanvasEdges as Edge<RelationshipEdgeData>[]);

  // ── 画布引用 ──
  const flowRef = useRef<HTMLDivElement>(null);

  // ── 当前缩放级别（通过 onMove 追踪） ──
  const [currentZoom, setCurrentZoom] = React.useState(1);

  // ══════════════════════════════════════════════════════════
  // 数据同步：当 store 中的 canvasNodes/canvasEdges 变更时，
  // 同步到 ReactFlow 的本地节点/边状态
  // ══════════════════════════════════════════════════════════
  useEffect(() => {
    setNodes(storeCanvasNodes as Node<EntityNodeData>[]);
  }, [storeCanvasNodes, setNodes]);

  useEffect(() => {
    setEdges(storeCanvasEdges as Edge<RelationshipEdgeData>[]);
  }, [storeCanvasEdges, setEdges]);

  // ══════════════════════════════════════════════════════════
  // 事件处理
  // ══════════════════════════════════════════════════════════

  /** 单击节点 → 选中实体 */
  const handleNodeClick = useCallback(
    (_event: React.MouseEvent, node: Node) => {
      selectEntity(node.id);
    },
    [selectEntity]
  );

  /** 双击画布空白 → 弹出添加实体对话框 */
  const handlePaneClick = useCallback(
    (event: React.MouseEvent) => {
      if (event.detail === 2) {
        // 双击检测
        onDoubleClickPane?.();
      }
    },
    [onDoubleClickPane]
  );

  /** 拖拽连线 → 创建关系 */
  const handleConnect = useCallback(
    (connection: Connection) => {
      if (readOnly || !connection.source || !connection.target) return;
      if (connection.source === connection.target) return;
      // 检查是否已存在同方向连线
      const exists = edges.some(
        (e) => e.source === connection.source && e.target === connection.target
      );
      if (exists) return;

      // 1. 先添加临时边到画布
      const newEdge: Edge<RelationshipEdgeData> = {
        id: `temp-${Date.now()}`,
        source: connection.source,
        target: connection.target,
        type: "relationshipEdge",
        data: {
          code: "",
          name: "新关系",
          relationshipType: "RELATED_TO",
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: "#334155",
          width: 14,
          height: 14,
        },
      };

      setEdges((prev) => addEdge(newEdge, prev));

      // 2. 通过 store 创建关系（异步调用 API）
      createRelationship({
        sourceEntityId: connection.source,
        targetEntityId: connection.target,
        code: `rel_${connection.source}_${connection.target}`,
        name: `关联`,
        relationshipType: "RELATED_TO",
      }).catch((err) => {
        // 创建失败时移除临时边
        setEdges((prev) =>
          prev.filter((e) => e.id !== newEdge.id)
        );
        console.warn("创建关系失败:", err);
      });
    },
    [readOnly, edges, createRelationship, setEdges]
  );

  /** 连线开始 — 可用于高亮有效目标 */
  const handleConnectStart: OnConnectStart = useCallback(
    (_event, { nodeId }) => {
      // 预留扩展：高亮合法连接目标节点
    },
    []
  );

  /** 监听边的删除事件（来自 RelationshipEdge 的自定义事件） */
  useEffect(() => {
    const handler = (e: Event) => {
      const customEvent = e as CustomEvent<{ edgeId: string }>;
      const edgeId = customEvent.detail?.edgeId;
      if (!edgeId) return;

      // 尝试通过 store 删除关系
      useWorkbenchStore.getState().deleteRelationship(edgeId).catch((err) => {
        console.warn("删除关系失败:", err);
      });
    };

    document.addEventListener("relationship-edge-delete", handler);
    return () => {
      document.removeEventListener("relationship-edge-delete", handler);
    };
  }, []);

  // ══════════════════════════════════════════════════════════
  // 工具栏操作回调
  // ══════════════════════════════════════════════════════════

  /** 自适应视图 */
  const handleFitView = useCallback(() => {
    // ReactFlow 的 fitView 方法通过 DOM 访问，使用存储的 viewport 重置
    syncCanvasFromData();
  }, [syncCanvasFromData]);

  /** 力导向布局 */
  const handleForceLayout = useCallback(() => {
    const currentNodes = nodes.length > 0 ? nodes : storeCanvasNodes;
    const currentEdges = edges.length > 0 ? edges : storeCanvasEdges;

    const laidOut = applyForceLayout(
      currentNodes as Node<EntityNodeData>[],
      currentEdges as Edge<RelationshipEdgeData>[],
      60
    );
    setNodes(laidOut);
  }, [nodes, edges, storeCanvasNodes, storeCanvasEdges, setNodes]);

  /** 层级布局: 使用 BFS 按入度分层 */
  const handleHierarchicalLayout = useCallback(() => {
    const currentNodes = nodes.length > 0 ? nodes : storeCanvasNodes;
    const currentEdges = edges.length > 0 ? edges : storeCanvasEdges;

    // 计算入度
    const indegree = new Map<string, number>();
    const adj = new Map<string, string[]>();

    currentNodes.forEach((n) => {
      indegree.set(n.id, 0);
      adj.set(n.id, []);
    });

    (currentEdges as Edge<RelationshipEdgeData>[]).forEach((e) => {
      adj.get(e.source)?.push(e.target);
      indegree.set(e.target, (indegree.get(e.target) || 0) + 1);
    });

    // BFS 分层
    const layers: string[][] = [];
    const visited = new Set<string>();
    const layerMap = new Map<string, number>();

    // 从所有入度为 0 的节点开始
    const queue: string[] = [];
    indegree.forEach((deg, id) => {
      if (deg === 0) {
        queue.push(id);
        layerMap.set(id, 0);
      }
    });

    while (queue.length > 0) {
      const id = queue.shift()!;
      if (visited.has(id)) continue;
      visited.add(id);

      const layer = layerMap.get(id) || 0;
      while (layers.length <= layer) layers.push([]);
      layers[layer].push(id);

      (adj.get(id) || []).forEach((neighbor) => {
        if (!visited.has(neighbor)) {
          layerMap.set(neighbor, layer + 1);
          queue.push(neighbor);
        }
      });
    }

    // 未访问到的节点放在最后
    currentNodes.forEach((n) => {
      if (!visited.has(n.id)) {
        const lastLayer = layers.length;
        if (layers.length <= lastLayer) layers.push([]);
        layers[lastLayer].push(n.id);
      }
    });

    // 计算坐标
    const H_GAP = 260;
    const V_GAP = 150;
    const START_X = 50;
    const START_Y = 50;

    const positionMap = new Map<string, { x: number; y: number }>();
    layers.forEach((layer, layerIdx) => {
      layer.forEach((nodeId, nodeIdx) => {
        positionMap.set(nodeId, {
          x: START_X + layerIdx * H_GAP,
          y: START_Y + nodeIdx * V_GAP,
        });
      });
    });

    setNodes(
      currentNodes.map((node) => ({
        ...node,
        position: positionMap.get(node.id) || { x: 0, y: 0 },
      }))
    );
  }, [nodes, edges, storeCanvasNodes, storeCanvasEdges, setNodes]);

  /** 圆形布局 */
  const handleCircularLayout = useCallback(() => {
    const currentNodes = nodes.length > 0 ? nodes : storeCanvasNodes;
    const count = currentNodes.length;
    if (count === 0) return;

    const cx = 400;
    const cy = 300;
    const radius = Math.max(200, count * 30);

    setNodes(
      currentNodes.map((node, i) => {
        const angle = (2 * Math.PI * i) / count - Math.PI / 2;
        return {
          ...node,
          position: {
            x: cx + radius * Math.cos(angle),
            y: cy + radius * Math.sin(angle),
          },
        };
      })
    );
  }, [nodes, storeCanvasNodes, setNodes]);

  /** 处理布局切换 */
  const handleLayoutChange = useCallback(
    (layout: "force" | "hierarchical" | "circular") => {
      switch (layout) {
        case "force":
          handleForceLayout();
          break;
        case "hierarchical":
          handleHierarchicalLayout();
          break;
        case "circular":
          handleCircularLayout();
          break;
      }
    },
    [handleForceLayout, handleHierarchicalLayout, handleCircularLayout]
  );

  // ══════════════════════════════════════════════════════════
  // 计算显示名称 + 统计
  // ══════════════════════════════════════════════════════════

  const displayDomainName = domainName || domain?.name;

  // 统计（优先用 store 数据，次用画布状态）
  const entityStats = entities.length || storeCanvasNodes.length;
  const relationshipStats = relationships.length || storeCanvasEdges.length;

  // ══════════════════════════════════════════════════════════
  // 渲染
  // ══════════════════════════════════════════════════════════

  return (
    <div
      className={`
        relative flex flex-col w-full h-full
        bg-[#0f1117]
        ${className}
      `}
    >
      {/* ── 顶部工具栏 ── */}
      <div className="absolute top-3 left-3 z-10">
        <CanvasToolbar
          domainName={displayDomainName}
          entityCount={entityStats}
          relationshipCount={relationshipStats}
          onAddEntity={onRequestAddEntity}
          onLayoutChange={handleLayoutChange}
          onZoomIn={() => setCurrentZoom((z) => Math.min(z + 0.1, 2))}
          onZoomOut={() => setCurrentZoom((z) => Math.max(z - 0.1, 0.2))}
          onFitView={handleFitView}
          currentZoom={currentZoom * 100}
          editable={!readOnly}
        />
      </div>

      {/* ── ReactFlow 画布 ── */}
      <div ref={flowRef} className="flex-1 w-full">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onNodeClick={handleNodeClick}
          onConnect={handleConnect}
          onConnectStart={handleConnectStart}
          onPaneClick={handlePaneClick}
          onMove={(_event, viewport) =>
            setCurrentZoom(viewport.zoom)
          }
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          defaultEdgeOptions={defaultEdgeOptions}
          fitView
          fitViewOptions={FIT_VIEW_OPTIONS}
          minZoom={0.1}
          maxZoom={2}
          deleteKeyCode={["Backspace", "Delete"]}
          multiSelectionKeyCode="Shift"
          selectionKeyCode="Shift"
          panOnScroll
          selectionOnDrag
          selectNodesOnDrag
          panOnDrag={[1, 2]}
          elevateNodesOnSelect
          proOptions={{ hideAttribution: true }}
          className="!bg-[#0f1117]"
        >
          {/* ── 背景网格 ── */}
          <Background
            color="#1E293B"
            gap={20}
            size={0.5}
            className="opacity-50"
          />

          {/* ── MiniMap 缩略图 ── */}
          <MiniMap
            style={miniMapStyle}
            nodeStrokeWidth={2}
            nodeBorderRadius={6}
            pannable
            zoomable
            className="!bg-[#0f1117] !border !border-[#1E293B] !rounded-lg"
            maskStrokeColor="#334155"
            maskStrokeWidth={1}
          />

          {/* ── 缩放控件（右下角） ── */}
          <Controls
            className="
              !bg-[#0f1117] !border !border-[#1E293B] !rounded-xl
              !shadow-lg !shadow-black/30
              [&_button]:!bg-[#141924] [&_button]:!border-[#1E293B]
              [&_button]:!text-slate-400 [&_button]:hover:!bg-[#1A1F2E]
              [&_button]:hover:!text-white [&_button]:!fill-slate-400
              [&_button]:hover:!fill-white
              [&_svg]:!w-3.5 [&_svg]:!h-3.5
            "
            showInteractive={!readOnly}
          />
        </ReactFlow>
      </div>

      {/* ── 脚注：快捷键提示 ── */}
      <div className="absolute bottom-3 right-3 z-10">
        <div
          className="
            px-2.5 py-1 rounded-lg
            bg-[#0f1117]/80 backdrop-blur-md
            border border-[#1E293B]
            text-[9px] text-slate-600
            select-none
          "
        >
          <span>拖拽连线创建关系</span>
          <span className="mx-1.5 text-slate-700">|</span>
          <span>双击空白添加实体</span>
        </div>
      </div>

      {/* ── 空画布提示 ── */}
      {nodes.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="text-center">
            <div className="w-16 h-16 mx-auto mb-4 rounded-full bg-indigo-500/10 flex items-center justify-center">
              <svg
                className="w-8 h-8 text-indigo-400/60"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z"
                />
              </svg>
            </div>
            <h3 className="text-sm font-medium text-slate-500 mb-1">
              画布为空
            </h3>
            <p className="text-[11px] text-slate-600 max-w-[240px]">
              {readOnly
                ? "当前域暂无实体数据"
                : '点击上方「添加实体」按钮或双击画布空白区域，开始创建第一个本体实体'}
            </p>
          </div>
        </div>
      )}
    </div>
  );
}
