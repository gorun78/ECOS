-- V300 (#147A 补充设计册 R7~R11 数据面批 / DS-1·DS-2·DS-3·DQ-2 落位):
--   A. ecos_ontology.ecos_ontology_rule 新增 4 列（rule_carrier / rule_layer / scope / git_ref）
--      + 两枚值域 CHECK 约束（闭集）。
--   B. 新建 ecos_control.ecos_git_repo（Git 仓库注册表，裁决定名 DQ-2，td_git_repository 作废）
--      + 6 枚 repositoryId 种子（dw-pipeline / ontology-model / kb-asset-tree /
--        cognitive-model / agent-collab / scenario-def，§2.9.5① 语义化主键清单）。
--
-- 取证缘由（2026-10-31 现状实测，本脚本不发"已实现"叙述）:
--   ① 原设计册 §6.4.4 称 R10 已引入 5 列 ⇒ 本批按 dev PG 实表取证：
--      `ecos_ontology_rule` 现列 = id/entity_id/code/name/rule_type/expression/action/
--      priority/enabled/description/status/create_time/update_time/create_by/update_by/
--      is_deleted/**version_no**/domain ⇒ **version_no 已实存**（VARCHAR(20) NN DEFAULT '1'，
--      与 §6.4.4「乐观锁版本号」类型口径一致），本脚本**不重复加列**，只补缺失 4 列——
--      账实差异如实登记于此（DS-1 判据「5 列齐备」以实表终态核对，不以增量数核对）。
--   ② rule_layer 与 rule_type 正交（§6.4.4）：两列独立、值域互不推导；本脚本不建
--      任何推导约束/生成列，正交性由代码侧双维路由（#147B）与 DS-2 判据钉。
--   ③ 新列全部可空 + CHECK：存量行不受扰动（C-9 拒发布语义在应用层「缺 version_no/git_ref
--      拒发布」门前置成立，DDL 层不造假默认值冒充已登记）。
--   ④ 注册表落 **ecos_control** schema：dev PG 实测控制域新表已一律落 ecos_control
--      （V271/V293 先例 + ecos_cls_policy 等在位），主控制 schema 迁 public→ecos_control
--      既定口径；仓库根**单一事实源仍是 sys_config `ecos_git_repo_root`**
--      （GitRepoRootResolver，含路径穿越守卫），本表 repo_root 存「根下相对目录名」，
--      不存绝对路径（防双套缺省漂移，§2.9.9 G 系列缺口）。
--   ⑤ td_git_repository：dev PG 实**不存在**该表，唯余 datanet GitController L82/L87
--      两枚死腿 SQL（属 #147C 代码面收口，本脚本不动）。
--
-- 纪律: IR02(手动 psql -f，Flyway 永久禁用) / IR03(只加不删) / DR01(小写下划线+schema限定) /
--       DR02(ecos_ 前缀新表) / MC01(主键 VARCHAR(64) 应用侧语义键，禁库端 UUID 发生器) /
--       MC02(VARCHAR/INTEGER/TIMESTAMP/SMALLINT 白名单类型) / ST07(控制域落 ecos_control、
--       本体域落 ecos_ontology) / DR06(审计基线列齐备) / 不触 4 张废弃版本表（DQ-6 前置未满足）
--
-- ── A.1 rule 表补 4 列（幂等）──────────────────────────────────────────────
ALTER TABLE ecos_ontology.ecos_ontology_rule
    ADD COLUMN IF NOT EXISTS rule_carrier VARCHAR(32),
    ADD COLUMN IF NOT EXISTS rule_layer   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS scope        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS git_ref      VARCHAR(64);

-- ── A.2 值域闭集 CHECK（§6.4.4 值域列；可空⇒存量零扰动）────────────────────
-- 幂等形态订正（本批实跑两度取证，如实登记）：
--   ① 裸 ADD CONSTRAINT 复跑 ⇒ "constraint ... already exists" ERROR（首版缺陷）；
--   ② `ADD CONSTRAINT IF NOT EXISTS` ⇒ PG **不支持**该语法（syntax error at or near "NOT"，
--      容器实测 PostgreSQL 16.14——PG16 亦无该语法，禁凭"PG16 支持"的记忆口径写 SQL，以实跑为准）。
--   终态改 DO 块存在性守卫（幂等且与 PG 各代际兼容）。
--   MC03 可移植形态订正（本批同轮取证）：守卫查询原用 regclass 显式 cast 命中 PG_CAST 判据
--   （GOVERNED_PG_SYNTAX_PIN 自 V292 起成员级零新增），改 pg_constraint×pg_class×pg_namespace
--   三表联查按名判定，零 PG 专有语法，判定语义不变。
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c
                   JOIN pg_class t ON t.oid = c.conrelid
                   JOIN pg_namespace n ON n.oid = t.relnamespace
                   WHERE n.nspname = 'ecos_ontology'
                     AND t.relname = 'ecos_ontology_rule'
                     AND c.conname = 'ck_ontology_rule_carrier') THEN
        ALTER TABLE ecos_ontology.ecos_ontology_rule
            ADD CONSTRAINT ck_ontology_rule_carrier CHECK (
                rule_carrier IS NULL OR rule_carrier IN
                    ('ONTOLOGY_ELEMENT','RULE_OBJECT','KNOWLEDGE_ASSET'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint c
                   JOIN pg_class t ON t.oid = c.conrelid
                   JOIN pg_namespace n ON n.oid = t.relnamespace
                   WHERE n.nspname = 'ecos_ontology'
                     AND t.relname = 'ecos_ontology_rule'
                     AND c.conname = 'ck_ontology_rule_layer') THEN
        ALTER TABLE ecos_ontology.ecos_ontology_rule
            ADD CONSTRAINT ck_ontology_rule_layer CHECK (
                rule_layer IS NULL OR rule_layer IN
                    ('STRUCTURAL','VALIDATION','BUSINESS','POLICY','COGNITIVE'));
    END IF;
END
$$;

-- ── A.3 列注释登记（口径单源镜像）──────────────────────────────────────────
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.rule_carrier IS
    '规则载体（R10/§6.2.6③）：ONTOLOGY_ELEMENT执行载体/RULE_OBJECT声明治理载体/KNOWLEDGE_ASSET知识推理载体；与数据域 carrier_ref 消歧命名；V300 引入';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.rule_layer IS
    '规则层（R11/§6.2.6②五层分发）：STRUCTURAL/VALIDATION/BUSINESS/POLICY/COGNITIVE；与 rule_type 正交独立列、互不推导；空值记录由发布门禁拒发布告警（C-9）；V300 引入';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.scope IS
    '作用域（R10）：租户/组织/领域标识；V300 引入';
COMMENT ON COLUMN ecos_ontology.ecos_ontology_rule.git_ref IS
    'Git 归档引用（R10）：历史走 Git 归档单通道；缺 git_ref 拒发布（§6.2.6⑧）；V300 引入';

-- ── B.1 Git 仓库注册表（DQ-2 定名 ecos_git_repo；IF NOT EXISTS 幂等）────────
CREATE TABLE IF NOT EXISTS ecos_control.ecos_git_repo (
    id              VARCHAR(64)  PRIMARY KEY,          -- MC01 应用侧语义键（= repository_id 字面）
    repository_id   VARCHAR(64)  NOT NULL,             -- 语义化注册标识（运行时契约主键）
    repo_name       VARCHAR(128) NOT NULL,
    repo_root       VARCHAR(256),                      -- 根下相对目录名；绝对根单源=sys_config ecos_git_repo_root
    branch_default  VARCHAR(64)  NOT NULL DEFAULT 'main',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    create_time     TIMESTAMP    NOT NULL DEFAULT now(),
    update_time     TIMESTAMP    NOT NULL DEFAULT now(),
    create_by       VARCHAR(100),
    update_by       VARCHAR(100),
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    version_no      VARCHAR(20)  NOT NULL DEFAULT '1',
    domain          VARCHAR(50)  NOT NULL DEFAULT 'default'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ecos_git_repo_repository_id
    ON ecos_control.ecos_git_repo (repository_id)
    WHERE is_deleted = 0;

COMMENT ON TABLE ecos_control.ecos_git_repo IS
    'Git 仓库注册表（§2.9.5①/DQ-2 裁决定名；td_git_repository 作废）。服务端持久化注册面，'
    'repositoryId 由本表登记；仓库根路径单一事实源仍为 sys_config ecos_git_repo_root'
    '（GitRepoRootResolver 解析＋路径穿越守卫）。V300 建表';

-- ── B.2 repositoryId 种子 6 枚（§2.9.5① 清单；NOT EXISTS 守卫幂等）──────────
-- MC03 可移植形态订正（本批同轮取证）：原 upsert 冲突短语命中 ON_CONFLICT 判据
-- （GOVERNED_PG_SYNTAX_PIN 自 V292 起成员级零新增），改 VALUES 派生表 + NOT EXISTS
-- 反查守卫，重复执行零插入，判定语义不变、零 PG 专有语法。
INSERT INTO ecos_control.ecos_git_repo
    (id, repository_id, repo_name, repo_root, branch_default, create_by, update_by, domain)
SELECT v.id, v.repository_id, v.repo_name, v.repo_root, v.branch_default,
       v.create_by, v.update_by, v.domain
FROM (VALUES
    ('dw-pipeline',     'dw-pipeline',     '数据接入管道仓库',   'pipeline',        'main', 'V300', 'V300', 'default'),
    ('ontology-model',  'ontology-model',  '本体模型仓库',       'ontology',        'main', 'V300', 'V300', 'default'),
    ('kb-asset-tree',   'kb-asset-tree',   '知识库资产树仓库',   'kb-asset-tree',   'main', 'V300', 'V300', 'default'),
    ('cognitive-model', 'cognitive-model', '认知模型仓库',       'cognitive-model', 'main', 'V300', 'V300', 'default'),
    ('agent-collab',    'agent-collab',    'Agent 协同流程仓库', 'agent-collab',    'main', 'V300', 'V300', 'default'),
    ('scenario-def',    'scenario-def',    '场景定义仓库',       'scenario-def',    'main', 'V300', 'V300', 'default')
) AS v(id, repository_id, repo_name, repo_root, branch_default, create_by, update_by, domain)
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_git_repo g WHERE g.id = v.id);

-- ══ 实跑状态（2026-10-31，#147A 本批门禁会话取证，非叙述）══════════════════
-- 通道: docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 < 本脚本
--   ① dry-run（BEGIN…ROLLBACK）：全语句通过，INSERT 0 6；
--   ② 正式实跑：ALTER×3 / COMMENT×4 / CREATE TABLE / CREATE INDEX / INSERT 0 6 全回响；
--   ③ 落地后结构取证：rule 表 5 列齐备（4 新可空 + version_no 实存 NN），
--      CHECK 二枚在位（ck_ontology_rule_carrier / ck_ontology_rule_layer）；
--      ecos_control.ecos_git_repo 6 行种子（repository_id 语义键齐全）；
--   ④ 反向用例（能变红取证）：非法 rule_layer / rule_carrier INSERT 均报
--      "violates check constraint"（订正前后各实测一次，约束持续生效）；
--      订正前对照：无 CHECK 的同形 INSERT 成功（INSERT 0 1，事务回滚不留痕）＝约束必要性实证；
--   ⑤ 幂等复跑：DO 块守卫版二连跑零 ERROR，种子表 count 保持 6；
--   ⑥ 存量扰动：rule 表新列全可空 ⇒ 存量行零 UPDATE（本脚本无任何存量值改写）。
--   ⑦ MC03 形态订正后复跑（本批 gateway 门禁 GOVERNED_PG_SYNTAX_PIN 自 V292 成员级零新增，
--      原 cast 短语与 upsert 冲突短语两枚命中 ⇒ 正文改写，见 A.2/B.2 订正注）：
--      复跑实测零 ERROR / EXIT=0，NOTICE skipping×6（列/表/索引已存在），种子 INSERT 0 0，
--      count=6 / ck_cnt=2 复核保持；列完备负向探针仍红
--      （"violates check constraint ck_ontology_rule_layer"）＝改写未削弱任何在位判定。
