package com.chinacreator.gzcm.services.aiming;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.chinacreator.gzcm.engine.ai.controller.DiagnosticAgentController;
import com.chinacreator.gzcm.engine.ai.controller.CognitiveController;
import com.chinacreator.gzcm.engine.ai.service.CognitiveService;

/**
 * ECOS Aiming 微服务 — 智能服务（火·W 智慧）
 *
 * <p>P2-5 目标态：聚合 ai-engine（Agent/Loop/Memory/LLM/工具）+ agent-service runtime
 * （ToolRouter/Memory/Planner/Governance/Reflection/Telemetry）+ llm-gateway，
 * 承担 ming (K→W) 转化。
 *
 * <p>依赖方向（单向，无循环）：
 * <ul>
 *   <li>ai-engine-impl 以 import 形式引用 services.agent.* 的 model/service（compile-time 依赖）</li>
 *   <li>agent-service POM 依赖 llm-gateway/common-api/runtime-core</li>
 *   <li>aiming 同时依赖三者，classpath 齐备</li>
 * </ul>
 *
 * <p>排除项：
 * <ul>
 *   <li>{@code services.agent.AgentServiceApplication} — 它有自己的 @ComponentScan，
 *       与本 service 双启动冲突，用正则 exclude 隔离</li>
 *   <li>ai-engine-boot 的 {@code AiEngineApplication} — 同理，独立调试入口非生产</li>
 * </ul>
 *
 * @author ecos-factory
 * @since PMO-49 P2-5
 */
@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class
})
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
        // Aiming 服务自身 (HeaderAuthInterceptor / 服务级 config / ADR-7)
        "com.chinacreator.gzcm.services.aiming",
        // 主封装引擎 — ai-engine (火·W): Agent/Loop/Memory/LLM/工具路由
        "com.chinacreator.gzcm.engine.ai",
        // agent-service runtime (ToolRouter/Memory/Planner/Governance/Reflection/Telemetry)
        "com.chinacreator.gzcm.services.agent",
        // LLM 调用收敛 (铁律 §2.5)
        "com.chinacreator.gzcm.runtime.llm",
        // 横切底座
        "com.chinacreator.gzcm.runtime",
        // sysman.config.service — CognitiveConfigController (ai-engine) 需要 SysConfigService
        // 具体实现; 只扫 config.service 子包，不带入 sysman 安全管理 controller
        "com.chinacreator.gzcm.sysman.config.service",
        // sysman 域 mapper/dao (SysConfigService 依赖 SysConfig DAO)
        "com.chinacreator.gzcm.sysman.config"
}, excludeFilters = {
        // agent-service 独立启动器 (自身 @ComponentScan, 双启动冲突)
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.chinacreator\\.gzcm\\.services\\.agent\\.AgentServiceApplication"),
        // ai-engine-boot 独立启动器 (非生产入口, 铁律 §0.3)
        @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "com\\.chinacreator\\.gzcm\\.engine\\.ai\\.boot\\..*"),
        // R1: 镜像 gateway GatewayApplication 对 ai 域的三重解冲突排除
        //  - DiagnosticAgentController 与 AgentChatController 同路由 POST /api/v1/agent/chat
        //  - CognitiveController (ai) 与 cognitive2 版本同路由 cognitive/health
        //  - CognitiveService 引用已删 Class (CausalReasoner 等), 启动 UnsatisfiedDependency
        // 保留: AgentChatController + cognitive2 版本 (与 gateway 取舍一致)
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                DiagnosticAgentController.class,
                CognitiveController.class,
                CognitiveService.class
        })
})
@MapperScan({
        // ai-engine 主 repository (Skill/CronJob/Agent/Mission/Knowledge)
        "com.chinacreator.gzcm.engine.ai.**.repository",
        "com.chinacreator.gzcm.engine.ai.**.dao",
        "com.chinacreator.gzcm.engine.ai.**.mapper",
        // agent runtime 若有独立 mapper (防御性)
        "com.chinacreator.gzcm.services.agent.**.repository",
        "com.chinacreator.gzcm.services.agent.**.dao",
        "com.chinacreator.gzcm.services.agent.**.mapper",
        // llm-gateway 横切底座 (AgentCallLogRepository)
        "com.chinacreator.gzcm.runtime.llm.repository",
        // runtime 横切底座
        "com.chinacreator.gzcm.runtime.**.mapper",
        "com.chinacreator.gzcm.runtime.**.dao"
})
public class AimingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AimingServiceApplication.class, args);
    }
}
