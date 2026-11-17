package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.service.dashboard.BizDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 经营看板聚合端点（D33）。
 *
 * <p><b>背景</b>：前端 {@code #/biz_dashboard}（{@code BizDashboard.tsx}）与
 * {@code #/ops_dashboard}（{@code OperationsDashboard.tsx}）共用 {@code fetchBizDashboard()}
 * → {@code GET /api/v1/ecos/biz/dashboard}，但该端点后端从未实现（live 404）；
 * 其真实数据源 {@code ecos_cognitive.ecos_biz_contract/project/metric/target} 四表
 * 在库中真实存在（contract 5 / project 3 行），却零读取方 ⇒ 两看板恒不可用。</p>
 *
 * <p><b>为何用 cognitive 前缀</b>：{@code /api/v1/ecos} 整根属主不唯一（route-manifest 明文
 * 「只登记子族，禁把整根判给单一属主」），新增 {@code ecos/biz} 子族须改共享路由表；
 * 而 {@code /api/v1/cognitive/**} 已由本模块承流（gateway + aiming 双宿主），
 * 新增子路径零路由改动、零 schema 改动（只读既有表）。</p>
 *
 * <p><b>契约</b>：返回 {@code ApiResponse<Map>}，字段对齐前端 {@code BizData}（biz 页）与
 * {@code DashboardData}（ops 页）：{@code departments/projectStats/contractStats/metrics/targets}
 * + {@code monthlyTrend/contractTrend}（ops 复用）。只增不改。</p>
 *
 * @author ecos-factory
 * @since D33 (2026-11-16)
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class BizDashboardController {

    private final BizDashboardService bizDashboardService;

    public BizDashboardController(BizDashboardService bizDashboardService) {
        this.bizDashboardService = bizDashboardService;
    }

    /** GET /api/v1/cognitive/biz-dashboard — 经营看板聚合（biz + ops 两页共用）。 */
    @GetMapping("/biz-dashboard")
    public ApiResponse<Map<String, Object>> bizDashboard() {
        return ApiResponse.success(bizDashboardService.dashboard());
    }
}
