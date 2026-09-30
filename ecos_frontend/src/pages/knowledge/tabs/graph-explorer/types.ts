/**
 * GraphExplorerTab 拆出类型（自 GraphExplorerTab 搬迁，行为不变）。
 * Graph Node / Edge types (compatible with GraphCanvas)
 */

export interface GraphNode {
  id: string;
  label: string;
  type: string;
  properties?: Record<string, unknown>;
  description?: string;
}

export interface GraphEdge {
  id: string;
  source: string;
  target: string;
  relationship?: string;
  weight?: number;
}

/** 新建节点表单状态 */
export interface NewNodeFormState {
  label: string;
  nodeType: string;
  description: string;
  properties: string;
}

/** 新建边表单状态 */
export interface NewEdgeFormState {
  sourceNodeId: string;
  targetNodeId: string;
  relationship: string;
  weight: string;
}
