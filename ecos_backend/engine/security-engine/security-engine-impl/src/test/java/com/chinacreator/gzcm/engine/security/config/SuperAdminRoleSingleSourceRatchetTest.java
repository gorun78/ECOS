package com.chinacreator.gzcm.engine.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.security.service.SecuritySandboxService;
import com.chinacreator.gzcm.engine.security.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 超管角色判定<b>属主单源</b>棘轮门（#142/B4-3e-86，收口台账 <b>D-B4-103</b>）。
 *
 * <p><b>收口前缺陷</b>：{@code SecuritySandboxDao.countSuperAdminRole} 在
 * {@code td_user_security_profile}/{@code td_role_security_profile} 两表间用
 * {@code usp.profile_id = rsp.profile_id} JOIN 并按 {@code rsp.role_name} 过滤——
 * V24 DDL 实测两表主键分别是 {@code user_id}/{@code role_id}，<b>均无 profile_id 与
 * role_name 列</b> ⇒ 该 SQL 恒 PG error，被 service catch 折 0 ⇒
 * ROLE_SUPER_ADMIN 用户<b>永远不被识别</b>（SEC-P0-5 解密放行腿的全 fail-open）。
 * 系 D-B4-102（3e-85）台账登记时同步发现的姊妹腿。</p>
 *
 * <p><b>本批处置</b>：
 * <ol>
 *   <li>属主 api 门面 {@link ISecurityProfileQueryService} 扩一枚角色码判读
 *       {@code hasRoleCode(userId, roleCode)}，单源语句落 sysman 属主 DAO
 *       （真列 {@code td_user_role → td_role} 的 role_code/role_name）；</li>
 *   <li>安全引擎 {@code SecuritySandboxService.isSuperAdminRole} 改经门面，
 *       幻影列 DAO <b>整个删除</b>（而非留壳）；</li>
 *   <li>语义裁决：这是<b>授权增益位</b>（true 即解密放行），异常/未装配 ⇒ 判否
 *       （fail-closed），不改变 SEC-P0-5「取不到 ≠ 是超管」的红线，但取数通路
 *       换成真列（SQL 不再恒错）。</li>
 * </ol></p>
 */
@Timeout(120)
class SuperAdminRoleSingleSourceRatchetTest {

    private static final String ENGINE_SERVICE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/service/SecuritySandboxService.java";
    private static final String ENGINE_DAO_GONE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/dao/SecuritySandboxDao.java";
    private static final String OWNER_SERVICE =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/service/SecurityProfileQueryService.java";
    private static final String OWNER_DAO =
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/dao/SecurityProfileQueryDao.java";

    // ── ① 幻影形态全仓禁回流 ─────────────────────────────

    @Test
    @DisplayName("全仓 main 源：幻影列形态（profile_id JOIN / countSuperAdminRole）零残留；SecuritySandboxDao 下线")
    void phantomColumnShapeIsGoneRepoWide() throws IOException {
        // 引擎域读：引擎 main 代码面（含剥注释）不得再出现幻影列名与已删 DAO 方法名
        Map<String, String> engine = mainJavaSourceStripped("ecos_backend/engine/security-engine/security-engine-impl/src/main/java");
        assertTrue(engine.size() > 80, "引擎扫描面塌陷：main java 仅 " + engine.size() + " 枚（本批实测基线 101，取 80 留缩容量）");
        int enginePhantom = 0;
        for (Map.Entry<String, String> e : engine.entrySet()) {
            String src = e.getValue();
            if (src.contains("profile_id")) {
                enginePhantom++;
                System.out.println("ENGINE_PHANTOM_PROFILEID: " + e.getKey());
            }
            if (src.contains("countSuperAdminRole")) {
                enginePhantom++;
                System.out.println("ENGINE_PHANTOM_COUNT: " + e.getKey());
            }
        }
        assertEquals(0, enginePhantom,
                "引擎 main 出现幻影列 profile_id 或 countSuperAdminRole 方法名（D-B4-103 复活形态）");
        assertFalse(Files.exists(locateRepoRoot().resolve(ENGINE_DAO_GONE)),
                "SecuritySandboxDao.java 复活：整文件是幻影列 JOIN 的唯一载体，应删除而非留壳");

        // 全仓 main 读（引擎＋属主 impl 两域剥注释后）：幻影 JOIN 谓词形态不得再现
        Map<String, String> corpus = new LinkedHashMap<>();
        for (String root : List.of(
                "ecos_backend/engine/security-engine/security-engine-impl/src/main/java",
                "ecos_backend/services/sysman/impl/sysman-impl/src/main/java")) {
            mainJavaSourceStripped(root).forEach(corpus::putIfAbsent);
        }
        assertTrue(corpus.size() > 200, "全仓扫描面塌陷：合计仅 " + corpus.size() + " 枚（本批实测基线 221，取 200 留缩容量）");
        int phantom = 0;
        for (Map.Entry<String, String> e : corpus.entrySet()) {
            String src = e.getValue();
            if (src.contains("profile_id =") || src.contains("= profile_id")
                    || src.contains("profile_id = ?") || src.contains("usp.profile_id")) {
                phantom++;
                System.out.println("PHANTOM: " + e.getKey() + " 含 profile_id 连接形态");
            }
        }
        assertEquals(0, phantom, "存在 profile_id 连接形态的 main 源代码（D-B4-103 幻影腿回流）");
    }

    // ── ② 引擎服务只经门面 ─────────────────────────────

    @Test
    @DisplayName("引擎服务：isSuperAdminRole 只经 hasRoleCode 门面；未装配/异常判否（fail-closed）；代码面不引 DAO")
    void engineServiceGoesThroughFacadeOnly() throws IOException {
        String src = stripAt(ENGINE_SERVICE);
        String body = methodBody(src, "private boolean isSuperAdminRole(String userId)");
        assertTrue(body.contains("profileQueryService.hasRoleCode("),
                "裁决腿未走属主门面 hasRoleCode（取数通路应唯一）");
        assertTrue(body.contains("profileQueryService == null"),
                "未装配判据缺席：缺装配时必须有显式 fail-closed 腿（不是 NPE）");
        assertTrue(body.contains("ROLE_SUPER_ADMIN"),
                "角色码字面量漂移：本面消费的是平台超管码 ROLE_SUPER_ADMIN");
        assertFalse(src.contains("SecuritySandboxDao"),
                "服务代码面仍引用 SecuritySandboxDao（DAO 已下线）");
        // 构造器不得再带 DAO 参（构造面漂移＝装配形态回流）
        assertFalse(src.contains("public SecuritySandboxService(ApplicationEventPublisher eventPublisher, "),
                "双参构造器复活（DAO 参回流）：装配形态已是「单参构造 + setter 注入门面」");
        assertTrue(src.contains("public void setProfileQueryService(ISecurityProfileQueryService"),
                "setter 装配入口缺席：monolith 宿主经它把属主门面注进来");
    }

    // ── ③ 属主侧单源语句与纯委派 ─────────────────────────

    @Test
    @DisplayName("属主侧：单源语句走 td_user_role→td_role 真列；实现类原样委派、不折叠")
    void ownerSideCarriesSingleSourceStatement() throws IOException {
        String dao = stripAt(OWNER_DAO);
        String daoBody = methodBody(dao, "public boolean hasRoleCode(String userId, String roleCode)");
        assertTrue(daoBody.contains("FROM td_user_role ur"),
                "属主语句不按真实隶属表 td_user_role 走（副本形态回流）");
        assertTrue(daoBody.contains("td_role r ON ur.role_id = r.role_id"),
                "属主语句不按 td_role role_id 真列 JOIN（JOIN 键面漂移）");
        assertTrue(daoBody.contains("r.role_code = ?") && daoBody.contains("r.role_name = ?"),
                "角色码/角色名带内命中谓词漂移（语义裁决见门面 javadoc）");
        assertFalse(daoBody.contains("profile_id"),
                "属主语句又出现幻影列 profile_id（单源里不应有幻影形态）");

        String service = stripAt(OWNER_SERVICE);
        String svcBody = methodBody(service, "public boolean hasRoleCode(String userId, String roleCode)");
        String compact = svcBody.replaceAll("\\s+", " ").trim();
        assertTrue(compact.contains("return dao.hasRoleCode(userId, roleCode);"),
                "实现类未原样委派属主 DAO（期望 return dao.hasRoleCode(userId, roleCode);，实得 " + compact + "）");
        assertFalse(svcBody.contains("try"), "实现类出现 try：取数异常必须上抛（断腿 ≠ 未命中）");
        assertFalse(svcBody.contains("catch"), "实现类出现 catch：折叠会吞掉「断腿」证据");
        assertFalse(svcBody.contains("return true"), "实现类出现兜底 true：授权增益位禁默认放行");
        assertFalse(svcBody.contains("return false"), "实现类出现兜底 false：同上（未命中由 DAO 单源给）");
    }

    // ── ④ 行为钉值：布尔腿翻转裁决分支 ────────────────────

    @Test
    @DisplayName("行为钉值：门面命中⇒blocked 组织也放行（超管优先腿）；未命中⇒组织隔离拒绝；异常/未装配⇒判否不 NPE")
    void behaviourPinsFailClosedSemantics() {
        // —— 命中：即使组织是 blocked 也走「超管授权」优先腿 ——
        ISecurityProfileQueryService hit = mock(ISecurityProfileQueryService.class);
        when(hit.hasRoleCode("u-sa", "ROLE_SUPER_ADMIN")).thenReturn(true);
        SecuritySandboxService svc = new SecuritySandboxService(mock(ApplicationEventPublisher.class));
        svc.setProfileQueryService(hit);
        Map<String, Object> granted = svc.decrypt("u-sa", "org_blocked_x", "ds-1");
        assertEquals(Boolean.TRUE, granted.get("success"),
                "门面命中 ROLE_SUPER_ADMIN 未走超管优先放行腿（fail-closed 方向被改）");
        verify(hit).hasRoleCode("u-sa", "ROLE_SUPER_ADMIN");

        // —— 未命中：blocked 组织走组织级拒绝腿 ——
        ISecurityProfileQueryService miss = mock(ISecurityProfileQueryService.class);
        when(miss.hasRoleCode("u-x", "ROLE_SUPER_ADMIN")).thenReturn(false);
        SecuritySandboxService svcMiss = new SecuritySandboxService(mock(ApplicationEventPublisher.class));
        svcMiss.setProfileQueryService(miss);
        Map<String, Object> denied = svcMiss.decrypt("u-x", "org_blocked_x", "ds-1");
        assertEquals(Boolean.FALSE, denied.get("success"),
                "未命中超管却走了放行腿（授权增益位被削弱）");
        verify(miss).hasRoleCode("u-x", "ROLE_SUPER_ADMIN");

        // —— 门面异常：判否＋不外抛 ——
        ISecurityProfileQueryService boom = mock(ISecurityProfileQueryService.class);
        when(boom.hasRoleCode("u-b", "ROLE_SUPER_ADMIN")).thenThrow(new IllegalStateException("db down"));
        SecuritySandboxService svcBoom = new SecuritySandboxService(mock(ApplicationEventPublisher.class));
        svcBoom.setProfileQueryService(boom);
        Map<String, Object> closed = svcBoom.decrypt("u-b", "org_blocked_x", "ds-1");
        assertEquals(Boolean.FALSE, closed.get("success"), "取数异常折成放行＝fail-open 复活");

        // —— 未装配：判否＋不 NPE ——
        SecuritySandboxService bare = new SecuritySandboxService(mock(ApplicationEventPublisher.class));
        Map<String, Object> bareClosed = bare.decrypt("u-c", "org_blocked_x", "ds-1");
        assertEquals(Boolean.FALSE, bareClosed.get("success"), "门面未装配却放行（缺装配＝不可判定，禁冒充确认）");
        verifyNoFacadeCallOnBare(bare);
    }

    private static void verifyNoFacadeCallOnBare(SecuritySandboxService svc) {
        // 未装配时不得触碰门面（若触碰即 NPE，与上面 success=false 的达成路径矛盾）——
        // 用反射确认字段为 null 即可，保持本门无多余 mock。
        try {
            java.lang.reflect.Field f = SecuritySandboxService.class.getDeclaredField("profileQueryService");
            f.setAccessible(true);
            assertEquals(null, f.get(svc), "「未装配」夹具的字段非 null（夹具失真）");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("反射读 profileQueryService 字段失败：" + e.getMessage(), e);
        }
    }

    // ── ⑤ 活性取证：合成复活夹具判据能变红 ────────────────

    @Test
    @DisplayName("活性取证：合成幻影腿命中本门判据（判据能变红，非恒绿）")
    void judgementsFireOnSyntheticRegression() {
        String revived = SourceCommentStripper.strip("""
                public class RevivedDao {
                    public int countSuperAdminRole(String userId) {
                        var rows = jdbcTemplate.queryForList(
                            "SELECT COUNT(*) FROM td_user_security_profile usp "
                            + "JOIN td_role_security_profile rsp ON usp.profile_id = rsp.profile_id "
                            + "WHERE usp.user_id = ? AND rsp.role_name = 'ROLE_SUPER_ADMIN'",
                            Long.class, userId);
                        return rows.isEmpty() ? 0 : ((Number) rows.get(0)).intValue();
                    }
                }
                """);
        assertTrue(revived.contains("countSuperAdminRole"), "判据①复活夹具未命中（恒绿假绿）");
        assertTrue(revived.contains("profile_id"), "判据①profile_id 判据未命中复活夹具（恒绿假绿）");
        String stripped = methodBody(revived, "public int countSuperAdminRole(String userId)");
        assertFalse(stripped.isEmpty(), "方法体抽取器对夹具失效（判据①塌陷风险）");
    }

    // ── 工具 ─────────────────────────────────────────────

    /** 抽方法体（花括号配平；签名不含行首修饰符，调用侧传完整签名）。 */
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

    /** 扫描 main 源（含 javadoc/注释一并读入后再整体剥离）：幻影列判据须看<b>代码面</b>，
     *  历史来历注记（如 {@code 无 profile_id 列} 的 remediation javadoc）是合法文字，不判红。 */
    private static Map<String, String> mainJavaSourceStripped(String relativeRoot) throws IOException {
        return mainJavaSource(relativeRoot)
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> SourceCommentStripper.strip(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private static Map<String, String> mainJavaSource(String relativeRoot) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path root = locateRepoRoot().resolve(relativeRoot);
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (List<Path>) s.filter(q -> q.toString().endsWith(".java")).toList()) {
                out.put(relative(root, p), Files.readString(p, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String relative(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    private static String stripAt(String relative) throws IOException {
        return SourceCommentStripper.strip(Files.readString(locateRepoRoot().resolve(relative), StandardCharsets.UTF_8));
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
