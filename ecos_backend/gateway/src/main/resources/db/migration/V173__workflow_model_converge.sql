-- V173 (卷03 §E.3 / F03-12+F03-13): workflow 单模型收敛到控制域 ecos_ontology —— 并建合规主表+节点/边投影表+审批表合规形态；v1 trigger_event 投影为节点属性；旧 public 侧停写不迁
-- 追溯: W85/C69（O-17：ecos_workflow_approval 只存在于 ecos_ontology，代码裸名查询必然 relation does not exist —— 结构必败）；W86/C70、W88（O-16 双模型 / O-18 物理删除）；REQ-ONTO-01 / PRD-08
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-7=归属①（控制域 ecos_ontology）+ 模型 b（ecos_workflow_v2 的 nodes/edges 形态为唯一模型；v1 trigger_event 投影为节点属性；主表与审批表同源同域）
-- 号段: 整数号接续段（卷02 止于 V171 整号，V173 无冲突；V172 为本组上序文件）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 表名说明：文档 F03-12 将目标单模型定名 `ecos_workflow`（v2 形态内容），故新表 = ecos_ontology.ecos_workflow；
--   与旧 public.ecos_workflow（v1 trigger_event 形态，V2__ecos_workflow.sql）同名不同 schema，靠限定名区分，
--   文档定名保留不另造。**不建 FOREIGN KEY**（跨 schema/跨表引用由应用侧维护，任务红线）。
-- 列名收口：文档示例 nodes/edges 未带 _json 后缀，违 DR04（JSON 语义列 _json + TEXT）→ 目标列定名
--   nodes_json/edges_json（红线优先，差异已登记汇报）；MC02 附则（E.4）：两列只做存取，禁 WHERE/JOIN/索引，
--   检索走投影表（本节 2）。
-- 【2026-09-30 校订 · 实跑可行性】只读取证（information_schema.columns + count(*)）实测：
--   `ecos_ontology.ecos_workflow` **实存**（11 列：id varchar(64)/name varchar(255)/description text/status
--   varchar(32)/mode varchar(32)/nodes text/edges text/published_at/tenant_id varchar(64)/created_at/updated_at，**0 行**）。
--   原 §1 直接按目标态建表 → 在现网 IF NOT EXISTS 为 no-op，§4a/4b 的 INSERT 引用 nodes_json/is_deleted/
--   version_no/domain 等列会报 "column does not exist"，脚本必败；空库重建则得到与现网不同的形态。
--   修正：CREATE 块**镜像现网形态**，目标态列全部下移为 ALTER ADD COLUMN IF NOT EXISTS（两条路径均可执行）。
--   同理修 §3a 审批表（现网 id varchar(50)、task_id/instance_id varchar(50)、form_data jsonb、created_at NOT NULL，0 行）。
-- 未落子项（原因）：① v2 nodes/edges JSON 逐元素拆行入投影表 —— 需 PG 专有 JSON 集合展开函数，与 MC03
--   多库纪律冲突，且文档 F03-12 本身定义投影表为"保存时拆解写入"（代码侧机制），故本脚本只建表 +
--   迁移已有独立 node/edge 台账（public 侧该两表无仓库 DDL 基线，列形态未验证，不做盲迁）；
--   ② workflow_node_type 字典 seed（E.3 尾注）属 sysman 字典表批次，未落。
-- 回滚说明：全部为增量对象（CREATE/ADD COLUMN/幂等 INSERT/视图）；回滚 = 代码回退读旧表，新表停写即可，
--   旧表数据零改动。

-- ── 1. 合规主表（CREATE 严格镜像现网 11 列形态；目标态列全部走 §1b ALTER 增补）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow (
    id            VARCHAR(64) PRIMARY KEY,            -- 现网镜像（MC01 只约束新表；迁入侧统一 left(...,36)）
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    status        VARCHAR(32) DEFAULT 'draft',
    mode          VARCHAR(32) DEFAULT 'sequential',   -- sequential/parallel（v2 形态）
    nodes         TEXT,                               -- 现网列（knownLegacy：无 _json 后缀，DR04 存量漂移，只停写不改名）
    edges         TEXT,
    published_at  TIMESTAMP,
    tenant_id     VARCHAR(64),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 现网列名（knownLegacy，DR06 反例只停写）
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 1b. 目标态增补列（幂等；空库重建与现网两条路径收敛到同一形态）
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS code          VARCHAR(64);   -- v1 迁入列（v2 无 code，留兼容位）
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS workflow_type VARCHAR(20);   -- v1 迁入属性投影位
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS nodes_json    TEXT;          -- DR04 唯一模型列，只做存取（E.4 禁 WHERE）
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS edges_json    TEXT;          -- DR04
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS create_time   TIMESTAMP;     -- DR06（新写用；与 created_at 双写过渡）
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS update_time   TIMESTAMP;
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS create_by     VARCHAR(100);
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS update_by     VARCHAR(100);
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS is_deleted    SMALLINT NOT NULL DEFAULT 0;  -- DR05（删除=置 1，禁物理 DELETE，F03-12 修 W86/O-18）
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS version_no    VARCHAR(20) NOT NULL DEFAULT '1';      -- DR07
ALTER TABLE ecos_ontology.ecos_workflow ADD COLUMN IF NOT EXISTS domain        VARCHAR(50) NOT NULL DEFAULT 'default';-- DR08
COMMENT ON TABLE ecos_ontology.ecos_workflow IS '工作流定义唯一模型表（R-7 归属①+模型 b；旧 public.ecos_workflow(v1,8 行) 与 public.ecos_workflow_v2(11 行) 停写只读，数据收敛本表）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_workflow_name ON ecos_ontology.ecos_workflow(name, is_deleted); -- 复合唯一含 is_deleted（MC03 禁 partial index）
CREATE INDEX IF NOT EXISTS idx_ecos_workflow_status ON ecos_ontology.ecos_workflow(status);
CREATE INDEX IF NOT EXISTS idx_ecos_workflow_domain ON ecos_ontology.ecos_workflow(domain);

-- ── 2. 节点/边投影表（检索走此对表，E.4；保存时由代码拆解写入，本脚本仅建合规形态）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_node (
    id              VARCHAR(36) PRIMARY KEY,          -- MC01
    workflow_id     VARCHAR(36) NOT NULL,             -- 应用侧维护引用，不建 FK（任务红线）
    node_key        VARCHAR(100) NOT NULL,            -- JSON 内节点 id
    name            VARCHAR(255),
    node_type       VARCHAR(20),                      -- start/task/ai/end（字典 workflow_node_type）
    sort_order      INTEGER,
    attributes_json TEXT,                             -- DR04: 节点属性（含 v1 trigger_event 投影，见 §4b）
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT NOT NULL DEFAULT 0,
    version_no      VARCHAR(20) NOT NULL DEFAULT '1',
    domain          VARCHAR(50) NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE ecos_ontology.ecos_workflow_node IS '工作流节点投影表（F03-12；供血缘与校验检索，主表 nodes_json 不参与 WHERE）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_wf_node ON ecos_ontology.ecos_workflow_node(workflow_id, node_key, is_deleted);

CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_edge (
    id               VARCHAR(36) PRIMARY KEY,         -- MC01
    workflow_id      VARCHAR(36) NOT NULL,
    edge_key         VARCHAR(100) NOT NULL,           -- JSON 内边 id
    source_node_key  VARCHAR(100) NOT NULL,
    target_node_key  VARCHAR(100) NOT NULL,
    sort_order       INTEGER,
    attributes_json  TEXT,                            -- DR04
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by        VARCHAR(100),
    update_by        VARCHAR(100),
    is_deleted       SMALLINT NOT NULL DEFAULT 0,
    version_no       VARCHAR(20) NOT NULL DEFAULT '1',
    domain           VARCHAR(50) NOT NULL DEFAULT 'default'
);
COMMENT ON TABLE ecos_ontology.ecos_workflow_edge IS '工作流边投影表（F03-12）';
CREATE UNIQUE INDEX IF NOT EXISTS uniq_ecos_wf_edge ON ecos_ontology.ecos_workflow_edge(workflow_id, edge_key, is_deleted);

-- ── 3. 审批表合规形态（修 W85/O-17：与主表同源 ecos_ontology；主表+审批表不再跨 schema 分裂）──
-- 3a. 空库兜底建表（**严格镜像现网 8 列形态**：id varchar(50)/task_id、instance_id varchar(50) 可空/
--     decision varchar(50)/form_data jsonb/created_at NOT NULL，实测 0 行；现网实存时本句为 no-op）
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_workflow_approval (
    id           VARCHAR(50) PRIMARY KEY,             -- 现网镜像（MC01 只约束新表；容纳 36 位 UUID，偏差登记 knownLegacy）
    task_id      VARCHAR(50),
    instance_id  VARCHAR(50),
    approver     VARCHAR(100),
    decision     VARCHAR(50),                         -- Approved/Rejected/Transferred
    opinion      TEXT,
    form_data    JSONB,                               -- 现网镜像列（knownLegacy：MC02 只约束新表；合规列 form_json 见 3b）
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP  -- 现网列名（代码 ORDER BY 依赖，保留）
);
-- 3b. 实存旧形态表补合规列（全部幂等；禁改已有列，旧 JSON 二进制类型列 form_data 只停写不删）
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS form_json   TEXT;
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS create_time TIMESTAMP;
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS update_time TIMESTAMP;
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS create_by   VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS update_by   VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE IF EXISTS ecos_ontology.ecos_workflow_approval ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
-- 3c. 旧表单列内容幂等回填（仅当旧列实存；目标方言 PostgreSQL，CAST 非 ::）
DO $$
BEGIN
    IF to_regclass('ecos_ontology.ecos_workflow_approval') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'ecos_ontology' AND table_name = 'ecos_workflow_approval' AND column_name = 'form_data') THEN
        EXECUTE $q$
            UPDATE ecos_ontology.ecos_workflow_approval
               SET form_json = CAST(form_data AS TEXT)
             WHERE form_json IS NULL AND form_data IS NOT NULL
        $q$;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_ecos_wf_appr_task     ON ecos_ontology.ecos_workflow_approval(task_id);
CREATE INDEX IF NOT EXISTS idx_ecos_wf_appr_instance ON ecos_ontology.ecos_workflow_approval(instance_id);
COMMENT ON TABLE ecos_ontology.ecos_workflow_approval IS '工作流审批表（W85/C69 修复载体：与主表同域 ecos_ontology；代码侧必须 schema 限定查询（F03-13），裸名即 SchemaDriftLintTest CI 红）';

-- ── 4a. 数据迁移：public.ecos_workflow_v2（11 行，唯一模型来源）→ 新主表（幂等）──────
DO $$
BEGIN
    IF to_regclass('public.ecos_workflow_v2') IS NOT NULL THEN
        EXECUTE $q$
            INSERT INTO ecos_ontology.ecos_workflow
                (id, code, name, description, status, mode, nodes_json, edges_json,
                 workflow_type, published_at, create_time, update_time, create_by, update_by,
                 is_deleted, version_no, domain)
            SELECT left(o.id, 36), NULL, o.name, o.description,
                   COALESCE(o.status, 'draft'), o.mode,
                   CAST(o.nodes AS TEXT), CAST(o.edges AS TEXT),   -- CAST 标准写法（MC03 禁 ::；PG 目标方言）
                   NULL, o.published_at,
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, o.created_at, CURRENT_TIMESTAMP),
                   'system-legacy', 'system-legacy', 0, '1', 'default'
              FROM public.ecos_workflow_v2 o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_workflow n WHERE n.id = left(o.id, 36))
        $q$;
        RAISE NOTICE 'V173: ecos_workflow_v2 → ecos_ontology.ecos_workflow 收敛完成（幂等）';
    ELSE
        RAISE NOTICE 'V173: public.ecos_workflow_v2 不存在，跳过 4a';
    END IF;
END $$;

-- ── 4b. 数据迁移：public.ecos_workflow（v1，8 行，trigger_event 式）→ 新主表 + trigger_event 投影为起始节点属性 ──
DO $$
BEGIN
    IF to_regclass('public.ecos_workflow') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'ecos_workflow' AND column_name = 'trigger_event') THEN
        EXECUTE $q$
            INSERT INTO ecos_ontology.ecos_workflow
                (id, code, name, description, status, mode, nodes_json, edges_json,
                 workflow_type, published_at, create_time, update_time, create_by, update_by,
                 is_deleted, version_no, domain)
            SELECT left('v1_' || o.id, 36), o.code, o.name, o.description,
                   COALESCE(o.status, 'draft'), NULL, '[]', '[]',   -- v1 无 nodes/edges，置空数组，触发语义入投影节点
                   o.workflow_type, NULL,
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, o.created_at, CURRENT_TIMESTAMP),
                   'system-legacy', 'system-legacy', 0,
                   left(COALESCE(CAST(o.version AS VARCHAR), '1'), 20), 'default'
              FROM public.ecos_workflow o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_workflow n WHERE n.id = left('v1_' || o.id, 36))
        $q$;
        -- trigger_event 投影为 start 节点属性（R-7 模型 b 口径；纯字符串拼接，不涉 JSON 类型操作符）
        EXECUTE $q$
            INSERT INTO ecos_ontology.ecos_workflow_node
                (id, workflow_id, node_key, name, node_type, sort_order, attributes_json,
                 create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
            SELECT left('v1n_' || o.id, 36), left('v1_' || o.id, 36), 'legacy-trigger',
                   'v1 触发入口', 'start', 0,
                   '{"triggerEvent":' || COALESCE(quote_literal(o.trigger_event), '""') ||
                   ',"workflowType":' || COALESCE(quote_literal(o.workflow_type), '""') || '}',
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system-legacy', 'system-legacy', 0, '1', 'default'
              FROM public.ecos_workflow o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_workflow_node n
                               WHERE n.id = left('v1n_' || o.id, 36))
        $q$;
        RAISE NOTICE 'V173: v1 ecos_workflow 8 行收敛 + trigger_event 节点投影完成（幂等；PG 方言函数 quote_literal，IR02 手动 psql 单库执行）';
    ELSE
        RAISE NOTICE 'V173: public.ecos_workflow 缺表或缺 trigger_event 列（V2__ 锚点环境），跳过 4b';
    END IF;
END $$;
-- 迁移验收（人工，WorkflowRowParityAfterV173Test 同口径）：
--   SELECT count(*) FROM ecos_ontology.ecos_workflow;  -- 期望 = v2 11 行 + v1 8 行 = 19（去重后）

-- ── 5. 旧表停写登记（不迁不删，R-1 a 只加限定名；knownLegacy 只停写）──────────
DO $$
BEGIN
    IF to_regclass('public.ecos_workflow') IS NOT NULL THEN
        EXECUTE $q$ COMMENT ON TABLE public.ecos_workflow IS
            '工作流 v1 旧模型（trigger_event 式，8 行）：R-7 模型 b 作废形态，V173 起停写；数据已收敛 ecos_ontology.ecos_workflow；不删（IR03），清理走 PMO 专项' $q$;
    END IF;
    IF to_regclass('public.ecos_workflow_v2') IS NOT NULL THEN
        EXECUTE $q$ COMMENT ON TABLE public.ecos_workflow_v2 IS
            '工作流 v2 过渡表（11 行）：内容即唯一模型来源，V173 起停写于 ecos_ontology.ecos_workflow；sysman PortalSearchQueryService 对该表的跨域直查属 W88/O-19 代码整改项；不删（IR03）' $q$;
    END IF;
END $$;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全对象 schema 限定 ecos_ontology./public. ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] nodes_json/edges_json/attributes_json/form_json 均 _json + TEXT ✓（现网 nodes/edges/form_data 镜像保留=knownLegacy）
-- [DR05] is_deleted/is_* SMALLINT ✓
-- [DR06] 新建投影表（ecos_workflow_node/edge）规范五列齐 ✓；实存主表/审批表 created_at/updated_at 列名不动（IR03），
--        create_time/update_time/create_by/update_by 以 §1b/§3b ALTER 增补，新写用规范列（E.3 漂移同法）
-- [DR07/08] ALTER 补齐列均带 DEFAULT，避免历史行违反 NOT NULL ✓
-- [MC01] **新建**表 PK 全部 VARCHAR(36) 且无默认值函数 ✓；ecos_ontology.ecos_workflow(id varchar(64)) 与
--        ecos_workflow_approval(id varchar(50)) 为现网实存表镜像（迁入侧 left(...,36) 归一），偏差登记 knownLegacy
-- [MC02] 新表无 JSON 类型列；现网 jsonb 列（form_data）按镜像保留 + form_json TEXT 承接合规读写
--        CAST(... AS TEXT) 读旧列，不新增 JSON 类型使用 ✓
-- [MC03] 唯一索引均复合含 is_deleted，无 partial index；无 CREATE POLICY / PARTITION BY / text[] / timestamptz / :: ✓
--        （quote_literal / to_regclass / left() 属 PG 方言函数，IR02 手动 psql 单库执行，已登记为数据迁移批次专用）
-- [ST07] 新表全部落控制域 ecos_ontology（R-7 归属①）；审批表与主表同源（修 O-17 跨 schema 分裂）✓
-- [IR02] 手动 psql，未实跑 ✓  [IR03] 旧表/旧列零删改，仅停写注释 ✓  无跨 schema FOREIGN KEY ✓
