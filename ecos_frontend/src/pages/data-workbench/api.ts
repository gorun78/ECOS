/**
 * Data Workbench — backend API layer
 * 对接 databridge-v2 真实后端，含字段映射适配。
 * @license Apache-2.0
 *
 * 说明（W66 / B.7）：本文件已拆分为按域 thin barrel。
 * 函数体分别落在：
 *   - httpClient.ts     共享 fetch/JSON 助手（get/post/put/del + authHeaders 单源）
 *   - apiCommon.ts      跨域共享类型/映射
 *   - apiDatasource.ts  数据源 + 元数据采集/版本/预览/字段/资源
 *   - apiPipeline.ts    管道 CRUD/执行 + 血缘 + 同步任务
 *   - apiMetadata.ts    Schema Preview + 数据采集(ingest)/数据湖 + 非结构化 + 文件夹
 * 本文件仅 re-export，供既有 `import { ... } from './api'` 消费方与 api.test.ts 继续使用。
 */

export {
  fetchDataConnections,
  createDataSource,
  testDataSource,
  updateDataSource,
  deleteDataSource,
  testDataSourceRaw,
  fetchDataSourceResources,
  triggerMetadataCollect,
  fetchCollectStatus,
  triggerCollectSync,
  fetchActiveCollectTasks,
  fetchCollectDiff,
  fetchVersionHistory,
  fetchVersionDiff,
  fetchPreview,
  fetchFields,
  saveMetadataStrategy,
  type MetadataVersion,
  type VersionDiffRow,
  type VersionDiffResult,
  type DataFieldMeta,
} from './apiDatasource';

export {
  fetchDataSyncTasks,
  fetchDataPipelines,
  getPipelineDefinition,
  createPipeline,
  updatePipeline,
  savePipelineDefinition,
  deletePipeline,
  executePipeline,
  fetchLineageTopology,
  fetchLineageImpact,
  rebuildLineage,
  fetchSyncTasksFromPipeline,
  createSyncTask,
  triggerSyncRun,
  type PipelineSavePayload,
  type LineageTopologyNode,
  type LineageTopologyEdge,
  type LineageTopology,
} from './apiPipeline';

export {
  buildPreviewSchemaPayload,
  fetchSchemaPreview,
  fetchIngestTargets,
  runDataIngest,
  fetchIngestStatus,
  saveIngestSchedule,
  fetchIngestSchedule,
  fetchDatalakeStatus,
  initDatalake,
  uploadUnstructured,
  listFolderFiles,
  collectFolderFiles,
  type SchemaField,
  type IngestTarget,
  type IngestTaskItem,
  type UnstructuredUploadOptions,
  type UnstructuredUploadResult,
  type FolderFileVo,
  type FolderCollectReq,
  type FolderCollectResult,
} from './apiMetadata';
