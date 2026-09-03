# cognitive-engine-boot — 认知引擎·启动器

> 子模块: cognitive-engine/boot | 端口: 共享父模块 18089 | 仅开发调试用
> 上层: 见 ../AGENTS.md（cognitive-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `CognitiveEngine2Application.java`（唯一 file，包名 `com.chinacreator.gzcm.engine.cognitive2.boot`）：

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.cognitive2"
})
public class CognitiveEngine2Application {
    public static void main(String[] args) {
        SpringApplication.run(CognitiveEngine2Application.class, args);
    }
}
```

> ⚠️ 注意：
> 1. 类名是 `CognitiveEngine2Application`（不是 `CognitiveEngineApplication`）— 历史命名保留，**不要重命名**。
> 2. 包名是 `cognitive2`（不是 `cognitive`）— 顶层模块名仍叫 `cognitive-engine`，但内部 package 用 `cognitive2`，**不要改 package**。
> 3. 该 boot **未** `exclude JPA/Hibernate`、**未** `@MapperScan`、**未** `@EnableScheduling`：与另 5 个 engine boot 不一致。
>    - 新增 `@Mapper` 接口需手动加 `@MapperScan("com.chinacreator.gzcm.engine.cognitive2.**.dao")` 或启动 `Class` Bean。
>    - 本项目已禁用 JPA（统一 MyBatis），建议后续与父模块同步抽 `CognitiveBoolConfig` 加 `exclude` 与 `@MapperScan`，**但本次不做大动作**。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `cognitive2/` 一个 package。
- → 通过 gateway 聚合加载时（生产模式），`GatewayApplication.excludeFilters` 会排除本类，**避免多 `SpringBootApplication`** 冲突。
- ← 被调用方: 开发环境本地独立 JVM。生产走 gateway。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录 8 个 Controller）。
- 新增端点流程：impl 加 Controller → `gateway/VersionPrefixRewriteFilter` 映射 → `sysman/SecurityConfig` permitAll 加 `/api/v1/cognitive/**` + `/api/cognitive/**` → `ClearanceInterceptor` 加双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）。
- 不重命名类、不重命名 package（`cognitive2` 是稳定 API 入口，重命名会编译失败 + Bean 冲突，违反 PMO 铁律 5.1 #1）。
- 不硬编码 token / BOD / metadata（凭据走 `application-*.yml`，不在代码字面量）。
- 不在此 boot 内 `new DataSource` / `new JdbcTemplate` / `new Neo4jDriver`（Driver 收敛 `runtime-access`）。
- 不直接 `new ScheduledExecutorService` 自行调度（委托 `runtime-task`，架构铁律 2.5）。
- 不启用 Flyway（`spring.flyway.enabled: false`，schema 变更走 ADR）。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
