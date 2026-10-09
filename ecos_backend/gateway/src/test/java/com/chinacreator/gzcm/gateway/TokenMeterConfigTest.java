package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详细设计-06 §八 接缝义务 5（<b>M0 直改</b>）—— 模型名称禁用硬编码供应商标识。
 *
 * <p>病灶（X-85）：{@code TokenMeterAspect} 曾在计量兜底处硬编码
 * {@code "hermes-agent"} 作为缺省模型名，使"模型标识唯一来源 = llm-gateway"
 * 的单源纪律在计量侧破口。本守卫断言 <b>可执行代码</b>（剥除注释后）不再出现
 * 任何硬编码模型名，且降级回落已改绑配置单源 {@code llm.default-model}。</p>
 *
 * <p>验收：{@code mvn -Dtest=TokenMeterConfigTest#noHardcodedModelName}</p>
 */
public class TokenMeterConfigTest {

    /** 曾硬编码、须彻底清除的供应商品牌标识（剥注释后不得再出现于可执行代码）。 */
    private static final List<String> FORBIDDEN_MODEL_LITERALS = List.of(
            "hermes-agent", "hermes_agent", "gemini-1.5-pro");

    @Test
    void noHardcodedModelName() throws IOException {
        Path aspect = locateSource(
                "gateway", "src/main/java/com/chinacreator/gzcm/gateway/telemetry/TokenMeterAspect.java");
        assertTrue(Files.isRegularFile(aspect),
                "找不到 TokenMeterAspect 源文件: " + aspect);

        String executable = stripComments(new String(Files.readAllBytes(aspect), StandardCharsets.UTF_8));

        // ① 可执行代码不得再出现任何硬编码模型名字面量（注释里的说明文字不算）。
        for (String literal : FORBIDDEN_MODEL_LITERALS) {
            String token = "\"" + literal + "\"";
            assertFalse(executable.contains(token),
                    "TokenMeterAspect 可执行代码仍硬编码模型名 " + token
                            + "（接缝义务 5 M0 直改：模型名唯一来源 = llm-gateway / 配置单源）");
        }

        // ② 降级回落必须改绑配置单源 llm.default-model（gateway application.yml 权威契约），
        //    而非字面量兜底 —— 这是"去硬编码"的正向承接，防止改成另一个硬编码常量。
        assertTrue(executable.contains("llm.default-model"),
                "TokenMeterAspect 缺少对配置单源 llm.default-model 的绑定；"
                        + "模型名回落必须来自配置单源，不得再自行字面量兜底");
    }

    /** 定位 ecos_backend 根下相对路径的源文件（沿 cwd 向上找 pom.xml + gateway/ 目录）。 */
    private static Path locateSource(String... rel) {
        Path root = backendRoot();
        Path p = root;
        for (String seg : rel) {
            p = p.resolve(seg);
        }
        return p.normalize();
    }

    private static Path backendRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("gateway")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到 ecos_backend 根（pom.xml + gateway/）");
    }

    /**
     * 剥除 Java 块注释与行注释，保留字符串/字符字面量原文
     * （模型名判断依赖字符串内容，故字符串不剥）。
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            char next = i + 1 < n ? src.charAt(i + 1) : 0;
            if (c == '/' && next == '/') {               // line comment
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {         // block comment
                i += 2;
                while (i < n && !(src.charAt(i) == '*' && i + 1 < n && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = i + 2 <= n ? i + 2 : n;
            } else if (c == '"') {                        // string literal: copy verbatim
                out.append(c);
                i++;
                while (i < n) {
                    char sc = src.charAt(i);
                    out.append(sc);
                    if (sc == '\\') {
                        if (i + 1 < n) {
                            out.append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                    if (sc == '"') {
                        break;
                    }
                }
            } else if (c == '\'') {                       // char literal: copy verbatim
                out.append(c);
                i++;
                while (i < n) {
                    char cc = src.charAt(i);
                    out.append(cc);
                    if (cc == '\\') {
                        if (i + 1 < n) {
                            out.append(src.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    i++;
                    if (cc == '\'') {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
