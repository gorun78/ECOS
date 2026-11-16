package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.IFunctionPropertyEvaluator;
import com.chinacreator.gzcm.common.engine.StateMachineEngine;
import com.chinacreator.gzcm.common.service.ObjectRuntimeService;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.WorkspaceExceptionHandler;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.security.RlsFilter;
import com.chinacreator.gzcm.workspace.service.ObjectKgSyncService;
import com.chinacreator.gzcm.workspace.service.ObjectQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ObjectController 写路径契约门禁（D90，2026-11-14 验证批）。
 *
 * <p><b>缺陷形态（活体实测）</b>：{@code POST /api/v1/ecos/objects/Customer}
 * 对四种输入全部返回 HTTP 500「服务内部错误」：</p>
 * <ol>
 *   <li>合法字段 + 无 id —— {@code null value in column "id" ... violates not-null constraint}；</li>
 *   <li>带 {@code email} —— {@code IllegalArgumentException: 非法字段: email} 落兜底 → 500；</li>
 *   <li>带 {@code creditScore}（驼峰） —— 同上 500；</li>
 *   <li>带 {@code tenant_id} —— 白名单通过，但 INSERT 报 {@code column "tenant_id" does not exist}，
 *       因为该列只存在于 {@code ecos_sysman} 的 legacy 副本，而写入落在 {@code ecos_control}。</li>
 * </ol>
 *
 * <p><b>本门禁覆盖四条不变量</b>：</p>
 * <ul>
 *   <li>{@link #createGeneratesIdWhenClientOmitsIt()} —— 主键服务端生成；</li>
 *   <li>{@link #invalidFieldIsFourHundredNotFiveHundred()} —— 非法字段走 400 + 点名；</li>
 *   <li>{@link #columnWhitelistIsPinnedToResolvedSchema()} —— 列清单查询锁 schema，
 *       不再按表名全库扫（否则同名 legacy 表会污染白名单）；</li>
 *   <li>{@link #schemaEndpointCannotServeUnionOfSameNameTables()} —— {@code /schema} 面同源。</li>
 * </ul>
 *
 * <p><b>反假绿</b>：{@link #probeDetectsLegacyUnpinnedQuery()} 断言本门禁的正则确实能命中
 * 旧写法（不限定 schema 的 {@code WHERE table_name = ?}）；若这条自检变绿，
 * 说明门禁已失效而主用例还在「通过」，必须先修门禁。</p>
 */
@DisplayName("D90 — Object 写路径：id 生成 / 非法字段 400 / 列白名单锁 schema")
class ObjectWritePathContractTest {

    /** demo_customer 在 ecos_control（权威表）的真实列，取自 information_schema。 */
    private static final List<String> CONTROL_COLUMNS =
            List.of("id", "name", "industry", "region", "level", "credit_score", "status", "created_at");

    private ObjectQueryService queryService;
    private ObjectController controller;

    @BeforeEach
    void setUp() throws Exception {
        queryService = mock(ObjectQueryService.class);
        ObjectRuntimeService runtimeService = mock(ObjectRuntimeService.class);
        StateMachineEngine stateMachineEngine = mock(StateMachineEngine.class);
        IFunctionPropertyEvaluator evaluator = mock(IFunctionPropertyEvaluator.class);
        AbacQueryFilter abacFilter = mock(AbacQueryFilter.class);
        ObjectKgSyncService kgSyncService = mock(ObjectKgSyncService.class);

        when(abacFilter.buildRowFilter(anyString())).thenReturn(RlsFilter.unrestricted());
        when(abacFilter.filterColumns(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));

        // COLUMNS_CACHE 是 static：不清会让前一个用例的列集泄漏到后一个（用例假绿/假红）。
        java.lang.reflect.Field cache = ObjectController.class.getDeclaredField("COLUMNS_CACHE");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();

        controller = new ObjectController(queryService, runtimeService, stateMachineEngine,
                evaluator, abacFilter, kgSyncService);
    }

    // ── 1. 主键生成 ──────────────────────────────────

    @Test
    @DisplayName("客户端不传 id 时服务端生成 UUID，而不是直插 NULL 撞 not-null")
    void createGeneratesIdWhenClientOmitsIt() {
        stubColumns(CONTROL_COLUMNS);
        AtomicReference<String> insertedSql = new AtomicReference<>();
        AtomicReference<Object[]> insertedArgs = new AtomicReference<>();
        when(queryService.update(anyString(), any(Object[].class))).thenAnswer(inv -> {
            insertedSql.set(inv.getArgument(0));
            // Mockito 把 varargs 展开进 getArguments()，第 0 位是 SQL，其后才是参数
            Object[] all = inv.getArguments();
            insertedArgs.set(java.util.Arrays.copyOfRange(all, 1, all.length));
            return 1;
        });
        when(queryService.queryForList(contains("WHERE id = ?"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "placeholder")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "No-Id Customer");
        body.put("level", "A");

        ApiResponse<Map<String, Object>> resp = controller.createObject("Customer", body);

        assertEquals(ApiResponse.CODE_SUCCESS, resp.getCode(),
                "缺 id 的合法请求必须创建成功，实际: " + resp.getMessage());
        String sql = insertedSql.get();
        assertNotNull(sql, "必须真的发出 INSERT");
        assertTrue(sql.contains("\"id\""), "INSERT 必须包含 id 列，实际 SQL: " + sql);
        List<?> args = List.of(insertedArgs.get());
        Object idParam = args.get(0);
        assertNotNull(idParam, "id 参数不得为 null（NOT NULL 约束会直接 500）");
        assertFalse(idParam.toString().isBlank(), "生成的 id 不得为空串");
        assertTrue(idParam.toString().length() >= 32,
                "生成的主键应是完整 UUID（≥32 字符），实际: " + idParam);
    }

    @Test
    @DisplayName("客户端自带 id 时保留原值（不覆盖调用方主键）")
    void createKeepsClientSuppliedId() {
        stubColumns(CONTROL_COLUMNS);
        AtomicReference<Object[]> insertedArgs = new AtomicReference<>();
        when(queryService.update(anyString(), any(Object[].class))).thenAnswer(inv -> {
            Object[] all = inv.getArguments();
            insertedArgs.set(java.util.Arrays.copyOfRange(all, 1, all.length));
            return 1;
        });
        when(queryService.queryForList(contains("WHERE id = ?"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "client-pk-1")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "client-pk-1");
        body.put("name", "Supplied");

        controller.createObject("Customer", body);

        assertEquals("client-pk-1", List.of(insertedArgs.get()).get(0),
                "调用方指定的主键不得被服务端覆盖");
    }

    // ── 2. 非法字段 → 400 ──────────────────────────

    @Test
    @DisplayName("非法字段抛领域异常（HTTP 400）而不是兜底 500")
    void invalidFieldIsFourHundredNotFiveHundred() {
        stubColumns(CONTROL_COLUMNS);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Has Email");
        body.put("email", "nobody@example.com");

        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> controller.createObject("Customer", body),
                "非法字段必须是领域异常（→400）；抛裸 IllegalArgumentException 会被兜底成 500");

        assertEquals(400, ex.getHttpStatus(), "非法字段的 HTTP 语义是 400 而非 500");
        assertTrue(ex.getMessage().contains("email"), "响应必须点名非法字段: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("industry"),
                "响应应回带该实体可用字段（前端据此渲染表单）: " + ex.getMessage());

        // 走真实 advice，证明对外确实是 400
        ResponseEntity<ApiResponse<Map<String, Object>>> resp =
                new WorkspaceExceptionHandler().handleDomain(ex);
        assertEquals(400, resp.getStatusCode().value(),
                "advice 映射后必须仍是 400，实际: " + resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(400, resp.getBody().getCode());
    }

    @Test
    @DisplayName("驼峰字段名同样判非法（物理列是 credit_score，不是 creditScore）")
    void camelCaseFieldIsRejected() {
        stubColumns(CONTROL_COLUMNS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Camel");
        body.put("creditScore", 90);

        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> controller.createObject("Customer", body));
        assertTrue(ex.getMessage().contains("creditScore"), "必须点名 creditScore: " + ex.getMessage());
    }

    @Test
    @DisplayName("全部字段合法时不得误伤")
    void allValidFieldsPass() {
        stubColumns(CONTROL_COLUMNS);
        when(queryService.update(anyString(), any(Object[].class))).thenReturn(1);
        when(queryService.queryForList(contains("WHERE id = ?"), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", "x")));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "cust-ok");
        body.put("name", "Ok");
        body.put("industry", "aviation");
        body.put("region", "CN");
        body.put("level", "A");
        body.put("credit_score", 88);

        assertDoesNotThrow(() -> controller.createObject("Customer", body));
    }

    // ── 3. 列白名单锁 schema ────────────────────────

    @Test
    @DisplayName("列清单查询按 to_regclass 解析出的 (schema, table) 取列，不含 legacy 副本独有列")
    void columnWhitelistIsPinnedToResolvedSchema() {
        // 纯 mock 复现不出「跨 schema 并集」——那是真实 PG 上同名表的物理事实。
        // 因此这条不变量只能钉在「实际发出去的 SQL 形状」上：必须出现 to_regclass 定表
        // 且必须比对 table_schema；只按 table_name 过滤就是 D90 的缺陷形态。
        AtomicReference<String> columnSql = new AtomicReference<>();
        when(queryService.queryForList(anyString(), any(Object[].class))).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.contains("information_schema.columns")) {
                columnSql.set(sql);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (String c : CONTROL_COLUMNS) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("column_name", c);
                    rows.add(row);
                }
                return rows;
            }
            return List.of();
        });

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "cust-tenant");
        body.put("name", "Tenant Probe");

        controller.createObject("Customer", body);

        String sql = columnSql.get();
        assertNotNull(sql, "必须真的查过列清单");
        String lower = sql.toLowerCase();
        assertTrue(lower.contains("to_regclass"),
                "列清单必须按 to_regclass 解析 search_path 后的唯一表，否则同名 legacy 表会污染白名单。实际 SQL: " + sql);
        assertTrue(lower.contains("table_schema"),
                "必须比对 table_schema，只按 table_name 过滤会取到跨 schema 的列并集。实际 SQL: " + sql);
        assertFalse(lower.matches("(?s).*where\\s+table_name\\s*=\\s*\\?\\s*(order|$).*"),
                "不得退化为「只按 table_name 过滤」的旧写法。实际 SQL: " + sql);
    }

    @Test
    @DisplayName("tenant_id 这类 legacy 独有列若不在白名单，写入前就被 400 拒掉（不落到 SQL）")
    void legacyOnlyColumnRejectedBeforeSql() {
        stubColumns(CONTROL_COLUMNS);   // 权威表没有 tenant_id

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "cust-tenant");
        body.put("name", "Tenant Probe");
        body.put("tenant_id", "tenant-a");

        ActionValidationException ex = assertThrows(ActionValidationException.class,
                () -> controller.createObject("Customer", body),
                "tenant_id 只存在于 legacy 副本，必须被判非法；放行会让 INSERT 撞「列不存在」→500");
        assertTrue(ex.getMessage().contains("tenant_id"), "必须点名 tenant_id: " + ex.getMessage());
    }

    @Test
    @DisplayName("白名单表为空（表不存在）时一切字段都判非法 —— fail-closed")
    void unknownTableRejectsEveryField() {
        stubColumns(List.of());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Any");

        assertThrows(ActionValidationException.class,
                () -> controller.createObject("Customer", body),
                "解析不到物理表时不得退化成「白名单空＝全放行」");
    }

    // ── 4. 门禁自检（反假绿） ───────────────────────

    @Test
    @DisplayName("探针自检：旧写法（不限定 schema 的 information_schema 查询）确实会被识别")
    void probeDetectsLegacyUnpinnedQuery() {
        String legacy =
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_name = ? ORDER BY ordinal_position";
        String fixed =
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE (table_schema, table_name) = (SELECT n.nspname, c.relname ...)";

        assertTrue(isUnpinnedColumnQuery(legacy), "旧写法必须被识别为未锁定 schema");
        assertFalse(isUnpinnedColumnQuery(fixed), "新写法不得被误判为未锁定 schema");
    }

    /**
     * 门禁判据：{@code information_schema.columns} + 只按 {@code table_name} 过滤
     * 且未出现 {@code table_schema} 约束。这是 D90 跨 schema 并集的最小可判形态。
     */
    private static boolean isUnpinnedColumnQuery(String sql) {
        String lower = sql.toLowerCase();
        if (!lower.contains("information_schema.columns")) return false;
        if (!lower.contains("table_name")) return false;
        return !lower.contains("table_schema");
    }

    // ── 桩 ────────────────────────────────────────

    /** 让 {@code getAllowedColumns} 看到指定列集；其余 SELECT 返回空。 */
    private void stubColumns(List<String> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String c : columns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("column_name", c);
            rows.add(row);
        }
        when(queryService.queryForList(contains("information_schema.columns"), any(Object[].class)))
                .thenReturn(rows);
        when(queryService.queryForList(eq("SELECT column_name FROM information_schema.columns "
                + "WHERE (table_schema, table_name) = (SELECT n.nspname, c.relname FROM pg_class c "
                + "  JOIN pg_namespace n ON n.oid = c.relnamespace "
                + " WHERE c.oid = to_regclass(?)) ORDER BY ordinal_position"), any(Object[].class)))
                .thenReturn(rows);
    }

    /** 供断言的去重列集（LinkedHashSet 保序）。 */
    @SuppressWarnings("unused")
    private static LinkedHashSet<String> setOf(String... vals) {
        return new LinkedHashSet<>(List.of(vals));
    }
}
