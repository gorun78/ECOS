package com.chinacreator.gzcm.ai.wagent.autos;

import com.chinacreator.gzcm.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.RunStatus;

import java.util.List;

/**
 * 分册10 F10-03/F10-05 · §5.2 24 个 W Agent 控制面 REST 端点的 operationId 单源。
 *
 * <p>path 字段<b>直接引用 {@link WAgentApiPaths} 的常量</b>（24 条路径的权威源），
 * 本文件补齐 method / operationId / 受影响 Run 状态三要素，构成 gateway 侧
 * {@code WagentApiParityGuard} 可遍历的 24 项冻结契约：guard 断言
 * ① {@link #OPERATIONS} 恰好 24 条；② 每条 opId 唯一；③ 每条 path 与 WAgentApiPaths 同字面（防漂移）。
 * 禁在别处用字面量重开 24 端点清单（R-59 契约同源）。</p>
 */
public final class WAgentAssestsApi {

    private WAgentAssestsApi() {}

    /** 一个 REST 端点的冻结契约：HTTP 方法 + 路径（取自 WAgentApiPaths 常量）+ operationId + 受影响 Run 状态集合。 */
    public record Op(String method, String path, String opId, RunStatus[] affectedStates) {}

    private static RunStatus[] st(RunStatus... s) { return s; }

    /**
     * §5.2 24 端点单源清单（顺序冻结，与 WAgentApiPaths 注释 #N 一一对应；API 只增不改）。
     * 受影响状态 = 该端点驱动/可观察的 RunState（只读查询端点允许空集合）。
     */
    public static final List<Op> OPERATIONS = List.of(
        new Op("POST",  WAgentApiPaths.CREATE_GOAL,        "createGoal",         st(RunStatus.PLANNING, RunStatus.AWAITING_INPUT)),                          // #1
        new Op("PATCH", WAgentApiPaths.REVISE_GOAL,        "reviseGoal",         st(RunStatus.PLANNING)),                                                            // #2
        new Op("POST",  WAgentApiPaths.CREATE_QUESTION,    "createQuestion",     st(RunStatus.PLANNING)),                                                            // #3
        new Op("GET",   WAgentApiPaths.GET_QUESTION,       "getQuestion",        st()),                                                                              // #4
        new Op("POST",  WAgentApiPaths.START_RUN,          "startRun",           st(RunStatus.PLANNING)),                                                            // #5
        new Op("GET",   WAgentApiPaths.GET_RUN,            "getRun",             st()),                                                                              // #6
        new Op("GET",   WAgentApiPaths.STREAM_RUN_EVENTS,  "streamRunEvents",    st(RunStatus.EXECUTING, RunStatus.WAITING_TASK)),                                // #7
        new Op("POST",  WAgentApiPaths.SUBMIT_RUN_INPUT,   "submitRunInput",     st(RunStatus.AWAITING_INPUT, RunStatus.EXECUTING)),                              // #8
        new Op("POST",  WAgentApiPaths.APPROVE_RUN_GATE,   "approveRunGate",     st(RunStatus.AWAITING_APPROVAL, RunStatus.PLAN_VALIDATED)),                     // #9
        new Op("POST",  WAgentApiPaths.CANCEL_RUN,         "cancelRun",          st(RunStatus.CANCELLED)),                                                           // #10
        new Op("POST",  WAgentApiPaths.SET_KILL_SWITCH,    "setKillSwitch",      st(RunStatus.CANCELLED, RunStatus.TIMED_OUT)),                                    // #11
        new Op("GET",   WAgentApiPaths.GET_READINESS,      "getReadiness",       st()),                                                                              // #12
        new Op("POST",  WAgentApiPaths.REFILL_READINESS,   "refillReadiness",    st(RunStatus.AWAITING_INPUT)),                                                      // #13
        new Op("GET",   WAgentApiPaths.SEARCH_TOOLS,       "searchTools",        st()),                                                                              // #14
        new Op("GET",   WAgentApiPaths.DESCRIBE_TOOL,      "describeTool",       st()),                                                                              // #15
        new Op("POST",  WAgentApiPaths.VALIDATE_TOOL_CONTRACT, "validateToolContract", st()),                                                                        // #16
        new Op("GET",   WAgentApiPaths.LIST_CANDIDATES,    "listCandidates",     st()),                                                                              // #17
        new Op("GET",   WAgentApiPaths.GET_CANDIDATE,      "getCandidate",       st()),                                                                              // #18
        new Op("POST",  WAgentApiPaths.REVIEW_CANDIDATE,   "reviewCandidate",    st()),                                                                              // #19
        new Op("POST",  WAgentApiPaths.PUBLISH_CANDIDATE,  "publishCandidate",   st(RunStatus.EXECUTING, RunStatus.WAITING_TASK)),                                // #20
        new Op("GET",   WAgentApiPaths.LIST_CLAIMS,        "listClaims",         st()),                                                                              // #21
        new Op("GET",   WAgentApiPaths.TRACE_EVIDENCE,     "traceEvidence",      st()),                                                                              // #22
        new Op("POST",  WAgentApiPaths.RECORD_DECISION,    "recordDecision",     st(RunStatus.PLAN_VALIDATED)),                                                      // #23
        new Op("POST",  WAgentApiPaths.DRAFT_ACTION,       "draftAction",        st(RunStatus.EXECUTING))                                                            // #24
    );

    /** 冻结基数（= 24）：guard 用此常量，不硬编码。 */
    public static int operationCount() { return OPERATIONS.size(); }

    /** operationId 唯一性自检：任何 guard 先调此方法（重复 opId ⇒ false）。 */
    public static boolean operationIdsUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Op op : OPERATIONS) {
            if (!seen.add(op.opId())) return false;
        }
        return true;
    }
}
