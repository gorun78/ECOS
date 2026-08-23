package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 根因分析器 — 从因果链定位根因 + 生成改进建议 + 规则引擎兜底。
 *
 * <p>从 CausalReasonerServiceImpl 拆出，依赖 SuggestionBuilder 进行LLM调用。
 */
@Component
public class RootCauseAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(RootCauseAnalyzer.class);

    /** 领域知识库 — 常见指标的因果级联关系 */
    private static final Map<String, String[]> CAUSAL_KNOWLEDGE = Map.of(
        "毛利率", new String[]{
            "2: 原材料价格上涨 推动主营成本上升 (confidence=0.75)",
            "3: 供应链上游大宗商品价格波动 导致原材料涨价 (confidence=0.60)",
            "4: 地缘政治/供需失衡 引发大宗商品市场波动 (confidence=0.50)"
        },
        "利润率", new String[]{
            "2: 运营费用率增长 压缩利润空间 (confidence=0.70)",
            "3: 人力成本上升+管理费用增加 推高运营费用 (confidence=0.60)",
            "4: 行业人才竞争加剧 导致人力成本结构性上涨 (confidence=0.50)"
        },
        "应收账款周转率", new String[]{
            "2: 客户回款周期拉长 降低周转效率 (confidence=0.72)",
            "3: 下游客户自身现金流紧张 延迟付款 (confidence=0.62)",
            "4: 宏观经济下行+信贷紧缩 导致下游流动性不足 (confidence=0.52)"
        },
        "库存周转率", new String[]{
            "2: 销售不及预期 导致库存积压 (confidence=0.68)",
            "3: 市场需求疲软+渠道库存高 制约出货 (confidence=0.58)",
            "4: 消费信心下滑+竞争加剧 削减终端需求 (confidence=0.48)"
        },
        "销售增长率", new String[]{
            "2: 市场份额被竞品侵蚀 增速放缓 (confidence=0.74)",
            "3: 产品竞争力下降+渠道拓展不力 导致市占率下降 (confidence=0.64)",
            "4: 研发投入不足+营销策略失当 削弱产品竞争力 (confidence=0.54)"
        }
    );

    private final SuggestionBuilder suggestionBuilder;

    public RootCauseAnalyzer(SuggestionBuilder suggestionBuilder) {
        this.suggestionBuilder = suggestionBuilder;
    }

    /**
     * 从因果链中定位根因，生成改进建议。
     */
    void identifyRootCauseAndSuggestions(CausalChainResult result, DiagnosisRequest request) {
        List<CausalChainNode> chain = result.getCausalChain();

        if (result.getRootCause() == null || result.getRootCause().isEmpty()) {
            Optional<CausalChainNode> deepestKg = chain.stream()
                    .filter(n -> "KG".equals(n.getSource()))
                    .max(Comparator.comparingInt(CausalChainNode::getDepth));

            Optional<CausalChainNode> deepestAny = chain.stream()
                    .max(Comparator.comparingInt(CausalChainNode::getDepth));

            if (deepestKg.isPresent()) {
                result.setRootCause(deepestKg.get().getNode());
            } else if (deepestAny.isPresent() && !"metric".equals(deepestAny.get().getSource())) {
                result.setRootCause(deepestAny.get().getNode());
            } else {
                tryLlGenerateRootCause(result, request);
            }
        }

        if (result.getSuggestions().isEmpty()) {
            tryLlGenerateSuggestions(result, request);
        }

        if (result.getAffectedMetrics().isEmpty()) {
            Set<String> metrics = new LinkedHashSet<>();
            metrics.add(request.getMetric().replaceAll("[^a-zA-Z_]", "").toLowerCase());

            for (CausalChainNode node : chain) {
                if (!"metric".equals(node.getSource())) {
                    String nodeText = node.getNode();
                    if (nodeText.contains("营收") || nodeText.contains("收入")) metrics.add("revenue");
                    if (nodeText.contains("成本")) metrics.add("cost");
                    if (nodeText.contains("利润") || nodeText.contains("毛利")) metrics.add("gross_margin");
                    if (nodeText.contains("客户")) metrics.add("customer_concentration");
                    if (nodeText.contains("订单")) metrics.add("order_volume");
                    if (nodeText.contains("供应") || nodeText.contains("库存")) metrics.add("supply_chain");
                }
            }
            result.getAffectedMetrics().addAll(metrics);
        }
    }

    /**
     * 通过LLM生成根因描述。
     */
    private void tryLlGenerateRootCause(CausalChainResult result, DiagnosisRequest request) {
        String chainSummary = result.getCausalChain().stream()
                .map(n -> "  层" + n.getDepth() + ": " + n.getNode())
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "基于以下因果链，用一句话概括根本原因：\n\n%s\n\n" +
                "只输出根因描述（一句话，不要标记）：",
                chainSummary);

        try {
            String response = suggestionBuilder.callLlm(prompt);
            if (response != null && !response.isEmpty()) {
                result.setRootCause(response.trim());
            }
        } catch (Exception e) {
            result.getCausalChain().stream()
                    .max(Comparator.comparingInt(CausalChainNode::getDepth))
                    .ifPresent(n -> result.setRootCause(n.getNode()));
        }
    }

    /**
     * 通过LLM生成改进建议列表。
     */
    private void tryLlGenerateSuggestions(CausalChainResult result, DiagnosisRequest request) {
        String chainSummary = result.getCausalChain().stream()
                .map(n -> "  层" + n.getDepth() + ": " + n.getNode())
                .collect(Collectors.joining("\n"));

        String rootCause = result.getRootCause() != null ? result.getRootCause() : "未知";

        String prompt = String.format(
                "针对指标「%s」的偏差和根因「%s」，给出3-5条具体改进建议。\n\n" +
                "因果链:\n%s\n\n" +
                "只输出JSON数组（不要markdown标记）：\n" +
                "[\"建议1\", \"建议2\", \"建议3\"]",
                request.getMetric(), rootCause, chainSummary);

        try {
            String response = suggestionBuilder.callLlm(prompt);
            String json = suggestionBuilder.extractJson(response);
            List<String> suggestions = suggestionBuilder.getObjectMapper().readValue(json,
                    new TypeReference<List<String>>() {});
            if (suggestions != null) {
                result.getSuggestions().addAll(suggestions);
            }
        } catch (Exception e) {
            log.warn("LLM生成建议失败: {}", e.getMessage());
            result.getSuggestions().add("深入分析" + request.getMetric() + "的波动原因");
            result.getSuggestions().add("监控关键相关指标变化趋势");
        }
    }

    /**
     * 规则引擎扩展因果链 — 从领域知识库生成多步因果节点。
     */
    void ruleBasedExpansion(CausalChainResult result, DiagnosisRequest request, int maxDepth) {
        String metric = request.getMetric();
        String[] chain = CAUSAL_KNOWLEDGE.get(metric);
        if (chain == null) {
            for (Map.Entry<String, String[]> entry : CAUSAL_KNOWLEDGE.entrySet()) {
                if (metric.contains(entry.getKey()) || entry.getKey().contains(metric)) {
                    chain = entry.getValue();
                    break;
                }
            }
        }
        if (chain == null) {
            chain = new String[]{
                "2: 相关业务因素变化 影响" + metric + " (confidence=0.55)",
                "3: 外部市场环境波动 传导至业务层面 (confidence=0.45)",
                "4: 宏观政策调整/行业周期 引发市场环境变化 (confidence=0.38)"
            };
        }

        int currentSize = result.getCausalChain().size();
        for (int i = 0; i < chain.length && (currentSize + i) < maxDepth + 1; i++) {
            String entry = chain[i];
            int colonIdx = entry.indexOf(":");
            int parenIdx = entry.lastIndexOf("(confidence=");
            if (colonIdx < 0 || parenIdx < 0) continue;

            String nodeDesc = entry.substring(colonIdx + 1, parenIdx).trim();
            double conf = 0.5;
            try {
                String confStr = entry.substring(parenIdx + 12, entry.indexOf(")", parenIdx));
                conf = Double.parseDouble(confStr);
            } catch (Exception ignored) {}

            CausalChainNode node = new CausalChainNode(
                currentSize + i + 1, nodeDesc,
                suggestionBuilder.clampConfidence(conf, 0.3, 0.9), "RULE_ENGINE",
                request.getDomain());
            result.getCausalChain().add(node);
        }
        log.info("规则引擎兜底完成: 因果链 size={} (新增{}层)",
                result.getCausalChain().size(), Math.min(chain.length, maxDepth + 1 - currentSize));
    }
}
