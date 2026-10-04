package com.chinacreator.gzcm.engine.ai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F06-05 架构护栏（设计-06 §314 要点 1/3，X-22/X-23 收口）。
 *
 * <p>源码级守则：</p>
 * <ol>
 *   <li><b>engineMustNotOwnProviderAbstraction</b>：ai-engine-impl main 源码内
 *       <b>LLMProvider 类型 0 命中</b>（0 实现，预防性拆除，避免将来绕行）；
 *   <li><b>noRawApiKeyFieldInEngineSideRequest</b>：ai-engine-impl main 源码内
 *       <b>{@code request.setApiKey(</b> 0 命中（引擎侧不向 llm-gateway 传明文 key，
 *       X-23 明面，密钥改经 SecurityEngineBridge 服务端解码，engine 不持 key）。
 * </ol>
 *
 * <p>剥离块注释与行注释（同 GuardrailComplianceArchTest 先例），避免
 * Javadoc 提及字面判误命中。</p>
 */
class LlmSingleExitArchTest {

    private static final Pattern PROVIDER_TYPE = Pattern.compile("\\bLLMProvider\\b");
    private static final Pattern SET_API_KEY = Pattern.compile("\\.\\s*setApiKey\\s*\\(");

    @Test
    @DisplayName("engineMustNotOwnProviderAbstraction: ai-engine-impl main 无 LLMProvider 类型命中 (X-22)")
    void engineMustNotOwnProviderAbstraction() throws IOException {
        StringBuilder hits = new StringBuilder();
        for (String src : listMainJavaFiles()) {
            String code = stripComments(src);
            if (PROVIDER_TYPE.matcher(code).find()) {
                hits.append(src).append("\n");
            }
        }
        assertTrue(hits.length() == 0,
                "F06-05 违规 —— ai-engine-impl main 存在 LLMProvider 类型命中（应 0 实施）:\n" + hits);
    }

    @Test
    @DisplayName("noRawApiKeyFieldInEngineSideRequest: ai-engine-impl main 无 .setApiKey( 命中 (X-23)")
    void noRawApiKeyFieldInEngineSideRequest() throws IOException {
        StringBuilder hits = new StringBuilder();
        for (String src : listMainJavaFiles()) {
            String code = stripComments(src);
            if (SET_API_KEY.matcher(code).find()) {
                hits.append(src).append("\n");
            }
        }
        assertTrue(hits.length() == 0,
                "F06-05 违规 —— ai-engine-impl main 存在 .setApiKey( 明文 key 塞入请求（X-23，engine 不持 key）:\n" + hits);
    }

    /** 剥块注释与行注释（同 GuardrailComplianceArchTest 先例） */
    private static String stripComments(String s) {
        String noBlock = s.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlock.replaceAll("//[^\n]*", "");
    }

    private static java.util.List<String> listMainJavaFiles() throws IOException {
        Path root = resolveMainJava();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
    }

    private static Path resolveMainJava() {
        String userDir = System.getProperty("user.dir");
        Path cur = Paths.get(userDir).toAbsolutePath().normalize();
        for (String rel : new String[]{"src/main/java",
                "ai-engine-impl/src/main/java",
                "engine/ai-engine/ai-engine-impl/src/main/java"}) {
            Path cand = cur.resolve(rel);
            if (Files.isDirectory(cand)) return cand;
        }
        throw new IllegalStateException("无法定位 ai-engine-impl/src/main/java (user.dir=" + userDir + ")");
    }
}
