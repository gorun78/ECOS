package com.chinacreator.gzcm.engine.ai.wagent.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据访问方式守卫（详细设计-10 §6.2 / 铁律 §2.5 #1；验收名 {@code wAgentMappersAbsent}）。
 *
 * <p>W 域数据层落点 = {@code ..wagent.dao.WAgent*Dao extends JdbcTemplateDaoSupport}
 * （AiToolRegistryDao 同款 pattern），<b>不建 MyBatis Mapper</b>：附件 {@code agt_*} 表的
 * {@code aim_} Mapper 形态已被 R-33① 收编口径否掉，混用两套 ORM 会让 SQL 单源分裂。</p>
 */
class NoDirectEngineTableAccessTest {

    @Test
    @DisplayName("§6.2：wagent 包内禁 MyBatis Mapper（数据层唯一形态 = DAO extends JdbcTemplateDaoSupport）")
    void wAgentMappersAbsent() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.chinacreator.gzcm.engine.ai.wagent");
        noClasses().should().dependOnClassesThat()
                .haveFullyQualifiedName("org.apache.ibatis.annotations.Mapper")
                .check(imported);
        noClasses().should().dependOnClassesThat()
                .resideInAPackage("org.apache.ibatis..")
                .check(imported);
        noClasses().should().dependOnClassesThat()
                .resideInAPackage("com.baomidou.mybatisplus..")
                .check(imported);

        List<String> offenders = new ArrayList<>();
        for (JavaClass javaClass : imported) {
            if (javaClass.getSimpleName().endsWith("Test")) {
                continue;
            }
            if (javaClass.getSimpleName().endsWith("Mapper")) {
                offenders.add(javaClass.getName());
            }
        }
        assertTrue(offenders.isEmpty(), "W 域出现 *Mapper 形态（禁与 DAO 双轨）: " + offenders);
    }

    @Test
    @DisplayName("§6.2：wagent DAO 一律继承 JdbcTemplateDaoSupport（禁自持 JdbcTemplate、禁新建第二套基类）")
    void wAgentDaosUseTheSingleDaoSupport() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.chinacreator.gzcm.engine.ai.wagent");
        List<String> daos = new ArrayList<>();
        List<String> extraBases = new ArrayList<>();
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
                        javaClass.isAssignableTo("com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport"),
                        "DAO 必须 extends JdbcTemplateDaoSupport（铁律 §2.5 #1 唯一基类）: " + javaClass.getName());
            }
        }
        assertTrue(daos.size() >= 16, "wagent DAO 应覆盖 16 表，实测 " + daos.size() + " -> " + daos);
        assertTrue(extraBases.isEmpty(), "禁在 wagent 包内新建第二套 DAO 基类: " + extraBases);
    }
}
