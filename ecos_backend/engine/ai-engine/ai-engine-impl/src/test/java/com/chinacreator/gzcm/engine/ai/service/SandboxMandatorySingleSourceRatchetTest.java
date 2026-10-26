package com.chinacreator.gzcm.engine.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinacreator.gzcm.engine.ai.dao.ToolDao;
import com.chinacreator.gzcm.engine.ai.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.model.SecurityProfile;
import com.chinacreator.gzcm.sysman.security.profile.service.ISecurityProfileQueryService;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 沙盒强制位 {@code sandbox_mandatory} <b>属主门面单源</b>棘轮门（#142/B4-3e-85，收口台账 <b>D-B4-102</b>）。
 *
 * <p><b>缺陷本质</b>：{@code td_user_security_profile} 属主是 sysman（铁律 #5），但 AI 工具咽喉
 * 原先在 {@code ToolDao} 另存一条直读 SQL，谓词写成小写 {@code scope_type = 'user'}——DDL 口径
 * （V41：{@code USER/ROLE/TENANT/ORG/GLOBAL}）恒大写，该腿恒取空集 ⇒ 强制沙盒的用户被当成
 * 「不强制」，高危指令的沙盒审查被<b>静默跳过（fail-open）</b>。</p>
 *
 * <p><b>本批处置</b>：取数只经属主门面 {@code ISecurityProfileQueryService.findUser}，
 * {@code ToolDao} 该腿删除；三态语义——门面未装配或取数异常 ⇒ {@code null}（不可判定，
 * 调用方 FAIL_CLOSED），档案缺失或配置位为 null ⇒ {@code false}（不强制）。
 * 安全引擎的另一枚幻影列腿（{@code SecuritySandboxDao.countSuperAdminRole}，
 * {@code profile_id}×2＋{@code role_name}）登记 <b>D-B4-103</b> 另批收口，本门不越域。</p>
 */
@Timeout(120)
class SandboxMandatorySingleSourceRatchetTest {

    private static final String SERVICE_SRC =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java";
    private static final String DAO_SRC =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/dao/ToolDao.java";

    private static final Pattern OWNER_TABLE_IN_AI = Pattern.compile("td_user_security_profile|td_role_security_profile");
    /** 复活判据：小写 user 档谓词（D-B4-102 原形态，DDL 大写口径下恒空集）。 */
    private static final Pattern LOWER_CASE_SCOPE_PREDICATE = Pattern.compile(
            "scope_type\\s*=\\s*'?user'?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELEGATION = Pattern.compile("profileQueryService\\.findUser\\s*\\(");

    // ── ① AI 域 main 源不再持有属主表名 ──────────────────

    @Test
    @DisplayName("本域 main 源无属主表 td_user_security_profile / td_role_security_profile（注释剥净后）")
    void aiDomainMainCarriesNoOwnerTable() throws IOException {
        Map<String, String> domain = domainMainSource(aiEngineRoot());
        assertTrue(domain.size() > 100,
                "扫描面塌陷：ai-engine main java 文件仅 " + domain.size() + " 枚（基线 280，防塌陷 >100）");
        List<String> violators = new ArrayList<>();
        for (Map.Entry<String, String> e : domain.entrySet()) {
            if (OWNER_TABLE_IN_AI.matcher(e.getValue()).find()) {
                violators.add(e.getKey());
            }
        }
        assertTrue(violators.isEmpty(),
                "AI 域 main 源又出现属主表名（跨域直读复发，D-B4-102）：" + violators);
    }

    // ── ② 咽喉委派形状：只经门面，DAO 无谓词腿 ───────────

    @Test
    @DisplayName("ToolExecutorService.querySandboxMandatory 只经门面 findUser；ToolDao 无 querySandboxMandatory 腿")
    void chokepointDelegatesViaFacadeOnly() throws IOException {
        String service = stripAt(SERVICE_SRC);
        String body = methodBody(service, "private Boolean querySandboxMandatory(String userId)");
        assertTrue(body != null, "querySandboxMandatory 方法体缺席或签名漂移：本门禁的扫描面已塌陷");
        assertTrue(DELEGATION.matcher(body).find(),
                "querySandboxMandatory 不再经 profileQueryService.findUser 取数（旁路回流）");
        assertFalse(LOWER_CASE_SCOPE_PREDICATE.matcher(body).find(),
                "querySandboxMandatory 又出现 scope_type='user' 小写谓词（D-B4-102 原形态复活，fail-open）");
        assertTrue(body.contains("profileQueryService == null"),
                "门面未装配判据缺席（不可判定⇒FAIL_CLOSED 的三态被折叠）");

        String dao = stripAt(DAO_SRC);
        assertFalse(dao.contains("querySandboxMandatory("),
                "ToolDao 又出现 querySandboxMandatory 腿（引擎直读属主表复发）");
        assertFalse(LOWER_CASE_SCOPE_PREDICATE.matcher(dao).find(),
                "ToolDao 又出现小写 scope_type 谓词（D-B4-102 原形态复活）");
        // DAO 未被顺手清空（防「删干净＝假绿」）：既有三腿仍在
        for (String retained : List.of("loadToolDefinition(", "insertSandboxAudit(", "queryForList(", "executeSelect(")) {
            assertTrue(dao.contains(retained), "ToolDao 既有的 " + retained + " 缺席：扫描面疑似被截断");
        }
    }

    // ── ③ 行为钉值：三态各自归位 ──────────────────────────

    @Test
    @DisplayName("三态行为钉值：门面未装配⇒null；档案/配置位缺失⇒false；true⇒true；门面异常⇒null")
    void triStateSemanticsAreNailed() throws IOException {
        Method m = findMethod();

        // 门面未装配：不可判定（禁折叠成 false，否则绕过审查＝fail-open）
        assertNull((Boolean) invoke(m, new ToolExecutorService(), "u-none"),
                "门面未装配未回 null：三态被折叠成『不强制』＝fail-open 复活");

        ToolExecutorService svc = new ToolExecutorService();
        ISecurityProfileQueryService facade = mock(ISecurityProfileQueryService.class);
        svc.setProfileQueryService(facade);

        // 档案缺失 ⇒ 不强制（false）
        when(facade.findUser("u-noprofile", null)).thenReturn(null);
        assertFalse(evaluate(m, svc, "u-noprofile"), "档案缺失未归 false（画像缺失语义被改写）");

        // 档案在但 sandboxMandatory=null ⇒ 配置位缺省＝不强制（false）
        SecurityProfile pNull = new SecurityProfile();
        when(facade.findUser("u-nullflag", null)).thenReturn(pNull);
        assertFalse(evaluate(m, svc, "u-nullflag"), "配置位 null 未归 false（配置缺省语义被改写）");

        // 显式 false ⇒ false（调用方直接 dispatch）
        SecurityProfile pOff = new SecurityProfile();
        pOff.setSandboxMandatory(Boolean.FALSE);
        when(facade.findUser("u-off", null)).thenReturn(pOff);
        assertFalse(evaluate(m, svc, "u-off"), "显式不强制未归 false");

        // 显式 true ⇒ true（进入沙盒审查）
        SecurityProfile pOn = new SecurityProfile();
        pOn.setSandboxMandatory(Boolean.TRUE);
        when(facade.findUser("u-on", null)).thenReturn(pOn);
        assertTrue(evaluate(m, svc, "u-on"), "显式强制未归 true（审查腿被静默跳过＝fail-open）");

        // 门面异常 ⇒ 不可判定（null），禁折叠成 false
        when(facade.findUser("u-boom", null)).thenThrow(new IllegalStateException("db down"));
        assertNull((Boolean) invoke(m, svc, "u-boom"),
                "门面异常未归 null：取数失败被折叠成『不强制』＝fail-open");
    }

    // ── ④ 活性取证：合成复活夹具逐条命中判据 ───────────────

    @Test
    @DisplayName("活性取证：合成回退夹具（小写谓词直读 SQL / 去门面判据 / dao 旁路）逐条命中，判据能变红")
    void judgementsFireOnSyntheticRegression() {
        String revived = SourceCommentStripper.strip("""
                class FixtureService {
                    String sql = "SELECT sandbox_mandatory FROM td_user_security_profile " +
                                 "WHERE scope_type = 'user' AND user_id = ? LIMIT 1";
                    Boolean querySandboxMandatory(String userId) {
                        return toolDao.querySandboxMandatory(userId).isEmpty() ? Boolean.FALSE : Boolean.TRUE;
                    }
                }
                """);
        assertTrue(OWNER_TABLE_IN_AI.matcher(revived).find(),
                "属主表名判据对复活夹具不命中＝本门①恒绿（假绿）");
        assertTrue(LOWER_CASE_SCOPE_PREDICATE.matcher(revived).find(),
                "小写 scope 谓词判据对复活夹具不命中＝本门②恒绿（假绿）");
        assertFalse(revived.contains("profileQueryService == null"),
                "『门面未装配判据』缺失夹具误命中：判据方向反了");
        assertFalse(delegationFindsInMethod(revived),
                "『经门面 findUser』判据对未委派夹具误命中：判据方向反了");

        String good = SourceCommentStripper.strip("""
                class GoodService {
                    Boolean querySandboxMandatory(String userId) {
                        if (profileQueryService == null) {
                            return null;
                        }
                        SecurityProfile profile = profileQueryService.findUser(userId, null);
                        if (profile == null || profile.getSandboxMandatory() == null) {
                            return false;
                        }
                        return profile.getSandboxMandatory();
                    }
                }
                """);
        assertTrue(delegationFindsInMethod(good),
                "『经门面 findUser』判据对合规夹具不命中＝本门②恒红（误伤）");
        assertTrue(good.contains("profileQueryService == null"),
                "『门面未装配判据』对合规夹具不命中＝本门②恒红（误伤）");
    }

    // ── 工具 ─────────────────────────────────────

    private static boolean delegationFindsInMethod(String src) {
        String body = methodBody(src, "Boolean querySandboxMandatory(String userId)");
        return body != null && DELEGATION.matcher(body).find();
    }

    private static Method findMethod() {
        for (Method m : ToolExecutorService.class.getDeclaredMethods()) {
            if (m.getName().equals("querySandboxMandatory") && m.getParameterCount() == 1) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("ToolExecutorService.querySandboxMandatory(String) 缺席：咽喉阶段链已重构？本门须同步");
    }

    private static Boolean invoke(Method m, ToolExecutorService svc, String userId) {
        try {
            return (Boolean) m.invoke(svc, userId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("反射调用失败", e);
        }
    }

    private static boolean evaluate(Method m, ToolExecutorService svc, String userId) {
        Boolean v = invoke(m, svc, userId);
        return Boolean.TRUE.equals(v);
    }

    /** 花括号配平抽取方法体（含方法头行）。 */
    static String methodBody(String src, String signaturePrefix) {
        int i = src.indexOf(signaturePrefix);
        if (i < 0) {
            return null;
        }
        int open = src.indexOf('{', i + signaturePrefix.length());
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int j = open; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return src.substring(i, j + 1);
                }
            }
        }
        return null;
    }

    private static String stripAt(String relative) throws IOException {
        return SourceCommentStripper.strip(
                Files.readString(locateRepoRoot().resolve(relative), StandardCharsets.UTF_8));
    }

    private static String aiEngineRoot() {
        return "ecos_backend/engine/ai-engine";
    }

    private static Map<String, String> domainMainSource(String domainRoot) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Path root = locateRepoRoot();
        try (Stream<Path> s = Files.walk(root.resolve(domainRoot))) {
            for (Path f : s.filter(Files::isRegularFile).toList()) {
                String p = f.toString().replace('\\', '/');
                if (!p.contains("/src/main/java/") || p.contains("/target/")) {
                    continue;
                }
                out.put(root.relativize(f).toString().replace('\\', '/'),
                        SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8)));
            }
        }
        return out;
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
