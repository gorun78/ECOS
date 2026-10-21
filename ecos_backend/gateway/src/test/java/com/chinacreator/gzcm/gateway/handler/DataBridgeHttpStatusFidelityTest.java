package com.chinacreator.gzcm.gateway.handler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-15（差距 D-B4-52）— gateway 域桥异常的 <b>HTTP 状态行保真</b>门。
 *
 * <p>收口前 {@link GlobalExceptionHandler#handleDataBridge} / {@code handleDataAccess}
 * 返回裸 {@code ApiResponse}，Spring 侧状态行恒 HTTP 200：异常自述的 httpStatus 只进了日志，
 * 从未上过 HTTP 面。后果是 §4.7.2「不可用 ≠ 无权限」的分态在传输层被抹平 ——
 * {@code ECOS-SEC-501} 的 503 与策略拒绝的 403 在状态行同形，且都被前端的
 * {@code res.ok} 读成成功信封。本门把"携带值必须落成状态行"钉成可执行契约。</p>
 *
 * <p>纯单元、无 Spring 上下文（与同包 {@code NoFourOhFourMaskingTest} 一致）：直接调用
 * handler 方法并断言 {@code ResponseEntity.getStatusCode()}，因为状态行正是由该返回值决定。</p>
 */
class DataBridgeHttpStatusFidelityTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── 验收 1：security-engine 不可用 → 状态行真 503 ──────────────────────────

    @Test
    @DisplayName("handleDataBridge — 携带 503（ECOS-SEC-501 不可用腿）→ 状态行 503，不再伪装成功")
    void securityUnavailableLegWrites503StatusLine() {
        DataBridgeException ex = new DataBridgeException(
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": pipeline.execute 未获安全裁决");

        ResponseEntity<ApiResponse<Void>> resp = handler.handleDataBridge(ex);

        assertNotNull(resp, "handleDataBridge 必须返回 ResponseEntity（状态行由其决定）");
        assertEquals(503, resp.getStatusCode().value(),
                "security-engine 不可用的 503 必须落在 HTTP 状态行（B4-3e-15 前恒 200）");
        ApiResponse<Void> body = resp.getBody();
        assertNotNull(body);
        // #142/B4-3e-24 收口 D-B4-55：本断言在收口前是「把缺陷钉成契约」的
        // assertEquals("503", body.getErrorCode())。DataBridgeException.errorCode 仍是 int
        // （签名不动），但信封 errorCode 字段改由 MachineErrorCode.orNumeric(message, int) 单源取值
        // ⇒ message 起始位的机读码 ECOS-SEC-501 现在真的上了 errorCode 字段，前端可按码匹配。
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_CODE, body.getErrorCode(),
                "机读码必须落在 errorCode 字段（收口前该字段被 String.valueOf(int) 冒充为 503）");
        assertEquals("503", String.valueOf(body.getCode()),
                "code 仍是异常携带的数字语义值，与状态行同源（本批不动该契约）");
        assertTrue(body.getMessage() != null && body.getMessage().startsWith(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "message 前缀形态保持不变（errorCode 与 message 同源不同字段），实际=" + body.getMessage());
    }

    // ── 验收 2：策略拒绝 → 状态行 403，且与 503 腿在状态行分形 ─────────────────

    @Test
    @DisplayName("handleDataBridge — 携带 403（策略 DENY）→ 状态行 403，与不可用 503 腿分形")
    void policyDenyLegWrites403StatusLine() {
        // data-engine SecurityDenyException 的 (httpStatus, errorCode) = (403, 403)，
        // gateway 无该异常型的专用 handler ⇒ 走 handleDataBridge，故用根类型等价构造。
        DataBridgeException deny = new DataBridgeException(403, 403, "Pipeline 执行被 ABAC 策略拒绝");
        DataBridgeException unavailable = new DataBridgeException(
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": 未获安全裁决");

        int denyStatus = handler.handleDataBridge(deny).getStatusCode().value();
        int unavailableStatus = handler.handleDataBridge(unavailable).getStatusCode().value();

        assertEquals(403, denyStatus, "DENY 腿状态行 = 403");
        assertEquals(503, unavailableStatus, "UNEVALUATED 腿状态行 = 503");
        assertTrue(denyStatus != unavailableStatus,
                "§4.7.2：不可用与无权限必须在 HTTP 面可分辨，实际同为 " + denyStatus);
    }

    // ── 验收 3：非法携带值回退 500，禁写进状态行 ──────────────────────────────

    @Test
    @DisplayName("resolveHttpStatus — 只有 4xx/5xx 才可上状态行；0/-100/2xx/600 一律回退 500")
    void illegalCarriedStatusFallsBackTo500() {
        assertEquals(503, GlobalExceptionHandler.resolveHttpStatus(503));
        assertEquals(403, GlobalExceptionHandler.resolveHttpStatus(403));
        assertEquals(499, GlobalExceptionHandler.resolveHttpStatus(499));
        assertEquals(500, GlobalExceptionHandler.resolveHttpStatus(0), "未设置 → 500");
        assertEquals(500, GlobalExceptionHandler.resolveHttpStatus(-100),
                "DataAccessException 默认 errorCode 形状不得当状态用");
        assertEquals(500, GlobalExceptionHandler.resolveHttpStatus(200), "2xx 不是失败语义");
        assertEquals(599, GlobalExceptionHandler.resolveHttpStatus(599), "400..599 为合法携带区间，原样透出");
        assertEquals(500, GlobalExceptionHandler.resolveHttpStatus(600), "越界 → 500");
    }

    @Test
    @DisplayName("handleDataBridge — 携带非法 httpStatus 的异常 → 状态行 500（不得容器报假语义）")
    void illegalCarriedValueYields500NotRawEcho() {
        ResponseEntity<ApiResponse<Void>> resp =
                handler.handleDataBridge(new DataBridgeException(0, 4001, "参数非法"));
        assertEquals(500, resp.getStatusCode().value(), "非法携带值必须回退 500");
    }

    // ── 验收 4：BusinessException(403) 陷阱仍被钉住（本批有意不扩面）────────────

    @Test
    @DisplayName("handleBusiness — BusinessException(403,…) 仍落 400：异常型钉死 httpStatus，非 handler 缺陷")
    void businessExceptionHandlerStillPins400DocumentingTheTrap() throws Exception {
        // BusinessException 两个 ctor 都把 httpStatus 写死 400，首参只进 body code。
        // ⇒「投 BusinessException(403) 得到 HTTP 403」是个不成立的假设（D-B4-53 的根因），
        // 本批的修法 = 让策略拒绝改投真正的 403 承载型异常，而不是改 handler 去猜 body 码。
        assertEquals(400, new BusinessException(403, "策略拒绝").getHttpStatus(),
                "钉住陷阱本身：BusinessException 的 httpStatus 恒 400");

        ApiResponse<Void> resp = handler.handleBusiness(new BusinessException(403, "策略拒绝"));
        assertNotNull(resp);
        assertEquals(403, resp.getCode(), "body code 仍是调用方传入的业务码");

        Method m = GlobalExceptionHandler.class.getDeclaredMethod(
                "handleBusiness", BusinessException.class);
        ResponseStatus ann = m.getAnnotation(ResponseStatus.class);
        assertNotNull(ann, "handleBusiness 必须仍声明 @ResponseStatus");
        assertEquals(HttpStatus.BAD_REQUEST, ann.value(),
                "handleBusiness 保持 400（改它会波及 250+ 既有 BusinessException 站点，本批不扩面）");
    }

    // ── 验收 5：数据访问异常 → 状态行 500，body 仍脱敏 ─────────────────────────

    @Test
    @DisplayName("handleDataAccess — 状态行 500（注释承诺的「跟随 httpStatus」现已兑现）+ body 脱敏")
    void dataAccessLegWrites500AndMasksDetails() {
        com.chinacreator.gzcm.common.exception.DataAccessException ex =
                new com.chinacreator.gzcm.common.exception.DataAccessException(
                        "Column not found: 'secret_col'", new IllegalStateException("raw-sql"));

        ResponseEntity<ApiResponse<Void>> resp = handler.handleDataAccess(ex);

        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode().value(),
                "DB 故障必须在 HTTP 面暴露为 500（B4-3e-15 前状态行恒 200＝违约自身 javadoc）");
        ApiResponse<Void> body = resp.getBody();
        assertNotNull(body);
        String msg = body.getMessage();
        assertTrue(msg != null && !msg.contains("Column not found") && !msg.contains("raw-sql"),
                "body 不得含 SQL/堆栈细节，实际=" + msg);
        // 信封侧既有形态：code 取 errorCode(-100) 与状态行不一致 —— 属 common-api 信封契约，
        // 本批不扩面，仅在此登记以免"顺手改成 500"造成前端判定字段漂移。
        assertEquals(ex.getErrorCode(), body.getCode(),
                "ApiResponse.code 保持 errorCode（-100），本批只动状态行");
    }

    // ── 验收 6：结构形状 —— 两个 handler 必须返回 ResponseEntity ────────────────

    @Test
    @DisplayName("handleDataBridge / handleDataAccess 返回类型必须是 ResponseEntity（裸 ApiResponse＝状态行恒 200）")
    void bothHandlersReturnResponseEntity() {
        for (String name : new String[] {"handleDataBridge", "handleDataAccess"}) {
            Method m = findMethod(name);
            assertEquals(ResponseEntity.class, m.getReturnType(),
                    name + " 返回类型必须 = ResponseEntity，实际=" + m.getReturnType());
        }
    }

    // ── 验收 7：全域结构规则 —— 任何 handler 都不得让失败静默成 HTTP 200 ─────────

    @Test
    @DisplayName("结构性零容忍：每支 @ExceptionHandler 要么返回 ResponseEntity，要么声明 @ResponseStatus")
    void noHandlerMaySilentlyReturn200() {
        // D-B4-52 的本质不是「这两支漏了」而是「缺一条结构性约束」：裸 ApiResponse + 无
        // @ResponseStatus ＝ Spring 落 200，失败语义只在 body 里，前端 res.ok 一律读成成功。
        // 本批前实扫违例 2 支（handleDataBridge／handleDataAccess），修完后必须为 0，
        // 且今后新增 handler 若忘记声明状态行会当场变红。
        List<String> silentOk = new ArrayList<>();
        int total = 0;
        for (Method m : GlobalExceptionHandler.class.getDeclaredMethods()) {
            if (m.getAnnotation(ExceptionHandler.class) == null) {
                continue;
            }
            total++;
            boolean setsStatus = ResponseEntity.class.isAssignableFrom(m.getReturnType())
                    || m.getAnnotation(ResponseStatus.class) != null;
            if (!setsStatus) {
                silentOk.add(m.getName() + " → " + m.getReturnType().getSimpleName() + " 无 @ResponseStatus");
            }
        }
        assertTrue(total >= 16, "扫描面异常收缩＝门禁失效，@ExceptionHandler 方法数实测 " + total);
        assertTrue(silentOk.isEmpty(), "存在会把失败伪装成 200 的 handler：" + silentOk);
    }

    // ── 辅助 ────────────────────────────────────────────────────────────────

    private static Method findMethod(String name) {
        for (Method m : GlobalExceptionHandler.class.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1) {
                return m;
            }
        }
        return fail("未找到 handler 方法 " + name);
    }
}
