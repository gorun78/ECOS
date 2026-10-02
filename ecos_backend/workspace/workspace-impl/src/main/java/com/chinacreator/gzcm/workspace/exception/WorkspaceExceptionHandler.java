package com.chinacreator.gzcm.workspace.exception;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * workspace 场景域全局异常处理器（详细设计-07 F07-13 / C-5）。
 *
 * <p>唯一持有「领域异常 → 真实 HTTP 状态码」映射，杜绝 X-16/X-17「HTTP 恒 200 + body.code 伪装」：
 * 孤岛 409、非法迁移 409、权限 403、认知引擎不可用 503、动作五必填 400、演练引用 400、布局冲突 409、
 * 场景不存在 404 一律落真实状态码。基线限定在 workspace 场景域 controller 包，避免波及其它部署单元。</p>
 *
 * <p>A3 红线（X-41）：内部异常（{@link DataAccessException} / HTTP client 等）原文只进日志，
 * 响应体只给固定文案 + traceId，绝不携带异常类型或堆栈详情。</p>
 */
@RestControllerAdvice(basePackages = "com.chinacreator.gzcm.workspace")
public class WorkspaceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceExceptionHandler.class);

    // ── F07-10 五必填 / F07-14 run body → 400 ACTION_FIELDS_MISSING ─
    @ExceptionHandler(ActionValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleActionValidation(ActionValidationException ex) {
        log.warn("ActionValidation: {}", ex.getMessage());
        return ApiResponse.error(400, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── F07-09 FORMAL 引用演练产物 → 400 SANDBOX_REF_FORBIDDEN ──────
    @ExceptionHandler(SandboxReferenceForbiddenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleSandboxReference(SandboxReferenceForbiddenException ex) {
        log.warn("SandboxReferenceForbidden: {}", ex.getMessage());
        return ApiResponse.error(400, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── F07-18 权限拒绝（不泄露资源存在性）→ 403 PERMISSION_DENIED ──
    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenOperationException ex) {
        log.warn("ForbiddenOperation: {}", ex.getMessage());
        return ApiResponse.error(403, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── F07-04 孤岛绑定 → 409 ISLAND_BINDING + 孤岛清单 ────────────
    @ExceptionHandler(IslandBindingException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Map<String, Object>> handleIslandBinding(IslandBindingException ex) {
        log.warn("IslandBinding: {} ({} islands)", ex.getMessage(), ex.getIslands().size());
        ApiResponse<Map<String, Object>> body = ApiResponse.error(409, ex.getErrorCodeString(), ex.getMessage());
        List<Map<String, Object>> islands = new ArrayList<>();
        for (IslandBindingException.IslandItem it : ex.getIslands()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bindingId", it.bindingId());
            m.put("name", it.name());
            m.put("bindingType", it.bindingType());
            m.put("reason", it.reason());
            islands.add(m);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("islands", islands);
        body.setData(details);
        return body;
    }

    // ── F07-07 非法状态迁移 → 409 ────────────────────────────────
    @ExceptionHandler(IllegalScenarioTransitionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleIllegalTransition(IllegalScenarioTransitionException ex) {
        log.warn("IllegalTransition code={}: {}", ex.getErrorCodeString(), ex.getMessage());
        return ApiResponse.error(409, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── 沙盘 layout 乐观锁冲突 → 409 LAYOUT_CONFLICT ───────────────
    @ExceptionHandler(OptimisticLockConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleLayoutConflict(OptimisticLockConflictException ex) {
        log.warn("LayoutConflict: {}", ex.getMessage());
        return ApiResponse.error(409, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── 场景不存在（含软删 is_deleted）→ 404 SCENARIO_NOT_FOUND ────
    @ExceptionHandler(ScenarioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleScenarioNotFound(ScenarioNotFoundException ex) {
        log.info("ScenarioNotFound: {}", ex.getMessage());
        return ApiResponse.error(404, ex.getErrorCodeString(), ex.getMessage());
    }

    // ── F07-12 认知引擎不可用：503 原样穿透，禁 200 空 body（A3）──
    @ExceptionHandler(CognitiveEngineUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleCognitiveUnavailable(CognitiveEngineUnavailableException ex) {
        log.error("CognitiveUnavailable: {}", ex.getMessage(), ex.getCause() != null ? ex.getCause() : ex);
        return ApiResponse.error(503, ex.getErrorCodeString(), "cognitive engine unavailable");
    }

    // ── 跨服务依赖不可达 → 503 EXTERNAL_SERVICE_UNAVAILABLE（X-17：body 503 + HTTP 200 已无效）──
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleExternalUnavailable(ExternalServiceUnavailableException ex) {
        log.warn("ExternalServiceUnavailable: {}", ex.getMessage());
        return ApiResponse.error(503, ex.getErrorCodeString(), "service unavailable");
    }

    // ── common-api 业务异常族（BusinessException / NotFoundException / …）→ 真实状态码 ──
    // 兜住本域仍用 common-api 异常的既有路径（只增不改），避免其落入下方 500 兜底而丢失 4xx 语义。
    @ExceptionHandler(DataBridgeException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleDataBridge(DataBridgeException ex) {
        int status = ex.getHttpStatus() < 400 ? 400 : ex.getHttpStatus();
        log.warn("DataBridge {} {}: {}", status, ex.getClass().getSimpleName(), ex.getMessage());
        org.springframework.http.HttpStatus http;
        try {
            http = org.springframework.http.HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            http = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return org.springframework.http.ResponseEntity.status(http)
                .body(ApiResponse.error(status, ex.getClass().getSimpleName(), ex.getMessage()));
    }

    // ── 数据访问异常 → 500 INTERNAL（原文不入体，X-41）─────────────
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleDataAccess(DataAccessException ex) {
        log.error("DataAccess error in workspace scenario domain", ex);
        return ApiResponse.error(500, WorkspaceInternalException.CODE, "系统繁忙，请稍后重试");
    }

    // ── workspace 域内部错误 → 500 INTERNAL ─────────────────────
    @ExceptionHandler(WorkspaceInternalException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleWorkspaceInternal(WorkspaceInternalException ex) {
        log.error("WorkspaceInternal: {}", ex.getMessage(), ex);
        return ApiResponse.error(500, WorkspaceInternalException.CODE, "系统繁忙，请稍后重试");
    }

    // ── 兜底：领域内未识别异常 → 500 INTERNAL，固定文案不打原文 ──
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleAny(Exception ex) {
        log.error("Unhandled exception in workspace scenario domain: {}", ex.getMessage(), ex);
        return ApiResponse.error(500, WorkspaceInternalException.CODE, "系统繁忙，请稍后重试");
    }
}