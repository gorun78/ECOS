package com.chinacreator.gzcm.engine.cognitive2.guard;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveModelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REQ-COG-07 规格 2 / 详设-05 §8.1 硬边界 · CM-07 数值禁令守卫（冻结验收名 §8.2-2：
 * {@code Cm07NumericGuardTest#llmTypeCannotWriteProbabilityOrAmount}）。
 *
 * <p>契约：CM-07（LLM 假设·语义推理）只能产 hypothesis；金额、比率、概率只准来自
 * CM-01~CM-05 或 data-engine 事实，CM-07/LLM 执行路径<b>不得</b>写
 * {@code ecos_cognitive_belief.probability}（含 distribution 载体）或任何金额/比率列；
 * grade 禁 LLM 回填。实现 = 源码级守卫（与 {@code CognitiveNoLlmNumbersTest} 同 harness：
 * {@link CogScope#streamMainAll()} + 剥注释后扫描，注释里的"零参与"免责声明不算数）。</p>
 *
 * <p>三层断言：
 * ① 全 cognitive2 生产源中，"含数值写 SQL 的文件"不得同时含代码级 LLM 通道
 *    （{@code LLMGatewayService} import / {@code runtime.llm}）或 CM-07 类型分支；
 * ② 注册中心本批新面（{@code ModelRegistryDao/Service/ModelPublishGateEvaluator}）
 *    不得出现任何 probability/distribution/amount/ratio 写形态；
 * ③ 契约锚点：{@link CognitiveModelType#numericWritesForbidden()} 仅 CM-07 为 true
 *    （枚举单源声明禁写面，新增数值禁令类型必须先改契约）。</p>
 */
class Cm07NumericGuardTest {

    /** 数值列写形态（SQL 字面量级：INSERT 列面 / SET 赋值；表名限认知域心智/结果列面）。 */
    private static final Pattern NUMERIC_WRITE = Pattern.compile(
            "(?i)(INSERT\\s+INTO\\s+[\\w.]+[^;]{0,400}?\\b(probability|distribution|amount|ratio)\\b"
            + "|SET\\s+[^;]{0,400}?\\b(probability|distribution|amount|ratio)\\s*=)");

    /** 代码级 LLM 通道（stripComments 后仍命中 ⇒ 真实调用面，非注释自辩）。 */
    private static final Pattern LLM_CHANNEL = Pattern.compile(
            "\\bLLMGatewayService\\b|com\\.chinacreator\\.gzcm\\.runtime\\.llm");

    /** CM-07 类型分支（枚举常量或存储字面量）。 */
    private static final Pattern CM07_BRANCH = Pattern.compile("\\bCM_07\\b|\"CM-07\"");

    private static final List<String> REGISTRY_SURFACE_FILES = List.of(
            "ModelRegistryDao.java", "ModelRegistryService.java", "ModelPublishGateEvaluator.java");

    @Test
    @DisplayName("CM-07/LLM 执行路径禁写 probability/金额/比率列（REQ-COG-07 规格 2）")
    void llmTypeCannotWriteProbabilityOrAmount() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<String> registrySurfaceHits = new ArrayList<>();

        try (var walk = CogScope.streamMainAll()) {
            walk.forEach(p -> {
                String src;
                try {
                    src = CogScope.stripComments(Files.readString(p));
                } catch (IOException ignored) {
                    return;
                }
                boolean writesNumeric = NUMERIC_WRITE.matcher(src).find();
                if (writesNumeric) {
                    if (LLM_CHANNEL.matcher(src).find()) {
                        offenders.add("数值写 + LLM 通道: " + p.getFileName());
                    }
                    if (CM07_BRANCH.matcher(src).find()) {
                        offenders.add("数值写 + CM-07 分支: " + p.getFileName());
                    }
                }
                String name = p.getFileName().toString();
                if (REGISTRY_SURFACE_FILES.contains(name) && writesNumeric) {
                    registrySurfaceHits.add(name);
                }
            });
        }
        assertTrue(registrySurfaceHits.isEmpty(),
                "注册中心新面出现数值列写形态（规格 2：注册中心只碰模型资产列）：" + registrySurfaceHits);
        assertTrue(offenders.isEmpty(),
                "CM-07/LLM 可达路径写 probability/amount/ratio（REQ-COG-07 规格 2 硬边界违反）：\n"
                        + String.join("\n", offenders));

        // ③ 契约锚点：CM-07 唯一数值禁写类型（CM-01~CM-06 可写由 CM-01~05/data-engine 事实来源裁决）
        assertTrue(CognitiveModelType.CM_07.numericWritesForbidden(),
                "CM-07 数值禁令锚点漂移（契约面必须先改 CognitiveModelType）");
        for (CognitiveModelType t : CognitiveModelType.values()) {
            if (t != CognitiveModelType.CM_07) {
                assertFalse(t.numericWritesForbidden(), "非 CM-07 类型不应带数值禁令: " + t);
            }
        }
    }
}
