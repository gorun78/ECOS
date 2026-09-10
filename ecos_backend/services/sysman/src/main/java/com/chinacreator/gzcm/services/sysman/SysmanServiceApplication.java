package com.chinacreator.gzcm.services.sysman;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * ECOS Sysman 微服务 — 系统管理服务（护·横切）
 *
 * <p>P2-1 目标态：封装 security-engine（认证/授权/审计/脱敏/ABAC）+ 原顶层 sysman
 * （IAM/角色/字典/租户/管理面板 + GlobalExceptionHandler）。
 *
 * <p>三滤波器（铁律 §1.2）适配：
 * <ul>
 *   <li>① VersionPrefixRewriteFilter 仍属 gateway，sysman service 不重写已有路径</li>
 *   <li>② SecurityConfig (Spring Security 硬编码 permitAll) 在 sysman 包内，直接生效</li>
 *   <li>③ ClearanceInterceptor (准入等级四级防护) 在 sysman 包内，仍生效</li>
 *   <li>④ ADR-7 HeaderAuthInterceptor 待 Phase 3 完善（Phase 2 拆分期 enabled=false）</li>
 * </ul>
 *
 * <p>与 gateway 的拆分双跑策略：
 * <ul>
 *   <li>gateway 保留 sysman 组件扫描（老流量继续走 :8080）</li>
 *   <li>本 service 独立 :18081 承接安全裁决 + IAM 域路由</li>
 *   <li>切流 ECOS_ROUTE_sysman=monolith|service, 拆分期避免同时写同表</li>
 * </ul>
 *
 * <p>排除：
 * <ul>
 *   <li>SysManApplication (原 sysman-boot 启动器, 它有自己的 @ComponentScan 会冲突)</li>
 *   <li>gateway 所属的 GlobalExceptionHandler (本 service 复用 sysman.boot 版本)</li>
 * </ul>
 *
 * @author ecos-factory
 * @since PMO-49 P2-1
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Sysman service 自身 (HeaderAuthInterceptor / 服务级 config)
        "com.chinacreator.gzcm.services.sysman",
        // sysman 三环: boot (GlobalExceptionHandler) / impl (IAM/Config/Security) / api 兼容
        "com.chinacreator.gzcm.sysman",
        // security-engine (护·横切: 认证/授权/审计/脱敏/OPA ABAC/RLS/CLM)
        "com.chinacreator.gzcm.engine.security",
        // 横切底座 (runtime-core 通用 mybatis/mapper/Jackson)
        "com.chinacreator.gzcm.runtime"
}, excludeFilters = {
        // 原 sysman-boot 启动器 — 它有独立 Application + @ComponentScan, 与本 service 双启动冲突
        // 用正则 exclude 避免直接引用 (隔离 monorepo 依赖域, 不依赖 sysman-boot artifact)
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.sysman\\.boot\\..*"),
})
@MapperScan({
        // sysman dao 包 (iac/iam, config, security, abac, kvstore, touch) — 实际 DAO 用 `.dao` 子包,
        // 不是 `.mapper`; 通配 `**` 后缀兜底 (monolith gateway 里 gateway-impl 也是用 `**` 后缀)
        "com.chinacreator.gzcm.sysman.**.dao",
        "com.chinacreator.gzcm.sysman.**.mapper",
        // security-engine dao (DATA_SECURITY_POLICY / RLS / ABAC 服务落在 security.engine 里,
        // 铁律 §0.3 声明由 sysman service 管理命中)
        "com.chinacreator.gzcm.engine.security.**.dao",
        "com.chinacreator.gzcm.engine.security.**.mapper",
        // runtime 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao"
})
public class SysmanServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysmanServiceApplication.class, args);
    }
}
