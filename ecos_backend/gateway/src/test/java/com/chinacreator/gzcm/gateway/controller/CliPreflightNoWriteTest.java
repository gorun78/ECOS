package com.chinacreator.gzcm.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 计划 §三.3 / §九 红线：preflight <b>只报告，不创建 run</b>。
 *
 * <p>{@code CliPreflightControllerTest} 已从行为面钉住 {@code createsRun=false}，但那只证明
 * 「报告字段是 false」——若实现里真去写了 run 表，字段照样可以谎报 false。本门禁从<b>源码面</b>
 * 补上互补证明：preflight 的两个实现文件里不得出现任何写语句，也不得依赖任何 Run 写入 DAO。</p>
 *
 * <p>§九 明文：「`preflight` 缺数据 → BLOCKED 且审计无 run 行」。行为面（BLOCKED + 11 步）
 * 已由 controller 测试覆盖；「无 run 行」由此处的源码面无写入 + {@code createsRun=false} 共同保证。</p>
 */
class CliPreflightNoWriteTest {

    private static final List<String> GUARDED = List.of(
            "src/main/java/com/chinacreator/gzcm/gateway/controller/CliPreflightController.java",
            "src/main/java/com/chinacreator/gzcm/gateway/dao/CliPreflightProbeDao.java");

    /** 写语句形态（大小写不敏感，容忍跨行）。 */
    private static final Pattern WRITE_SQL = Pattern.compile(
            "(?i)\\b(INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|TRUNCATE\\s|DROP\\s|ALTER\\s+TABLE"
                    + "|CREATE\\s+(TABLE|INDEX))\\b");

    /** Run 写入相关的依赖/调用形态。 */
    private static final Pattern RUN_WRITE = Pattern.compile(
            "(?i)(WAgentOrchestrator|startRun\\s*\\(|RunDao|WAgentRunDao|insertRun\\s*\\(|createRun\\s*\\()");

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("ecos_backend").resolve("gateway"))) {
            dir = dir.getParent();
        }
        assertNotNull(dir, "未定位到仓根（找不到 ecos_backend/gateway）");
        return dir;
    }

    private static String read(String rel) throws Exception {
        Path f = repoRoot().resolve("ecos_backend/gateway").resolve(rel);
        assertTrue(Files.isRegularFile(f), "受保护文件缺失: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** 去掉行注释与块注释，避免把说明文字当成实现。 */
    private static String stripComments(String java) {
        return java.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    @DisplayName("preflight 实现面不得含任何写 SQL（只报告的机器层证明）")
    void preflightSourcesContainNoWriteSql() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String rel : GUARDED) {
            String code = stripComments(read(rel));
            var m = WRITE_SQL.matcher(code);
            if (m.find()) {
                offenders.add(rel + " → " + m.group());
            }
        }
        assertTrue(offenders.isEmpty(),
                "preflight 实现里出现写语句（违反「只报告，不创建 run」）: " + offenders);
    }

    @Test
    @DisplayName("preflight 不得依赖 Run 写入通道（编排器/Run DAO）")
    void preflightDoesNotDependOnRunWriters() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String rel : GUARDED) {
            String code = stripComments(read(rel));
            var m = RUN_WRITE.matcher(code);
            while (m.find()) {
                offenders.add(rel + " → " + m.group());
            }
        }
        assertTrue(offenders.isEmpty(),
                "preflight 依赖了 Run 写入通道（预检不得产生 run）: " + offenders);
    }

    @Test
    @DisplayName("探活 DAO 全仓只能有只读查询（禁 SELECT 1 冒充可达）")
    void probeDaoIsReadOnlyAndReadsRealTable() throws Exception {
        String rel = "src/main/java/com/chinacreator/gzcm/gateway/dao/CliPreflightProbeDao.java";
        String code = stripComments(read(rel));
        assertFalse(WRITE_SQL.matcher(code).find(), "探活 DAO 含写语句");
        assertTrue(code.toUpperCase(java.util.Locale.ROOT).contains("SELECT"),
                "探活 DAO 应至少有一条真实查询");
        // 「真读表」：必须点名具体表，不能只 SELECT 1
        assertFalse(Pattern.compile("(?i)SELECT\\s+1\\b").matcher(code).find(),
                "探活不得用 SELECT 1 冒充表可达性（应真读目标表）");
        assertTrue(Pattern.compile("(?i)FROM\\s+[a-z_]+\\.[a-z_]+").matcher(code).find(),
                "探活查询应使用 schema 限定的真实表名");
    }
}
