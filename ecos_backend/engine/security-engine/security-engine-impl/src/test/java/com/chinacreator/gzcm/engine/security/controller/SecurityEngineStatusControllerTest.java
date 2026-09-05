package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.security.SecurityEngineImpl;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * SecurityEngineStatusControllerTest — 引擎状态端点三接口 200 验证。
 *
 * <p>Wave-5.1 T-05：不加载 Spring Context、不连 PG，
 * 通过构造 + ReflectionTestUtils 注入 mock 的 IEngine。
 */
@ExtendWith(MockitoExtension.class)
class SecurityEngineStatusControllerTest {

    @Mock
    private SecurityConfigService configService;

    private SecurityEngineStatusController controller;

    @BeforeEach
    void setUp() {
        SecurityEngineImpl engine = new SecurityEngineImpl(configService);
        // 模拟 @PostConstruct 启动
        ReflectionTestUtils.invokeMethod(engine, "autoStart");
        this.controller = new SecurityEngineStatusController();
        ReflectionTestUtils.setField(controller, "engine", engine);
    }

    @Test
    @DisplayName("GET /api/v1/engine/security/health — 200 且 body.status=UP")
    void healthReturns200() {
        when(configService.ping()).thenReturn(true);
        ApiResponse<HealthCheck> resp = controller.health();
        assertTrue(resp.isSuccess(), "health 应 code=0");
        assertNotNull(resp.getData());
        assertEquals("UP", resp.getData().getStatus());
        assertEquals("UP", resp.getData().getComponents().get("db"));
    }

    @Test
    @DisplayName("GET /api/v1/engine/security/config — 200 且含 module/security")
    void configReturns200() {
        ApiResponse<Map<String, Object>> resp = controller.config();
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("security", resp.getData().get("module"));
    }

    @Test
    @DisplayName("GET /api/v1/engine/security/status — 200 且 status=RUNNING")
    void statusReturns200() {
        ApiResponse<Map<String, Object>> resp = controller.status();
        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals("security-engine", resp.getData().get("name"));
        assertEquals("RUNNING", resp.getData().get("status"));
    }
}
