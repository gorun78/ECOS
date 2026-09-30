package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.rls.PredicateBinding;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator.TemplateRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 详细设计-01 W30 / F01-01 — {@link RlsPredicateValidator} 白名单校验器。
 * 非法谓词模板（SQL 关键字 / 拼接语义 / 未绑定占位符 / 非白名单 source / 超限 STATIC_LIST）
 * 必须被拒绝；合法模板放行。
 */
class RlsTemplateValidatorTest {

    private final RlsPredicateValidator validator = new RlsPredicateValidator();

    @Test
    @DisplayName("1. 合法参数化模板 + CONTEXT source → 通过")
    void validTemplatePasses() {
        assertDoesNotThrow(() -> validator.validate(
                "department_id = :userDeptId",
                List.of(new PredicateBinding("userDeptId", PredicateBinding.SRC_CONTEXT_ORG, null))));
    }

    @Test
    @DisplayName("2. UNION 关键字 → 拒绝")
    void unionRejected() {
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("id = 1 OR 1=1 UNION SELECT * FROM td_user", List.of()));
    }

    @Test
    @DisplayName("3. 分号 / -- 注释 → 拒绝")
    void semicolonAndCommentRejected() {
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("id = 1; DROP TABLE x", List.of()));
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("id = 1 -- comment", List.of()));
    }

    @Test
    @DisplayName("4. CONCAT 拼接语义 → 拒绝")
    void concatRejected() {
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("name = CONCAT(:a, :b)",
                        List.of(new PredicateBinding("a", PredicateBinding.SRC_STATIC_LIST, "x"),
                                new PredicateBinding("b", PredicateBinding.SRC_STATIC_LIST, "y"))));
    }

    @Test
    @DisplayName("5. 占位符无对应绑定 → 拒绝")
    void unboundPlaceholderRejected() {
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("tenant_id = :userTenantId", List.of()));
    }

    @Test
    @DisplayName("6. 非白名单 source → 拒绝")
    void unknownSourceRejected() {
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("x = :a",
                        List.of(new PredicateBinding("a", "SOME_RANDOM", "v"))));
    }

    @Test
    @DisplayName("7. STATIC_LIST 超限(>50) → 拒绝")
    void staticListOverLimitRejected() {
        java.util.List<Object> big = new java.util.ArrayList<>();
        for (int i = 0; i < 51; i++) big.add("v" + i);
        assertThrows(TemplateRejectedException.class, () ->
                validator.validate("id IN (:ids)",
                        List.of(new PredicateBinding("ids", PredicateBinding.SRC_STATIC_LIST, big))));
    }

    @Test
    @DisplayName("8. REST body 形态校验（predicateTemplate + bindings[]）")
    void bodyFormValidation() {
        Map<String, Object> good = Map.of(
                "predicateTemplate", "department_id = :userDeptId",
                "bindings", List.of(Map.of("name", "userDeptId", "source", "CONTEXT_ORG")));
        assertDoesNotThrow(() -> validator.validateTemplate(good));

        Map<String, Object> bad = Map.of(
                "predicateTemplate", "1=1; DELETE FROM t",
                "bindings", List.of());
        assertThrows(TemplateRejectedException.class, () -> validator.validateTemplate(bad));
    }
}
