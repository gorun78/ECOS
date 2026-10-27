package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.service.DataFreshnessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据新鲜度端点（WAgent Readiness data.staleness_days 供数面）。
 *
 * <p>挂载于 {@code /api/v1/engine/data/freshness}（与 {@link DataEngineStatusController}
 * 同族，保持 {@code /api/v1/engine/data/} 前缀）。响应 = {@code ApiResponse<Map<String,Object>>}，
 * data 段键面：{@code lastSucceededFinishedAt}（ISO-8601 字符串或 {@code null}）+
 * {@code source = "ecos_pipeline_run"}。无任何成功管道运行时为 {@code null}
 * ——"没有数据"必须由 null 表达，禁止 0 值或估算。</p>
 *
 * <p>鉴权：不特殊处理，落到 {@code anyRequest().authenticated()} 默认闸，
 * 不登记 AnonymousEndpointRegistry（该端点属业务数据面，非 health 匿名面）。</p>
 */
@RestController
@RequestMapping("/api/v1/engine/data")
public class DataFreshnessController {

    private final DataFreshnessService freshnessService;

    public DataFreshnessController(DataFreshnessService freshnessService) {
        this.freshnessService = freshnessService;
    }

    @GetMapping("/freshness")
    public ApiResponse<Map<String, Object>> freshness() {
        return ApiResponse.success(freshnessService.currentFreshness());
    }
}
