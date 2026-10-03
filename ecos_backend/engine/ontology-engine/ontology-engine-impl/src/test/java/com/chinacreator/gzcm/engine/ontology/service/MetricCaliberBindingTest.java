package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.engine.ontology.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-01 指标口径绑定（W76/C60）：
 * <ul>
 *   <li>规则 4：PUBLISHED 指标必带 caliber_id，否则 400 ECOS-ONTO-040；</li>
 *   <li>规则 2：升版不自动传播 —— 需调 bindMetricCaliber 显式绑新版；</li>
 *   <li>规则 3：caliber_snapshot 一次性写入后只读 —— 再更新命中该列 → 409 ECOS-ONTO-041；</li>
 *   <li>V1 门禁同源：PUBLISHED 所绑版本非 APPROVED → 400 ECOS-ONTO-040。</li>
 * </ul>
 * 全 mock，禁连库。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricCaliberBindingTest {

    @Mock MetricDefinitionRepository repo;
    @Mock CaliberService caliberService;

    private MetricDefinitionService svc() { return new MetricDefinitionService(repo, caliberService); }

    @Test
    @DisplayName("DRAFT 指标可无口径 —— 直接建，不触发 040")
    void c1_draftWithoutCaliber() {
        when(repo.findByCode("M_DRAFT_X")).thenReturn(Optional.empty());
        MetricDefinition m = metric("M_DRAFT_X", "DRAFT", null);
        MetricDefinition r = svc().create(m, "pmo");
        assertEquals("DRAFT", r.getStatus());
        verify(repo).insert(any());
    }

    @Test
    @DisplayName("PUBLISHED 指标无 caliber_id → 400 ECOS-ONTO-040 拒绝创建")
    void c2_publishedRequiresCaliber() {
        when(repo.findByCode("M_PUB_NOCAL")).thenReturn(Optional.empty());
        MetricDefinition m = metric("M_PUB_NOCAL", "PUBLISHED", null);
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().create(m, "pmo"));
        assertEquals("ECOS-ONTO-040", ex.code);
        assertEquals(400, ex.httpStatus);
        verify(repo, never()).insert(any());
    }

    @Test
    @DisplayName("PUBLISHED 所绑版本号非 APPROVED → 400 ECOS-ONTO-040（V1 门禁同源）")
    void c3_bindPublishedWithUnapprovedVersion() {
        MetricDefinition m = metric("M_PUB_APPROVE", "PUBLISHED", "CAL-1");
        when(repo.findByCode("M_PUB_APPROVE")).thenReturn(Optional.of(m));
        when(caliberService.isVersionApproved("CAL-1", "2.0")).thenReturn(false);
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().bindMetricCaliber("M_PUB_APPROVE", "CAL-1", "2.0", "PUBLISHED", "op"));
        assertEquals("ECOS-ONTO-040", ex.code);
        assertEquals(400, ex.httpStatus);
        verify(repo, never()).bindCaliber(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PUBLISHED 绑 APPROVED 版本 → 快照一次性冻结为当前公式文本")
    void c4_bindPublishedAppliesApprovedWithSnapshot() {
        MetricDefinition m = metric("M_PUB_OK", "DRAFT", null);
        when(repo.findByCode("M_PUB_OK")).thenReturn(Optional.of(m));
        when(caliberService.isVersionApproved("CAL-2", "1.0")).thenReturn(true);
        when(caliberService.findVersionFormula("CAL-2", "1.0")).thenReturn("revenue - cost");
        MetricDefinition r = svc().bindMetricCaliber("M_PUB_OK", "CAL-2", "1.0", "PUBLISHED", "op");
        assertEquals("CAL-2", r.getCaliberId());
        assertEquals("1.0", r.getFormulaVersion());
        assertEquals("revenue - cost", r.getCaliberSnapshot());
        assertEquals("PUBLISHED", r.getStatus());
        verify(repo).bindCaliber(any(), eq("CAL-2"), eq("1.0"), eq("revenue - cost"), eq("PUBLISHED"), eq("op"));
    }

    @Test
    @DisplayName("DRAFT 升 PUBLISHED 前缺 caliber_id → 400 ECOS-ONTO-040")
    void c5_draftUpgradeMissingCaliber() {
        MetricDefinition m = metric("M_UPGRADE", "DRAFT", null);
        when(repo.findByCode("M_UPGRADE")).thenReturn(Optional.of(m));
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().bindMetricCaliber("M_UPGRADE", null, "1.0", "PUBLISHED", "op"));
        assertEquals("ECOS-ONTO-040", ex.code);
        verify(repo, never()).bindCaliber(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("指标不存在 → 404 ECOS-ONTO-080")
    void c6_unknownCode404() {
        when(repo.findByCode("M_NA")).thenReturn(Optional.empty());
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().bindMetricCaliber("M_NA", "CAL-9", "1.0", "PUBLISHED", "op"));
        assertEquals("ECOS-ONTO-080", ex.code);
        assertEquals(404, ex.httpStatus);
    }

    private static MetricDefinition metric(String code, String status, String caliberId) {
        MetricDefinition m = new MetricDefinition();
        m.setId("id-" + code);
        m.setCode(code);
        m.setExpression("a - b");
        m.setAggregation("SUM");
        m.setStatus(status);
        m.setCaliberId(caliberId);
        return m;
    }
}
