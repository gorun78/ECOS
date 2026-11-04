package com.chinacreator.gzcm.engine.kb.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ValidationException;

/**
 * kb 错误码分域门（#147BS）。
 *
 * <p><b>修的是什么</b>：{@code ValidationException} 此前落 {@code KbErrorAdvice#onBridge}——
 * 状态行 400 对，但机读码是 {@code ECOS-KB-501}「数据桥域异常」，把「调用方参数错」标成
 * 「别家领域异常」（活体实测：非法游标 → 400 + {@code ECOS-KB-501}）。现在 {@code onValidation}
 * 更具体（子类）优先命中，给本域 400 码 {@code ECOS-KB-010}。</p>
 *
 * <p><b>为什么不用 MockMvc 探针</b>：kb 的 ArchUnit 规则要求 {@code @RestController} 只能落在
 * controller 包，测试里放探针会把该门打红（实测）。故这里两头钉：① 用 Spring 的
 * {@link ExceptionHandlerMethodResolver} 证明**选择链**（子类异常解析到 {@code onValidation}、
 * 父类解析到 {@code onBridge}）；② 直调 handler 断言状态行与机读码。</p>
 *
 * @author ECOS KB #147BS
 */
class KbErrorAdviceValidationTest {

    private final KbErrorAdvice advice = new KbErrorAdvice();

    @Test
    @DisplayName("选择链：ValidationException → onValidation；DataBridgeException → onBridge（子类优先，桥接兜底不被吃掉）")
    void resolverPrefersSpecificHandler() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(KbErrorAdvice.class);

        assertEquals("onValidation",
                resolver.resolveMethodByThrowable(new ValidationException("无效的分页游标")).getName(),
                "入参校验失败必须由本域 400 腿处理");
        assertEquals("onBridge",
                resolver.resolveMethodByThrowable(new DataBridgeException(403, 1, "别域拒绝")).getName(),
                "别家领域异常仍走桥接兜底（回归）");
    }

    @Test
    @DisplayName("入参校验失败 → 400 + ECOS-KB-010，message 保留原始原因")
    void validationFailureCarriesKbDomainCode() {
        var resp = advice.onValidation(new ValidationException("无效的分页游标"));

        assertEquals(400, resp.getStatusCode().value());
        ApiResponse<Void> body = resp.getBody();
        assertEquals(400, body.getCode());
        assertEquals("ECOS-KB-010", body.getErrorCode());
        assertTrue(body.getMessage().contains("无效的分页游标"), "原因应保留: " + body.getMessage());
    }

    @Test
    @DisplayName("回归：别家 DataBridgeException 仍按自述状态行 + ECOS-KB-501")
    void bridgeExceptionStillUsesBridgeCode() {
        var resp = advice.onBridge(new DataBridgeException(403, 1, "别域拒绝"));

        assertEquals(403, resp.getStatusCode().value());
        assertEquals("ECOS-KB-501", resp.getBody().getErrorCode());
    }
}
