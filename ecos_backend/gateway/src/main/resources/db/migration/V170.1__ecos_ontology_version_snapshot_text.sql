-- V170.1 (卷03 §E.3 / F03-15): ecos_ontology_version 快照列合规化 —— 新增 snapshot_json TEXT 承载快照 + git_ref VARCHAR(64)，旧 snapshot 列停写
-- 追溯: W83/C66（O-21：实测 public.ecos_ontology_version.snapshot 为 jsonb，违 MC02；id VARCHAR(50) 违 MC01；历史版本 DB/Git 双写）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-5=①+③ 关联项（版本×Git 归档：DB 只存在用版本，历史走 Git，后端规范 §十一）
-- 号段: 与卷02 同版本号冲突，按定版规则取 .1 子版本（V170 整号 = 卷02）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 处置策略（依任务定版口径 + IR03）：MC02 存量违规列**不原地改型、不 DROP**——
--   铁律 §3.1"设计资产历史版本表可 DROP"例外的前置 = 已备份 + 代码读写已切 Git 链路；本批代码未切，
--   例外**未启用**，故采用「新增合规列 snapshot_json + 幂等回填 + 旧列停写标记」双写过渡。
--   snapshot→text 的转换使用标准 SQL `CAST(... AS TEXT)`（**目标方言 PostgreSQL**：jsonb 的文本表示即
--   其 JSON 序列化，CAST 无损；MySQL/Oracle 档该 UPDATE 由人工按同语义改写，属 IR02 手动执行注意项）。
-- 【2026-09-30 DR04 收口】合规列定名由原稿 `snapshot_text` 改为 **`snapshot_json`**（DR04：JSON 语义列必加 `_json` 后缀）。
--   安全性实测：现网仅 `public.ecos_ontology_version.snapshot jsonb` 实存，`snapshot_text`/`snapshot_json` 均不存在
--   （information_schema 命中 0）；全仓 Java/XML/TS 对两名零引用 ⇒ 改名零代价。文件名 slug 保留 `_snapshot_text`
--   不改动（IR02 手动执行按文件名指定，避免破坏运维/文档引用），同法见 V178。卷03 §E.3 同步勘误。
-- 遗留偏差登记（不在本脚本修复，只加不改）：id VARCHAR(50)（违 MC01）与 created_at（违 DR06）保留，
--   新写由应用侧生成 36 位 UUID（50 长兼容容纳）并以 create_time/update_time 双写（V169.1 已补列，若缺本脚本 §1b 兜底）。

-- ── 1a. public 侧补合规新列（IF EXISTS/IF NOT EXISTS 双幂等；V120/V122 锚点环境列已存在则 no-op）──
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS snapshot_json TEXT;
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS git_ref       VARCHAR(64);
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS create_time   TIMESTAMP;
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS update_time   TIMESTAMP;
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS create_by     VARCHAR(100);
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS update_by     VARCHAR(100);
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS is_deleted    SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS version_no    VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE IF EXISTS public.ecos_ontology_version ADD COLUMN IF NOT EXISTS domain        VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── 1b. ecos_ontology 镜像侧同构补列（O-21 镜像 = V47 幽灵表，缺表仅 WARNING）──
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS snapshot_json TEXT;
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS git_ref       VARCHAR(64);
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS create_time   TIMESTAMP;
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS update_time   TIMESTAMP;
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS create_by     VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS update_by     VARCHAR(100);
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS is_deleted    SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS version_no    VARCHAR(20) NOT NULL DEFAULT '1';
ALTER TABLE IF EXISTS ecos_ontology.ecos_ontology_version ADD COLUMN IF NOT EXISTS domain        VARCHAR(50) NOT NULL DEFAULT 'default';

-- ── 2. 幂等回填 snapshot → snapshot_json（仅当表与 snapshot 列实存；重复执行无副作用）──
-- 回滚说明: snapshot_json 为纯新增列，回填行可用
--   `UPDATE public.ecos_ontology_version SET snapshot_json = NULL WHERE ...` 复位；旧列零改动，无数据损失。
DO $$
BEGIN
    IF to_regclass('public.ecos_ontology_version') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'ecos_ontology_version' AND column_name = 'snapshot') THEN
        EXECUTE $q$
            UPDATE public.ecos_ontology_version
               SET snapshot_json = CAST(snapshot AS TEXT)   -- MC03 合规写法：禁 :: 裸 cast；CAST 为标准 SQL（PG 目标方言，见头注）
             WHERE snapshot_json IS NULL
               AND snapshot IS NOT NULL
        $q$;
        RAISE NOTICE 'V170.1: public.ecos_ontology_version.snapshot_json 回填完成（幂等）';
    ELSE
        RAISE NOTICE 'V170.1: public.ecos_ontology_version 或其 snapshot 列不存在，跳过回填';
    END IF;
END $$;

-- ── 3. 旧 snapshot 列停写标记（COMMENT 登记，不 DROP 不改型；DROP 例外未启用，见头注）──
DO $$
BEGIN
    IF to_regclass('public.ecos_ontology_version') IS NOT NULL
       AND EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'ecos_ontology_version' AND column_name = 'snapshot') THEN
        EXECUTE $q$ COMMENT ON COLUMN public.ecos_ontology_version.snapshot IS
            'W83/C66 违例存量列（MC02 禁用的 JSON 二进制类型形态）：V170.1 起停写，新写一律 snapshot_json（TEXT）；列保留不删（IR03），历史语义降级为"在用版本"指针，行内容以 git_ref 指向的 Git 归档为准（后端规范 §十一）' $q$;
    END IF;
    IF to_regclass('public.ecos_ontology_version') IS NOT NULL THEN
        EXECUTE $q$ COMMENT ON COLUMN public.ecos_ontology_version.snapshot_json IS
            '在用版本快照 JSON 文本（MC02 合规形态；只做存取，禁 WHERE/JOIN/索引）' $q$;
        EXECUTE $q$ COMMENT ON COLUMN public.ecos_ontology_version.git_ref IS
            'Git 归档引用（F03-15：发布成功后写 ontology 仓库 ref；归档失败入 git_archive_pending 补偿队列，由 runtime-task 重试——队列表建表属卷03 其他批次，未在本脚本落）' $q$;
    END IF;
END $$;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR06/07/08] 补列齐 ✓  [MC01] 未新建表，存量 id 不改（登记遗留偏差）✓
-- [DR04] 新列 `snapshot_json`：JSON 语义 + `_json` 后缀 ✓（2026-09-30 收口，原稿 `snapshot_text` 违后缀约定）
-- [MC02] 新列 snapshot_json 为 TEXT 合规形态；旧违例列只停写不新增使用 ✓（注停写注释中提及旧类型字样，属缺陷登记说明，非 DDL 使用）
-- [MC03] 回填用 CAST(... AS TEXT) 而非 ::；无 partial index / CREATE POLICY / PARTITION BY；无新时间默认值改动 ✓
-- [ST07] 仅触 public + ecos_ontology ✓  [IR02] 手动 psql，未实跑 ✓
-- [IR03] 无 DROP / 无 ALTER COLUMN TYPE / 无 RENAME；铁律 §3.1 DROP 例外因"代码读写未切 Git 链路"前置未满足而**未启用** ✓
