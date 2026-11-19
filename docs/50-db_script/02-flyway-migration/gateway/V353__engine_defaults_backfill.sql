-- ============================================================================
-- V353 — 四引擎 Controller DEFAULTS 常量回填 sys_config.default_value
-- ============================================================================
-- 为什么需要本脚本（E-X 差距根因修复，V351 后续）：
--   `docs/30-设计/ECOS系统配置现状与差距分析-2026-11-19.md` E-X 登记：
--   「各引擎的 DEFAULTS 常量硬编码在各自 Controller 内，违反『默认值事实源 =
--   后端 default_value 列』（前端规范 §九.3），也是 reset 404 的根因」。
--   V351 已补 `default_value` 列并回填安全/全局 11 键；本脚本把引擎组
--   （data-engine / ontology-engine / knowledge / cognitive-engine）的默认值
--   从 Controller 常量迁到该列，使 reset 与前端「恢复默认」对引擎配置同样可用。
--
-- 口径（与 V351 相同）：
--   ① 仅对 `default_value IS NULL` 的行回填，不覆盖任何既有值；
--   ② 回填值 = 该键在对应 Controller `DEFAULTS` 常量中的原值（= 引擎出厂基线）；
--      数据源（逐字摘抄，不改语义）：
--        data-engine  ← DataEngineConfigController.java:37-…（LinkedHashMap DEFAULTS，64 键）
--        ontology-engine ← OntologyConfigController.java:46-57（String[][] DEFAULTS，11 键）
--        knowledge ← KnowledgeSettingsController.java（String[][] DEFAULTS，12 键）
--        cognitive-engine ← ai-engine CognitiveConfigController.java（String[][] DEFAULTS，7 键）
--   ③ 只加不改（IR03 兼容）；历史归档区 V1~V349 不动。
--
-- 注意：cognitive 引擎存在双组并存（`cognitive` + `cognitive-engine`，差距 E-4），
-- 本脚本只回填 `cognitive-engine` 组 7 键（ai-engine 侧常量）；`cognitive` 组
-- （cognitive-engine-impl 侧，CD-2 整改对象）无 DEFAULTS 常量，不在本脚本范围。
-- ============================================================================

-- ── data-engine 组（64 键，DataEngineConfigController DEFAULTS 原值） ──
UPDATE sys_config SET default_value = 'memory'                  WHERE config_key = 'dw.execution.mode'                AND default_value IS NULL;
UPDATE sys_config SET default_value = '100000'                  WHERE config_key = 'dw.execution.memory.max_rows'     AND default_value IS NULL;
UPDATE sys_config SET default_value = '4'                       WHERE config_key = 'dw.execution.memory.threads'      AND default_value IS NULL;
UPDATE sys_config SET default_value = 'localhost'               WHERE config_key = 'dw.execution.doris.host'          AND default_value IS NULL;
UPDATE sys_config SET default_value = '9030'                    WHERE config_key = 'dw.execution.doris.port'          AND default_value IS NULL;
UPDATE sys_config SET default_value = 'root'                    WHERE config_key = 'dw.execution.doris.user'          AND default_value IS NULL;
UPDATE sys_config SET default_value = 'ecos_dw'                 WHERE config_key = 'dw.execution.doris.database'      AND default_value IS NULL;
UPDATE sys_config SET default_value = '10000'                   WHERE config_key = 'dw.execution.doris.batch_size'    AND default_value IS NULL;
UPDATE sys_config SET default_value = '600'                     WHERE config_key = 'dw.execution.timeout'             AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.lake.enabled'                  AND default_value IS NULL;
UPDATE sys_config SET default_value = ''                        WHERE config_key = 'dw.lake.datasource_id'            AND default_value IS NULL;
UPDATE sys_config SET default_value = 'parquet'                 WHERE config_key = 'dw.lake.storage_format'           AND default_value IS NULL;
UPDATE sys_config SET default_value = 'dt'                      WHERE config_key = 'dw.lake.partition_by'             AND default_value IS NULL;
UPDATE sys_config SET default_value = '90'                      WHERE config_key = 'dw.lake.retention_days'           AND default_value IS NULL;
UPDATE sys_config SET default_value = 'minio'                   WHERE config_key = 'dw.storage.type'                  AND default_value IS NULL;
UPDATE sys_config SET default_value = 'http://localhost:9000'   WHERE config_key = 'dw.storage.minio.endpoint'        AND default_value IS NULL;
UPDATE sys_config SET default_value = 'minioadmin'              WHERE config_key = 'dw.storage.minio.access_key'      AND default_value IS NULL;
UPDATE sys_config SET default_value = 'minioadmin'              WHERE config_key = 'dw.storage.minio.secret_key'      AND default_value IS NULL;
UPDATE sys_config SET default_value = 'ecos-data'               WHERE config_key = 'dw.storage.minio.bucket'          AND default_value IS NULL;
UPDATE sys_config SET default_value = 'us-east-1'               WHERE config_key = 'dw.storage.minio.region'          AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.storage.minio.ssl'             AND default_value IS NULL;
UPDATE sys_config SET default_value = '20'                      WHERE config_key = 'dw.pipeline.max_steps'            AND default_value IS NULL;
UPDATE sys_config SET default_value = '4'                       WHERE config_key = 'dw.pipeline.parallel_steps'       AND default_value IS NULL;
UPDATE sys_config SET default_value = '10000'                   WHERE config_key = 'dw.pipeline.default_chunk_size'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'ecos_tmp_'               WHERE config_key = 'dw.pipeline.temp_table_prefix'    AND default_value IS NULL;
UPDATE sys_config SET default_value = '24'                      WHERE config_key = 'dw.pipeline.temp_table_ttl_hours' AND default_value IS NULL;
UPDATE sys_config SET default_value = '3'                       WHERE config_key = 'dw.pipeline.retry_max'            AND default_value IS NULL;
UPDATE sys_config SET default_value = '5000'                    WHERE config_key = 'dw.pipeline.retry_backoff_ms'     AND default_value IS NULL;
UPDATE sys_config SET default_value = '1.0'                     WHERE config_key = 'dw.quality.sample_rate'           AND default_value IS NULL;
UPDATE sys_config SET default_value = '1000000'                 WHERE config_key = 'dw.quality.sample_max_rows'       AND default_value IS NULL;
UPDATE sys_config SET default_value = '24'                      WHERE config_key = 'dw.quality.stale_threshold_hours' AND default_value IS NULL;
UPDATE sys_config SET default_value = '80'                      WHERE config_key = 'dw.quality.default_alert_score'   AND default_value IS NULL;
UPDATE sys_config SET default_value = '2'                       WHERE config_key = 'dw.quality.concurrent_checks'     AND default_value IS NULL;
UPDATE sys_config SET default_value = '300'                     WHERE config_key = 'dw.quality.check_timeout'         AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'dw.lineage.enabled'               AND default_value IS NULL;
UPDATE sys_config SET default_value = 'sql'                     WHERE config_key = 'dw.lineage.parser'                AND default_value IS NULL;
UPDATE sys_config SET default_value = '10'                      WHERE config_key = 'dw.lineage.max_depth'             AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.lineage.neo4j_enabled'         AND default_value IS NULL;
UPDATE sys_config SET default_value = '5000'                    WHERE config_key = 'dw.sync.batch_size'               AND default_value IS NULL;
UPDATE sys_config SET default_value = '3'                       WHERE config_key = 'dw.sync.max_retries'              AND default_value IS NULL;
UPDATE sys_config SET default_value = '10000'                   WHERE config_key = 'dw.query.max_rows'                AND default_value IS NULL;
UPDATE sys_config SET default_value = '30'                      WHERE config_key = 'dw.query.timeout'                 AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'dw.engine.auto_start'             AND default_value IS NULL;
UPDATE sys_config SET default_value = 'db'                      WHERE config_key = 'dw.pipeline.log_storage'          AND default_value IS NULL;
UPDATE sys_config SET default_value = '30'                      WHERE config_key = 'dw.pipeline.log_retention_days'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'dw.pipeline.resume_enabled'       AND default_value IS NULL;
UPDATE sys_config SET default_value = '3'                       WHERE config_key = 'dw.pipeline.resume_max_retries'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.pipeline.keep_history'         AND default_value IS NULL;
UPDATE sys_config SET default_value = '10'                      WHERE config_key = 'dw.pipeline.history_max_versions' AND default_value IS NULL;
UPDATE sys_config SET default_value = 'sample'                  WHERE config_key = 'dw.pipeline.preview_mode'         AND default_value IS NULL;
UPDATE sys_config SET default_value = '1000'                    WHERE config_key = 'dw.pipeline.preview_max_rows'     AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'dw.pipeline.alert_on_failure'     AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.pipeline.alert_on_success'     AND default_value IS NULL;
UPDATE sys_config SET default_value = ''                        WHERE config_key = 'dw.pipeline.template_repo_url'    AND default_value IS NULL;
UPDATE sys_config SET default_value = 'vs-dark'                 WHERE config_key = 'dw.pipeline.monaco_theme'         AND default_value IS NULL;
UPDATE sys_config SET default_value = 'internal'                WHERE config_key = 'dw.notify.channel'                AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'dw.copilot.enabled'               AND default_value IS NULL;
UPDATE sys_config SET default_value = 'openai'                  WHERE config_key = 'dw.copilot.provider'              AND default_value IS NULL;
UPDATE sys_config SET default_value = 'gpt-4o'                  WHERE config_key = 'dw.copilot.model'                 AND default_value IS NULL;
UPDATE sys_config SET default_value = '0.2'                     WHERE config_key = 'dw.copilot.temperature'           AND default_value IS NULL;
UPDATE sys_config SET default_value = '4096'                    WHERE config_key = 'dw.copilot.max_tokens'            AND default_value IS NULL;
UPDATE sys_config SET default_value = '20'                      WHERE config_key = 'dw.datasource.page_size'          AND default_value IS NULL;
UPDATE sys_config SET default_value = '30000'                   WHERE config_key = 'dw.datasource.conn_timeout'       AND default_value IS NULL;
UPDATE sys_config SET default_value = '60'                      WHERE config_key = 'dw.metadata.collect_timeout'      AND default_value IS NULL;
UPDATE sys_config SET default_value = '500'                     WHERE config_key = 'dw.catalog.search_limit'          AND default_value IS NULL;
UPDATE sys_config SET default_value = '50'                      WHERE config_key = 'dw.metadata.history_versions'     AND default_value IS NULL;

-- ── ontology-engine 组（11 键，OntologyConfigController DEFAULTS 原值） ──
UPDATE sys_config SET default_value = 'pg'                      WHERE config_key = 'ontology.graph.backend'           AND default_value IS NULL;
UPDATE sys_config SET default_value = 'bolt://localhost:7687'   WHERE config_key = 'ontology.graph.neo4j.uri'         AND default_value IS NULL;
UPDATE sys_config SET default_value = 'neo4j'                   WHERE config_key = 'ontology.graph.neo4j.user'        AND default_value IS NULL;
UPDATE sys_config SET default_value = 'neo4j123'                WHERE config_key = 'ontology.graph.neo4j.password'    AND default_value IS NULL;
UPDATE sys_config SET default_value = '10'                      WHERE config_key = 'ontology.version.max_versions'    AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'ontology.version.auto_version'    AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'ontology.version.require_approval' AND default_value IS NULL;
UPDATE sys_config SET default_value = '1000'                    WHERE config_key = 'ontology.import.batch_size'       AND default_value IS NULL;
UPDATE sys_config SET default_value = 'jsonld'                  WHERE config_key = 'ontology.export.format'           AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'ontology.proposal.auto_publish'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'buszhi'                  WHERE config_key = 'ontology.workflow.engine'         AND default_value IS NULL;

-- ── knowledge 组（12 键，KnowledgeSettingsController DEFAULTS 原值） ──
UPDATE sys_config SET default_value = 'default'                 WHERE config_key = 'knowledge.graph.defaultDomain'    AND default_value IS NULL;
UPDATE sys_config SET default_value = '3'                       WHERE config_key = 'knowledge.graph.maxNeighborDegree' AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'knowledge.index.autoSyncEnabled'  AND default_value IS NULL;
UPDATE sys_config SET default_value = '500'                     WHERE config_key = 'knowledge.index.batchSize'        AND default_value IS NULL;
UPDATE sys_config SET default_value = '5'                       WHERE config_key = 'knowledge.rag.topK'               AND default_value IS NULL;
UPDATE sys_config SET default_value = '0.7'                     WHERE config_key = 'knowledge.rag.similarityThreshold' AND default_value IS NULL;
UPDATE sys_config SET default_value = 'text-embedding-3-small'  WHERE config_key = 'knowledge.rag.model'              AND default_value IS NULL;
UPDATE sys_config SET default_value = '10'                      WHERE config_key = 'knowledge.lineage.maxDepth'       AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'extract.allow_direct_upload'      AND default_value IS NULL;
UPDATE sys_config SET default_value = '500'                     WHERE config_key = 'extract.page_limit'               AND default_value IS NULL;
UPDATE sys_config SET default_value = '200'                     WHERE config_key = 'extract.max_pages'                AND default_value IS NULL;
UPDATE sys_config SET default_value = 'false'                   WHERE config_key = 'extract.periodic_enabled'         AND default_value IS NULL;

-- ── cognitive-engine 组（7 键，ai-engine CognitiveConfigController DEFAULTS 原值） ──
UPDATE sys_config SET default_value = '5'                       WHERE config_key = 'prompt-compiler.topK'             AND default_value IS NULL;
UPDATE sys_config SET default_value = 'text-embedding-3-small'  WHERE config_key = 'prompt-compiler.vectorModel'      AND default_value IS NULL;
UPDATE sys_config SET default_value = 'keyword_match'           WHERE config_key = 'agent-mesh.routingStrategy'       AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'guardrails.piiDetectionEnabled'   AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'guardrails.hallucinationCheckEnabled' AND default_value IS NULL;
UPDATE sys_config SET default_value = 'true'                    WHERE config_key = 'action-bridge.autoExecuteEnabled' AND default_value IS NULL;
UPDATE sys_config SET default_value = '0.7'                     WHERE config_key = 'action-bridge.matchConfidenceThreshold' AND default_value IS NULL;
