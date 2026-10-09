package com.chinacreator.gzcm.engine.ai.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具返回体的引用元数据与引用块（详细设计-06 F06-11 要点 4 / F06-12 要点 1）。
 *
 * <p>引用块模板<b>逐字符冻结</b>（PRD-06 §4.2）：
 * {@code [运行 {forecastRunId} · 口径 {caliberId}@{version} · 截至 {asOfTime} · 证据 {links}]}。
 * 字段只能从工具返回体取，本类因此同时提供"从返回体提取"与"渲染成引用块"两个方向，
 * 中间不留 LLM 复述通道（铁律 §0.2.1 禁 LLM 生成数值）。</p>
 */
public record ToolCitation(String forecastRunId, String caliberId, String version,
                           String asOfTime, List<String> evidenceLinks) {

    /** 冻结分隔符（渲染与解析共用，任何改动都会破坏 PRD §4.2 的逐字符一致性）。 */
    public static final String PREFIX = "[运行 ";
    public static final String SEP_CALIBER = " · 口径 ";
    public static final String SEP_VERSION = "@";
    public static final String SEP_ASOF = " · 截至 ";
    public static final String SEP_EVIDENCE = " · 证据 ";
    public static final String SUFFIX = "]";

    /** 提取顺序与 V200/PRD 字段名一致；{@code links}/{@code evidence} 为兼容别名。 */
    private static final List<String> RUN_ID_KEYS = List.of("forecastRunId", "runId", "run_id");
    private static final List<String> CALIBER_KEYS = List.of("caliberId", "caliber_id", "caliber");
    private static final List<String> VERSION_KEYS = List.of("version", "caliberVersion", "version_no");
    private static final List<String> ASOF_KEYS = List.of("asOfTime", "as_of_time", "asOf");
    private static final List<String> LINKS_KEYS = List.of("evidenceLinks", "evidence_links", "links");

    /** 引用块识别（用于"回答里到底有没有引用块"的确定性判定，禁再叫一次 LLM）。 */
    private static final Pattern CITATION_BLOCK = Pattern.compile(
            "\\[运行 ([^ ·\\]]+) · 口径 ([^@\\]]+)@([^ ·\\]]+) · 截至 ([^ ·\\]]+) · 证据 ([^\\]]*)\\]");

    /**
     * 数字 token：整数/小数，容忍千分位逗号与全角句点。定界用"前后不得是数字或字母"，
     * 从而 {@code v2}/{@code 2026Q1} 这类版本、期间标识不会被切出伪数值。
     */
    private static final Pattern NUMBER_TOKEN = Pattern.compile(
            "(?<![0-9A-Za-z_.-])\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?(?![0-9A-Za-z])"
                    + "|(?<![0-9A-Za-z_.-])\\d+(?:\\.\\d+)?(?![0-9A-Za-z])");

    public ToolCitation {
        evidenceLinks = evidenceLinks == null
                ? List.of() : List.copyOf(evidenceLinks.stream().filter(s -> s != null && !s.isBlank()).toList());
    }

    /** 四要素齐备才算可用引用（证据链接允许为空集，数值本身仍有运行/口径/版本可溯）。 */
    public boolean isComplete() {
        return isText(forecastRunId) && isText(caliberId) && isText(version) && isText(asOfTime);
    }

    public String render() {
        return PREFIX + forecastRunId + SEP_CALIBER + caliberId + SEP_VERSION + version
                + SEP_ASOF + asOfTime + SEP_EVIDENCE + String.join(",", evidenceLinks) + SUFFIX;
    }

    /** 缺少哪几项（供 CITATION_MISSING 的诊断文案，禁上浮原始返回体）。 */
    public List<String> missingFields() {
        List<String> missing = new ArrayList<>();
        if (!isText(forecastRunId)) missing.add("forecastRunId");
        if (!isText(caliberId)) missing.add("caliberId");
        if (!isText(version)) missing.add("version");
        if (!isText(asOfTime)) missing.add("asOfTime");
        return missing;
    }

    /**
     * 从工具返回体（已解析的 Map）提取引用元数据。
     *
     * <p>支持两种落位：返回体顶层直铺，或挂在 {@code citation} / {@code metadata} 子对象下
     * （分册 09 的 results 端点按后者定形）。取不到即返回 {@code null}，由调用方判
     * {@code CITATION_MISSING} —— <b>不做任何默认值兜底</b>（兜底即伪造引用）。</p>
     */
    @SuppressWarnings("unchecked")
    public static ToolCitation fromResult(Map<String, Object> body) {
        if (body == null) return null;
        Map<String, Object> scope = body;
        for (String nested : new String[]{"citation", "reference", "metadata"}) {
            Object v = body.get(nested);
            if (v instanceof Map<?, ?> m && !m.isEmpty()) {
                scope = (Map<String, Object>) m;
                break;
            }
        }
        String runId = firstText(scope, body, RUN_ID_KEYS);
        String caliber = firstText(scope, body, CALIBER_KEYS);
        String version = firstText(scope, body, VERSION_KEYS);
        String asOf = firstText(scope, body, ASOF_KEYS);
        List<String> links = firstLinks(scope, body, LINKS_KEYS);
        if (!isText(runId) && !isText(caliber) && !isText(version) && !isText(asOf) && links.isEmpty()) {
            return null; // 完全无引用段：交由 CITATION_MISSING，而不是造一个空引用
        }
        return new ToolCitation(runId, caliber, version, asOf, links);
    }

    /** 回答文本中出现的所有引用块（确定性正则解析，禁 LLM 判"有没有引用"）。 */
    public static List<ToolCitation> parseBlocks(String answer) {
        if (answer == null || answer.isBlank()) return List.of();
        List<ToolCitation> blocks = new ArrayList<>();
        Matcher m = CITATION_BLOCK.matcher(answer);
        while (m.find()) {
            blocks.add(new ToolCitation(m.group(1), m.group(2), m.group(3), m.group(4),
                    splitLinks(m.group(5))));
        }
        return blocks;
    }

    public static boolean hasCitationBlock(String answer) {
        return answer != null && CITATION_BLOCK.matcher(answer).find();
    }

    /** 答复中首个引用块的原文（逐字，供结构化字段下发给前端渲染；无块返回 null）。 */
    public static String firstBlock(String answer) {
        if (answer == null || answer.isBlank()) return null;
        Matcher m = CITATION_BLOCK.matcher(answer);
        return m.find() ? m.group() : null;
    }

    /**
     * 数字 token 集合（千分位归一为纯数字串，使回答与工具结果可在同一口径下做集合比对）。
     *
     * <p>集合比对是 F06-12 要点 1 的核心："工具结果中不存在的数字"直接判违规，
     * 比"有没有引用块"更强 —— 它封住的是幻觉数值本身。</p>
     */
    public static Set<String> numberTokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null || text.isBlank()) return tokens;
        Matcher m = NUMBER_TOKEN.matcher(text);
        while (m.find()) {
            tokens.add(normalize(m.group()));
        }
        return tokens;
    }

    /** 一批工具结果文本的数字 token 并集（引用白名单的唯一来源）。 */
    public static Set<String> numberTokensUnion(List<String> texts) {
        Set<String> union = new LinkedHashSet<>();
        for (String t : texts == null ? List.<String>of() : texts) {
            union.addAll(numberTokens(t));
        }
        return Collections.unmodifiableSet(union);
    }

    /**
     * 回答中"工具结果里不存在"的数字（违规集，保持出现顺序）。
     *
     * <p>引用块自身的数字（运行 ID / 版本 / 截至时间里的数字串）不计入违规：它们是引用载体，
     * 不是被声称的经营数值。</p>
     */
    public static List<String> numbersAbsentFrom(String answer, Set<String> allowedTokens) {
        if (answer == null || answer.isBlank()) return List.of();
        Set<String> allowed = allowedTokens == null ? Set.of() : allowedTokens;
        String withoutBlocks = CITATION_BLOCK.matcher(answer).replaceAll(" ");
        List<String> offenders = new ArrayList<>();
        for (String token : numberTokens(withoutBlocks)) {
            if (!allowed.contains(token)) offenders.add(token);
        }
        return offenders;
    }

    private static String normalize(String raw) {
        String s = raw.replace(",", "").replace('．', '.');
        int dot = s.indexOf('.');
        if (dot >= 0) {
            String frac = s.substring(dot + 1);
            while (frac.endsWith("0")) frac = frac.substring(0, frac.length() - 1);
            s = frac.isEmpty() ? s.substring(0, dot) : s.substring(0, dot + 1) + frac;
        }
        return s;
    }

    private static List<String> splitLinks(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> links = new ArrayList<>();
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) links.add(s);
        }
        return links;
    }

    private static String firstText(Map<String, Object> scope, Map<String, Object> body, List<String> keys) {
        for (String key : keys) {
            Object v = scope.get(key);
            if (v == null) v = body.get(key);
            if (v != null && isText(String.valueOf(v))) return String.valueOf(v).trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> firstLinks(Map<String, Object> scope, Map<String, Object> body, List<String> keys) {
        for (String key : keys) {
            Object v = scope.get(key);
            if (v == null) v = body.get(key);
            if (v == null) continue;
            if (v instanceof List<?> list) {
                List<String> links = new ArrayList<>();
                for (Object o : list) if (o != null && isText(String.valueOf(o))) links.add(String.valueOf(o).trim());
                return links;
            }
            if (v instanceof String s && !s.isBlank()) return splitLinks(s);
        }
        return List.of();
    }

    private static boolean isText(String s) {
        return s != null && !s.isBlank() && !s.toLowerCase(Locale.ROOT).equals("null");
    }
}
