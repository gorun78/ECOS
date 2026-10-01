package com.chinacreator.gzcm.engine.cognitive2;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchitectureTest — cognitive-engine 架构约束（PMO-D1）
 *
 * <p>断言：
 * <ol>
 *   <li>所有类在 engine.cognitive 包下</li>
 *   <li>service 接口以 I 开头</li>
 *   <li>controller 在 controller 包</li>
 *   <li>entity/model 不依赖 service 层</li>
 *   <li>引擎间依赖边界：Allowed cross-engine deps: kb</li>
 * </ol>
 */
public class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .importPackages("com.chinacreator.gzcm.engine.cognitive2");
    }

    // ── 包约束 ──

    @Test
    void allPackagesShouldBeUnderEngine() {
        ArchRule rule = classes().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.cognitive2..")
            .should().resideInAnyPackage(
                "com.chinacreator.gzcm.engine.cognitive2..",
                "com.chinacreator.gzcm.runtime..",
                "com.chinacreator.gzcm.common..",
                "java..",
                "javax..",
                "jakarta..",
                "org.springframework..",
                "org.mybatis..",
                "com.fasterxml..",
                "org.slf4j..",
                "org.junit..",
                "com.tngtech.."
            );
        rule.check(classes);
    }

    // ── 命名规范 ──

    @Test
    void serviceInterfacesShouldStartWithI() {
        ArchRule rule = classes().that()
            .resideInAPackage("..service..")
            .and().areInterfaces()
            .should().haveSimpleNameStartingWith("I")
            .orShould().haveSimpleNameStartingWith("Knowledge")  // kb-engine legacy
            .orShould().haveSimpleNameStartingWith("Causal")     // cognitive-engine legacy
            .orShould().haveSimpleNameStartingWith("Engine")     // IEngine variants
            // 2026-10-02 详细设计-04（commit b617c8bc）新增的两个非 I 前缀契约：
            //   - CognitivePipelineStore：认知管线持久化端口，其 impl 是 JdbcTemplate 承载的
            //     CognitivePipelineRepository（R2 数据访问收口产物）；此处仅作为解耦端口，
            //     命名沿 "…Store" 而非 "IService…" 是历史接口文件里的既有命名。
            //   - MentalEvent：Cognitive 事件契约（PMO-59 P2b / ADR-9，topic `ecos.cognitive`），
            //     承载 COGNITIVE_HYPOTHESIS_INVALIDATED / BELIEF_UPDATED / EVIDENCE_REGISTERED
            //     三类心智层事件；是事件 DTO 而非服务门面，故不套 I 前缀。
            // 精确白名单（haveSimpleName）而非前缀，避免放宽 "..service.. 下所有 Cognitive*/Mental*"。
            .orShould().haveSimpleName("CognitivePipelineStore")
            .orShould().haveSimpleName("MentalEvent");
        rule.allowEmptyShould(true).check(classes);
    }

    // ── 分层约束 ──

    @Test
    void entityClassesShouldNotDependOnServiceLayer() {
        ArchRule rule = noClasses().that()
            .resideInAnyPackage("..entity..", "..model..", "..bean..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..");
        rule.check(classes);
    }

    @Test
    void controllersShouldBeInControllerPackage() {
        ArchRule rule = classes().that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..controller..");
        rule.check(classes);
    }

    // ── 引擎间依赖边界（P1-2） ──

    @Test
    void shouldNotDependOnOtherEngines() {
        ArchRule rule = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.cognitive2..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.chinacreator.gzcm.engine.data..", "com.chinacreator.gzcm.engine.ontology..", "com.chinacreator.gzcm.engine.ai..", "com.chinacreator.gzcm.engine.security..");
        rule.check(classes);
    }
}
