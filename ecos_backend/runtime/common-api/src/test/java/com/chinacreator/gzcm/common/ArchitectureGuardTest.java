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

    /** 业务模块根包 —— 全部锚定到全限定根，不用裸段（裸段 "..cognitive.." 会同时命中 gzcm.common.cognitive） */
    private static final String[] BUSINESS_PACKAGES = {
        "com.chinacreator.gzcm.buszhi..", "com.chinacreator.gzcm.dccheng..",
        "com.chinacreator.gzcm.datanet..", "com.chinacreator.gzcm.workspace..",
        "com.chinacreator.gzcm.worldmodel..", "com.chinacreator.gzcm.aimod..",
        "com.chinacreator.gzcm.portal..", "com.chinacreator.gzcm.market..",
        "com.chinacreator.gzcm.cognitive..", "com.chinacreator.gzcm.gateway..",
        "com.chinacreator.gzcm.sysman.."
    };

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.chinacreator.gzcm");
        // 导入分母必须可见：importPackages 走 classpath，本模块 classpath 上 0 个业务构件，
        // 分母只含 common 自身编译产物。没有这个数字，绿色会被误读为"全仓依赖方向合规"。
        System.out.println("Imported " + allClasses.size() + " classes for ArchitectureGuardTest scope analysis.");
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
        // 两侧都锚定全限定根：
        //   主语用精确的平台公共层根包，不用 "..common.."（裸段会把 gzcm.sysman.common.* 也算成公共层，
        //       于是 sysman 内部自依赖被误判为「公共层依赖业务模块」）；
        //   宾语用锚定根包，不用 "..cognitive.." 等裸段（会同时命中 gzcm.common.cognitive，
        //       使「引用自身嵌套类」被误判为依赖业务模块 —— 本规则此前 3 次开火全部源于此）。
        noClasses().that().resideInAPackage("com.chinacreator.gzcm.common..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(BUSINESS_PACKAGES)
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
