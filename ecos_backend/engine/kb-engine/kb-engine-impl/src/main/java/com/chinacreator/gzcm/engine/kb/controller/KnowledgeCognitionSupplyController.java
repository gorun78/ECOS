package com.chinacreator.gzcm.engine.kb.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.cognition.CognitionContractGuard;
import com.chinacreator.gzcm.engine.kb.cognition.CognitionReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 认知供给侧只读控制器（F04-08 / REQ-KB-04，2026-10-12 复盘补齐）—
 * {@code GET /api/v1/knowledge/cognition/{hypotheses|beliefs|minds}}。
 *
 * <p>详细设计-04 D.1「认知契约（只读）{@code /api/v1/knowledge/cognition/**} — kb 只读开放，
 * 禁写代理」承诺的供给侧端点族。此前仅有 {@link CognitionReadService} 无 Controller 映射（欠账）。</p>
 *
 * <p><b>契约纪律</b>（详设-04 D.2）：
 * <ul>
 *   <li>只读：仅 GET；任何 POST/PUT/PATCH/DELETE 命中本族 → {@link CognitionContractGuard#assertReadOnly()}
 *       fail-loud 405 {@code ECOS-KB-070}（禁写代理，REQ-KB-04）；</li>
 *   <li>列表强制 limit：默认 50、上限 500（DAO 收口，禁全量拉取 K-55）；</li>
 *   <li>fail-loud：后端不可用（表缺失/访问被拒）→ 503 {@code ECOS-KB-073}
 *       （{@code COGNITION_SUPPLY_UNAVAILABLE}，由 {@code KbErrorAdvice} 透出），
 *       <b>严禁空列表伪装"无数据"</b>；真实空结果集才返回空列表；</li>
 *   <li>错误响应统一 {@code ApiResponse{code,errorCode,message,traceId}}（D.2 #3）。</li>
 * </ul>
 *
 * <p>三滤波器口径：{@code /api/v1/knowledge/} 族在 VersionPrefixRewriteFilter 为 KEEP（无正向条目，
 * 见该 filter L45 注释），本路径直接命中不需新增映射；默认走鉴权，不加 permitAll（与既有
 * knowledge 族同形态，SecurityConfig/ClearanceInterceptor 零改动）。</p>
 *
 * @group COGNITION
 */
@RestController
@RequestMapping("/api/v1/knowledge/cognition")
public class KnowledgeCognitionSupplyController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCognitionSupplyController.class);

    private final CognitionReadService cognitionReadService;
    private final CognitionContractGuard guard;

    public KnowledgeCognitionSupplyController(CognitionReadService cognitionReadService,
                                              CognitionContractGuard guard) {
        this.cognitionReadService = cognitionReadService;
        this.guard = guard;
    }

    /** GET /hypotheses — 认知假设三态只读（status 白名单 PROPOSED/EVIDENCED/BELIEVED/REFUTED）。 */
    @GetMapping("/hypotheses")
    public ApiResponse<List<Map<String, Object>>> hypotheses(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> rows = cognitionReadService.hypothesesSupply(domain, limit);
        log.debug("cognition supply hypotheses domain={} rows={}", domain, rows.size());
        return ApiResponse.<List<Map<String, Object>>>success(rows).withTraceIdFromMdc();
    }

    /** GET /beliefs — 不确定性判断（信念分布）三态只读。 */
    @GetMapping("/beliefs")
    public ApiResponse<List<Map<String, Object>>> beliefs(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> rows = cognitionReadService.beliefsSupply(domain, limit);
        log.debug("cognition supply beliefs domain={} rows={}", domain, rows.size());
        return ApiResponse.<List<Map<String, Object>>>success(rows).withTraceIdFromMdc();
    }

    /** GET /minds — 心智层三态只读。 */
    @GetMapping("/minds")
    public ApiResponse<List<Map<String, Object>>> minds(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<Map<String, Object>> rows = cognitionReadService.mindsSupply(domain, limit);
        log.debug("cognition supply minds domain={} rows={}", domain, rows.size());
        return ApiResponse.<List<Map<String, Object>>>success(rows).withTraceIdFromMdc();
    }

    /**
     * 写代理禁入守门：本族下任何非 GET 请求 → 405 {@code ECOS-KB-070}
     * （{@link CognitionContractGuard#assertReadOnly()} 恒抛，由 {@code KbErrorAdvice} 透出）。
     */
    @RequestMapping(path = "/**", method = {RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE})
    public ApiResponse<Void> writeProxyRejected() {
        guard.assertReadOnly();
        // 不可达：assertReadOnly 恒抛
        throw new IllegalStateException("unreachable: cognition write guard must fail-loud");
    }
}
