package com.chinacreator.gzcm.buszhi.workflow;

/**
 * 源文件扫描工具（W85/W86 门守卫共用）— 去注释，保留字符串字面量。
 *
 * <p>去注释 = 去 {@code //} 行注释与 {@code /* ... *}/{@code //}
 * 块注释（未转义，Java 源文件足够）；字符串/字符字面量内部不动（这里的 SQL 事实文本）。
 *
 * <p>剥离后未被移除的 {@code .java} / {@code yml} / 配置文件里没有 {@code /*} 风格，
 * 只对 .java 生效。对 {@code String.contains} 或其他含字面量的非敏感场景也安全。
 *
 * <p>2026-09-30 · W149 拆工具包，W85/W86/M0 门守卫共用了此处。
 */
final class WorkflowArchSources {

    private WorkflowArchSources() {}

    /** 去注释：{@code //} 行内 / {@code /* ... *}/{@code //} 块注释移除；字符串/字符字面量完整保留。 */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char d = s.charAt(i + 1);
                if (d == '/') {
                    while (i < n && s.charAt(i) != '\n') i++;
                    if (i < n) { out.append(s.charAt(i)); i++; }
                    continue;
                }
                if (d == '*') {
                    i += 2;
                    while (i < n) {
                        if (s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/') { i += 2; break; }
                        if (s.charAt(i) == '\n') out.append('\n');
                        i++;
                    }
                    out.append(' ');
                    continue;
                }
            }
            if (c == '"' || c == '\'') {
                char q = c;
                out.append(c);
                i++;
                while (i < n) {
                    char q2 = s.charAt(i);
                    out.append(q2);
                    i++;
                    if (q2 == '\\') {
                        if (i < n) { out.append(s.charAt(i)); i++; }
                        continue;
                    }
                    if (q2 == q) break;
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
