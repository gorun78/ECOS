package com.chinacreator.gzcm.engine.security.rolechannel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #142/B4-3a — 角色解析 SQL 形态门禁（防回归，与 dev PG 实跑凭证配对）。
 *
 * <p><b>钉住的缺陷本体</b>：{@code RowLevelSecurityDao} / {@code ColumnLevelSecurityDao}
 * 的角色子查询原写作 {@code SELECT "ROLE_ID" FROM TD_USER_ROLE WHERE "USER_ID" = ?}。
 * PostgreSQL 双引号标识符<b>区分大小写</b>，而 {@code ecos_control.td_user_role} 的列是按 DDL
 * 单源（{@code docs/50-db_script/01-ddl-多方言/postgresql/01_ecos_sysman.sql:122}）
 * 以<b>不加引号小写</b>建的 ⇒ 该语句在 dev PG 上恒
 * {@code ERROR: column "ROLE_ID" does not exist}（本批实跑凭证 A，见差距文档 v1.0.14）。
 * 后果：RLS 侧异常被 catch 成空策略 ⇒ 谓词退化为 {@code 1=1}（靠 {@code denyIfEmpty} 兜住），
 * CLS 侧异常直接 {@code visibleColumns=[]} 全拒绝。<b>整个 §4.7.2 矩阵在这个缺陷上不可能通过</b>。</p>
 *
 * <p><b>判定口径（本批实跑校准出来的，不是先验写的）</b>：缺陷形态是<b>双引号包全大写</b>
 * （PG 视其为大小写敏感标识符），而<b>不加引号的大写</b>（{@code SecurityConfigDao} 的
 * {@code JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id}）会被 PG 折叠成小写、<b>可执行</b>，
 * 故门禁只禁前者、不禁后者——若把后者也判红，就是拿"改写风格"冒充缺陷，属假红。
 * 首版门禁正是没带这条对照而被实跑打回（命中 SecurityConfigDao 的合法 JOIN 形态），
 * 现以 {@link #unquotedUppercaseJoinFormIsAccepted()} 把该边界钉成用例。</p>
 *
 * <p><b>为什么只看 {@code """} SQL 文本块</b>：本批先量过一次——对 security-impl 全量 main 源
 * 直接 grep {@code "[A-Z][A-Z0-9_]*"} 会命中大量非 SQL 字面量（{@code "UP"/"ALLOW"/"SECRET"…}），
 * 门禁会因噪声被"放宽"成假绿。文本块内的双引号在 Java 里只可能是 SQL 定界标识符，
 * 判定面因此是精确的。诚实边界：本门禁<b>不覆盖</b>普通字符串拼接的 SQL
 * （sysman {@code UserController}/{@code UserLookupDao}/{@code AuthDao} 与
 * {@code ClearanceLookupDao} 的同类形态已实跑证明同样报错，登记为 <b>D-B4-7</b>，
 * 属另一模块批次，不由本门禁冒充收口）。</p>
 */
class RoleResolutionSqlShapeTest {

    /** 角色子查询的必需形态：不加引号小写 + JDBC 占位符（三档方言同时成立的唯一写法）。 */
    private static final Pattern REQUIRED_ROLE_SUBQUERY = Pattern.compile(
            "SELECT\\s+role_id\\s+FROM\\s+td_user_role\\s+WHERE\\s+user_id\\s*=\\s*\\?");

    /** 已知角色解析点：RLS 与 CLS 两个 DAO 必须各自命中一条子查询（具名文件，不牵合法的 JOIN 形态）。 */
    private static final List<String> ROLE_RESOLUTION_SITES = List.of(
            "com/chinacreator/gzcm/engine/security/dao/RowLevelSecurityDao.java",
            "com/chinacreator/gzcm/engine/security/dao/ColumnLevelSecurityDao.java");

    /** 违例形态：SQL 内以双引号包住的全大写标识符（PG 视为大小写敏感标识符）。 */
    private static final Pattern QUOTED_UPPER_IDENT = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"");

    /** 注释行（{@code --} 起始）先剥离，避免把证据注释里的旧形态当成活代码。 */
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*--.*$");

    @Test
    @DisplayName("门禁面不为空 — security-impl 必须有 ≥2 个含 td_user_role 的 SQL 文本块（防止删掉子查询来骗过门禁）")
    void roleSubquerySitesExist() throws IOException {
        List<SqlBlock> blocks = roleSubqueryBlocks();
        assertTrue(blocks.size() >= 2,
                "期望 RLS + CLS 两处角色子查询文本块，实得 " + describe(blocks)
                        + "（若某处删除了角色解析，属功能缺失，不得靠门禁空转通过）");
    }

    @Test
    @DisplayName("具名点 — RowLevelSecurityDao / ColumnLevelSecurityDao 必须各含小写不加引号的角色子查询")
    void roleResolutionSitesUseUnquotedLowercaseSubquery() throws IOException {
        List<SqlBlock> blocks = allSqlBlocks();
        for (String site : ROLE_RESOLUTION_SITES) {
            List<SqlBlock> mine = blocks.stream()
                    .filter(b -> b.where().replace('\\', '/').contains(site)).toList();
            assertFalse(mine.isEmpty(), "扫描面缺失具名文件 " + site + "（门禁不得因扫不到而空转）");
            boolean matched = mine.stream().anyMatch(b -> REQUIRED_ROLE_SUBQUERY.matcher(b.sql()).find());
            assertTrue(matched, "角色子查询形态错误（应为不加引号小写；dev PG 实测引号大写恒报 "
                    + "column \"ROLE_ID\" does not exist）: " + site);
        }
    }

    @Test
    @DisplayName("合法形态不误伤 — td_user_role 以不加引号大写（ur.ROLE_ID）参与 JOIN 是 PG 折叠合法的，不得判违例")
    void unquotedUppercaseJoinFormIsAccepted() throws IOException {
        // SecurityConfigDao.queryHighestRoleProfileForUser 用 `JOIN td_user_role ur ON ur.ROLE_ID = ...`：
        // 未加引号 ⇒ PG 折叠为小写 ⇒ 可执行。门禁只禁「双引号包全大写」，不强制小写拼写，
        // 否则就是把合法 SQL 改成红，属假红而非防回归。
        boolean hasJoinSite = roleSubqueryBlocks().stream()
                .anyMatch(b -> b.sql().toUpperCase().contains("JOIN TD_USER_ROLE"));
        assertTrue(hasJoinSite, "SecurityConfigDao 的 JOIN 形态应出现在扫描面里（证本用例确有覆盖）");
        for (SqlBlock b : roleSubqueryBlocks()) {
            assertFalse(QUOTED_UPPER_IDENT.matcher(b.sql()).find(),
                    "引用 td_user_role 的文本块内含双引号全大写标识符: " + b.where());
        }
    }

    @Test
    @DisplayName("security-impl 全部 SQL 文本块内不得出现双引号全大写标识符（PG 大小写敏感陷阱）")
    void noQuotedUppercaseIdentifiersInSqlBlocks() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SqlBlock b : allSqlBlocks()) {
            Matcher m = QUOTED_UPPER_IDENT.matcher(b.sql());
            while (m.find()) {
                violations.add(b.where() + " → \"" + m.group(1) + "\"");
            }
        }
        assertTrue(violations.isEmpty(),
                "SQL 文本块内含双引号全大写标识符（在 PG 上按大小写敏感解析，非三档方言通用写法）:\n  "
                        + String.join("\n  ", violations));
    }

    // ── 扫描实现 ────────────────────────────────────────

    private static List<SqlBlock> roleSubqueryBlocks() throws IOException {
        List<SqlBlock> out = new ArrayList<>();
        for (SqlBlock b : allSqlBlocks()) {
            if (b.sql().toLowerCase().contains("td_user_role")) out.add(b);
        }
        return out;
    }

    /** 提取 main 源里所有 {@code """} 文本块（先剥 {@code --} 注释行），返回"文件:行 + 内容"。 */
    private static List<SqlBlock> allSqlBlocks() throws IOException {
        Path root = mainJavaRoot();
        List<SqlBlock> blocks = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(p);
                int i = 0;
                while (i < lines.size()) {
                    int open = lines.get(i).indexOf("\"\"\"");
                    if (open < 0) { i++; continue; }
                    int startLine = i;
                    StringBuilder body = new StringBuilder();
                    String tail = lines.get(i).substring(open + 3);
                    int close = tail.indexOf("\"\"\"");
                    if (close >= 0) {
                        body.append(tail, 0, close);
                        i++;
                    } else {
                        body.append(tail).append('\n');
                        i++;
                        boolean closed = false;
                        while (i < lines.size()) {
                            int c2 = lines.get(i).indexOf("\"\"\"");
                            if (c2 >= 0) {
                                body.append(lines.get(i), 0, c2);
                                closed = true;
                                i++;
                                break;
                            }
                            body.append(lines.get(i)).append('\n');
                            i++;
                        }
                        if (!closed) break;
                    }
                    String sql = LINE_COMMENT.matcher(body.toString()).replaceAll("");
                    blocks.add(new SqlBlock(root.relativize(p).toString() + ":" + (startLine + 1), sql));
                }
            }
        }
        return blocks;
    }

    private static Path mainJavaRoot() throws IOException {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(
                    "engine/security-engine/security-engine-impl/src/main/java");
            if (Files.isDirectory(candidate)) return candidate;
            Path self = cur.resolve("src/main/java");
            if (Files.isDirectory(self) && cur.endsWith("security-engine-impl")) return self;
            cur = cur.getParent();
        }
        throw new IOException("未定位到 security-engine-impl/src/main/java（角色解析 SQL 形态门禁的扫描根）");
    }

    private static String describe(List<SqlBlock> blocks) {
        return blocks.stream().map(SqlBlock::where).toList().toString();
    }

    private record SqlBlock(String where, String sql) { }
}
