package com.chinacreator.gzcm.engine.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C.3.6（写入通道唯一性）— gateway（:8080 monolith）<b>禁</b>直查数据域 legacy 表
 * （{@code ecos_dq_issue} / {@code ecos_dq_rule} / {@code td_data}），
 * 一律经 datanet REST 契约（W56 → C45）。
 *
 * <p>守卫面 = gateway/src/main 下所有 .java / .xml 里"真正 SQL 语义"的行
 *（SELECT / INSERT INTO / UPDATE / DELETE FROM / FROM / JOIN 动词同工位命中数据域表），
 * 注释行（// 、星号开头、-- 开头）忽略。
 *
 * <pre>
 *   放行（documented, 已知存量 → W56 清偿）：
 *     gateway/.../service/MonitorService#countOpenDqIssues  监控大盘读 /api/v1/monitor/* 依赖
 *     → 已登记 shift 到 M2（走 datanet /api/v1/dq/health/selfcheck 聚合），本文件在测试侧
 *       KNOWN_PREEXISTING 白名单只等 M2 剔除；新增任何其它 gateway 数据域直查 → FAIL。
 * </pre>
 *
 * <p>TenantAwareJdbcTemplate 的 TENANT_TABLES 集合里以 "ecos_dq_rule" 字符串常量登记
 * 属租户过滤词表（无 SQL 动词同位），不在本扫描触发范围（规则只锁 SQL 语义同工位）。
 */
@DisplayName("W56：gateway 禁直查数据域 legacy 表（ecos_dq_issue/td_data/ecos_dq_rule）")
class GatewayNoDomainTableArchTest {

    /** W56 数据域 legacy 表集合（td_data 需边界护着不匹配 td_data_resource）。 */
    private static final Pattern DOMAINTABLE = Pattern.compile(
            "\\b(ecos_dq_issue|ecos_dq_rule|td_data)\\b(?![_\\w])");
    /** SQL 动词同工位（同一条语句同工位命中表名才算直查）。 */
    private static final Pattern SQLVERB = Pattern.compile(
            "\\b(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|FROM|JOIN)\\b",
            Pattern.CASE_INSENSITIVE);
    /** 已知存量白名单（M2 剔除 ≤ M2 之后不动）：reference reason 内嵌在文件名中。 */
    private static final List<String> KNOWN_PREEXISTING = List.of(
            "MonitorService.java"  // countOpenDqIssues 监控大盘聚合读；W56 订正 = 改走 datanet /api/v1/dq 契约
    );

    @Test
    @DisplayName("gateway 主源码无新增数据域 legacy 表 SQL 直查（已有白名单外任何命中 → FAIL）")
    void noDirectSqlOnDataDomainTables() throws IOException {
        Path gatewaySrc = repoRoot().resolve("ecos_backend/gateway/src/main");
        assertTrue(Files.isDirectory(gatewaySrc), "gateway/src/main 缺: " + gatewaySrc);

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(gatewaySrc)) {
            for (Path f : (List<Path>) walk
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml")).toList()) {
                String name = f.getFileName().toString();
                if (KNOWN_PREEXISTING.stream().anyMatch(name::equals)) continue;
                int lin = 0;
                for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    lin++;
                    String t = raw.trim();
                    if (t.isEmpty()) continue;
                    if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                            || t.startsWith("--")) {
                        continue;
                    }
                    if (DOMAINTABLE.matcher(raw).find() && SQLVERB.matcher(raw).find()) {
                        violations.add(f.getFileName() + ":" + lin + ": " + t);
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "W56 违约：gateway 主源码直查数据域 legacy 表（应走 datanet REST 契约）：\n"
                        + String.join("\n", violations)
                        + "\n（存量例外只允许 " + KNOWN_PREEXISTING + "，新增任何其它文件→FAIL）");
    }

    @Test
    @DisplayName("gateway 主源码扫描到至少 1 处数据域表提及（防静默通过）")
    void gatewayMainTreeExists() throws IOException {
        Path gatewaySrc = repoRoot().resolve("ecos_backend/gateway/src/main");
        Path target = gatewaySrc;
        assertTrue(Files.isDirectory(target), "gateway/src/main 目录缺: " + target);
        int javaFiles = 0;
        try (Stream<Path> walk = Files.walk(gatewaySrc)) {
            javaFiles = (int) walk.filter(p -> p.toString().endsWith(".java")).count();
        }
        assertTrue(javaFiles >= 10, "gateway 主源码 .java 应 ≥10，实际 " + javaFiles);
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
