package com.chinacreator.gzcm.services.agent.runtime.toolrouter.tools;

import java.util.Map;

/**
 * agent 工具族的<b>未发起调用</b>载荷单源（#142/B4-3e-9，随 D-B4-46 收口引入）。
 *
 * <p>基址经 {@code AgentToolEndpointResolver} 解析失败（resolver Bean 缺席或属主未登记）时，
 * 工具<b>不回落硬编码地址、也不伪造成业务结果</b>，而是如实回一个「本次未发起调用」的载荷。
 * 七支工具共用同一个形态，避免各工具自造 {@code available}/{@code ok}/{@code success} 键面。</p>
 */
final class AgentToolResponses {

    /** 载荷键：本次调用是否真的发出过请求。 */
    static final String KEY_AVAILABLE = "available";

    /** 载荷键：面向 LLM 的如实说明。 */
    static final String KEY_MESSAGE = "message";

    /**
     * 载荷键：结论态名（{@link Outcome} 的枚举名，机读）。
     *
     * <p>#142/B4-3e-22 起键面单源在此：此前该键由 {@code QueryPhysicalTableTool} 私有持有，
     * 两支写腿收口时若各自再声明一遍，就会出现三个定义点。</p>
     */
    static final String KEY_STATE = "state";

    private AgentToolResponses() {
    }

    /**
     * 组「依赖基址不可解析 ⇒ 调用未发起」载荷。
     *
     * @param family 族名（用于定位是哪条腿断了）
     */
    static Map<String, Object> unreachable(String family) {
        return Map.of(KEY_AVAILABLE, false,
                KEY_MESSAGE, family + " 服务基址不可解析（route-manifest 未登记属主，或 runtime-access "
                        + "ServiceEndpointResolver 缺席），本次工具调用未发起");
    }
}
