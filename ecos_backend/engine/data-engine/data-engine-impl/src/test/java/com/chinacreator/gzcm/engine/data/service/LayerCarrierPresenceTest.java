package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.chinacreator.gzcm.engine.data.model.DataLayer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-05-2 / B.4（详细设计-02）五层载体存在性验收：
 * <ul>
 *   <li>{@code LayerCarrierPresenceTest} —— 无已登记载体的层 {@code declared=false}，
 *       前端不得显示为"有数据"（现状 SEMANTIC/APPLICATION = 0 载体，D-9/W51 P0）。</li>
 * </ul>
 * 判定性单测（Mockito 打桩 {@code JdbcTemplate}，不触库）。
 */
class LayerCarrierPresenceTest {

    private final JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    private final DataLayerService svc = new DataLayerService(jdbc);

    /**
     * 打桩 per-layer COUNT：SEMANTIC / APPLICATION 载体=0（现状 0 载体），其余层 >0。
     * 关键：queryForObject(…, Integer.class, Object... args) 的层名在 varargs[0]。
     */
    @Test
    @DisplayName("LayerCarrierPresenceTest — 无载体层 declared=false，有载体层 declared=true")
    void emptyCarrierLayer_notDeclared() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), Mockito.<Object>any()))
                .thenAnswer(inv -> {
                    // Mockito 把 varargs 展开为独立参数：arg(2) = 首个 vararg = 层名
                    String layer = (String) inv.getArgument(2);
                    return DataLayer.SEMANTIC.name().equals(layer)
                            || DataLayer.APPLICATION.name().equals(layer) ? 0 : 5;
                });

        Map<String, Object> summary = svc.getLayerSummary();

        @SuppressWarnings("unchecked")
        Map<String, Object> semantic = (Map<String, Object>) summary.get(DataLayer.SEMANTIC.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> application = (Map<String, Object>) summary.get(DataLayer.APPLICATION.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> curated = (Map<String, Object>) summary.get(DataLayer.CURATED.name());

        assertEquals(0, semantic.get("count"), "SEMANTIC 现状载体应为 0");
        assertEquals(Boolean.FALSE, semantic.get("declared"), "无载体层必须 declared=false（禁显示'有数据'）");

        assertEquals(Boolean.FALSE, application.get("declared"), "无载体层必须 declared=false");

        assertEquals(Boolean.TRUE, curated.get("declared"), "有载体层 declared=true");
        assertTrue((Integer) curated.get("count") > 0, "CURATED 载体数应 >0");
    }
}
