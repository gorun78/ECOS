package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.engine.kb.nav.model.NavCategorySaveDTO;
import com.chinacreator.gzcm.engine.kb.nav.model.NavFolderQuery;
import com.chinacreator.gzcm.engine.kb.nav.model.NavProductItemVO;
import com.chinacreator.gzcm.engine.kb.service.nav.NavLlmRecommendService;
import com.chinacreator.gzcm.engine.kb.service.nav.NavTaxonServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * F04-16 验收（{@code mvn -Dtest=KbIsolationTest}）— 域隔离与可归因写入（K-41/K-42/K-43）。
 *
 * <p>三条红线的验收口径：
 * <ul>
 *   <li>{@link #writeUsesRealActorAndDomain()}：登录后调用
 *       {@code NavTaxonServiceImpl.createCategory}，JDBC 执行 INSERT 时 {@code create_by /
 *       update_by} 两个 param 位置必须取 JWT 真实主体（而非 {@code 'current-user'} 字面量）。
 *       这条断言本身即证 K-42 已被纠偏：若 service 仍硬编码 {@code 'current-user'}，
 *       实际参数值将是 {@code "current-user"} 而非 {@code "u-1001"}。</li>
 *   <li>{@link #crossDomainReadIsDenied()}：登录 u-1001 后 {@code listProducts} 携带
 *       {@code domain=d-owned} 时，SQL 参数中必须携带 {@code d-owned}（即 WHERE 子句已按
 *       domain 谓词收敛），返回集只回同域记录。跨域记录在 SQL 阶段就被过滤掉，
 *       不依赖 Java 端过滤（K-41 RLS 语义）。</li>
 *   <li>{@link #everyWriteEndpointHasPermissionAnnotation()}：反射扫描
 *       {@code engine.kb.controller.*Controller} 全部 {@code @PostMapping/@PutMapping/@DeleteMapping}
 *       方法（含多层级），每一个必须带 {@link RequirePermission}
 *       （K-43 写端点权限无极扩展）。反空扫描：至少观察到 1 个写端点，防"包空"拟判。</li>
 * </ul>
 *
 * <p>全部纯 Mockito，不启动 Spring；不依赖 live DB；不引入 AssertJ / H2 / SpringBootTest。</p>
 *
 * @author ECOS KB Team
 */
class KbIsolationTest {

    private JdbcTemplate jdbc;
    private NavTaxonServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        var securityClient = mock(com.chinacreator.gzcm.engine.kb.security.KnowledgeNavSecurityEngineClient.class);
        NavLlmRecommendService llm = mock(NavLlmRecommendService.class);
        service = new NavTaxonServiceImpl(jdbc, securityClient, llm);
        // 允许 ABAC 通过（否则 createCategory 会在 checkAbac 处早退）
        when(securityClient.evaluatePolicy(anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login(String principal, String... roles) {
        List<org.springframework.security.core.GrantedAuthority> authorities = new ArrayList<>();
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority(r));
        }
        TestingAuthenticationToken token = new TestingAuthenticationToken(principal, "n/a", authorities);
        token.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    @DisplayName("F04-16: createCategory 的 JDBC 参数必须取真实 JWT 主体（K-42，禁 'current-user'）")
    @SuppressWarnings("unchecked")
    void writeUsesRealActorAndDomain() {
        login("u-1001", "knowledge-admin");

        NavCategorySaveDTO dto = new NavCategorySaveDTO();
        dto.setDomain("d1");
        dto.setName("hello");
        // parentId null → level 1，无需额外 jdbc stub

        try {
            service.createCategory(dto);
        } catch (RuntimeException ignored) {
            // find-by-id 后续 jdbc stub 未 mock 会抛异常；但 INSERT 已经发生，参数值可查
        }

        // 遍历所有 jdbc.update 调用：定位到 INSERT INTO kb_nav_category 那次
        org.mockito.invocation.Invocation hit = null;
        for (org.mockito.invocation.Invocation inv : mockingDetails(jdbc).getInvocations()) {
            if (!"update".equals(inv.getMethod().getName())) {
                continue;
            }
            Object[] args = inv.getArguments();
            if (args.length >= 2 && args[0] instanceof String sql
                    && sql.contains("INSERT INTO ecos_knowledge.kb_nav_category")) {
                hit = inv;
                break;
            }
        }
        assertNotNull(hit, "createCategory 必须触发 JDBC INSERT INTO kb_nav_category");

        Object[] args = hit.getArguments();
        // SQL 形：INSERT INTO ... (id auto, domain, parent_id, name, path, level, sort_order, create_by, update_by)
        //       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        // args[0] = SQL；Mockito 对 varargs 的存储把 8 个 bind 参摊平到 args[1..8]
        // → domain=args[1], parent_id=args[2], name=args[3], path=args[4],
        //   level=args[5], sort_order=args[6], create_by=args[7], update_by=args[8]
        assertEquals(9, args.length,
                "INSERT INTO kb_nav_category JDBC 参数应为 sql + 8 值（Mockito 摊平 varargs），实际 = " + args.length);
        assertEquals("d1", args[1], "domain 应与请求 domain 一致");
        assertEquals("hello", args[3], "name 应与请求 name 一致");
        // create_by / update_by 是最后 2 个 bind 参
        assertEquals("u-1001", args[7],
                "create_by 必须等于真实 JWT 主体（K-42，禁 'current-user' 常量）");
        assertEquals("u-1001", args[8],
                "update_by 必须等于真实 JWT 主体（K-42，禁 'current-user' 常量）");
        assertNotEquals("current-user", args[7]);
        assertNotEquals("current-user", args[8]);
    }

    @Test
    @DisplayName("F04-16: listProducts 的 SQL 必须携带参数化谓词 a.domain = ?，值 = 请求 domain（K-41 RLS 语义）")
    void crossDomainReadIsDenied() {
        NavFolderQuery q = new NavFolderQuery();
        q.setDomain("d-owned");
        q.setPageNum(1);
        q.setPageSize(20);

        // JDBC stub：模拟 DB 层已经按 domain 谓词过滤的结果集（同域 1 条）。
        // 反例回归：若 service 生成的 SQL 未携带 a.domain = ?（或改成了字面量），
        // 实际 JDBC 收到的 sql 文本与参数会都没有 'd-owned' 命中，行记录里会混入跨域。
        java.util.Map<String, Object> row1 = new java.util.LinkedHashMap<>();
        row1.put("id", "a1");
        row1.put("title", "OK");
        row1.put("source", "manual");
        row1.put("domain", "d-owned");
        row1.put("category", "doc");
        row1.put("status", "published");
        row1.put("updated_at", null);
        java.util.List<java.util.Map<String, Object>> rows = java.util.List.of(row1);
        when(jdbc.queryForList(
                        argThat((String s) -> s != null && s.contains("FROM ecos_knowledge.knowledge_article")),
                        (Object[]) any()))
                .thenReturn(rows);

        List<NavProductItemVO> out = service.listProducts(q);
        assertEquals(1, out.size(), "listProducts 返回集大小应与 JDBC 返回集一致");
        assertEquals("d-owned", out.get(0).getDomain(), "返回集 domain 必须等于请求 domain（跨域记录不应出现）");

        boolean sawParameterizedPredicate = false;
        boolean sawDomainValueParam = false;
        for (org.mockito.invocation.Invocation inv : mockingDetails(jdbc).getInvocations()) {
            if (!"queryForList".equals(inv.getMethod().getName())) {
                continue;
            }
            Object[] a = inv.getArguments();
            if (a.length < 2 || !(a[0] instanceof String sql)
                    || !sql.contains("FROM ecos_knowledge.knowledge_article")) {
                continue;
            }
            if (sql.contains("a.domain = ?")) {
                sawParameterizedPredicate = true;
            }
            // 展开 vararg：jdbc.queryForList(String, Object...) 的第二次实参通常是
            // List.toArray() 出来的 Object[]，需要再展开一层才能看到单个参数值。
            for (Object v : a) {
                if ("d-owned".equals(v)) {
                    sawDomainValueParam = true;
                    break;
                }
                if (v instanceof Object[] inner && inner.length > 0) {
                    for (Object vv : inner) {
                        if ("d-owned".equals(vv)) {
                            sawDomainValueParam = true;
                            break;
                        }
                    }
                }
                if (sawDomainValueParam) {
                    break;
                }
            }
            if (sawParameterizedPredicate && sawDomainValueParam) {
                break;
            }
        }
        assertTrue(sawParameterizedPredicate,
                "listProducts 的 SQL 必须含参数化谓词 'a.domain = ?'（禁字面量、禁硬编码）");
        assertTrue(sawDomainValueParam,
                "listProducts 传递的 JDBC 参数必须含 'd-owned'（即 WHERE a.domain = ? 的值）");
    }

    private static org.mockito.ArgumentMatcher<String> argThatSqlForArticle() {
        return (String s) -> s != null && s.contains("FROM ecos_knowledge.knowledge_article");
    }

    @Test
    @DisplayName("F04-16: kb 全部写端点（@PostMapping/@PutMapping/@DeleteMapping）必须带 @RequirePermission（K-43）")
    void everyWriteEndpointHasPermissionAnnotation() throws Exception {
        Path controllerDir = resolveKbControllerDir();
        assertTrue(Files.isDirectory(controllerDir),
                "未定位到 kb controller 目录: " + controllerDir);

        List<Path> controllerFiles = new ArrayList<>();
        try (var s = Files.walk(controllerDir)) {
            s.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith("Controller.java"))
                    .forEach(controllerFiles::add);
        }
        assertFalse(controllerFiles.isEmpty(), "未扫描到任何 controller 源文件");

        List<String> missing = new ArrayList<>();
        int scannedWriteEndpoints = 0;

        for (Path f : controllerFiles) {
            String rel = controllerDir.relativize(f).toString().replace('\\', '/');
            String fqcn = "com.chinacreator.gzcm.engine.kb.controller."
                    + rel.substring(0, rel.length() - ".java".length()).replace('/', '.');
            Class<?> cls = Class.forName(fqcn);
            for (Method m : cls.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                boolean isWrite = m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class);
                if (!isWrite) {
                    continue;
                }
                scannedWriteEndpoints++;
                if (!m.isAnnotationPresent(RequirePermission.class)) {
                    missing.add(fqcn + "#" + m.getName());
                }
            }
        }

        assertTrue(scannedWriteEndpoints > 0,
                "反空扫描失败：未在 kb controller 包内观察到任何写端点（@Post/@Put/@DeleteMapping）");
        assertTrue(missing.isEmpty(),
                "下列 kb 写端点缺 @RequirePermission（K-43 必须覆盖全部写端点）：\n  "
                        + String.join("\n  ", missing));
    }

    /** 通过 classpath 定位 {@code engine/kb/engine/kb-engine-impl/src/main/java/.../controller}。 */
    private static Path resolveKbControllerDir() throws Exception {
        URL resource = ClassLoader.getSystemResource("com/chinacreator/gzcm/engine/kb/controller");
        assertNotNull(resource, "classpath 上未找到 kb controller 目录");
        Path targetDir = Paths.get(resource.toURI());
        Path cur = targetDir;
        // cur 现在是 target/classes/com/.../controller —— 向上走到 kb-engine-impl
        for (int i = 0; i < 8; i++) {
            Path candidate = cur.resolve("src/main/java/com/chinacreator/gzcm/engine/kb/controller");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path parent = cur.getParent();
            if (parent == null) {
                break;
            }
            cur = parent;
        }
        // 兜底：从测试类源码定位（surefire CWD 稳定）
        Path c = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 10 && c != null; i++) {
            Path p = c.resolve("src/main/java/com/chinacreator/gzcm/engine/kb/controller");
            if (Files.isDirectory(p)) {
                return p;
            }
            c = c.getParent();
        }
        fail("未定位到 kb controller 源目录（classpath + CWD up 均未命中）");
        return null;
    }
}
