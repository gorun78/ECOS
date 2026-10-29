package com.chinacreator.gzcm.workspace.backtest;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 回测五指标契约承流面（#146aa 附录A 第 33 条 {@code listForecastBacktests}，
 * 契约行 = 详设 line 8267：{@code GET /api/v1/workspace/backtests?period=&granularity=}）。
 *
 * <p><b>本类只补承流面，不补能力</b>：粒度词表 400 逐字段、RLS 403/503 两腿做实
 * （{@link BacktestReadService}）；五指标读数面缺载体恒成立 ⇒ 501 {@code STUB_CAPABILITY}
 * ＋ {@code data.stub=true} ＋ {@code data.missing[]} 可复验取证（判定不翻转，同 #146w 口径）。
 * 类级前缀 {@code /api/v1/workspace/backtests} 独占（同基路径禁双 Controller 纪律：
 * {@code ScenarioActionController} 的 {@code /api/v1/workspace} 裸前缀面一字未动）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/backtests")
public class BacktestContractController {

    private final BacktestReadService readService;

    public BacktestContractController(BacktestReadService readService) {
        this.readService = readService;
    }

    /**
     * 回测五指标列表（契约「400/403」两腿做实；501 承流腿替代 200 空列表伪装）。
     *
     * @throws ActionValidationException            granularity 词表不合法（400 逐字段）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws SecurityEngineUnavailableException   security-engine 不可用（503，不可用≠无权限）
     * @throws BacktestNotLiveException             缺载体未上线（501，stub=true + missing 取证）
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listForecastBacktests(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "granularity", required = false) String granularity) {
        readService.list(period, granularity);
        return null; // 不可达：list 恒先抛承载（501/400/403/503），禁在读数缺位时回 200 空体
    }
}
