package com.chinacreator.gzcm.engine.kb.config;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode;
import com.chinacreator.gzcm.engine.kb.shared.KbErrorCode.KbErrorCodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 知识域局部 ControllerAdvice（F04-03：掩蔽链第一环）。
 *
 * <p>详细设计-04 §F04-03 / K-46 / K-19：网关侧 {@code GlobalExceptionHandler}
 * 把未分类异常统一映射为 404（伪 404 掩蔽），上层难以区分"接口故障"与"无数据"。
 * 本 advice 只作用于 kb 21 个 Controller（{@code basePackages = …kb.controller}），
 * 显式承担三条语义：
 * <ul>
 *   <li>{@link KbErrorCodeException}（D.5 已定码）→ 按码映射 HTTP + 串面 code；</li>
 *   <li>{@link DataAccessException}（含表缺失 / SQL 语法错误）→ 500 +
 *       {@code ECOS-KB-050}（知识导航表缺失语义，含"表缺失"推理兜底），
 *       <b>不落入网关 Exception→404 兜底</b>；</li>
 *   <li>{@link IllegalStateException} / {@link IllegalArgumentException} → 400/409。</li>
 * </ul>
 *
 * <p>{@code @Order(0)}：优先于具有默认 ord 的 {@code GlobalExceptionHandler}，
 * 防止被外层 advice 抢先 404 化。</p>
 *
 * @author ECOS KB Team
 */
@RestControllerAdvice(basePackages = "com.chinacreator.gzcm.engine.kb.controller")
@Order(0)
public class KbLocalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KbLocalExceptionHandler.class);

    /**
     * KB 域错误码异常：D.5 已定码，直接透传。
     */
    @ExceptionHandler(KbErrorCodeException.class)
    public ApiResponse<Void> handleKbErrorCode(KbErrorCodeException ex) {
        log.warn("kb-scope domain exception: {} → {}, msg={}", ex.getCodeStr(), ex.getHttpStatus(), ex.getMessage());
        return ApiResponse.error(ex.getHttpStatus(), ex.getCodeStr(), ex.getMessage());
    }

    /**
     * DataAccessException：结构性能容错时多为 {@link DataAccessResourceFailureException}
     * （表缺失 / schema 缺失 / 建表未完成）；其他子型按 500 处理，禁用 404 化。
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccess(DataAccessException ex) {
        String codeStr = (ex instanceof DataAccessResourceFailureException)
                ? KbErrorCode.KB_050
                : KbErrorCode.PREFIX + "050";
        int http = KbErrorCode.httpOf(codeStr);
        log.error("kb-scope data access failure (any subtype → 500, not 404): {}", ex.getMessage(), ex);
        return ApiResponse.error(http, codeStr,
                "控制台存储访问失败：" + nullSafe(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ApiResponse<Void> handleIllegalState(IllegalStateException ex) {
        log.warn("kb-scope IllegalStateException: {}", ex.getMessage());
        return ApiResponse.error(409, KbErrorCode.KB_060, nullSafe(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("kb-scope IllegalArgumentException: {}", ex.getMessage());
        return ApiResponse.error(400, KbErrorCode.PREFIX + "012", nullSafe(ex.getMessage()));
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
