package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * DataBridge 所有异常的统一根类（非受检）。
 * <p>
 * 设计决策：使用 RuntimeException 而非 checked Exception，
 * 避免老工程中 {@code throws Exception} 扩散的问题。
 * 所有子类异常通过 GlobalExceptionHandler 统一转换为 ApiResponse。
 *
 * @see com.chinacreator.gzcm.common.base.ApiResponse
 */
public class DataBridgeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 状态码，用于 GlobalExceptionHandler */
    private final int httpStatus;

    /** 业务错误码，映射到 ApiResponse.code */
    private final int errorCode;

    /** 调试上下文（不暴露给前端） */
    private final transient Object debugContext;

    public DataBridgeException(int httpStatus, int errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = null;
    }

    public DataBridgeException(int httpStatus, int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = null;
    }

    public DataBridgeException(int httpStatus, int errorCode, String message,
                               Throwable cause, Object debugContext) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = debugContext;
    }

    public int getHttpStatus() { return httpStatus; }
    public int getErrorCode() { return errorCode; }
    public Object getDebugContext() { return debugContext; }

    /**
     * 异常携带的 httpStatus → 可写进 HTTP 状态行的值（<b>全仓单源</b>）。
     *
     * <p>只有 4xx/5xx 才是合法的「本异常自述的失败状态」；其余（0、-100、2xx、&gt;599）一律回退
     * 500，禁把非法值原样写进状态行（那会让容器报出 500 之外的假语义）。裁决口径与 §4.7.2
     * 「不可用 ≠ 无权限」一致：折叠只在出口按本方法判定，构造器不改写携带值。</p>
     *
     * <p><b>判据归属依据（#142/B4-3e-29，差距 D-B4-66）</b>：本方法收口前只存在于
     * {@code gateway} 的 {@code GlobalExceptionHandler} 包内私有实现。而参数型为
     * {@code DataBridgeException} 根本身的 {@code @ExceptionHandler} 腿共 3 枚，分属
     * gateway / kb-engine / sysman-boot 三个模块——判据放在任一宿主都会逼出第二份复制，
     * 或直接让另两个宿主无法兑现「跟随携带值」的注释承诺。单源下沉到携带者所在的
     * {@code common-api}（本类），gateway 原方法保留为委托。区间字面量在 main 面只允许出现
     * 于本方法，由 {@code RootCarrierHttpStatusSingleSourceRatchetTest} 判据 B 钉住。</p>
     */
    public static int resolveHttpStatus(int httpStatus) {
        return httpStatus >= 400 && httpStatus <= 599 ? httpStatus : 500;
    }
}
