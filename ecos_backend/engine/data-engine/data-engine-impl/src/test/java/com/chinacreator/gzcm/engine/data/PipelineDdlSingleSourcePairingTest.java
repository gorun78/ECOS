package com.chinacreator.gzcm.engine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * W58 / D-17（详细设计-02 E.5 lint 纪律检查③「ecos-sql 与单源同名表定义存在性配对」，M2）——
 * 管道 DAG 两张表（{@code ecos_pipeline_definition}/{@code ecos_pipeline_execution}）的
 * <b>DDL 单源 × 多库镜像配对</b>离线护栏。
 *
 * <p>背景：W58/C47 实证管道两表原本只在 {@code ecos-sql/postgresql/03_ecos_data.sql}
 * 有建表、单源目录 {@code gateway/.../db/migration/} 缺失 → 空库重建与 lint 配对漂移。
 * D-17 补写 {@code V170__pipeline_definition_single_source.sql} 进单源目录关闭 W58；
 * V170 头注自审声明「与 ecos-sql/postgresql/03 列形态一致性由 db-migration-lint 检查③配对校验」。
 * 本测试把「该表的配对盘点」落成离线可跑护栏：**只锁 D-17 两张表**（不越权全树 99 文件扫描），
 * 精确对齐 E.5 检查③的语义，而不扩大成 noisy 的基线全量 lint。</p>
 *
 * <p>断言（每张表）：</p>
 * <ul>
 *   <li>① 单一源侧（V170）与多库镜像侧（03_ecos_data.sql）**两侧都**存在该表 CREATE TABLE 块
 *       （存在性配对——防任一侧被静默删除）。</li>
 *   <li>② 两侧块的**列名集合相等**（形态配对——防单源补写时漏列/多列与多库镜像漂移；
 *       只比列名不比类型文案，避免 NOW()/CURRENT_TIMESTAMP 这类已知等价写法被误判，见 V170 头注合规化①）。</li>
 * </ul>
 *
 * <p><b>不触库、不 Spring 容器、不联网</b>：纯文件读取 + 正则解析。
 * 白名单 = 仅 D-17 两表（R9 不擅扩；全语_Group 方言配对已登记 §5 属人工/lint 面，不在本护栏）。</p>
 */
@DisplayName("W58/D-17 管道两表 DDL 单源×多库镜像配对（E.5 检查③，M2）")
class PipelineDdlSingleSourcePairingTest {

    /** D-17 两张管道 DAG 表（V170 单源补写；03_ecos_data.sql 多库镜像）。 */
    private static final String[] PIPELINE_TABLES = {
            "ecos_pipeline_definition",
            "ecos_pipeline_execution",
    };

    @ParameterizedTest(name = "{0}: 单源 V170 与 ecos-sql 03 两侧均定义且列名集合一致")
    @ValueSource(strings = {"ecos_pipeline_definition", "ecos_pipeline_execution"})
    void pipelineTablePairing_inBothSources_andColumnsMatch(String table) throws IOException {
        Path repoRoot = resolveRepoRoot();

        String singleSource = readTableBlock(
                repoRoot.resolve("ecos_backend/gateway/src/main/resources/db/migration")
                        .resolve("V170__pipeline_definition_single_source.sql"),
                table, "单源 V170");
        String multiDb = readTableBlock(
                repoRoot.resolve("docs/50-db_script/sql/8split/postgresql/03_ecos_data.sql"),
                table, "多库镜像 03_ecos_data.sql");

        Set<String> srcCols = columnNames(singleSource, table, "单源 V170");
        Set<String> imgCols = columnNames(multiDb, table, "多库镜像 03_ecos_data.sql");

        assertEquals(imgCols, srcCols,
                "DDL 单源×多库镜像列名集合漂移（E.5 检查③配对违规）：表 " + table + " 单源=" + srcCols
                        + " 镜像=" + imgCols);
    }

    @Test
    @DisplayName("双侧文件自身可读（配对前提：单源 V170 与多库 03 均存在）")
    void bothSourceFiles_present() {
        Path repoRoot = resolveRepoRoot();
        assertTrue(Files.isRegularFile(repoRoot.resolve(
                "ecos_backend/gateway/src/main/resources/db/migration/V170__pipeline_definition_single_source.sql")),
                "单源目录缺失 V170__pipeline_definition_single_source.sql —— DDL 单源漂移，W58 违规");
        assertTrue(Files.isRegularFile(repoRoot.resolve("docs/50-db_script/sql/8split/postgresql/03_ecos_data.sql")),
                "多库镜像缺失 docs/50-db_script/sql/8split/postgresql/03_ecos_data.sql —— W58 配对前提不成立 (P3 归集后路径)");
    }

    // ─────────────────────────── 工具 ───────────────────────────

    private Path resolveRepoRoot() {
        Path srcMain = RlsInjectionGuardArchTest.resolveModuleSrcMainJava();
        Path cur = srcMain;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("docs")) && Files.isDirectory(cur.resolve("ecos_backend"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        fail("无法从 " + srcMain + " 上溯到 ECOS 仓根（含 docs + ecos_backend）以定位 DDL 单源/多库镜像目录");
        return null; // unreachable
    }

    /** 读文件按表名锚定 CREATE TABLE 块；锚点行含目标表 token，累积到首个以 ");" 结尾行。 */
    private String readTableBlock(Path sqlFile, String table, String where) throws IOException {
        assertTrue(Files.isRegularFile(sqlFile), "缺失 " + where + " 文件 " + sqlFile);
        String body = Files.readString(sqlFile, StandardCharsets.UTF_8);
        String block = extractCreateTableBlock(body, table);
        assertTrue(block != null,
                "未能在 " + where + "（" + sqlFile.getFileName() + "）定位 CREATE TABLE … "
                        + table + "（块）—— DDL 漂移，W58 配对前提不成立");
        return block;
    }

    /** 从 CREATE TABLE 块首行之后逐行取首个标识符作为列名（跳过以 COMMENT/约束关键字开头的行）。 */
    private Set<String> columnNames(String block, String table, String where) {
        Set<String> cols = new LinkedHashSet<>();
        String[] lines = block.split("\\R", -1);
        boolean entered = false;
        for (String line : lines) {
            String t = stripLineComment(line).trim();
            if (t.isEmpty()) {
                continue;
            }
            if (!entered) {
                entered = true; // 首行 = CREATE TABLE ... ( opener，跳过（列从次行开始）
                continue;
            }
            if (t.endsWith(";") && !t.startsWith(",")) {
                // 收尾 ");" 或表约束尾行
                if (t.equals(")") || t.startsWith(")") ) {
                    break;
                }
            }
            // 列定义行：首个 token 为列名；排除表级约束关键字
            String first = t.replaceAll("^,\\s*", "").split("\\s+")[0];
            String upper = first.toUpperCase();
            if (upper.equals("PRIMARY") || upper.equals("CONSTRAINT") || upper.equals("UNIQUE")
                    || upper.equals("FOREIGN") || upper.equals("CHECK") || first.equals(")")) {
                continue;
            }
            if (Pattern.matches("[A-Za-z_][A-Za-z0-9_]*", first)) {
                cols.add(first.toLowerCase());
            }
        }
        assertTrue(!cols.isEmpty(),
                "未能从 " + where + " 表 " + table + " 解析出任何列名 —— 块形态漂移，请人工复核");
        return cols;
    }

    private String stripLineComment(String line) {
        int idx = line.indexOf("--");
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    /** 按行提取 CREATE TABLE … <schema>.<table> ( … ); 正文块（同 AuditColumnPresenceTest 口径）。 */
    private String extractCreateTableBlock(String sql, String table) {
        Pattern anchor = Pattern.compile(
                "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[\\w\\.]*" + Pattern.quote(table) + "\\b",
                Pattern.CASE_INSENSITIVE);
        String[] lines = sql.split("\\R", -1);
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (anchor.matcher(lines[i]).find()) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        StringBuilder block = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i];
            block.append(line).append('\n');
            if (line.trim().endsWith(");")) {
                return block.toString();
            }
        }
        return null;
    }
}
