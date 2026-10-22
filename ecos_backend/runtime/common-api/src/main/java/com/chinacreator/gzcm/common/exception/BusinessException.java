package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 业务逻辑异常。
 * <p>
 * 由 Service 层在业务规则校验失败时抛出。HTTP 状态码默认为 400。
 *
 * <p><b>#142/B4-3e-28（差距 D-B4-56）</b>：收口前本类只有「单参」与「(errorCode, message)」两枚
 * 构造，两者都把 {@code httpStatus} 写死 {@link #DEFAULT_HTTP_STATUS} ⇒ 调用方传入的
 * {@code 404}/{@code 409}/{@code 500}/{@code 501}/{@code 503} 只进了 body {@code code}，
 * 状态行恒 400 —— 携带值与状态行异号，§3.2.7「返回值 code 与状态行同号」与 §4.7.2
 * 「不可用 ≠ 无权限」在 {@code BusinessException} 面上失效（实测 main 面 23 处）。</p>
 *
 * <p>修法按「API 只增不改」：新增可显式携带 {@code httpStatus} 的三参构造，既有两枚签名与
 * 语义原样保留（默认仍 400）。{@code httpStatus} 越界时由出口统一回退（见
 * {@code GlobalExceptionHandler.resolveHttpStatus}），本类不做二次裁决，避免与根异常型的双源判定。</p>
 */
public class BusinessException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 400;
    public static final int DEFAULT_ERROR_CODE = 400;

    public BusinessException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public BusinessException(int errorCode, String message) {
        super(DEFAULT_HTTP_STATUS, errorCode, message);
    }

    /**
     * 显式携带状态行的构造（#142/B4-3e-28 新增，只增不改）。
     *
     * @param httpStatus 期望落在 HTTP 状态行的数值；须与 {@code errorCode} 同号才满足 §3.2.7，
     *                   非法值（非 4xx/5xx）在出口回退 500
     * @param errorCode  body {@code code} 的业务/数据语义值，取值与迁移前逐字不变
     */
    public BusinessException(int httpStatus, int errorCode, String message) {
        super(httpStatus, errorCode, message);
    }
}
