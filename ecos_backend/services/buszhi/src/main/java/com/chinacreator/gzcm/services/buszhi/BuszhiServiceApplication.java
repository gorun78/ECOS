package com.chinacreator.gzcm.services.buszhi;

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
 * ECOS Buszhi 微服务 — 本体服务（金·I）
 *
 * <p>P2-3 目标态：封装 ontology-engine（本体建模/对象/关系/版本）+ 原顶层 buszhi（工作流引擎 + 对象管理）。
 *
 * <p>Phase 2 现状：**代码物理搬迁待做**
 * <ul>
 *   <li>原 buszhi/ 顶层目录 → services/buszhi/src (保留 package 名 com.chinacreator.gzcm.buszhi)**
 *   <li>gateway 的 @ComponentScan 移 basePackages 中 com.chinacreator.gzcm.buszhi + 相应 excludeFilters
 *   <li>VersionPrefixRewriteFilter 需为精确前缀路由 (buszhi /api/v1/ecos/object/* vs workspace /api/v1/ecos/objects/*)
 * </ul>
 *
 * @author ecos-factory
 * @since PMO-49 P2-3 (骨架交付, 代码干 Phase 3 执行)
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Buszhi 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7)
        "com.chinacreator.gzcm.services.buszhi",
        // 主封装引擎 — ontology-engine (金·I): 本体建模/对象/关系/版本
        "com.chinacreator.gzcm.engine.ontology",
        // 原 buszhi 包 — 即物体到 buszhi-impl 库 (workflow: 23 类),
        // 依赖包 classpath 自动引入, 此处 pack-scan 名义注册
        "com.chinacreator.gzcm.buszhi",
        // sysman.config.service — CognitiveConfigController 依赖 SysConfigService,
        // 只扫 config 子包避开 sysman 主体 security 域 (避免三滤波器越界)
        "com.chinacreator.gzcm.sysman.config",
        // engine.data.service — ICopilotService impl (CopilotServiceImpl) in DatEngine,
        // OntologyCopilotController 依赖 bean (同 workspace-service)
        "com.chinacreator.gzcm.engine.data.service",
        // 横切底座
        "com.chinacreator.gzcm.runtime"
}, excludeFilters = {
        // PMO-57 T2 后说明 (2026-09-13): 本排除项 target 是 ontology 侧副本
        // com.chinacreator.gzcm.engine.ontology.controller.WorkflowController,
        // 该 class 已随 PMO-57 T2 物理删除（workflow 业务按架构铁律 §0.3.1
        // 单点归 buszhi 服务层）。REGEX 型排除对不存在的 class 恒不命中、
        // 零副作用（不触发包类加载即无语义错误），当前保留无风险，
        // 留待 PMO-57 后续批次收口清单统一撤除。
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.chinacreator\\.gzcm\\.engine\\.ontology\\.controller\\.WorkflowController")
})
@MapperScan({
        "com.chinacreator.gzcm.engine.ontology.**.dao",
        "com.chinacreator.gzcm.engine.ontology.**.mapper",
        "com.chinacreator.gzcm.engine.ontology.**.repository",
        "com.chinacreator.gzcm.buszhi.**.dao",
        "com.chinacreator.gzcm.buszhi.**.mapper",
        "com.chinacreator.gzcm.buszhi.**.repository",
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao",
        // runtime.llm.repository 横切底座
        "com.chinacreator.gzcm.runtime.llm.repository",
        // engine.data @Mapper 接口 (CopilotServiceImpl 构造器需 DataSourceRepository)
        "com.chinacreator.gzcm.engine.data.**.repository",
        "com.chinacreator.gzcm.engine.data.**.mapper"
})
public class BuszhiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuszhiServiceApplication.class, args);
    }
}
