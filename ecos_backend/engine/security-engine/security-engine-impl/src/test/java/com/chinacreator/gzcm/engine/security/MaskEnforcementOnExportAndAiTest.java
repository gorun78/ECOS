package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 详细设计-01 F01-03 — 脱敏（mask）出口治理：导出与 AI 工具通道<b>强制</b>经 mask 裁决。
 *
 * <p>B3b-security 数据访问收口后：mask 源目录收敛到 {@link SecurityDecisionDao#masksForTable}。</p>
 *
 * <p>断言：</p>
 * <ul>
 *   <li>敏感数据目录登记了 mask 列 → {@code decide(EXPORT)} 与 {@code decide(AI)} 的
 *       <b>同一</b> bundle 都携带对应 {@code masks[]}{@code {column, strategy}}；</li>
 *   <li>目录未登记 → masks 为空（不放大权限，不做全表脱敏冒充受控）。</li>
 * </ul>
 */
class MaskEnforcementOnExportAndAiTest {

    private SecurityDecisionService svc;

    @BeforeEach
    void setUp() {
        RowLevelSecurityServiceImpl rls = mock(RowLevelSecurityServiceImpl.class);
        ColumnLevelSecurityServiceImpl cls = mock(ColumnLevelSecurityServiceImpl.class);
        OpaPolicyService opa = mock(OpaPolicyService.class);
        SecurityDecisionDao dao = mock(SecurityDecisionDao.class);
        svc = new SecurityDecisionService(rls, cls, opa, dao);

        Map<String, Object> rlsRes = new LinkedHashMap<>();
        Map<String, Object> pred = new LinkedHashMap<>();
        pred.put("predicateTemplate", "1=1");
        pred.put("denyIfEmpty", false);
        rlsRes.put("predicate", pred);
        when(rls.apply(anyString(), anyString())).thenReturn(rlsRes);

        Map<String, Object> clsRes = new LinkedHashMap<>();
        clsRes.put("visibleColumns", List.of("staff_ref", "amount", "plain"));
        clsRes.put("blockedColumns", List.of());
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(clsRes);

        Map<String, Object> opaRes = new LinkedHashMap<>();
        opaRes.put("allow", true);
        opaRes.put("policyRef", "ecos.data.access");
        opaRes.put("obligations", List.of());
        when(opa.evaluate(anyString(), anyMap())).thenReturn(opaRes);

        // 目录：staff_ref → STAFF_REF，amount → AMOUNT（两张登记列都走 mask）
        when(dao.masksForTable(anyString(), anyString())).thenReturn(List.of(
                Map.of("column", "staff_ref", "strategy", "STAFF_REF"),
                Map.of("column", "amount", "strategy", "AMOUNT")));
    }

    private DecisionContext ctx() { return DecisionContext.of("u1", "t1", null, null, List.of(), "tr"); }
    private AssetRef asset() {
        return new AssetRef("public", "fc_resource_input_fact",
                List.of("staff_ref", "amount", "plain"));
    }

    @Test
    @DisplayName("导出通道 — masks 必须携带目录登记的脱敏列 + 策略")
    void exportChannelCarriesMaskColumns() {
        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.EXPORT, ctx());
        assertTrue(b.masks().size() >= 2, "目录登记的 mask 列必须出现在导出 bundle: " + b.masks());
        Map<String, Object> staff = b.masks().stream()
                .filter(m -> "staff_ref".equals(m.get("column"))).findFirst().orElse(null);
        assertNotNull(staff, "staff_ref 必须带 mask 策略");
        assertEquals("STAFF_REF", staff.get("strategy"));
    }

    @Test
    @DisplayName("AI 通道 — 同入口同样强带 mask（与导出同源，防旁路）")
    void aiChannelCarriesSameMaskColumns() {
        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.AI, ctx());
        assertTrue(b.masks().size() >= 2, "AI 通道同样经过 mask 裁决: " + b.masks());
        Map<String, Object> amount = b.masks().stream()
                .filter(m -> "amount".equals(m.get("column"))).findFirst().orElse(null);
        assertNotNull(amount, "amount 必须带 mask 策略（ST03-A 联动：金额列遇明文 → 走脱敏出口）");
    }

    @Test
    @DisplayName("目录未登记 → masks 空（不放大权限）")
    void noCatalogEntryMeansNoMaskInflation() {
        // 用空目录重建服务
        SecurityDecisionDao emptyDao = mock(SecurityDecisionDao.class);
        RowLevelSecurityServiceImpl rls = mock(RowLevelSecurityServiceImpl.class);
        ColumnLevelSecurityServiceImpl cls = mock(ColumnLevelSecurityServiceImpl.class);
        OpaPolicyService opa = mock(OpaPolicyService.class);
        SecurityDecisionService svcEmpty = new SecurityDecisionService(rls, cls, opa, emptyDao);

        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> pred = new LinkedHashMap<>();
        pred.put("predicateTemplate", "1=1");
        r.put("predicate", pred);
        when(rls.apply(anyString(), anyString())).thenReturn(r);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("visibleColumns", List.of("plain"));
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(c);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("allow", true);
        o.put("policyRef", "p");
        o.put("obligations", List.of());
        when(opa.evaluate(anyString(), anyMap())).thenReturn(o);
        when(emptyDao.masksForTable(anyString(), anyString())).thenReturn(List.of());

        DecisionBundle b = svcEmpty.decide(asset(), DecisionBundle.Purpose.EXPORT, ctx());
        assertTrue(b.masks().isEmpty(), "无目录登记不得凭空放大 mask 集合");
    }
}
