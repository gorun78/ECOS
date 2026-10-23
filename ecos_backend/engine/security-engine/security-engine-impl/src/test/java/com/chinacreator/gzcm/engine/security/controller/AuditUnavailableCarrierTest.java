package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.error.SystemErrorCode;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.engine.security.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.audit.model.AuditEvent;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * #142/B4-3e-56 —— 收口差距 <b>D-B4-86</b>：security 属主 {@code AuditController} 的
 * 「依赖未装配」腿禁双向折叠（不可用 ≠ 无权限 ≠ 空结果，详设 §4.7.2 ＋ 铁律 §2.4）。
 *
 * <h3>差距形态（修法前，逐枚勘察＝本类 6 枚腿／四种折叠）</h3>
 * <ul>
 *   <li>{@code /logs} 未装配 ⇒ {@code 200＋空集}（把「查不了」谎报成「库里没数据」）；</li>
 *   <li>{@code /stats} 未装配 ⇒ {@code 200＋全 0 计数}（前端统计卡片渲染成「今日 0 条审计」）；</li>
 *   <li>{@code /verify-integrity} 与 {@code /verify-chain} 未装配 ⇒ {@code 200＋valid=false}
 *       （把「链校验跑不了」谎报成「链被篡改」——审计完整性面上的反向假信号）；</li>
 *   <li>{@code /logs/{id}} 与 {@code /log} 未装配 ⇒ {@code internalError("审计服务未就绪")}
 *       ＝{@code code=-1}、状态行仍恒 HTTP 200（把可预期的缺装配谎报成本服务自身故障）。</li>
 * </ul>
 * <p>登记口径订正：上批（#142/B4-3e-53）按「信封折叠字面形态」判据只记到 1 枚（即
 * {@code internalError} 那两枚中的写法面），本批属主侧逐枚勘察＝<b>1 文件 6 枚腿</b>。
 * 成因与前例同名：按判据字面而非按腿形登记，故「200＋空集／全 0／valid=false」三型不在其内；
 * 判据扩展在册余项 D-B4-72，本门把该面的读数钉死为 0 枚复现即可。</p>
 *
 * <h3>本门钉住的口径（沿用 3e-40~43／3e-52／3e-53／3e-55 在册承载族，不另立第三形态）</h3>
 * <p>未装配腿统一抛 {@link DataBridgeException}(httpStatus=503, errorCode=503, 消息首段＝
 * {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE})，行码同取
 * {@link RlsExecutionContract} 单源常量（与本模块 {@code DecisionController}、data-engine
 * {@code QueryController} 的不可用腿同源），由双宿主 gateway／sysman-boot
 * {@code GlobalExceptionHandler} 渲染状态行；端点自身保留
 * {@code catch (DataBridgeException e) { throw e; }} 显式承载腿，禁被 {@code catch (Exception)}
 * 兜底折回 500。<b>端点签名一字不动</b>（不触「既有签名不可变」红线）。真实空结果不受影响：
 * 服务在位且查到零条仍回 200＋空集，由第 5~6 案的正向对照钉住。</p>
 *
 * <h3>能变红</h3>
 * <p>生产码退回缺陷形态时：第 1 案红（返回信封而非抛出）、第 2 案红（承载腿被兜底 catch 吃掉）、
 * 第 3 案红（真实服务故障也被折成不可用）、第 4 案红（单源常量被换成字面量）、
 * 第 5~6 案红（正向对照被 fail-closed 取代 ⇒ 真空结果也变 503）、
 * 第 7 案红（源码面重新出现折叠字面形态／「未就绪」字样／守卫与承载腿数跌破逐枚计数）。
 * 全部读数来自本模块实跑，不以 -DskipTests 宣称。</p>
 */
class AuditUnavailableCarrierTest {

    private static final String AUDIT_CTRL =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/security/controller/AuditController.java";

    /** 未装配守卫（取实例）与显式承载腿的逐枚应有数——少一枚即说明该腿又折回兜底。 */
    private static final int AUDIT_GUARDS = 6;
    private static final int AUDIT_RETHROWS = 6;

    /**
     * 缺陷形态的字面特征（修法前实读），复现即红。
     * <p>守卫只认「{@code == null} 直接 return 信封」这一折叠腿形；入参校验的
     * {@code if (x == null) return ApiResponse.badRequest(...)} 属正常语义面，不在判据内。</p>
     */
    private static final List<String> FOLD_GUARD_ONE_LINERS = List.of(
            "== null) return ApiResponse.internalError",
            "== null) return ApiResponse.success");

    /** 四种折叠载荷的字面特征（逐型一条，缺一型即该型重新失明）。 */
    private static final String FOLD_EMPTY_PAGE = "empty.put(\"total\", 0)";
    private static final String FOLD_ZERO_STATS = "result.put(\"todayCount\", 0)";
    private static final String FOLD_INVALID_CHAIN = "m.put(\"valid\", false)";
    private static final String NOT_READY_MESSAGE = "未就绪";

    // ── 1. 六枚未装配腿：一律抛显式不可用 ─────────────────────

    @Test
    @Timeout(120)
    @DisplayName("六枚腿：审计依赖未装配一律抛 503＋ECOS-SEC-501，禁回 200＋空集/全 0/valid=false，也禁折 500")
    void everyUnassembledLegThrowsCarriedUnavailable() {
        AuditController down = new AuditController(absentProvider(), absentProvider());

        assertCarrier(() -> down.list(null, null, null, null, null, 1, 20), "list(/logs)");
        assertCarrier(() -> down.get("a-1"), "get(/logs/{id})");
        assertCarrier(down::stats, "stats(/stats)");
        assertCarrier(down::verifyIntegrity, "verifyIntegrity(/verify-integrity)");
        assertCarrier(() -> down.verifyChain(Map.of("from", 1, "to", 9)), "verifyChain(/verify-chain)");
        assertCarrier(() -> down.writeLog(Map.of("userId", "u-1", "action", "login"), null),
                "writeLog(/log)");
    }

    // ── 2. 端点显式承载腿：不得被兜底 catch 折回 500 ──────────

    @Test
    @Timeout(120)
    @DisplayName("服务在位但抛承载型拒绝（403/503）时，六枚端点必须原样上抛，禁折 internalError")
    void endpointsRethrowCarriedDenialInsteadOfCollapsingTo500() {
        IAuditLogService audit = mock(IAuditLogService.class);
        // 重桩必须走 doThrow 形态：when(audit.query(any())) 会先把已装的 503 桩触发一次，
        // 异常从打桩语句本身抛出（用例假失败），而不是从被测端点抛出
        org.mockito.Mockito.doThrow(new DataBridgeException(503, 503,
                        RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": RLS 裁决内部失败"))
                .when(audit).query(any());
        assertCarrier(() -> new AuditController(availableProvider(audit), absentProvider())
                .list(null, null, null, null, null, 1, 20), "list 透传上游不可用");
        assertCarrier(() -> new AuditController(availableProvider(audit), absentProvider()).stats(),
                "stats 透传上游不可用");

        org.mockito.Mockito.doThrow(new DataBridgeException(403, 403,
                        SystemErrorCode.ECOS_SYS_403 + ": 无审计读取权限"))
                .when(audit).query(any());
        assertCarrier(() -> new AuditController(availableProvider(audit), absentProvider())
                .list("u-1", null, null, null, null, 1, 20), 403, "list 透传 403 拒绝");

        AuditHashChainService chain = mock(AuditHashChainService.class);
        when(chain.verifyHashChain()).thenThrow(new DataBridgeException(403, 403,
                SystemErrorCode.ECOS_SYS_403 + ": 无链校验权限"));
        assertCarrier(() -> new AuditController(absentProvider(), availableProvider(chain))
                .verifyIntegrity(), 403, "verifyIntegrity 透传 403 拒绝");
    }

    // ── 3. 真实服务故障与「依赖不在」必须分态 ─────────────────

    @Test
    @Timeout(120)
    @DisplayName("分态对照：服务在位但 DAO 抛非承载型异常＝仍回本服务 500 信封，禁冒充 503 不可用")
    void realServiceFailureStaysInternalErrorNotUnavailable() {
        IAuditLogService audit = mock(IAuditLogService.class);
        when(audit.query(any())).thenThrow(new RuntimeException("jdbc:postgresql://10.0.0.7 down"));

        AuditController controller = new AuditController(availableProvider(audit), absentProvider());
        ApiResponse<Map<String, Object>> resp = controller.list(null, null, null, null, null, 1, 20);
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, resp.getCode(),
                "真实故障与缺装配是两个态：前者留在本服务信封面，不得被统一折成 503");
        assertFalse(resp.getMessage().contains("jdbc"), "异常细节不回显（N-25 面不动）");

        AuditHashChainService chain = mock(AuditHashChainService.class);
        when(chain.verifyHashChainRange(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("index out of range"));
        ApiResponse<Map<String, Object>> chainResp = new AuditController(absentProvider(),
                availableProvider(chain)).verifyChain(Map.of("from", 1, "to", 9));
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR, chainResp.getCode());
    }

    // ── 4. 载体单源：行面与码面都不许另立字面量 ───────────────

    @Test
    @Timeout(120)
    @DisplayName("载体码面与状态行同取 RlsExecutionContract 单源，禁在本类写 503／ECOS-SEC-501 字面量")
    void carrierTakesSingleSourceConstantsAndStatusFollowsCode() {
        AuditController down = new AuditController(absentProvider(), absentProvider());
        DataBridgeException thrown = assertThrows(DataBridgeException.class, down::stats);

        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, thrown.getHttpStatus());
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, thrown.getErrorCode(),
                "携带码须与状态行同号（#142/B4-3e-26／3e-29 口径）");
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                "状态行必须由 resolveHttpStatus 单源推出");
        assertTrue(thrown.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE + ":"),
                "消息首段必须是机读码 ECOS-SEC-501，实际：" + thrown.getMessage());
    }

    // ── 5~6. 正向对照：真实空结果与真实数据都不受 fail-closed 影响 ──

    @Test
    @Timeout(120)
    @DisplayName("正向对照 A：服务在位且真零条＝仍是 200＋空集/全 0（不可用与空结果必须可区分）")
    void realEmptyResultStillReturnsSuccessEnvelope() {
        IAuditLogService audit = mock(IAuditLogService.class);
        when(audit.query(any())).thenReturn(List.of());
        AuditController controller = new AuditController(availableProvider(audit), absentProvider());

        ApiResponse<Map<String, Object>> list = controller.list(null, null, null, null, null, 1, 20);
        assertEquals(ApiResponse.CODE_SUCCESS, list.getCode(),
                "§4.7.2：空结果走成功信封，只有「依赖不在」才走 503");
        assertEquals(List.of(), list.getData().get("data"));
        assertEquals(0, list.getData().get("total"));

        ApiResponse<Map<String, Object>> stats = controller.stats();
        assertEquals(ApiResponse.CODE_SUCCESS, stats.getCode());
        assertEquals(0L, stats.getData().get("todayCount"));

        AuditHashChainService chain = mock(AuditHashChainService.class);
        when(chain.verifyHashChain()).thenReturn(Map.of("valid", true, "totalChecked", 0));
        ApiResponse<Map<String, Object>> integrity =
                new AuditController(absentProvider(), availableProvider(chain)).verifyIntegrity();
        assertEquals(ApiResponse.CODE_SUCCESS, integrity.getCode());
        assertEquals(Boolean.TRUE, integrity.getData().get("valid"),
                "真零条链校验通过不得被写成 valid=false");
    }

    @Test
    @Timeout(120)
    @DisplayName("正向对照 B：服务在位且有数据＝回真实数据（证明对照 A 的 0 来自空集而非恒 0）")
    void assembledServiceWithRowsReturnsRealData() {
        AuditEvent row = new AuditEvent();
        row.setEventId("e-1");
        IAuditLogService audit = mock(IAuditLogService.class);
        when(audit.query(any())).thenReturn(List.of(row));
        when(audit.getById("e-1")).thenReturn(row);

        AuditController controller = new AuditController(availableProvider(audit), absentProvider());
        ApiResponse<Map<String, Object>> list = controller.list(null, null, null, null, null, 1, 20);
        assertEquals(ApiResponse.CODE_SUCCESS, list.getCode());
        assertEquals(1, list.getData().get("total"), "对照 A 的 0 必须来自空集而非判据失灵");
        assertNotNull(controller.get("e-1"));

        AuditHashChainService chain = mock(AuditHashChainService.class);
        when(chain.verifyHashChainRange(1L, 9L)).thenReturn(Map.of("valid", false, "brokenAt", List.of(5L)));
        ApiResponse<Map<String, Object>> range = new AuditController(absentProvider(),
                availableProvider(chain)).verifyChain(Map.of("from", 1, "to", 9));
        assertEquals(ApiResponse.CODE_SUCCESS, range.getCode());
        assertEquals(Boolean.FALSE, range.getData().get("valid"),
                "真实断链结果仍由载荷表达（本门不得把 valid=false 一律当成折叠形态禁掉）");
    }

    // ── 7. 源码面：折叠形态不得复现，逐枚守卫与承载腿必须齐 ────

    @Test
    @Timeout(120)
    @DisplayName("主源码面：AuditController 零折叠字面形态，守卫／承载腿各 6 枚且载体码取单源常量")
    void mainSourceHasNoFoldingShapeAndCarriesEveryLeg() throws IOException {
        String code = SourceCommentStripper.strip(Files.readString(repoFile(AUDIT_CTRL)));

        for (String guard : FOLD_GUARD_ONE_LINERS) {
            assertFalse(code.contains(guard),
                    "复现「未装配 ⇒ 直接 return 信封」的折叠守卫 [" + guard + "]（D-B4-86）");
        }
        assertFalse(code.contains(NOT_READY_MESSAGE), "复现「服务未就绪」文案（折叠腿的自证字样）");
        assertFalse(code.contains(FOLD_EMPTY_PAGE), "/logs 不得再把缺服务折成 200＋空集");
        assertFalse(code.contains(FOLD_ZERO_STATS), "/stats 不得再把缺服务折成 200＋全 0 计数");
        assertFalse(code.contains(FOLD_INVALID_CHAIN),
                "/verify-integrity 与 /verify-chain 不得再把缺服务折成 200＋valid=false");

        assertEquals(AUDIT_GUARDS, occurrences(code, "getIfAvailable()"),
                "未装配守卫数漂移（逐枚勘察＝6 枚腿）");
        assertEquals(AUDIT_RETHROWS, occurrences(code, "catch (DataBridgeException"),
                "显式承载腿数漂移（缺一枚即该腿又被兜底 catch 折回 500）");

        assertTrue(code.contains("RlsExecutionContract.SEC_UNAVAILABLE_HTTP"),
                "载体状态行必须取单源常量");
        assertTrue(code.contains("RlsExecutionContract.SEC_UNAVAILABLE_CODE"),
                "载体机读码必须取单源常量，禁写字面量");
        assertFalse(code.contains("new DataBridgeException(503"),
                "禁在本类另立 503 字面量（码行分写两处＝D-B4-52 漂移形态）");
        assertFalse(code.contains("ECOS-SEC-501"),
                "禁在本类另立 ECOS-SEC-501 字面量副本（单源在册 D-B4-31）");
    }

    // ── helpers ────────────────────────────────────────────

    private interface Call {
        Object run() throws Exception;
    }

    private static void assertCarrier(Call call, String leg) {
        assertCarrier(call, RlsExecutionContract.SEC_UNAVAILABLE_HTTP, leg);
    }

    private static void assertCarrier(Call call, int expectedStatus, String leg) {
        DataBridgeException thrown = assertThrows(DataBridgeException.class,
                () -> call.run(), leg + " 必须抛出承载型异常，不得返回信封（折叠即谎报）");
        assertEquals(expectedStatus, thrown.getHttpStatus(), leg + " 携带状态行不符");
        assertEquals(expectedStatus, thrown.getErrorCode(), leg + " 携带码须与状态行同号");
        assertNotNull(thrown.getMessage(), leg + " 消息不得为 null");
        if (expectedStatus == RlsExecutionContract.SEC_UNAVAILABLE_HTTP) {
            assertTrue(thrown.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE + ":"),
                    leg + " 消息首段必须是机读码 ECOS-SEC-501，实际：" + thrown.getMessage());
        }
        assertEquals(thrown.getHttpStatus(), DataBridgeException.resolveHttpStatus(thrown.getErrorCode()),
                leg + " 状态行必须由 resolveHttpStatus 单源推出");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> absentProvider() {
        return mock(ObjectProvider.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> availableProvider(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    private static int occurrences(String code, String needle) {
        int count = 0;
        for (int i = code.indexOf(needle); i >= 0; i = code.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到判据两侧源码（禁跳过校验）: " + repoRelative);
    }
}
