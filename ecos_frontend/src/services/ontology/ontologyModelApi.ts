/**
 * 本体模型 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：本体模型 / 实体 / 属性 / 关系 / 知识图谱 / 自动发现。
 * 端点前缀：/api/v1/ecos（BASE）。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口），
 * 本模块的导出名即原 ontologyApi.ts 中同名导出，零行为变更。
 */

import { apiFetchData } from "../../api";
import { API_BASE } from "../httpClient";
import type {
  KnowledgeGraphResponse,
  Entity,
  Property,
  Relationship,
  CreateEntityDTO,
  UpdateEntityDTO,
  CreatePropertyDTO,
  UpdatePropertyDTO,
  CreateRelationshipDTO,
} from "../../types/workbench";
import type { ObjectType, PropertyType, LinkType } from "../../types/ontology";

// ── 配置常量 ──────────────────────────────────────────────

/** 默认本体 ID */
export const DEFAULT_ONTOLOGY_ID = "ont001";

/** API 基础路径 */
const BASE = `${API_BASE}/v1/ecos`;

// ================================================================
// 本体基础类型定义 (T2: 由 api.ts 收敛迁移至此)
// 原位置: api.ts "Ontology Designer" 段落中的 export interface
// 迁移原因: api.ts 仅保留通用业务函数,本体类型/接口归属收口于此
// ================================================================

/** 本体实体 (Entity) 基础结构 — 对应后端 GET /api/v1/ecos/ontologies/{id}/entities 项 */
export interface OntologyEntity {
  id: string;
  ontologyId: string;
  code: string;
  name: string;
  description?: string;
  entityType: string;
  /** 所属业务域 id（后端实体可选返回，用于对象类型归域展示） */
  domainId?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 本体实体属性 (Property) 基础结构 — 对应后端 GET /api/v1/ecos/entities/{e}/properties 项 */
export interface OntologyProperty {
  id: string;
  entityId: string;
  code: string;
  name: string;
  propertyType: string;
  /** 属性说明（对应后端 ecos_ontology_property.description） */
  description?: string;
  functionType?: string;
  functionExpression?: string;
  requiredFlag: number;
  searchableFlag: number;
  uniqueFlag?: number;
  sortOrder?: number;
}

/** 本体关系 (Relationship) 基础结构 — 对应后端 GET /api/v1/ecos/relationships 项 */
export interface OntologyRelationship {
  id: string;
  sourceEntityId: string;
  targetEntityId: string;
  code: string;
  name: string;
  relationshipType: string;
}

/** 动态路径拼接 (T2 备注: 此函数仅作为私有 helper, 不对前端页面暴露) */
const ontPath = (ontologyId: string, path: string) =>
  `${BASE}/ontologies/${ontologyId}${path}`;

/** 实体列表摘要 — 仅保留 code/name/description/entityType (T2: 由 api.ts 收敛迁移至此) */
export interface EntityListItem {
  code: string;
  name: string;
  description?: string;
  entityType?: string;
}

/**
 * 获取默认本体的实体列表(用于 ObjectExplorer 动态渲染)
 * T2: 由 api.ts GET /api/v1/ecos/ontologies/ont001/entities 收敛迁移至此
 */
export async function fetchEntityList(): Promise<EntityListItem[]> {
  return apiFetchData<EntityListItem[]>(`${BASE}/ontologies/ont001/entities`);
}

// ================================================================
// 知识图谱
// ================================================================

/**
 * 获取全局知识图谱数据
 * GET /api/v1/knowledge/ecos-graph
 */
export async function fetchKnowledgeGraph(): Promise<KnowledgeGraphResponse> {
  return apiFetchData<KnowledgeGraphResponse>(`${BASE}/knowledge-graph`);
}

// ================================================================
// 本体 CRUD（ecos_ontology 表）
// ================================================================

/**
 * 获取全部本体列表
 * GET /api/v1/ecos/ontologies
 */
export async function fetchOntologies(): Promise<Array<{ id: string; code: string; name: string; version?: string; status?: string; description?: string }>> {
  return apiFetchData(`${BASE}/ontologies`);
}

/**
 * 创建本体
 */
export async function createOntology(data: { code: string; name: string; description?: string }): Promise<any> {
  return apiFetchData(`${BASE}/ontologies`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * 更新本体
 */
export async function updateOntology(id: string, data: { name?: string; description?: string; status?: string }): Promise<any> {
  return apiFetchData(`${BASE}/ontologies/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

/**
 * 删除本体
 */
export async function deleteOntology(id: string): Promise<void> {
  await apiFetchData(`${BASE}/ontologies/${id}`, { method: "DELETE" });
}

// ================================================================
// 实体 CRUD
// ================================================================

/**
 * 获取指定本体的全部实体列表
 */
export async function fetchEntities(ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<Entity[]> {
  return apiFetchData<Entity[]>(ontPath(ontologyId, "/entities"));
}

/**
 * 创建实体
 */
export async function createEntity(data: CreateEntityDTO, ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<Entity> {
  return apiFetchData<Entity>(ontPath(ontologyId, "/entities"), {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * 更新实体
 */
export async function updateEntity(id: string, data: UpdateEntityDTO, ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<Entity> {
  return apiFetchData<Entity>(ontPath(ontologyId, `/entities/${id}`), {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

/**
 * 删除实体
 */
export async function deleteEntity(id: string, ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<void> {
  await apiFetchData(ontPath(ontologyId, `/entities/${id}`), { method: "DELETE" });
}

// ================================================================
// 属性 CRUD
// 后端 canonical 端点为实体域 (OntologyPropertyController @ /api/v1/ecos/entities)，
// 不含 {ontologyId} 段 —— 传入 ontologyId 会被拼成不存在的路径而 404。
// ================================================================

/** 属性集合端点（实体域） */
const entityPropsPath = (entityId: string) => `${BASE}/entities/${entityId}/properties`;

/** 单个属性端点（实体域） */
const entityPropPath = (entityId: string, propId: string) =>
  `${entityPropsPath(entityId)}/${propId}`;

/**
 * 获取实体的属性列表
 * GET /api/v1/ecos/entities/{entityId}/properties
 */
export async function fetchProperties(entityId: string): Promise<Property[]> {
  return apiFetchData<Property[]>(entityPropsPath(entityId));
}

/**
 * 创建实体属性
 * POST /api/v1/ecos/entities/{entityId}/properties
 */
export async function createProperty(
  entityId: string,
  data: CreatePropertyDTO
): Promise<Property> {
  return apiFetchData<Property>(entityPropsPath(entityId), {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * 更新实体属性
 * PUT /api/v1/ecos/entities/{entityId}/properties/{propId}
 */
export async function updateProperty(
  entityId: string,
  propId: string,
  data: UpdatePropertyDTO
): Promise<Property> {
  return apiFetchData<Property>(entityPropPath(entityId, propId), {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

/**
 * 删除实体属性（后端为逻辑删除）
 * DELETE /api/v1/ecos/entities/{entityId}/properties/{propId}
 */
export async function deleteProperty(entityId: string, propId: string): Promise<void> {
  await apiFetchData(entityPropPath(entityId, propId), { method: "DELETE" });
}

// ================================================================
// 关系 CRUD
// ================================================================

/**
 * 获取全部关系列表
 * GET /api/v1/ecos/relationships（全局端点，无 ontology-scoped 关系端点）
 */
export async function fetchRelationships(): Promise<Relationship[]> {
  return apiFetchData<Relationship[]>(`${BASE}/relationships`);
}

/**
 * 创建关系
 * POST /api/v1/ecos/entities/{sourceEntityId}/relationships
 * （source 由 path 传入，body 携带 targetEntityId/code/name/relationshipType）
 */
export async function createRelationship(data: CreateRelationshipDTO): Promise<Relationship> {
  const { sourceEntityId, ...body } = data;
  return apiFetchData<Relationship>(`${BASE}/entities/${sourceEntityId}/relationships`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/**
 * 删除关系（后端为逻辑删除）
 * DELETE /api/v1/ecos/relationships/{relId}
 */
export async function deleteRelationship(relId: string): Promise<void> {
  await apiFetchData(`${BASE}/relationships/${relId}`, { method: "DELETE" });
}

// ================================================================
// VO → 工作台领域模型 映射
// 收敛 useOntologyData / OntologyWorkbenchLayout 的重复映射逻辑，
// 保证「列表加载」与「创建后重拉」两条路径产出一致。
// ================================================================

/** 后端 propertyType（大写枚举）→ 前端 PropertyDataType（小写） */
export function normalizePropertyDataType(raw?: string): PropertyType['dataType'] {
  const value = String(raw || 'string').trim().toLowerCase();
  const allowed: PropertyType['dataType'][] = [
    'string', 'integer', 'decimal', 'boolean', 'date', 'timestamp', 'geopoint',
  ];
  return (allowed as string[]).includes(value) ? (value as PropertyType['dataType']) : 'string';
}

/** 后端属性 VO → 前端 PropertyType */
export function mapPropertyToPropertyType(p: OntologyProperty): PropertyType {
  return {
    id: String(p.id),
    displayName: p.name || p.code || '',
    apiName: p.code || '',
    dataType: normalizePropertyDataType(p.propertyType),
    isPrimaryKey: Number(p.uniqueFlag ?? 0) === 1,
    description: p.description || '',
    required: Number(p.requiredFlag ?? 0) === 1,
    searchable: Number(p.searchableFlag ?? 0) === 1,
  };
}

/** 后端实体 VO + 属性 VO 列表 → 前端 ObjectType */
export function mapEntityToObjectType(
  entity: OntologyEntity,
  properties: OntologyProperty[] = []
): ObjectType {
  const props = properties.map(mapPropertyToPropertyType);
  return {
    id: String(entity.id),
    displayName: entity.name || entity.code || '',
    apiName: entity.code || '',
    description: entity.description || '',
    icon: entity.entityType === 'MASTER' ? 'Database' : 'FileText',
    color: entity.entityType === 'MASTER'
      ? 'border-indigo-500 bg-indigo-50 text-indigo-700'
      : 'border-teal-500 bg-teal-50 text-teal-700',
    primaryKey: props.find(p => p.isPrimaryKey)?.id || 'id',
    titleProperty: props.length > 0 ? props[0].id : 'id',
    status: 'ACTIVE',
    properties: props,
    mapping: (entity as any).mapping || undefined,
    domainId: entity.domainId || undefined,
  };
}

/** 后端关系 VO → 前端 LinkType */
export function mapRelationshipToLinkType(rel: OntologyRelationship): LinkType {
  const cardinality =
    rel.relationshipType === 'ONE_TO_ONE' ? '1:1'
      : rel.relationshipType === 'MANY_TO_MANY' ? 'N:N'
        : '1:N';
  return {
    id: String(rel.id),
    displayName: rel.name || `${rel.sourceEntityId}→${rel.targetEntityId}`,
    apiName: rel.code || '',
    description: '',
    sourceObjectType: rel.sourceEntityId,
    targetObjectType: rel.targetEntityId,
    cardinality: cardinality as LinkType['cardinality'],
    mapping: { type: 'foreign_key', foreignKeyMapping: { sourceKey: '', targetKey: '' } },
  };
}

// ================================================================
// 自动发现 (Auto Discover) — T1
// ================================================================

/** 自动发现 — 预览候选实体请求参数 */
export interface AutoDiscoverPreviewParams {
  datasourceId: string;
  resourceNames?: string[];
}

/** 自动发现 — 候选实体预览项 */
export interface AutoDiscoverEntityPreview {
  resourceName: string;
  resourceType: string;
  fieldCount: number;
  confidence: number;
  schemaName?: string;
}

/** 自动发现 — 生成请求参数 */
export interface AutoDiscoverParams {
  datasourceId: string;
  resourceNames: string[];
}

/** 自动发现 — 生成结果 */
export interface AutoDiscoverResult {
  entityCount: number;
  propertyCount: number;
  mappingCount: number;
}

/**
 * 预览候选实体
 * POST /api/v1/ecos/domains/{domainCode}/auto-discover/preview
 */
export async function previewEntities(
  domainCode: string,
  params: AutoDiscoverPreviewParams
): Promise<AutoDiscoverEntityPreview[]> {
  return apiFetchData<AutoDiscoverEntityPreview[]>(
    `${BASE}/domains/${domainCode}/auto-discover/preview`,
    {
      method: "POST",
      body: JSON.stringify(params),
    }
  );
}

/**
 * 执行自动发现生成
 * POST /api/v1/ecos/domains/{domainCode}/auto-discover
 */
export async function autoDiscover(
  domainCode: string,
  params: AutoDiscoverParams
): Promise<AutoDiscoverResult> {
  return apiFetchData<AutoDiscoverResult>(
    `${BASE}/domains/${domainCode}/auto-discover`,
    {
      method: "POST",
      body: JSON.stringify(params),
    }
  );
}

/**
 * 获取数据源列表（用于自动发现数据源选择）
 * GET /api/v1/ontology/sources
 */
export async function fetchOntologySources(): Promise<
  Array<{
    datasourceId: string;
    datasourceName: string;
    datasourceType: string;
    schemas?: string[];
  }>
> {
  return apiFetchData(`${BASE}/ontology/sources`);
}
