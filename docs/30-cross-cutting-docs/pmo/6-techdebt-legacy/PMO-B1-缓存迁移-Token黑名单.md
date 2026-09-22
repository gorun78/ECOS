# PMO指令: B1 Token 黑名单缓存迁移（Caffeine + Redis 分版本）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①standard 版不引 Redis（PG-only）②不改 API 路径签名、不改 Controller 行为 ③禁止跨 Task 预创建文件

## 零、现状摸底（已核实，无需重查）

`sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/TokenServiceImpl.java`

```java
// 第 37 行
private Map<String, Long> blacklist = new ConcurrentHashMap<>();
// addToBlacklist(token, expirationTime) 第 145 行 → blacklist.put(token, expiration)
// isBlacklisted(token) 第 155 行 → get + 手动 remove 过期项
```

问题：①无 TTL 自动过期，靠查询时手动 remove ②纯内存，重启后黑名单丢失（被拉黑 token 复活 = 安全漏洞）③无上限，恶意 token 可撑爆内存。

## 一、目标状态

Token 黑名单从 `ConcurrentHashMap` 迁移到 **Caffeine 本地缓存（三版本通用）+ 分版本持久化**：

| 版本 | 持久化 | 说明 |
|------|--------|------|
| standard | DB 持久化 | PG-only，黑名单落 `sys_token_blacklist` 表，启动加载未过期项 |
| enterprise/ultimate | Redis TTL | Caffeine 本地一级 + Redis 分布式二级，多实例共享 |

## 二、分阶段执行计划

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1 | `sysman/sysman-impl/pom.xml` | 加 `com.github.ben-manes.caffeine:caffeine` 依赖（三版本通用，参照 data-engine-impl pom 第 57 行） |
| P1-2 | 主 `pom.xml` 的 `enterprise` / `ultimate` profile | 各加 `<dependencies>` 块引入 `spring-boot-starter-data-redis`（**standard profile 不加**，依赖在 dependencyManagement 第 351 行已声明版本） |
| P1-3 | 新建 `BlacklistStore` 接口 + `DbBlacklistStore` + `RedisBlacklistStore` | 接口三方法 `save/load/remove`；DB 实现建 `sys_token_blacklist` 表（token/expire_at）；Redis 实现用 `RedisTemplate` + TTL |
| P1-4 | `TokenServiceImpl.java` | `blacklist` 字段改 Caffeine `Cache<String, Long>`（`expireAfterWrite` 按 token 过期时间）；注入 `@Autowired(required=false) BlacklistStore`；启动时从 store `load` 未过期项回填 Caffeine；`addToBlacklist`/`isBlacklisted` 同步写 store |
| P1-5 | `RedisBlacklistStore` 的 `@ConditionalOnClass` | 加 `@ConditionalOnClass(name="org.springframework.data.redis.core.RedisTemplate")`，standard 无 redis 依赖时 Bean 不创建；DB 实现反之 |

**实现顺序**：P1-1 → P1-2 → P1-3 → P1-4 → P1-5（P1-4 依赖 P1-3 的接口）。

## 三、禁止清单

- ❌ standard profile 引入任何 redis 依赖（破坏 PG-only 版本架构）
- ❌ 新建 `ScheduledExecutorService` / `@Scheduled` 清理过期项（Caffeine `expireAfterWrite` 自动过期，禁止自建调度）
- ❌ 修改 `generateAccessToken` / `generateRefreshToken` / `validateToken` 的行为
- ❌ 改黑名单相关方法的签名（`addToBlacklist(String, long)` / `isBlacklisted(String)` 保持不变）
- ❌ 直接改 `PermissionCacheService` / `DecisionCacheService` / `DictService` / `SysConfigService`（那是 B2 指令的范围）

## 四、风险与回滚

- **DB 建表风险**：`sys_token_blacklist` 表 DDL 放在 sysman 的 migration 脚本，幂等（`CREATE TABLE IF NOT EXISTS`）。
- **Redis 未连风险**：enterprise 启动时若 Redis 不可达，`RedisBlacklistStore` 应降级为仅 Caffeine（try-catch 吞连接异常，不让 Gateway 起不来）。
- **回滚**：每 Task 单独 commit，`git revert` 即可；本次改动集中在 sysman-impl + 主 pom，不影响引擎层。

## 五、验证门禁

```bash
# V1: standard 全量编译（确认无 redis 依赖也能编译）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'

# V2: enterprise 全量编译（确认 redis 依赖 + RedisBlacklistStore 编译通过）
# 同上，-Penterprise

# V3: 残留旧名搜索
grep -rn "ConcurrentHashMap" sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/TokenServiceImpl.java
# 期望: 0 匹配

# V4: 黑名单逻辑 grep 验证
grep -n "Caffeine\|BlacklistStore\|expireAfterWrite" sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/service/impl/TokenServiceImpl.java
# 期望: 命中 Caffeine + BlacklistStore
```

## 六、工时估算

P1-1（0.5h）+ P1-2（0.5h）+ P1-3（2h）+ P1-4（2h）+ P1-5（0.5h）≈ **5.5h**
