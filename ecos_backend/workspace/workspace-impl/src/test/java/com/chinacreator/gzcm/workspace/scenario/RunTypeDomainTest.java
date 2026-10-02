package com.chinacreator.gzcm.workspace.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F07-08 / C154 / REQ-WS-04 run_type 与 run_mode 值域终态门禁。
 *
 * <p>§8.1 矩阵点名项 {@code RunTypeDomainTest#runTypeValuesEqualAdjudicatedVocabularyAndRunModeIsOrthogonal}：
 * R-25 ① 裁决后 run_type 权威值域 = 代码那套 {@code {DIAGNOSE,FORECAST,SIMULATE,STRATEGY,SAFEGUARD}}
 * （V125 注释那套含 {@code FULL} 无 {@code SAFEGUARD} 属历史值域，G2-5 关闭时出清），run_mode
 * 正交列值域 = {@code {FORMAL,SANDBOX}}；两者语义正交（"算什么" vs "是否演练"），零交集——
 * 同一列承载两套语义是 X-33 三套值域打架的根源（PRD-08 §4.1 二次修订：从"在 run_type 塞
 * FORMAL/SANDBOX"改"新增 run_mode 列"，见本卷 §7.1 line 1198）。</p>
 *
 * <p>本类读 {@link ScenarioRunService} 的 {@code private static final Set<String>
 * ALLOWED_RUN_TYPES / ALLOWED_RUN_MODES} 两个单源常量做值级断言，禁 SSOT 派发解脱
 * （两处各写一份词表即 X-33 复辟）。</p>
 */
class RunTypeDomainTest {

    /** R-25 ① 裁决：run_type 终态值域 = 代码那套（含 SAFEGUARD，无 FULL）。 */
    private static final Set<String> ADJUDICATED_RUN_TYPES =
            Set.of("DIAGNOSE", "FORECAST", "SIMULATE", "STRATEGY", "SAFEGUARD");

    /** G2-5 G2 解法：run_mode 正交列值域 = {FORMAL, SANDBOX}。 */
    private static final Set<String> ADJUDICATED_RUN_MODES = Set.of("FORMAL", "SANDBOX");

    /** 历史 X-33 分叉值：V125 注释那套含 FULL，代码那套没有；R-25 ① 选代码那套 ⇒ FULL 必出清。 */
    private static final String LEGACY_V125_VALUE = "FULL";

    @Test
    @DisplayName("run_type 词表 = R-25 ① 裁决终态（代码那套：DIAGNOSE/FORECAST/SIMULATE/STRATEGY/SAFEGUARD）")
    void runTypeVocabularyEqualsAdjudicatedSet() {
        Set<String> actual = readVocabulary("ALLOWED_RUN_TYPES");
        assertEquals(ADJUDICATED_RUN_TYPES, actual,
                "run_type 权威值域必须与 R-25 ① 批准的终态一致（PRD-08 §4.1 修订后红线）");
    }

    @Test
    @DisplayName("run_mode 词表 = {FORMAL, SANDBOX}（G2-5 G2 解法：正交列，非 run_type 扩列）")
    void runModeVocabularyEqualsFormalSandbox() {
        Set<String> actual = readVocabulary("ALLOWED_RUN_MODES");
        assertEquals(ADJUDICATED_RUN_MODES, actual,
                "run_mode 正交列值域必须恰为 FORMAL|SANDBOX（禁止扩列，B-5 只增不改：一动 run_type 值域即 G2-5 复辟）");
    }

    @Test
    @DisplayName("正交红线：run_type 与 run_mode 值域零交集（防同一列承载两套语义回潮）")
    void runTypeAndRunModeVocabulariesDisjoint() {
        Set<String> types = readVocabulary("ALLOWED_RUN_TYPES");
        Set<String> modes = readVocabulary("ALLOWED_RUN_MODES");
        Set<String> overlap = new HashSet<>(types);
        overlap.retainAll(modes);
        assertTrue(overlap.isEmpty(),
                "正交语义红线：run_type（『算什么』）与 run_mode（『是否演练』）值域必须零交集；交集=" + overlap);
    }

    @Test
    @DisplayName("反向：run_mode 任何值不得混入 run_type（PRD-08 §4.1 修订防回锁）")
    void runModeValuesAbsentFromRunTypeVocabulary() {
        Set<String> types = readVocabulary("ALLOWED_RUN_TYPES");
        for (String m : ADJUDICATED_RUN_MODES) {
            assertFalse(types.contains(m),
                    "run_mode 值 (=\"" + m + "\") 不得混入 run_type 值域——X-33 三套值域打架的解药，G2-5 关闭证明");
        }
    }

    @Test
    @DisplayName("历史 V125 注释分叉值 FULL 必出清（R-25 ① 选代码那套，FULL 无下落层）")
    void legacyV125FullIsRemovedNotAccidental() {
        Set<String> types = readVocabulary("ALLOWED_RUN_TYPES");
        assertFalse(types.contains(LEGACY_V125_VALUE),
                LEGACY_V125_VALUE + " 属 V125 注释那套历史值域（X-33），R-25 ① 裁决选代码那套——"
                        + LEGACY_V125_VALUE + " 必不在终态；若一日被加回即等于 G2-5 复辟");
    }

    private static Set<String> readVocabulary(String fieldName) {
        try {
            Field f = ScenarioRunService.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            Object raw = f.get(null);
            if (!(raw instanceof Set<?> s)) {
                throw new AssertionError("常量字段 " + fieldName + " 必须为 Set<String>，实为 " + raw.getClass());
            }
            @SuppressWarnings("unchecked")
            Set<String> out = (Set<String>) s;
            return out;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("常量字段 " + fieldName + " 必须存在于 ScenarioRunService（F07-08 单源词表）", e);
        }
    }
}
