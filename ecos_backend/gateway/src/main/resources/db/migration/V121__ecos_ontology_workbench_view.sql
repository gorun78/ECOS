-- ============================================================
-- V121__ecos_ontology_workbench_view.sql
-- 本体工作台 5 类视图对象统一存储表（Wave B-5 T7）
-- 来源: 全栈开发工程师 / 日期: 2026-09-13
--
-- 背景: 前端本体工作台 5 类视图 (action / interface / shared_property /
--       function / dataset) 此前完全本地 state, 无任何后端持久化。
--       T7 以"object 视图"统一存储: 按 object_type 区分 5 类,
--       definition_json 承载各类差异字段, 字段对齐前端 5 类 TypeScript 视图对象。
--
-- 设计:
--   1. 单表统一 (避免 5 张同质表), object_type + ontology_id 建索引覆盖
--      list 查询 (GET /ontologies/{id}/objects?type=X)
--   2. definition_json: 各类差异字段 JSON 原文, 透传前端结构, 不落子列
--      (嵌套动态结构, 与 T16-1 action preconditions/effects 同豁免策略)
--   3. 6 基线列 (id/create_time/update_time/create_by/update_by/is_deleted)
--      + status (逻辑删除 T17 语义: is_deleted=1 + status='ARCHIVED')
--
-- 幂等性: CREATE TABLE IF NOT EXISTS, 可重复执行
-- Schema 铁律: 只加不删 (3.1); 表建于 public, search_path 含 public 回退
-- ============================================================

CREATE TABLE IF NOT EXISTS public.ecos_ontology_workbench_object (
    id              VARCHAR(64)   PRIMARY KEY,
    ontology_id     VARCHAR(64)   NOT NULL,
    object_type     VARCHAR(32)   NOT NULL,
    code            VARCHAR(255)  NOT NULL,
    name            VARCHAR(255)  NOT NULL,
    description     TEXT          NOT NULL DEFAULT '',
    definition_json TEXT          NOT NULL DEFAULT '',
    status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP     DEFAULT now(),
    update_time     TIMESTAMP     DEFAULT now(),
    create_by       VARCHAR(128),
    update_by       VARCHAR(128),
    is_deleted      SMALLINT      NOT NULL DEFAULT 0
);

-- list 查询索引: 按本体 + 类型过滤 (idx_表名_字段)
CREATE INDEX IF NOT EXISTS idx_ecos_ontology_workbench_object_ontology_type
    ON public.ecos_ontology_workbench_object (ontology_id, object_type);

-- 类型过滤二级索引 (含逻辑删除过滤列覆盖)
CREATE INDEX IF NOT EXISTS idx_ecos_ontology_workbench_object_type
    ON public.ecos_ontology_workbench_object (object_type, is_deleted);
