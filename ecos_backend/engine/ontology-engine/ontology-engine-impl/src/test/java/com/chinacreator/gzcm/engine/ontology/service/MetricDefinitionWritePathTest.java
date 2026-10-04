package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.engine.ontology.repository.MetricDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F03-09 指标语义二分：唯一可写入口 = {@code ecos_metric_definition}（定义侧）。
 * 断言：
 * <ul>
 *   <li>create 只走 repo.insert 一条路径（禁多入口）；</li>
 *   <li>DRAFT 允许 caliberId=null；PUBLISHED 强制 caliberId；</li>
 *   <li>升版切换走 bindMetricCaliber（repo.bindCaliber 唯一旁路），不走 repo.updateDefinition 改快照；</li>
 *   <li>重复 code → 409。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricDefinitionWritePathTest {

    @Mock MetricDefinitionRepository repo;
    @Mock ICaliberService caliberService;

    private MetricDefinitionService svc() { return new MetricDefinitionService(repo, caliberService); }

    @Test
    @DisplayName("DRAFT 指标 create：repo.insert 走一条路径；DR06 createBy/updateBy = operator")
    void draftCreateUsesInsert() {
        when(repo.findByCode("M_A")).thenReturn(Optional.empty());
        MetricDefinition in = new MetricDefinition();
        in.setCode("M_A");
        in.setName("验收金额");
        in.setExpression("SUM(x)");
        in.setAggregation("SUM");
        in.setStatus("DRAFT");

        MetricDefinition r = svc().create(in, "creator-1");
        assertNotNull(r.getId(), "应用侧生成 UUID（MC01）");
        assertEquals("DRAFT", r.getStatus());
        ArgumentCaptor<MetricDefinition> cap = ArgumentCaptor.forClass(MetricDefinition.class);
        verify(repo).insert(cap.capture());
        assertEquals("creator-1", cap.getValue().getCreateBy());
        assertEquals("creator-1", cap.getValue().getUpdateBy());
        // 未触发 bindCaliber
        verify(repo, never()).bindCaliber(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PUBLISHED create 未带 caliberId → 400 ECOS-ONTO-040，不 insert")
    void publishedCreateRequiresCaliber() {
        when(repo.findByCode("M_PUB")).thenReturn(Optional.empty());
        MetricDefinition m = new MetricDefinition();
        m.setCode("M_PUB");
        m.setStatus("PUBLISHED");
        m.setExpression("x");
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().create(m, "op"));
        assertEquals("ECOS-ONTO-040", ex.code);
        assertEquals(400, ex.httpStatus);
        verify(repo, never()).insert(any());
    }

    @Test
    @DisplayName("重复 code → 409（走快照码口径 ECOS-ONTO-041，兼容 C.8 状态机口径）")
    void duplicateCodeRejected() {
        MetricDefinition existing = new MetricDefinition();
        existing.setId("id-1");
        when(repo.findByCode("M_DUP")).thenReturn(Optional.of(existing));
        MetricDefinition m = new MetricDefinition();
        m.setCode("M_DUP");
        m.setStatus("DRAFT");
        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().create(m, "op"));
        assertEquals("ECOS-ONTO-041", ex.code);
        assertEquals(409, ex.httpStatus);
        verify(repo, never()).insert(any());
    }

    @Test
    @DisplayName("升版 SUM → SUMX 显式 bindMetricCaliber：APPROVED 版本才允许 PUBLISHED targetStatus")
    void upgradeBindingPath() {
        // 现有 PUBLISHED 指标
        MetricDefinition m = new MetricDefinition();
        m.setId("m-2");
        m.setCode("M_UP");
        m.setStatus("PUBLISHED");
        m.setCaliberId("CAL-9");
        m.setFormulaVersion("1.0");
        m.setCaliberSnapshot("v1 snapshot");
        when(repo.findByCode("M_UP")).thenReturn(Optional.of(m));

        // 新 APPROVED 版本 2.0
        when(caliberService.isVersionApproved("CAL-9", "2.0")).thenReturn(true);

        MetricDefinition r = svc().bindMetricCaliber("M_UP", "CAL-9", "2.0", "PUBLISHED", "op2");
        assertEquals("2.0", r.getFormulaVersion());
        // 快照已冻结 → 保持 v1 snapshot（不改写）
        assertEquals("v1 snapshot", r.getCaliberSnapshot());
        verify(repo).bindCaliber(eq("m-2"), eq("CAL-9"), eq("2.0"), eq("v1 snapshot"), eq("PUBLISHED"), eq("op2"));
    }

    @Test
    @DisplayName("PUBLISHED 绑非 APPROVED 版本 → 400 ECOS-ONTO-040，不写库")
    void upgradeRejectsUnapproved() {
        MetricDefinition m = new MetricDefinition();
        m.setId("m-3");
        m.setCode("M_UPB");
        m.setStatus("PUBLISHED");
        m.setCaliberId("CAL-10");
        m.setFormulaVersion("1.0");
        m.setCaliberSnapshot("v1");
        when(repo.findByCode("M_UPB")).thenReturn(Optional.of(m));
        when(caliberService.isVersionApproved("CAL-10", "3.0")).thenReturn(false);

        MetricDefinitionService.MetricException ex = assertThrows(MetricDefinitionService.MetricException.class,
                () -> svc().bindMetricCaliber("M_UPB", "CAL-10", "3.0", "PUBLISHED", "op"));
        assertEquals("ECOS-ONTO-040", ex.code);
        verify(repo, never()).bindCaliber(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("create：DR06/DR07 时间戳与版本号自动补齐（clock 保真）")
    void dramFieldsPopulated() {
        when(repo.findByCode("M_K")).thenReturn(Optional.empty());
        MetricDefinition m = new MetricDefinition();
        m.setCode("M_K");
        m.setStatus("DRAFT");
        MetricDefinition r = svc().create(m, "op");
        ArgumentCaptor<MetricDefinition> cap = ArgumentCaptor.forClass(MetricDefinition.class);
        verify(repo).insert(cap.capture());
        assertNotNull(cap.getValue().getCreateTime(), "DR06 create_time 必落");
        assertNotNull(cap.getValue().getUpdateTime(), "DR06 update_time 必落");
        assertNotEquals("op", cap.getValue().getId(), "id 应为 UUID 而非 operator");
    }
}
