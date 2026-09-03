# data-engine-boot — 数据引擎·启动器

> 子模块: data-engine/boot | 端口: 共享父模块 18082 | 仅开发调试用
> 上层: 见 ../AGENTS.md（data-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `DataEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.data",
    "com.chinacreator.gzcm.runtime"
})
public class DataEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataEngineApplication.class, args);
    }
}
```

> 注意：当前 `@SpringBootApplication` 未标 `@MapperScan`，依赖 `autoconf` 的 mybatis-spring 自动扫描（mapper XML: `classpath*:mapper/*.xml`）。
> 新增 `@Mapper` 接口需被自动扫描到；若扫不到，在本目录新增 `DataBootConfig` 配置类（**不要**改 Application），标 `@MapperScan("com.chinacreator.gzcm.engine.data.**.dao")`。
> **不要**自己 `new DataSource`，PG 凭据走 `application.yml`。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `com.chinacreator.gzcm.engine.data` + `com.chinacreator.gzcm.runtime` 两个 package。
- → 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（MyBatis 项目禁 JPA）。
- → `@EnableScheduling` + `@EnableAsync`：管道任务调度 + 异步（注意：调度任务自 Phase 1 起已计划委托 `runtime-task`；本 boot 仍保留切面以兼容当前代码）。
- ← 被调用方: 开发环境本地独立 JVM。生产 gateway 启动时通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录各 Controller）。
- 新增端点流程：impl 加 Controller → `gateway/VersionPrefixRewriteFilter` 映射 → `sysman/SecurityConfig` permitAll 加 `/api/v1/xxx/**` 与 `/api/xxx/**` → `ClearanceInterceptor` 加双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）。
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（违反架构铁律 4 + 数据层 3.1）。
- 不硬编码 token / BOD / metadata（凭据走 `application-*.yml`、应用开关走 environment profile，不在代码字面量）。
- 不在此 boot 内 `new DataSource` / `new JdbcTemplate`（PG 连接池随 `runtime-access` 注入，本 boot 仅 monitor startup hook）。
- 不启用 Flyway（`spring.flyway.enabled: false`）。
- 调度任务：Phase 2+ 任务调度迁移到 `runtime-task` 后，本 boot 的 `@EnableScheduling` 必须降级移除；迁移前不删。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
