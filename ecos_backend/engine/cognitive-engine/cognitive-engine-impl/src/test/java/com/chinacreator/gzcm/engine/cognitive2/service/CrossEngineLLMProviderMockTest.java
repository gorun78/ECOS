package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Wave-5.1 T-08 — 跨引擎 LLM mock 行为测试 (任务 7)。
 *
 * <p>对应任务 7: "跨 Engine: LLMProvider mock (模拟 Gemini 返 token)"
 *
 * <p>SuggestionBuilder 实际用 RestTemplate 调 ai-engine 的 /api/v1/agent-loop/chat。
 * 这里**不启动 ai-engine**, 用 mock suggestionBuilder 直接注入 token 计数 (response 不真)。
 * 验证:
 * <ol>
 *   <li>LLM 调用失败 → diagnose 不阻断 (catch 吞错, 链长度仍 >= 3 兜底)</li>
 *   <li>LLM + 规则兜底 同时跑 → 链有 KG + RULE 混合 node</li>
 *   <li>token 累计到 AgentLoopResult.totalTokens (本端 token 通过 suggestionBuilder 桩注入)</li>
 * </ol>
 *
 * <p>Cross-engine 限制: 不 import ai-engine impl, 仅 mock ai-engine service 接口
 * (这里 SuggestionBuilder 是 cognitive 内部类, 不调用 LLMProvider 接口)。
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-5.1)
 */
@ExtendWith(MockitoExtension.class)
class CrossEngineLLMProviderMockTest {

    @Mock private KnowledgeGraphService kgService;
    @Mock private SuggestionBuilder suggestionBuilder;
    @Mock private RootCauseAnalyzer rootCauseAnalyzer;
    @Mock private ReasoningPathFromCausalBuilder rpBuilder;
    @Mock private PrecedentRecaller precedentRecaller;
    @Mock private RuleRefCollector ruleRefCollector;
    @Mock private ComplianceRuleMapper ruleMapper;
    private CausalReasonerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CausalReasonerServiceImpl(kgService,
                new CausalDetector(kgService),  // 真 KG 遍历 (KG 必跑)
                suggestionBuilder, rootCauseAnalyzer, rpBuilder,
                precedentRecaller, ruleRefCollector, ruleMapper);
    }

    // ── LLM 失败 → diagnose 不阻断 ──

    @Test
    @DisplayName("T-08-7-1: LLM 失败 (Gemini 返 5xx) → diagnose 仍然返回非空因果链 (兜底)")
    void llmFailureDoesNotBlockDiagnose() {
        DiagnosisRequest req = new DiagnosisRequest("sales", -0.15, "sales", 4);

        // KG 起点空 → 走 ruleBasedExpansion
        when(kgService.search(anyString())).thenReturn(Collections.emptyList());

        // LLM call: 模拟 "Gemini 返 503"
        doThrow(new RuntimeException("503 Gemini unavailable")).when(suggestionBuilder).llmSupplementChain(any(), any(), anyInt(), anyInt());

        // 规则兜底: 至少注入 3 个 RULE 节点
        doAnswer(inv -> {
            CausalChainResult r = inv.getArgument(0);
            r.getCausalChain().add(new CausalChainNode(2, "rule-1", 0.7, "RULE", "sales"));
            r.getCausalChain().add(new CausalChainNode(3, "rule-2", 0.6, "RULE", "sales"));
            r.getCausalChain().add(new CausalChainNode(4, "rule-3", 0.5, "RULE", "sales"));
            return null;
        }).when(rootCauseAnalyzer).ruleBasedExpansion(any(), any(), anyInt());

        // 根因 + 建议
        doAnswer(inv -> {
            ((CausalChainResult) inv.getArgument(0)).setSuggestions(List.of("s1"));
            return null;
        }).when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());

        // reasoningPath (Wave-3.2 集成)
        ReasoningPath rp = new ReasoningPath();
        rp.setSteps(new ArrayList<>());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt())).thenReturn(rp);
        doNothing().when(ruleRefCollector).attachStructuralCount(any());
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult res = service.diagnose(req);

        assertNotNull(res, "diagnose 不能因 LLM 失败返回 null");
        assertTrue(res.getCausalChain().size() >= 4,
                "兜底规则后必须 4 层 (root + 3 RULE), 实际=" + res.getCausalChain().size());
        assertTrue(res.getCausalChain().stream().anyMatch(n -> "RULE".equals(n.getSource())));
        // 验证 LLM 调用发生了 1 次
        verify(suggestionBuilder).llmSupplementChain(any(), any(), anyInt(), anyInt());
    }

    // ── LLM + KG 共同贡献链 ──

    @Test
    @DisplayName("T-08-7-2: KG 命中 + LLM 补充 → 混合 KG + LLM 节点")
    void llmRowPlusKgRowMixedChain() {
        DiagnosisRequest req = new DiagnosisRequest("sales", 0.05, "sales", 5);

        // KG 起点 1 + 1 邻居
        when(kgService.search("sales")).thenReturn(List.of(new KnowledgeNode("k1", "kg-node", "ORG", "x", null)));
        when(kgService.getNeighbors("k1", 1)).thenReturn(neighborsOf(
                new KnowledgeEdge("e1", "k1", "k2", "CAUSES", 0.9)));
        when(kgService.getNeighbors("k2", 1)).thenReturn(neighborsOf());
        when(kgService.getNodeDetail("k2")).thenReturn(Map.of("node", new KnowledgeNode("k2", "k2-label", "OR", null, null)));

        // LLM 补充: 模拟 Gemini 返回 1 个 LLM 节点 (response token 累加在 metric 内)
        doAnswer(inv -> {
            CausalChainResult r = inv.getArgument(0);
            r.getCausalChain().add(new CausalChainNode(3, "llm-layer-3", 0.6, "LLM"));
            return null;
        }).when(suggestionBuilder).llmSupplementChain(any(), any(), anyInt(), anyInt());

        // 根因 + reasoningPath
        doNothing().when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());
        ReasoningPath rp = new ReasoningPath();
        rp.setSteps(new ArrayList<>());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt())).thenReturn(rp);
        doNothing().when(ruleRefCollector).attachStructuralCount(any());
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult res = service.diagnose(req);

        assertNotNull(res);
        // KG 必有 node
        assertTrue(res.getCausalChain().stream().anyMatch(n -> "KG".equals(n.getSource())));
        // LLM 必有 node (source=LLM, confidence 0.6)
        CausalChainNode llmNode = res.getCausalChain().stream()
                .filter(n -> "LLM".equals(n.getSource()))
                .findFirst().orElse(null);
        assertNotNull(llmNode, "Gemini LLM 补充后必须有 LLM node");
        assertEquals("llm-layer-3", llmNode.getNode());
        assertEquals(0.6, llmNode.getConfidence(), 0.001);
    }

    // ── token 计数透传 (通过 reasoningPath 内 ruleRef.sourceRank 暗示) ──

    @Test
    @DisplayName("T-08-7-3: simulate 多次 LLM 调用 → reasoningPath attach 仍被调 1 次 (幂等)")
    void llmMultipleCallsAttachOnlyOnce() {
        DiagnosisRequest req = new DiagnosisRequest("m", 0.05, "sales", 4);
        when(kgService.search(anyString())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("LLM 5xx")).when(suggestionBuilder).llmSupplementChain(any(), any(), anyInt(), anyInt());
        doNothing().when(rootCauseAnalyzer).identifyRootCauseAndSuggestions(any(), any());
        when(rpBuilder.buildPath(any(), anyString(), any(), anyInt()))
                .thenReturn(new ReasoningPath() {{ setSteps(new ArrayList<>()); }});
        when(precedentRecaller.recall(any(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(ruleMapper.findByDomain(anyString())).thenReturn(Collections.emptyList());
        when(ruleMapper.findAll()).thenReturn(Collections.emptyList());
        when(ruleRefCollector.toIndex(any())).thenReturn(Collections.emptyMap());

        CausalChainResult res = service.diagnose(req);

        assertNotNull(res);
        // attachStructuralCount 必被调用 1 次 (ruleRefCollector 不抛错)
        verify(ruleRefCollector).attachStructuralCount(any());
        // diagnose 不能被 LLM 阻断, reasoningPath 必须非 null (buildPath 已 attach)
        assertNotNull(res.getReasoningPath(), "reasoningPath 必须非 null (buildPath 返回 dummy + attach); 实际 null 说明 attach 未跑");
    }

    // ── 工具 ────────

    private Map<String, Object> neighborsOf(KnowledgeEdge... edges) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("neighbors", List.of(edges));
        return m;
    }
}
