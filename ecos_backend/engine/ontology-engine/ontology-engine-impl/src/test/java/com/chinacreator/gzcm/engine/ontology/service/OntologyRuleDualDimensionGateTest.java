package com.chinacreator.gzcm.engine.ontology.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRuleVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRuleRepository;

/**
 * #147B 规则契约面门禁 —— 双维承载值域闭集（册 I-3）+ 发布前置门
 * （册 §6.2.6⑧ / C-9 / DF-5 / L260 正交条款；§2.1 零新增端点裁决：发布经
 * PUT 携带 status=PUBLISHED 触发）。
 *
 * <p>判据面：</p>
 * <ul>
 *   <li>ONT-030：rule_carrier / rule_layer 非空即须落 I-3 闭集，非法值拒且<b>不落库</b>；</li>
 *   <li>ONT-031：status=PUBLISHED 时合并态（存量+DTO）五字段
 *       rule_carrier/rule_layer/scope/version_no/git_ref 全量就绪方可放行，
 *       缺 version_no/git_ref 拒发布（DF-5 负向用例）、rule_layer 空值拒发布（C-9）、
 *       rule_type×rule_layer 正交缺 rule_type 拒发布（L260）、表达式不过 L1 基线拒发布，
 *       且拒时<b>不触达 repository.update</b>；</li>
 *   <li>放行腿：五字段齐备 + 可解析表达式 ⇒ 15 参 update 携带 status=PUBLISHED；</li>
 *   <li>Map 旧门面收敛内核：两轨同效，不旁路。</li>
 * </ul>
 */
class OntologyRuleDualDimensionGateTest {

    private static final String PARSABLE_EXPR = "age > 0";

    private final OntologyRuleRepository repository = mock(OntologyRuleRepository.class);
    private final OntologyRuleService service = new OntologyRuleService(repository);

    /** 五字段齐备 + 可解析表达式的 DTO（发布门放行形态）。 */
    private static OntologyRuleSaveDTO readyDto() {
        OntologyRuleSaveDTO dto = new OntologyRuleSaveDTO();
        dto.setRuleType("VALIDATION");
        dto.setExpression(PARSABLE_EXPR);
        dto.setRuleCarrier("ONTOLOGY_ELEMENT");
        dto.setRuleLayer("VALIDATION");
        dto.setScope("entity");
        dto.setVersionNo("2");
        dto.setGitRef("abc1234");
        return dto;
    }

    /** 存量行（模拟 live 表一行 ACTIVE 规则，双维与就绪件套全空——存量真实形态）。 */
    private static OntologyRule existingRule() {
        OntologyRule r = new OntologyRule();
        r.setId("rule1001");
        r.setEntityId("ent1");
        r.setCode("R-001");
        r.setRuleType("VALIDATION");
        r.setExpression(PARSABLE_EXPR);
        r.setEnabled(1);
        r.setStatus("ACTIVE");
        return r;
    }

    // ═══════════════ ONT-030 值域闭集（I-3） ═══════════════

    @Test
    @DisplayName("I-3：创建时非法 rule_carrier 拒 ONT-030 且不落库")
    void createRejectsCarrierOutsideClosedSet() {
        OntologyRuleSaveDTO dto = readyDto();
        dto.setRuleCarrier("GIT_FILE");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createRule("ent1", dto));
        assertTrue(e.getMessage().startsWith(OntologyRuleService.CODE_VALUE_DOMAIN + ":"),
            "message 起始位须承载机读码 ONT-030：" + e.getMessage());
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("I-3：更新时非法 rule_layer 拒 ONT-030 且不触达 update")
    void updateRejectsLayerOutsideClosedSet() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        OntologyRuleSaveDTO dto = new OntologyRuleSaveDTO();
        dto.setRuleLayer("INFRA");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().startsWith(OntologyRuleService.CODE_VALUE_DOMAIN + ":"), e.getMessage());
        verify(repository, never()).update(anyString(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("存量兼容：carrier/layer 留空允许创建（空值只在发布时拒绝）")
    void createAllowsBlankDualDimension() {
        OntologyRuleSaveDTO dto = new OntologyRuleSaveDTO();
        dto.setName("legacy-rule");
        service.createRule("ent1", dto);
        ArgumentCaptor<OntologyRule> cap = ArgumentCaptor.forClass(OntologyRule.class);
        verify(repository).insert(cap.capture());
        assertEquals("ACTIVE", cap.getValue().getStatus());
        assertEquals("1", cap.getValue().getVersionNo());
        assertEquals("default", cap.getValue().getDomain());
        assertNotNull(cap.getValue().getRuleType());
    }

    // ═══════════════ ONT-031 发布前置门（§6.2.6⑧ / C-9 / DF-5 / L260） ═══════════════

    @Test
    @DisplayName("DF-5 负向：缺 git_ref 发布拒 ONT-031 且 update 未触达")
    void publishRejectedWhenGitRefMissing() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setGitRef(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().startsWith(OntologyRuleService.CODE_PUBLISH_GATE + ":"), e.getMessage());
        assertTrue(e.getMessage().contains("git_ref"), "violation 明细须点名 git_ref：" + e.getMessage());
        verify(repository, never()).update(anyString(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DF-5 负向：存量与 DTO 均无 version_no 发布拒 ONT-031")
    void publishRejectedWhenVersionNoMissing() {
        OntologyRule legacy = existingRule();
        legacy.setVersionNo(null);
        when(repository.findById("rule1001")).thenReturn(Optional.of(legacy));
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setVersionNo(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().contains("version_no"), e.getMessage());
        verify(repository, never()).update(anyString(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("C-9：合并态 rule_layer 空值记录发布拒 ONT-031（不静默放行）")
    void publishRejectedWhenLayerMissing() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setRuleLayer(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().contains("rule_layer"), e.getMessage());
    }

    @Test
    @DisplayName("L260 正交：DTO 清空 rule_type 且存量无值 ⇒ 拒发布（rule_type×rule_layer 双登记）")
    void publishRejectedWhenRuleTypeMissing() {
        OntologyRule noType = existingRule();
        noType.setRuleType(null);
        when(repository.findById("rule1001")).thenReturn(Optional.of(noType));
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setRuleType(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().contains("rule_type"), e.getMessage());
    }

    @Test
    @DisplayName("L1 基线：表达式不可解析发布拒 ONT-031")
    void publishRejectedWhenExpressionUnparsable() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setExpression("hello world");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", dto).ifPresent(v -> { }));
        assertTrue(e.getMessage().contains("expression"), e.getMessage());
    }

    @Test
    @DisplayName("放行腿：五字段齐备+可解析 ⇒ 15 参 update 携带 status=PUBLISHED")
    void publishPassesWhenReady() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        when(repository.update(eq("rule1001"), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        service.updateRule("rule1001", readyDtoWithStatusPublished()).ifPresent(vo ->
            assertEquals("rule1001", vo.getId()));
        verify(repository).update(eq("rule1001"), isNull(), isNull(), eq("VALIDATION"),
            eq(PARSABLE_EXPR), isNull(), isNull(), isNull(), isNull(),
            eq("ONTOLOGY_ELEMENT"), eq("VALIDATION"), eq("entity"),
            eq("2"), eq("abc1234"), eq("PUBLISHED"));
    }

    private static OntologyRuleSaveDTO readyDtoWithStatusPublished() {
        OntologyRuleSaveDTO dto = readyDto();
        dto.setStatus(OntologyRuleService.STATUS_PUBLISHED);
        return dto;
    }

    @Test
    @DisplayName("创建即发布：DTO status=PUBLISHED 且五字段齐备 ⇒ insert 携带 PUBLISHED 放行")
    void createAsPublishedPassesGate() {
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        OntologyRuleVO vo = service.createRule("ent1", dto);
        assertEquals("PUBLISHED", vo.getStatus());
        assertEquals("ONTOLOGY_ELEMENT", vo.getRuleCarrier());
        assertEquals("abc1234", vo.getGitRef());
        verify(repository).insert(any());
    }

    @Test
    @DisplayName("创建即发布负向：status=PUBLISHED 缺 scope ⇒ 拒 ONT-031 且不落库")
    void createAsPublishedRejectedWhenScopeMissing() {
        OntologyRuleSaveDTO dto = readyDtoWithStatusPublished();
        dto.setScope(null);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createRule("ent1", dto));
        assertTrue(e.getMessage().contains("scope"), e.getMessage());
        verify(repository, never()).insert(any());
    }

    // ═══════════════ Map 旧门面收敛（不旁路） ═══════════════

    @Test
    @DisplayName("Map 轨 create：非法 carrier 同样拒 ONT-030")
    void mapFacadeSharesValueDomainGate() {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ruleCarrier", "WRONG");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createRule("ent1", body));
        assertTrue(e.getMessage().startsWith(OntologyRuleService.CODE_VALUE_DOMAIN + ":"), e.getMessage());
        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("Map 轨 update：status=PUBLISHED 缺 git_ref 同样拒 ONT-031")
    void mapFacadeSharesPublishGate() {
        when(repository.findById("rule1001")).thenReturn(Optional.of(existingRule()));
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", "PUBLISHED");
        body.put("ruleCarrier", "RULE_OBJECT");
        body.put("ruleLayer", "BUSINESS");
        body.put("scope", "domain");
        body.put("versionNo", "3");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.updateRule("rule1001", body).ifPresent(v -> { }));
        assertTrue(e.getMessage().startsWith(OntologyRuleService.CODE_PUBLISH_GATE + ":"), e.getMessage());
        verify(repository, never()).update(anyString(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VO 承载腿：toVO 输出双维+就绪件套+status/domain 七键")
    void voCarriesDualDimensionFields() {
        OntologyRule r = existingRule();
        r.setRuleCarrier("KNOWLEDGE_ASSET");
        r.setRuleLayer("POLICY");
        r.setScope("tenant");
        r.setVersionNo("v1");
        r.setGitRef("deadbee");
        r.setDomain("default");
        when(repository.findById("rule1001")).thenReturn(Optional.of(r));
        OntologyRuleVO vo = service.getRuleVO("rule1001");
        assertEquals("KNOWLEDGE_ASSET", vo.getRuleCarrier());
        assertEquals("POLICY", vo.getRuleLayer());
        assertEquals("tenant", vo.getScope());
        assertEquals("v1", vo.getVersionNo());
        assertEquals("deadbee", vo.getGitRef());
        assertEquals("ACTIVE", vo.getStatus());
        assertEquals("default", vo.getDomain());
    }
}
