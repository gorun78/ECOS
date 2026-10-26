package com.chinacreator.gzcm.engine.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.security.dao.SecurityConfigDao;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.engine.security.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 安全档案<b>读面单源</b>棘轮门（#142/B4-3e-84，收口台账 <b>D-B4-100</b>）。
 *
 * <p><b>缺陷本质</b>：{@code td_user_security_profile}／{@code td_role_security_profile}
 * 属主是 sysman，但安全引擎 {@code SecurityConfigDao} 另存了七组同形查询语句
 * （用户档、角色档、全局默认档、全量用户档、全量角色档、按作用域取档／列档）。
 * 同一事实两处语句必然漂移，且属主侧改动不会传导到引擎侧。</p>
 *
 * <p><b>本批处置</b>：七组读面改走属主 api 门面 {@link ISecurityProfileQueryService}，
 * 引擎 DAO 不再持有任何 {@code SELECT *}／{@code RowMapper}。写面九腿由
 * <b>SecurityConfigWriteFaceSingleSourceRatchetTest</b>（#142/B4-3e-87，收口 D-B4-101）
 * 单独钉单源——同门同时双向钉（读缺席+写缺席）会把「两存」误判成「删干净＝假绿」，
 * 故写面判据归专属写面门托管。</p>
 */
@Timeout(120)
class SecurityConfigReadFaceSingleSourceRatchetTest {

    private static final String ENGINE_DAO =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/dao/SecurityConfigDao.java";
    private static final String ENGINE_SERVICE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/SecurityConfigService.java";
    private static final String OWNER_DAO =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/SecurityProfileQueryDao.java";

    /** 引擎侧必须消失的七个读方法名（DAOS 层）。 */
    private static final List<String> RETIRED_READS = List.of(
            "queryUserProfile", "queryRoleProfile", "queryGlobalDefaultProfile",
            "queryAllUserProfiles", "queryAllRoleProfiles",
            "queryProfileByScope", "queryProfilesByScope");

    /** 门面承接的七组读面：引擎服务方法名 → 门面方法名。 */
    private static final Map<String, String> DELEGATIONS = Map.of(
            "queryUserProfile", "findUser",
            "queryRoleProfile", "findRole",
            "queryGlobalDefaultProfile", "findGlobalDefault",
            "queryAllUserProfiles", "listAllUser",
            "queryAllRoleProfiles", "listAllRole",
            "queryProfileByScope", "findUserByScope",
            "queryProfilesByScope", "listProfilesByScope");

    private static final Pattern SERVICE_CONSTRUCTOR = Pattern.compile(
            "SecurityConfigService\\s*\\(\\s*SecurityConfigDao\\s+\\w+\\s*,\\s*ISecurityProfileQueryService\\s+\\w+\\s*\\)");
    private static final Pattern DAO_QUERY_CALL = Pattern.compile("\\bdao\\.query\\w*\\s*\\(");
    private static final Pattern TENANT_SCOPED_LIST_SQL = Pattern.compile(
            "WHERE\\s+tenant_id\\s*=\\s*\\?\\s+ORDER\\s+BY\\s+updated_at\\s+DESC", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCOPE_TYPE_LIST_SQL = Pattern.compile(
            "WHERE\\s+scope_type\\s*=\\s*\\?\\s+ORDER\\s+BY\\s+updated_at\\s+DESC", Pattern.CASE_INSENSITIVE);

    // ── ① 引擎 DAO 不再持有读面 ─────────────────────

    @Test
    @DisplayName("引擎 DAO 无读面：七个查询方法、SELECT *、RowMapper 一律缺席")
    void engineDaoCarriesNoReadFace() throws IOException {
        String src = stripAt(ENGINE_DAO);
        for (String retired : RETIRED_READS) {
            assertFalse(src.contains(retired + "("),
                    "引擎 DAO 又出现读方法 " + retired + "（读写两存复发，D-B4-100）");
        }
        assertFalse(src.contains("SELECT *"),
                "引擎 DAO 又出现 SELECT *（读面回流）");
        assertFalse(src.contains("RowMapper"),
                "引擎 DAO 又出现 RowMapper（行映射应随读面留在 sysman 属主侧）");
        assertFalse(src.contains("jdbcTemplate.query("),
                "引擎 DAO 又出现 jdbcTemplate.query(（列表/映射查询＝读面回流）");
        // #142/B4-3e-87（D-B4-101 收口后）：本 DAO 只留 ping() 一枚自检腿，
        // 写面九腿与拼标识符腿应已迁往属主单源 —— 见
        // SecurityConfigWriteFaceSingleSourceRatchetTest
        assertTrue(src.contains("SELECT 1"),
                "引擎 DAO 的自检探针（SELECT 1 连接存活）缺席：Carrier 基石塌陷");
        assertFalse(src.contains("td_user_security_profile"),
                "引擎 DAO 又出现属主用户表名（写面回流，D-B4-101）");
        assertFalse(src.contains("td_role_security_profile"),
                "引擎 DAO 又出现属主角色表名（写面回流，D-B4-101）");
        assertFalse(src.toLowerCase().contains("insert into"),
                "引擎 DAO 又出现 INSERT（写面回流，D-B4-101）");
        assertFalse(src.toLowerCase().contains("on conflict"),
                "引擎 DAO 又出现 ON CONFLICT UPSERT（写面回流，D-B4-101）");
        assertFalse(src.contains("String.format"),
                "引擎 DAO 又出现 String.format（拼标识符形态回流，D-B4-101）");
    }

    // ── ② 引擎服务只经门面 ──────────────────────────

    @Test
    @DisplayName("引擎服务七组读面逐条委派门面、无 dao.query 旁路，构造器带门面（必装配）")
    void engineServiceDelegatesEveryRead() throws IOException {
        String src = stripAt(ENGINE_SERVICE);
        for (Map.Entry<String, String> e : DELEGATIONS.entrySet()) {
            assertTrue(src.contains("profileQueryService." + e.getValue() + "("),
                    "引擎服务未委派门面方法 " + e.getValue() + "（" + e.getKey() + " 腿仍是直读）");
        }
        assertFalse(DAO_QUERY_CALL.matcher(src).find(),
                "引擎服务仍有 dao.query* 旁路：读面必须全部经属主门面");
        assertTrue(SERVICE_CONSTRUCTOR.matcher(src).find(),
                "SecurityConfigService 构造器形状漂移（须 (SecurityConfigDao, ISecurityProfileQueryService)，"
                        + "门面缺席即 context 起不来，禁折叠成可选依赖）");
        assertTrue(countOccurrences(src, "catch (Exception e)") >= 8,
                "fail-soft 包装腿少于 8 条（7 读面 + 最高角色档）：异常处置被削弱");
    }

    // ── ③ 门面形状 ────────────────────────────────

    @Test
    @DisplayName("门面形状：七组读面齐备且签名约定（单档 (String[,String])→SecurityProfile、列表→List）")
    void facadeExposesEveryReadFace() {
        Map<String, Method> byName = new LinkedHashMap<>();
        for (Method m : ISecurityProfileQueryService.class.getDeclaredMethods()) {
            byName.put(m.getName(), m);
        }
        for (Map.Entry<String, String> e : DELEGATIONS.entrySet()) {
            Method m = byName.get(e.getValue());
            assertTrue(m != null, "门面缺方法 " + e.getValue() + "（引擎侧委派会编译失败）");
            boolean listResult = List.class.isAssignableFrom(m.getReturnType());
            Class<?>[] params = m.getParameterTypes();
            if (listResult) {
                assertEquals(SecurityProfile.class,
                        (Class<?>) ((java.lang.reflect.ParameterizedType) m.getGenericReturnType())
                                .getActualTypeArguments()[0],
                        e.getValue() + " 的列表元素类型不是 SecurityProfile");
            } else {
                assertEquals(SecurityProfile.class, m.getReturnType(),
                        e.getValue() + " 的单档返回类型漂移");
            }
            assertTrue(params.length == 1 || params.length == 2,
                    e.getValue() + " 参数个数异常: " + Arrays.toString(params));
            for (Class<?> p : params) {
                assertEquals(String.class, p, e.getValue() + " 参数类型应为 String: " + Arrays.toString(params));
            }
        }
    }

    // ── ④ 属主语句单源（全仓扫描 + 活性夹具）──────────

    @Test
    @DisplayName("全仓 main 源：租户档列表语句 2 处、作用域类型列表语句 2 处，均只在 sysman 属主 DAO")
    void scopedListSqlIsOwnedBySysman() throws IOException {
        Map<String, String> corpus = mainJavaSource();
        assertTrue(corpus.size() > 500,
                "扫描面塌陷：main java 文件仅 " + corpus.size() + " 枚（基线 >500）");

        Map<String, Integer> tenantSites = applyTo(corpus, TENANT_SCOPED_LIST_SQL);
        assertEquals(Map.of(OWNER_DAO, 2), tenantSites,
                "「WHERE tenant_id = ? ORDER BY updated_at DESC」不再只由属主 DAO 持有：" + tenantSites);

        Map<String, Integer> scopeSites = applyTo(corpus, SCOPE_TYPE_LIST_SQL);
        assertEquals(Map.of(OWNER_DAO, 2), scopeSites,
                "「WHERE scope_type = ? ORDER BY updated_at DESC」不再只由属主 DAO 持有：" + scopeSites);
    }

    @Test
    @DisplayName("活性取证：合成夹具逐条命中本门判据，证明判据能变红")
    void judgementsFireOnSyntheticRegression() {
        String revivedRead = SourceCommentStripper.strip("""
                class FixtureDao {
                    java.util.List<Object> queryUserProfile(String userId, String tenantId) {
                        return jdbcTemplate.query("SELECT * FROM td_user_security_profile WHERE user_id = ? LIMIT 1",
                                mapper, userId);
                    }
                }
                """);
        assertTrue(revivedRead.contains("queryUserProfile("),
                "读方法判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(revivedRead.contains("SELECT *"),
                "SELECT * 判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(revivedRead.contains("jdbcTemplate.query("),
                "jdbcTemplate.query 判据对复活夹具不命中＝本门①恒绿（假绿）");

        String revokedService = SourceCommentStripper.strip("""
                class FixtureService {
                    Object queryUserProfile(String userId) {
                        return dao.queryUserProfile(userId, null);
                    }
                }
                """);
        assertTrue(DAO_QUERY_CALL.matcher(revokedService).find(),
                "dao.query 旁路判据对复活夹具不命中＝本门②恒绿（假绿）");

        Map<String, String> synthetic = Map.of(
                "ecos_backend/engine/security-engine/impl/src/main/java/RevivedDao.java",
                "SELECT * FROM td_user_security_profile WHERE scope_type = ? ORDER BY updated_at DESC");
        assertEquals(Map.of("ecos_backend/engine/security-engine/impl/src/main/java/RevivedDao.java", 1),
                applyTo(synthetic, SCOPE_TYPE_LIST_SQL),
                "单源归属判据对合成夹具不命中＝本门④恒绿（假绿）");
    }

    // ── ⑤ 行为：fail-soft 语义原样保留 ─────────────────

    @Test
    @DisplayName("行为钉值：命中透传、门面异常仍折 null／空表（读面搬家不改对外语义），未知作用域不触门面")
    void failSoftSemanticsArePreserved() throws IOException {
        SecurityConfigDao dao = mock(SecurityConfigDao.class);
        ISecurityProfileQueryService facade = mock(ISecurityProfileQueryService.class);
        SecurityConfigService service = new SecurityConfigService(dao, facade);

        SecurityProfile p = new SecurityProfile();
        p.setClearanceLevel(3);
        when(facade.findUser(eq("u-1"), nullable(String.class))).thenReturn(p);
        assertEquals(3, service.queryUserProfile("u-1").getClearanceLevel());

        when(facade.findRole(eq("r-1"), nullable(String.class))).thenReturn(p);
        assertEquals(3, service.queryRoleProfile("r-1").getClearanceLevel());

        when(facade.findGlobalDefault(nullable(String.class))).thenReturn(p);
        assertEquals(3, service.queryGlobalDefaultProfile().getClearanceLevel());

        when(facade.listAllUser(nullable(String.class))).thenReturn(List.of(p));
        assertEquals(1, service.queryAllUserProfiles().size());

        when(facade.listAllRole(nullable(String.class))).thenReturn(List.of(p));
        assertEquals(1, service.queryAllRoleProfiles().size());

        when(facade.findUserByScope("TENANT", "t-1")).thenReturn(p);
        assertEquals(3, service.queryProfileByScope("TENANT", "t-1").getClearanceLevel());

        when(facade.listProfilesByScope("ORG", "o-1")).thenReturn(List.of(p));
        assertEquals(1, service.queryProfilesByScope("ORG", "o-1").size());

        when(facade.findHighestRoleForUser(eq("u-1"), nullable(String.class))).thenReturn(p);
        assertEquals(3, service.queryHighestRoleProfileForUser("u-1").getClearanceLevel());

        // 门面异常：准入面 fail-soft 语义不变（null／空表），不向控制器抛
        when(facade.findUser(eq("boom"), nullable(String.class)))
                .thenThrow(new IllegalStateException("db down"));
        assertNull(service.queryUserProfile("boom"), "取数异常被折叠成非 null＝fail-soft 语义被改");
        when(facade.listAllRole(nullable(String.class)))
                .thenThrow(new IllegalStateException("db down"));
        assertTrue(service.queryAllRoleProfiles().isEmpty(), "取数异常未折成空表＝fail-soft 语义被改");
        when(facade.listProfilesByScope(anyString(), anyString()))
                .thenThrow(new IllegalStateException("db down"));
        assertTrue(service.queryProfilesByScope("TENANT", "t-1").isEmpty(), "列表异常未折成空表");
    }

    @Test
    @DisplayName("未知作用域短路：不触门面、不回落到任何档位")
    void unknownScopeDoesNotTouchFacade() {
        SecurityConfigDao dao = mock(SecurityConfigDao.class);
        ISecurityProfileQueryService facade = mock(ISecurityProfileQueryService.class);
        SecurityConfigService service = new SecurityConfigService(dao, facade);

        assertNull(service.queryProfileByScope("GLOBAL", "x-1"),
                "未知作用域不再返回 null（禁兜底成任何档位）");
        verifyNoInteractions(facade);
    }

    // ── 工具 ─────────────────────────────────────

    private static Map<String, Integer> applyTo(Map<String, String> corpus, Pattern p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : corpus.entrySet()) {
            Matcher m = p.matcher(e.getValue());
            int n = 0;
            while (m.find()) {
                n++;
            }
            if (n > 0) {
                out.put(e.getKey(), n);
            }
        }
        return out;
    }

    private static Map<String, String> mainJavaSource() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path root = locateRepoRoot();
        try (Stream<Path> s = Files.walk(root.resolve("ecos_backend"))) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String p = f.toString().replace('\\', '/');
                if (!p.contains("/src/main/java/") || p.contains("/target/") || p.contains("/archive/")) {
                    continue;
                }
                out.put(relativize(root, f), stripFile(f));
            }
        }
        return out;
    }

    private static String stripAt(String relative) throws IOException {
        return stripFile(locateRepoRoot().resolve(relative));
    }

    private static String stripFile(Path f) throws IOException {
        return SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static Path locateRepoRoot() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        List<Path> seen = new ArrayList<>();
        while (dir != null) {
            seen.add(dir);
            if (Files.isDirectory(dir.resolve("ecos_backend")) && Files.isDirectory(dir.resolve("docs"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IOException("找不到仓库根（含 ecos_backend/ 与 docs/）：" + seen);
    }
}
