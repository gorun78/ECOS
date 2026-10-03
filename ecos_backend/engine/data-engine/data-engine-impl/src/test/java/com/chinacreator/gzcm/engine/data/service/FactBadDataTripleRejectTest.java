package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-06 / DATA-01（详细设计-02，P0）—— 坏数据三连拒，rejected 明细可定位、不入库。
 *
 * <p>设计 F02-06 验收「{@code FactBadDataTripleRejectTest}（DQ-F01/F02/F09 各 1 行，
 * rejected 含 rowNo/field/ruleId/suggestion）」：逐行门禁的三条代表性规则各构造一行坏行，
 * 断言 {@code ImportResult.rejected} 恰好命中对应 ruleId、字段与修复建议非空、且行号定位正确，
 * 同时三条坏行<b>一律不入库</b>（{@code accepted=0}、{@link JdbcTemplate#batchUpdate} 从不调用）。</p>
 *
 * <ul>
 *   <li>DQ-F01 必填项非空：resource 行缺 {@code project_id}；</li>
 *   <li>DQ-F02 期/月格式：{@code period=2025-7}（非 {@code \d{4}-\d{2}}）；</li>
 *   <li>DQ-F09 工号哈希：resource 行 {@code staff_ref_hash} 非 SHA-256 hex(64)。</li>
 * </ul>
 *
 * <p>判定性单测：Mockito 打桩 {@link JdbcTemplate}，只做内存行级门禁判定，不触库。</p>
 */
@DisplayName("F02-06 坏数据三连拒（DQ-F01/F02/F09，rejected 明细可定位 + 不入库，P0）")
class FactBadDataTripleRejectTest {

    private static final String SHA256_64 =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessFactService svc = new BusinessFactService(jdbc);

    /** 合规 resource 行基线（必填齐全 + period 规整 + hash 合规），逐项破坏以命中目标规则。 */
    private static Map<String, Object> cleanResourceRow() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project_id", "p-1");
        r.put("department_id", "d-1");
        r.put("period", "2025-07");
        r.put("staff_ref_hash", SHA256_64);
        r.put("staff_hash_algo", "SHA-256");
        r.put("fact_type", "WORK");
        return r;
    }

    @Test
    @DisplayName("FactBadDataTripleRejectTest — DQ-F01/F02/F09 各 1 行 → 3 条 rejected，各带 rowNo/field/ruleId/suggestion")
    void tripleBadRows_eachRejectedWithLocatableDetail() {
        List<Map<String, Object>> rows = new ArrayList<>();

        Map<String, Object> f01 = cleanResourceRow();
        f01.put("project_id", null);                 // 必填缺 → DQ-F01

        Map<String, Object> f02 = cleanResourceRow();
        f02.put("period", "2025-7");                 // 期/月格式非法 → DQ-F02

        Map<String, Object> f09 = cleanResourceRow();
        f09.put("staff_ref_hash", "not-a-hash");     // 非 SHA-256 hex(64) → DQ-F09

        rows.add(f01);
        rows.add(f02);
        rows.add(f09);

        BusinessFactService.ImportResult res = svc.importBusinessFacts("resource", "batch-T1", rows);

        assertEquals(0, res.accepted(), "三条坏行均不得 accepted");
        assertEquals(3, res.rejected().size(), "应产生 3 条 rejected 明细");

        BusinessFactService.RejectedRow f01r = res.rejected().get(0);
        assertEquals(1, f01r.rowNo(), "DQ-F01 行号应定位第 1 行");
        assertEquals("DQ-F01", f01r.ruleId(), "缺必填项应落 DQ-F01; 实际 " + f01r.ruleId());
        assertTrue(f01r.field().contains("project_id"), "定位字段应指 project_id; 实际 " + f01r.field());

        BusinessFactService.RejectedRow f02r = res.rejected().get(1);
        assertEquals(2, f02r.rowNo(), "DQ-F02 行号应定位第 2 行");
        assertEquals("DQ-F02", f02r.ruleId(), "期/月格式非法应落 DQ-F02; 实际 " + f02r.ruleId());
        assertTrue(f02r.field().contains("period"), "定位字段应指 period; 实际 " + f02r.field());

        BusinessFactService.RejectedRow f09r = res.rejected().get(2);
        assertEquals(3, f09r.rowNo(), "DQ-F09 行号应定位第 3 行");
        assertEquals("DQ-F09", f09r.ruleId(), "工号哈希非法应落 DQ-F09; 实际 " + f09r.ruleId());
        assertTrue(f09r.field().contains("staff_ref_hash"), "定位字段应指 staff_ref_hash; 实际 " + f09r.field());

        for (BusinessFactService.RejectedRow r : res.rejected()) {
            assertTrue(r.message() != null && !r.message().isBlank(), "每条拒绝必须带 message");
            assertTrue(r.suggestion() != null && !r.suggestion().isBlank(),
                    "每条拒绝必须带可执行的修复 suggestion（对齐 B.5 错误表）");
        }
    }

    @Test
    @DisplayName("FactBadDataTripleRejectTest（不入库）— 3 条坏行 accepted=0 ⇒ batchUpdate 从不调用")
    void rejectedRows_neverPersisted() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> bad = cleanResourceRow();
        bad.put("staff_ref_hash", "xx");             // DQ-F09
        rows.add(bad);

        BusinessFactService.ImportResult res = svc.importBusinessFacts("resource", "batch-T2", rows);

        assertEquals(0, res.accepted());
        assertEquals(1, res.rejected().size());
        verify(jdbc, never()).batchUpdate(anyString(), (java.util.List<Object[]>) any());
    }

    @Test
    @DisplayName("FactBadDataTripleRejectTest（反向）— 合规 resource 行不触发任一规则，accepted=1")
    void cleanRow_notRejected() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(cleanResourceRow());
        BusinessFactService.ImportResult res = svc.importBusinessFacts("resource", "batch-T3", rows);
        assertEquals(1, res.accepted(), "合规行应 accepted（不误伤）");
        assertTrue(res.rejected().isEmpty(), "合规行不应产生 rejected");
    }
}
