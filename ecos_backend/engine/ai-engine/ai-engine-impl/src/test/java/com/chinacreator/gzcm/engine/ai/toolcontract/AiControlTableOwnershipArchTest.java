package com.chinacreator.gzcm.engine.ai.toolcontract;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 控制表 Schema 归属守卫（2026-11-23 去 wagent 化后重写；原
 * {@code wagent.arch.SchemaOwnershipArchTest}）。
 *
 * <p><b>为什么重写而不是删</b>：wagent 面删除后仍有<b>两处</b>读控制表的 DAO 留在生产面 ——
 * 工具契约（{@code ToolContractDao} → {@code ecos_ai.ecos_wagent_tool_contract}）与
 * Playbook 引用（{@code MissionDao} → {@code ecos_ai.ecos_wagent_mission}）。它们仍须遵守
 * ST07（只落 {@code ecos_ai} 限定、schema 名前缀、禁他域 schema）与 R-33① 的命名纪律。</p>
 *
 * <p>表名是数据库物理标识，<b>不随类改名而变</b> —— 本名录刻意保留 {@code ecos_wagent_*} 前缀，
 * 因为那正是 DDL 里既有的表名（V228/V236）。</p>
 */
class AiControlTableOwnershipArchTest {

    /**
     * 保留面 DAO 持有的控制表名录（数据库物理表名，禁随类改名而改）。
     *
     * <p>三张表：Mission（Playbook 引用）/ ToolContract（工具契约）/ agent_approval
     * （L3 审批凭证，V50 建 + V326 补列；表名不带 wagent 前缀是因为它建在 wagent 之前）。</p>
     */
    private static final List<String> LIVE_TABLES = List.of(
            "ecos_ai.ecos_wagent_mission",
            "ecos_ai.ecos_wagent_tool_contract",
            "ecos_ai.agent_approval");

    /** 他域 schema 限定形态（出现即违 ST09/§6.3 禁直连 Engine 表红线）。 */
    private static final Pattern FOREIGN_SCHEMA =
            Pattern.compile("ecos_(knowledge|ontology|cognitive|workflow|runtime|data|sys|demo)\\.[a-z_]+");

    /** 保留面 DAO 所在包（改名/搬家须同批改本名录，防扫描面漂移成空集假绿）。 */
    private static final List<String> DAO_PACKAGES = List.of(
            "com.chinacreator.gzcm.engine.ai.toolcontract",
            "com.chinacreator.gzcm.engine.ai.approval");

    @Test
    @DisplayName("ST07：保留面 DAO 的控制表只落 ecos_ai 限定，且与名录一一对应")
    void liveDasHoldOnlyRegisteredControlTables() throws Exception {
        Set<String> discovered = new HashSet<>();
        int daoCount = 0;
        for (String pkg : DAO_PACKAGES) {
            JavaClasses imported = new ClassFileImporter().importPackages(pkg);
            for (JavaClass javaClass : imported) {
                if (!javaClass.getSimpleName().endsWith("Dao") || javaClass.getSimpleName().contains("Test")) {
                    continue;
                }
                Class<?> dao = Class.forName(javaClass.getName());
                Field tableField;
                try {
                    tableField = dao.getDeclaredField("TABLE");
                } catch (NoSuchFieldException noTable) {
                    continue; // 非表 DAO（如无 TABLE 常量的辅助类）不参与本名录
                }
                tableField.setAccessible(true);
                String table = (String) tableField.get(null);
                daoCount++;
                assertTrue(table.startsWith("ecos_ai."),
                        "控制表必须 schema 限定 ecos_ai（ST07）: " + dao.getSimpleName() + " TABLE=" + table);
                assertFalse(table.startsWith("ecos_agent.") || table.contains(".agt_"),
                        "禁 ecos_agent schema / agt_ 形态（R-33①）: " + table);
                discovered.add(table);
            }
        }
        assertEquals(LIVE_TABLES.size(), daoCount,
                "保留面 DAO 数量与名录不符（增/删 DAO 须同批改本名录）: DAO=" + daoCount + " 名录=" + LIVE_TABLES);
        assertEquals(new HashSet<>(LIVE_TABLES), discovered,
                "DAO 表名集合与名录不一致: " + discovered);
    }

    @Test
    @DisplayName("F10-03 红线：保留面生产类不得引用他域 schema 限定表名（跨 Engine 只经 REST/门面）")
    void liveCodeNeverNamesForeignEngineTables() throws Exception {
        for (String pkg : DAO_PACKAGES) {
            JavaClasses imported = new ClassFileImporter().importPackages(pkg);
            for (JavaClass javaClass : imported) {
                if (javaClass.isAnnotation()) {
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
                            "出现他域 schema 限定表名（§6.3 禁直连，跨 Engine 走属主 REST/门面）: "
                                    + raw.getSimpleName() + "." + field.getName() + "=" + value);
                }
            }
        }
    }
}
