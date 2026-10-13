/**
 * GraphCanvasXflow — @xyflow/react 渲染层（B.9 / K-55 纠正 §4.6）。
 *
 * 职责：把 GraphPage 的 raw nodes/edges 映射为 ReactFlow 节点/边
 *   - 节点按 5 类 primitive（entity/edge/document/vector/pending）着色
 *     （W3-6 订正：全部走 useTheme().styles token className 通道，0 裸色板类；
 *      GraphPage 图例同映射：entity=info / edge=badge / document=success /
 *      vector=warning / pending=danger）
 *   - 布局：按**连通深度分层**（W3-6：原「数组索引 8 列网格」完全忽略拓扑，
 *      现以入度 0 节点为根 BFS 分层，列 = 深度、行 = 层内序号；不可达节点
 *      顺延补层，保证孤点不丢）
 *   - 支持选中 / 双击展开 / 折叠 / 键盘通路（Tab 聚焦节点 + Enter 展开/折叠，
 *      PRD F5 验收：展开邻居不只双击）
 *   - 提供 zoom / pan / fitView
 *
 * 约束：
 *   - 主题守护 §4.1：不硬编码 `#xxx` / 裸色板类；选中态描边用 `styles.accentRing`
 *     （替代悬空 `kb-node-emphasis` 类——index.css 从未定义，归底座治理范围，
 *     本组件不再引用）
 *   - i18n §4.3：0 硬编码中文（label 来自 API，本组件不添加文案）
 *   - 单向数据流：节点/边由 GraphPage 计算后传入，本组件不再二次过滤
 *   - ≤ 400 行
 *
 * 依赖：@xyflow/react（v12.x，已装）
 */
import { useCallback, useMemo, type KeyboardEvent as ReactKeyboardEvent } from 'react';
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

/**
 * 桶 → 语义主题 token 类（§4.1：仅 styles.* 通道，禁裸色板类）。
 * 返回 { card, dot } 两组 className；消费方以 className 拼接。
 */
function bucketClasses(
  bucket: NodeBucket,
  styles: ReturnType<typeof useTheme>['styles'],
): { card: string; dot: string } {
  switch (bucket) {
    case 'document':
      return { card: `${styles.successBg} ${styles.successText} border ${styles.successBorder}`, dot: styles.successBg };
    case 'vector':
      return { card: `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`, dot: styles.warningBg };
    case 'pending':
      return { card: `${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder}`, dot: styles.dangerBg };
    case 'edge':
      return { card: `${styles.badgeBg} ${styles.badgeText} border ${styles.cardBorder}`, dot: styles.badgeBg };
    case 'entity':
    default:
      return { card: `${styles.infoBg} ${styles.infoText} border ${styles.infoBorder}`, dot: styles.infoBg };
  }
}

const Bucket = ({ data, selected }: NodeProps<KbGraphNode>) => {
  const { styles } = useTheme();
  const c = bucketClasses(data.bucket, styles);
  return (
    <div
      className={`${c.card} rounded-md px-2 py-1 text-[11px] font-medium min-w-[80px] max-w-[180px] truncate shadow-sm${
        selected ? ` ring-2 ${styles.accentRing}` : ''
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

/**
 * 连通深度分层布局（W3-6，替换旧的「索引 % 8 网格」假拓扑）：
 * 1. 以入度 0 节点为根（无向图兜底：全有入度时按原顺序取首个未访问节点）
 * 2. BFS：depth = 层号 → x = depth * GAP_X；层内序号 → y = idx * GAP_Y（层内垂直居中）
 * 3. 不可达分量：各自主根继续补层，节点不会被丢弃
 */
function computeLayeredPositions(
  nodes: XflowGraphNode[],
  edges: XflowGraphEdge[],
  gapX: number,
  gapY: number,
): Map<string, { x: number; y: number }> {
  const ids = nodes.map((n) => n.id);
  const idSet = new Set(ids);
  const adjacency = new Map<string, string[]>();
  ids.forEach((id) => adjacency.set(id, []));
  const inDegree = new Map<string, number>();
  ids.forEach((id) => inDegree.set(id, 0));
  for (const e of edges) {
    if (!idSet.has(e.source) || !idSet.has(e.target)) continue;
    adjacency.get(e.source)!.push(e.target);
    adjacency.get(e.target)!.push(e.source);
    inDegree.set(e.target, (inDegree.get(e.target) || 0) + 1);
  }

  const depthById = new Map<string, number>();
  const roots = ids.filter((id) => (inDegree.get(id) || 0) === 0);
  const rootOrder = roots.length > 0 ? [...roots, ...ids.filter((id) => !depthById.has(id) && !roots.includes(id))] : ids;

  for (const root of rootOrder) {
    if (depthById.has(root)) continue;
    // BFS 起点深度 = 当前已用最大层 + 1（隔离分量整体右移，避免与主图重叠）
    const baseDepth = depthById.size === 0 ? 0 : Math.max(...depthById.values()) + 1;
    const queue: Array<{ id: string; d: number }> = [{ id: root, d: baseDepth }];
    while (queue.length > 0) {
      const cur = queue.shift()!;
      if (depthById.has(cur.id)) continue;
      depthById.set(cur.id, cur.d);
      for (const nb of adjacency.get(cur.id) || []) {
        if (!depthById.has(nb)) queue.push({ id: nb, d: cur.d + 1 });
      }
    }
  }

  // 层内分组 → 垂直居中排布
  const byDepth = new Map<number, string[]>();
  depthById.forEach((d, id) => {
    const arr = byDepth.get(d) || [];
    arr.push(id);
    byDepth.set(d, arr);
  });
  const maxLayerSize = Math.max(1, ...Array.from(byDepth.values()).map((a) => a.length));
  const positions = new Map<string, { x: number; y: number }>();
  byDepth.forEach((layerIds, depth) => {
    const offsetY = ((maxLayerSize - layerIds.length) * gapY) / 2;
    layerIds.forEach((id, idx) => {
      positions.set(id, { x: depth * gapX, y: offsetY + idx * gapY });
    });
  });
  // 理论上不可达节点已在 BFS 中兜底；防御：仍缺位置的按末层追加
  let tail = (Math.max(0, ...Array.from(byDepth.keys())) || 0) + 1;
  for (const id of ids) {
    if (!positions.has(id)) {
      positions.set(id, { x: tail * gapX, y: 0 });
      tail += 1;
    }
  }
  return positions;
}

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
    const GAP_X = 210;
    const GAP_Y = 110;
    const focused = focusNodeId || selectedNodeId;
    const positions = computeLayeredPositions(nodes, edges, GAP_X, GAP_Y);
    return nodes.map((n) => {
      const bucket = mapTreeNodeToBucket(n.type);
      const isFocus = focused === n.id;
      const isPath = pathNodeIds.has(n.id);
      return {
        id: n.id,
        type: 'kbNode',
        position: positions.get(n.id) || { x: 0, y: 0 },
        selected: isFocus,
        draggable: interactive,
        connectable: false,
        data: { label: n.label, bucket, row: n.row, properties: n.properties },
        // 选中 / 路径高亮：accentRing token（原 kb-node-emphasis 悬空类已除，index.css 零定义）
        className: (isFocus || isPath) ? `ring-2 ${styles.accentRing}` : undefined,
      } as KbGraphNode;
    });
  }, [nodes, edges, focusNodeId, selectedNodeId, pathNodeIds, interactive, styles.accentRing]);

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
        // §4.1 token：非路径边走 stroke-current + 主题 muted 文本色；
        // 路径边走 styles.accentStroke（ThemeStyles 唯一登记的 SVG hex token）
        className: `stroke-current ${isPath ? '' : styles.muted}`,
        style: {
          stroke: isPath ? styles.accentStroke : undefined,
          strokeWidth: isPath ? 2 : 1,
          opacity: 0.85,
        },
        labelStyle: { fontSize: 10 },
      };
    });
  }, [edges, pathEdgeIds, styles.muted, styles.accentStroke]);

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

  /**
   * 键盘通路（检视报告行 189：展开邻居仅双击 → 补键盘）：
   * ReactFlow 节点默认 tabbable；Enter 作用于当前聚焦节点 = 展开/折叠邻居，
   * 空格键 = 选中（与双击的鼠标通路语义对齐）。
   */
  const onKeyDown = useCallback((e: ReactKeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    const active = document.activeElement as HTMLElement | null;
    const nodeEl = active?.closest?.('.react-flow__node') as HTMLElement | null;
    const nodeId = nodeEl?.getAttribute('data-id');
    if (!nodeId) return;
    e.preventDefault();
    if (e.key === ' ') {
      onSelectNode(nodeId);
      return;
    }
    if (expandedNodeIds.has(nodeId)) onCollapseNode(nodeId);
    else onDoubleClickNode(nodeId);
  }, [expandedNodeIds, onDoubleClickNode, onCollapseNode, onSelectNode]);

  return (
    <div className={`w-full h-full ${styles.appBg}`} onKeyDown={onKeyDown}>
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
        <Background gap={24} size={1} />
        <MiniMap
          nodeColor={styles.accentStroke}
          maskColor="rgba(127,127,127,0.28)"
        />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}
