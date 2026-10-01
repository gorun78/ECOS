package com.chinacreator.gzcm.engine.data.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.chinacreator.gzcm.engine.data.quality.dao.DqWorkOrderDao;
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
 *   <li><b>ruleId</b> — 触发规则的 rule_id；</li>
 *   <li><b>assetId</b> — alert.assetId，回退 rule.targetId；</li>
 *   <li><b>status=PENDING</b> + <b>handling_mode ∈ {MANUAL, AUTO_REPAIR}</b>。</li>
 * </ol>
 *
 * <p>Refactor 2 (2026-10-01): 服务收口走 DAO。本测试改 mock {@link DqWorkOrderDao#insertWorkOrder}
 * + {@link DqWorkOrderDao#nextOrderSeq}，断言落库行四要素不变。SQL 契约同值保证由
 * {@code WorkOrderListSqlContractTest} 承接。</p>
 */
@DisplayName("F02-07 待处理队列 dq_work_order 行 actionable（ruleId + 定位 + 修复入口）")
class PendingQueueActionableTest {

    @Test
    @DisplayName("new bad row → 队例行含 ruleId + 定位 assetId + status=PENDING + handling_mode 非空")
    void queuedRowCarriesRuleIdTargetAndRepairEntry() {
        DqWorkOrderDao dao = mock(DqWorkOrderDao.class);
        when(dao.nextOrderSeq(anyString())).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class),
                mock(DqRcaService.class),
                mockProviderNull(),
                dao);

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-001");
        alert.setAssetId("sysman.customer");
        alert.setRuleName("not-null @ email");
        alert.setMessage("第 3 行 email 为空");

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-001");
        rule.setSeverity("HIGH");
        rule.setParametersJson(null);                  // → handling_mode=MANUAL

        String workOrderId = svc.createWorkOrder(alert, rule);
        assertNotNull(workOrderId, "createWorkOrder 应返回新工单 id");

        // 捕获 insertWorkOrder 的 10 个实参 —— 入队行四要素（ruleId / assetId / status / handling_mode）齐全
        verify(dao).insertWorkOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq("alert-001"),
                eq("rule-001"),
                eq("sysman.customer"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("第 3 行 email 为空"),
                eq("PENDING"),
                eq("MANUAL"),
                eq("HIGH"));
    }

    @Test
    @DisplayName("alert.assetId 为空 → 定位回退 rule.targetId；autoRepair=true → AUTO_REPAIR")
    void missingAssetIdFallsBackToRuleTargetId() {
        DqWorkOrderDao dao = mock(DqWorkOrderDao.class);
        when(dao.nextOrderSeq(anyString())).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), mockProviderNull(), dao);

        DqAlertVO alert = new DqAlertVO();
        alert.setId("alert-002");
        // 不 setAssetId → 触发回退

        DqRuleVO rule = new DqRuleVO();
        rule.setId("rule-200");
        rule.setSeverity("MEDIUM");
        rule.setTargetId("sysman.partner");
        rule.setParametersJson("{\"autoRepair\":true}");

        svc.createWorkOrder(alert, rule);

        // 定位回退：alert.assetId 空 → rule.targetId
        verify(dao).insertWorkOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq("alert-002"),
                eq("rule-200"),
                eq("sysman.partner"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eq("PENDING"),
                eq("AUTO_REPAIR"),
                eq("MEDIUM"));
    }

    @Test
    @DisplayName("队例行 title/description 携带定位信息；orderNo 带 WO- 前缀")
    void queuedRowTitleAndDescriptionLocatable() {
        DqWorkOrderDao dao = mock(DqWorkOrderDao.class);
        when(dao.nextOrderSeq(anyString())).thenReturn(1);

        DqWorkOrderServiceImpl svc = new DqWorkOrderServiceImpl(
                mock(DqSecurityService.class), mock(DqRcaService.class), mockProviderNull(), dao);

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

        ArgumentCaptor<String> orderNoCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> descCap = ArgumentCaptor.forClass(String.class);
        verify(dao).insertWorkOrder(
                org.mockito.ArgumentMatchers.anyString(),
                orderNoCap.capture(),
                eq("alert-003"),
                eq("rule-300"),
                eq("sysman.customer"),
                titleCap.capture(),
                descCap.capture(),
                eq("PENDING"),
                eq("MANUAL"),
                eq("CRITICAL"));

        // title = "[CRITICAL] not-null @ email"
        String title = titleCap.getValue();
        assertTrue(title.startsWith("[CRITICAL]") && title.contains("not-null @ email"),
                "title 须携 severity+ruleName 定位信息，实际: " + title);
        assertEquals("第 3 行 email 为空", descCap.getValue(),
                "description 须保留命中定位说明");
        assertTrue(orderNoCap.getValue().startsWith("WO-"),
                "orderNo 须为可读追踪号 WO-yyyyMMdd-NNN，实际 " + orderNoCap.getValue());
    }

    /** 可选 knowledge sink 注入缺省 → null。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ObjectProvider<DqKnowledgeSinkService> mockProviderNull() {
        ObjectProvider p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }
}
