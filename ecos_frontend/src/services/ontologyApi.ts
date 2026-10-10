/**
 * 本体工作台 API 服务层 — 兼容入口（W197/R-56 拆分后）
 *
 * 本文件为 **纯 re-export 兼容入口**：原 1,605 行单文件按端点域拆分至
 * `src/services/ontology/` 下九个模块（行数门禁 `file-size.test.ts`）。
 * 对外导入路径（"../services/ontologyApi"）与导出名（函数/类型/ontologyApi 对象）
 * 与拆分前**零变更**（API 只增不改），18 个既有消费方无需改动。
 *
 * 拆分模块（按实际端点前缀分组）：
 *   ontology/ontologyModelApi     /api/v1/ecos          本体/实体/属性/关系/知识图谱/自动发现
 *   ontology/ontologyEngineApi    /api/v1/engine/ontology  引擎状态/配置/工作流/图谱查询/Copilot/引擎 Git
 *   ontology/ontologyMappingApi   /api/v1/ontology/mappings  映射/DW 取数/操作类型
 *   ontology/ontologyProposalApi  /api/v1/ontology/proposals 提案评审链路
 *   ontology/ontologyLineageApi   /api/v1/lineage        数据血缘
 *   ontology/ontologyVersionApi   /api/v1/ecos/versions  版本时间线/diff
 *   ontology/ontologyDomainApi    /api/v1/ontology/domains 工作台域 CRUD
 *   ontology/ontologyExportApi    /api/v1/ontology/export 导出任务/下载
 *   ontology/ontologyDataApi      /api/v1/ontology/{data,functions} 数据对象/函数沙盒
 *
 * 注意：属性/关系端点为「实体域」而非「本体域」——后端
 * OntologyPropertyController / OntologyRelationshipController 的 canonical 路径不含
 * {ontologyId} 段，故各模块不再拼 ontPath（详见 ontologyModelApi 注释）。
 */

export * from "./ontology/ontologyModelApi";
export * from "./ontology/ontologyEngineApi";
export * from "./ontology/ontologyMappingApi";
export * from "./ontology/ontologyProposalApi";
export * from "./ontology/ontologyLineageApi";
export * from "./ontology/ontologyVersionApi";
export * from "./ontology/ontologyDomainApi";
export * from "./ontology/ontologyExportApi";
export * from "./ontology/ontologyDataApi";

import {
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
} from "./ontology/ontologyModelApi";
import {
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
} from "./ontology/ontologyEngineApi";
import {
  fetchMappings,
  fetchMapping,
  createMapping,
  updateMapping,
  deleteMapping,
  fetchMappableObjects,
  validateEntityMappings,
  buildDocAnchorPayload,
} from "./ontology/ontologyMappingApi";
import {
  fetchExportTasks,
  fetchExportTask,
  createExportTask,
  downloadExport,
  deleteExportTask,
} from "./ontology/ontologyExportApi";
import {
  fetchOntologyData,
  fetchOntologyDataObjects,
  fetchOntologyDataRecord,
  createOntologyData,
  updateOntologyData,
  deleteOntologyData,
  clearOntologyDataByType,
} from "./ontology/ontologyDataApi";
import {
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
} from "./ontology/ontologyProposalApi";
import {
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
} from "./ontology/ontologyLineageApi";
import {
  fetchWorkbenchDomains,
  createWorkbenchDomain,
  updateWorkbenchDomain,
  deleteWorkbenchDomain,
  publishWorkbenchDomain,
  deprecateWorkbenchDomain,
  reassignObjectDomain,
} from "./ontology/ontologyDomainApi";

// ================================================================
// 导出 ontologyApi 对象（便捷调用）
// ================================================================

/**
 * ontologyApi — 集中导出的 API 客户端对象
 * 可用于 import { ontologyApi } from "..." 一次性导入全部方法
 * （键集与拆分前逐字一致；清单外新增函数请直接经命名导入使用）
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
  validateEntityMappings,
  buildDocAnchorPayload,
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
