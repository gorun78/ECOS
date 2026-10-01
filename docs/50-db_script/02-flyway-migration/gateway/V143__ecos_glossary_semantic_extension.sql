-- ============================================================
-- V143__ecos_glossary_semantic_extension.sql
-- 本体工作台 Wiki（词条库）语义资产化扩展
-- ============================================================
-- 背景：词条库原为「名词解释字典」（name/definition/domain/status），
--       本体模型无法消费，词条之间也无法表达关系。
-- 本迁移做两件事：
--   ① ecos_glossary_term 增加 7 列语义字段（分类/别名/示例/标签/版本/
--      所属实体/上位词条），使其成为可被本体引用的语义资产；
--   ② 新建 ecos_glossary_term_relation 关系边表（6 类基础关系），
--      支撑前端「关系」与「图谱」视图。
-- 说明：Flyway 在本项目已禁用，本脚本需手工执行（见仓库 seed 执行惯例）。
--       全部语句幂等，可重复执行。
-- ============================================================

-- ════════════════════════════════════════════════════════════
-- ① ecos_glossary_term 语义字段扩展（7 列）
-- ════════════════════════════════════════════════════════════

-- 词条分类：ENTITY 实体类 / RELATION 关系类 / METRIC 指标类
--           / FUNCTION 函数类 / CONCEPT 概念类
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS term_type      VARCHAR(32) DEFAULT 'CONCEPT';

-- 同义词 / 别名列表（检索归一化的关键）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS aliases        TEXT[] DEFAULT '{}';

-- 引用的本体实体主键（ecos_ontology_entity.id，如 ent001）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS object_type_id VARCHAR(64);

-- 上位词条（is-a 关系的内联快捷方式，与关系表的 ISA 边互为补充）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS parent_term_id BIGINT;

-- 词条定义版本号（定义演进，从 1 起）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS version        INTEGER DEFAULT 1;

-- 示例值列表（让定义有锚定）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS examples       TEXT[] DEFAULT '{}';

-- 方法论 / 场景标签（检索辅助）
ALTER TABLE public.ecos_glossary_term
    ADD COLUMN IF NOT EXISTS tags           TEXT[] DEFAULT '{}';

-- 上位词条自引用外键（词条删除时子词条 parent 置空，不级联删除）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_glossary_term_parent'
    ) THEN
        ALTER TABLE public.ecos_glossary_term
            ADD CONSTRAINT fk_glossary_term_parent
            FOREIGN KEY (parent_term_id) REFERENCES public.ecos_glossary_term(id)
            ON DELETE SET NULL;
    END IF;
END $$;

COMMENT ON COLUMN public.ecos_glossary_term.term_type      IS '词条分类: ENTITY/RELATION/METRIC/FUNCTION/CONCEPT';
COMMENT ON COLUMN public.ecos_glossary_term.aliases        IS '同义词/别名列表';
COMMENT ON COLUMN public.ecos_glossary_term.object_type_id IS '引用的本体实体主键(ecos_ontology_entity.id)';
COMMENT ON COLUMN public.ecos_glossary_term.parent_term_id IS '上位词条 id(自引用)';
COMMENT ON COLUMN public.ecos_glossary_term.version        IS '词条定义版本号';
COMMENT ON COLUMN public.ecos_glossary_term.examples       IS '示例值列表';
COMMENT ON COLUMN public.ecos_glossary_term.tags           IS '方法论/场景标签';

CREATE INDEX IF NOT EXISTS idx_glossary_term_type       ON public.ecos_glossary_term(term_type);
CREATE INDEX IF NOT EXISTS idx_glossary_term_object_type ON public.ecos_glossary_term(object_type_id);
CREATE INDEX IF NOT EXISTS idx_glossary_term_parent      ON public.ecos_glossary_term(parent_term_id);

-- ════════════════════════════════════════════════════════════
-- ② ecos_glossary_term_relation 词条关系边表
-- ════════════════════════════════════════════════════════════
-- relation_type 枚举（6 类基础关系）：
--   ISA        上下位   —— 「数据血缘」ISA「元数据」
--   SYNONYM    同义     —— 「ETL」SYNONYM「数据集成转换」
--   PART_OF    组成     —— 「ETL 任务」PART_OF「数据管道」
--   SEE_ALSO   参见     —— 弱关联
--   CAUSAL     因果     —— 「数据质量」CAUSAL「决策可信度」
--   RELATED    相关     —— 兜底关联
-- ============================================================

CREATE TABLE IF NOT EXISTS public.ecos_glossary_term_relation (
    id            BIGSERIAL PRIMARY KEY,
    from_term_id  BIGINT       NOT NULL,
    to_term_id    BIGINT       NOT NULL,
    relation_type VARCHAR(32)  NOT NULL,
    weight        INTEGER      DEFAULT 100,
    description   VARCHAR(512) DEFAULT '',
    created_by    VARCHAR(128) DEFAULT '',
    created_at    TIMESTAMP    DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_glossary_relation_from'
    ) THEN
        ALTER TABLE public.ecos_glossary_term_relation
            ADD CONSTRAINT fk_glossary_relation_from
            FOREIGN KEY (from_term_id) REFERENCES public.ecos_glossary_term(id)
            ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_glossary_relation_to'
    ) THEN
        ALTER TABLE public.ecos_glossary_term_relation
            ADD CONSTRAINT fk_glossary_relation_to
            FOREIGN KEY (to_term_id) REFERENCES public.ecos_glossary_term(id)
            ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uniq_glossary_relation_edge'
    ) THEN
        ALTER TABLE public.ecos_glossary_term_relation
            ADD CONSTRAINT uniq_glossary_relation_edge
            UNIQUE (from_term_id, to_term_id, relation_type);
    END IF;
END $$;

COMMENT ON TABLE  public.ecos_glossary_term_relation               IS '词条关系边 — Wiki 词条间语义关系';
COMMENT ON COLUMN public.ecos_glossary_term_relation.from_term_id  IS '关系起点词条 id';
COMMENT ON COLUMN public.ecos_glossary_term_relation.to_term_id    IS '关系终点词条 id';
COMMENT ON COLUMN public.ecos_glossary_term_relation.relation_type IS '边类型: ISA/SYNONYM/PART_OF/SEE_ALSO/CAUSAL/RELATED';
COMMENT ON COLUMN public.ecos_glossary_term_relation.weight        IS '关系权重(默认 100)';

CREATE INDEX IF NOT EXISTS idx_glossary_relation_from ON public.ecos_glossary_term_relation(from_term_id);
CREATE INDEX IF NOT EXISTS idx_glossary_relation_to   ON public.ecos_glossary_term_relation(to_term_id);
CREATE INDEX IF NOT EXISTS idx_glossary_relation_type ON public.ecos_glossary_term_relation(relation_type);

-- ════════════════════════════════════════════════════════════
-- ③ 字典补充（前后端同源，替换前端硬编码领域枚举）
-- ════════════════════════════════════════════════════════════

-- ── glossary_domain: 词条领域 ──────────────────────────────
INSERT INTO public.sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-glossary-domain-data',      'glossary_domain', 'DATA_MGMT',    '数据管理',     'Data Management',        1),
('dict-glossary-domain-ai',        'glossary_domain', 'AI_TECH',      'AI技术',       'AI Technology',          2),
('dict-glossary-domain-business',  'glossary_domain', 'BUSINESS',     '业务词条',     'Business Term',          3),
('dict-glossary-domain-arch',      'glossary_domain', 'TECH_ARCH',    '技术架构',     'Technical Architecture', 4),
('dict-glossary-domain-security',  'glossary_domain', 'SECURITY',     '安全合规',     'Security & Compliance',  5),
('dict-glossary-domain-operation', 'glossary_domain', 'OPERATION',    '运营管理',     'Operations Management',  6),
('dict-glossary-domain-highway',   'glossary_domain', 'HIGHWAY_OPS',  '高速公路运营', 'Highway Operations',     7),
('dict-glossary-domain-struct',    'glossary_domain', 'STRUCT_ENG',   '结构工程',     'Structural Engineering', 8),
('dict-glossary-domain-its',       'glossary_domain', 'ITS',          '智能交通',     'Intelligent Transport',  9),
('dict-glossary-domain-gov',       'glossary_domain', 'DATA_GOV',     '数据治理',     'Data Governance',       10),
('dict-glossary-domain-safety',    'glossary_domain', 'SAFETY_MGMT',  '安全管理',     'Safety Management',     11),
('dict-glossary-domain-digital',   'glossary_domain', 'DIGITAL_TX',   '数字化转型',   'Digital Transformation',12),
('dict-glossary-domain-eval',      'glossary_domain', 'OPS_EVAL',     '运营评价',     'Operations Evaluation', 13),
('dict-glossary-domain-other',     'glossary_domain', 'OTHER',        '其他',         'Other',                 14)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ── glossary_term_type: 词条分类 ───────────────────────────
INSERT INTO public.sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-glossary-termtype-entity',   'glossary_term_type', 'ENTITY',   '实体词条', 'Entity Term',   1),
('dict-glossary-termtype-relation', 'glossary_term_type', 'RELATION', '关系词条', 'Relation Term', 2),
('dict-glossary-termtype-metric',   'glossary_term_type', 'METRIC',   '指标词条', 'Metric Term',   3),
('dict-glossary-termtype-function', 'glossary_term_type', 'FUNCTION', '函数词条', 'Function Term', 4),
('dict-glossary-termtype-concept',  'glossary_term_type', 'CONCEPT',  '概念词条', 'Concept Term',  5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ── glossary_relation_type: 词条关系类型 ───────────────────
INSERT INTO public.sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-glossary-reltype-isa',     'glossary_relation_type', 'ISA',      '上下位', 'Is-A',      1),
('dict-glossary-reltype-synonym', 'glossary_relation_type', 'SYNONYM',  '同义',   'Synonym',   2),
('dict-glossary-reltype-partof',  'glossary_relation_type', 'PART_OF',  '组成',   'Part-Of',   3),
('dict-glossary-reltype-seealso', 'glossary_relation_type', 'SEE_ALSO', '参见',   'See Also',  4),
('dict-glossary-reltype-causal',  'glossary_relation_type', 'CAUSAL',   '因果',   'Causal',    5),
('dict-glossary-reltype-related', 'glossary_relation_type', 'RELATED',  '相关',   'Related',   6)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ── glossary_status: 补 DEPRECATED（后端状态机支持但字典缺失，
--    导致前端废弃态标签回落到「草稿」）────────────────────
INSERT INTO public.sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order) VALUES
('dict-glossary-status-deprecated', 'glossary_status', 'DEPRECATED', '已废弃', 'Deprecated', 5)
ON CONFLICT (dict_type, dict_code) DO NOTHING;

-- ════════════════════════════════════════════════════════════
-- ④ 存量 domain 中文标签 → 字典 code 归一
--    仅归一已登记的领域标签；未登记的保持原值（不丢数据）
-- ════════════════════════════════════════════════════════════
UPDATE public.ecos_glossary_term SET domain = 'DATA_MGMT'   WHERE domain IN ('数据管理');
UPDATE public.ecos_glossary_term SET domain = 'AI_TECH'     WHERE domain IN ('AI技术');
UPDATE public.ecos_glossary_term SET domain = 'BUSINESS'    WHERE domain IN ('业务术语', '业务词条');
UPDATE public.ecos_glossary_term SET domain = 'TECH_ARCH'   WHERE domain IN ('技术架构');
UPDATE public.ecos_glossary_term SET domain = 'SECURITY'    WHERE domain IN ('安全合规');
UPDATE public.ecos_glossary_term SET domain = 'OPERATION'   WHERE domain IN ('运营管理');
UPDATE public.ecos_glossary_term SET domain = 'HIGHWAY_OPS' WHERE domain IN ('高速公路运营');
UPDATE public.ecos_glossary_term SET domain = 'STRUCT_ENG'  WHERE domain IN ('结构工程');
UPDATE public.ecos_glossary_term SET domain = 'ITS'         WHERE domain IN ('智能交通');
UPDATE public.ecos_glossary_term SET domain = 'DATA_GOV'    WHERE domain IN ('数据治理');
UPDATE public.ecos_glossary_term SET domain = 'SAFETY_MGMT' WHERE domain IN ('安全管理');
UPDATE public.ecos_glossary_term SET domain = 'DIGITAL_TX'  WHERE domain IN ('数字化转型');
UPDATE public.ecos_glossary_term SET domain = 'OPS_EVAL'    WHERE domain IN ('运营评价');
UPDATE public.ecos_glossary_term SET domain = 'OTHER'       WHERE domain IN ('其他');

-- 存量词条补齐 term_type 默认值（避免 NULL 参与前端过滤）
UPDATE public.ecos_glossary_term SET term_type = 'CONCEPT' WHERE term_type IS NULL;