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
    // 2026-09-12: gateway 与 workspace 同名 Bean 模块级排除 — gateway 是 authority (ITaskManagementService / alerts 等),
    // workspace 包中所有 controller stub 与 gateway 同名冲突 (alertController/engineTaskController/taskController/...)
    // 完整排除 workspace.controller 包 (workspace 端独立 :18090 已扫, gateway 无需重复)
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.workspace\\.controller\\..*"),
    // workspace.twin.* 同样与 gateway.twin.* 同名冲突 (digitalTwinService 等) — workspace 端独立运行, 整包排除
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.chinacreator\\.gzcm\\.workspace\\.twin\\..*"),
    // workspace.service.* 中的 PgObjectStorageService 已在 ASSIGNABLE_TYPE 排除, 这里不重复
    // 如需追加更宽的包级排除 (workspace 端业务 service), 在此追加
    // A+5: runtime.core.config.dao.ConfigDao 源码已删（sysman版本保留，无冲突）
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
        com.chinacreator.gzcm.runtime.core.mybatis.config.MyBatisConfig.class,
        com.chinacreator.gzcm.sysman.config.dao.ConfigDao.class,
        com.chinacreator.gzcm.sysman.controller.SysConfigController.class,
        // 数据引擎已接管，旧 datanet 模块已删除（A+7c）
        // PMO-57 T2 (2026-09-13): ontology 侧 workflow Controller 副本已物理删除，
        // workflow 端点单点收敛至 buszhi 侧 WorkflowController（/api/v1/ecos/workflows），
        // 原"排除 buszhi 侧副本让 ontology 副本存活"条目随之移除，
        // gateway fat-JAR 态恢复 buszhi 侧 Bean 加载（追踪: docs/04-本体/PMO-57）。
        com.chinacreator.gzcm.engine.ai.controller.DiagnosticAgentController.class,
        com.chinacreator.gzcm.engine.ai.controller.CognitiveController.class,
        // PMO-55 E-A 修复 (2026-09-12)：OagForwardController 已在 gateway 正式接管
        // /api/v1/oag/**（bd07da4 refactor），engine 侧 OagController 同路径
        // /api/v1/oag/chat/stream 导致 Ambiguous mapping。排除 engine 副本，
        // 统一走 gateway 转发（与 CognitiveController 排除模式对齐）。
        com.chinacreator.gzcm.engine.ai.oag.OagController.class,
        // PMO-55 E-A 修复 (2026-09-12)：cognitive2/CognitiveConfigController 是
        // PMO-51 引入的权威认知配置端点（含 DTO + PUT），engine/ai 侧同名
        // CognitiveConfigController 只处理 GET，两者都映射 GET /api/v1/cognitive/config
        // 导致 Ambiguous mapping。排除 ai 侧简化版，保留 cognitive2 完整版。
        com.chinacreator.gzcm.engine.ai.controller.CognitiveConfigController.class,
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
        // 2026-09-12: gateway.controller.AlertController 已删除 (源码 stub) — 保留 workspace 版本作为唯一 AlertController
        // workspace.controller.EngineTaskController 类已随 merge feature/knowledge-pmo-50 从代码库删除
        // (2026-09-14 PMO-59-P2a 核对: 原 exclude 条目引用不存在的类致 gateway 编译失败, 移除该死引用;
        //  该类 Bean 本就缺失, 移除 exclude 0 行为变化 — workspace.controller 整包已由上方 REGEX 排除覆盖)
        // E3: sysman-boot GlobalExceptionHandler与gateway版本冲突,排除sysman-boot副本
        com.chinacreator.gzcm.sysman.boot.handler.GlobalExceptionHandler.class,
        // E3: 排除SysManApplication——它有自己的@ComponentScan会注册冲突bean
        com.chinacreator.gzcm.sysman.boot.SysManApplication.class,
        // PMO-57 T5 (2026-09-13): CognitiveEngineOpenHealthController (PMO-55 E-C 新副本)
        // 与 ai-engine AiEngineStatusController 同占 GET /api/v1/engine/cognitive/health
        // 导致 Ambiguous mapping。AiEngineStatusController 整类承载 5 端点 (health/config/
        // status/start/stop)，历史由它服务该 prefix，排除它会误杀 4 个 ai 专属端点；
        // 故保 ai 副本、排 cognitive 新副本 (0 行为变化)。cognitive 开放 health 功能
        // 仅在 cognitive standalone 态 (:18089) 生效。裁定原则: gateway 行为零变化优先。
        com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController.class,
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
    // PMO-48-A: DqRuleMapper 在 data.quality.mapper，纳入扫描否则 bean 缺失（质量包下任意 mapper 包）
    "com.chinacreator.gzcm.engine.data.quality.**.mapper",
    "com.chinacreator.gzcm.engine.data.quality.mapper",
    "com.chinacreator.gzcm.engine.kb.repository",
    // 非结构化文档链路：kb.doc.mapper 下的 KbDocMapper/KbDocChunkMapper 不在 kb.repository 内，
    // 不纳入扫描会导致 KnowledgeDocIngestService 注入失败（启动即失败），与 dccheng 侧口径保持一致
    "com.chinacreator.gzcm.engine.kb.**.mapper"
})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
