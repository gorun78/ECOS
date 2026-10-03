package com.chinacreator.gzcm.engine.security.service.predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("W30 RLS 模板白名单校验器")
class RlsTemplateValidatorTest {

    @Test
    @DisplayName("合法参数化模板：department_id = :userDeptId + CONTEXT_ORG 绑定 → 通过")
    void validParameterizedTemplate_passes() {
        List<PredicateBinding> bindings = List.of(
                new PredicateBinding("userDeptId", "CONTEXT_ORG", null, null));
        assertDoesNotThrow(() ->
                RlsPredicateValidator.validateTemplate("department_id = :userDeptId", bindings));
    }

    @Test
    @DisplayName("未声明绑定变量 → 异常")
    void undeclaredBinding_throws() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate("department_id = :userDeptId", List.of()));
    }

    @Test
    @DisplayName("全量行谓词 1=1 → 通过（无绑定）")
    void allRowsPredicate_passes() {
        assertDoesNotThrow(() ->
                RlsPredicateValidator.validateTemplate("1=1", List.of()));
    }

    @Test
    @DisplayName("存量兼容数值条件 clearance_level >= 4 → 通过")
    void legacyNumericComparison_passes() {
        assertDoesNotThrow(() ->
                RlsPredicateValidator.validateTemplate("clearance_level >= 4", List.of()));
    }

    @Test
    @DisplayName("模板含 UNION SELECT SQL 注入关键字 → 异常")
    void sqlInjectionUnionSelect_rejected() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate("union select * from users", List.of()));
    }

    @Test
    @DisplayName("模板含引号字符串字面量 → 异常")
    void quotedStringLiteral_rejected() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate("department_id = 'dept1'", List.of()));
    }

    @Test
    @DisplayName("空模板 / null 模板 → 异常")
    void emptyOrBlankTemplate_rejected() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate(null, List.of()));
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate("", List.of()));
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate("   ", List.of()));
    }

    @Test
    @DisplayName("STATIC_LIST 合法 3 项 → 通过")
    void staticListWithinLimit_passes() {
        assertDoesNotThrow(() ->
                RlsPredicateValidator.validateStaticList("STATIC_LIST",
                        List.of("v1", "v2", "v3")));
    }

    @Test
    @DisplayName("STATIC_LIST 空列表 → 异常")
    void staticListEmpty_rejected() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateStaticList("STATIC_LIST", List.of()));
    }

    @Test
    @DisplayName("placeholdersOf 提取 :name 占位符（去重保序）")
    void placeholdersOf_extractsBindingNames() {
        List<String> phs = RlsPredicateValidator.placeholdersOf("a = :x AND b = :y AND c = :x");
        assertEquals(List.of("x", "y"), phs);
    }

    @Test
    @DisplayName("多绑定源参数化 clearance_level >= :userClearance → 通过")
    void multiSourceTemplate_passes() {
        List<PredicateBinding> bs = List.of(
                new PredicateBinding("userClearance", "CONTEXT_CLEARANCE_LEVEL", null, null));
        assertDoesNotThrow(() ->
                RlsPredicateValidator.validateTemplate("clearance_level >= :userClearance", bs));
    }

    @Test
    @DisplayName("CONCAT 拼接函数 → 异常（禁拼接语义）")
    void concatFunction_rejected() {
        assertThrows(RlsPredicateValidator.IllegalPredicateTemplateException.class, () ->
                RlsPredicateValidator.validateTemplate(
                        "col = CONCAT(a, :x)",
                        List.of(new PredicateBinding("x", "CONTEXT_ORG", null, null))));
    }
}
