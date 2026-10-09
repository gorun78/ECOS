package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;
import java.util.Map;

/**
 * 决策底座访问适配层（卷07 F07-10，复用 cognitive {@code DecisionService}，禁新建第二套）。
 * <p>workspace 只依赖 cognitive <b>api 模块</b>（不直依 engine-impl，破 F07-16）；调用形态（HTTP / 本地 bean）
 * 由 R-24 裁决，两态各一实现，差异只封装在本适配层。具体实现属其它 Agent 的接缝（本包仅定义契约 + 编排）。</p>
 */
public interface DecisionGateway {

    Map<String, Object> proposeAction(String scenarioId, ActionProposal proposal, String approverSubject);

    Map<String, Object> approve(String actionId, String approverSubject);

    List<Map<String, Object>> listActions(String scenarioId);

    List<Map<String, Object>> listDecisions(String scenarioId);
}
