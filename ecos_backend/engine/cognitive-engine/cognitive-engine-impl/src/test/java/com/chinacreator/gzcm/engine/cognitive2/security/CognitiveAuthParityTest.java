package com.chinacreator.gzcm.engine.cognitive2.security;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F05-14 两态鉴权等价（R-16 ①+③ 裁定：aiming service 态必须能独立裁决）：
 * 同一组认知契约端点，gateway 态（fat-jar 宿主，sysman SecurityConfig 默认 DENY +
 * JWT 链）与 service 态（common-api HeaderAuthInterceptor 信任头 fail-closed）
 * 都必须拒绝匿名访问；两态匿名面同引 {@link AnonymousEndpointRegistry} 单源。
 */
class CognitiveAuthParityTest {

    private static final Path BACKEND = Path.of("..", "..", "..", "..").toAbsolutePath().normalize()
            .resolve("ecos_backend");

    private static final List<String> CONTRACT_ENDPOINTS = List.of(
            cognition("detect"),
            cognition("operation-eval"),
            cognition("hypotheses"),
            cognition("beliefs"),
            CognitiveApiPaths.COGNITIVE + "/diagnose",
            CognitiveApiPaths.COGNITIVE + "/beliefs/var-a/1/replay");

    private static String cognition(String suffix) {
        return String.format(CognitiveApiPaths.SCENARIO_COGNITION_PREFIX_TEMPLATE, "scn-parity-1")
                + "/" + suffix;
    }

    @ParameterizedTest(name = "mode={0}")
    @ValueSource(strings = {"gateway", "service"})
    @DisplayName("同一组认知端点两态匿名访问都被拒（默认 DENY，无第二套匿名面）")
    void sameEndpointsDenyInBothModes(String mode) throws IOException {
        for (String path : CONTRACT_ENDPOINTS) {
            assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt(path),
                    "认知契约端点泄漏进匿名/豁免单源（默认 DENY 被绕）：" + path);
        }
        if ("gateway".equals(mode)) {
            gatewayDeniesViaAuthedChain();
        } else {
            serviceDeniesViaTrustHeaders();
        }
    }

    /** gateway 态：fat-jar 内生效链 = sysman SecurityConfig，permitAll 只由注册表单源生成。 */
    private void gatewayDeniesViaAuthedChain() throws IOException {
        String src = Files.readString(BACKEND.resolve(
                "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java"));
        assertTrue(src.contains("AnonymousEndpointRegistry.permitAllPatterns()"),
                "gateway SecurityConfig 不再由匿名单源驱动 permitAll（两态等价锚点漂移）");
        assertTrue(src.contains("anyRequest().authenticated()"),
                "gateway SecurityConfig 丢失默认 DENY 兜底（未登记路径将裸奔）");
    }

    /** service 态：aiming 启用信任头 fail-closed，拦截器对同一组端点逐条真实裁决。 */
    private void serviceDeniesViaTrustHeaders() throws IOException {
        String yml = Files.readString(
                BACKEND.resolve("services/aiming/src/main/resources/application.yml"));
        assertTrue(Pattern.compile("(?s)\\n\\s+security:\\s*\\n\\s+header-auth:\\s*\\n\\s+enabled:\\s*true")
                        .matcher(yml).find(),
                "aiming 未启用 ecos.security.header-auth.enabled=true（F05-14/R-16 service 态独立裁决）");

        HeaderAuthInterceptor interceptor = new HeaderAuthInterceptor();
        for (String path : CONTRACT_ENDPOINTS) {
            HttpServletResponse resp = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();
            when(resp.getWriter()).thenReturn(new PrintWriter(body));
            assertFalse(interceptor.preHandle(anonymousGet(path), resp, new Object()),
                    "service 态匿名访问未被拒（越权通道失守）：" + path);
            verify(resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertTrue(body.toString().contains("ECOS-AUTH-010"),
                    "service 态拒绝码非 ECOS-AUTH-010：" + path + " body=" + body);
        }
        // 健康面两态同口径：route-manifest 登记 /api/v1/engine/cognitive/health 匿名
        assertTrue(interceptor.preHandle(
                        anonymousGet(CognitiveApiPaths.ENGINE_COGNITIVE_HEALTH),
                        mock(HttpServletResponse.class), new Object()),
                "aiming 宿主引擎健康被信任头门禁误杀（route-manifest 匿名面漂移）");

        // 带信任头 → 放行并还原 ServiceContext；afterCompletion 必清（防线程池串号）
        HttpServletRequest granted = mock(HttpServletRequest.class);
        when(granted.getRequestURI()).thenReturn(cognition("detect"));
        when(granted.getMethod()).thenReturn("GET");
        when(granted.getHeader(HeaderAuthInterceptor.H_USER)).thenReturn("admin");
        when(granted.getHeader(HeaderAuthInterceptor.H_TENANT)).thenReturn("default");
        assertTrue(interceptor.preHandle(granted, mock(HttpServletResponse.class), new Object()));
        assertTrue(ServiceContext.present(), "信任头齐备后未构造 ServiceContext（身份链断裂）");
        interceptor.afterCompletion(granted, mock(HttpServletResponse.class), new Object(), null);
        assertFalse(ServiceContext.present(), "afterCompletion 未清 ThreadLocal（M0 红线：线程池串号）");
    }

    private static HttpServletRequest anonymousGet(String path) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn(path);
        when(req.getMethod()).thenReturn("GET");
        return req;
    }
}
