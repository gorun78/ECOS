-- V306 (#147AA 补充设计册 R7~R11 / §2.9.8⑤ 配置面补齐：runtime 缓存配置四行落 sys_config config_group=cache)
--
-- 为什么本脚本必须存在（#147AA 取证，非叙述）：
--   ① #147J 交付 `SysConfigCacheSettingsSource`（缓存配置唯一读取面，键 ecos.cache.*）时
--      **从未播种任何 config_group=cache 行**。dev PG 实查（2026-11-02）：
--        SELECT config_group, count(*) FROM sys_config GROUP BY 1;
--      结果 16 组、**无 cache 组** ⇒ 读取面即使被扫到也只能全取缺省 ⇒ 缓存恒 DISABLED。
--   ② 同批另查出**承流断点**：该类原落 `com.chinacreator.gzcm.services.sysman.cache`，
--      不在 gateway `@ComponentScan` 的 basePackages 内 ⇒ Bean 从不生成、`CacheSettingsSource`
--      SPI 恒缺席。已移至 `com.chinacreator.gzcm.sysman.cache` 并配防回退门
--      `gateway/.../arch/CacheSettingsSourceScanGateTest`。
--   两件事缺一，`RuntimeCacheManager` 都等于「已建但永不生效」——本脚本补的是数据面那一半。
--
-- 值口径（保守基线，禁写成「缓存已启用」）：
--   * `ecos.cache.enabled = 'false'`：**默认关闭**是 §2.9.8⑤ 与 `CacheSettings.DISABLED` 的既定口径；
--     播种四行只为让读取面有权威值可读、让运维有单点可改。翻成 'true' 由运维决定，
--     且生效**不需要重启**——读取面改为每次穿透 `SysConfigService` 进程内配置缓存
--     （#147AA 移除了原先无人刷新的 volatile 快照，「关掉缓存」这类安全动作必须即时生效）。
--   * `default_ttl_seconds = 120`：写入未显式给 TTL 时的缺省值（仍受下一行钳制）。
--   * `max_stale_seconds = 60`：**陈旧上限**，>0 时是所有写入的 TTL 天花板（显式 TTL 也压不过它）；
--     权限类对象在该值 ≤0 时一律拒缓存（`PermissionCacheServiceImpl` fail-closed，§2.9.8⑥）。
--     60s 取「撤权最迟可见时间」而非性能口径；撤权本身走写后即时失效，上限只是竞态兜底。
--   * `ecos.cache.redis_enabled = 'false'`：Redis 未接入运行链路（§2.9.8 现状基线），
--     当前存储为进程内 TTL；接 Redis 须另补底座与容器裁决（基线禁新增容器）。
--
-- 有意**不播种**的一枚：`ecos.cache.env`。环境标识是**部署事实**而非业务配置，
--   同一份迁移会在 dev/test/prod 各跑一次，若播种则三者共用一个环境段（恰恰是它要防的串环境）。
--   取值链见 `SysConfigCacheSettingsSource`：`-D ecos.cache.env` → 环境变量 `ECOS_CACHE_ENV` →
--   sys_config 的 `ecos.cache.env`（本脚本不写，留给极端隔离需求）→ 缺省段 `default`。
--
-- 归属纪律：`sys_config` 属 sysman 域（控制域主 schema），runtime 底座不直读配置表，
--   一律经 `SysConfigService` 门面（本组四键的唯一读缝 = `SysConfigCacheSettingsSource`）。
-- 幂等形态：逐行 `INSERT ... SELECT ... WHERE NOT EXISTS`——重跑零重复，且**不覆盖**运维已改的值。
--   刻意**不用 `ON CONFLICT`**：gateway `Mc03TimeFormTest` 的「V200+ 段 PG 专有语法待清退名录」
--   逐脚本计数，用一次就多一枚待清退债；本脚本用标准 SQL 表达同一语义，不增长该名录。
-- 纪律: IR02(手动 psql -f，Flyway 永久禁用) / IR03(只加不删) / MC01(主键 VARCHAR(36) 应用侧指定) /
--       禁 DROP、禁新建容器、禁改既有列。

-- ── 种子行（4 枚，逐行幂等）──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status)
SELECT 'cfg-cache-enabled', 'cache', 'ecos.cache.enabled', 'false', 'bool',
       '缓存·总开关', 'Cache Enabled',
       '受控加速层总开关（§2.9.8①）。false ⇒ get 恒空、put 恒 no-op，调用方一律回源（绝不把「没有缓存」当「没有数据/没有权限」）。默认关闭；翻 true 前须确认三项已达标：陈旧上限为正值、写路径已接失效、缓存命中不绕鉴权', 1, 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ecos.cache.enabled');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status)
SELECT 'cfg-cache-default-ttl', 'cache', 'ecos.cache.default_ttl_seconds', '120', 'int',
       '缓存·缺省 TTL（秒）', 'Cache Default TTL (sec)',
       '写入未显式给 TTL 时的缺省值；≤0 视为不缓存。最终 TTL 仍受 max_stale_seconds 钳制，故本值大于上限时以硬上限为准', 2, 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ecos.cache.default_ttl_seconds');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status)
SELECT 'cfg-cache-max-stale', 'cache', 'ecos.cache.max_stale_seconds', '60', 'int',
       '缓存·最大陈旧时间（秒）', 'Cache Max Stale (sec)',
       '所有写入的 TTL 天花板（显式 TTL 也压不过）。权限类对象（角色·权限·授权关系）在本值 ≤0 时一律拒缓存——「撤权最迟多久可见」无法证明就不启用缓存（§2.9.8⑥ fail-closed）。撤权本身由写路径提交后即时失效，本值只是 Cache-Aside 竞态的兜底窗口', 3, 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ecos.cache.max_stale_seconds');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status)
SELECT 'cfg-cache-redis-enabled', 'cache', 'ecos.cache.redis_enabled', 'false', 'bool',
       '缓存·Redis 通道', 'Cache Redis Enabled',
       'Redis 通道开关。当前基线为 false（Redis 未接入运行链路，存储为进程内 TTL）；接 Redis 须先补 runtime 底座远程实现并另行裁决容器，铁律「不新增 Docker container」在其前仍然有效。本开关为 true 但底座缺失时须显式降级，不得静默假装分布式', 4, 'active'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'ecos.cache.redis_enabled');
