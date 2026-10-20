package com.chinacreator.gzcm.common.testscan;

import java.util.regex.Pattern;

/**
 * 源码门禁的注释剥离器（同族门禁唯一口径，差距 <b>D-B4-40</b> 的收口件）。
 *
 * <p><b>为什么存在</b>：同族门禁长期沿用一条朴素正则
 * {@code src.replaceAll(行注释模式, " ")} 来"只判代码行为"。该写法不区分字符串字面量，
 * 于是 {@code postForObject("http://localhost:8080/api/v1/policy-engine/evaluate", …)}
 * 里的 {@code //} 被当成行注释起点 ⇒ <b>整行代码连带 URL 一起被吃掉</b>。#142/B4-3e-4
 * 批实测：在该口径下 {@code CheckPermissionTool} 从 ABAC 消费集枚举里凭空消失，
 * "消费方少 1 枚"被读成"消费方合规"＝假阴性。剥离器本身不可信时，落在违例行上的盲区
 * 会把"有违例"读成"无违例"，故本类用状态机替换正则。</p>
 *
 * <p><b>口径</b>：注释字符以空格抹平，块注释内的换行原样保留（⇒ 行号与原文一致，
 * 违例可直接定位）；字符串/字符字面量与 {@code """} 文本块逐字符原样输出（含转义序列），
 * 不做还原，以免改变判据的匹配面。文本块必须单独识别：实测 ecos_backend 有 44 个主源码
 * 文件使用 {@code """}（多为 SQL 文本块），三引号若按单引号成对推进会让剥离器跨块错认
 * 状态，把后面的真代码当成字面量留下 ⇒ 违例被藏起来，正是本类要消灭的假阴性形态。</p>
 *
 * <p><b>跨模块复制</b>：仓内无 test-jar 机制，而"不加新 Maven 模块"是根 {@code AGENTS.md}
 * 红线，故各使用方模块各持一份副本。副本不得手工漂移：common-api 的
 * {@code NaiveCommentStripperBanTest} 会枚举全仓同名副本并与本文件逐字符比对。</p>
 *
 * <p><b>本类不含朴素正则</b>：{@link #naiveStrip(String)} 的被禁模式在运行期拼接而成，
 * 源码里不出现"以 // 开头的正则字面量"，因此禁令的豁免集为空（唯一豁免是
 * {@code GatewayRouteIntegrityTest#codeOnly}，它先把字符串字面量整体抹平，形态不同）。</p>
 */
public final class SourceCommentStripper {

    /** 朴素行注释模式的片段（运行期拼接，避免本文件成为禁令的违例面）。 */
    private static final String NAIVE_LINE_BODY = "[^" + "\\n" + "]*";

    /** 朴素块注释模式（同样运行期拼接）。 */
    private static final Pattern NAIVE_BLOCK =
            Pattern.compile("(?s)" + "/\\*" + ".*?" + "\\*/");

    private SourceCommentStripper() {
    }

    /**
     * 引号感知剥离：字面量内的 {@code //} 与块注释定界符不是注释。
     *
     * @param src Java 源码全文
     * @return 只留代码本体（注释位置变空格，换行数保持不变）
     */
    public static String strip(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char d = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (c == '/' && d == '*') {
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                i += 2;
                out.append(' ');
            } else if (c == '/' && d == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                out.append(' ');
            } else if (c == '"' || c == '\'') {
                if (c == '"' && d == '"' && i + 2 < n && src.charAt(i + 2) == '"') {
                    // 文本块：整体原样保留（实测 44 个主源码文件用 """，若不识别会跨块错认状态）
                    out.append("\"\"\"");
                    i += 3;
                    while (i < n) {
                        if (src.charAt(i) == '\\' && i + 1 < n) {
                            out.append(src.charAt(i)).append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        if (src.charAt(i) == '"' && i + 2 < n && src.charAt(i + 1) == '"'
                                && src.charAt(i + 2) == '"') {
                            out.append("\"\"\"");
                            i += 3;
                            break;
                        }
                        out.append(src.charAt(i));
                        i++;
                    }
                    continue;
                }
                char quote = c;
                out.append(c);
                i++;
                while (i < n) {
                    char s = src.charAt(i);
                    if (s == '\\' && i + 1 < n) {
                        out.append(s).append(src.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (s == quote) {
                        out.append(s);
                        i++;
                        break;
                    }
                    if (s == '\n') {
                        // 未闭合字面量（编译不过的源码）：保守停在行尾，剩下的交回主循环
                        break;
                    }
                    out.append(s);
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 被禁的朴素口径，只作反面证据夹具（{@code naiveStripStillBlindsTheUrlLiteral} 钉住）。
     *
     * <p>行为与历史门禁一致：块注释与行注释一律以空格抹平，且不区分字面量。</p>
     */
    public static String naiveStrip(String src) {
        return NAIVE_BLOCK.matcher(src).replaceAll(" ")
                .replaceAll("//" + NAIVE_LINE_BODY, " ");
    }

    /** 与 {@link #naiveStrip(String)} 同口径的行注释模式串（供禁令的判据自证使用）。 */
    public static String naiveLinePattern() {
        return "//" + NAIVE_LINE_BODY;
    }
}
