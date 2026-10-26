package com.chinacreator.gzcm.sysman.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.chinacreator.gzcm.sysman.testscan.SourceCommentStripper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 密级取数面标识符白名单门禁（#142/B4-3e-82，收口台账 <b>D-B4-99 e)</b>）。
 *
 * <p><b>缺陷本质</b>：{@code ClearanceLookupDao.queryClearanceRows} 改前是
 * {@code (String tableName, String idColumn, String idValue, String tenantId)}，内部
 * {@code String.format("SELECT clearance_level FROM %s WHERE %s = ? …", tableName, idColumn)}
 * ——表名与列名由调用方字符串拼进 SQL。改前唯一生产调用点传的是字面量，<b>今日不可利用</b>；
 * 真正的问题是它<b>无门禁</b>：任何后来者把变量（租户号、机构号、配置键，乃至前端可影响的
 * 字典项）传进这两个位置即成 SQL 注入，而本模块的密级裁决正建在这条取数腿之上。
 * 「现在没人这么调」不是安全属性，「类型上表达不出来」才是。</p>
 *
 * <p><b>收口形态</b>：标识符只存在于 {@link ClearanceLookupDao.ClearanceRowKey} 枚举常量里的
 * <b>完整语句字面量</b>（带租户／不带租户两形态各一条）。方法签名收 {@code ClearanceRowKey}，
 * 运行期无拼接、无格式化；未登记的行表在类型系统上无法表达。本门把这件事钉成可复跑读数。</p>
 *
 * <p><b>判据（八条，全部作用在剥注释后的源码 + 运行期反射面上）</b>：
 * ① 格式化家族恒 0：{@code String.format}／{@code formatted(}／{@code %s}／{@code MessageFormat}；
 * ② SQL 行内标识符拼接恒 0：同一行内既含 SQL 关键字（SELECT/FROM/WHERE/JOIN）又含
 * {@code + 变量} 的行读数 0（字面量续行形如 {@code + "FROM …"} 不误计——{@code +} 后紧跟引号）；
 * ③ 语句字面量多重集＝9 枚（文本块 2 + 普通字面量 7）且表名闭包＝三枚在册表；
 * ④ 白名单条目闭包＝{@code {USER_PROFILE}}，逐枚钉 table/idColumn/两形态语句原文；
 * ⑤ 占位符 arity <b>多重集</b>＝九枚语句逐枚同号（实跑 {@code [0,1,1,1,1,1,2,2,2]}），
 *    且零占位符形态只许出现在登记过的无参语句（全局默认档不带租户腿）上——防「语句改了、参数没改」
 *    与「值被拼进 SQL」两件事（不写「每枚都必须有 ?」：那条会把合法的无参语句判成违例）；
 * ⑥ 反射面：{@code findOrgById} 不存在；{@code queryClearanceRows} 只有一枚且形参恰为
 * {@code (ClearanceRowKey, String, String)}；DAO 全部 {@code public} 方法参数里不含
 * 「可当标识符传入」的裸 {@code String} 表名/列名组合（判据＝参数个数 ≥3 且前两参皆 String）；
 * ⑦ 生产调用点闭包＝{@code SubjectClearanceQueryServiceImpl} 一枚文件；
 * ⑧ 活性：合成回退源码（格式化 + 标识符拼接）逐条命中①②。</p>
 *
 * <p><b>已声明盲区</b>：① 判据②按行判，把变量拼到<b>不含 SQL 关键字</b>的续行上不命中——
 * 由③（语句多重集必须整体等于基线）与④（白名单逐枚钉原文）双重兜住：新增任何一条语句、
 * 或改动任一条语句的字面量都会让多重集变红；② 只扫本 DAO 文件与其调用点，不覆盖别的 DAO；
 * ③ 不查已编译构件，也不做 live SQL 执行（本机 Docker daemon 未起＝无 dev PG，
 * 本批证据等级为「单元 + 源码级门禁」，非 live）。</p>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl services/sysman/impl/sysman-impl test}</p>
 */
@Timeout(120)
class ClearanceLookupDaoSqlFaceRatchetTest {

    /** 被测 DAO 源码（仓根相对路径）。 */
    private static final String DAO_SOURCE =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/ClearanceLookupDao.java";

    /** 唯一在册生产调用点（密级五档链属主单源实现）。 */
    private static final String ONLY_PRODUCTION_CALLER =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/security/clearance/service/impl/"
                    + "SubjectClearanceQueryServiceImpl.java";

    /** ① 格式化家族：拼出标识符的三条通道 + 一条备选。 */
    private static final Pattern FORMAT_FAMILY = Pattern.compile(
            "String\\.format|\\.formatted\\(|%1\\$s|%s|MessageFormat");

    /** ② 同一行内「SQL 关键字 + 变量拼接」。 */
    private static final Pattern SQL_LINE_WITH_IDENT_ARG = Pattern.compile(
            "(?i).*(SELECT|FROM|WHERE|JOIN).*\\+\\s*[A-Za-z_][A-Za-z0-9_.()]*\\s*[+;].*");

    /** 语句字面量抽取：普通字符串字面量。 */
    private static final Pattern PLAIN_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.){8,})\"");

    /** 语句字面量抽取：文本块。 */
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(?s:(.*?))\"\"\"");

    /** ③ 语句字面量基线枚数（#142/B4-3e-82 建门批实跑读数＝白名单 2 + COALESCE 2 + 默认档 2 + 机构档 2 + 租户档 1）。 */
    private static final int BASELINE_SQL_LITERALS = 9;

    /** ③ 表名闭包：密级取数只许读这三枚属主侧在册表。 */
    private static final Set<String> BASELINE_TABLES = Set.of(
            "td_user_security_profile", "td_user_role", "td_role_security_profile");

    /**
     * ③′ 占位符 arity <b>多重集</b>基线（#142/B4-3e-82 建门批实跑读数，与九枚语句一一对应）。
     *
     * <p>存在理由：判据③原本写「每枚语句都必须含占位符」，被实跑否掉——全局默认档的
     * 不带租户形态（{@code WHERE is_default = TRUE LIMIT 1}）本就无参数，DAO 侧传的也是
     * {@code new Object[]{}}。含糊的「必须有 ?」会把这条合法语句判成违例（假红），而把它整条删掉
     * 又会让「语句有参数却没走占位符」这条注入前兆失去探针。改钉 arity 多重集后：枚数、每枚的参数个数
     * 以及「零参数只许出现在登记过的无参形态上」三件事同时可取证——有人把 {@code ?} 换成拼接、
     * 或新加一枚 arity 对不上的语句，都会在这里变红，而不是靠注释约定。</p>
     */
    private static final List<Integer> BASELINE_PLACEHOLDER_ARITIES = List.of(0, 1, 1, 1, 1, 1, 2, 2, 2);

    /** ③″ 唯一在册的「零占位符」语句形态（按列名识别，不按整句文本，免得语句改写即假红）。 */
    private static final String ALLOWED_ZERO_ARITY_MARK = "is_default";

    /** ⑥ 已删除的死腿名。 */
    private static final String DEAD_LEG = "findOrgById";

    /** 一次读取、多条判据共用的剥注释后 DAO 源码。 */
    private static final String DAO_SRC = readDaoSource();

    private static String readDaoSource() {
        try {
            return SourceCommentStripper.strip(
                    Files.readString(locateRepoRoot().resolve(DAO_SOURCE), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读不到被测 DAO 源码：" + DAO_SOURCE + "，" + e.getMessage(), e);
        }
    }

    // ── 1. 格式化家族归零 ───────────────────────────

    @Test
    @DisplayName("DAO 内不得存在任何标识符格式化通道（String.format / %s / formatted / MessageFormat 恒 0）")
    void identifierFormattingIsZeroOnTheClearanceDao() {
        List<String> hits = new ArrayList<>();
        Matcher m = FORMAT_FAMILY.matcher(DAO_SRC);
        while (m.find()) {
            hits.add(m.group());
        }
        assertEquals(List.of(), hits,
                "密级取数面又出现标识符格式化通道：" + hits
                        + "。表名/列名只能来自 ClearanceRowKey 里的完整语句字面量——"
                        + "把变量拼进 SQL 是注入面，与「当前调用点传的是字面量」无关（D-B4-99 e)）");
    }

    @Test
    @DisplayName("SQL 关键字所在行不得出现「+ 变量」拼接（字面量续行 +\"…\" 不误计）")
    void sqlBearingLinesNeverConcatenateIdentifiers() {
        List<String> hits = new ArrayList<>();
        for (String line : DAO_SRC.split("\r?\n")) {
            if (SQL_LINE_WITH_IDENT_ARG.matcher(line).matches()) {
                hits.add(line.trim());
            }
        }
        assertEquals(List.of(), hits,
                "SQL 行内出现标识符拼接：" + hits
                        + "。拼接语句会让本门的「语句多重集 == 基线」判据失去可比对象");
    }

    // ── 2. 语句字面量闭包 ───────────────────────────

    @Test
    @DisplayName("DAO 内 SELECT 语句字面量枚数与表名闭包双向锁定（新增/改动任一语句即红）")
    void sqlLiteralFaceIsClosed() {
        List<String> sqls = extractSqlLiterals(DAO_SRC);
        assertEquals(BASELINE_SQL_LITERALS, sqls.size(),
                "密级取数语句枚数 ≠ 基线 " + BASELINE_SQL_LITERALS + "，实跑=" + sqls.size()
                        + "，语句集=" + sqls + "。新增语句＝取数面扩张，必须同批登记进白名单判据并重设本基线");

        Set<String> tables = new TreeSet<>();
        List<Integer> arities = new ArrayList<>();
        for (String sql : sqls) {
            Matcher tm = Pattern.compile("(?i)\\bFROM\\s+([a-zA-Z_][a-zA-Z0-9_]*)").matcher(sql);
            while (tm.find()) {
                tables.add(tm.group(1).toLowerCase());
            }
            Matcher jm = Pattern.compile("(?i)\\bJOIN\\s+([a-zA-Z_][a-zA-Z0-9_]*)").matcher(sql);
            while (jm.find()) {
                tables.add(jm.group(1).toLowerCase());
            }
            int arity = 0;
            Matcher pm = Pattern.compile("\\?").matcher(sql);
            while (pm.find()) {
                arity++;
            }
            arities.add(arity);
            if (arity == 0) {
                assertTrue(sql.toLowerCase().contains(ALLOWED_ZERO_ARITY_MARK),
                        "出现未登记的零占位符语句：只许无参的全局默认档形态不带 ?，"
                                + "其余语句一旦没有占位符，值就只能是从别处拼进来的：" + sql);
            }
            assertTrue(!sql.toLowerCase().contains("is_primary"),
                    "密级 DAO 不该再读主机构列（机构上下文归 IOrganizationService 属主门面）：" + sql);
        }
        arities.sort(Integer::compareTo);
        assertEquals(BASELINE_PLACEHOLDER_ARITIES, arities,
                "占位符 arity 多重集被改动：实跑=" + arities + "，基线=" + BASELINE_PLACEHOLDER_ARITIES
                        + "（逐枚对应九条语句）。参数个数变了＝调用侧的 Object[] 与语句不再同号，"
                        + "要么是把值拼进了 SQL，要么是加了语句没同步基线");
        assertEquals(BASELINE_TABLES, tables,
                "密级取数表名闭包被改动：实跑=" + tables + "，基线=" + BASELINE_TABLES
                        + "。换表＝跨域取数，须按铁律 #5 走属主门面而不是在本 DAO 加语句");
    }

    @Test
    @DisplayName("抽取判据本身可复跑：文本块与普通字面量各按形态计数，不重不漏")
    void literalExtractorShapeIsPinned() {
        List<String> sqls = extractSqlLiterals(DAO_SRC);
        long fromTextBlocks = sqls.stream().filter(s -> s.startsWith("SELECT COALESCE")).count();
        assertEquals(2, fromTextBlocks,
                "COALESCE 腿（角色档两形态）枚数变化：" + fromTextBlocks
                        + "。它与文本块形态绑定，判据③的 9 枚拆分口径随之失效");
        assertTrue(sqls.stream().anyMatch(s -> s.contains("LIMIT 1")),
                "白名单语句形态漂移：抽不到含 LIMIT 1 的语句，抽取判据可能已失效");
    }

    // ── 3. 白名单条目本体 ───────────────────────────

    @Test
    @DisplayName("ClearanceRowKey 条目闭包＝{USER_PROFILE}，逐枚钉表名/列名/两形态语句原文")
    void rowKeyWhitelistEntriesArePinnedVerbatim() {
        ClearanceLookupDao.ClearanceRowKey[] keys = ClearanceLookupDao.ClearanceRowKey.values();
        assertEquals(1, keys.length,
                "白名单条目数变化（实跑 " + keys.length + " 枚：" + Arrays.toString(keys)
                        + "）。每新增一条目都要同步扩语句多重集基线与表名闭包，禁「顺手加一行」");
        assertEquals(Set.of("USER_PROFILE"),
                Arrays.stream(keys).map(Enum::name).collect(java.util.stream.Collectors.toSet()));

        ClearanceLookupDao.ClearanceRowKey user = ClearanceLookupDao.ClearanceRowKey.USER_PROFILE;
        assertEquals("td_user_security_profile", user.table());
        assertEquals("user_id", user.idColumn());
        assertEquals("SELECT clearance_level FROM td_user_security_profile "
                        + "WHERE user_id = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                normalize(user.tenantScopedSql()),
                "带租户腿语句原文变化：这条语句决定「tenant_id IS NULL 的行对任意租户可见」的既有语义，改动即行为变更");
        assertEquals("SELECT clearance_level FROM td_user_security_profile WHERE user_id = ? LIMIT 1",
                normalize(user.unscopedSql()));
        assertTrue(user.table().equals("td_user_security_profile"), "table() 不再是纯字面量 getter");
    }

    @Test
    @DisplayName("占位符 arity 与传参形态同源：带租户腿 2 枚 ?、无租户腿 1 枚 ?")
    void placeholderArityMatchesTheParamArray() {
        ClearanceLookupDao.ClearanceRowKey user = ClearanceLookupDao.ClearanceRowKey.USER_PROFILE;
        assertEquals(2, countChar(user.tenantScopedSql(), '?'),
                "带租户形态的 ? 数 ≠ 2＝queryClearanceRows 的 new Object[]{idValue, tenantId} 与语句不同源（静默错位绑定）");
        assertEquals(1, countChar(user.unscopedSql(), '?'),
                "无租户形态的 ? 数 ≠ 1＝同上");
    }

    // ── 4. 反射面（签名即门禁）──────────────────────

    @Test
    @DisplayName("反射钉签名：queryClearanceRows 只一枚且首参为枚举；findOrgById 恒不存在")
    void daoSignatureIsPinned() {
        List<Method> rowsMethods = Arrays.stream(ClearanceLookupDao.class.getDeclaredMethods())
                .filter(m -> "queryClearanceRows".equals(m.getName()))
                .toList();
        assertEquals(1, rowsMethods.size(),
                "queryClearanceRows 重载数 ≠ 1：" + rowsMethods
                        + "。留一枚旧的 (String,String,String,String) 形＝把注入面以重载形式复活，"
                        + "调用方仍可传变量表名");
        Method m = rowsMethods.get(0);
        assertEquals(List.of(ClearanceLookupDao.ClearanceRowKey.class, String.class, String.class),
                Arrays.asList(m.getParameterTypes()),
                "queryClearanceRows 形参形态变化：首参必须是枚举（标识符封闭的唯一载体）");

        for (Method declared : ClearanceLookupDao.class.getDeclaredMethods()) {
            assertTrue(!DEAD_LEG.equals(declared.getName()),
                    "机构取数死腿 findOrgById 复活：" + declared
                            + "。机构隶属属 sysman 属主门面，本 DAO 不抄别人的表（D-B4-99 b)）");
        }

        // 表名/列名以「前两参皆 String」的形态传入即绕开白名单——本 DAO 现在没有任何这种签名的公共方法
        List<String> suspect = new ArrayList<>();
        for (Method pm : ClearanceLookupDao.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(pm.getModifiers()) || pm.getParameterCount() < 3) {
                continue;
            }
            Class<?>[] pts = pm.getParameterTypes();
            if (pts[0] == String.class && pts[1] == String.class) {
                suspect.add(pm.getName());
            }
        }
        assertEquals(List.of(), suspect,
                "出现「(String, String, …)」三参数以上公共方法：" + suspect
                        + "＝可能被当作表名/列名传入的第二形态，须改为枚举承载");
    }

    // ── 5. 生产调用点闭包 ───────────────────────────

    @Test
    @DisplayName("queryClearanceRows 的生产调用点恰一枚（密级五档链属主单源），新调用点即红")
    void productionCallSiteIsClosed() throws IOException {
        Path root = locateRepoRoot();
        List<String> callers = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            if (Pattern.compile("queryClearanceRows\\s*\\(").matcher(stripped).find()
                    && !stripped.contains("public List<Map<String, Object>> queryClearanceRows")) {
                callers.add(root.relativize(f).toString().replace('\\', '/'));
            }
        }
        assertEquals(List.of(ONLY_PRODUCTION_CALLER), callers,
                "密级取数生产调用集 ≠ 1 枚：" + callers
                        + "。新调用点＝新的密级判定链（D-B4-23 族的成因就是多条链），"
                        + "须并流到 ISubjectClearanceQueryService 或台账留痕后重设本基线");
    }

    // ── 6. 活性取证 ─────────────────────────────────

    @Test
    @DisplayName("合成回退源码命中①②：证明格式化/拼接判据能变红")
    void judgementsFireOnSyntheticRegression() {
        String regressed = SourceCommentStripper.strip("""
                public class FixtureDao {
                    java.util.List<java.util.Map<String, Object>> q(String tableName, String idColumn,
                                                                    String idValue, String tenantId) {
                        String sql = String.format("SELECT clearance_level FROM %s WHERE %s = ? LIMIT 1",
                                tableName, idColumn);
                        String hint = "SELECT clearance_level FROM " + tableName + " WHERE x = ?";
                        return null;
                    }
                }
                """);
        assertTrue(FORMAT_FAMILY.matcher(regressed).find(),
                "格式化家族判据对 String.format/%s 形回退不命中＝本门①恒绿（假绿）");
        boolean concatHit = false;
        for (String line : regressed.split("\r?\n")) {
            if (SQL_LINE_WITH_IDENT_ARG.matcher(line).matches()) {
                concatHit = true;
                break;
            }
        }
        assertTrue(concatHit, "SQL 行内拼接判据对「FROM \" + tableName + \"」形不命中＝本门②恒绿（假绿）");

        // 反向对照：真实 DAO 源码里「字面量续行」不该被②误计
        assertFalseLiteralContinuation();
    }

    /** 反向对照：DAO 里确有跨行拼接的语句（枚举常量三行传参），判据②不得因它变红。 */
    private static void assertFalseLiteralContinuation() {
        for (String line : DAO_SRC.split("\r?\n")) {
            if (SQL_LINE_WITH_IDENT_ARG.matcher(line).matches()) {
                fail("真实 DAO 源码被②误计：" + line.trim());
            }
        }
    }

    // ── 抽取实现 ────────────────────────────────────

    /** 抽取语句字面量：文本块整体取，普通字面量在文本块挖空后取；只保留含 SELECT 的语句并归一空白。 */
    private static List<String> extractSqlLiterals(String strippedSrc) {
        List<String> out = new ArrayList<>();
        StringBuilder blanked = new StringBuilder();
        Matcher tb = TEXT_BLOCK.matcher(strippedSrc);
        int last = 0;
        while (tb.find()) {
            blanked.append(strippedSrc, last, tb.start());
            blanked.append(' ');
            last = tb.end();
            String body = normalize(tb.group(1));
            if (body.toUpperCase().contains("SELECT")) {
                out.add(body);
            }
        }
        blanked.append(strippedSrc, last, strippedSrc.length());
        Matcher pl = PLAIN_LITERAL.matcher(blanked);
        while (pl.find()) {
            String body = normalize(pl.group(1));
            if (body.toUpperCase().contains("SELECT")) {
                out.add(body);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static int countChar(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        throw new IllegalStateException("无法从 user.dir=" + cur + " 向上定位仓根（缺 ecos_backend/）");
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/")
                                && !s.contains("/archive/");
                    })
                    .sorted()
                    .forEach(out::add);
        }
    }
}
