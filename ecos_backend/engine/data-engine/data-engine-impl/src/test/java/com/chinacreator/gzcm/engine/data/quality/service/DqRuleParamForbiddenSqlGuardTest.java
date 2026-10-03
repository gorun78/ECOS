package com.chinacreator.gzcm.engine.data.quality.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DqRuleParamForbiddenSqlGuardTest — F02-07 / D.1 ECOS-DQ-102 规则参数禁用构造护栏（离线，不触库/不 Spring）。
 *
 * <p>设计-02 行 234："规则参数<b>仅允许绑定变量，禁自由 SQL</b>"；D.1 码表行 431：
 * {@code ECOS-DQ-102 | 400 | 规则表达式含禁用构造（自由 SQL/裸表名）}。
 *
 * <p>护栏落 {@link DqRuleLifecycleServiceImpl#findForbiddenSqlConstruct} / {@code assertNoForbiddenSqlConstruct}
 * （纯静态，createDraft/updateDraft 边界已挂调用）。纯静态直调即可覆盖契约，无需 mock 依赖。
 *
 * <ul>
 *   <li>自由 SQL 动词（select/insert/update/delete/drop/alter/truncate/merge）→ 命中，断言码 + 命中动词</li>
 *   <li>camelCase 字段名（createdAt / selectedColumns / deletedFlag）不误伤</li>
 *   <li>日期区间 from/to 键、空参数、null 参数 → 不误判</li>
 *   <li>白线：码常量值 = doc 语义</li>
 * </ul>
 */
class DqRuleParamForbiddenSqlGuardTest {

    private static DqRuleDTO dtoWithParams(String json) {
        DqRuleDTO dto = new DqRuleDTO();
        dto.setParameters(json);
        return dto;
    }

    @Test
    @DisplayName("自由 SQL 注入 SELECT → 命中 select，assertNoForbiddenSqlConstruct 抛 102")
    void freeSqlSelectIsRejected() {
        DqRuleDTO dto = dtoWithParams("{\"query\":\"select * from ecos_dw.dq_rule where status='ACTIVE'\"}");
        assertEquals("select", DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dto));
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> DqRuleLifecycleServiceImpl.assertNoForbiddenSqlConstruct(dto));
        assertTrue(ex.getMessage().contains("ECOS-DQ-102"),
                "应携带 D.1 功能码 ECOS-DQ-102，实际：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("select"),
                "应把命中动词写进消息，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("自由 SQL 注入 DROP / UPDATE 动词 → 均命中（case-insensitive）")
    void freeSqlDropAndUpdateAreRejected() {
        assertEquals("drop", DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams("{\"sql\":\"DROP TABLE x\"}")));
        assertEquals("update", DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams("{\"dml\":\"UPDATE t SET a=1\"}")));
    }

    @Test
    @DisplayName("纯绑定变量参数 → 未命中（null）")
    void pureBindVariablesPass() {
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(
                dtoWithParams("{\"field\":\"amount\",\"op\":\">\",\"threshold\":100}")));
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams("{}")));
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams(null)));
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams("   ")));
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(null));
    }

    @Test
    @DisplayName("camelCase 字段名 / from-to 日期区间 → 不误伤")
    void camelCaseAndDateRangeDoNotFalsePositive() {
        // "select" 出现在 selectedColumns 中（无边界）、created/deleted 为前缀 camelCase、from/to 是日期键
        assertNull(DqRuleLifecycleServiceImpl.findForbiddenSqlConstruct(dtoWithParams(
                "{\"selectedColumns\":[\"a\"],\"createdAt\":\"2024-01-01\",\"from\":20240101,\"to\":20241231,\"deletedFlag\":false,\"updatedAt\":123}")));
        assertDoesNotThrow(() -> DqRuleLifecycleServiceImpl.assertNoForbiddenSqlConstruct(dtoWithParams(
                "{\"from\":20240101,\"to\":20241231}")));
    }

    @Test
    @DisplayName("白线：功能码常量值 = doc 语义（防空文件静默绿）")
    void c15CodeConstantWhiteLine() {
        assertEquals("ECOS-DQ-102", DqRuleLifecycleServiceImpl.CODE_DQ_RULE_EXPR_FORBIDDEN);
    }
}
