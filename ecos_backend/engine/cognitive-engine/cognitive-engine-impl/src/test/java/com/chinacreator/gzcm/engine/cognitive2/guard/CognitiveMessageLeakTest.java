package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-07 #4 验收（M0 契约面）：认知响应体禁 {@code ApiResponse.*(... e.getMessage())} 泄漏
 * （只入日志/traceId）。范围 = {@link CogScope#inScope}。
 */
class CognitiveMessageLeakTest {

    private static final Pattern LEAK = Pattern.compile(
            "ApiResponse\\s*\\.(?:error|unauthorized|forbidden|notFound|internalError|badRequest)\\s*\\([^)]*\\be\\.getMessage\\(\\)",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("场景认知契约面响应体禁止泄漏 e.getMessage()")
    void noRawExceptionMessageInResponse() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamInScope()) {
            walk.forEach(p -> {
                try {
                    String s = Files.readString(p);
                    if (LEAK.matcher(s).find()) offenders.add(p.toString());
                } catch (IOException ignored) {}
            });
        }
        assertTrue(offenders.isEmpty(),
                "场景认知契约面存在 ApiResponse.* 内 e.getMessage() 泄漏：\n" + String.join("\n", offenders));
    }
}
