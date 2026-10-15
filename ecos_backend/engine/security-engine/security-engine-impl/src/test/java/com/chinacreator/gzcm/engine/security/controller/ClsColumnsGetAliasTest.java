package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code GET /api/v1/security/cls/columns} 兼容别名门禁（详设-01 §4.3.2 L2593，#142/B4-1 第 3 项）。
 *
 * <p>契约原文：「签名不变｜新增 {@code mode}｜<b>空集仍按 DENY 解释</b>」。因此本门禁分两侧：
 * <ol>
 *   <li><b>不动既有 POST 面</b>：{@code POST /columns} 的方法名、入参（body Map）、
 *       出参键集合保持原样——用「别名只增」的方式满足契约，既有调用方零影响；</li>
 *   <li><b>空集 = DENY 必须可机读</b>：{@code visibleColumns} 为空 ⇒ {@code mode=deny} +
 *       {@code effectiveDecision=DENY} + {@code errorCode=ECOS-SEC-402} + {@code denyIfEmpty=true}，
 *       且 HTTP 仍是 200（契约没改状态码，解释义务在消费方；把它折成 403 属于改既有行为，
 *       已登记交 #143 契约批裁决）。</li>
 * </ol>
 */
class ClsColumnsGetAliasTest {

    private final ColumnLevelSecurityServiceImpl service = mock(ColumnLevelSecurityServiceImpl.class);
    private final ClsController controller = new ClsController(service);

    @Test
    @DisplayName("非空可读列 ⇒ mode=allow、decision=ALLOW，且不带 DENY 解释位")
    void nonEmptyVisibleColumnsYieldAllowMode() {
        when(service.getColumns(eq("t_order"), anyString(), any()))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "tableName", "t_order",
                        "visibleColumns", List.of("id", "amount"),
                        "blockedColumns", List.of("id_card"),
                        "policies", List.of())));

        ApiResponse<Map<String, Object>> resp = controller.getColumnsByGet("t_order", "u-1", null);

        assertEquals(0, resp.getCode());
        Map<String, Object> data = resp.getData();
        assertEquals("allow", data.get("mode"));
        assertEquals("ALLOW", data.get("effectiveDecision"));
        assertEquals(Boolean.TRUE, data.get("denyIfEmpty"));
        assertFalse(data.containsKey("errorCode"), "允许面不得挂 ECOS-SEC-402");
        // 底座四键必须原样透传（别名不得改形状）
        assertEquals("t_order", data.get("tableName"));
        assertEquals(List.of("id", "amount"), data.get("visibleColumns"));
        assertEquals(List.of("id_card"), data.get("blockedColumns"));
    }

    @Test
    @DisplayName("空集 ⇒ mode=deny + DENY + ECOS-SEC-402 + denyIfEmpty（仍 200，只加解释不改状态码）")
    void emptyVisibleColumnsAreDenied() {
        when(service.getColumns(anyString(), anyString(), any()))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "tableName", "t_secret",
                        "visibleColumns", List.of(),
                        "blockedColumns", List.of("salary"),
                        "policies", List.of())));

        ApiResponse<Map<String, Object>> resp = controller.getColumnsByGet("t_secret", "u-9", null);

        assertEquals(0, resp.getCode(), "契约未改状态码，空集 DENY 靠 data 表达");
        Map<String, Object> data = resp.getData();
        assertEquals("deny", data.get("mode"));
        assertEquals("DENY", data.get("effectiveDecision"));
        assertEquals(ClsController.ERROR_CLS_EMPTY_DENY, data.get("errorCode"));
        assertEquals("ECOS-SEC-402", ClsController.ERROR_CLS_EMPTY_DENY);
        assertEquals(Boolean.TRUE, data.get("denyIfEmpty"));
        assertNotNull(data.get("message"));
    }

    @Test
    @DisplayName("visibleColumns 键缺失/为 null 也按 DENY（缺字段不得被读成「全允许」）")
    void missingVisibleKeyIsDeniedToo() {
        when(service.getColumns(anyString(), anyString(), any()))
                .thenReturn(new LinkedHashMap<>(Map.of("tableName", "t_x")));
        Map<String, Object> data = controller.getColumnsByGet("t_x", "u-1", null).getData();
        assertEquals("deny", data.get("mode"));
        assertEquals("DENY", data.get("effectiveDecision"));
    }

    @Test
    @DisplayName("底座异常 ⇒ internalError（fail-closed，不返回空 allow 清单）")
    void serviceFailureFailsClosed() {
        when(service.getColumns(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("policy store down"));
        ApiResponse<Map<String, Object>> resp = controller.getColumnsByGet("t_x", "u-1", null);
        assertEquals(-1, resp.getCode());
        assertNull(resp.getData(), "异常分支不得带 data（空清单会被误读成「无列可读＝全允许」）");
    }

    @Test
    @DisplayName("入参校验：tableName/userId 空 ⇒ 400，且不触底座查询")
    void blankParamsRejectedBeforeServiceCall() {
        assertEquals(400, controller.getColumnsByGet("  ", "u-1", null).getCode());
        assertEquals(400, controller.getColumnsByGet("t_order", null, null).getCode());
        verify(service, org.mockito.Mockito.never())
                .getColumns(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("形态守卫：既有 POST /columns 仍在位，GET 别名参数走 @RequestParam")
    void postSignatureUntouchedAndGetUsesQueryParams() throws Exception {
        Method post = ClsController.class.getMethod("getColumns", Map.class);
        assertNotNull(post.getAnnotation(PostMapping.class), "POST /columns 属既有面，必须原样保留");

        Method get = ClsController.class.getMethod(
                "getColumnsByGet", String.class, String.class, List.class);
        assertNotNull(get.getAnnotation(GetMapping.class));
        Parameter[] params = get.getParameters();
        assertEquals(3, params.length, "别名入参数必须与契约一致（tableName/userId/allColumns）");
        for (Parameter p : params) {
            // 只断言「全部是查询参数」：@RequestParam 未写显式 name 时 value() 为空串，
            // 参数名依赖 -parameters 编译选项，禁在此处造一个假的名字断言
            assertNotNull(p.getAnnotation(RequestParam.class),
                    "GET 别名参数必须全部来自 query（契约签名不变），实际参数=" + p.getType());
        }
    }
}
