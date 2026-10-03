package com.chinacreator.gzcm.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W15（docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-13 / C.6.2，M1）
 * — 三档发布装配矩阵（standard/enterprise/ultimate）：依赖裁剪 + {@code spring.profiles.active}
 * 生效 + 能力开关三处一致。核心红线（ST02，用户裁决 Q9/C6）：
 * <b>standard 档禁实启 Neo4j</b>（图谱形态走 PG 表），enterprise/ultimate 允许 Neo4j。
 *
 * <p>3 用例（验收命令 {@code -Dtest='EditionProfileGuardTest'}，×3 profile，文本扫不启 Spring）：
 * <ol>
 *   <li>tc-standard：standard 档 yml 图谱/分析/存储 provider 不得有 neo4j（= 无 Neo4j bean 装配）</li>
 *   <li>tc-enterprise：enterprise 档允许 neo4j（graph.provider=neo4j + neo4j-uri 存在）</li>
 *   <li>tc-ultimate：ultimate 档允许 neo4j，且若任一档声明 {@code dw.olap.engine} 其值必须 ∈
 *       {doris, clickhouse}（列存二选一）</li>
 * </ol>
 * </p>
 *
 * <p><b>现状基线（2026-09-30 实读）</b>：三个 profile yml 均以 {@code ecos.{graph,analytics,
 * storage}.provider} 表达档位能力开关（standard=pg/pg/pg；enterprise/ultimate=neo4j）。
 * {@code dw.olap.engine} 键**当前未在任何 yml 声明**（列存引擎由 {@code ecos.analytics.provider}
 * 与 doris-* 配置承载），故 tc-ultimate 对 olap.engine 采用"声明则约束、未声明则过"口径
 * （设计文档"若未设也算过"）。</p>
 */
class EditionProfileGuardTest {

    private static Path backendRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path guess = cwd;
        while (guess != null) {
            if (Files.isDirectory(guess.resolve("gateway/src/main/resources"))
                    && Files.isDirectory(guess.resolve("services/sysman"))) {
                return guess;
            }
            guess = guess.getParent();
        }
        return cwd;
    }

    private static String readProfile(Path root, String profile) {
        // profile yml 名形如 application-{standard,enterprise,ultimate}.yml
        Path p = root.resolve("gateway/src/main/resources/application-" + profile + ".yml");
        assertTrue(Files.isRegularFile(p), "缺 profile yml: " + p);
        try {
            return Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("profile yml 读取失败: " + p, e);
        }
    }

    /** 提取所有 {@code provider: <value>} 取值集合 */
    private static Set<String> providers(String yaml) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String line : yaml.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("provider:")) {
                out.add(t.substring("provider:".length()).trim().toLowerCase());
            }
        }
        return out;
    }

    /** tc-standard：ST02 红线 — standard 档禁 Neo4j（图谱 PG 表形态） */
    @Test
    @DisplayName("tc-standard standard 档 yml 不得出现 neo4j provider（ST02 禁实启 Neo4j）")
    void standardProfileForbidsNeo4j() {
        String yaml = readProfile(backendRoot(), "standard");
        Set<String> ps = providers(yaml);
        assertFalse(ps.contains("neo4j"),
                "ST02 红线：standard 档图谱形态走 PG 表，provider 不得出现 neo4j；实际 " + ps);
        assertFalse(yaml.lines().anyMatch(l -> l.trim().startsWith("neo4j-uri")),
                "standard 档不得声明 neo4j-uri 连接");
        // 图谱/分析/存储全部 PG（能力开关下降）
        assertTrue(ps.contains("pg"), "standard 档至少一项 provider 应为 pg，实际 " + ps);
    }

    /** tc-enterprise：允许 Neo4j（图谱 provider=neo4j + 连接 URN） */
    @Test
    @DisplayName("tc-enterprise enterprise 档允许 neo4j（graph.provider=neo4j + neo4j-uri）")
    void enterpriseProfileAllowsNeo4j() {
        String yaml = readProfile(backendRoot(), "enterprise");
        Set<String> ps = providers(yaml);
        assertTrue(ps.contains("neo4j"), "enterprise 档图谱 provider 应为 neo4j，实际 " + ps);
        assertTrue(yaml.lines().anyMatch(l -> l.trim().startsWith("neo4j-uri")),
                "enterprise 档应声明 neo4j-uri 连接串");
    }

    /** tc-ultimate：允许 Neo4j + 列存 olap.engine（声明则 ∈{doris,clickhouse}，未声明则过） */
    @Test
    @DisplayName("tc-ultimate ultimate 档允许 neo4j，dw.olap.engine 若声明则 ∈ {doris, clickhouse}")
    void ultimateProfileAllowsNeo4jAndConstrainsOlapEngine() {
        String ult = readProfile(backendRoot(), "ultimate");
        Set<String> ps = providers(ult);
        assertTrue(ps.contains("neo4j"), "ultimate 档图谱 provider 应为 neo4j，实际 " + ps);
        assertTrue(yamlHasOlapCapability(ult),
                "ultimate 档应有列存/分析能力（doris/clickhouse/olap 配置）");

        // 列存二选一约束：扫描三档，若出现 dw.olap.engine 键，其值必须 ∈ {doris, clickhouse}
        Set<String> allowedEngines = Set.of("doris", "clickhouse");
        for (String profile : List.of("standard", "enterprise", "ultimate")) {
            String yaml = readProfile(backendRoot(), profile);
            for (String line : yaml.split("\\r?\\n")) {
                String t = line.trim();
                if (t.startsWith("olap.engine:")) {
                    String val = t.substring("olap.engine:".length()).trim().toLowerCase();
                    assertTrue(allowedEngines.contains(val),
                            profile + " 档 dw.olap.engine 只能 doris|clickhouse 二选一，实际 " + val);
                }
            }
        }
    }

    private static boolean yamlHasOlapCapability(String yaml) {
        for (String line : yaml.split("\\r?\\n")) {
            String t = line.trim().toLowerCase();
            if (t.contains("doris") || t.contains("clickhouse") || t.contains("olap")) {
                return true;
            }
        }
        return false;
    }
}
