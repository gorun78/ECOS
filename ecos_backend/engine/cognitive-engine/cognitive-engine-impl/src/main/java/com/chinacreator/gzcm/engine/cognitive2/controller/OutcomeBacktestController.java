package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.trace.EcosTraceContext;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.backtest.BacktestReviewRouteResult;
import com.chinacreator.gzcm.engine.cognitive.api.outcome.OutcomeEvaluateResult;
import com.chinacreator.gzcm.engine.cognitive2.service.backtest.BacktestSurfaceService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 场景年度经营预测回测面（详设-09 v1.2 §十一 H-4；挂在 §9 既有 {@code /outcomes} 族之下<b>只增子路径</b>）。
 *
 * <h3>只增不改的证据链</h3>
 * <ul>
 *   <li>既有四端点（{@code POST /outcomes/evaluate}、{@code GET /outcomes/{evaluationId}}、
 *       {@code GET /outcomes}、{@code GET /outcomes/by-run/{runId}}）留在
 *       {@code OutcomeEvaluateController}，路径/参数签名/响应语义零改动，本类不覆盖它们；</li>
 *   <li>类前缀复用冻结常量 {@link CognitiveApiPaths#COGNITIVE}，方法级用
 *       {@link CognitiveApiPaths#OUTCOMES_BACKTEST_REL} / {@link CognitiveApiPaths#OUTCOMES_REVIEW_TASKS_REL}
 *       （绝对=前缀+相对，同 {@code CognitiveAssetCatalogController} 先例），
 *       故本类源码内零 {@code /api} 字面量（{@code CognitiveContractSingleSourceTest} 门禁）；</li>
 *   <li>响应体 {@link OutcomeEvaluateResult} 只新增可空 {@code backtest} 段与行级可空字段，
 *       反序列化旧客户端忽略新字段即可。</li>
 * </ul>
 *
 * <h3>三滤波器影响分析（新增两个映射的结论：零登记、零 permitAll）</h3>
 * 两个新路径都落在既有 {@code /api/v1/cognitive/**} 命名空间内：
 * <ul>
 *   <li>{@code VersionPrefixRewriteFilter} 的 {@code V1_REWRITE_MAP} 无 {@code /api/v1/cognitive/} 条目
 *       （该族属 "KEEP v1 单路径"，与 {@code /api/v1/pipeline/}、{@code /api/v1/knowledge/} 同列注释先例）
 *       ⇒ 请求按原 URI 分发，新增子路径<b>不需要</b>新增改写条目；</li>
 *   <li>sysman {@code SecurityConfig} 的 permitAll 清单唯一来源是
 *       {@code AnonymousEndpointRegistry.permitAllPatterns()}，其中无任何 cognitive 条目
 *       ⇒ 两个新路径自然落到 {@code anyRequest().authenticated()} = <b>默认 DENY</b>，
 *       本批次未新增 permitAll（新增未登记路径会由 {@code AnonymousEndpointInventoryTest} 判 FAIL）；</li>
 *   <li>{@code ClearanceInterceptor} 按前缀规则 {@code {"/api/v1/", "1"}} 生效，
 *       是前缀匹配而非逐路径登记 ⇒ 新子路径自动纳入密级校验。</li>
 * </ul>
 *
 * <h3>禁前端传阈值（R-66 ①）—— 本类采用"显式 400 拒绝"</h3>
 * 两种可选做法：忽略+审计，或直接 400。此处选 <b>400</b>：阈值是复审结论的判据，
 * 静默忽略会让调用方误以为自己的口径生效（与 DG-9 fail-loud 相悖），
 * 而"忽略+审计"在同步请求里无法保证调用方读到审计。既有 {@code POST /outcomes/evaluate}
 * 的请求体本就不含阈值类参数（结构上不存在前端阈值通道），故其签名与行为保持零改动；
 * 本批两个新端点则对任何阈值形态的查询参数/请求体键名回
 * {@code ECOS-COG-400P}（400），绝不采信。
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class OutcomeBacktestController {

    private final BacktestSurfaceService backtestSurfaceService;

    public OutcomeBacktestController(BacktestSurfaceService backtestSurfaceService) {
        this.backtestSurfaceService = backtestSurfaceService;
    }

    /**
     * 回测指标视图：既有评估面（MAE/MAPE）+ 偏差方向 + 区间覆盖率 + 数据覆盖率 + {@code LOW_SAMPLE} 守卫态。
     * <p>阈值只来自 sysman 配置门面，逐枚生效来源在 {@code backtest.thresholdSources} 回显。</p>
     */
    @GetMapping(CognitiveApiPaths.OUTCOMES_BACKTEST_REL)
    public ApiResponse<OutcomeEvaluateResult> backtest(
            @PathVariable(CognitiveApiPaths.PARAM_EVALUATION_ID) String evaluationId,
            @RequestParam(required = false) Map<String, String> queryParams) {
        FrontendThresholdGuard.rejectQueryParams(queryParams);
        return ApiResponse.success(backtestSurfaceService.view(evaluationId));
    }

    /**
     * 复审任务路由（越限 ⇒ 经 runtime-task 建复审任务，角色只来自 security-engine 裁决）。
     * <p>裁决 DENY ⇒ 403、裁决链路不可用/未装配 ⇒ 503，两条路径均<b>不建任务</b>；
     * {@code LOW_SAMPLE} ⇒ 200 + {@code SUPPRESSED_BY_LOW_SAMPLE} 且不建任务（不给误导性结论）。</p>
     */
    @PostMapping(CognitiveApiPaths.OUTCOMES_REVIEW_TASKS_REL)
    public ApiResponse<BacktestReviewRouteResult> reviewTask(
            @PathVariable(CognitiveApiPaths.PARAM_EVALUATION_ID) String evaluationId,
            @RequestBody(required = false) JsonNode body,
            @RequestParam(required = false) Map<String, String> queryParams) {
        FrontendThresholdGuard.rejectQueryParams(queryParams);
        FrontendThresholdGuard.rejectBodyKeys(body);
        return ApiResponse.success(backtestSurfaceService.routeReview(
                evaluationId, EcosTraceContext.currentTraceId()));
    }

    /**
     * 阈值来源单源守卫（R-66 ①）：任何"阈值形态"的入参一律 400，认知侧永不采信。
     *
     * <p>识别口径 = 键名（非值）匹配下列形态：{@code threshold} / {@code min_samples} /
     * {@code on_target} / {@code deviation} / {@code coverage} / {@code band} / {@code samples}。
     * 递归覆盖嵌套对象与数组元素；请求体为数组/标量时同样逐键扫描。</p>
     */
    static final class FrontendThresholdGuard {

        private static final Pattern THRESHOLD_KEY = Pattern.compile(
                "(?i).*(threshold|min[_-]?samples?|on[_-]?target|deviation|coverage|band).*");

        private FrontendThresholdGuard() {
        }

        static void rejectQueryParams(Map<String, String> params) {
            if (params == null || params.isEmpty()) {
                return;
            }
            List<String> offenders = new ArrayList<>();
            for (String key : params.keySet()) {
                if (key != null && THRESHOLD_KEY.matcher(key).matches()) {
                    offenders.add("query." + key);
                }
            }
            reject(offenders);
        }

        static void rejectBodyKeys(JsonNode body) {
            if (body == null || body.isNull()) {
                return;
            }
            List<String> offenders = new ArrayList<>();
            walk(body, "", offenders);
            reject(offenders);
        }

        private static void walk(JsonNode node, String path, List<String> offenders) {
            if (node.isObject()) {
                node.fieldNames().forEachRemaining(name -> {
                    String at = path.isEmpty() ? name : path + "." + name;
                    if (THRESHOLD_KEY.matcher(String.valueOf(name)).matches()) {
                        offenders.add(at);
                    }
                    walk(node.get(name), at, offenders);
                });
            } else if (node.isArray()) {
                for (int i = 0; i < node.size(); i++) {
                    walk(node.get(i), path + "[" + i + "]", offenders);
                }
            }
        }

        private static void reject(List<String> offenders) {
            if (offenders.isEmpty()) {
                return;
            }
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM,
                    "FRONTEND_THRESHOLD_REJECTED (R-66 ①): review thresholds are single-sourced from"
                    + " sysman config_group ecos.fc.backtest and never accepted from the caller;"
                    + " offending keys=" + String.join(",", offenders));
        }
    }
}
