# ai-engine-boot — AI 引擎·启动器

> 子模块: ai-engine/boot | 端口: 共享父模块 18084 | 仅开发调试用
> 上层: 见 ../AGENTS.md（ai-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `AiEngineApplication.java`（唯一 file，包名 `com.chinacreator.gzcm.engine.ai.boot`）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.ai",
    "com.chinacreator.gzcm.cognitive",
    "com.chinacreator.gzcm.runtime"
})
public class AiEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiEngineApplication.class, args);
    }
}
```

> ⚠️ 注意：
> 1. `@ComponentScan` 含 `com.chinacreator.gzcm.cognitive`（**历史包**，当前**不存在该包**）— 该行是历史残留，扫不到任何类也不会报错（Spring 容忍扫空包），但**不要**删除（删了不影响，留着可给后续兼容；**不要**把它改成 `com.chinacreator.gzcm.engine.cognitive2`，避免未来 Spring Scan 重复 Bean 冲突）。
> 2. `@EnableScheduling`：当前本 impl 内有 Agent 周期任务（CronJob 调度），调度入口走 `runtime-task`；本 boot 保留 `@EnableScheduling` 是为兼容现有代码，**Phase 5 必须移除本 boot 自定义调度任务后改纯 runtime-task**。
> 3. 该 boot **未** `@MapperScan`：注入的 MyBatis Mapper 需符合 mybatis-spring 自动扫描（`classpath*:mapper/*.xml`）；新增 `@Mapper` 接口需被自动扫描到，否则在本目录新增 `AiBootConfig`（**不要**改 Application），标 `@MapperScan("com.chinacreator.gzcm.engine.ai.**.dao")`。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `engine.ai` + `cognitive`（历史空包）+ `runtime` 三个 package（与 Gateway 对齐）。
- → 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（MyBatis 项目禁 JPA）。
- → `@EnableScheduling`：兼容现有调度，未来迁 runtime-task 后移除（不要新加）。
- → 不启用 Flyway（`spring.flyway.enabled: false`）。
- ← 被调用方: 开发环境本地独立 JVM。生产 gateway 启动时通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录 27 个 Controller）。
- 新增端点流程：impl 加 Controller → `gateway/VersionPrefixRewriteFilter` 映射 → `sysman/SecurityConfig` permitAll 加 `/api/v1/agent-loop/**` 与 `/api/v1/ai-engine/**` 等 → `ClearanceInterceptor` 加双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）。
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（违反架构铁律 4）。
- 不修改 `@ComponentScan` 把 `com.chinacreator.gzcm.cognitive` 改成别的 package（历史空包，保留兼容；改包名会编译失败 + Bean 冲突，违反 PMO 5.1 #1）。
- 不硬编码 token / BOD / metadata（LLM 凭据/Agent Token 走 `llm-gateway` + `application-*.yml`，不在代码字面量）。
- 不在此 boot 内 `new DataSource` / `new JdbcTemplate` / `new Neo4jDriver`（Driver 收敛 `runtime-access`）。
- 不在此 boot 内 `new ScheduledExecutorService`（调度委托 `runtime-task`，架构铁律 2.5）。
- 不启用 Flyway（`spring.flyway.enabled: false`）。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
