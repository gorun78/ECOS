package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/data-masking")
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
            return ApiResponse.internalError("脱敏处理失败: " + e.getMessage());
        }
    }
}
