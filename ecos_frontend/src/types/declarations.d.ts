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

declare module '@xyflow/react' {
  export interface Node<T = Record<string, unknown>> {
    id: string;
    position: { x: number; y: number };
    data: T;
    type?: string;
  }
  export interface Edge {
    id: string;
    source: string;
    target: string;
    type?: string;
    animated?: boolean;
  }
  export function ReactFlow(props: unknown): JSX.Element;
  export function useNodesState(initialNodes: unknown[]): [unknown[], unknown, unknown];
  export function useEdgesState(initialEdges: unknown[]): [unknown[], unknown, unknown];
  export function Handle(props: unknown): JSX.Element;
  export function MiniMap(props?: unknown): JSX.Element;
  export function Controls(props?: unknown): JSX.Element;
  export function Background(props?: unknown): JSX.Element;
  export class Position {
    static Left: string;
    static Right: string;
    static Top: string;
    static Bottom: string;
  }
}
