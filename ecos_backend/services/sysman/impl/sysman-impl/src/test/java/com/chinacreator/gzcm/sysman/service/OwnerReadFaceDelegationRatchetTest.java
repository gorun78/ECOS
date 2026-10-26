package com.chinacreator.gzcm.sysman.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.sysman.dao.SecurityProfileQueryDao;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import com.chinacreator.gzcm.sysman.testscan.SourceCommentStripper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 属主读面+写面<b>委派单源</b>棘轮门（#142/B4-3e-84 收口 <b>D-B4-100</b>；
 * #142/B4-3e-87 扩面收口 <b>D-B4-101</b> 九腿写面纯委派判据）。
 *
 * <p><b>为什么钉属主这一侧</b>：引擎侧门禁（读面
 * {@code SecurityConfigReadFaceSingleSourceRatchetTest} 与写面
 * {@code SecurityConfigWriteFaceSingleSourceRatchetTest}）只能证明「引擎不再直读／直写」，
 * 拿门面当黑盒——门面实现如果把取数折叠成 {@code null}／空表、把写腿折叠成 0 行、
 * 或把参数改写后下发，引擎侧仍全绿，两宿主看到的「同一事实」反而分叉。
 * 故本门钉门面唯一实现者：九组读面与九腿写面必须逐条
 * {@code return dao.<同名>(原样参数)}，禁 try/catch 折叠、禁兜底值。</p>
 *
 * <p><b>三态纪律</b>：本类（服务）是纯委派层，取数／落写失败一律<b>上抛</b>——
 * 「断腿」与「无配置」「0 行」必须可被调用方分辨。实现类里 catch 后折
 * {@code null}／空表／0 行，调用方无从分辨，正是本门要拦的形态。</p>
 */
@Timeout(120)
class OwnerReadFaceDelegationRatchetTest {

    private static final String IMPL =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/service/SecurityProfileQueryService.java";

    /** 门面九组读面 + 九腿写面：方法名 → 委派语句（参数原样透传，禁改写）。 */
    private static final Map<String, String> DELEGATIONS = new LinkedHashMap<>();

    static {
        DELEGATIONS.put("findUser", "return dao.findUser(userId, tenantId);");
        DELEGATIONS.put("findRole", "return dao.findRole(roleId, tenantId);");
        DELEGATIONS.put("findHighestRoleForUser", "return dao.findHighestRoleForUser(userId, tenantId);");
        DELEGATIONS.put("findGlobalDefault", "return dao.findGlobalDefault(tenantId);");
        DELEGATIONS.put("listAllUser", "return dao.listAllUser(tenantId);");
        DELEGATIONS.put("listAllRole", "return dao.listAllRole(tenantId);");
        DELEGATIONS.put("findUserByScope", "return dao.findUserByScope(scopeType, scopeId);");
        DELEGATIONS.put("listProfilesByScope", "return dao.listProfilesByScope(scopeType, scopeId);");
        // #142/B4-3e-86（D-B4-103）：角色码判读承接「用户是否挂某角色码」的属主单源腿
        DELEGATIONS.put("hasRoleCode", "return dao.hasRoleCode(userId, roleCode);");
        // #142/B4-3e-87（D-B4-101）：九腿写面（语句单源在属主 DAO，本类只纯委派）
        DELEGATIONS.put("upsertUserProfile",
                "return dao.upsertUserProfile(userId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);");
        DELEGATIONS.put("upsertRoleProfile",
                "return dao.upsertRoleProfile(roleId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, scopeType, tenantId, orgId);");
        DELEGATIONS.put("upsertCoalescing",
                "return dao.upsertCoalescing(profileTarget, idValue, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);");
        DELEGATIONS.put("clearAllDefaults", "return dao.clearAllDefaults();");
        DELEGATIONS.put("activateDefault", "return dao.activateDefault(userId);");
        DELEGATIONS.put("deleteUserProfile", "return dao.deleteUserProfile(userId);");
        DELEGATIONS.put("deleteRoleProfile", "return dao.deleteRoleProfile(roleId);");
        DELEGATIONS.put("updateProfileFields",
                "return dao.updateProfileFields(userId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, isDefault);");
        DELEGATIONS.put("cloneUserProfile",
                "return dao.cloneUserProfile(newUserId, clearanceLevel, linkedWorkstation, auditMode, sandboxMandatory, scopeType, tenantId, orgId);");
    }

    /** 九腿写面方法名（D-B4-101 判据命名用）。 */
    private static final List<String> WRITE_LEGS = List.of(
            "upsertUserProfile", "upsertRoleProfile", "upsertCoalescing",
            "clearAllDefaults", "activateDefault", "deleteUserProfile", "deleteRoleProfile",
            "updateProfileFields", "cloneUserProfile");

    // ── ① 源码面：逐条原样委派、无折叠 ─────────────────

    @Test
    @DisplayName("十八方法逐条 return dao.<同名>(原样参数)：禁 try/catch、禁兜底 null/空表/0 行")
    void everyReadDelegatesVerbatimWithoutFold() throws IOException {
        String src = stripAt(IMPL);
        for (Map.Entry<String, String> e : DELEGATIONS.entrySet()) {
            String body = methodBody(src, "public " + resultTypeOf(e.getKey()) + " " + e.getKey() + "(");
            String compact = body.replaceAll("\\s+", " ").trim();
            assertTrue(compact.contains(e.getValue()),
                    e.getKey() + " 未原样委派属主 DAO（期望含 " + e.getValue() + "；实得 " + compact + "）");
            assertFalse(body.contains("try"),
                    e.getKey() + " 出现 try：取数/落写异常必须上抛，由调用方分态（断腿 ≠ 无配置／0 行）");
            assertFalse(body.contains("catch"),
                    e.getKey() + " 出现 catch：折叠会吞掉「断腿」证据");
            assertFalse(body.contains("return null"),
                    e.getKey() + " 出现兜底 null：未解析与失败被折叠成同一读数");
            assertFalse(body.toLowerCase().contains("emptylist"),
                    e.getKey() + " 出现空表兜底：同上");
        }
        // 写腿不得折 0 行（0 行＝「没写进去」的信号，被折叠就与断腿不可分辨）
        for (String w : WRITE_LEGS) {
            String body = methodBody(src, "public int " + w + "(");
            assertFalse(body.contains("return 0;"),
                    w + " 出现折 0 行兜底：写腿异常/未命中被折叠成 0 行，调用方无从分辨断腿");
        }
        // 扫描面防塌陷：十八 @Override 与唯一的既有 update 腿都还在
        assertEquals(18, countOccurrences(src, "@Override"),
                "实现类的 @Override 数 ≠ 18（九读面 + 九写腿）：门面面被削减或扫描面被截断");
        assertTrue(src.contains("public int update(String sql, Object... args)"),
                "既有 update 腿被顺手删除（本批不扩面改行为）");
        assertTrue(src.contains("private final SecurityProfileQueryDao dao;"),
                "DAO 依赖形态漂移（构造注入的单依赖是本类的全部取数面）");
    }

    // ── ② 形状面：实现者恰十八枚公开方法（九读 + 九写 + update）─

    @Test
    @DisplayName("实现类公开方法集恰为十八门面方法 + update；接口方法全部落地")
    void publicMethodSetIsPinned() {
        Set<String> actual = new TreeSet<>();
        for (Method m : SecurityProfileQueryService.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                actual.add(m.getName());
            }
        }
        Set<String> expected = new TreeSet<>(DELEGATIONS.keySet());
        expected.add("update");
        assertEquals(expected, actual,
                "实现类公开方法集漂移（实跑 " + actual + "）：新增/删改面须同批扩本判据");

        assertTrue(ISecurityProfileQueryService.class.isAssignableFrom(SecurityProfileQueryService.class),
                "实现类不再实现属主门面（引擎侧与密级五档链会同时断腿）");
        Set<String> facadeNames = new TreeSet<>();
        for (Method m : ISecurityProfileQueryService.class.getDeclaredMethods()) {
            facadeNames.add(m.getName());
        }
        assertEquals(new TreeSet<>(DELEGATIONS.keySet()), facadeNames,
                "门面方法集与本门的委派表不一致（实跑 " + facadeNames + "）：扩面须同批扩判据");
    }

    // ── ③ 行为面：返回值与参数逐条透传，异常上抛 ──────────

    @Test
    @DisplayName("行为钉值：读面返回值同一实例透传、参数原样下发；DAO 异常原样上抛")
    void behaviourPassesThroughValuesAndArguments() {
        SecurityProfileQueryDao dao = mock(SecurityProfileQueryDao.class);
        SecurityProfileQueryService service = new SecurityProfileQueryService(dao);
        SecurityProfile one = new SecurityProfile();
        List<SecurityProfile> many = List.of(one);

        when(dao.findUser("u-1", "t-1")).thenReturn(one);
        assertSame(one, service.findUser("u-1", "t-1"), "findUser 返回值被替换（应同一实例透传）");
        verify(dao).findUser("u-1", "t-1");

        when(dao.findRole("r-1", "t-1")).thenReturn(one);
        assertSame(one, service.findRole("r-1", "t-1"), "findRole 返回值被替换");
        verify(dao).findRole("r-1", "t-1");

        when(dao.findHighestRoleForUser("u-1", "t-1")).thenReturn(one);
        assertSame(one, service.findHighestRoleForUser("u-1", "t-1"), "findHighestRoleForUser 返回值被替换");
        verify(dao).findHighestRoleForUser("u-1", "t-1");

        when(dao.findGlobalDefault("t-1")).thenReturn(one);
        assertSame(one, service.findGlobalDefault("t-1"), "findGlobalDefault 返回值被替换");
        verify(dao).findGlobalDefault("t-1");

        when(dao.listAllUser("t-1")).thenReturn(many);
        assertSame(many, service.listAllUser("t-1"), "listAllUser 返回值被替换（空表兜底会掩盖断腿）");
        verify(dao).listAllUser("t-1");

        when(dao.listAllRole("t-1")).thenReturn(many);
        assertSame(many, service.listAllRole("t-1"), "listAllRole 返回值被替换");
        verify(dao).listAllRole("t-1");

        when(dao.findUserByScope("TENANT", "t-1")).thenReturn(one);
        assertSame(one, service.findUserByScope("TENANT", "t-1"), "findUserByScope 返回值被替换");
        verify(dao).findUserByScope("TENANT", "t-1");

        when(dao.listProfilesByScope("ORG", "o-1")).thenReturn(many);
        assertSame(many, service.listProfilesByScope("ORG", "o-1"), "listProfilesByScope 返回值被替换");
        verify(dao).listProfilesByScope("ORG", "o-1");

        when(dao.findRole("boom", null)).thenThrow(new IllegalStateException("db down"));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.findRole("boom", null),
                "DAO 异常被实现类吞掉＝「断腿」折叠成「无配置」，调用方无从分辨");
        assertEquals("db down", thrown.getMessage());
    }

    // ── ④ 写面行为：参数原样下发、行数透传、异常上抛（D-B4-101）──

    @Test
    @DisplayName("写面九腿：参数原样下发属主 DAO、影响行数透传、DAO 异常原样上抛")
    void writeLegsPassThroughArgumentsAndRows() {
        SecurityProfileQueryDao dao = mock(SecurityProfileQueryDao.class);
        SecurityProfileQueryService service = new SecurityProfileQueryService(dao);

        when(dao.upsertUserProfile("u-1", 3, "ws-1", "full", true, false, "USER", "t-1", null))
                .thenReturn(1);
        assertEquals(1, service.upsertUserProfile("u-1", 3, "ws-1", "full", true, false, "USER", "t-1", null),
                "upsertUserProfile 影响行数被改写（0 行折叠会掩盖「没写进去」）");
        verify(dao).upsertUserProfile("u-1", 3, "ws-1", "full", true, false, "USER", "t-1", null);

        when(dao.upsertRoleProfile("r-1", 2, null, "basic", null, "ROLE", "t-1", null))
                .thenReturn(1);
        assertEquals(1, service.upsertRoleProfile("r-1", 2, null, "basic", null, "ROLE", "t-1", null),
                "upsertRoleProfile 影响行数被改写");
        verify(dao).upsertRoleProfile("r-1", 2, null, "basic", null, "ROLE", "t-1", null);

        when(dao.upsertCoalescing("USER", "_global_default_", 1, null, null, null, true, null, "t-1", null))
                .thenReturn(1);
        assertEquals(1, service.upsertCoalescing("USER", "_global_default_", 1, null, null, null, true, null, "t-1", null),
                "upsertCoalescing 影响行数被改写");
        verify(dao).upsertCoalescing("USER", "_global_default_", 1, null, null, null, true, null, "t-1", null);

        when(dao.clearAllDefaults()).thenReturn(2);
        assertEquals(2, service.clearAllDefaults(), "clearAllDefaults 影响行数被改写");
        verify(dao).clearAllDefaults();

        when(dao.activateDefault("u-9")).thenReturn(1);
        assertEquals(1, service.activateDefault("u-9"), "activateDefault 影响行数被改写");
        verify(dao).activateDefault("u-9");

        when(dao.deleteUserProfile("u-2")).thenReturn(1);
        assertEquals(1, service.deleteUserProfile("u-2"), "deleteUserProfile 影响行数被改写");
        verify(dao).deleteUserProfile("u-2");

        when(dao.deleteRoleProfile("r-2")).thenReturn(0);
        assertEquals(0, service.deleteRoleProfile("r-2"), "deleteRoleProfile 须把 0 行原样透传（0 行＝不存在的信号）");
        verify(dao).deleteRoleProfile("r-2");

        when(dao.updateProfileFields("u-3", 4, "ws-3", "audit", true, false)).thenReturn(1);
        assertEquals(1, service.updateProfileFields("u-3", 4, "ws-3", "audit", true, false),
                "updateProfileFields 影响行数被改写");
        verify(dao).updateProfileFields("u-3", 4, "ws-3", "audit", true, false);

        when(dao.cloneUserProfile("new-1", 3, "ws-1", "full", true, "USER", "t-1", null)).thenReturn(1);
        assertEquals(1, service.cloneUserProfile("new-1", 3, "ws-1", "full", true, "USER", "t-1", null),
                "cloneUserProfile 影响行数被改写");
        verify(dao).cloneUserProfile("new-1", 3, "ws-1", "full", true, "USER", "t-1", null);

        when(dao.activateDefault("boom")).thenThrow(new IllegalStateException("db down"));
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> service.activateDefault("boom"),
                "写腿 DAO 异常被实现类吞掉／折 0＝「断腿」折叠成「0 行」，调用方无从分辨");
        assertEquals("db down", thrown.getMessage());
    }

    // ── ⑤ 活性：合成折叠夹具命中本门判据 ─────────────────

    @Test
    @DisplayName("活性取证：折叠夹具（catch→null／空表／0 行）逐条命中判据，证明判据能变红")
    void judgementsFireOnSyntheticFold() {
        String folded = SourceCommentStripper.strip("""
                class FixtureService {
                    Object findUser(String userId, String tenantId) {
                        try {
                            return dao.findUser(userId, tenantId);
                        } catch (Exception e) {
                            return null;
                        }
                    }
                    java.util.List<Object> listAllUser(String tenantId) {
                        try {
                            return dao.listAllUser(tenantId);
                        } catch (Exception e) {
                            return java.util.Collections.emptyList();
                        }
                    }
                    int upsertUserProfile(String userId, Integer clearanceLevel, String linkedWorkstation,
                                          String auditMode, Boolean sandboxMandatory, boolean isDefault,
                                          String scopeType, String tenantId, String orgId) {
                        try {
                            return dao.upsertUserProfile(userId, clearanceLevel, linkedWorkstation,
                                    auditMode, sandboxMandatory, isDefault, scopeType, tenantId, orgId);
                        } catch (Exception e) {
                            return 0;
                        }
                    }
                }
                """);
        String single = methodBody(folded, "Object findUser(").replaceAll("\\s+", " ").trim();
        assertTrue(single.contains("catch"), "折叠判据对 catch 夹具不命中＝本门①恒绿（假绿）");
        assertTrue(single.contains("return null"), "兜底 null 判据对夹具不命中＝本门①恒绿（假绿）");

        String list = methodBody(folded, "java.util.List<Object> listAllUser(").toLowerCase();
        assertTrue(list.contains("emptylist"), "空表兜底判据对夹具不命中＝本门①恒绿（假绿）");

        String write = methodBody(folded, "int upsertUserProfile(").replaceAll("\\s+", " ").trim();
        assertTrue(write.contains("return 0;"), "写腿折 0 行判据对夹具不命中＝本门①恒绿（假绿）");
    }

    // ── 工具 ────────────────────────────────────────

    private static String resultTypeOf(String method) {
        if ("hasRoleCode".equals(method)) {
            return "boolean";
        }
        if (WRITE_LEGS.contains(method)) {
            return "int";
        }
        return DELEGATIONS.get(method).startsWith("return dao.list") ? "List<SecurityProfile>" : "SecurityProfile";
    }

    /** 取方法体（花括号配平；返回不含最外层花括号的正文）。 */
    private static String methodBody(String strippedSrc, String signature) {
        int start = strippedSrc.indexOf(signature);
        assertTrue(start >= 0, "抽不到方法：" + signature + "（抽取失效，禁空转通过）");
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

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static String stripAt(String relative) throws IOException {
        return SourceCommentStripper.strip(
                Files.readString(locateRepoRoot().resolve(relative), StandardCharsets.UTF_8));
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
