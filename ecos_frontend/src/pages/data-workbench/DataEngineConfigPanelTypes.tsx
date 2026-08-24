/**
 * DataEngineConfigPanelTypes — 配置类型定义与分组构建
 * 从 DataEngineConfigPanel 拆分而来，逻辑不变
 * @license Apache-2.0
 */
import React from 'react';
import {
  Cpu, Database, HardDrive, Workflow, ShieldCheck, GitBranch, Wrench,
} from 'lucide-react';

// ── Types ────────────────────────────────────────────────────

export interface ConfigItem {
  key: string;
  label: string;
  type: 'string' | 'int' | 'float' | 'bool' | 'enum' | 'password';
  defaultValue: string | number | boolean;
  description: string;
  options?: string[]; // for enum type
  sensitive?: boolean;
  subgroup: string;
}

export interface ConfigGroup {
  id: string;       // subgroup key, e.g. "execution"
  label: string;    // Chinese label, e.g. "执行引擎"
  icon: React.ReactNode;
  items: ConfigItem[];
  modified: boolean;
}

export interface ConfigValues {
  [key: string]: string | number | boolean;
}

export interface DefaultValues {
  [key: string]: string | number | boolean;
}

// ── Config Item Definitions ──────────────────────────────────

export function buildConfigGroups(): ConfigGroup[] {
  return [
    {
      id: 'execution',
      label: '执行引擎',
      icon: <Cpu size={15} />,
      items: [
        { key: 'dw.execution.mode', label: '执行模式', type: 'enum', defaultValue: 'memory', description: 'memory=内存模式, doris=Apache Doris', options: ['memory', 'doris'], subgroup: 'execution' },
        { key: 'dw.execution.memory.max_rows', label: '内存最大处理行数', type: 'int', defaultValue: 100000, description: '内存模式最大处理行数', subgroup: 'execution' },
        { key: 'dw.execution.memory.threads', label: '并行线程数', type: 'int', defaultValue: 4, description: '内存模式并行线程数', subgroup: 'execution' },
        { key: 'dw.execution.doris.host', label: 'Doris FE 地址', type: 'string', defaultValue: 'localhost', description: 'Doris FE 地址', subgroup: 'execution' },
        { key: 'dw.execution.doris.port', label: 'Doris 端口', type: 'int', defaultValue: 9030, description: 'Doris MySQL 协议端口', subgroup: 'execution' },
        { key: 'dw.execution.doris.user', label: 'Doris 用户名', type: 'string', defaultValue: 'root', description: 'Doris 用户名', subgroup: 'execution' },
        { key: 'dw.execution.doris.database', label: 'Doris 默认库', type: 'string', defaultValue: 'ecos_dw', description: 'Doris 默认库', subgroup: 'execution' },
        { key: 'dw.execution.doris.batch_size', label: 'Doris 批量写入行数', type: 'int', defaultValue: 10000, description: 'Doris 批量写入行数', subgroup: 'execution' },
        { key: 'dw.execution.timeout', label: '任务超时(秒)', type: 'int', defaultValue: 600, description: '任务超时秒数', subgroup: 'execution' },
      ],
      modified: false,
    },
    {
      id: 'data-lake',
      label: '数据湖',
      icon: <Database size={15} />,
      items: [
        { key: 'dw.lake.enabled', label: '启用数据湖', type: 'bool', defaultValue: false, description: '是否启用数据湖', subgroup: 'data-lake' },
        { key: 'dw.lake.datasource_id', label: '目标数据源 ID', type: 'string', defaultValue: '', description: '数据湖目标数据源 ID', subgroup: 'data-lake' },
        { key: 'dw.lake.storage_format', label: '存储格式', type: 'enum', defaultValue: 'parquet', description: 'parquet/orc/avro', options: ['parquet', 'orc', 'avro'], subgroup: 'data-lake' },
        { key: 'dw.lake.partition_by', label: '默认分区字段', type: 'string', defaultValue: 'dt', description: '默认分区字段', subgroup: 'data-lake' },
        { key: 'dw.lake.retention_days', label: '数据保留天数', type: 'int', defaultValue: 90, description: '数据保留天数', subgroup: 'data-lake' },
      ],
      modified: false,
    },
    {
      id: 'object-storage',
      label: '对象存储',
      icon: <HardDrive size={15} />,
      items: [
        { key: 'dw.storage.type', label: '存储类型', type: 'enum', defaultValue: 'minio', description: 'minio/s3/oss', options: ['minio', 's3', 'oss'], subgroup: 'object-storage' },
        { key: 'dw.storage.minio.endpoint', label: 'MinIO 地址', type: 'string', defaultValue: 'http://localhost:9000', description: 'MinIO 服务地址', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.access_key', label: 'Access Key', type: 'string', defaultValue: 'minioadmin', description: 'MinIO Access Key', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.secret_key', label: 'Secret Key', type: 'password', defaultValue: 'minioadmin', description: 'MinIO Secret Key（加密存储）', sensitive: true, subgroup: 'object-storage' },
        { key: 'dw.storage.minio.bucket', label: '默认 Bucket', type: 'string', defaultValue: 'ecos-data', description: '默认 Bucket 名称', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.region', label: '区域', type: 'string', defaultValue: 'us-east-1', description: '区域标识', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.ssl', label: '启用 SSL', type: 'bool', defaultValue: false, description: '是否启用 SSL', subgroup: 'object-storage' },
      ],
      modified: false,
    },
    {
      id: 'pipeline',
      label: '管道',
      icon: <Workflow size={15} />,
      items: [
        { key: 'dw.pipeline.max_steps', label: '最大步骤数', type: 'int', defaultValue: 20, description: '单个管道最大步骤数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.parallel_steps', label: '并行步骤数', type: 'int', defaultValue: 4, description: '允许的并行步骤数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.default_chunk_size', label: '分块行数', type: 'int', defaultValue: 10000, description: '默认分块行数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_prefix', label: '临时表前缀', type: 'string', defaultValue: 'ecos_tmp_', description: '临时表前缀', subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_ttl_hours', label: '临时表过期(小时)', type: 'int', defaultValue: 24, description: '临时表过期时间（小时）', subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_max', label: '重试次数', type: 'int', defaultValue: 3, description: '步骤默认重试次数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_backoff_ms', label: '重试间隔(毫秒)', type: 'int', defaultValue: 5000, description: '重试间隔（毫秒）', subgroup: 'pipeline' },
      ],
      modified: false,
    },
    {
      id: 'quality',
      label: '数据质量',
      icon: <ShieldCheck size={15} />,
      items: [
        { key: 'dw.quality.sample_rate', label: '采样率', type: 'float', defaultValue: 1.0, description: '采样率（0.0–1.0）', subgroup: 'quality' },
        { key: 'dw.quality.sample_max_rows', label: '采样最大行数', type: 'int', defaultValue: 1000000, description: '采样最大行数', subgroup: 'quality' },
        { key: 'dw.quality.stale_threshold_hours', label: '过期阈值(小时)', type: 'int', defaultValue: 24, description: '数据过期阈值（小时）', subgroup: 'quality' },
        { key: 'dw.quality.default_alert_score', label: '告警分数阈值', type: 'int', defaultValue: 80, description: '默认告警分数阈值', subgroup: 'quality' },
        { key: 'dw.quality.concurrent_checks', label: '并发检查数', type: 'int', defaultValue: 2, description: '并发检查任务数', subgroup: 'quality' },
        { key: 'dw.quality.check_timeout', label: '检查超时(秒)', type: 'int', defaultValue: 300, description: '单次检查超时秒数', subgroup: 'quality' },
      ],
      modified: false,
    },
    {
      id: 'lineage',
      label: '血缘',
      icon: <GitBranch size={15} />,
      items: [
        { key: 'dw.lineage.enabled', label: '启用血缘', type: 'bool', defaultValue: true, description: '是否启用血缘采集', subgroup: 'lineage' },
        { key: 'dw.lineage.parser', label: '解析引擎', type: 'enum', defaultValue: 'sql', description: '血缘解析引擎', options: ['sql', 'spark', 'dbt'], subgroup: 'lineage' },
        { key: 'dw.lineage.max_depth', label: '最大追溯深度', type: 'int', defaultValue: 10, description: '最大追溯深度', subgroup: 'lineage' },
        { key: 'dw.lineage.cache_ttl_minutes', label: '缓存时间(分钟)', type: 'int', defaultValue: 30, description: '血缘缓存时间', subgroup: 'lineage' },
        { key: 'dw.lineage.neo4j_enabled', label: 'Neo4j 图存储', type: 'bool', defaultValue: false, description: '是否启用 Neo4j 图存储', subgroup: 'lineage' },
      ],
      modified: false,
    },
    {
      id: 'general',
      label: '通用',
      icon: <Wrench size={15} />,
      items: [
        { key: 'dw.sync.batch_size', label: '同步批次大小', type: 'int', defaultValue: 5000, description: '同步任务默认批次大小', subgroup: 'general' },
        { key: 'dw.sync.max_retries', label: '同步最大重试', type: 'int', defaultValue: 3, description: '同步任务最大重试', subgroup: 'general' },
        { key: 'dw.query.max_rows', label: '查询最大行数', type: 'int', defaultValue: 10000, description: 'SQL 查询最大返回行数', subgroup: 'general' },
        { key: 'dw.query.timeout', label: '查询超时(秒)', type: 'int', defaultValue: 30, description: 'SQL 查询超时秒数', subgroup: 'general' },
        { key: 'dw.cache.ttl_seconds', label: '缓存时间(秒)', type: 'int', defaultValue: 300, description: '元数据缓存时间', subgroup: 'general' },
        { key: 'dw.engine.auto_start', label: '自动启动引擎', type: 'bool', defaultValue: true, description: '启动时自动启动数据引擎', subgroup: 'general' },
      ],
      modified: false,
    },
  ];
}
