package com.chinacreator.gzcm.gateway.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W56 验收（详细设计-02 §7.1 W56 → C45）— {@code GatewayNoDomainTableArchTest} 源码门禁。
 *
 * <p>命题（T-DAT-01 数据域边界 / IR01 跨引擎不操作对方表）：gateway 单体
 * （{@code gateway/src/main/**}）的 Java 源码<b>禁止</b>以 schema 限定名直查
 * 数据域 / 其他微服务 schema 表，必须改经 datanet 契约 REST（W56：现状 gateway
 * 直查 {@code ecos_dq_issue}/{@code ecos_dq_rule}，须改经 datanet 契约）：
 * <ul>
 *   <li>禁 {@code \becos_dw\.\w+} —— 数据域五层业务表（R-1：新写落引擎 schema，
 *       gateway 禁直查）；</li>
 *   <li>禁 {@code \becos_dq\.\w+} —— DQ 治理表（R-1b：dq 归属数据域 schema）；</li>
 *   <li>禁 {@code \becos_sysman\.\w+ / ecos_datanet / ecos_buszhi / ecos_dccheng /
 *       ecos_aiming} —— knownLegacy 按服务分库残留（ST07「5+1」禁项，R9 只停写不迁）。</li>
 * </ul>
 *
 * <p>边界裁定（2026-10-21 实测收窄，防误伤）：控制域五引擎 schema
 * （{@code ecos_data / ecos_ontology / ecos_knowledge / ecos_ai / ecos_cognitive}）
 * + 主控制 schema（现 {@code public}）属 gateway 合法承载面，<b>不入禁表</b>；
 * 故禁表仅含数据域 {@code ecos_dw/ecos_dq} 与 knownLegacy 服务残留 schema，
 * 不含泛化 {@code \becos_\w+}。
 *
 * <p>例外规则：行 strip 后以 {@code //} 或 {@code *} 或 {@code /*} 起的注释行跳过。
 *
 * <p>baseline（2026-10-21 实测）：gateway src/main 42 文件，禁表 schema 限定命中 = 0
 * → PASS。<b>新增一处越权 → FAIL。</b>
 *
 * <p>surefire 工作目录 = {@code gateway/}，故相对路径 {@code src/main/}。
 */
class GatewayNoDomainTableArchTest {

    /**
     * 禁表（schema.table）限定名。仅含数据域（T-DAT-01）+ knownLegacy 服务残留
     * schema；<b>不含</b>控制域 5 引擎 schema + 主控制 schema（gateway 合法承载）。
     */
    private static final Pattern FORBIDDEN_SCHEMA_TABLE = Pattern.compile(
            "\\b(?:ecos_dw|ecos_dq|ecos_sysman|ecos_datanet|ecos_buszhi"
          + "|ecos_dccheng|ecos_aiming)\\.[A-Za-z_][A-Za-z0-9_]*");

    /** 真注释行（strip 后以 {@code //} / {@code *} / {@code /*} 起）。 */
    private static final Pattern COMMENT_LINE = Pattern.compile("^\\s*(//|\\*|/\\*)");

    private static Path gatewaySrcMain() {
        Path root = Paths.get("src/main").toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "gateway/src/main 未找到: " + root);
        return root;
    }

    @Test
    @DisplayName("gateway(src/main) Java 源码零数据域/禁表 schema 限定引用（W56/T-DAT-01/IR01）")
    void noDomainTableRefInGatewayMain() throws IOException {
        Path root = gatewaySrcMain();

        Set<String> tokens = new LinkedHashSet<>();
        List<String> byFile = new ArrayList<>();
        int fileChecked = 0;

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("target"))
                .forEach(p -> {
                    try {
                        fileChecked++;
                        List<String> lines = Files.readAllLines(p);
                        int fileHits = 0;
                        Set<String> fileToks = new LinkedHashSet<>();
                        for (String line : lines) {
                            if (COMMENT_LINE.matcher(line).find()) continue;   // 注释行跳过
                            Matcher m = FORBIDDEN_SCHEMA_TABLE.matcher(line);
                            while (m.find()) {
                                fileToks.add(m.group());
                                fileHits++;
                            }
                        }
                        if (!fileToks.isEmpty()) {
                            tokens.addAll(fileToks);
                            byFile.add(root.relativize(p).toString().replace('\\', '/')
                                    + " (" + fileHits + ")");
                        }
                    } catch (IOException e) {
                        fail("读取失败: " + p + " -> " + e.getMessage());
                    }
                });
        }

        assertTrue(fileChecked > 0, "gateway src/main 未扫到任何 .java 文件（路径错？）");
        assertTrue(tokens.isEmpty(),
                "gateway 禁查数据域/knownLegacy 服务表 schema 限定引用（W56/T-DAT-01/IR01）。"
                        + " baseline=0, actual tokens=" + tokens
                        + (byFile.isEmpty() ? "" : ", files=" + byFile.stream()
                                .map(s -> "\"" + s + "\"").collect(Collectors.joining(", "))));
    }
}
