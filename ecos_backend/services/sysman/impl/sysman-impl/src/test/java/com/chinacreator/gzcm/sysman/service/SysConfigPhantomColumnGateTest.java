package com.chinacreator.gzcm.sysman.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 系统配置查询面「幻影列」回归门（#147CC · 活体缺陷实证）。
 *
 * <p><b>为什么需要本门</b>：dev PG 实查 {@code ecos_control.sys_config} 只有 13 列，
 * 不含 {@code config_options}/{@code impact_scope}；而旧实现的根列表与元数据两条 SELECT 引用了
 * 这两列 ⇒ 活体上 {@code GET /api/v1/system/config} 与 {@code /metadata} <b>恒 500</b>
 * （2026-11-05 活体复现：18081 返回 {@code {"code":-1,"message":"查询配置失败"}}）。
 *
 * <p><b>口径</b>：两个键名允许出现在 {@code @JsonProperty("…")}（wire 键面必须保留）里，
 * <b>不许</b>出现在其它字符串字面量（即 SQL 选列或 {@code row.get(…)} 取值）中。判据先剥掉
 * 注解再扫，避免把键面声明误判成回归。</p>
 *
 * <p><b>反证</b>：把任一列名写进 SQL/取值字面量，本门即红（{@link #predicateCatchesRegression()}）。</p>
 */
class SysConfigPhantomColumnGateTest {

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern JSON_PROPERTY = Pattern.compile("@JsonProperty\\(\"(?:[^\"]*)\"\\)");

    private static List<String> offenders(String source) {
        String stripped = JSON_PROPERTY.matcher(source).replaceAll("@JsonProperty()");
        List<String> hits = new ArrayList<>();
        Matcher m = STRING_LITERAL.matcher(stripped);
        while (m.find()) {
            String literal = m.group(1);
            if (literal.contains("config_options")) {
                hits.add("config_options");
            }
            if (literal.contains("impact_scope")) {
                hits.add("impact_scope");
            }
        }
        return hits;
    }

    @Test
    @DisplayName("SysConfigQueryService 的 SQL/取值面不得引用幻影列（键面注解除外）")
    void serviceHasNoPhantomColumnLiterals() throws IOException {
        String source = Files.readString(sourcePath(), StandardCharsets.UTF_8);
        assertEquals(List.of(), offenders(source),
                "查询面再次引用 DDL 不存在的列 ⇒ 活体会 500；键位请用 null 保留，禁选列");
        assertTrue(source.contains("edition"), "实存列 edition 仍须参与选择（键面依赖）");
        assertTrue(source.contains("@JsonProperty(\"config_options\")"),
                "键位声明须保留（前端 || 回落依赖该键存在）");
    }

    @Test
    @DisplayName("反证：判据能变红（合成样例）；注解形态不误报")
    void predicateCatchesRegression() {
        assertFalse(offenders("@JsonProperty(\"impact_scope\") String x").contains("impact_scope"),
                "@JsonProperty 里的键名不得误报");
        assertEquals(List.of("config_options"),
                offenders("String sql = \"config_options\";"));
        assertEquals(List.of("impact_scope"),
                offenders("row.get(\"impact_scope\")"));
        assertEquals(List.of("config_options"),
                offenders("String sql = \"SELECT config_options FROM sys_config\";"));
    }

    /** Surefire CWD 漂移：向上探测同时含 ecos_backend/ 与 ecos-docker/ 的仓库根。 */
    private static Path sourcePath() {
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("ecos_backend")) && Files.isDirectory(cur.resolve("ecos-docker"))) {
                return cur.resolve("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                        + "com/chinacreator/gzcm/sysman/service/SysConfigQueryService.java");
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到仓库根，CWD=" + Paths.get("").toAbsolutePath());
    }
}
