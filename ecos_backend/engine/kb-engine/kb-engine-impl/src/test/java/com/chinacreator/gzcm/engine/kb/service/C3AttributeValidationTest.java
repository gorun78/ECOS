package com.chinacreator.gzcm.engine.kb.service;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.service.KbC3AttributeValidator.AttrDef;
import com.chinacreator.gzcm.engine.kb.service.KbC3AttributeValidator.AttrType;
import com.chinacreator.gzcm.engine.kb.service.KbC3AttributeValidator.Result;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * F04-05 C3 属性完整性校验验收测试（REQ-KB-01，mvn -Dtest=C3AttributeValidationTest）。
 * <p>
 * 三规则语义：R1 未知属性 WARN（仍写入、不删除）/ R2 必填缺失 WARN（节点仍写入）/
 * R3 类型不符 WARN 且置 null 不写入。全 WARN 不阻断；{@code c3_enabled=false} 时
 * report 标 {@code c3Skipped=true}（禁静默关闭）。
 *
 * @author ECOS KB Team
 */
class C3AttributeValidationTest {

    private static final String ENTITY = "M_RR";

    /** R2：必填属性缺失 → WARN（C3_MISSING_REQUIRED），且行内既有字段仍写入（不阻断）。 */
    @Test
    void missingRequiredProducesWarnButStillWrites() {
        List<AttrDef> defs = List.of(
                new AttrDef("age", true, AttrType.INTEGER),
                new AttrDef("version", false, AttrType.TEXT));

        // 行内只给了 version，缺必填 age
        Map<String, Object> rowProperties = new LinkedHashMap<>();
        rowProperties.put("version", "v2");

        KbEntityInstanceExtractionService svc = newService(true);
        EntityInstanceExtractionReportVO report = new EntityInstanceExtractionReportVO();
        Map<String, Object> cleaned = svc.runC3(ENTITY, rowProperties, defs, report);

        // R2 WARN 命中，未阻断抽取
        assertTrue(hasIssueCode(report, "C3_MISSING_REQUIRED"), "必填缺失应产生 C3_MISSING_REQUIRED WARN");
        // 仍写入：清洗后属性集保留行内既有字段（version），不丢整行
        assertEquals("v2", cleaned.get("version"), "R2 WARN 不阻断：行内既有字段仍写入节点属性");
        assertFalse(cleaned.containsKey("age"), "缺失字段不在候选集内（未写入脏值）");
    }

    /** R1：候选属性不在实体声明集内 → WARN（C3_UNKNOWN_ATTR），仍照常写入（保留原值不删除）。 */
    @Test
    void unknownAttributeWarnsAndStillWrites() {
        List<AttrDef> defs = List.of(
                new AttrDef("age", false, AttrType.INTEGER),
                new AttrDef("version", false, AttrType.TEXT));

        Map<String, Object> rowProperties = new LinkedHashMap<>();
        rowProperties.put("version", "v2");
        rowProperties.put("legacyField", "orphan"); // 未声明

        Result result = KbC3AttributeValidator.validate(ENTITY, rowProperties, defs);

        assertEquals(List.of("C3_UNKNOWN_ATTR"), codesOf(result), "未知属性 → C3_UNKNOWN_ATTR WARN");
        // 仍写入：清洗后属性集保留 unknown 属性原值（R1 不删除）
        assertNotNull(result.cleanedProperties().get("legacyField"), "R1 未知属性仍照常写入，不删除");
        assertEquals("orphan", result.cleanedProperties().get("legacyField"));
    }

    /** R3：属性值与声明类型不符 → WARN（C3_TYPE_MISMATCH）且置 null 不写入。 */
    @Test
    void typeMismatchWarnsAndNullifiesField() {
        List<AttrDef> defs = List.of(
                new AttrDef("age", false, AttrType.INTEGER),
                new AttrDef("active", false, AttrType.BOOLEAN));

        Map<String, Object> rowProperties = new LinkedHashMap<>();
        rowProperties.put("age", "not-a-number"); // INTEGER 不符
        rowProperties.put("active", "true");         // BOOLEAN 合法

        Result result = KbC3AttributeValidator.validate(ENTITY, rowProperties, defs);

        assertEquals(List.of("C3_TYPE_MISMATCH"), codesOf(result), "类型不符 → C3_TYPE_MISMATCH WARN");
        assertNull(result.cleanedProperties().get("age"), "R3 命中字段置 null 不写入");
        assertEquals("true", result.cleanedProperties().get("active"), "类型合法字段保留原值");
    }

    /** c3_enabled=false → report 标 c3Skipped=true（禁静默关闭）且 runC3 不校验、原样放行。 */
    @Test
    void c3DisabledMarksC3Skipped() {
        List<AttrDef> defs = List.of(new AttrDef("age", true, AttrType.INTEGER));
        Map<String, Object> rowProperties = new LinkedHashMap<>(); // age 缺失

        KbEntityInstanceExtractionService svc = newService(false);
        EntityInstanceExtractionReportVO report = new EntityInstanceExtractionReportVO();
        svc.markC3SkippedIfDisabled(report);
        Map<String, Object> cleaned = svc.runC3(ENTITY, rowProperties, defs, report);

        assertTrue(report.isC3Skipped(), "c3_enabled=false 必须显式标 c3Skipped=true（禁静默关闭）");
        assertEquals(rowProperties, cleaned, "关闭时 runC3 不校验、原样放行");
        assertFalse(hasIssueCode(report, "C3_MISSING_REQUIRED"), "关闭时不产生 C3 校验 issue");
    }

    // ─── helpers ────────────────────────────────

    private static KbEntityInstanceExtractionService newService(boolean c3Enabled) {
        return new KbEntityInstanceExtractionService(
                mock(JdbcTemplate.class), mock(RestTemplate.class),
                "http://localhost:18082", "http://localhost:18083/api/v1", c3Enabled);
    }

    private static boolean hasIssueCode(EntityInstanceExtractionReportVO report, String code) {
        return report.getIssues().stream().anyMatch(i -> code.equals(i.getCode()));
    }

    private static List<String> codesOf(Result result) {
        return result.issues().stream().map(KbC3AttributeValidator.Issue::code).toList();
    }
}
