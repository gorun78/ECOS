package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.dto.AssetProposalDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.asset.CognitiveAssetLifecycleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认知资产目录 / 只读解析 / 受控提案 REST（《认知域与智能域补充详细设计 v1.0》§8、§13 SUP-COG-01/02/03/04）。
 *
 * <p><b>路径池（§13 单源登记；类前缀 {@link CognitiveApiPaths#COGNITIVE} = /api/v1/cognitive）</b>：</p>
 * <pre>
 * GET  /api/v1/cognitive/assets/catalog               — C 类 11 对象 + §8.1 A/B/C 三层表（只读）
 * GET  /api/v1/cognitive/assets/{assetId}/resolve     — 按 bel/hyp/mod 前缀路由到对应 DAO（只读）
 * POST /api/v1/cognitive/assets/{assetId}/proposals   — 只创建候选 / 审计留痕（SUP-COG-03）
 * </pre>
 *
 * <p><b>SUP-COG-01</b>：目录来源只走 {@link CognitiveAssetLifecycleService#catalog()} 单源装配
 * （内部从 CognitiveObjectCatalogService 枚举 + §8.1 三层挂载属性），本 Controller 不持第二套清单。</p>
 *
 * <p><b>SUP-COG-03</b>：proposals 只通过 {@link CognitiveAssetLifecycleService#propose} 走
 * 审计事件通道；原资产行 0 改、行数 0 变（落点口径见该服务 javadoc「候选落点口径」段）；
 * 响应只回 {@code proposalId} 与 {@code status=PENDING_APPROVAL}，不复读原资产行。</p>
 *
 * <p>三滤波器核对：类前缀 {@code /api/v1/cognitive} 已在 VersionPrefixRewriteFilter KEEP，
 * 无新 ROUTE_MANIFEST 条目；SecurityConfig {@code /api/v1/cognitive/**} 已覆盖；
 * ClearanceInterceptor 经 {@code /api/v1/} 规则；错误矩阵由
 * {@code CognitiveErrorAdvice}（basePackages 含本包 {@code cognitive2.controller}）承接。</p>
 */
@RestController("ecosCognitiveAssetCatalogController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveAssetCatalogController {

    private final CognitiveAssetLifecycleService lifecycleService;

    public CognitiveAssetCatalogController(CognitiveAssetLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /** GET catalog（SUP-COG-01；只读、零 SQL）。 */
    @GetMapping(CognitiveApiPaths.ASSETS_CATALOG)
    public ApiResponse<Map<String, Object>> catalog() {
        return ApiResponse.success(lifecycleService.catalog());
    }

    /** GET resolve（资产引用解析；bel/hyp/mod 前缀路由，查不到 404，非法前缀 400）。 */
    @GetMapping(CognitiveApiPaths.ASSETS_RESOLVE)
    public ApiResponse<Map<String, Object>> resolve(
            @PathVariable(CognitiveApiPaths.PARAM_ASSET_ID) String assetId) {
        return ApiResponse.success(lifecycleService.resolve(assetId));
    }

    /** POST proposals（SUP-COG-03；候选 = 审计事件 + CognitiveCandidateProposed 通知，禁改原资产行）。 */
    @PostMapping(CognitiveApiPaths.ASSETS_PROPOSALS)
    public ApiResponse<Map<String, Object>> propose(
            @PathVariable(CognitiveApiPaths.PARAM_ASSET_ID) String assetId,
            @RequestBody AssetProposalDTO proposal) {
        Map<String, Object> body = proposal == null
                ? null
                : dtoToProposalMap(proposal);
        return ApiResponse.success(lifecycleService.propose(assetId, body));
    }

    /**
     * POST transition（§8.4 受控状态迁移）：合法迁移才落 {@code lifecycle_state} 并发
     * {@code CognitiveAssetStatusChanged}；非法迁移 ⇒ 409 {@code ECOS-COG-409S}
     * （原行不变，§13.1「业务前置条件未满足 → 409」）。
     */
    @PostMapping(CognitiveApiPaths.ASSETS_TRANSITION)
    public ApiResponse<Map<String, Object>> transition(
            @PathVariable(CognitiveApiPaths.PARAM_ASSET_ID) String assetId,
            @RequestBody AssetTransitionRequest request) {
        if (request == null) {
            throw new com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException(
                    com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.E400_INVALID_PARAM,
                    com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode.MSG_INVALID_PARAM, "body");
        }
        return ApiResponse.success(lifecycleService.transition(assetId, request.getTargetState(),
                request.getReason(), request.getEvidenceRef(), request.getOperator()));
    }

    /** AssetProposalDTO → 生命周期服务期望的 Map 单源（避免 impl 里 DTO → Map 双分裂）。 */
    private static Map<String, Object> dtoToProposalMap(AssetProposalDTO dto) {
        if (dto == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("targetState", dto.getTargetState());
        m.put("reason", dto.getReason());
        m.put("evidenceRef", dto.getEvidenceRef());
        m.put("operator", dto.getOperator());
        m.put("note", dto.getNote());
        return m;
    }

    /** 状态迁移请求体（§8.4 四要件：operator / reason / evidenceRef + traceId 由服务端生成）。 */
    @lombok.Data
    public static class AssetTransitionRequest {
        /** 目标治理状态（必填；CognitiveAssetStatus 词表） */
        private String targetState;
        /** 迁移原因（必填；SUPERSEDED 时须含 {@code supersededBy=<新版本引用>}） */
        private String reason;
        /** 证据引用（可空） */
        private String evidenceRef;
        /** 操作者身份（可空，缺省 system） */
        private String operator;
    }
}
