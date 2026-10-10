package com.chinacreator.gzcm.engine.ai.wagent.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema 归属守卫（详细设计-10 F10-03 / ST07 五引擎 schema 枚举；验收名
 * {@code agentControlTablesOnlyInFiveEngineSchemas}）。
 *
 * <p>R-33① 裁定：W 编排域 16 张控制表全部落 {@code ecos_ai.ecos_wagent_*}，
 * <b>不建 ecos_agent schema、不建 agt_/aim_/cog_ 前缀、不建审计表</b>（ST06 走 Kafka ecos.audit）。
 * 本守卫把「表名单源」钉在 DAO 的 {@code TABLE} 常量上：DAO 数量 = 表数量 = V227~V240 名录，
 * 且无一处越出 ecos_ai 限定形态。</p>
 */
class SchemaOwnershipArchTest {

    /** V227~V240 名录单源（16 表；新增表必须先改本名录，即"只加需登记"）。 */
    private static final List<String> WAGENT_TABLES = List.of(
            "ecos_ai.ecos_wagent_goal", "ecos_ai.ecos_wagent_mission", "ecos_ai.ecos_wagent_question",
            "ecos_ai.ecos_wagent_readiness", "ecos_ai.ecos_wagent_readiness_item",
            "ecos_ai.ecos_wagent_readiness_gap", "ecos_ai.ecos_wagent_run", "ecos_ai.ecos_wagent_run_step",
            "ecos_ai.ecos_wagent_run_event", "ecos_ai.ecos_wagent_tool_contract", "ecos_ai.ecos_wagent_skill",
            "ecos_ai.ecos_wagent_candidate", "ecos_ai.ecos_wagent_candidate_review",
            "ecos_ai.ecos_wagent_evidence", "ecos_ai.ecos_wagent_claim", "ecos_ai.ecos_wagent_claim_evidence");

    /** 他域 schema 限定形态（出现即违 ST09/§6.3 禁直连 Engine 表红线）。 */
    private static final Pattern FOREIGN_SCHEMA =
            Pattern.compile("ecos_(knowledge|ontology|cognitive|workflow|runtime|data|sys|demo)\\.[a-z_]+");

    @Test
    @DisplayName("ST07/R-33①：wagent 控制表只落 ecos_ai.ecos_wagent_*，DAO 与 V227~V240 名录一一对应")
    void agentControlTablesOnlyInFiveEngineSchemas() throws Exception {
        JavaClasses imported = new ClassFileImporter().importPackages("com.chinacreator.gzcm.engine.ai.wagent");
        Set<String> discoveredTables = new HashSet<>();
        int daoCount = 0;
        for (JavaClass javaClass : imported) {
            if (!javaClass.getPackageName().endsWith("wagent.dao")
                    || !javaClass.getSimpleName().endsWith("Dao")) {
                continue;
            }
            daoCount++;
            Class<?> dao = Class.forName(javaClass.getName());
            Field tableField = dao.getDeclaredField("TABLE");
            tableField.setAccessible(true);
            String table = (String) tableField.get(null);
            assertTrue(table.startsWith("ecos_ai.ecos_wagent_"),
                    "表名必须 schema 限定且落 ecos_ai.ecos_wagent_*（ST07/R-33①）: " + dao.getSimpleName()
                            + " TABLE=" + table);
            assertFalse(table.startsWith("ecos_agent.") || table.contains(".agt_") || table.contains(".agent_"),
                    "禁 ecos_agent schema / agt_ / agent_ 形态（R-33①）: " + table);
            discoveredTables.add(table);
        }
        assertEquals(WAGENT_TABLES.size(), daoCount,
                "DAO 数量应与 V227~V240 表数一致（每表一 DAO），实测 DAO=" + daoCount
                        + " 表=" + WAGENT_TABLES.size());
        assertEquals(new HashSet<>(WAGENT_TABLES), discoveredTables,
                "DAO 表名集合与 V227~V240 名录不一致（新增/改名需同步登记）");
    }

    @Test
    @DisplayName("F10-03 红线：wagent 生产类不得引用他域 schema 限定表名（跨 Engine 只经 REST/门面）")
    void wAgentCodeNeverNamesForeignEngineTables() throws Exception {
        JavaClasses imported = new ClassFileImporter().importPackages("com.chinacreator.gzcm.engine.ai.wagent");
        for (JavaClass javaClass : imported) {
            if (javaClass.isAnnotation() || javaClass.getName().contains(".arch.")) {
                continue;
            }
            Class<?> raw = Class.forName(javaClass.getName());
            for (Field field : raw.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (value == null) {
                    continue;
                }
                assertFalse(FOREIGN_SCHEMA.matcher(String.valueOf(value)).find(),
                        "W 域代码出现他域 schema 限定表名（§6.3 禁直连，跨 Engine 走属主 REST/门面）: "
                                + raw.getSimpleName() + "." + field.getName() + "=" + value);
            }
        }
    }
}
