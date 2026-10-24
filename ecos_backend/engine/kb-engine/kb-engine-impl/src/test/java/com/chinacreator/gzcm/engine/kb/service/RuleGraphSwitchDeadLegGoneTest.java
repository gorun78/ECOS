package com.chinacreator.gzcm.engine.kb.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3e-58（D-B4-88 第 4 枚 ＝ D-B4-77 死面腿）— {@code RuleGraphService.switchNeo4jEnabled} 行为契约。
 *
 * <p>收口前该方法是：
 * <pre>try { return Boolean.parseBoolean(sw.trim()); } catch (Exception e) { return true; }</pre>
 * 而 {@code Boolean.parseBoolean} 按 JLS 对任意入参都不抛异常（仅 "true" 忽略大小写得 true，
 * 其余一律 false），故 catch 体<b>不可达</b>。它不是运行期 fail-open，而是「数得到的死面」：
 * 宽松降级棘轮门把它计在面上，反而掩盖了「本方法没有判定通道故障态」的事实。</p>
 *
 * <p>本测试锁两件事，缺一不可：
 * <ol>
 *   <li><b>语义不变</b>：删除不可达 try/catch 前后，四态取值逐项一致（null/空白 → true，
 *       "true"/带空格带大写 → true，"false" 与任意非法串 → false）。
 *       这条断言就是「不是行为修复而是死面清理」的证据；若有人顺手改成 fail-closed，本门变红。</li>
 *   <li><b>死腿不许回来</b>：方法体内不得再出现 {@code catch}
 *       （连同 common-api 侧 {@code LenientDegradeTrueFaceRatchetTest} 的零面锚形成双保险：
 *       本模块锚语义，门禁锚全局面集）。</li>
 * </ol>
 */
class RuleGraphSwitchDeadLegGoneTest {

    private static final String METHOD_HEAD = "public boolean switchNeo4jEnabled()";

    // ── 1. 语义：四态取值与收口前逐项一致 ────────────────────

    @Test
    @DisplayName("配置缺失（null）与空白一律默认启用 —— 与收口前一致")
    void missingOrBlankConfigDefaultsToEnabled() {
        assertTrue(switchWithNull(), "字段未注入（null）必须默认启用（enterprise/ultimate 档默认写图）");
        assertTrue(switchWith(""), "空串必须默认启用");
        assertTrue(switchWith("   "), "纯空白必须默认启用");
    }

    @Test
    @DisplayName("显式 true 认可大小写与前后空格")
    void explicitTrueIsEnabled() {
        assertTrue(switchWith("true"));
        assertTrue(switchWith("TRUE"));
        assertTrue(switchWith("  true  "), "trim 语义必须保留");
    }

    @Test
    @DisplayName("显式 false 与任意非 true 串一律关闭 —— 非法值不折成通过")
    void explicitFalseAndGarbageAreDisabled() {
        assertFalse(switchWith("false"), "false 必须关闭");
        assertFalse(switchWith("FALSE"));
        assertFalse(switchWith("yes"), "非法开关值按 Boolean.parseBoolean 语义为 false，不得折成通过");
        assertFalse(switchWith("1"));
        assertFalse(switchWith("nul"));
    }

    // ── 2. 死腿不许回来 ─────────────────────────────────────

    @Test
    @DisplayName("方法体内无 catch：不可达的 fail-open 死面已按 D-B4-77 删除")
    void switchMethodBodyHasNoCatchLeg() throws IOException {
        Path src = locateMainSrc("RuleGraphService.java");
        assertNotNull(src, "RuleGraphService 主源码必须存在");
        // 先剥注释再取方法体：收口说明里逐字写了被删掉的 `try { … } catch (Exception e) { … }`
        // 与其花括号，不剥会同时污染 catch 判定与花括号配平（注释不是代码面）。
        String code = stripComments(Files.readString(src, StandardCharsets.UTF_8));
        String body = methodBody(code);
        assertFalse(body.contains("catch"),
                "switchNeo4jEnabled 不得再包 try/catch（Boolean.parseBoolean 不抛异常 ⇒ 死面）：实跑体=" + body);
        assertTrue(body.contains("Boolean.parseBoolean"),
                "开关判定必须仍是 Boolean.parseBoolean 单语义通道：" + body);
    }

    // ── helpers ─────────────────────────────────────────────

    /** 反射注入私有 @Value 字段后实跑被测方法。 */
    private static boolean switchWith(String rawConfigValue) {
        return switchWith((Object) rawConfigValue);
    }

    /** 字段保持未注入态（null）。 */
    private static boolean switchWithNull() {
        return switchWith((Object) null);
    }

    private static boolean switchWith(Object boxed) {
        try {
            RuleGraphService svc = new RuleGraphService();
            Field f = RuleGraphService.class.getDeclaredField("neo4jSwitchOnWrite");
            f.setAccessible(true);
            f.set(svc, boxed);
            return svc.switchNeo4jEnabled();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("反射装配失败：" + e.getMessage(), e);
        }
    }

    /** 从方法头起按花括号配平取出方法体（含首尾花括号）。 */
    private static String methodBody(String src) {
        int head = src.indexOf(METHOD_HEAD);
        assertTrue(head >= 0, "switchNeo4jEnabled 方法必须在位");
        int open = src.indexOf('{', head);
        assertTrue(open >= 0, "方法体起点必须在位");
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("方法体花括号未配平：" + METHOD_HEAD);
    }

    /** 在 kb-engine-impl 主源码树内按文件名定位。 */
    private static Path locateMainSrc(String fileName) {
        Path root = null;
        for (Path d = Paths.get("").toAbsolutePath(); d != null; d = d.getParent()) {
            if (Files.isDirectory(d.resolve("src/main/java"))) {
                root = d.resolve("src/main/java");
                break;
            }
        }
        if (root == null) return null;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /** 防止方法被改名/改可见性/加重载 —— 一旦签名漂移，上面的行为与死腿断言会读不到方法而变红。 */
    @Test
    @DisplayName("签名守卫：switchNeo4jEnabled 只允许一处 public 定义")
    void methodSignatureIsStable() throws IOException {
        Path src = locateMainSrc("RuleGraphService.java");
        assertNotNull(src);
        String code = stripComments(Files.readString(src, StandardCharsets.UTF_8));
        assertEquals(1, occurrences(code, METHOD_HEAD),
                "switchNeo4jEnabled 只允许一处 public 定义（禁重载折叠）");
    }

    /**
     * 剥行注释与块注释（保留串/字符字面量原样）。
     *
     * <p>不剥注释会做出假判定：本批的收口说明里逐字抄了被删掉的死腿源码，
     * 其中既有 {@code catch} 关键字又有花括号。</p>
     */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < src.length()) {
                    char d = src.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == c) break;
                    j++;
                }
                out.append(src, i, Math.min(j + 1, src.length()));
                i = j + 1;
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                i = (end < 0) ? src.length() : end + 2;
                out.append('\n');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int occurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
