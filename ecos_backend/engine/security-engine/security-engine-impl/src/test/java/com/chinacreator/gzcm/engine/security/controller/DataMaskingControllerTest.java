package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DataMaskingControllerTest — Wave-5.4 T-19 seed 单测.
 *
 * <p>覆盖 Controller 的 /demo 与 /apply 两条端点；mock DataMaskingService 跳过真实脱敏逻辑，
 * 验证参数校验与异常兜底 (400 / 200) 路径。
 */
@ExtendWith(MockitoExtension.class)
class DataMaskingControllerTest {

    @Mock
    private DataMaskingService service;

    private DataMaskingController controller;

    @BeforeEach
    void setUp() {
        this.controller = new DataMaskingController(service);
    }

    @Test
    void demo_shouldReturnSamplesAndSupportedRules() {
        when(service.getDemoSamples()).thenReturn(List.of(Map.of("rule", "phone", "masked", "138****1234")));
        when(service.getSupportedRules()).thenReturn(List.of("phone", "email", "idCard"));

        ApiResponse<Map<String, Object>> resp = controller.demo();

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        assertNotNull(resp.getData().get("samples"));
        assertNotNull(resp.getData().get("supportedRules"));
        assertNotNull(resp.getData().get("description"));
    }

    @Test
    void apply_emptyData_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(Map.of("data", List.of(), "rules", List.of("phone")));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_emptyRules_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(Map.of("data", List.of("x"), "rules", List.of()));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_rulesLengthMismatch_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(
                Map.of("data", List.of("a", "b", "c"), "rules", List.of("phone", "email")));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_validRequest_shouldReturnSuccess() {
        when(service.applyMasking(anyList(), anyList()))
                .thenReturn(List.of(Map.of("original", "1381", "masked", "138****1234")));
        List<String> data = List.of("13800001234", "13800005678");
        List<String> rules = List.of("phone", "phone");

        ApiResponse<?> resp = controller.apply(Map.of("data", data, "rules", rules));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        verify(service).applyMasking(data, rules);
    }

    @Test
    void contractAliasPrefixIsAdditive() throws Exception {
        RequestMapping rm = DataMaskingController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "类级 @RequestMapping 缺失");
        assertEquals(List.of("/api/v1/data-masking", "/api/v1/security/masking", "/api/v1/security/mask"),
                List.of(rm.value()), "旧两前缀必须原位保留，契约别名只许追加在末位（只增不改）");

        PostMapping apply = DataMaskingController.class.getMethod("apply", Map.class)
                .getAnnotation(PostMapping.class);
        assertNotNull(apply, "POST /apply 方法级映射必须保留");
        assertEquals(List.of("/apply"), List.of(apply.value()));
        assertEquals("/api/v1/security/mask/apply", rm.value()[2] + apply.value()[0],
                "别名路径必须与详设 §4.3.2 契约路径逐字一致");
    }
}
