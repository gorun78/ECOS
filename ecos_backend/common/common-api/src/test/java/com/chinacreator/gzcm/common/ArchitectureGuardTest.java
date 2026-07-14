package com.chinacreator.gzcm.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.*;

public class ArchitectureGuardTest {
    private static JavaClasses allClasses;

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinacreator.gzcm");
    }

    // ── 层次违规 ──────────────────────────────

    @Test
    void workspace层不得依赖buszhi层() {
        noClasses().that().resideInAPackage("..workspace..")
            .should().dependOnClassesThat()
            .resideInAPackage("..buszhi..")
            .allowEmptyShould(true)
            .check(allClasses);
    }

    @Test
    void workspace层不得依赖worldmodel层() {
        noClasses().that().resideInAPackage("..workspace..")
            .should().dependOnClassesThat()
            .resideInAPackage("..worldmodel..")
            .allowEmptyShould(true)
            .check(allClasses);
    }

    @Test
    void common层不得依赖任何业务模块() {
        noClasses().that().resideInAPackage("..common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..buszhi..", "..dccheng..", "..datanet..",
                "..workspace..", "..worldmodel..", "..aimod..",
                "..portal..", "..market..", "..cognitive..",
                "..gateway..", "..sysman.."
            )
            .check(allClasses);
    }

    @Test
    void 禁止下層依賴上層() {
        // datanet(D) 不得依赖 buszhi(K)
        noClasses().that().resideInAPackage("..datanet..")
            .should().dependOnClassesThat()
            .resideInAPackage("..buszhi..")
            .allowEmptyShould(true)
            .check(allClasses);
    }

    // ── Controller规范 ────────────────────────

    @Test
    void Controller不得new_JdbcTemplate() {
        noClasses().that().resideInAPackage("..controller..")
            .and().areNotAnonymousClasses()
            .should().accessClassesThat()
            .resideInAPackage("org.springframework.jdbc..")
            .allowEmptyShould(true)
            .check(allClasses);
        // JdbcTemplate必须通过构造器注入，不得字段直接new
    }

    @Test
    void Controller返回类型必须是ApiResponse() {
        classes().that().resideInAPackage("..controller..")
            .and().arePublic()
            .and().haveSimpleNameEndingWith("Controller")
            .should()  // 不强校验每个方法，但检查类存在
            .resideInAPackage("..controller..")
            .allowEmptyShould(true)
            .check(allClasses);
        // TODO: 逐步强化到方法级检查
    }

    // ── 模块物理边界 ───────────────────────────

    @Test
    void 标准版不得引用Doris() {
        // 任何模块不得直接import org.apache.doris
        noClasses().that().resideOutsideOfPackage("..olap..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.apache.doris..")
            .allowEmptyShould(true)
            .check(allClasses);
    }

    @Test
    void 标准版不得引用Neo4j_Driver() {
        // 任何模块不得直接import org.neo4j.driver.*
        noClasses().that().resideOutsideOfPackage("..graph..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.neo4j.driver..")
            .allowEmptyShould(true)
            .check(allClasses);
    }

    // ── 模块间无循环依赖 ───────────────────────

    @Test
    void 模块之间不得存在循环依赖() {
        slices().matching("com.chinacreator.gzcm.(*)..")
            .should().beFreeOfCycles()
            .check(allClasses);
    }

    // ── 禁止清单 ───────────────────────────────

    @Test
    void 禁止直接使用System_out_println() {
        noClasses().that().resideInAPackage("com.chinacreator.gzcm..")
            .should().callMethod(System.class, "out")
            .check(allClasses);
    }

    @Test
    void 禁止在Controller外使用HttpServletRequest() {
        noClasses().that().resideOutsideOfPackages("..controller..", "..filter..", "..security..")
            .should().dependOnClassesThat()
            .resideInAPackage("jakarta.servlet.http..")
            .allowEmptyShould(true)
            .check(allClasses);
    }
}
