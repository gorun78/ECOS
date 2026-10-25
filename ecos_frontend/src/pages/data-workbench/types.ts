/**
 * Data Workbench — shared type definitions
 * Aligned with DataWorkbenchLayout.tsx actual usage patterns.
 * @license Apache-2.0
 */

export interface ObjectType {
  id: string;
  name: string;
  domain: string;
  displayName?: string;
  color?: string;
  icon?: string;
  properties?: Record<string, unknown>[];
}

export interface Dataset {
  id: string;
  name: string;
  path: string;
  columns?: string[];
  rowCount?: number;
  ontologyNodeId?: string;
}

export interface TableInfo {
  name: string;
  // B8: 后端 recordCount 可能 -1/ null, 前端用 null 表"未知"，UI 显示"未知"
  rowCount: number | null;
  columns: { name: string; type: string }[];
  // T3-2: 新增 — 表对应的 DataResource ID, 用于调 preview/{resourceId} 取字段明细
  resourceId?: string;
  // T3-2: 新增 — 后端 DataResource 附带信息
  sourcePath?: string;
  description?: string;
  fieldCount?: number;
}

export interface DataConnection {
  id: string;
  name: string;
  /**
   * #142/B4-3e-64（D-B4-93）：显示面取值含 `unknown`——后端类型名解析不到时
   * 显示「未知类型」并保留 `typeRaw`，禁回落成 PostgreSQL 这枚具体类型。
   */
  type: DisplayConnType;
  /** 后端 `datasourceType` 原文（`unknown` 时页面据此出「未知类型（原值 X）」） */
  typeRaw?: string;
  status: 'connected' | 'disconnected' | 'error' | 'pending' | 'testing';
  config: {
    host: string;
    port: number;
    database?: string;
    username?: string;
    password?: string;
    schema?: string;
    warehouse?: string;
    bucket?: string;
    endpointUrl?: string;
    role?: string;
    lastTested?: string;
    /** PMO-48-T5: 子类型(oracle 细分为 SID/SERVICE_NAME) */
    dbType?: string;
    /** PMO-48-T5: Doris FE 地址 */
    feHost?: string;
    /** PMO-48-T5: Doris FE HTTP 端口 */
    feHttpPort?: number;
    /** PMO-48-T5: MinIO 访问方式(STS → roleArn=arn:aws:iam:::assumed-role/...) */
    roleArn?: string;
    /** PMO-48-T5: MinIO 监听端口 */
    listenPort?: number;
    /** PMO-48-T5: 文件源匹配模式 */
    pattern?: string;
    /** PMO-48-T5: SFTP 密钥算法 */
    algo?: string;
    /** PMO-48-T5: SAP 应用服务器 */
    appServer?: string;
    /** PMO-48-T5: SAP 实例号 */
    instance?: string;
    /** PMO-48-T5: SAP 系统号 */
    sysNum?: string;
    /** PMO-48-T5: SAP 函数模块 */
    funcModule?: string;
    /** PMO-48-T5: REST API Base URL */
    baseUrl?: string;
    /** PMO-48-T5: REST API Key */
    apiKey?: string;
    /** PMO-48-T5: REST 客户端凭据 (id:secret) */
    clientCreds?: string;
    /** PMO-48-T5: 字符集 */
    charset?: string;
  };
  lastTested?: string;
  description?: string;
  category?: string;
  tablesAvailable: TableInfo[];
  // PMO-37 元数据获取策略（存储于 td_datasource.metadata_config JSONB）
  strategy?: { trigger?: 'MANUAL' | 'ON_SAVE' | 'ON_SCHEDULE'; countMethod?: 'OFF' | 'ESTIMATE' | 'EXACT'; scheduleCron?: string };
  metadataConfig?: Record<string, unknown>;
}

export type ConnType =
  | 'postgresql' | 'mysql' | 'doris' | 'clickhouse'
  | 'oracle' | 'mssql' | 'dm' | 'kingbase' | 'gaussdb'
  | 's3' | 'oss' | 'minio'
  | 'csv'
  | 'sftp'
  | 'sap'
  | 'rest_api'
  | 'kafka'
  | 'mongodb'
  | 'fs';

/**
 * #142/B4-3e-64（差距 <b>D-B4-93</b> 收口）—— 显示面取值。
 *
 * <p>改前 `mapDsType` 在 17 条 `includes` 分支之后<b>无条件 `return 'postgresql'`</b>：
 * 词表外名、空串（后端 `datasourceType` 为 null 时前端传 `''`）一律显示成 PostgreSQL，
 * 与同批收口的 `JdbcConnector`「无方言静默按 PG 拼 URL」同族（详设 §4.7.2
 * 「无配置＝按别族猜」）。现改为：解析不到即 `'unknown'`，页面出「未知类型（原值 X）」
 * 并保留后端原文，禁把「不知道」折成一个具体类型。</p>
 *
 * <p>{@link ConnType} 本身保持 19 枚不变——它是「前端可显示的类型名」面，受
 * `DatasourceTypeVocabularySingleSourceTest` 与属主目录对账；`unknown`
 * 不是类型，是「未解析」的读数，故不入 `CONNECTION_TYPES` 选项面。</p>
 */
export type DisplayConnType = ConnType | 'unknown';

/**
 * PMO-48-T5: 连接类型显示元数据面（分类排序: 关系型→国产→对象存储→文件→消息→API）。
 *
 * #142/B4-3e-64（D-B4-78 前端侧腿）：<b>本表不再是「可新建哪些数据源」的准入面。</b>
 * 准入面唯一属主 = 后端承流出口 `GET /api/v1/datanet/datasource/supported-types`
 * （数据单源 `DataSourceTypeVocabulary`）。下拉实际渲染的是
 * `deriveSupportedOptions()` 用属主目录裁剪本表后的子集；此前 19 枚里有 9 枚
 * （`doris/s3/oss/csv/sftp/kafka/mongodb/sap/rest_api`）后端不承流，用户选了只能撞 400
 * ——本批起该 9 枚不再进入下拉（显示元数据保留，用于历史行的标签）。
 */
export const CONNECTION_TYPES: { value: ConnType; i18nKey: string; tier: 'relational' | 'domestic' | 'object-storage' | 'file' | 'message' | 'api' }[] = [
  // 关系型
  { value: 'postgresql', i18nKey: 'dw.connType.postgresql', tier: 'relational' },
  { value: 'mysql', i18nKey: 'dw.connType.mysql', tier: 'relational' },
  { value: 'doris', i18nKey: 'dw.connType.doris', tier: 'relational' },
  { value: 'clickhouse', i18nKey: 'dw.connType.clickhouse', tier: 'relational' },
  { value: 'oracle', i18nKey: 'dw.connType.oracle', tier: 'relational' },
  { value: 'mssql', i18nKey: 'dw.connType.mssql', tier: 'relational' },
  // 国产库
  { value: 'dm', i18nKey: 'dw.connType.dm', tier: 'domestic' },
  { value: 'kingbase', i18nKey: 'dw.connType.kingbase', tier: 'domestic' },
  { value: 'gaussdb', i18nKey: 'dw.connType.gaussdb', tier: 'domestic' },
  // 对象存储
  { value: 's3', i18nKey: 'dw.connType.s3', tier: 'object-storage' },
  { value: 'oss', i18nKey: 'dw.connType.oss', tier: 'object-storage' },
  { value: 'minio', i18nKey: 'dw.connType.minio', tier: 'object-storage' },
  // 文件
  { value: 'csv', i18nKey: 'dw.connType.csv', tier: 'file' },
  { value: 'sftp', i18nKey: 'dw.connType.sftp', tier: 'file' },
  { value: 'fs', i18nKey: 'dw.connType.fs', tier: 'file' },
  // 消息/API
  { value: 'kafka', i18nKey: 'dw.connType.kafka', tier: 'message' },
  { value: 'mongodb', i18nKey: 'dw.connType.mongodb', tier: 'relational' },
  { value: 'sap', i18nKey: 'dw.connType.sap', tier: 'api' },
  { value: 'rest_api', i18nKey: 'dw.connType.restApi', tier: 'api' },
];

/** PMO-48-T5: 标准版禁用的类型(旗舰/企业专属)。fs(本地文件/文件夹数据源) 全版本可用，不在此列。 */
export const STANDARD_DISABLED_TYPES: ConnType[] = ['gaussdb', 'minio'];

/** PMO-48-T5: 标签 i18n key 前缀 */
export const CONN_TYPE_LABEL_PREFIX = 'dw.connType.';

export interface DataSyncTask {
  id: string;
  name: string;
  sourceConnectionId: string;
  targetTable?: string;
  sourceTable?: string;
  targetDatasetId?: string;
  status: 'active' | 'paused' | 'running' | 'error' | 'completed' | 'success' | 'failed';
  schedule?: string;
  cronExpression?: string;
  lastRun?: string;
  lastRunTime?: string;
  rowsSynced?: number;
  recordsSynced?: number;
  errorMessage?: string;
  description?: string;
  syncMode?: 'full' | 'incremental' | 'cdc' | 'snapshot' | 'append';
  taskType?: 'TRANSFORM' | 'SYNC' | 'LAKE_EXPORT';
  durationMs?: number;
}

export interface PipelineNode {
  id: string;
  type: string;
  name?: string;
  recordCount?: number;
  outputColumns?: { name: string; type: string }[];
  left?: string;
  right?: string;
  join?: { type: string; on: string[] };
  inputs?: string[];
  config?: Record<string, unknown>;
}

export interface DataPipeline {
  id: string;
  name: string;
  status: 'active' | 'draft' | 'running' | 'success' | 'error';
  lastExecuted?: string;
  sourceConnections?: string[];
  targetDataset?: string;
  description?: string;
  expressionsCount?: number;
  computeEngine?: 'doris' | 'memory';
  nodes?: PipelineNode[];
}

// ── Pipeline Builder ──────────────────────────────────────────────

/** 转换规则 */
export interface TransformRule {
  id: string;
  functionName: string;
  column: string;
  args: string[];
  description: string;
}

/** ReactFlow 节点的 data 类型 */
export interface PipelineNodeData {
  nodeType: 'source' | 'transform' | 'join' | 'aggregate' | 'sink';
  label: string;
  status: 'idle' | 'running' | 'success' | 'error';
  sourceTable?: string;
  targetDataset?: string;
  transforms?: TransformRule[];
  joinCondition?: string;
  groupByColumns?: string[];
  engine?: 'memory' | 'doris';
}

/** 保存 Pipeline 时的数据格式 */
export interface PipelineSaveData {
  id?: string;
  name: string;
  description?: string;
  nodes: PipelineNodeData[];
  edges: {
    source: string;
    target: string;
    sourceHandle?: string;
    targetHandle?: string;
  }[];
  computeEngine: 'memory' | 'doris';
}

// ── Lineage ───────────────────────────────────────────────────────

/** 血缘节点 data 类型 */
export interface LineageNodeData {
  phase: 1 | 2 | 3 | 4 | 5;
  label: string;
  sublabel?: string;
  status?: string;
  metadata?: Record<string, unknown>;
}

/** 信息面板 tab 枚举 */
export type LineageInfoTab = 'info' | 'relationships' | 'transforms' | 'execution';
