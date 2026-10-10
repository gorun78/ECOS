package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playbook 模板填充器（详细设计-10 F10-12 / PlaybookRendererTest#parametersFilledDeterministically）。
 *
 * <p>Playbook 定义 = Git 归档资产（铁律 §3.1，DB 只存 {@code playbook_git_ref + playbook_version}，
 * 内容经 {@link PlaybookContentSource} 接缝读取）；本类只做<b>确定性填充</b>：
 * 占位形态 <code>{{slotKey}}</code>，同参数必得同输出（无随机、无时钟、无 LLM 参与——
 * "数字不来自 LLM"在计划面的形态）。参数缺失 ⇒ 拒绝（禁半填充 Plan 出闸）。</p>
 */
@Component
public class PlaybookRenderer {

    /** 占位符语法单源：{@code {{key}}}（key 为槽位名字符）。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    /**
     * 用参数表填充模板：逐占位符字面替换；填充后仍残留占位 ⇒ {@code E-VALIDATION}
     * （缺参 Plan 不得进入七项校验，更早暴露）。
     *
     * @return 填充后的模板文本（确定性：同 template + 同 params 两次调用逐字符相等）
     */
    public String fill(String template, Map<String, String> params) {
        if (template == null) {
            throw WAgentOrchestrationException.validation("Playbook 模板不得为 null");
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = params == null ? null : params.get(key);
            if (value == null) {
                throw WAgentOrchestrationException.validation(
                        "Playbook 占位参数缺失: " + key + "（禁半填充计划出闸）");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        String filled = out.toString();
        Matcher residue = PLACEHOLDER.matcher(filled);
        if (residue.find()) {
            // 防御双校：参数值本身不得再引入占位（防注入二次展开）
            throw WAgentOrchestrationException.validation(
                    "Playbook 填充后仍含占位（参数值注入占位被拒绝）: " + residue.group());
        }
        return filled;
    }
}
