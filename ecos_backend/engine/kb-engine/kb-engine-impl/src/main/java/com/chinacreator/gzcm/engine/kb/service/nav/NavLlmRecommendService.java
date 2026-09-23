package com.chinacreator.gzcm.engine.kb.service.nav;

import com.chinacreator.gzcm.engine.kb.nav.model.NavRecommendVO;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识导航·LLM 归类候选服务（PMO-A A4）。
 *
 * <p><b>仅建议，不落库</b>：所有路径返回 {@link NavRecommendVO}，失败/不可用时返回
 * {@code tags=[] / suggestedCategoryId=null / reason="llm-gateway 不可用 或 解析失败"} 合规兜底，
 * 不抛 {@code RuntimeException} — 调用方自行决定是否写入 {@code kb_nav_article_rel}。</p>
 *
 * <p><b>调用链</b>：取文章 title+content（截断 2000 字）→ {@code LLMGatewayService.execute(
 * "dccheng", "kb-nav-assistant", prompt)} → 解析 JSON（容错）→ 返回
 * {@code tags ≤ 5} + {@code suggestedCategoryName}（供调用方按名匹配 {@code kb_nav_category}，
 * 此处仅返回 name，不 JOIN 目录）。</p>
 */
@Service
public class NavLlmRecommendService {

    private static final Logger log = LoggerFactory.getLogger(NavLlmRecommendService.class);

    /** 输入文章正文截断上限（铁律 §2.5-2 占用 LLM token 控制） */
    private static final int MAX_CONTENT_CHARS = 2000;

    /** LLM prompt 系统提示 — 数据分类专家（与 ai-engine ClassificationController 同款风格） */
    private static final String SYSTEM_PROMPT =
        "你是知识导航专家。基于文章标题与摘要，给出候选标签（最多 5 个，短词）与一个推荐二级目录名。"
        + "输出纯 JSON（不要 markdown 代码块）："
        + "{\"tags\":[\"标签1\",\"标签2\"],\"suggestedCategory\":\"二级目录名\",\"reason\":\"一句话\"}。";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private LLMGatewayService llmGatewayService;

    /**
     * 生成归类建议（<b>仅建议，不落库</b>）。
     *
     * @param articleId 文章 ID（仅入参引用，便于日志）
     * @param title     标题（可空）
     * @param content   正文（截断 {@link #MAX_CONTENT_CHARS}）
     * @param domain    多租户 domain（仅作日志引用，不参与 prompt）
     * @return LLM 建议 VO；失败返回空 + reason，不抛
     */
    public NavRecommendVO recommend(String articleId, String title, String content, String domain) {
        NavRecommendVO vo = new NavRecommendVO();
        vo.setGeneratedAt(Instant.now());
        if (llmGatewayService == null) {
            vo.setTags(List.of());
            vo.setReason("llm-gateway 不可用");
            log.warn("kb-nav recommend: llmGatewayService 未注入 articleId={}", articleId);
            return vo;
        }
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_CONTENT_CHARS) {
            safeContent = safeContent.substring(0, MAX_CONTENT_CHARS);
        }
        String prompt = SYSTEM_PROMPT
                + "\n\n标题: " + safeTitle
                + "\n正文摘要（受限 2000 字）:\n" + safeContent
                + "\n\n请按 JSON 输出。";
        log.info("kb-nav recommend 调用 llm: articleId={} domain={} titleChars={} contentChars={}",
                articleId, domain == null ? "" : domain, safeTitle.length(), safeContent.length());
        try {
            AgentResult result = llmGatewayService.execute("dccheng", "kb-nav-assistant", prompt);
            if (result == null || !result.isSuccess()) {
                vo.setTags(List.of());
                vo.setReason("LLM 调用失败: " + (result == null ? "null" : result.getErrorMsg()));
                log.warn("kb-nav recommend 失败: articleId={} reason={}",
                        articleId, result == null ? "null" : result.getErrorMsg());
                return vo;
            }
            String body = result.getContent();
            Map<String, Object> parsed = parseLlmJson(body);
            if (parsed == null || parsed.isEmpty()) {
                vo.setTags(List.of());
                vo.setReason("LLM 解析失败（响应空或非 JSON）");
                vo.setTokens(result.getTokensInput() + result.getTokensOutput());
                return vo;
            }
            List<String> tags = extractTags(parsed);
            String suggested = firstNonBlank(parsed.get("suggestedCategory"), parsed.get("suggested_category"), parsed.get("suggested"));
            vo.setTags(tags);
            vo.setSuggestedCategoryName(suggested);
            vo.setReason(parsed.get("reason") == null ? null : String.valueOf(parsed.get("reason")));
            vo.setTokens(result.getTokensInput() + result.getTokensOutput());
            log.info("kb-nav recommend 完成: articleId={} tags={} suggested={} tokensIn={} tokensOut={}",
                    articleId, tags.size(), suggested, result.getTokensInput(), result.getTokensOutput());
            return vo;
        } catch (Exception e) {
            vo.setTags(List.of());
            vo.setReason("LLM 调用异常: " + e.getMessage());
            log.warn("kb-nav recommend 异常: articleId={} reason={}", articleId, e.getMessage(), e);
            return vo;
        }
    }

    /** 容错解析：剥离 markdown 代码块包裹，找到第一个 { 到最后一个 }；失败返回 null。 */
    private Map<String, Object> parseLlmJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String s = content.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String json = s.substring(start, end + 1).trim();
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("kb-nav LLM JSON parse fail: {}", e.getMessage());
            return null;
        }
    }

    /** 兼容 tags / candidateTags / tags_candidate 等多种 key，统一抽取并去空。 */
    @SuppressWarnings("unchecked")
    private List<String> extractTags(Map<String, Object> parsed) {
        Object candidate = parsed.get("tags");
        if (candidate == null) {
            candidate = parsed.get("candidate_tags");
        }
        if (candidate == null) {
            candidate = parsed.get("candidateTags");
        }
        List<String> raw = new ArrayList<>();
        if (candidate instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isBlank()) {
                    String t = s.trim();
                    if (!t.isEmpty() && t.length() <= 64) {
                        raw.add(t);
                    }
                } else if (o instanceof Number n) {
                    raw.add(n.toString());
                }
            }
        } else if (candidate instanceof String s && !s.isBlank()) {
            // 兜底：LLM 用 "," 分隔
            for (String piece : Arrays.asList(s.split("\\s*(,|，|、)\\s*"))) {
                String t = piece.trim();
                if (!t.isEmpty() && t.length() <= 64) {
                    raw.add(t);
                }
            }
        }
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String t : raw) {
            if (seen.add(t) && out.size() < 5) {
                out.add(t);
            }
        }
        return out;
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return null;
    }
}
