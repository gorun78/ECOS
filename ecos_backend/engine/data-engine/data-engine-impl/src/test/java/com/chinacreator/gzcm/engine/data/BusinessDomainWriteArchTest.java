package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.3.6 — 业务域写入通道唯一性（{@code /write-channel}，M1；ST07 数据域二分）。
 *
 * <p>规则：{@code ecos_dw} 业务域五层表 <b>只允许</b> data-engine-impl
 * 内部通过 C.3.6 写通道（{@code WriteChannelController} → 服务 → Mapper）读写。
 * 任何 data-engine-impl 之外的 module 里出现 MyBatis {@code <mapper>} XML 且引用
 * {@code ecos_dw.} 限定表名 → FAIL（跨引擎越权写业务域表，违反 C.3.6 "网关唯一入口"）。
 *
 * <p>非严格 SQL 现场：Java 源码里出现 {@code "ecos_dw."} 字面量也一起扫（F02-14 A3
 * 时代 kb 里 {@code INSERT INTO ecos_knowledge.kb_doc_chunk} 就是同族反例；W65 已清偿，
 * 本测试永久守卫）。
 */
@DisplayName("C.3.6：非 data-engine 模块禁 MyBatis 直读写 ecos_dw 业务域表")
class BusinessDomainWriteArchTest {

    /** 唯一的合法承载 module 目录（相对 ecos_backend/）。 */
    private static final String DATA_ENGINE_IMPL = "engine/data-engine/data-engine-impl/";

    /** 触发条件 = {@code ecos_dw.} 带点源限定名（Java 或 XML 或 SQL 字面量）。 */
    private static final Pattern ECOS_DW_QUALIFIED = Pattern.compile(
            "ecos_dw\\.[A-Za-z_]\\w*");

    /** 白名单：非 data-engine 但仅出现在注释 / 文档 / SQL 白名单配置字面量（只用作校验）。 */
    private static final Set<String> KNOWN_PREEXISTING = Set.of(
            // ai-engine 里 AgentToolSqlWhitelist.java —— LLM UDF SQL 白名单表名配置字面量
            // （非 SQL 直读；W49/C38 QualifiedTableNameArchTest 已另守卫）。
            "AgentToolSqlWhitelist.java",
            "SecurityCheckerNodeDenyTest.java",
            // kb-engine 里 W62「A3 退出」双跑 diff 白名单与禁用断言 @Disabled 文案里
            // 出现 "ecos_dw.doc_chunk"（作为跨库写通道 REST 端点契约的字符串宣言），非 SQL 直读。
            "NoKbChunkWriteArchTest.java",
            "KbChunkWriteGoneArchTest.java");

    @Test
    @DisplayName("非 data-engine 模块禁 ecos_dw 限定表名直读直查（Java + XML 同扫）")
    void noEcosDwOutsideDataEngine() throws IOException {
        Path backend = repoRoot().resolve("ecos_backend");
        assertTrue(Files.isDirectory(backend), "ecos_backend 缺");

        List<String> violations = new ArrayList<>();
        Set<String> offenders = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(backend)) {
            for (Path f : walk.filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/target/") ;
                    })
                    .filter(p -> !p.toString().endsWith("/") && p.getFileName() != null)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".java") || n.endsWith(".xml");
                    })
                    .toList()) {
                // 只列文件
                if (!Files.isRegularFile(f)) continue;
                String name = f.getFileName().toString();
                if (KNOWN_PREEXISTING.contains(name)) continue;
                // Windows 分隔符归一：repoRoot/repo 均为绝对 FileSystem 绝对路径，relativize 会带 \
                String rel = backend.relativize(f).toString().replace('\\', '/');
                if (rel.startsWith(DATA_ENGINE_IMPL)) continue;  // 合法目标侧
                FileCheck.check(rel, name, f, violations);
                if (!violations.isEmpty()
                        && violations.get(violations.size() - 1).startsWith(name + ":")) {
                    offenders.add(name);
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "C.3.6 越权：以下非 data-engine 文件含 ecos_dw 限定名（应走 /write-channel）:\n"
                        + String.join("\n", violations)
                        + "\noffenders: " + offenders);
    }

    @Test
    @DisplayName("data-engine-impl 里至少 1 处 ecos_dw 限定名存在（write-channel 唯一入口非过检）")
    void dataEngineOwnsEcosDwTables() throws IOException {
        Path impl = repoRoot().resolve("ecos_backend").resolve(
                Path.of("engine").resolve("data-engine").resolve("data-engine-impl").resolve("src/main"));
        int hits = 0;
        try (Stream<Path> walk = Files.walk(impl)) {
            for (Path f : (List<Path>) walk
                    .filter(p -> p.toString().endsWith(".xml")
                            || p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                if (ECOS_DW_QUALIFIED.matcher(text).find()) hits++;
            }
        }
        assertTrue(hits >= 2,
                "data-engine-impl 内 ecos_dw 限定名应 ≥2 处（write-channel fact mapper 家族），实际 " + hits);
    }

    /** 单文件扫描：Java 忽略 行首 // * /* 注释；XML 忽略 行首 <!--。 */
    private static final class FileCheck {
        static void check(String rel, String name, Path f, List<String> violations)
                throws IOException {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            int lin = 0;
            for (String raw : text.split("\\R", -1)) {
                lin++;
                String t = raw.trim();
                if (t.isEmpty()) continue;
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                        || t.startsWith("--") || t.startsWith("<!--")) {
                    continue;
                }
                // 仅 -File 文档纯叙述（.md 不进入）；.java / .xml 只信实际字面量
                if (ECOS_DW_QUALIFIED.matcher(raw).find()) {
                    violations.add(name + ":" + lin + " (" + rel + "): " + t);
                    return; // 每文件只记首个（长行降噪）
                }
            }
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = dir;
        while (root != null) {
            if (Files.isRegularFile(root.resolve(".git")) || Files.isDirectory(root.resolve(".git"))) {
                return root;
            }
            root = root.getParent();
        }
        Path cur = dir;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("ecos_backend"))) return cur;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到 ECOS 仓库根（缺 .git）");
    }
}
