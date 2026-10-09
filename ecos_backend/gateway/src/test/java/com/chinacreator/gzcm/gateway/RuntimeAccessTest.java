package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 F06-17 — AI 域 RestTemplate 收敛 named projection（验收命令命名入口）。
 *
 * <p>F06-17 验收命令显式要求 {@code -Dtest='RuntimeAccessTest#noNewRestTemplateInAiDomain'}。
 * 本类为该命名入口的<b>独立投影</b>，与母规则
 * {@link ModuleDependencyArchTest#businessMustNotConstructRestTemplate()} 保持一致口径：
 * <ul>
 *   <li>限定 AI 域（{@code com.chinacreator.gzcm.engine.ai..} + {@code com.chinacreator.gzcm.runtime.llm..}）；</li>
 *   <li>免除 {@code ..config..} 共享客户端工厂（唯一合法 new RestTemplate 位置 = config 包的 Bean 工厂，
 *       {@code AiRestClientConfig#aiRestTemplate} / {@code CognitiveRestClientConfig#cognitiveRestTemplate} 形态）；</li>
 *   <li>免除 {@code ai-security} / {@code tool-executor} 两条已登记基线存量（详见
 *       {@code rest-template-baseline.txt} 头部：AiSecurityEngineClient 走 <b>per-call</b> 独立超时工厂，
 *       ToolExecutorService 保留非 final 字段作 F06-11 IT 注入面，均不得轻率迁移）。</li>
 * </ul>
 *
 * <p>X-24 主断言：W148 F06-17 收口后，AI 域三个 {@code new RestTemplate()} 的 original 三份
 * （{@code DiagnosticAgentController / ToolRegistry / ToolExecutorService:88 老路}）中
 * {@code ToolRegistry} / {@code DiagnosticAgentController} 已于本批次迁
 * {@code @Qualifier("aiRestTemplate")} 共享 Bean 工厂，基线相应删除；
 * {@code ToolExecutorService} 因 IT 注入面随 W148 收尾保留（基线仍登记）；
 * 故剩下的两个基线存量之外，任何 AI 域业务类直接 {@code new RestTemplate()} → 本投影 FAIL。</p>
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test -Dtest='RuntimeAccessTest'}</p>
 */
class RuntimeAccessTest {

    /** AI 域：ai-engine 全量 + llm-gateway 底座（承接 X-26 使底座独立 Facade 也能被断言）。 */
    private static final String[] AI_DOMAIN_PACKAGES = {
            "com.chinacreator.gzcm.engine.ai..",
            "com.chinacreator.gzcm.runtime.llm.."
    };

    /**
     * AI 域新 RestTemplate 存量基线。X-24 收口后应仅剩这两条：
     * {@code AiSecurityEngineClient}（per-call 独立超时工厂，锚定错误率隔离）与
     * {@code ToolExecutorService}（F06-11 IT 注入面）。任一条随迁移被删除时应同步删对应基线条目。
     */
    private static final Set<String> AI_REST_TEMPLATE_BASELINE = Set.of(
            "com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient",
            "com.chinacreator.gzcm.engine.ai.service.ToolExecutorService"
    );

    private static JavaClasses allClasses;

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.chinacreator.gzcm.engine.ai", "com.chinacreator.gzcm.runtime.llm");
        System.out.println("[RuntimeAccess] imported " + allClasses.size() + " AI-domain classes");
        assertTrue(allClasses.size() > 0,
                "AI 域宇宙 import 为空 —— llm-gateway/ai-engine target 未编入 classpath，门禁空转");
    }

    @Test
    @DisplayName("F06-17/X-24 named entry：AI 域除 config 工厂与登记基线外，任何类不得 new RestTemplate")
    void noNewRestTemplateInAiDomain() {
        DescribedPredicate<JavaClass> aiDomainSubject =
                new DescribedPredicate<JavaClass>("AI 域且非 config 共享客户端工厂、不在基线") {
                    @Override
                    public boolean test(JavaClass input) {
                        String pkg = input.getPackageName();
                        if (pkg.endsWith(".config") || pkg.contains(".config.")) {
                            return false; // 共享 Bean 工厂豁免（AiRestClientConfig / CognitiveRestClientConfig 形态）
                        }
                        return !AI_REST_TEMPLATE_BASELINE.contains(input.getName());
                    }
                };

        noClasses().that().resideInAnyPackage(AI_DOMAIN_PACKAGES)
                .and(aiDomainSubject)
                .should(new com.tngtech.archunit.lang.ArchCondition<JavaClass>(
                        "不 new org.springframework.web.client.RestTemplate（X-24 应收口至 AiRestClientConfig 共享 Bean）") {
                    @Override
                    public void check(JavaClass item, com.tngtech.archunit.lang.ConditionEvents events) {
                        java.util.Collection<com.tngtech.archunit.core.domain.JavaConstructorCall> calls =
                                item.getConstructorCallsFromSelf();
                        for (com.tngtech.archunit.core.domain.JavaConstructorCall call : calls) {
                            if ("org.springframework.web.client.RestTemplate".equals(
                                    call.getTarget().getOwner().getName())) {
                                events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(item,
                                        item.getName() + " 直接 new RestTemplate（应走 "
                                                + "com.chinacreator.gzcm.engine.ai.config.AiRestClientConfig#aiRestTemplate "
                                                + "或契约已清路径）"));
                            }
                        }
                    }
                })
                .allowEmptyShould(true)
                .check(allClasses);
    }
}
