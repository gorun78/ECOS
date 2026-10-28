package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据脱敏端点（安全域「护」）。
 *
 * <p>类级三前缀（只增不改）：{@code /api/v1/data-masking}、{@code /api/v1/security/masking} 为既有面；
 * {@code /api/v1/security/mask} 为《详细设计说明书-从零实施版》§4.3.2 / 第4篇接口总表登记的兼容别名，
 * 原句「{@code POST /api/v1/security/mask/apply}｜保留｜只能作为 {@code decide} 之后的执行器，不得独立决策」。
 * 故本类只把<b>已裁决通过</b>的脱敏规则作用到值上，不含策略选择与放行判定。</p>
 *
 * <p>additive 副产品：{@code GET /api/v1/security/mask/demo} 与既有 {@code /demo} 同体（类级前缀共用的必然结果）；
 * 旧两前缀下的路径、入参与响应一字未动。</p>
 *
 * <p>限流：{@code POST /api/v1/security/mask/apply} 已登记 gateway {@code RateLimitFilter}（10 次/分钟），
 * 与既有两前缀同档，防新别名成为绕过限流的旁路。</p>
 */
@RestController
@RequestMapping({"/api/v1/data-masking", "/api/v1/security/masking", "/api/v1/security/mask"})
public class DataMaskingController {

    private static final Logger log = LoggerFactory.getLogger(DataMaskingController.class);

    private final DataMaskingService service;

    public DataMaskingController(DataMaskingService service) {
        this.service = service;
    }

    @GetMapping("/demo")
    public ApiResponse<Map<String, Object>> demo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("samples", service.getDemoSamples());
        result.put("supportedRules", service.getSupportedRules());
        result.put("description", "数据脱敏示例：展示三种内置规则的原始值与脱敏值对照");
        return ApiResponse.success(result);
    }

    @PostMapping("/apply")
    public ApiResponse<?> apply(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> data = (List<String>) body.get("data");
            @SuppressWarnings("unchecked")
            List<String> rules = (List<String>) body.get("rules");

            if (data == null || data.isEmpty()) {
                return ApiResponse.badRequest("data 不能为空");
            }
            if (rules == null || rules.isEmpty()) {
                return ApiResponse.badRequest("rules 不能为空");
            }

            if (rules.size() != 1 && rules.size() != data.size()) {
                return ApiResponse.badRequest(
                        "rules 长度必须为 1（统一规则）或与 data 一一对应，当前 data="
                                + data.size() + ", rules=" + rules.size());
            }

            List<Map<String, Object>> results = service.applyMasking(data, rules);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", results.size());
            result.put("results", results);
            return ApiResponse.success(result);

        } catch (ClassCastException e) {
            log.error("数据脱敏请求格式错误", e);
            return ApiResponse.badRequest("请求格式错误: data 和 rules 必须为字符串数组");
        } catch (Exception e) {
            log.error("数据脱敏处理异常", e);
            return ApiResponse.internalError("脱敏处理失败");
        }
    }
}
