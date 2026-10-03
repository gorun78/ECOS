package com.chinacreator.gzcm.engine.kb.profile;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.chinacreator.gzcm.engine.kb.profile.KbProfileServiceImpl.GenerationResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F04-06 验收测试（resolve 侧：mvn -Dtest=KbProfileResolveTest）。
 * <p>
 * resolveReturnsFullDegradeChain：发布后的画像在 resolve 返回完整退化链（截至命中粒度的各级可读名）。
 *
 * @author ECOS KB Team
 */
class KbProfileResolveTest {

    private KbProfileMapper profileMapper;
    private KbProfileStatsMapper statsMapper;
    private CuratedFactSource factSource;
    private KbProfileServiceImpl service;

    private void buildService() {
        profileMapper = Mockito.mock(KbProfileMapper.class);
        statsMapper = Mockito.mock(KbProfileStatsMapper.class);
        factSource = Mockito.mock(CuratedFactSource.class);
        service = new KbProfileServiceImpl(profileMapper, statsMapper, factSource, null);
    }

    @Test
    void resolveReturnsFullDegradeChain() {
        buildService();

        // 命中 grain idx2（项目类型×环节）→ degradePathJson = 三级可读名（截至 idx2）
        String chainJson = ("项目×部门×环节" + "," + "项目类型×部门×环节" + "," + "项目类型×环节");
        Map<String, Object> row = new HashMap<>();
        row.put("id", "p-1");
        row.put("profileKey", "M_RR|projectType=APP|stage=建设");
        row.put("metricCode", "M_RR");
        row.put("profileVersion", "1.0.0");
        row.put("confidence", "MEDIUM");
        row.put("status", "PUBLISHED");
        row.put("statsRef", "s-1");
        row.put("degradePathJson", chainJson);
        row.put("degradeFrom", "项目类型×部门×环节");
        row.put("traceId", "tr-1");
        when(profileMapper.findByKeyForResolve("M_RR", null)).thenReturn(row);

        Map<String, Object> stats = new HashMap<>();
        stats.put("sampleCount", 12);
        stats.put("missingRate", new BigDecimal("0.0000"));
        stats.put("p10", new BigDecimal("2.0"));
        stats.put("p50", new BigDecimal("6.5"));
        stats.put("p90", new BigDecimal("11.0"));
        stats.put("meanValue", new BigDecimal("6.5"));
        stats.put("ciLow", new BigDecimal("4.0"));
        stats.put("ciHigh", new BigDecimal("9.0"));
        when(statsMapper.findById("s-1")).thenReturn(stats);

        ProfileResolveVO vo = service.resolve("M_RR", null, null);

        assertNotNull(vo);
        assertEquals("p-1", vo.getProfileId());
        assertEquals("M_RR|projectType=APP|stage=建设", vo.getProfileKey());
        assertEquals("MEDIUM", vo.getConfidence());
        // 退化链完整还原（截至命中粒度的 3 级可读名）
        List<String> chain = vo.getDegradeChain();
        assertNotNull(chain);
        assertEquals(3, chain.size(), "退化链应含截至命中粒度的各级");
        assertEquals("项目×部门×环节", chain.get(0));
        assertEquals("项目类型×部门×环节", chain.get(1));
        assertEquals("项目类型×环节", chain.get(2));
        // 数值统计跨 schema 组装（R-8 ②）
        assertNotNull(vo.getStats());
        assertEquals(12L, vo.getStats().getSampleCount());
        assertEquals(new BigDecimal("6.5"), vo.getStats().getP50());
        assertEquals(KbProfileServiceImpl.STATS_METHOD, vo.getStats().getStatsMethod());
        assertEquals("tr-1", vo.getTraceId());
    }

    @Test
    void resolveNullWhenNoRow() {
        buildService();
        when(profileMapper.findByKeyForResolve(anyString(), any())).thenReturn(null);
        assertNull(service.resolve("M_X", null, null), "无命中行 → resolve 返回 null（controller 映射 404）");
    }
}
