package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessCheckStatus;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.ReadinessLayer;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReadinessGrader} 的确定性定级门禁（详细设计-10 F10-08 验收
 * {@code ReadinessGraderTest#gradeIsDeterministicFromThresholdsNotLlm}；差距分析 P0-8）。
 *
 * <p>守的四件事：①定级是纯函数（同输入同输出、与遍历顺序无关、不触 LLM 门面）；
 * ②规格四条规则各自生效（blocker→D / fail→C / warn→B / 全 pass→A）；
 * ③UNKNOWN 与空集不得洗成好档（禁算 pass 的定级侧对偶）；
 * ④DIKC 逐层档位与缺项清单口径确定（层缺探测＝UNKNOWN，非 pass 全入清单且全序）。</p>
 */
class ReadinessGraderTest {

    private final ReadinessGrader grader = new ReadinessGrader();

    private static ReadinessCheck check(ReadinessLayer layer, String code, ReadinessCheckStatus status) {
        return ReadinessCheck.of(layer, code, status);
    }

    /** 四层全 pass 的基线输入（各条用例在此之上改一处档位，保证差异只来自被测状态）。 */
    private static List<ReadinessCheck> allPass() {
        return List.of(
                check(ReadinessLayer.D, "data.check_coverage", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.D, "data.run_dq", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.I, "onto.check_metric", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.I, "onto.detect_conflict", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.K, "know.select_profiles", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.C, "cog.model_availability", ReadinessCheckStatus.PASS));
    }

    private static List<ReadinessCheck> withStatus(List<ReadinessCheck> base, String checkCode,
                                                   ReadinessCheckStatus status) {
        List<ReadinessCheck> out = new ArrayList<>();
        for (ReadinessCheck c : base) {
            out.add(c.checkCode().equals(checkCode)
                    ? check(c.layer(), c.checkCode(), status)
                    : check(c.layer(), c.checkCode(), c.status()));
        }
        return out;
    }

    // ════════════════════════════════════════════════════════════════
    // ① 具名门禁：确定性 + 非 LLM
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("F10-08 具名门禁：定级由阈值档位确定性算出，输入面与实现面都不接 LLM")
    void gradeIsDeterministicFromThresholdsNotLlm() {
        List<ReadinessCheck> input = withStatus(allPass(), "onto.detect_conflict", ReadinessCheckStatus.FAIL);

        ReadinessGradeResult first = grader.grade(input);
        ReadinessGradeResult second = grader.grade(new ArrayList<>(input));
        assertEquals(first, second, "同一输入必须同一结果（定级可复现是 Readiness 只追加快照的前提）");

        // 与遍历顺序无关：倒序输入不得改变任何一档
        List<ReadinessCheck> reversed = new ArrayList<>(input);
        Collections.reverse(reversed);
        ReadinessGradeResult fromReversed = grader.grade(reversed);
        assertEquals(first.grade(), fromReversed.grade(), "总级不得依赖输入顺序");
        assertEquals(first.layerStatus(), fromReversed.layerStatus(), "层位档位不得依赖输入顺序");
        assertEquals(first.missingItems(), fromReversed.missingItems(), "缺项清单顺序不得依赖输入顺序");
        assertEquals(first.matchedRule(), fromReversed.matchedRule(), "命中规则标识不得依赖输入顺序");

        // 输入面结构性排除文本：定级器只见 (层, 检查码, 档位)，LLM 可填的
        // description/impact 文本列根本没有入口
        List<String> components = new ArrayList<>();
        for (RecordComponent rc : ReadinessCheck.class.getRecordComponents()) {
            components.add(rc.getName());
        }
        assertEquals(List.of("layer", "checkCode", "status"), components,
                "定级输入面出现文本/度量字段即给 LLM 伪造档位留了口子");

        // 实现面结构性排除 LLM 门面：定级器无任何实例字段（无协作者、无可变状态），
        // readiness 包整体不依赖 llm-gateway
        List<String> instanceFields = Arrays.stream(ReadinessGrader.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .toList();
        assertTrue(instanceFields.isEmpty(),
                "定级器出现实例字段＝引入协作者（含 LLM 门面）或可变状态，违背 F10-08「服务端计算」: "
                        + instanceFields);
        JavaClasses readinessClasses = new ClassFileImporter()
                .importPackages("com.chinacreator.gzcm.engine.ai.wagent.readiness");
        noClasses().should().dependOnClassesThat()
                .resideInAPackage("com.chinacreator.gzcm.runtime.llm..")
                .check(readinessClasses);
        assertTrue(readinessClasses.size() >= 3,
                "readiness 包类数异常（应 ≥3：Grader/Check/GradeResult），实测 " + readinessClasses.size());
    }

    // ════════════════════════════════════════════════════════════════
    // ② 规格四条规则
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("规格原文「任一 blocker→D」：一个 blocker 压全级，且优先级高于其它缺档")
    void anyBlockerYieldsD() {
        List<ReadinessCheck> input = withStatus(withStatus(allPass(), "data.run_dq",
                ReadinessCheckStatus.BLOCKER), "know.select_profiles", ReadinessCheckStatus.WARN);
        ReadinessGradeResult result = grader.grade(input);
        assertEquals(ReadinessGrade.D, result.grade(), "blocker 必须直落 D，不得被 warn 稀释成 B");
        assertEquals("BLOCKER_PRESENT->D", result.matchedRule());
    }

    @Test
    @DisplayName("规格原文「≥2 fail→C」：两个 fail ⇒ C")
    void twoFailsYieldC() {
        List<ReadinessCheck> input = withStatus(withStatus(allPass(), "data.check_coverage",
                ReadinessCheckStatus.FAIL), "onto.check_metric", ReadinessCheckStatus.FAIL);
        ReadinessGradeResult result = grader.grade(input);
        assertEquals(ReadinessGrade.C, result.grade());
        assertEquals("FAIL_PRESENT->C", result.matchedRule());
    }

    @Test
    @DisplayName("[裁决1] 恰好 1 个 fail 规格未定义 ⇒ 收紧为 C（禁落空档或降为 B）")
    void singleFailIsTightenedToC() {
        List<ReadinessCheck> input = withStatus(allPass(), "data.check_coverage", ReadinessCheckStatus.FAIL);
        ReadinessGradeResult result = grader.grade(input);
        assertEquals(ReadinessGrade.C, result.grade(),
                "单 fail 若降为 B 即「有失败证据」比「只有告警」评级更高，逻辑倒挂");
        assertEquals("FAIL_PRESENT->C", result.matchedRule());
    }

    @Test
    @DisplayName("规格原文「任一 warn→B」：无 blocker/fail/unknown 时 warn ⇒ B")
    void anyWarnYieldsB() {
        List<ReadinessCheck> input = withStatus(allPass(), "know.select_profiles", ReadinessCheckStatus.WARN);
        ReadinessGradeResult result = grader.grade(input);
        assertEquals(ReadinessGrade.B, result.grade());
        assertEquals("WARN_PRESENT->B", result.matchedRule());
    }

    @Test
    @DisplayName("规格原文「全 pass→A」：四层齐备且全 pass ⇒ A，缺项清单为空")
    void allPassYieldsA() {
        ReadinessGradeResult result = grader.grade(allPass());
        assertEquals(ReadinessGrade.A, result.grade());
        assertEquals("ALL_PASS->A", result.matchedRule());
        assertTrue(result.missingItems().isEmpty(), "全 pass 不得凭空产出缺项");
    }

    // ════════════════════════════════════════════════════════════════
    // ③ UNKNOWN 与空集：禁算 pass 的定级侧对偶
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("[裁决2] UNKNOWN 保守档 C：探测失败不并入 pass，也不得优于有证据失败")
    void unknownIsNotWashedIntoBetterThanFail() {
        List<ReadinessCheck> input = withStatus(allPass(), "cog.model_availability",
                ReadinessCheckStatus.UNKNOWN);
        ReadinessGradeResult result = grader.grade(input);
        assertEquals(ReadinessGrade.C, result.grade(), "UNKNOWN 若给 B/A＝把探测失败洗成基本就绪");
        assertEquals("UNKNOWN_PRESENT->C", result.matchedRule());
        assertTrue(result.missingItems().stream().anyMatch(c -> c.status() == ReadinessCheckStatus.UNKNOWN),
                "UNKNOWN 必须计入缺项（F10-08 原文）");
    }

    @Test
    @DisplayName("[裁决2] 空集不给 A：一个检查项都没有＝四层全无证据 ⇒ C")
    void emptyInputNeverGradesA() {
        ReadinessGradeResult result = grader.grade(List.of());
        assertEquals(ReadinessGrade.C, result.grade());
        assertEquals("NO_CHECK->C", result.matchedRule());
        assertEquals(ReadinessCheckStatus.UNKNOWN, result.layerStatus().get(ReadinessLayer.D));

        assertEquals(ReadinessGrade.C, grader.grade(null).grade(), "null 输入同空集，禁抛 NPE 也禁给 A");
    }

    @Test
    @DisplayName("status 为 null 的探测行归一为 UNKNOWN，不给 pass（fail-closed）")
    void nullStatusNormalizesToUnknown() {
        ReadinessCheck bare = ReadinessCheck.of(ReadinessLayer.K, "know.select_profiles", null);
        assertEquals(ReadinessCheckStatus.UNKNOWN, bare.status());
        ReadinessGradeResult result = grader.grade(List.of(bare));
        assertEquals(ReadinessGrade.C, result.grade());
    }

    // ════════════════════════════════════════════════════════════════
    // ④ 层位聚合与缺项清单
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("V230 layer_* 四列恒有值：逐层取最差档，层无检查项 ⇒ UNKNOWN（裁决3）")
    void layerStatusIsWorstPerLayerAndNeverAbsent() {
        List<ReadinessCheck> input = List.of(
                check(ReadinessLayer.D, "data.check_coverage", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.D, "data.run_dq", ReadinessCheckStatus.WARN),
                check(ReadinessLayer.I, "onto.check_metric", ReadinessCheckStatus.BLOCKER));
        Map<ReadinessLayer, ReadinessCheckStatus> layers = grader.grade(input).layerStatus();

        assertEquals(ReadinessCheckStatus.WARN, layers.get(ReadinessLayer.D), "D 层取最差档 warn 而非 pass");
        assertEquals(ReadinessCheckStatus.BLOCKER, layers.get(ReadinessLayer.I));
        assertEquals(ReadinessCheckStatus.UNKNOWN, layers.get(ReadinessLayer.K), "K 层无检查项＝该层未探测");
        assertEquals(ReadinessCheckStatus.UNKNOWN, layers.get(ReadinessLayer.C));
        assertEquals(4, layers.size(), "DIKC 四层必须齐列（缺列会让前端读不到档位而自行猜）");
    }

    @Test
    @DisplayName("缺项清单：只收非 pass，按「严重度降序 → DIKC 层序 → checkCode」全序排列")
    void missingItemsAreNonPassOnlyInTotalOrder() {
        List<ReadinessCheck> input = List.of(
                check(ReadinessLayer.C, "cog.model_availability", ReadinessCheckStatus.PASS),
                check(ReadinessLayer.K, "know.select_profiles", ReadinessCheckStatus.WARN),
                check(ReadinessLayer.I, "onto.zz_conflict", ReadinessCheckStatus.FAIL),
                check(ReadinessLayer.I, "onto.aa_metric", ReadinessCheckStatus.FAIL),
                check(ReadinessLayer.D, "data.run_dq", ReadinessCheckStatus.BLOCKER),
                check(ReadinessLayer.D, "data.check_coverage", ReadinessCheckStatus.PASS));
        List<ReadinessCheck> missing = grader.grade(input).missingItems();

        assertEquals(List.of("data.run_dq", "onto.aa_metric", "onto.zz_conflict", "know.select_profiles"),
                missing.stream().map(ReadinessCheck::checkCode).toList(),
                "缺项须 blocker→fail(层序→码升序)→warn，pass 不入清单");
        assertEquals(2, grader.grade(input).statusCounts().get(ReadinessCheckStatus.PASS),
                "档位计数供 layer_summary_json 快照用，须与实际一致");
    }

    @Test
    @DisplayName("结果不可变且四列档位快照齐备：接线落库前不会被下游改写")
    void resultIsImmutableSnapshot() {
        ReadinessGradeResult result = grader.grade(withStatus(allPass(), "data.run_dq",
                ReadinessCheckStatus.UNKNOWN));
        assertThrows(UnsupportedOperationException.class, () -> result.layerStatus().put(ReadinessLayer.D,
                ReadinessCheckStatus.PASS), "档位快照可改写＝给「事后洗级」留门");
        assertThrows(UnsupportedOperationException.class, () -> result.missingItems().clear());
    }
}
