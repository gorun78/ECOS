/**
 * knowledgeApi.ts — 兼容层（B.9 / F04-17 / K-55 拆分后的聚合入口）。
 *
 * 现状（before）：1,299 行 / 103 exports / 3 组重复 base 别名 / 5 处
 *   `localStorage.getItem('token')` 拼Authorization / fetchGraph 无 limit。
 *
 * 目标（after）：以下 6 个模块按 Tab 维度拆分后，本文件仅作为**兼容入口**
 *   - overview.ts   — 总览 / 底座（stats、sync、health、engine、jobs）
 *   - assets.ts     — 资产 / 生命周期 / catalog
 *   - extract.ts    — 抽取 / 定时 / 日志 / 本体树 / SSE 上传
 *   - graph.ts      — 图谱拉取（limit 保护）+ 路径 / 邻居
 *   - wiki.ts       — 设置 / 术语表 / RAG / 规则库
 *   - govern.ts     — profiles / assumptions / eval / RAG-SSE
 *
 * Auth 头单源：所有 raw fetch 走 `src/services/auth.ts`（getAuthToken）；
 *   `apiFetchData` / `kbFetchData` 保留各自证书面（回归 + 错误态分治）。
 *
 * `KbApiError` 从 `./kbFetch` 再导出 —— 保留原文件顶部位置语义
 *   （旧文件顶部 `export class KbApiError` 契约）。
 *
 * `knowledgeApi` 对象聚合：`export * from './xxx'` 语义保留；
 *   Tab 组件既有 `import { knowledgeApi } from '…/knowledgeApi'` 全部兼容。
 */

// ── F04-03 错误态分治 — KbApiError 单源定义（kbFetch.ts） ─────────────────────
export { KbApiError, kbFetchData, kbSseRequest } from './kbFetch';

// ── 6 Tab 维度拆分模块全量 re-export — 兼容面 ──────────────────────────────────
export * from './overview';
export * from './assets';
export * from './extract';
export * from './graph';
export * from './wiki';
export * from './govern';

// ── Graph 常量（K-55 limit 保护） ────────────────────────────────────────────
export {
  GRAPH_FETCH_DEFAULT_LIMIT,
  GRAPH_FETCH_MAX_LIMIT,
  clampLimit,
} from './graph';

// ── 兼容 knowledgeApi 聚合对象 — 原 103 导出面 ───────────────────────────────
import {
  fetchGraphStats,
  fetchEntityMappings,
  fetchEngineHealth,
  fetchSyncStatuses,
  triggerFullSync,
  triggerObjectSync,
  fetchSyncLogs,
  fetchOntologyMappings,
  saveOntologyMappings,
  exportOntology,
  fetchIndexStatus,
  syncVectors,
  fetchGraphJobs,
  previewGraphBuild,
  triggerGraphBuild,
  previewGraphJob,
  rollbackGraphJob,
  fetchGraphJobLogs,
  fetchCognitiveConfig,
  updateCognitiveConfig,
  fetchEngineConfig,
  saveEngineConfig,
} from './overview';
import {
  fetchLifecycleAssets,
  fetchLifecycleAssetsPage,
  kbFetchLifecycleAssets,
  kbLifecycleTransition,
  lifecycleTransition,
  fetchLifecycleAudit,
  kbFetchLifecycleAudit,
  classifyAsset,
  kbClassifyAsset,
} from './assets';
import {
  uploadDocumentChunked,
  fetchExtractCandidates,
  fetchUploadEnabled,
  fetchTriggerStructuredExtract,
  fetchStructuredJobs,
  fetchStructuredJobDetail,
  fetchExtractEngineConfig,
  fetchExtractCandidateFiles,
  triggerStructuredExtract,
  fetchExtractStatus,
  fetchExtractJobs,
  fetchExtractJobStatus,
  fetchOntologyTree,
  kbFetchOntologyTree,
  fetchScheduledExtracts,
  kbFetchScheduledExtracts,
  createScheduledExtract,
  updateScheduledExtract,
  deleteScheduledExtract,
  createScheduledExtractRow,
  updateScheduledExtractRow,
  fetchExtractLogs,
  kbFetchExtractLogs,
  fetchExportLogBody,
  exportExtractLog,
  fetchLineageImpact,
  parseLineage,
  fetchIntegrationMetadata,
  fetchIntegrationLogs,
  toggleSimulationDrift,
  fetchDataWorkbenchSources,
  fetchMetadataDrift,
  fetchImportQueue,
  addImportToQueue,
  retryImport,
} from './extract';
import {
  fetchGraph,
  fetchNode,
  searchKnowledge,
  findPath,
  graphSearch,
  graphPath,
  fetchNeighbors,
  createNode,
  createEdge,
  getDataSource,
} from './graph';
import {
  getSettings,
  updateSettings,
  fetchGlossaryTerms,
  kbFetchGlossaryTerms,
  createGlossaryTerm,
  updateGlossaryTerm,
  deleteGlossaryTerm,
  kbCreateGlossaryTerm,
  kbUpdateGlossaryTerm,
  kbDeleteGlossaryTerm,
  runRAGQuery,
  kbRunRAGQuery,
  runKnowledgeQuery,
  kbFetchNavProducts,
  kbFetchNavCategories,
  fetchRules,
  kbFetchRules,
  createRule,
  updateRule,
  deleteRule,
  fetchRuleVersions,
  kbFetchRuleVersions,
} from './wiki';
import {
  kbGenerateProfiles,
  kbFetchProfiles,
  kbPublishProfile,
  kbResolveProfile,
  kbCreateAssumption,
  kbFetchAssumptions,
  kbSubmitAssumption,
  kbApproveAssumption,
  kbResolveAssumptions,
  fetchEvalSeeds,
  kbFetchEvalSeeds,
  uploadEvalSeed,
  runEval,
  kbRunEval,
  runRAGQuerySSE,
} from './govern';

/**
 * 兼容层聚合对象 — 原 103 exports 面（Tab 组件既有
 * `import { knowledgeApi } from '../services/knowledgeApi'` 全部兼容）。
 *
 * 新增 K-55（limit）/ F04-17（govern 子 Tab）/ F04-03（KbApiError 分治）
 * 语义层面的成员统一通过 `kb*` 前缀暴露；旧 silent 版保留在对象内以
 * 兼容既有 Tab 调用面（回归保护）。
 */
export const knowledgeApi = {
  // graph
  fetchGraph,
  fetchNode,
  searchKnowledge,
  findPath,
  graphSearch,
  graphPath,
  fetchNeighbors,
  createNode,
  createEdge,
  getDataSource,
  // overview
  fetchSyncStatuses,
  triggerFullSync,
  triggerObjectSync,
  fetchSyncLogs,
  fetchOntologyMappings,
  saveOntologyMappings,
  exportOntology,
  fetchIndexStatus,
  syncVectors,
  fetchGraphStats,
  fetchEntityMappings,
  fetchEngineHealth,
  fetchGraphJobs,
  previewGraphBuild,
  triggerGraphBuild,
  previewGraphJob,
  rollbackGraphJob,
  fetchGraphJobLogs,
  fetchCognitiveConfig,
  updateCognitiveConfig,
  fetchEngineConfig,
  saveEngineConfig,
  // assets
  fetchLifecycleAssets,
  fetchLifecycleAssetsPage,
  kbFetchLifecycleAssets,
  kbLifecycleTransition,
  lifecycleTransition,
  fetchLifecycleAudit,
  kbFetchLifecycleAudit,
  classifyAsset,
  kbClassifyAsset,
  // extract
  uploadDocumentChunked,
  fetchExtractCandidates,
  fetchUploadEnabled,
  fetchTriggerStructuredExtract,
  fetchStructuredJobs,
  fetchStructuredJobDetail,
  fetchExtractEngineConfig,
  fetchExtractCandidateFiles,
  triggerStructuredExtract,
  fetchExtractStatus,
  fetchExtractJobs,
  fetchExtractJobStatus,
  fetchOntologyTree,
  kbFetchOntologyTree,
  fetchScheduledExtracts,
  kbFetchScheduledExtracts,
  createScheduledExtract,
  updateScheduledExtract,
  deleteScheduledExtract,
  createScheduledExtractRow,
  updateScheduledExtractRow,
  fetchExtractLogs,
  kbFetchExtractLogs,
  fetchExportLogBody,
  exportExtractLog,
  fetchLineageImpact,
  parseLineage,
  fetchIntegrationMetadata,
  fetchIntegrationLogs,
  toggleSimulationDrift,
  fetchDataWorkbenchSources,
  fetchMetadataDrift,
  fetchImportQueue,
  addImportToQueue,
  retryImport,
  // wiki
  getSettings,
  updateSettings,
  fetchGlossaryTerms,
  kbFetchGlossaryTerms,
  createGlossaryTerm,
  updateGlossaryTerm,
  deleteGlossaryTerm,
  kbCreateGlossaryTerm,
  kbUpdateGlossaryTerm,
  kbDeleteGlossaryTerm,
  runRAGQuery,
  kbRunRAGQuery,
  runKnowledgeQuery,
  kbFetchNavProducts,
  kbFetchNavCategories,
  fetchRules,
  kbFetchRules,
  createRule,
  updateRule,
  deleteRule,
  fetchRuleVersions,
  kbFetchRuleVersions,
  // govern
  kbGenerateProfiles,
  kbFetchProfiles,
  kbPublishProfile,
  kbResolveProfile,
  kbCreateAssumption,
  kbFetchAssumptions,
  kbSubmitAssumption,
  kbApproveAssumption,
  kbResolveAssumptions,
  fetchEvalSeeds,
  kbFetchEvalSeeds,
  uploadEvalSeed,
  runEval,
  kbRunEval,
  runRAGQuerySSE,
};

// ── Wave 3 C1 api 导出（保留：原 knowledgeApi.ts:1201 相同 export name） ───────
export const wave3c1C1Api = {
  fetchOntologyTree,
  fetchExtractJobs,
  fetchExtractJobStatus,
  fetchScheduledExtracts,
  createScheduledExtractRow,
  updateScheduledExtractRow,
  deleteScheduledExtract,
  fetchExtractLogs,
  exportExtractLog,
};
