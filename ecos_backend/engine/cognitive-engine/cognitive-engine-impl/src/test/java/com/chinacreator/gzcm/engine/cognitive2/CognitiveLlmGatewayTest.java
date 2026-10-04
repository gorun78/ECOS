package com.chinacreator.gzcm.engine.cognitive2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 分册05 B-5 / 铁律 §2.5 离线护栏：LLM/调度/事件/存储一律经 runtime 底座，禁引擎自建侧通道。
 *
 * <p>认知 impl 现况（实查）：5 处裸 {@code new RestTemplate(...)} 建客户端——
 * EngineCapabilityRegistryImpl / EntityLinker / ScenarioSimulatorServiceImpl / SuggestionBuilder
 * 各 1 处、KbRestClientConfig 内 1 处工厂造 {@code new RestTemplate(factory)} 系<b>配置侧</b>
 * 可接受形态。本护栏只做<b>棘轮</b>（不越界代改存量）：</p>
 * <ul>
 *   <li>{@code new RestTemplate()} 全体命中 &le; 5——2026-10-04 实测基线，新代码引入 1 处即红；</li>
 *   <li>硬编码 {@code http://localhost:8080/api/v1/agent-loop/chat}（SuggestionBuilder:34）属
 *       硬编码 endpoint 越界证据，本护栏<b>同月同批</b>延期至 R-14 组切 llm-gateway 通道再收，
 *       此处先不增门，只登记。</li>
 * </ul>
 */
final class CognitiveLlmGatewayTest {

    private static final Pattern NEW_REST_TEMPLATE =
        Pattern.compile("new\\s+RestTemplate\\s*\\(");

    @Test
    @DisplayName("认知 impl 内 new RestTemplate 只减不增（baseline 5：4 服务侧 + 1 配置侧；新引入即红）")
    void noNewRestTemplateBeyondBaseline() throws IOException {
        List<Path> files = CognitiveDocPaths.cognitiveMainJava();
        int hits = CognitiveDocPaths.countHits(files, NEW_REST_TEMPLATE);
        assertTrue(hits <= 5,
            "new RestTemplate() 命中 %d > 5（超出 2026-10-04 基线；认知侧不走 llm-gateway 之外新通道）"
                .formatted(hits));
    }
}
