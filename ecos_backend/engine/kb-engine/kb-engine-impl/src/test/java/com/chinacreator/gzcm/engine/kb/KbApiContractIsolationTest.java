package com.chinacreator.gzcm.engine.kb;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * F04-18 契约隔离验收测试（mvn -Dtest=KbApiContractIsolationTest）。
 *
 * <p>{@link #apiModuleHasNoEngineInternalImports()} — kb-engine-api 的对外模型
 * （{@code engine.kb.model.*} / {@code engine.kb.nav.*}）不得引用 kb 内部实现分层
 * （service / controller / dao / repository / mapper / task / shared / profile / assumption），
 * 亦不得引用其他引擎的内部实现（仅允许共享 {@code engine.ontology.model.*}，
 * 例如 {@code ExtractedSubGraph.*}）。
 *
 * @author ECOS KB Team
 */
public class KbApiContractIsolationTest {

    private static JavaClasses apiClasses;

    @BeforeAll
    static void setUp() {
        apiClasses = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.engine.kb.model",
                        "com.chinacreator.gzcm.engine.kb.nav");
    }

    @Test
    public void apiModuleHasNoEngineInternalImports() {
        ArchRule rule = noClasses().that()
                .resideInAnyPackage(
                        "com.chinacreator.gzcm.engine.kb.model..",
                        "com.chinacreator.gzcm.engine.kb.nav..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.chinacreator.gzcm.engine.kb.service..",
                        "com.chinacreator.gzcm.engine.kb.controller..",
                        "com.chinacreator.gzcm.engine.kb.dao..",
                        "com.chinacreator.gzcm.engine.kb.repository..",
                        "com.chinacreator.gzcm.engine.kb.mapper..",
                        "com.chinacreator.gzcm.engine.kb.task..",
                        "com.chinacreator.gzcm.engine.kb.profile..",
                        "com.chinacreator.gzcm.engine.kb.assumption..",
                        "com.chinacreator.gzcm.engine.kb.shared..",
                        "com.chinacreator.gzcm.engine.ontology.service..",
                        "com.chinacreator.gzcm.engine.ontology.impl..",
                        "com.chinacreator.gzcm.engine.ontology.controller..",
                        "com.chinacreator.gzcm.engine.ontology.mapper..",
                        "com.chinacreator.gzcm.engine.ontology.repository..",
                        "com.chinacreator.gzcm.engine.data..",
                        "com.chinacreator.gzcm.engine.cognitive2..",
                        "com.chinacreator.gzcm.engine.ai..",
                        "com.chinacreator.gzcm.engine.security..");
        // 允许 engine.ontology.model.*（OntologyTreeController 共享 ExtractedSubGraph.*）。
        rule.check(apiClasses);
    }
}
