package com.chinacreator.gzcm.engine.data;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W31 (P0) 安全红线守护 —— 详细设计-01 两张必测单之一。
 *
 * <p>红线：data-engine-impl 里不得出现把 RLS 稳定谓词字符串和用户可控
 * <b>sql 文本</b>做<b>字符串拼接</b>进 SQL 的形态（原始违规样本：
 * {@code final String rlsSql = "SELECT * FROM (" + sql + ") AS _rls WHERE " + rlsCondition;}）。
 * 违规形态（源码 regex 硬编码）：字面量 {@code "SELECT * FROM ("} 后跟
 * {@code +} 变量入口（历史崩溃点 QueryController 85 行）。
 *
 * <p>运行时守护点到点（KB24 归属点：L0 · KA 语法 · 走 mvn test）：
 * 春扫 src/main/java 全文；出现即 fail — 防止未来 Polyfiller / 新一代 Agent
 * 重回头作。一改再复扫。</p>
 *
 * <p>注意：本测试<b>不</b>是 ArchUnit class-scan，而是<b>源码文本</b>guard，
 * 因为违规点极少数是 string-literal concat，bytecode-scanner 反而 closure
 * 更弱。文本层是权威护栏。</p>
 */
public class RlsInjectionGuardArchTest {

    /**
     * 违规 1（原文形态）：字面量 "SELECT * FROM ( 紧接 + ».
     */
    private static final Pattern SELECT_STAR_WRAP_CONCAT =
            Pattern.compile("\"\\s*SELECT\\s+\\*\\s+FROM\\s*\\(\\s*\"\\s*\\+");

    /**
     * 违规 2：字符串字面量流入 SQL 硬包装 —— 形式 {@code " ... WHERE " + xxx}
     * 且前方已出现 "SELECT * FROM (" 引用；简化版：出现 "SELECT * FROM (" 字面量的
     * 文件中，若同一文件里还含引号串联的 `+ <ident>` 形态，且"_rls" 字符串出现，
     * 就视为可能的回退。
     * 但为误报防护，只用严格的字面量拼接红线，即 违规 1 —— 两条 test 分别护。
     */
    private static final Pattern WHERE_LIT_CONCAT =
            Pattern.compile("\"\\s*SELECT\\s+\\*\\s+FROM\\s*\\([^)]+\\)\\s+AS\\s+_rls[^\\n]*?\"\\s*\\+|\\+\\s*rlsCondition|\\+\\s*rlsFilter|\\+\\s*rlsCondition\\.");

    @Test
    void noRlsConcatInjectionInDataEngineSources() throws IOException {
        List<String> offenders = scan();
        assertFalse(offenders.isEmpty(), "data-engine-impl/src/main 未找到任何 .java 文件，扫描路径异常");
        List<String> hits = new ArrayList<>();
        for (String off : offenders) {
            Path p = Paths.get(off);
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            // Javadoc/注释引用同源违规字面量的说明（如 {@code "SELECT * FROM (" + sql}），
            // 不属于"实际拼接代码"，剔除后再匹配，避免误报。
            String text = stripComments(raw);
            if (SELECT_STAR_WRAP_CONCAT.matcher(text).find()) {
                hits.add(off + ": SELECT * FROM ( + <var> 字面量拼接");
            }
            if (WHERE_LIT_CONCAT.matcher(text).find()) {
                hits.add(off + ": WHERE 拼接 rlsCondition / rlsFilter 字面量");
            }
        }
        assertTrue(hits.isEmpty(),
                "发现 W31 违规（RLS 谓词 × user sql 字符串拼接）：\n" + String.join("\n", hits));
    }

    /**
     * 剔除 Java 注释（块 {@code /* ... *\/} + {@code //} 行注释）后的字符串——保持
     * 源顺序，避免 Javadoc 引用使抗扫描误报。简化解析：不处理字符串字面量内的 {@code //}
     * 与 {@code /*}，因为本题域内的红线字节序列仅出现在代码不出现于字符串体。
     */
    static String stripComments(String src) {
        final char APOSTROPHE = 39;
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0, n = src.length();
        boolean inBlock = false, inLine = false, inStr = false, inChar = false;
        while (i < n) {
            char ch = src.charAt(i);
            char nxt = (i + 1 < n) ? src.charAt(i + 1) : '\0';
            if (inBlock) {
                if (ch == '*' && nxt == '/') { inBlock = false; i += 2; continue; }
                i++; continue;
            }
            if (inLine) {
                if (ch == '\n') { inLine = false; sb.append(ch); }
                i++; continue;
            }
            if (inStr) {
                sb.append(ch);
                if (ch == '\\' && nxt != 0) { sb.append(nxt); i += 2; continue; }
                if (ch == '"') { inStr = false; }
                i++; continue;
            }
            if (inChar) {
                sb.append(ch);
                if (ch == '\\' && nxt != 0) { sb.append(nxt); i += 2; continue; }
                if (ch == APOSTROPHE) { inChar = false; }
                i++; continue;
            }
            if (ch == '"' && !inStr && !inChar) { inStr = true; sb.append(ch); i++; continue; }
            if (ch == APOSTROPHE && !inStr && !inChar) { inChar = true; sb.append(ch); i++; continue; }
            if (ch == '/' && nxt == '*') { inBlock = true; i += 2; continue; }
            if (ch == '/' && nxt == '/') { inLine = true; i += 2; continue; }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    /** 限定 guard 范围：本模块 src/main/java；不含 test 目录（本测试自身也会提
     *  该字面量，豁免)* */
    private static List<String> scan() {
        Path root = resolveModuleSrcMainJava();
        assertNotNull(root, "无法解析 data-engine-impl/src/main/java");
        assertTrue(Files.isDirectory(root), "data-engine-impl src/main/java 不存在: " + root);
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toString)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 解析到本模块 src/main/java —
     * 尝试顺序（覆盖单模块 build / 跨模块 -pl -am / IDEA classpath 三种部署）：
     * <ol>
     *   <li>当前工作目录（mvn 跑测试时 cwd = 子模块目录）</li>
     *   <li>类加载器 resource + 磁盘手术（fallback 到 target/classes 反推）</li>
     *   <li>相对 user.dir 的 ../../ 游走</li>
     * </ol>
     */
    static Path resolveModuleSrcMainJava() {
        String userDir = System.getProperty("user.dir");
        Path cur = Paths.get(userDir).toAbsolutePath().normalize();
        // 情形 A：cwd 就是 data-engine-impl
        Path a = cur.resolve("src/main/java");
        if (Files.isDirectory(a)) return a;
        // 情形 B：cwd 是 engine/data-engine
        Path b = cur.resolve("data-engine-impl/src/main/java");
        if (Files.isDirectory(b)) return b;
        // 情形 C：cwd 是 eco 仓根 / ecos_backend 根
        Path c = cur.resolve(engineLayoutPointer());
        if (Files.isDirectory(c)) return c;
        // 情形 D：从类加载器反推
        try {
            java.net.URL url = RlsInjectionGuardArchTest.class.getResource("/"
                    + RlsInjectionGuardArchTest.class.getName().replace('.', '/') + ".class");
            if (url != null && "file".equals(url.getProtocol())) {
                Path cls = Paths.get(url.toURI());
                // .../data-engine-impl/target/test-classes/com/chinacreator/gzcm/engine/data/RlsInjectionGuardArchTest.class
                for (int i = 0; i < 12 && cls != null; i++) {
                    if (Files.isDirectory(cls.resolve("../../../src/main/java"))) {
                        return cls.resolve("../../../src/main/java").normalize();
                    }
                    cls = cls.getParent();
                }
            }
        } catch (Exception ignore) {
            // 反推失败退兜
        }
        throw new IllegalStateException(
                "无法解析 data-engine-impl/src/main/java，user.dir=" + userDir);
    }

    private static String engineLayoutPointer() {
        return "engine/data-engine/data-engine-impl/src/main/java";
    }
}
