package com.chinacreator.gzcm.ai.wagent.promptinjection;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分册10 F10-14/F10-24 · Prompt Injection 过滤守卫（Plan 白名单 × 提议 Step）。
 *
 * <p>当前 Plan 的 {@code allowedToolNames} 是唯一来源；提议的 toolCall 里若追加了
 * <b>计划外</b> 工具名（narrative 白名单外）⇒ <b>整条丢弃</b>（返回 {@code null}），
 * 不部分放行。{@link #extractToolNames(String)} 用保守正则从 narrative 抽 "tool:" 前缀名。</p>
 */
public final class PromptInjectionGuard {

    /** 提议的 Step 调用。 */
    public record StepCall(String toolName, Map<String, String> args, String narrative) {}

    /** "tool: <name>" / "tool:<name>" 模式（保守：不猜自然语言工具名）。 */
    private static final Pattern TOOL_NAME =
            Pattern.compile("tool:\\s*([A-Za-z0-9_\\-\\.]+)");

    private PromptInjectionGuard() {}

    /**
     * 过滤：主工具名必须 ∈ allowed；narrative 里追加的任何 "tool:<name>" 也必须 ∈ allowed。
     * 违例 → 返回 {@code null}（丢弃整条 Step，不部分放行）。
     *
     * @param call    提议 Step
     * @param allowed 当前 Plan 白名单（不可为空）
     * @return 干净则原样返回，否则 {@code null}
     */
    public static StepCall filter(StepCall call, Set<String> allowed) {
        if (call == null || allowed == null || allowed.isEmpty()) {
            return null;
        }
        String main = call.toolName();
        if (main == null || !allowed.contains(main)) {
            return null;
        }
        Set<String> narrativeTools = extractToolNames(call.narrative());
        for (String extra : narrativeTools) {
            if (!allowed.contains(extra)) {
                return null; // 计划外工具名 → Plan 被套用 → 整条丢弃
            }
        }
        return call;
    }

    /** 从 narrative 抽 "tool:<name>"。无匹配返回空集。 */
    static Set<String> extractToolNames(String narrative) {
        Set<String> out = new LinkedHashSet<>();
        if (narrative == null || narrative.isEmpty()) return out;
        Matcher m = TOOL_NAME.matcher(narrative);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }
}
