package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.engine.data.quality.DqKnowledgeSinkService;
import com.chinacreator.gzcm.engine.data.quality.DqRcaService;
import com.chinacreator.gzcm.engine.data.quality.model.DqAlertVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqRuleVO;
import com.chinacreator.gzcm.engine.data.quality.service.DqSecurityService;
import com.chinacreator.gzcm.engine.data.quality.service.DqWorkOrderServiceImpl;

/**
 * PendingQueueActionableTest — F02-07 验收：待处理队列（{@code dq_work_order} 简化态）
 * 的一条工单行必须"可执行"（actionable），携带：
 * <ol>
 *   <li><b>ruleId</b> — 触发规则的 rule_id（定位哪条 DQ 规则命中）；</li>
 *   <li><b>定位字段</b> — asset_id（目标表/字段定位，来自 alert.assetId，回退 rule.targetId）；</li>
 *   <li><b>修复入口</b> — status='PENDING'（可被在线认领 assign→startWork→resolve 的修复流）
 *       + handling_mode ∈ {MANUAL, AUTO_REPAIR}（明确处置模式，非空）。</li>
 * </ol>
 *
 * <p>驱动真实 {@link DqWorkOrderServiceImpl#createWorkOrder} — mock JdbcTemplate 捕获 INSERT 参数，
 * 断言落库行的四要素（ruleId / assetId / status / handling_mode）齐全且可执行。与既有
 * {@code WorkOrderListSqlContractTest}（查 SQL 契约）互补 —— 本测聚焦「入队行字段 actionable」。</p>
 */
@DisplayName("F02-07 待处理队列 dq_work_order 行 actionable（ruleId + 定位 + 修复入口）")
class PendingQueueActionableTest {

    @Test
    @DisplayName("new bad row → 队例行含 ruleId + 定位 assetId + status=PENDING + handling_mode 非空（可 assign 修复）")
    void queuedRowCarriesRuleIdTargetAndRepairEntry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> jprov = mock(ObjectProvider.class);
        when(jprov.getIfAvailable()).thenReturn(jdbc);
        // generateOrderNo 的探针查询：返回 1（空表置顶）
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class),
                mock(DqRcaService.class),
                jprov,
                mockProviderNull());

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-001");
        alert.setAssetId("sysman.customer");          // 定位字段（目标表）
        alert.setRuleName("not-null @ email");
        alert.setMessage("第 3 行 email 为空");

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-001");                       // ruleId
        rule.setSeverity("HIGH");
        rule.setParametersJson(null);                  // → handling_mode=MANUAL

        String workOrderId = svc.createWorkOrder(alert, rule);
        assertNotNull(workOrderId, "createWorkOrder 应返回新工单 id");

        // 捕获 INSERT INTO ecos_dq.dq_work_order(...) 的参数
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object[]> argsCaptor = (ArgumentCaptor<Object[]>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sqlCaptor.capture(), argsCaptor.capture());
        String insertSql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        // ── 0) 确实写的是待处理队列表 ──
        assertTrue(insertSql.contains("INSERT INTO ecos_dq.dq_work_order"),
                "入队须落 ecos_dq.dq_work_order，实际: " + insertSql);

        // ── 参数位序（对照 createWorkOrder INSERT 列序）：
        //   (id, order_no, alert_id, rule_id, asset_id, title, description, status, handling_mode,
        //    severity, retry_count=0(NOW 常量在 SQL 内), ...)
        // 实参只传 10 个（retry_count=0/NOW/NOW/FALSE 字面在 SQL）→ 见 args 对象长度
        assertNotNull(args, "INSERT 参数不得为空");

        // 定位 ruleId（第 4 位 args[3]）
        assertEquals("rule-001", args[3], "rule_id 定位字段缺失 —— 待处理行须溯源到触发规则");
        // 定位 assetId（第 5 位 args[4]）
        assertEquals("sysman.customer", args[4], "asset_id 定位字段缺失 —— 待处理行须给出目标表/字段");
        // status = PENDING（第 8 位 args[7]）—— 入队即 PENDING（可被认领/处理的 actionable 初态）
        assertEquals("PENDING", args[7],
                "入队行 status 必为 PENDING（actionable 初态，可 assign→startWork→resolve），实际 " + args[7]);
        // handling_mode = MANUAL（第 9 位 args[8]）—— 明确处置模式 = 修复入口
        assertEquals("MANUAL", args[8],
                "handling_mode 必为具体处置模式（MANUAL/AUTO_REPAIR）= 修复入口，实际 " + args[8]);
        // severity 透传（第 10 位 args[9]）
        assertEquals("HIGH", args[9], "severity 应透传 HIGH");
    }

    @Test
    @DisplayName("alert.assetId 为空 → 定位回退 rule.targetId（保证定位字段不空）")
    void missingAssetIdFallsBackToRuleTargetId() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> jprov = mock(ObjectProvider.class);
        when(jprov.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), jprov, mockProviderNull());

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-002");
        // 不 setAssetId → 触发回退

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-200");
        rule.setSeverity("MEDIUM");
        rule.setTargetId("sysman.partner");             // assetId 回退到此（定位字段）
        rule.setParametersJson("{\"autoRepair\":true}");   // → handling_mode=AUTO_REPAIR

        svc.createWorkOrder(alert, rule);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object[]> argsCaptor = (ArgumentCaptor<Object[]>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sqlCaptor.capture(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        // 定位回退：alert.assetId 空 → rule.targetId
        assertEquals("sysman.partner", args[4],
                "alert.assetId 缺失时应回退 rule.targetId 作定位字段，实际 " + args[4]);
        // parameters.autoRepair=true → AUTO_REPAIR（修复入口明确）
        assertEquals("AUTO_REPAIR", args[8],
                "parameters.autoRepair=true 应映射 handling_mode=AUTO_REPAIR，实际 " + args[8]);
    }

    @Test
    @DisplayName("队例行 title/description 携带定位信息（severity + ruleName + 命中说明）—— 可操作性元数据齐全")
    void queuedRowTitleAndDescriptionLocatable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ObjectProvider<JdbcTemplate> jprov = mock(ObjectProvider.class);
        when(jprov.getIfAvailable()).thenReturn(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), jprov, mockProviderNull());

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-003");
        alert.setAssetId("sysman.customer");
        alert.setRuleName("not-null @ email");
        alert.setMessage("第 3 行 email 为空");

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-300");
        rule.setSeverity("CRITICAL");
        rule.setTargetId("sysman.customer");

        svc.createWorkOrder(alert, rule);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object[]> argsCaptor = (ArgumentCaptor<Object[]>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sqlCaptor.capture(), argsCaptor.capture());
        Object[] args = argsCaptor.getValue();

        // title（args[5]）= "[SEVERITY] ruleName" —— 可操作性：一眼看出哪条规则、哪级严重度
        String title = (String) args[5];
        assertTrue(title.startsWith("[CRITICAL]") && title.contains("not-null @ email"),
                "title 须携 severity+ruleName 定位信息，实际: " + title);
        // description（args[6]）= 命中说明（定位到具体行/字段）
        assertEquals("第 3 行 email 为空", args[6],
                "description 须保留命中定位说明（第N行/字段），实际 " + args[6]);
        // retry_count 初值 0 由 SQL 内字面（不在参数），orderNo（args[1]）必带 WO- 前缀（可读追踪位）
        assertTrue(String.valueOf(args[1]).startsWith("WO-"),
                "orderNo 须为可读追踪号 WO-yyyyMMdd-NNN，实际 " + args[1]);
    }

    /** 可选 knowledge sink 注入缺省 → null（点火）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObjectProvider<DqKnowledgeSinkService> mockProviderNull() {
        ObjectProvider p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }
}
