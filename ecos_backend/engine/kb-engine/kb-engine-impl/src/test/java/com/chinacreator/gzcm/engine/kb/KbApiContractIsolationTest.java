package com.chinacreator.gzcm.engine.kb;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F04-18 kb API 契约隔离测试 — 源码文本扫描。
 *
 * <p>断言 {@code kb-engine-api/src/main} 所有 .java 源文件<b>不出现
 * {@code engine.ontology} 字面量</b>（包括 import 语句与 FQCN 使用）。
 *
 * <p>与 {@link com.chinacreator.gzcm.engine.kb.arch.KbDirectCallLlmArchTest}
 * 同源（文本扫描方式）：ArchUnit ClassFileImporter 扫描已编译 class files
 * 无法反映「新增 import 但尚未使用」的边界情况，文本扫描在 CI 编译前即可
 * 提前拦截违规提交。</p>
 *
 * <p>路径解析：开发环境 root = {@code pom.xml} 所在仓库根；{@code Paths.get("")}
 * 是 Maven surefire 默认 relativeToCwd=project 的 working directory，即
 * {@code <module>/}；向上找 {@code ecosystem-ontology-engine/backstage/kb-engine-api}
 * 相对位置更稳妥 — 使用 {@code ../../kb-engine-api} 相对定位，找不到时回退 {@code .}。</p>
 *
 * <p>失败信息含 <b>首 30 条 offending line</b>，方便逐条整改。</p>
 */
public class KbApiContractIsolationTest {

    private static final List<Path> API_SRC_ROOTS = new ArrayList<>();

    static {
        // Surefire 工作目录默认 = 当前 mvn 模块（kb-engine-impl），
        // 因此 api 源在 ../../kb-engine-api/src/main/java
        Path[] candidates = {
                Paths.get("..", "..", "kb-engine-api", "src", "main", "java"),
                Paths.get("kb-engine-api", "src", "main", "java"),
                Paths.get("../", "kb-engine-api", "src", "main", "java"),
        };
        for (Path p : candidates) {
            if (Files.isDirectory(p)) {
                Path norm = p.toAbsolutePath().normalize();
                if (norm.endsWith(Paths.get("kb-engine-api/src/main/java"))) {
                    API_SRC_ROOTS.add(norm);
                    break;
                }
            }
        }
        if (API_SRC_ROOTS.isEmpty()) {
            System.err.println("[KbApiContractIsolationTest] WARN: kb-engine-api/api main src 定位失败；"
                    + "尝试兜底 relativeToCwd checkbox：可能在模块根下运行（mvn -pl kb-engine-api test）");
            // 兜底：若 cwd 即 kb-engine-api，直接使用当前
            Path cwd = Paths.get("").toAbsolutePath().normalize();
            if (cwd.endsWith(Paths.get("kb-engine-api"))
                    && Files.isDirectory(cwd.resolve("src/main/java"))) {
                API_SRC_ROOTS.add(cwd.resolve("src/main/java").normalize());
            }
        }
    }

    @Test
    public void apiModuleHasNoEngineInternalImports() throws IOException {
        if (API_SRC_ROOTS.isEmpty()) {
            fail("kb-engine-api/src/main/java 未在可识别路径找到 — 检查 working directory "
                    + "(cwd = " + Paths.get("").toAbsolutePath() + ")");
        }

        List<String> offenders = new ArrayList<>();
        for (Path root : API_SRC_ROOTS) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> scanFile(p, offenders));
            }
        }

        assertTrue(offenders.isEmpty(),
                "F04-18 kb-engine-api 契约隔离违反：以下文件含 engine.ontology 字面量（期望 0）\n"
                + offenders.stream().limit(30).reduce("", (a, b) -> a + "\n" + b)
                + (offenders.size() > 30 ? "\n... (+" + (offenders.size() - 30) + " more)" : ""));
    }

    /** 单文件扫描：去注释后逐行检测 "engine.ontology" 字面量（覆盖 import、FQCN、字符串里的
     *  真实引用；排除 javadoc/行注释里的说明性提及，避免自述文档被误判为耦合）。 */
    static void scanFile(Path file, List<String> offenders) {
        try {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            String stripped = stripComments(src);
            String[] lines = stripped.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.indexOf("engine.ontology") >= 0) {
                    offenders.add(file.getFileName() + ":" + (i + 1) + " | " + line.trim());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("KB api 契约扫描读文件失败: " + file, ex);
        }
    }

    /** 去注释：移除 {@code //} 行注释与 {@code /* ... *\/} 块注释；字符串/字符字面量完整保留。 */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n) {
                char d = s.charAt(i + 1);
                if (d == '/') {
                    while (i < n && s.charAt(i) != '\n') i++;
                    continue;
                }
                if (d == '*') {
                    i += 2;
                    while (i < n) {
                        if (s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/') { i += 2; break; }
                        if (s.charAt(i) == '\n') out.append('\n');
                        i++;
                    }
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
