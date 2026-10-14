-- V281 (缺陷回补 / 本体映射契约载体缺建表): ecos_ontology.ecos_entity_table_mapping
-- ============================================================
-- 缺陷事实（live 实证，非推断）:
--   浏览器 console 报 GET /api/v1/ontology/mappings?objectId=ent001 → 500；gateway 日志同 traceId 根因行 =
--     org.postgresql.util.PSQLException: ERROR: relation "ecos_entity_table_mapping" does not exist
--     （_win_tasks/logs/gateway-stdout.log:14478/14698，SQL 即 OntologyMappingDao.MAPPING_COLUMNS 那条 SELECT）
--   全仓 grep `CREATE TABLE.*ecos_entity_table_mapping` = 0 命中 ⇒ 该表**从未有建脚本**，
--   只有三枚对它的 ALTER 登记（V135 加 materialized / V145 加 doc_anchor+doc_anchor_type / V150 把 doc_anchor 改名 doc_anchor_json），
--   且全部限定 `public.` schema —— 而 V135/V145 的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 在表不存在时是**直接报错**还是静默，
--   取决于当时库里有没有这张表；V200 治理把 public 归位为 ecos_control 后，这三枚 `public.` 限定语句所指对象已不存在（knownLegacy，见下"被取代的登记"）。
--   ⇒ 本脚本补齐缺失的 CREATE，使既有代码契约有载体；不新增列、不改列名、不动任何存量表（IR03）。
--
-- 列清单单源 = 代码（禁凭设计文档臆造）:
--   * OntologyMappingDao.MAPPING_COLUMNS（ontology-engine-impl/dao:22-31）→ 13 列逐字对齐，
--     该常量同时用于 SELECT/INSERT/回读，是本表唯一显式列清单权威；
--   * OntologyMappingController.java:217 起的动态 UPDATE 只写 entity_name/resource_name/domain_code/
--     table_schema/materialized/doc_anchor_json/doc_anchor_type/updated_at ⇒ 全部已在 13 列内；
--   * sysman EntityTableMappingQueryDao（写 8 列 + `SELECT *`）与 AutoDiscoverDao.upsertMapping（写 8 列 + ON CONFLICT）
--     均不引用 13 列之外的列；三者都靠 JDBC `currentSchema=...,ecos_ontology,...`（gateway application.yml:15）
--     以**裸表名**访问 ⇒ 表必须落在 search_path 内的 ecos_ontology，DDL 里 schema 限定只用于归属声明（DR01/MC06 见下偏差登记）。
--
-- Schema 归属裁定: `ecos_ontology`（ST07 控制域本体引擎 schema）。依据 =
--   docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md §"B → ecos_ontology（15）" 已把
--   ecos_entity_table_mapping 列为该 schema 成员；且同表三表 JOIN 的对端 ecos_ontology_entity/
--   ecos_ontology_domain 实测在 ecos_ontology（information_schema 实查 10 张 ecos_ontology* 表）。
--   **不写 public**（V200 后 public 不再是本体域落点）、不写 ecos_dw（映射契约属控制域设计资产，非业务事实）。
--
-- 索引建立的硬依据（不是装饰）:
--   * UNIQUE (entity_code, datasource_id, resource_name) —— AutoDiscoverDao.upsertMapping 的
--     `ON CONFLICT (entity_code, datasource_id, resource_name) DO UPDATE` **必须有同名唯一约束**才存在，
--     缺它则自动发现落库第一次冲突就抛 "there is no unique or exclusion constraint matching"；
--     sysman 侧 countExistingMapping(同三元组) 是"先查后插"的竞态版，同一约束才是最终保障。
--     三列全 NOT NULL：可空列在唯一索引里互不相等，会让重复映射穿透上去。
--   * (domain_code)：listMappings 的 sourceType 过滤面（OntologyMappingDao.java:44）。
--   * 不建 (entity_code) 单列索引：唯一索引前导列已是 entity_code，`WHERE entity_code=?` 直接走它（避免冗余索引写放大）。
--
-- 被取代的登记（只声明，不执行任何 DROP/RENAME — IR03）:
--   V135/V145/V150 三条 `public.ecos_entity_table_mapping` 的 ALTER 语句，其 materialized / doc_anchor_json /
--   doc_anchor_type 三列**已在本 CREATE 内以最终列名与最终语义直接建出**（materialized 默认 TRUE = Q2 裁决原文；
--   doc_anchor_* = W2 语义，V150 的 DR04 改名已吸收）。这三枚脚本因此对本表不再有作用面，
--   保留原文作为裁决历史，不改写、不删除、不再执行。
--
-- 状态: **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属逐项授权项；纯新增表，对存量零影响）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)
-- 类型映射: field_mappings / doc_anchor_json = JSONB（代码写侧带 `::jsonb` 显式转换，见下方偏差登记 D2）
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_entity_table_mapping (
    id                VARCHAR(36)    PRIMARY KEY,             -- MC01 应用侧 UUID（三处 DAO 均应用侧生成后传入，DDL 零 gen_random_uuid()）
    entity_code       VARCHAR(100)   NOT NULL,                -- 本体对象 code（历史差异：可能存 id 也可能存 code，代码按双匹配读，故不加 FK）
    entity_name       VARCHAR(200),                           -- 来源/对象显示名（Controller 里与 resource_name 同值写入）
    domain_code       VARCHAR(64),                            -- 来源类型 = 本体域 code（listMappings 的 sourceType 过滤面）
    datasource_id     VARCHAR(64)    NOT NULL DEFAULT '',      -- 数据源引用；OntologyMappingDao.insertMapping 显式插空串 ⇒ NOT NULL 只禁缺列不禁空值
    resource_name     VARCHAR(200)   NOT NULL,                -- 资源（表）名
    table_schema      VARCHAR(64),                            -- 来源 URI/schema 名（可空：自动发现之外的人工映射可能未填）
    field_mappings    JSONB          NOT NULL,                -- 映射契约正文 {objectType,sourceType,fieldMappings,propertyMappings,description,status}
    materialized      BOOLEAN        NOT NULL DEFAULT TRUE,   -- Q2 裁决: TRUE=参与图谱实例化（默认，含历史行）, FALSE=显式关闭
    doc_anchor_json   JSONB,                                  -- W2: 非结构化文档锚点 {"docId","source","docChunkCount"}；NULL=无锚点
    doc_anchor_type   VARCHAR(32),                            -- W2: TABLE(默认语义)/DOC_ONLY/MIXED；代码把空白归一为 NULL ⇒ 禁 NOT NULL
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 列表 ORDER BY created_at DESC 的排序面（MC02 禁 timestamptz）
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 动态 UPDATE 末尾固定 SET updated_at=NOW()
    version_no        VARCHAR(20)    NOT NULL DEFAULT '1',    -- DR07 乐观锁预留（本表写路径当前不带版本比较，列先立、语义待应用侧接入）
    domain            VARCHAR(50)    NOT NULL DEFAULT 'default',          -- DR08 多租户预留
    is_deleted        SMALLINT       NOT NULL DEFAULT 0                    -- DR06 软删位（当前写路径是硬 DELETE，见待整改 R1）
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_v281_etm_entity_ds_resource
    ON ecos_ontology.ecos_entity_table_mapping (entity_code, datasource_id, resource_name);
CREATE INDEX IF NOT EXISTS idx_v281_etm_domain_code
    ON ecos_ontology.ecos_entity_table_mapping (domain_code);

COMMENT ON TABLE ecos_ontology.ecos_entity_table_mapping IS
    '本体实体 → 物理表映射契约（本体工作台产出、知识工作台实例抽取消费）；列清单单源 = OntologyMappingDao.MAPPING_COLUMNS；'
    '唯一约束 (entity_code, datasource_id, resource_name) 是 AutoDiscoverDao.upsertMapping ON CONFLICT 的前置条件';
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.materialized IS
    '是否参与图谱实例化: TRUE=参与(默认), FALSE=显式关闭（Q2 裁决原文，V135 登记，本脚本内联为建表默认）';
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_json IS
    '非结构化文档锚点（W2）：JSON {"docId":"...","source":"...","docChunkCount":N}；V150 改名后的最终列名';
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.doc_anchor_type IS
    '锚点类型：TABLE(默认)/DOC_ONLY/MIXED（W2，V145 登记）';
COMMENT ON COLUMN ecos_ontology.ecos_entity_table_mapping.field_mappings IS
    '映射契约 JSON（DR04/MC02 偏差列 D1/D2 登记对象：无 _json 后缀 + JSONB 形态，理由见脚本头部下方）';

-- ── 偏差登记（逐条写明"为什么不是标准形态"，禁悄悄违例）──────────────────
-- D1 [DR04] `field_mappings` 缺 `_json` 后缀：本表列名由**既有代码**冻结
--     （OntologyMappingDao.MAPPING_COLUMNS、OntologyMappingController 的 body.containsKey 语义、
--      OntologyMappingSaveDTO 注释"写入 field_mappings 列"、sysman INSERT 列表），
--     改名 = 三 DAO + 一 Controller + VO 反序列化面返工，属独立批次，不在"补建缺失表"这一刀里夹带。
--     登记口径 = 规范 §十 既有行「13 处 JSONB 无 `_json` 后缀：历史不重命名；新表必带」——
--     本列是**为存量代码补载体**，不是新语义列，故按存量列名对齐而非新表命名。
-- D2 [MC02] `field_mappings` / `doc_anchor_json` 用 JSONB 而非白名单 TEXT：
--     代码写侧是 `?::jsonb` 显式转换（OntologyMappingDao.java:81、AutoDiscoverDao.upsertMapping、
--     OntologyMappingController 的 `doc_anchor_json=?::jsonb`）。JSONB→TEXT 在 PG 无 assignment cast，
--     若建 TEXT 列，这三处 INSERT 会立即 bad SQL grammar（= 把 404/500 换成另一个 500，缺陷没修只搬家）。
--     且 V145 原始登记即声明 JSONB。收口方向 = 待 MC03 方言片段批次（`databaseId` + 非 PG 降级分支）一并转 TEXT。
-- D3 [MC03] 本表唯一约束的**消费方** AutoDiscoverDao.upsertMapping 裸用 PG `ON CONFLICT`，跨库不可移植：
--     存量代码问题，DDL 侧只提供约束、不新增违例；整改挂在 MC03 既有待办（工程清单 #6 同族）。
-- D4 [DR06] 时间审计列沿用代码契约 `created_at/updated_at`，未另立 `create_time/update_time`：
--     db-migration-lint.ps1 第 5 项判据是 `create_time|created_at` 二者居一（实装即此），
--     且规范 §十「`created_at/updated_at` 字段：存量不返工」。
--     **有意不补** create_time/update_time 双轨：两对时间戳无同步机制 ⇒ 必然漂移，
--     比缺一枚基线列更坏（禁为过 lint 造第二份真相）。
--     已补齐的基线列 = version_no(DR07) / domain(DR08) / is_deleted(DR06 软删位)。
-- D5 [MC06] DDL 硬编码 `ecos_ontology.` 前缀：与 V272/V273/V277~V280 同批同形态（单源目录内的既有 house style），
--     代码侧裸表名靠 currentSchema 解析；配置化改造属工程清单 #6（源码限定名批次），本脚本不单独破例。
-- R1 [待整改] `is_deleted` 立了但写路径是硬 DELETE（OntologyMappingDao.deleteMapping、
--     EntityTableMappingQueryDao.deleteMappingById 各一枚 `DELETE FROM ... WHERE id=?`）：
--     软删收敛 = 读路径全部加 is_deleted=0 谓词后才能启用，本次不改行为（API 只增不改 + 禁顺手清理）。
-- ────────────────────────────────────────────────────────────

-- ── 回滚说明 ──────────────────────────────────────────────
-- 幂等：CREATE TABLE/INDEX IF NOT EXISTS + COMMENT（可重复执行，第二次零变更零报错）。
-- 数据回滚：本脚本只新增一张当前**不存在**的表（实查 information_schema.tables = 0 行），
--   回滚 = 应用侧停用 /api/v1/ontology/mappings 族；删表只允许人工裁决（IR03），脚本不提供 DROP。
-- 实跑影响面（逐项授权时核对）：
--   1) 新增 ecos_ontology.ecos_entity_table_mapping（0 行）+ 2 枚索引；
--   2) 不触碰任何存量表/列/视图/策略；无 RLS 语句（控制域设计资产表，读经引擎与 security facade）；
--   3) 建表后 GET /api/v1/ontology/mappings?objectId=xxx 由 500 转 200（空列表），
--      POST/PUT/DELETE 与自动发现落库首次获得可写载体；无需重启 gateway（表在请求期解析）。
--
-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 表名小写下划线 + schema 限定（ecos_ontology.）✓  [DR02] ecos_ 前缀 ✓  [DR03] 表单数（无 _v2）✓
-- [DR04] doc_anchor_json 带后缀 ✓；field_mappings 违例=登记 D1  [DR05] is_deleted/materialized：materialized 属存量契约列名，登记于头部
-- [DR06] created_at/updated_at + is_deleted = 登记 D4  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain ✓
-- [MC01] VARCHAR(36) 主键、无 gen_random_uuid()/BIGSERIAL ✓  [MC02] JSONB=登记 D2，其余全在类型白名单（VARCHAR/TIMESTAMP/SMALLINT/BOOLEAN*）
--        * BOOLEAN 属存量契约列类型（V135 原文 + 代码按 Boolean 绑定），白名单外登记同族于 D2 处置口径
-- [MC03] 本脚本无 PG 专有语法；消费侧 ON CONFLICT 违例=登记 D3  [MC06] 限定前缀=登记 D5
-- [ST07] 控制域本体引擎 schema ✓（8 schema 封闭枚举内）  [ST03] 无敏感列，无需加密/豁免登记 ✓
-- [ST09] 无跨表 FK ✓（entity_code/domain_code/datasource_id 全为逻辑引用）
-- [IR02] 仅落文件，Flyway 禁用，实跑=人工 psql（待逐项授权）  [IR03] 只加不删 ✓
-- [IR06] 无跨引擎写：表归 ontology-engine，sysman 侧经自身 DAO 读写同一控制域表（存量形态，非新增跨域写通道）
