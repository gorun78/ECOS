// TODO D4: 归位 ge-service（格）
package com.chinacreator.gzcm.engine.data.transform.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.transform.TransformChain;
import com.chinacreator.gzcm.engine.data.transform.TransformException;
import com.chinacreator.gzcm.engine.data.transform.impl.TransformChainImpl;
import com.chinacreator.gzcm.engine.data.transform.model.DataFrame;
import com.chinacreator.gzcm.engine.data.transform.model.TransformResult;
import com.chinacreator.gzcm.engine.data.transform.service.ITransformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TransformController 单元测试 — Wave-2B ge (D→I) 收口测试。
 * <p>
 * 纯 JUnit 5（不引 spring-boot-starter-test），与 {@code ArchitectureTest} 同模式。
 * stub {@link ITransformService} 委托给 {@link TransformChainImpl}，
 * 让 step 真实执行（cleansing 会 trim / mapping 会重命名），覆盖业务语义。
 *
 * <p>覆盖 5 个 case：</p>
 * <ol>
 *   <li>GET /meta — 6 类 step 清单、字段完整</li>
 *   <li>POST /execute null body — 400</li>
 *   <li>POST /execute input 非 Map — 400</li>
 *   <li>POST /execute 未知 step type — 400</li>
 *   <li>POST /execute 合法 cleansing 链路 — 200 success=true 且 trim 生效</li>
 * </ol>
 *
 * @author ECOS Wave-2B ge D→I 收口
 */
public class TransformControllerTest {

    private TransformController controller;

    @BeforeEach
    void setUp() {
        // stub ITransformService：把 chain 委托给 TransformChainImpl.execute
        ITransformService stub = new ITransformService() {
            @Override
            public TransformResult transform(DataFrame input, TransformChain chain)
                    throws TransformException {
                if (chain instanceof TransformChainImpl tc) {
                    if (tc.getSteps() == null || tc.getSteps().isEmpty()) {
                        TransformResult r = new TransformResult();
                        if (input != null) {
                            r.setOutput(input);
                        }
                        return r;
                    }
                    return tc.execute(input);
                }
                TransformResult r = new TransformResult();
                if (input != null) {
                    r.setOutput(input);
                }
                return r;
            }

            @Override
            public boolean validateChain(TransformChain chain) {
                return chain != null && chain.getSteps() != null && !chain.getSteps().isEmpty();
            }
        };
        this.controller = new TransformController(stub);
    }

    // ── T1: meta 端点返回 6 类步骤 ──────────────────────

    @Test
    @DisplayName("GET /meta — 应返回 6 类步骤清单且字段完整")
    void metaReturnsSixSteps() {
        ApiResponse<Map<String, Object>> resp = controller.meta();
        assertTrue(resp.isSuccess(), "meta 应成功");
        Map<String, Object> data = resp.getData();
        assertNotNull(data, "data 非空");
        assertEquals(6, data.get("totalSteps"), "共 6 类步骤");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) data.get("availableSteps");
        assertNotNull(steps);
        assertEquals(6, steps.size());

        // 6 类 type 必须都在
        assertTrue(steps.stream().anyMatch(s -> "cleansing".equals(s.get("type"))));
        assertTrue(steps.stream().anyMatch(s -> "mapping".equals(s.get("type"))));
        assertTrue(steps.stream().anyMatch(s -> "typeConversion".equals(s.get("type"))));
        assertTrue(steps.stream().anyMatch(s -> "validation".equals(s.get("type"))));
        assertTrue(steps.stream().anyMatch(s -> "aggregation".equals(s.get("type"))));
        assertTrue(steps.stream().anyMatch(s -> "calculator".equals(s.get("type"))));

        // 每个 step 三字段齐
        for (Map<String, Object> s : steps) {
            assertNotNull(s.get("type"), "step.type 非空");
            assertNotNull(s.get("name"), "step.name 非空");
            assertNotNull(s.get("description"), "step.description 非空");
        }
    }

    // ── T2: 请求体 null ────────────────────────────────

    @Test
    @DisplayName("POST /execute — 请求体 null 应返回 400")
    void executeNullBodyReturnsBadRequest() {
        ApiResponse<Map<String, Object>> resp = controller.execute(null);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
        assertFalse(resp.isSuccess());
    }

    // ── T3: input 非 Map ───────────────────────────────

    @Test
    @DisplayName("POST /execute — input 非 Map 应返回 400")
    void executeInvalidInputTypeReturnsBadRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", "should-be-map"); // 非法
        body.put("chain", List.of());
        ApiResponse<Map<String, Object>> resp = controller.execute(body);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getMessage().contains("input"), "错误信息应提及 input");
    }

    // ── T4: chain 含未知 type ──────────────────────────

    @Test
    @DisplayName("POST /execute — 未知 step type 应返回 400")
    void executeUnknownStepTypeReturnsBadRequest() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("columns", List.of("a"));
        input.put("rows", new ArrayList<Map<String, Object>>());
        body.put("input", input);
        body.put("chain", List.of(Map.of("type", "notExist")));
        ApiResponse<Map<String, Object>> resp = controller.execute(body);
        assertEquals(ApiResponse.CODE_BAD_REQUEST, resp.getCode());
        assertTrue(resp.getMessage().contains("notExist"), "错误信息应提及未知 type");
    }

    // ── T5: 合法 cleansing 链路 — trim 生效 ────────────

    @Test
    @DisplayName("POST /execute — 合法 cleansing chain 应 trim 空行灰边并 success=true")
    void executeValidCleansingChainSucceeds() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("columns", List.of("name", "age"));
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("name", "  张三  ", "age", 30));
        input.put("rows", rows);

        Map<String, Object> cleansingStep = new LinkedHashMap<>();
        cleansingStep.put("type", "cleansing");
        cleansingStep.put("params", Map.of("trimWhitespace", true));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", input);
        body.put("chain", List.of(cleansingStep));

        ApiResponse<Map<String, Object>> resp = controller.execute(body);
        assertNotNull(resp);
        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(), "合法 cleansing 链路应 success (code=0)");

        Map<String, Object> data = resp.getData();
        assertNotNull(data);
        Boolean success = (Boolean) data.get("success");
        assertEquals(Boolean.TRUE, success, "success 应为 true");

        // 输出看 output → rows[0].name 是否被 trim
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) data.get("output");
        assertNotNull(out, "output 非空");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outRows = (List<Map<String, Object>>) out.get("rows");
        assertNotNull(outRows);
        assertTrue(outRows.size() >= 1, "应有行");
        Object nameVal = outRows.get(0).get("name");
        if (nameVal instanceof String s) {
            assertEquals("张三", s.trim(), "trim 后值应等于「张三」（空白首尾被去除）");
        }
    }
}
