# kb-engine-boot — 知识库引擎·启动器

> 子模块: kb-engine/boot | 端口: 共享父模块 18086 | 仅开发调试用
> 上层: 见 ../AGENTS.md（kb-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `KbEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.kb",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.sysman.config"
})
@MapperScan("com.chinacreator.gzcm.engine.kb.repository")
public class KbEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(KbEngineApplication.class, args);
    }
}
```

> 注意：本模块是 6 个 engine 中**唯一标了 `@MapperScan`** 的（包：`com.chinacreator.gzcm.engine.kb.repository`）。
> **不要**把 `@MapperScan` 范围扩散到 `com.chinacreator.gzcm.engine.**`，避免和 gateway 加载 `*-impl` 的 `*Impl` Bean 冲突（架构铁律 1.3 "多 Bean 冲突"踩坑点）。
> 新增 `@Mapper` 接口必须落在 `repository` 包子内，**不要** rename。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `kb/` + `common/` + `sysman/config` 三个 package（与 Gateway 对齐）。
- → 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（MyBatis 项目禁 JPA）。
- → `@MapperScan("...engine.kb.repository")` 自动将 `@Mapper` 注册为员工（注意：不在全局 `@MapperScan`，避免与 gateway 的扫描重载冲突）。
- → 不启用 Flyway（`spring.flyway.enabled: false`）。
- ← 被调用方: 开发环境本地独立 JVM。生产 gateway 启动时通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录 13 个 Controller）。
- 新增端点流程：impl 加 Controller → `gateway/VersionPrefixRewriteFilter` 映射 → `sysman/SecurityConfig` permitAll 加 `/api/v1/kb/**` 与 `/api/kb/**` → `ClearanceInterceptor` 加双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）。
- 不删扩容 `@MapperScan("com.chinacreator.gzcm.engine.kb.repository")` 范围（不扩，避免与 gateway Bean 冲突）。
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（违反架构铁律 4）。
- 不硬编码 token / BOD / metadata（Neo4j / MinIO 凭据走 `application-*.yml` + `runtime-access`，不在代码字面量）。
- 不在此 boot 内 `new Driver` / `new JdbcTemplate` / `new Neo4jDriver`（Driver 收敛 `runtime-access`）。
- 不启用 Flyway（`spring.flyway.enabled: false`）。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
