/**
 * 本体工作台 API 服务层
 *
 * 封装所有本体相关的后端 API 调用，使用 apiFetchData<T>() 从 ../api 统一处理
 * token 认证和响应格式解析。100% 复用现有后端端点，零后端变更。
 *
 * 现有后端端点:
 *   GET    /api/v1/ecos/knowledge-graph              → 知识图谱总览
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

// 动态路径拼接
const ontPath = (ontologyId: string, path: string) =>
  `${BASE}/ontologies/${ontologyId}${path}`;

// ================================================================
// 知识图谱
// ================================================================

/**
 * 获取全局知识图谱数据
 * GET /api/v1/ecos/knowledge-graph
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
};
