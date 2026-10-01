package com.chinacreator.gzcm.engine.kb.service.profile;

import com.chinacreator.gzcm.engine.kb.support.ProfileStatsCalculator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F04-06 画像解析验收（REQ-KB-02）：resolve 需回完整退化链（细→粗）供 UI 可视化与对账。
 */
class KbProfileResolveTest {

    static final class FakeStore implements KbProfileStore {
        final List<KbProfileDO> rows = new ArrayList<>();

        @Override
        public List<KbProfileDO> query(String metric, String status, String domain, String keyPrefix) {
            return new ArrayList<>(rows);
        }

        @Override
        public KbProfileDO findLatestByKey(String key) {
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public KbProfileDO findById(String id) {
            for (KbProfileDO r : rows) {
                if (id.equals(r.getId())) return r;
            }
            return null;
        }

        @Override
        public void insert(KbProfileDO row) {
            if (findById(row.getId()) == null) {
                rows.add(row);
            }
        }
    }

    @Test
    void resolveReturnsFullDegradeChain() {
        FakeStore store = new FakeStore();
        KbProfileService svc = new KbProfileService(store, (metric, dims, f, t) -> List.of());

        KbProfileDO published = new KbProfileDO();
        published.setId("id-1");
        published.setProfileKey("delivery_cycle");
        published.setMetricCode("delivery_cycle");
        published.setStatus("PUBLISHED");
        published.setDomain("kb");
        store.insert(published);

        KbProfileService.ResolveResult r = svc.resolve("delivery_cycle", "PUBLISHED", null);
        assertNotNull(r.profile());
        assertEquals("PUBLISHED", r.profile().getStatus());

        // 退化链应覆盖全退化链 5 层（细→粗）
        assertFalse(r.degradeChain().isEmpty());
        assertEquals(ProfileStatsCalculator.degradeChain().size(), r.degradeChain().size());
        // 最细层在项目维度，最粗层含 global
        assertEquals(ProfileStatsCalculator.chainLevel(0).stream()
                .reduce((a, b) -> a + "×" + b).orElse(""), r.degradeChain().get(0));
        assertEquals(ProfileStatsCalculator.chainLevel(4).stream()
                .reduce((a, b) -> a + "×" + b).orElse(""), r.degradeChain().get(r.degradeChain().size() - 1));
    }
}
