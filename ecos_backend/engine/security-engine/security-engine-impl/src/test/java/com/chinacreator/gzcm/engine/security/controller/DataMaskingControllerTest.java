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

    private static DataMaskingController.MaskApplyRequest request(List<Object> data, List<Object> rules) {
        DataMaskingController.MaskApplyRequest req = new DataMaskingController.MaskApplyRequest();
        req.setData(data);
        req.setRules(rules);
        return req;
    }

    @Test
    void demo_shouldReturnSamplesAndSupportedRules() {
        when(service.getDemoSamples()).thenReturn(List.of(
                new DataMaskingService.MaskSample("phone", "****", "138****1234")));
        when(service.getSupportedRules()).thenReturn(List.of("phone", "email", "idCard"));

        ApiResponse<DataMaskingController.MaskDemoVO> resp = controller.demo();

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        assertNotNull(resp.getData().samples());
        assertNotNull(resp.getData().supportedRules());
        assertNotNull(resp.getData().description());
    }

    @Test
    void apply_emptyData_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(request(List.of(), List.of("phone")));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_emptyRules_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(request(List.of("x"), List.of()));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_rulesLengthMismatch_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(
                request(List.of("a", "b", "c"), List.of("phone", "email")));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
    }

    @Test
    void apply_nonStringElement_shouldReturnBadRequest() {
        ApiResponse<?> resp = controller.apply(request(List.of(13800001234L), List.of("phone")));
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode(),
                "非字符串元素仍 400（旧 ClassCastException 腿的口径不变）");
    }

    @Test
    void apply_validRequest_shouldReturnSuccess() {
        when(service.applyMasking(anyList(), anyList()))
                .thenReturn(List.of(new DataMaskingService.MaskResult(0, "138****1234", "phone", null)));
        List<String> data = List.of("13800001234", "13800005678");
        List<String> rules = List.of("phone", "phone");

        ApiResponse<DataMaskingController.MaskApplyResultVO> resp =
                controller.apply(request(List.of("13800001234", "13800005678"),
                        List.of("phone", "phone")));

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode());
        assertNotNull(resp.getData());
        assertEquals(1, resp.getData().total());
        verify(service).applyMasking(data, rules);
    }

    @Test
    void maskResultErrorKeyOnlyWhenPresent() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> ok = mapper.convertValue(
                new DataMaskingService.MaskResult(0, "138****1234", "phone", null), Map.class);
        assertEquals(java.util.Set.of("index", "masked", "rule"), ok.keySet(),
                "已知规则不得出现 error 键（属性级 NON_NULL，与旧 Map 条件 put 同义）");
        @SuppressWarnings("unchecked")
        Map<String, Object> err = mapper.convertValue(
                new DataMaskingService.MaskResult(0, "***", "nope", "不支持的脱敏规则: nope"), Map.class);
        assertEquals(java.util.Set.of("index", "masked", "rule", "error"), err.keySet());
    }

    @Test
    void contractAliasPrefixIsAdditive() throws Exception {
        RequestMapping rm = DataMaskingController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "类级 @RequestMapping 缺失");
        assertEquals(List.of("/api/v1/data-masking", "/api/v1/security/masking", "/api/v1/security/mask"),
                List.of(rm.value()), "旧两前缀必须原位保留，契约别名只许追加在末位（只增不改）");

        PostMapping apply = DataMaskingController.class.getMethod(
                        "apply", DataMaskingController.MaskApplyRequest.class)
                .getAnnotation(PostMapping.class);
        assertNotNull(apply, "POST /apply 方法级映射必须保留");
        assertEquals(List.of("/apply"), List.of(apply.value()));
        assertEquals("/api/v1/security/mask/apply", rm.value()[2] + apply.value()[0],
                "别名路径必须与详设 §4.3.2 契约路径逐字一致");
    }
}
