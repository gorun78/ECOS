# PMO指令: B2/B3 本地缓存迁移（4 类 → Caffeine）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①纯 Caffeine 本地缓存，不涉及 Redis（分版本归 B1）②不改 API 路径签名、不改 Controller 行为 ③禁止跨 Task 预创建文件

## 零、现状摸底（已核实，无需重查）

| 类 | 位置 | 现状 |
|----|------|------|
| InMemoryPermissionCacheService | `sysman/sysman-impl/.../sysman/iam/cache/` | 两个 `ConcurrentHashMap`（cache + decisionCache），已手写 TTL（expireAt），**无容量上限** |
| InMemoryDecisionCacheService | `engine/security-engine/security-engine-impl/.../engine/security/policy/cache/impl/` | `ConcurrentHashMap<String, CacheEntry>`，已手写 TTL，无容量上限 |
| DictService | `sysman/sysman-impl/.../sysman/dict/service/impl/` | `ConcurrentHashMap<String, List<SysDict>>`，无 TTL，有 `refreshCache()` |
| SysConfigService | `sysman/sysman-impl/.../sysman/config/service/impl/` | `ConcurrentHashMap<String, String>`，无 TTL，有 `refreshCache()` |

参考实现（data-engine 已落地 Caffeine）：
```java
// DataSourceRegistryService.java 第 24 行
private final Cache<String, List<DataSourceEntity>> dsListCache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(1000).build();
```

## 一、目标状态

4 个类从 `ConcurrentHashMap`（手写 TTL / 无 TTL / 无上限）迁移到 **Caffeine**（自动 TTL + 容量上限 + 启动预热），消除内存泄漏与冷启动。

## 二、分阶段执行计划

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1 | `sysman/sysman-impl/pom.xml` | 若 B1 指令已加 caffeine 依赖则跳过；否则加 caffeine（**须与 B1 指令协调，避免重复声明**） |
| P1-2 | `security-engine/security-engine-impl/pom.xml` | 加 caffeine 依赖（该模块现无 caffeine） |
| P1-3 | `InMemoryPermissionCacheService.java` | 两个 ConcurrentHashMap → Caffeine（permission cache `maximumSize(5000)` + `expireAfterWrite(默认 TTL)`；decision cache 同理）；去掉手写 `expireAt` 判断 |
| P1-4 | `InMemoryDecisionCacheService.java` | `ConcurrentHashMap` → Caffeine（`maximumSize(10000)` + `expireAfterWrite`）；`shutdown()` 调 `cache.cleanUp()` |
| P1-5 | `DictService.java` | `cache` 字段 → Caffeine（`maximumSize(100)` + 字典常驻不设过期，仅 `refreshCache()` 显式 invalidate）；构造器保留 `refreshCache()` 预热 |
| P1-6 | `SysConfigService.java` | `cache` 字段 → Caffeine（`maximumSize(1000)` + 配置常驻不设过期，`refreshCache()` 显式 invalidate）；构造器保留预热 |

**实现顺序**：P1-1/P1-2（依赖）→ P1-3~P1-6（互不依赖，可并行）。

## 三、禁止清单

- ❌ 引入 Redis（分版本持久化是 B1 指令的范围）
- ❌ 新建 `ScheduledExecutorService` / `@Scheduled` 清理（Caffeine 自动过期）
- ❌ 改 4 个类对外方法签名（`getUserPermissions` / `get` / `getDictItems` / `getAll` 等保持不变）
- ❌ 改 `TokenServiceImpl`（那是 B1 指令的范围）
- ❌ 字典/配置缓存设置 `expireAfterWrite`（字典和配置是常驻数据，应靠 `refreshCache()` 主动失效，不靠时间过期）

## 四、风险与回滚

- **Caffeine 语义差异**：`ConcurrentHashMap` 的 `get` 不触发过期，Caffeine 的 `get` 会触发 lazy eviction——`refreshCache()` 里的 `invalidateAll()` 后重新加载即可，无需改调用方。
- **回滚**：每 Task 单独 commit，`git revert` 即可。

## 五、验证门禁

```bash
# V1: 全量编译（standard profile）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'

# V2: 残留旧名搜索（4 个类）
for f in InMemoryPermissionCacheService InMemoryDecisionCacheService; do
  grep -rn "ConcurrentHashMap" $(find . -name "$f.java" -not -path '*/target/*') 
done
# 期望: 0 匹配（Caffeine 缓存字段不再用 ConcurrentHashMap）

# V3: Caffeine 落地 grep
grep -rn "Caffeine.newBuilder\|maximumSize" \
  sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/dict/service/impl/DictService.java \
  sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/config/service/impl/SysConfigService.java \
  sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/iam/cache/InMemoryPermissionCacheService.java \
  engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/policy/cache/impl/InMemoryDecisionCacheService.java
# 期望: 4 个文件各命中
```

## 六、工时估算

P1-1/P1-2（0.5h）+ P1-3~P1-6（各 0.5-1h）≈ **4h**
