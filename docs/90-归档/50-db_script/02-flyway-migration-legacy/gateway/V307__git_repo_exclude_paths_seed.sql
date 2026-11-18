-- V307 (#147AE 补充设计册 R7~R11 §2.9.5③ 数据面：注册表排除清单铺设)
--
-- 为什么本脚本必须存在（#147AE 取证，非叙述）：
--   V304 只给 `ecos_control.ecos_git_repo` 加了 `include_paths` / `exclude_paths` 两枚可空列，
--   **存量 6 行全部为空**（dev PG 实测：`SELECT repository_id, include_paths, exclude_paths`
--   六行皆空 ⇒ 「清单」只有载体没有数据）。空清单的语义是「全量纳入、零排除」，
--   于是 §2.9.5③「密码/令牌/密钥/会话/个人敏感信息不得入普通 Git」依旧**无代码载体**——
--   一旦某个归档腿写入 `secrets/…` 一类路径，`GitRepository.allows()` 会照单放行。
--
-- 只播 `exclude_paths`、**不播 `include_paths`**（裁决依据）：
--   * 纳入清单非空 ⇒ 「未命中即拒」（`allows()` 实测语义），而 6 个仓库的归档路径形状各不相同
--     （rule/认知模型/agent/场景/本体/管道），凭本轮取证无法为每个仓写准前缀；
--     写错会让归档腿**直接 FAILED**（DesignAssetArchiver 命中未纳入即拒），属"以安全名义打断业务"。
--   * 排除清单是纯收紧面：前缀失配最多是"没排到"，不会误伤既有路径。故本轮先落排除面，
--     纳入面待各域给出确定目录形状后**按仓单独立项**（登记为待办，不臆造）。
--
-- 排除项与语义（`GitRepository.allows` 为**前缀匹配**，非通配；排除优先于纳入）：
--   `.env` / `credential` / `secrets` / `private-key` / `session`
--   —— 覆盖环境变量文件、凭据与密钥目录、私钥目录、会话材料目录四类常见落点；
--   因是前缀匹配，`secrets-backup/` 这类派生目录同样被排除（保守方向，代价可接受）。
--   未列入 `*.pem`/`id_rsa` 者：前缀语义下通配不生效，写成字面量会与文件名混淆，故不收。
--
-- 幂等与不覆盖：`UPDATE ... WHERE exclude_paths IS NULL` —— 仅在运维**尚未**填过时播种；
--   重跑零变更；运维已自定义清单的仓**不被覆盖**（禁把运维配置打回模板）。
-- 纪律: IR02(手动 psql -f，Flyway 永久禁用) / IR03(只加不删、不改列) / 不改既有 15 列 / 不动 include_paths。

-- ── 排除清单（6 枚仓库，逐行幂等）──
UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'agent-collab' AND exclude_paths IS NULL;

UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'cognitive-model' AND exclude_paths IS NULL;

UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'dw-pipeline' AND exclude_paths IS NULL;

UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'kb-asset-tree' AND exclude_paths IS NULL;

UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'ontology-model' AND exclude_paths IS NULL;

UPDATE ecos_control.ecos_git_repo SET exclude_paths = '.env,credential,secrets,private-key,session'
 WHERE repository_id = 'scenario-def' AND exclude_paths IS NULL;
