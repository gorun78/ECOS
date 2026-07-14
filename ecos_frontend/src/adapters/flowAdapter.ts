/**
 * 流适配器 (Flow Adapter)
 *
 * 将本体实体 (Entity) 和关系 (Relationship) 数据转换为
 * ReactFlow (@xyflow/react) 画布可用的 Node/Edge 格式。
 *
 * 依赖: @xyflow/react 的 Node / Edge 类型
 */

import type { Node, Edge, XYPosition } from "@xyflow/react";
import type { Entity, Relationship } from "../types/workbench";

/** 实体节点的画布数据 */
export interface EntityNodeData {
  /** 实体编码 */
  code: string;
  /** 实体名称 */
  name: string;
  /** 实体类型 (MASTER / TRANSACTION / EVENT / REFERENCE) */
  entityType: string;
  /** 属性数量 */
  propertyCount: number;
  [key: string]: unknown;
}

/** 关系边的画布数据 */
export interface RelationshipEdgeData {
  /** 关系编码 */
  code: string;
  /** 关系名称 */
  name: string;
  /** 关系类型 */
  relationshipType: string;
  [key: string]: unknown;
}

// ================================================================
// 网格布局计算
// ================================================================

/**
 * 计算节点在网格布局中的位置
 *
 * 采用自动换行的网格布局，根据实体数量和索引计算 (x, y) 坐标。
 *
 * @param index   - 当前实体在列表中的索引 (0-based)
 * @param total   - 实体总数（用于计算列数）
 * @param columns - 每行节点数（默认 4，自动根据总数调整）
 * @returns ReactFlow XYPosition
 */
export function calculateGridPosition(
  index: number,
  total: number,
  columns?: number
): XYPosition {
  // 根据总数自适应列数
  const cols = columns || (total <= 4 ? total : total <= 8 ? 4 : Math.ceil(Math.sqrt(total)));
  const col = index % cols;
  const row = Math.floor(index / cols);

  // 节点间距
  const H_GAP = 220; // 水平间距
  const V_GAP = 160; // 垂直间距
  const START_X = 50; // 起始 X 偏移
  const START_Y = 50; // 起始 Y 偏移

  return {
    x: START_X + col * H_GAP,
    y: START_Y + row * V_GAP,
  };
}

// ================================================================
// 实体/关系 → ReactFlow 格式转换
// ================================================================

/**
 * 将域内实体数据转换为 ReactFlow 画布格式
 *
 * 每个 Entity 映射为一个 EntityNode (自定义 ReactFlow 节点 type='entityNode')，
 * 每个 Relationship 映射为一条 RelationshipEdge (type='relationshipEdge')。
 *
 * @param entities      - 实体列表
 * @param relationships - 关系列表
 * @returns { nodes: Node<EntityNodeData>[], edges: Edge<RelationshipEdgeData>[] }
 */
export function mapEntitiesToFlow(
  entities: Entity[],
  relationships: Relationship[]
): {
  nodes: Node<EntityNodeData>[];
  edges: Edge<RelationshipEdgeData>[];
} {
  // ── 转换实体为节点 ──
  const nodes: Node<EntityNodeData>[] = entities.map(
    (entity: Entity, index: number) => ({
      id: entity.id,
      type: "entityNode",
      position: calculateGridPosition(index, entities.length),
      data: {
        code: entity.code,
        name: entity.name,
        entityType: entity.entityType || "MASTER",
        propertyCount: entity.properties?.length || 0,
      },
    })
  );

  // ── 转换关系为边 ──
  const edges: Edge<RelationshipEdgeData>[] = relationships.map(
    (rel: Relationship) => ({
      id: rel.id,
      source: rel.sourceEntityId,
      target: rel.targetEntityId,
      type: "relationshipEdge",
      data: {
        code: rel.code,
        name: rel.name,
        relationshipType: rel.relationshipType || "RELATED_TO",
      },
    })
  );

  return { nodes, edges };
}

/**
 * 将单个实体转换为画布节点
 *
 * @param entity - 实体对象
 * @param index  - 在列表中的位置（用于计算初始坐标）
 * @returns ReactFlow 节点
 */
export function mapEntityToNode(
  entity: Entity,
  index: number = 0
): Node<EntityNodeData> {
  return {
    id: entity.id,
    type: "entityNode",
    position: calculateGridPosition(index, 1),
    data: {
      code: entity.code,
      name: entity.name,
      entityType: entity.entityType || "MASTER",
      propertyCount: entity.properties?.length || 0,
    },
  };
}

/**
 * 将单个关系转换为画布边
 *
 * @param rel - 关系对象
 * @returns ReactFlow 边
 */
export function mapRelationshipToEdge(
  rel: Relationship
): Edge<RelationshipEdgeData> {
  return {
    id: rel.id,
    source: rel.sourceEntityId,
    target: rel.targetEntityId,
    type: "relationshipEdge",
    data: {
      code: rel.code,
      name: rel.name,
      relationshipType: rel.relationshipType || "RELATED_TO",
    },
  };
}

/**
 * 计算力导向布局（简化版）
 *
 * 对已有节点集合应用简单的排斥力 + 弹簧力迭代，返回新的位置数组。
 * 注意：这是一个轻量实现，生产环境建议使用 dagre 或 elkjs。
 *
 * @param nodes - 当前画布节点列表
 * @param edges - 当前画布边列表
 * @param iterations - 迭代次数（默认 50）
 * @returns 更新位置后的节点列表
 */
export function applyForceLayout(
  nodes: Node<EntityNodeData>[],
  edges: Edge<RelationshipEdgeData>[],
  iterations: number = 50
): Node<EntityNodeData>[] {
  if (nodes.length === 0) return nodes;

  // 创建可变副本
  const positions: Map<string, { x: number; y: number; vx: number; vy: number }> =
    new Map();

  nodes.forEach((node) => {
    positions.set(node.id, {
      x: node.position.x,
      y: node.position.y,
      vx: 0,
      vy: 0,
    });
  });

  const REPULSION = 5000;
  const ATTRACTION = 0.01;
  const DAMPING = 0.9;
  const MIN_DIST = 50;

  for (let iter = 0; iter < iterations; iter++) {
    // 排斥力：任意两个节点之间
    const nodeIds = Array.from(positions.keys());
    for (let i = 0; i < nodeIds.length; i++) {
      for (let j = i + 1; j < nodeIds.length; j++) {
        const a = positions.get(nodeIds[i])!;
        const b = positions.get(nodeIds[j])!;
        let dx = a.x - b.x;
        let dy = a.y - b.y;
        const dist = Math.max(Math.sqrt(dx * dx + dy * dy), MIN_DIST);
        const force = REPULSION / (dist * dist);

        const fx = (dx / dist) * force;
        const fy = (dy / dist) * force;

        a.vx += fx;
        a.vy += fy;
        b.vx -= fx;
        b.vy -= fy;
      }
    }

    // 弹簧力：有边连接的节点之间
    edges.forEach((edge) => {
      const source = positions.get(edge.source);
      const target = positions.get(edge.target);
      if (!source || !target) return;

      const dx = target.x - source.x;
      const dy = target.y - source.y;
      const dist = Math.max(Math.sqrt(dx * dx + dy * dy), MIN_DIST);

      const fx = dx * ATTRACTION;
      const fy = dy * ATTRACTION;

      source.vx += fx;
      source.vy += fy;
      target.vx -= fx;
      target.vy -= fy;
    });

    // 应用速度 + 阻尼
    positions.forEach((p) => {
      p.vx *= DAMPING;
      p.vy *= DAMPING;
      p.x += p.vx;
      p.y += p.vy;
      p.vx = 0;
      p.vy = 0;
    });
  }

  // 更新节点位置
  return nodes.map((node) => {
    const pos = positions.get(node.id);
    if (!pos) return node;
    return {
      ...node,
      position: { x: Math.round(pos.x), y: Math.round(pos.y) },
    };
  });
}
