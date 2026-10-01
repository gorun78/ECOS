/**
 * GraphCanvasXflow — @xyflow/react 渲染层（B.9 / K-55 纠正 §4.6）。
 *
 * 职责：把 GraphPage 的 raw nodes/edges 映射为 ReactFlow 节点/边
 *   - 节点按 5 类 primitive（entity/edge/document/vector/pending）着色（token 驱动）
 *   - 支持选中 / 双击展开 / 折叠
 *   - 提供 zoom / pan / fitView
 *
 * 约束：
 *   - 主题守护 §4.1：不硬编码 `#xxx` / `bg-white`，颜色通过 useTheme().styles
 *     提供的 Tailwind token（class 字符串）传入 —— 注意 styles.xxx 是 className，
 *     走 `className` 通道而非 inline `style`（与仓内其它组件一致）。
 *   - i18n §4.3：0 硬编码中文（label 来自 API，本组件不添加文案）
 *   - 单向数据流：节点/边由 GraphPage 计算后传入，本组件不再二次过滤
 *   - ≤ 400 行
 *
 * 依赖：@xyflow/react（v12.x，已装）
 */
import { useCallback, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  type Node,
  type Edge,
  type NodeProps,
  type NodeTypes,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useTheme } from '../../../components/ThemeContext';

export interface XflowGraphNode {
  id: string;
  label: string;
  type: string;
  row?: string;
  properties?: Record<string, unknown>;
}

export interface XflowGraphEdge {
  id: string;
  source: string;
  target: string;
  relation?: string;
  weight?: number;
}

export type NodeBucket = 'entity' | 'edge' | 'document' | 'vector' | 'pending';

export function mapTreeNodeToBucket(type: string): NodeBucket {
  const t = (type || 'entity').toLowerCase();
  if (t.includes('vector') || t.includes('embedding')) return 'vector';
  if (t.includes('doc') || t.includes('file') || t.includes('text')) return 'document';
  if (t.includes('pending') || t.includes('candidate') || t.includes('review')) return 'pending';
  if (t.includes('edge') || t.includes('rel')) return 'edge';
  return 'entity';
}

interface KbNodeData {
  label: string;
  bucket: NodeBucket;
  row?: string;
  properties?: Record<string, unknown>;
}

interface KbGraphNode extends Node<KbNodeData> {}

interface GraphCanvasXflowProps {
  nodes: XflowGraphNode[];
  edges: XflowGraphEdge[];
  selectedNodeId: string | null;
  focusNodeId: string | null;
  pathNodeIds: Set<string>;
  pathEdgeIds: Set<string>;
  expandedNodeIds: Set<string>;
  onSelectNode: (id: string | null) => void;
  onDoubleClickNode: (id: string) => void;
  onCollapseNode: (id: string) => void;
  interactive?: boolean;
}

/** 桶 → 主题 token 类名（cache 在 ThemeContext；走 className 通道） */
function bucketClasses(bucket: NodeBucket): { card: string; dot: string } {
  // 复用现页可用的 token；无独立 hex 依赖
  switch (bucket) {
    case 'document':
      return { card: 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800', dot: 'bg-emerald-500' };
    case 'vector':
      return { card: 'bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300 border border-purple-200 dark:border-purple-800', dot: 'bg-purple-500' };
    case 'pending':
      return { card: 'bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800', dot: 'bg-amber-500' };
    case 'edge':
      return { card: 'bg-slate-50 dark:bg-slate-800/40 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700', dot: 'bg-slate-500' };
    case 'entity':
    default:
      return { card: 'bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800', dot: 'bg-blue-500' };
  }
}

const Bucket = ({ data, selected }: NodeProps<KbGraphNode>) => {
  const c = bucketClasses(data.bucket);
  return (
    <div
      className={`${c.card} rounded-md px-2 py-1 text-[11px] font-medium min-w-[80px] max-w-[180px] truncate shadow-sm ${
        selected ? 'ring-2 ring-indigo-400' : ''
      }`}
      title={data.label}
    >
      <Handle type="target" position={Position.Left} className="!h-1.5 !w-1.5 !border-0 !bg-current opacity-60" />
      <span className="truncate">{data.label || '—'}</span>
      <Handle type="source" position={Position.Right} className="!h-1.5 !w-1.5 !border-0 !bg-current opacity-60" />
    </div>
  );
};

const nodeTypes: NodeTypes = { kbNode: Bucket };

export default function GraphCanvasXflow({
  nodes,
  edges,
  selectedNodeId,
  focusNodeId,
  pathNodeIds,
  pathEdgeIds,
  expandedNodeIds,
  onSelectNode,
  onDoubleClickNode,
  onCollapseNode,
  interactive = true,
}: GraphCanvasXflowProps) {
  const { styles } = useTheme();

  const flowNodes: KbGraphNode[] = useMemo(() => {
    const COLS = 8;
    const GAP_X = 190;
    const GAP_Y = 120;
    const focused = focusNodeId || selectedNodeId;
    return nodes.map((n, i) => {
      const row = Math.floor(i / COLS);
      const col = i % COLS;
      const bucket = mapTreeNodeToBucket(n.type);
      const isFocus = focused === n.id;
      const isPath = pathNodeIds.has(n.id);
      return {
        id: n.id,
        type: 'kbNode',
        position: { x: col * GAP_X, y: row * GAP_Y },
        selected: isFocus,
        draggable: interactive,
        connectable: false,
        data: { label: n.label, bucket, row: n.row, properties: n.properties },
        className: (isFocus || isPath) ? 'kb-node-emphasis' : undefined,
      } as KbGraphNode;
    });
  }, [nodes, focusNodeId, selectedNodeId, pathNodeIds, interactive]);

  const flowEdges: Edge[] = useMemo(() => {
    return edges.map((e) => {
      const isPath = pathEdgeIds.has(e.id);
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        label: e.relation,
        animated: isPath,
        selected: isPath,
        // 走 tailwind stroke-* dark:* 别名（与 nodes 的 bucketClasses 同款语义），
        // §4.1 token：不 hardcode #xxx
        className: isPath ? 'stroke-emerald-500 dark:stroke-emerald-400' : 'stroke-slate-400 dark:stroke-slate-500',
        style: {
          strokeWidth: isPath ? 2 : 1,
          opacity: 0.85,
        },
        labelStyle: { fontSize: 10 },
      };
    });
  }, [edges, pathEdgeIds]);

  const onNodeClick = useCallback((_e: unknown, node: Node) => {
    onSelectNode(node.id);
  }, [onSelectNode]);

  const onNodeDoubleClick = useCallback((_e: unknown, node: Node) => {
    if (expandedNodeIds.has(node.id)) onCollapseNode(node.id);
    else onDoubleClickNode(node.id);
  }, [expandedNodeIds, onDoubleClickNode, onCollapseNode]);

  const onPaneClick = useCallback(() => {
    onSelectNode(null);
  }, [onSelectNode]);

  return (
    <div className={`w-full h-full ${styles.appBg}`}>
      <ReactFlow
        nodes={flowNodes}
        edges={flowEdges}
        nodeTypes={nodeTypes}
        onNodeClick={onNodeClick}
        onNodeDoubleClick={onNodeDoubleClick}
        onPaneClick={onPaneClick}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        minZoom={0.1}
        maxZoom={2.5}
        nodesDraggable={interactive}
        nodesConnectable={false}
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={24} size={1} color="rgba(148,163,184,0.18)" />
        <MiniMap
          maskColor="rgba(0,0,0,0.35)"
          className="!bg-white/80 dark:!bg-slate-900/80 !border !border-slate-200 dark:!border-slate-700"
        />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
