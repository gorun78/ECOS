package com.chinacreator.gzcm.sysman;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchitectureTest — 验证 sysman 模块架构约束
 * 
 * 模块结构：sysman-api (接口/DTO/Entity/DAO接口) + sysman-impl (DAO实现/Service实现)
 */
public class ArchitectureTest {

    private static JavaClasses apiClasses;
    private static JavaClasses implClasses;
    private static JavaClasses allClasses;

    @BeforeAll
    static void setUp() {
        apiClasses = new ClassFileImporter()
            .importPackages("com.chinacreator.gzcm.sysman");
        
        allClasses = apiClasses; // 已包含 api + impl
    }

    // ── 包约束 ──

    @Test
    void allPackagesShouldBeUnderSysman() {
        ArchRule rule = classes().that()
            .resideInAPackage("com.chinacreator.gzcm.sysman..")
            .should().resideInAnyPackage(
                "com.chinacreator.gzcm.sysman..",
                "com.chinacreator.gzcm.runtime.core..",
                "com.chinacreator.gzcm.common..",
                "java..",
                "javax..",
                "org.springframework..",
                "org.mybatis..",
                "com.fasterxml..",
                "org.slf4j.."
            );
        rule.check(allClasses);
    }

    @Test
    void sysmanDependenciesShouldNotLeakToOtherModules() {
        // sysman 不应该依赖 buszhi, dccheng, aimod 等业务模块
        ArchRule rule = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.sysman..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.chinacreator.gzcm.bus..",
                "com.chinacreator.gzcm.Cheng..",
                "com.chinacreator.gzcm.Ming.."
            );
        rule.check(allClasses);
    }

    @Test
    void apiLayerShouldNotDependOnImplPackage() {
        // api 模块的接口不能依赖 impl 中的实现类
        // 排除 DAO impl（api 层定义 DAO 接口，impl 实现它们）
        ArchRule rule = noClasses().that()
            .resideInAPackage("com.chinacreator.gzcm.sysman..api..")
            .and().resideOutsideOfPackage("..dao..")
            .should().dependOnClassesThat()
            .resideInAPackage("..impl..");
        rule.check(allClasses);
    }

    @Test
    void serviceInterfacesShouldStartWithI() {
        ArchRule rule = classes().that()
            .resideInAPackage("..service..")
            .and().areInterfaces()
            .should().haveSimpleNameStartingWith("I");
        rule.check(allClasses);
    }

    @Test
    void daoInterfacesShouldEndWithDao() {
        ArchRule rule = classes().that()
            .resideInAPackage("..dao..")
            .and().areInterfaces()
            .should().haveSimpleNameEndingWith("Dao");
        rule.check(allClasses);
    }

    @Test
    void daoImplementationsShouldBeInDaoImplPackage() {
        ArchRule rule = classes().that()
            .haveSimpleNameEndingWith("DaoImpl")
            .should().resideInAPackage("..dao.impl..");
        rule.check(allClasses);
    }

    @Test
    void serviceImplementationsShouldBeInServiceImplPackage() {
        ArchRule rule = classes().that()
            .haveSimpleNameEndingWith("ServiceImpl")
            .should().resideInAPackage("..service.impl..");
        rule.check(allClasses);
    }

    @Test
    void entityClassesShouldNotDependOnServiceLayer() {
        ArchRule rule = noClasses().that()
            .resideInAnyPackage("..bean..", "..entity..", "..model..")
            .should().dependOnClassesThat()
            .resideInAPackage("..service..");
        rule.check(allClasses);
    }

    // @Autowired field injection check skipped — ArchUnit JavaField.Predicates 
    // has ambiguous resolution with JavaMember/HasType in v1.2.1.
    // Enable after ArchUnit upgrade or with explicit full qualification.

    @Test
    void abacPackageStructure() {
        // ABAC 相关类必须在 abac 包下
        ArchRule rule = classes().that()
            .haveSimpleNameContaining("Abac")
            .should().resideInAPackage("..abac..");
        rule.check(allClasses);
    }
}
