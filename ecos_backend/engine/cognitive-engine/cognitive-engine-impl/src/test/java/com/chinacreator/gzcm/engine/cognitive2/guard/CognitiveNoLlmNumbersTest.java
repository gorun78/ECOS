package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-12 §0.2.1 / B-2：认知 chains.contribution / score 数值禁由 LLM 响应直接生成。
 * 验收范围 = 场景认知契约面（{@link CogScope#inScope}）。
 */
class CognitiveNoLlmNumbersTest {

    private static final Pattern FORBIDDEN_ASSIGN = Pattern.compile(
            "(contribution|score)\\s*[:=]\\s*[^;]*\\bllm(Resp|Response|Answer|Output)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("chains.contribution / score 禁直接取 LLM 响应数值")
    void chainsContributionScoreNeverFromLlmResponse() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String s = Files.readString(p);
                    if (FORBIDDEN_ASSIGN.matcher(s).find()) offenders.add(p.toString());
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "场景认知契约面 chains.contribution / score 直接取 LLM 响应数值（B-2 违反）：\n"
                        + String.join("\n", offenders));
    }
}
