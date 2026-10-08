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
 * F05-07 / C114 验收：认知域禁 {@code catch (Exception) { return Collections.empty*(); }} 空集回落。
 *
 * <p>本批 M0 验收范围 = E1~E4 场景认知契约面（{@link CogScope#inScope}）；legacy 全域 fail-open 收敛
 * 属 F05-13/其余批次 M1/M2（已在 C 系列登记），不在本批门禁。</p>
 */
class CognitiveNoEmptyFallbackTest {

    private static final Pattern EMPTY_FALLBACK = Pattern.compile(
            "catch\\s*\\(\\s*(?:Exception|Throwable)\\b[^)]*\\)\\s*\\{\\s*return\\s+Collections\\.empty\\w+\\s*\\(\\s*\\);",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("场景认知契约面禁 catch-Exception → Collections.empty*() 回落")
    void noCatchAllReturningEmptyCollection() throws IOException {
        List<String> offenders = new ArrayList<>();
        CogScope.streamInScope().forEach(p -> {
            try {
                String src = Files.readString(p);
                var m = EMPTY_FALLBACK.matcher(src);
                while (m.find()) offenders.add(p + ":" + CogScope.lineOf(src, m.start()));
            } catch (IOException ignored) {}
        });
        assertTrue(offenders.isEmpty(),
            "场景认知契约面存在 catch-Exception → empty*() 空集回落（X-39/X-40，F05-07 第 3 项）：\n"
            + String.join("\n", offenders));
    }
}
