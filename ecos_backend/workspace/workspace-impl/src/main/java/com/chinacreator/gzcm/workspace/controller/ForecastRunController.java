package com.chinacreator.gzcm.workspace.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.forecast.ForecastAuditPackService;
import com.chinacreator.gzcm.workspace.forecast.ForecastExportService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunCommand;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunEvidence;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunOrchestrationService;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunRequestValidator;
import com.chinacreator.gzcm.workspace.forecast.ForecastRunView;
import com.chinacreator.gzcm.workspace.scenario.ActionProposal;
import com.chinacreator.gzcm.workspace.scenario.ScenarioActionService;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 预测 run 编排面 REST API —— 详细设计-09 v1.2 §十一 H-5（卷 07 移交，只增不改）
 * + H-6（导出/审计包两行，§5.1 rows 12/15）。
 *
 * <pre>
 * POST /api/v1/workspace/forecast-runs                    — 创建 run（六要素落位 + 幂等；H-6 超阈值转异步返回真实 taskId）
 * GET  /api/v1/workspace/forecast-runs/{runId}            — run 状态
 * GET  /api/v1/workspace/forecast-runs/{runId}/results    — 结果明细（区间三值全展 R-62 ①）
 * GET  /api/v1/workspace/forecast-runs/{runId}/evidence   — 证据引用
 * GET  /api/v1/workspace/forecast-runs/{runId}/export     — H-6 CSV 导出（SEC-01 与查询同管道）
 * GET  /api/v1/workspace/forecast-runs/{runId}/audit-pack — H-6 审计包 zip（SEC-04 七文件 + manifest sha256）
 * POST /api/v1/workspace/forecast-runs/{runId}/scenario-copy — 情景复制
 * GET  /api/v1/workspace/forecast-runs/compare           — 双 run 对比（?baselineRunId=&amp;candidateRunId=）
 * POST /api/v1/workspace/forecast-runs/{runId}/retry      — 重试（派生新 run，回链源）
 * POST /api/v1/workspace/forecast-runs/{runId}/actions    — 经营动作提案（createFcAction，#146aa 附录A 第 32 条）
 * </pre>
 *
 * <p><b>三滤波器</b>：{@code /api/v1/workspace/**} 已由 SecurityConfig（默认 DENY）+
 * ClearanceInterceptor（L1）+ VersionPrefixRewriteFilter（KEEP 直传，无 forward 项）统一覆盖，
 * 本族无需新增登记（证据见交付报告 file:line）。</p>
 *
 * <p><b>依赖未就绪 → 未上线</b>：计算 SPI 未装配时经 {@code ForecastRunNotLiveException} 回 501
 * STUB_CAPABILITY（禁 mock 成功 / 200 造数）；跨域取数经引擎通道，workspace 不直连 {@code ecos_dw}（ST08）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/forecast-runs")
public class ForecastRunController {

    private static final Logger log = LoggerFactory.getLogger(ForecastRunController.class);

    private final ForecastRunOrchestrationService orchestrationService;
    private final ForecastRunRequestValidator requestValidator;
    private final ScenarioReadGuard readGuard;
    private final ForecastExportService exportService;
    private final ForecastAuditPackService auditPackService;
    private final ScenarioActionService actionService;

    public ForecastRunController(ForecastRunOrchestrationService orchestrationService,
                                 ForecastRunRequestValidator requestValidator,
                                 ScenarioReadGuard readGuard,
                                 ForecastExportService exportService,
                                 ForecastAuditPackService auditPackService,
                                 ScenarioActionService actionService) {
        this.orchestrationService = orchestrationService;
        this.requestValidator = requestValidator;
        this.readGuard = readGuard;
        this.exportService = exportService;
        this.auditPackService = auditPackService;
        this.actionService = actionService;
    }

    /** 创建预测 run：body 需六要素 + idempotencyKey + contentHash；可选 runMode/baselineRunId。 */
    @PostMapping
    public ApiResponse<ForecastRunView> create(@RequestBody Map<String, Object> param) {
        ForecastRunCommand command = requestValidator.validate(param);
        log.info("创建预测 run scenario={} caliber={}@{} idempotencyKey={} runMode={}",
                command.getScenarioId(), command.getCaliberId(), command.getCaliberVersion(),
                command.getIdempotencyKey(), command.getRunMode());
        return ApiResponse.success(orchestrationService.create(command));
    }

    /** run 状态（按 runId）。F07-18：经 security-engine RLS/CLS 裁决后方可返回。 */
    @GetMapping("/{runId}")
    public ApiResponse<ForecastRunView> status(@PathVariable("runId") String runId) {
        readGuard.assertReadable("forecast_run");
        return ApiResponse.success(orchestrationService.status(runId));
    }

    /** 结果明细（区间三值 P10/P50/P90 原样透传，禁插值合成）。 */
    @GetMapping("/{runId}/results")
    public ApiResponse<Map<String, Object>> results(@PathVariable("runId") String runId) {
        readGuard.assertReadable("forecast_run");
        return ApiResponse.success(orchestrationService.results(runId));
    }

    /** 证据引用（后端组装，编排面不拼公式）。 */
    @GetMapping("/{runId}/evidence")
    public ApiResponse<ForecastRunEvidence> evidence(@PathVariable("runId") String runId) {
        readGuard.assertReadable("forecast_run");
        return ApiResponse.success(orchestrationService.evidence(runId));
    }

    /**
     * H-6 CSV 导出（详设-09 §5.1 row 12 / SEC-01）：导出裁决（三通道唯一入口
     * {@code POST /api/v1/security/decision}，purpose=export，#142/B4-3b）先于取数，
     * 取数与 {@code /results} 同一通道、列裁剪同一 CLS 咽喉；文件头写裁决给出的
     * 权限范围/水印/口径/asOf。SPI 未装配诚实 501。
     */
    @GetMapping("/{runId}/export")
    public ResponseEntity<String> export(@PathVariable("runId") String runId) {
        ForecastExportService.ExportPayload payload = exportService.buildExport(runId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDispositionFormData("attachment", "forecast_" + runId + "_results.csv");
        return new ResponseEntity<>(payload.getCsv(), headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * H-6 审计包导出（详设-09 §5.1 row 15 / SEC-04）：zip 七文件 + manifest sha256，
     * OPA {@code audit:export} 默认 DENY；成功发 {@code AUDIT_PACK_EXPORT} 审计事件；
     * 包内权限范围 = 导出人自身权限（同管道裁决结果，禁旁路取数）。
     */
    @GetMapping("/{runId}/audit-pack")
    public ResponseEntity<byte[]> auditPack(@PathVariable("runId") String runId) {
        ForecastAuditPackService.AuditPack pack = auditPackService.buildAuditPack(runId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", pack.getFileName());
        return new ResponseEntity<>(pack.getZipBytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    /** 情景复制（row 13）：以源 run 为基线派生新 run；可选 body idempotencyKey/overrides。 */
    @PostMapping("/{runId}/scenario-copy")
    public ApiResponse<ForecastRunView> scenarioCopy(@PathVariable("runId") String runId,
                                                     @RequestBody(required = false) Map<String, Object> param) {
        Map<String, Object> p = param == null ? Map.of() : param;
        String idempotencyKey = asString(p.get("idempotencyKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = p.get("overrides") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        return ApiResponse.success(orchestrationService.scenarioCopy(runId, overrides, idempotencyKey));
    }

    /** 双 run 对比（row 14 / U-5）：?baselineRunId=&candidateRunId=，数值取存值，缺位不合成。 */
    @GetMapping("/compare")
    public ApiResponse<Map<String, Object>> compare(@RequestParam("baselineRunId") String baselineRunId,
                                                    @RequestParam("candidateRunId") String candidateRunId) {
        readGuard.assertReadable("forecast_run");
        return ApiResponse.success(orchestrationService.compare(baselineRunId, candidateRunId));
    }

    /** 重试（row 16）：对终态源 run 派生新 run 并回链；不改写源，命中关账期禁止重算。 */
    @PostMapping("/{runId}/retry")
    public ApiResponse<ForecastRunView> retry(@PathVariable("runId") String runId,
                                              @RequestBody(required = false) Map<String, Object> param) {
        Map<String, Object> p = param == null ? Map.of() : param;
        String idempotencyKey = asString(p.get("idempotencyKey"));
        log.info("重试预测 run sourceRunId={}", runId);
        return ApiResponse.success(orchestrationService.retry(runId, idempotencyKey));
    }

    /**
     * 经营动作提案（详设 line 8266 / 附录A 第 32 条 {@code createFcAction}，#146aa）。
     *
     * <p><b>run 锚定先于一切</b>：路径 runId 经与 {@code GET /{runId}} 同一编排通道读回
     * （不存在 ⇒ 404 {@code FORECAST_RUN_NOT_FOUND}，禁拿不存在的 run 挂动作）；body 的
     * {@code forecastRunId} 覆写为路径 runId（提案与 run 的关联以 URL 单源，禁两处自相矛盾）。
     * 五必填（owner/dueDate/approver/expectedImpact/controlMetric）与「forecastRunId 必指
     * FORMAL run」的守卫全部在 {@link ScenarioActionService}（与 {@code POST /scenarios/{id}/actions}
     * 同一校验咽喉，禁第二套）；写侧落点经 {@code DecisionGateway} 委托决策底座，未装配 ⇒
     * 503 {@code COGNITIVE_UNAVAILABLE}（诚实缺省，禁本地造动作记录冒充已落）。
     * 审批人取自主体（JWT），绝不取请求体。</p>
     *
     * <p><b>缺列诚实位</b>：契约入参列 {@code factorRef}/{@code projectId} 在既有
     * {@link ActionProposal} 面无字段（改列＝改既有场景动作面签名，触「API 只增不改」）⇒
     * 回显两键恒 null 并附 {@code factorRefApplicable=false}/{@code projectIdApplicable=false}
     * 自证，<b>禁</b>造值。</p>
     */
    @PostMapping("/{runId}/actions")
    public ApiResponse<Map<String, Object>> createFcAction(@PathVariable("runId") String runId,
                                                           @RequestBody ActionProposal proposal) {
        ForecastRunView view = orchestrationService.status(runId);
        if (proposal == null) {
            proposal = new ActionProposal();
        }
        proposal.setForecastRunId(runId);
        String approver = currentSubject();
        Map<String, Object> result =
                actionService.propose(view.getScenarioId(), proposal, approver);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (result != null) {
            out.putAll(result);
        }
        out.put("forecastRunId", runId);
        out.put("factorRef", null);
        out.put("factorRefApplicable", false);
        out.put("projectId", null);
        out.put("projectIdApplicable", false);
        log.info("经营动作提案（run 锚定） runId={} by={}", runId, approver);
        return ApiResponse.success(out);
    }

    private static String currentSubject() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
            // 与 ScenarioActionController#currentSubject 同法：主体缺失由服务端五必填判 approver 缺失（400），禁伪造
        }
        return null;
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
