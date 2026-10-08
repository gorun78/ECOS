package com.chinacreator.gzcm.engine.cognitive2.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F05-15 铁律 §2.5-2：LLM 调用一律经 llm-gateway（`runtime/llm-gateway`）。认知域内禁止
 * <br/>
 * <ol>
 *   <li>直接 import 任何 deepseek / openai / langchain4j 客户端；</li>
 *   <li>直连 http(s) 到 llm 域名的裸 HTTP 调用；</li>
 * </ol>
 * 允许 {@code LlmGatewayClient} / {@code LlmServiceBean} 类名为网关合法入口。
 */
class CognitiveLlmGatewayTest {

    private static final Path COG_IMPL = Path.of("../../../..")
            .toAbsolutePath().normalize()
            .resolve("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java");

    /** 直接 import 三家 LLM SDK 都判违规。 */
    private static final Pattern FORBIDDEN_IMPORT = Pattern.compile(
            "^import\\s+(?:com\\.openai|dev\\.langchain4j|com\\.deepseek|io\\.github\\.stdioai|co\\.hereapi)\\.",
            Pattern.MULTILINE);

    /** 直连 llm 域名 */
    private static final Pattern DIRECT_URL = Pattern.compile(
            "https?://[\\w\\-.]+(?:deepseek|openai|anthropic|prod\\.llm)[.\\-/]",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("认知 impl 无直接 LLM SDK import / 无直连 llm 域名 HTTP 调用")
    void noLlmCallOutsideGateway() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(COG_IMPL)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String s;
                try { s = Files.readString(p); } catch (IOException e) { return; }
                if (FORBIDDEN_IMPORT.matcher(s).find()) offenders.add("import:" + p);
                if (DIRECT_URL.matcher(s).find()) offenders.add("url:" + p);
            });
        }
        assertTrue(offenders.isEmpty(),
                "认知 impl 存在 F05-15 禁 LLM 绕网关情形：\n" + String.join("\n", offenders));
    }
}
