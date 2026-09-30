declare module '@apollo/client' {
  export function useQuery<T = unknown>(query: unknown, options?: unknown): {
    data: T | undefined;
    loading: boolean;
    error: Error | undefined;
  };
  export function useMutation<T = unknown>(mutation: unknown, options?: unknown): [
    (options?: unknown) => Promise<{ data: T }>,
    { loading: boolean; error: Error | undefined }
  ];
  export function useLazyQuery<T = unknown>(query: unknown, options?: unknown): [
    (options?: unknown) => Promise<{ data: T }>,
    { data: T | undefined; loading: boolean; error: Error | undefined }
  ];
  export function gql(strings: TemplateStringsArray): unknown;
  export class ApolloClient<T> {
    constructor(options: unknown);
  }
  export class InMemoryCache {}
  export function createHttpLink(options?: unknown): unknown;
  export function ApolloProvider(props: { client: ApolloClient<unknown>; children?: React.ReactNode }): React.ReactElement;
}

declare module 'react-markdown' {
  import type { ComponentType } from 'react';
  const _a: any;
  export default _a;
  export interface ReactMarkdownProps {
    children?: string;
    [key: string]: unknown;
  }
  export type Components = Record<string, ComponentType<Record<string, unknown>>>;
}

declare module '@xyflow/react' {
  export interface XYPosition {
    x: number;
    y: number;
    z?: number;
  }

  export interface Node<T = Record<string, unknown>> {
    id: string;
    position: XYPosition;
    data: T;
    type?: string | number;
    measured?: { width?: number; height?: number };
    width?: number;
    height?: number;
    selected?: boolean;
    draggable?: boolean;
    deletable?: boolean;
    connectable?: boolean;
    parentId?: string;
    [key: string]: unknown;
  }

  export interface Edge<T = Record<string, unknown>> {
    id: string;
    source: string;
    target: string;
    type?: string | number;
    animated?: boolean;
    data?: T;
    sourceHandle?: string | null;
    targetHandle?: string | null;
    selected?: boolean;
    deletable?: boolean;
    style?: Record<string, unknown>;
    [key: string]: unknown;
  }

  export interface ReactFlowInstance {
    getNode(id: string): Node | undefined;
    getInternalNode(id: string): Node | undefined;
    getNodes(): Node[];
    getInternalNodes(): Node[];
    getEdges(): Edge[];
    getInternalEdges(): Edge[];
    setNodes(nodes: Node[]): void;
    setEdges(edges: Edge[]): void;
    addNodes(nodes: Node | Node[]): void;
    addEdges(edges: Edge | Edge[]): void;
    deleteElements(deleteElements: { nodes?: Node[]; edges?: Edge[] }): void;
    fitView(options?: FitViewOptions): void;
    screenToFlowPosition(point: { x: number; y: number }): XYPosition;
    getViewport(): { x: number; y: number; zoom: number };
    setViewport(viewport: { x: number; y: number; zoom: number }): void;
    zoomIn(options?: unknown): void;
    zoomOut(options?: unknown): void;
    zoomTo(level: number, options?: unknown): void;
    deleteNode(id: string): void;
    deleteEdge(id: string): void;
    getInternalState(): { nodes: Node[]; edges: Edge[] };
    getElementUnitPosition(): { width: number; height: number };
    removeNode(id: string): void;
    removeEdge(id: string): void;
    addNode(node: Node, options?: unknown): void;
    addEdge(edge: Edge): void;
    [key: string]: unknown;
  }

  export interface FitViewOptions {
    padding?: number;
    duration?: number;
    maxZoom?: number;
    minZoom?: number;
  }

  export type OnChange<T = unknown> = (changes: T[]) => void;
  export type NodeChange<T = unknown> = { id: string; type: string; [key: string]: unknown };
  export type EdgeChange<T = unknown> = { id: string; type: string; [key: string]: unknown };

  export function addEdge<T = any>(edgeOrPayload: EdgeOrPayload | Edge<T>, edges?: any[]): Edge<T>[];
  export interface EdgeOrPayload {
    source?: string;
    target?: string;
    [key: string]: unknown;
  }

  export interface MarkerType {
    None: 'none';
    Arrow: 'arrow';
    ArrowClosed: 'arrowclosed';
  }

  export interface Connection {
    source: string;
    target: string;
    sourceHandle?: string | null;
    targetHandle?: string | null;
  }

  export type OnConnectStart = (
    event: MouseEvent | React.MouseEvent,
    nodeId: string | null,
    handleId: string | null
  ) => void;

  export type OnConnectEnd = (event: MouseEvent | React.MouseEvent) => void;

  export const MarkerType: Record<string, string>;

  export const Connection: {
    status: Record<string, number>;
    handle: {
      on(event: string, callback: (...args: unknown[]) => void): { dispose(): void };
    };
  };

  export function useNodesState<T = unknown>(initialNodes: T[]): [
    T[],
    (nodes: T[] | ((prev: T[]) => T[])) => void,
    OnChange<NodeChange<T>>
  ];
  export function useEdgesState<T = unknown>(initialEdges: T[]): [
    T[],
    (edges: T[] | ((prev: T[]) => T[])) => void,
    OnChange<EdgeChange<T>>
  ];
  export function useReactFlow(): ReactFlowInstance;
  export function useReactFlowStore(): Record<string, unknown> & { getSnapshot(): Record<string, unknown> };

  export function ReactFlow<D = unknown, E = unknown>(
    props: React.Attributes & {
      children?: React.ReactNode;
      nodes?: Node<D>[];
      edges?: Edge<E>[];
      nodeTypes?: Record<string, unknown>;
      edgeTypes?: Record<string, unknown>;
      onNodesChange?: OnChange;
      onEdgesChange?: OnChange;
      onNodeDrag?: (event: React.DragEvent, node: Node) => void;
      onNodeClick?: (event: React.MouseEvent, node: Node) => void;
      onNodeDoubleClick?: (event: React.MouseEvent, node: Node) => void;
      onNodeContextMenu?: (event: React.MouseEvent, node: Node) => void;
      onEdgeClick?: (event: React.MouseEvent, edge: Edge) => void;
      onEdgeDoubleClick?: (event: React.MouseEvent, edge: Edge) => void;
      onEdgeContextMenu?: (event: React.MouseEvent, edge: Edge) => void;
      onConnect?: (event: Connection) => void;
      onConnectStart?: OnConnectStart;
      onConnectEnd?: OnConnectEnd;
      onEdgeMouseEnter?: (event: React.MouseEvent, edge: Edge) => void;
      onEdgeMouseLeave?: (event: React.MouseEvent, edge: Edge) => void;
      onInit?: (instance: ReactFlowInstance) => void;
      onMove?: (event: MouseEvent, viewport: { x: number; y: number; zoom: number }) => void;
      onMoveStart?: (event: MouseEvent, viewport: { x: number; y: number; zoom: number }) => void;
      onMoveEnd?: (event: MouseEvent, viewport: { x: number; y: number; zoom: number }) => void;
      onSelectionChange?: (selection: readonly { nodes: Node[]; edges: Edge[] }) => void;
      onSelectionContextMenu?: (event: React.MouseEvent, nodes: Node[], edges: Edge[]) => void;
      onSelectionDrag?: (event: React.DragEvent, nodes: Node[], edges: Edge[]) => void;
      onPaneClick?: (event: React.MouseEvent | undefined, _trap: boolean) => void;
      onPaneMouseEnter?: (event: React.MouseEvent) => void;
      onPaneMouseLeave?: (event: React.MouseEvent) => void;
      onPaneMouseMove?: (event: React.MouseEvent) => void;
      onPaneMouseUp?: (event: React.MouseEvent, trap: boolean) => void;
      onPaneMouseDown?: (event: React.MouseEvent, trap: boolean) => void;
      onNodeObserver?: (event: unknown) => void;
      onRef?: (ref: unknown) => void;
      id?: string;
      colorMode?: 'light' | 'dark' | 'system';
      colorModeClassNames?: { dark: string; light: string };
      onlyRenderVisibleElements?: boolean;
      defaultEdgeOptions?: Record<string, unknown>;
      minZoom?: number;
      maxZoom?: number;
      snapToGrid?: boolean;
      snapGrid?: [number, number];
      zoomOnDoubleClick?: boolean;
      selectNodesOnDrag?: boolean;
      selectEdgesOnFocus?: boolean;
      nodesConnectable?: boolean;
      elementsSelectable?: boolean;
      nodesDraggable?: boolean;
      deleteKeyCode?: string[] | null;
      multiSelectionKeyCode?: string | null;
      selectionKeyCode?: string | null;
      panOnDrag?: boolean | number | [number, number];
      panOnScroll?: boolean;
      zoomOnPinch?: boolean;
      zoomOnScroll?: boolean;
      scrollZoom?: boolean;
      zoomActivationKeyCode?: string | null;
      panActivationKeyCode?: string | null;
      proOptions?: { appId?: string; hideAttribution?: boolean };
      fitView?: boolean;
      fitViewOptions?: FitViewOptions;
      nodeOrigin?: { x: number; y: number };
      autoPanOnNodeFocus?: boolean;
      autoPanOnConnect?: boolean;
      autoPanOnNodeDrag?: boolean;
      autoPan?: boolean;
      autoPanSpeed?: number;
      className?: string;
      style?: React.CSSProperties;
      [key: string]: unknown;
    }
  ): React.ReactPortal | null;

  export function Handle(props: React.Attributes & {
    type?: string;
    position?: string;
    id?: string;
    className?: string;
    style?: React.CSSProperties;
    isConnectable?: boolean;
    isDisabled?: boolean;
  }): React.ReactElement;

  export function MiniMap(props?: Record<string, unknown>): React.ReactElement;
  export function Controls(props?: Record<string, unknown>): React.ReactElement;
  export function Background(props?: Record<string, unknown>): React.ReactElement;

  /**
   * @xyflow/react 12 包装器组件 (proOptions / children 透传，与 reactflow 11 的
   * <ReactFlowProvider> 保持同义签名，供 LogicView 等 canvas 容器使用)。
   */
  export function ReactFlowProvider(props: { children?: React.ReactNode; [key: string]: unknown }): React.ReactPortal | null;

  export const BackgroundVariant: Record<string, unknown>;
  export type NodeTypes = Record<string, unknown>;
  export const NodeTypes: NodeTypes;
  export type EdgeTypes = Record<string, unknown>;
  export const EdgeTypes: EdgeTypes;

  export function Panel(props?: Record<string, unknown>): React.ReactElement;

  export type NodeProps<N = Node> = Record<string, unknown> & {
    id: string;
    type?: string;
    data?: N['data'];
    selected?: boolean;
    positionAbsoluteX?: number;
    positionAbsoluteY?: number;
    dragging?: boolean;
  };

  export type EdgeProps<E = Edge> = Record<string, unknown> & {
    id: string;
    source: string;
    target: string;
    sourceX: number;
    sourceY: number;
    targetX: number;
    targetY: number;
    data?: E['data'];
    selected?: boolean;
    animated?: boolean;
  };

  export type HandleProps = Record<string, unknown> & {
    id?: string;
    type?: string;
    position?: string;
    className?: string;
  };

  export interface BaseEdge {
    id: string;
    path: string;
    style: React.CSSProperties;
  }

  export function BaseEdge(props: React.Attributes & Record<string, unknown>): React.ReactElement;
  export function EdgeLabelRenderer(props: React.Attributes & Record<string, unknown>): React.ReactElement;

  export interface BezierPath {
    path: string;
    sourceX: number;
    sourceY: number;
    sourceControlX: number;
    sourceControlY: number;
    targetX: number;
    targetY: number;
    targetControlX: number;
    targetControlY: number;
  }

  export function getBezierPath(options: Record<string, unknown>): [number, number, number, number, string];

  export class Position {
    static Left: string;
    static Right: string;
    static Top: string;
    static Bottom: string;
  }

  export class XYPosition {
    constructor(x: number, y: number, z?: number);
    x: number;
    y: number;
    z?: number;
  }
}

declare interface Window {
  monaco?: any;
  /** ExpressionEditor 自动补全的列名来源：provider 在 beforeMount 一次性注册，列名随节点变化，故走全局 */
  __expressionColumns__?: string[];
}
