/**
 * 本体工作台 API 服务层
 *
 * 封装所有本体相关的后端 API 调用，使用 apiFetchData<T>() 从 ../api 统一处理
 * token 认证和响应格式解析。100% 复用现有后端端点，零后端变更。
 *
 * 现有后端端点:
 *   GET    /api/v1/knowledge/ecos-graph              → 知识图谱总览
 *   GET    /api/v1/ecos/ontologies/{id}/entities     → 实体列表
 *   POST   /api/v1/ecos/ontologies/{id}/entities     → 创建实体
 *   PUT    /api/v1/ecos/ontologies/{id}/entities/{e} → 更新实体
 *   DELETE /api/v1/ecos/ontologies/{id}/entities/{e} → 删除实体
 *   GET    /api/v1/ecos/ontologies/{id}/entities/{e}/properties  → 属性列表
 *   POST   /api/v1/ecos/ontologies/{id}/entities/{e}/properties  → 创建属性
 *   PUT    /api/v1/ecos/ontologies/{id}/entities/{e}/properties/{p} → 更新属性
 *   DELETE /api/v1/ecos/ontologies/{id}/entities/{e}/properties/{p} → 删除属性
 *   GET    /api/v1/ecos/ontologies/{id}/relationships → 关系列表
 *   POST   /api/v1/ecos/ontologies/{id}/relationships → 创建关系
 *   DELETE /api/v1/ecos/ontologies/{id}/relationships/{r} → 删除关系
 */

import { apiFetchData } from "../api";
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
} from "../types/workbench";
import type {
  OntologyMappingRecord,
  CreateMappingDTO,
  UpdateMappingDTO,
  ExportTaskSummary,
  ExportTask,
  CreateExportDTO,
  PaginatedDataResponse,
  DataRecord,
  CreateDataDTO,
  UpdateDataDTO,
  Proposal,
  CreateProposalDTO,
  UpdateProposalDTO,
  VerifyProposalResult,
  ReviewProposalDTO,
  LineageEdge,
  CreateLineageDTO,
  UpdateLineageDTO,
  LineageGraph,
  LineageTraceResult,
  LineageImpactResult,
  ParseLineageResult,
} from "../types/ontology";

// ── 配置常量 ──────────────────────────────────────────────

/** 默认本体 ID */
export const DEFAULT_ONTOLOGY_ID = "ont001";

/** API 基础路径 */
const BASE = "/api/v1/ecos";

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
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 本体实体属性 (Property) 基础结构 — 对应后端 GET /api/v1/ecos/ontologies/{id}/entities/{e}/properties 项 */
export interface OntologyProperty {
  id: string;
  entityId: string;
  code: string;
  name: string;
  propertyType: string;
  functionType?: string;
  functionExpression?: string;
  requiredFlag: number;
  searchableFlag: number;
  sortOrder?: number;
}

/** 本体关系 (Relationship) 基础结构 — 对应后端 GET /api/v1/ecos/ontologies/{id}/relationships 项 */
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
// ================================================================

/**
 * 获取实体的属性列表
 */
export async function fetchProperties(entityId: string, ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<Property[]> {
  return apiFetchData<Property[]>(ontPath(ontologyId, `/entities/${entityId}/properties`));
}

/**
 * 创建实体属性
 */
export async function createProperty(
  entityId: string,
  data: CreatePropertyDTO,
  ontologyId: string = DEFAULT_ONTOLOGY_ID
): Promise<Property> {
  return apiFetchData<Property>(ontPath(ontologyId, `/entities/${entityId}/properties`), {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * 更新实体属性
 */
export async function updateProperty(
  entityId: string,
  propId: string,
  data: UpdatePropertyDTO,
  ontologyId: string = DEFAULT_ONTOLOGY_ID
): Promise<Property> {
  return apiFetchData<Property>(
    ontPath(ontologyId, `/entities/${entityId}/properties/${propId}`),
    {
      method: "PUT",
      body: JSON.stringify(data),
    }
  );
}

/**
 * 删除实体属性
 * DELETE /api/v1/ecos/ontologies/{ont001}/entities/{entityId}/properties/{propId}
 */
export async function deleteProperty(
  entityId: string,
  propId: string,
  ontologyId: string = DEFAULT_ONTOLOGY_ID
): Promise<void> {
  await apiFetchData(
    ontPath(ontologyId, `/entities/${entityId}/properties/${propId}`),
    { method: "DELETE" }
  );
}

// ================================================================
// 关系 CRUD
// ================================================================

/**
 * 获取全部关系列表
 * 使用全局关系端点 GET /api/v1/ecos/relationships
 * （前端按 ontologyId 过滤，因后端无 ontology-scoped 关系端点）
 */
export async function fetchRelationships(ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<Relationship[]> {
  // 后端实际端点: GET /api/v1/ecos/relationships（全局）
  // 返回全部关系，前端按实体归属过滤到当前本体
  return apiFetchData<Relationship[]>(`${BASE}/relationships`);
}

/**
 * 创建关系
 */
export async function createRelationship(
  data: CreateRelationshipDTO,
  ontologyId: string = DEFAULT_ONTOLOGY_ID
): Promise<Relationship> {
  return apiFetchData<Relationship>(ontPath(ontologyId, "/relationships"), {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/**
 * 删除关系
 */
export async function deleteRelationship(relId: string, ontologyId: string = DEFAULT_ONTOLOGY_ID): Promise<void> {
  await apiFetchData(ontPath(ontologyId, `/relationships/${relId}`), { method: "DELETE" });
}

// ================================================================
// 引擎状态 / 启停
// ================================================================

const ENGINE_BASE = "/api/v1/engine/ontology";

export async function fetchEngineHealth() {
  return apiFetchData(`${ENGINE_BASE}/health`);
}

export async function fetchEngineStatus() {
  return apiFetchData(`${ENGINE_BASE}/status`);
}

export async function startEngine() {
  return apiFetchData(`${ENGINE_BASE}/start`, { method: "POST" });
}

export async function stopEngine() {
  return apiFetchData(`${ENGINE_BASE}/stop`, { method: "POST" });
}

// ================================================================
// 引擎配置
// ================================================================

export async function fetchEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/settings`);
}

export async function fetchEngineSettingsDefaults() {
  return apiFetchData(`${ENGINE_BASE}/settings/defaults`);
}

export async function updateEngineSettings(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData(`${ENGINE_BASE}/settings`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function refreshEngineSettings() {
  return apiFetchData(`${ENGINE_BASE}/settings/refresh`, { method: "POST" });
}

// ================================================================
// 工作流（引擎统一入口）
// ================================================================

export async function fetchWorkflowDefinitions(pageSize = 50) {
  return apiFetchData(`${ENGINE_BASE}/workflow/definitions?pageSize=${pageSize}`);
}

export async function createWorkflowDefinition(data: Record<string, unknown>) {
  return apiFetchData(`${ENGINE_BASE}/workflow/definitions`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchWorkflowInstances(limit = 50) {
  return apiFetchData(`${ENGINE_BASE}/workflow/instances?limit=${limit}`);
}

export async function startWorkflowInstance(data: { workflowId: string; [k: string]: unknown }) {
  return apiFetchData(`${ENGINE_BASE}/workflow/instances`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchWorkflowInstance(id: string) {
  return apiFetchData(`${ENGINE_BASE}/workflow/instances/${id}`);
}

export async function approveWorkflow(id: string, body: Record<string, unknown> = {}) {
  return apiFetchData(`${ENGINE_BASE}/workflow/instances/${id}/approve`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function rejectWorkflow(id: string, body: Record<string, unknown> = {}) {
  return apiFetchData(`${ENGINE_BASE}/workflow/instances/${id}/reject`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ================================================================
// 图谱查询（引擎统一入口）
// ================================================================

export async function fetchOntologyGraph(ontologyId: string) {
  return apiFetchData(`${ENGINE_BASE}/graph/${ontologyId}`);
}

export async function fetchFullGraph() {
  return apiFetchData(`${ENGINE_BASE}/graph/full`);
}

export async function fetchNodeTrace(nodeId: string) {
  return apiFetchData(`${ENGINE_BASE}/graph/trace/${nodeId}`);
}

// ================================================================
// Copilot（AI辅助建模）
// ================================================================

export async function copilotSuggestEntity(prompt: string, schemaInfo = "") {
  return apiFetchData(`${ENGINE_BASE}/copilot/entity`, {
    method: "POST",
    body: JSON.stringify({ prompt, schemaInfo }),
  });
}

export async function copilotSuggestRelation(prompt: string, schemaInfo = "") {
  return apiFetchData(`${ENGINE_BASE}/copilot/relation`, {
    method: "POST",
    body: JSON.stringify({ prompt, schemaInfo }),
  });
}

export async function copilotValidateConsistency(schemaInfo: string) {
  return apiFetchData(`${ENGINE_BASE}/copilot/validate`, {
    method: "POST",
    body: JSON.stringify({ schemaInfo }),
  });
}

export async function copilotReverseImport(schemaInfo: string) {
  return apiFetchData(`${ENGINE_BASE}/copilot/import`, {
    method: "POST",
    body: JSON.stringify({ schemaInfo }),
  });
}

// ================================================================
// Git 版本管理
// ================================================================

export async function commitToGit(ontologyId: string, message: string) {
  return apiFetchData(`${ENGINE_BASE}/git/commit/${ontologyId}`, {
    method: "POST",
    body: JSON.stringify({ message }),
  });
}

export async function pullFromGit(ontologyId: string) {
  return apiFetchData(`${ENGINE_BASE}/git/pull/${ontologyId}`, {
    method: "POST",
    body: JSON.stringify({}),
  });
}

export async function loadFromGit(url: string) {
  return apiFetchData(`${ENGINE_BASE}/git/load`, {
    method: "POST",
    body: JSON.stringify({ url }),
  });
}

// ================================================================
// 本体映射 (Ontology Mapping) — T2.1
// ================================================================

const MAPPING_BASE = "/api/v1/ontology/mappings";

export async function fetchMappings(params?: { objectId?: string; sourceType?: string }) {
  const qs = new URLSearchParams();
  if (params?.objectId) qs.set("objectId", params.objectId);
  if (params?.sourceType) qs.set("sourceType", params.sourceType);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<OntologyMappingRecord[]>(`${MAPPING_BASE}${query}`);
}

export async function fetchMapping(id: string) {
  return apiFetchData<OntologyMappingRecord>(`${MAPPING_BASE}/${id}`);
}

export async function createMapping(data: CreateMappingDTO) {
  return apiFetchData<OntologyMappingRecord>(MAPPING_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateMapping(id: string, data: UpdateMappingDTO) {
  return apiFetchData<OntologyMappingRecord>(`${MAPPING_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteMapping(id: string) {
  await apiFetchData(`${MAPPING_BASE}/${id}`, { method: "DELETE" });
}

export async function fetchMappableObjects() {
  return apiFetchData(`${MAPPING_BASE}/objects`);
}

// ================================================================
// 本体导出 (Ontology Export) — T2.2
// ================================================================

const EXPORT_BASE = "/api/v1/ontology/export";

export async function fetchExportTasks(params?: { ontologyId?: string; format?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.ontologyId) qs.set("ontologyId", params.ontologyId);
  if (params?.format) qs.set("format", params.format);
  if (params?.status) qs.set("status", params.status);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<ExportTaskSummary[]>(`${EXPORT_BASE}${query}`);
}

export async function fetchExportTask(id: string) {
  return apiFetchData<ExportTask>(`${EXPORT_BASE}/${id}`);
}

export async function createExportTask(data: CreateExportDTO) {
  return apiFetchData<ExportTask>(EXPORT_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function downloadExport(id: string): Promise<void> {
  const token = localStorage.getItem("token") || "";
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${EXPORT_BASE}/${id}/download`, { headers });
  if (!res.ok) throw new Error(`Download failed: HTTP ${res.status}`);
  const blob = await res.blob();
  const contentDisposition = res.headers.get("content-disposition") || "";
  const match = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
  const filename = match ? match[1].replace(/['"]/g, "") : `ontology-export-${id}`;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function deleteExportTask(id: string) {
  await apiFetchData(`${EXPORT_BASE}/${id}`, { method: "DELETE" });
}

// ================================================================
// 本体数据 (Ontology Data) — T2.3
// ================================================================

const DATA_BASE = "/api/v1/ontology/data";

export async function fetchOntologyData(params?: { type?: string; objectTypeId?: string; page?: number; size?: number }) {
  const qs = new URLSearchParams();
  if (params?.type) qs.set("type", params.type);
  if (params?.objectTypeId) qs.set("objectTypeId", params.objectTypeId);
  if (params?.page) qs.set("page", String(params.page));
  if (params?.size) qs.set("size", String(params.size));
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<PaginatedDataResponse>(`${DATA_BASE}${query}`);
}

export async function fetchOntologyDataObjects() {
  return apiFetchData(`${DATA_BASE}/objects`);
}

export async function fetchOntologyDataRecord(id: string) {
  return apiFetchData<DataRecord>(`${DATA_BASE}/${id}`);
}

export async function createOntologyData(data: CreateDataDTO) {
  return apiFetchData<DataRecord>(DATA_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateOntologyData(id: string, data: UpdateDataDTO) {
  return apiFetchData<DataRecord>(`${DATA_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteOntologyData(id: string) {
  await apiFetchData(`${DATA_BASE}/${id}`, { method: "DELETE" });
}

export async function clearOntologyDataByType(objectTypeId: string) {
  await apiFetchData(`${DATA_BASE}?objectTypeId=${encodeURIComponent(objectTypeId)}`, { method: "DELETE" });
}

// ================================================================
// 本体提案 (Ontology Proposal) — T2.4
// ================================================================

const PROPOSAL_BASE = "/api/v1/ontology/proposals";

export async function fetchProposals(params?: { status?: string; targetType?: string }) {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.targetType) qs.set("targetType", params.targetType);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<Proposal[]>(`${PROPOSAL_BASE}${query}`);
}

export async function fetchProposal(id: string) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}`);
}

export async function createProposal(data: CreateProposalDTO) {
  return apiFetchData<Proposal>(PROPOSAL_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateProposal(id: string, data: UpdateProposalDTO) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteProposal(id: string) {
  await apiFetchData(`${PROPOSAL_BASE}/${id}`, { method: "DELETE" });
}

export async function submitProposal(id: string) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}/submit`, { method: "POST" });
}

export async function approveProposal(id: string, body: ReviewProposalDTO = {}) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}/approve`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function rejectProposal(id: string, body: ReviewProposalDTO = {}) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}/reject`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function verifyProposal(id: string) {
  return apiFetchData<VerifyProposalResult>(`${PROPOSAL_BASE}/${id}/verify`, { method: "POST" });
}

export async function executeProposal(id: string) {
  return apiFetchData<Proposal>(`${PROPOSAL_BASE}/${id}/execute`, { method: "POST" });
}

// ================================================================
// 数据血缘 (Lineage) — T2.5
// ================================================================

const LINEAGE_BASE = "/api/v1/lineage";

export async function fetchLineages(params?: { source?: string; target?: string; direction?: string }) {
  const qs = new URLSearchParams();
  if (params?.source) qs.set("source", params.source);
  if (params?.target) qs.set("target", params.target);
  if (params?.direction) qs.set("direction", params.direction);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<LineageEdge[]>(`${LINEAGE_BASE}${query}`);
}

export async function fetchLineage(id: string) {
  return apiFetchData<LineageEdge>(`${LINEAGE_BASE}/${id}`);
}

export async function createLineage(data: CreateLineageDTO) {
  return apiFetchData<LineageEdge>(LINEAGE_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateLineage(id: string, data: UpdateLineageDTO) {
  return apiFetchData<LineageEdge>(`${LINEAGE_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteLineage(id: string) {
  await apiFetchData(`${LINEAGE_BASE}/${id}`, { method: "DELETE" });
}

export async function fetchLineageGraph() {
  return apiFetchData<LineageGraph>(`${LINEAGE_BASE}/graph`);
}

export async function traceLineage(nodeId: string) {
  return apiFetchData<LineageTraceResult>(`${LINEAGE_BASE}/trace/${nodeId}`);
}

export async function fetchLineageEntities() {
  return apiFetchData(`${LINEAGE_BASE}/entities`);
}

export async function parseLineage(data: { format: string; data: string }) {
  return apiFetchData<ParseLineageResult>(`${LINEAGE_BASE}/parse`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchLineageImpact(params?: { objectId?: string; rootObject?: string; depth?: number }) {
  const qs = new URLSearchParams();
  if (params?.objectId) qs.set("objectId", params.objectId);
  if (params?.rootObject) qs.set("rootObject", params.rootObject);
  if (params?.depth) qs.set("depth", String(params.depth));
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<LineageImpactResult>(`${LINEAGE_BASE}/impact${query}`);
}

// ================================================================
// 导出 ontologyApi 对象（便捷调用）
// ================================================================

/**
 * ontologyApi — 集中导出的 API 客户端对象
 * 可用于 import { ontologyApi } from "..." 一次性导入全部方法
 */
export const ontologyApi = {
  fetchKnowledgeGraph,
  fetchEntities,
  createEntity,
  updateEntity,
  deleteEntity,
  fetchProperties,
  createProperty,
  updateProperty,
  deleteProperty,
  fetchRelationships,
  createRelationship,
  deleteRelationship,
  fetchEngineHealth,
  fetchEngineStatus,
  startEngine,
  stopEngine,
  fetchEngineSettings,
  fetchEngineSettingsDefaults,
  updateEngineSettings,
  refreshEngineSettings,
  fetchWorkflowDefinitions,
  createWorkflowDefinition,
  fetchWorkflowInstances,
  startWorkflowInstance,
  fetchWorkflowInstance,
  approveWorkflow,
  rejectWorkflow,
  fetchOntologyGraph,
  fetchFullGraph,
  fetchNodeTrace,
  copilotSuggestEntity,
  copilotSuggestRelation,
  copilotValidateConsistency,
  copilotReverseImport,
  commitToGit,
  pullFromGit,
  loadFromGit,
  fetchMappings,
  fetchMapping,
  createMapping,
  updateMapping,
  deleteMapping,
  fetchMappableObjects,
  fetchExportTasks,
  fetchExportTask,
  createExportTask,
  downloadExport,
  deleteExportTask,
  fetchOntologyData,
  fetchOntologyDataObjects,
  fetchOntologyDataRecord,
  createOntologyData,
  updateOntologyData,
  deleteOntologyData,
  clearOntologyDataByType,
  fetchProposals,
  fetchProposal,
  createProposal,
  updateProposal,
  deleteProposal,
  submitProposal,
  approveProposal,
  rejectProposal,
  verifyProposal,
  executeProposal,
  fetchLineages,
  fetchLineage,
  createLineage,
  updateLineage,
  deleteLineage,
  fetchLineageGraph,
  traceLineage,
  fetchLineageEntities,
  parseLineage,
  fetchLineageImpact,
  fetchWorkbenchDomains,
  createWorkbenchDomain,
  updateWorkbenchDomain,
  deleteWorkbenchDomain,
  publishWorkbenchDomain,
  deprecateWorkbenchDomain,
  reassignObjectDomain,
};

// ================================================================
// 版本管理 (Version Timeline) — T3
// ================================================================

const VERSION_BASE = "/api/v1/ecos/versions";

/** 版本列表项 — 对齐后端 OntologyVersionVO (T16-5 简化端点强类型化) */
export interface VersionItem {
  /** 版本 ID（ver 前缀，diff 端点定位键） */
  id: string;
  /** 版本号（如 1.0.0） */
  versionNo: string;
  /** 状态（Draft / Published / Deprecated） */
  status?: string;
  /** 发布人 */
  publisher?: string;
  /** 发布时间 ISO 字符串 */
  publishedAt?: string;
  /** 创建时间 ISO 字符串 */
  createdAt: string;
}

/**
 * 版本 diff 条目 — 对齐后端 OntologyVersionDiffVO 动态条目契约（T16-4 强类型容器，
 * added/removed/modified 元素保持 {field, value[, newValue]} 动态结构豁免）。
 */
export interface VersionDiffEntry {
  /** 变更的快照顶层字段名（entities / properties / relationships / actions） */
  field: string;
  /** 旧值（added 条目 = 新值；removed 条目 = 旧值；快照动态结构，Object 映射豁免） */
  value: unknown;
  /** 新值（仅 modified 条目填充） */
  newValue?: unknown;
}

/**
 * 版本差异响应 — 对齐后端 OntologyVersionDiffVO 字段（T16-4）。
 * 正常分支: version1/version2 + snapshot1/snapshot2 + added/removed/modified；
 * 空 diff 分支: version1Id/version2Id + 三空列表（NON_NULL 按需输出，故均可选）。
 */
export interface VersionDiff {
  version1?: string;
  version2?: string;
  version1Id?: string;
  version2Id?: string;
  /** 第一版本快照（动态 JSON，T16-4 豁免） */
  snapshot1?: unknown;
  /** 第二版本快照（动态 JSON，T16-4 豁免） */
  snapshot2?: unknown;
  added?: VersionDiffEntry[];
  removed?: VersionDiffEntry[];
  modified?: VersionDiffEntry[];
}

/** 与前一版本 diff 响应 — 对齐后端 OntologyVersionPreviousDiffVO (T16-5) */
export interface VersionPreviousDiff {
  currentVersion: string;
  currentSnapshot?: unknown;
  previousVersion?: string;
  previousSnapshot?: unknown;
}

/**
 * 获取版本历史列表（跨全部 ontology，按创建时间由新到旧）
 * GET /api/v1/ecos/versions
 * 后端返回 OntologyVersionVO 列表（T16-5 强类型）；
 * domainCode 参数按 API 只增不改原则保留，当前后端未消费，
 * 前端如需按 ontology 过滤应在客户端本地过滤。
 */
export async function fetchVersions(domainCode: string): Promise<VersionItem[]> {
  return apiFetchData<VersionItem[]>(
    `${VERSION_BASE}?domainCode=${encodeURIComponent(domainCode)}`
  );
}

/**
 * 获取版本详情（含全量 snapshot）
 * GET /api/v1/ecos/versions/{id}
 * 用于版本 diff 对比：前后端契约铁律 — 列表摘要不渲染编辑态，
 * 对比前必须拉取两侧完整快照。
 */
export async function fetchVersionDetail(id: string): Promise<VersionItem & { snapshot?: unknown }> {
  return apiFetchData<VersionItem & { snapshot?: unknown }>(
    `${VERSION_BASE}/${encodeURIComponent(id)}`
  );
}

/**
 * 获取两个版本之间的 diff（T11 联调真实端点 — 后端 T16-4 强类型）
 * GET /api/v1/ontology/versions/diff?v1={versionId1}&v2={versionId2}
 * 后端契约: VersionDiffController（ontology-engine-impl，PMO-39 批次2 T3），
 * v1/v2 为版本 ID（ver 前缀），非版本号；两版本需分属同一快照可比。
 * 返回 OntologyVersionDiffVO（version1/version2[/version1Id/version2Id]/
 * snapshot1/snapshot2/added/removed/modified），raw 动态值豁免。
 */
export async function fetchOntologyVersionDiff(v1Id: string, v2Id: string): Promise<VersionDiff> {
  return apiFetchData<VersionDiff>(
    `/api/v1/ontology/versions/diff?v1=${encodeURIComponent(v1Id)}&v2=${encodeURIComponent(v2Id)}`
  );
}

// ================================================================
// 域 CRUD (Workbench Domain) — T8
// 对齐后端 OntologyDomainApiController (@RequestMapping("/api/v1/ontology"))
//   GET    /api/v1/ontology/domains         → 域列表
//   POST   /api/v1/ontology/domains         → 创建域
//   PUT    /api/v1/ontology/domains/{code}  → 更新域(id 即 domainCode)
//   DELETE /api/v1/ontology/domains/{code}  → 删除域
//   PUT    /api/v1/ontology/objects/{id}/domain → 对象归属域变更
// 后端 T16-2 强类型契约: OntologyDomainVO / OntologyDomainSaveDTO
// 注: 后端无独立 publish/deprecate 端点; 域状态变更经 PUT status 字段实现
//     (OntologyDomainVO.status: Draft / Published / Deprecated)
// ================================================================

/**
 * 工作台域 VO — 对齐后端 OntologyDomainVO (T16-2)。
 * id 格式 "dom"+序号, code 为唯一业务键。
 */
export interface WorkbenchDomainVO {
  id: string;
  code: string;
  name: string;
  owner?: string;
  description?: string;
  status?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 工作台域新增/编辑 DTO — 对齐后端 OntologyDomainSaveDTO */
export interface WorkbenchDomainSaveDTO {
  code: string;
  name: string;
  owner?: string;
  description?: string;
  status?: string;
}

/** 对象归属域变更结果 — 对齐后端 OntologyDomainReassignVO */
export interface WorkbenchDomainReassignVO {
  entityId: string;
  domainId: string;
  domainCode: string;
  domainName: string;
}

/** 对象归属变更 DTO — 兼容 domainCode / domainId 双字段 */
export interface WorkbenchDomainReassignDTO {
  domainCode?: string;
  domainId?: string;
}

/**
 * 获取工作台域列表
 * GET /api/v1/ontology/domains
 */
export async function fetchWorkbenchDomains(): Promise<WorkbenchDomainVO[]> {
  return apiFetchData<WorkbenchDomainVO[]>("/api/v1/ontology/domains");
}

/**
 * 创建工作台域
 * POST /api/v1/ontology/domains
 */
export async function createWorkbenchDomain(
  dto: WorkbenchDomainSaveDTO
): Promise<WorkbenchDomainVO> {
  return apiFetchData<WorkbenchDomainVO>("/api/v1/ontology/domains", {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

/**
 * 更新工作台域
 * PUT /api/v1/ontology/domains/{code} (id 即 domainCode)
 */
export async function updateWorkbenchDomain(
  code: string,
  dto: WorkbenchDomainSaveDTO
): Promise<WorkbenchDomainVO> {
  return apiFetchData<WorkbenchDomainVO>(
    `/api/v1/ontology/domains/${encodeURIComponent(code)}`,
    { method: "PUT", body: JSON.stringify(dto) }
  );
}

/**
 * 删除工作台域 (含实体时后端拒绝)
 * DELETE /api/v1/ontology/domains/{code}
 */
export async function deleteWorkbenchDomain(code: string): Promise<void> {
  await apiFetchData(`/api/v1/ontology/domains/${encodeURIComponent(code)}`, {
    method: "DELETE",
  });
}

/**
 * 发布域 (状态 → Published)
 * 后端无独立 publish 端点: 经 "拉当前 VO → 合并 status → PUT 回写" 实现。
 * 读-改-写而非直传 status, 避免后端 update 全字段覆盖把 name/description 清掉。
 */
export async function publishWorkbenchDomain(code: string): Promise<WorkbenchDomainVO> {
  return changeWorkbenchDomainStatus(code, "Published");
}

/**
 * 废弃域 (状态 → Deprecated)
 * 后端无独立 deprecate 端点: 实现同 publishWorkbenchDomain。
 */
export async function deprecateWorkbenchDomain(code: string): Promise<WorkbenchDomainVO> {
  return changeWorkbenchDomainStatus(code, "Deprecated");
}

/**
 * 内部: 拉取当前域 VO → 仅改 status 回写 (name/description/owner 原值保留;
 * 空值字段以 undefined 传递 → JSON 省略 → 后端 DTO 反序列化为 null = 不动)。
 */
async function changeWorkbenchDomainStatus(code: string, status: string): Promise<WorkbenchDomainVO> {
  // eslint-disable-next-line no-use-before-define
  const vos = await listWorkbenchDomains();
  const vo = (vos || []).find((v) => v.code === code || v.id === code);
  if (!vo) {
    throw new Error(`Workbench domain not found: ${code}`);
  }
  const dto: WorkbenchDomainSaveDTO = {
    code: vo.code,
    name: vo.name,
    status,
  };
  if (vo.description) {
    dto.description = vo.description;
  }
  if (vo.owner) {
    dto.owner = vo.owner;
  }
  return updateWorkbenchDomain(vo.code, dto);
}

/** 域列表别名 — 与 fetchWorkbenchDomains 同源 (status 读改写的内部取词) */
function listWorkbenchDomains(): Promise<WorkbenchDomainVO[]> {
  return fetchWorkbenchDomains();
}

/**
 * 变更对象归属域
 * PUT /api/v1/ontology/objects/{id}/domain
 */
export async function reassignObjectDomain(
  objectId: string,
  dto: WorkbenchDomainReassignDTO
): Promise<WorkbenchDomainReassignVO> {
  return apiFetchData<WorkbenchDomainReassignVO>(
    `/api/v1/ontology/objects/${encodeURIComponent(objectId)}/domain`,
    { method: "PUT", body: JSON.stringify(dto) }
  );
}

// ================================================================
// 函数沙盒 (Function Sandbox) — T9
// 对齐后端 FunctionController (@RequestMapping("/api/v1/ontology/functions"))
//   POST /api/v1/ontology/functions/test     → 沙盒执行（expression + entityName）
//   POST /api/v1/ontology/functions/compile  → 仅编译（返回生成的参数化 SQL）
// 后端 T16-3 强类型契约：入参 OntologyFunctionSaveDTO / 返回 FunctionResult
// ================================================================

const FUNCTION_BASE = "/api/v1/ontology/functions";

/** 函数沙盒执行结果 — 对齐后端 FunctionResult POJO（@JsonInclude NON_NULL） */
export interface FunctionTestResultVO {
  /** 计算结果单值（无数据行时为 null） */
  value: number | string | boolean | null;
  /** SQL 类型：LONG / DOUBLE / NUMERIC 等（inferSqlType 推断） */
  sqlType?: string;
  /** 执行耗时（毫秒） */
  executionTimeMs: number;
  /** 编译生成的参数化 SQL（调试/预览） */
  compiledSql?: string;
  /** 是否命中结果缓存 */
  fromCache?: boolean;
  /** 缓存键 */
  cacheKey?: string;
}

/** 函数沙盒执行请求 — 对齐后端 OntologyFunctionSaveDTO */
export interface FunctionTestParams {
  /** SQL 表达式（必填），如 "COUNT(id) FROM ecos_ontology_data WHERE object_type='ot1'" */
  expression: string;
  /** 目标实体名（必填，与 FROM 子句表名一致；按实体名=表名映射执行） */
  entityName: string;
  /** 调用方 id（可选，默认 anonymous；写入函数执行审计日志） */
  callerId?: string;
}

/** 函数表达式编译结果 — 对齐后端 OntologyFunctionCompileVO */
export interface FunctionCompileVO {
  /** 编译生成的参数化 SQL */
  sql?: string;
  /** SQL 占位参数（动态 list，沙盒运行时 payload） */
  params?: unknown[];
  /** 解析出的目标实体名 */
  entityName?: string;
}

/**
 * 函数沙盒真实执行（替代前端 mock TS 执行）。
 * POST /api/v1/ontology/functions/test
 * 业务错误（白名单拒绝/执行失败）由 apiFetchData 统一转抛。
 */
export async function testFunction(params: FunctionTestParams): Promise<FunctionTestResultVO> {
  return apiFetchData<FunctionTestResultVO>(`${FUNCTION_BASE}/test`, {
    method: "POST",
    body: JSON.stringify(params),
  });
}

/**
 * 函数表达式仅编译（不执行）— 返回真实生成的参数化 SQL，供沙盒控制台日志预览。
 * POST /api/v1/ontology/functions/compile
 */
export async function compileFunction(
  expression: string,
  entityName?: string
): Promise<FunctionCompileVO> {
  return apiFetchData<FunctionCompileVO>(`${FUNCTION_BASE}/compile`, {
    method: "POST",
    body: JSON.stringify({ expression, entityName }),
  });
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
