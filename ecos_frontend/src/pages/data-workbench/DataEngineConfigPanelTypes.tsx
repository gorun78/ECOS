/**
 * DataEngineConfigPanelTypes — 配置类型定义与单项元数据
 * 从 DataEngineConfigPanel 拆分而来。
 *
 * 分层归属（段 / 子分区）由后端 GET /api/v1/engine/data/settings/groups 决定，
 * 本文件只保留「单项元数据」（控件类型 / 默认值 / 枚举 / 灰显），避免分组双真相源。
 * PMO-3J-T6: label/description 走 i18nKey；Doris 配置项三版本感知（standard 灰显）。
 * @license Apache-2.0
 */
import React from 'react';
import { Database, Layers, Boxes, Wrench } from 'lucide-react';

// ── Types ────────────────────────────────────────────────────

/** ECOS 产品版本：standard（标准）/ enterprise（企业）/ flagship（旗舰） */
export type EcosEdition = 'standard' | 'enterprise' | 'flagship';

/** 单项配置的类型元数据（不含分组信息 —— 分组由后端 /groups 驱动） */
export interface ConfigItemMeta {
  type: 'string' | 'int' | 'float' | 'bool' | 'enum' | 'password';
  defaultValue: string | number | boolean;
  /** enum 类型的候选值 */
  options?: string[];
  sensitive?: boolean;
  /** 运行时灰显标记（如 standard 版 Doris 配置） */
  disabled?: boolean;
  /** 灰显原因的 i18n key */
  disabledReasonKey?: string;
}

/** 面板中的单项配置 = 元数据（本地定义）+ 归属（后端注入） */
export interface ConfigItem extends ConfigItemMeta {
  /** config_key */
  key: string;
  /** 项标签 i18n key */
  labelKey: string;
  /** 项描述 i18n key */
  descriptionKey: string;
  /** 所属子分区 id（后端 /groups 注入），用于段内分区渲染 */
  subgroup: string;
}

/** 段内子分区声明（顺序即渲染顺序） */
export interface ConfigSubGroup {
  id: string;
  /** 子分区标签 i18n key */
  labelKey: string;
}

export interface ConfigGroup {
  /** 段 id：near-source | dw | semantic | global */
  id: string;
  /** 段标签 i18n key */
  labelKey: string;
  icon: React.ReactNode;
  /** 段内子分区；空数组时按 items 平铺渲染 */
  subgroups: ConfigSubGroup[];
  items: ConfigItem[];
  modified: boolean;
}

export interface ConfigValues {
  [key: string]: string | number | boolean;
}

export interface DefaultValues {
  [key: string]: string | number | boolean;
}

// ── i18n key 生成器 ───────────────────────────────────────────

/** 段标签 i18n key */
export const groupLabelKey = (segmentId: string): string => `dw.cfg.group.${segmentId}`;

/** 子分区标签 i18n key */
export const subgroupLabelKey = (subgroupId: string): string => `dw.cfg.subgroup.${subgroupId}`;

/** 配置项标签 i18n key */
export const itemLabelKey = (configKey: string): string => `dw.cfg.label.${configKey}`;

/** 配置项描述 i18n key */
export const itemDescriptionKey = (configKey: string): string => `dw.cfg.desc.${configKey}`;

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

/** Doris 灰显原因 i18n key */
const DORIS_DISABLED_REASON_KEY = 'dw.cfg.doris.disabledReason';

// ── 段图标 ────────────────────────────────────────────────────

/** 段 id → 图标（图标仅用 lucide-react；未知段回退 Wrench） */
const SEGMENT_ICONS: Record<string, React.ReactNode> = {
  'near-source': <Database size={15} />,
  dw: <Layers size={15} />,
  semantic: <Boxes size={15} />,
  global: <Wrench size={15} />,
};

/** 取段图标 */
export function segmentIcon(segmentId: string): React.ReactNode {
  return SEGMENT_ICONS[segmentId] ?? <Wrench size={15} />;
}

// ── 元数据构造器 ──────────────────────────────────────────────

const bool = (defaultValue: boolean): ConfigItemMeta => ({ type: 'bool', defaultValue });
const int = (defaultValue: number): ConfigItemMeta => ({ type: 'int', defaultValue });
const float = (defaultValue: number): ConfigItemMeta => ({ type: 'float', defaultValue });
const text = (defaultValue = ''): ConfigItemMeta => ({ type: 'string', defaultValue });
const enumOf = (defaultValue: string, options: string[]): ConfigItemMeta =>
  ({ type: 'enum', defaultValue, options });
const password = (defaultValue: string): ConfigItemMeta =>
  ({ type: 'password', defaultValue, sensitive: true });

// ── 68 项单项元数据（与后端 DataEngineConfigController.DEFAULTS 对齐） ──

/**
 * 构建单项元数据表：config_key → ConfigItemMeta。
 * 数量与后端 DEFAULTS 一一对应（68 项），后端返回的每个 key 都能取到元数据。
 *
 * @param edition 当前产品版本；standard 版时 Doris 配置项灰显。
 */
export function buildItemMeta(edition: EcosEdition = 'standard'): Record<string, ConfigItemMeta> {
  const meta: Record<string, ConfigItemMeta> = {};
  const put = (key: string, m: ConfigItemMeta): void => {
    meta[key] = m;
  };
  /** standard 版灰显包装（Doris 配置项） */
  const doris = (m: ConfigItemMeta): ConfigItemMeta =>
    edition === 'standard'
      ? { ...m, disabled: true, disabledReasonKey: DORIS_DISABLED_REASON_KEY }
      : m;

  // ── 近源层 · 数据湖 ──
  put('dw.lake.enabled', bool(false));
  put('dw.lake.datasource_id', text());
  put('dw.lake.storage_format', enumOf('parquet', ['parquet', 'orc', 'avro']));
  put('dw.lake.partition_by', text('dt'));
  put('dw.lake.retention_days', int(90));

  // ── 近源层 · 对象存储 ──
  put('dw.storage.type', enumOf('minio', ['minio', 's3', 'oss']));
  put('dw.storage.minio.endpoint', text('http://localhost:9000'));
  put('dw.storage.minio.access_key', text('minioadmin'));
  put('dw.storage.minio.secret_key', password('minioadmin'));
  put('dw.storage.minio.bucket', text('ecos-data'));
  put('dw.storage.minio.region', text('us-east-1'));
  put('dw.storage.minio.ssl', bool(false));

  // ── 近源层 · 数据同步 ──
  put('dw.sync.batch_size', int(5000));
  put('dw.sync.max_retries', int(3));

  // ── 近源层 · 数据源接入 ──
  put('dw.datasource.page_size', int(20));
  put('dw.datasource.conn_timeout', int(30000));

  // ── DW 层 · 执行引擎 ──
  put('dw.execution.mode', enumOf('memory', ['memory', 'doris']));
  put('dw.execution.memory.max_rows', int(100000));
  put('dw.execution.memory.threads', int(4));
  put('dw.execution.doris.host', doris(text('localhost')));
  put('dw.execution.doris.port', doris(int(9030)));
  put('dw.execution.doris.user', doris(text('root')));
  put('dw.execution.doris.database', doris(text('ecos_dw')));
  put('dw.execution.doris.batch_size', doris(int(10000)));
  put('dw.execution.timeout', int(600));

  // ── DW 层 · 管道基础 ──
  put('dw.pipeline.max_steps', int(20));
  put('dw.pipeline.parallel_steps', int(4));
  put('dw.pipeline.default_chunk_size', int(10000));
  put('dw.pipeline.temp_table_prefix', text('ecos_tmp_'));
  put('dw.pipeline.temp_table_ttl_hours', int(24));
  put('dw.pipeline.retry_max', int(3));
  put('dw.pipeline.retry_backoff_ms', int(5000));

  // ── DW 层 · 管道高级 ──
  put('dw.pipeline.log_storage', text('db'));
  put('dw.pipeline.log_retention_days', int(30));
  put('dw.pipeline.resume_enabled', bool(true));
  put('dw.pipeline.resume_max_retries', int(3));
  put('dw.pipeline.keep_history', bool(false));
  put('dw.pipeline.history_max_versions', int(10));
  put('dw.pipeline.preview_mode', text('sample'));
  put('dw.pipeline.preview_max_rows', int(1000));
  put('dw.pipeline.alert_on_failure', bool(true));
  put('dw.pipeline.alert_on_success', bool(false));
  put('dw.pipeline.template_repo_url', text());
  put('dw.pipeline.monaco_theme', enumOf('vs-dark', ['vs', 'vs-dark', 'hc-black', 'hc-light']));

  // ── DW 层 · 数据质量 ──
  put('dw.quality.sample_rate', float(1.0));
  put('dw.quality.sample_max_rows', int(1000000));
  put('dw.quality.stale_threshold_hours', int(24));
  put('dw.quality.default_alert_score', int(80));
  put('dw.quality.concurrent_checks', int(2));
  put('dw.quality.check_timeout', int(300));

  // ── DW 层 · 血缘 ──
  put('dw.lineage.enabled', bool(true));
  put('dw.lineage.parser', enumOf('sql', ['sql', 'spark', 'dbt']));
  put('dw.lineage.max_depth', int(10));
  put('dw.lineage.cache_ttl_minutes', int(30));
  put('dw.lineage.neo4j_enabled', bool(false));

  // ── DW 层 · 查询 ──
  put('dw.query.max_rows', int(10000));
  put('dw.query.timeout', int(30));

  // ── DW 层 · 缓存 ──
  put('dw.cache.ttl_seconds', int(300));

  // ── DW 层 · 数据目录 ──
  put('dw.catalog.search_limit', int(500));

  // ── DW 层 · 元数据 ──
  put('dw.metadata.collect_timeout', int(60));
  put('dw.metadata.history_versions', int(50));

  // ── 全局 · 引擎 ──
  put('dw.engine.auto_start', bool(true));

  // ── 全局 · 通知 ──
  put('dw.notify.channel', text('internal'));

  // ── 全局 · Copilot ──
  put('dw.copilot.enabled', bool(false));
  put('dw.copilot.provider', text('openai'));
  put('dw.copilot.model', text('gpt-4o'));
  put('dw.copilot.temperature', float(0.2));
  put('dw.copilot.max_tokens', int(4096));

  return meta;
}
