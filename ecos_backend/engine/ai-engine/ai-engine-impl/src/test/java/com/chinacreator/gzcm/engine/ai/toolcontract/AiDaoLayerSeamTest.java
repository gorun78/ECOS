package com.chinacreator.gzcm.engine.ai.toolcontract;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据层接缝守卫（2026-11-23 去 wagent 化后重写；原
 * {@code wagent.arch.NoDirectEngineTableAccessTest}）。
 *
 * <p>保留面（工具契约 + L3 审批）仍持有 DAO，须继续守：① 唯一基类
 * {@code JdbcTemplateDaoSupport}；② 禁 MyBatis 双轨；③ 禁自建第二套 DAO 基类。</p>
 */
class AiDaoLayerSeamTest {

    private static final List<String> LIVE_PACKAGES = List.of(
            "com.chinacreator.gzcm.engine.ai.toolcontract",
            "com.chinacreator.gzcm.engine.ai.approval");

    @Test
    @DisplayName("§6.2：保留面禁 MyBatis（数据层唯一形态 = DAO extends JdbcTemplateDaoSupport）")
    void livePackagesForbidMyBatis() {
        for (String pkg : LIVE_PACKAGES) {
            JavaClasses imported = new ClassFileImporter().importPackages(pkg);
            noClasses().should().dependOnClassesThat()
                    .resideInAPackage("org.apache.ibatis..")
                    .check(imported);
            noClasses().should().dependOnClassesThat()
                    .resideInAPackage("com.baomidou.mybatisplus..")
                    .check(imported);
            List<String> mappers = new ArrayList<>();
            for (JavaClass javaClass : imported) {
                if (javaClass.getSimpleName().endsWith("Test")) {
                    continue;
                }
                if (javaClass.getSimpleName().endsWith("Mapper")) {
                    mappers.add(javaClass.getName());
                }
            }
            assertTrue(mappers.isEmpty(), "出现 *Mapper 形态（禁与 DAO 双轨）: " + mappers);
        }
    }

    @Test
    @DisplayName("§6.2：保留面 DAO 一律继承 JdbcTemplateDaoSupport（禁自持 JdbcTemplate、禁第二套基类）")
    void liveDaosUseTheSingleDaoSupport() {
        List<String> daos = new ArrayList<>();
        List<String> extraBases = new ArrayList<>();
        for (String pkg : LIVE_PACKAGES) {
            JavaClasses imported = new ClassFileImporter().importPackages(pkg);
            for (JavaClass javaClass : imported) {
                if (javaClass.getSimpleName().endsWith("Test")) {
                    continue;
                }
                if (javaClass.getSimpleName().endsWith("DaoSupport")) {
                    extraBases.add(javaClass.getName());
                    continue;
                }
                if (javaClass.getSimpleName().endsWith("Dao")) {
                    daos.add(javaClass.getSimpleName());
                    assertTrue(
                            javaClass.isAssignableTo(
                                    "com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport"),
                            "DAO 必须 extends JdbcTemplateDaoSupport（铁律 §2.5 #1 唯一基类）: "
                                    + javaClass.getName());
                }
            }
        }
        // 保留面 DAO：L3ApprovalDao / ToolContractDao / MissionDao（删/增须同批改本判据）
        assertFalse(daos.isEmpty(), "扫描面失效：保留面应至少有 1 个 DAO");
        assertTrue(daos.size() >= 3, "保留面 DAO 数量低于预期（扫描根漂移？）: " + daos);
        assertTrue(extraBases.isEmpty(), "禁新建第二套 DAO 基类: " + extraBases);
    }
}
