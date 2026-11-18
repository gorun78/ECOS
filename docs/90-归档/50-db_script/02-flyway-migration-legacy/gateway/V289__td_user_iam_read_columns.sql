-- V289 (企业经营管控演示数据集 二期 W2 / IAM 读面对齐): ecos_control.td_user 追加 3 列
-- 追溯: 规划册《企业经营管控演示数据集二期-六域联动规划-2026-10-16》§三 域0「人员」面 + §五 号段纪律
-- 代码权威 = User-sql.xml:45-55 `queryUsers` 显式列清单：
--   SELECT user_id, username, password, email, mobile_tel1 AS phone, status, locked, lock_time,
--          last_login_time, login_ip, login_count, real_name, created_time, ... FROM TD_USER
--   ⇒ login_ip / login_count / real_name 三列由**已上线读路径**按名引用，实库 information_schema 实测不存在
--     （2026-10-16 实测：td_user 17 列 = user_id, username, password, email, mobile_tel1, status, locked,
--      lock_time, last_login_time, mfa_secret, mfa_type, mfa_enabled, tenant_id, created_time, created_by,
--      updated_time, updated_by）⇒ /api/v1/system/users 必报 bad SQL grammar，属"数据到位也查不出来"
--   的读面缺列缺陷，不是 seed 能绕过的问题（SELECT * 系 findById/findByUsername 不受影响）。
-- 号段: 单源目录实扫最大号 V287 ⇒ 本枚取 **V289**。V288 由《数据库访问规范》v1.4 §十 订正注
--   D-#144A-3 预留给 #144-c（V281/V285 类型红线修正脚本），本批不占、不改该规范文件。
-- 批准: 用户裁决 G2=全授权（增量迁移实跑已获授权，见 AGENTS.md 其他约束 #5）；纯增列、零 DROP、零改名
-- 状态: 待实跑（dry-run 已在规划册 §五 记录；实跑证据回写执行记录）
-- 纪律: IR02(禁 Flyway，手动 psql) / IR03(只加不删) / MC01(不动主键形态) / MC02(新增列类型仅 VARCHAR/INTEGER，
--       无 JSONB/BOOLEAN/BIGSERIAL) / DR05(不新增布尔列) / ST07(控制域表留在 ecos_control)
-- 存量注记（只登记不返工）：td_user 审计列是 created_time/updated_time 形态（IAM 侧既有约定），
--   与 DR06 的 create_time/update_time 不同轴 ⇒ 本脚本不改名（改名会断 User-sql.xml 与 sysman 写路径），
--   该形态属规范 §十「存量不返工」面。
-- 上线: psql -U postgres -d sys_man -f V289__td_user_iam_read_columns.sql

ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS real_name   VARCHAR(64);
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS login_ip    VARCHAR(64);
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS login_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ecos_control.td_user.real_name   IS '人员姓名（User-sql.xml queryUsers 按名引用；演示数据 seed 回填，非凭据面）';
COMMENT ON COLUMN ecos_control.td_user.login_ip    IS '最近登录 IP（运行结果面，seed 恒空，禁伪造演示值）';
COMMENT ON COLUMN ecos_control.td_user.login_count IS '登录次数（运行结果面，seed 恒 0，禁伪造演示值）';
