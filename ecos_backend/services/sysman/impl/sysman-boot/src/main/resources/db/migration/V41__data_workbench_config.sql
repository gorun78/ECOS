-- ============================================================
-- V41__data_workbench_config.sql — 数据工作台配置体系
-- 将所有 dw.* 配置写入 sys_config，config_group = 'data-engine'
-- ============================================================

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, description, sort_order) VALUES
-- 执行引擎 (9)
('cfg-dw-exec-mode',           'data-engine', 'dw.execution.mode',              'memory',               'enum',    '执行模式',              '执行模式 memory/doris',                         1),
('cfg-dw-exec-mem-max-rows',   'data-engine', 'dw.execution.memory.max_rows',   '100000',               'int',     '内存最大处理行数',      '内存模式最大处理行数',                         2),
('cfg-dw-exec-mem-threads',    'data-engine', 'dw.execution.memory.threads',    '4',                    'int',     '并行线程数',            '内存模式并行线程数',                           3),
('cfg-dw-exec-doris-host',     'data-engine', 'dw.execution.doris.host',        'localhost',            'string',  'Doris FE地址',          'Doris FE 地址',                                4),
('cfg-dw-exec-doris-port',     'data-engine', 'dw.execution.doris.port',        '9030',                 'int',     'Doris端口',             'Doris MySQL 协议端口',                         5),
('cfg-dw-exec-doris-user',     'data-engine', 'dw.execution.doris.user',        'root',                 'string',  'Doris用户名',           'Doris 用户名',                                 6),
('cfg-dw-exec-doris-db',       'data-engine', 'dw.execution.doris.database',    'ecos_dw',              'string',  'Doris默认库',           'Doris 默认库',                                 7),
('cfg-dw-exec-doris-batch',    'data-engine', 'dw.execution.doris.batch_size',  '10000',                'int',     'Doris批量写入行数',     'Doris 批量写入行数',                           8),
('cfg-dw-exec-timeout',        'data-engine', 'dw.execution.timeout',           '600',                  'int',     '任务超时秒',            '任务超时秒数',                                 9),

-- 数据湖 (5)
('cfg-dw-lake-enabled',        'data-engine', 'dw.lake.enabled',                'false',                'bool',    '启用数据湖',            '是否启用数据湖',                               10),
('cfg-dw-lake-datasource-id',  'data-engine', 'dw.lake.datasource_id',          '',                     'string',  '数据湖目标数据源ID',    '数据湖目标数据源 ID',                          11),
('cfg-dw-lake-storage-format', 'data-engine', 'dw.lake.storage_format',         'parquet',              'enum',    '存储格式',              '存储格式 parquet/orc/avro',                    12),
('cfg-dw-lake-partition-by',   'data-engine', 'dw.lake.partition_by',           'dt',                   'string',  '默认分区字段',          '默认分区字段',                                 13),
('cfg-dw-lake-retention-days', 'data-engine', 'dw.lake.retention_days',         '90',                   'int',     '保留天数',              '数据保留天数',                                 14),

-- 对象存储 (7)
('cfg-dw-storage-type',        'data-engine', 'dw.storage.type',                'minio',                'enum',    '对象存储类型',          '对象存储类型 minio/s3/oss',                    15),
('cfg-dw-storage-minio-ep',    'data-engine', 'dw.storage.minio.endpoint',      'http://localhost:9000','string',  'MinIO地址',             'MinIO 地址',                                   16),
('cfg-dw-storage-minio-ak',    'data-engine', 'dw.storage.minio.access_key',    'minioadmin',           'string',  'AccessKey',             'Access Key',                                   17),
('cfg-dw-storage-minio-sk',    'data-engine', 'dw.storage.minio.secret_key',    'minioadmin',           'string',  'SecretKey',             'Secret Key（加密存储）',                       18),
('cfg-dw-storage-minio-bucket','data-engine', 'dw.storage.minio.bucket',        'ecos-data',            'string',  '默认Bucket',            '默认 Bucket',                                  19),
('cfg-dw-storage-minio-region','data-engine', 'dw.storage.minio.region',        'us-east-1',            'string',  '区域',                  '区域',                                         20),
('cfg-dw-storage-minio-ssl',   'data-engine', 'dw.storage.minio.ssl',           'false',                'bool',    '是否启用SSL',           '是否启用 SSL',                                 21),

-- 管道 (7)
('cfg-dw-pipe-max-steps',      'data-engine', 'dw.pipeline.max_steps',          '20',                   'int',     '最大步骤数',            '单个管道最大步骤数',                           22),
('cfg-dw-pipe-parallel-steps', 'data-engine', 'dw.pipeline.parallel_steps',     '4',                    'int',     '并行步骤数',            '允许的并行步骤数',                             23),
('cfg-dw-pipe-chunk-size',     'data-engine', 'dw.pipeline.default_chunk_size', '10000',                'int',     '分块行数',              '默认分块行数',                                 24),
('cfg-dw-pipe-temp-prefix',    'data-engine', 'dw.pipeline.temp_table_prefix',  'ecos_tmp_',            'string',  '临时表前缀',            '临时表前缀',                                   25),
('cfg-dw-pipe-tmp-ttl',        'data-engine', 'dw.pipeline.temp_table_ttl_hours','24',                  'int',     '临时表过期时间',        '临时表过期时间（小时）',                       26),
('cfg-dw-pipe-retry-max',      'data-engine', 'dw.pipeline.retry_max',          '3',                    'int',     '重试次数',              '步骤默认重试次数',                             27),
('cfg-dw-pipe-retry-backoff',  'data-engine', 'dw.pipeline.retry_backoff_ms',   '5000',                 'int',     '重试间隔',              '重试间隔（毫秒）',                             28),

-- 数据质量 (6)
('cfg-dw-qual-sample-rate',    'data-engine', 'dw.quality.sample_rate',         '1.0',                  'float',   '采样率',                '采样率（0.0-1.0）',                            29),
('cfg-dw-qual-sample-max',     'data-engine', 'dw.quality.sample_max_rows',     '1000000',              'int',     '采样最大行数',          '采样最大行数',                                 30),
('cfg-dw-qual-stale-threshold','data-engine', 'dw.quality.stale_threshold_hours','24',                  'int',     '数据过期阈值',          '数据过期阈值（小时）',                         31),
('cfg-dw-qual-alert-score',    'data-engine', 'dw.quality.default_alert_score', '80',                   'int',     '告警分数阈值',          '默认告警分数阈值',                             32),
('cfg-dw-qual-concurrent',     'data-engine', 'dw.quality.concurrent_checks',   '2',                    'int',     '并发检查任务数',        '并发检查任务数',                               33),
('cfg-dw-qual-check-timeout',  'data-engine', 'dw.quality.check_timeout',       '300',                  'int',     '检查超时秒',            '单次检查超时秒数',                             34),

-- 血缘 (5)
('cfg-dw-lineage-enabled',     'data-engine', 'dw.lineage.enabled',             'true',                 'bool',    '启血缘采集',            '是否启血缘采集',                               35),
('cfg-dw-lineage-parser',      'data-engine', 'dw.lineage.parser',              'sql',                  'enum',    '血缘解析引擎',          '血缘解析引擎 sql/spark/dbt',                   36),
('cfg-dw-lineage-max-depth',   'data-engine', 'dw.lineage.max_depth',           '10',                   'int',     '最大追溯深度',          '最大追溯深度',                                 37),
('cfg-dw-lineage-cache-ttl',   'data-engine', 'dw.lineage.cache_ttl_minutes',   '30',                   'int',     '血缘缓存时间',          '血缘缓存时间（分钟）',                         38),
('cfg-dw-lineage-neo4j',       'data-engine', 'dw.lineage.neo4j_enabled',       'false',                'bool',    'Neo4j图存储',           '是否启用 Neo4j 图存储',                         39),

-- 数据引擎自身 (6)
('cfg-dw-sync-batch-size',     'data-engine', 'dw.sync.batch_size',             '5000',                 'int',     '同步批次大小',          '同步任务默认批次大小',                         40),
('cfg-dw-sync-max-retries',    'data-engine', 'dw.sync.max_retries',            '3',                    'int',     '同步最大重试',          '同步任务最大重试',                             41),
('cfg-dw-query-max-rows',      'data-engine', 'dw.query.max_rows',              '10000',                'int',     '查询最大行数',          'SQL 查询最大返回行数',                          42),
('cfg-dw-query-timeout',       'data-engine', 'dw.query.timeout',               '30',                   'int',     '查询超时秒',            'SQL 查询超时秒数',                             43),
('cfg-dw-cache-ttl',           'data-engine', 'dw.cache.ttl_seconds',           '300',                  'int',     '缓存时间',              '元数据缓存时间',                               44),
('cfg-dw-engine-auto-start',   'data-engine', 'dw.engine.auto_start',           'true',                 'bool',    '自动启动引擎',          '启动时自动启动数据引擎',                       45),

-- 迁移项 (4)
('cfg-dw-ds-page-size',        'data-engine', 'dw.datasource.page_size',        '20',                   'int',     '数据源分页大小',        '数据源列表默认分页大小',                       46),
('cfg-dw-ds-conn-timeout',     'data-engine', 'dw.datasource.conn_timeout',     '30000',                'int',     '数据源连接超时',        '数据源连接超时(ms)',                            47),
('cfg-dw-meta-collect-timeout','data-engine', 'dw.metadata.collect_timeout',    '60',                   'int',     '元数据采集超时',        '元数据采集超时秒数',                           48),
('cfg-dw-catalog-search-limit','data-engine', 'dw.catalog.search_limit',        '500',                  'int',     '目录搜索上限',          '目录搜索结果数量上限',                         49)
ON CONFLICT (config_key) DO UPDATE SET
    config_value  = EXCLUDED.config_value,
    config_type   = EXCLUDED.config_type,
    description   = EXCLUDED.description,
    config_label  = EXCLUDED.config_label,
    updated_at    = NOW();
