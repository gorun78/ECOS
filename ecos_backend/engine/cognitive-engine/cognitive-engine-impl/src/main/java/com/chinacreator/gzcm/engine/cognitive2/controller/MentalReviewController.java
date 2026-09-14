package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.service.mental.MentalReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 周一故障复盘聚合 REST API（PMO-59 P3b T4 / 复盘口径定稿落地点）。
 *
 * <pre>
 * GET /api/v1/cognitive/mental-reviews?tag=P2b-mental-layer-review&since=2026-09-14T00:00:00
 *     — 按 reviewTag 白名单 + 时间下界聚合心智层复盘结构：
 *        belief 版本时间线 + 假设失效时间线 + 证据登记时间线 + run 作废留痕（V130）+ summary 计数。
 *        口径=三表版本链重建（Kafka 事件未落 PG，P3b T4 定稿二选一说明，见 MentalReviewService 类注）。
 * </pre>
 *
 * <p>三滤波器：{@code /api/v1/cognitive/**} 既有通配覆盖（P3a 核对结论沿用，零新增登记）；
 * 只读聚合端点，0 写库。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive")
public class MentalReviewController {

    private final MentalReviewService reviewService;

    public MentalReviewController(MentalReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * 复盘聚合（tag 必填白名单校验 / since 可选 ISO 时间下界）。
     *
     * <p>输出结构：{tag, since, reconstructionMode, beliefTimelines[], hypotheses[],
     * evidence[], runImpacts[], summary{} }——供周一故障复盘流水线消费
     * （用户确认决策 ②：faultContext/reviewTag 聚合口径本 Phase 定稿落地点）。</p>
     */
    @GetMapping("/mental-reviews")
    public ApiResponse<Map<String, Object>> mentalReviews(
            @RequestParam String tag,
            @RequestParam(required = false) String since) {
        return ApiResponse.success(reviewService.aggregate(tag, since));
    }
}
