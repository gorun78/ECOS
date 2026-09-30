package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-07 / W ：DQ 发布门禁红线 —— 事实写入路径<b>禁</b>「补默认值 / 补 0 以通过校验」的
 * 静默填充（详细设计-02 §F02-07 红线 + {@code NoSilentDefaultFillArchTest} 验收）。
 *
 * <p>语义：行级数据缺失（如金额/度量为空）必须被 DQ 规则（DQ-F01..F11）<b>显式拒收</b>
 * （rejected 不入库带 rowNo/field/ruleId/suggestion），而不是在落库前用
 * {@code putIfAbsent(field, 0)} / {@code getOrDefault(field, 0)} / {@code orElse(0)}
 * 等把缺失度量悄悄填成 0「以通过校验」。
 *
 * <p>扫描面 = {@code fact/**} 写入路径 Java（service + dq + model），
 * 目标字段集 = 业务度量/金额度量列（amount / hourlyRate / fte / workHours /
 * contractBase / realizationRate / attributionRatio 及 snake 形态）。
 * 命中即 FAIL。布尔语义列（isPool）与系统列（isDeleted/domain/versionNo）等非度量
 * 字段的 0 默认<b>不</b>在本红线扫描范围内（它们不是"补度量通过校验"）。</p>
 *
 * <p>源码扫描守卫，无 DB，无 .toURI()（沿用 NoDriverManagerArchTest 口径：
 * user.dir 上溯 .git 定位模块根，Files.readAllLines 逐行正则，注释行忽略）。</p>
 */
@DisplayName("F02-07 红线：fact 写入路径禁静默补 0（putIfAbsent/getOrDefault/orElse 落度量列）")
class NoSilentDefaultFillArchTest {

    /** 业务度量/金额列（camelCase + snake_case 双形态）—— 缺失须拒收，不得补 0。 */
    private static final Set<String> MEASURE_FIELDS = Set.of(
            "amount", "amountBh", "amountCny",
            "hourlyRate", "hourly_rate",
            "fte", "workHours", "work_hours",
            "contractBase", "contract_base",
            "realizationRate", "realization_rate",
            "attributionRatio", "attribution_ratio");

    /** 静默补 0 三类模式（宽松：field 用捕获组对齐）。 */
    private static final Pattern PUT_IF_ABSENT =
            Pattern.compile("putIfAbsent\\(\\s*\"([^\"]+)\"\\s*,\\s*0(?:\\.0+\\s*[;,)D])");
    private static final Pattern GET_OR_DEFAULT =
            Pattern.compile("getOrDefault\\(\\s*\"([^\"]+)\"\\s*,\\s*0(?:\\.0+\\s*[;,)D])");
    private static final Pattern MAP_GET_OR_ELSE =
            Pattern.compile("\\.orElse\\(\\s*0(?:\\.0+\\s*[;,)D])\\b");

    @Test
    @DisplayName("fact/** 写入路径无 putIfAbsent/getOrDefault 把度量列补 0")
    void noSilentZeroFillOnMeasureColumns() throws IOException {
        Path factRoot = moduleRoot().resolve("src/main/java/com/chinacreator/gzcm/engine/data/fact");
        assertTrue(Files.isDirectory(factRoot), "缺 fact 写入路径根: " + factRoot);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(factRoot)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                int lin = 0;
                for (String raw : lines) {
                    lin++;
                    String t = raw.trim();
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) {
                        continue;
                    }
                    Matcher m1 = PUT_IF_ABSENT.matcher(raw);
                    if (m1.find() && MEASURE_FIELDS.contains(m1.group(1).toLowerCase())) {
                        violations.add(rel(f) + ":" + lin + ": " + t);
                    }
                    Matcher m2 = GET_OR_DEFAULT.matcher(raw);
                    if (m2.find() && MEASURE_FIELDS.contains(m2.group(1).toLowerCase())) {
                        violations.add(rel(f) + ":" + lin + ": " + t);
                    }
                    // orElse(0)：紧邻变量名含度量字段名
                    Matcher m3 = MAP_GET_OR_ELSE.matcher(raw);
                    if (m3.find() && anyMeasureToken(raw)) {
                        violations.add(rel(f) + ":" + lin + ": " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "F02-07 红线违例：fact 写入路径静默补 0 落地度量列（应显式拒收 rejection，"
                        + "禁止 putIfAbsent/getOrDefault/orElse 落 amount/度量 0 值）：\n"
                        + String.join("\n", violations));
    }

    /** orElse(0) 行的变量名是否命中度量字段（宽松含判断，防误报业务 0 初值如 int count=0）。 */
    private static boolean anyMeasureToken(String line) {
        for (String f : MEASURE_FIELDS) {
            if (line.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private static String rel(Path f) {
        Path base = moduleRoot();
        try {
            return base.relativize(f).toString();
        } catch (Exception e) {
            return f.getFileName().toString();
        }
    }

    private static Path moduleRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (hasGitAndBackend(cur)) {
                return cur.resolve("ecos_backend/engine/data-engine/data-engine-impl");
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未能定位 data-engine-impl 模块根（缺 .git + ecos_backend）");
    }

    private static boolean hasGitAndBackend(Path p) {
        return (Files.isRegularFile(p.resolve(".git")) || Files.isDirectory(p.resolve(".git")))
                && Files.isDirectory(p.resolve("ecos_backend"));
    }
}
