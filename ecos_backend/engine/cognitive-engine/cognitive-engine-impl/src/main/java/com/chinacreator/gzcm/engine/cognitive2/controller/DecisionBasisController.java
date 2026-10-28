package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.service.decision.DecisionBasisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Decision Basis 只读解析 REST API（差距分析附录A 第 19 条；契约行 = 详设接口总表第 5416 行
 * {@code GET /api/v1/cognitive/decision-basis/{decisionId}} · {@code getDecisionBasis}，
 * 档位 B，错误面 404/504）。
 *
 * <h3>零 SQL / 零业务计算</h3>
 * 本类只做参数绑定与信封封装，解析语义单源在 {@link DecisionBasisService}
 * （三段诚实口径、时间锚定拒绝、列面取证全部在那一侧，此处不得出现第二套口径）。
 *
 * <h3>只增不改</h3>
 * 类级前缀复用冻结常量 {@link CognitiveApiPaths#COGNITIVE}，方法级只用相对段
 * {@link CognitiveApiPaths#DECISION_BASIS_REL}（绝对常量 {@link CognitiveApiPaths#DECISION_BASIS}
 * 仅供契约/文档面引用，直接挂到方法级会注册成双前缀路径——SUP-D 已实测教训）。
 * 既有 {@code /api/v1/cognitive/decision/**}（{@code DecisionController} legacy 面）
 * 与 {@code /provenance} 面的路径、签名、载荷一字未动。
 *
 * <h3>只读面</h3>
 * 唯一 handler 是 {@code @GetMapping}，全类零写映射；错误矩阵由 {@code CognitiveErrorAdvice}
 * （basePackages 含本包）承接：404 {@code ECOS-COG-404D}（决策不存在 / pinned 引用无法锚定）、
 * 400 {@code ECOS-COG-400P}（入参结构非法）、SQL 异常 503 {@code ECOS-COG-501}、
 * 其余 500 {@code ECOS-COG-504}。契约错误面的 504 由认知内部错误码承载，本批不新增超时通道
 * （只读单库点查，无外部依赖腿）。
 */
@RestController("ecosCognitiveDecisionBasisController")
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class DecisionBasisController {

    private final DecisionBasisService decisionBasisService;

    public DecisionBasisController(DecisionBasisService decisionBasisService) {
        this.decisionBasisService = decisionBasisService;
    }

    /**
     * 解析一条决策的 Decision Basis（只读，无写回）。
     *
     * @param decisionId 认知决策底座主键（路径段）
     * @param version    Agent 引用的 pinned 版本（查询段，可缺省）
     * @param hash       Agent 引用的 pinned hash（查询段，可缺省）
     */
    @GetMapping(CognitiveApiPaths.DECISION_BASIS_REL)
    public ApiResponse<Map<String, Object>> getDecisionBasis(
            @PathVariable(CognitiveApiPaths.PARAM_DECISION_ID) String decisionId,
            @RequestParam(value = CognitiveApiPaths.PARAM_VERSION, required = false) String version,
            @RequestParam(value = CognitiveApiPaths.PARAM_HASH, required = false) String hash) {
        return ApiResponse.success(decisionBasisService.resolve(decisionId, version, hash));
    }
}
