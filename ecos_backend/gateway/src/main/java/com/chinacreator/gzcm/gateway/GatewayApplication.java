package com.chinacreator.gzcm.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS 统一入口应用 — 基于 Spring Boot MVC (Tomcat)。
 *
 * <p>接管原 sysman-boot 的所有职责：
 * <ul>
 *   <li>导入全部业务模块（sysman/runtime/buszhi/datanet/aimod/portal/market/worldmodel/workspace）</li>
 *   <li>暴露 /api/* REST 端点（无 /sys-man 前缀）</li>
 *   <li>健康检查 /actuator/health</li>
 *   <li>OpenAPI 3.0 文档 /v3/api-docs</li>
 * </ul>
 *
 * <p>启动命令：
 * <pre>mvn spring-boot:run -pl gateway -DskipTests -Dspring-boot.run.profiles=dev</pre>
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.gateway",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime",
    "com.chinacreator.gzcm.buszhi",
    "com.chinacreator.gzcm.aimod",
    "com.chinacreator.gzcm.market",
    "com.chinacreator.gzcm.worldmodel",
    "com.chinacreator.gzcm.workspace",
    "com.chinacreator.gzcm.portal",
    "com.chinacreator.gzcm.datanet",
    "com.chinacreator.gzcm.cognitive",
    "com.chinacreator.gzcm.engine",
    "com.chinacreator.gzcm.services.agent.runtime",
    "com.chinacreator.gzcm.services.agent.model",
}, excludeFilters = {
    // A+3: 排除旧包 runtime.core.agent（已迁入 ai-engine）
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.agent\\..*"),
    // A+4: 排除迁出后的 runtime.core 旧包
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.git\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.datapermission\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.compliance\\..*"),
    // A+5: 排除 runtime.access.storage（gateway有自己的MinioStorageService实现）
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.access\\.storage\\..*"),
    // A+5: runtime.core.config.dao.ConfigDao 源码已删（sysman版本保留，无冲突）
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.aimod\\.controller\\..*"),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig.class,
        // A+4: 排除旧实现（runtime-access 有同名类，gateway版本保留供 DataLakeExportService 使用）
        com.chinacreator.gzcm.workspace.service.MinioObjectStorageService.class,
        // MinioStorageService (gateway.service) 保留：DataLakeExportService 依赖它，不要排除
        com.chinacreator.gzcm.sysman.config.dao.ConfigDao.class,
        com.chinacreator.gzcm.sysman.controller.SysConfigController.class,
        // 安全引擎已接管（阶段1），排除sysman侧副本
        com.chinacreator.gzcm.sysman.controller.AbacController.class,
        com.chinacreator.gzcm.sysman.controller.AuditController.class,
        com.chinacreator.gzcm.sysman.controller.CryptoAuditController.class,
        com.chinacreator.gzcm.sysman.controller.DataMaskingController.class,
        com.chinacreator.gzcm.sysman.controller.DataPermissionController.class,
        com.chinacreator.gzcm.sysman.controller.PolicyEngineController.class,
        com.chinacreator.gzcm.sysman.controller.SecurityConfigController.class,
        // 数据引擎已接管（阶段2），排除datanet侧副本
        com.chinacreator.gzcm.datanet.controller.CatalogController.class,
        com.chinacreator.gzcm.datanet.controller.DataSourceController.class,
        com.chinacreator.gzcm.datanet.controller.MetadataController.class,
        com.chinacreator.gzcm.datanet.controller.CategoryController.class,
        com.chinacreator.gzcm.datanet.controller.DatanetHealthController.class,
        // 本体引擎已接管（阶段3），排除buszhi侧副本
        com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController.class,
        // 认知引擎已接管（阶段4），aimod.controller包已由REGEX过滤器整体排除
        com.chinacreator.gzcm.gateway.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.CognitiveController.class,
        // 双重认知端点冲突: cognitive2/CognitiveEngineHealthController + ai-engine/CognitiveController 都映射 /api/v1/cognitive/health
        // ai-engine/CognitiveController 应保留在 classpath，exclude cognitive-engine 版本
        // A+3: 排除旧包 runtime.core.agent.mesh.*（ai-engine 版本接管）
        // agent.mesh 14 类迁入 ai-engine，gateway classpath 仍有 runtime-core JAR，需排除旧 Bean
        com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController.class,
        // 引擎接管: gateway→data-engine/cognitive-engine/security-engine (阶段6)
        // DataLakeController 留在gateway (依赖gateway内部service: DuckDB/DataLakeExport/Minio)
        com.chinacreator.gzcm.gateway.controller.EcosKnowledgeGraphController.class,
        com.chinacreator.gzcm.gateway.controller.SecurityController.class,
        // 安全引擎: security-engine-impl 的 abac.dao 与 runtime-crypto JAR 冲突，exclude 源码版本
        com.chinacreator.gzcm.engine.security.abac.dao.impl.AbacPolicyDaoImpl.class,
        // 模块吸收: portal→workspace (阶段5.1)
        com.chinacreator.gzcm.portal.controller.BizDashboardController.class,
        com.chinacreator.gzcm.portal.controller.ContractStatsController.class,
        com.chinacreator.gzcm.portal.controller.ProjectStatsController.class,
        com.chinacreator.gzcm.portal.controller.PortalAggregationController.class,
        com.chinacreator.gzcm.portal.controller.MenuController.class,
        // 模块吸收: market→workspace (阶段5.1)
        com.chinacreator.gzcm.market.controller.MarketplaceController.class,
        // 模块吸收: worldmodel→buszhi (阶段5.1)
        com.chinacreator.gzcm.worldmodel.controller.CaseController.class,
        com.chinacreator.gzcm.worldmodel.controller.CausalController.class,
        com.chinacreator.gzcm.worldmodel.controller.ParetoController.class,
        com.chinacreator.gzcm.worldmodel.controller.WorldModelController.class,
        com.chinacreator.gzcm.worldmodel.service.OntologyKgSyncService.class,
        com.chinacreator.gzcm.worldmodel.service.PgGraphService.class,
        com.chinacreator.gzcm.worldmodel.service.Neo4jGraphService.class,
        // A+3: 排除旧包 runtime.core.agent.mesh（agent.mesh 已迁入 ai-engine）
    })
})
@MapperScan({
    "com.chinacreator.gzcm.sysman.**.mapper",
    // runtime.core.config.dao.ConfigDao 与 sysman 版本冲突，exclude runtime 包中的副本
    "com.chinacreator.gzcm.runtime.core.config.dao!",
    "com.chinacreator.gzcm.runtime.**.mapper",
    "com.chinacreator.gzcm.runtime.llm.repository",
    "com.chinacreator.gzcm.engine.ai.agent.mesh.repository",
    "com.chinacreator.gzcm.engine.ai.agent.mesh.knowledge.repository",
    "com.chinacreator.gzcm.datanet.repository",
    "com.chinacreator.gzcm.engine.kb.repository"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
