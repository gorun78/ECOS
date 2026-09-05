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

declare module 'monaco-editor' {
  export function create(container: HTMLElement, options?: unknown, override?: unknown): unknown;
  export const editor: {
    create(container: HTMLElement, options?: unknown, override?: unknown): unknown;
    defineTheme(name: string, theme: unknown): void;
    setTheme(name: string): void;
  };
  export const languages: {
    register(options: unknown): void;
    setMonarchTokensProvider(languageId: string, tokenizer: unknown): void;
  };
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

declare module 'monaco-editor' {
  export function create(container: HTMLElement, options?: unknown, override?: unknown): any;
  namespace editor {
    export function getModels(): any[];
    export function createModel(text: string, uri?: string): any;
    export function create(container: HTMLElement, options?: unknown, override?: unknown): any;
    export function createModelValue(text: string, options?: unknown): any;
    export function defineTheme(name: string, theme: unknown): void;
    export function setTheme(name: string): void;
    export function trigger(id: string, reason: string, ranges: unknown, inputValue: unknown): void;
    export interface IPosition {
      lineNumber: number;
      column: number;
    }
    export interface IRange {
      startLineNumber: number;
      startColumn: number;
      endLineNumber: number;
      endColumn: number;
    }
    export interface IModel {
      getValue(): string;
      setValue(value: string): void;
      getLineCount(): number;
      onDidContentChange(listener: (e: unknown) => void): { dispose(): void };
      onDidChangeCursorPosition(listener: (e: { position: IPosition }) => void): { dispose(): void };
      dispose(): void;
    }
    export interface ITextModel {
      getValue(): string;
      setValue(value: string): void;
      getLineCount(): number;
      getLineMaxLength(): number;
      getValueLength(): number;
      onDidChangeContent(listener: (e: unknown) => void): { dispose(): void };
      onDidChangeDecorations(listener: (e: unknown) => void): { dispose(): void };
      dispose(): void;
    }
    export interface ITextBuffer {
      append(value: string): void;
      replace(range: IRange, value: string): void;
    }
    export interface IStandaloneCodeEditor extends IModel, ITextModel {
      getModel(): ITextModel | null;
      setModel(model: ITextModel | null): void;
      layout(dims?: unknown): void;
      focus(): void;
      getDomNode(): HTMLElement;
      getValue(): string;
      setValue(value: string): void;
      onDidFocus(listener: () => void): { dispose(): void };
      onDidBlur(listener: () => void): { dispose(): void };
      onDidChangeModelContent(listener: (e: { model: ITextModel; hasUndoStack: () => boolean }) => void): { dispose(): void };
      trigger(source: string, commandId: string, commandArgs?: unknown): void;
    }
    export interface IDimension { width: number; height: number };
    export interface IReadonlyEditorOption<T> { value: T };
    export interface IEditorOption<T> { value: T };
    export interface IEditorOptions {
      [key: string]: unknown;
    }
  }
  namespace languages {
    export function register(options: unknown): void;
    export function setMonarchTokensProvider(languageId: string, tokenizer: unknown): void;
    export interface ICompletionItem {
      label: string;
      kind?: number;
      insertText?: string;
      detail?: string;
      documentation?: string;
    }
    export interface ISignature {
      label: string;
      documentation?: string;
      parameters?: unknown[];
    }
    export interface ISignatureHelp {
      signatures: ISignature[];
      activeSignature?: number;
      activeParameter?: number;
    }
    export interface ICompletionList {
      suggestions: ICompletionItem[];
    }
    export interface ISignatureHelpProvider {
      signatureHelpProvider?: unknown;
    }
    export interface CompletionTriggerKind {
      Invoke: number;
      TriggerCharacter: number;
      TriggerForIncompleteCompletions: number;
    }
    export const CompletionTriggerKind: Record<string, number>;
    export interface ITextModelUpdate {
      versionId: number;
      [key: string]: unknown;
    }
    export interface TypescriptMap {
      [key: string]: unknown;
    }
    export interface typescript {
      registerCompletionItemProvider(language: string, provider: unknown): { dispose(): void };
      registerSignatureHelpProvider(language: string, provider: unknown): { dispose(): void };
      registerDefinitionProvider(language: string, provider: unknown): { dispose(): void };
      registerHoverProvider(language: string, provider: unknown): { dispose(): void };
      ScriptTarget: Record<string, unknown>;
      ModuleResolutionKind: Record<string, unknown>;
      ModuleKind: Record<string, unknown>;
    }
    export const typescript: typescript;
    export const javascript: Record<string, unknown>;
  }
  export const languages: unknown;
  export const editor: unknown;

  export interface IStandaloneEditorConstructionOptions extends Record<string, unknown> {}
  export interface IEditorConstructionOptions extends Record<string, unknown> {}
  export function registerEditorOption(name: string, option: unknown): void;
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

  export function ReactFlow(
    props: React.Attributes & {
      children?: React.ReactNode;
      nodes?: Node[];
      edges?: Edge[];
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
}
