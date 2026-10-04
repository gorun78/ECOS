package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.controller.FunctionController;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyFunctionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionResult;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionSandboxEngine;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;
import com.chinacreator.gzcm.engine.ontology.gate.AuditContextGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-06 W80/C63 (M0 P0)：Function 审计实参错位修复（O-13，PRD-03 §4.1-3）。
 *
 * <p>老实参错位：{@code FunctionController} 所有 {@code writeAudit} 调用点第一参
 * （{@code function_name}）传 {@code null}（/test）或 {@code propertyId}（/execute）——
 * 前者使 DB 列 {@code function_name} 恒空，后者把"主体 ID"误存为"函数名"，两条
 * 均令审计 attribution 无效（不知道跑的是哪个函数）。
 *
 * <p>修复契约：
 * <ul>
 *   <li>{@link FunctionValidator#extractFunctionName(String)} 从表达式提取首个
 *       白名单函数名（大写），跳过 SQL 关键字；表达式无函数调用时返回 null；</li>
 *   <li>{@code FunctionController /test}/{@code /{propertyId}/execute} 在
 *       每次 {@code writeAudit} 前派生 {@code funcName}，第一参传 {@code funcName}
 *       （不再传 {@code null}/{@code propertyId}）；</li>
 *   <li>{@code caller_id} 保持原有取值（{@code callerId}；/execute 路径
 *       {@code "api_execute_" + propertyId}）——<b>不</b>参与函数名归属反查，
 *       避免 W81 {@code AuditContextGuard} 语义被稀释。</li>
 * </ul>
 *
 * <p>V171.1 迁移实跑（回填历史行的 {@code function_name}）与 Kafka probe 属
 * 授权闸（未跑），本测试只守护 <b>调用侧</b> 契约（新落值语义 + 无回归）。
 *
 * <p>纯 Mockito + MDC 内设 traceId，不触 Spring/DB/网络。
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditAttributionTest {

    @Mock FunctionValidator validator;
    @Mock FunctionSandboxEngine engine;
    @Mock FunctionCacheManager cacheManager;

    private AuditContextGuard guard;
    private FunctionController controller;

    @BeforeEach
    void setUp() {
        guard = new AuditContextGuard();
        controller = new FunctionController(validator, engine, cacheManager, guard);
    }

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
    }

    // ── extractFunctionName 抽取函数（静态工具，直接调，不需 Spring）──

    @Test
    @DisplayName("extractFunctionName：SUM(x) FROM emp → SUM；MIN/MAX/大写不敏感")
    void extractFirstFunctionNameFromExpression() {
        assertEquals("SUM", FunctionValidator.extractFunctionName("SUM(x) FROM emp"));
        assertEquals("AVG", FunctionValidator.extractFunctionName("AVG(salary) FROM emp"));
        assertEquals("COALESCE", FunctionValidator.extractFunctionName("coalesce(a, b) FROM t"));
        assertEquals("CONCAT", FunctionValidator.extractFunctionName("CONCAT(first_name, last_name) FROM emp"));
    }

    @Test
    @DisplayName("extractFunctionName：跳过 SQL 关键字（FROM/SELECT/WHERE 不算函数）")
    void extractSkipsSqlKeywords() {
        // FROM(...) — SQL 关键字，不是函数；断言返回下一个真函数或 null
        assertNull(FunctionValidator.extractFunctionName("SELECT 1 FROM emp WHERE active"));
        // WHERE 表达式 filter 里再带函数 → 应抽到 COALESCE
        assertEquals("COALESCE", FunctionValidator.extractFunctionName(
                "SELECT COALESCE(name, 'x') FROM emp WHERE id = 1"));
    }

    @Test
    @DisplayName("extractFunctionName：无函数调用表达式（null / 空 / 纯引用）→ null，不误报")
    void extractReturnsNullWhenNoFunctionCall() {
        assertNull(FunctionValidator.extractFunctionName(null));
        assertNull(FunctionValidator.extractFunctionName(""));
        assertNull(FunctionValidator.extractFunctionName("  "));
        assertNull(FunctionValidator.extractFunctionName("col_a FROM emp"));
    }

    // ── Controller /test：writeAudit 首参不再传 null，而传 extractFunctionName 结果 ──

    @Test
    @DisplayName("/test：合规表达式白线 → writeAudit 首参 = SUM（函数名），caller_id = 原 callerId")
    void testSuccessWritesFunctionNameNotNullOrPropertyId() {
        org.slf4j.MDC.put("traceId", "trace-attr-test");
        when(validator.quickScan(anyString())).thenReturn(null);
        when(validator.validate(anyString())).thenReturn(Map.of("valid", true));
        when(cacheManager.get(anyString(), anyString())).thenReturn(null);
        FunctionResult result = new FunctionResult();
        result.setValue(42);
        result.setExecutionTimeMs(3L);
        when(engine.test(anyString(), anyString(), anyString())).thenReturn(result);

        OntologyFunctionSaveDTO req = new OntologyFunctionSaveDTO();
        req.setExpression("SUM(amount) FROM orders");
        req.setEntityName("orders");
        req.setCallerId("pmo-user-1");

        ApiResponse<FunctionResult> resp = controller.test(req);
        assertEquals(0, resp.getCode());

        // 焦点：writeAudit 首参 = "SUM"（非 null/propertyId），caller_id = "pmo-user-1"
        verify(cacheManager).writeAudit(
                eq("SUM"),
                eq("SUM(amount) FROM orders"),
                eq("orders"),
                eq("42"),
                eq(3L),
                eq("pmo-user-1"),
                eq("SUCCESS"),
                isNull());
    }

    @Test
    @DisplayName("/test：validator 白名单失败 → writeAudit 首参 = 反查函数名 + status=FORBIDDEN")
    void testWhitelistFailStillWritesFunctionName() {
        org.slf4j.MDC.put("traceId", "trace-attr-fail");
        when(validator.quickScan(anyString())).thenReturn(null);
        // validate 判不合规（举例：RLS 类似注入子串快速拦截；此处只关心 writeAudit 首参非 null）
        when(validator.validate(anyString())).thenReturn(
                Map.of("valid", false, "errors", java.util.List.of("cannot: evaluate('...')")));

        OntologyFunctionSaveDTO req = new OntologyFunctionSaveDTO();
        req.setExpression("evaluate('DROP') FROM emp");  // evaluate 不在白名单
        req.setEntityName("emp");
        req.setCallerId("pmo-user-2");

        controller.test(req);

        // evaluate( 与白名单白路无关——extractFunctionName 会跳过非白名单 token（其实 extractFunctionName
        // 不做白名单过滤，只做"首个 NAME(" 形态匹配）；这里匹配 "EVALUATE"
        verify(cacheManager).writeAudit(
                eq("EVALUATE"),
                eq("evaluate('DROP') FROM emp"),
                eq("emp"),
                isNull(),
                eq(0L),
                eq("pmo-user-2"),
                eq("FORBIDDEN"),
                anyString());
    }

    @Test
    @DisplayName("/test：安全扫描 quickScan 命中（句号包 DROP）→ 400 & writeAudit 首参=反查函数或 null")
    void testForbiddenQuickScanStillAuditWritesFunctionNameOrEmpty() {
        org.slf4j.MDC.put("traceId", "trace-attr-forbidden");
        // quickScan 命中 SQL 注入模式 "DROP"
        when(validator.quickScan(anyString())).thenReturn("禁止的操作: ; DROP");

        OntologyFunctionSaveDTO req = new OntologyFunctionSaveDTO();
        // 表达式里带 SUM 便于观察 funcName — 但 quickScan 直接短路，不会打白名单路径
        req.setExpression("SUM(x) FROM t; DROP TABLE emp;--");
        req.setEntityName("t");
        req.setCallerId("pmo-user-3");

        ApiResponse<?> resp = controller.test(req);
        // quickScan 命中 → controller 走 badRequest 快速拒
        assertTrue(resp.getCode() == 400 || resp.getData() == null);

        // writeAudit 应带反查函数名 "SUM"（第一个 NAME( 命中即归）
        verify(cacheManager).writeAudit(
                eq("SUM"),
                eq("SUM(x) FROM t; DROP TABLE emp;--"),
                eq("t"),
                isNull(),
                eq(0L),
                eq("pmo-user-3"),
                eq("FORBIDDEN"),
                anyString());
    }

    // ── Controller /{propertyId}/execute：writeAudit 首参=反查函数名（不再传 propertyId）──

    @Test
    @DisplayName("/execute：合规白线 → writeAudit 首参 = AVG（非 propertyId），caller_id = api_execute_<propertyId>")
    void executeSuccessWritesFunctionNameNotPropertyId() {
        org.slf4j.MDC.put("traceId", "trace-attr-exec");
        when(validator.quickScan(anyString())).thenReturn(null);
        when(validator.validate(anyString())).thenReturn(Map.of("valid", true));
        when(cacheManager.get(anyString(), anyString())).thenReturn(null);
        FunctionResult result = new FunctionResult();
        result.setValue(7);
        result.setExecutionTimeMs(2L);
        when(engine.test(anyString(), anyString(), anyString())).thenReturn(result);

        ApiResponse<FunctionResult> resp = controller.execute("prop-42", "AVG(salary) FROM emp", "emp");
        assertEquals(0, resp.getCode(), "白线应正常执行");
        assertNotNull(resp.getData());

        // O-13 修复：writeAudit 首参 = "AVG"（函数名），而非 "prop-42"（propertyId）
        verify(cacheManager).writeAudit(
                eq("AVG"),
                eq("AVG(salary) FROM emp"),
                eq("emp"),
                eq("7"),
                eq(2L),
                eq("api_execute_prop-42"),
                eq("SUCCESS"),
                isNull());
    }

    @Test
    @DisplayName("/execute：异常路径 → writeAudit 首参 也已更正为函数名（ERROR 也 attribution）")
    void executeErrorCodePathStillWritesFunctionName() {
        org.slf4j.MDC.put("traceId", "trace-attr-exec-err");
        when(validator.quickScan(anyString())).thenReturn(null);
        when(validator.validate(anyString())).thenReturn(Map.of("valid", true));
        when(cacheManager.get(anyString(), anyString())).thenReturn(null);
        when(engine.test(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("engine boom"));

        controller.execute("prop-99", "MIN(price) FROM item", "item");

        verify(cacheManager).writeAudit(
                eq("MIN"),
                eq("MIN(price) FROM item"),
                eq("item"),
                isNull(),
                eq(0L),
                eq("api_execute_prop-99"),
                eq("ERROR"),
                eq("engine boom"));
    }

    // ── 白线：execute 无函数调用（纯 col 表达式）→ writeAudit 首参 = null（明确无函数调用，不再误传 propertyId）──

    @Test
    @DisplayName("/execute：表达式无函数调用 token → writeAudit 首参 = null（不再误传 propertyId）")
    void executeNoFunctionRefWritesNullNotPropertyId() {
        org.slf4j.MDC.put("traceId", "trace-attr-noref");
        when(validator.quickScan(anyString())).thenReturn(null);
        when(validator.validate(anyString())).thenReturn(Map.of("valid", true));
        when(cacheManager.get(anyString(), anyString())).thenReturn(null);
        FunctionResult result = new FunctionResult();
        result.setValue("OK");
        result.setExecutionTimeMs(1L);
        when(engine.test(anyString(), anyString(), anyString())).thenReturn(result);

        controller.execute("prop-77", "col_a FROM emp", "emp");

        // 无 NAME( pattern 命中 → funcName = null（不再是 "prop-77"）
        verify(cacheManager).writeAudit(
                isNull(),
                eq("col_a FROM emp"),
                eq("emp"),
                eq("OK"),
                eq(1L),
                eq("api_execute_prop-77"),
                eq("SUCCESS"),
                isNull());
    }
}
