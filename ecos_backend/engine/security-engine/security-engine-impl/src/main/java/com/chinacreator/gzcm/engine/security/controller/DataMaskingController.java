package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService.MaskResult;
import com.chinacreator.gzcm.engine.security.service.DataMaskingService.MaskSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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
 *
 * <p><b>#147CG（DoD DI-2 第十一批）</b>：出入参强类型化——请求体改 {@link MaskApplyRequest}
 * （{@code data}/{@code rules} 仍按「字符串数组」校验，非字符串元素 → 400 同旧码），
 * 响应改 {@link MaskDemoVO}/{@link MaskApplyResultVO}；属主 {@link DataMaskingService}
 * 的样例/结果同步改 record（{@code error} 仍只在未知规则时出现）。键面逐字不变。</p>
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
    public ApiResponse<MaskDemoVO> demo() {
        return ApiResponse.success(new MaskDemoVO(service.getDemoSamples(),
                service.getSupportedRules(),
                "数据脱敏示例：展示三种内置规则的原始值与脱敏值对照"));
    }

    @PostMapping("/apply")
    public ApiResponse<MaskApplyResultVO> apply(@RequestBody MaskApplyRequest body) {
        try {
            List<String> data = asStringList(body.getData(), "data");
            List<String> rules = asStringList(body.getRules(), "rules");

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

            List<MaskResult> results = service.applyMasking(data, rules);
            return ApiResponse.success(new MaskApplyResultVO(results.size(), results));
        } catch (IllegalArgumentException e) {
            log.error("数据脱敏请求格式错误", e);
            return ApiResponse.badRequest("请求格式错误: data 和 rules 必须为字符串数组");
        } catch (Exception e) {
            log.error("数据脱敏处理异常", e);
            return ApiResponse.internalError("脱敏处理失败");
        }
    }

    /** 字符串数组校验（保留旧「非字符串元素 ⇒ 400」口径；null 表示未提供）。 */
    private static List<String> asStringList(List<Object> raw, String field) {
        if (raw == null) {
            return null;
        }
        List<String> out = new ArrayList<>(raw.size());
        for (Object value : raw) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException(field + " 必须是字符串数组");
            }
            out.add(text);
        }
        return out;
    }

    /** 脱敏示例回执（3 键，键序不变）。 */
    public record MaskDemoVO(List<MaskSample> samples, List<String> supportedRules, String description) {
    }

    /** 批量脱敏回执（2 键）。 */
    public record MaskApplyResultVO(int total, List<MaskResult> results) {
    }

    /** 批量脱敏请求（{@code data}/{@code rules} 为字符串数组，元素类型在此显式校验）。 */
    public static final class MaskApplyRequest {
        private List<Object> data;
        private List<Object> rules;

        public List<Object> getData() {
            return data;
        }

        public void setData(List<Object> data) {
            this.data = data;
        }

        public List<Object> getRules() {
            return rules;
        }

        public void setRules(List<Object> rules) {
            this.rules = rules;
        }
    }
}
