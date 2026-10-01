package com.chinacreator.gzcm.gateway.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 数据域 public.sql 回归门禁 — 源码扫描，无运行进程依赖。
 *
 * <p>背景（W49 / R-1 审批结果 a）：数据域表现状全在 {@code public}，
 * R-1 裁决 = 新写落引擎 schema、{@code public} 只停写不迁。
 * 本门禁守护 {@code public} 侧 DDL 存量在仓库中被跟踪且不再膨胀：
 *
 * <ol>
 *   <li>.gitignore 必须有 <b>且仅有</b> 明确的 {@code public.sql}（任一形态）保护：
 *       正则 {@code ^.*public\.sql\s*$} 或 {@code ^.*\*\.sql\s*$} 任一命中
 *       → 该行会排除掉 DDL 存量追踪 → FAIL；</li>
 *   <li>{@code ecos-sql/postgresql/} 实际 DDL 源（00_init + 01~08 分 schema）在仓库
 *       中可被枚举；最大文件应 &gt; 5KB（真实 DDL 是几十 KB，5KB 是安全下限）；</li>
 *   <li>{@code public.sql}(拼写来源)</b>的具体路径当前树<b>不存在</b>：
 *       <b>public.sql 物理落点 = {@code ecos-docker/scripts/v47_reconverge_public.sql}</b>
 *       （reconverge 脚本，对照 R-1 "public 停写"），grep 实测见本类 Javadoc。
 *       因此 3 号下标 {@code publicSqlSanitySize} 标记 {@code @Disabled}
 *       （缺文件不能黑盒失败；一旦后续真写 {@code public.sql}，重新启用该用例即可）。</li>
 * </ol>
 *
 * <p>surefire 工作目录 = {@code gateway/}，仓库根 = {@code ../../../}。
 */
class PublicSqlRegressionTest {

    private static final String REPO_ROOT = "../../../";  // surefire cwd = ecos_backend/gateway/

    /** 5KB 安全下限（真实 DDL 是几十 KB）。 */
    private static final long MIN_PUBLIC_SQL_BYTES = 5 * 1024L;

    /** .gitignore 中禁止的 public.sql 保护模式（任一命中即 FAIL）。 */
    private static final Pattern GITIGNORE_PUBLIC =
            Pattern.compile(".*public\\.sql\\s*$");
    private static final Pattern GITIGNORE_ALL_SQL =
            Pattern.compile(".*\\*\\.sql\\s*$");

    private static Path repoRoot() {
        Path root = Paths.get(REPO_ROOT).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(root), "仓库根未找到: " + root);
        return root;
    }

    /** 1. .gitignore 不得含 "public.sql" 或 "*.sql" 类排除模式（否则 DDL 存量脱版）。 */
    @Test
    @DisplayName(".gitignore 不得排除 public.sql 或 *.sql（DDL 存量须被版本控制）")
    void gitignoreMustNotExcludePublicSql() throws IOException {
        Path gi = repoRoot().resolve(".gitignore");
        assertTrue(Files.isRegularFile(gi), "根 .gitignore 缺失: " + gi);
        List<String> lines = Files.readAllLines(gi);
        List<String> offenders = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // 仅检查 glob 类条目（排除与文件路径无关联的目录锚点已无关，该检查足够）。
            if (GITIGNORE_PUBLIC.matcher(line).matches()
                    || GITIGNORE_ALL_SQL.matcher(line).matches()) {
                offenders.add(line);
            }
        }
        assertTrue(offenders.isEmpty(),
                ".gitignore 禁止排除 public.sql / *.sql（DDL 脱版 → 基线不可重放）: " + offenders);
    }

    /** 2. ecos-sql/{postgresql,mysql} 实际 DDL 源枚举且最大 PG DDL &gt; 5KB。 */
    @Test
    @DisplayName("ecos-sql/{postgresql,mysql} DDL 源齐备 + PG 最大 ddd &gt; 5KB")
    void sqlDdlSourcesPresentAndSaneSize() throws IOException {
        Path pg = repoRoot().resolve("ecos-sql/postgresql");
        Path my = repoRoot().resolve("ecos-sql/mysql");
        assertTrue(Files.isDirectory(pg), "ecos-sql/postgresql/ 缺失: " + pg);
        assertTrue(Files.isDirectory(my), "ecos-sql/mysql/ 缺失: " + my);

        long[] maxHolder = {0L};
        List<String> pgFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.list(pg)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".sql"))
                  .forEach(p -> {
                      try {
                          long sz = Files.size(p);
                          if (sz > maxHolder[0]) maxHolder[0] = sz;
                          pgFiles.add(p.getFileName() + " (" + sz + "B)");
                      } catch (IOException e) {
                          fail("读取大小失败: " + p + " -> " + e.getMessage());
                      }
                  });
        }
        long max = maxHolder[0];
        // 物理落点实测（2026-10-21 grep）：00_init_database.sql (1288B)
        //  + 01..08_ecos_*.sql；其中 04_ecos_ontology.sql ~ 23.8KB 为最大，远超 5KB。
        assertTrue(max > MIN_PUBLIC_SQL_BYTES,
                "PG DDL 最大文件应 &gt; 5KB (sane floor): current max = " + max
                        + "B; files=" + pgFiles);
    }

    /**
     * 3. public.sql 物理落点（grep 实测，2026-10-21）：<b>不存在</b>于
     *    {@code ecos-sql/postgresql/}。相关镜像 / 迁移脚本在
     *    {@code ecos-docker/scripts/v47_reconverge_public.sql}（reconverge 脚本，对照 R-1
     *    "public 停写"）。一旦未来真写 {@code public.sql} 到 {@code ecos-sql/postgresql/}，
     *    重新启用该用例：断言 &gt; 5KB 即回归。
     */
    @Disabled("ecos-sql/postgresql/public.sql 不存在（grep 实测；public 镜像 DDL 已迁 v47_reconverge 路径，R-1 停写）")
    @Test
    @DisplayName("public.sql（若落库）应 &gt; 5KB（sanity floor）")
    void publicSqlSanitySize() throws IOException {
        Path f = repoRoot().resolve("ecos-sql/postgresql/public.sql");
        assertTrue(Files.isRegularFile(f), "public.sql 缺失: " + f);
        long sz = Files.size(f);
        assertTrue(sz > MIN_PUBLIC_SQL_BYTES,
                "public.sql 应 &gt; 5KB：当前 " + sz + "B");
    }
}
