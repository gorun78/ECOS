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
 * Agent 评估框架 — 逐题执行标准问题集，裁判打分，汇总报告。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>加载 eval-questions/default.json 中的标准问题集</li>
 *   <li>逐题调用 AgentLoopService 执行，观察工具调用和回答</li>
 *   <li>裁判打分（1-5）— 基于关键词匹配、工具调用正确性</li>
 *   <li>汇总得分报告</li>
 * </ol>
 *
 * <h3>裁判规则</h3>
 * <ul>
 *   <li>5分：正确调用预期工具 + 回答包含至少2个预期关键词</li>
 *   <li>4分：正确调用预期工具 + 回答包含至少1个预期关键词</li>
 *   <li>3分：调用了工具但非预期工具 + 回答合理</li>
 *   <li>2分：未调用工具但回答基本相关</li>
 *   <li>1分：未调用工具且回答不相关 / 错误</li>
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
     * 单题评估结果
     */
    public static class EvalItemResult {
        private String questionId;
        private String question;
        private String category;
        private int score;
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
        private String status; // running | completed | failed
        private int totalQuestions;
        private double totalScore;
        private double avgScore;
        private String grade; // S/A/B/C/D
        private List<EvalItemResult> items;
        private String startedAt;
        private String completedAt;
        private long totalDurationMs;

        public String getEvalId() { return evalId; }
        public void setEvalId(String evalId) { this.evalId = evalId; }

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
    }

    // ═══════════════════════════════════════════════════
    //  公共 API
    // ═══════════════════════════════════════════════════

    /**
     * 启动评估 — 加载标准问题集，逐题执行，裁判打分。
     *
     * @return evalId，用于后续查询报告
     */
    public String startEvaluation() {
        String evalId = "eval-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        EvalReport report = new EvalReport();
        report.setEvalId(evalId);
        report.setStatus("running");
        report.setStartedAt(Instant.now().toString());
        store.put(evalId, report);

        // 异步执行评估
        new Thread(() -> runEvaluation(evalId), "eval-" + evalId).start();

        log.info("Evaluation started: evalId={}", evalId);
        return evalId;
    }

    /**
     * 获取评估报告
     */
    public EvalReport getReport(String evalId) {
        return store.get(evalId);
    }

    // ═══════════════════════════════════════════════════
    //  评估执行
    // ═══════════════════════════════════════════════════

    private void runEvaluation(String evalId) {
        EvalReport report = store.get(evalId);
        if (report == null) return;

        long evalStart = System.currentTimeMillis();

        try {
            // 1. 加载标准问题集
            List<EvalQuestion> questions = loadQuestions();
            if (questions == null || questions.isEmpty()) {
                report.setStatus("failed");
                report.setCompletedAt(Instant.now().toString());
                log.error("No evaluation questions found");
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
            double total = items.stream().mapToInt(EvalItemResult::getScore).sum();
            report.setTotalScore(total);
            report.setAvgScore(total / items.size());
            report.setGrade(calculateGrade(report.getAvgScore()));
            report.setStatus("completed");
            report.setCompletedAt(Instant.now().toString());
            report.setTotalDurationMs(System.currentTimeMillis() - evalStart);

            log.info("Evaluation completed: evalId={} avgScore={:.2f} grade={}",
                    evalId, report.getAvgScore(), report.getGrade());
        } catch (Exception e) {
            log.error("Evaluation failed: evalId={}", evalId, e);
            report.setStatus("failed");
            report.setCompletedAt(Instant.now().toString());
        }
    }

    /**
     * 加载标准问题集
     */
    private List<EvalQuestion> loadQuestions() {
        try {
            ClassPathResource resource = new ClassPathResource("eval-questions/default.json");
            try (InputStream is = resource.getInputStream()) {
                return mapper.readValue(is, new TypeReference<List<EvalQuestion>>() {});
            }
        } catch (Exception e) {
            log.error("Failed to load eval questions: {}", e.getMessage());
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

            // 裁判打分
            int score = judgeScore(q, loopResult, answer);
            result.setScore(score);

            // 匹配关键词
            List<String> matchedKw = matchKeywords(q.getExpectedKeywords(), answer);
            result.setMatchedKeywords(matchedKw);

            // 裁判理由
            result.setJudgeReason(buildJudgeReason(q, score, actualTool, matchedKw));

            log.info("Eval qId={} score={} tool={} matched={}",
                    q.getId(), score, actualTool, matchedKw.size());
        } catch (Exception e) {
            log.error("Eval question failed: qId={}", q.getId(), e);
            result.setScore(1);
            result.setAgentAnswer("执行异常: " + e.getMessage());
            result.setJudgeReason("Agent 推理过程中抛出异常");
        }

        return result;
    }

    // ═══════════════════════════════════════════════════
    //  裁判逻辑
    // ═══════════════════════════════════════════════════

    /**
     * 裁判打分 — 1-5 分
     */
    private int judgeScore(EvalQuestion q, AgentLoopResult loopResult, String answer) {
        String expectedTool = q.getExpectedTool();
        String actualTool = extractActualTool(loopResult);
        int matchedKw = matchKeywords(q.getExpectedKeywords(), answer).size();

        // 1. 预期工具为 null 的知识问答类 — 仅评关键词
        if (expectedTool == null) {
            if (matchedKw >= 3) return 5;
            if (matchedKw >= 2) return 4;
            if (matchedKw >= 1) return 3;
            if (answer != null && answer.length() > 20) return 2;
            return 1;
        }

        // 2. 工具调用正确 + 关键词匹配
        boolean toolMatch = expectedTool.equals(actualTool);

        if (toolMatch && matchedKw >= 2) return 5;
        if (toolMatch && matchedKw >= 1) return 4;
        if (toolMatch) return 3;

        // 3. 调用了其他工具
        if (actualTool != null && !actualTool.equals(expectedTool)) {
            if (matchedKw >= 1) return 3;
            return 2;
        }

        // 4. 未调用工具
        if (matchedKw >= 1) return 2;
        return 1;
    }

    /**
     * 提取实际调用的工具名称
     */
    private String extractActualTool(AgentLoopResult loopResult) {
        if (loopResult == null || loopResult.getToolCalls() == null || loopResult.getToolCalls().isEmpty()) {
            return null;
        }
        // 取第一个工具调用的 toolName
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
    private String buildJudgeReason(EvalQuestion q, int score, String actualTool, List<String> matchedKw) {
        StringBuilder sb = new StringBuilder();
        sb.append("得分: ").append(score).append("/5. ");

        if (q.getExpectedTool() != null) {
            sb.append("预期工具: ").append(q.getExpectedTool());
            sb.append(", 实际工具: ").append(actualTool != null ? actualTool : "无").append(". ");
        }

        if (matchedKw != null && !matchedKw.isEmpty()) {
            sb.append("匹配关键词: ").append(matchedKw).append(". ");
        } else {
            sb.append("未匹配预期关键词. ");
        }

        return sb.toString();
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
