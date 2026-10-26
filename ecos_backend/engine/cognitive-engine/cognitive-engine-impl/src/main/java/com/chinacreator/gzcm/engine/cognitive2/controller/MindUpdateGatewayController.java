package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindUpdateVO;
import com.chinacreator.gzcm.engine.cognitive2.service.mind.MindAggregateService;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * R4 Mind 聚合根读面 + 候选更新闸门 REST（详设 v2.1-R4 §8.1.4/§8.4.2 §5685/§7922 /
 * 差距分析 P0-6 / B3 认知治理批 #141）。
 *
 * <p><b>路径池（§13 单源登记；类前缀 {@link CognitiveApiPaths#COGNITIVE} = /api/v1/cognitive）</b>：</p>
 * <pre>
 * GET  /api/v1/cognitive/minds                             — Mind 列表（三轴可空；r4ColumnsProvisioned 明示）
 * GET  /api/v1/cognitive/minds/{mindId}                    — Mind 详情（六段投影 + 三轴 + 版本历史可空回显）
 * POST /api/v1/cognitive/minds/{mindId}/proposals          — 提交候选（恒落 PROPOSED，W Agent 通道等价字面）
 * GET  /api/v1/cognitive/minds/{mindId}/proposals          — 按 Mind 列候选（倒序，limit 默认 20 上限 100）
 * GET  /api/v1/cognitive/minds/proposals/{updateId}        — 回读单行
 * PUT  /api/v1/cognitive/minds/proposals/{updateId}/status — 受控置终态 ACCEPTED/REJECTED（唯一终态写入口）
 * </pre>
 *
 * <p>与既有 {@link com.chinacreator.gzcm.engine.cognitive2.service.asset.CognitiveAssetLifecycleService}
 * 资产提案族区分：本端点族属 <b>Mind 聚合根</b>（V292 三轴 + 六段）候选闸门，
 * 表 = {@code ecos_cognitive_mind_update}，词表 = {@link MindUpdateVO#TARGET_TYPES}；
 * 资产提案族是 SUP-COG-03 审计事件通道，两族不同表不同端点（{@code MINDS_*} vs {@code ASSETS_*}）。</p>
 *
 * <p><b>三滤波器核对</b>（同 {@code ForecastRunComputeController} KEEP 先例）：
 * 类前缀 {@code /api/v1/cognitive} 已在 VersionPrefixRewriteFilter KEEP，无新 rewrite 条目；
 * 默认 {@code anyRequest().authenticated()}（AnonymousEndpointRegistry cognitive 面 0 条），写端点不强开匿名；
 * ClearanceInterceptor 经 {@code /api/v1/} 规则；错误矩阵由 {@code CognitiveErrorAdvice}
 * （basePackages 含本包 {@code cognitive2.controller}）承接，全走 {@code CognitiveErrorCode}。</p>
 */
@RestController("ecosCognitiveMindGatewayController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class MindUpdateGatewayController {

    private final MindAggregateService service;

    public MindUpdateGatewayController(MindAggregateService service) {
        this.service = service;
    }

    /** Mind 列表（#/decision/mind 列表态；三轴按 r4ColumnsProvisioned 可空回显）。 */
    @GetMapping(CognitiveApiPaths.MINDS_REL)
    public ApiResponse<Map<String, Object>> listMinds(
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(service.mindList(limit));
    }

    /** Mind 详情（六段投影；V292 未迁移的三轴/版本字段显式 null，禁假值。 */
    @GetMapping(CognitiveApiPaths.MINDS_REL + "/{" + CognitiveApiPaths.PARAM_MIND_ID + "}")
    public ApiResponse<Map<String, Object>> mindView(
            @PathVariable(CognitiveApiPaths.PARAM_MIND_ID) String mindId) {
        return ApiResponse.success(service.mindView(mindId));
    }

    /** 提交候选（恒 PROPOSED；mind 缺失/词表违例/变更体空白 ⇒ 400）。 */
    @PostMapping(CognitiveApiPaths.MINDS_SUBMIT_REL)
    public ApiResponse<MindUpdateVO> submit(
            @PathVariable(CognitiveApiPaths.PARAM_MIND_ID) String mindId,
            @RequestBody MindProposalRequest req) {
        if (req == null) {
            throw new MindNullBody();
        }
        return ApiResponse.success(service.submitProposal(mindId, req.getAgentRunId(),
                req.getTargetType(), req.getTargetId(), req.getChangeJson(),
                req.getEvidenceRefsJson(), req.getOperator()));
    }

    /** 该 Mind 的候选队列（倒序；limit 默认 20 上限 100，DAO 侧夹取）。 */
    @GetMapping(CognitiveApiPaths.MINDS_PROPOSED_REL)
    public ApiResponse<List<MindUpdateVO>> list(
            @RequestParam(CognitiveApiPaths.PARAM_MIND_ID) String mindId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(service.listProposals(mindId, limit));
    }

    /** 回读单行（未找到/软删 ⇒ 404 ECOS-COG-404U；DAO 已承载）。 */
    @GetMapping(CognitiveApiPaths.MINDS_ITEM_REL)
    public ApiResponse<MindUpdateVO> get(
            @PathVariable(CognitiveApiPaths.PARAM_UPDATE_ID) String updateId) {
        return ApiResponse.success(service.getProposal(updateId));
    }

    /** 受控置终态（已终态 ⇒ 409 ECOS-COG-409U；reason 必填留痕四要件）。 */
    @PutMapping(CognitiveApiPaths.MINDS_ITEM_STATUS_REL)
    public ApiResponse<MindUpdateVO> finalize(
            @PathVariable(CognitiveApiPaths.PARAM_UPDATE_ID) String updateId,
            @RequestBody MindFinalizeRequest req) {
        if (req == null) {
            throw new MindNullBody();
        }
        return ApiResponse.success(service.finalizeProposal(updateId,
                req.getStatus(), req.getReviewReason(), req.getOperator()));
    }

    /** null body → 400（同 CognitiveAssetCatalogController.transition 先例）。 */
    private static final class MindNullBody extends
            com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException {
        MindNullBody() {
            super(com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.E400_INVALID_PARAM,
                    com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_INVALID_PARAM,
                    "body");
        }
    }

    /** 提交候选请求体（四要件 + operator 可空走 UserContext 单源）。 */
    @Data
    public static class MindProposalRequest {
        /** 提出候选的 W Agent 运行号（必填；可空=400）。 */
        private String agentRunId;
        /** 候选作用对象（必填；词表 {@link MindUpdateVO#TARGET_TYPES}）。 */
        private String targetType;
        /** 候选作用目标行 id（可空=新增型候选）。 */
        private String targetId;
        /** 候选变更全文 TEXT JSON（必填；服务端不解析，原样落库回显）。 */
        private String changeJson;
        /** 证据引用数组 TEXT JSON（可空，DAO 侧默认 "[]"）。 */
        private String evidenceRefsJson;
        /** 操作者（可空，缺省取 UserContext → "system"）。 */
        private String operator;
    }

    /** 置终态请求体（status ∈ {ACCEPTED, REJECTED}；reason 必填留痕）。 */
    @Data
    public static class MindFinalizeRequest {
        /** 目标终态（必填；非 ACCEPTED/REJECTED ⇒ 400）。 */
        private String status;
        /** 复核原因（必填留痕；空白 ⇒ 400）。 */
        private String reviewReason;
        /** 复核人（可空，缺省 UserContext → "system"）。 */
        private String operator;
    }
}
