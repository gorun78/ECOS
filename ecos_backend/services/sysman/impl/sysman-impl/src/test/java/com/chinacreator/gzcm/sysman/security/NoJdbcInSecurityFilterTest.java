package com.chinacreator.gzcm.sysman.security;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W04（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §W04 行 / 铁律 §2.4-7，M1）
 * — 认证/准入组件禁持 JdbcTemplate 裸查安全策略表；裁决全部走 security-engine REST 门面（01 册 D 章）。
 *
 * <p><b>当前状态：豁免保留（@Disabled）。</b>设计文档 W04 排期 M1，现状
 * {@link JwtAuthenticationFilter} / {@link ClearanceInterceptor} 仍持 {@code JdbcTemplate}
 * （黑名单 COUNT / clearance 级联 / 租户回查）。规则预期"包内 Filter/Interceptor 类
 * 不再 import {@code org.springframework.jdbc.core.JdbcTemplate}"，W04 切完 REST 门面后
 * 去掉 @Disabled 即成门禁（新增回退 JDBC 直查 = FAIL）。</p>
 *
 * <p>放 sysman-impl（而非 gateway）：两个组件类都在 {@code sysman-impl} 的
 * {@code com.chinacreator.gzcm.sysman.security} 包；gateway 仅聚合加载。</p>
 */
class NoJdbcInSecurityFilterTest {

    /**
     * W04 未关闭前 susceptibility：规则预期 JwtAuthenticationFilter/ClearanceInterceptor
     * 使用 JdbcTemplate；等 W04 切完 security-engine REST 门面后再启用。
     */
    @Disabled("W04 未关闭前 susceptibility：这规则预期 JwtAuthenticationFilter/ClearanceInterceptor 使用 JdbcTemplate；等 W04 切完 security-engine REST 门面后再启用")
    @Test
    void noJdbcTemplateImportsInSecurityPackage() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.sysman.security");

        ArchRule rule = noClasses()
                .that().resideInAPackage("com.chinacreator.gzcm.sysman.security..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate");

        // W04 关闭前后语义：当前会 FAIL → @Disabled；启用后此断言即门禁
        rule.check(classes);
        assertTrue(true, "占位：规则通过");
    }

    /**
     * 反向探针（非门禁，常驻绿）：规则本身可执行、且当前确实存在 JdbcTemplate 依赖
     * —— 防止规则写错（如包名拼写错导致 no-op 恒绿）。W04 关闭后此用例应翻转为红/改断言。
     */
    @Test
    void probe_securityPackageCurrentlyStillDependsOnJdbcTemplate() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.sysman.security");

        boolean dependsOnJdbc = classes.stream()
                .anyMatch(c -> c.getDirectDependenciesFromSelf().stream()
                        .anyMatch(d -> d.toString().contains("org.springframework.jdbc.core.JdbcTemplate")));
        assertTrue(dependsOnJdbc,
                "探针：当前 JwtAuthenticationFilter/ClearanceInterceptor 应仍依赖 JdbcTemplate（W04 未关闭基线）；"
                        + "若此失败说明 W04 已切换，需移除 @Disabled 启用门禁用例");
    }
}
