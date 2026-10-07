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
     * F02-08 / W46（详细设计-02）：Spring DAO/MyBatis 底层异常 <b>禁止</b>再落入 catch-all 的 404。
     * <p>
     * {@link org.springframework.dao.DataAccessException}（{@code BadSqlGrammarException} /
     * {@code MyBatisSystemException} 等皆为其子类）属真实的数据层失败，若被掩蔽成 404「端点暂未开放」，
     * 前端会误判为路由/功能未就绪并静默回落 legacy，掩盖缺陷（D-6 a/b/c 三处实测根因）。
     * 此处显式映射为 <b>500 + ECOS-DATA-031</b>，traceId 由 {@link ApiResponse#getTraceId()} 自动从 MDC 回填，
     * 便于全链定位；仅真正的 {@code NoHandlerFound/NoResourceFound}（路由不存在）才应走 404。
     * </p>
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleSpringDaoAccess(org.springframework.dao.DataAccessException ex) {
        String root = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.error("SpringDaoDataAccessException (data-layer failure, NOT masked as 404): {}", root, ex);
        return ApiResponse.error(500, "ECOS-DATA-031", "数据层处理异常，请稍后重试或联系管理员");
    }

    /**
     * MyBatis 运行时异常（{@code org.apache.ibatis.exceptions.PersistenceException} /
     * {@code MyBatisSystemException} 之上未归入 Spring DAO 体系的分支）。
     * <p>同样禁止掩蔽为 404，映射 500 + ECOS-DATA-031。</p>
     */
    @ExceptionHandler(org.apache.ibatis.exceptions.PersistenceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleMyBatisPersistence(org.apache.ibatis.exceptions.PersistenceException ex) {
        log.error("MyBatisPersistenceException (data-layer failure, NOT masked as 404): {}", ex.getMessage(), ex);
        return ApiResponse.error(500, "ECOS-DATA-031", "数据层处理异常，请稍后重试或联系管理员");
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
     * R1.6（详细设计-02 W47）兜底：所有未识别的 Exception → 500 + ECOS-SYS-500。
     * <p>
     * 旧版（Wave-8）曾将 catch-all 映射为 404 Not Found，理由是"让前端能区分路由不存在/功能未就绪"。该策略存在
     * 严重副作用：所有真实的 5xx 内部错误（NPE、依赖不可用、事务未回滚、下游服务 500 序列化失败等）都被
     * 掩蔽成 404，前端会静默回落 legacy 路由或误报"端点尚未开放"——D-6 a/b/c 三处实测根因已在
     * {@link #handleSpringDaoAccess} 修复数据层同一问题（500 + ECOS-DATA-031）。<b>本 handler 是同一
     * 治理的最后一环</b>：不再用 404 掩盖内部错误。真路由不存在场景由 Spring DispatcherExceptionResolver
     * 的 {@code NoHandlerFoundException}（若开启）或 {@code NoResourceFoundException} 走 404，不经本 handler。
     * </p>
     * <p>响应体保持通用提示，不暴露异常类型或堆栈（避免技术侦察）；traceId 由 {@link ApiResponse}
     * 从 MDC 自动回填，运维凭 traceId 定位真实根因。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<Void> handleAny(Exception ex) {
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        log.error("Unhandled exception (interal server error, NOT masked as 404): type={}, msg={}", type, msg, ex);
        return ApiResponse.error(500, "ECOS-SYS-500", "服务器内部错误，请稍后重试或联系管理员");
    }
}
