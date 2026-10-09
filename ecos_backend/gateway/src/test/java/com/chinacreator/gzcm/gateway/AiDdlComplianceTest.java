package com.chinacreator.gzcm.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F06-18 详细设计-06 数据层归位 — 新增 AI 表 DDL 合规门禁。
 *
 * <p><b>断言（源码扫描，无 DB）</b>：E 章 V200~V204 中真正"新建表"的四条脚本
 * （{@code ecos_ai_tool_registry} / {@code ecos_ai_tool_permission_binding} /
 * {@code ecos_ai_compression_batch} / {@code ecos_ai_guardrail_decision_ledger}）
 * 必须满足 MC01~MC02 + DR05~DR08：
 * <ol>
 *   <li><b>UUID 主键</b>：{@code id VARCHAR(36)} 应用侧生成 + {@code PRIMARY KEY (id)}，
 *       禁用 {@code gen_random_uuid()} / {@code SERIAL} / {@code nextval}（MC01）；</li>
 *   <li><b>七基线列</b>：{@code create_by}/{@code update_by}/{@code create_time}/
 *       {@code update_time}/ {@code is_deleted}（DR05）+ {@code domain}（DR08）+
 *       {@code version_no}（DR07）；</li>
 *   <li><b>类型红线</b>：零 {@code jsonb}（MC02，JSON 语义列用 TEXT）、零
 *       {@code timestamptz}（MC03 显式）。</li>
 * </ol>
 *
 * <p>边界：V202（{@code __ai_session_compression_governance}）是<b>对既有真身的
 * ADD COLUMN</b>（R-19 口径：public 真身只加列不改归属），不含 CREATE TABLE，故
 * 不在"新表"断言面内 —— 本门禁只针对真正 {@code CREATE TABLE} 的新建白名单四脚本。
 *
 * <p>验收：{@code mvn -f ecos_backend/pom.xml -pl gateway -am test
 * -Dtest='AiDdlComplianceTest#newAiTablesHaveBaselineColumnsAndUuidPk'}
 */
class AiDdlComplianceTest {

    private static final Path MIGRATION_DIR_REL =
            Paths.get("docs/50-db_script/02-flyway-migration/gateway");

    /** AI 新表脚本（含 CREATE TABLE）—— V202 是既有真身的 ADD COLUMN（ALTER-only），故用负向排除。 */
    private static final Pattern NEW_TABLE_SCRIPT = Pattern.compile("^V20(0|1|3|4)__ai_.*\\.sql$");

    /** DR05~DR08 七基线列。 */
    private static final String[] BASELINE_COLUMNS = {
            "create_by", "update_by", "create_time", "update_time",
            "is_deleted", "domain", "version_no"
    };

    /** MC01/MC02 类型红线（词边界，避开注释里的中文说明与 "serialization" 之类）。 */
    private static final Pattern[] FORBIDDEN = {
            Pattern.compile("gen_random_uuid", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bjsonb\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(big|small)?serial\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnextval\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\btimestamptz\\b", Pattern.CASE_INSENSITIVE)
    };

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        assertTrue(false, "cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
        return null;
    }

    @Test
    @DisplayName("AI 新表 DDL 均有 VARCHAR(36) UUID 主键 + 七基线列，且零 jsonb/serial/nextval")
    void newAiTablesHaveBaselineColumnsAndUuidPk() throws IOException {
        Path migrationDir = repoRoot().resolve(MIGRATION_DIR_REL);
        assertTrue(Files.isDirectory(migrationDir), "迁移单源目录缺失: " + migrationDir);

        List<Path> scripts = listAiScripts(migrationDir, NEW_TABLE_SCRIPT);
        assertTrue(scripts.size() >= 4,
                "AI 新表 DDL 脚本应有 ≥4（V200/V201/V203/V204），实测 " + scripts.size());

        for (Path script : scripts) {
            String sql = stripCommentLines(Files.readString(script, StandardCharsets.UTF_8));
            String name = script.getFileName().toString();
            assertTrue(sql.toUpperCase().contains("CREATE TABLE"),
                    name + " 应为建表脚本（含 CREATE TABLE），实际缺失");

            // MC01：UUID 主键
            assertTrue(Pattern.compile("\\bid\\s+VARCHAR\\(36\\)\\s+NOT NULL", Pattern.CASE_INSENSITIVE)
                            .matcher(sql).find(),
                    name + " 缺 MC01 UUID 主键形态 `id VARCHAR(36) NOT NULL`");
            assertTrue(Pattern.compile("PRIMARY\\s+KEY\\s*\\(\\s*id\\s*\\)", Pattern.CASE_INSENSITIVE)
                            .matcher(sql).find(),
                    name + " 缺 `PRIMARY KEY (id)`（MC01 主键须落在应用侧 UUID 列）");

            // DR05~DR08 七基线列
            for (String col : BASELINE_COLUMNS) {
                assertTrue(Pattern.compile("\\b" + Pattern.quote(col) + "\\b", Pattern.CASE_INSENSITIVE)
                                .matcher(sql).find(),
                        name + " 缺 DR 基线列 `" + col + "`");
            }

            // MC01/MC02 类型红线
            for (Pattern p : FORBIDDEN) {
                assertTrue(!p.matcher(sql).find(),
                        name + " 命中类型红线 " + p.pattern() + "（违 MC01/MC02/MC03）");
            }
        }
    }

    static List<Path> listAiScripts(Path dir, Pattern namePattern) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .filter(f -> namePattern.matcher(f.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    /** 去除整行 `--` 注释（自审注释含 "JSON" / "jsonb" 字样，会污染红线扫描）。 */
    static String stripCommentLines(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (line.trim().startsWith("--")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
