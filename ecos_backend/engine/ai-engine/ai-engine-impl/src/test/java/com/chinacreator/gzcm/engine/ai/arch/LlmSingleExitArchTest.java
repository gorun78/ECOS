package com.chinacreator.gzcm.engine.ai.arch;

import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 详细设计-06 F06-05「LLM 出口收一」验收门禁（X-22 / X-23 / I-4）。
 *
 * <p>源码级扫描，不看运行态探针：引擎侧一旦重新长出 Provider 抽象或明文 api-key 赋值路径，
 * 本门禁必须失败。扫描目录定位失败一律 fail-loud（禁"找不到目录=通过"）。</p>
 */
@DisplayName("F06-05 LLM 出口收一：引擎侧不得自持 Provider 抽象 / 明文 api-key")
class LlmSingleExitArchTest {

    /** 引擎源码根（全仓所有引擎，不只是 ai-engine） */
    private static final List<String> ENGINE_ROOT_CANDIDATES =
            List.of("engine", "ecos_backend/engine");

    @Test
    @DisplayName("F06-05/X-22 引擎侧 Provider 抽象必须为 0 命中（LLMProvider 类型已删除）")
    void engineMustNotOwnProviderAbstraction() throws IOException {
        Path providerFile = resolveBackendRoot().resolve(
                "engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/LLMProvider.java");
        assertTrue(Files.notExists(providerFile),
                "LLMProvider.java 仍存在（F06-05 要点 1 要求删除）: " + providerFile);

        List<String> hits = new ArrayList<>();
        for (Path file : engineMainSources()) {
            String code = stripComments(Files.readString(file));
            if (containsIdentifier(code, "LLMProvider")) {
                hits.add(relativize(file));
            }
        }
        assertTrue(hits.isEmpty(),
                "引擎侧仍存在 LLMProvider 引用（唯一 LLM 出口 = runtime.llm.gateway.LLMGateway，X-22）: " + hits);
    }

    @Test
    @DisplayName("F06-05/X-23/I-4 引擎侧不得给 ChatRequest.apiKey 赋值；该字段必须为 @Deprecated")
    void noRawApiKeyFieldInEngineSideRequest() throws Exception {
        Field apiKey = ChatRequest.class.getDeclaredField("apiKey");
        assertTrue(apiKey.isAnnotationPresent(Deprecated.class),
                "ChatRequest.apiKey 必须标 @Deprecated（服务端不消费，PMO-74 N14 VG-7）");
        assertNotNull(ChatRequest.class.getMethod("setApiKeyRef", String.class),
                "引擎侧如需指定密钥只能传引用 apiKeyRef");

        List<String> hits = new ArrayList<>();
        for (Path file : engineMainSources()) {
            String code = stripComments(Files.readString(file));
            String flat = code.replaceAll("\\s+", " ");
            if (flat.contains(".setApiKey(") || flat.contains("setApiKey (")) {
                hits.add(relativize(file) + " → setApiKey 调用");
            }
            if (code.contains("llm.deepseek.api-key")) {
                hits.add(relativize(file) + " → 读取明文 api-key 配置项");
            }
        }
        assertTrue(hits.isEmpty(),
                "引擎侧存在明文 api-key 赋值/取用路径（F06-05 要点 3 要求切断，I-4）: " + hits);
    }

    // ── 扫描与工具 ──────────────────────────────────────────────────────

    private static List<Path> engineMainSources() {
        Path root = resolveBackendRoot();
        List<Path> files = new ArrayList<>();
        for (String candidate : ENGINE_ROOT_CANDIDATES) {
            Path engineDir = root.resolve(candidate);
            if (Files.isDirectory(engineDir)) {
                walkJavaMainSources(engineDir, files);
            }
        }
        assertTrue(!files.isEmpty(),
                "引擎源码扫描为空（目录定位失败不得默认通过），root=" + root);
        return files;
    }

    private static void walkJavaMainSources(Path engineDir, List<Path> out) {
        try (Stream<Path> stream = Files.walk(engineDir)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .forEach(out::add);
        } catch (IOException e) {
            fail("扫描引擎源码失败: " + e.getMessage(), e);
        }
    }

    private static Path resolveBackendRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = start; p != null; p = p.getParent()) {
            for (String candidate : List.of("engine", "ecos_backend/engine")) {
                if (Files.isDirectory(p.resolve(candidate))) {
                    return p;
                }
            }
        }
        return fail("未能从 user.dir=" + start + " 定位 engine 目录，LLM 出口门禁不得默认通过");
    }

    private static String relativize(Path file) {
        return file.toString().replace('\\', '/');
    }

    /** 去注释后再匹配，避免文档/日志文案里的端点名造成误判（与 ObligationsApplyTest 同口径）。 */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static boolean containsIdentifier(String code, String identifier) {
        return java.util.regex.Pattern.compile("\\b" + identifier + "\\b")
                .matcher(code).find();
    }
}
