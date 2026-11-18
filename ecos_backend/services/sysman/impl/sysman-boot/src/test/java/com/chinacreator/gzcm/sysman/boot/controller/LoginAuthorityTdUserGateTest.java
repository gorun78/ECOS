package com.chinacreator.gzcm.sysman.boot.controller;

import com.chinacreator.gzcm.sysman.boot.service.AuthService;
import com.chinacreator.gzcm.sysman.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录权威表单源门禁（V347，2026-11-17）：登录必须读 {@code td_user}，不得再读平行表 {@code users}。
 *
 * <p><b>为什么钉这一条</b>：改造前全后端唯一的 {@code /api/v1/auth/login} 在本模块
 * {@code AuthController}，而它读 {@code users}；{@code users} 只有 2 行，靠
 * {@code LoginCredentialService} 从 {@code td_user}（17 行）<b>单向</b>镜像出来。
 * 单向镜像已经产生真实分叉：u-d01 在 users 侧 {@code locked_until} 有值、td_user 侧
 * {@code locked=0}，即「管理面已锁、登录面未锁」与反向都可能发生。
 * 另有一版 td_user 登录实现 {@code AuthServiceImpl} 是 {@code @Service} 但零注入方，
 * 从来没有 HTTP 入口 —— 所以「td_user 是登录表」在改造前只是文档口径，不是事实。
 *
 * <p><b>部署面取证</b>（推翻「boot 不部署」的误判，故本门禁必须覆盖 boot）：
 * {@code gateway/pom.xml} 依赖 {@code sysman-boot}，部署 fat JAR 内实存
 * {@code BOOT-INF/lib/sysman-boot-1.0.0-SNAPSHOT.jar}，{@code GatewayApplication}
 * 扫 {@code com.chinacreator.gzcm.sysman} 覆盖本包。
 *
 * <p><b>角色排序是授权判据不是美观</b>：{@code rbac.rego} 的 {@code input.role} 取首角色，
 * {@code PermissionAspect}/{@code GitRepoOrgGuard} 大小写敏感判 {@code ROLE_SUPER_ADMIN}/{@code admin}，
 * 而 td_role 的系统管理员码是 {@code ADMIN} ⇒ 不置首则这些判据全落空。
 *
 * <p>先剥注释再判定：本门禁与被测类的 javadoc 都要提到旧表名，直接全文匹配会把「解释缺陷」
 * 误判成「重现缺陷」。
 */
@DisplayName("V347：登录权威表 = td_user（禁 users 平行表回流）")
class LoginAuthorityTdUserGateTest {

    private static final Pattern USERS_TABLE =
            Pattern.compile("(?i)\\b(from|into|update|join)\\s+(ecos_control\\.)?users\\b");

    private static Path bootMain() {
        Path local = Path.of("src/main");
        if (Files.exists(local.resolve("java/com/chinacreator/gzcm/sysman/boot/dao/AuthDao.java"))) {
            return local;
        }
        return Path.of("ecos_backend/services/sysman/impl/sysman-boot/src/main");
    }

    private static Path implMain() {
        Path sibling = Path.of("../sysman-impl/src/main");
        if (Files.exists(sibling.resolve("java/com/chinacreator/gzcm/sysman/iam/service/impl/UserServiceImpl.java"))) {
            return sibling;
        }
        return Path.of("ecos_backend/services/sysman/impl/sysman-impl/src/main");
    }

    private static String read(Path p) throws Exception {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 剥掉块注释与行注释，只留可执行代码面。 */
    private static String codeOnly(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                continue;
            }
            if (src.startsWith("//", i)) {
                int end = src.indexOf('\n', i + 2);
                i = end < 0 ? n : end;
                continue;
            }
            out.append(src.charAt(i++));
        }
        return out.toString();
    }

    private static List<Path> sources(Path root) throws Exception {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String f = p.toString();
                        return f.endsWith(".java") || f.endsWith(".xml");
                    })
                    .toList();
        }
    }

    /** 取某方法的代码面（从签名起到下一个同缩进方法/类尾），用于逐语句钉值。 */
    private static String methodBody(String code, String signature) {
        int start = code.indexOf(signature);
        if (start < 0) {
            return "";
        }
        int brace = code.indexOf('{', start);
        if (brace < 0) {
            return "";
        }
        int depth = 0;
        for (int i = brace; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return code.substring(brace, i + 1);
                }
            }
        }
        return code.substring(brace);
    }

    /**
     * 取字符串常量的拼接值（SQL 片段被抽成 {@code LOGINABLE}/{@code LOGIN_COLUMNS} 共享）。
     * 只钉方法体里的字面量会漏掉常量承载的部分，故常量值与方法体两面都要钉。
     */
    private static String constantValue(String code, String name) {
        int start = code.indexOf("String " + name + " =");
        if (start < 0) {
            return "";
        }
        int semi = code.indexOf(';', start);
        if (semi < 0) {
            return "";
        }
        String expr = code.substring(start, semi);
        StringBuilder out = new StringBuilder();
        var m = Pattern.compile("\"([^\"]*)\"").matcher(expr);
        while (m.find()) {
            out.append(m.group(1));
        }
        return out.toString();
    }

    private static String daoCode() throws Exception {
        return codeOnly(read(bootMain().resolve("java/com/chinacreator/gzcm/sysman/boot/dao/AuthDao.java")));
    }

    private static String controllerCode() throws Exception {
        return codeOnly(read(bootMain().resolve(
                "java/com/chinacreator/gzcm/sysman/boot/controller/AuthController.java")));
    }

    // ── ① 棘轮：两个部署模块的可执行代码面都不得再出现 users 表 ──────────────

    @Test
    @DisplayName("棘轮：sysman-boot 与 sysman-impl 的 src/main 不得再引用 users 表")
    void noUsersTableReferenceInDeployedModules() throws Exception {
        List<String> hits = new ArrayList<>();
        for (Path root : List.of(bootMain(), implMain())) {
            for (Path p : sources(root)) {
                String code = codeOnly(read(p));
                var m = USERS_TABLE.matcher(code);
                while (m.find()) {
                    hits.add(root.relativize(p) + " → " + m.group());
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "登录权威表已统一到 td_user，不得再读写 users（命中: " + hits + "）");
    }

    @Test
    @DisplayName("镜像层必须已拆除：两个类文件不存在，且无 LoginCredential 残留引用")
    void mirrorLayerRemoved() throws Exception {
        Path impl = implMain();
        assertFalse(Files.exists(impl.resolve(
                        "java/com/chinacreator/gzcm/sysman/iam/dao/impl/LoginCredentialDaoImpl.java")),
                "LoginCredentialDaoImpl 仍在：它是 users 的唯一写方，留着就等于平行表回流");
        assertFalse(Files.exists(impl.resolve(
                        "java/com/chinacreator/gzcm/sysman/iam/service/impl/LoginCredentialService.java")),
                "LoginCredentialService 仍在：单向镜像正是锁定态分叉的成因");
        for (Path root : List.of(bootMain(), impl)) {
            for (Path p : sources(root)) {
                assertFalse(codeOnly(read(p)).contains("LoginCredential"),
                        "残留镜像层引用: " + root.relativize(p));
            }
        }
    }

    // ── ② 读面：登录投影必须落 td_user 且只放 ACTIVE ─────────────────────────

    @Test
    @DisplayName("登录投影读 td_user，别名对齐旧键面，且只放 status='ACTIVE'")
    void loginProjectionReadsTdUserActiveOnly() throws Exception {
        String code = daoCode();
        String body = methodBody(code, "public List<Map<String, Object>> findUserForLogin(");
        assertFalse(body.isEmpty(), "找不到 findUserForLogin：读面被改名或移除，门禁需同批订正");
        assertTrue(body.contains("FROM td_user"), "登录必须查 td_user，实得: " + body);

        // 状态过滤与列别名由共享常量承载，两面都钉：常量值 + 方法确实拼上了它。
        String loginable = constantValue(code, "LOGINABLE");
        assertTrue(loginable.contains("status = 'ACTIVE'"),
                "INACTIVE/DELETED 账号必须拒登（逻辑删只打标记，不滤状态则已删账号仍能登录），实得: " + loginable);
        assertTrue(body.contains("+ LOGINABLE"), "登录语句必须拼上状态过滤常量，摘掉就等于放行已删账号");
        for (String sig : List.of("public List<Map<String, Object>> findUserById(",
                "public List<Map<String, Object>> findUserForPasswordChange(")) {
            assertTrue(methodBody(code, sig).contains("+ LOGINABLE"),
                    "/me 与改密读面同样必须滤状态: " + sig);
        }

        String columns = constantValue(code, "LOGIN_COLUMNS");
        assertTrue(body.contains("LOGIN_COLUMNS"), "登录投影必须用共享列面常量");
        assertTrue(columns.contains("user_id AS id") && columns.contains("password AS password_hash"),
                "键面别名漂移会让 AuthController 取到 null 而静默走「口令错误」分支，实得: " + columns);
        assertTrue(columns.contains("locked") && columns.contains("locked_until"),
                "两种锁字段都要投影出来，否则管理锁或爆破锁之一会被登录面忽略");
    }

    @Test
    @DisplayName("角色单源 = td_user_role JOIN td_role，且超管码显式置首")
    void roleCodesFromMembershipWithSuperAdminFirst() throws Exception {
        String body = methodBody(daoCode(), "public List<String> findRoleCodes(");
        assertFalse(body.isEmpty(), "找不到 findRoleCodes：角色取数面被改名或移除");
        assertTrue(body.contains("FROM td_user_role ur"), "角色必须走真实隶属表 td_user_role");
        assertTrue(body.contains("JOIN td_role r ON ur.role_id = r.role_id"),
                "JOIN 键必须是 role_id 真列（与 SecurityProfileQueryDao.hasRoleCode 同形态）");
        assertTrue(body.contains("CASE WHEN r.role_code = 'ROLE_SUPER_ADMIN' THEN 0 ELSE 1 END"),
                "超管码必须置首：rbac.rego 的 input.role 取首角色，ADMIN 排前面会让 scenario:write 恒 false");
        assertFalse(body.contains("users.roles"), "不得再读 users 的 JSON 角色列");
    }

    @Test
    @DisplayName("改密写面落 td_user.password，历史列走 TEXT 不用 jsonb cast")
    void passwordWriteTargetsTdUser() throws Exception {
        String body = methodBody(daoCode(), "public void updatePassword(");
        assertTrue(body.contains("UPDATE td_user SET password = ?"), "改密必须写 td_user.password");
        assertTrue(body.contains("is_password_change_required = FALSE"), "改密后必须清强制改密标记");
        assertTrue(body.contains("password_history_json = ?"), "历史列必须是 DR04 的 _json 后缀真列");
        assertFalse(body.toLowerCase().contains("as jsonb"), "MC03：不得用 PG 专有 cast 绑历史列");
    }

    @Test
    @DisplayName("爆破锁只写 locked_until，不得写管理锁标志 locked='1'")
    void bruteForceLockDoesNotSetManagementFlag() throws Exception {
        String body = methodBody(daoCode(), "public void lockAccount(");
        assertTrue(body.contains("locked_until = NOW() + (? * INTERVAL '1 minute')"),
                "爆破锁必须写到期时刻，否则锁永不过期");
        assertFalse(body.contains("locked = '1'") || body.contains("locked='1'"),
                "爆破锁写 locked='1' 会把临时锁定变成永久锁定（管理锁判据无到期）");
        String reset = methodBody(daoCode(), "public void resetFailedAttempts(");
        assertTrue(reset.contains("failed_attempts = 0") && reset.contains("locked_until = NULL"),
                "登录成功后必须清失败计数与爆破锁");
        assertFalse(reset.contains("locked = '0'"),
                "不得顺手清管理锁：那是管理员施加的，只有 unlockUser 有权解除");
    }

    // ── ③ 行为钉值：真跑控制器，锁与哨兵必须按语义拒登 ───────────────────────

    /** 手写桩（boot 模块无 mockito）：记录写面调用，便于断言「拒登时不得计次」。 */
    private static final class StubAuth extends AuthService {
        private final Map<String, Object> row;
        final List<String> writes = new ArrayList<>();

        StubAuth(Map<String, Object> row) {
            super(null);
            this.row = row;
        }

        @Override
        public List<Map<String, Object>> findUserForLogin(String username) {
            return List.of(row);
        }

        @Override
        public List<String> findRoleCodes(String userId) {
            return List.of("ADMIN");
        }

        @Override
        public String findLinkedWorkstation(String userId) {
            return null;
        }

        @Override
        public List<Map<String, Object>> findTenantByUsername(String username) {
            return List.of();
        }

        @Override
        public void updateFailedAttempts(int failedAttempts, String username) {
            writes.add("updateFailedAttempts");
        }

        @Override
        public void lockAccount(int failedAttempts, int lockoutDurationMinutes, String username) {
            writes.add("lockAccount");
        }

        @Override
        public void resetFailedAttempts(String userId) {
            writes.add("resetFailedAttempts");
        }
    }

    private static Map<String, Object> account(String passwordHash) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", "u-gate");
        row.put("username", "gate");
        row.put("password_hash", passwordHash);
        row.put("display_name", "门禁账号");
        row.put("status", "ACTIVE");
        row.put("locked", "0");
        row.put("failed_attempts", 0);
        row.put("locked_until", null);
        row.put("password_change_required", Boolean.FALSE);
        row.put("last_password_change", null);
        row.put("password_history", null);
        return row;
    }

    private static int loginStatus(StubAuth stub) {
        AuthController controller = new AuthController(null, stub, null);
        ResponseEntity<?> res = controller.login(LoginRequest.of("gate", "Whatever@2026Aa"), null);
        return res.getStatusCode().value();
    }

    @Test
    @DisplayName("管理锁 locked='1' ⇒ 423，且不计失败次数")
    void managementLockRejectsWith423() {
        Map<String, Object> row = account("$2a$10$abcdefghijklmnopqrstuv");
        row.put("locked", "1");
        StubAuth stub = new StubAuth(row);
        assertEquals(423, loginStatus(stub), "管理员锁定的账号必须 423，不能放行进口令比对");
        assertTrue(stub.writes.isEmpty(), "拒登路径不得写失败计数/锁定: " + stub.writes);
    }

    @Test
    @DisplayName("爆破锁未到期 ⇒ 423；管理锁优先于已到期的爆破锁")
    void bruteForceLockRejectsWith423() {
        Map<String, Object> future = account("$2a$10$abcdefghijklmnopqrstuv");
        future.put("locked_until", Timestamp.from(Instant.now().plusSeconds(600)));
        assertEquals(423, loginStatus(new StubAuth(future)), "未到期的爆破锁必须 423");

        Map<String, Object> both = account("$2a$10$abcdefghijklmnopqrstuv");
        both.put("locked", "1");
        both.put("locked_until", Timestamp.from(Instant.now().minusSeconds(600)));
        assertEquals(423, loginStatus(new StubAuth(both)),
                "爆破锁已到期但管理锁仍在 ⇒ 仍须 423（两种锁是或关系，不是互相覆盖）");
    }

    @Test
    @DisplayName("NOLOGIN 哨兵账号 ⇒ 401，且不得给它计次或写爆破锁")
    void nologinSentinelRejectedWithoutCounting() {
        StubAuth stub = new StubAuth(account("NOLOGIN:seed-entmgmt"));
        assertEquals(401, loginStatus(stub), "种子哨兵账号没有口令，必须 401");
        assertTrue(stub.writes.isEmpty(),
                "哨兵账号被计次/写锁属无意义写面: " + stub.writes);
    }

    @Test
    @DisplayName("反向探针自检：旧 users 语句形态必须被棘轮命中")
    void probeRecognizesLegacyShape() {
        assertTrue(USERS_TABLE.matcher("SELECT id FROM users WHERE username = ?").find(),
                "不限定 schema 的旧形态必须命中");
        assertTrue(USERS_TABLE.matcher("UPDATE ecos_control.users SET enabled = FALSE").find(),
                "限定 schema 的旧形态必须命中");
        assertTrue(USERS_TABLE.matcher("INSERT INTO ecos_control.users (id) VALUES (?)").find(),
                "写面形态必须命中");
        assertFalse(USERS_TABLE.matcher("SELECT user_id FROM td_user WHERE username = ?").find(),
                "新读面不得被误判");
        assertFalse(USERS_TABLE.matcher("SELECT count(*) FROM td_user_role ur").find(),
                "隶属表不得被误判（词边界）");
        String commented = codeOnly("/* SELECT id FROM users */ // UPDATE users SET x=1\nSELECT 1 FROM td_user");
        assertFalse(USERS_TABLE.matcher(commented).find(),
                "注释里的旧形态不得被误判成回流（否则门禁自己的 javadoc 就会让它恒红）");
    }
}