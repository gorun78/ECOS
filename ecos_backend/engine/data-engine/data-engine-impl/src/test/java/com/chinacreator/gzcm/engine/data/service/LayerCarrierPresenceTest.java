package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F02-05 验收 · 第 2 条 / D-9 / W51 —— 载体存在性。
 *
 * <p>断言：{@code GET /api/v1/engine/data/layers} 响应体对「0 载体」的层
 * 必须回 {@code declared:false}（SEMANTIC/APPLICATION 当前 0 载体），
 * 禁止前端把无载体层呈现为"有数据"。
 *
 * <p>纯单元测（无 Spring 容器 / 无 DB）：mock {@link JdbcTemplate}（层行数计数）
 * 与 {@link DataResourceRepository#countRegisteredCarriersByLayer}（载体计数）。
 */
class LayerCarrierPresenceTest {

    private JdbcTemplate jdbc;
    private DataResourceRepository repository;
    private DataLayerService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = mock(DataResourceRepository.class);
        service = new DataLayerService(jdbc, repository);
    }

    @SuppressWarnings("unchecked")
    private void mockLayerCounts(int perLayer) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString()))
            .thenReturn(perLayer);
    }

    @Test
    @DisplayName("0 载体层（SEMANTIC/APPLICATION）→ declared=false，不得呈现为'有数据'")
    void zeroCarrierLayerDeclaredFalse() {
        mockLayerCounts(10); // 每层资源条数 > 0（有元数据行）
        when(repository.countRegisteredCarriersByLayer(anyString())).thenReturn(0); // 但载体登记 = 0

        Map<String, Object> summary = service.getLayerSummary();
        assertNotNull(summary);

        @SuppressWarnings("unchecked")
        Map<String, Boolean> declared = (Map<String, Boolean>) summary.get("declared");
        assertNotNull(declared, "响应体必须携带 declared 存在性子映射");

        // D-9：SEMANTIC / APPLICATION 当前 0 载体
        assertFalse(declared.get("SEMANTIC"), "SEMANTIC 0 载体必须 declared=false");
        assertFalse(declared.get("APPLICATION"), "APPLICATION 0 载体必须 declared=false");
        assertTrue(declared.keySet().size() >= 5, "五层都要给出存在性裁决");
    }

    @Test
    @DisplayName("≥1 载体层 → declared=true（对照）")
    void carrierLayerDeclaredTrue() {
        mockLayerCounts(0); // 层资源条数为 0 不关键 —— declared 只看载体计数
        when(repository.countRegisteredCarriersByLayer(anyString()))
            .thenAnswer(inv -> "CURATED".equals(inv.getArgument(0)) ? 3 : 0);

        Map<String, Object> summary = service.getLayerSummary();
        @SuppressWarnings("unchecked")
        Map<String, Boolean> declared = (Map<String, Boolean>) summary.get("declared");

        assertTrue(declared.get("CURATED"), "CURATED 登记了 3 载体必须 declared=true");
        assertFalse(declared.get("RAW"), "RAW 0 载体保持 declared=false（只增不改，其它层不受影响）");
    }
}
