package com.chinacreator.gzcm.common.stream;

import com.chinacreator.gzcm.common.trace.AgentLoopTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OAG 通道承流事件单源（#143/B5-5a 收口 D-B5-7）。
 *
 * <p>为什么与 {@link SseFrameContract} 分表而非并入：两篇契约本就不同形——
 * 详设-06 §9.3.3 是 agent-loop 对话流七帧（首帧 {@code meta}），详设-10 §10.3.6 是
 * WAgent run 流八帧；OAG 是既有 8 步 DAG 通道，事件名 {@code node/response/blocked/
 * error/done} 已被前端 {@code pages/aiworkbench/api.ts} 的 handlers 与网关
 * {@code OagForwardController} 透传消费。按「API 只增不改」红线，事件名与既有键
 * 不得改名，故本类登记的是<b>该通道自己的词表</b>，禁把 OAG 词硬塞进七帧序里造假同形。</p>
 *
 * <p>本批收的是「词表散在发射点」：改前 {@code OagController} 逐处
 * {@code sendEvent(emitter, "node", ...)} 自拼事件名（七帧词 {@code error}/{@code done}
 * 与 agent-loop 同字面量却不同载荷形），前端解析器与审计对账无从对齐。现事件名、
 * 键名与末帧规则唯一登记在本类，写出面只剩 {@code stream/OagSseFrames}。</p>
 *
 * <p>缺失标记复用 {@link SseFrameContract#textOrAbsent(String)}：两条通道对"未上报"
 * 必须写同一个标记，禁一条通道整键缺席、另一条写 {@code "-"}。</p>
 */
public final class OagSseFrameContract {

    /** 事件 {@code node}：每个管道节点完成时一条。 */
    public static final String EVENT_NODE = "node";
    /** 事件 {@code response}：模型最终正文（管道 COMPLETED）。 */
    public static final String EVENT_RESPONSE = "response";
    /** 事件 {@code blocked}：安全护栏拦截。 */
    public static final String EVENT_BLOCKED = "blocked";
    /** 事件 {@code error}：任一失败分支，永不携带异常原文。 */
    public static final String EVENT_ERROR = "error";
    /** 事件 {@code done}：末帧必发。 */
    public static final String EVENT_DONE = "done";

    /** OAG 通道事件词表（词形保持与前端 handlers 一致，禁改名）。 */
    public static final List<String> OAG_EVENT_ORDER = List.of(
            EVENT_NODE, EVENT_RESPONSE, EVENT_BLOCKED, EVENT_ERROR, EVENT_DONE);

    // ── 键面 ──

    public static final String KEY_NODE = "node";
    public static final String KEY_STATUS = "status";
    public static final String KEY_NODE_ELAPSED_MS = "nodeElapsedMs";
    /** 与 agent-loop 通道同一键名，取自单源，禁在本类再抄一遍字面量。 */
    public static final String KEY_TRACE_ID = SseFrameContract.KEY_TRACE_ID;
    public static final String KEY_INTENT = "intent";
    public static final String KEY_SECURITY_PASSED = "securityPassed";
    public static final String KEY_MESSAGE = "message";
    /**
     * 拦截原因键（前端 {@code onBlocked} 读 {@code data.reason}）。
     *
     * <p>改前 blocked 事件只出 {@code message}，前端读 {@code reason} 恒得空串 ⇒
     * 界面表现为「被拦截但不知道为什么」。本键与 {@link #KEY_MESSAGE} 在同一条事件里
     * 并存：{@code reason} 供拦截态渲染，{@code message} 保留给既有纯文本兜底腿。</p>
     */
    public static final String KEY_REASON = "reason";
    public static final String KEY_CONTENT = "content";

    private OagSseFrameContract() {
        throw new UnsupportedOperationException("常量/校验类，禁止实例化");
    }

    /** 该事件名是否在 OAG 通道词表内。 */
    public static boolean isKnownEvent(String event) {
        return event != null && OAG_EVENT_ORDER.contains(event);
    }

    /**
     * 事件序位（1 起）；未知事件名返回 {@link AgentLoopTrace#UNKNOWN}。
     *
     * <p>口径与七帧面 {@link SseFrameContract#frameIndex(String)} 完全一致：同一概念
     * （"第几帧"）在两条通道上必须是同一 numbering 与同一未知标记，否则跨通道对账
     * 要把两套下标换算一遍——那正是本批要消灭的漂移形态。</p>
     */
    public static int eventIndex(String event) {
        int at = event == null ? -1 : OAG_EVENT_ORDER.indexOf(event);
        return at < 0 ? (int) AgentLoopTrace.UNKNOWN : at + 1;
    }

    /** 末帧判定。 */
    public static boolean isTerminal(String event) {
        return EVENT_DONE.equals(event);
    }

    /**
     * 组装一条 OAG 载荷：{@code traceId} 恒置首且不可由业务键覆盖（缺失写单源标记）。
     *
     * @throws IllegalArgumentException 事件名不在 OAG 词表内（发射侧编程缺陷，fail-loud）
     */
    public static Map<String, Object> payload(String event, String traceId, Map<String, ?> fields) {
        if (!isKnownEvent(event)) {
            throw new IllegalArgumentException("未知 OAG 事件名: " + event + "（词表单源=OagSseFrameContract）");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(KEY_TRACE_ID, SseFrameContract.textOrAbsent(traceId));
        if (fields != null) {
            for (Map.Entry<String, ?> entry : fields.entrySet()) {
                if (KEY_TRACE_ID.equals(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                data.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(data);
    }

    /**
     * 事件序校验（OAG 通道的可静态断言部分）。
     *
     * <p>规则取自现状 + 承流底线：非空、事件名全在词表内、末帧 {@code done}、
     * {@code error} 至多一条且必须紧邻末帧之前、{@code response} 至多一条。</p>
     *
     * <p><b>为什么不校验首帧</b>：OAG 无 {@code meta} 通道上下文帧（改前即如此，
     * 前端 handlers 也不认 {@code meta}）。凭空规定首帧会把一条既有合规流判成违例，
     * 属自造判据；该差异作为通道偏差登记，不在本批擅自扩面。</p>
     *
     * @return 违例描述；合法序列返回空
     */
    public static Optional<String> validateSequence(List<String> events) {
        if (events == null || events.isEmpty()) {
            return Optional.of("空序列：OAG 承流必须至少以 done 收尾");
        }
        for (String event : events) {
            if (!isKnownEvent(event)) {
                return Optional.of("未知 OAG 事件名: " + event + "（词表单源=OagSseFrameContract）");
            }
        }
        String last = events.get(events.size() - 1);
        if (!EVENT_DONE.equals(last)) {
            return Optional.of("末帧必须为 " + EVENT_DONE + "，实得 " + last);
        }
        int errorCount = 0;
        int errorAt = -1;
        int responseCount = 0;
        for (int i = 0; i < events.size(); i++) {
            if (EVENT_ERROR.equals(events.get(i))) {
                errorCount++;
                errorAt = i;
            }
            if (EVENT_RESPONSE.equals(events.get(i))) {
                responseCount++;
            }
        }
        if (errorCount > 1) {
            return Optional.of("error 事件至多一条，实得 " + errorCount + " 条");
        }
        if (errorCount == 1 && errorAt != events.size() - 2) {
            return Optional.of("error 事件必须紧接 done 之前，实得下标 " + errorAt + "/" + (events.size() - 1));
        }
        if (responseCount > 1) {
            return Optional.of("response 事件至多一条，实得 " + responseCount + " 条");
        }
        return Optional.empty();
    }

    /** 事件名列表（供静态门与测试取词表，禁在消费侧另抄一份）。 */
    public static List<String> events() {
        return OAG_EVENT_ORDER;
    }

    /** 键名列表（供静态门计数：键面声明枚数漂移即词表被改动）。 */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        keys.add(KEY_TRACE_ID);
        keys.add(KEY_NODE);
        keys.add(KEY_STATUS);
        keys.add(KEY_NODE_ELAPSED_MS);
        keys.add(KEY_INTENT);
        keys.add(KEY_SECURITY_PASSED);
        keys.add(KEY_MESSAGE);
        keys.add(KEY_REASON);
        keys.add(KEY_CONTENT);
        return Collections.unmodifiableList(keys);
    }
}
