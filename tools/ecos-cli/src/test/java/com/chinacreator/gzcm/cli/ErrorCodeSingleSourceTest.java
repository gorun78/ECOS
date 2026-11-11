package com.chinacreator.gzcm.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错误码单源门禁（计划 §二「错误码表：把散落的 ECOS-CLI-* 集中为常量」）。
 *
 * <p>只把常量集中到 {@link CliErrorCodes} 是不够的：只要还有人写裸字面量，
 * 「单源」就只是口头约定，改码时漏改一处也不会有人发现。本门禁扫全部主源码，
 * 除 {@code CliErrorCodes.java} 外禁止出现 {@code "ECOS-CLI-"} 字面量。</p>
 *
 * <p>同时反向校验：{@code CliErrorCodes} 里的每个常量都必须以 {@code ECOS-CLI-} 开头
 * （否则输出侧口径分叉，用户看到的 error.code 有两套前缀）。</p>
 */
class ErrorCodeSingleSourceTest {

    private static final String PREFIX = "ECOS-CLI-";
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final String OWNER = "CliErrorCodes.java";

    private static List<Path> mainSources() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        return out;
    }

    @Test
    @DisplayName("主源码中除 CliErrorCodes 外不得出现 \"ECOS-CLI-\" 裸字面量")
    void noBareErrorCodeLiteralsOutsideOwner() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : mainSources()) {
            if (OWNER.equals(file.getFileName().toString())) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                if (line.contains("\"" + PREFIX)) {
                    offenders.add(file + ":" + (i + 1) + "  " + trimmed);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "错误码必须走 CliErrorCodes 常量（计划 §二「错误码单源」），发现裸字面量：\n"
                        + String.join("\n", offenders));
    }

    @Test
    @DisplayName("CliErrorCodes 的常量值必须全部以 ECOS-CLI- 开头")
    void everyConstantUsesTheSharedPrefix() throws IOException {
        String src = Files.readString(MAIN_SOURCES.resolve(Path.of("com", "chinacreator", "gzcm",
                "cli", OWNER)), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("String \\w+\\s*=\\s*\"([^\"]*)\";").matcher(src);
        List<String> values = new ArrayList<>();
        while (m.find()) {
            values.add(m.group(1));
        }
        assertTrue(values.size() >= 15, "CliErrorCodes 常量数异常：实得 " + values.size());
        List<String> bad = values.stream().filter(v -> !v.startsWith(PREFIX)).toList();
        assertTrue(bad.isEmpty(), "以下常量未使用统一前缀 " + PREFIX + "：" + bad);
    }

    @Test
    @DisplayName("门禁自身可被触发（离群字面量必须被抓到）")
    void detectorFiresOnOffendingSnippet() {
        String snippet = "EnvelopeMapper.local(CliStatus.SUCCESS, \"ECOS-CLI-CONFIG\", \"…\");";
        assertTrue(containsBareLiteral(snippet), "检测逻辑对裸字面量失明");
        assertTrue(!containsBareLiteral("EnvelopeMapper.local(CliStatus.SUCCESS, CliErrorCodes.CONFIG, "));
        assertTrue(!containsBareLiteral("// 历史写法 \"ECOS-CLI-CONFIG\" 已废弃"),
                "注释行不应误报");
    }

    /** 与 {@link #noBareErrorCodeLiteralsOutsideOwner()} 同一判定，抽出来供自检。 */
    private static boolean containsBareLiteral(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
            return false;
        }
        return line.contains("\"" + PREFIX);
    }

    @Test
    @DisplayName("状态→退出码钉死（0/1/2/3/4），SUCCESS 归零、其余非零且互不相等")
    void exitCodeMappingIsPinned() {
        assertEquals(0, ExitCodes.forStatus(CliStatus.SUCCESS));
        assertEquals(1, ExitCodes.forStatus(CliStatus.FAILED));
        assertEquals(2, ExitCodes.forStatus(CliStatus.BLOCKED));
        assertEquals(3, ExitCodes.forStatus(CliStatus.UNAVAILABLE));
        assertEquals(4, ExitCodes.forStatus(CliStatus.PARTIAL));
        assertEquals(64, ExitCodes.USAGE);
        long distinct = java.util.Arrays.stream(CliStatus.values())
                .mapToInt(ExitCodes::forStatus).distinct().count();
        assertEquals(CliStatus.values().length, distinct, "不同状态不得映射到同一退出码");
    }
}
