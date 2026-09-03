# ontology-engine-boot — 本体引擎·启动器

> 子模块: ontology-engine/boot | 端口: 共享父模块 18083 | 仅开发调试用
> 上层: 见 ../AGENTS.md（ontology-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `OntologyEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.ontology",
    "com.chinacreator.gzcm.buszhi",
    "com.chinacreator.gzcm.runtime"
})
public class OntologyEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(OntologyEngineApplication.class, args);
    }
}
```

> 注意：当前 `@SpringBootApplication` 未标 `@MapperScan`，依赖 mybatis-spring 自动扫描（mapper XML: `classpath*:mapper/*.xml`）。
> 新增 `@Mapper` 接口需被自动扫描到；若扫不到，在本目录新增 `OntologyBootConfig` 配置类（**不要**改 Application），标 `@MapperScan("com.chinacreator.gzcm.engine.ontology.**.dao")`。
> `@ComponentScan` 含 `com.chinacreator.gzcm.buszhi`（历史包路径，保留兼容，不要删除）。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `ontology/` + `buszhi/`（历史包）+ `runtime/` 三个 package。
- → 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（MyBatis 项目禁 JPA）。
- → 不启用 Flyway（`spring.flyway.enabled: false`）。
- ← 被调用方: 开发环境本地独立 JVM。生产 gateway 启动时通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录 27 个 Controller）。
- 新增端点流程：impl 加 Controller → `gateway/VersionPrefixRewriteFilter` 映射 → `sysman/SecurityConfig` permitAll 加 `/api/v1/xxx/**` 与 `/api/xxx/**` → `ClearanceInterceptor` 加双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）。
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（违反架构铁律 4）。
- 不硬编码 token / BOD / metadata（KG 凭据走 `runtime-access` + `application-*.yml`，不在代码字面量）。
- 不在此 boot 内 `new Driver` / `new JdbcTemplate` / `new Neo4jDriver`（Driver 收敛 `runtime-access`）。
- 不修改 `ComponentScan` 删 `com.chinacreator.gzcm.buszhi`（历史兼容包，删掉会有 class 缺失）。
- 不启用 Flyway（`spring.flyway.enabled: false`）。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
