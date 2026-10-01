package com.chinacreator.gzcm.engine.kb.support;

import com.chinacreator.gzcm.engine.kb.dto.EntityInstanceExtractionReportVO;
import com.chinacreator.gzcm.engine.kb.service.KbEntityInstanceExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F04-05 / REQ-KB-01 C3 属性完整性校验验收（详细设计-04 验收标识逐条对应）。
 *
 * <p>三规则全 WARN 不阻断（I-3）：
 * <ul>
 *   <li>R2 必填缺失 → 记 C3_MISSING_REQUIRED，不阻断写入（{@code missingRequiredProducesWarnButStillWrites}）；</li>
 *   <li>R1 未知属性 → 记 C3_UNKNOWN_ATTR，不阻断（{@code unknownAttributeWarnsAndStillWrites}）；</li>
 *   <li>R3 类型不符 → 记 C3_TYPE_MISMATCH 且置 null 不写入（{@code typeMismatchWarnsAndNullifiesField}）；</li>
 *   <li>显式关闭 → {@code c3Skipped=true}，禁静默关闭（{@code c3DisabledMarksC3Skipped}）。</li>
 * </ul>
 *
 * <p>纯单测（Mockito），不 {@code @SpringBootTest}、不触 DB/Neo4j（对齐 kb-engine-impl 测试口径，禁 assertj）。
 */
class C3AttributeValidationTest {

    private static final String ENTITY = "PROJECT";

    /** R2 必填缺失 → WARN + 仍写入（不阻断、不应置 null）。 */
    @Test
    void missingRequiredProducesWarnButStillWrites() {
        Set<C3AttributeCompletenessValidator.PropertyDef> props = Set.of(
                new C3AttributeCompletenessValidator.PropertyDef("contract_amount", true, "DOUBLE"));
        List<C3AttributeCompletenessValidator.MappingRef> refs = List.of(
                new C3AttributeCompletenessValidator.MappingRef("amount_col", "contract_amount"));
        Map<String, Object> row = Map.of("amount_col", "   "); // 空白 = 视为缺失（isBlank）

        C3AttributeCompletenessValidator.Result result =
                C3AttributeCompletenessValidator.validate(ENTITY, props, Set.of(), refs, row);

        assertTrue(result.issues().stream()
                        .anyMatch(i -> C3AttributeCompletenessValidator.CODE_MISSING_REQUIRED.equals(i.code())),
                "必填缺失应记 C3_MISSING_REQUIRED");
        // 不阻断：必填缺失不置 null（该字段本就为空），错误来源是 C3_MISSING_REQUIRED 而非类型置空
        assertFalse(result.nullifiedRefs().contains("contract_amount"),
                "必填缺失不应触发 R3 置 null");
    }

    /** R1 未知属性 → WARN + 仍写入（不阻断、无已知属性集合时不判 R1）。 */
    @Test
    void unknownAttributeWarnsAndStillWrites() {
        Set<C3AttributeCompletenessValidator.PropertyDef> props = Set.of(
                new C3AttributeCompletenessValidator.PropertyDef("project_name", true, "STRING"));
        List<C3AttributeCompletenessValidator.MappingRef> refs = List.of(
                new C3AttributeCompletenessValidator.MappingRef("extra_col", "ghost_attr"));
        Map<String, Object> row = Map.of("extra_col", "value-1");

        C3AttributeCompletenessValidator.Result result =
                C3AttributeCompletenessValidator.validate(ENTITY, props, Set.of(), refs, row);

        assertTrue(result.issues().stream()
                        .anyMatch(i -> C3AttributeCompletenessValidator.CODE_UNKNOWN_ATTR.equals(i.code())),
                "未知属性应记 C3_UNKNOWN_ATTR");
        assertTrue(result.nullifiedRefs().isEmpty(),
                "未知属性不阻断、不置 null（保留原值写入）");
    }

    /** R3 类型不符 → WARN + 该字段置 null 不写入。 */
    @Test
    void typeMismatchWarnsAndNullifiesField() {
        Set<C3AttributeCompletenessValidator.PropertyDef> props = Set.of(
                new C3AttributeCompletenessValidator.PropertyDef("progress", true, "INTEGER"));
        List<C3AttributeCompletenessValidator.MappingRef> refs = List.of(
                new C3AttributeCompletenessValidator.MappingRef("pct_col", "progress"));
        Map<String, Object> row = Map.of("pct_col", "not-a-number");

        C3AttributeCompletenessValidator.Result result =
                C3AttributeCompletenessValidator.validate(ENTITY, props, Set.of(), refs, row);

        assertTrue(result.issues().stream()
                        .anyMatch(i -> C3AttributeCompletenessValidator.CODE_TYPE_MISMATCH.equals(i.code())),
                "类型不符应记 C3_TYPE_MISMATCH");
        assertTrue(result.nullifiedRefs().contains("progress"),
                "类型不符命中的属性应置 null 不写入");

        // 上层重排：nullify 后 properties 中该引用落 null
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("progress", "not-a-number");
        Map<String, Object> rewritten = C3AttributeCompletenessValidator.nullify(properties, refs,
                result.nullifiedRefs());
        assertTrue(rewritten.containsKey("progress") && rewritten.get("progress") == null,
                "nullify 后该属性应为 null");
    }

    /** 显式关闭 C3 → 报告必标 c3Skipped=true（禁静默关闭）。 */
    @Test
    @SuppressWarnings("unchecked")
    void c3DisabledMarksC3Skipped() {
        com.chinacreator.gzcm.engine.kb.dao.KbEntityInstanceExtractionServiceDao dao =
                org.mockito.Mockito.mock(com.chinacreator.gzcm.engine.kb.dao.KbEntityInstanceExtractionServiceDao.class);
        RestTemplate restTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        // 无可用本体快照 → extract 早退（返回前 c3Skipped 已在开关关闭时置位）
        org.mockito.Mockito.when(dao.selectAllSnapshots()).thenReturn(List.of());

        KbEntityInstanceExtractionService svc =
                new KbEntityInstanceExtractionService(dao, restTemplate, "http://datanet.local", false);
        EntityInstanceExtractionReportVO report = svc.extract(null, "job-c3-off", false);

        assertTrue(report.isC3Skipped(), "c3_enabled=false 时报告必须标 c3Skipped=true");
    }
}
