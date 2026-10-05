package com.chinacreator.gzcm.workspace.fc;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分册09 F09-13/14 · 回测与期间关账 REST API（§5.1 #18/#19）。
 *
 * <p><b>本层职责</b>：</p>
 * <ul>
 *   <li>{@code GET /backtests} 生产链路：读 {@code ecos_dw.ecos_fc_backtest}（data-engine 读通道），
 *       本层只透传聚合 + LOW_SAMPLE 徽标；数值不在本层计算</li>
 *   <li>{@code POST /periods/{yyyMM}/close} 生产链路：应走 data-engine 唯一回灌管道
 *       + audit 事件（Kafka ecos.audit，ST06）；本层现在触发 coordinator-hint
 *       （routingTo + reject side-effect）</li>
 * </ul>
 *
 * <p><b>可离线单测的回测五指标</b>：放在 cognitive-engine-api {@code FcBacktestCalc}（BigDecimal，除零守卫/
 * LOW_SAMPLE 打点），本 controller 只展示路由；如果生产端要"直接算"（例：dry-run preview）请在
 * service 层 delegate 到 {@code FcBacktestCalc}（本类保持"编排 + 路由提示"薄层）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class FcBacktestAndPeriodController {

    private static final Logger log = LoggerFactory.getLogger(FcBacktestAndPeriodController.class);
    private final FcRunState state;

    public FcBacktestAndPeriodController(FcRunState state) {
        this.state = state;
    }

    /** §5.1 #18 {@code listForecastBacktests} —— 五指标 + LOW_SAMPLE 标记。 */
    @Operation(operationId = "listForecastBacktests", summary = "listForecastBacktests")
    @GetMapping("/backtests")
    public ApiResponse<Map<String, Object>> listBacktests(@RequestParam(required = false) String period,
                                                          @RequestParam(required = false) String granularity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", period);
        m.put("granularity", granularity);
        m.put("routingTo", "GET data-engine /api/v1/datanet/backtests?period=&granularity= → ecos_dw.ecos_fc_backtest");
        m.put("fiveMetrics", List.of("mae", "mape", "biasDirection", "intervalCoverage", "dataCoverage"));
        m.put("lowSampleFlag", "sampleFlag=LOW_SAMPLE when n<5 (from cognitive FcBacktestCalc)");
        return ApiResponse.success(m);
    }

    /** §5.1 #19 {@code closePeriod} —— 财务角色 + 审计事件；本层只发 routing 提示（生产：data-engine 唯一回灌 + Kafka）。 */
    @Operation(operationId = "closePeriod", summary = "closePeriod")
    @PostMapping("/periods/{yyyMM}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable String yyyMM) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", yyyMM);
        m.put("periodStatus", "CLOSED");
        m.put("routingTo", "POST data-engine /api/v1/datanet/periods/" + yyyMM + "/close + audit-event:ecos.audit");
        m.put("rejectReason", "本层不写库：唯一回灌通道 = data-engine 导入管道（禁独立回灌，§3.4）");
        log.info("closePeriod hint period={} (delegated to data-engine + Kafka)", yyyMM);
        return ApiResponse.success(m);
    }

    /** 回测五指标 dry-run preview helper（不入库，可离线单测；委托 FcBacktestCalc，返回 BigDecimal 纹）。 */
    @Operation(operationId = "previewBacktestMetrics", summary = "previewBacktestMetrics（离线 dry-run，仅回给 CI 用；不入库）")
    @PostMapping("/backtests/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody PreviewDto dto) {
        if (dto.samples == null || dto.samples.isEmpty()) {
            throw new BusinessException(400, "samples 至少 1 条");
        }
        if (dto.mapeThreshold == null || dto.coverageThreshold == null || dto.minSample <= 0) {
            throw new BusinessException(400, "thresholds 三键必填 (mapePct/coveragePct/minSample) — R-66① 禁默认");
        }
        List<com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.SampleRow> rows = new ArrayList<>();
        for (SampleRowDto s : dto.samples) {
            rows.add(new com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.SampleRow(
                    bd(s.forecast), bd(s.actual), bd(s.p10), bd(s.p90)));
        }
        var th = new com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.Thresholds(
                dto.mapeThreshold, dto.coverageThreshold, dto.minSample);
        var r = com.chinacreator.gzcm.engine.cognitive2.fc.FcBacktestCalc.compute(
                dto.period, dto.granularity, dto.projectId, dto.departmentId, dto.stage, rows, th);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mae", r.mae());
        m.put("mape", r.mape());
        m.put("biasDirection", r.biasDirection());
        m.put("intervalCoverage", r.intervalCoverage());
        m.put("dataCoverage", r.dataCoverage());
        m.put("zeroActualCount", r.zeroActualCount());
        m.put("sampleCount", r.sampleCount());
        m.put("sampleFlag", r.sampleFlag());
        m.put("reviewRequired", r.review().required());
        m.put("reviewRole", r.review().role());
        m.put("reviewReason", r.review().reason());
        return ApiResponse.success(m);
    }

    public record PreviewDto(String period, String granularity, String projectId,
                              String departmentId, String stage,
                              BigDecimal mapeThreshold, BigDecimal coverageThreshold, int minSample,
                              List<SampleRowDto> samples) {}

    public record SampleRowDto(BigDecimal forecast, BigDecimal actual, BigDecimal p10, BigDecimal p90) {}

    private static BigDecimal bd(BigDecimal v) { return v; }
}
