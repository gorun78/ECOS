package com.chinacreator.gzcm.cli;

/**
 * CLI 错误码单源（详设第 16 篇 §16.4 / 落地登记册为口径）。
 *
 * <p>此前 12 个 {@code ECOS-CLI-*} 字面量散落在 Cli / EnvelopeMapper / 各命令中，
 * 无法机器校验也无法与文档同步；此处收敛为常量，禁止再写裸字面量。</p>
 */
public final class CliErrorCodes {

    /** 未认证：无凭据或 Token 失效（HTTP 401）。 */
    public static final String AUTH = "ECOS-CLI-AUTH";
    /** 权限拒绝：服务端 ABAC/准入拒绝（HTTP 403）。 */
    public static final String FORBIDDEN = "ECOS-CLI-FORBIDDEN";
    /** 依赖不可用：连接失败 / 超时 / 502 / 503 / 504。 */
    public static final String UNAVAILABLE = "ECOS-CLI-UNAVAILABLE";
    /** 依赖真实不可用（区别于本地网络）：502-504。 */
    public static final String DEP_UNAVAILABLE = "ECOS-CLI-DEP-UNAVAILABLE";
    /** 后端信封非零 code 且未带 errorCode。 */
    public static final String RESPONSE = "ECOS-CLI-RESPONSE";
    /** 本地终态映射：BLOCKED / FAILED / PARTIAL。 */
    public static final String BLOCKED = "ECOS-CLI-BLOCKED";
    public static final String FAILED = "ECOS-CLI-FAILED";
    public static final String PARTIAL = "ECOS-CLI-PARTIAL";
    /** 未捕获异常（含堆栈信息的类名+消息，已脱敏）。 */
    public static final String INTERNAL = "ECOS-CLI-INTERNAL";
    /** 用法错误（退出码 64）。 */
    public static final String USAGE = "ECOS-CLI-USAGE";
    /** dry-run 计划输出（非错误，仅作 code 标识）。 */
    public static final String DRY_RUN = "ECOS-CLI-DRY-RUN";
    /** doctor 汇总态。 */
    public static final String DOCTOR = "ECOS-CLI-DOCTOR";
    /** {@code --show-config} 快照（非错误，仅作 code 标识）。 */
    public static final String CONFIG = "ECOS-CLI-CONFIG";
    /** 契约声明为 planned/未实现，CLI 拒绝真实调用（不得伪造空成功）。 */
    public static final String NOT_IMPLEMENTED = "ECOS-CLI-NOT-IMPLEMENTED";
    /** 凭据缺失导致的本地阻塞（不重试）。 */
    public static final String CREDENTIALS = "ECOS-CLI-CREDENTIALS";
    /** 重试耗尽。 */
    public static final String RETRY_EXHAUSTED = "ECOS-CLI-RETRY-EXHAUSTED";

    /** HTTP 类错误码前缀：{@code ECOS-CLI-HTTP-<status>}。 */
    public static String http(int status) {
        return "ECOS-CLI-HTTP-" + status;
    }

    private CliErrorCodes() {
    }
}
