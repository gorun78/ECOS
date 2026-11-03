-- V308 (#147G8 补充设计册 R7~R11 §2.9.9 G8 组织权限矩阵的载体列)
--
-- 为什么本脚本必须存在（#147G8 取证）：
--   §2.9.9 G8「个人账号托管无组织权限矩阵」的现状是：`ecos_git_repo` 只有 create_by/update_by
--   （"谁建的"），**没有任何机构归属列**，也没有任何机构级授权判定；`organization_member`/`ecos_oidc`
--   全仓零命中。于是"某个仓库属于哪个机构、谁能对它下手"只能靠流程约定。
--
-- 本脚本只补**载体**，不补数据：
--   `owner_org_id` 可空 —— NULL = **全局仓库（不按机构限制）**，这是现状 6 行的语义，
--   因此播种为空即可保持既有行为（禁把"未配置归属"读成"禁止访问"）。
--   一旦运维为某个仓库填了 owner_org_id，gateway 侧 `GitRepoOrgGuard` 即按
--   「服务端解析的机构 == 仓库属主机构」放行，否则 403（超管旁路）。
--
-- 纪律: 只加列不改既有列、可空、无默认值（MC01 主键口径不涉）；禁 DROP；
--       IR02 手动 psql -f，Flyway 永久禁用。机构标识与 `PrimaryOrganizationRule` 同词表（无新枚举）。

ALTER TABLE ecos_control.ecos_git_repo
    ADD COLUMN IF NOT EXISTS owner_org_id VARCHAR(64);

COMMENT ON COLUMN ecos_control.ecos_git_repo.owner_org_id IS
    '§2.9.9 G8 仓库属主机构（空=全局仓库，不按机构限制）；非空时仅该机构（或超管）可操作，gateway GitRepoOrgGuard 强制';
