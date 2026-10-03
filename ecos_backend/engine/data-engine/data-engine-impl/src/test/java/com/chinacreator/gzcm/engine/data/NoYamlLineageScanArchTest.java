package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F02-11 (W64) —— 血缘不再扫 YAML / 拼接 LIMIT：仅对血缘路径文件（path 含
 * {@code [Ii]lineage}）施加两条文本红线：
 * <ol>
 *   <li>禁 {@code yaml_content ILIKE} 全表扫（D-23）—— 血缘表
 *   {@link com.chinacreator.gzcm.engine.data.service.DataLineageService}
 *   应经参数化 bound 查询。</li>
 *   <li>禁 {@code LIMIT " + <var>} 字符串拼接（IR05）—— 应改用
 *   {@code LIMIT ?} 传参。</li>
 * </ol>
 *
 * <p><b>范围口径</b>：本测试既不是全模块 lint，也不是 SQL 语法断言；仅覆盖
 * <b>血缘派生</b>路径文件（F02-11 目标 = 数据血缘不再走 YAML 反向解析 + LIMIT 拼接）。
 * 存储适配器里历史遗留的
 * {@code " LIMIT " + limit} 属另族（不在 D-23/W64 口径内），
 * 已由 {@link com.chinacreator.gzcm.engine.data.quality.service} 等其它
 * 专项 arch 测试分别治理，不在此处收编。
 */
@DisplayName("F02-11 血缘不扫 YAML：lineage 路径禁 yaml_content ILIKE / LIMIT 拼接")
class NoYamlLineageScanArchTest {

    private static final Pattern LINEAGE_PATH = Pattern.compile("(?i)lineage");
    private static final Pattern YAML_ILIKE =
            Pattern.compile("yaml_content\\s+ILIKE", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_CONCAT =
            Pattern.compile("(?i)\\bLIMIT\\s*\"?\\s*\\+");

    @Test
    void lineageScopedSourcesAvoidYamlIlkeAndLimitConcat() throws IOException {
        List<String> hits = new ArrayList<>();
        Path root = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk
                    .filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> LINEAGE_PATH.matcher(f.getFileName().toString()).find())
                    .sorted()
                    ::iterator) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (YAML_ILIKE.matcher(text).find()) {
                    hits.add(p.toString() + ": yaml_content ILIKE 全表扫（W64/D-23）");
                }
                if (LIMIT_CONCAT.matcher(text).find()) {
                    hits.add(p.toString() + ": LIMIT \" + var 拼接（IR05）");
                }
            }
        }
        assertTrue(hits.isEmpty(),
            "F02-11 违规 —— 血缘 YML 扫描 / LIMIT 拼接回归（lineage 路径）:\n" + String.join("\n", hits));
    }
}
