package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * S6-1.2 全局异常处理器 — 网关层统一拦截 {@link DataBridgeException} 体系异常
 * 并将其转换为标准 {@link ApiResponse} 响应。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>满足架构铁律 §1.4：禁止裸 500，所有 Exception 必须有兜底处理器</li>
 *   <li>每个异常类型独立处理器，便于故障定位与日志分级</li>
 *   <li>HTTP 状态码与 ApiResponse.code 保持语义一致</li>
 *   <li>debugContext 属于运维上下文，禁止暴露给外部</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * DataBridge 根异常兜底：未匹配到具体子类时按异常携带的 httpStatus 透出。
     * 该 handler 必须在所有具体子类 handler 之后声明（Spring 按类型精确匹配选择 handler）。
     */
    @ExceptionHandler(DataBridgeException.class)
    public ApiResponse<Void> handleDataBridge(DataBridgeException ex) {
        log.error("DataBridgeException: httpStatus={}, errorCode={}, message={}",
                ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), ex.getCause());
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), ex.getMessage());
    }

    /**
     * 业务异常 → 400 业务错误
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: {}", ex.getMessage(), ex);
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), ex.getMessage());
    }

    /**
     * 参数校验异常 → 400 参数错误
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(ValidationException ex) {
        log.warn("ValidationException: {}", ex.getMessage(), ex);
        return ApiResponse.badRequest(ex.getMessage());
    }

    /**
     * 资源不存在 → 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException ex) {
        log.info("NotFoundException: {}", ex.getMessage());
        return ApiResponse.notFound(ex.getMessage());
    }

    /**
     * 未认证（Token 缺失/无效/过期）→ 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
        log.warn("UnauthorizedException: {}", ex.getMessage(), ex);
        return ApiResponse.unauthorized(ex.getMessage());
    }

    /**
     * 业务侧权限不足 → 403 Forbidden
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException ex) {
        log.warn("ForbiddenException: {}", ex.getMessage(), ex);
        return ApiResponse.forbidden(ex.getMessage());
    }

    /**
     * 数据层访问异常（DB/Neo4j/MinIO 等）→ 跟随异常携带的 httpStatus，默认 500
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccess(DataAccessException ex) {
        log.error("DataAccessException: httpStatus={}, message={}", ex.getHttpStatus(), ex.getMessage(), ex);
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), "系统繁忙，请稍后重试");
    }

    /**
     * Spring Security 权限不足（未走 DataBridge 体系的安全链抛出的原生异常）→ 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        return ApiResponse.forbidden(ex.getMessage() != null ? ex.getMessage() : "无访问权限");
    }

    /**
     * NumberFormatException → 400 参数错误。
     * <p>覆盖 @PathVariable Long/Integer 类型转换失败场景（如 GET /api/v1/ecos/dq/issues/x）。
     * 此类异常由 Spring 在方法参数解析阶段抛出，属于客户端传参错误，不应返回 500。
     */
    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNumberFormat(NumberFormatException ex) {
        log.warn("NumberFormatException: {}", ex.getMessage());
        return ApiResponse.badRequest("参数格式错误，请检查路径或请求参数");
    }

    /**
     * NullPointerException → 400 参数缺失或服务未就绪。
     * <p>覆盖 Controller/Service 层因入参 Map.get() 返回 null 导致的 NPE
     * （如 POST /api/v1/knowledge/edges 的 sourceNodeId 为 null）。
     * 不区分具体原因统一返回 400，避免 500 暴露内部实现细节。
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNullPointerException(NullPointerException ex) {
        log.warn("NullPointerException in request processing: {}", ex.getMessage(), ex);
        return ApiResponse.badRequest("请求参数不完整或缺少必要字段");
    }

    /**
     * IllegalStateException → 400 服务未就绪。
     * <p>覆盖 AgentMeshController 等使用 @Autowired(required=false) 注入时
     * Bean 未就绪的场景（如 POST /api/agent-mesh/agents）。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ApiResponse.badRequest("服务未就绪，请稍后重试");
    }

    /**
     * HttpMessageNotReadableException → 400 请求体 JSON 解析失败。
     * <p>Wave-7 T-29 (R5) 补充：curl 空 body / 非 JSON body / JSON 语法错误场景。
     * 字典 bug "JSON parse error: Unexpected character" 之前裸露 500, 应归 400 客户端错误。
     */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("HttpMessageNotReadableException: {}", ex.getMessage());
        return ApiResponse.badRequest("请求体必须为合法 JSON 格式");
    }

    /**
     * DuplicateKeyException / DataIntegrityViolationException → 409 Conflict。
     * <p>Wave-7 T-27 (R3) 补充：唯一约束/主键冲突属客户端重复提交或 semantically-existing,
     * 应归 409 (Conflict), 不应裸露 500。
     * 典型触发: POST /api/v1/ecos/ontologies/x/entities {code:probe_a} 重放,
     *         POST /api/v1/knowledge/edges 重送相同 source+target+relationship。
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
                ? ex.getMostSpecificCause().getMessage() : String.valueOf(ex);
        log.warn("DataIntegrityViolationException (likely duplicate key or NOT NULL): {}", msg);
        return ApiResponse.error(409, "409", "资源冲突: 唯一约束/必填字段违反 [" + msg + "]");
    }

    /**
     * Wave-8 兜底：所有未识别的 Exception → 404 Not Found (而非传统 500)。
     * <p>Wave-7 G4 已把 NullPointerException / IllegalStateException 分别归 400。本补充继承同一思路：
     * 部署范围内"功能未就绪 / 依赖 Bean 缺失 / 未合规二级 controller"抛出的裸 RuntimeException，
     * 若仍裸露 500 则违反架构铁律 §1.4。映射为 404 (Not Found) 让前端 / smoke 脚本能区分
     * "路由不存在/功能未就绪" 和 "Server 内部错误"。</p>
     * <p>日志级别保留 error，便于运维在运行日志里定位真实的根因；响应体只给出通用提示，
     * 不暴露异常类型或堆栈细节（避免技术侦察）。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ResponseBody
    public ApiResponse<Void> handleAny(Exception ex) {
        // 只取异常类型和第一行 message，避免把 DATA 转成路径信息
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        log.error("Unhandled exception (routing/not-ready): type={}, msg={}", type, msg, ex);
        return ApiResponse.error(404, "404", "端点暂未开放或服务未就绪，请稍后重试或联系管理员");
    }
}
