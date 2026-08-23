package com.chinacreator.gzcm.engine.ontology;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchitectureTest — ontology-engine 架构约束（PMO-D1）
 *
 * <p>断言：
 * <ol>
 *   <li>所有类在 engine.ontology 包下</li>
 *   <li>service 接口以 I 开头</li>
 *   <li>controller 在 controller 包</li>
 *   <li>entity/model 不依赖 service 层</li>
 *   <li>引擎间依赖边界：No cross-engine deps allowed</li>
 * </ol>
 */
public class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .importPackages("com.chinacreator.gzcm.engine.ontology");
    }

    // ── 包约束 ──

    @Test
    void allPackagesShouldBeUnderEngine() {
        ArchRule rule = classes().that()
            .resideInAPackage("com.chinacreator.gzcm.engine.ontology..")
            .should().resideInAnyPackage(
                "com.chinacreator.gzcm.engine.ontology..",
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
            .orShould().haveSimpleNameStartingWith("Engine");    // IEngine variants
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
            .resideInAPackage("com.chinacreator.gzcm.engine.ontology..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.chinacreator.gzcm.engine.data..", "com.chinacreator.gzcm.engine.kb..", "com.chinacreator.gzcm.engine.cognitive2..", "com.chinacreator.gzcm.engine.ai..", "com.chinacreator.gzcm.engine.security..");
        rule.check(classes);
    }
}
