package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.dao.AuditDao;
import com.chinacreator.gzcm.engine.security.service.AuditHashChainService;
import com.chinacreator.gzcm.engine.security.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.sysman.audit.service.IAuditLogService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * #146ab 附录A 第 6 条 {@code GET /api/v1/security/audit/{id}}（getAuditLogDetail）契约门。
 *
 * <p>判读口径：本批落码＝契约路径逐字 {@code /api/v1/security/audit/{id}}（与既有
 * {@link AuditController} 的 {@code /logs}、{@code /stats} 等字面量叶段<b>同前缀重叠</b>，
 * 靠 Spring「字面量段优先于变量段」的特异性判序共存，非正交 — 见
 * {@link #literalSegmentWinsOverVariableSegment()}）＋真读 {@code public.ecos_audit_log}
 *（V295 锚定 16 列，{@link AuditDao#findAuditDetailById(long)} 单源）。载荷三键
 * prevHash / currentHash / detailJson 分别真读 prev_hash / curr_hash / detail 列。</p>
 *
 * <p><b>状态面判定（D-B4-59 第三族＝端点整体折成 {@code ResponseEntity}）</b>：
 * 本门走<b>行为面</b>——调公开端点 {@link AuditDetailController#getDetail(String)}
 * 读 {@code getStatusCode()}，验到的是<b>真实 HTTP 状态行</b>，不看信封 code
 * （文本棘轮门 {@code SecurityDenialHttpStatusRatchetTest} 判据 A／B 对「折成
 * {@code ok(...)}」的折叠变异看不见，只有行为门看得见）。本是「半只眼」互补
 * （承 #142/B4-3e-35／3e-37 口径）：
 * <ul>
 *   <li>200 正例：三键 prevHash / currentHash / detailJson 真读对应列，
 *       未打戳列如实 null（不伪装 0）</li>
 *   <li>400 {@code AUDIT_ID_FORMAT_INVALID}：id 非数字 / 负数</li>
 *   <li>403 {@code PERMISSION_DENIED}：无已鉴别主体（L2 fail-closed）</li>
 *   <li>404 {@code AUDIT_LOG_NOT_FOUND}：库面 0 行（不存在才 404）</li>
 *   <li>500 {@code AUDIT_DETAIL_READ_ERROR}：依赖在位但 DAO 抛异常（不折 404）</li>
 *   <li>503 承流：依赖双 provider 缺席 ⇒ {@link DataBridgeException} carry
 *       {@code SEC_UNAVAILABLE_HTTP} ＋ {@code ECOS-SYS-503}（D-B4-86 同源族）</li>
 * </ul>
 * 变异＝改任一路径前缀／键名／状态行／机读码即红。</p>
 *
 * <p>不连库：以 {@link StubDao}（只覆写本端点达到的 {@code findAuditDetailById}）＋
 * Mockito mock {@link ObjectProvider}（在位时返非 null mock / 缺席时返 null）。</p>
 */
class AuditDetailControllerContractTest {

    // ── Mockito ObjectProvider 替身（仅在位判定面不触 DB）────────────
    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> stubPresent(Class<T> iface) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> p = (ObjectProvider<T>) mock(ObjectProvider.class);
        T bean = (T) mock(iface);
        when(p.getIfAvailable()).thenReturn(bean);
        when(p.getIfUnique()).thenReturn(bean);
        return p;
    }

    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> stubAbsent() {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> p = (ObjectProvider<T>) mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        when(p.getIfUnique()).thenReturn(null);
        return p;
    }

    // ── 替身 AuditDao（不连库，只覆写本端点会调到的一枚读腿）────────
    static class StubDao extends AuditDao {
        final Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        volatile boolean throwOnRead = false;
        StubDao() { /* super() 默认构造 */ }
        @Override public Map<String, Object> findAuditDetailById(long id) {
            if (throwOnRead) throw new RuntimeException("simulated DB down (not a 404)");
            return rows.get(id);
        }
    }

    private AuditDetailController presentCtl(StubDao dao) {
        return new AuditDetailController(
                stubPresent(IAuditLogService.class), stubPresent(AuditHashChainService.class), dao);
    }

    private void inRequest(Runnable body) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        try { body.run(); } finally { RequestContextHolder.resetRequestAttributes(); }
    }

    /**
     * 建立已鉴别主体（L2 门禁的正面侧）。本端点的 403 腿先于任何参数校验与 DAO 读腿，
     * 故除「无主体 ⇒ 403」与遮蔽判序两枚外，其余行为腿必须先在此落 {@link UserContext}
     * ——首跑实测正是以 6 枚「期望 400/404/500/200 实得 403」把这条判定顺序钉出来的。
     */
    private static void authenticate() {
        UserContext context = new UserContext();
        context.setUserId("u-contract-01");
        context.setUsername("contract-tester");
        UserContext.setCurrent(context);
    }

    @AfterEach
    void clearSubject() {
        UserContext.clear();
    }

    // ── ① 路径锚：契约路径逐字 ─────────────────────────────────────────

    @Test
    @DisplayName("路径锚：类级 /api/v1/security + 方法级 /audit/{id} 逐字在位")
    void contractPathAnchored() {
        Class<?> c = AuditDetailController.class;
        var classMap = c.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(classMap, "类级 @RequestMapping 必须在位");
        assertTrue(Arrays.asList(classMap.value()).contains("/api/v1/security"),
                "类级前缀必须含 /api/v1/security，实际=" + Arrays.toString(classMap.value()));

        List<String> auditGetPaths = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            var gm = m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (gm != null) {
                auditGetPaths.addAll(Arrays.asList(gm.value().length > 0 ? gm.value() : gm.path()));
            }
        }
        assertTrue(auditGetPaths.contains("/audit/{id}"),
                "@GetMapping 必须含逐字 /audit/{id}，实际=" + auditGetPaths);
    }

    // ── ② 503 承流：双 provider 全缺席 ⇒ DataBridgeException 携带态 ─────

    @Test
    @DisplayName("503 承流：双 provider 全缺席 ⇒ doRead 抛 DataBridgeException 携带 SEC_UNAVAILABLE_HTTP")
    void unavailableCarriesSys503() {
        AuditDetailController ctl = new AuditDetailController(
                stubAbsent(), stubAbsent(), new StubDao());
        inRequest(() -> {
            DataBridgeException ex = assertThrows(DataBridgeException.class, () -> ctl.doRead("42"));
            assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, ex.getHttpStatus(),
                    "承流状态行必须取 RlsExecutionContract.SEC_UNAVAILABLE_HTTP 单源");
            assertEquals(DataBridgeException.resolveHttpStatus(RlsExecutionContract.SEC_UNAVAILABLE_HTTP),
                    503, "承流 503 经 resolveHttpStatus 单源推出必须回落 503（口径自检）");
            assertTrue(ex.getMessage().contains("fail-closed"), "消息须表明 fail-closed 承流");
            assertTrue(ex.getMessage().contains(AuditDetailController.CODE_UNAVAILABLE),
                    "承流消息必须携带机读码单源 ECOS-SYS-503");
            assertEquals("ECOS-SYS-503", AuditDetailController.CODE_UNAVAILABLE,
                    "机读码单源常量必须等于 ECOS-SYS-503（变异＝改常量即红）");
        });
    }

    // ── ③ 400 格式（行为面读真实状态行）────────────────────────────────

    @Test
    @DisplayName("400 格式：id 非数字 ⇒ 状态行 400 + 信封 AUDIT_ID_FORMAT_INVALID（不触 DAO）")
    void nonNumericIdIsBadRequest() {
        StubDao dao = new StubDao();
        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(dao).getDetail("not-a-number");
            assertEquals(400, resp.getStatusCode().value(), "非数字 id 状态行必须 400");
            assertEquals(AuditDetailController.ERR_ID_FORMAT, resp.getBody().getErrorCode());
        });
    }

    @Test
    @DisplayName("400 格式：负数 id ⇒ 状态行 400 + 信封 AUDIT_ID_FORMAT_INVALID")
    void negativeIdIsBadRequest() {
        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(new StubDao()).getDetail("-5");
            assertEquals(400, resp.getStatusCode().value());
            assertEquals(AuditDetailController.ERR_ID_FORMAT, resp.getBody().getErrorCode());
        });
    }

    // ── ④ 404 缺行 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("404 缺行：数字 id 但库面 0 行 ⇒ 状态行 404 + 信封 AUDIT_LOG_NOT_FOUND")
    void missingRowIs404() {
        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(new StubDao()).getDetail("999");
            assertEquals(404, resp.getStatusCode().value(), "库面无该行状态行必须 404，不得折成 200");
            assertEquals(AuditDetailController.ERR_NOT_FOUND, resp.getBody().getErrorCode());
        });
    }

    // ── ⑤ 200 正例：三键 prevHash/currentHash/detailJson 真读对应列 ─────

    @Test
    @DisplayName("200 三键：状态行 200 + prevHash←prev_hash / currentHash←curr_hash / detailJson←detail")
    @SuppressWarnings("unchecked")
    void happyPathStatusLineAndKeysReadFromCorrectColumns() {
        StubDao dao = new StubDao();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("prev_hash", "PREV_HEX_A");
        row.put("curr_hash", "CURR_HEX_B");
        row.put("username", "approver-01");
        row.put("hash_algorithm", "SHA-256");
        row.put("detail", "{\"k\":\"v\"}");
        row.put("created_at", "2026-10-29 08:00:00");
        row.put("operation", "audit_read");
        row.put("action", "audit_read");
        row.put("resource", "secret-key-1");
        row.put("entity_type", "crypto_key");
        row.put("entity_id", "key-01");
        row.put("user_id", "u-100");
        row.put("result", "SUCCESS");
        dao.rows.put(7L, row);

        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(dao).getDetail("7");
            assertEquals(200, resp.getStatusCode().value(), "真读成功状态行必须 200（显式 ok 腿）");
            ApiResponse<?> body = resp.getBody();
            assertEquals(0, body.getCode(), "成功腿信封 code 必须 CODE_SUCCESS（HTTP 状态行另走显式 200）");
            Map<String, Object> data = (Map<String, Object>) body.getData();
            assertEquals("PREV_HEX_A", data.get("prevHash"), "prevHash 必须真读 prev_hash 列");
            assertEquals("CURR_HEX_B", data.get("currentHash"), "currentHash 必须真读 curr_hash 列");
            assertEquals("{\"k\":\"v\"}", data.get("detailJson"), "detailJson 必须真读 detail 列");
            assertEquals("SHA-256", data.get("hashAlgorithm"));
            assertEquals("approver-01", data.get("approvedBy"));
            assertEquals("2026-10-29 08:00:00", data.get("occurredAt"));
            assertEquals("u-100", data.get("userId"));
            assertEquals("SUCCESS", data.get("result"));
            assertNotNull(data.get("id"));
        });
    }

    @Test
    @DisplayName("200 缺打戳：prev_hash/curr_hash/detail 全 null 即如实直读 null，不伪装 0")
    @SuppressWarnings("unchecked")
    void nullColumnsReadHonestNotPadded() {
        StubDao dao = new StubDao();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 8L);
        row.put("created_at", "2026-10-29 09:00:00");
        row.put("operation", "op");
        dao.rows.put(8L, row);
        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(dao).getDetail("8");
            assertEquals(200, resp.getStatusCode().value());
            Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
            assertNull(data.get("prevHash"), "prev_hash 未打戳必须如实 null");
            assertNull(data.get("currentHash"), "curr_hash 未打戳必须如实 null");
            assertNull(data.get("detailJson"), "detail 未打戳必须如实 null");
        });
    }

    @Test
    @DisplayName("500 读腿异常：依赖在位但 DAO 抛异常 ⇒ 状态行 500 + 信封 AUDIT_DETAIL_READ_ERROR（非 404）")
    void daoThrowsIs500Not404() {
        StubDao dao = new StubDao();
        dao.throwOnRead = true;
        authenticate();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(dao).getDetail("10");
            assertEquals(500, resp.getStatusCode().value(), "读腿异常状态行必须 500，不得折成 404/200");
            assertEquals(AuditDetailController.ERR_READ, resp.getBody().getErrorCode());
        });
    }

    @Test
    @DisplayName("403 无主体：UserContext 无已鉴别主体 ⇒ 状态行 403 + 信封 PERMISSION_DENIED（L2 fail-closed）")
    void unauthenticatedSubjectsGet403() {
        UserContext.clear();
        inRequest(() -> {
            ResponseEntity<ApiResponse<?>> resp = presentCtl(new StubDao()).getDetail("7");
            assertEquals(403, resp.getStatusCode().value(), "无已鉴别主体状态行必须 403（fail-closed）");
            assertEquals(AuditDetailController.ERR_PERMISSION, resp.getBody().getErrorCode());
            ResponseEntity<ApiResponse<?>> shapely = presentCtl(new StubDao()).getDetail("not-a-number");
            assertEquals(403, shapely.getStatusCode().value(),
                    "L2 门禁先于参数校验：无主体时即便 id 形态也非法，仍必须报 403 而非 400——"
                            + "否则未鉴别调用方可用状态行差探测校验顺序（信息泄露面）");
        });
    }

    // ── 静态助手面（失败腿经 failure 单源推导状态行）─────────────────────

    @Test
    @DisplayName("静态助手：permissionDenied=403/PERMISSION_DENIED，notFound=404，idInvalid=400（真实状态行）")
    void staticHelpersCarryStatusLineAndEnvelopeCodes() {
        ResponseEntity<ApiResponse<?>> p = AuditDetailController.permissionDenied();
        assertEquals(403, p.getStatusCode().value(), "403 状态行必须由 failure 单源推出，不得折回 200");
        assertEquals(403, p.getBody().getCode());
        assertEquals("PERMISSION_DENIED", p.getBody().getErrorCode());
        ResponseEntity<ApiResponse<?>> nf = AuditDetailController.notFound("42");
        assertEquals(404, nf.getStatusCode().value());
        assertEquals(404, nf.getBody().getCode());
        assertEquals("AUDIT_LOG_NOT_FOUND", nf.getBody().getErrorCode());
        ResponseEntity<ApiResponse<?>> bad = AuditDetailController.idInvalid("x");
        assertEquals(400, bad.getStatusCode().value());
        assertEquals(400, bad.getBody().getCode());
        assertEquals("AUDIT_ID_FORMAT_INVALID", bad.getBody().getErrorCode());
    }

    @Test
    @DisplayName("ok/failure 单源：成功腿显式 200（CODE_SUCCESS 不过 resolveHttpStatus），失败腿状态行从信封 code 推出")
    void okAndFailureDeriveTheStatusLineFromTheEnvelopeCode() {
        ApiResponse<Void> ok = ApiResponse.success(null);
        assertEquals(0, ok.getCode(), "成功信封 code 恒 CODE_SUCCESS=0");
        assertEquals(200, AuditDetailController.ok(ok).getStatusCode().value(),
                "成功腿必须显式 200（不经 resolveHttpStatus——0 ∉ [400,599] 会被折成 500）");
        assertEquals(500, DataBridgeException.resolveHttpStatus(ok.getCode()),
                "口径自证：成功码 0 若误走 resolveHttpStatus 即被折成 500＝本类选择显式 ok 的理由");
        assertEquals(200, AuditDetailController.ok(AuditDetailController.notFound("1").getBody())
                .getStatusCode().value(),
                "本枚是<b>危险面自证</b>而非成功判据：ok(..) 只看调用点、不读信封 code，"
                        + "把 404 体误包进 ok(..) 就得到 200＋AUDIT_LOG_NOT_FOUND 的折叠态。"
                        + "这正是失败腿必须一律走 failure(..)、且属主源码零枚裸回的原因（见 "
                        + "ownerSourceHasNoBareEnvelopeReturn 与两枚 common-api 横切门）");
        assertEquals(404, AuditDetailController.failure(AuditDetailController.notFound("1").getBody())
                .getStatusCode().value(), "404 状态行必须从信封 code 单源推出");
        assertEquals(403, AuditDetailController.failure(AuditDetailController.permissionDenied().getBody())
                .getStatusCode().value(), "403 状态行必须从信封 code 单源推出");
        assertEquals(400, AuditDetailController.failure(AuditDetailController.idInvalid("x").getBody())
                .getStatusCode().value(), "400 状态行必须从信封 code 单源推出");
        // 口径自检：resolveHttpStatus 对 [400,599] 恒等，本端点失败腿的状态行 ≡ 信封 code。
        assertEquals(404, DataBridgeException.resolveHttpStatus(404));
    }

    @Test
    @DisplayName("分态可分辨：403/400/404/500 四种失败状态行互不重合，且均 ≠ 200")
    void statusStatesDistinct() {
        List<Integer> states = List.of(
                AuditDetailController.permissionDenied().getStatusCode().value(),
                AuditDetailController.idInvalid("x").getStatusCode().value(),
                AuditDetailController.notFound("1").getStatusCode().value(),
                500);
        assertEquals(4, new java.util.HashSet<>(states).size(),
                "各分态状态行必须互不重合（403/400/404/500）");
        assertTrue(!states.contains(200), "失败态不得与成功态 200 同形");
    }

    @Test
    @DisplayName("属主源码零枚 return ApiResponse. 裸回出口：状态行只能经 ok/failure 单源出")
    void ownerSourceHasNoBareEnvelopeReturn() {
        // 判据 A/B（SecurityDenialHttpStatusRatchetTest）锚 `return\s+ApiResponse\.` 逐行匹配；
        // 本枚把「不新增裸回持有者」从横切门读数前移到属主行为门，回归即在本域变红而非只在 common-api 红。
        // 「读原文逐行」在本域首跑即红过一次：类 Javadoc 里描述该禁形的 {@code return ApiResponse.}
        // 字面量被当成代码命中——故与两枚横切门同法先经 SourceCommentStripper 剥注释（D-B4-40 同族盲区）。
        String src = AuditDetailController.class.getName();
        java.nio.file.Path file = java.nio.file.Paths.get("src/main/java/"
                + src.replace('.', '/') + ".java");
        assertTrue(java.nio.file.Files.exists(file),
                "属主源码路径解析失败（cwd 应为 security-engine-impl 模块目录）: " + file.toAbsolutePath());
        String code;
        try {
            code = SourceCommentStripper.strip(java.nio.file.Files.readString(file));
        } catch (java.io.IOException e) {
            fail("读取属主源码失败: " + e);
            return;
        }
        List<String> bare = code.lines()
                .filter(l -> l.matches(".*\\breturn\\s+ApiResponse\\s*\\..*"))
                .toList();
        assertEquals(List.of(), bare,
                "属主侧出现裸回信封出口＝状态行被折回恒 200（D-B4-59 第三族回退）:\n"
                        + String.join("\n", bare));
        // 正向锚：禁形消失不得由「把腿删了」换来——四枚失败腿与一枚成功腿的 helper 出口必须在位。
        assertEquals(4, code.lines()
                .filter(l -> l.contains("return failure(ApiResponse."))
                .count(),
                "403/400/404/500 四枚失败腿各出一枚 failure(...) 单源出口；少一枚＝该分态被摘腿或改写");
        assertTrue(code.contains("return ok(ApiResponse.success(data))"),
                "成功腿的显式 ok 出口必须在位（缺它即成功面被摘）");
    }

    // ── ⑥ 遮蔽判序：契约路径 /audit/{id} 与同前缀字面量端点重叠，靠特异性共存 ──

    /**
     * 形状探针：复刻既有 {@link AuditController} 的类级双前缀 ＋ {@code /logs}、{@code /stats}
     * 字面量叶段形态。真类依赖（多枚 service/DAO Bean）使它在本门不可构造，故判序证据取自
     * 同形态探针，而<b>形态本身</b>由 {@link #realHostMappingShapeIsShadowed()} 从真类注解上钉死
     * ——真类改了映射，本探针即失配（两枚必须同批改）。
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping({"/api/v1/audit", "/api/v1/security/audit"})
    static class ShadowProbeController {
        @GetMapping("/logs")
        public String logs() {
            return "PROBE-LOGS";
        }

        @GetMapping("/stats")
        public String stats() {
            return "PROBE-STATS";
        }
    }

    @Test
    @DisplayName("真宿主形态锚：AuditController 类级含 /api/v1/security/audit 且带 /logs+/stats 字面量＝遮蔽面存在的事实依据")
    void realHostMappingShapeIsShadowed() {
        var classMap = AuditController.class
                .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertNotNull(classMap, "既有宿主类级 @RequestMapping 必须在位");
        assertTrue(Arrays.asList(classMap.value()).contains("/api/v1/security/audit"),
                "既有宿主与本类同前缀 /api/v1/security/audit ⇒ 重叠面真实存在，实际="
                        + Arrays.toString(classMap.value()));

        List<String> literals = new ArrayList<>();
        for (Method m : AuditController.class.getDeclaredMethods()) {
            var gm = m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (gm != null) {
                Arrays.stream(gm.value().length > 0 ? gm.value() : gm.path())
                        .filter(p -> !p.contains("{"))
                        .forEach(literals::add);
            }
        }
        assertTrue(literals.contains("/logs") && literals.contains("/stats"),
                "本探针的 /logs、/stats 两条字面量叶段必须与真宿主同形，真宿主实测字面 GET=" + literals);
    }

    @Test
    @DisplayName("遮蔽判序：GET /audit/logs、/audit/stats 判给字面量宿主；GET /audit/42 才落本类（无主体⇒403）")
    void literalSegmentWinsOverVariableSegment() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(presentCtl(new StubDao()), new ShadowProbeController())
                .build();

        MvcResult logs = mvc.perform(MockMvcRequestBuilders.get("/api/v1/security/audit/logs"))
                .andReturn();
        assertEquals(200, logs.getResponse().getStatus(), "/audit/logs 由字面量宿主服务⇒200");
        assertEquals("PROBE-LOGS", logs.getResponse().getContentAsString(),
                "/audit/logs 的响应体必须是探针自己的字符串，不得是本类的信封");
        assertEquals(ShadowProbeController.class,
                ((org.springframework.web.method.HandlerMethod) logs.getHandler()).getBeanType(),
                "/audit/logs 必须由字面量宿主服务——若落进本类即契约路径把既有列表端点吃掉（只增改红线破口）");

        MvcResult stats = mvc.perform(MockMvcRequestBuilders.get("/api/v1/security/audit/stats"))
                .andReturn();
        assertEquals(200, stats.getResponse().getStatus(), "/audit/stats 由字面量宿主服务⇒200");
        assertEquals("PROBE-STATS", stats.getResponse().getContentAsString());
        assertEquals(ShadowProbeController.class,
                ((org.springframework.web.method.HandlerMethod) stats.getHandler()).getBeanType(),
                "/audit/stats 同上");

        MvcResult variable = mvc.perform(MockMvcRequestBuilders.get("/api/v1/security/audit/42"))
                .andReturn();
        assertEquals(403, variable.getResponse().getStatus(),
                "/audit/42 落本类且无已鉴别主体⇒真实 403 状态行（非 200 信封里的失败码）");
        org.springframework.web.method.HandlerMethod hm =
                (org.springframework.web.method.HandlerMethod) variable.getHandler();
        assertEquals(AuditDetailController.class, hm.getBeanType(),
                "/audit/42 必须落到本类 getDetail（403 是无已鉴别主体的 L2 fail-closed，同时是处理器身份判据）");
        assertEquals("getDetail", hm.getMethod().getName());
    }
}
