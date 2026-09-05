# security-engine-boot — 安全引擎·启动器

> 子模块: security-engine/boot | 端口: 共享父模块 18081 | 仅开发调试用
> 上层: 见 ../AGENTS.md（security-engine 顶层）

## 本模块干什么
- **启动器（独立 Boot）**：生产不走，仅开发/调试用。生产统一走 `gateway/GatewayApplication.java` 的 `excludeFilters` 聚合加载。

## 主要 code
- `SecurityEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.security",
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime"
})
public class SecurityEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityEngineApplication.class, args);
    }
}
```

> 注意：当前 `@SpringBootApplication` 未标 `@MapperScan`，依赖 `commybatis 2.3` 的 mybatis-spring 自动配置扫描；
> 新增 `@MyMapper` 接口需确保编译后 class path 可被自动扫描（若不可用，在 `SecurityBootConfig` 加 `@MapperScan("com.chinacreator.gzcm.engine.security.**.dao")`）。
> **不要**在本类中自己加 `new DataSource` / `new HikariDataSource`，PG 配置走 `application.yml`。

## 调用链（只读 + 调谁）
- → `ComponentScan` 扫 `security/` + `sysman/` + `runtime/` 三个 package（与 Gateway 对齐，避免 Bean 缺失）。
- → 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（项目统一 MyBatis，禁 JPA 自动装配）。
- ← 被调用方：开发环境本地独立:jvm `java -jar security-engine-boot-*.jar`。生产 gateway 启动时通过 `excludeFilters` 排除本类同名副本。

## 端点 / 补丁
- 删 Phase：Phase 1 已 startup。Phase 2+ 该 boot 不增端点，端点池在 impl 子模块（见 impl 目录双 `@RequestMapping`）。
- 新增端点流程：impl 加 Controller → `gateway` 的 `VersionPrefixRewriteFilter` 映射 → `sysman/security/SecurityConfig` permitAll 加 `/api/v1/xxx/**` + `/api/xxx/**` → `ClearanceInterceptor` 加双路径豁免 → 编译 `mvn install -DskipTests` 启动 gateway 验证。

## 禁止
- 不在此模块新加任何业务代码（业务在 impl，接口在 api）。
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（违反架构铁律 4 / 数据层铁律 3.1）。
- 不硬编码 token / BOD / metadata（密钥走 `application-*.yml` 与 `KeyManagementService`）。
- 不在此 boot 内 `new Driver` / `new JdbcTemplate`（PG 连接池随 `runtime-access` 注入）。
- 不启用 Flyway（架构铁律：`spring.flyway.enabled: false`）。
- 生产发布**禁止**以 boot 启动，生产只走 gateway。
