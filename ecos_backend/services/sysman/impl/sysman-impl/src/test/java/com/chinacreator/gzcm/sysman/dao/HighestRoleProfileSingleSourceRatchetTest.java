package com.chinacreator.gzcm.sysman.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import com.chinacreator.gzcm.sysman.service.SecurityProfileQueryService;
import com.chinacreator.gzcm.sysman.testscan.SourceCommentStripper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 「取用户最高角色档」单源棘轮门（#142/B4-3e-83，收口台账 <b>D-B4-99 d)</b>）。
 *
 * <p><b>缺陷本质</b>：同一事实「该用户角色里的最高密级」在仓内有<b>三处独立实现</b>——
 * ① sysman {@code SecurityProfileQueryDao.findHighestRoleForUser} 行读
 * （{@code ORDER BY rsp.clearance_level DESC LIMIT 1}）；② security-engine
 * {@code SecurityConfigDao.queryHighestRoleProfileForUser} 是 ① 的逐字副本；③ sysman
 * {@code ClearanceLookupDao.highestRoleClearanceLevel} 是聚合腿
 * {@code COALESCE(MAX(rsp.clearance_level), -1)}。三处<b>且已经漂移</b>：
 * {@code clearance_level} 在 DDL 上可空（{@code INT DEFAULT 0}，显式写 NULL 即落空值），
 * PG 的 {@code ORDER BY … DESC} 默认 <b>NULLS FIRST</b> 而 {@code MAX()} <b>忽略 NULL</b> ⇒
 * 行读面会把空密级行读成 {@code 0}（L0 公开），聚合面则当成「无配置」继续下探。
 * 同一主体拿到的密级取决于调用方走了哪条腿，这不是风格问题。</p>
 *
 * <p><b>收口形态</b>：语句只留 ①（属主 sysman 的行读，显式 {@code AND rsp.clearance_level IS NOT NULL}）；
 * ②删除，引擎侧改走属主 api 门面 {@link ISecurityProfileQueryService}（铁律 #5）；
 * ③删除 SQL，委派 ① 并只保留 {@code -1}＝「无配置」的读取值归一。本门把这件事钉成可复跑读数。</p>
 *
 * <p><b>判据（七条）</b>：
 * ① 全仓 main 源码里「最高角色档 JOIN」恰一枚文件、两处（带/不带租户两形态）；
 * ② 聚合腿 {@code MAX(…clearance_level)} 全仓归零；
 * ③ {@code ClearanceLookupDao} 无 SQL、无属主表名，`highestRoleClearanceLevel` 方法体只做委派与 {@code -1} 归一；
 * ④ {@code SecurityProfileQueryDao} 的两条行读臂<b>各自</b>含 {@code clearance_level IS NOT NULL}
 *    （防「只给一条臂加谓词、另一条臂仍取空值」）；
 * ⑤ 引擎侧副本下线：{@code SecurityConfigDao} 无 {@code td_user_role}、无
 *    {@code queryHighestRoleProfileForUser}；{@code SecurityConfigService} 经门面取数、构造器双参；
 * ⑥ 门面面钉值：接口恰一枚方法 {@code (String,String)->SecurityProfile}，实现者恰一枚（sysman 服务）；
 * ⑦ 活性：合成回退夹具（复活聚合腿 / 复活引擎副本 / 去掉空值谓词）逐条命中判据，且委托行为
 *    （命中→值、无档案→{@code -1}、空密级→{@code -1}、异常上抛不吞）钉住。</p>
 *
 * <p><b>已声明盲区</b>：① 判据①②按「语句文本形态」判，改写到别的口径（如换成
 * {@code DISTINCT ON}）不会被本门发现——但那时白名单/多重集基线（
 * {@code ClearanceLookupDaoSqlFaceRatchetTest} 与 ④）仍会因语句改动变红；② 不查已编译构件，
 * 也不做 live SQL 执行（本批无 dev PG，证据等级＝单元＋源码级门禁）；③ 引擎侧其余直读属主表的腿
 * （用户/角色/全局默认/按作用域）本批不扩面，登记为 <b>D-B4-100</b>。</p>
 *
 * <p>验收：{@code mvn -o -f ecos_backend/pom.xml -pl services/sysman/impl/sysman-impl test}</p>
 */
@Timeout(120)
class HighestRoleProfileSingleSourceRatchetTest {

    /** ① 属主行读（唯一在册语句宿主）。 */
    private static final String OWNER_DAO =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/SecurityProfileQueryDao.java";

    /** ③ 密级五档链取数 DAO（聚合腿已删，改委派）。 */
    private static final String CLEARANCE_DAO =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/ClearanceLookupDao.java";

    /** ⑥ 门面实现的唯一在册宿主。 */
    private static final String OWNER_SERVICE =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/service/SecurityProfileQueryService.java";

    /** ⑤ 引擎侧 DAO（副本应已下线）。 */
    private static final String ENGINE_DAO =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/dao/SecurityConfigDao.java";

    /** ⑤ 引擎侧服务（应经门面取数）。 */
    private static final String ENGINE_SERVICE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/SecurityConfigService.java";

    /** ① 最高角色档 JOIN 的目标形态（`ur.ROLE_ID = rsp.role_id` 是这一族的判别子）。 */
    private static final Pattern HIGHEST_ROLE_JOIN = Pattern.compile(
            "(?i)join\\s+td_user_role\\s+ur\\s+on\\s+ur\\.role_id\\s*=\\s*rsp\\.role_id");

    /** ② 聚合腿形态（带不带表别名都算）。 */
    private static final Pattern AGGREGATE_LEG = Pattern.compile(
            "(?i)max\\s*\\(\\s*(?:rsp\\.)?clearance_level\\s*\\)");

    /** ④ 空值档排除谓词。 */
    private static final Pattern NULL_LEVEL_GUARD = Pattern.compile(
            "(?i)rsp\\.clearance_level\\s+is\\s+not\\s+null");

    /** ④ 行读语句臂的判别子（两条臂都按最高档排序取一行）。 */
    private static final Pattern HIGHEST_ROLE_ORDER = Pattern.compile(
            "(?i)order\\s+by\\s+rsp\\.clearance_level\\s+desc");

    /** ⑥ 门面实现者扫描。 */
    private static final Pattern FACADE_IMPLEMENTATION = Pattern.compile(
            "implements\\s+[\\w\\s,.]*\\bISecurityProfileQueryService\\b");

    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(?s:(.*?))\"\"\"");

    private static final String OWNER_DAO_SRC = stripAt(OWNER_DAO);
    private static final String CLEARANCE_DAO_SRC = stripAt(CLEARANCE_DAO);
    private static final String ENGINE_DAO_SRC = stripAt(ENGINE_DAO);
    private static final String ENGINE_SERVICE_SRC = stripAt(ENGINE_SERVICE);

    private static String stripAt(String relPath) {
        try {
            return SourceCommentStripper.strip(
                    Files.readString(locateRepoRoot().resolve(relPath), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读不到源码：" + relPath + "，" + e.getMessage(), e);
        }
    }

    // ── ① 语句单源 ──────────────────────────────────

    @Test
    @DisplayName("全仓「最高角色档」JOIN 恰一枚文件、两处（带/不带租户两形态）")
    void highestRoleJoinSitesAreSingleSourced() throws IOException {
        Map<String, Integer> sites = new TreeMap<>();
        for (Path f : mainJavaFiles()) {
            int hits = countMatches(HIGHEST_ROLE_JOIN, stripFile(f));
            if (hits > 0) {
                sites.put(relativize(f), hits);
            }
        }
        Map<String, Integer> expected = new LinkedHashMap<>();
        expected.put(OWNER_DAO, 2);
        assertEquals(expected, sites,
                "「取用户最高角色档」的语句宿主 ≠ 属主单源 " + OWNER_DAO + "（两臂）：实跑=" + sites
                        + "。同一事实多处语句必然漂移（D-B4-99 d)：PG 的 DESC 默认 NULLS FIRST、"
                        + "MAX() 却忽略 NULL）；引擎侧请走 ISecurityProfileQueryService 门面");
    }

    // ── ② 聚合腿归零 ────────────────────────────────

    @Test
    @DisplayName("全仓不得再有 MAX(…clearance_level) 聚合腿（与行读对空值档读数相反）")
    void aggregateLegIsGoneEverywhere() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path f : mainJavaFiles()) {
            String src = stripFile(f);
            if (AGGREGATE_LEG.matcher(src).find()) {
                hits.add(relativize(f));
            }
        }
        assertEquals(List.of(), hits,
                "角色档聚合腿复活于：" + hits + "。聚合 MAX() 忽略 NULL、行读 DESC 默认 NULLS FIRST，"
                        + "两条腿对空密级行给出相反读数——这正是 D-B4-99 d) 的缺陷本体");
    }

    // ── ③ 委派面 ────────────────────────────────────

    @Test
    @DisplayName("ClearanceLookupDao 无 SQL：highestRoleClearanceLevel 只委派属主行读并保留 -1 归一")
    void clearanceChainDelegatesWithoutSql() {
        assertFalse(CLEARANCE_DAO_SRC.toLowerCase().contains("td_user_role"),
                "密级五档链 DAO 又出现 td_user_role（角色档语句应只存在于 SecurityProfileQueryDao）");
        assertFalse(CLEARANCE_DAO_SRC.toLowerCase().contains("td_role_security_profile"),
                "密级五档链 DAO 又出现 td_role_security_profile（同上）");

        String body = methodBody(CLEARANCE_DAO_SRC, "public Integer highestRoleClearanceLevel(");
        assertTrue(body.contains("findHighestRoleForUser"),
                "highestRoleClearanceLevel 未委派属主行读：方法体=" + body);
        assertFalse(body.toLowerCase().contains("select"),
                "highestRoleClearanceLevel 内又自带 SQL：" + body);
        assertFalse(body.contains("jdbcTemplate"),
                "highestRoleClearanceLevel 又直查库（铁律 §2.5 与单源要求）：" + body);
        assertTrue(body.contains("return -1"),
                "「无档案 ⇒ -1」的读取值归一被删（调用方按该哨兵继续下探）：" + body);
    }

    // ── ④ 空值档排除 ────────────────────────────────

    @Test
    @DisplayName("属主行读两臂各自排除空密级：每条最高档排序臂都带 clearance_level IS NOT NULL")
    void bothOwnerArmsExcludeNullLevels() {
        List<String> arms = new ArrayList<>();
        Matcher tb = TEXT_BLOCK.matcher(OWNER_DAO_SRC);
        while (tb.find()) {
            String body = tb.group(1);
            if (HIGHEST_ROLE_ORDER.matcher(body).find()) {
                arms.add(body);
            }
        }
        assertEquals(2, arms.size(),
                "属主行读的「最高档排序」语句臂数 ≠ 2（带/不带租户）：实得 " + arms.size()
                        + "。若把两臂合并成一条动态拼接，判据④的逐臂取证随之失效，须同批改本门");
        for (String arm : arms) {
            assertTrue(NULL_LEVEL_GUARD.matcher(arm).find(),
                    "最高档排序臂未排除空密级行（PG 的 DESC 默认 NULLS FIRST ⇒ 取到空值行、"
                            + "rs.getInt 读成 0＝L0 公开）：" + normalize(arm));
        }
    }

    // ── ⑤ 引擎侧副本下线 ────────────────────────────

    @Test
    @DisplayName("引擎侧副本下线：SecurityConfigDao 无 td_user_role/无该方法；Service 经门面取数且构造器双参")
    void engineCopyIsGone() {
        assertFalse(ENGINE_DAO_SRC.toLowerCase().contains("td_user_role"),
                "引擎侧 DAO 又出现 td_user_role（副本复活；应走 ISecurityProfileQueryService 门面）");
        assertFalse(ENGINE_DAO_SRC.contains("queryHighestRoleProfileForUser"),
                "引擎侧 DAO 又声明 queryHighestRoleProfileForUser（逐字副本复活）");

        assertFalse(ENGINE_SERVICE_SRC.contains("dao.queryHighestRoleProfileForUser"),
                "引擎侧服务仍从本域 DAO 取最高角色档（应经属主门面）");
        assertTrue(ENGINE_SERVICE_SRC.contains("profileQueryService.findHighestRoleForUser"),
                "引擎侧服务未改走属主门面 ISecurityProfileQueryService");
        assertTrue(Pattern.compile(
                        "SecurityConfigService\\s*\\(\\s*SecurityConfigDao\\s+\\w+\\s*,"
                                + "\\s*ISecurityProfileQueryService\\s+\\w+\\s*\\)")
                        .matcher(ENGINE_SERVICE_SRC).find(),
                "SecurityConfigService 构造器不再是 (SecurityConfigDao, ISecurityProfileQueryService)："
                        + "门面缺席即跨域事实断腿，禁「拿不到就跳档」的折叠");
    }

    // ── ⑥ 门面面钉值 ────────────────────────────────

    @Test
    @DisplayName("门面钉值：接口恰一枚方法 (String,String)->SecurityProfile；实现者恰 sysman 服务一枚")
    void facadeShapeIsPinned() throws IOException {
        Method[] methods = ISecurityProfileQueryService.class.getDeclaredMethods();
        assertEquals(1, methods.length,
                "门面方法数 ≠ 1（实跑 " + Arrays.toString(methods)
                        + "）：门面扩面须同批扩判据，禁顺手加方法");
        Method m = methods[0];
        assertEquals("findHighestRoleForUser", m.getName());
        assertEquals(SecurityProfile.class, m.getReturnType());
        assertEquals(List.of(String.class, String.class), Arrays.asList(m.getParameterTypes()),
                "门面签名漂移：首参主体、次参租户（可为 null）是与既有调用面约定的形态");

        assertTrue(ISecurityProfileQueryService.class.isAssignableFrom(SecurityProfileQueryService.class),
                "SecurityProfileQueryService 不再实现门面（sysman 侧消费方与安全引擎会同时断腿）");

        List<String> impls = new ArrayList<>();
        for (Path f : mainJavaFiles()) {
            if (FACADE_IMPLEMENTATION.matcher(stripFile(f)).find()) {
                impls.add(relativize(f));
            }
        }
        assertEquals(List.of(OWNER_SERVICE), impls,
                "门面实现者 ≠ 1 枚：" + impls
                        + "。第二实现者＝第二个「最高档」答案（D-B4-99 d) 的成因）");
    }

    // ── ⑦ 委托行为与活性 ────────────────────────────

    @Test
    @DisplayName("委派行为钉值：命中给值、无档案 -1、空密级 -1、取数异常上抛不吞")
    void delegationBehaviourIsPinned() {
        SecurityProfileQueryDao dao = mock(SecurityProfileQueryDao.class);
        ClearanceLookupDao lookup = new ClearanceLookupDao(dao);

        SecurityProfile hit = new SecurityProfile();
        hit.setClearanceLevel(3);
        when(dao.findHighestRoleForUser("u-hit", "t1")).thenReturn(hit);
        assertEquals(Integer.valueOf(3), lookup.highestRoleClearanceLevel("u-hit", "t1"),
                "命中最高档时不得改写档值（-1 只表示「无配置」）");

        when(dao.findHighestRoleForUser("u-miss", "t1")).thenReturn(null);
        assertEquals(Integer.valueOf(-1), lookup.highestRoleClearanceLevel("u-miss", "t1"),
                "无档案 ⇒ 必须回 -1 哨兵（调用方据此继续下探；回 0 会被读成 L0 公开）");

        when(dao.findHighestRoleForUser("u-null", "t1")).thenReturn(new SecurityProfile());
        assertEquals(Integer.valueOf(-1), lookup.highestRoleClearanceLevel("u-null", "t1"),
                "档案存在但密级为空 ⇒ 同样是「无配置」(-1)，禁冒充 0");

        when(dao.findHighestRoleForUser("u-boom", "t1"))
                .thenThrow(new IllegalStateException("db down"));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> lookup.highestRoleClearanceLevel("u-boom", "t1"),
                "取数异常被吞＝「断腿」折叠成「无配置」，消费方无从分辨可信性");
        assertEquals("db down", thrown.getMessage());
    }

    @Test
    @DisplayName("活性取证：合成回退夹具逐条命中①②③④，证明判据能变红")
    void judgementsFireOnSyntheticRegression() {
        // 夹具自身含文本块，故用转义字符串拼（Java 文本块不能嵌套自身定界符）
        String revivedAggregate = SourceCommentStripper.strip(
                "class FixtureDao {\n"
                        + "    Integer q(String userId) {\n"
                        + "        return jdbcTemplate.queryForObject(\"\"\"\n"
                        + "                SELECT COALESCE(MAX(rsp.clearance_level), -1) AS max_level\n"
                        + "                FROM td_user_role ur\n"
                        + "                JOIN td_role_security_profile rsp ON ur.ROLE_ID = rsp.role_id\n"
                        + "                WHERE ur.USER_ID = ?\n"
                        + "                \"\"\", Integer.class, userId);\n"
                        + "    }\n"
                        + "}\n");
        assertTrue(AGGREGATE_LEG.matcher(revivedAggregate).find(),
                "聚合腿判据对复活夹具不命中＝本门②恒绿（假绿）");

        String revivedEngineCopy = SourceCommentStripper.strip("""
                class FixtureEngineDao {
                    String sql = "SELECT rsp.* FROM td_role_security_profile rsp "
                            + "JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id "
                            + "WHERE ur.USER_ID = ? ORDER BY rsp.clearance_level DESC LIMIT 1";
                    void queryHighestRoleProfileForUser() {
                    }
                }
                """);
        assertTrue(revivedEngineCopy.toLowerCase().contains("td_user_role")
                        && revivedEngineCopy.contains("queryHighestRoleProfileForUser"),
                "引擎副本判据对复活夹具不命中＝本门⑤恒绿（假绿）");
        assertTrue(HIGHEST_ROLE_JOIN.matcher(revivedEngineCopy).find(),
                "单源判据（①）对「复活引擎副本」这一夹具不命中＝本门①恒绿（假绿）");

        String armWithoutGuard = SourceCommentStripper.strip(
                "class FixtureOwnerDao {\n"
                        + "    String sql = \"\"\"\n"
                        + "            SELECT rsp.* FROM td_role_security_profile rsp\n"
                        + "            JOIN td_user_role ur ON ur.ROLE_ID = rsp.role_id\n"
                        + "            WHERE ur.USER_ID = ?\n"
                        + "            ORDER BY rsp.clearance_level DESC\n"
                        + "            LIMIT 1\n"
                        + "            \"\"\";\n"
                        + "}\n");
        assertTrue(HIGHEST_ROLE_ORDER.matcher(textBlocks(armWithoutGuard).get(0)).find(),
                "行读臂抽取判据对合成夹具不命中（抽取失效，④将空转）");
        assertFalse(NULL_LEVEL_GUARD.matcher(textBlocks(armWithoutGuard).get(0)).find(),
                "空值谓词判据对「缺谓词」的夹具命中＝判据方向写反（会放行缺陷）");
    }

    // ── 扫描与抽取实现 ──────────────────────────────

    private static List<String> textBlocks(String strippedSrc) {
        List<String> out = new ArrayList<>();
        Matcher m = TEXT_BLOCK.matcher(strippedSrc);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** 取方法体（含嵌套花括号配平；返回不含最外层花括号的正文）。 */
    private static String methodBody(String strippedSrc, String signature) {
        int start = strippedSrc.indexOf(signature);
        assertTrue(start >= 0, "抽不到方法：" + signature + "（判据失效，禁空转通过）");
        int open = strippedSrc.indexOf('{', start);
        assertTrue(open >= 0, "方法无方法体：" + signature);
        int depth = 0;
        for (int i = open; i < strippedSrc.length(); i++) {
            char c = strippedSrc.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return strippedSrc.substring(open + 1, i);
                }
            }
        }
        throw new IllegalStateException("方法体未闭合：" + signature);
    }

    private static int countMatches(Pattern p, String src) {
        int n = 0;
        Matcher m = p.matcher(src);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String stripFile(Path p) {
        try {
            return SourceCommentStripper.strip(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读不到源码：" + p + "，" + e.getMessage(), e);
        }
    }

    private static String relativize(Path p) {
        return locateRepoRoot().relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** 全仓 main 源（排除 target 与 archive）——判据①②⑤⑥的扫描面。 */
    private static List<Path> mainJavaFiles() throws IOException {
        Path backend = locateRepoRoot().resolve("ecos_backend");
        List<Path> out = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(backend)) {
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
        assertTrue(out.size() > 500,
                "扫描面塌陷：main 源仅 " + out.size() + " 枚，判据将空转通过");
        return out;
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
}
