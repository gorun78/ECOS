-- ============================================================================
-- V354 — runtime 底座配置建组入 sys_config（llm / event / storage，G-2 首批）
-- ============================================================================
-- 为什么需要本脚本（差距分析 G-2）：
--   `docs/30-设计/ECOS系统配置现状与差距分析-2026-11-19.md` G-2 登记：
--   「runtime 配置（llm/kafka/minio/neo4j/调度/事件）0 项入门面，全部散在 yml 与
--   @Value/@ConfigurationProperties」；唯一先例是 V306 的 cache 组 4 键
--   （消费缝 `SysConfigCacheSettingsSource`）。本脚本把 llm / event / storage
--   三组中**运行期可在线调整**的代表键播种进 sys_config，数据面先行。
--
-- 选键口径（只收「运行期调了有意义」的键，连接/寻址类仍留 yml）：
--   * llm 组 4 键：default-model / default-temperature / agent-timeout-ms /
--     max-context-tokens——这些是 LLM 调用的行为参数，改值即影响后续请求，
--     适合在线调整。provider 选择与 api-key-ref 属部署形态，留 yml。
--   * event 组 2 键：kafka auto-offset-reset / group-prefix——消费行为参数。
--     bootstrap-servers 属部署寻址，留 yml。
--   * storage 组 2 键：minio bucket / mineru base-url 超时——资源名与超时可调。
--     endpoint/access-key/secret-key 属部署凭据，留 yml。
--
-- 推翻旧口径声明（V264 头注「模型目录属部署期形态，留在 Spring yml 而非 sys_config」）：
--   该口径把「模型目录（catalog，部署期清单）」与「LLM 行为参数（运行期可调）」混为一谈。
--   本脚本只收编后者；catalog 清单仍留 yml（部署形态不变）。G-2 整改方向第 1 条
--   据此成立，V264 头注对「行为参数」不再适用。
--
-- 消费缝状态（2026-11-19 第二批实测订正 — 勿把「已播种」当「已生效」）：
--   | 键 | 消费方 | 改库是否真的影响行为 |
--   |----|--------|----------------------|
--   | llm.default-model     | ModelCapabilityExecutorImpl#defaultModel()（缺省模型路径） | ✅ 是 |
--   | llm.default-temperature | **无**（温度取值来自 AgentProfile/LLMConfig，不读本键） | ❌ 否 |
--   | llm.agent-timeout-ms    | **无**（LLMGatewayProperties.getDefaultTimeout 定义但无人调用） | ❌ 否 |
--   | llm.max-context-tokens  | **无**（上下文窗口由 AgentLoopConfig 逐次传入） | ❌ 否 |
--   | event.* / storage.*     | **无**（桥接未做） | ❌ 否 |
--
--   ⇒ 现状诚实口径：**8 枚键中仅 llm.default-model 真正接线**（门禁 LlmDefaultModelSourceGateTest
--   锁死其 sys_config→yml 取值链）。其余 7 枚属「数据面先行」：已入库、可在配置面看到与编辑，
--   但改值不影响运行行为。**在桥接补齐前不得对外宣称这些键可在线调整**——
--   接入方须按 `SysConfigCacheSettingsSource` / `SysConfigLlmSettingsSource` 同款形态，
--   在各自读取点插入「配置面优先 → yml 回落」判定。
--
-- 桥接落地形态（llm.default-model 已按此完成，可作模板）：
--   runtime 侧 SPI（`LlmSettingsSource`，底座零依赖）→ sysman-impl 适配 Bean
--   （`SysConfigLlmSettingsSource`，读表）→ 消费点在决策处 ObjectProvider 取用。
--   未装配 ⇒ 纯 yml 行为（等价改造前），禁 fail-open 改行为。
--
-- 值口径（保守基线，与 yml/Properties 现值对齐，不借本脚本改行为）：
--   * llm.default-model = 'deepseek/deepseek-chat'、default-temperature = '0.7'、
--     agent-timeout-ms = '300000'、max-context-tokens = '8192'：
--     取 `LLMGatewayProperties` 现行缺省（defaultModel='deepseek/deepseek-chat'，
--     provider/model 组合键；agent-timeout 取 gateway application.yml 的 300s 口径），
--     播种后语义不变。temperature/max-context 为运行期可调代表键。
--   * event.kafka.auto-offset-reset = 'earliest'、group-prefix = 'ecos'：
--     取 runtime-event KafkaBusConsumerConfig 现行缺省。
--   * storage.minio.bucket = 'ecos-data'、storage.mineru.timeout-seconds = '120'：
--     取 runtime-access 现行缺省。
--
-- 幂等形态：逐行 `INSERT ... SELECT ... WHERE NOT EXISTS`（同 V306），
--   不用 ON CONFLICT（避免增长 Mc03TimeFormTest 的「PG 专有语法待清退名录」）。
-- 纪律：IR02(手动 psql -f) / IR03(只加不删) / MC01(主键应用侧指定) /
--       禁 DROP、禁新建容器、禁改既有列。
-- ============================================================================

-- ── llm 组（4 键，运行期行为参数） ──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-llm-default-model', 'llm', 'llm.default-model', 'deepseek/deepseek-chat', 'string',
       'LLM·默认模型', 'LLM Default Model',
       '未显式指定模型时的缺省模型键（provider/model 组合，对齐 LLMGatewayProperties.engine.defaultModel 现行缺省）。在线调整即时影响后续请求；模型目录清单（catalog）本身属部署形态仍留 yml（V264 旧口径仅对 catalog 适用）', 1, 'active', 'deepseek/deepseek-chat'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.default-model');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-llm-default-temperature', 'llm', 'llm.default-temperature', '0.7', 'float',
       'LLM·默认温度', 'LLM Default Temperature',
       '采样温度缺省值（0.0~2.0）。运行期可调，影响后续请求的创作/确定性倾向', 2, 'active', '0.7'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.default-temperature');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-llm-agent-timeout', 'llm', 'llm.agent-timeout-ms', '300000', 'int',
       'LLM·Agent 超时（毫秒）', 'LLM Agent Timeout (ms)',
       'Agent 长流调用的整体超时（毫秒）。运行期可调；超时是上游拒绝的地板，非建议值', 3, 'active', '300000'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.agent-timeout-ms');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-llm-max-context', 'llm', 'llm.max-context-tokens', '8192', 'int',
       'LLM·最大上下文（tokens）', 'LLM Max Context Tokens',
       '单次请求上下文 tokens 上限（超过截断）。运行期可调；受模型实际上限钳制', 4, 'active', '8192'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'llm.max-context-tokens');

-- ── event 组（2 键，Kafka 消费行为） ──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-event-offset-reset', 'event', 'event.kafka.auto-offset-reset', 'earliest', 'enum',
       '事件·Kafka 位点重置', 'Event Kafka Auto Offset Reset',
       '消费者组无初始位点时的重置策略（earliest/latest）。运行期可调，新消费者组生效；broker 寻址（bootstrap-servers）属部署形态留 yml', 1, 'active', 'earliest'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'event.kafka.auto-offset-reset');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-event-group-prefix', 'event', 'event.kafka.group-prefix', 'ecos', 'string',
       '事件·Kafka 消费组前缀', 'Event Kafka Group Prefix',
       '消费者组名前缀（环境隔离用）。运行期可调；改后新订阅生效，既有组不迁移', 2, 'active', 'ecos'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'event.kafka.group-prefix');

-- ── storage 组（2 键，资源名与超时） ──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-storage-minio-bucket', 'storage', 'storage.minio.bucket', 'ecos-data', 'string',
       '存储·MinIO 桶名', 'Storage MinIO Bucket',
       '对象存储目标桶名。运行期可调；endpoint/access-key/secret-key 属部署凭据留 yml', 1, 'active', 'ecos-data'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'storage.minio.bucket');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-storage-mineru-timeout', 'storage', 'storage.mineru.timeout-seconds', '120', 'int',
       '存储·MinerU 解析超时（秒）', 'Storage MinerU Timeout (sec)',
       'MinerU 文档解析 HTTP 调用超时（秒）。运行期可调；解析器寻址（ecos.mineru.base-url）属部署形态留 yml', 2, 'active', '120'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'storage.mineru.timeout-seconds');
