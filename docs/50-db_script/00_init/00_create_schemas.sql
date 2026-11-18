-- ============================================================================
-- 00_create_schemas.sql — ECOS 权威 schema 与扩展（初始化第 0 步）
-- ============================================================================
-- 依据: 《数据库访问规范》v1.4 ST07 八 schema 封闭枚举（2026-10-01 V200 schema 治理）。
--       新增任何 schema = FAIL；本文件即该枚举的唯一建库落点。
--
-- 八 schema 归属:
--   ecos_control    主控制域（IAM/配置/字典/审计/安全策略 + workspace 场景层
--                   + sysman/security/runtime/service 层新表一律落此）
--   ecos_data       土 D · data-engine（数据源/管道/函数/质量/血缘）
--   ecos_ontology   金 I · ontology-engine（本体/领域/术语/工作流模型）
--   ecos_knowledge  水 K · kb-engine（文档/切片/向量/图谱/抽取）
--   ecos_ai         火 W · ai-engine（Agent/Loop/LLM/技能/定时任务/成本）
--   ecos_cognitive  木 C · cognitive-engine（证据/假设/信念/决策/世界模型）
--   ecos_infra      横切底座（outbox 事件/运行时任务/监控）
--   ecos_dw         业务域五层数据湖（ODS/DWD/DWS/ADS/APPLICATION）
--
-- 关于 public（重要，勿误删）:
--   pgvector 与 pg_trgm 两个扩展的宿主 schema 实测就是 public
--   （pg_extension.extnamespace 实查：vector 0.8.7 / pg_trgm 1.6 均在 public）。
--   ⇒ 新库**必须保留 public schema**。原 public 下 59 张表已按 2026-11-17 审计处置完毕：
--      · 29 张归位到归属 schema（V345/V346，实库已搬，125 行保全）；
--      · 23 张被 ecos_control 遮蔽的 0 行死壳已清退（V349，实库已 DROP）；
--      · 剩 7 张独有表**归属待裁定**，见 11_public_pending.sql（单源即在 public 建、
--        main 代码零真实 SQL 引用、无归属信号 ⇒ 不臆造搬走，也不丢弃唯一副本）。
--   功能性引用另允许 public.vector / public.vector_cosine_ops / public.gin_trgm_ops。
--   应用 JDBC currentSchema 链末位仍含 public，作扩展类型解析兜底。
--
-- 不在本枚举内、初始化刻意不建的 schema:
--   ecos_sysman(8 表) / ecos_security(7 父表 + 分区子表) —— 全 0 行、不在任何 currentSchema，
--     已由 V349 在实库 DROP（含 schema 本身），新库不复建；
--   ecos_dq / ecos_datanet / ecos_buszhi / ecos_dccheng / ecos_aiming —— knownLegacy 空壳。
--   依据 ST07 + R9：存量只停写，新库一律不复建。
--
-- 运行: psql -U postgres -d <新库> -v ON_ERROR_STOP=1 -f 00_create_schemas.sql
-- 幂等: 全部 IF NOT EXISTS，可重复执行。
-- ============================================================================

-- 扩展宿主（PG 建库默认已存在 public；显式声明以防被前置脚本删除）
CREATE SCHEMA IF NOT EXISTS public;

-- 扩展：vector 供 ecos_knowledge.knowledge_embedding.embedding_vec vector(1536) 与 HNSW 索引；
--       pg_trgm 供 ecos_knowledge.graph_node.label 的 gin 模糊检索索引。
--       二者必须先于 01~08 建表，否则列类型/操作符类解析失败。
CREATE EXTENSION IF NOT EXISTS vector  WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- ST07 八 schema
CREATE SCHEMA IF NOT EXISTS ecos_control;
CREATE SCHEMA IF NOT EXISTS ecos_data;
CREATE SCHEMA IF NOT EXISTS ecos_ontology;
CREATE SCHEMA IF NOT EXISTS ecos_knowledge;
CREATE SCHEMA IF NOT EXISTS ecos_ai;
CREATE SCHEMA IF NOT EXISTS ecos_cognitive;
CREATE SCHEMA IF NOT EXISTS ecos_infra;
CREATE SCHEMA IF NOT EXISTS ecos_dw;

-- 收口自检：八 schema 齐备且扩展就位（缺任一即 fail-loud，禁带病继续）
DO $$
DECLARE
    v_need TEXT[] := ARRAY['ecos_control','ecos_data','ecos_ontology','ecos_knowledge',
                           'ecos_ai','ecos_cognitive','ecos_infra','ecos_dw'];
    v_s    TEXT;
    v_ext  TEXT;
BEGIN
    FOREACH v_s IN ARRAY v_need LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = v_s) THEN
            RAISE EXCEPTION 'ST07 schema 缺失: %', v_s;
        END IF;
    END LOOP;
    FOREACH v_ext IN ARRAY ARRAY['vector','pg_trgm'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = v_ext) THEN
            RAISE EXCEPTION '扩展缺失: %（01~08 的列类型/索引操作符类依赖它）', v_ext;
        END IF;
    END LOOP;
    RAISE NOTICE '00_create_schemas 自检通过：8 schema + vector/pg_trgm 就位';
END $$;