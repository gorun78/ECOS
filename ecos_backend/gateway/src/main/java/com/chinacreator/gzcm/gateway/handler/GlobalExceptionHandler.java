package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
     *
     * <p><b>#142/B4-3e-15（差距 D-B4-52）</b>：收口前本方法返回裸 {@code ApiResponse}，
     * 状态行恒 HTTP 200，"httpStatus 透出"只写在注释里 —— 于是 {@code ECOS-SEC-501}
     * 的 503 语义（security-engine 不可用）与 403 策略拒绝在 HTTP 面同形，前端按
     * {@code res.ok} 分态的通道全部失明（"护栏没说话"被读成成功信封）。现按携带值落
     * 状态行；携带值不在 4xx/5xx 区间时回退 500（禁把非法值原样写进状态行）。</p>
     */
    @ExceptionHandler(DataBridgeException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataBridge(DataBridgeException ex) {
        int status = resolveHttpStatus(ex.getHttpStatus());
        log.error("DataBridgeException: httpStatus={}, errorCode={}, message={}",
                status, ex.getErrorCode(), ex.getMessage(), ex.getCause());
        return ResponseEntity.status(status).body(
                ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), ex.getMessage()));
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
     * F02-08 规格 1：真实"路由/资源不存在" → 404。
     * <p>Spring Boot 3.2 未匹配路由抛 {@link NoResourceFoundException}，未匹配 handler 抛
     * {@link NoHandlerFoundException}。这是<b>唯一</b>合法返回 404 的业务外路径，
     * 与 {@code ECOS-DATA-031}（500 数据层异常）严格区分（分册 00 D.5 错误码表同步）。</p>
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleRouteNotFound(Exception ex) {
        log.info("Route/Resource not found: {}", ex.getMessage());
        return ApiResponse.<Void>error(404, "404", "资源不存在").withTraceIdFromMdc();
    }

    /**
     * F02-08 规格 1：Spring DAO 家族异常（DB/MyBatis SQL 失败）→ 500 + ECOS-DATA-031。
     * <p>覆盖 {@code BadSqlGrammarException / MyBatisSystemException / DataIntegrityViolation}
     * 之外的所有 {@code org.springframework.dao.DataAccessException} 子类（DB 连接失败、
     * 超时、JDBC 执行错误等）。<b>不得</b>再掩蔽为 404（P0），响应体附 traceId 便于定位。</p>
     * <p>注意：本 handler 覆盖 Spring DAO 根类；更具体的 {@code DataIntegrityViolationException}
     * 由 {@link #handleDataIntegrityViolation} 更窄优先级处理（Spring 按最具体类型匹配）。</p>
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<Void> handleSpringDataAccess(
            org.springframework.dao.DataAccessException ex) {
        log.error("org.springframework.dao.DataAccessException (DB/MyBatis failure): {}",
                ex.getMessage(), ex);
        ApiResponse<Void> resp =
                ApiResponse.error(500, DataDomainErrorCode.ECOS_DATA_031, "数据服务异常，请稍后重试");
        return resp.withTraceIdFromMdc();
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
     * 数据层访问异常（DB/Neo4j/MinIO 等）→ 跟随异常携带的 httpStatus，默认 500。
     *
     * <p><b>#142/B4-3e-15（差距 D-B4-52 同族）</b>：本方法注释自 v1 起即承诺"跟随 httpStatus"，
     * 而收口前返回裸 {@code ApiResponse} ⇒ 状态行恒 HTTP 200，DB 故障在 HTTP 面伪装成成功。
     * 现兑现该承诺：状态行取携带值（{@link DataAccessException#DEFAULT_HTTP_STATUS}＝500），
     * body 仍走脱敏文案，{@code ApiResponse.code} 保持 {@code errorCode} 不变
     * （-100 与状态行不一致属信封侧既有形态，本批不扩面，已按实测登记台账）。</p>
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        int status = resolveHttpStatus(ex.getHttpStatus());
        log.error("DataAccessException: httpStatus={}, message={}", status, ex.getMessage(), ex);
        return ResponseEntity.status(status).body(
                ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), "系统繁忙，请稍后重试"));
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
     * F02-08 规格 1（P0 修复）：所有未识别的 Exception → 500 (而非 404)。
     * <p>旧实现（Wave-8）把裸 RuntimeException 一律落 404 "端点暂未开放或服务未就绪"，
     * 这是<b>错误掩蔽</b>：真实的服务内部错误被伪装成"路由不存在"，前端/smoke 无法区分
     * "真 404" 与 "服务端故障"，违反分册 00 D.5 语义："仅真 NoHandlerFound/NoResourceFound
     * 才 404"（二者已由 {@link #handleRouteNotFound} 独立处理）。</p>
     * <p>响应体仍只给通用提示，不暴露异常类型或堆栈细节（避免技术侦察）；
     * 完整堆栈走 error 日志（含 traceId MDC）。</p>
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<Void> handleAny(Exception ex) {
        // 只取异常类型和第一行 message，避免把 DATA 转成路径信息
        String type = ex.getClass().getSimpleName();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        log.error("Unhandled exception (server internal error): type={}, msg={}", type, msg, ex);
        ApiResponse<Void> resp =
                ApiResponse.error(500, "ECOS-SYS-500", "服务内部错误，请稍后重试或联系管理员");
        return resp.withTraceIdFromMdc();
    }

    /**
     * 异常携带的 httpStatus → 可写进状态行的值。
     *
     * <p>只有 4xx/5xx 才是合法的"本异常自述的失败状态"；其余（0、-100、2xx、>599）
     * 一律回退 500，禁把非法值原样写进状态行（那会让容器报 500 之外的假语义）。</p>
     */
    static int resolveHttpStatus(int httpStatus) {
        return httpStatus >= 400 && httpStatus <= 599 ? httpStatus : 500;
    }
}
