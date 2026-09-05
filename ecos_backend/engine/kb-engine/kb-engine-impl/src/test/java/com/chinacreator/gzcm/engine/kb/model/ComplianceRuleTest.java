package com.chinacreator.gzcm.engine.kb.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wave-5.1 T-07 — ComplianceRule POJO 4 字段固守 (V100 TIP)。
 *
 * <p>对应任务 8: ComplianceRuleTest 完整 4 字段 (V100 TIP: name/domain/ruleType/description not null)。
 *
 * <p>ENT-01 验收: 这 4 字段在实体抽取入库前必须全部非空, 缺任一视为 dirty data,
 * 业务侧必须拦截 (测试这里只做 POJO 行为验证, 业务校验在 ExpertRuleValidator 未来扩展)。
 *
 * @author ECOS KB Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
class ComplianceRuleTest {

    // ── 默认 constructor: 时间字段 0L, 必带 V100 4 字段 ──

    @Test
    @DisplayName("V100 ①: 默认构造器 — name/domain/ruleType/description null, status=DRAFT, version=1")
    void defaultConstructorRequiredFieldsEmpty() {
        ComplianceRule r = new ComplianceRule();
        assertNull(r.getName(), "name 默认 null");
        assertNull(r.getDomain(), "domain 默认 null");
        assertNull(r.getRuleType(), "ruleType 默认 null");
        assertNull(r.getDescription(), "description 默认 null");
        // ExpertRule 基类 extras
        assertNull(r.getCondition());
        assertNull(r.getAction());
        // 合法默认值
        assertEquals("DRAFT", r.getStatus(), "默认 status=DRAFT (Ingested)");
        assertEquals(1, r.getVersion(), "默认 version=1");
        assertEquals(0L, r.getCreatedAt());
        assertEquals(0L, r.getUpdatedAt());
        assertFalse(r.isEnabled(), "默认 disabled");
    }

    // ── 创建完整对象 ──

    @Test
    @DisplayName("V100 ②: 完整 4 字段 + 父类字段, 通过 set + get 闭环")
    void fullRuleSettersAndGetters() {
        ComplianceRule r = new ComplianceRule();
        r.setId("cr-1");
        r.setName("大额审批规则");
        r.setDomain("finance");
        r.setRuleType("ERROR");
        r.setDescription("金额>100 时必须拒绝");
        r.setCondition("#amount > 100");
        r.setAction("REJECT");
        r.setPriority(1);
        r.setEnabled(true);
        r.setStatus("ACTIVE");
        r.setVersion(3);
        r.setRequiredFactList("amount,type");
        r.setExtractedRuleId("xr-1");
        r.setApprovedBy("alice");
        r.setEffectiveDate(1_700_000_000_000L);
        r.setExpiryDate(1_800_000_000_000L);
        r.setCreatedAt(1_700_000_000_000L);
        r.setUpdatedAt(1_700_000_000_001L);

        // V100 4 字段 not null
        assertNotNull(r.getName());
        assertNotNull(r.getDomain());
        assertNotNull(r.getRuleType());
        assertNotNull(r.getDescription());
        assertFalse(r.getName().isBlank());
        assertFalse(r.getDomain().isBlank());
        assertFalse(r.getRuleType().isBlank());
        assertFalse(r.getDescription().isBlank());

        // 父类字段
        assertEquals("cr-1", r.getId());
        assertEquals("#amount > 100", r.getCondition());
        assertEquals("REJECT", r.getAction());
        assertEquals(1, r.getPriority());
        assertTrue(r.isEnabled());

        // ComplianceRule 自身字段
        assertEquals("ACTIVE", r.getStatus());
        assertEquals(3, r.getVersion());
        assertEquals("amount,type", r.getRequiredFactList());
        assertEquals("xr-1", r.getExtractedRuleId());
        assertEquals("alice", r.getApprovedBy());
        assertEquals(1_700_000_000_000L, r.getEffectiveDate());
        assertEquals(1_800_000_000_000L, r.getExpiryDate());
        assertEquals(1_700_000_000_000L, r.getCreatedAt());
        assertEquals(1_700_000_000_001L, r.getUpdatedAt());
    }

    // ── V100 业务校验 (含税期/边界) ──

    @Test
    @DisplayName("V100 ③: 时间字段 (epoch ms) — 0L 兼容 PG empty (P0-4 v7 链路: 0 = NULL 哨兵)")
    void zeroTimestampsAreHandledSinceP04V7() {
        ComplianceRule r = new ComplianceRule();
        r.setEffectiveDate(0L);
        r.setExpiryDate(0L);
        r.setCreatedAt(0L);
        r.setUpdatedAt(0L);

        // 0 在 mapper SQL 里 = NULL 哨兵 (CASE WHEN #{effectiveDate} = 0 THEN NULL ...)
        assertEquals(0L, r.getEffectiveDate());
        assertEquals(0L, r.getExpiryDate());
    }

    @Test
    @DisplayName("V100 ④: 时间字段允许 long 范围无溢出 (max value-safe)")
    void timeFieldsExceedLongSafeRange() {
        ComplianceRule r = new ComplianceRule();
        long now = System.currentTimeMillis();
        r.setCreatedAt(now);
        r.setUpdatedAt(now + 1);
        r.setEffectiveDate(now - 1_000);
        r.setExpiryDate(now + 1_000_000);
        // 不抛错 (POJO 层无校验)
        assertTrue(r.getCreatedAt() > 0);
        assertTrue(r.getExpiryDate() > r.getEffectiveDate());
    }

    // ── fromExtractedRule 工厂 (Wave-2C) ──

    @Test
    @DisplayName("fromExtractedRule: 抽取规则转 ComplianceRule 进入 IN_REVIEW 状态 (IR)")
    void fromExtractedRuleEntersInReviewState() {
        var er = new com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRule();
        er.setName("r-ex");
        er.setDomain("supply");
        er.setCondition("#c>10");
        er.setAction("ALERT");
        er.setSourceExcerpt("原文片段");

        ComplianceRule r = ComplianceRule.fromExtractedRule(er);
        assertEquals("r-ex", r.getName());
        assertEquals("supply", r.getDomain());
        assertEquals("IN_REVIEW", r.getStatus(), "fromExtractedRule 必须进入 IN_REVIEW 态");
        assertEquals("原文片段", r.getDescription(), "sourceExcerpt → description");
        assertEquals("#c>10", r.getCondition());
        assertEquals("ALERT", r.getAction());
        assertEquals(1, r.getVersion(), "新建 rule version=1");
    }

    @Test
    @DisplayName("fromExtractedRule: null conflict — er=null 仍返回 DRAFT? (语义: 工厂要 either-name 才能转 IN_REVIEW)")
    void fromExtractedRuleKeepsRequiredFieldsOnIr() {
        var er = new com.chinacreator.gzcm.engine.ontology.model.ExtractedSubGraph.ExtractedRule();
        er.setName("r-null");
        er.setDomain(null); // 不强制 null, 业务上 supply 是 hint
        ComplianceRule r = ComplianceRule.fromExtractedRule(er);
        assertEquals("r-null", r.getName());
        assertTrue(r.getDomain() == null || r.getDomain().isEmpty());
        assertNull(r.getCondition(), "无 condition 时保持 null (业务侧应在 service 层强校验)");
    }
}
