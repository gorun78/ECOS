/**
 * DDL for data workbench configuration system.
 * Execute via: psql -h localhost -U postgres -d sys_man -f this_file.sql
 */
-- 1. Extend sys_config table
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_group VARCHAR(50) DEFAULT 'general';
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE sys_config ADD COLUMN IF NOT EXISTS config_type VARCHAR(20) DEFAULT 'string';

-- 2. Insert data engine config items
INSERT INTO sys_config (config_key, config_value, config_group, config_type, description, status) VALUES
-- execution engine
('dw.execution.mode','memory','data-engine','enum','执行模式 memory/doris','active'),
('dw.execution.memory.max_rows','100000','data-engine','int','内存模式最大处理行数','active'),
('dw.execution.memory.threads','4','data-engine','int','内存模式并行线程数','active'),
('dw.execution.doris.host','localhost','data-engine','string','Doris FE地址','active'),
('dw.execution.doris.port','9030','data-engine','int','Doris MySQL协议端口','active'),
('dw.execution.doris.user','root','data-engine','string','Doris用户名','active'),
('dw.execution.doris.database','ecos_dw','data-engine','string','Doris默认库','active'),
('dw.execution.doris.batch_size','10000','data-engine','int','Doris批量写入行数','active'),
('dw.execution.timeout','600','data-engine','int','任务超时秒数','active'),
-- data lake
('dw.lake.enabled','false','data-engine','bool','是否启用数据湖','active'),
('dw.lake.datasource_id','','data-engine','string','数据湖目标数据源ID','active'),
('dw.lake.storage_format','parquet','data-engine','enum','存储格式 parquet/orc/avro','active'),
('dw.lake.partition_by','dt','data-engine','string','默认分区字段','active'),
('dw.lake.retention_days','90','data-engine','int','数据保留天数','active'),
-- object storage
('dw.storage.type','minio','data-engine','enum','对象存储类型','active'),
('dw.storage.minio.endpoint','http://localhost:9000','data-engine','string','MinIO地址','active'),
('dw.storage.minio.access_key','minioadmin','data-engine','string','Access Key','active'),
('dw.storage.minio.secret_key','minioadmin','data-engine','password','Secret Key','active'),
('dw.storage.minio.bucket','ecos-data','data-engine','string','默认Bucket','active'),
('dw.storage.minio.region','us-east-1','data-engine','string','区域','active'),
('dw.storage.minio.ssl','false','data-engine','bool','是否启用SSL','active'),
-- pipeline
('dw.pipeline.max_steps','20','data-engine','int','单管道最大步骤数','active'),
('dw.pipeline.parallel_steps','4','data-engine','int','允许并行步骤数','active'),
('dw.pipeline.default_chunk_size','10000','data-engine','int','默认分块行数','active'),
('dw.pipeline.temp_table_prefix','ecos_tmp_','data-engine','string','临时表前缀','active'),
('dw.pipeline.temp_table_ttl_hours','24','data-engine','int','临时表过期时间h','active'),
('dw.pipeline.retry_max','3','data-engine','int','步骤默认重试次数','active'),
('dw.pipeline.retry_backoff_ms','5000','data-engine','int','重试间隔ms','active'),
-- quality
('dw.quality.sample_rate','1.0','data-engine','float','采样率(0.0-1.0)','active'),
('dw.quality.sample_max_rows','1000000','data-engine','int','采样最大行数','active'),
('dw.quality.stale_threshold_hours','24','data-engine','int','数据过期阈值h','active'),
('dw.quality.default_alert_score','80','data-engine','int','默认告警分数阈值','active'),
('dw.quality.concurrent_checks','2','data-engine','int','并发检查任务数','active'),
('dw.quality.check_timeout','300','data-engine','int','单检超时秒','active'),
-- lineage
('dw.lineage.enabled','true','data-engine','bool','启用血缘采集','active'),
('dw.lineage.parser','sql','data-engine','enum','血缘解析引擎 sql/spark/dbt','active'),
('dw.lineage.max_depth','10','data-engine','int','最大追溯深度','active'),
('dw.lineage.cache_ttl_minutes','30','data-engine','int','血缘缓存时间min','active'),
('dw.lineage.neo4j_enabled','false','data-engine','bool','Neo4j图存储','active'),
-- data engine general
('dw.sync.batch_size','5000','data-engine','int','同步任务批次大小','active'),
('dw.sync.max_retries','3','data-engine','int','同步最大重试次数','active'),
('dw.query.max_rows','10000','data-engine','int','SQL查询最大行数','active'),
('dw.query.timeout','30','data-engine','int','SQL查询超时秒','active'),
('dw.cache.ttl_seconds','300','data-engine','int','元数据缓存时间秒','active'),
('dw.engine.auto_start','true','data-engine','bool','启动自动启引擎','active'),
-- migrated from sysman
('dw.datasource.page_size','20','data-engine','int','[迁移] 数据源分页大小','active'),
('dw.datasource.conn_timeout','30000','data-engine','int','[迁移] 连接超时ms','active'),
('dw.metadata.collect_timeout','60','data-engine','int','[迁移] 采集超时秒','active'),
('dw.catalog.search_limit','500','data-engine','int','[迁移] 目录搜索限制','active')
ON CONFLICT (config_key) DO UPDATE SET config_value=EXCLUDED.config_value, description=EXCLUDED.description;
