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
 *   <li>导入全部业务模块（sysman/runtime/buszhi/portal/market/worldmodel/workspace）</li>
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
    // M0 改造 (2026-09): 删除 4 个已删/迁移 basePackages (0 class 残留):
    //   - com.chinacreator.gzcm.market      (workspace 吸收, 已无 class)
    //   - com.chinacreator.gzcm.worldmodel  (buszhi 吸收, 已无 class)
    //   - com.chinacreator.gzcm.portal      (workspace 吸收, 已无 class)
    //   - com.chinacreator.gzcm.cognitive   (引擎层在 com.chinacreator.gzcm.engine.cognitive*, 不是 .cognitive)
    // 排除项保留 (excludeFilters 仍引用这些包的 class, 父类用于冲突规避)
    "com.chinacreator.gzcm.workspace",
    "com.chinacreator.gzcm.engine",
    "com.chinacreator.gzcm.services.agent.runtime",
    "com.chinacreator.gzcm.services.agent.model",
}, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.agent\\..*"),
    // A+4: 排除迁出后的 runtime.core 旧包
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.git\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.datapermission\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.runtime\\.core\\.compliance\\..*"),
    // A+5: runtime.core.config.dao.ConfigDao 源码已删（sysman版本保留，无冲突）
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig.class,
        com.chinacreator.gzcm.sysman.config.dao.ConfigDao.class,
        com.chinacreator.gzcm.sysman.controller.SysConfigController.class,
        // 数据引擎已接管，旧 datanet 模块已删除（A+7c）
        // 本体引擎已接管（阶段3），排除buszhi侧副本
        com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController.class,
        com.chinacreator.gzcm.engine.ai.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.CognitiveController.class,
        // 双重认知端点冲突: cognitive2/CognitiveEngineHealthController + ai-engine/CognitiveController 都映射 /api/v1/cognitive/health
        // ai-engine/CognitiveController 应保留在 classpath，exclude cognitive-engine 版本
        com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineHealthController.class,
        // 引擎接管: gateway→data-engine/cognitive-engine/security-engine (阶段6)
        com.chinacreator.gzcm.gateway.controller.EcosKnowledgeGraphController.class,
        com.chinacreator.gzcm.gateway.controller.SecurityController.class,
        // 安全引擎: security-engine-impl 的 abac.dao 与 runtime-crypto JAR 冲突，exclude 源码版本
        com.chinacreator.gzcm.engine.security.abac.dao.impl.AbacPolicyDaoImpl.class,
        // M0-P0 修复 (2026-09-01): CognitiveService 引用 pre-existing 已删除的
        // com.chinacreator.gzcm.cognitive.impl.RuleEngine/CausalReasoner/NsgaIIOptimizer
        // (只在 .m2 stale JAR 中存在, 当前 0 模块构建), 启动时 UnsatisfiedDependencyException.
        // 真实认知能力由 cognitive-engine (com.chinacreator.gzcm.engine.cognitive2.*) 提供,
        // CognitiveController 此前已 exclude, 一并 exclude CognitiveService Bean.
        // 跟踪: 08-产品化重构方案/04-C1-CognitiveService重复-impl-清理 (Wave-2 ai)
        com.chinacreator.gzcm.engine.ai.service.CognitiveService.class,
        // M0 改造 (2026-09): 删除 5 个已删/迁移模块的排除项 (class 已从 classpath 移除):
        //   - portal (workspace 吸收): BizDashboard/ContractStats/ProjectStats/PortalAggregation/Menu
        //   - market (workspace 吸收): Marketplace
        //   - worldmodel (buszhi 吸收): Case/Causal/Pareto/WorldModel
        //   - worldmodel services: OntologyKgSync/PgGraph/Neo4jGraph (迁到 ontology-engine)
        // E3-T2: gateway PgObjectStorageService是stub, workspace版是权威(@Profile("standard")), 删gateway副本避免Bean名冲突
        com.chinacreator.gzcm.gateway.service.PgObjectStorageService.class,
        // E3: sysman-boot GlobalExceptionHandler与gateway版本冲突,排除sysman-boot副本
        com.chinacreator.gzcm.sysman.boot.handler.GlobalExceptionHandler.class,
        // E3: 排除SysManApplication——它有自己的@ComponentScan会注册冲突bean
        com.chinacreator.gzcm.sysman.boot.SysManApplication.class,
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
    "com.chinacreator.gzcm.engine.data.repository",
    "com.chinacreator.gzcm.engine.kb.repository"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
