package com.chinacreator.gzcm.engine.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.security.dao.SecurityConfigDao;
import com.chinacreator.gzcm.engine.security.service.SecurityConfigService;
import com.chinacreator.gzcm.engine.security.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 安全档案<b>写面单源</b>棘轮门（#142/B4-3e-87，收口台账 <b>D-B4-101</b>）。
 *
 * <p><b>缺陷本质</b>：{@code td_user_security_profile}／{@code td_role_security_profile}
 * 属主是 sysman，但安全引擎 {@code SecurityConfigDao} 还存了一份九腿写面
 * （upsertUserProfile／upsertRoleProfile／upsertProfile／clearAllDefaults／
 * activateProfile／deleteUserProfile／deleteRoleProfile／updateProfileFields／
 * cloneProfile），其中 {@code upsertProfile} 以 {@code String.format} 拼表名列名
 * （注入面），且两处 {@code ON CONFLICT} 子句 {@code COALESCE(?, is_default)}
 * 与属主侧「{@code ON CONFLICT 不碰 is_default}」的既有语义相反——同一事实两处语句
 * 必漂移、且任何属主侧语义修正都不会传导到引擎侧。</p>
 *
 * <p><b>本批处置</b>：九腿写语句单源收到 sysman {@code SecurityProfileQueryDao}，
 * 引擎侧 {@code SecurityConfigDao} 整腿删除（只留 {@code ping()} 一枚 {@code SELECT 1}
 * 自检，它是存活探针、不读属主任何事实列）；引擎 {@code SecurityConfigService}
 * 只经属主 api 门面 {@link ISecurityProfileQueryService} 的写方法落写；
 * {@code upsertProfile(targetTable, idColumn, …)} 对外签名不变（API 只增不改），
 * 但内部先把 (table, column) 二值映射到具名目标档 {@code USER}/{@code ROLE}——
 * 非白名单值在本腿抛 {@link IllegalArgumentException}（原分态：调用方 catch→internalError）。</p>
 *
 * <p><b>判据（五放）</b>：
 * ① 引擎 DAO 零写腿残留＋形态零残留（属主两表名／INSERT／ON CONFLICT／COALESCE(?,is_default)／
 *    String.format／jdbcTemplate.update），载体形态钉 {@code SELECT 1} 与 {@code SELECT *}／
 *    {@code RowMapper} 全缺席——扫不到 {@code SELECT 1} 即扫面塌陷（假绿防线）；
 * ② 引擎服务九腿逐条只经门面（禁 {@code dao.<write>(} 旁路、禁任何 jdbcTemplate 或其它
 *    JDBC 旁路通道），构造器 (SecurityConfigDao, ISecurityProfileQueryService) 必装配（ping 载体
 *    的读侧存活探针仍要装配）；
 * ③ 属主单源：全仓 main 源「INSERT/UPDATE/DELETE 属主表」六写语句多重集 ＝ 属主 DAO 一枚；
 *    {@code String.format"} 的 UPSERT 拼标识符面全仓 0 命中；引擎域（engine/security-engine/）
 *    不得引用这两张属主表；
 * ④ 行为钉值：{@code upsertProfile} 的 (table, column) → {@code USER}/{@code ROLE} 映射钉、
 *    非法对抛 {@link IllegalArgumentException} 且不触门面；其余写腿参数原样透传门面、
 *    门面异常原样上抛（写面三态：引擎面 fail-loud，禁 catch 折 0 行）；
 * ⑤ 活性取证：合成复活夹具（revived engine DAO / service）逐条命中①②判据。</p>
 */
@Timeout(120)
class SecurityConfigWriteFaceSingleSourceRatchetTest {

    private static final String ENGINE_DAO =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/dao/SecurityConfigDao.java";
    private static final String ENGINE_SERVICE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/SecurityConfigService.java";
    private static final String OWNER_DAO =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/SecurityProfileQueryDao.java";

    /** 引擎面九腿写方法名（本门要证明它们在引擎 DAO 缺席）。 */
    private static final List<String> RETIRED_WROTE = List.of(
            "upsertUserProfile", "upsertRoleProfile", "upsertProfile", "clearAllDefaults",
            "activateProfile", "deleteUserProfile", "deleteRoleProfile", "updateProfileFields",
            "cloneProfile");

    /** 九腿写面：引擎服务方法名 → 门面方法名（本门②逐条钉）。 */
    private static final Map<String, String> DELEGATIONS = new LinkedHashMap<>();

    static {
        DELEGATIONS.put("upsertUserProfile", "upsertUserProfile");
        DELEGATIONS.put("upsertRoleProfile", "upsertRoleProfile");
        DELEGATIONS.put("upsertProfile", "upsertCoalescing");
        DELEGATIONS.put("clearAllDefaults", "clearAllDefaults");
        DELEGATIONS.put("activateProfile", "activateDefault");
        DELEGATIONS.put("deleteUserProfile", "deleteUserProfile");
        DELEGATIONS.put("deleteRoleProfile", "deleteRoleProfile");
        DELEGATIONS.put("updateProfileFields", "updateProfileFields");
        DELEGATIONS.put("cloneProfile", "cloneUserProfile");
    }

    /** ② 旁路判定：引擎服务内核直接触 JDBC 通道。 */
    private static final Pattern ENGINE_JDBC_BY_PASS = Pattern.compile(
            "jdbcTemplate\\.|namedParameterJdbcTemplate\\.|SimpleJdbcTemplate\\(");

    /** ② 引擎服务写方法名只用 profileQueryService 承接的负向形态。 */
    private static final Pattern SERVICE_NON_FACADE_WRITE_CALL = Pattern.compile(
            "\\bdao\\s*\\.\\s*(upsertUserProfile|upsertRoleProfile|upsertProfile|clearAllDefaults"
                    + "|activateProfile|deleteUserProfile|deleteRoleProfile|updateProfileFields"
                    + "|cloneProfile)\\s*\\(");

    /** ③ 属主表写语句形态（大小写不敏感；词间空白含换行 ⇒ 文本块语句折白后可命中）。 */
    private static final Pattern OWNER_WRITE_SQL = Pattern.compile(
            "(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+td_[a-z0-9_]*security_profile");

    /** ③ String.format 拼属主表标识符注入形态（UI 注入面）。 */
    private static final Pattern FORMAT_INJECTION_ON_OWNER_TABLE = Pattern.compile(
            "String\\.format\\s*\\(\\s*\"[^\"]*(INSERT|UPDATE|DELETE)[^\"]*%s");

    /** ① 拼出 {@code COALESCE(?, is_default)} 的 {@code ON CONFLICT} 子句（属主侧不触 is_default，与引擎旧语义相反）。 */
    private static final Pattern ON_CONFLICT_ISDEFAULT = Pattern.compile(
            "(?i)COALESCE\\(\\s*\\?,\\s*is_default\\s*\\)");

    private static final Pattern CONSTRUCTOR_SHAPE = Pattern.compile(
            "SecurityConfigService\\s*\\(\\s*SecurityConfigDao\\s+\\w+\\s*,\\s*ISecurityProfileQueryService\\s+\\w+\\s*\\)");

    // ── ① 引擎 DAO：写腿九枚全删、载体只留 ping() ────────

    @Test
    @DisplayName("引擎 DAO 写面九枚写方法全删；扫面以 SELECT 1 探针锚定（防「删干净＝假绿」）")
    void engineDaoIsPingOnlyCarrier() throws IOException {
        String src = stripAt(ENGINE_DAO);
        for (String retired : RETIRED_WROTE) {
            assertFalse(Pattern.compile("\\b" + retired + "\\s*\\(").matcher(src).find(),
                    "引擎 DAO 又出现写方法 " + retired + "（写面回流，D-B4-101）");
        }
        assertFalse(src.contains("td_user_security_profile"),
                "引擎 DAO 又出现属主用户表名（写面回流，D-B4-101）");
        assertFalse(src.contains("td_role_security_profile"),
                "引擎 DAO 又出现属主角色表名（写面回流，D-B4-101）");
        assertFalse(OWNER_WRITE_SQL.matcher(src).find(),
                "引擎 DAO 又出现属主表写语句（INSERT/UPDATE/DELETE *_security_profile）");
        assertFalse(ON_CONFLICT_ISDEFAULT.matcher(src).find(),
                "引擎 DAO 又出现 COALESCE(?, is_default)（旧 UPSERT 语义复活）");
        assertFalse(src.contains("String.format"),
                "引擎 DAO 又出现 String.format（拼标识符注入面复活，D-B4-101）");
        assertFalse(src.contains("SELECT *"),
                "引擎 DAO 又出现 SELECT *（读面也回流；本批只动写面但载体形态必须闭包于 ping-only）");
        assertFalse(src.contains("RowMapper"),
                "引擎 DAO 又出现 RowMapper（行映射回填）");
        // 载体形态锚：ping() 存活探针必须还在，否则 scan 面被截断＝假绿
        assertTrue(src.contains("SELECT 1"),
                "引擎 DAO 的 ping() 存活探针（SELECT 1）缺席：Carrier 基石塌陷（本类还须装配读侧探针）");
    }

    // ── ② 引擎服务：只经门面，禁 JDBC 直触 ─────────────

    @Test
    @DisplayName("引擎服务九腿写面逐条委派门面、无 dao.<write> 与任何 JDBC 旁路，构造器形状保持")
    void engineServiceWritesOnlyViaFacade() throws IOException {
        String src = stripAt(ENGINE_SERVICE);
        for (Map.Entry<String, String> e : DELEGATIONS.entrySet()) {
            assertTrue(Pattern.compile(Pattern.quote(e.getKey()) + "\\s*\\(").matcher(src).find(),
                    "引擎服务 " + e.getKey() + " 方法缺席（对外签名是 API 契约，禁顺带删）");
            assertTrue(src.contains("profileQueryService." + e.getValue() + "("),
                    "引擎服务 " + e.getKey() + " 未委派门面方法 " + e.getValue() + "（写面旁路）");
        }
        assertFalse(SERVICE_NON_FACADE_WRITE_CALL.matcher(src).find(),
                "引擎服务仍有 dao.<write> 旁路：写面必须全部经属主门面");
        assertFalse(ENGINE_JDBC_BY_PASS.matcher(src).find(),
                "引擎服务出现 jdbcTemplate/namedParameterJdbcTemplate 直触：写面绕开了属主门面");
        assertTrue(CONSTRUCTOR_SHAPE.matcher(src).find(),
                "SecurityConfigService 构造器形状漂移（须 (SecurityConfigDao, ISecurityProfileQueryService)）");
    }

    // ── ③ 属主单源：全仓写语句 = 属主 DAO 一枚 ──────────

    @Test
    @DisplayName("全仓 main 源：INSERT/UPDATE/DELETE 属主表的语句仅属主 DAO 持有；String.format 拼标识符全仓零命中；引擎域不得引用属主表名")
    void ownerSideOwnsEveryWriteStatement() throws IOException {
        Map<String, String> corpus = mainJavaSourceUnder("ecos_backend");
        assertTrue(corpus.size() > 400,
                "扫描面塌陷：main java 文件仅 " + corpus.size() + " 枚（基线 >400）");

        Map<String, Integer> writeSites = scan(corpus, OWNER_WRITE_SQL);
        assertTrue(!writeSites.isEmpty(),
                "属主 DAO 的写语句读不到了：要么属主 DAO 被误删，要么判据已失效");
        // 属主 DAO 里就是所有写语句的唯一落点
        long nonOwnerSites = writeSites.entrySet().stream()
                .filter(e -> !e.getKey().equals(OWNER_DAO))
                .mapToLong(Map.Entry::getValue).sum();
        assertEquals(0, nonOwnerSites,
                "属主表写语句不再只由属主 DAO 持有：" + writeSites
                        + "（引擎或第三宿主直写属主表 = D-B4-101 家族复发，铁律 #5 违反）");

        Map<String, Integer> formatSplice = scan(corpus, FORMAT_INJECTION_ON_OWNER_TABLE);
        assertEquals(Map.of(), formatSplice,
                "全仓又出现 String.format 拼 INSERT/UPDATE/DELETE 标识符（注入面复活，D-B4-101）：" + formatSplice);
    }

    // ── ④ 行为：白名单映射＋分态＋异常上抛 ──────────────

    @Test
    @DisplayName("行为钉值：upsertProfile 白名单 (table,column)→USER/ROLE；非法对抛 IllegalArgumentException 且不触门面")
    void upsertProfileWhitelistMapsToNamedTarget() {
        SecurityConfigDao dao = mock(SecurityConfigDao.class);
        ISecurityProfileQueryService facade = mock(ISecurityProfileQueryService.class);
        SecurityConfigService service = new SecurityConfigService(dao, facade);

        service.upsertProfile("td_user_security_profile", "user_id", "u-1",
                3, "ws-1", "full", true, false, "USER", "t-1", "o-1");
        verify(facade).upsertCoalescing(eq("USER"), eq("u-1"), eq(3), eq("ws-1"),
                eq("full"), eq(true), eq(false), eq("USER"), eq("t-1"), eq("o-1"));

        service.upsertProfile("td_role_security_profile", "role_id", "r-1",
                2, "ws-2", "basic", null, true, "ROLE", "t-2", null);
        verify(facade).upsertCoalescing(eq("ROLE"), eq("r-1"), eq(2), eq("ws-2"),
                eq("basic"), eq(null), eq(true), eq("ROLE"), eq("t-2"), eq(null));

        // 非白名单对：服务层就地拒绝，不触属主门面（分态＝调用方 catch→internalError，与旧 String.format 拼非法语句后的 JDBC 报错同分）
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertProfile("td_not_a_profile_table", "whatever", "x",
                        1, null, null, null, false, null, null, null),
                "非白名单 (table, column) 未抛 IllegalArgumentException＝白名单判据被旁路");
        assertThrows(IllegalArgumentException.class,
                () -> service.upsertProfile("td_user_security_profile", "role_id", "x",
                        1, null, null, null, false, null, null, null),
                "跨表错配 (user 表配 role_id 列) 未抛＝白名单 (table,column) 二值校验被绕过");
        // 两枚合法调用已在上文 verify；门面只见这两次 upsertCoalescing，无其它交互
        verify(facade, org.mockito.Mockito.times(2)).upsertCoalescing(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoMoreInteractions(facade);
    }

    @Test
    @DisplayName("行为钉值：其它八腿写面参数原样透传门面；门面异常原样上抛（写面 fail-loud，禁折 0 行）")
    void otherWriteLegsPassThroughAndThrowOnFacadeFailure() {
        SecurityConfigDao dao = mock(SecurityConfigDao.class);
        ISecurityProfileQueryService facade = mock(ISecurityProfileQueryService.class);
        SecurityConfigService service = new SecurityConfigService(dao, facade);

        service.upsertUserProfile("u-1", 3, "ws-1", "full", true, false, "USER", "t-1", "o-1");
        verify(facade).upsertUserProfile("u-1", 3, "ws-1", "full", true, false, "USER", "t-1", "o-1");

        service.upsertRoleProfile("r-1", 2, "ws-2", "basic", null, "ROLE", "t-1", null);
        verify(facade).upsertRoleProfile("r-1", 2, "ws-2", "basic", null, "ROLE", "t-1", null);

        service.clearAllDefaults();
        verify(facade).clearAllDefaults();

        service.activateProfile("u-9");
        verify(facade).activateDefault("u-9");

        when(facade.deleteUserProfile("u-2")).thenReturn(1);
        int u = service.deleteUserProfile("u-2");
        assertEquals(1, u, "deleteUserProfile 行数被折叠");
        verify(facade).deleteUserProfile("u-2");

        when(facade.deleteRoleProfile("r-2")).thenReturn(0);
        int r = service.deleteRoleProfile("r-2");
        assertEquals(0, r, "deleteRoleProfile 须原样透传 0 行（0 行＝不存在，是调用方判断 notFound 的信号）");
        verify(facade).deleteRoleProfile("r-2");

        service.updateProfileFields("u-3", 4, "ws-3", "audit", true, false);
        verify(facade).updateProfileFields("u-3", 4, "ws-3", "audit", true, false);

        // cloneProfile：源档经门面读不到 ⇒ 就地抛 IllegalArgumentException（禁兜底成假新建）
        when(facade.findUser(eq("missing"), any())).thenReturn(null);
        when(facade.findRole(eq("missing"), any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.cloneProfile("missing", "new-1"),
                "源档案不存在时 cloneProfile 未抛：调成隐式「新建」是行为变更");
        verify(facade, org.mockito.Mockito.never()).cloneUserProfile(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // 写面异常上抛（三态：断腿 ≠ 0 行）——引擎侧 write face 是 fail-loud（错误时调用方 catch→internalError）
        when(facade.clearAllDefaults()).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, service::clearAllDefaults,
                "写面 DAO 异常被服务层 catch 吞掉＝断腿折叠，禁用 0 行代替");
    }

    // ── ⑤ 活性：合成复活夹具命中判据 ───────────────────

    @Test
    @DisplayName("活性取证：合成复活夹具（引擎 DAO 九腿 / String.format / 属主表写语句）逐条命中判据，证明能变红")
    void judgementsFireOnSyntheticRegression() {
        String revivedEngineDao = SourceCommentStripper.strip("""
                class RevivedDao {
                    int upsertUserProfile(String userId, Integer clearanceLevel,
                            String linkedWorkstation, String auditMode, Boolean sandboxMandatory,
                            boolean isDefault, String scopeType, String tenantId, String orgId) {
                        String sql = "INSERT INTO td_user_security_profile (user_id, clearance_level) VALUES (?, ?) "
                                + "ON CONFLICT (user_id) DO UPDATE SET "
                                + "is_default = COALESCE(?, is_default), updated_at = NOW()";
                        return jdbcTemplate.update(sql, userId, clearanceLevel);
                    }
                    int upsertProfile(String targetTable, String idColumn, String idValue) {
                        String sql = String.format("INSERT INTO %s (%s) VALUES (?)", targetTable, idColumn);
                        return jdbcTemplate.update(sql, idValue);
                    }
                    int deleteUserProfile(String userId) {
                        return jdbcTemplate.update("DELETE FROM td_user_security_profile WHERE user_id = ?", userId);
                    }
                }
                """);

        // ① 形零残留判据：五类形态逐一命中
        assertTrue(Pattern.compile("\\bupsertUserProfile\\s*\\(").matcher(revivedEngineDao).find(),
                "写方法名判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(revivedEngineDao.contains("td_user_security_profile"),
                "属主表名判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(OWNER_WRITE_SQL.matcher(revivedEngineDao).find(),
                "属主表写语句判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(ON_CONFLICT_ISDEFAULT.matcher(revivedEngineDao).find(),
                "COALESCE(?, is_default) 判据对旧 UPSERT 语义夹具不命中＝本门①恒绿（假绿）");
        assertTrue(revivedEngineDao.contains("String.format"),
                "String.format 判据对拼接夹具不命中＝本门①恒绿（假绿）");
        assertTrue(ENGINE_JDBC_BY_PASS.matcher(revivedEngineDao).find(),
                "jdbcTemplate 旁路判据对复活夹具不命中＝本门①恒绿（假绿）");

        // ② 引擎服务旁路判据：dao.<write>( 形
        String revivedService = SourceCommentStripper.strip("""
                class RevivedService {
                    void upsertUserProfile(String userId, Integer clearanceLevel,
                            String linkedWorkstation, String auditMode, Boolean sandboxMandatory,
                            boolean isDefault, String scopeType, String tenantId, String orgId) {
                        dao.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                                auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
                    }
                    void clearAllDefaults() {
                        dao.clearAllDefaults();
                    }
                }
                """);
        assertTrue(SERVICE_NON_FACADE_WRITE_CALL.matcher(revivedService).find(),
                "dao.<write> 旁路判据对复活夹具不命中＝本门②恒绿（假绿）");

        // ③ String.format 拼标识符注入面判据（合成「main 源语料」→ scan 计数）
        Map<String, String> syntheticCorpus = Map.of(
                "ecos_backend/engine/security-engine/impl/src/main/java/Revived.java",
                "int q(String t) { return jdbcTemplate.update(String.format(\"INSERT INTO %s VALUES (?)\", t)); }");
        assertEquals(Map.of("ecos_backend/engine/security-engine/impl/src/main/java/Revived.java", 1),
                scan(syntheticCorpus, FORMAT_INJECTION_ON_OWNER_TABLE),
                "String.format 拼标识符判据对合成夹具不命中＝本门③恒绿（假绿）");
    }

    // ── 工具 ─────────────────────────────────────────

    private static Map<String, Integer> scan(Map<String, String> corpus, Pattern p) {
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

    private static Map<String, String> mainJavaSourceUnder(String rootRel) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path root = locateRepoRoot();
        try (Stream<Path> s = Files.walk(root.resolve(rootRel))) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String p = f.toString().replace('\\', '/');
                if (!p.contains("/src/main/java/") || p.contains("/target/")
                        || p.contains("/archive/") || p.contains("/test/")) {
                    continue;
                }
                out.put(relativize(root, f),
                        // 词间白空格折叠：属主 DAO 的写语句是文本块（多行），判据按词元匹配须折白
                        SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8))
                                .replaceAll("\\s+", " "));
            }
        }
        return out;
    }

    private static String stripAt(String relative) throws IOException {
        // 词间白空格折叠：与③全仓语料同口径（属主 DAO 文本块语句须折白才可命中词元判据）
        return SourceCommentStripper.strip(
                        Files.readString(locateRepoRoot().resolve(relative), StandardCharsets.UTF_8))
                .replaceAll("\\s+", " ");
    }

    private static String relativize(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static Path locateRepoRoot() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        java.util.List<Path> seen = new java.util.ArrayList<>();
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
