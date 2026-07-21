package com.chinacreator.gzcm.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECOS 统一入口应用 — 基于 Spring Boot MVC (Tomcat)。
 *
 * <p>接管原 sysman-boot 的所有职责：
 * <ul>
 *   <li>导入全部业务模块（sysman/runtime/buszhi/dccheng/datanet/aimod/portal/market/worldmodel/workspace）</li>
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
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.gateway",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime",
    "com.chinacreator.gzcm.dccheng",
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
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.aimod\\.controller\\..*"),
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig.class,
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
        // 本体引擎已接管（阶段3），排除dccheng/buszhi侧副本
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyActionController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyActionApiController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyPropertyController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyRelationshipController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyDomainController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyDomainApiController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyVersionController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyVersionSimpleController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyRuleController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyProposalController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyMappingController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyExportController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.OntologyDataController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.CeosCompatController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.AutoDiscoverController.class,
        com.chinacreator.gzcm.dccheng.ontology.controller.LineageController.class,
        com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController.class,
        // 认知引擎已接管（阶段4），aimod.controller包已由REGEX过滤器整体排除
        com.chinacreator.gzcm.dccheng.knowledgegraph.controller.KnowledgeGraphController.class,
        com.chinacreator.gzcm.dccheng.knowledgegraph.controller.GraphSyncController.class,
        com.chinacreator.gzcm.dccheng.knowledge.KnowledgeApiController.class,
        com.chinacreator.gzcm.dccheng.knowledge.KnowledgeSettingsController.class,
        com.chinacreator.gzcm.dccheng.guardrails.GuardrailsApiController.class,
        com.chinacreator.gzcm.dccheng.glossary.controller.GlossaryController.class,
        com.chinacreator.gzcm.dccheng.classification.controller.ClassificationController.class,
        com.chinacreator.gzcm.cognitive.impl.CognitiveController.class,
        com.chinacreator.gzcm.gateway.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.AgentChatController.class,
        com.chinacreator.gzcm.engine.ai.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.CognitiveController.class,
        // 引擎接管: gateway→data-engine/cognitive-engine/security-engine (阶段6)
        // DataLakeController 留在gateway (依赖gateway内部service: DuckDB/DataLakeExport/Minio)
        com.chinacreator.gzcm.gateway.controller.EcosKnowledgeGraphController.class,
        com.chinacreator.gzcm.gateway.controller.SecurityController.class,
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
        // 知识引擎已接管（阶段7），排除ai-engine侧知识Controller副本
        com.chinacreator.gzcm.engine.ai.controller.KnowledgeApiController.class,
        com.chinacreator.gzcm.engine.ai.controller.KnowledgeGraphController.class,
        com.chinacreator.gzcm.engine.ai.controller.EcosKnowledgeGraphController.class,
        com.chinacreator.gzcm.engine.ai.controller.KnowledgeSettingsController.class,
        com.chinacreator.gzcm.engine.ai.controller.GraphSyncController.class,
        // Gateway瘦身: 业务Controller移回引擎 (阶段6)
    })
})
@MapperScan({
    "com.chinacreator.gzcm.sysman.**.mapper",
    "com.chinacreator.gzcm.runtime.**.mapper",
    "com.chinacreator.gzcm.runtime.hermes.repository",
    "com.chinacreator.gzcm.runtime.core.agent.mesh.repository",
    "com.chinacreator.gzcm.runtime.core.agent.mesh.knowledge.repository",
    "com.chinacreator.gzcm.datanet.repository",
    "com.chinacreator.gzcm.engine.kb.repository"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
