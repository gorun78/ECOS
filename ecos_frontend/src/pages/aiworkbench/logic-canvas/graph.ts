/**
 * logic-canvas 图模型 helpers — 由 LogicView.tsx 机械抽取（H6-T4），实现与原文逐行一致。
 * 注意：NODE_COUNTS 是模块级可变计数器，必须由本文件单点持有，
 * LogicView / 各子模块共享同一实例，行为与原单文件版本完全一致。
 * @license Apache-2.0
 */
import type { Node, Edge } from '@xyflow/react';
import type {
  AIPLogicPipeline,
  AipPipelineGraph,
  LogicNodeData,
  LogicEdgeData,
  LogicNodeType,
  LogicNodeConfig,
} from '../../../types/aiworkbench';

// ── Default configs per node type ──────────────────────────

export const DEFAULT_CONFIGS: Record<LogicNodeType, LogicNodeConfig> = {
  llm: { model: 'gemini-1.5-pro', temperature: 0.7, maxTokens: 4096, systemPrompt: '' },
  tool: { toolName: 'http_request', parameters: '{}' },
  ontology: { objectType: 'Flight', queryType: 'get', filter: 'id == ""' },
  approval: { approver: 'admin', timeout: 300 },
  condition: { conditionExpr: '$.status == "ok"', thenBranch: '通过', elseBranch: '拒绝' },
  trigger: { cronExpr: '0 0 * * *', timezone: 'Asia/Shanghai' },
};

export const NODE_COUNTS = { llm: 0, tool: 0, ontology: 0, approval: 0, condition: 0, trigger: 0 };

export function resetNodeCounts() {
  NODE_COUNTS.llm = 0;
  NODE_COUNTS.tool = 0;
  NODE_COUNTS.ontology = 0;
  NODE_COUNTS.approval = 0;
  NODE_COUNTS.condition = 0;
  NODE_COUNTS.trigger = 0;
}

export function nextNodeId(type: LogicNodeType): string {
  NODE_COUNTS[type]++;
  return `${type}-${NODE_COUNTS[type]}`;
}

export function makeNode(type: LogicNodeType, label: string, position: { x: number; y: number }, overrides?: Partial<LogicNodeConfig>): Node<LogicNodeData> {
  return {
    id: nextNodeId(type),
    type,
    position,
    data: {
      type,
      label,
      status: 'idle',
      config: { ...DEFAULT_CONFIGS[type], ...overrides },
    },
  };
}

// ── Build nodes/edges from pipeline blocks (legacy → canvas) ──
export function pipelineToGraph(pipeline: AIPLogicPipeline | undefined): { nodes: Node<LogicNodeData>[]; edges: Edge<LogicEdgeData>[] } {
  if (!pipeline || !pipeline.blocks.length) return { nodes: [], edges: [] };
  resetNodeCounts();

  const nodes: Node<LogicNodeData>[] = [];
  const edges: Edge<LogicEdgeData>[] = [];
  const spacing = 200;

  // Map legacy block types to canvas node types
  const typeMap: Record<string, LogicNodeType> = {
    input: 'trigger',
    query_ontology: 'ontology',
    llm: 'llm',
    ontology_action: 'tool',
    output: 'tool',
  };

  pipeline.blocks.forEach((block, idx) => {
    const ntype = typeMap[block.type] || 'tool';
    const pos = { x: 100, y: 50 + idx * spacing };

    let config: LogicNodeConfig;
    switch (ntype) {
      case 'llm':
        config = {
          model: block.config.modelId || 'gemini-1.5-pro',
          temperature: block.config.temperature ?? 0.7,
          maxTokens: 4096,
          systemPrompt: block.config.systemPrompt || '',
        };
        break;
      case 'ontology':
        config = {
          objectType: block.config.queryTarget || 'Object',
          queryType: 'get',
          filter: block.config.queryFilter || '',
        };
        break;
      case 'trigger':
        config = { cronExpr: '0 0 * * *', timezone: 'Asia/Shanghai' };
        break;
      case 'tool':
        if (block.type === 'ontology_action') {
          config = { toolName: block.config.actionTypeId || 'action', parameters: JSON.stringify(block.config.actionMapping || {}) };
        } else {
          config = { toolName: block.type === 'output' ? 'output_formatter' : 'tool', parameters: '{}' };
        }
        break;
      default:
        config = DEFAULT_CONFIGS[ntype];
    }

    nodes.push({
      id: nextNodeId(ntype),
      type: ntype,
      position: pos,
      data: { type: ntype, label: block.name, status: 'idle', config },
    });

    if (idx > 0) {
      const prevNode = nodes[idx - 1];
      edges.push({
        id: `e-${prevNode.id}-${nodes[idx].id}`,
        source: prevNode.id,
        target: nodes[idx].id,
        animated: false,
        data: {},
      });
    }
  });

  return { nodes, edges };
}

// ── Canvas persistence (V329: definition_json 的 graph 快照) ──

/**
 * 画布 → 持久化快照：只保留重放必需的字段（id/type/position/data、边的
 * id/source/target/data），禁写 React Flow 运行时字段（selected/dragging 等）。
 */
export function graphToSnapshot(
  nodes: Node<LogicNodeData>[],
  edges: Edge<LogicEdgeData>[],
): AipPipelineGraph {
  return {
    nodes: nodes.map(n => ({
      id: n.id,
      type: (n.data?.type ?? n.type ?? 'tool') as LogicNodeType,
      position: { x: n.position.x, y: n.position.y },
      data: n.data,
    })),
    edges: edges.map(e => ({
      id: e.id,
      source: e.source,
      target: e.target,
      ...(e.data ? { data: e.data } : {}),
    })),
  };
}

/** 持久化快照 → 画布（配 node 计数器复位，保证后续新增节点 ID 不撞车）。 */
export function snapshotToGraph(snapshot: AipPipelineGraph | null | undefined): {
  nodes: Node<LogicNodeData>[];
  edges: Edge<LogicEdgeData>[];
} {
  if (!snapshot || !Array.isArray(snapshot.nodes) || snapshot.nodes.length === 0) {
    return { nodes: [], edges: [] };
  }
  resetNodeCounts();
  const nodes: Node<LogicNodeData>[] = snapshot.nodes.map(n => ({
    id: n.id,
    type: n.data?.type ?? n.type,
    position: n.position,
    data: { ...n.data, status: 'idle' },
  }));
  // 计数器对齐到已有最大序号，避免 nextNodeId 生成重复 ID
  for (const n of nodes) {
    const m = /^([a-z]+)-(\d+)$/.exec(n.id);
    if (m && m[1] in NODE_COUNTS) {
      const t = m[1] as LogicNodeType;
      NODE_COUNTS[t] = Math.max(NODE_COUNTS[t], Number(m[2]));
    }
  }
  const edges: Edge<LogicEdgeData>[] = (snapshot.edges ?? []).map(e => ({
    id: e.id,
    source: e.source,
    target: e.target,
    animated: false,
    data: e.data ?? {},
  }));
  return { nodes, edges };
}

/** 从 pipeline 行还原画布：graph 快照优先（V329 新形态），blocks 兜底（legacy 行）。 */
export function pipelineToCanvas(pipeline: AIPLogicPipeline | undefined): {
  nodes: Node<LogicNodeData>[];
  edges: Edge<LogicEdgeData>[];
} {
  if (pipeline?.graph && pipeline.graph.nodes.length > 0) {
    return snapshotToGraph(pipeline.graph);
  }
  return pipelineToGraph(pipeline);
}

// ── Helpers ────────────────────────────────────────────────

export const TYPE_LABELS: Record<LogicNodeType, string> = {
  llm: 'LLM',
  tool: 'Tool',
  ontology: 'Ontology',
  approval: '审批',
  condition: '条件',
  trigger: '触发器',
};

export function typeLabel(type: LogicNodeType): string {
  return TYPE_LABELS[type];
}

// Topological sort for execution order
export function topologicalSort(nodes: Node<LogicNodeData>[], edges: Edge<LogicEdgeData>[]): string[] {
  const inDegree: Record<string, number> = {};
  const adj: Record<string, string[]> = {};

  for (const n of nodes) {
    inDegree[n.id] = 0;
    adj[n.id] = [];
  }
  for (const e of edges) {
    if (inDegree[e.target] !== undefined) {
      inDegree[e.target]++;
    }
    if (adj[e.source]) {
      adj[e.source].push(e.target);
    }
  }

  const queue: string[] = [];
  for (const n of nodes) {
    if (inDegree[n.id] === 0) queue.push(n.id);
  }

  const result: string[] = [];
  while (queue.length > 0) {
    const nodeId = queue.shift()!;
    result.push(nodeId);
    for (const neighbor of adj[nodeId] || []) {
      if (--inDegree[neighbor] === 0) {
        queue.push(neighbor);
      }
    }
  }
  return result;
}
