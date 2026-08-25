/**
 * DataEngineConfigPanelTypes — 配置类型定义与分组构建
 * 从 DataEngineConfigPanel 拆分而来。
 * PMO-3J-T6: label/description 走 i18nKey；Doris 配置组三版本感知（standard 灰显）。
 * @license Apache-2.0
 */
import React from 'react';
import {
  Cpu, Database, HardDrive, Workflow, ShieldCheck, GitBranch, Wrench,
} from 'lucide-react';

// ── Types ────────────────────────────────────────────────────

/** ECOS 产品版本：standard（标准）/ enterprise（企业）/ flagship（旗舰） */
export type EcosEdition = 'standard' | 'enterprise' | 'flagship';

export interface ConfigItem {
  key: string;
  /** i18n key for the item label, resolved at render time via t() */
  labelKey: string;
  /** i18n key for the item description, resolved at render time via t() */
  descriptionKey: string;
  type: 'string' | 'int' | 'float' | 'bool' | 'enum' | 'password';
  defaultValue: string | number | boolean;
  options?: string[]; // for enum type
  sensitive?: boolean;
  subgroup: string;
  /** 运行时灰显标记（如 standard 版 Doris 配置） */
  disabled?: boolean;
  /** 灰显原因的 i18n key */
  disabledReasonKey?: string;
}

export interface ConfigGroup {
  id: string;       // subgroup key, e.g. "execution"
  /** i18n key for the group label, resolved at render time via t() */
  labelKey: string;
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

// ── Edition detection ─────────────────────────────────────────

/**
 * 探测当前 ECOS 产品版本。
 * 优先级：import.meta.env.VITE_ECOS_VERSION → 默认 standard。
 * 后端 /api/v1/engine/data/settings 当前不含版本字段，故以前端环境变量为准。
 */
export function detectEdition(): EcosEdition {
  // import.meta.env 类型在当前 tsconfig 下未声明（无 vite/client 引用），
  // 故用安全 cast 读取；构建期 Vite 会静态替换 VITE_ECOS_VERSION。
  const env = (import.meta as unknown as { env?: Record<string, string | undefined> }).env;
  const raw = env?.VITE_ECOS_VERSION;
  const v = (raw || 'standard').toLowerCase();
  if (v === 'enterprise' || v === 'flagship' || v === 'standard') return v;
  return 'standard';
}

/** Doris 配置项的 key 前缀，standard 版需灰显 */
const DORIS_KEY_PREFIX = 'dw.execution.doris.';
/** Doris 灰显原因 i18n key */
const DORIS_DISABLED_REASON_KEY = 'dw.cfg.doris.disabledReason';

// ── Config Item Definitions ──────────────────────────────────

/**
 * 构建配置分组。
 * @param edition 当前产品版本；standard 版时 Doris 配置项灰显。
 */
export function buildConfigGroups(edition: EcosEdition = 'standard'): ConfigGroup[] {
  const isDorisDisabled = edition === 'standard';

  // 给 Doris 配置项附加 disabled + disabledReasonKey（standard 版）
  const dorisItem = (
    key: string,
    labelKey: string,
    descriptionKey: string,
    type: ConfigItem['type'],
    defaultValue: string | number | boolean,
  ): ConfigItem => ({
    key,
    labelKey,
    descriptionKey,
    type,
    defaultValue,
    subgroup: 'execution',
    disabled: isDorisDisabled ? true : undefined,
    disabledReasonKey: isDorisDisabled ? DORIS_DISABLED_REASON_KEY : undefined,
  });

  return [
    {
      id: 'execution',
      labelKey: 'dw.cfg.group.execution',
      icon: <Cpu size={15} />,
      items: [
        { key: 'dw.execution.mode', labelKey: 'dw.cfg.label.dw.execution.mode', descriptionKey: 'dw.cfg.desc.dw.execution.mode', type: 'enum', defaultValue: 'memory', options: ['memory', 'doris'], subgroup: 'execution' },
        { key: 'dw.execution.memory.max_rows', labelKey: 'dw.cfg.label.dw.execution.memory.max_rows', descriptionKey: 'dw.cfg.desc.dw.execution.memory.max_rows', type: 'int', defaultValue: 100000, subgroup: 'execution' },
        { key: 'dw.execution.memory.threads', labelKey: 'dw.cfg.label.dw.execution.memory.threads', descriptionKey: 'dw.cfg.desc.dw.execution.memory.threads', type: 'int', defaultValue: 4, subgroup: 'execution' },
        dorisItem('dw.execution.doris.host', 'dw.cfg.label.dw.execution.doris.host', 'dw.cfg.desc.dw.execution.doris.host', 'string', 'localhost'),
        dorisItem('dw.execution.doris.port', 'dw.cfg.label.dw.execution.doris.port', 'dw.cfg.desc.dw.execution.doris.port', 'int', 9030),
        dorisItem('dw.execution.doris.user', 'dw.cfg.label.dw.execution.doris.user', 'dw.cfg.desc.dw.execution.doris.user', 'string', 'root'),
        dorisItem('dw.execution.doris.database', 'dw.cfg.label.dw.execution.doris.database', 'dw.cfg.desc.dw.execution.doris.database', 'string', 'ecos_dw'),
        dorisItem('dw.execution.doris.batch_size', 'dw.cfg.label.dw.execution.doris.batch_size', 'dw.cfg.desc.dw.execution.doris.batch_size', 'int', 10000),
        { key: 'dw.execution.timeout', labelKey: 'dw.cfg.label.dw.execution.timeout', descriptionKey: 'dw.cfg.desc.dw.execution.timeout', type: 'int', defaultValue: 600, subgroup: 'execution' },
      ],
      modified: false,
    },
    {
      id: 'data-lake',
      labelKey: 'dw.cfg.group.data-lake',
      icon: <Database size={15} />,
      items: [
        { key: 'dw.lake.enabled', labelKey: 'dw.cfg.label.dw.lake.enabled', descriptionKey: 'dw.cfg.desc.dw.lake.enabled', type: 'bool', defaultValue: false, subgroup: 'data-lake' },
        { key: 'dw.lake.datasource_id', labelKey: 'dw.cfg.label.dw.lake.datasource_id', descriptionKey: 'dw.cfg.desc.dw.lake.datasource_id', type: 'string', defaultValue: '', subgroup: 'data-lake' },
        { key: 'dw.lake.storage_format', labelKey: 'dw.cfg.label.dw.lake.storage_format', descriptionKey: 'dw.cfg.desc.dw.lake.storage_format', type: 'enum', defaultValue: 'parquet', options: ['parquet', 'orc', 'avro'], subgroup: 'data-lake' },
        { key: 'dw.lake.partition_by', labelKey: 'dw.cfg.label.dw.lake.partition_by', descriptionKey: 'dw.cfg.desc.dw.lake.partition_by', type: 'string', defaultValue: 'dt', subgroup: 'data-lake' },
        { key: 'dw.lake.retention_days', labelKey: 'dw.cfg.label.dw.lake.retention_days', descriptionKey: 'dw.cfg.desc.dw.lake.retention_days', type: 'int', defaultValue: 90, subgroup: 'data-lake' },
      ],
      modified: false,
    },
    {
      id: 'object-storage',
      labelKey: 'dw.cfg.group.object-storage',
      icon: <HardDrive size={15} />,
      items: [
        { key: 'dw.storage.type', labelKey: 'dw.cfg.label.dw.storage.type', descriptionKey: 'dw.cfg.desc.dw.storage.type', type: 'enum', defaultValue: 'minio', options: ['minio', 's3', 'oss'], subgroup: 'object-storage' },
        { key: 'dw.storage.minio.endpoint', labelKey: 'dw.cfg.label.dw.storage.minio.endpoint', descriptionKey: 'dw.cfg.desc.dw.storage.minio.endpoint', type: 'string', defaultValue: 'http://localhost:9000', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.access_key', labelKey: 'dw.cfg.label.dw.storage.minio.access_key', descriptionKey: 'dw.cfg.desc.dw.storage.minio.access_key', type: 'string', defaultValue: 'minioadmin', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.secret_key', labelKey: 'dw.cfg.label.dw.storage.minio.secret_key', descriptionKey: 'dw.cfg.desc.dw.storage.minio.secret_key', type: 'password', defaultValue: 'minioadmin', sensitive: true, subgroup: 'object-storage' },
        { key: 'dw.storage.minio.bucket', labelKey: 'dw.cfg.label.dw.storage.minio.bucket', descriptionKey: 'dw.cfg.desc.dw.storage.minio.bucket', type: 'string', defaultValue: 'ecos-data', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.region', labelKey: 'dw.cfg.label.dw.storage.minio.region', descriptionKey: 'dw.cfg.desc.dw.storage.minio.region', type: 'string', defaultValue: 'us-east-1', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.ssl', labelKey: 'dw.cfg.label.dw.storage.minio.ssl', descriptionKey: 'dw.cfg.desc.dw.storage.minio.ssl', type: 'bool', defaultValue: false, subgroup: 'object-storage' },
      ],
      modified: false,
    },
    {
      id: 'pipeline',
      labelKey: 'dw.cfg.group.pipeline',
      icon: <Workflow size={15} />,
      items: [
        { key: 'dw.pipeline.max_steps', labelKey: 'dw.cfg.label.dw.pipeline.max_steps', descriptionKey: 'dw.cfg.desc.dw.pipeline.max_steps', type: 'int', defaultValue: 20, subgroup: 'pipeline' },
        { key: 'dw.pipeline.parallel_steps', labelKey: 'dw.cfg.label.dw.pipeline.parallel_steps', descriptionKey: 'dw.cfg.desc.dw.pipeline.parallel_steps', type: 'int', defaultValue: 4, subgroup: 'pipeline' },
        { key: 'dw.pipeline.default_chunk_size', labelKey: 'dw.cfg.label.dw.pipeline.default_chunk_size', descriptionKey: 'dw.cfg.desc.dw.pipeline.default_chunk_size', type: 'int', defaultValue: 10000, subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_prefix', labelKey: 'dw.cfg.label.dw.pipeline.temp_table_prefix', descriptionKey: 'dw.cfg.desc.dw.pipeline.temp_table_prefix', type: 'string', defaultValue: 'ecos_tmp_', subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_ttl_hours', labelKey: 'dw.cfg.label.dw.pipeline.temp_table_ttl_hours', descriptionKey: 'dw.cfg.desc.dw.pipeline.temp_table_ttl_hours', type: 'int', defaultValue: 24, subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_max', labelKey: 'dw.cfg.label.dw.pipeline.retry_max', descriptionKey: 'dw.cfg.desc.dw.pipeline.retry_max', type: 'int', defaultValue: 3, subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_backoff_ms', labelKey: 'dw.cfg.label.dw.pipeline.retry_backoff_ms', descriptionKey: 'dw.cfg.desc.dw.pipeline.retry_backoff_ms', type: 'int', defaultValue: 5000, subgroup: 'pipeline' },
      ],
      modified: false,
    },
    {
      id: 'quality',
      labelKey: 'dw.cfg.group.quality',
      icon: <ShieldCheck size={15} />,
      items: [
        { key: 'dw.quality.sample_rate', labelKey: 'dw.cfg.label.dw.quality.sample_rate', descriptionKey: 'dw.cfg.desc.dw.quality.sample_rate', type: 'float', defaultValue: 1.0, subgroup: 'quality' },
        { key: 'dw.quality.sample_max_rows', labelKey: 'dw.cfg.label.dw.quality.sample_max_rows', descriptionKey: 'dw.cfg.desc.dw.quality.sample_max_rows', type: 'int', defaultValue: 1000000, subgroup: 'quality' },
        { key: 'dw.quality.stale_threshold_hours', labelKey: 'dw.cfg.label.dw.quality.stale_threshold_hours', descriptionKey: 'dw.cfg.desc.dw.quality.stale_threshold_hours', type: 'int', defaultValue: 24, subgroup: 'quality' },
        { key: 'dw.quality.default_alert_score', labelKey: 'dw.cfg.label.dw.quality.default_alert_score', descriptionKey: 'dw.cfg.desc.dw.quality.default_alert_score', type: 'int', defaultValue: 80, subgroup: 'quality' },
        { key: 'dw.quality.concurrent_checks', labelKey: 'dw.cfg.label.dw.quality.concurrent_checks', descriptionKey: 'dw.cfg.desc.dw.quality.concurrent_checks', type: 'int', defaultValue: 2, subgroup: 'quality' },
        { key: 'dw.quality.check_timeout', labelKey: 'dw.cfg.label.dw.quality.check_timeout', descriptionKey: 'dw.cfg.desc.dw.quality.check_timeout', type: 'int', defaultValue: 300, subgroup: 'quality' },
      ],
      modified: false,
    },
    {
      id: 'lineage',
      labelKey: 'dw.cfg.group.lineage',
      icon: <GitBranch size={15} />,
      items: [
        { key: 'dw.lineage.enabled', labelKey: 'dw.cfg.label.dw.lineage.enabled', descriptionKey: 'dw.cfg.desc.dw.lineage.enabled', type: 'bool', defaultValue: true, subgroup: 'lineage' },
        { key: 'dw.lineage.parser', labelKey: 'dw.cfg.label.dw.lineage.parser', descriptionKey: 'dw.cfg.desc.dw.lineage.parser', type: 'enum', defaultValue: 'sql', options: ['sql', 'spark', 'dbt'], subgroup: 'lineage' },
        { key: 'dw.lineage.max_depth', labelKey: 'dw.cfg.label.dw.lineage.max_depth', descriptionKey: 'dw.cfg.desc.dw.lineage.max_depth', type: 'int', defaultValue: 10, subgroup: 'lineage' },
        { key: 'dw.lineage.cache_ttl_minutes', labelKey: 'dw.cfg.label.dw.lineage.cache_ttl_minutes', descriptionKey: 'dw.cfg.desc.dw.lineage.cache_ttl_minutes', type: 'int', defaultValue: 30, subgroup: 'lineage' },
        { key: 'dw.lineage.neo4j_enabled', labelKey: 'dw.cfg.label.dw.lineage.neo4j_enabled', descriptionKey: 'dw.cfg.desc.dw.lineage.neo4j_enabled', type: 'bool', defaultValue: false, subgroup: 'lineage' },
      ],
      modified: false,
    },
    {
      id: 'general',
      labelKey: 'dw.cfg.group.general',
      icon: <Wrench size={15} />,
      items: [
        { key: 'dw.sync.batch_size', labelKey: 'dw.cfg.label.dw.sync.batch_size', descriptionKey: 'dw.cfg.desc.dw.sync.batch_size', type: 'int', defaultValue: 5000, subgroup: 'general' },
        { key: 'dw.sync.max_retries', labelKey: 'dw.cfg.label.dw.sync.max_retries', descriptionKey: 'dw.cfg.desc.dw.sync.max_retries', type: 'int', defaultValue: 3, subgroup: 'general' },
        { key: 'dw.query.max_rows', labelKey: 'dw.cfg.label.dw.query.max_rows', descriptionKey: 'dw.cfg.desc.dw.query.max_rows', type: 'int', defaultValue: 10000, subgroup: 'general' },
        { key: 'dw.query.timeout', labelKey: 'dw.cfg.label.dw.query.timeout', descriptionKey: 'dw.cfg.desc.dw.query.timeout', type: 'int', defaultValue: 30, subgroup: 'general' },
        { key: 'dw.cache.ttl_seconds', labelKey: 'dw.cfg.label.dw.cache.ttl_seconds', descriptionKey: 'dw.cfg.desc.dw.cache.ttl_seconds', type: 'int', defaultValue: 300, subgroup: 'general' },
        { key: 'dw.engine.auto_start', labelKey: 'dw.cfg.label.dw.engine.auto_start', descriptionKey: 'dw.cfg.desc.dw.engine.auto_start', type: 'bool', defaultValue: true, subgroup: 'general' },
      ],
      modified: false,
    },
  ];
}
