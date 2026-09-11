package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqAutoRepairService;
import com.chinacreator.gzcm.engine.data.quality.DqWorkOrderService;
import com.chinacreator.gzcm.engine.data.quality.mapper.DqRuleMapper;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqKnowledgeEntryVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaRequest;
import com.chinacreator.gzcm.engine.data.quality.model.DqRcaResult;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;
import com.chinacreator.gzcm.engine.data.quality.report.DqReportServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.report.MinioReportStorage;
import com.chinacreator.gzcm.engine.data.quality.service.DqAlertServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqKnowledgeSinkServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqRcaServiceImpl;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;
import com.chinacreator.gzcm.runtime.core.alert.IAlertService;

/**
 * DqPhase4E2eIT — Phase 4 三 fixture E2E 集成测试（PMO-48-D T17）。
 *
 * <p>E2E 场景串联（纯 Mockito，不连 PG、不 new RestTemplate）：</p>
 * <ol>
 *   <li><b>Fixture 1 规则通过 → 评分快照 → 报告生成</b>
 *       ：mock 表数据行 + {@link MinioReportStorage}，调
 *       {@link DqReportServiceImpl#generateDaily(String)} 验证 DAILY 报告 VO
 *       （payloadHtml 含 DQ / 6 维 + LLM stub + 未走 MinIO）。</li>
 *   <li><b>Fixture 2 P0 告警 → 工单创建 → RCA 调 knowledgeSink</b>
 *       ：CRITICAL rule 触发 P0 分发 + 工单自动建 + audit 落库，紧接
 *       {@link DqRcaServiceImpl#runDiagnose(DqRcaRequest)} 验证 RAG 相似知识检索
 *       + cognitive 响应映射 rootCause / confidence。</li>
 *   <li><b>Fixture 3 报告 DB 持久化 → 知识 RAG 检索</b>
 *       ：alert 触发 {@link DqKnowledgeSinkServiceImpl#ingestFromAlert(String, DqAlertVO)}
 *       写 dq_knowledge_entry；随后 {@link DqKnowledgeSinkServiceImpl#searchSimilar(String, int)}
 *       返回 top N（≤ 3），embedding 字段非空。</li>
 * </ol>
 *
 * <p>禁止清单遵守：不真连 PG（全 mock JdbcTemplate）；构造器注入 + {@link ObjectProvider}；
 * 不 new 全局无超时 RestTemplate（Fixture 2 用 {@link Spy} 覆盖 {@code postForEntity}）。</p>
 *
 * @author PMO-48-D T17
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DqPhase4E2eIT {

    // ────────────────────────────────────────────────────────────────────
    // 各服务共享 mock 依赖
    // ────────────────────────────────────────────────────────────────────

    @Mock
    private DqSecurityService securityService;

    @Mock
    private MinioReportStorage minioReportStorage;

    @Mock
    private DqKnowledgeSinkService knowledgeSinkService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DqRuleMapper ruleMapper;

    @Mock
    private DqWorkOrderService workOrderService;

    @Mock
    private DqAutoRepairService autoRepairService;

    @Mock
    private IAlertService runtimeAlertService;

    /** Fixture 2 searchSimilar 行桩行模板（按 size 裁剪） */
    private List<Map<String, Object>> scanRows = new ArrayList<>();

    /** Fixture 2 RCA mock RestTemplate（不 new 全局无超时，stub postForEntity 模拟 cognitive 响应） */
    @Mock
    private RestTemplate restTemplateMock;

    // ────────────────────────────────────────────────────────────────────
    // Fixture 1 — 报告生成
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fixture 1 规则通过 → 评分快照 → 报告生成：DAILY 报告 VO 含 6 维 / LLM stub；HTML < 200KB 不上传 MinIO")
    void fixture1_generateDaily_returnsDailyVo_andSkipsMinio() {
        // 1) mock 4 个 JdbcTemplate 聚合查询（fat SQL 数据行），让 DqReportServiceImpl 取数成功
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any()))
                .thenReturn(42);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(3);
        when(jdbcTemplate.queryForObject(anyString(), eq(Double.class)))
                .thenReturn(0.87);
        // queryForList(String, Object...) — 六维快照行
        final String scoreSql = "SELECT dimension, score_value FROM ecos_dq.dq_score_snapshot";
        List<Map<String, Object>> dimScores = List.of(
                Map.of("dimension", "COMPLETENESS", "score_value", "0.92"),
                Map.of("dimension", "ACCURACY", "score_value", "0.88"),
                Map.of("dimension", "CONSISTENCY", "score_value", "0.91"),
                Map.of("dimension", "FRESHNESS", "score_value", "0.55"),
                Map.of("dimension", "UNIQUENESS", "score_value", "0.95"),
                Map.of("dimension", "VALIDITY", "score_value", "0.90"));
        doReturn(dimScores)
                .when(jdbcTemplate)
                .queryForList((String) anyString(), (Object[]) org.mockito.ArgumentMatchers.<Object>any());

        // 写 dq_report 成功
        when(jdbcTemplate.update(anyString(), any(Object.class), any(), any(), any(), any(),
                any(Object.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        DqReportServiceImpl reportSvc = newReportSvc();

        DqReportVO vo = reportSvc.generateDaily("ALL");

        // 4) 断言：报告类型 DAILY + payload 含 6 维 / DQ + LLM stub 已注入
        assertNotNull(vo, "generateDaily 应返回 DqReportVO");
        assertEquals("DAILY", vo.getReportType(), "reportType 应为 DAILY");
        assertEquals("ALL", vo.getScope(), "scope 应规范化为 ALL");
        assertNotNull(vo.getPayloadHtml(), "payloadHtml 不应为空");
        // 6 维清单必须全部出现
        for (String dim : new String[]{"COMPLETENESS", "ACCURACY", "CONSISTENCY",
                "FRESHNESS", "UNIQUENESS", "VALIDITY"}) {
            assertTrue(vo.getPayloadHtml().contains(dim),
                    "payloadHtml 应含 6 维之一: " + dim);
        }
        assertTrue(vo.getPayloadHtml().contains("DQ")
                || vo.getPayloadHtml().contains("数据质量"),
                "payloadHtml 应含 DQ 标识");
        assertNotNull(vo.getLlmSummary(), "llmSummary 不应为空");
        assertTrue(vo.getLlmSummary().contains("DQ 健康度"),
                "llmSummary 应含 DQ 健康度（stub 文本）");
        assertTrue(vo.getLlmSummary().contains("待 LLM 接入"),
                "llmSummary 应含 Phase 4 stub 标志");
        // 5) HTML 内嵌（< 200KB）→ MinIO 不触发
        assertTrue(vo.getPayloadHtml().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                < 200 * 1024, "HTML 应 < 200KB 才走纯 PG 路径");
        verify(minioReportStorage, never()).putReport(anyString(), any(byte[].class), anyString());
        // 6) 安全卡（铁律 2.4 #5）：生成异步审计 1 次
        verify(securityService, times(1))
                .auditWrite(eq("DQ_REPORT_GENERATE"), anyString(), eq("SUCCESS"));
    }

    // ────────────────────────────────────────────────────────────────────
    // Fixture 2 — P0 告警 → 工单 / RCA / knowledgeSink
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fixture 2 P0 告警 → 工单创建 → RCA 调 knowledgeSink：audit ≥ 1 + audit 命中 + searchSimilar + rootCause / confidence > 0")
    void fixture2_p0Alert_triggersWorkOrderAndKbAndRca() throws Exception {
        // Rule: CRITICAL severity → P0 级
        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-c1");
        rule.setRuleName("not-null-customer-email");
        rule.setRuleType("NOT_NULL");
        rule.setSeverity("CRITICAL");
        rule.setTargetTable("sysman.customer");
        rule.setTargetField("email");
        rule.setTargetKind("FIELD");
        when(ruleMapper.findById("rule-c1")).thenReturn(rule);

        // Jdbc 桩：check 行空；判重空；findDedupAlertId 空；工单查找空；update 成功
        // 默认：queryForList(String, Object...) 返回空列表
        doAnswer(inv -> new ArrayList<Map<String, Object>>())
                .when(jdbcTemplate)
                .queryForList((String) anyString(), (Object[]) org.mockito.ArgumentMatchers.<Object>any());
        // readWorkOrder 命中（RCA 阶段调）— 走 queryForList(String, Object...) 变体，
        // argument 中 workOrderId 命中 anyString()（与 dispatch 用的 queryForList(String, Class, Object...) 重载隔离）
        doAnswer(inv -> new ArrayList<>(List.of(Map.of(
                "ruleId", "rule-c1",
                "assetId", "sysman.customer",
                "severity", "CRITICAL",
                "description", "customer.email 大量 NULL 行，pass_rate=0.55"))))
                .when(jdbcTemplate)
                .queryForList(
                        org.mockito.ArgumentMatchers.argThat(s ->
                                s != null && s.contains("dq_work_order")),
                        anyString());
        // RAG 上下文：errorMessage 非空 → DqRcaServiceImpl.searchSimilarSafe 完成 searchSimilar 调用
        // → 命中 mock → similar.size()=0 → confidence=0.4（BASE，再加 hit bonus cap 0.95）
        when(knowledgeSinkService.searchSimilar(anyString(), eq(3)))
                .thenReturn(new PageResult<>(new ArrayList<>(), 0L, 1, 3));
        when(jdbcTemplate.update(anyString(), any(Object.class), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(1);

        // 工单创建返回新 ID
        when(workOrderService.createWorkOrder(any(DqAlertVO.class), any(DqRuleVO.class)))
                .thenReturn("wo-f2-001");

        // 组装 DqAlertServiceImpl 7 参构造器
        DqAlertServiceImpl alertSvc = newAlertSvc();

        // 调用 dispatchOnRuleCheckFailed
        alertSvc.dispatchOnRuleCheckFailed("rule-c1", "check-001", "FIELD", "sysman.customer:email");

        // 断言：审计 ≥ 1 次（DQ_ALERT_DISPATCH）
        verify(securityService, times(1))
                .auditWrite(eq("DQ_ALERT_DISPATCH"), anyString(), anyString());
        // 工单链：P0 → 自动建单 + 异步 audit
        verify(workOrderService, times(1)).createWorkOrder(any(DqAlertVO.class), any(DqRuleVO.class));
        verify(securityService, times(1))
                .auditWrite(eq("DQ_WORK_ORDER_AUTO_CREATE"), eq("wo-f2-001"), eq("SUCCESS"));

        // ─── RCA：mock spy RestTemplate 返回 cognitive diagnose 响应 ───
        String respJson = "{\"code\":200,\"message\":\"ok\",\"data\":{"
                + "\"rootCause\":\"dataSource disconnect at 02:14UTC\","
                + "\"causalChain\":[\"dataSource\",\"pipeline\",\"rule not-null-customer-email\"],"
                + "\"candidates\":[{\"cause\":\"dis\",\"prob\":0.72}]}}";
        when(restTemplateMock.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respJson, HttpStatus.OK));

        DqRcaServiceImpl rcaSvc = new DqRcaServiceImpl(
                restTemplateMock,
                knowledgeSinkService,
                jdbcTemplateProvider());

        DqRcaRequest req = new DqRcaRequest();
        req.setWorkOrderId("wo-f2-001");
        req.setFailedRuleId("rule-c1");
        req.setAssetId("sysman.customer:email");

        DqRcaResult result = rcaSvc.runDiagnose(req);

        // 断言：rootCause 非空非 STUB: 开头 + confidence > 0 + causalChain 已映射
        assertNotNull(result, "runDiagnose 应返回 DqRcaResult");
        assertNotNull(result.getRootCause(), "rootCause 不应为空");
        assertFalse(result.getRootCause().startsWith("STUB:"),
                "rootCause 不应以 STUB: 开头（应来自 cognitive 或 fallback）");
        assertNotNull(result.getConfidence(), "confidence 不应为空");
        assertTrue(result.getConfidence() > 0.0, "confidence 应 > 0（启发式基底 0.4）");
        assertNotNull(result.getCausalChain(), "causalChain 不应为空");
        assertEquals(3, result.getCausalChain().size(), "causalChain 应有 3 级");
        // RCA RAG 上下文：wo.description 非空 → searchSimilarSafe 命中 mock → 完成 searchSimilar 调用
        verify(knowledgeSinkService, times(1)).searchSimilar(anyString(), eq(3));
    }

    // ────────────────────────────────────────────────────────────────────
    // Fixture 3 — 报告 DB 持久化 → 知识 RAG 检索
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fixture 3 报告 DB 持久化 → 知识 RAG 检索：ingestFromAlert 写 dq_knowledge_entry；searchSimilar top ≤ 3 且 embedding 非空")
    void fixture3_ingestAlert_persistAndSearchSimilar() {
        // 组装 DqKnowledgeSinkServiceImpl，复用 jdbcTemplate mock
        DqKnowledgeSinkServiceImpl sinkSvc = new DqKnowledgeSinkServiceImpl(
                securityService, ruleMapper, jdbcTemplateProvider());

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-f3-001");
        alert.setRuleId("rule-c1");
        alert.setAlertLevel("P1");
        alert.setRuleName("not-null-customer-email");
        alert.setAssetId("sysman.customer");
        alert.setAssetName("sysman.customer:email");
        alert.setMessage("[DQ P1] not-null-customer-email 检查未通过 (FIELD:sysman.customer:email)");
        alert.setPayload(Map.of("passRate", 0.55, "failedRows", 90L));
        // 1 行 INSERT 成功
        when(jdbcTemplate.update(anyString(), any(Object.class), any(), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(1);

        sinkSvc.ingestFromAlert("alert-f3-001", alert);

        // 1 次 INSERT dq_knowledge_entry + 1 次 audit
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object.class), any(), any(),
                any(), any(), any(), any(), any(), any());
        verify(securityService, times(1))
                .auditWrite(eq("DQ_KB_INGEST_ALERT"), eq("alert-f3-001"), eq("SUCCESS"));

        // searchSimilar 桩：3 行 embedding 齐全
        scanRows = new ArrayList<>();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("id", "e-001");
        base.put("sourceType", "ALERT");
        base.put("sourceId", "alert-f3-001");
        base.put("category", "alert");
        base.put("title", "[DQ P1] not-null-customer-email");
        base.put("summary", "[DQ P1] not-null 检查未通过");
        base.put("contentMd", "数据质量告警 P1 not-null-customer-email 检查未通过");
        base.put("entityJson", "{\"ruleId\":\"rule-c1\"}");
        base.put("embeddingJson", "[0.5,0.7,0.9]");
        base.put("scoreHint", 0.81);
        scanRows.add(base);
        Map<String, Object> e2 = new LinkedHashMap<>(base);
        e2.put("id", "e-002");
        e2.put("contentMd", "其他告警情境，无交集");
        e2.put("scoreHint", 0.33);
        scanRows.add(e2);
        Map<String, Object> e3 = new LinkedHashMap<>(base);
        e3.put("id", "e-003");
        e3.put("contentMd", "数据质量告警 P1 not-null 检查 失败");
        e3.put("scoreHint", 0.55);
        scanRows.add(e3);

        // jdbc.query(String, RowMapper, Object...) 行映射：返回预设 VOs（按 limit*5 扫描）
        doAnswer(inv -> {
            List<DqKnowledgeEntryVO> vos = new ArrayList<>();
            for (Map<String, Object> m : scanRows) {
                DqKnowledgeEntryVO v = new DqKnowledgeEntryVO();
                v.setId(str(m.get("id")));
                v.setSourceType(str(m.get("sourceType")));
                v.setSourceId(str(m.get("sourceId")));
                v.setCategory(str(m.get("category")));
                v.setTitle(str(m.get("title")));
                v.setSummary(str(m.get("summary")));
                v.setContentMd(str(m.get("contentMd")));
                v.setEntityJson(str(m.get("entityJson")));
                v.setEmbeddingJson(str(m.get("embeddingJson")));
                Object sh = m.get("scoreHint");
                v.setScoreHint(sh instanceof Number n ? n.doubleValue() : null);
                vos.add(v);
            }
            return vos;
        }).when(jdbcTemplate).query(
                (String) anyString(), (RowMapper<DqKnowledgeEntryVO>) any(), (Object[]) any());

        PageResult<DqKnowledgeEntryVO> page = sinkSvc.searchSimilar(
                "数据质量告警 P1 not-null-customer-email", 3);

        assertNotNull(page, "searchSimilar 应返回 PageResult");
        assertNotNull(page.getData(), "data 列表不应为空");
        assertTrue(page.getData().size() <= 3, "top = 3，实际 size 应 ≤ 3；实际: " + page.getData().size());
        assertTrue(!page.getData().isEmpty(), "搜索匹配应返回 ≥ 1 条");
        for (DqKnowledgeEntryVO v : page.getData()) {
            assertNotNull(v.getEmbeddingJson(), "命中条 embeddingJson 应已写入");
            assertFalse(v.getEmbeddingJson().isBlank(), "embeddingJson 应非空");
            assertNotNull(v.getTitle(), "命中条 title 应已写入");
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 私有工厂
    // ────────────────────────────────────────────────────────────────────

    /** 同一测试内多次创建服务共享同一 provider mock（避免重复 verify 累积） */
    private LruProvider<JdbcTemplate> jdbcProviderLru = new LruProvider<>();

    private ObjectProvider<JdbcTemplate> jdbcTemplateProvider() {
        return jdbcProviderLru.ensure(jdbcTemplate);
    }

    /** ObjectProvider 桩缓存：同一测试内复用同一 mock 实例 */
    @SuppressWarnings("unchecked")
    private static class LruProvider<T> {
        private T target;
        private ObjectProvider<T> provider;

        @SuppressWarnings("unchecked")
        ObjectProvider<T> ensure(T target) {
            if (provider == null || this.target != target) {
                this.target = target;
                this.provider = mock(ObjectProvider.class);
                when(this.provider.getIfAvailable()).thenReturn(target);
            }
            return this.provider;
        }
    }

    private DqReportServiceImpl newReportSvc() {
        return new DqReportServiceImpl(securityService, minioReportStorage, jdbcTemplateProvider());
    }

    @SuppressWarnings("unchecked")
    private DqAlertServiceImpl newAlertSvc() throws Exception {
        ObjectProvider<IAlertService> alertProvider = mock(ObjectProvider.class);
        when(alertProvider.getIfAvailable()).thenReturn(runtimeAlertService);
        ObjectProvider<DqKnowledgeSinkService> sinkProvider = mock(ObjectProvider.class);
        when(sinkProvider.getIfAvailable()).thenReturn(knowledgeSinkService);
        return new DqAlertServiceImpl(
                ruleMapper, securityService, workOrderService, autoRepairService,
                alertProvider, jdbcTemplateProvider(), sinkProvider);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
