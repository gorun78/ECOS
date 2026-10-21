package com.chinacreator.gzcm.common.base;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 机读错误码（ECOS-* 形态）在响应信封 {@code ApiResponse.errorCode} 字段上的<b>唯一</b>取法属主。
 *
 * <p><b>#142/B4-3e-24（差距 D-B4-55）</b>：收口前 gateway 的三处异常出口用
 * {@code String.valueOf(ex.getErrorCode())} 填 {@code ApiResponse.errorCode}，而
 * {@code DataBridgeException}/{@code BusinessException}/{@code DataAccessException} 的
 * {@code errorCode} 字段类型是 {@code int}（＝HTTP 语义值），机读码只活在 {@code message} 前缀
 * （{@code RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": ..."} 这类抛点形态）。后果：前端与调用方
 * 按 {@code errorCode} 匹配 {@code ECOS-SEC-*}／{@code ECOS-DATA-*} 的通道恒落空，只能去解析
 * 人类可读文案 —— 而文案是本层不该承诺的稳定面。</p>
 *
 * <p>本类把"从异常 message 前缀取机读码、取不到才回落数字串"收成一个属主，禁各处再写第二份解析正则。
 * 形态判据取自分册 00 D.5 错误码表的实际字面量集合（{@code ECOS-<域 2~8 大写字母>-<2~4 位数字>[尾母]}，
 * 实测样本：{@code ECOS-SEC-501}／{@code ECOS-SEC-520}／{@code ECOS-DATA-041}／{@code ECOS-KB-062}／
 * {@code ECOS-DQ-101}）。<b>只认 message 起始位</b>：机读码一旦允许出现在文案任意位置，
 * "该请求的错误码是 ECOS-SEC-501"这类转述文案会把别人的码冒充成本请求的码。</p>
 */
public final class MachineErrorCode {

    /**
     * message 起始位的机读码形态。
     *
     * <p>尾部 {@code (?![0-9A-Za-z])} 是<b>右边界</b>：{@code ECOS-SEC-5011} 不能被截成
     * {@code ECOS-SEC-501}（截短即错码），{@code ECOS-SEC-501X} 若真存在则按尾母整枚取走。</p>
     */
    private static final Pattern HEAD = Pattern.compile(
            "^(ECOS-[A-Z]{2,8}-\\d{2,4}[A-Z]?)(?![0-9A-Za-z])");

    private MachineErrorCode() {
    }

    /**
     * 从 message 起始位取机读码。
     *
     * @param message 异常/信封文案，可为 {@code null}
     * @return 命中则返回码本体（不含后续冒号与文案），否则 {@link Optional#empty()}
     */
    public static Optional<String> fromMessage(String message) {
        if (message == null || message.length() < 10) {
            return Optional.empty();
        }
        Matcher m = HEAD.matcher(message);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * 信封 {@code errorCode} 字段的单源取值：有机器码用机器码，没有才回落异常携带的数字码字符串化。
     *
     * <p>回落路径保证既有契约不回归：main 面三异常家族（{@code BusinessException}／
     * {@code DataAccessException}／{@code DataBridgeException}）抛点实测 <b>277</b> 枚，其中数字首参
     * <b>132</b> 枚；只有把机读码写进 message 起始位的那些点会改变 {@code errorCode} 面值，
     * 其余仍拿到与收口前逐字相同的数字串（如 {@code "503"}）。</p>
     */
    public static String orNumeric(String message, int numericCode) {
        return fromMessage(message).orElseGet(() -> String.valueOf(numericCode));
    }
}
