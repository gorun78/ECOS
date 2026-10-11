package com.chinacreator.gzcm.engine.data.fact.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.fact.service.PeriodCloseService;
import com.chinacreator.gzcm.engine.data.model.PeriodCloseRequestVO;
import com.chinacreator.gzcm.engine.data.model.PeriodCloseResultVO;
import com.chinacreator.gzcm.engine.data.model.PeriodStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 期间关账 REST 面 — 详细设计-09 v1.2 §十一 H-3 交付①（只增不改）。
 * <ul>
 *   <li>{@code POST /api/v1/datanet/facts/periods/{yyyMM}/close} — closeDataPeriod
 *       （幂等：重复关账 200 + alreadyClosed=true；财务角色门槛=security-engine OPA，
 *       DENY/不可用 → 403 fail-closed；新关闭发 ecos.audit 事件）</li>
 *   <li>{@code GET  /api/v1/datanet/facts/periods/{yyyMM}} — getDataPeriodStatus（诊断/前端置灰）</li>
 * </ul>
 *
 * <p>路径挂在既有 {@code /api/v1/datanet/facts} 前缀下 = 沿用现域三滤波器口径
 * （DataEndpointTriFilterTest：datanet 业务路径禁 permitAll，默认 DENY）；
 * gateway 侧登记属主会话（交付报告开放项）。
 *
 * <p>关账只写控制域状态行（V278），不触达任何事实/运行历史数据（PRD-09 §5.1 永不覆盖）。
 */
@RestController
@RequestMapping("/api/v1/datanet/facts")
public class PeriodCloseController {

    private final PeriodCloseService periodCloseService;

    public PeriodCloseController(PeriodCloseService periodCloseService) {
        this.periodCloseService = periodCloseService;
    }

    /** closeDataPeriod — 关账（幂等；403 fail-closed；400 非法期间形态）。 */
    @PostMapping("/periods/{yyyMM}/close")
    public ApiResponse<PeriodCloseResultVO> closeDataPeriod(@PathVariable("yyyMM") String yyyMM,
                                                            @RequestBody(required = false) PeriodCloseRequestVO req) {
        return ApiResponse.success(periodCloseService.close(yyyMM, req));
    }

    /** getDataPeriodStatus — 期间状态（未登记=OPEN；DB 不可读=降级配置期锁 + degraded=true）。 */
    @GetMapping("/periods/{yyyMM}")
    public ApiResponse<PeriodStatusVO> getDataPeriodStatus(@PathVariable("yyyMM") String yyyMM) {
        return ApiResponse.success(periodCloseService.status(yyyMM));
    }
}
