package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes.DomainException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * kb-engine 局部 @RestControllerAdvice — 使 DataAccessException/表缺失/domain exception
 * 走 5xx + ECOS-KB-* 错误码，不再落入网关 {@code GlobalExceptionHandler} Exception→404 兜底
 * （F04-03 / W94 三级掩蔽链第 1 级：<b>kb 局部 advice 必须</b>先于 gateway 兜底命中）。
 *
 * <p>命中顺序由 Spring 的"最具体类型优先"驱动：
 * <ul>
 *   <li>{@link DataAccessException}（含 "table does not exist"）→ 500 + ECOS-KB-050；</li>
 *   <li>{@link DomainException}（是 {@link DataBridgeException} 子类，更具体，将先于后者命中）
 *       → 使用该异常自身的 httpStatus + code；</li>
 *   <li>别家 {@link DataBridgeException}（跨域桥异常）→ 502 + ECOS-KB-501。</li>
 * </ul>
 * 不改网关 {@code GlobalExceptionHandler}；本 advice 仅在 kb 引擎包范围内生效。
 */
@RestControllerAdvice
public class KbErrorAdvice {

    /** DataAccessException（含"table does not exist"）→ 500 + ECOS-KB-050 */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> onDa(DataAccessException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "ECOS-KB-050", "kb_nav_* 表缺失或 DDL 未执行，DB 层异常：" + rootMsg(ex)));
    }

    /** kb DomainException → 使用其携带的 httpStatus + code */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> onDomain(DomainException ex) {
        int status = ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 500;
        String code = ex.getCode() == null ? "ECOS-KB-500" : ex.getCode();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status, code, ex.getMessage()));
    }

    /** 别家 DataBridge 领域异常 → 502 */
    @ExceptionHandler(DataBridgeException.class)
    public ResponseEntity<ApiResponse<Void>> onBridge(DataBridgeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(502, "ECOS-KB-501", "数据桥域异常: " + ex.getMessage()));
    }

    private static String rootMsg(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getMessage();
    }
}
