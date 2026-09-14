-- Wave B-3 T15 对象实例运行时数据表
-- 来源: 肖国荣 / 日期: 2026-09-12 / 责任人: fullstack-implementer
-- 工件: V119__ecos_ontology_data.sql (Flyway 路径, 区别于定义表 ecos_ontology_*)
--
-- 用途: OntologyDataController 的运行时数据记录持久化 (原 ConcurrentHashMap 内存态, 重启丢)
-- 字段对齐 6 基线列规范 (后端开发规范 §六): id/create_time/update_time/create_by/update_by/is_deleted
-- 幂等: CREATE TABLE IF NOT EXISTS, 重复执行不报错
--
-- 与定义表 ecos_ontology_* 的区别: 本表存"对象实例运行时数据" (一个 objectTypeId 下的多条 record),
-- 而定义表存"本体模型定义" (objectTypeId 自身)。一表一职责, 不混用。

CREATE TABLE IF NOT EXISTS public.ecos_ontology_data (
    id            VARCHAR(64)   PRIMARY KEY,
    ontology_id   VARCHAR(64)   NOT NULL,
    object_type   VARCHAR(32)   NOT NULL,
    record_key    VARCHAR(255),
    payload       TEXT,
    status        VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    create_time   TIMESTAMP     NOT NULL DEFAULT now(),
    update_time   TIMESTAMP     NOT NULL DEFAULT now(),
    create_by     VARCHAR(128),
    update_by     VARCHAR(128),
    is_deleted    SMALLINT      NOT NULL DEFAULT 0
);

-- 按 ontology_id 过滤 (列表/批量删索引)
CREATE INDEX IF NOT EXISTS idx_ecos_ontology_data_ontology ON public.ecos_ontology_data(ontology_id);

-- 唯一约束: 同一 ontology 下同一 object_type 同一 record_key 不可重复
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_ontology_data_key
    ON public.ecos_ontology_data(ontology_id, object_type, record_key);
