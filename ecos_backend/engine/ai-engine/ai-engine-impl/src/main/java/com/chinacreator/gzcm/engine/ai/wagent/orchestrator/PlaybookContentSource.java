package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import java.util.Optional;

/**
 * Playbook 内容读取接缝（详细设计-10 F10-12 / 铁律 §3.1）。
 *
 * <p>Playbook 正文 = Git 归档资产，DB（V228）只存在用版本引用
 * {@code playbook_git_ref + playbook_version}；本接口是编排侧读取模板正文的<b>唯一消费面</b>。
 * 真实实现（经 runtime-access GitService 归档通道读取）随 #131/R3-A 落地——
 * 编排核心经 {@code ObjectProvider} 注入本接口，缺席时 PlanBuilder 一律走
 * {@link ExplorationPlanner} EXPLORED 路径并记缺口事件（<b>禁伪称已读到 Playbook</b>）。</p>
 */
public interface PlaybookContentSource {

    /**
     * 按在用版本引用读取 Playbook 模板正文（含 <code>{{slot}}</code> 占位的 JSON 文本）。
     *
     * @return {@code Optional.empty()} = 该引用在当前归档中不可得（上游据此落 EXPLORED + 缺口事件）
     */
    Optional<String> loadActiveTemplate(String tenantId, String playbookGitRef, String playbookVersion);
}
