package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.cognitive.HypothesisVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.HypothesisSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveHypothesisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认知假设 REST API — 心智层 H 库（PMO-59 P2a / ADR-9 外部方案层3.1"假设管理模块"）。
 *
 * <pre>
 * GET  /api/v1/cognitive/hypotheses                  — 假设列表（domain/status 可选过滤）
 * GET  /api/v1/cognitive/hypotheses/{id}             — 假设详情（含证据引用与失效状态）
 * POST /api/v1/cognitive/hypotheses                  — 注册假设（初始 VALID，evidence 引用绑定）
 * POST /api/v1/cognitive/hypotheses/{id}/invalidate  — 人工标记失效（专家兜底；自动失效检测 P2b）
 * </pre>
 *
 * <p>P2a 边界：本单只做状态切换 + 失效时间写；证据触发失效的自动检测 / 下游推演作废 /
 * 告警链路 P2b 落地（IHypothesisLifecycleService.detectInvalidation 语义的端点位届时接入）。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive/hypotheses")
public class CognitiveHypothesisController {

    private final CognitiveHypothesisService hypothesisService;

    public CognitiveHypothesisController(CognitiveHypothesisService hypothesisService) {
        this.hypothesisService = hypothesisService;
    }

    /** 假设列表（domain 过滤推演装配；status 可选：VALID / INVALIDATED / ARCHIVED）。 */
    @GetMapping
    public ApiResponse<List<HypothesisVO>> list(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(hypothesisService.list(domain, status));
    }

    /** 假设详情；无记录 → 404。 */
    @GetMapping("/{id}")
    public ApiResponse<HypothesisVO> getDetail(@PathVariable String id) {
        HypothesisVO vo = hypothesisService.getDetail(id);
        if (vo == null) {
            return ApiResponse.notFound("认知假设不存在: id=" + id);
        }
        return ApiResponse.success(vo);
    }

    /** 注册一条假设（hypothesis_code 幂等）。 */
    @PostMapping
    public ApiResponse<HypothesisVO> register(@RequestBody HypothesisSaveDTO dto) {
        return ApiResponse.success(hypothesisService.register(dto));
    }

    /** 人工标记失效（body: {"reason":"..." } 必填，留痕审计）。 */
    @PostMapping("/{id}/invalidate")
    public ApiResponse<HypothesisVO> invalidate(@PathVariable String id,
                                                 @RequestBody Map<String, String> body) {
        return ApiResponse.success(hypothesisService.invalidate(id, body.get("reason")));
    }
}
