package com.chinacreator.gzcm.gateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A1 拆解 dccheng 的删前门禁。
 * 断言：引擎层(com.chinacreator.gzcm.engine..) 不得依赖 dccheng(com.chinacreator.gzcm.dccheng..)。
 * A1-1 迁移底层类后此断言必须为绿；A1-3 删 dccheng 目录前必须通过。
 */
public class DcchengRemovalGuardTest {

    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinacreator.gzcm.engine", "com.chinacreator.gzcm.dccheng");

    @Test
    void 引擎层不得依赖dccheng() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.chinacreator.gzcm.engine..")
                .should().dependOnClassesThat().resideInAPackage("com.chinacreator.gzcm.dccheng..");
        rule.check(classes);
    }
}
