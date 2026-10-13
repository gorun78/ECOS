package com.chinacreator.gzcm.services.datanet;

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
 * ECOS Datanet 微服务 — 数据服务（土·D）
 *
 * <p>P2-2 目标态：封装 data-engine（数据源/管道/血缘/DQ）+ ge 转化（D→I），
 * 替代原 gateway fat-JAR 中对 {@code com.chinacreator.gzcm.engine.data.*} 的聚合扫描。
 *
 * <p>拆分期双跑策略：
 * <ul>
 *   <li>gateway 保留对 data-engine 的组件扫描（老流量继续走 :8080）</li>
 *   <li>本 service 独立 :18082 承接 data-engine 全量路由</li>
 *   <li>切流由 gateway 路由开关 ECOS_ROUTE_datanet 控制，最终 gateway 清理 basePackages 完成</li>
 * </ul>
 *
 * <p>与 {@link com.chinacreator.gzcm.engine.data.boot.DataEngineApplication} 端口冲突处理：
 * 二者均默认 :18082，本机互斥运行（铁律 §0.3 引擎 boot 非生产入口）。
 *
 * @author ecos-factory
 * @since PMO-49 P2-2
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        // P3-A 修正：datanet 不扫 sysman 安全域 → 无 HttpSecurity bean → Actuat 的 ManagementWebSecurityAutoConfiguration
        // 尝试 create managementSecurityFilterChain 需 HttpSecurity → Unsatisfied. exclude 之.
        org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Datanet 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7 认证头还原)
        "com.chinacreator.gzcm.services.datanet",
        // 主封装引擎 — data-engine (土·D)
        "com.chinacreator.gzcm.engine.data",
        // 横切底座 (runtime-core 通用 mybatis/mapper/Jackson / runtime-access Driver 封装 / runtime-task 全局调度)
        "com.chinacreator.gzcm.runtime",
        // data-engine GitRepoRootResolver 依赖 sysman 域 SysConfigService (唯一注入 JdbcTemplate,
        // DataSource 自动配置即可满足) — 只补扫该实现包，并排除同包另外两个 sysman 域服务，
        // 避免连带拖入 ConfigDao/GitService/ISystemVariableService 等 datanet 不可满足的依赖
        "com.chinacreator.gzcm.sysman.config.service.impl",
        // R2 数据访问收口（2026-10-11 全栈拉起修复）：SysConfigService 的 JdbcTemplate 已下沉
        // sysman.dao.SysConfigDao（构造器注入）。sysman.dao 全部为无状态 JdbcTemplate DAO
        // （无跨域 Bean 依赖，逐一核过），定向补扫本包不复活上方排除的 sysman 域服务。
        "com.chinacreator.gzcm.sysman.dao"
}, excludeFilters = {
        // PMO-49 P3-A 修正：datanet 不跨领域扫 sysman 安全域 (security 域 JwtProvider/JWT filter bean 独立启动 JWT DI 现实无运行时 JWT 注入).
        // 跨域 controller 治理：DataEngineConfigController 依赖 sysman 域 SysConfigService 具体类, 属 sysman 而非 data 职责.
        // 独立 datanet 启动下 exclude 该孤 controller, /api/v1/data-engine/config/* 暂下 (P5/6 sysman REST 补).
        // 不影响 P3-A 迁迁的 DqDashboard/Git/DataLake 三大 controller.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                value = com.chinacreator.gzcm.engine.data.controller.DataEngineConfigController.class),
        // sysman.config.service.impl 定向扫描的配套排除（理由见上方 basePackages 注释）
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                com.chinacreator.gzcm.sysman.config.service.impl.ConfigServiceImpl.class,
                com.chinacreator.gzcm.sysman.config.service.impl.ConfigHotUpdateService.class
        })
})
@MapperScan({
        // data-engine 主 mapper (repository / quality / datasource / pipeline)
        "com.chinacreator.gzcm.engine.data.repository",
        // PMO-48-A T5: DqRuleMapper 与质量包下 mapper
        "com.chinacreator.gzcm.engine.data.quality.mapper",
        "com.chinacreator.gzcm.engine.data.quality.**.mapper",
        // data-engine 内部 dao 兼容写法
        "com.chinacreator.gzcm.engine.data.**.dao",
        "com.chinacreator.gzcm.engine.data.**.mapper",
        // 补充设计 v1.0 写通道：DwWriteChannelIdempotencyMapper 直接落 writechannel 包
        // （非 .mapper 子包），通配不命中，须显式列出（gateway 已同法登记）。
        "com.chinacreator.gzcm.engine.data.writechannel",
        // 详设-09 H-6 主数据完备性：MasterDataCompletenessMapper 落 fact.masterdata 包
        // （非 .mapper/.dao 子包），通配不命中，未登记则 datanet 启动即失败。
        "com.chinacreator.gzcm.engine.data.fact.masterdata",
        // 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao",
        // F06-20⑥ (X-8)：llm-gateway 横切底座（AgentCallLogRepository / ProfileConfigRepository）
        // —— 落在 .repository 后缀而非 .mapper/.dao，与 "runtime.**.mapper"/"runtime.**.dao" 通配不重叠，
        // 必须显式列出；否则 LlmUsageMeter / ProfileManagerImpl 构造注入 bean 缺失即启动失败。
        "com.chinacreator.gzcm.runtime.llm.repository"
})
public class DatanetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatanetServiceApplication.class, args);
    }
}
