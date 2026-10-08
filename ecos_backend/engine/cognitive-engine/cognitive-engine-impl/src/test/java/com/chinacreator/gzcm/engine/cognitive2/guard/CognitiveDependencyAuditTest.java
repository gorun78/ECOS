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
 * F05-15 底座收口（X-34/X-35）：
 * <ul>
 *   <li>cognitive-engine-impl pom 不引规则引擎库（drools / easy-rules / mvel）；</li>
 *   <li>cognitive2 <b>全量主源码</b>（{@link CogScope#streamMainAll()}，先剥注释）禁
 *       {@code new RestTemplate()}、硬编码 {@code http://localhost:8080} 基址、
 *       直连 localhost agent-loop、自建 LLM 客户端。</li>
 * </ul>
 *
 * <p>豁免口径：{@code /config/} 包为共享客户端 Bean 工厂（{@code CognitiveRestClientConfig}
 * 集中构造 {@code cognitiveRestTemplate} 并下发超时策略），是铁律允许的唯一构造点，不在禁面内；
 * 业务 Service 只准经构造器注入复用，不准自建（X-35）。</p>
 */
class CognitiveDependencyAuditTest {

    private static final Path COG_POM = CogScope.REPO.resolve(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/pom.xml");

    private static final Pattern RULE_ENGINE = Pattern.compile(
            "<artifactId>\\s*(?:drools-[\\w-]+|easy-rules[\\w-]*|mvel(?:-group)?)[^<]*</artifactId>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEW_REST_TEMPLATE = Pattern.compile("new\\s+RestTemplate\\s*\\(");
    private static final Pattern DIRECT_LLM = Pattern.compile(
            "\\b(?:OpenAiClient|ChatGptClient|LLMClient|LlmRestTemplate)\\b");
    private static final Pattern LOCALHOST_AGENT_LOOP = Pattern.compile(
            "http://localhost:8080[\\s\\S]{0,40}agent-loop");
    private static final Pattern LOCALHOST_BASE = Pattern.compile("http://localhost:8080");
    private static final Pattern SELF_THREAD = Pattern.compile("new\\s+Thread\\s*\\(");

    @Test
    @DisplayName("认知 pom 不引规则引擎依赖 (drools|easy-rules|mvel)")
    void noRuleEngineOnClasspath() throws IOException {
        String pom = Files.readString(COG_POM);
        assertTrue(!RULE_ENGINE.matcher(pom).find(),
                "cognitive-engine-impl/pom.xml 存在规则引擎依赖（F05-12 REQ-COG-05 禁）");
    }

    @Test
    @DisplayName("cognitive2 全量主源码（config 工厂豁免）无 new RestTemplate()/localhost 基址/自建 LLM 绕网关")
    void noDirectRestTemplateOrLlmBypass() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var walk = CogScope.streamMainAll()) {
            walk.forEach(p -> {
                String s;
                try { s = CogScope.stripComments(Files.readString(p)); } catch (IOException e) { return; }
                if (NEW_REST_TEMPLATE.matcher(s).find()) offenders.add("RestTemplate: " + p);
                if (DIRECT_LLM.matcher(s).find()) offenders.add("DirectLLM: " + p);
                if (LOCALHOST_AGENT_LOOP.matcher(s).find()) offenders.add("LocalhostLoop: " + p);
                if (LOCALHOST_BASE.matcher(s).find()) offenders.add("LocalhostBase: " + p);
                if (SELF_THREAD.matcher(s).find()) offenders.add("SelfThread: " + p);
            });
        }
        assertTrue(offenders.isEmpty(),
                "cognitive2 主源码存在 F05-15 禁项（X-34/X-35/X-36，config 共享 Bean 工厂已豁免）：\n"
                        + String.join("\n", offenders));
    }
}
