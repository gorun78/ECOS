package com.chinacreator.gzcm.gateway.f0214;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W57 部分：public.sql 待评审 git 跟踪（源：详细设计-02 / D-17 双 DDL 源）。
 *
 * <p>设计意图：{@code ecos-sql/postgresql/public.sql}（主控制 schema = public 目标
 * {@code ecos_control}）应作为<b>待评审、被 git 跟踪</b>的 DDL 资产存在，且
 * {@code ecos-sql/mysql/public_mysql.sql} 提供 mysql 镜像；根 {@code .gitignore}
 * 不得把 {@code public.sql} / {@code public*.sql} 排出（否则视为"未跟踪"）。</p>
 *
 * <p><b>当前实现状态（2026-09-30 实查）= 缺口未闭合 / blocker</b>：</p>
 * <ul>
 *   <li>全仓 grep {@code public.sql} / {@code public_mysql.sql} = <b>0 命中</b>；</li>
 *   <li>{@code ecos-sql/postgresql/} 现仅 {@code 00_init_database.sql} +
 *       {@code 01~08_ecos_*.sql}，无 {@code public.sql}；</li>
 *   <li>{@code ecos-sql/mysql/} 现仅 {@code 01~08_ecos_*.sql}，无 {@code public_mysql.sql}；</li>
 *   <li>根 {@code .gitignore} 未忽略 public*.sql（此项已健康，可固化断言）。</li>
 * </ul>
 *
 * <p><b>本文件（6 项内之一）收口为可发现的 baseline，不伪造 public.sql</b>：</p>
 * <ul>
 *   <li>{@code gitignoreDoesNotExcludePublicSql}（启用）：root .gitignore 无
 *       {@code public.sql} / {@code public*.sql} 忽略行 —— 这条当下为真，先行固化；</li>
 *   <li>{@code publicSqlAbsent_baseline}（启用，soft checkpoint）：如实记录
 *       {@code public.sql}（postgresql）与 {@code public_mysql.sql}（mysql 镜像）当前<b>均缺席</b>。
 *       若有人先建了文件却漏掉本 test，会相反 fail 提示"评审 + git 跟踪"未走全；</li>
 *   <li>{@code @Disabled publicSqlStatementCount}：待 {@code public.sql} 落地后启用，
 *       以分号粗分句断言语句数 &gt; 500（基线 sanity，实际 count 已知后改为精确 +20% 余量）。</li>
 * </ul>
 *
 * @author ECOS Gateway/DB Team / W57-public-sql-gap
 */
@DisplayName("W57 部分：public.sql 待评审/git 跟踪（当前缺席 gap 记录）")
class PublicSqlRegressionTest {

    private static final Path REPO_ROOT = Paths.get("..", "..")
            .toAbsolutePath().normalize();  // gateway/ → ecos_backend/ → ECOS/
    private static final Path PG_SQL_DIR = REPO_ROOT.resolve("ecos-sql/postgresql");
    private static final Path MYSQL_SQL_DIR = REPO_ROOT.resolve("ecos-sql/mysql");
    private static final Path PUBLIC_SQL = PG_SQL_DIR.resolve("public.sql");
    private static final Path PUBLIC_MYSQL_SQL = MYSQL_SQL_DIR.resolve("public_mysql.sql");

    @Test
    @DisplayName("gitignore 守门：root .gitignore 未忽略 public.sql / public*.sql（可立即固化）")
    void gitignoreDoesNotExcludePublicSql() throws IOException {
        Path gitignore = REPO_ROOT.resolve(".gitignore");
        assertTrue(Files.isRegularFile(gitignore), "根 .gitignore 缺失: " + gitignore);

        boolean excludesPublic = false;
        for (String line : Files.readAllLines(gitignore)) {
            String t = line.trim();
            if (t.startsWith("#") || t.isEmpty()) {
                continue;
            }
            String lower = t.toLowerCase();
            if (lower.contains("public.sql") || lower.contains("public*.sql")
                    || lower.contains("public*.sql")) {
                excludesPublic = true;
                break;
            }
        }
        assertFalse(excludesPublic,
                "public.sql 若被 .gitignore 排除则视为"未 git 跟踪"，与 W57 评审前提冲突：需从忽略表删除");
    }

    @Test
    @DisplayName("Baseline（W57 前提未闭合）：public.sql(pgsq) 与 public_mysql.sql(mysql 镜像) 当前均缺席")
    void publicSqlAbsent_baseline() {
        boolean pgExists = Files.isRegularFile(PUBLIC_SQL);
        boolean mysqlExists = Files.isRegularFile(PUBLIC_MYSQL_SQL);

        System.err.println("[PublicSqlRegressionTest] public.sql(pgsq) exists=" + pgExists
                + " | public_mysql.sql(mysql) exists=" + mysqlExists);

        // 如实固化当下 gap：两个文件都不在文件系统里（未被创建评审，也未被跟踪）。
        // 一旦任一落地，本断言会 fail —— 这是有意的：提示评审/登记流程未闭环。
        assertTrue(!pgExists && !mysqlExists,
                "若 public.sql / public_mysql.sql 已存在，须同步启用 @Disabled 的 publicSqlStatementCount，"
                        + "并确认其确被 git 跟踪（git ls-files 命中）。当前基线=两者均缺席");
    }

    @Disabled("W57 blocker：ecos-sql/postgresql/public.sql 尚未创建评审（当前 0 命中），mysql 镜像 public_mysql.sql 同缺。"
            + "public.sql 落地并 git 跟踪后启用：加载文件、以分号粗分句，断言语句数 > 500（基线 sanity；"
            + "实际 count 确定后改用 精确count + 20% 余量）。同时 root .gitignore 已确认未排除 public*.sql。")
    @Test
    @DisplayName("(W57 完成态) public.sql 加载后语句数 > 500 + 与 git 跟踪对齐")
    void publicSqlStatementCount() throws IOException {
        // public.sql 落地后此处：
        // 1. String sql = Files.readString(PUBLIC_SQL);
        // 2. int stmts = (int) sql.chars().filter(c -> c == ';').count();  // 粗分句（去注释后更精）
        // 3. assertTrue(stmts > 500, "public.sql 语句数应 > 500, 实际 " + stmts);
        // 4. 若已知精确基线 N，改为 assertTrue(stmts > (N * 1.2), ...)
    }
}
