package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.dto.EvalRunRequest;
import com.chinacreator.gzcm.engine.kb.dto.KnowledgeEvalReportVO;
import com.chinacreator.gzcm.engine.kb.service.KnowledgeEvalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识检索质量评估控制器 — {@code POST /api/v1/knowledge/eval/run}
 * （方案 §6.2 K6 检索质量评估 / 附录 B eval Tab，B5-2 D6）。
 *
 * <p>指标由 {@link KnowledgeEvalService} 基于真实向量检索链路计算，不返回硬编码数据；
 * 数据不足时以 {@code degraded=true} 如实降级（前端据此回落本地评测）。</p>
 */
@RestController
@RequestMapping("/api/v1/knowledge/eval")
public class KnowledgeEvalController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEvalController.class);

    private final KnowledgeEvalService evalService;

    public KnowledgeEvalController(KnowledgeEvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * 运行一次检索质量评估（Recall@K / MRR@K / nDCG@K）。
     *
     * @param req 入参（可空；前端当前传 {seedSetName}）
     * @return 评估报告
     */
    @PostMapping("/run")
    public ApiResponse<KnowledgeEvalReportVO> run(@RequestBody(required = false) EvalRunRequest req) {
        try {
            return ApiResponse.success(evalService.runEval(req));
        } catch (Exception e) {
            log.error("知识检索质量评估失败: {}", e.getMessage(), e);
            return ApiResponse.internalError("评估失败: " + e.getMessage());
        }
    }
}
