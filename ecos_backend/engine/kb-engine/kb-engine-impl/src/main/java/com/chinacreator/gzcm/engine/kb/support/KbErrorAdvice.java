package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.kb.support.KbErrorCodes.DomainException;
import org.springframework.dao.DataAccessException;
import org.springframework.core.annotation.Order;
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
 *
 * <p><b>#142/B4-3e-15（差距 D-B4-52）</b>：上一段注释自 v1 起就声明"仅在 kb 包范围内生效"，
 * 而注解实为无 {@code basePackages} 的全局 advice —— 在 gateway fat-jar 组合面里，它与
 * {@code GlobalExceptionHandler} 同为全局且都声明处理 {@link DataBridgeException}，
 * 排序未定义 ⇒ 别域（data／ontology／runtime）的 403／503 会被本类改写成 502／ECOS-KB-501，
 * "哪个域的错误由哪个域渲染"取决于 Bean 名排序这种实现细节。现把注释兑现成注解：
 * {@code basePackages} 限定 kb 引擎包，{@code @Order(1)} 显式声明先于网关兜底命中
 * （对齐同仓既有 {@code CognitiveErrorAdvice}／{@code ContextBuildErrorAdvice} 先例）。</p>
 */
@Order(1)
@RestControllerAdvice(basePackages = {"com.chinacreator.gzcm.engine.kb"})
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
        // #142/B4-3e-29（差距 D-B4-66 同族，登记 D-B4-67 前半）：收口前本行写
        // ex.getHttpStatus() > 0 ? … : 500 —— 只判正数、不判区间，700／200 之类越界携带值会被
        // 原样写进状态行，且判据散落本域构成第二源。现改走 common-api 单源（4xx/5xx 原样透出，
        // 其余回退 500），合法 4xx/5xx 取值逐字不变。
        int status = DataBridgeException.resolveHttpStatus(ex.getHttpStatus());
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
