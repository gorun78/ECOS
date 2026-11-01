package com.chinacreator.gzcm.runtime.access.git.publish;

/**
 * 非法状态转换异常（§2.9.6① 状态机的可执行拒绝面；#147D）。
 *
 * <p><b>为什么是一枚独立异常、而不是复用 {@code BusinessException}</b>：
 * 后者的错误码是 {@code int}，把状态机违例压成数字即丢掉「从哪态到哪态、允许的出边是什么」
 * 这三样判定所需的信息（同类反例见台账 D-B4-64）。本异常直接携带
 * {@code from}/{@code to}/{@code allowedTargets} 与机器码。</p>
 *
 * <p><b>为什么机器码不写成 {@code ECOS-*} 族</b>：common-api 的
 * {@code SystemErrorCode} 家族纪律明文「域内业务错误码仍归各域码表，禁把域错误塞进本族以绕过
 * 归属」，而错误码册（附录 C）尚无发布域码段。故本批用 {@code PUB_*} 下划线机器码作为
 * <b>状态机内部标识</b>，并在台账登记「发布/双写域错误码段待附录 C 收编」为余项；
 * 禁在本批私自往 {@code ECOS-SYS} 里加码。</p>
 *
 * <p><b>HTTP 承流边界</b>：{@link #httpStatus()} 给出 409（冲突态），但本批<b>不新增</b>
 * Controller 端点、也不改动既有发布端点的响应语义（铁律：API 只增不改；新增 Controller
 * 须过三滤波器）。409 在网关侧的真实落点与错误体形状随 #147E 的读写腿收口一并处理，
 * 台账登记为未覆盖面，禁凭本类存在而宣称「409 已对外承流」。</p>
 *
 * @author ECOS Runtime底座 #147D
 */
public class IllegalPublishTransitionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 冲突类状态转换违例的 HTTP 语义（承流面见类注释）。 */
    public static final int HTTP_CONFLICT = 409;

    /** 机器码：出边不在合法转换表内。 */
    public static final String CODE_ILLEGAL_TRANSITION = "PUB_ILLEGAL_TRANSITION";

    /** 机器码：起点是终态，不得再推进。 */
    public static final String CODE_TERMINAL_STATE = "PUB_TERMINAL_STATE";

    /** 机器码：回滚缺少可检出的历史版本引用（DF-5 同形门）。 */
    public static final String CODE_ROLLBACK_REF_REQUIRED = "PUB_ROLLBACK_REF_REQUIRED";

    private final int httpStatus;
    private final String errorCode;
    private final PublishState from;
    private final PublishState to;
    private final java.util.Set<PublishState> allowedTargets;

    public IllegalPublishTransitionException(String errorCode, String message,
                                            PublishState from, PublishState to,
                                            java.util.Set<PublishState> allowedTargets) {
        super(message);
        this.httpStatus = HTTP_CONFLICT;
        this.errorCode = errorCode;
        this.from = from;
        this.to = to;
        this.allowedTargets = allowedTargets == null
                ? java.util.Collections.emptySet()
                : java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(allowedTargets));
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public PublishState from() {
        return from;
    }

    public PublishState to() {
        return to;
    }

    /** 该起点在合法转换表内的全部出边（供调用方把拒绝说成可行动信息，而不是笼统 500）。 */
    public java.util.Set<PublishState> allowedTargets() {
        return allowedTargets;
    }
}
