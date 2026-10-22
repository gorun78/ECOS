package com.chinacreator.gzcm.sysman.boot.handler;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-29（差距 D-B4-66）— sysman-boot 属主 handler 的根异常型腿<b>状态行同号</b>门。
 *
 * <p>收口前 {@code handleDataBridgeException} 返回裸 {@code ApiResponse}，状态行由方法上的
 * {@code @ResponseStatus(INTERNAL_SERVER_ERROR)} 恒定决定；而本腿入参型是根异常型
 * {@link DataBridgeException}（三枚构造的首参就是状态行，没有 {@code DEFAULT_HTTP_STATUS} 锚定），
 * 于是异常自述的 403/503/404 一律在 HTTP 面报成 500，方法头注释「使用异常内置的 httpStatus」
 * 凭注释成立。本类逐值钉死收口后的三件事：①合法携带值原样上状态行；②非法携带值（0／负数码／
 * 2xx）由单源回退 500，禁把假语义写进状态行；③机读码腿（{@code errorCode} 字段）不再缺席。</p>
 *
 * <p>静态面（全后端根型腿分布、单源唯一性、锁死子类型不扩面）在
 * {@code common-api} 的 {@code RootCarrierHttpStatusSingleSourceRatchetTest}；本类只钉本宿主
 * 这一枚的行为面——那里是判据面，这里是行为面，两两不可互替。</p>
 */
class SysmanBootDataBridgeStatusFidelityTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("携带 403 → 状态行 403（收口前恒 500），body code 同号，机读码从 message 起始位取")
    void carriedDenyStatusLandsOnStatusLine() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDataBridgeException(
                new DataBridgeException(403, 403, "ECOS-SEC-403 策略拒绝：无该资源读权限"));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "§3.2.7：状态行必须与异常携带值同号；收口前注解锁 500 ⇒ 拒绝在 HTTP 面伪装成服务端故障");
        ApiResponse<Void> body = resp.getBody();
        assertNotNull(body, "响应体不得为 null");
        assertEquals(403, body.getCode(), "body code 取异常携带的 errorCode，逐字不变");
        assertEquals("ECOS-SEC-403", body.getErrorCode(),
                "errorCode 字段走 MachineErrorCode.orNumeric 单源：message 起始位命中即取机读码");
    }

    @Test
    @DisplayName("携带 503 → 状态行 503；不可用与无权限分态不得互相折叠（§4.7.2）")
    void unavailableStaysUnavailable() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDataBridgeException(
                new DataBridgeException(503, -100, "上游引擎不可用"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resp.getStatusCode(),
                "503 折成 500 或 403 都违反 §4.7.2「不可用 ≠ 无权限」");
        ApiResponse<Void> body = resp.getBody();
        assertNotNull(body);
        assertEquals(-100, body.getCode(), "DataBridge 负数码留在 body，不被状态行取代");
        assertEquals("-100", body.getErrorCode(),
                "message 起始位无机读码时回落数字串（与收口前面值一致，不新造第二个码）");
    }

    @Test
    @DisplayName("非法携带值（0／-1／2xx／越界）一律回退 500，禁把假语义写进状态行")
    void illegalCarriedValuesFallBackToServerError() {
        for (int illegal : new int[] {0, -1, -100, 200, 302, 600, 700}) {
            ResponseEntity<ApiResponse<Void>> resp = handler.handleDataBridgeException(
                    new DataBridgeException(illegal, 500, "携带值非法"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode(),
                    "非法携带值 " + illegal + " 不得原样上状态行（走 resolveHttpStatus 单源回退 500）");
        }
    }

    @Test
    @DisplayName("本腿永不裸回 2xx：HTTP 面与 body 失败语义同侧")
    void failureNeverRidesSuccessStatusLine() {
        for (int carried : new int[] {400, 403, 404, 409, 500, 502, 503, 0, -100, 200}) {
            ResponseEntity<ApiResponse<Void>> resp = handler.handleDataBridgeException(
                    new DataBridgeException(carried, carried, "ECOS-SYS-409 冲突"));
            assertTrue(resp.getStatusCode().value() >= 400,
                    "carried=" + carried + " 时状态行落到 " + resp.getStatusCode()
                            + "：失败语义在 HTTP 面伪装成成功（裸回棘轮门同族缺陷）");
        }
    }

    @Test
    @DisplayName("声明面只增不改：方法名与入参签名不变，返回类型改 ResponseEntity，注解锁留意保留")
    void declarationFaceIsAdditiveOnly() throws NoSuchMethodException {
        Method leg = GlobalExceptionHandler.class
                .getDeclaredMethod("handleDataBridgeException", DataBridgeException.class);
        assertEquals(ResponseEntity.class, leg.getReturnType(),
                "返体必须是状态行承载手段（裸 ApiResponse 等于把携带值丢掉）");
        ResponseStatus declared = leg.getAnnotation(ResponseStatus.class);
        assertNotNull(declared,
                "@ResponseStatus(INTERNAL_SERVER_ERROR) 有意保留为默认声明面：它与单源回退值同号，"
                        + "删除它属另一批裁决，不是本批顺手改");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, declared.value());
    }
}
