package com.chinacreator.gzcm.workspace;

import com.chinacreator.gzcm.common.context.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询历史落库（F07-21 / C167 / W185）离线门禁 & X-55 内存态退役验证。
 *
 * <p><b>范围</b>：Mockito 桩 {@link JdbcTemplate}，不启 spring 容器、不启 PG。
 * 断言本服务<b>代码侧契约</b>：
 * <ul>
 *   <li>V212 表名 `public.ecos_scenario_query_history` 严格使用（ST07 控制域 + DR04 单数）</li>
 *   <li>写入 7 参数顺序 (id, trace_id, subject_id, sql_digest, row_count, create_by, update_by)</li>
 *   <li>E-5 摘要红线：写入参数不含 queryJson 原文，{@code sql_digest} 是 64 字符 sha-256 hex</li>
 *   <li>traceId 由 {@link TraceContext#current()} MDC 贯通，无 MDC 时 NULL（V212 允许）</li>
 *   <li>subjectId 无 SecurityContext 兜 bottom {@code "anonymous"}；有主体时用 principal</li>
 *   <li>delete / clear 一律走 {@code UPDATE … is_deleted = 1}，禁 {@code DELETE FROM}（IR03 语义）</li>
 *   <li>切断线 fail-soft：table 未建 / PG 打嗝 → save 不抛 + 返回已 id 化记录；getHistory 空清单降级</li>
 *   <li><b>源文件自反射</b>：本文 {@code CopyOnWriteArrayList} 0 命中（F07-21 设计原文 grep 门禁）</li>
 * </ul>
 *
 * <p><b>与 X-55 的差</b>：X-55 用内存 list MAX=200 → 多实例不一致 + 重启即失。
 * 这里用两实例 (同 jdbc mock) 读回一致 + 250 次 save 恰好触 250 次 JDBC update
 * （无 200 上限截断）来锁死"无 bean 内上限"。</p>
 *
 * <p><b>P-2 边界</b>：真容器 PG 落库与 "重启后读回同一行" 由 {@code QueryHistoryPersistenceIT}
 * 承接（该 IT 属 P-2 carrier，本窗不建以免对未实跑库造假，§14.4 未授权项）。</p>
 */
class QueryHistoryPersistenceTest {

    private JdbcTemplate jdbc;
    private QueryHistoryService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TraceContext.clear();
        jdbc = mock(JdbcTemplate.class);
        service = new QueryHistoryService(jdbc);
        // 服务用 jdbc.query(sql, RowCallbackHandler, Object...)（void 返回）：默认桩不喂行 → getHistory 空清单。
        doAnswer(inv -> null).when(jdbc)
            .query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), (Object[]) any());
    }

    /**
     * stub {@code jdbc.update(sql, 7 个变长槽位)} 并用 per-element {@code any()} 匹配器（Mockito 对
     * varargs **数组**传参的 cast 形式不可靠，离散 {@code any()} 是文档化的稳定写法）。
     * {@code sqlCap}（可空）捕获 SQL 文本；{@code cap} 捕获 7 个变长槽位（下标 0=id..6=update_by）。
     */
    private void stubSaveUpdate(AtomicReference<String> sqlCap, AtomicReference<Object[]> cap) {
        doAnswer(inv -> {
            Object[] a = new Object[7];
            for (int k = 0; k < 7; k++) a[k] = inv.getArgument(1 + k);
            cap.set(a);
            if (sqlCap != null) sqlCap.set(inv.getArgument(0, String.class));
            return 1;
        }).when(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TraceContext.clear();
    }

    /** 设计原文 {@code #historyIsPagedNotBoundedInMemory}：250 次 save → 恰 250 次 JDBC INSERT（X-55 MAX=200 上限已退役）。 */
    @Test
    void historyIsPagedNotBoundedInMemory() {
        int n = 250; // 刻意越过 X-55 的 MAX_HISTORY=200
        for (int i = 0; i < n; i++) {
            service.save("q-" + i, "SELECT i FROM t WHERE i=" + i, 1);
        }
        verify(jdbc, times(n)).update(anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    /** 同 jdbc stub 下两个 service 实例读路径回显相同（内存态在 bean 而是 jdbc 底）—— 无法真正重启也不看重启侧效果，
     *  本例锁的是"两个 service 实例共享行为 = 无 per-instance memory holding"。真容器重启验证归 P-2 IT。 */
    @Test
    void historySurvivesContextRestart() {
        // 让 query 桩喂一行（模拟"库里已存在一条"）：服务用 jdbc.query(sql, RowCallbackHandler, Object...)，
        // 桩内把服务给的 RowCallbackHandler 对一行 stub 结果 callback 一次。
        QueryRecord sentinel = new QueryRecord();
        sentinel.setId(id36("sentinel-" + 1));
        doAnswer(inv -> {
            org.springframework.jdbc.core.RowCallbackHandler rch = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("id")).thenReturn(sentinel.getId());
            when(rs.getString("trace_id")).thenReturn(null);
            when(rs.getString("subject_id")).thenReturn(null);
            when(rs.getString("sql_digest")).thenReturn(null);
            when(rs.getInt("row_count")).thenReturn(0);
            when(rs.getTimestamp("create_time")).thenReturn(Timestamp.valueOf(java.time.LocalDateTime.now()));
            rch.processRow(rs);
            return null;
        }).when(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), (Object[]) any());
        List<QueryRecord> fromA = service.getHistory(10);
        QueryHistoryService b = new QueryHistoryService(jdbc);
        List<QueryRecord> fromB = b.getHistory(10);

        assertEquals(1, fromA.size());
        assertEquals(1, fromB.size());
        assertEquals(sentinel.getId(), fromA.get(0).getId());
        assertEquals(fromA.get(0).getId(), fromB.get(0).getId(),
            "两实例同 jdbc 读路径同输出 ⇒ 无 per-instance 内存");
    }

    /** 设计原文 grep 门禁（本会话实现）：源文件 0 命中 {@code CopyOnWriteArrayList}。 */
    @Test
    void sourceFileContainsNoCopyOnWriteArrayList() throws Exception {
        Path me = selfSourcePath();
        assertNotNull(me, "反身门禁需读取 QueryHistoryService 源码，但未能定位（CWD 异常）");
        String src = new String(Files.readAllBytes(me), StandardCharsets.UTF_8);
        int idx = src.indexOf("CopyOnWriteArrayList");
        assertTrue(idx == -1, "QueryHistoryService.java 仍保留 X-55 in-memory 承载: 首次命中 offset=" + idx);
    }

    /** 设计原文 grep 门禁的等价印证：源文件必须使用 V212 目标表名 {@code public.ecos_scenario_query_history}。 */
    @Test
    void sourceTargetsV212SchemaQualifiedTable() throws Exception {
        Path me = selfSourcePath();
        assertNotNull(me, "反身门禁需读取 QueryHistoryService 源码");
        String src = new String(Files.readAllBytes(me), StandardCharsets.UTF_8);
        assertTrue(src.contains("public.ecos_scenario_query_history"),
            "QueryHistoryService 未引用 V212 表（脚本名 / 常量缺失）");
        // 相邻语义：不允许出现裸表名引用（附则 1）—— 每个 "ecos_scenario_query_history"
        // 命中点前 7 字符必须是 "public."（E-5 单源表名带 schema 限定）
        int i = 0;
        while ((i = src.indexOf("ecos_scenario_query_history", i)) >= 0) {
            int start = i;
            boolean qualified = start >= 7 && src.regionMatches(start - 7, "public.", 0, 7);
            assertTrue(qualified,
                "裸表名命中在 offset=" + start + "（前 7 字符应恰为 public.，实为 '"
                    + (start >= 7 ? src.substring(start - 7, start) : "") + "'）");
            i += 1;
        }
    }

    /** E-5 红线：写入参数不含 queryJson 任何原文片段，第 4 slot = 64 字符 sha-256 hex。 */
    @Test
    void saveWritesSchemaQualifiedV212TableWithSha256DigestOnly() {
        AtomicReference<String> sqlCap = new AtomicReference<>();
        AtomicReference<Object[]> argsCap = new AtomicReference<>();
        stubSaveUpdate(sqlCap, argsCap);

        String sensitive = "SELECT secret_pin FROM secret_table WHERE x=998877";
        service.save("user-text-question", sensitive, 42);

        String sql = sqlCap.get();
        assertTrue(sql.contains("INSERT INTO public.ecos_scenario_query_history"), "INSERT 未指向 V212 表: " + sql);

        Object[] a = argsCap.get();
        assertNotNull(a, "jdbc.update 变长参数未捕获");
        // 全参数不得泄漏原文 3 段文字
        for (Object arg : a) {
            String s = String.valueOf(arg);
            assertFalse(s.contains("secret_pin"),   "写入参数泄漏列名: " + s);
            assertFalse(s.contains("998877"),       "写入参数泄漏值: " + s);
            assertFalse(s.contains("secret_table"), "写入参数泄漏表名: " + s);
            assertFalse(s.contains("user-text-question"), "写入参数泄漏 question 原文: " + s);
        }
        // slot 3 = sql_digest，须 64 字符 sha-256 hex
        String digest = (String) a[3];
        assertNotNull(digest);
        assertEquals(64, digest.length(), "sha-256 hex 长 64，实际 " + digest.length());
        assertTrue(digest.matches("[0-9a-f]{64}"), "digest 非合法 sha-256 hex: " + digest);
        assertFalse(digest.contains("secret"), "digest 出现原文片段（摘要回反射）");
        // slot 4 = row_count = 42
        assertEquals(42, a[4]);
    }

    /** digest 稳定性 & 空归 null（V212 sql_digest 列允许 NULL）。 */
    @Test
    void digestIsStableForSameInputAndNullForBlank() {
        assertEquals(QueryHistoryService.sha256Hex("Q1"), QueryHistoryService.sha256Hex("Q1"));
        assertFalse(QueryHistoryService.sha256Hex("Q1").equals(QueryHistoryService.sha256Hex("Q2")),
            "不同输入生成相同 sha-256 摘要 = 极异异常");
        assertNull(QueryHistoryService.sha256Hex(null));
        assertNull(QueryHistoryService.sha256Hex(""));
        assertNull(QueryHistoryService.sha256Hex("   "));
    }

    /** MDC traceId 贯通：无 MDC → NULL；有 MDC 值 → 原样落 trace_id slot。 */
    @Test
    void saveCarriesTraceIdFromMdc() {
        AtomicReference<Object[]> argsCap = new AtomicReference<>();
        stubSaveUpdate(null, argsCap);

        service.save("q", "SELECT 1", 1);
        assertNull(argsCap.get()[1], "无 MDC traceId 时 trace_id slot 应 NULL");

        TraceContext.put("7d0f-a1b2-c3d4");
        try {
            service.save("q2", "SELECT 2", 2);
        } finally {
            TraceContext.clear();
        }
        assertEquals("7d0f-a1b2-c3d4", argsCap.get()[1], "MDC traceId 应原样落 trace_id slot");
    }

    /** subject_id 双分支：无 SecurityContext → "anonymous"；已认证 principal → principal（NOT NULL 约束满足）。 */
    @Test
    void saveSubjectIdFallsBackToAnonymousAndUsesPrincipal() {
        // ① 分支 A：无认证主体 → 兜底 anonymous（单元契约：currentSubjectId 单源）
        assertEquals("anonymous", QueryHistoryService.currentSubjectId(),
            "无主体上下文 subject 应兜底 anonymous");

        // ② 分支 B：已认证 principal（2 参 TestingAuthenticationToken 是未认证的，须用 3 参带 authorities 才 isAuthenticated()=true）
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user-4711", "ignored", List.of()));
        try {
            assertEquals("user-4711", QueryHistoryService.currentSubjectId(), "有已认证 principal 时 subject 应取 principal");
        } finally {
            SecurityContextHolder.clearContext();
        }

        // ③ 落库路径回查：save 的 subject_id 槽（slot 2）确由 currentSubjectId() 填充
        AtomicReference<Object[]> argsCap = new AtomicReference<>();
        stubSaveUpdate(null, argsCap);
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user-8890", "ignored", List.of()));
        try {
            service.save("q2", "SELECT 2", 1);
            assertEquals("user-8890", argsCap.get()[2], "save 落库 subject 槽应取已认证 principal");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ─── 切断线 fail-soft 语义（V212 未实跑期间的可观测降级形态）────────────────

    @Test
    void saveIsFailSoftWhenBackendUnavailable() {
        doThrow(new NoTableDataAccessException("relation does not exist: public.ecos_scenario_query_history"))
            .when(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any());

        QueryRecord r = assertDoesNotThrow(() -> service.save("q", "SELECT 1", 1));
        assertNotNull(r, "fail-soft 应返回已 id 化记录");
        assertNotNull(r.getId());
        assertEquals(36, r.getId().length(), "id 应为 36 字符 UUID");
    }

    @Test
    void getHistoryIsFailSoftWhenBackendUnavailable() {
        doThrow(new NoTableDataAccessException("relation does not exist: public.ecos_scenario_query_history"))
            .when(jdbc).query(anyString(), any(org.springframework.jdbc.core.RowCallbackHandler.class), (Object[]) any());

        List<QueryRecord> out = assertDoesNotThrow(() -> service.getHistory(20));
        assertTrue(out.isEmpty(), "backend 不可用时 getHistory 应降级空清单");
    }

    @Test
    void deleteIsSoftDeleteNotHardDelete() {
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        // delete 传 2 个变长槽位 (update_by, id)
        when(jdbc.update(anyString(), (Object) any(), (Object) any())).thenReturn(1);

        assertTrue(service.delete("some-id-36c"));
        verify(jdbc, times(1)).update(sqlCap.capture(), (Object) any(), (Object) any());
        String sql = sqlCap.getValue();
        assertTrue(sql.startsWith("UPDATE public.ecos_scenario_query_history"), "delete 应 UPDATE: " + sql);
        assertTrue(sql.contains("is_deleted = 1"), "软删须置 is_deleted=1: " + sql);
        assertFalse(sql.toUpperCase().contains("DELETE FROM"), "禁硬删: " + sql);
        assertTrue(sql.contains("WHERE id = ? AND is_deleted = 0"), "delete 应对已删记录幂等: " + sql);
    }

    @Test
    void clearIsSoftDeleteAllNotHardDelete() {
        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        // clear 传 1 个变长槽位 (update_by)
        when(jdbc.update(anyString(), (Object) any())).thenReturn(3);

        service.clear();
        verify(jdbc, times(1)).update(sqlCap.capture(), (Object) any());
        String sql = sqlCap.getValue();
        assertTrue(sql.startsWith("UPDATE public.ecos_scenario_query_history"), "clear 应 UPDATE: " + sql);
        assertTrue(sql.contains("is_deleted = 1"), "clear 应置 is_deleted=1: " + sql);
        assertFalse(sql.toUpperCase().contains("DELETE FROM"), "clear 禁硬删: " + sql);
        assertTrue(sql.contains("WHERE is_deleted = 0"), "clear 应保持幂等（跳过已删）: " + sql);
    }

    /** 定位 QueryHistoryService 源文件（surefire CWD=模块 或 IDE CWD=仓库根 均兼容）。 */
    private static Path selfSourcePath() {
        Path repoRoot = Paths.get("").toAbsolutePath();
        while (repoRoot != null && repoRoot.toFile().exists()
                && !Files.exists(repoRoot.resolve("ecos_backend"))) {
            repoRoot = repoRoot.getParent();
        }
        if (repoRoot == null) return null;
        Path p = repoRoot.resolve(
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/QueryHistoryService.java");
        return Files.isRegularFile(p) ? p : null;
    }

    /** 36 字符合成 id helper（测试用；不新增 UUID gen 单源）。 */
    private static String id36(String seed) {
        StringBuilder sb = new StringBuilder(seed);
        java.util.Random r = new java.util.Random(seed.hashCode());
        while (sb.length() < 35) sb.append((char) ('a' + r.nextInt(26)));
        sb.append((char) ('8' + (int) seed.charAt(0) % 9));
        String s = sb.toString();
        return s.length() > 36 ? s.substring(0, 36) : s;
    }

    /** DataAccessException 抽象，取具体子类用于切断线 fail-soft 桩（关系不存在/连不上库）。 */
    static class NoTableDataAccessException extends DataAccessException {
        NoTableDataAccessException(String msg) { super(msg); }
    }
}
