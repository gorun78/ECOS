-- V186 (卷04 §E.3 V186 行): 文档三载体收敛（三 → 一）—— 权威载体**认定**（非新建）+ 其余标 legacy 只停写
-- 追溯: K-20（`public.extraction_drafts`(4 行) / `ecos_knowledge.kb_doc`+`kb_doc_chunk`(V138) / `ecos_dw.doc`+`doc_chunk`(V139) 三套文档载体并存，
--       业务域出现第三套文档表与湖规分层归属冲突）+ K-37（上传落 java.io.tmpdir 绕 MinIO RAW）→ W96/C78 + W102/C84；
--       门禁 DocCarrierConvergenceTest#singleAuthoritativeDocTable、#uploadGoesThroughMinioNotTmpdir（F04-12）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 **R-10=①**：权威 = `ecos_knowledge`（知识层），
--       `ecos_dw.doc*` 定性演示只停写，`extraction_drafts` 降为草稿态；②（权威=ecos_dw）已否决——
--       会把切片/向量拉进业务域，与 MC05 冲突。执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 本脚本取 §E.3 V186 行"建/认"中的**认**支：R-10=① 下权威载体（`ecos_knowledge.kb_doc` / `kb_doc_chunk`，V138）**已存在**，
--   故不新建第四张文档表，只做三件事：① 用表/列注释把权威身份落盘；② 用登记视图把"唯一权威"变成可被门禁断言的数据；
--   ③ 其余两套载体定性 legacy 并登记停写口径。三套载体的**数据搬迁与代码切写**属实现对齐（本批不覆盖）。
-- 与 V139 口径的冲突（已在注释中覆写）: V139 把 `ecos_dw.doc`/`doc_chunk` 定义为"A1 目标态、数据工作台唯一写入"，
--   R-10=① 裁决后其身份降为演示遗留（业务域不承载文档/切片权威），本脚本不改其结构，只改其定性注释。
-- 与 V183/V184 的分工: `public.ecos_knowledge_document` 属 K-17 双镜像 6 对之一（同表跨 schema），
--   由 V183 镜像登记表 + V184 `ecos_kb_document` 统一主键宽度处理，本脚本不重复登记。

-- ── 1. 权威载体认定（ecos_knowledge.kb_doc / kb_doc_chunk，V138 既有表，零结构变更）──
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='ecos_knowledge' AND table_name='kb_doc') THEN
        COMMENT ON TABLE ecos_knowledge.kb_doc IS
            'V186/R-10① 权威载体（唯一）: 知识层非结构化文档级状态机（queued/parsing/extracting/done/failed）。DR 基线列（domain/version_no/is_deleted/审计五列/trace_id）由 V180 补齐；其余两套文档载体（public.extraction_drafts、ecos_dw.doc）自本脚本起定性 legacy 只停写。本表不新建、不迁不删（IR03）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc.object_key IS
            'V186/F04-12: MinIO RAW 近源层唯一入口对象 key（raw/unstructured/{source}/{docId}/{originalFileName}）。上传一律走 KnowledgeDocIngestService 合规链；java.io.tmpdir 仅作抽取临时缓冲且必须 TTL 清理，禁终存（K-37/W102）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc.text_source_path IS
            'V186: 解析文本登记为 CURATED 资源后的 source_path（经数据工作台登记端点，非直写 DW 表；ADR-2 A2 永久否决）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc.doc_id IS
            'V186 登记: 权威文档业务键（VARCHAR(128) 承重旧形态，MC01 新表口径为 VARCHAR(36)）。本批不改列类型（IR03 禁 ALTER 既有列型）；宽度收敛随代码切写另批申报';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='ecos_knowledge' AND table_name='kb_doc_chunk') THEN
        COMMENT ON TABLE ecos_knowledge.kb_doc_chunk IS
            'V186/R-10① 权威载体（唯一）: 知识层切片表（文本+序号+字符偏移+元数据）。A3 过渡语义自本脚本起转为目标态口径（知识层权威），V138「待 A1 落地迁入 DW」的退出计划随 R-10① 作废；切片/向量不得迁入业务域（MC05/R-10 ② 否决理由）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.embedding_id IS
            'V186: 关联 ecos_knowledge.knowledge_embedding.id（向量权威在知识层，V137/V185 形态 A；降级副本见 ecos_dw.ecos_kb_vector_fallback，非权威）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.metadata IS
            'V186 登记: 存量 JSONB 列，读写已切 V178 的 metadata_json 后本列停写（MC02/IR03，不 DROP）';
        COMMENT ON COLUMN ecos_knowledge.kb_doc_chunk.doc_id IS
            'V186: 权威链切片→文档外键（应用层保证，无跨 schema FK），指向本 schema kb_doc.doc_id';
    END IF;
END $$;

-- ── 2. 载体登记表视图（把"三 → 一"变成可断言的数据；门禁 singleAuthoritativeDocTable）──
-- 纪律: 视图非表（V183 同源做法）；is_authoritative=1 每类载体各唯一（文档级 1 行 kb_doc、切片级 1 行 kb_doc_chunk），
--   门禁可 SELECT carrier_role, COUNT(*) ... WHERE is_authoritative=1 GROUP BY carrier_role 断言每类恰为 1。
-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。

CREATE OR REPLACE VIEW ecos_knowledge.v_kb_doc_carrier_registry AS
SELECT * FROM (VALUES
    -- schema            table              carrier_role        is_authoritative  write_authority      converge_action
    ('ecos_knowledge', 'kb_doc',           '文档级状态机',       1,                'ecos_knowledge',    '权威保留（本批零结构变更，V180 补 DR 基线列）'),
    ('ecos_knowledge', 'kb_doc_chunk',     '切片',               1,                'ecos_knowledge',    '权威保留（metadata→metadata_json 双写，V178）'),
    ('public',         'extraction_drafts','抽取草稿',           0,                'ecos_knowledge',    '降为草稿态：只承抽取中间产物，禁作文档库写入；文档/切片读写切 kb_doc*'),
    ('ecos_dw',        'doc',              '演示遗留文档表',     0,                'ecos_knowledge',    '停写：V139 "A1 目标态" 口径被 R-10① 覆写为演示遗留；不 DROP（IR03）'),
    ('ecos_dw',        'doc_chunk',        '演示遗留切片表',     0,                'ecos_knowledge',    '停写：同上；metadata 存量 JSONB 列随停写一并冻结'),
    ('ecos_knowledge', 'knowledge_embedding','向量载体',         0,                'ecos_knowledge',    '向量权威在知识层（V137/V185 形态 A），非文档载体故标 0；降级副本 ecos_dw.ecos_kb_vector_fallback 亦非权威'),
    ('public',         'ecos_knowledge_document','双镜像文档表', 0,                'ecos_knowledge',    'K-17 双镜像 6 对之一，由 V183 镜像登记表 + V184 ecos_kb_document 处理，本视图仅占位供对账')
) AS r(carrier_schema, carrier_table, carrier_role, is_authoritative, write_authority_schema, converge_action);

COMMENT ON VIEW ecos_knowledge.v_kb_doc_carrier_registry IS
    'V186/R-10①: 文档与切片载体登记表（三→一 唯一权威的可断言口径）。is_authoritative=1 仅 kb_doc/kb_doc_chunk 两行；'
    '门禁 DocCarrierConvergenceTest#singleAuthoritativeDocTable 据此 + information_schema 对账。视图只读、不写、无数据搬移';

-- ── 3. public.extraction_drafts 定性为"抽取草稿"（非文档库权威）──
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='extraction_drafts') THEN
        COMMENT ON TABLE public.extraction_drafts IS
            'V186/R-10① 定性: 抽取**草稿态**载体（候选/待确认的抽取中间产物），非文档库权威。文档级与切片级读写自本口径起归 ecos_knowledge.kb_doc / kb_doc_chunk（权威）；本表存量行（live 实测 4 行）保留不删（IR03 + R-12② 逻辑隔离），无单源 DDL 属 v1 时代实表（V180 注释已登记）';
        -- 本表无单源 DDL（v1 时代实表），列存在性逐列探测后再注释（V162 未跑时该列不存在，跳过不报错）
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema='public' AND table_name='extraction_drafts'
                     AND column_name='extracted_links_json') THEN
            COMMENT ON COLUMN public.extraction_drafts.extracted_links_json IS
                'V186 登记: 草稿态抽取产物（V162 收编列，TEXT 合规）。切写权威载体后由 kb_doc 侧字段承载，本列随草稿态生命周期退役';
        END IF;
    END IF;
END $$;

-- ── 4. ecos_dw.doc / doc_chunk 演示遗留只停写（零 DDL 变更，仅定性注释）──
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='ecos_dw' AND table_name='doc') THEN
        COMMENT ON TABLE ecos_dw.doc IS
            'V186/R-10① 定性: 演示遗留（legacy）文档表，**只停写**：新文档一律写 ecos_knowledge.kb_doc（唯一权威）。V139 曾按 A1 目标态将其定义为「数据工作台唯一写入」，该口径已被 R-10① 覆写（业务域不承载文档权威）。本批不 DROP、不 ALTER（IR03；R-12 DROP 重建前置件——备份+引用扫描+代码切换——未闭环）';
        COMMENT ON COLUMN ecos_dw.doc.doc_id IS
            'V186 登记: 停写后本列仅作历史对账键；权威文档业务键为 ecos_knowledge.kb_doc.doc_id（应用层映射，无跨 schema FK）';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='ecos_dw' AND table_name='doc_chunk') THEN
        COMMENT ON TABLE ecos_dw.doc_chunk IS
            'V186/R-10① 定性: 演示遗留（legacy）切片表，**只停写**：切片一律写 ecos_knowledge.kb_doc_chunk。切片/向量属 K 层，迁入/落 DW 会与 MC05 冲突（R-10 ② 否决理由）；不 DROP、不 ALTER（IR03）';
        COMMENT ON COLUMN ecos_dw.doc_chunk.metadata IS
            'V186 登记: 存量 JSONB 列，随本表停写一并冻结（MC02 不为本表补 _text 列——停写载体无读写路径，补列即制造第四形态）';
    END IF;
END $$;

-- ── 5. 数据资源登记口径对账（V139 曾把两条登记为 A1 目标态 CURATED 资产）──
-- 只改 description 文本，使登记说明与 R-10① 定性一致；status/layer/zone 零变更（不动生命周期与分层归属），不 DELETE。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema='public' AND table_name='td_data_resource') THEN
        UPDATE public.td_data_resource SET description =
            'V186/R-10① 定性: 演示遗留（legacy）文档表，只停写；A1 目标态口径已被覆写（权威 = ecos_knowledge.kb_doc）'
            WHERE resource_id = 'res-dw-doc';
        UPDATE public.td_data_resource SET description =
            'V186/R-10① 定性: 演示遗留（legacy）切片表，只停写；权威切片 = ecos_knowledge.kb_doc_chunk'
            WHERE resource_id = 'res-dw-doc-chunk';
    END IF;
END $$;

-- ── 6. 存量数据迁 0（本批不执行；实现批按此口径做，且必须先过引用扫描）────────
-- 说明: §E.3 V186 行的"数据搬迁"属实现动作，需应用侧写入路径（DocParseService / ExtractionService 等）先切写，
--   故本文件不含 INSERT/UPDATE 载体数据语句（除第 5 节登记说明文本外）。
--   实现期待办（另批，逐项再确认）：
--     a) public.extraction_drafts 中与文档级重复的行 → 归 ecos_knowledge.kb_doc（doc_id 幂等 upsert），草稿行保留；
--     b) ecos_dw.doc / doc_chunk 存量行（V139 演示种子）→ 按 R-12② 以 domain/is_deleted 逻辑隔离，不 DELETE；
--     c) 上传落点改造（java.io.tmpdir → MinIO RAW），非 DDL。
-- 回滚: 第 1/3/4 节均为 COMMENT 覆盖，回滚 = 恢复原注释文本（原语义已在本文件头部与 V138/V139 注释中留存）；
--   第 2 节视图为 REPLACE，可 DROP VIEW 回滚（本文件不写 DROP，回滚语句留给人工执行：
--   -- DROP VIEW IF EXISTS ecos_knowledge.v_kb_doc_carrier_registry;）；
--   第 5 节 UPDATE 幂等（目标文本恒定），回滚 = 恢复 "A1 目标态…" 原文本。整文件可重复执行。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全对象 schema 限定小写 ✓  [DR02] 本文件零新建表（取"认"支；视图 v_kb_* 沿用 V183 v_ 前缀命名）✓
-- [DR03] 单数 ✓  [DR04] 零新列，故零 JSONB 新增；JSONB 仅出现在"存量列停写"登记注释 ✓
-- [DR05] 零新列（is_deleted 由 V180 补齐）✓  [DR06]/[DR07]/[DR08] 同上，基线列归 V180 ✓
-- [MC01] 零新 PK/SERIAL/gen_random_uuid ✓  [MC02] 零 JSONB / 零裸 NUMERIC ✓
-- [MC03] 零 partial index（本文件零 CREATE INDEX）/ 零 CREATE POLICY / PARTITION BY / text[] / timestamptz / `::` ✓
-- [ST03-A] 本文件零金额/比例列（文档载体无量化列）✓
-- [ST07] 白名单内（ecos_knowledge / public / ecos_dw）；权威=知识层，业务域只承载停写遗留与降级副本 ✓
-- [IR03] 只加不删：零 DROP / 零 ALTER DROP / 零改既有列类型；kb_doc.doc_id 宽度 128 保留并登记 ✓
-- [R-12] ① DROP 重建前置未闭环 ⇒ 三载体全部并建/认定 + 旧载体停写 ✓
-- [零实跑] 未连库、未执行 psql；本文件为迁移单源记录（IR02 手动执行，待逐项授权）✓
