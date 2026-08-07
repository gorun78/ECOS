package com.chinacreator.gzcm.engine.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 评估框架 — 逐题执行标准问题集，5维裁判打分，汇总报告。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>加载 eval-questions/{questionSet}.json 中的标准问题集</li>
 *   <li>逐题调用 AgentLoopService 执行，观察工具调用和回答</li>
 *   <li>5维裁判打分 — 工具准确性 / 关键词覆盖 / 回答质量 / 执行效率 / 幻觉抵抗</li>
 *   <li>汇总得分报告，等级 S/A/B/C/D</li>
 * </ol>
 *
 * <h3>5维评分体系 (每个维度 1-5 分)</h3>
 * <table>
 *   <tr><th>维度</th><th>名称</th><th>评分依据</th></tr>
 *   <tr><td>D1</td><td>工具准确性</td><td>是否正确调用预期工具</td></tr>
 *   <tr><td>D2</td><td>关键词覆盖</td><td>预期关键词匹配数量</td></tr>
 *   <tr><td>D3</td><td>回答质量</td><td>回答长度、结构、相关性</td></tr>
 *   <tr><td>D4</td><td>执行效率</td><td>推理轮次、成功/失败</td></tr>
 *   <tr><td>D5</td><td>幻觉抵抗</td><td>无虚构内容、回答基于事实</td></tr>
 * </table>
 *
 * <h3>等级映射</h3>
 * <ul>
 *   <li>S: avgScore ≥ 4.5</li>
 *   <li>A: avgScore ≥ 3.5</li>
 *   <li>B: avgScore ≥ 2.5</li>
 *   <li>C: avgScore ≥ 1.5</li>
 *   <li>D: avgScore < 1.5</li>
 * </ul>
 */
@Service
public class AgentEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AgentEvaluator.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** 评估结果存储（内存） */
    private final Map<String, EvalReport> store = new ConcurrentHashMap<>();

    @Autowired
    private AgentLoopService agentLoopService;

    // ═══════════════════════════════════════════════════════════════
    //  内嵌 POJO
    // ═══════════════════════════════════════════════════════════════

    /**
     * 评估问题定义
     */
    public static class EvalQuestion {
        private String id;
        private String question;
        private String expectedTool;
        private String category;
        private List<String> expectedKeywords;
        private int minScore;
        private String description;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public String getExpectedTool() { return expectedTool; }
        public void setExpectedTool(String expectedTool) { this.expectedTool = expectedTool; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public List<String> getExpectedKeywords() { return expectedKeywords; }
        public void setExpectedKeywords(List<String> expectedKeywords) { this.expectedKeywords = expectedKeywords; }

        public int getMinScore() { return minScore; }
        public void setMinScore(int minScore) { this.minScore = minScore; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * 5维评分明细
     */
    public static class DimensionScores {
        private int toolAccuracy;       // D1: 工具准确性 (1-5)
        private int keywordCoverage;    // D2: 关键词覆盖 (1-5)
        private int answerQuality;      // D3: 回答质量 (1-5)
        private int executionEfficiency; // D4: 执行效率 (1-5)
        private int hallucinationResist; // D5: 幻觉抵抗 (1-5)

        public DimensionScores() {}

        public DimensionScores(int toolAccuracy, int keywordCoverage, int answerQuality,
                               int executionEfficiency, int hallucinationResist) {
            this.toolAccuracy = toolAccuracy;
            this.keywordCoverage = keywordCoverage;
            this.answerQuality = answerQuality;
            this.executionEfficiency = executionEfficiency;
            this.hallucinationResist = hallucinationResist;
        }

        /** 5维加权平均分 (权重: D1=30%, D2=25%, D3=20%, D4=15%, D5=10%) */
        public double weightedAvg() {
            return toolAccuracy * 0.30 + keywordCoverage * 0.25
                 + answerQuality * 0.20 + executionEfficiency * 0.15
                 + hallucinationResist * 0.10;
        }

        /** 简单算术平均分 */
        public double simpleAvg() {
            return (toolAccuracy + keywordCoverage + answerQuality
                  + executionEfficiency + hallucinationResist) / 5.0;
        }

        public int getToolAccuracy() { return toolAccuracy; }
        public void setToolAccuracy(int v) { this.toolAccuracy = v; }

        public int getKeywordCoverage() { return keywordCoverage; }
        public void setKeywordCoverage(int v) { this.keywordCoverage = v; }

        public int getAnswerQuality() { return answerQuality; }
        public void setAnswerQuality(int v) { this.answerQuality = v; }

        public int getExecutionEfficiency() { return executionEfficiency; }
        public void setExecutionEfficiency(int v) { this.executionEfficiency = v; }

        public int getHallucinationResist() { return hallucinationResist; }
        public void setHallucinationResist(int v) { this.hallucinationResist = v; }
    }

    /**
     * 单题评估结果
     */
    public static class EvalItemResult {
        private String questionId;
        private String question;
        private String category;
        private int score;                  // 综合分 (1-5, 加权平均取整)
        private double rawScore;            // 原始加权平均分
        private DimensionScores dimensions;  // 5维评分明细
        private String agentAnswer;
        private String actualTool;
        private List<String> matchedKeywords;
        private String judgeReason;
        private long durationMs;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public double getRawScore() { return rawScore; }
        public void setRawScore(double rawScore) { this.rawScore = rawScore; }

        public DimensionScores getDimensions() { return dimensions; }
        public void setDimensions(DimensionScores dimensions) { this.dimensions = dimensions; }

        public String getAgentAnswer() { return agentAnswer; }
        public void setAgentAnswer(String agentAnswer) { this.agentAnswer = agentAnswer; }

        public String getActualTool() { return actualTool; }
        public void setActualTool(String actualTool) { this.actualTool = actualTool; }

        public List<String> getMatchedKeywords() { return matchedKeywords; }
        public void setMatchedKeywords(List<String> matchedKeywords) { this.matchedKeywords = matchedKeywords; }

        public String getJudgeReason() { return judgeReason; }
        public void setJudgeReason(String judgeReason) { this.judgeReason = judgeReason; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    }

    /**
     * 评估报告
     */
    public static class EvalReport {
        private String evalId;
        private String questionSet;        // 使用的问题集名称
        private String status;             // running | completed | failed
        private int totalQuestions;
        private double totalScore;
        private double avgScore;
        private String grade;              // S/A/B/C/D
        private List<EvalItemResult> items;
        private String startedAt;
        private String completedAt;
        private long totalDurationMs;
        // 5维汇总均分
        private DimensionScores dimensionAvgs;

        public String getEvalId() { return evalId; }
        public void setEvalId(String evalId) { this.evalId = evalId; }

        public String getQuestionSet() { return questionSet; }
        public void setQuestionSet(String questionSet) { this.questionSet = questionSet; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

        public double getTotalScore() { return totalScore; }
        public void setTotalScore(double totalScore) { this.totalScore = totalScore; }

        public double getAvgScore() { return avgScore; }
        public void setAvgScore(double avgScore) { this.avgScore = avgScore; }

        public String getGrade() { return grade; }
        public void setGrade(String grade) { this.grade = grade; }

        public List<EvalItemResult> getItems() { return items; }
        public void setItems(List<EvalItemResult> items) { this.items = items; }

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

        public String getCompletedAt() { return completedAt; }
        public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

        public long getTotalDurationMs() { return totalDurationMs; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }

        public DimensionScores getDimensionAvgs() { return dimensionAvgs; }
        public void setDimensionAvgs(DimensionScores dimensionAvgs) { this.dimensionAvgs = dimensionAvgs; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  公共 API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 启动评估 — 使用默认问题集 "default"。
     *
     * @return evalId，用于后续查询报告
     */
    public String startEvaluation() {
        return startEvaluation("default");
    }

    /**
     * 启动评估 — 指定问题集名称。
     *
     * @param questionSet 问题集名称（不含路径和扩展名），如 "default", "finance_20", "legal_20", "compliance_20"
     * @return evalId，用于后续查询报告
     */
    public String startEvaluation(String questionSet) {
        String setName = (questionSet != null && !questionSet.isBlank()) ? questionSet : "default";
        String evalId = "eval-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        EvalReport report = new EvalReport();
        report.setEvalId(evalId);
        report.setQuestionSet(setName);
        report.setStatus("running");
        report.setStartedAt(Instant.now().toString());
        store.put(evalId, report);

        // 异步执行评估
        new Thread(() -> runEvaluation(evalId, setName), "eval-" + evalId).start();

        log.info("Evaluation started: evalId={} questionSet={}", evalId, setName);
        return evalId;
    }

    /**
     * 获取评估报告
     */
    public EvalReport getReport(String evalId) {
        return store.get(evalId);
    }

    /**
     * 列出所有已完成的评估报告
     */
    public List<EvalReport> listReports() {
        List<EvalReport> reports = new ArrayList<>(store.values());
        reports.sort((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()));
        return reports;
    }

    // ═══════════════════════════════════════════════════════════════
    //  评估执行
    // ═══════════════════════════════════════════════════════════════

    private void runEvaluation(String evalId, String questionSet) {
        EvalReport report = store.get(evalId);
        if (report == null) return;

        long evalStart = System.currentTimeMillis();

        try {
            // 1. 加载标准问题集
            List<EvalQuestion> questions = loadQuestions(questionSet);
            if (questions == null || questions.isEmpty()) {
                report.setStatus("failed");
                report.setCompletedAt(Instant.now().toString());
                log.error("No evaluation questions found for set: {}", questionSet);
                return;
            }

            report.setTotalQuestions(questions.size());
            List<EvalItemResult> items = new ArrayList<>();

            // 2. 逐题执行
            for (EvalQuestion q : questions) {
                long itemStart = System.currentTimeMillis();
                EvalItemResult itemResult = evaluateQuestion(q);
                itemResult.setDurationMs(System.currentTimeMillis() - itemStart);
                items.add(itemResult);

                // 短暂间隔，避免 LLM 限流
                Thread.sleep(500);
            }

            // 3. 汇总报告
            report.setItems(items);
            report.setTotalQuestions(items.size());
            double total = items.stream().mapToDouble(i -> i.getRawScore()).sum();
            report.setTotalScore(total);
            report.setAvgScore(total / items.size());
            report.setGrade(calculateGrade(report.getAvgScore()));

            // 4. 计算5维汇总均分
            report.setDimensionAvgs(calculateDimensionAvgs(items));

            report.setStatus("completed");
            report.setCompletedAt(Instant.now().toString());
            report.setTotalDurationMs(System.currentTimeMillis() - evalStart);

            log.info("Evaluation completed: evalId={} avgScore={:.2f} grade={} dimAvgs=[D1={:.1f},D2={:.1f},D3={:.1f},D4={:.1f},D5={:.1f}]",
                    evalId, report.getAvgScore(), report.getGrade(),
                    report.getDimensionAvgs() != null ? report.getDimensionAvgs().simpleAvg() : 0);
        } catch (Exception e) {
            log.error("Evaluation failed: evalId={}", evalId, e);
            report.setStatus("failed");
            report.setCompletedAt(Instant.now().toString());
        }
    }

    /**
     * 加载指定问题集
     */
    private List<EvalQuestion> loadQuestions(String questionSet) {
        String path = "eval-questions/" + questionSet + ".json";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (InputStream is = resource.getInputStream()) {
                List<EvalQuestion> questions = mapper.readValue(is,
                        new TypeReference<List<EvalQuestion>>() {});
                log.info("Loaded {} questions from {}", questions.size(), path);
                return questions;
            }
        } catch (Exception e) {
            log.error("Failed to load eval questions from {}: {}", path, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 对单个问题执行评估
     */
    private EvalItemResult evaluateQuestion(EvalQuestion q) {
        EvalItemResult result = new EvalItemResult();
        result.setQuestionId(q.getId());
        result.setQuestion(q.getQuestion());
        result.setCategory(q.getCategory());

        try {
            // 执行 Agent 推理
            AgentLoopConfig config = new AgentLoopConfig();
            config.setModel("deepseek-chat");
            config.setTemperature(0.3);
            config.setMaxTokens(2048);

            AgentLoopResult loopResult = agentLoopService.run(config, q.getQuestion(), null);

            // 提取回答
            String answer = loopResult != null && loopResult.getContent() != null
                    ? loopResult.getContent() : "";
            result.setAgentAnswer(answer);

            // 提取实际调用的工具
            String actualTool = extractActualTool(loopResult);
            result.setActualTool(actualTool);

            // 匹配关键词
            List<String> matchedKw = matchKeywords(q.getExpectedKeywords(), answer);
            result.setMatchedKeywords(matchedKw);

            // 5维评分
            DimensionScores dims = judgeDimensions(q, loopResult, answer, matchedKw);
            result.setDimensions(dims);
            result.setRawScore(dims.weightedAvg());
            result.setScore((int) Math.round(dims.weightedAvg()));

            // 裁判理由
            result.setJudgeReason(buildJudgeReason(q, result.getScore(), actualTool, matchedKw, dims));

            log.info("Eval qId={} score={}(raw={:.2f}) dims=[D1={},D2={},D3={},D4={},D5={}] tool={} matched={}",
                    q.getId(), result.getScore(), result.getRawScore(),
                    dims.getToolAccuracy(), dims.getKeywordCoverage(), dims.getAnswerQuality(),
                    dims.getExecutionEfficiency(), dims.getHallucinationResist(),
                    actualTool, matchedKw.size());
        } catch (Exception e) {
            log.error("Eval question failed: qId={}", q.getId(), e);
            result.setScore(1);
            result.setRawScore(1.0);
            result.setAgentAnswer("执行异常: " + e.getMessage());
            result.setJudgeReason("Agent 推理过程中抛出异常");
            result.setDimensions(new DimensionScores(1, 1, 1, 1, 1));
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  5维裁判逻辑
    // ═══════════════════════════════════════════════════════════════

    /**
     * 5维裁判打分
     *
     * @param q         题目定义
     * @param loopResult Agent 执行结果
     * @param answer    回答文本
     * @param matchedKw 匹配到的关键词列表
     * @return 5维评分
     */
    private DimensionScores judgeDimensions(EvalQuestion q, AgentLoopResult loopResult,
                                            String answer, List<String> matchedKw) {
        String expectedTool = q.getExpectedTool();
        String actualTool = extractActualTool(loopResult);

        // D1: 工具准确性 (1-5)
        int d1ToolAccuracy = scoreToolAccuracy(expectedTool, actualTool);

        // D2: 关键词覆盖 (1-5)
        int d2KeywordCoverage = scoreKeywordCoverage(
                q.getExpectedKeywords(), matchedKw, expectedTool);

        // D3: 回答质量 (1-5)
        int d3AnswerQuality = scoreAnswerQuality(answer);

        // D4: 执行效率 (1-5)
        int d4ExecEfficiency = scoreExecutionEfficiency(loopResult);

        // D5: 幻觉抵抗 (1-5)
        int d5Hallucination = scoreHallucinationResist(answer, q.getQuestion());

        return new DimensionScores(d1ToolAccuracy, d2KeywordCoverage, d3AnswerQuality,
                d4ExecEfficiency, d5Hallucination);
    }

    /**
     * D1: 工具准确性评分
     * <ul>
     *   <li>5: 正确调用预期工具</li>
     *   <li>4: 预期工具为 null，知识问答类 — 按回答质量给分</li>
     *   <li>3: 调用了工具但非预期工具</li>
     *   <li>2: 未调用工具（预期有工具时）</li>
     *   <li>1: 没有任何工具调用且预期有工具</li>
     * </ul>
     */
    private int scoreToolAccuracy(String expectedTool, String actualTool) {
        if (expectedTool == null) {
            // 知识问答 — 不需要工具调用，中性给分
            return actualTool == null ? 5 : 4;
        }
        if (expectedTool.equals(actualTool)) return 5;
        if (actualTool != null && !actualTool.equals(expectedTool)) return 3;
        return 2;
    }

    /**
     * D2: 关键词覆盖评分
     * <ul>
     *   <li>5: 匹配 100% 关键词 (知识问答 ≥3 个)</li>
     *   <li>4: 匹配 ≥75% 或 (知识问答 ≥2 个)</li>
     *   <li>3: 匹配 ≥50% 或 (知识问答 ≥1 个)</li>
     *   <li>2: 匹配 ≥25%</li>
     *   <li>1: 无匹配</li>
     * </ul>
     */
    private int scoreKeywordCoverage(List<String> expectedKeywords, List<String> matchedKw,
                                     String expectedTool) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) return 3;
        int total = expectedKeywords.size();
        int matched = matchedKw != null ? matchedKw.size() : 0;
        double ratio = (double) matched / total;

        if (expectedTool == null) {
            // 知识问答类：放宽标准
            if (matched >= 3) return 5;
            if (matched >= 2) return 4;
            if (matched >= 1) return 3;
            return 2;
        }

        if (ratio >= 1.0) return 5;
        if (ratio >= 0.75) return 4;
        if (ratio >= 0.5) return 3;
        if (ratio >= 0.25) return 2;
        return 1;
    }

    /**
     * D3: 回答质量评分
     * <ul>
     *   <li>5: 回答 ≥200 字符，结构清晰</li>
     *   <li>4: 回答 ≥100 字符</li>
     *   <li>3: 回答 ≥50 字符</li>
     *   <li>2: 回答 ≥20 字符</li>
     *   <li>1: 空白/异常回答</li>
     * </ul>
     */
    private int scoreAnswerQuality(String answer) {
        if (answer == null || answer.isBlank()) return 1;
        int len = answer.trim().length();
        if (len >= 200) return 5;
        if (len >= 100) return 4;
        if (len >= 50) return 3;
        if (len >= 20) return 2;
        return 1;
    }

    /**
     * D4: 执行效率评分
     * <ul>
     *   <li>5: 成功，turns ≤ 2</li>
     *   <li>4: 成功，turns ≤ 3</li>
     *   <li>3: 成功，turns ≤ 5</li>
     *   <li>2: 失败/超限</li>
     *   <li>1: 错误/null</li>
     * </ul>
     */
    private int scoreExecutionEfficiency(AgentLoopResult loopResult) {
        if (loopResult == null) return 1;
        if (!loopResult.isSuccess()) return 2;
        int turns = loopResult.getTurns();
        if (turns <= 2) return 5;
        if (turns <= 3) return 4;
        if (turns <= 5) return 3;
        return 2;
    }

    /**
     * D5: 幻觉抵抗评分
     * 检测回答中是否包含明显的虚构标记：
     * <ul>
     *   <li>5: 回答有实质内容，无幻觉特征</li>
     *   <li>4: 轻微不确定性表述但内容相关</li>
     *   <li>3: 包含不确定用语（"可能"、"大概"等）但无具体虚构</li>
     *   <li>2: 包含明显幻觉/虚构内容</li>
     *   <li>1: 空回答或完全无关</li>
     * </ul>
     */
    private int scoreHallucinationResist(String answer, String question) {
        if (answer == null || answer.isBlank()) return 1;

        String lower = answer.toLowerCase();

        // 检测幻觉/虚构标记
        boolean hasFabrication = lower.contains("据我所知不存在") ||
                lower.contains("没有相关信息") ||
                lower.contains("无法查询");

        boolean hasUncertainty = lower.contains("可能") ||
                lower.contains("大概") ||
                lower.contains("大约") ||
                lower.contains("估计");

        boolean hasSubstance = answer.trim().length() > 50;

        if (hasFabrication) return 2;
        if (hasUncertainty && !hasSubstance) return 3;
        if (hasUncertainty && hasSubstance) return 4;
        if (hasSubstance) return 5;
        return 2;
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 提取实际调用的工具名称
     */
    private String extractActualTool(AgentLoopResult loopResult) {
        if (loopResult == null || loopResult.getToolCalls() == null || loopResult.getToolCalls().isEmpty()) {
            return null;
        }
        Map<String, Object> firstCall = loopResult.getToolCalls().get(0);
        Object toolName = firstCall.get("toolName");
        return toolName != null ? toolName.toString() : null;
    }

    /**
     * 关键词匹配 — 大小写不敏感
     */
    private List<String> matchKeywords(List<String> keywords, String text) {
        if (keywords == null || keywords.isEmpty() || text == null) {
            return Collections.emptyList();
        }
        List<String> matched = new ArrayList<>();
        String lowerText = text.toLowerCase();
        for (String kw : keywords) {
            if (lowerText.contains(kw.toLowerCase())) {
                matched.add(kw);
            }
        }
        return matched;
    }

    /**
     * 构建裁判理由
     */
    private String buildJudgeReason(EvalQuestion q, int score, String actualTool,
                                    List<String> matchedKw, DimensionScores dims) {
        StringBuilder sb = new StringBuilder();
        sb.append("综合得分: ").append(score).append("/5. ");

        if (q.getExpectedTool() != null) {
            sb.append("预期工具: ").append(q.getExpectedTool());
            sb.append(", 实际工具: ").append(actualTool != null ? actualTool : "无").append(".");
        }

        sb.append(" [D1工具准确性=").append(dims.getToolAccuracy());
        sb.append(", D2关键词覆盖=").append(dims.getKeywordCoverage());
        sb.append(", D3回答质量=").append(dims.getAnswerQuality());
        sb.append(", D4执行效率=").append(dims.getExecutionEfficiency());
        sb.append(", D5幻觉抵抗=").append(dims.getHallucinationResist()).append("]");

        if (matchedKw != null && !matchedKw.isEmpty()) {
            sb.append(" 匹配关键词: ").append(matchedKw).append(".");
        }

        return sb.toString();
    }

    /**
     * 计算5维汇总均分
     */
    private DimensionScores calculateDimensionAvgs(List<EvalItemResult> items) {
        if (items == null || items.isEmpty()) return new DimensionScores(0, 0, 0, 0, 0);

        double sumD1 = 0, sumD2 = 0, sumD3 = 0, sumD4 = 0, sumD5 = 0;
        int count = 0;
        for (EvalItemResult item : items) {
            DimensionScores dims = item.getDimensions();
            if (dims != null) {
                sumD1 += dims.getToolAccuracy();
                sumD2 += dims.getKeywordCoverage();
                sumD3 += dims.getAnswerQuality();
                sumD4 += dims.getExecutionEfficiency();
                sumD5 += dims.getHallucinationResist();
                count++;
            }
        }
        if (count == 0) return new DimensionScores(0, 0, 0, 0, 0);

        return new DimensionScores(
                (int) Math.round(sumD1 / count),
                (int) Math.round(sumD2 / count),
                (int) Math.round(sumD3 / count),
                (int) Math.round(sumD4 / count),
                (int) Math.round(sumD5 / count));
    }

    /**
     * 总分 → 等级
     */
    private String calculateGrade(double avgScore) {
        if (avgScore >= 4.5) return "S";
        if (avgScore >= 3.5) return "A";
        if (avgScore >= 2.5) return "B";
        if (avgScore >= 1.5) return "C";
        return "D";
    }
}
