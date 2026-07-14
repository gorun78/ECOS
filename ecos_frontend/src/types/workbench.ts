/**
 * 本体工作台 (Ontology Workbench) 类型定义
 *
 * 复用了 api.ts 中已有的 OntologyEntity / OntologyProperty / OntologyRelationship 作为基础类型，
 * 在此之上扩展工作台特有的域 (Domain)、知识图谱响应、数据映射等类型。
 */

import type {
  OntologyEntity,
  OntologyProperty,
  OntologyRelationship,
} from "../api";

// ── 复用 api.ts 基础类型并重导出 ──────────────────────────────
export type { OntologyEntity, OntologyProperty, OntologyRelationship };

// ================================================================
// 域 (Domain)
// ================================================================

/** 业务域 — 本体的顶层组织单元 */
export interface Domain {
  /** 域编码（唯一标识，如 "sales"、"supply_chain"） */
  code: string;
  /** 域显示名称（如 "销售域"、"供应链"） */
  name: string;
  /** 域下实体编码列表 */
  entities: string[];
  /** 实体数量 */
  entityCount: number;
  /** 关系数量 */
  relationshipCount: number;
  /** 域描述 */
  description?: string;
  /** 域状态 */
  status: "active" | "inactive" | "draft";
}

// ================================================================
// 知识图谱响应
// ================================================================

/** 知识图谱节点（来自 GET /api/v1/ecos/knowledge-graph） */
export interface KGNode {
  id: string;
  label?: string;
  /** 所属域编码 — 用于前端 domainAdapter 提取域信息 */
  domain?: string;
  /** 域显示名称 */
  domainName?: string;
  /** 实体编码 */
  entityCode?: string;
  /** 实体名称 */
  entityName?: string;
  /** 实体类型（MASTER / TRANSACTION / EVENT / REFERENCE） */
  entityType?: string;
  /** 节点类型 */
  nodeType?: string;
  /** 描述 */
  description?: string;
  /** 属性 JSON */
  properties?: Record<string, any>;
}

/** 知识图谱边（来自 GET /api/v1/ecos/knowledge-graph） */
export interface KGEdge {
  id: string;
  source: string;
  target: string;
  /** 关系类型 */
  relationship?: string;
  /** 关系名称 */
  relationshipName?: string;
  /** 权重 */
  weight?: number;
}

/** 知识图谱统计信息 */
export interface KGStats {
  /** 节点总数 */
  totalNodes: number;
  /** 边总数 */
  totalEdges: number;
  /** 域数量 */
  totalDomains?: number;
}

/** 知识图谱 API 响应体 */
export interface KnowledgeGraphResponse {
  nodes: KGNode[];
  edges: KGEdge[];
  stats: KGStats;
}

// ================================================================
// 工作台实体（基于 OntologyEntity 扩展）
// ================================================================

/** 工作台实体 — 在 OntologyEntity 基础上增加属性和关系列表 */
export interface Entity extends OntologyEntity {
  /** 实体属性列表（使用时按需填充） */
  properties?: OntologyProperty[];
  /** 实体关系列表（使用时按需填充） */
  relationships?: OntologyRelationship[];
}

/** 工作台属性 — 直接复用 OntologyProperty */
export type Property = OntologyProperty;

/** 工作台关系 — 直接复用 OntologyRelationship */
export type Relationship = OntologyRelationship;

// ================================================================
// 术语库相关类型 (v1.1)
// ================================================================

/** 术语库术语 */
export interface GlossaryTerm {
  id: string;
  /** 术语名称 */
  name: string;
  /** 术语编码 */
  code?: string;
  /** 所属域 */
  domain?: string;
  /** 状态 */
  status: string;
  /** 描述 */
  description?: string;
  /** 创建时间 */
  createdAt?: string;
  /** 更新时间 */
  updatedAt?: string;
}

/** 术语查询过滤条件 */
export interface GlossaryFilter {
  domain?: string;
  status?: string;
  keyword?: string;
}

/** 实体 ↔ 术语关联 */
export interface EntityTermBinding {
  entityId: string;
  termId: string;
  boundAt?: string;
}

/** 属性 ↔ 术语关联 */
export interface PropertyTermBinding {
  propertyId: string;
  termId: string;
  boundAt?: string;
}

// ================================================================
// 数据映射相关类型 (v1.1)
// ================================================================

/** 物理数据资源（来自 /api/v1/datanet/metadata/resources/all） */
export interface BulkResource {
  /** 资源ID */
  resourceId: string;
  /** 资源名称（表名/视图名） */
  resourceName: string;
  /** 类型: TABLE | VIEW */
  resourceType: string;
  /** 源路径（如 "sales.customer"） */
  sourcePath: string;
  /** 数据源名称 */
  datasourceName: string;
  /** 数据源类型: JDBC | ... */
  datasourceType: string;
  /** 字段数量 */
  fieldCount: number;
}

/** 数据底座资源摘要（用于 DataSourcePanel 列表） */
export interface DataResourceSummary {
  resourceId: string;
  resourceName: string;
  resourceType: string;
  datasourceName: string;
  fieldCount: number;
}

/** 物理字段（来自 /api/v1/datanet/metadata/fields/{resourceId}） */
export interface WorkbenchDataField {
  /** 字段名 */
  fieldName: string;
  /** 字段类型: VARCHAR / INTEGER / DECIMAL / DATE / ... */
  fieldType: string;
  /** 是否可为空 */
  nullable: boolean;
  /** 是否主键 */
  primaryKey: boolean;
  /** 字段注释 */
  comment: string;
}

/**
 * 实体 ↔ 物理表映射
 * 一个本体实体可以映射到 0..N 个物理表/视图
 */
export interface EntityTableMapping {
  /** 映射ID */
  id: string;
  /** 本体实体 ID */
  entityId: string;
  /** 物理资源 ID */
  resourceId: string;
  /** 物理资源名称（冗余，方便展示） */
  resourceName: string;
  /** TABLE | VIEW */
  resourceType: string;
  /** 数据源名称 */
  datasourceName: string;
  /** 手动映射 / 自动发现 */
  mappingType: "manual" | "auto_discovered";
  /** 映射时间 */
  mappedAt: string;
  /** 操作用户 */
  mappedBy: string;
}

/**
 * 属性 ↔ 物理字段映射
 * 本体属性映射到物理表的具体字段
 */
export interface FieldMapping {
  /** 映射ID */
  id: string;
  /** 本体实体 ID */
  entityId: string;
  /** 本体属性 code */
  propertyCode: string;
  /** 物理资源 ID */
  resourceId: string;
  /** 物理字段名 */
  fieldName: string;
  /** 物理字段类型 */
  fieldType: string;
  /** 自动推断 / 手动指定 */
  mappingType: "auto" | "manual";
  /** 自动匹配置信度 (0-1) */
  confidence?: number;
}

// ================================================================
// CRUD DTO
// ================================================================

/** 创建实体请求体 */
export interface CreateEntityDTO {
  code: string;
  name: string;
  description?: string;
  entityType?: string;
  domain?: string;
  sortOrder?: number;
}

/** 更新实体请求体 */
export interface UpdateEntityDTO {
  name?: string;
  description?: string;
  entityType?: string;
  domain?: string;
  sortOrder?: number;
}

/** 创建属性请求体 */
export interface CreatePropertyDTO {
  code: string;
  name: string;
  propertyType: string;
  requiredFlag?: number;
  searchableFlag?: number;
  sortOrder?: number;
  functionType?: string;
  functionExpression?: string;
}

/** 更新属性请求体 */
export interface UpdatePropertyDTO {
  name?: string;
  propertyType?: string;
  requiredFlag?: number;
  searchableFlag?: number;
  sortOrder?: number;
  functionType?: string;
  functionExpression?: string;
}

/** 创建关系请求体 */
export interface CreateRelationshipDTO {
  sourceEntityId: string;
  targetEntityId: string;
  code: string;
  name: string;
  relationshipType?: string;
}

/** 创建字段映射请求体 (v1.1) */
export interface CreateFieldMappingDTO {
  entityId: string;
  propertyCode: string;
  resourceId: string;
  fieldName: string;
  fieldType: string;
  mappingType: "auto" | "manual";
  confidence?: number;
}
