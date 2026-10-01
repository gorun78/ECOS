package com.chinacreator.gzcm.engine.kb.service.profile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-06 历史画像生成验收（REQ-KB-02，P0 新能力）。
 *
 * <p>只读 CURATED 供数 → 沿退化链分组（首个 n≥10 层命中）→ 统计落库（DRAFT）；
 * 发布置 PUBLISHED + 同 key 旧版转 SUPERSEDED（新版本取代）；已发布不可改（I-6）。
 * 纯内存 fake store + fake CURATED 供数，禁 {@code @SpringBootTest}。
 */
class KbProfileGenerateTest {

    /** 内存 fake：保留引用以便状态变更可见。 */
    static final class FakeStore implements KbProfileStore {
        final List<KbProfileDO> rows = new ArrayList<>();

        @Override
        public List<KbProfileDO> query(String metric, String status, String domain, String keyPrefix) {
            List<KbProfileDO> out = new ArrayList<>();
            for (KbProfileDO r : rows) {
                if (r.getIsDeleted() != 0) continue;
                if (metric != null && !metric.isBlank() && !metric.equals(r.getMetricCode())) continue;
                if (status != null && !status.isBlank() && !status.equals(r.getStatus())) continue;
                if (domain != null && !domain.isBlank() && !domain.equals(r.getDomain())) continue;
                out.add(r);
            }
            return out;
        }

        @Override
        public KbProfileDO findLatestByKey(String key) {
            KbProfileDO best = null;
            for (KbProfileDO r : rows) {
                if (r.getIsDeleted() == 0 && key.equals(r.getProfileKey())) {
                    if (best == null || r.getCreateTime() >= best.getCreateTime()) {
                        best = r;
                    }
                }
            }
            return best;
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

    /** CURATED 供数 fake：交错给退化链各层样本。valuesByLevel 长度须 = 退化链 5 层。 */
    static final class FakeFacts implements KbProfileService.CuratedFactSource {
        final List<List<Number>> valuesByLevel;

        FakeFacts(List<List<Number>> valuesByLevel) {
            this.valuesByLevel = valuesByLevel;
        }

        @Override
        public List<Number> fetchValues(String metric, List<String> dims, String wFrom, String wTo) {
            // 与 ProfileStatsCalculator.DEGRADE_CHAIN 逐层精确对齐（细→粗）：
            //   0=[project,dept,stage] 1=[project_type,dept,stage] 2=[project_type,stage]
            //   3=[dept,stage] 4=[global,stage]
            String key = dims.stream().sorted().reduce("", (a, b) -> a + b + "\u0000");
            if (key.equals("dept\u0000project\u0000stage\u0000")) return valuesByLevel.get(0);
            if (key.equals("dept\u0000project_type\u0000stage\u0000")) return valuesByLevel.get(1);
            if (key.equals("project_type\u0000stage\u0000")) return valuesByLevel.get(2);
            if (key.equals("dept\u0000stage\u0000")) return valuesByLevel.get(3);
            if (key.equals("global\u0000stage\u0000")) return valuesByLevel.get(4);
            return valuesByLevel.get(0);
        }
    }

    static List<Number> ints(int from, int n) {
        List<Number> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(from + i);
        }
        return out;
    }

    static Map<String, String> dims() {
        Map<String, String> m = new HashMap<>();
        m.put("project", "p1");
        m.put("project_type", "ct");
        m.put("dept", "D1");
        m.put("stage", "S1");
        return m;
    }

    @Test
    void groupedByTypeDeptStageWithHighConfidence() {
        // 第 0 层（project×dept×stage）只有 3 条 → 退化；第 1 层（project_type×dept×stage）55 条 → HIGH
        List<List<Number>> byLevel = Arrays.asList(
                ints(1, 3),                 // level 0: 不足 → 退化
                ints(1, 55),                // level 1: 55 ≥ 30 → HIGH，命中
                ints(1, 100), ints(1, 100), ints(1, 100));
        FakeStore store = new FakeStore();
        KbProfileService svc = new KbProfileService(store, new FakeFacts(byLevel));

        KbProfileService.Generated g = svc.generate("delivery_cycle", dims(), "2026-01", "2026-06",
                "kb", "task-1", "analyst");
        assertNotNull(g.row());
        assertEquals(1, g.grouping().levelIndex());
        assertEquals("HIGH", g.row().getConfidence());
        assertEquals("DRAFT", g.row().getStatus());
        assertEquals(Integer.valueOf(55), g.row().getSampleCount());
        assertNotNull(g.row().getP10());
        assertNotNull(g.row().getP50());
        assertNotNull(g.row().getP90());
        // 命中级更细层 → 有退化来源
        assertTrue(g.row().getDegradeFrom() != null && !g.row().getDegradeFrom().isBlank());
    }

    @Test
    void insufficientSampleDegradesAndRecordsDegradeFrom() {
        // 第 0 层仅 4 条、第 1 层仅 5 条（均 <10）→ 退化到第 2 层 12 条（10≤n<30 MEDIUM）
        List<List<Number>> byLevel = Arrays.asList(
                ints(1, 4),
                ints(1, 5),
                ints(1, 12),
                ints(1, 100),
                ints(1, 100));
        FakeStore store = new FakeStore();
        KbProfileService svc = new KbProfileService(store, new FakeFacts(byLevel));

        KbProfileService.Generated g = svc.generate("defect_rate", dims(), "2026-01", "2026-06",
                "kb", "task-2", "analyst");
        assertEquals(2, g.grouping().levelIndex());
        assertEquals("MEDIUM", g.row().getConfidence());
        assertEquals(Integer.valueOf(12), g.row().getSampleCount());
        // 退化来源 = 上一更细层（第 1 层）的规范化 profileKey
        assertTrue(g.row().getDegradeFrom() != null && !g.row().getDegradeFrom().isBlank());
    }

    @Test
    void republishCreatesNewVersionAndSupersedesOld() {
        List<List<Number>> byLevel = Arrays.asList(
                ints(1, 40), ints(1, 100), ints(1, 100), ints(1, 100), ints(1, 100));
        FakeStore store = new FakeStore();
        KbProfileService svc = new KbProfileService(store, new FakeFacts(byLevel));

        KbProfileService.Generated first = svc.generate("cost_index", dims(), "2026-01", "2026-06",
                "kb", "task-a", "analyst");
        svc.publish(first.row().getId(), "admin");
        assertEquals("PUBLISHED", first.row().getStatus());
        String v1 = first.row().getProfileVersion();

        // 同 key 二次生成 + 发布 → 新版本，旧 PUBLISHED 转 SUPERSEDED
        KbProfileService.Generated second = svc.generate("cost_index", dims(), "2026-07", "2026-12",
                "kb", "task-b", "analyst");
        svc.publish(second.row().getId(), "admin");

        assertNotNull(second.row().getProfileVersion());
        assertNotEquals(v1, second.row().getProfileVersion());
        assertEquals("PUBLISHED", second.row().getStatus());

        // 发布后同 key 只剩一个 PUBLISHED（second），first 转 SUPERSEDED
        List<KbProfileDO> published = store.query("cost_index", "PUBLISHED", null, null);
        assertEquals(1, published.size());
        assertEquals(second.row().getId(), published.get(0).getId());
        List<KbProfileDO> superseded = store.query("cost_index", "SUPERSEDED", null, null);
        assertEquals(1, superseded.size());
        assertEquals(first.row().getId(), superseded.get(0).getId());
    }

    @Test
    void publishedProfileUpdateIsRejected() {
        List<List<Number>> byLevel = Arrays.asList(
                ints(1, 40), ints(1, 100), ints(1, 100), ints(1, 100), ints(1, 100));
        FakeStore store = new FakeStore();
        KbProfileService svc = new KbProfileService(store, new FakeFacts(byLevel));

        KbProfileService.Generated g = svc.generate("margin", dims(), "2026-01", "2026-06",
                "kb", "task-c", "analyst");
        svc.publish(g.row().getId(), "admin");

        // 断言已有 PUBLISHED 行不可改（I-6）→ 409
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.assertUpdatable(g.row().getId()));
        assertTrue(ex.getMessage().contains("ECOS-KB-061"), "应带 PUBLISHED_IMMUTABLE 错误码");
    }
}
