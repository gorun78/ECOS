package com.chinacreator.gzcm.engine.kb.shared;

import java.io.Serial;

import com.chinacreator.gzcm.common.exception.DataBridgeException;

/**
 * 知识域（kb-engine）统一错误码常量 + 领域异常（详细设计-04 §D.5 全表）。
 * <p>
 * 详细设计-04 §D.5 将每个错误码与一个 HTTP 语义值绑定；本类同时保存
 * <b>串面</b>（响应体 {@code ApiResponse.errorCode} = {@code ECOS-KB-XXX}）与
 * <b>HTTP 面</b>（响应体 {@code ApiResponse.code} = HTTP 数值，与 status 一致，"错误码进响应 code"）。
 * 各业务类通过 {@link #ex(String, String)} 工厂抛出 {@link KbErrorCodeException}，
 * 由 Service 抛出后在 Controller 边界统一映射为 {@code ApiResponse.error(http, codeStr, msg)}。
 * <p>
 * 已 grep 确认全仓<b>无</b>既有 {@code KbErrorCode} / {@code engine.kb.error} 包，故此处新建。
 *
 * @author ECOS KB Team
 */
public final class KbErrorCode {

    private KbErrorCode() {
    }

    public static final String PREFIX = "ECOS-KB-";

    // ── D.5 全表：码 → (串面, HTTP) ─────────────────────────────────────────
    public static final String KB_011 = PREFIX + "011"; public static final int HTTP_011 = 409; // 本体快照缺失
    public static final String KB_012 = PREFIX + "012"; public static final int HTTP_012 = 422; // 抽取字段映射 C1/C2 硬失败
    public static final String KB_020 = PREFIX + "020"; public static final int HTTP_020 = 503; // 安全裁决不可用→DENY
    public static final String KB_021 = PREFIX + "021"; public static final int HTTP_021 = 503; // 任务底座不可用
    public static final String KB_022 = PREFIX + "022"; public static final int HTTP_022 = 500; // 事件底座不可用且兜底失败
    public static final String KB_023 = PREFIX + "023"; public static final int HTTP_023 = 503; // LLM 底座不可用
    public static final String KB_030 = PREFIX + "030"; public static final int HTTP_030 = 500; // 对象存储不可用
    public static final String KB_031 = PREFIX + "031"; public static final int HTTP_031 = 415; // 上传类型/大小不合规
    public static final String KB_040 = PREFIX + "040"; public static final int HTTP_040 = 502; // CURATED 供数不可达
    public static final String KB_041 = PREFIX + "041"; public static final int HTTP_041 = 422; // 画像样本不足且退化链穷尽
    public static final String KB_050 = PREFIX + "050"; public static final int HTTP_050 = 500; // 知识导航表缺失
    public static final String KB_051 = PREFIX + "051"; public static final int HTTP_051 = 500; // 路由映射缺失
    public static final String KB_060 = PREFIX + "060"; public static final int HTTP_060 = 409; // 状态机非法跃迁
    public static final String KB_061 = PREFIX + "061"; public static final int HTTP_061 = 409; // 已发布资产不可变
    public static final String KB_062 = PREFIX + "062"; public static final int HTTP_062 = 403; // 权限不足(非知识管理员)
    public static final String KB_070 = PREFIX + "070"; public static final int HTTP_070 = 405; // 认知契约写请求被拒
    public static final String KB_071 = PREFIX + "071"; public static final int HTTP_071 = 409; // 修改 APPROVED 假设
    public static final String KB_072 = PREFIX + "072"; public static final int HTTP_072 = 410; // 假设已过期不可引用
    public static final String KB_080 = PREFIX + "080"; public static final int HTTP_080 = 500; // 图谱形态不一致

    // ── HTTP 面检索（码串 → HTTP） ─────────────────────────────────────────
    /** 由 ECOS-KB-XXX 串面取 HTTP 数值；未知码回退 500。 */
    public static int httpOf(String codeStr) {
        if (codeStr == null) return 500;
        return switch (codeStr) {
            case KB_011 -> HTTP_011;
            case KB_012 -> HTTP_012;
            case KB_020 -> HTTP_020;
            case KB_021 -> HTTP_021;
            case KB_022 -> HTTP_022;
            case KB_023 -> HTTP_023;
            case KB_030 -> HTTP_030;
            case KB_031 -> HTTP_031;
            case KB_040 -> HTTP_040;
            case KB_041 -> HTTP_041;
            case KB_050 -> HTTP_050;
            case KB_051 -> HTTP_051;
            case KB_060 -> HTTP_060;
            case KB_061 -> HTTP_061;
            case KB_062 -> HTTP_062;
            case KB_070 -> HTTP_070;
            case KB_071 -> HTTP_071;
            case KB_072 -> HTTP_072;
            case KB_080 -> HTTP_080;
            default -> 500;
        };
    }

    /** 构造领域异常（Service 抛出，Controller 边界映射）。 */
    public static KbErrorCodeException ex(String codeStr, String message) {
        return new KbErrorCodeException(httpOf(codeStr), codeStr, message);
    }

    /**
     * 携带 ECOS-KB 串面码的领域异常。继承 common-api {@link DataBridgeException}，
     * 便于 gateway {@code GlobalExceptionHandler} 兜底；Controller 内亦可直接捕获映射为 2xx/4xx ApiResponse。
     */
    public static final class KbErrorCodeException extends DataBridgeException {
        @Serial
        private static final long serialVersionUID = 1L;
        private final String codeStr;

        public KbErrorCodeException(int httpStatus, String codeStr, String message) {
            // errorCode 数字面 = HTTP 数值，保证即便走全局兜底（String.valueOf(errorCode)）也可读；
            // 串面 codeStr 由 Controller 显式写入 ApiResponse.errorCode。
            super(httpStatus, httpStatus, message);
            this.codeStr = codeStr;
        }

        public String getCodeStr() {
            return codeStr;
        }
    }
}
